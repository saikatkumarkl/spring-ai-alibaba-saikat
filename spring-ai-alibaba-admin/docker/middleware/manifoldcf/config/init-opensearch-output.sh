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
#   1. OpenSearch output connection exists
#   2. Alfresco CMIS repository connection exists
#   3. Crawl job (CMIS → OpenSearch) exists

MCF_API="${MCF_API_URL:-http://localhost:8345/mcf-api-service}"
MAX_WAIT=120
INTERVAL=5

# --- Environment defaults ---
OPENSEARCH_URL="${OPENSEARCH_URL:-http://opensearch:9200/}"
OPENSEARCH_INDEX="${OPENSEARCH_INDEX:-manifoldcf}"

CMIS_USERNAME="${CMIS_USERNAME:-CHANGE_ME_USERNAME}"
CMIS_PASSWORD="${CMIS_PASSWORD:-CHANGE_ME_PASSWORD}"
CMIS_PROTOCOL="${CMIS_PROTOCOL:-https}"
CMIS_SERVER="${CMIS_SERVER:-alfresco-demo.crestsolution.com}"
CMIS_PORT="${CMIS_PORT:-8080}"
CMIS_PATH="${CMIS_PATH:-/alfresco/api/-default-/cmis/versions/1.1/atom}"
CMIS_BINDING="${CMIS_BINDING:-atom}"
CMIS_REPOSITORY_ID="${CMIS_REPOSITORY_ID:--default-}"

OUTPUT_CONN_NAME="OpenSearch"
REPO_CONN_NAME="Alfresco CMIS"
JOB_DESCRIPTION="Alfresco CMIS Crawl to OpenSearch"

# =====================================================
# Wait for ManifoldCF API
# =====================================================
echo "=============================================="
echo "ManifoldCF Auto-Configuration"
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
# 1. OpenSearch Output Connection
# =====================================================
echo ""
echo "--- [1/3] OpenSearch Output Connection ---"
existing=$(curl -sf "${MCF_API}/json/outputconnections/${OUTPUT_CONN_NAME}" 2>/dev/null)
if echo "$existing" | grep -q '"isnew"'; then
  echo "Output connection '${OUTPUT_CONN_NAME}' already exists — skipping."
else
  echo "Creating output connection '${OUTPUT_CONN_NAME}' -> ${OPENSEARCH_URL}..."
  result=$(curl -sf -X PUT "${MCF_API}/json/outputconnections/${OUTPUT_CONN_NAME}" \
    -H "Content-Type: application/json" \
    -d "{
      \"outputconnection\": {
        \"name\": \"${OUTPUT_CONN_NAME}\",
        \"class_name\": \"org.apache.manifoldcf.agents.output.elasticsearch.ElasticSearchConnector\",
        \"description\": \"Default OpenSearch output via ElasticSearch connector\",
        \"max_connections\": \"10\",
        \"configuration\": {
          \"_PARAMETER_\": [
            {\"_attribute_name\": \"SERVERLOCATION\", \"_value_\": \"${OPENSEARCH_URL}\"},
            {\"_attribute_name\": \"INDEXNAME\", \"_value_\": \"${OPENSEARCH_INDEX}\"},
            {\"_attribute_name\": \"INDEXTYPE\", \"_value_\": \"_doc\"}
          ]
        }
      }
    }" 2>&1)
  if [ $? -eq 0 ]; then
    echo "Output connection '${OUTPUT_CONN_NAME}' created successfully."
  else
    echo "WARNING: Failed to create output connection: $result"
  fi
fi
check=$(curl -sf "${MCF_API}/json/status/outputconnections/${OUTPUT_CONN_NAME}" 2>/dev/null)
echo "Status: $check"

# =====================================================
# 2. Alfresco CMIS Repository Connection
# =====================================================
echo ""
echo "--- [2/3] Alfresco CMIS Repository Connection ---"
# URL-encode spaces in connection name
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
        \"description\": \"Alfresco CMIS Repository via AtomPub binding\",
        \"max_connections\": \"10\",
        \"configuration\": {
          \"_PARAMETER_\": [
            {\"_attribute_name\": \"binding\",      \"_value_\": \"${CMIS_BINDING}\"},
            {\"_attribute_name\": \"username\",     \"_value_\": \"${CMIS_USERNAME}\"},
            {\"_attribute_name\": \"password\",     \"_value_\": \"${CMIS_PASSWORD}\"},
            {\"_attribute_name\": \"protocol\",     \"_value_\": \"${CMIS_PROTOCOL}\"},
            {\"_attribute_name\": \"server\",       \"_value_\": \"${CMIS_SERVER}\"},
            {\"_attribute_name\": \"port\",         \"_value_\": \"${CMIS_PORT}\"},
            {\"_attribute_name\": \"path\",         \"_value_\": \"${CMIS_PATH}\"},
            {\"_attribute_name\": \"repositoryId\", \"_value_\": \"${CMIS_REPOSITORY_ID}\"}
          ]
        }
      }
    }" 2>&1)
  if [ $? -eq 0 ]; then
    echo "Repository connection '${REPO_CONN_NAME}' created successfully."
  else
    echo "WARNING: Failed to create repository connection: $result"
  fi
fi
check=$(curl -sf "${MCF_API}/json/status/repositoryconnections/${REPO_CONN_ENCODED}" 2>/dev/null)
echo "Status: $check"

# =====================================================
# 3. Crawl Job (CMIS → OpenSearch)
# =====================================================
echo ""
echo "--- [3/3] Crawl Job ---"
# Check if a job with this description already exists
existing_jobs=$(curl -sf "${MCF_API}/json/jobs" 2>/dev/null)
if echo "$existing_jobs" | grep -q "\"${JOB_DESCRIPTION}\""; then
  echo "Job '${JOB_DESCRIPTION}' already exists — skipping."
else
  echo "Creating job '${JOB_DESCRIPTION}'..."
  result=$(curl -sf -X POST "${MCF_API}/json/jobs" \
    -H "Content-Type: application/json" \
    -d "{
      \"job\": {
        \"_children_\": [
          {\"_type_\": \"description\",           \"_value_\": \"${JOB_DESCRIPTION}\"},
          {\"_type_\": \"repository_connection\", \"_value_\": \"${REPO_CONN_NAME}\"},
          {
            \"_type_\": \"pipelinestage\",
            \"_children_\": [
              {\"_type_\": \"stage_id\",             \"_value_\": \"0\"},
              {\"_type_\": \"stage_isoutput\",       \"_value_\": \"true\"},
              {\"_type_\": \"stage_connectionname\", \"_value_\": \"${OUTPUT_CONN_NAME}\"}
            ]
          },
          {\"_type_\": \"run_mode\",      \"_value_\": \"scan once\"},
          {\"_type_\": \"start_mode\",    \"_value_\": \"manual\"},
          {\"_type_\": \"hopcount_mode\", \"_value_\": \"accurate\"},
          {\"_type_\": \"priority\",      \"_value_\": \"5\"},
          {
            \"_type_\": \"document_specification\",
            \"_children_\": [
              {
                \"_type_\": \"startpoint\",
                \"_attribute_cmisQuery\": \"SELECT * FROM cmis:document\",
                \"_value_\": \"\"
              }
            ]
          }
        ]
      }
    }" 2>&1)
  if [ $? -eq 0 ]; then
    echo "Job created: $result"
  else
    echo "WARNING: Failed to create job: $result"
  fi
fi

echo ""
echo "=============================================="
echo "ManifoldCF auto-configuration complete."
echo "=============================================="
