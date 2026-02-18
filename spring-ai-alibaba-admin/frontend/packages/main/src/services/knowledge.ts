import { IChunkItem } from '@/pages/Knowledge/Detail/type';
import { request } from '@/request';
import { baseURL, session } from '@/request/request';
import { IApiResponse } from '@/types/common';
import {
  IChunksListItem,
  ICreateDocumentParams,
  ICreateKnowledgeListParams,
  IGetKnowledgeListParams,
  IKnowledgeDetail,
  IKnowledgeListItem,
  IPagingList,
  IUpdateChunksContentParams,
  IUpdateChunksParams,
  IUpdateStatusChunksParams,
} from '@/types/knowledge';

/**
 * Get knowledge base list
 * @param params Query parameters including pagination and filters
 * @returns Promise containing paginated list of knowledge bases
 */
export const getKnowledgeList = (params: IGetKnowledgeListParams) => {
  return request({
    url: '/console/v1/knowledge-bases',
    method: 'GET',
    params,
  }).then((res) => res.data.data as IPagingList<IKnowledgeListItem>);
};

/**
 * Create new knowledge base
 * @param params Knowledge base creation parameters
 * @returns Promise containing API response with knowledge base ID
 */
export const createKnowledge = (params: ICreateKnowledgeListParams) => {
  return request({
    url: '/console/v1/knowledge-bases',
    method: 'POST',
    data: params,
  }).then((res) => res.data.data as string);
};

/**
 * Update knowledge base
 * @param params Knowledge base update parameters
 * @returns Promise containing API response
 */
export const updateKnowledge = (params: ICreateKnowledgeListParams) => {
  const { kb_id, ...rest } = params;
  return request({
    url: `/console/v1/knowledge-bases/${kb_id}`,
    method: 'PUT',
    data: rest,
  }).then((res) => res.data as IApiResponse<string>);
};

/**
 * Delete knowledge base
 * @param kb_id Knowledge base ID to delete
 * @returns Promise containing API response
 */
export const deleteKnowledge = (kb_id: string) => {
  return request({
    url: `/console/v1/knowledge-bases/${kb_id}`,
    method: 'DELETE',
  }).then((res) => res.data as IApiResponse<string>);
};

/**
 * Get knowledge base details
 * @param kb_id Knowledge base ID
 * @returns Promise containing knowledge base details
 */
export const getKnowledgeDetail = (kb_id: string) => {
  return request({
    url: `/console/v1/knowledge-bases/${kb_id}`,
    method: 'GET',
  }).then((res) => res.data.data as IKnowledgeDetail);
};

/**
 * Test knowledge base retrieval
 * @param params Query parameters including search options
 * @returns Promise containing retrieval test results
 */
export const getKnowledgeRetrieve = (params: {
  query: string;
  search_options: {
    kb_ids: string[];
    similarity_threshold: number;
  };
}) => {
  return request({
    url: `/console/v1/knowledge-bases/retrieve`,
    method: 'POST',
    data: params,
  }).then((res) => {
    if (res) {
      return res.data.data;
    }
  });
};
/**
 * Get document list for a knowledge base
 * @param params Query parameters including pagination and filters
 * @returns Promise containing paginated list of documents
 */
export const getDocumentsList = (params: {
  size: number;
  current: number;
  kb_id: string;
  name?: string;
  index_status?: string;
}) => {
  return request({
    url: `/console/v1/knowledge-bases/${params.kb_id}/documents`,
    method: 'GET',
    params,
  }).then(
    (res) => res.data.data as IApiResponse<IPagingList<IKnowledgeListItem>>,
  );
};

/**
 * Create new document in knowledge base
 * @param params Document creation parameters
 * @returns Promise containing API response with document ID
 */
export const createDocuments = (params: ICreateDocumentParams) => {
  return request({
    url: `/console/v1/knowledge-bases/${params.kb_id}/documents`,
    method: 'POST',
    data: params,
  }).then((res) => res.data.data as IApiResponse<string>);
};

/**
 * Delete document from knowledge base
 * @param kb_id Knowledge base ID
 * @param doc_id Document ID to delete
 * @returns Promise containing API response
 */
export const deleteDocuments = (kb_id: string, doc_id: string) => {
  return request({
    url: `/console/v1/knowledge-bases/${kb_id}/documents/${doc_id}`,
    method: 'DELETE',
  }).then((res) => res.data.data as IApiResponse<string>);
};

/**
 * Batch delete documents from knowledge base
 * @param params Object containing knowledge base ID and document IDs
 * @returns Promise containing API response
 */
export const batchDeleteDocuments = (params: {
  kb_id: string;
  doc_ids: string[];
}) => {
  return request({
    url: `/console/v1/knowledge-bases/${params?.kb_id}/documents/batch-delete`,
    method: 'DELETE',
    data: params,
  }).then((res) => res.data.data as IApiResponse<string>);
};

/**
 * Batch update document enabled status
 * @param params Object containing knowledge base ID, document IDs, and enabled flag
 * @returns Promise containing API response
 */
export const batchUpdateDocumentEnabled = (params: {
  kb_id: string;
  doc_ids: string[];
  enabled: boolean;
}) => {
  return request({
    url: `/console/v1/knowledge-bases/${params.kb_id}/documents/batch-enabled?enabled=${params.enabled}`,
    method: 'PUT',
    data: params.doc_ids,
  }).then((res) => res.data.data as IApiResponse<string>);
};

/**
 * Get chunk list for a document
 * @param param0 Object containing document ID and pagination parameters
 * @returns Promise containing paginated list of chunks
 */
export const getChunksList = ({
  doc_id,
  current,
  size,
}: {
  doc_id: string;
  current: number;
  size: number;
}) => {
  return request({
    url: `/console/v1/documents/${doc_id}/chunks`,
    method: 'GET',
    params: {
      current,
      size,
    },
  }).then((res) => res.data.data as IApiResponse<IPagingList<IChunksListItem>>);
};

/**
 * Delete chunk from document
 * @param param0 Object containing document ID and chunk ID
 * @returns Promise containing API response
 */
export const deleteChunks = ({
  doc_id,
  chunk_id,
}: {
  doc_id: string;
  chunk_id: string;
}) => {
  return request({
    url: `/console/v1/documents/${doc_id}/chunks/${chunk_id}`,
    method: 'DELETE',
  }).then((res) => res.data.data as IApiResponse<string>);
};

/**
 * Update chunk content
 * @param params Object containing document ID, chunk ID and new content
 * @returns Promise containing API response
 */
export const updateChunksContent = (params: IUpdateChunksContentParams) => {
  const { doc_id, chunk_id, ...rest } = params;
  return request({
    url: `/console/v1/documents/${doc_id}/chunks/${chunk_id}`,
    method: 'PUT',
    data: rest,
  }).then((res) => res.data.data as IApiResponse<string>);
};

/**
 * Update chunk settings and re-index document
 * @param params Object containing document ID, knowledge base ID and new settings
 * @returns Promise containing API response
 */
export const updateChunks = (params: IUpdateChunksParams) => {
  const { doc_id, kb_id, ...rest } = params;
  return request({
    url: `/console/v1/knowledge-bases/${kb_id}/documents/${doc_id}/re-index`,
    method: 'PUT',
    data: rest,
  }).then((res) => res.data.data as IApiResponse<string>);
};

/**
 * Preview chunk settings changes
 * @param params Object containing document ID and new settings
 * @returns Promise containing API response
 */
export const previewChunks = (params: IUpdateChunksParams) => {
  const { doc_id, ...rest } = params;
  return request({
    url: `/console/v1/documents/${doc_id}/chunks/preview`,
    method: 'POST',
    data: rest,
  }).then((res) => res.data.data as IChunkItem[]);
};

/**
 * Enable/disable chunks
 * @param params Object containing document ID and chunk status
 * @returns Promise containing API response
 */
export const updateStatusChunks = (params: IUpdateStatusChunksParams) => {
  const { doc_id, ...rest } = params;
  return request({
    url: `/console/v1/documents/${doc_id}/chunks/update-status`,
    method: 'PUT',
    data: rest,
  }).then((res) => res.data.data as IApiResponse<string>);
};

/**
 * Get knowledge bases by their codes
 * @param kb_ids Array of knowledge base IDs
 * @returns Promise containing list of knowledge base items
 */
export const getKnowledgeListByCodes = (kb_ids: string[]) => {
  return request({
    url: '/console/v1/knowledge-bases/query-by-codes',
    method: 'POST',
    data: {
      kb_ids,
    },
  }).then((res) => res.data.data as IKnowledgeListItem[]);
};

// ---- Knowledge Sync APIs ----

/** Create a sync job for a knowledge base */
export const createKnowledgeSync = (kbId: string, params: {
  source_id: string;
  destination_id: string;
  sync_cron?: string;
}) => {
  return request({
    url: `/console/v1/knowledge-bases/${kbId}/sync`,
    method: 'POST',
    data: params,
  }).then((res) => res.data.data as string);
};

/** Get sync info for a knowledge base */
export const getKnowledgeSync = (kbId: string) => {
  return request({
    url: `/console/v1/knowledge-bases/${kbId}/sync`,
    method: 'GET',
  }).then((res) => res.data.data);
};

/** List all syncs for a knowledge base */
export const listKnowledgeSyncs = (kbId: string) => {
  return request({
    url: `/console/v1/knowledge-bases/${kbId}/syncs`,
    method: 'GET',
  }).then((res) => res.data.data as any[]);
};

/** Start a sync job */
export const startKnowledgeSync = (syncId: string) => {
  return request({
    url: `/console/v1/knowledge-bases/sync/${syncId}/start`,
    method: 'POST',
  }).then((res) => res.data.data);
};

/** Get sync status */
export const getKnowledgeSyncStatus = (syncId: string) => {
  return request({
    url: `/console/v1/knowledge-bases/sync/${syncId}/status`,
    method: 'GET',
  }).then((res) => res.data.data);
};

/** Delete a sync job */
export const deleteKnowledgeSync = (syncId: string) => {
  return request({
    url: `/console/v1/knowledge-bases/sync/${syncId}`,
    method: 'DELETE',
  }).then((res) => res.data);
};

/** Update sync cron schedule */
export const updateKnowledgeSyncCron = (syncId: string, cron: string) => {
  return request({
    url: `/console/v1/knowledge-bases/sync/${syncId}/schedule`,
    method: 'PUT',
    data: { cron },
  }).then((res) => res.data);
};

/** Start only the document sync phase (ManifoldCF crawl → OpenSearch) */
export const syncKnowledgeDocuments = (syncId: string) => {
  return request({
    url: `/console/v1/knowledge-bases/sync/${syncId}/sync-documents`,
    method: 'POST',
  }).then((res) => res.data.data);
};

/** Start only the RAG reindex phase (OpenSearch → RAG vector embeddings) */
export const reindexKnowledgeRag = (syncId: string) => {
  return request({
    url: `/console/v1/knowledge-bases/sync/${syncId}/reindex-rag`,
    method: 'POST',
  }).then((res) => res.data.data);
};

/** List documents from OpenSearch index for source-based KBs */
export const listSyncDocuments = (kbId: string, params: {
  current: number;
  size: number;
  query?: string;
}) => {
  return request({
    url: `/console/v1/knowledge-bases/${kbId}/sync-documents`,
    method: 'GET',
    params,
  }).then((res) => res.data.data);
};

/** Hard reset: delete all indices and reset sync to pending */
export const hardResetSync = (syncId: string) => {
  return request({
    url: `/console/v1/knowledge-bases/sync/${syncId}/hard-reset`,
    method: 'POST',
  }).then((res) => res.data.data);
};

/** Stop a running sync job */
export const stopSync = (syncId: string) => {
  return request({
    url: `/console/v1/knowledge-bases/sync/${syncId}/stop`,
    method: 'POST',
  }).then((res) => res.data.data);
};

/** Browse documents in the document index for a sync job */
export const browseDocumentIndex = (syncId: string, params: {
  current: number;
  size: number;
  query?: string;
}) => {
  return request({
    url: `/console/v1/knowledge-bases/sync/${syncId}/browse`,
    method: 'GET',
    params,
  }).then((res) => res.data.data);
};

/** Get a single document's full detail from the document index */
export const getDocumentDetail = (syncId: string, docId: string) => {
  return request({
    url: `/console/v1/knowledge-bases/sync/${syncId}/document`,
    method: 'GET',
    params: { docId },
  }).then((res) => res.data.data);
};

/** Update document metadata in the document index */
export const updateDocumentMetadata = (syncId: string, docId: string, metadata: Record<string, any>) => {
  return request({
    url: `/console/v1/knowledge-bases/sync/${syncId}/document/metadata`,
    method: 'PUT',
    params: { docId },
    data: metadata,
  }).then((res) => res.data.data);
};

/** Get RAG chunks for a specific document */
export const getDocumentChunks = (syncId: string, docId: string, params: {
  current: number;
  size: number;
}) => {
  return request({
    url: `/console/v1/knowledge-bases/sync/${syncId}/document/chunks`,
    method: 'GET',
    params: { docId, ...params },
  }).then((res) => res.data.data);
};

/** Update a single RAG chunk's content */
export const updateChunkContent = (syncId: string, chunkId: string, content: string) => {
  return request({
    url: `/console/v1/knowledge-bases/sync/${syncId}/chunk`,
    method: 'PUT',
    params: { chunkId },
    data: { content },
  }).then((res) => res.data.data);
};

/** Download the original document from the CMIS source system (triggers browser download) */
export const downloadSourceDocument = async (syncId: string, docId: string, fileName?: string) => {
  const url = `${baseURL.get()}/console/v1/knowledge-bases/sync/${syncId}/document/download?docId=${encodeURIComponent(docId)}`;
  try {
    const response = await fetch(url, {
      headers: {
        Authorization: `Bearer ${session.get()}`,
      },
    });
    if (!response.ok) {
      throw new Error(`Download failed: ${response.statusText}`);
    }
    const blob = await response.blob();
    const blobUrl = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = blobUrl;
    if (fileName) a.download = fileName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(blobUrl);
  } catch (e) {
    console.error('Download failed:', e);
    throw e;
  }
};

/** Re-RAG specific documents: delete old chunks and re-chunk/re-embed selected documents */
export const reragDocuments = (syncId: string, docIds: string[]) => {
  return request({
    url: `/console/v1/knowledge-bases/sync/${syncId}/rerag-documents`,
    method: 'POST',
    data: docIds,
  }).then((res) => res.data.data);
};
