package com.alibaba.cloud.ai.studio.admin.service.impl;

import com.alibaba.cloud.ai.studio.admin.common.PageResult;
import com.alibaba.cloud.ai.studio.admin.dto.*;
import com.alibaba.cloud.ai.studio.admin.dto.request.OverviewQueryRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.ServicesQueryRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.TracesQueryRequest;
import com.alibaba.cloud.ai.studio.admin.repository.TracingRepository;
import com.alibaba.cloud.ai.studio.admin.service.TracingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TracingServiceImpl implements TracingService {

    private final TracingRepository tracingRepository;

    @Override
    public PageResult<TraceSpanDTO> queryTraces(TracesQueryRequest request) {
        log.info("Query Traces list: {}", request);
        return tracingRepository.queryTraces(request);
    }

    @Override
    public TraceDetailDTO getTraceDetail(String traceId) {
        log.info("Query Trace details: {}", traceId);
        return tracingRepository.getTraceDetail(traceId);
    }

    @Override
    public ServicesResponseDTO getServices(ServicesQueryRequest request) {
        log.info("Query service list: {}", request);
        return tracingRepository.getServices(request);
    }

    @Override
    public OverviewStatsDTO getOverview(OverviewQueryRequest request) {
        log.info("Query overview information: {}", request);
        return tracingRepository.getOverview(request);
    }
}
