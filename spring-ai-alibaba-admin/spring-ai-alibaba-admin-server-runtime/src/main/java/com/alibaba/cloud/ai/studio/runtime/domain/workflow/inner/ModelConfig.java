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
package com.alibaba.cloud.ai.studio.runtime.domain.workflow.inner;

import com.alibaba.cloud.ai.studio.runtime.domain.workflow.Node;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Model configuration information
 *
 * @since 1.0.0.3
 */
@Data
public class ModelConfig implements Serializable {

	/**
	 * Model ID, corresponding to the name under model
	 */
	@JsonProperty("model_id")
	private String modelId;

	/**
	 * Model name
	 */
	@JsonProperty("model_name")
	private String modelName;

	/**
	 * model provider
	 */
	private String provider;

	/**
	 * Model parameters
	 */
	private List<ModelParam> params;

	/**
	 * Model mode: chat or completion
	 */
	private String mode;

	/**
	 * Visual parameter list
	 */
	@JsonProperty("vision_config")
	private SkillConfig visionConfig;

	@Data
	public static class SkillConfig implements Serializable {

		@JsonProperty("enable")
		private Boolean enable;

		private List<Node.InputParam> params;

	}

	/**
	 * Model parameter configuration
	 */
	@Data
	public static class ModelParam implements Serializable {

		/**
		 * Parameter key name
		 */
		private String key;

		/**
		 * Parameter type
		 */
		private String type;

		/**
		 * default value
		 */
		@JsonProperty("default_value")
		private Object defaultValue;

		/**
		 * Parameter value
		 */
		private Object value;

		/**
		 * Parameter switch
		 */
		@JsonProperty("enable")
		private Boolean enable;

	}

}
