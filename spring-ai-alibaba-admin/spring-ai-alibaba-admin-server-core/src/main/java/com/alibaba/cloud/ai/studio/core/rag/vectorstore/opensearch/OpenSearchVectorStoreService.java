/*
 * Copyright 2025 the original author or authors.
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

package com.alibaba.cloud.ai.studio.core.rag.vectorstore.opensearch;

import com.alibaba.cloud.ai.studio.runtime.exception.BizException;
import com.alibaba.cloud.ai.studio.runtime.enums.ErrorCode;
import com.alibaba.cloud.ai.studio.runtime.domain.PagingList;
import com.alibaba.cloud.ai.studio.runtime.domain.knowledgebase.DocumentChunk;
import com.alibaba.cloud.ai.studio.runtime.domain.knowledgebase.IndexConfig;
import com.alibaba.cloud.ai.studio.core.model.embedding.DefaultBatchingStrategy;
import com.alibaba.cloud.ai.studio.core.model.embedding.EmbeddingModelDimension;
import com.alibaba.cloud.ai.studio.core.model.llm.ModelFactory;
import com.alibaba.cloud.ai.studio.core.rag.RagConstants;
import com.alibaba.cloud.ai.studio.core.rag.vectorstore.VectorStoreService;
import com.alibaba.cloud.ai.studio.core.rag.DocumentChunkConverter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.RestClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.opensearch.OpenSearchFilterExpressionConverter;
import org.springframework.ai.vectorstore.opensearch.OpenSearchVectorStore;
import org.springframework.ai.vectorstore.opensearch.OpenSearchVectorStoreOptions;
import org.springframework.ai.vectorstore.opensearch.SimilarityFunction;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.alibaba.cloud.ai.studio.core.rag.RagConstants.*;

/**
 * OpenSearch vector store service implementation. Provides functionality for managing
 * vector indices and document chunks in OpenSearch.
 *
 * @since 1.0.0
 */
@Service
@Slf4j
@Qualifier("openSearchVectorStoreService")
public class OpenSearchVectorStoreService implements VectorStoreService {

	/** Factory for creating embedding models */
	private final ModelFactory modelFactory;

	/** OpenSearch client for index operations */
	private final OpenSearchClient openSearchClient;

	/** REST client for vector store operations */
	private final RestClient restClient;

	/** Converter for filter expressions to OpenSearch queries */
	private final FilterExpressionConverter filterExpressionConverter = new OpenSearchFilterExpressionConverter();

	@Value("${rag.opensearch.shards:1}")
	private int shards;

	@Value("${rag.opensearch.replicas:0}")
	private int replicas;

	public OpenSearchVectorStoreService(ModelFactory modelFactory, OpenSearchClient openSearchClient,
			RestClient restClient) {
		this.modelFactory = modelFactory;
		this.openSearchClient = openSearchClient;
		this.restClient = restClient;
	}

	/**
	 * Creates a new OpenSearch index with knn_vector search capabilities
	 * @param indexConfig Configuration for the index including name and embedding model
	 */
	@Override
	public void createIndex(IndexConfig indexConfig) {
		String indexName = indexConfig.getName();

		if (StringUtils.isBlank(indexName)) {
			throw new IllegalArgumentException("OpenSearch index name must be provided");
		}

		int dimension = EmbeddingModelDimension.getDimension(indexConfig.getEmbeddingModel(), DEFAULT_DIMENSION);
		String spaceType = SimilarityFunction.cosine.toOpenSearchSpaceType();

		// Build the mapping JSON for knn_vector type
		String mappingJson = String.format("""
				{
					"properties": {
						"%s": {
							"type": "knn_vector",
							"dimension": %d,
							"method": {
								"name": "hnsw",
								"space_type": "%s",
								"engine": "lucene"
							}
						},
						"%s": {
							"type": "text"
						},
						"metadata": {
							"type": "object",
							"properties": {
								"%s": { "type": "keyword" },
								"%s": { "type": "keyword" },
								"%s": { "type": "keyword" },
								"%s": { "type": "keyword" }
							}
						}
					}
				}
				""", RagConstants.VECTOR_FIELD, dimension, spaceType,
				RagConstants.TEXT_FIELD,
				KEY_WORKSPACE_ID, KEY_DOC_ID, KEY_ENABLED, KEY_CHUNK_INDEX);

		try {
			var indexResponse = openSearchClient.indices()
				.create(createIndexBuilder -> createIndexBuilder.index(indexName)
					.settings(s -> s.numberOfShards(String.valueOf(shards))
						.numberOfReplicas(String.valueOf(replicas)).knn(true))
					.mappings(m -> m
						.withJson(new ByteArrayInputStream(mappingJson.getBytes(StandardCharsets.UTF_8)))));

			if (!indexResponse.acknowledged()) {
				throw new RuntimeException("failed to create index");
			}

			log.info("create opensearch index {} successfully", indexName);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Deletes an existing OpenSearch index
	 * @param indexConfig Configuration containing the index name to delete
	 */
	@Override
	public void deleteIndex(IndexConfig indexConfig) {
		String indexName = indexConfig.getName();
		try {
			openSearchClient.indices().delete(idx -> idx.index(indexName));
		}
		catch (OpenSearchException ex) {
			if (ex.response().status() == 404) {
				log.warn("index {} not found", indexName);
			}
			else {
				throw new RuntimeException(ex);
			}
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Creates and returns a vector store instance for the specified index
	 * @param indexConfig Configuration for the index
	 * @return Configured vector store instance
	 */
	@Override
	public VectorStore getVectorStore(IndexConfig indexConfig) {
		EmbeddingModel embeddingModel = modelFactory.getEmbeddingModel(MetadataMode.EMBED, indexConfig);

		int dimension = EmbeddingModelDimension.getDimension(indexConfig.getEmbeddingModel(), DEFAULT_DIMENSION);
		OpenSearchVectorStoreOptions storeOptions = new OpenSearchVectorStoreOptions();
		storeOptions.setIndexName(indexConfig.getName());
		storeOptions.setSimilarity(SimilarityFunction.cosine);
		storeOptions.setDimensions(dimension);

		return OpenSearchVectorStore.builder(restClient, embeddingModel)
			.options(storeOptions)
			.initializeSchema(false)
			.batchingStrategy(new DefaultBatchingStrategy())
			.build();
	}

	/**
	 * Lists document chunks from the index with pagination support
	 * @param indexConfig Index configuration
	 * @param searchRequest Search parameters including pagination and filters
	 * @return Paginated list of document chunks
	 */
	public PagingList<DocumentChunk> listDocumentChunks(IndexConfig indexConfig, SearchRequest searchRequest) {
		try {
			int from = searchRequest.getFrom();
			int size = searchRequest.getTopK();
			String queryString = Objects.isNull(searchRequest.getFilterExpression()) ? "*"
					: this.filterExpressionConverter.convertExpression(searchRequest.getFilterExpression());
			SearchResponse<Document> res = this.openSearchClient.search(sr -> sr.index(indexConfig.getName())
				.query(q -> q.queryString(qs -> qs.query(queryString)))
				.from(searchRequest.getFrom())
				.size(searchRequest.getTopK()), Document.class);

			List<DocumentChunk> chunks;
			List<Hit<Document>> hits = res.hits().hits();
			if (CollectionUtils.isEmpty(hits)) {
				chunks = new ArrayList<>();
			}
			else {
				chunks = hits.stream()
					.filter(x -> x.source() != null)
					.map(x -> DocumentChunkConverter.toDocumentChunk(x.source()))
					.toList();
			}

			long total = res.hits().total() == null ? 0 : res.hits().total().value();
			int current = (from / size) + 1;
			return new PagingList<>(current, size, total, chunks);
		}
		catch (IOException e) {
			throw new BizException(ErrorCode.DOCUMENT_RETRIEVAL_ERROR.toError(), e);
		}
	}

	/**
	 * Updates multiple document chunks in the index
	 * @param indexConfig Index configuration
	 * @param chunks List of document chunks to update
	 */
	@Override
	public void updateDocumentChunks(IndexConfig indexConfig, List<DocumentChunk> chunks) {
		try {
			EmbeddingModel embeddingModel = modelFactory.getEmbeddingModel(MetadataMode.EMBED, indexConfig);
			BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();

			List<Document> documents = chunks.stream().map(DocumentChunkConverter::toDocument).toList();
			List<float[]> embeddings = embeddingModel.embed(documents, EmbeddingOptions.builder().build(),
					new DefaultBatchingStrategy());

			for (Document document : documents) {
				OpenSearchVectorStore.OpenSearchDocument doc = new OpenSearchVectorStore.OpenSearchDocument(
						document.getId(), document.getText(), document.getMetadata(),
						embeddings.get(documents.indexOf(document)));
				bulkRequestBuilder.operations(op -> op
					.update(idx -> idx.index(indexConfig.getName()).id(document.getId()).document(doc)));
			}

			bulkUpdate(bulkRequestBuilder.build());
		}
		catch (IOException e) {
			throw new BizException(ErrorCode.UPDATE_DOCUMENT_CHUNK_ERROR.toError(), e);
		}
	}

	/**
	 * Updates the enabled status of multiple document chunks
	 * @param indexConfig Index configuration
	 * @param chunkIds List of chunk IDs to update
	 * @param enabled New enabled status
	 */
	@Override
	public void updateDocumentChunkStatus(IndexConfig indexConfig, List<String> chunkIds, boolean enabled) {
		try {
			BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();
			for (String chunkId : chunkIds) {
				OpenSearchVectorStore.OpenSearchDocument doc = new OpenSearchVectorStore.OpenSearchDocument(
						chunkId, null, Map.of(KEY_ENABLED, enabled), null);
				bulkRequestBuilder.operations(
						op -> op.update(idx -> idx.index(indexConfig.getName()).id(chunkId).document(doc)));
			}

			bulkUpdate(bulkRequestBuilder.build());
		}
		catch (IOException e) {
			throw new BizException(ErrorCode.UPDATE_DOCUMENT_CHUNK_ERROR.toError(), e);
		}
	}

	/**
	 * Performs a bulk update operation and handles any errors
	 * @param request Bulk update request
	 * @throws IOException if the update operation fails
	 */
	private void bulkUpdate(BulkRequest request) throws IOException {
		BulkResponse bulkRequest = this.openSearchClient.bulk(request);
		if (bulkRequest.errors()) {
			List<BulkResponseItem> bulkResponseItems = bulkRequest.items();
			for (BulkResponseItem bulkResponseItem : bulkResponseItems) {
				if (bulkResponseItem.error() != null) {
					throw new IllegalStateException(bulkResponseItem.error().reason());
				}
			}
		}
	}

}
