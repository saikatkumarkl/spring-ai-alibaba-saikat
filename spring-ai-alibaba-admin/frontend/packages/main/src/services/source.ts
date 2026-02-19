import { request } from '@/request';
import {
  ICreateSourceParams,
  IGetSourceListParams,
  IPagingList,
  ISourceDetail,
  ISourceListItem,
  ISyncStatus,
  ITestConnectionResult,
  IUpdateSourceParams,
} from '@/types/source';

const BASE_URL = '/console/v1/source-systems';

/** List source systems with pagination */
export const getSourceList = (params: IGetSourceListParams) => {
  return request({
    url: BASE_URL,
    method: 'GET',
    params,
  }).then((res) => res.data.data as IPagingList<ISourceListItem>);
};

/** Get source system detail */
export const getSourceDetail = (sourceId: string) => {
  return request({
    url: `${BASE_URL}/${sourceId}`,
    method: 'GET',
  }).then((res) => res.data.data as ISourceDetail);
};

/** Create a new source system */
export const createSource = (params: ICreateSourceParams) => {
  return request({
    url: BASE_URL,
    method: 'POST',
    data: params,
  }).then((res) => res.data.data as string);
};

/** Update an existing source system */
export const updateSource = (params: IUpdateSourceParams) => {
  const { source_id, ...rest } = params;
  return request({
    url: `${BASE_URL}/${source_id}`,
    method: 'PUT',
    data: rest,
  }).then((res) => res.data);
};

/** Delete a source system */
export const deleteSource = (sourceId: string) => {
  return request({
    url: `${BASE_URL}/${sourceId}`,
    method: 'DELETE',
  }).then((res) => res.data);
};

/** Get available connector types from ManifoldCF */
export const getConnectorTypes = () => {
  return request({
    url: `${BASE_URL}/connector-types`,
    method: 'GET',
  }).then((res) => res.data.data as Array<Record<string, string>>);
};

/** Test connection for a source system */
export const testConnection = (sourceId: string) => {
  return request({
    url: `${BASE_URL}/${sourceId}/test-connection`,
    method: 'POST',
  }).then((res) => res.data.data as ITestConnectionResult);
};

/** Start sync (crawl job) for a source system */
export const startSync = (sourceId: string, query?: string) => {
  return request({
    url: `${BASE_URL}/${sourceId}/sync`,
    method: 'POST',
    data: query ? { query } : {},
  }).then((res) => res.data.data as string);
};

/** Get sync status for a source system */
export const getSyncStatus = (sourceId: string) => {
  return request({
    url: `${BASE_URL}/${sourceId}/sync-status`,
    method: 'GET',
  }).then((res) => res.data.data as ISyncStatus);
};

/** Abort a running sync job */
export const abortSync = (sourceId: string) => {
  return request({
    url: `${BASE_URL}/${sourceId}/abort`,
    method: 'POST',
  }).then((res) => res.data);
};

/** Update sync schedule (cron expression) */
export const updateSyncSchedule = (sourceId: string, cron: string) => {
  return request({
    url: `${BASE_URL}/${sourceId}/schedule`,
    method: 'PUT',
    data: { cron },
  }).then((res) => res.data);
};

/** Test Group/User API configuration for ACL enforcement */
export const testGroupApi = (sourceId: string) => {
  return request({
    url: `${BASE_URL}/${sourceId}/test-group-api`,
    method: 'POST',
  }).then((res) => res.data.data as Record<string, string>);
};

/** Enable a source system (validates connection + ACL before enabling) */
export const enableSource = (sourceId: string) => {
  return request({
    url: `${BASE_URL}/${sourceId}/enable`,
    method: 'POST',
  }).then((res) => res.data.data as Record<string, string>);
};

/** Test a query against the source (CMIS query, REST API seed, Group/User API) */
export const testQuery = (sourceId: string, testType: string, query?: string) => {
  return request({
    url: `${BASE_URL}/${sourceId}/test-query`,
    method: 'POST',
    data: { test_type: testType, query },
  }).then((res) => res.data.data as {
    status: string;
    count: number;
    items: Record<string, any>[];
    message: string;
  });
};

/** Copy (duplicate) a source system */
export const copySource = (sourceId: string) => {
  return request({
    url: `${BASE_URL}/${sourceId}/copy`,
    method: 'POST',
  }).then((res) => res.data.data as string);
};

/** Get sample Browse-in-Source URLs generated from real indexed documents */
export const getSampleSourceUrls = (sourceId: string, size = 10) => {
  return request({
    url: '/console/v1/chatbot/sample-source-urls',
    method: 'GET',
    params: { sourceId, size },
  }).then((res) => res.data.data as {
    sampleUrls: Array<{
      objectId: string;
      nodeId: string;
      fileName: string;
      sourceUrl: string;
    }>;
    totalDocuments: number;
    template: string;
  });
};
