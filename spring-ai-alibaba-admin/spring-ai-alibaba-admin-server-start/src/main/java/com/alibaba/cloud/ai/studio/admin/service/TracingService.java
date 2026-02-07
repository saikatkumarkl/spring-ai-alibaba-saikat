package com.alibaba.cloud.ai.studio.admin.service;

import com.alibaba.cloud.ai.studio.admin.common.PageResult;
import com.alibaba.cloud.ai.studio.admin.dto.*;
import com.alibaba.cloud.ai.studio.admin.dto.request.OverviewQueryRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.ServicesQueryRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.TracesQueryRequest;

public interface TracingService {

    /**
     * Paginated query tracking list
     */
    PageResult<TraceSpanDTO> queryTraces(TracesQueryRequest request);

    /**
     * Get tracking details based on TraceId
     */
    TraceDetailDTO getTraceDetail(String traceId);

    /**
     * Get service list
     */
    ServicesResponseDTO getServices(ServicesQueryRequest request);

    /**
     * Get overview information
     */
    OverviewStatsDTO getOverview(OverviewQueryRequest request);
}
