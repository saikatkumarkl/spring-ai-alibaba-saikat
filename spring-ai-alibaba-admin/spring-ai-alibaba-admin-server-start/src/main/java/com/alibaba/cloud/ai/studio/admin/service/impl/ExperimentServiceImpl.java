package com.alibaba.cloud.ai.studio.admin.service.impl;

import com.alibaba.cloud.ai.studio.admin.common.PageResult;
import com.alibaba.cloud.ai.studio.admin.dto.*;
import com.alibaba.cloud.ai.studio.admin.dto.request.*;
import com.alibaba.cloud.ai.studio.admin.entity.*;
import com.alibaba.cloud.ai.studio.admin.enums.ExperimentStatus;
import com.alibaba.cloud.ai.studio.admin.exception.StudioException;
import com.alibaba.cloud.ai.studio.admin.mapper.*;
import com.alibaba.cloud.ai.studio.admin.service.*;
import com.alibaba.cloud.ai.studio.admin.utils.CommonUtils;
import com.alibaba.cloud.ai.studio.admin.utils.ModelConfigParser;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static com.alibaba.cloud.ai.studio.admin.utils.SessionUtils.convertChatMessages;


@Slf4j
@Service
@RequiredArgsConstructor
public class ExperimentServiceImpl implements ExperimentService {

    private final ExperimentMapper experimentMapper;
    private final ExperimentResultMapper experimentResultMapper;
    private final DatasetVersionMapper datasetVersionMapper;
    private final EvaluatorMapper evaluatorMapper;
    private final EvaluatorVersionMapper evaluatorVersionMapper;
    private final DatasetItemMapper datasetItemMapper;
    private final ModelConfigParser modelConfigParser;


    @Autowired
    private PromptVersionService promptVersionService;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private EvaluatorServiceImpl evaluatorServiceImpl;

    //Create a thread pool for asynchronous execution of experiments
    private final ExecutorService experimentExecutor = Executors.newFixedThreadPool(5);


    @Override
    @Transactional
    public Experiment create(ExperimentCreateRequest request) {
        log.info("Create experiment: {}", request);


        //Build experimental entities
        ExperimentDO experimentDO = ExperimentDO.builder()
                .name(request.getName())
                .description(request.getDescription())
                .datasetId(request.getDatasetId())
                .datasetVersionId(request.getDatasetVersionId())
                .datasetVersion(request.getDatasetVersion())
                .evaluationObjectConfig(request.getEvaluationObjectConfig())
                .evaluatorConfig(request.getEvaluatorConfig())
                .status(String.valueOf(ExperimentStatus.RUNNING))
                .progress(0)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        //Insert into database
        int result = experimentMapper.insert(experimentDO);
        if (result <= 0) {
            throw new RuntimeException("Failed to create experiment");
        }

        log.info("Experiment created successfully: {}", experimentDO.getId());
        
        //Start experiment execution asynchronously
        startExperimentExecution(experimentDO);
        
        return Experiment.fromDO(experimentDO);
    }

    @Override
    public PageResult<Experiment> list(ExperimentListRequest request) {
        log.info("Query experiment list: {}", request);


        ExperimentStatus status = null;
        if (StringUtils.hasText(request.getStatus())) {
            try {
                status = ExperimentStatus.fromCode(request.getStatus());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid experiment status: {}", request.getStatus());
            }
        }
        
        //Calculate offset
        long offset = (request.getPageNumber() - 1L) * request.getPageSize();
        
        //Query data
        List<ExperimentDO> experimentDOList = experimentMapper.selectList(request.getName(), status, offset, request.getPageSize());

        //Get total
        int totalCount = experimentMapper.count(request.getName(), status);

        return new PageResult<>(
                (long) totalCount,
                (long) request.getPageNumber(),
                (long) request.getPageSize(),
                experimentDOList.stream()
                        .map(Experiment::fromDO)
                        .toList());

    }

    @Override
    public Experiment getById(Long id) {
        log.info("Query experiment details: {}", id);
        
        if (id == null) {
            throw new IllegalArgumentException("Experiment ID cannot be null");
        }
        
        ExperimentDO experimentDO = experimentMapper.selectById(id);
        if (experimentDO == null) {
            log.warn("Experiment with ID {} ​​not found", id);
            return null;
        }

        Experiment experiment = Experiment.fromDO(experimentDO);

        List<EvaluatorConfig> evaluatorConfigList = JSON.parseArray(experiment.getEvaluatorConfig(), EvaluatorConfig.class);

        evaluatorConfigList.stream()
                .filter(Objects::nonNull)
                .forEach(evaluatorConfig -> {
                    try {
                        EvaluatorDO evaluatorDO = evaluatorMapper.selectById(evaluatorConfig.getEvaluatorId());
                        evaluatorConfig.setEvaluatorName(evaluatorDO != null ? evaluatorDO.getName() : "Unknown Evaluator");
                    } catch (Exception e) {
                        log.warn("Failed to fetch evaluator name for id: {}", evaluatorConfig.getEvaluatorId(), e);
                        evaluatorConfig.setEvaluatorName("Error Fetching Name");
                    }
                });
        experiment.setEvaluatorConfig(JSON.toJSONString(evaluatorConfigList));
        return experiment;
    }

    @Override
    public List<ExperimentEvaluatorResult> getResults(Long experimentId) {
        log.info("Query experimental results: {}", experimentId);
        
        //First check if the experiment exists
        ExperimentDO experiment = experimentMapper.selectById(experimentId);
        if (experiment == null) {
            log.warn("Experiment does not exist: {}", experimentId);
            return null;
        }

        Integer dataCount = datasetVersionMapper.selectById(experiment.getDatasetVersionId()).getDataCount();
        //Check if dataCount is null or 0 to avoid divide-by-zero exceptions
        if (dataCount == null || dataCount == 0) {
            log.warn("The data volume of the dataset version is 0 or does not exist: {}", experiment.getDatasetVersionId());
            dataCount = 1; //Avoid divide-by-zero exceptions and set default values
        }


        //Correctly parses evaluatorConfig JSON array string as List<EvaluatorConfig>
        List<EvaluatorConfig> evaluatorConfigList = JSON.parseArray(experiment.getEvaluatorConfig(), EvaluatorConfig.class);

        //Extract evaluatorVersionId list
        List<Long> evaluatorList = evaluatorConfigList.stream()
                .map(e -> Long.valueOf(e.getEvaluatorVersionId()))
                .toList();

        //Use stream map collect method to build the result list
        Integer finalDataCount = dataCount;
        return evaluatorList.stream().map(evaluatorVersionId -> {
            List<ExperimentResultDO> resultList = experimentResultMapper.selectByExperimentAndEvaluator(experimentId, evaluatorVersionId);
            //Calculate the average score to avoid division by zero exceptions
            BigDecimal averageScore = BigDecimal.ZERO;
            if (resultList != null && !resultList.isEmpty()) {
                averageScore = resultList.stream()
                        .map(ExperimentResultDO::getScore)
                        .filter(score -> score != null) //Filter out null values
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(new BigDecimal(resultList.size()), 2, BigDecimal.ROUND_HALF_UP);
            }
            int completeItemsCount = (resultList != null) ? resultList.size() : 0;
            Integer progress = completeItemsCount * 100 / finalDataCount;
            return ExperimentEvaluatorResult.builder()
                    .experimentId(experimentId)
                    .averageScore(averageScore)
                    .evaluatorVersionId(evaluatorVersionId)
                    .progress(progress)
                    .completeItemsCount(completeItemsCount)
                    .totalItemsCount(finalDataCount)
                    .build();
        }).collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    @Override
    public PageResult<ExperimentEvaluatorResultDetail> getResult(ExperimentEvaluatorResultDetailListRequest request){

        //First check if the experiment exists
        ExperimentDO experiment = experimentMapper.selectById(request.getExperimentId());
        if (experiment == null) {
            log.warn("Experiment does not exist: {}", request.getExperimentId());
            return null;
        }
        if (request.getEvaluatorVersionId() == null) {
            throw new IllegalArgumentException("Evaluator version ID cannot be null");
        }


        Integer offset = (request.getPageNumber() - 1) * request.getPageSize();

        Integer limit = request.getPageSize();

        if (offset < 0 || limit <= 0) {
            throw new IllegalArgumentException("Invalid page number or page size");
        }


        Integer totalCount = experimentResultMapper.selectCountByExperimentIdAndEvaluator(request.getExperimentId(), request.getEvaluatorVersionId());

        List<ExperimentResultDO> resultList = experimentResultMapper.selectByExperimentAndEvaluatorWithPageble(request.getExperimentId(),request.getEvaluatorVersionId(),offset,request.getPageSize());


        List<ExperimentEvaluatorResultDetail> resultItems = resultList.stream()
                .map(ExperimentEvaluatorResultDetail::fromDO)
                .toList();

        return new PageResult<>(
                (long) totalCount,
                (long) request.getPageNumber(),
                (long) request.getPageSize(),
                resultItems);

    }

    @Override
    @Transactional
    public Experiment stop(Long id) {
        log.info("Stop experiment: {}", id);
        
        if (id == null) {
            throw new IllegalArgumentException("Experiment ID cannot be null");
        }
        
        //Get experimental information
        ExperimentDO experimentDO = experimentMapper.selectById(id);
        if (experimentDO == null) {
            throw new IllegalArgumentException("Experiment not found: " + id);
        }



        //Check experiment status
        if (ExperimentStatus.COMPLETED.getCode().equals(experimentDO.getStatus()) ||
            ExperimentStatus.FAILED.getCode().equals(experimentDO.getStatus()) ||
            ExperimentStatus.STOPPED.getCode().equals(experimentDO.getStatus())) {
            log.warn("Experiment {} has status {} and cannot be stopped", id, experimentDO.getStatus());
            return Experiment.fromDO(experimentDO);
        }


        
        //Update experiment status to stopped
        experimentDO.setStatus(String.valueOf(ExperimentStatus.STOPPED));
        experimentDO.setUpdateTime(LocalDateTime.now());
        
        int result = experimentMapper.updateById(experimentDO);
        if (result <= 0) {
            throw new RuntimeException("Failed to stop experiment");
        }
        
        log.info("Experiment stopped successfully: {}", id);
        return Experiment.fromDO(experimentDO);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        log.info("Delete experiment: {}", id);
        
        if (id == null) {
            throw new IllegalArgumentException("Experiment ID cannot be null");
        }
        
        //Check if the experiment exists
        ExperimentDO experimentDO = experimentMapper.selectById(id);
        if (experimentDO == null) {
            throw new IllegalArgumentException("Experiment not found: " + id);
        }
        
        //Check the experiment status. Running experiments cannot be deleted.
        if (Objects.equals(experimentDO.getStatus(), ExperimentStatus.RUNNING.getCode())) {
            throw new IllegalStateException("Cannot delete running experiment: " + id);
        }
        
        //Delete experiment
        int result = experimentMapper.deleteById(id);
        if (result <= 0) {
            throw new RuntimeException("Failed to delete experiment");
        }
//
////Delete related experimental results
//        experimentResultMapper.deleteByExperimentId(id);
        
        log.info("Experiment deleted successfully: {}", id);
    }

    @Override
    public void restartById(Long id) {
        //Clean historical data
        experimentResultMapper.deleteByExperimentId(id);
        //Experiment execution

        ExperimentDO experimentDO = experimentMapper.selectById(id);

        startExperimentExecution(experimentDO);


    }



    /**
     * Start experiment execution
     */
    private void startExperimentExecution(ExperimentDO experimentDO)  {
        try {
            experimentExecutor.submit(
                    ()->{
                        try {
                            executeExperiment (experimentDO);
                        } catch (Exception e) {
                            log.error("An error occurred during experiment execution: {}", experimentDO.getId(), e);
                            updateExperimentStatus(experimentDO.getId(), ExperimentStatus.FAILED, null);
                        }
                    }
            );

            log.info("Experiment execution task has been started: {}", experimentDO.getId());

        } catch (Exception e) {
            log.error("Failed to start experiment execution: {}", experimentDO.getId(), e);

            //Update experiment status to failed
            updateExperimentStatus(experimentDO.getId(), ExperimentStatus.FAILED, null);
        }
    }

    /**
     * The core logic of executing the experiment
     */
    private void executeExperiment(ExperimentDO experimentDO) throws StudioException {
        log.info("Start running the experiment: {}", experimentDO.getId());

        //Analyze experiment target configuration

        EvaluationObjectConfig evaluationObjectConfig = JSONObject.parseObject(experimentDO.getEvaluationObjectConfig(),EvaluationObjectConfig.class);
        if(evaluationObjectConfig.getType().equals("prompt")){
            promptEvaluation(experimentDO);

        }
    }



    private void promptEvaluation(ExperimentDO experimentDO) throws StudioException {
        EvaluationObjectConfig evaluationObjectConfig = JSONObject.parseObject(experimentDO.getEvaluationObjectConfig(),EvaluationObjectConfig.class);
        EvaluationPromptConfig evaluationPromptConfig = JSONObject.parseObject(evaluationObjectConfig.getConfig(),EvaluationPromptConfig.class);

        Long experimentId = experimentDO.getId();

        //Get all data items in the dataset

        DatasetVersionDO datasetVersion = datasetVersionMapper.selectById(experimentDO.getDatasetVersionId());

        List<Long> itemIds = CommonUtils.parseItemIds(datasetVersion.getDatasetItems());

        List<DatasetItemDO> datasetItems = datasetItemMapper.selectByDatasetIdAndItemIds(
                datasetVersion.getDatasetId(), itemIds);

        if (datasetItems.isEmpty()) {
            log.warn("The data set is empty and the experiment is completed: {}", experimentId);
            updateExperimentStatus(experimentId, ExperimentStatus.COMPLETED, 100);
            return;
        }

        int totalItems = datasetItems.size();
        AtomicInteger processedItems = new AtomicInteger(0);

        log.info("Experiment {} starts processing {} data items", experimentId, totalItems);


        PromptVersionDetail prompt = promptVersionService.getByPromptKeyAndVersion(evaluationPromptConfig.getPromptKey(),evaluationPromptConfig.getVersion());


        for (DatasetItemDO datasetItem : datasetItems) {
            try {
                //Check if the experiment has been stopped
                if (isExperimentStopped(experimentId)) {
                    log.info("Experiment {} has been stopped", experimentId);
                    return;
                }


                JSONObject dataContent = JSONObject.parseObject(datasetItem.getDataContent());


                String actualOutput = getPromptResult(prompt, dataContent, evaluationPromptConfig);


                List<EvaluatorConfig> evaluatorConfigs = JSON.parseArray(experimentDO.getEvaluatorConfig(), EvaluatorConfig.class);

                evaluatorConfigs.forEach(
                        evaluatorConfig -> {
                            EvaluatorDebugResult debugResult= getEvaluatorResult(evaluatorConfig,dataContent,actualOutput);
                            saveExperimentResult(experimentId, datasetItem.getId(), dataContent.getString("input"), actualOutput, dataContent.getString("reference_output"), debugResult.getScore(), debugResult.getReason(), evaluatorConfig.getEvaluatorVersionId());
                        }

                );


                //update progress
                int currentProgress = (processedItems.incrementAndGet() * 100) / totalItems;
                updateExperimentProgress(experimentId, currentProgress);

                log.debug("Experiment {} Progress: {}/{} ({}%)", experimentId, processedItems.get(), totalItems, currentProgress);

            } catch (Exception e) {
                log.error("Failed to process data item: experimentId={}, itemId={}", experimentId, datasetItem.getId(), e);
                //Continue processing the next data item without interrupting the entire experiment
            }
        }

        //Experiment completed
        log.info("Experiment {} has been executed and a total of {} data items have been processed.", experimentId, totalItems);
        updateExperimentStatus(experimentId, ExperimentStatus.COMPLETED, 100);

    }



    private  String getPromptResult(PromptVersionDetail prompt,JSONObject dataContent,EvaluationPromptConfig evaluationPromptConfig){
        //value is determined through EvaluationPromptConfigVariableMap.
        JSONObject variables = JSONObject.parseObject(prompt.getVariables());
        //Get the mapping relationship between prompt variables and datasetvolumsname from EvaluationPromptConfigVariableMap, get the corresponding value from datacontent, and put it into the key corresponding to prompt viriable name in variables.

        List<EvaluationPromptConfigVariableMap> variableMapList = evaluationPromptConfig.getVariableMap();

        variableMapList.forEach(
                variableMap -> {
                    variables.put(variableMap.getPromptVariable(), dataContent.getString(variableMap.getDatasetVolumn()));
                }
        );


        String userPrompt = modelConfigParser.replaceVariables(prompt.getTemplate(),variables.toJSONString());

        log.info("getPromptResult,prompt:{}",userPrompt);



        ChatSession session = chatSessionService.createSession(prompt.getPromptKey(), prompt.getVersion(), prompt.getTemplate(),
                variables.toJSONString(), prompt.getModelConfig());

        session.addUserMessage((String) dataContent.get("input"));
        chatSessionService.updateSession(session);
        
        Map<String, String> observationMetadata = new HashMap<>();
        observationMetadata.put("studioSource", "experiment");
        observationMetadata.put("promptKey", prompt.getPromptKey());
        observationMetadata.put("promptVersion", prompt.getVersion());
        observationMetadata.put("promptTemplate", prompt.getTemplate());
        observationMetadata.put("promptVariables", variables.toJSONString());
        //Get or create a session-bound ModelClient
        ChatClient client = chatSessionService.getOrCreateSessionChatClient(session.getSessionId(), observationMetadata);


        String response = client.prompt(userPrompt).messages(convertChatMessages(session.getMessages())).call().content();

        log.info("getPromptResult,response:{}",response);

        return response;
    }


    private EvaluatorDebugResult getEvaluatorResult(EvaluatorConfig evaluatorConfig, JSONObject dataContent,String actualOutput) {

        EvaluatorTestRequest request = new EvaluatorTestRequest();

        EvaluatorVersionDO evaluatorVersionDO = evaluatorVersionMapper.selectById(evaluatorConfig.getEvaluatorVersionId());

        JSONObject variables = JSONObject.parseObject(evaluatorVersionDO.getVariables());

        evaluatorConfig.getVariableMap().forEach(
                variableMapItem -> {
                    if(variableMapItem.getSource().equals("actual_output")){
                        variables.put(variableMapItem.getEvaluatorVariable(),actualOutput);
                    }else{
                        variables.put(variableMapItem.getEvaluatorVariable(),dataContent.getString(variableMapItem.getSource()));
                    }
                }
        );

        request.setModelConfig(evaluatorVersionDO.getModelConfig());
        request.setPrompt(evaluatorVersionDO.getPrompt());
        request.setVariables(variables.toJSONString());

        EvaluatorDebugResult result = evaluatorServiceImpl.evaluatorTest(request);
        return result;
    }



    /**
     * Save experiment results
     */
    private void saveExperimentResult(Long experimentId, Long datasetItemId,
                                      String input, String actualOutput, String referenceOutput,
                                      String score, String reason, Long evaluatorVersionId) {
        try {
            ExperimentResultDO resultDO = ExperimentResultDO.builder()
                    .experimentId(experimentId)
                    .input(input)
                    .actualOutput(actualOutput)
                    .referenceOutput(referenceOutput)
                    .score(new BigDecimal(score))
                    .reason(reason)
                    .evaluatorVersionId(evaluatorVersionId)
                    .evaluationTime(LocalDateTime.now())
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            
            //Use the bulk insert method to wrap individual results into a list
            List<ExperimentResultDO> results = new ArrayList<>();
            results.add(resultDO);
            experimentResultMapper.batchInsert(results);
            log.debug("Experiment results saved successfully: experimentId={}, itemId={}", experimentId, datasetItemId);
            
        } catch (Exception e) {
            log.error("Failed to save experiment results: experimentId={}, itemId={}", experimentId, datasetItemId, e);
        }
    }

    /**
     * Check if the experiment has been stopped
     */
    private boolean isExperimentStopped(Long experimentId) {
        try {
            ExperimentDO experimentDO = experimentMapper.selectById(experimentId);
            return experimentDO != null && 
                   ExperimentStatus.STOPPED.getCode().equals(experimentDO.getStatus());
        } catch (Exception e) {
            log.error("Failed to check experiment status: {}", experimentId, e);
            return false;
        }
    }

    /**
     * Update experiment progress
     */
    private void updateExperimentProgress(Long experimentId, Integer progress) {
        try {
            ExperimentDO experimentDO = ExperimentDO.builder()
                    .id(experimentId)
                    .progress(progress)
                    .updateTime(LocalDateTime.now())
                    .build();
            experimentMapper.updateById(experimentDO);
        } catch (Exception e) {
            log.error("Failed to update experiment progress: {}", experimentId, e);
        }
    }

    /**
     * Update experiment status
     */
    private void updateExperimentStatus(Long experimentId, ExperimentStatus status, Integer progress) {
        try {
            ExperimentDO experimentDO = ExperimentDO.builder()
                    .id(experimentId)
                    .status(status.getCode())
                    .progress(progress)
                    .updateTime(LocalDateTime.now())
                    .build();
            
            if (status == ExperimentStatus.COMPLETED) {
                experimentDO.setCompleteTime(LocalDateTime.now());
            }
            
            experimentMapper.updateById(experimentDO);
            log.info("Experiment status updated successfully: experimentId={}, status={}", experimentId, status);
            
        } catch (Exception e) {
            log.error("Failed to update experiment status: {}", experimentId, e);
        }
    }

    @Override
    public PageResult<Experiment> getExperimentsByEvaluator(EvaluatorExperimentsListRequest request) {
        log.info("Query the experiments associated with the evaluator: {}", request);
        
        try {
            //Calculate offset
            long offset = (request.getPageNumber() - 1L) * request.getPageSize();
            
            List<ExperimentDO> experimentDOList;
            int totalCount;

            experimentDOList = experimentMapper.selectByEvaluatorId(
                    request.getEvaluatorId(), offset, request.getPageSize());

            totalCount = experimentMapper.selectCountByEvaluatorId(request.getEvaluatorId());

            
            //Convert to DTO and get dataset version information
            List<Experiment> experiments = experimentDOList.stream()
                    .map(experimentDO -> {
                        try {
                            return Experiment.fromDO(experimentDO);
                        } catch (Exception e) {
                            log.warn("Failed to obtain dataset version information: experimentId={}, datasetVersionId={}", 
                                experimentDO.getId(), experimentDO.getDatasetVersionId(), e);
                            return Experiment.fromDO(experimentDO);
                        }
                    })
                    .toList();
            
            return new PageResult<>(
                    (long) totalCount,
                    (long) request.getPageNumber(),
                    (long) request.getPageSize(),
                    experiments
            );
            
        } catch (Exception e) {
            log.error("Experiment associated with query evaluator failed: {}", request, e);
            throw new RuntimeException("Experiment associated with query evaluator failed:" + e.getMessage());
        }
    }

} 
