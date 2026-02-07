package com.alibaba.cloud.ai.studio.admin.repository;

import com.alibaba.cloud.ai.studio.admin.entity.ModelConfigDO;

import java.util.List;

/**
 * Model configuration warehousing interface (file-driven implementation).
 */
public interface ModelConfigRepository {

    ModelConfigDO findById(Long id);

    boolean existsById(Long id);

    List<ModelConfigDO> list(String name, String provider, Integer status, int offset, int limit);

    int count(String name, String provider, Integer status);

    List<ModelConfigDO> listEnabled();
}


