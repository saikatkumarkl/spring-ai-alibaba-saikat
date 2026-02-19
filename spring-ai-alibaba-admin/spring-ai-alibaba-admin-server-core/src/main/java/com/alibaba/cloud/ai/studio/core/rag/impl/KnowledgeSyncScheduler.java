/*
 * Copyright 2025-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.studio.core.rag.impl;

import com.alibaba.cloud.ai.studio.core.base.entity.KnowledgeSyncEntity;
import com.alibaba.cloud.ai.studio.core.base.mapper.KnowledgeSyncMapper;
import com.alibaba.cloud.ai.studio.core.rag.KnowledgeSyncService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Scheduler that reads sync_cron expressions from the knowledge_sync table and
 * triggers startSync() at the configured intervals. Re-evaluates cron expressions
 * every 60 seconds to pick up changes made via the API.
 *
 * <p>Uses Spring's {@link TaskScheduler} to dynamically register/cancel cron tasks.
 * Each sync job gets at most one active scheduled future. When the cron expression
 * changes, the old task is cancelled and a new one is registered.</p>
 */
@Slf4j
@Component
public class KnowledgeSyncScheduler {

	private final KnowledgeSyncMapper syncMapper;

	private final KnowledgeSyncService knowledgeSyncService;

	private final TaskScheduler taskScheduler;

	/**
	 * Tracks active scheduled futures by syncId.
	 * Key = syncId, Value = tuple of (cronExpression, scheduledFuture).
	 */
	private final ConcurrentHashMap<String, ScheduledEntry> scheduledTasks = new ConcurrentHashMap<>();

	public KnowledgeSyncScheduler(KnowledgeSyncMapper syncMapper, KnowledgeSyncService knowledgeSyncService,
			TaskScheduler taskScheduler) {
		this.syncMapper = syncMapper;
		this.knowledgeSyncService = knowledgeSyncService;
		this.taskScheduler = taskScheduler;
		log.info("KnowledgeSyncScheduler initialized — will poll DB every 60s for cron-scheduled syncs");
	}

	/**
	 * Polls the knowledge_sync table every 60 seconds for rows with a non-empty sync_cron.
	 * Registers, updates, or cancels cron-triggered tasks as needed.
	 */
	@Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
	public void refreshScheduledSyncs() {
		try {
			// Find all sync entries that have a cron expression set
			LambdaQueryWrapper<KnowledgeSyncEntity> wrapper = new LambdaQueryWrapper<>();
			wrapper.isNotNull(KnowledgeSyncEntity::getSyncCron);
			List<KnowledgeSyncEntity> syncsWithCron = syncMapper.selectList(wrapper);

			// Track which syncIds are still active (have a cron expression)
			java.util.Set<String> activeSyncIds = new java.util.HashSet<>();

			for (KnowledgeSyncEntity entity : syncsWithCron) {
				String syncId = entity.getSyncId();
				String cron = entity.getSyncCron();

				if (StringUtils.isBlank(cron)) {
					continue;
				}

				activeSyncIds.add(syncId);
				ScheduledEntry existing = scheduledTasks.get(syncId);

				if (existing != null && existing.cronExpression.equals(cron)) {
					// Same cron — no change needed
					continue;
				}

				// Cancel old task if cron changed
				if (existing != null) {
					log.info("Cron expression changed for sync '{}': '{}' → '{}'. Rescheduling.",
							syncId, existing.cronExpression, cron);
					existing.future.cancel(false);
				}
				else {
					log.info("Registering cron schedule for sync '{}': '{}'", syncId, cron);
				}

				// Schedule new task
				try {
					CronTrigger trigger = new CronTrigger(cron);
					ScheduledFuture<?> future = taskScheduler.schedule(
							() -> triggerSync(syncId), trigger);
					scheduledTasks.put(syncId, new ScheduledEntry(cron, future));
				}
				catch (IllegalArgumentException e) {
					log.error("Invalid cron expression '{}' for sync '{}': {}", cron, syncId, e.getMessage());
				}
			}

			// Cancel tasks for syncs whose cron was removed
			scheduledTasks.keySet().removeIf(syncId -> {
				if (!activeSyncIds.contains(syncId)) {
					ScheduledEntry entry = scheduledTasks.get(syncId);
					if (entry != null) {
						log.info("Cron removed for sync '{}'. Cancelling scheduled task.", syncId);
						entry.future.cancel(false);
					}
					return true;
				}
				return false;
			});

			if (!scheduledTasks.isEmpty()) {
				log.debug("Active cron-scheduled syncs: {}", scheduledTasks.keySet());
			}
		}
		catch (Exception e) {
			log.error("Error refreshing scheduled syncs: {}", e.getMessage(), e);
		}
	}

	/**
	 * Triggers a sync for the given syncId. Catches and logs exceptions so the
	 * scheduled task doesn't die on failure.
	 */
	private void triggerSync(String syncId) {
		log.info("Cron-triggered sync starting for syncId='{}'", syncId);
		try {
			Map<String, String> result = knowledgeSyncService.startSync(syncId);
			log.info("Cron-triggered sync for '{}' started successfully: {}", syncId, result);
		}
		catch (Exception e) {
			// Don't rethrow — let the cron schedule continue for next trigger
			log.warn("Cron-triggered sync for '{}' failed: {}", syncId, e.getMessage());
		}
	}

	@PreDestroy
	public void shutdown() {
		log.info("Shutting down KnowledgeSyncScheduler — cancelling {} scheduled tasks", scheduledTasks.size());
		scheduledTasks.values().forEach(entry -> entry.future.cancel(false));
		scheduledTasks.clear();
	}

	/**
	 * Tracks a scheduled cron task entry.
	 */
	private record ScheduledEntry(String cronExpression, ScheduledFuture<?> future) {
	}

}
