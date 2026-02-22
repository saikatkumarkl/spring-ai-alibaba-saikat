package com.alibaba.cloud.ai.studio.admin.service.impl;

import com.alibaba.cloud.ai.studio.admin.common.PageResult;
import com.alibaba.cloud.ai.studio.admin.dto.*;
import com.alibaba.cloud.ai.studio.admin.dto.request.*;
import com.alibaba.cloud.ai.studio.admin.entity.EvaluatorDO;
import com.alibaba.cloud.ai.studio.admin.entity.EvaluatorVersionDO;
import com.alibaba.cloud.ai.studio.admin.mapper.EvaluatorMapper;
import com.alibaba.cloud.ai.studio.admin.mapper.EvaluatorVersionMapper;
import com.alibaba.cloud.ai.studio.admin.service.ChatSessionService;
import com.alibaba.cloud.ai.studio.admin.service.EvaluatorService;
import com.alibaba.cloud.ai.studio.admin.utils.ModelConfigParser;
import com.alibaba.fastjson.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;


import java.time.LocalDateTime;
import java.util.*;

import static com.alibaba.cloud.ai.studio.admin.utils.CommonUtils.extractRawText;


@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluatorServiceImpl implements EvaluatorService {

    private final EvaluatorMapper evaluatorMapper;

    private final EvaluatorVersionMapper evaluatorVersionMapper;

    private final ChatSessionService chatSessionService;

    private final ModelConfigParser modelConfigParser;

    private final String SYSTEM_PROMPT = """
            Return the evaluation result in JSON format. For example:
            {"score":"0.85","reason":"The answer is basically correct and accurately answers the user's questions about artificial intelligence."}
            Only return the JSON string, no other content.
            """;

    @Override
    public Evaluator create(EvaluatorCreateRequest request) {
        log.info("Create evaluator: {}", request);

        //Build DO object
        EvaluatorDO evaluatorDO = EvaluatorDO.builder()
                .name(request.getName())
                .description(request.getDescription())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        //Insert into database
        int result = evaluatorMapper.insert(evaluatorDO);
        if (result > 0) {
            log.info("Evaluator created successfully: {}", evaluatorDO.getId());
            return Evaluator.fromDO(evaluatorDO);
        } else {
            throw new RuntimeException("Failed to create evaluator");
        }
    }

    @Override
    public PageResult<Evaluator> list(EvaluatorListRequest request) {
        log.info("Query evaluator list: {}", request);

        int pageNumber = request.getPageNumber() != null ? request.getPageNumber() : 1;
        int pageSize = request.getPageSize() != null ? request.getPageSize() : 10;
        long offset = (pageNumber - 1L) * pageSize;

        List<EvaluatorDO> evaluatorDOList = evaluatorMapper.selectList(request.getName(), offset, pageSize);

        List<Evaluator> evaluatorList = evaluatorDOList.stream()
                .map(Evaluator::fromDO)
                .map(evaluator -> {
                    EvaluatorVersionDO evaluatorVersionDO = evaluatorVersionMapper.selectLatestVersionByEvaluatorId(evaluator.getId());
                    if (evaluatorVersionDO != null) {
                        evaluator.setModelConfig(evaluatorVersionDO.getModelConfig());
                        evaluator.setLatestVersion(evaluatorVersionDO.getVersion());
                    }
                    return evaluator;
                })
                .toList();

        int total = evaluatorMapper.count(request.getName());

        return new PageResult<>(
                (long) pageNumber,
                (long) total,
                (long) pageSize,
                evaluatorList
        );
    }

    @Override
    public Evaluator getById(Long id) {
        log.info("Query evaluator details: {}", id);

        EvaluatorDO evaluatorDO = evaluatorMapper.selectById(id);
        if (evaluatorDO == null) {
            return null;
        }

        EvaluatorVersionDO evaluatorVersionDO = evaluatorVersionMapper.selectLatestVersionByEvaluatorId(id);
        Evaluator evaluator = Evaluator.fromDO(evaluatorDO);
        if (Objects.nonNull(evaluatorVersionDO)) {
            evaluator.setModelConfig(evaluatorVersionDO.getModelConfig());
            evaluator.setLatestVersion(evaluatorVersionDO.getVersion());
            evaluator.setPrompt(evaluatorVersionDO.getPrompt());
            evaluator.setVariables(evaluatorVersionDO.getVariables());
        }

        return evaluator;
    }

    @Override
    public Evaluator update(EvaluatorUpdateRequest request) {
        log.info("Update evaluator: {}", request);

        //Build DO object
        EvaluatorDO evaluatorDO = EvaluatorDO.builder()
                .id(request.getId())
                .name(request.getName())
                .description(request.getDescription())
                .build();

        //Update database
        int result = evaluatorMapper.update(evaluatorDO);
        if (result > 0) {
            log.info("Evaluator updated successfully: {}", request.getId());
            //Query again to get the latest data
            return Evaluator.fromDO(evaluatorMapper.selectById(request.getId()));
        } else {
            throw new RuntimeException("Update evaluator failed");
        }
    }

    @Override
    public void deleteById(Long id) {
        log.info("Remove evaluator: {}", id);

        int result = evaluatorMapper.deleteById(id);
        if (result > 0) {
            log.info("Evaluator deleted successfully: {}", id);
        } else {
            throw new RuntimeException("Removing evaluator failed");
        }
    }

    @Override
    public EvaluatorDebugResult debug(EvaluatorTestRequest request) {
        log.info("Debug evaluator: {}", request);

        EvaluatorDebugResult result = evaluatorTest(request);

        return result;
    }


    /**
     * Debug model calls
     */
    public EvaluatorDebugResult evaluatorTest(EvaluatorTestRequest request) {
        ChatSession session = chatSessionService.createEvaluatorSession(request.getPrompt(), request.getVariables(), request.getModelConfig());
        Map<String, String> observationMetadata = new HashMap<>();
        observationMetadata.put("studioSource", "evaluator");
        ChatClient client = chatSessionService.getOrCreateSessionChatClient(session.getSessionId(), observationMetadata);

        String userPrompt = modelConfigParser.replaceVariables(request.getPrompt(), request.getVariables());

        String prompt = userPrompt.concat(SYSTEM_PROMPT);

        log.info("evaluatorTest:prompt,{}", prompt);

        String response = Objects.requireNonNull(client.prompt(prompt).call().content()).trim();

        log.info("Model return value:{}", response);

        String formatedResponse = extractRawText(response);
        log.info("Model return value: {}, model return value after formatting: {}.", response, formatedResponse);

        try {
            return JSONObject.parseObject(formatedResponse, EvaluatorDebugResult.class);
        } catch (Exception e) {
            log.info("Parsing failed: {}", formatedResponse, e);
            throw new RuntimeException("There was an error parsing the model call result, please try again.");
        }

    }

} 
