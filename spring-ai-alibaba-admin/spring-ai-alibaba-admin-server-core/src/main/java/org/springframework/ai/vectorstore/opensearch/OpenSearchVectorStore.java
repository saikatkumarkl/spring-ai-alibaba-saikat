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

import com.alibaba.cloud.ai.studio.runtime.exception.BizException;
import com.alibaba.cloud.ai.studio.runtime.enums.ErrorCode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.opensearch.client.RestClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.model.EmbeddingUtils;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.observation.conventions.VectorStoreSimilarityMetric;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SearchType;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.alibaba.cloud.ai.studio.core.rag.RagConstants.SEARCH_TIMEOUT;
import static com.alibaba.cloud.ai.studio.core.utils.concurrent.ThreadPoolUtils.DEFAULT_TASK_EXECUTOR;

/**
 * OpenSearch-based vector store implementation using knn_vector field type.
 *
 * <p>Features:</p>
 * <ul>
 * <li>Automatic schema initialization with configurable index creation</li>
 * <li>Support for multiple similarity functions: Cosine, L2 Norm, and Dot Product</li>
 * <li>Metadata filtering using OpenSearch query strings</li>
 * <li>Configurable similarity thresholds for search results</li>
 * <li>Batch processing support with configurable strategies</li>
 * <li>Supports SEMANTIC, FULL_TEXT, and HYBRID search modes</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class OpenSearchVectorStore extends AbstractObservationVectorStore implements InitializingBean {

	private static final Map<SimilarityFunction, VectorStoreSimilarityMetric> SIMILARITY_TYPE_MAPPING = Map.of(
			SimilarityFunction.cosine, VectorStoreSimilarityMetric.COSINE, SimilarityFunction.l2_norm,
			VectorStoreSimilarityMetric.EUCLIDEAN, SimilarityFunction.dot_product, VectorStoreSimilarityMetric.DOT);

	private final OpenSearchClient openSearchClient;

	private final OpenSearchVectorStoreOptions options;

	private final FilterExpressionConverter filterExpressionConverter;

	private final boolean initializeSchema;

	protected OpenSearchVectorStore(Builder builder) {
		super(builder);

		Assert.notNull(builder.restClient, "RestClient must not be null");

		this.initializeSchema = builder.initializeSchema;
		this.options = builder.options;
		this.filterExpressionConverter = builder.filterExpressionConverter;

		this.openSearchClient = new OpenSearchClient(new RestClientTransport(builder.restClient,
				new JacksonJsonpMapper(
						new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false))));
	}

	@Override
	public void doAdd(List<Document> documents) {
		if (!indexExists()) {
			throw new IllegalArgumentException("Index not found");
		}
		BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();

		List<float[]> embeddings = this.embeddingModel.embed(documents, EmbeddingOptions.builder().build(),
				this.batchingStrategy);

		for (Document document : documents) {
			OpenSearchDocument doc = new OpenSearchDocument(document.getId(), document.getText(),
					document.getMetadata(), embeddings.get(documents.indexOf(document)));
			bulkRequestBuilder.operations(
					op -> op.index(idx -> idx.index(this.options.getIndexName()).id(document.getId()).document(doc)));
		}
		BulkResponse bulkRequest = bulkRequest(bulkRequestBuilder.build());
		if (bulkRequest.errors()) {
			List<BulkResponseItem> bulkResponseItems = bulkRequest.items();
			for (BulkResponseItem bulkResponseItem : bulkResponseItems) {
				if (bulkResponseItem.error() != null) {
					throw new IllegalStateException(bulkResponseItem.error().reason());
				}
			}
		}
	}

	@Override
	public void doDelete(List<String> idList) {
		BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();
		if (!indexExists()) {
			throw new IllegalArgumentException("Index not found");
		}
		for (String id : idList) {
			bulkRequestBuilder.operations(op -> op.delete(idx -> idx.index(this.options.getIndexName()).id(id)));
		}
		if (bulkRequest(bulkRequestBuilder.build()).errors()) {
			throw new IllegalStateException("Delete operation failed");
		}
	}

	@Override
	public void doDelete(Filter.Expression filterExpression) {
		if (!indexExists()) {
			throw new IllegalArgumentException("Index not found");
		}

		try {
			this.openSearchClient.deleteByQuery(d -> d.index(this.options.getIndexName())
				.query(q -> q.queryString(qs -> qs.query(getQueryString(filterExpression)))));
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to delete documents by filter", e);
		}
	}

	private BulkResponse bulkRequest(BulkRequest bulkRequest) {
		try {
			return this.openSearchClient.bulk(bulkRequest);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public List<Document> doSimilaritySearch(SearchRequest searchRequest) {
		Assert.notNull(searchRequest, "The search request must not be null.");

		return switch (searchRequest.getSearchType()) {
			case SEMANTIC -> searchBySemantic(searchRequest);
			case FULL_TEXT -> searchByFullText(searchRequest);
			case HYBRID -> searchByHybrid(searchRequest);
			default -> throw new IllegalArgumentException("Unsupported search type: " + searchRequest.getSearchType());
		};
	}

	private String getQueryString(Filter.Expression filterExpression) {
		return Objects.isNull(filterExpression) ? "*"
				: this.filterExpressionConverter.convertExpression(filterExpression);
	}

	private Document toDocument(Hit<Document> hit, SearchType searchType) {
		Document document = hit.source();
		Document.Builder documentBuilder = document.mutate();
		if (hit.score() != null) {
			documentBuilder.metadata(DocumentMetadata.DISTANCE.value(), 1 - normalizeSimilarityScore(hit.score()));

			if (searchType == SearchType.FULL_TEXT) {
				documentBuilder.score(1 - hit.score());
			}
			else {
				documentBuilder.score(normalizeSimilarityScore(hit.score()));
			}
		}
		return documentBuilder.build();
	}

	private double normalizeSimilarityScore(double score) {
		switch (this.options.getSimilarity()) {
			case l2_norm:
				return (1 - (java.lang.Math.sqrt((1 / score) - 1)));
			default:
				return (2 * score) - 1;
		}
	}

	public boolean indexExists() {
		try {
			return this.openSearchClient.indices().exists(ex -> ex.index(this.options.getIndexName())).value();
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void createIndexMapping() {
		try {
			// Use JSON-based mapping for knn_vector since OpenSearch uses different field type
			String mappingJson = String.format("""
					{
						"properties": {
							"embedding": {
								"type": "knn_vector",
								"dimension": %d,
								"method": {
									"name": "hnsw",
									"space_type": "%s",
									"engine": "lucene"
								}
							}
						}
					}
					""", this.options.getDimensions(), this.options.getSimilarity().toOpenSearchSpaceType());

			this.openSearchClient.indices()
				.create(cr -> cr.index(this.options.getIndexName())
					.settings(s -> s.knn(true))
					.mappings(m -> m
						.withJson(new ByteArrayInputStream(mappingJson.getBytes(StandardCharsets.UTF_8)))));
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void afterPropertiesSet() {
		if (!this.initializeSchema) {
			return;
		}
		if (!indexExists()) {
			createIndexMapping();
		}
	}

	@Override
	public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {
		int dimensions = this.options.getDimensions();
		if (dimensions <= 0) {
			dimensions = this.embeddingModel.dimensions();
		}

		return VectorStoreObservationContext.builder(VectorStoreProvider.ELASTICSEARCH.value(), operationName)
			.collectionName(this.options.getIndexName())
			.dimensions(dimensions)
			.similarityMetric(getSimilarityMetric());
	}

	private String getSimilarityMetric() {
		if (!SIMILARITY_TYPE_MAPPING.containsKey(this.options.getSimilarity())) {
			return this.options.getSimilarity().name();
		}
		return SIMILARITY_TYPE_MAPPING.get(this.options.getSimilarity()).value();
	}

	@Override
	public <T> Optional<T> getNativeClient() {
		@SuppressWarnings("unchecked")
		T client = (T) this.openSearchClient;
		return Optional.of(client);
	}

	public static Builder builder(RestClient restClient, EmbeddingModel embeddingModel) {
		return new Builder(restClient, embeddingModel);
	}

	/**
	 * The representation of {@link Document} along with its embedding.
	 */
	public record OpenSearchDocument(String id, String content, Map<String, Object> metadata, float[] embedding) {
	}

	public static class Builder extends AbstractVectorStoreBuilder<Builder> {

		private final RestClient restClient;

		private OpenSearchVectorStoreOptions options = new OpenSearchVectorStoreOptions();

		private boolean initializeSchema = false;

		private FilterExpressionConverter filterExpressionConverter = new OpenSearchFilterExpressionConverter();

		public Builder(RestClient restClient, EmbeddingModel embeddingModel) {
			super(embeddingModel);
			Assert.notNull(restClient, "RestClient must not be null");
			this.restClient = restClient;
		}

		public Builder options(OpenSearchVectorStoreOptions options) {
			Assert.notNull(options, "options must not be null");
			this.options = options;
			return this;
		}

		public Builder initializeSchema(boolean initializeSchema) {
			this.initializeSchema = initializeSchema;
			return this;
		}

		public Builder filterExpressionConverter(FilterExpressionConverter converter) {
			Assert.notNull(converter, "filterExpressionConverter must not be null");
			this.filterExpressionConverter = converter;
			return this;
		}

		@Override
		public OpenSearchVectorStore build() {
			return new OpenSearchVectorStore(this);
		}

	}

	protected List<Document> searchBySemantic(SearchRequest searchRequest) {
		try {
			float[] vectors = this.embeddingModel.embed(searchRequest.getQuery());
			String filterQueryString = getQueryString(searchRequest.getFilterExpression());

			List<Float> vectorList = EmbeddingUtils.toList(vectors);

			SearchResponse<Document> res;
			if ("*".equals(filterQueryString)) {
				res = this.openSearchClient.search(sr -> sr.index(this.options.getIndexName())
					.query(q -> q.knn(knn -> knn
						.field("embedding")
						.vector(vectors)
						.k(searchRequest.getTopK())))
					.size(searchRequest.getTopK()), Document.class);
			}
			else {
				Query filterQuery = Query.of(fq -> fq.queryString(qs -> qs.query(filterQueryString)));
				res = this.openSearchClient.search(sr -> sr.index(this.options.getIndexName())
					.query(q -> q.knn(knn -> knn
						.field("embedding")
						.vector(vectors)
						.k(searchRequest.getTopK())
						.filter(filterQuery)))
					.size(searchRequest.getTopK()), Document.class);
			}

			return res.hits()
				.hits()
				.stream()
				.filter(hit -> hit.score() != null
						&& normalizeSimilarityScore(hit.score()) >= searchRequest.getSimilarityThreshold())
				.map(x -> toDocument(x, SearchType.SEMANTIC))
				.collect(Collectors.toList());
		}
		catch (IOException e) {
			throw new BizException(ErrorCode.DOCUMENT_RETRIEVAL_ERROR.toError(), e);
		}
	}

	protected List<Document> searchByFullText(SearchRequest searchRequest) {
		try {
			SearchResponse<Document> res = this.openSearchClient.search(
					sr -> sr.index(this.options.getIndexName())
						.query(q -> q.bool(m -> m
							.must(qm -> qm.match(mm -> mm.field("content").query(fv -> fv.stringValue(searchRequest.getQuery()))))
							.filter(fl -> fl.queryString(
									qs -> qs.query(getQueryString(searchRequest.getFilterExpression()))))))
						.minScore(searchRequest.getSimilarityThreshold())
						.size((int) (1.5 * searchRequest.getTopK())),
					Document.class);

			return res.hits()
				.hits()
				.stream()
				.map(x -> toDocument(x, SearchType.FULL_TEXT))
				.collect(Collectors.toList());
		}
		catch (IOException e) {
			throw new BizException(ErrorCode.DOCUMENT_RETRIEVAL_ERROR.toError(), e);
		}
	}

	protected List<Document> searchByHybrid(SearchRequest searchRequest) {
		List<CompletableFuture<List<Document>>> futureList = new ArrayList<>();

		if (searchRequest.getHybridWeight() < 0 || searchRequest.getHybridWeight() > 1) {
			throw new IllegalArgumentException("hybrid alpha should be between 0 ~ 1.");
		}

		try {
			CompletableFuture<List<Document>> textFuture = CompletableFuture.supplyAsync(() -> {
				int textTopK = Math.round(searchRequest.getTopK() * (1 - searchRequest.getHybridWeight()));
				return searchByFullText(SearchRequest.builder()
					.query(searchRequest.getQuery())
					.similarityThreshold(searchRequest.getSimilarityThreshold())
					.topK(textTopK)
					.filterExpression(searchRequest.getFilterExpression())
					.build());
			}, DEFAULT_TASK_EXECUTOR);
			futureList.add(textFuture);

			CompletableFuture<List<Document>> vectorFuture = CompletableFuture.supplyAsync(() -> {
				int textTopK = Math.round(searchRequest.getTopK() * searchRequest.getHybridWeight());
				return searchBySemantic(SearchRequest.builder()
					.query(searchRequest.getQuery())
					.similarityThreshold(searchRequest.getSimilarityThreshold())
					.topK(textTopK)
					.filterExpression(searchRequest.getFilterExpression())
					.build());
			}, DEFAULT_TASK_EXECUTOR);
			futureList.add(vectorFuture);

			for (CompletableFuture<List<Document>> future : futureList) {
				future.get(SEARCH_TIMEOUT, TimeUnit.SECONDS);
			}

			List<Document> vectorList = vectorFuture.get() == null ? new ArrayList<>() : vectorFuture.get();
			List<Document> fullTextList = textFuture.get() == null ? new ArrayList<>() : textFuture.get();

			return Stream.concat(vectorList.stream(), fullTextList.stream())
				.collect(Collectors.toMap(Document::getId, student -> student, (existing, newStudent) -> existing))
				.values()
				.stream()
				.toList();
		}
		catch (InterruptedException | ExecutionException e) {
			throw new BizException(ErrorCode.DOCUMENT_RETRIEVAL_ERROR.toError(), e);
		}
		catch (TimeoutException e) {
			throw new BizException(ErrorCode.DOCUMENT_RETRIEVAL_TIMEOUT.toError(), e);
		}
	}

}
