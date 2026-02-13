package com.alibaba.cloud.ai.studio.admin.repository.impl;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class OpenSearchClientWrapper {

    private final OpenSearchClient openSearchClient;

    /**
     * Execute search query
     */
    public SearchResponse<Map> search(String index, SearchRequest searchRequest) {
        try {
            return openSearchClient.search(searchRequest, Map.class);
        } catch (IOException e) {
            throw new RuntimeException("Search request execution failed", e);
        }
    }

    /**
     * Batch index documents
     */
    public void bulkIndex(String index, List<Map<String, Object>> documents) {
        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

            for (Map<String, Object> doc : documents) {
                bulkBuilder.operations(op -> op
                    .index(idx -> idx
                        .index(index)
                        .document(doc)
                    )
                );
            }

            BulkResponse result = openSearchClient.bulk(bulkBuilder.build());

            if (result.errors()) {
                log.error("Bulk indexing partially failed: {}", result.items());
            } else {
                log.info("Bulk indexing successful: {} documents", documents.size());
            }

        } catch (IOException e) {
            throw new RuntimeException("Bulk indexing failed", e);
        }
    }

    /**
     * Check if the index exists
     */
    public boolean indexExists(String indexName) {
        try {
            ExistsRequest existsRequest = ExistsRequest.of(e -> e.index(indexName));
            return openSearchClient.indices().exists(existsRequest).value();
        } catch (IOException e) {
            log.error("Failed to check if index exists: {}", indexName, e);
            return false;
        }
    }

    /**
     * Convert SearchResponse to Map list
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> extractHits(SearchResponse<Map> response) {
        return response.hits().hits().stream()
            .map(hit -> (Map<String, Object>) hit.source())
            .collect(Collectors.toList());
    }

    /**
     * Get the total number of hits
     */
    public long getTotalHits(SearchResponse<Map> response) {
        return response.hits().total().value();
    }
}
