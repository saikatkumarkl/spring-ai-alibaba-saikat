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
package com.alibaba.cloud.ai.studio.runtime.domain.workflow;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * Edge of The Graph canvas edge declaration
 *
 * @since 1.0.0.3
 */
@Data
public class Edge implements Cloneable, Serializable {

	/**
	 * id of the edge id of the edge
	 */
	private String id;

	/**
	 * source node id of the edge source node id of the edge
	 */
	private String source;

	/**
	 * If the source node has multiple outgoing connection points, sourceHandle is
	 * formatted as {source}_{connectionPointId};Otherwise (if there’s only one connection
	 * point), sourceHandle simply uses {source}.
	 * The source node of an edge has multiple outgoing connection points, and sourceHandle is formatted as {source}_{id of the connection point}; otherwise sourceHandle is {source}
	 */
	@JsonProperty("source_handle")
	private String sourceHandle;

	/**
	 * target node id of the edge target node id of the edge
	 */
	private String target;

	/**
	 * If the target node has multiple incoming connection points, targetHandle is
	 * formatted as {target}_{connectionPointId};Otherwise (if there’s only one connection
	 * point), targetHandle simply uses {target}.
	 * The destination node of the edge has multiple incoming connection points, and the targetHandle is formatted as {target}_{id of the connection point}; otherwise the targetHandle is formatted as {target}
	 */
	@JsonProperty("target_handle")
	private String targetHandle;

	@Override
	public Object clone() {
		try {
			return super.clone();
		}
		catch (CloneNotSupportedException e) {
			// shouldn't happen as we are Cloneable
			throw new InternalError();
		}
	}

}
