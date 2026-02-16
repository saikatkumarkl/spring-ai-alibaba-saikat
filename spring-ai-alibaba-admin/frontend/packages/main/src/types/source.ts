/**
 * Source system type definitions
 */

/** Parameters for listing source systems */
export interface IGetSourceListParams {
  name?: string;
  current: number;
  size: number;
}

/** Source system list item */
export interface ISourceListItem {
  source_id: string;
  name: string;
  description: string;
  connector_type: string;
  connector_class: string;
  status: number;
  test_result: string;
  mcf_job_status: string;
  last_sync_time: string;
  sync_cron: string;
  docs_total: number;
  docs_processed: number;
  docs_failed: number;
  gmt_modified: string;
  gmt_create: string;
}

/** Source system detail */
export interface ISourceDetail {
  source_id: string;
  workspace_id: string;
  name: string;
  description: string;
  connector_type: string;
  connector_class: string;
  status: number;
  connection_config: Record<string, any>;
  test_result: string;
  mcf_connection_name: string;
  mcf_output_name: string;
  mcf_job_id: string;
  mcf_job_status: string;
  last_sync_time: string;
  sync_cron: string;
  docs_total: number;
  docs_processed: number;
  docs_failed: number;
  error_message: string;
  gmt_create: string;
  gmt_modified: string;
}

/** Parameters for creating a source system */
export interface ICreateSourceParams {
  name: string;
  description?: string;
  connector_type: string;
  connector_class: string;
  connection_config: Record<string, any>;
}

/** Parameters for updating a source system */
export interface IUpdateSourceParams {
  source_id: string;
  name?: string;
  description?: string;
  connection_config?: Record<string, any>;
  sync_cron?: string;
}

/** Connector type from ManifoldCF */
export interface IConnectorType {
  description: string;
  class_name: string;
}

/** Sync status response */
export interface ISyncStatus {
  status: string;
  message?: string;
  documents_processed?: string;
  documents_in_queue?: string;
  documents_outstanding?: string;
}

/** Test connection result */
export interface ITestConnectionResult {
  result: string;
  [key: string]: string;
}

/** Paginated list (reused from knowledge pattern) */
export interface IPagingList<T> {
  current: number;
  size: number;
  total: number;
  records: T[];
}
