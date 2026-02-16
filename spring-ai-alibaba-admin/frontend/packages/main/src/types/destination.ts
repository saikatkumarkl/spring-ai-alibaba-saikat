/**
 * Destination system type definitions
 */

/** Parameters for listing destinations */
export interface IGetDestinationListParams {
  name?: string;
  current: number;
  size: number;
}

/** Destination list item */
export interface IDestinationListItem {
  destination_id: string;
  name: string;
  description: string;
  provider_type: string;
  status: number;
  test_result: string;
  gmt_modified: string;
  gmt_create: string;
}

/** Destination detail */
export interface IDestinationDetail {
  destination_id: string;
  workspace_id: string;
  name: string;
  description: string;
  provider_type: string;
  status: number;
  connection_config: Record<string, any>;
  test_result: string;
  gmt_create: string;
  gmt_modified: string;
  creator: string;
  modifier: string;
}

/** Parameters for creating a destination */
export interface ICreateDestinationParams {
  name: string;
  description?: string;
  provider_type: string;
  connection_config: Record<string, any>;
}

/** Parameters for updating a destination */
export interface IUpdateDestinationParams {
  destination_id: string;
  name?: string;
  description?: string;
  connection_config?: Record<string, any>;
}

/** Provider type option */
export interface IProviderType {
  type: string;
  label: string;
  description: string;
}

/** Test connection result */
export interface ITestConnectionResult {
  status: string;
  message: string;
  [key: string]: string;
}

/** Paginated list */
export interface IPagingList<T> {
  current: number;
  size: number;
  total: number;
  records: T[];
}
