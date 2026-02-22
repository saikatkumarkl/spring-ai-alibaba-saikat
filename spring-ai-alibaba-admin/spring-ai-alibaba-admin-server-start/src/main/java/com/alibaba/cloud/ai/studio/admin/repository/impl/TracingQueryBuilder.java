package com.alibaba.cloud.ai.studio.admin.repository.impl;

import com.alibaba.cloud.ai.studio.admin.dto.request.OverviewQueryRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.ServicesQueryRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.TracesQueryRequest;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.json.JsonData;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.HashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class TracingQueryBuilder {

    private static final String TRACES_INDEX = "loongsuite_traces";
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Build Traces query request
     */
    public SearchRequest buildTracesQuery(TracesQueryRequest request) {
        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();

        //Time range filtering - using microsecond timestamps
        if (StringUtils.hasText(request.getStartTime()) && StringUtils.hasText(request.getEndTime())) {
            try {
                //Convert ISO8601 time to microsecond timestamp
                Long startTimeMicros = convertISO8601ToMicroseconds(request.getStartTime());
                Long endTimeMicros = convertISO8601ToMicroseconds(request.getEndTime());
                
                if (startTimeMicros != null && endTimeMicros != null) {
                    final Long startMicros = startTimeMicros;
                    final Long endMicros = endTimeMicros;
                    Query timeRangeQuery = Query.of(q -> q
                        .range(r -> r
                            .field("metadata.start")
                            .gte(JsonData.of(startMicros))
                            .lte(JsonData.of(endMicros)))
                    );
                    boolQueryBuilder.filter(timeRangeQuery);
                    log.debug("Add time range filter: {} - {} (microseconds: {} - {})", 
                        request.getStartTime(), request.getEndTime(), startTimeMicros, endTimeMicros);
                }
            } catch (Exception e) {
                log.error("Building time range query failed", e);
            }
        }

        //Service name filtering
        if (StringUtils.hasText(request.getServiceName())) {
            Query serviceQuery = Query.of(q -> q.term(t -> t
                .field("metadata.service")
                .value(FieldValue.of(request.getServiceName()))
            ));
            boolQueryBuilder.filter(serviceQuery);
        }

        //Trace ID filtering
        if (StringUtils.hasText(request.getTraceId())) {
            Query traceIdQuery = Query.of(q -> q.term(t -> t
                .field("metadata.traceID")
                .value(FieldValue.of(request.getTraceId()))
            ));
            boolQueryBuilder.filter(traceIdQuery);
        }

        //Span name filtering
        if (StringUtils.hasText(request.getSpanName())) {
            Query spanNameQuery = Query.of(q -> q.term(t -> t
                .field("metadata.name")
                .value(FieldValue.of(request.getSpanName()))
            ));
            boolQueryBuilder.filter(spanNameQuery);
        }

        //Attribute filtering
        if (StringUtils.hasText(request.getAttributes())) {
            addAttributesFilter(boolQueryBuilder, request.getAttributes());
        }

        //Build a search request
        SearchRequest.Builder searchBuilder = new SearchRequest.Builder()
            .index(TRACES_INDEX)
            .query(Query.of(q -> q.bool(boolQueryBuilder.build())))
            .from((request.getPageNumber() - 1) * request.getPageSize())
            .size(request.getPageSize())
            .sort(s -> s.field(f -> f.field("metadata.start").order(SortOrder.Desc)));

        return searchBuilder.build();
    }

    /**
     * Construct Trace details query request
     */
    public SearchRequest buildTraceDetailQuery(String traceId) {
        //Bugfix: Query using metadata.traceID field
        Query traceQuery = Query.of(q -> q.term(t -> t
            .field("metadata.traceID")
            .value(FieldValue.of(traceId))
        ));
        
        return SearchRequest.of(s -> s
            .index(TRACES_INDEX)
            .query(traceQuery)
            .size(1000)
            .sort(sort -> sort.field(f -> f.field("metadata.start").order(SortOrder.Asc)))
        );
    }

    /**
     * Build a service query request
     */
    public SearchRequest buildServicesQuery(ServicesQueryRequest request) {
        //Build an aggregate
        Map<String, Aggregation> aggregations = new HashMap<>();
        aggregations.put("services", Aggregation.of(a -> a
            .terms(t -> t.field("metadata.service").size(1000))
            .aggregations("operations", Aggregation.of(sub -> sub
                .terms(subTerms -> subTerms.field("metadata.name").size(1000))
            ))
        ));

        SearchRequest.Builder searchBuilder = new SearchRequest.Builder()
            .index(TRACES_INDEX)
            .size(0)
            .aggregations(aggregations);

        //Time range filtering - using microsecond timestamps
        if (StringUtils.hasText(request.getStartTime()) && StringUtils.hasText(request.getEndTime())) {
            try {
                //Convert ISO8601 time to microsecond timestamp
                Long startTimeMicros = convertISO8601ToMicroseconds(request.getStartTime());
                Long endTimeMicros = convertISO8601ToMicroseconds(request.getEndTime());
                
                if (startTimeMicros != null && endTimeMicros != null) {
                    final Long startMicros = startTimeMicros;
                    final Long endMicros = endTimeMicros;
                    Query timeRangeQuery = Query.of(q -> q
                        .range(r -> r
                            .field("metadata.start")
                            .gte(JsonData.of(startMicros))
                            .lte(JsonData.of(endMicros)))
                    );
                    
                    searchBuilder.query(timeRangeQuery);
                    log.debug("Add service query time range filter: {} - {} (microseconds: {} - {})", 
                        request.getStartTime(), request.getEndTime(), startTimeMicros, endTimeMicros);
                }
            } catch (Exception e) {
                log.error("Build service query time range failed", e);
            }
        }

        return searchBuilder.build();
    }

    /**
     * Build an overview query request
     */
    public SearchRequest buildOverviewQuery(OverviewQueryRequest request) {
        Map<String, Aggregation> aggregations = new HashMap<>();

        //Correct aggregate fields according to API documentation
        //1. Operation type statistics
        aggregations.put("operation_count", Aggregation.of(a -> a
            .terms(t -> t.field("attributes.gen_ai.operation.name").size(1000).missing(FieldValue.of("generic")))
        ));

        //2. Model Statistics
        aggregations.put("model_count", Aggregation.of(a -> a
            .terms(t -> t.field("attributes.gen_ai.request.model").size(1000))
        ));

        //3. Token usage statistics - grouped by model
        aggregations.put("total_usage_tokens", Aggregation.of(a -> a
            .terms(t -> t.field("attributes.gen_ai.request.model").size(1000))
            .aggregations("total_tokens", Aggregation.of(sub -> sub
                .sum(s -> s.field("usage.total_tokens"))
            ))
            .aggregations("input_tokens", Aggregation.of(sub -> sub
                .sum(s -> s.field("usage.input_tokens"))
            ))
            .aggregations("output_tokens", Aggregation.of(sub -> sub
                .sum(s -> s.field("usage.output_tokens"))
            ))
        ));

        SearchRequest.Builder searchBuilder = new SearchRequest.Builder()
            .index(TRACES_INDEX)
            .size(0)
            .aggregations(aggregations);

        //Time range filtering - using microsecond timestamps
        if (StringUtils.hasText(request.getStartTime()) && StringUtils.hasText(request.getEndTime())) {
            try {
                //Convert ISO8601 time to microsecond timestamp
                Long startTimeMicros = convertISO8601ToMicroseconds(request.getStartTime());
                Long endTimeMicros = convertISO8601ToMicroseconds(request.getEndTime());
                
                if (startTimeMicros != null && endTimeMicros != null) {
                    final Long startMicros = startTimeMicros;
                    final Long endMicros = endTimeMicros;
                    Query timeRangeQuery = Query.of(q -> q
                        .range(r -> r
                            .field("metadata.start")
                            .gte(JsonData.of(startMicros))
                            .lte(JsonData.of(endMicros)))
                    );
                    
                    searchBuilder.query(timeRangeQuery);
                    log.debug("Add overview query time range filter: {} - {} (microseconds: {} - {})", 
                        request.getStartTime(), request.getEndTime(), startTimeMicros, endTimeMicros);
                }
            } catch (Exception e) {
                log.error("Building overview query time range failed", e);
            }
        }

        return searchBuilder.build();
    }

    /**
     * Add attribute filter
     */
    private void addAttributesFilter(BoolQuery.Builder boolQuery, String attributesJson) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> attributesMap = objectMapper.readValue(attributesJson, Map.class);
            
            for (Map.Entry<String, Object> entry : attributesMap.entrySet()) {
                String field = "attributes." + entry.getKey();
                Query attrQuery = Query.of(q -> q.term(t -> t
                    .field(field)
                    .value(FieldValue.of(String.valueOf(entry.getValue())))
                ));
                boolQuery.filter(attrQuery);
            }
        } catch (Exception e) {
            log.warn("Failed to parse attribute filter: {}", attributesJson, e);
        }
    }



    /**
     * Convert ISO8601 time string to microsecond timestamp
     */
    private Long convertISO8601ToMicroseconds(String iso8601Time) {
        try {
            java.time.Instant instant = java.time.Instant.parse(iso8601Time);
            //Convert to microseconds
            return instant.toEpochMilli() * 1000;
        } catch (Exception e) {
            log.error("Time conversion failed: {}", iso8601Time, e);
            return null;
        }
    }
}
