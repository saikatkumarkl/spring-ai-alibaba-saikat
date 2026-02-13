#!/bin/sh
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# init-manifoldcf.sh
# Waits for ManifoldCF API, then ensures:
#   0. OpenSearch index template (ACL-aware + authorities) exists
#   1. OpenSearch output connection (for documents) exists
#   2. OpenSearch output connection (for authorities) exists
#   3. Alfresco CMIS repository connection exists
#   4. Authorities sync job (group/user fetch) is created and started
#   5. Document crawl job is created (queued until authorities index exists)

MCF_API="${MCF_API_URL:-http://localhost:8345/mcf-api-service}"
MAX_WAIT=120
INTERVAL=5

# --- Environment defaults ---
OPENSEARCH_URL="${OPENSEARCH_URL:-http://opensearch:9200/}"

# Dynamic index naming: manifold_{job_name_sanitized}
# Authorities index: manifold_{repo_name_sanitized}_authorities
CMIS_USERNAME="${CMIS_USERNAME:-CHANGE_ME_USERNAME}"
CMIS_PASSWORD="${CMIS_PASSWORD:-CHANGE_ME_PASSWORD}"
CMIS_PROTOCOL="${CMIS_PROTOCOL:-https}"
CMIS_SERVER="${CMIS_SERVER:-alfresco-demo.crestsolution.com}"
CMIS_PORT="${CMIS_PORT:-8080}"
CMIS_PATH="${CMIS_PATH:-/alfresco/api/-default-/cmis/versions/1.1/atom}"
CMIS_BINDING="${CMIS_BINDING:-atom}"
CMIS_REPOSITORY_ID="${CMIS_REPOSITORY_ID:--default-}"

# CMIS vendor + group API config (for authorities sync)
CMIS_VENDOR="${CMIS_VENDOR:-alfresco}"
CMIS_GROUP_API_URL="${CMIS_GROUP_API_URL:-/alfresco/api/-default-/public/alfresco/versions/1/groups}"
CMIS_GROUP_MEMBERS_API_URL="${CMIS_GROUP_MEMBERS_API_URL:-/alfresco/api/-default-/public/alfresco/versions/1/groups/{groupId}/members}"

# Skip ACL wait — set to "true" to skip waiting for authorities index
SKIP_ACL_WAIT="${SKIP_ACL_WAIT:-false}"

# Max file size in MB — files larger than this are skipped during indexing (0 = no limit)
MAX_FILE_SIZE_MB="${MAX_FILE_SIZE_MB:-0}"
# Convert MB to bytes for the connector config
if [ "$MAX_FILE_SIZE_MB" -gt 0 ] 2>/dev/null; then
  MAX_FILE_SIZE_BYTES=$((MAX_FILE_SIZE_MB * 1024 * 1024))
else
  MAX_FILE_SIZE_BYTES=0
fi

# Cron schedule for incremental crawl (default: every 6 hours)
CRAWL_CRON_MINUTE="${CRAWL_CRON_MINUTE:-0}"
CRAWL_CRON_HOUR="${CRAWL_CRON_HOUR:-*/6}"
CRAWL_CRON_DAY_OF_WEEK="${CRAWL_CRON_DAY_OF_WEEK:-*}"
CRAWL_CRON_DAY_OF_MONTH="${CRAWL_CRON_DAY_OF_MONTH:-*}"
CRAWL_CRON_MONTH="${CRAWL_CRON_MONTH:-*}"
CRAWL_CRON_YEAR="${CRAWL_CRON_YEAR:-*}"

# Recrawl interval in milliseconds (default: 6 hours = 21600000 ms)
# Documents modified since last run or new documents will be re-indexed
RECRAWL_INTERVAL_MS="${RECRAWL_INTERVAL_MS:-21600000}"

# Derive sanitized names for index naming
# e.g. "alfresco-demo.crestsolution.com" -> "alfresco_demo_crestsolution_com"
REPO_NAME_SANITIZED=$(echo "$CMIS_SERVER" | tr '[:upper:]' '[:lower:]' | sed 's/[^a-z0-9]/_/g')

OUTPUT_CONN_NAME="OpenSearch"
OUTPUT_CONN_AUTH_NAME="OpenSearch-Authorities"
REPO_CONN_NAME="Alfresco CMIS"

# Dynamic index names
DOC_INDEX_NAME="manifold_${REPO_NAME_SANITIZED}"
AUTH_INDEX_NAME="manifold_${REPO_NAME_SANITIZED}_authorities"

JOB_DESCRIPTION="Alfresco Document Crawl (${DOC_INDEX_NAME})"
AUTH_JOB_DESCRIPTION="Alfresco Authorities Sync (${AUTH_INDEX_NAME})"

# =====================================================
# Wait for ManifoldCF API
# =====================================================
echo "=============================================="
echo "ManifoldCF Auto-Configuration"
echo "=============================================="
echo "  Document index:     ${DOC_INDEX_NAME}"
echo "  Authorities index:  ${AUTH_INDEX_NAME}"
echo "  Skip ACL wait:      ${SKIP_ACL_WAIT}"
echo "  Max file size:      ${MAX_FILE_SIZE_MB} MB (${MAX_FILE_SIZE_BYTES} bytes)"
echo "=============================================="
echo "Waiting for ManifoldCF API at ${MCF_API}..."
elapsed=0
while [ "$elapsed" -lt "$MAX_WAIT" ]; do
  status=$(curl -sf -o /dev/null -w "%{http_code}" "${MCF_API}/json/outputconnectors" 2>/dev/null)
  if [ "$status" = "200" ]; then
    echo "ManifoldCF API is ready."
    break
  fi
  sleep "$INTERVAL"
  elapsed=$((elapsed + INTERVAL))
done

if [ "$elapsed" -ge "$MAX_WAIT" ]; then
  echo "ERROR: ManifoldCF API did not become available within ${MAX_WAIT}s"
  exit 1
fi

# =====================================================
# 0. OpenSearch Index Templates (ACL-aware + authorities)
# =====================================================
echo ""
echo "--- [0/6] OpenSearch Index Templates ---"
OPENSEARCH_BASE=$(echo "$OPENSEARCH_URL" | sed 's:/*$::')

# Delete legacy template that conflicts with our new naming
curl -sf -X DELETE "${OPENSEARCH_BASE}/_index_template/manifoldcf-acl" 2>/dev/null

# Template for document indexes (manifold_*)
template_status=$(curl -sf -o /dev/null -w "%{http_code}" "${OPENSEARCH_BASE}/_index_template/manifold-acl" 2>/dev/null)
if [ "$template_status" = "200" ]; then
  echo "Index template 'manifold-acl' already exists — skipping."
else
  echo "Creating index template 'manifold-acl' for document indexes..."
  curl -sf -X PUT "${OPENSEARCH_BASE}/_index_template/manifold-acl" \
    -H "Content-Type: application/json" \
    -d '{
      "index_patterns": ["manifold_*", "manifoldcf*"],
      "priority": 100,
      "template": {
        "settings": {
          "index.highlight.max_analyzed_offset": 100000000,
          "analysis": {
            "normalizer": {
              "lowercase": {
                "type": "custom",
                "filter": ["lowercase"]
              }
            }
          }
        },
        "mappings": {
          "dynamic": true,
          "properties": {
            "allow_token_document": { "type": "keyword", "normalizer": "lowercase" },
            "deny_token_document":  { "type": "keyword", "normalizer": "lowercase" },
            "allow_token_parent":   { "type": "keyword", "normalizer": "lowercase" },
            "deny_token_parent":    { "type": "keyword", "normalizer": "lowercase" },
            "allow_token_share":    { "type": "keyword", "normalizer": "lowercase" },
            "deny_token_share":     { "type": "keyword", "normalizer": "lowercase" },
            "authorities":          { "type": "keyword", "normalizer": "lowercase" }
          }
        }
      }
    }' 2>&1
  echo ""
fi

# Template for authorities indexes (manifold_*_authorities)
auth_template_status=$(curl -sf -o /dev/null -w "%{http_code}" "${OPENSEARCH_BASE}/_index_template/manifold-authorities" 2>/dev/null)
if [ "$auth_template_status" = "200" ]; then
  echo "Index template 'manifold-authorities' already exists — skipping."
else
  echo "Creating index template 'manifold-authorities' for authorities indexes..."
  curl -sf -X PUT "${OPENSEARCH_BASE}/_index_template/manifold-authorities" \
    -H "Content-Type: application/json" \
    -d '{
      "index_patterns": ["manifold_*_authorities"],
      "priority": 200,
      "template": {
        "settings": {
          "analysis": {
            "normalizer": {
              "lowercase": {
                "type": "custom",
                "filter": ["lowercase"]
              }
            }
          }
        },
        "mappings": {
          "dynamic": true,
          "properties": {
            "group_id":     { "type": "keyword", "normalizer": "lowercase" },
            "display_name": { "type": "text" },
            "members":      { "type": "keyword", "normalizer": "lowercase" },
            "synced_at":    { "type": "date" }
          }
        }
      }
    }' 2>&1
  echo ""
fi

# =====================================================
# 1. OpenSearch Output Connection (for documents)
# =====================================================
echo ""
echo "--- [1/6] OpenSearch Output Connection (Documents) ---"
existing=$(curl -sf "${MCF_API}/json/outputconnections/${OUTPUT_CONN_NAME}" 2>/dev/null)
if echo "$existing" | grep -q '"isnew"'; then
  echo "Output connection '${OUTPUT_CONN_NAME}' already exists — skipping."
else
  echo "Creating output connection '${OUTPUT_CONN_NAME}' -> index '${DOC_INDEX_NAME}'..."
  result=$(curl -sf -X PUT "${MCF_API}/json/outputconnections/${OUTPUT_CONN_NAME}" \
    -H "Content-Type: application/json" \
    -d "{
      \"outputconnection\": {
        \"name\": \"${OUTPUT_CONN_NAME}\",
        \"class_name\": \"org.apache.manifoldcf.agents.output.elasticsearch.ElasticSearchConnector\",
        \"description\": \"Document index: ${DOC_INDEX_NAME}\",
        \"max_connections\": \"10\",
        \"configuration\": {
          \"_PARAMETER_\": [
            {\"_attribute_name\": \"SERVERLOCATION\",       \"_value_\": \"${OPENSEARCH_URL}\"},
            {\"_attribute_name\": \"INDEXNAME\",            \"_value_\": \"${DOC_INDEX_NAME}\"},
            {\"_attribute_name\": \"INDEXTYPE\",            \"_value_\": \"_doc\"},
            {\"_attribute_name\": \"AUTHORITIESINDEXNAME\", \"_value_\": \"${AUTH_INDEX_NAME}\"}
          ]
        }
      }
    }" 2>&1)
  if [ $? -eq 0 ]; then
    echo "Output connection '${OUTPUT_CONN_NAME}' created: $result"
  else
    echo "WARNING: Failed to create output connection: $result"
  fi
fi
check=$(curl -sf "${MCF_API}/json/status/outputconnections/${OUTPUT_CONN_NAME}" 2>/dev/null)
echo "Status: $check"

# =====================================================
# 2. OpenSearch Output Connection (for authorities)
# =====================================================
echo ""
echo "--- [2/6] OpenSearch Output Connection (Authorities) ---"
OUTPUT_CONN_AUTH_ENCODED=$(echo "$OUTPUT_CONN_AUTH_NAME" | sed 's/ /%20/g')
existing=$(curl -sf "${MCF_API}/json/outputconnections/${OUTPUT_CONN_AUTH_ENCODED}" 2>/dev/null)
if echo "$existing" | grep -q '"isnew"'; then
  echo "Output connection '${OUTPUT_CONN_AUTH_NAME}' already exists — skipping."
else
  echo "Creating output connection '${OUTPUT_CONN_AUTH_NAME}' -> index '${AUTH_INDEX_NAME}'..."
  result=$(curl -sf -X PUT "${MCF_API}/json/outputconnections/${OUTPUT_CONN_AUTH_ENCODED}" \
    -H "Content-Type: application/json" \
    -d "{
      \"outputconnection\": {
        \"name\": \"${OUTPUT_CONN_AUTH_NAME}\",
        \"class_name\": \"org.apache.manifoldcf.agents.output.elasticsearch.ElasticSearchConnector\",
        \"description\": \"Authorities index: ${AUTH_INDEX_NAME}\",
        \"max_connections\": \"10\",
        \"configuration\": {
          \"_PARAMETER_\": [
            {\"_attribute_name\": \"SERVERLOCATION\", \"_value_\": \"${OPENSEARCH_URL}\"},
            {\"_attribute_name\": \"INDEXNAME\",      \"_value_\": \"${AUTH_INDEX_NAME}\"},
            {\"_attribute_name\": \"INDEXTYPE\",      \"_value_\": \"_doc\"}
          ]
        }
      }
    }" 2>&1)
  if [ $? -eq 0 ]; then
    echo "Output connection '${OUTPUT_CONN_AUTH_NAME}' created: $result"
  else
    echo "WARNING: Failed to create output connection: $result"
  fi
fi

# =====================================================
# 3. Alfresco CMIS Repository Connection
# =====================================================
echo ""
echo "--- [3/6] CMIS Repository Connection ---"
REPO_CONN_ENCODED=$(echo "$REPO_CONN_NAME" | sed 's/ /%20/g')
existing=$(curl -sf "${MCF_API}/json/repositoryconnections/${REPO_CONN_ENCODED}" 2>/dev/null)
if echo "$existing" | grep -q '"isnew"'; then
  echo "Repository connection '${REPO_CONN_NAME}' already exists — skipping."
else
  echo "Creating repository connection '${REPO_CONN_NAME}' -> ${CMIS_PROTOCOL}://${CMIS_SERVER}:${CMIS_PORT}${CMIS_PATH}..."
  result=$(curl -sf -X PUT "${MCF_API}/json/repositoryconnections/${REPO_CONN_ENCODED}" \
    -H "Content-Type: application/json" \
    -d "{
      \"repositoryconnection\": {
        \"name\": \"${REPO_CONN_NAME}\",
        \"class_name\": \"org.apache.manifoldcf.crawler.connectors.cmis.CmisRepositoryConnector\",
        \"description\": \"Alfresco CMIS Repository via ${CMIS_BINDING} binding\",
        \"max_connections\": \"10\",
        \"configuration\": {
          \"_PARAMETER_\": [
            {\"_attribute_name\": \"binding\",            \"_value_\": \"${CMIS_BINDING}\"},
            {\"_attribute_name\": \"username\",           \"_value_\": \"${CMIS_USERNAME}\"},
            {\"_attribute_name\": \"password\",           \"_value_\": \"${CMIS_PASSWORD}\"},
            {\"_attribute_name\": \"protocol\",           \"_value_\": \"${CMIS_PROTOCOL}\"},
            {\"_attribute_name\": \"server\",             \"_value_\": \"${CMIS_SERVER}\"},
            {\"_attribute_name\": \"port\",               \"_value_\": \"${CMIS_PORT}\"},
            {\"_attribute_name\": \"path\",               \"_value_\": \"${CMIS_PATH}\"},
            {\"_attribute_name\": \"repositoryId\",       \"_value_\": \"${CMIS_REPOSITORY_ID}\"},
            {\"_attribute_name\": \"cmisVendor\",         \"_value_\": \"${CMIS_VENDOR}\"},
            {\"_attribute_name\": \"groupApiUrl\",        \"_value_\": \"${CMIS_GROUP_API_URL}\"},
            {\"_attribute_name\": \"groupMembersApiUrl\", \"_value_\": \"${CMIS_GROUP_MEMBERS_API_URL}\"},
            {\"_attribute_name\": \"skipAclWait\",        \"_value_\": \"${SKIP_ACL_WAIT}\"},
            {\"_attribute_name\": \"maxFileSize\",        \"_value_\": \"${MAX_FILE_SIZE_BYTES}\"}
          ]
        }
      }
    }" 2>&1)
  if [ $? -eq 0 ]; then
    echo "Repository connection '${REPO_CONN_NAME}' created."
  else
    echo "WARNING: Failed to create repository connection: $result"
  fi
fi
check=$(curl -sf "${MCF_API}/json/status/repositoryconnections/${REPO_CONN_ENCODED}" 2>/dev/null)
echo "Status: $check"

# =====================================================
# 4. Authorities Sync Job (group/user fetch)
#    This job uses the CMIS connector's built-in group
#    membership syncer to populate the authorities index.
#    It is auto-started and runs on a schedule.
# =====================================================
echo ""
echo "--- [4/6] Authorities Sync Job ---"
existing_jobs=$(curl -sf "${MCF_API}/json/jobs" 2>/dev/null)
AUTH_JOB_ID=""
if echo "$existing_jobs" | grep -q "\"${AUTH_JOB_DESCRIPTION}\""; then
  echo "Job '${AUTH_JOB_DESCRIPTION}' already exists — skipping creation."
  # Extract job ID for later use
  AUTH_JOB_ID=$(echo "$existing_jobs" | sed -n "/${AUTH_JOB_DESCRIPTION}/{ s/.*\"id\":\"\([^\"]*\)\".*/\1/p; }" 2>/dev/null)
else
  echo "Creating authorities sync job '${AUTH_JOB_DESCRIPTION}'..."
  # Write JSON to temp file to avoid shell escaping issues in Alpine sh
  cat > /tmp/auth-job.json << ENDJSON
{"job":{"_children_":[{"_type_":"description","_value_":"${AUTH_JOB_DESCRIPTION}"},{"_type_":"repository_connection","_value_":"${REPO_CONN_NAME}"},{"_type_":"pipelinestage","_children_":[{"_type_":"stage_id","_value_":"0"},{"_type_":"stage_isoutput","_value_":"true"},{"_type_":"stage_connectionname","_value_":"${OUTPUT_CONN_AUTH_NAME}"}]},{"_type_":"run_mode","_value_":"scan once"},{"_type_":"start_mode","_value_":"manual"},{"_type_":"hopcount_mode","_value_":"accurate"},{"_type_":"priority","_value_":"3"},{"_type_":"recrawl_interval","_value_":"${RECRAWL_INTERVAL_MS}"},{"_type_":"document_specification","_children_":[{"_type_":"startpoint","_attribute_cmisQuery":"SELECT cmis:objectId FROM cmis:folder WHERE cmis:name = '__AUTHORITIES_SYNC__'","_value_":""}]},{"_type_":"schedule","_children_":[{"_type_":"requestminimum","_value_":"false"},{"_type_":"dayofweek","_value_":"${CRAWL_CRON_DAY_OF_WEEK}"},{"_type_":"monthofyear","_value_":"${CRAWL_CRON_MONTH}"},{"_type_":"dayofmonth","_value_":"${CRAWL_CRON_DAY_OF_MONTH}"},{"_type_":"year","_value_":"${CRAWL_CRON_YEAR}"},{"_type_":"hourofday","_value_":"${CRAWL_CRON_HOUR}"},{"_type_":"minutesofhour","_value_":"${CRAWL_CRON_MINUTE}"}]}]}}
ENDJSON
  result=$(curl -sf -X POST "${MCF_API}/json/jobs" \
    -H "Content-Type: application/json" \
    --data-binary @/tmp/auth-job.json 2>&1)
  if [ $? -eq 0 ]; then
    echo "Authorities job created: $result"
    # Extract job ID from response
    AUTH_JOB_ID=$(echo "$result" | sed -n 's/.*"job_id":"\([^"]*\)".*/\1/p' 2>/dev/null)
  else
    echo "WARNING: Failed to create authorities job: $result"
  fi
  rm -f /tmp/auth-job.json
fi

# Auto-start the authorities sync job
if [ -n "$AUTH_JOB_ID" ]; then
  echo "Starting authorities sync job (ID: ${AUTH_JOB_ID})..."
  curl -sf -X PUT "${MCF_API}/json/start/${AUTH_JOB_ID}" 2>/dev/null
  echo ""
fi

# =====================================================
# 5. Wait for Authorities Index (unless SKIP_ACL_WAIT)
# =====================================================
echo ""
echo "--- [5/6] Authorities Index Check ---"
if [ "$SKIP_ACL_WAIT" = "true" ]; then
  echo "SKIP_ACL_WAIT=true — proceeding without waiting for authorities index."
else
  echo "Waiting for authorities index '${AUTH_INDEX_NAME}' to be created..."
  auth_wait=0
  AUTH_MAX_WAIT=300
  while [ "$auth_wait" -lt "$AUTH_MAX_WAIT" ]; do
    idx_status=$(curl -sf -o /dev/null -w "%{http_code}" "${OPENSEARCH_BASE}/${AUTH_INDEX_NAME}" 2>/dev/null)
    if [ "$idx_status" = "200" ]; then
      # Check if it has at least one document
      doc_count=$(curl -sf "${OPENSEARCH_BASE}/${AUTH_INDEX_NAME}/_count" 2>/dev/null | sed -n 's/.*"count":\([0-9]*\).*/\1/p')
      if [ -n "$doc_count" ] && [ "$doc_count" -gt 0 ]; then
        echo "Authorities index '${AUTH_INDEX_NAME}' has ${doc_count} documents — ready."
        break
      fi
    fi
    sleep "$INTERVAL"
    auth_wait=$((auth_wait + INTERVAL))
    if [ $((auth_wait % 30)) -eq 0 ]; then
      echo "  Still waiting... (${auth_wait}s elapsed)"
    fi
  done
  if [ "$auth_wait" -ge "$AUTH_MAX_WAIT" ]; then
    echo "WARNING: Authorities index not ready after ${AUTH_MAX_WAIT}s."
    echo "  Proceeding with document crawl anyway (authorities field may be empty)."
  fi
fi

# =====================================================
# 6. Document Crawl Job (CMIS → OpenSearch)
#    Uses incremental crawl with cron scheduling.
#    Only indexes documents modified since last run.
# =====================================================
echo ""
echo "--- [6/6] Document Crawl Job ---"
existing_jobs=$(curl -sf "${MCF_API}/json/jobs" 2>/dev/null)
if echo "$existing_jobs" | grep -q "\"${JOB_DESCRIPTION}\""; then
  echo "Job '${JOB_DESCRIPTION}' already exists — skipping."
else
  echo "Creating document crawl job '${JOB_DESCRIPTION}'..."
  # Write JSON to temp file to avoid shell escaping issues in Alpine sh
  cat > /tmp/doc-job.json << ENDJSON
{"job":{"_children_":[{"_type_":"description","_value_":"${JOB_DESCRIPTION}"},{"_type_":"repository_connection","_value_":"${REPO_CONN_NAME}"},{"_type_":"pipelinestage","_children_":[{"_type_":"stage_id","_value_":"0"},{"_type_":"stage_isoutput","_value_":"true"},{"_type_":"stage_connectionname","_value_":"${OUTPUT_CONN_NAME}"}]},{"_type_":"run_mode","_value_":"scan once"},{"_type_":"start_mode","_value_":"manual"},{"_type_":"hopcount_mode","_value_":"accurate"},{"_type_":"priority","_value_":"5"},{"_type_":"recrawl_interval","_value_":"${RECRAWL_INTERVAL_MS}"},{"_type_":"document_specification","_children_":[{"_type_":"startpoint","_attribute_cmisQuery":"SELECT * FROM cmis:document WHERE IN_TREE('workspace://SpacesStore/1305f68b-4998-4ffd-85f6-8b49986ffd1b')","_value_":""}]},{"_type_":"schedule","_children_":[{"_type_":"requestminimum","_value_":"false"},{"_type_":"dayofweek","_value_":"${CRAWL_CRON_DAY_OF_WEEK}"},{"_type_":"monthofyear","_value_":"${CRAWL_CRON_MONTH}"},{"_type_":"dayofmonth","_value_":"${CRAWL_CRON_DAY_OF_MONTH}"},{"_type_":"year","_value_":"${CRAWL_CRON_YEAR}"},{"_type_":"hourofday","_value_":"${CRAWL_CRON_HOUR}"},{"_type_":"minutesofhour","_value_":"${CRAWL_CRON_MINUTE}"}]}]}}
ENDJSON
  result=$(curl -sf -X POST "${MCF_API}/json/jobs" \
    -H "Content-Type: application/json" \
    --data-binary @/tmp/doc-job.json 2>&1)
  if [ $? -eq 0 ]; then
    DOC_JOB_ID=$(echo "$result" | sed -n 's/.*"job_id":"\([^"]*\)".*/\1/p' 2>/dev/null)
    echo "Document crawl job created: $result"
    # Auto-start the document crawl job
    if [ -n "$DOC_JOB_ID" ]; then
      echo "Starting document crawl job (ID: ${DOC_JOB_ID})..."
      curl -sf -X PUT "${MCF_API}/json/start/${DOC_JOB_ID}" 2>/dev/null
      echo ""
    fi
  else
    echo "WARNING: Failed to create document crawl job: $result"
  fi
  rm -f /tmp/doc-job.json
fi

echo ""
echo "=============================================="
echo "ManifoldCF auto-configuration complete."
echo "  Document index:    ${DOC_INDEX_NAME}"
echo "  Authorities index: ${AUTH_INDEX_NAME}"
echo "  Crawl schedule:    ${CRAWL_CRON_MINUTE} ${CRAWL_CRON_HOUR} * * *"
echo "  Recrawl interval:  ${RECRAWL_INTERVAL_MS}ms"
echo "=============================================="
