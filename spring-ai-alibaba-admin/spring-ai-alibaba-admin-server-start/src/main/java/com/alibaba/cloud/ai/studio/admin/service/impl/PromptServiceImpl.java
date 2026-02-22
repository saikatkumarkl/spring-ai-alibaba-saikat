package com.alibaba.cloud.ai.studio.admin.service.impl;

import com.alibaba.cloud.ai.studio.admin.common.PageResult;
import com.alibaba.cloud.ai.studio.admin.dto.Prompt;
import com.alibaba.cloud.ai.studio.admin.dto.request.PromptCreateRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.PromptListRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.PromptUpdateRequest;
import com.alibaba.cloud.ai.studio.admin.entity.PromptDO;
import com.alibaba.cloud.ai.studio.admin.exception.StudioException;
import com.alibaba.cloud.ai.studio.admin.mapper.PromptMapper;
import com.alibaba.cloud.ai.studio.admin.mapper.PromptVersionMapper;
import com.alibaba.cloud.ai.studio.admin.service.PromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptServiceImpl implements PromptService {
    
    private final PromptMapper promptMapper;
    
    private final PromptVersionMapper promptVersionMapper;
    
    /**
     * Create Prompt object from Map
     */
    private Prompt createPromptFromMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        
        return Prompt.builder().promptKey((String) map.get("prompt_key"))
                .promptDescription((String) map.get("prompt_desc")).latestVersion((String) map.get("latest_version"))
                .latestVersionStatus((String) map.get("latest_version_status")).tags((String) map.get("tags"))
                .createTime(map.get("create_time") != null ? ((LocalDateTime) map.get("create_time")).atZone(
                        java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : null).updateTime(
                        map.get("update_time") != null ? ((LocalDateTime) map.get("update_time")).atZone(
                                java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : null).build();
    }
    
    @Override
    @Transactional
    public Prompt create(PromptCreateRequest request) throws StudioException {
        log.info("Create Prompt: {}", request);
        
        //Check if Prompt Key already exists
        PromptDO existingPrompt = promptMapper.selectByPromptKey(request.getPromptKey());
        if (existingPrompt != null) {
            throw new StudioException(StudioException.CONFLICT, "Prompt Key already exists:" + request.getPromptKey());
        }
        
        PromptDO promptDO = PromptDO.builder().promptKey(request.getPromptKey())
                .promptDesc(request.getPromptDescription()).tags(request.getTags()).build();
        
        promptMapper.insert(promptDO);
        log.info("Prompt created successfully: {}", promptDO.getId());
        
        return Prompt.fromDO(promptDO);
    }
    
    @Override
    public Prompt getByPromptKey(String promptKey) throws StudioException {
        log.info("Query Prompt details: {}", promptKey);
        
        Map<String, Object> promptMap = promptMapper.selectByPromptKeyWithLatestVersionStatus(promptKey);
        if (promptMap == null) {
            throw new StudioException(StudioException.NOT_FOUND, "Prompt does not exist:" + promptKey);
        }
        
        return createPromptFromMap(promptMap);
    }
    
    @Override
    public PageResult<Prompt> list(PromptListRequest request) throws StudioException {
        log.info("Query Prompt list: {}", request);
        
        //Validate search pattern parameters
        if (request.getSearch() != null && !"accurate".equals(request.getSearch()) && !"blur".equals(
                request.getSearch())) {
            throw new StudioException(StudioException.INVALID_PARAM, "Search mode must be accurate or blur");
        }
        
        int offset = (request.getPageNo() - 1) * request.getPageSize();
        
        List<Map<String, Object>> promptMapList = promptMapper.selectListWithLatestVersionStatus(request.getSearch(),
                request.getTag(), request.getPromptKey(), offset, request.getPageSize());
        
        int totalCount = promptMapper.selectCount(request.getSearch(), request.getTag(), request.getPromptKey());
        
        List<Prompt> promptList = promptMapList.stream().map(this::createPromptFromMap).collect(Collectors.toList());
        
        return new PageResult<>((long) totalCount, (long) request.getPageNo(), (long) request.getPageSize(),
                promptList);
    }
    
    @Override
    @Transactional
    public Prompt update(PromptUpdateRequest request) throws StudioException {
        log.info("UpdatePrompt: {}", request);
        
        //Check if Prompt exists
        PromptDO existingPrompt = promptMapper.selectByPromptKey(request.getPromptKey());
        if (existingPrompt == null) {
            throw new StudioException(StudioException.NOT_FOUND, "Prompt does not exist:" + request.getPromptKey());
        }
        
        PromptDO promptDO = PromptDO.builder().promptKey(request.getPromptKey())
                .promptDesc(request.getPromptDescription()).tags(request.getTags()).build();
        
        promptMapper.update(promptDO);
        log.info("Prompt updated successfully: {}", request.getPromptKey());
        
        return getByPromptKey(request.getPromptKey());
    }
    
    @Override
    @Transactional
    public void deleteByPromptKey(String promptKey) throws StudioException {
        log.info("Delete Prompt and all its versions: {}", promptKey);
        
        //Check if Prompt exists
        PromptDO existingPrompt = promptMapper.selectByPromptKey(promptKey);
        if (existingPrompt == null) {
            log.info("Prompt does not exist, no need to delete: {}", promptKey);
            return;
        }
        
        //Delete all versions first
        int deletedVersionsCount = promptVersionMapper.deleteByPromptKey(promptKey);
        log.info("All versions of Prompt {} have been deleted. {} versions have been deleted in total.", promptKey, deletedVersionsCount);
        
        //Then delete the prompt itself
        promptMapper.deleteByPromptKey(promptKey);
        log.info("Prompt deleted successfully: {}", promptKey);
    }
    
    @Override
    @Transactional
    public void updateLatestVersion(String promptKey, String latestVersion) {
        log.info("renewPromptlatest version: promptKey={}, latestVersion={}", promptKey, latestVersion);
        
        promptMapper.updateLatestVersion(promptKey, latestVersion);
        log.info("Prompt latest version updated successfully");
    }
}
