package com.alibaba.cloud.ai.studio.admin.controller;

import com.alibaba.cloud.ai.studio.admin.common.PageResult;
import com.alibaba.cloud.ai.studio.runtime.domain.Result;
import com.alibaba.cloud.ai.studio.admin.dto.*;
import com.alibaba.cloud.ai.studio.admin.dto.request.OverviewQueryRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.ServicesQueryRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.TracesQueryRequest;
import com.alibaba.cloud.ai.studio.admin.service.TracingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/observability")
@RequiredArgsConstructor
public class ObservabilityController {

    private final TracingService tracingService;

    /**
     * Get Trace list
     */
    @GetMapping("/traces")
    public Result<PageResult<TraceSpanDTO>> getTraces(@Valid TracesQueryRequest request) {
        log.info("Get Trace list request: {}", request);
        PageResult<TraceSpanDTO> result = tracingService.queryTraces(request);
        return Result.success(result);
    }

    /**
     * Get individual Trace details
     */
    @GetMapping("/traces/{traceId}")
    public Result<TraceDetailDTO> getTraceDetail(@PathVariable String traceId) {
        log.info("Get Trace details request: {}", traceId);
        TraceDetailDTO result = tracingService.getTraceDetail(traceId);
        return Result.success(result);
    }

    /**
     * Get service list
     */
    @GetMapping("/services")
    public Result<ServicesResponseDTO> getServices(@Valid ServicesQueryRequest request) {
        log.info("Get service list request: {}", request);
        ServicesResponseDTO result = tracingService.getServices(request);
        return Result.success(result);
    }

    /**
     * Get overview information
     */
    @GetMapping("/overview")
    public Result<OverviewStatsDTO> getOverview(@Valid OverviewQueryRequest request) {
        log.info("Request for overview information: {}", request);
        OverviewStatsDTO result = tracingService.getOverview(request);
        return Result.success(result);
    }
}
