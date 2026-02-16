export interface ISourceCard {
  source_id: string;
  name: string;
  description: string;
  connector_type: string;
  status: number;
  test_result: string;
  mcf_job_status: string;
  docs_total: number;
  docs_processed: number;
  docs_failed: number;
  last_sync_time: string;
  gmt_modified: string;
}
