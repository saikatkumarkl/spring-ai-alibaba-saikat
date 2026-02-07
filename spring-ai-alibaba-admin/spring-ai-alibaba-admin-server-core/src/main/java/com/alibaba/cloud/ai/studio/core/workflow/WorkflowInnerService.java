/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.studio.core.workflow;

import com.alibaba.cloud.ai.studio.runtime.domain.workflow.Edge;
import com.alibaba.cloud.ai.studio.runtime.domain.workflow.InvokeSourceEnum;
import com.alibaba.cloud.ai.studio.runtime.domain.workflow.Node;
import com.alibaba.cloud.ai.studio.runtime.domain.workflow.NodeResult;
import com.alibaba.cloud.ai.studio.runtime.domain.workflow.NodeStatusEnum;
import com.alibaba.cloud.ai.studio.runtime.domain.workflow.NodeTypeEnum;
import com.google.common.collect.Sets;
import com.alibaba.cloud.ai.studio.runtime.enums.ErrorCode;
import com.alibaba.cloud.ai.studio.runtime.exception.BizException;
import com.alibaba.cloud.ai.studio.runtime.utils.JsonUtils;
import com.alibaba.cloud.ai.studio.core.base.manager.RedisManager;
import com.alibaba.cloud.ai.studio.core.utils.common.VariableUtils;
import com.alibaba.cloud.ai.studio.core.workflow.processor.impl.EndExecuteProcessor;
import com.alibaba.cloud.ai.studio.core.workflow.processor.impl.OutputExecuteProcessor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.alibaba.cloud.ai.studio.core.base.constants.CacheConstants.WORKFLOW_TASK_CONTEXT_PREFIX;

/**
 * Internal service for workflow management. This service handles workflow execution
 * status tracking and context caching. It provides functionality for: - Managing workflow
 * task execution flags - Caching workflow context - Determining caching strategy based on
 * invocation source
 *
 * @since 1.0.0.3
 */
@Slf4j
@Service
public class WorkflowInnerService {

	private static final Set<String> CAN_STREAM_NODE_TYPE_SET = Sets.newHashSet(NodeTypeEnum.LLM.getCode(),
			NodeTypeEnum.COMPONENT.getCode());

	private final RedisManager redisManager;

	/**
	 * Constructor-based dependency injection for RedisManager
	 * @param redisManager Redis manager for cache operations
	 */
	public WorkflowInnerService(RedisManager redisManager) {
		this.redisManager = redisManager;
	}

	public WorkflowContext getContextCache(String workspaceId, String taskId) {
		WorkflowContext context = redisManager.get(WORKFLOW_TASK_CONTEXT_PREFIX + workspaceId + "_" + taskId);

		//Make sure the context returned has a valid version number
		if (context != null && context.getVersion() <= 0) {
			context.setVersion(1L);
		}

		return context;
	}

	/**
	 * Updates the workflow context in cache with version conflict detection and merging.
	 * Only console invocations require caching, API calls do not need cache refresh.
	 * @param context WorkflowContext to be cached
	 */
	public void refreshContextCache(WorkflowContext context) {
		if (checkRedisNecessity(context)) {
			forceRefreshContextCache(context);
		}
	}

	/**
	 * Forces the update of workflow context in cache with version conflict detection and
	 * merging. This method bypasses the invocation source check and always updates the
	 * cache. It retrieves the latest context from cache, compares versions, and merges if
	 * necessary before saving.
	 * @param context WorkflowContext to be cached
	 */
	public void forceRefreshContextCache(WorkflowContext context) {
		String cacheKey = WORKFLOW_TASK_CONTEXT_PREFIX + context.getWorkspaceId() + "_" + context.getTaskId();

		//Make sure the context has a valid version number
		if (context.getVersion() <= 0) {
			context.setVersion(1L);
		}

		//Get the latest context in the cache
		WorkflowContext existingContext = redisManager.get(cacheKey);

		if (existingContext != null) {
			//Make sure existingContext has a valid version number
			if (existingContext.getVersion() <= 0) {
				existingContext.setVersion(1L);
			}

			//If context exists in cache, perform version comparison
			if (existingContext.getVersion() > context.getVersion()) {
				//The version in the cache is newer or the same and needs to be merged
				log.debug("Version conflict detected: existing={}, new={}, taskId={}", existingContext.getVersion(),
						context.getVersion(), context.getTaskId());

				//Merge context version
				WorkflowContext mergedContext = mergeContextVersions(existingContext, context);

				//Update version number
				mergedContext.setVersion(Math.max(existingContext.getVersion(), context.getVersion()) + 1);

				//Save the merged context
				redisManager.put(cacheKey, mergedContext, Duration.ofHours(1));

				log.debug("Context merged and saved: taskId={}, newVersion={}", mergedContext.getTaskId(),
						mergedContext.getVersion());
			}
			else {
				//If the new context version is higher, save it directly.
				context.setVersion(existingContext.getVersion() + 1);
				redisManager.put(cacheKey, context, Duration.ofHours(1));

				log.debug("New context saved directly: taskId={}, newVersion={}", context.getTaskId(),
						context.getVersion());
			}
		}
		else {
			//The context does not exist in the cache and is saved directly.
			context.setVersion(1L);
			redisManager.put(cacheKey, context, Duration.ofHours(1));

			log.debug("Initial context saved: taskId={}, version={}", context.getTaskId(), context.getVersion());
		}
	}

	/**
	 * Determines if Redis caching is necessary based on the invocation source. Currently,
	 * only console invocations require caching.
	 * @param context WorkflowContext containing invocation source information
	 * @return boolean indicating if Redis caching is required (true) or not (false)
	 */
	public boolean checkRedisNecessity(WorkflowContext context) {
		return InvokeSourceEnum.valueOf(context.getInvokeSource()).isCached();
	}

	/**
	 * Determines if a node can be executed based on its dependencies and state
	 * @param graph The workflow graph
	 * @param nodeId The ID of the node to check
	 * @param context The execution context
	 * @return true if the node can be executed, false otherwise
	 */
	public boolean canExecute(DirectedAcyclicGraph<String, Edge> graph, String nodeId, WorkflowContext context) {
		// If node already has result, it cannot be executed
		if (context.getNodeResultMap().get(nodeId) != null) {
			return false;
		}

		Optional<Node> nodeOptional = context.getWorkflowConfig()
			.getNodes()
			.stream()
			.filter(node -> node.getId().equals(nodeId))
			.findFirst();
		if (nodeOptional.isEmpty()) {
			throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID.toError());
		}

		// Get all predecessor nodes
		Set<Edge> incomingEdges = graph.incomingEdgesOf(nodeId);
		// If there are no predecessor nodes, the node can be executed
		if (incomingEdges.isEmpty()) {
			return true;
		}

		// Process streaming output logic for output nodes
		String nodeType = nodeOptional.get().getType();
		if (NodeTypeEnum.OUTPUT.getCode().equals(nodeType)) {
			Node node = nodeOptional.get();
			OutputExecuteProcessor.NodeParam config = JsonUtils.fromMap(node.getConfig().getNodeParam(),
					OutputExecuteProcessor.NodeParam.class);

			boolean streamSwitch = config.getStreamSwitch() == null ? false : config.getStreamSwitch();
			if (streamSwitch) {
				Set<String> keys = VariableUtils.identifyVariableSetFromText(config.getOutput());
				if (CollectionUtils.isNotEmpty(keys)) {
					// Extract variable source node IDs
					Set<String> keyFromSet = keys.stream()
						.map(key -> key.contains(".") ? key.substring(0, key.indexOf(".")) : key)
						.collect(Collectors.toSet());

					return checkStreamOutputNodeExecutable(incomingEdges, context, keyFromSet);
				}
			}
		}

		// Process streaming output logic for end nodes
		if (NodeTypeEnum.END.getCode().equals(nodeType)) {
			Node node = nodeOptional.get();
			EndExecuteProcessor.NodeParam config = JsonUtils.fromMap(node.getConfig().getNodeParam(),
					EndExecuteProcessor.NodeParam.class);
			String outputType = config.getOutputType();
			if ("text".equals(outputType)) {
				boolean streamSwitch = config.getStreamSwitch() != null && config.getStreamSwitch();
				if (streamSwitch) {
					Set<String> keys = VariableUtils.identifyVariableSetFromText(config.getTextTemplate());
					if (CollectionUtils.isNotEmpty(keys)) {
						// Extract variable source node IDs
						Set<String> keyFromSet = keys.stream()
							.map(key -> key.contains(".") ? key.substring(0, key.indexOf(".")) : key)
							.collect(Collectors.toSet());

						return checkStreamOutputNodeExecutable(incomingEdges, context, keyFromSet);
					}
				}
			}
		}

		// Common node execution logic
		return checkCommonNodeExecutable(incomingEdges, context);
	}

	/**
	 * Checks if a streaming output node can be executed Considers the state of source
	 * nodes and streaming requirements
	 * @param incomingEdges The incoming edges to the node
	 * @param context The execution context
	 * @param keyFromSet Set of source node IDs
	 * @return true if the node can be executed, false otherwise
	 */
	private boolean checkStreamOutputNodeExecutable(Set<Edge> incomingEdges, WorkflowContext context,
			Set<String> keyFromSet) {
		for (Edge ancestor : incomingEdges) {
			NodeResult nodeResult = context.getNodeResultMap().get(ancestor.getSource());
			if (nodeResult == null) {
				return false;
			}
			if (keyFromSet.contains(ancestor.getSource())
					&& CAN_STREAM_NODE_TYPE_SET.contains(nodeResult.getNodeType())) {
				// Output node streaming output when result status is not empty and not
				// FAIL
				if (nodeResult.getNodeStatus().equals(NodeStatusEnum.FAIL.getCode())) {
					return false;
				}
			}
			else {
				// If execution is not completed or failed, the node cannot be executed
				if (isNodeCompleted(nodeResult)) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Checks if a common node can be executed Verifies that all incoming nodes have
	 * completed successfully
	 * @param incomingEdges The incoming edges to the node
	 * @param context The execution context
	 * @return true if the node can be executed, false otherwise
	 */
	private boolean checkCommonNodeExecutable(Set<Edge> incomingEdges, WorkflowContext context) {
		for (Edge ancestor : incomingEdges) {
			NodeResult nodeResult = context.getNodeResultMap().get(ancestor.getSource());
			if (isNodeCompleted(nodeResult)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Determines if a node has completed its execution
	 * @param nodeResult The result of the node execution
	 * @return true if the node has completed, false otherwise
	 */
	private boolean isNodeCompleted(NodeResult nodeResult) {
		return nodeResult == null || nodeResult.getNodeStatus().equals(NodeStatusEnum.FAIL.getCode())
				|| nodeResult.getNodeStatus().equals(NodeStatusEnum.EXECUTING.getCode())
				|| nodeResult.getNodeStatus().equals(NodeStatusEnum.PAUSE.getCode())
				|| nodeResult.getNodeStatus().equals(NodeStatusEnum.STOP.getCode());
	}

	/**
	 * Merge two WorkflowContext versions to ensure that new data will not be overwritten by old data. Give priority to retaining data in the new context, but retain important information that may be missed in the old context.
	 * @param existingContext existing context
	 * @param newContext new context
	 * @return merged context
	 */
	private WorkflowContext mergeContextVersions(WorkflowContext existingContext, WorkflowContext newContext) {
		//Create a merged context based on the new context
		WorkflowContext mergedContext = WorkflowContext.deepCopy(newContext);

		if (mergedContext == null) {
			log.warn("Failed to deep copy new context, using original: taskId={}", newContext.getTaskId());
			return newContext;
		}

		//Make sure the merged context has a valid version number
		if (mergedContext.getVersion() <= 0) {
			mergedContext.setVersion(1L);
		}

		//Merge nodeResultMap - keep latest results for all nodes
		if (existingContext.getNodeResultMap() != null) {
			existingContext.getNodeResultMap().forEach((nodeId, nodeResult) -> {
				//If there is no result for the node in the new context, or the node status is earlier in the new context, the old result is retained.
				NodeResult newResult = mergedContext.getNodeResultMap().get(nodeId);
				if (newResult == null || isNodeStatusOlder(newResult.getNodeStatus(), nodeResult.getNodeStatus())) {
					mergedContext.getNodeResultMap().put(nodeId, nodeResult);
				}
			});
		}

		//Merge variablesMap - keep all variables, new values ​​first
		if (existingContext.getVariablesMap() != null) {
			existingContext.getVariablesMap().forEach((key, value) -> {
				if (!mergedContext.getVariablesMap().containsKey(key)) {
					mergedContext.getVariablesMap().put(key, value);
				}
			});
		}

		//Merge executeOrderList - retain full execution order
		if (existingContext.getExecuteOrderList() != null && mergedContext.getExecuteOrderList() != null) {
			//Merge execution order to avoid duplication
			existingContext.getExecuteOrderList().forEach(nodeId -> {
				if (!mergedContext.getExecuteOrderList().contains(nodeId)) {
					mergedContext.getExecuteOrderList().add(nodeId);
				}
			});
		}

		//Merge subTaskIdSet - keep all subtask IDs
		if (existingContext.getSubTaskIdSet() != null) {
			existingContext.getSubTaskIdSet().forEach(subTaskId -> {
				mergedContext.getSubTaskIdSet().add(subTaskId);
			});
		}

		//Merge subWorkflowContextMap - keep all sub-workflow contexts
		if (existingContext.getSubWorkflowContextMap() != null) {
			existingContext.getSubWorkflowContextMap().forEach((key, subContext) -> {
				if (!mergedContext.getSubWorkflowContextMap().containsKey(key)) {
					mergedContext.getSubWorkflowContextMap().put(key, subContext);
				}
			});
		}

		log.debug("Merged context versions: taskId={}, existingNodes={}, newNodes={}, mergedNodes={}",
				mergedContext.getTaskId(),
				existingContext.getNodeResultMap() != null ? existingContext.getNodeResultMap().size() : 0,
				newContext.getNodeResultMap() != null ? newContext.getNodeResultMap().size() : 0,
				mergedContext.getNodeResultMap() != null ? mergedContext.getNodeResultMap().size() : 0);

		return mergedContext;
	}

	/**
	 * Determine whether the node status is earlier (used for priority judgment when merging versions)
	 * @param newStatus new status
	 * @param oldStatus old status
	 * @return true if the new state is earlier than the old state
	 */
	private boolean isNodeStatusOlder(String newStatus, String oldStatus) {
		//Define status priority: EXECUTING < SUCCESS < FAIL < PAUSE
		//The smaller the number, the earlier the status is
		java.util.Map<String, Integer> statusPriority = Map.of(NodeStatusEnum.EXECUTING.getCode(), 1,
				NodeStatusEnum.SUCCESS.getCode(), 2, NodeStatusEnum.FAIL.getCode(), 3, NodeStatusEnum.PAUSE.getCode(),
				4);

		Integer newPriority = statusPriority.get(newStatus);
		Integer oldPriority = statusPriority.get(oldStatus);

		//If the state is not in the predefined list, the new state is considered earlier
		if (newPriority == null) {
			return true;
		}
		if (oldPriority == null) {
			return false;
		}

		return newPriority < oldPriority;
	}

}
