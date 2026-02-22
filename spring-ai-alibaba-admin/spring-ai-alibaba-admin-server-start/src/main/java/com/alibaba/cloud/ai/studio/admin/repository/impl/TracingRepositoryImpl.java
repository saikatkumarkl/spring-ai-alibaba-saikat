package com.alibaba.cloud.ai.studio.admin.repository.impl;

import com.alibaba.cloud.ai.studio.admin.common.PageResult;
import com.alibaba.cloud.ai.studio.admin.dto.*;
import com.alibaba.cloud.ai.studio.admin.dto.request.OverviewQueryRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.ServicesQueryRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.TracesQueryRequest;
import com.alibaba.cloud.ai.studio.admin.repository.TracingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.CardinalityAggregate;
import org.opensearch.client.opensearch._types.aggregations.ValueCountAggregate;
import org.opensearch.client.opensearch._types.aggregations.SumAggregate;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Slf4j
public class TracingRepositoryImpl implements TracingRepository {

    private static final String TRACES_INDEX = "loongsuite_traces";
    
    private final OpenSearchClientWrapper elasticsearchClient;
    private final TracingQueryBuilder queryBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public PageResult<TraceSpanDTO> queryTraces(TracesQueryRequest request) {
        log.info("Query Traces list: {}", request);
        
        SearchRequest searchRequest = queryBuilder.buildTracesQuery(request);
        SearchResponse<Map> response = elasticsearchClient.search(TRACES_INDEX, searchRequest);
        
        List<TraceSpanDTO> spans = elasticsearchClient.extractHits(response).stream()
            .map(this::convertToTraceSpanDTO)
            .collect(Collectors.toList());
        
        return buildPageResult(response, spans, request);
    }

    @Override
    public TraceDetailDTO getTraceDetail(String traceId) {
        log.info("Query Trace details: {}", traceId);
        
        SearchRequest searchRequest = queryBuilder.buildTraceDetailQuery(traceId);
        SearchResponse<Map> response = elasticsearchClient.search(TRACES_INDEX, searchRequest);
        
        List<TraceSpanDTO> spans = elasticsearchClient.extractHits(response).stream()
            .map(this::convertToTraceSpanDTO)
            .collect(Collectors.toList());
        
        return TraceDetailDTO.builder().records(spans).build();
    }

    @Override
    public ServicesResponseDTO getServices(ServicesQueryRequest request) {
        log.info("Query service list: {}", request);
        
        SearchRequest searchRequest = queryBuilder.buildServicesQuery(request);
        SearchResponse<Map> response = elasticsearchClient.search(TRACES_INDEX, searchRequest);
        
        List<ServiceInfoDTO> services = new ArrayList<>();
        
        if (response.aggregations() != null) {
            Aggregate servicesAgg = response.aggregations().get("services");
            if (servicesAgg != null && servicesAgg.isSterms()) {
                //Aggregation using String Terms
                var termsAgg = servicesAgg.sterms();
                for (var bucket : termsAgg.buckets().array()) {
                    String serviceName = bucket.key();
                    List<String> operations = new ArrayList<>();
                    
                    Aggregate operationsAgg = bucket.aggregations().get("operations");
                    if (operationsAgg != null && operationsAgg.isSterms()) {
                        var opTermsAgg = operationsAgg.sterms();
                        operations = opTermsAgg.buckets().array().stream()
                            .map(opBucket -> opBucket.key())
                            .collect(Collectors.toList());
                    }
                    
                    services.add(ServiceInfoDTO.builder()
                        .name(serviceName)
                        .operations(operations)
                        .build());
                }
            }
        }
        
        return ServicesResponseDTO.builder().services(services).build();
    }

    @Override
    public OverviewStatsDTO getOverview(OverviewQueryRequest request) {
        log.info("Query overview statistics: {}", request);
        
        SearchRequest searchRequest = queryBuilder.buildOverviewQuery(request);
        SearchResponse<Map> response = elasticsearchClient.search(TRACES_INDEX, searchRequest);
        
        Map<String, Aggregate> aggregations = response.aggregations();
        
        //Construct statistical results - all queries use detail mode
        OverviewStatsDTO.StatDetail operationCount = buildOperationCountStats(aggregations, true);
        OverviewStatsDTO.StatDetail modelCount = buildModelCountStats(aggregations, true);
        OverviewStatsDTO.StatDetail usageTokens = buildUsageTokensStats(aggregations, true);
        
        return OverviewStatsDTO.builder()
            .operationCount(operationCount)
            .modelCount(modelCount)
            .usageTokens(usageTokens)
            .build();
    }

    @Override
    public void saveSpans(List<TraceSpanDTO> spans) {
        log.info("Save Span data in batches: {} items", spans.size());
        
        List<Map<String, Object>> documents = spans.stream()
            .map(this::convertToElasticsearchDoc)
            .collect(Collectors.toList());
        
        elasticsearchClient.bulkIndex(TRACES_INDEX, documents);
    }

    /**
     * Convert to TraceSpanDTO
     */
    @SuppressWarnings("unchecked")
    private TraceSpanDTO convertToTraceSpanDTO(Map<String, Object> source) {
        //Get metadata object
        Map<String, Object> metadata = (Map<String, Object>) source.get("metadata");
        if (metadata == null) {
            metadata = new HashMap<>();
        }
        
        //Get timestamp and convert to ISO8601 format
        Long startTimeUs = getLong(metadata, "start");
        String startTimeStr = startTimeUs != null ? convertMicrosecondsToISO8601(startTimeUs) : null;
        
        Long endTimeUs = getLong(metadata, "end");
        String endTimeStr = endTimeUs != null ? convertMicrosecondsToISO8601(endTimeUs) : null;

        Long durationUs = getLong(metadata, "duration");
        
        return TraceSpanDTO.builder()
            .traceId(getString(metadata, "traceID"))
            .spanId(getString(metadata, "spanID"))
            .parentSpanId(getString(metadata, "parentSpanID"))
            .durationNs(durationUs != null ? durationUs * 1000 : null) //microsecond to nanosecond
            .spanKind(convertSpanKind(getString(metadata, "kind")))
            .service(getString(metadata, "service"))
            .spanName(getString(metadata, "name"))
            .startTime(startTimeStr)
            .endTime(endTimeStr)
            .status(convertStatusCode(getString(metadata, "statusCode")))
            //FIXME: Temporarily set to 0, and can be calculated as needed later.
            .errorCount(0)
            .attributes((Map<String, Object>) source.get("attributes"))
            .resources((Map<String, Object>) source.get("resources"))
            .spanLinks(convertSpanLinks((List<Map<String, Object>>) source.get("spanLinks")))
            .spanEvents(convertSpanEvents((List<Map<String, Object>>) source.get("spanEvents")))
            .build();
    }

    /**
     * Convert to Elasticsearch document
     */
    private Map<String, Object> convertToElasticsearchDoc(TraceSpanDTO span) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("traceId", span.getTraceId());
        doc.put("spanId", span.getSpanId());
        doc.put("parentSpanId", span.getParentSpanId());
        doc.put("operationName", span.getSpanName());
        doc.put("startTime", span.getStartTime());
        doc.put("endTime", span.getEndTime());
        doc.put("duration", span.getDurationNs());
        doc.put("status", span.getStatus());
        doc.put("spanKind", span.getSpanKind());
        doc.put("serviceName", span.getService());
        doc.put("errorCount", span.getErrorCount());
        doc.put("attributes", span.getAttributes());
        doc.put("resources", span.getResources());
        doc.put("links", span.getSpanLinks());
        doc.put("events", span.getSpanEvents());
        return doc;
    }

    /**
     * Build paginated results
     */
    private PageResult<TraceSpanDTO> buildPageResult(SearchResponse<Map> response, 
                                                   List<TraceSpanDTO> spans, 
                                                   TracesQueryRequest request) {
        long totalCount = elasticsearchClient.getTotalHits(response);
        long totalPage = (totalCount + request.getPageSize() - 1) / request.getPageSize();
        
        PageResult<TraceSpanDTO> result = new PageResult<>();
        result.setTotalCount(totalCount);
        result.setTotalPage(totalPage);
        result.setPageNumber((long) request.getPageNumber());
        result.setPageSize((long) request.getPageSize());
        result.setPageItems(spans);
        
        return result;
    }

    /**
     * Convert microsecond timestamp to ISO8601 format
     */
    private String convertMicrosecondsToISO8601(Long microseconds) {
        if (microseconds == null) {
            return null;
        }
        //Microseconds to milliseconds
        long milliseconds = microseconds / 1000;
        return java.time.Instant.ofEpochMilli(milliseconds)
            .atZone(java.time.ZoneOffset.UTC)
            .format(java.time.format.DateTimeFormatter.ISO_INSTANT);
    }

    /**
     * Convert span type
     */
    private String convertSpanKind(String kind) {
        if (kind == null) {
            return "SPAN_KIND_INTERNAL";
        }
        switch (kind.toLowerCase()) {
            case "client":
                return "SPAN_KIND_CLIENT";
            case "server":
                return "SPAN_KIND_SERVER";
            case "producer":
                return "SPAN_KIND_PRODUCER";
            case "consumer":
                return "SPAN_KIND_CONSUMER";
            case "internal":
            default:
                return "SPAN_KIND_INTERNAL";
        }
    }

    /**
     * Conversion status code
     */
    private String convertStatusCode(String statusCode) {
        if (statusCode == null) {
            return "UNSET";
        }
        switch (statusCode.toUpperCase()) {
            case "OK":
                return "OK";
            case "ERROR":
                return "ERROR";
            case "UNSET":
            default:
                return "UNSET";
        }
    }



    /**
     * Build operation statistics (operation.count)
     */
    private OverviewStatsDTO.StatDetail buildOperationCountStats(Map<String, Aggregate> aggregations, 
                                                               Boolean detail) {
        Long total = 0L;
        List<OverviewStatsDTO.StatItem> detailList = new ArrayList<>();
        
        if (aggregations != null) {
            Aggregate operationCountAgg = aggregations.get("operation_count");
            if (operationCountAgg != null && operationCountAgg.isSterms()) {
                var termsAgg = operationCountAgg.sterms();
                
                //Calculate total number of operations
                for (var bucket : termsAgg.buckets().array()) {
                    total += bucket.docCount();
                }
                
                if (detail) {
                    detailList = termsAgg.buckets().array().stream()
                        .map(bucket -> OverviewStatsDTO.StatItem.builder()
                            .operationName(bucket.key())
                            .total(bucket.docCount())
                            .build())
                        .collect(Collectors.toList());
                }
            }
        }
        
        return OverviewStatsDTO.StatDetail.builder()
            .total(total)
            .detail(detailList)
            .build();
    }

    /**
     * Build model statistics (model.count)
     */
    private OverviewStatsDTO.StatDetail buildModelCountStats(Map<String, Aggregate> aggregations, 
                                                           Boolean detail) {
        Long total = 0L;
        List<OverviewStatsDTO.StatItem> detailList = new ArrayList<>();
        
        if (aggregations != null) {
            Aggregate modelCountAgg = aggregations.get("model_count");
            if (modelCountAgg != null && modelCountAgg.isSterms()) {
                var termsAgg = modelCountAgg.sterms();
                
                //Calculate the total number of models
                for (var bucket : termsAgg.buckets().array()) {
                    total += bucket.docCount();
                }
                
                if (detail) {
                    detailList = termsAgg.buckets().array().stream()
                        .map(bucket -> OverviewStatsDTO.StatItem.builder()
                            .modelName(bucket.key())
                            .total(bucket.docCount())
                            .build())
                        .collect(Collectors.toList());
                }
            }
        }
        
        return OverviewStatsDTO.StatDetail.builder()
            .total(total)
            .detail(detailList)
            .build();
    }

    /**
     * Build Token usage statistics
     */
    private OverviewStatsDTO.StatDetail buildUsageTokensStats(Map<String, Aggregate> aggregations, 
                                                            Boolean detail) {
        Long total = 0L;
        List<OverviewStatsDTO.StatItem> detailList = new ArrayList<>();
        
        if (aggregations != null) {
            Aggregate usageTokensAgg = aggregations.get("total_usage_tokens");
            if (usageTokensAgg != null && usageTokensAgg.isSterms()) {
                var termsAgg = usageTokensAgg.sterms();
                
                //Calculate the total number of tokens
                for (var bucket : termsAgg.buckets().array()) {
                    Aggregate totalTokensAgg = bucket.aggregations().get("total_tokens");
                    if (totalTokensAgg != null && totalTokensAgg.isSum()) {
                        SumAggregate sum = totalTokensAgg.sum();
                        Double value = sum.value();
						total += value.longValue();
					}
                }
                
                if (detail) {
                    detailList = termsAgg.buckets().array().stream()
                        .map(bucket -> {
                            String modelName = bucket.key();
                            Long tokens = 0L;
                            Aggregate totalTokensAgg = bucket.aggregations().get("total_tokens");
                            if (totalTokensAgg != null && totalTokensAgg.isSum()) {
                                SumAggregate sum = totalTokensAgg.sum();
                                Double value = sum.value();
								tokens = value.longValue();
							}
                            return OverviewStatsDTO.StatItem.builder()
                                .modelName(modelName)
                                .total(tokens)
                                .build();
                        })
                        .collect(Collectors.toList());
                }
            }
        }
        
        return OverviewStatsDTO.StatDetail.builder()
            .total(total)
            .detail(detailList)
            .build();
    }

    //Helper methods
    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                log.warn("failed to parse Long type: {}", value, e);
                return null;
            }
        }
        log.warn("failed to parse Long type: {}", value);
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<SpanLinkDTO> convertSpanLinks(List<Map<String, Object>> links) {
        if (links == null) return new ArrayList<>();
        
        return links.stream()
            .map(link -> SpanLinkDTO.builder()
                .traceId(getString(link, "traceID"))
                .spanId(getString(link, "spanID"))
                .attributes((Map<String, Object>) link.get("attribute"))
                .build())
            .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private List<SpanEventDTO> convertSpanEvents(List<Map<String, Object>> events) {
        if (events == null) return new ArrayList<>();
        
        return events.stream()
            .map(event -> SpanEventDTO.builder()
                .name(getString(event, "name"))
                .time(convertMicrosecondsToISO8601(getLong(event, "time")))
                .attributes((Map<String, Object>) event.get("attribute"))
                .build())
            .collect(Collectors.toList());
    }
}
