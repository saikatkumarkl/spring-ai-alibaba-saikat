import { request } from '@/request';
import { IApiResponse } from '@/types/common';
import type {
  ICreateModelParams,
  ICreateProviderParams,
  IListProvidersParams,
  IModel,
  IModelParameterRule,
  IProvider,
  IProviderConfigInfo,
  IUpdateModelParams,
} from '@/types/modelService';

/**
 * Create a new provider
 * @param params Parameters for creating provider
 * @returns Promise containing operation result
 */
export async function createProvider(
  params: ICreateProviderParams,
): Promise<IApiResponse<boolean>> {
  const response = await request({
    url: '/console/v1/providers',
    method: 'POST',
    data: params,
  });
  return response.data;
}

/**
 * Update an existing provider
 * @param provider Provider identifier
 * @param params Parameters for updating provider
 * @returns Promise containing operation result
 */
export async function updateProvider(
  provider: string,
  params: IProvider,
): Promise<IApiResponse<boolean>> {
  const response = await request({
    url: `/console/v1/providers/${provider}`,
    method: 'PUT',
    data: {
      ...params,
      supported_model_types:
        typeof params.supported_model_types === 'string'
          ? params.supported_model_types
          : params.supported_model_types?.join(','),
    },
  });
  return response.data;
}

/**
 * Delete a provider
 * @param provider Provider identifier to delete
 * @returns Promise containing operation result
 */
export async function deleteProvider(
  provider: string,
): Promise<IApiResponse<boolean>> {
  const response = await request({
    url: `/console/v1/providers/${provider}`,
    method: 'DELETE',
  });
  return response.data;
}

/**
 * Get list of providers
 * @param params Optional query parameters
 * @returns Promise containing list of providers
 */
export async function listProviders(
  params?: IListProvidersParams,
): Promise<IApiResponse<IProvider[]>> {
  const response = await request({
    url: '/console/v1/providers',
    method: 'GET',
    params,
  });
  return response.data;
}

/**
 * Get live reachability status for all enabled providers.
 * Returns a map of provider code → reachable (true/false).
 */
export async function getProviderHealth(): Promise<IApiResponse<Record<string, boolean>>> {
  const response = await request({
    url: '/console/v1/providers/health-status',
    method: 'GET',
  });
  return response.data;
}

/**
 * Get provider details
 * @param provider Provider identifier
 * @returns Promise containing provider details
 */
export async function getProviderDetail(
  provider: string,
): Promise<IApiResponse<IProviderConfigInfo>> {
  const response = await request({
    url: `/console/v1/providers/${provider}`,
    method: 'GET',
  });
  return response.data;
}

/**
 * Get list of supported provider protocols
 * @returns Promise containing list of protocols
 */
export async function getProviderProtocols(): Promise<IApiResponse<string[]>> {
  const response = await request({
    url: '/console/v1/providers/protocols',
    method: 'GET',
  });
  return response.data;
}

/**
 * Create a new model
 * @param provider Provider identifier
 * @param params Parameters for creating model
 * @returns Promise containing operation result
 */
export async function createModel(
  provider: string,
  params: ICreateModelParams,
): Promise<IApiResponse<boolean>> {
  const response = await request({
    url: `/console/v1/providers/${provider}/models`,
    method: 'POST',
    data: {
      model_id: params.model_id,
      model_name: params.name,
      type: params.type,
      tags:
        typeof params.tags === 'string' ? params.tags : params.tags?.join(','),
      icon: params.icon,
    },
  });
  return response.data;
}

/**
 * Update an existing model
 * @param provider Provider identifier
 * @param modelId Model ID to update
 * @param params Parameters for updating model
 * @returns Promise containing operation result
 */
export async function updateModel(
  provider: string,
  modelId: string,
  params: IUpdateModelParams,
): Promise<IApiResponse<boolean>> {
  const response = await request({
    url: `/console/v1/providers/${encodeURIComponent(provider)}/models`,
    method: 'PUT',
    params: { modelId },
    data: {
      model_id: modelId,
      model_name: params.name,
      type: params.type,
      tags:
        typeof params.tags === 'string' ? params.tags : params.tags?.join(','),
      icon: params.icon,
      enable: params.enable,
    },
  });
  return response.data;
}

/**
 * Delete a model
 * @param provider Provider identifier
 * @param modelId Model ID to delete
 * @returns Promise containing operation result
 */
export async function deleteModel(
  provider: string,
  modelId: string,
): Promise<IApiResponse<boolean>> {
  const response = await request({
    url: `/console/v1/providers/${encodeURIComponent(provider)}/models`,
    method: 'DELETE',
    params: { modelId },
  });
  return response.data;
}

/**
 * Get list of models for a provider
 * @param provider Provider identifier
 * @returns Promise containing list of models
 */
export async function listModels(
  provider: string,
): Promise<IApiResponse<IModel[]>> {
  const response = await request({
    url: `/console/v1/providers/${provider}/models`,
    method: 'GET',
  });
  return response.data;
}

/**
 * Get model details
 * @param provider Provider identifier
 * @param modelId Model ID
 * @returns Promise containing model details
 */
export async function getModelDetail(
  provider: string,
  modelId: string,
): Promise<IApiResponse<IModel>> {
  const response = await request({
    url: `/console/v1/providers/${encodeURIComponent(provider)}/models/detail`,
    method: 'GET',
    params: { modelId },
  });
  return response.data;
}

/**
 * Get model selector data
 * @param modelType Model type
 * @returns Promise containing model selector data
 */
export async function getModelSelector(
  modelType: string,
): Promise<IApiResponse<{ provider: IProvider; models: IModel[] }[]>> {
  const response = await request({
    url: `/console/v1/models/${modelType}/selector`,
    method: 'GET',
  });
  return response.data;
}

/**
 * Get model parameter rules
 * @param provider Provider identifier
 * @param modelId Model ID
 * @returns Promise containing list of parameter rules
 */
export async function getModelParameterRules(
  provider: string,
  modelId: string,
): Promise<IApiResponse<IModelParameterRule[]>> {
  const response = await request({
    url: `/console/v1/providers/${encodeURIComponent(provider)}/models/parameter_rules`,
    method: 'GET',
    params: { modelId },
  });
  return response.data;
}

/**
 * Get all enabled models for prompt usage
 * Returns models in a format compatible with legacy prompt API
 * @returns Promise containing list of enabled models
 */
export async function getEnabledModels(): Promise<IApiResponse<Array<{
  id: number;
  name: string;
  provider: string;
  modelName: string;
  baseUrl: string;
  defaultParameters: Record<string, any>;
  supportedParameters: any[];
  status: number;
}>>> {
  const response = await request({
    url: '/console/v1/models/enabled',
    method: 'GET',
  });
  return response.data;
}

/**
 * Fetch available models from a remote provider endpoint.
 * Uses the backend proxy which tries OpenAI-compatible /v1/models first,
 * then falls back to Ollama's /api/tags.
 * @param endpoint The provider API endpoint (e.g., http://localhost:11434)
 * @returns Promise containing list of available model names
 */
export async function getOllamaModels(
  endpoint: string,
): Promise<IApiResponse<string[]>> {
  const response = await request({
    url: '/console/v1/providers/available-models',
    method: 'GET',
    params: { endpoint },
  });
  return response.data;
}
