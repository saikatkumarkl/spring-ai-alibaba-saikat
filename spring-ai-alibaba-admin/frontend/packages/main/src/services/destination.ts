import { request } from '@/request';
import {
  ICreateDestinationParams,
  IDestinationDetail,
  IDestinationListItem,
  IGetDestinationListParams,
  IPagingList,
  ITestConnectionResult,
  IUpdateDestinationParams,
} from '@/types/destination';

const BASE_URL = '/console/v1/destinations';

/** List destinations with pagination */
export const getDestinationList = (params: IGetDestinationListParams) => {
  return request({
    url: BASE_URL,
    method: 'GET',
    params,
  }).then((res) => res.data.data as IPagingList<IDestinationListItem>);
};

/** Get destination detail */
export const getDestinationDetail = (destinationId: string) => {
  return request({
    url: `${BASE_URL}/${destinationId}`,
    method: 'GET',
  }).then((res) => res.data.data as IDestinationDetail);
};

/** Create a new destination */
export const createDestination = (params: ICreateDestinationParams) => {
  return request({
    url: BASE_URL,
    method: 'POST',
    data: params,
  }).then((res) => res.data.data as string);
};

/** Update an existing destination */
export const updateDestination = (params: IUpdateDestinationParams) => {
  const { destination_id, ...rest } = params;
  return request({
    url: `${BASE_URL}/${destination_id}`,
    method: 'PUT',
    data: rest,
  }).then((res) => res.data);
};

/** Delete a destination */
export const deleteDestination = (destinationId: string) => {
  return request({
    url: `${BASE_URL}/${destinationId}`,
    method: 'DELETE',
  }).then((res) => res.data);
};

/** Get available provider types */
export const getProviderTypes = () => {
  return request({
    url: `${BASE_URL}/provider-types`,
    method: 'GET',
  }).then((res) => res.data.data as Array<Record<string, string>>);
};

/** Test connection for a saved destination */
export const testConnection = (destinationId: string) => {
  return request({
    url: `${BASE_URL}/${destinationId}/test-connection`,
    method: 'POST',
  }).then((res) => res.data.data as ITestConnectionResult);
};

/** Test connection inline (before saving) */
export const testConnectionInline = (config: Record<string, any>) => {
  return request({
    url: `${BASE_URL}/test-connection`,
    method: 'POST',
    data: config,
  }).then((res) => res.data.data as ITestConnectionResult);
};
