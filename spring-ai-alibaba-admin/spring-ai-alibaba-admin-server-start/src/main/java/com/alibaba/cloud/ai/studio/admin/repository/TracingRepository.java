package com.alibaba.cloud.ai.studio.admin.repository;

import com.alibaba.cloud.ai.studio.admin.common.PageResult;
import com.alibaba.cloud.ai.studio.admin.dto.*;
import com.alibaba.cloud.ai.studio.admin.dto.request.OverviewQueryRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.ServicesQueryRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.TracesQueryRequest;

public interface TracingRepository {

    /**
     * Query Traces list
     */
    PageResult<TraceSpanDTO> queryTraces(TracesQueryRequest request);

    /**
     * Query Trace details based on TraceId
     */
    TraceDetailDTO getTraceDetail(String traceId);

    /**
     * Query service list
     */
    ServicesResponseDTO getServices(ServicesQueryRequest request);

    /**
     * Query overview statistics
     */
    OverviewStatsDTO getOverview(OverviewQueryRequest request);

    /**
     * Save Span data in batches
     */
    void saveSpans(java.util.List<TraceSpanDTO> spans);
}
