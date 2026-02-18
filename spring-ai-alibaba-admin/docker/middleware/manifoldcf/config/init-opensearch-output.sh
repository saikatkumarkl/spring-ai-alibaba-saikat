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
#   0. OpenSearch index template (ACL-aware) exists
#   1. OpenSearch output connection (for documents) exists
#   2. Alfresco CMIS repository connection exists
#   3. Tika transformation connection exists
#   4. Document crawl job is created and started

MCF_API="${MCF_API_URL:-http://localhost:8345/mcf-api-service}"
MCF_API_USER="${MCF_API_USERNAME:-admin}"
MCF_API_PASS="${MCF_API_PASSWORD:-admin}"
MAX_WAIT=120
INTERVAL=5

# --- Environment defaults ---
OPENSEARCH_URL="${OPENSEARCH_URL:-http://opensearch:9200/}"

# Dynamic index naming: manifold_{repo_name_sanitized}
CMIS_USERNAME="${CMIS_USERNAME:-CHANGE_ME_USERNAME}"
CMIS_PASSWORD="${CMIS_PASSWORD:-CHANGE_ME_PASSWORD}"
CMIS_PROTOCOL="${CMIS_PROTOCOL:-https}"
CMIS_SERVER="${CMIS_SERVER:-alfresco-demo.crestsolution.com}"
CMIS_PORT="${CMIS_PORT:-8080}"
CMIS_PATH="${CMIS_PATH:-/alfresco/api/-default-/cmis/versions/1.1/atom}"
CMIS_BINDING="${CMIS_BINDING:-atom}"
CMIS_REPOSITORY_ID="${CMIS_REPOSITORY_ID:--default-}"

# CMIS vendor + group API config (used by backend authority sync)
CMIS_VENDOR="${CMIS_VENDOR:-alfresco}"
CMIS_GROUP_API_URL="${CMIS_GROUP_API_URL:-/alfresco/api/-default-/public/alfresco/versions/1/groups}"
CMIS_GROUP_MEMBERS_API_URL="${CMIS_GROUP_MEMBERS_API_URL:-/alfresco/api/-default-/public/alfresco/versions/1/groups/{groupId}/members}"

# Skip ACL wait — kept for backward compatibility with existing deployments
SKIP_ACL_WAIT="${SKIP_ACL_WAIT:-true}"

# Max file size in MB — files larger than this are skipped during indexing (0 = no limit)
MAX_FILE_SIZE_MB="${MAX_FILE_SIZE_MB:-0}"
# Convert MB to bytes for the connector config
if [ "$MAX_FILE_SIZE_MB" -gt 0 ] 2>/dev/null; then
  MAX_FILE_SIZE_BYTES=$((MAX_FILE_SIZE_MB * 1024 * 1024))
else
  MAX_FILE_SIZE_BYTES=0
fi

# Cron schedule and recrawl interval are now managed per-sync by the admin app.
# These variables are kept only as defaults for the CMIS repo connection config.

# Derive sanitized names for index naming
# e.g. "alfresco-demo.crestsolution.com" -> "alfresco_demo_crestsolution_com"
REPO_NAME_SANITIZED=$(echo "$CMIS_SERVER" | tr '[:upper:]' '[:lower:]' | sed 's/[^a-z0-9]/_/g')

REPO_CONN_NAME="Alfresco CMIS"

# NOTE: No shared output connection or crawl job is created here.
# Index names and output connections are managed exclusively by the admin app.
# The admin app creates per-KB output connections with names like "KB_{kbId}"
# pointing to indices like "{kbId}_document". This prevents rogue "manifold_*"
# indices from being auto-created.

# =====================================================
# Wait for ManifoldCF API
# =====================================================
echo "=============================================="
echo "ManifoldCF Auto-Configuration"
echo "=============================================="
echo "  Index creation:     Managed by admin app only"
echo "  Skip ACL wait:      ${SKIP_ACL_WAIT}"
echo "  Max file size:      ${MAX_FILE_SIZE_MB} MB (${MAX_FILE_SIZE_BYTES} bytes)"
echo "=============================================="
echo "Waiting for ManifoldCF API at ${MCF_API}..."
elapsed=0
while [ "$elapsed" -lt "$MAX_WAIT" ]; do
  status=$(curl -sf -u "${MCF_API_USER}:${MCF_API_PASS}" -o /dev/null -w "%{http_code}" "${MCF_API}/json/outputconnectors" 2>/dev/null)
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
# 0. OpenSearch Index Templates (ACL-aware)
# =====================================================
echo ""
echo "--- [0/6] OpenSearch Index Templates ---"
OPENSEARCH_BASE=$(echo "$OPENSEARCH_URL" | sed 's:/*$::')

# Delete legacy templates that used "manifold_*" naming
curl -sf -X DELETE "${OPENSEARCH_BASE}/_index_template/manifoldcf-acl" 2>/dev/null
curl -sf -X DELETE "${OPENSEARCH_BASE}/_index_template/manifold-acl" 2>/dev/null

# Template for document/authority/rag indexes (admin-app-managed names)
# Matches admin app naming: {kbId}_document, {kbId}_authority, {kbId}_rag
template_status=$(curl -sf -o /dev/null -w "%{http_code}" "${OPENSEARCH_BASE}/_index_template/knowledge-acl" 2>/dev/null)
if [ "$template_status" = "200" ]; then
  echo "Index template 'knowledge-acl' already exists — skipping."
else
  echo "Creating index template 'knowledge-acl' for admin-app-managed indexes..."
  curl -sf -X PUT "${OPENSEARCH_BASE}/_index_template/knowledge-acl" \
    -H "Content-Type: application/json" \
    -d '{
      "index_patterns": ["*_document", "*_authority", "*_rag"],
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

# =====================================================
# 1. Output Connections — SKIPPED (managed by admin app)
# =====================================================
echo ""
echo "--- [1/6] OpenSearch Output Connection — SKIPPED ---"
echo "Output connections are created by the admin app per knowledge base."
echo "Each KB gets its own output connection (e.g., 'KB_{kbId}') pointing to"
echo "an admin-app-provided index name (e.g., '{kbId}_document')."
echo "No shared 'OpenSearch' output connection with 'manifold_*' names is created."

# =====================================================
# 2. Tika Transformation Connection (embedded)
#    Extracts text from binary docs (PDF, DOCX, PPTX, etc.)
#    before indexing to OpenSearch. No external Tika server needed.
# =====================================================
echo ""
echo "--- [2/8] Tika Transformation Connection ---"
TIKA_CONN_NAME="Tika"
existing=$(curl -sf -u "${MCF_API_USER}:${MCF_API_PASS}" "${MCF_API}/json/transformationconnections/${TIKA_CONN_NAME}" 2>/dev/null)
if echo "$existing" | grep -q '"isnew"'; then
  echo "Transformation connection '${TIKA_CONN_NAME}' already exists — skipping."
else
  echo "Creating transformation connection '${TIKA_CONN_NAME}'..."
  result=$(curl -sf -u "${MCF_API_USER}:${MCF_API_PASS}" -X PUT "${MCF_API}/json/transformationconnections/${TIKA_CONN_NAME}" \
    -H "Content-Type: application/json" \
    -d "{
      \"transformationconnection\": {
        \"name\": \"${TIKA_CONN_NAME}\",
        \"class_name\": \"org.apache.manifoldcf.agents.transformation.tika.TikaExtractor\",
        \"description\": \"Tika content extractor (embedded)\",
        \"max_connections\": \"10\"
      }
    }" 2>&1)
  if [ $? -eq 0 ]; then
    echo "Transformation connection '${TIKA_CONN_NAME}' created: $result"
  else
    echo "WARNING: Failed to create transformation connection: $result"
  fi
fi
check=$(curl -sf -u "${MCF_API_USER}:${MCF_API_PASS}" "${MCF_API}/json/status/transformationconnections/${TIKA_CONN_NAME}" 2>/dev/null)
echo "Status: $check"

# =====================================================
# 4. Alfresco CMIS Repository Connection
# =====================================================
echo ""
echo "--- [4/8] CMIS Repository Connection ---"
REPO_CONN_ENCODED=$(echo "$REPO_CONN_NAME" | sed 's/ /%20/g')
existing=$(curl -sf -u "${MCF_API_USER}:${MCF_API_PASS}" "${MCF_API}/json/repositoryconnections/${REPO_CONN_ENCODED}" 2>/dev/null)
if echo "$existing" | grep -q '"isnew"'; then
  echo "Repository connection '${REPO_CONN_NAME}' already exists — skipping."
else
  echo "Creating repository connection '${REPO_CONN_NAME}' -> ${CMIS_PROTOCOL}://${CMIS_SERVER}:${CMIS_PORT}${CMIS_PATH}..."
  result=$(curl -sf -u "${MCF_API_USER}:${MCF_API_PASS}" -X PUT "${MCF_API}/json/repositoryconnections/${REPO_CONN_ENCODED}" \
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
check=$(curl -sf -u "${MCF_API_USER}:${MCF_API_PASS}" "${MCF_API}/json/status/repositoryconnections/${REPO_CONN_ENCODED}" 2>/dev/null)
echo "Status: $check"

# =====================================================
# 4. Document Crawl Job — SKIPPED (managed by admin app)
# =====================================================
echo ""
echo "--- [4/8] Document Crawl Job — SKIPPED ---"
echo "Crawl jobs are created by the admin app per knowledge base sync."
echo "Each sync creates its own MCF job with a per-KB output connection."
echo "No shared crawl job with 'manifold_*' index names is created."

echo ""
echo "=============================================="
echo "ManifoldCF auto-configuration complete."
echo "  Infrastructure ready (template, Tika, CMIS repo connection)"
echo "  Index creation is managed exclusively by the admin app"
echo "  No 'manifold_*' indices will be auto-created"
echo "=============================================="
