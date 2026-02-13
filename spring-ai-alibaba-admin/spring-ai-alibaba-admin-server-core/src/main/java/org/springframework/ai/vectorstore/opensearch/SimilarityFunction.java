/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.vectorstore.opensearch;

/**
 * Similarity functions supported by OpenSearch knn_vector fields.
 * Maps to OpenSearch space_type parameter in knn_vector mapping.
 *
 * @since 1.0.0
 */
public enum SimilarityFunction {

	/** Cosine similarity. Maps to OpenSearch space_type "cosinesimil". */
	cosine("cosinesimil"),

	/** L2 (Euclidean) distance. Maps to OpenSearch space_type "l2". */
	l2_norm("l2"),

	/** Dot product / inner product. Maps to OpenSearch space_type "innerproduct". */
	dot_product("innerproduct");

	private final String openSearchSpaceType;

	SimilarityFunction(String openSearchSpaceType) {
		this.openSearchSpaceType = openSearchSpaceType;
	}

	/**
	 * Returns the OpenSearch space_type string for use in knn_vector mapping.
	 */
	public String toOpenSearchSpaceType() {
		return openSearchSpaceType;
	}

}
