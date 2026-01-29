import { request } from '../../utils/request';
import { API_PATH } from '../const';

// Query prompt list
export async function getPrompts(params: PromptAPI.GetPromptsParams) {
  return request<PromptAPI.GetPromptsResult>(`${API_PATH}/prompts`, {
    method: 'GET',
    params,
  });
}

// Query single prompt
export async function getPrompt(params: { promptKey: string }) {
  return request<PromptAPI.GetPromptResult>(`${API_PATH}/prompt`, {
    method: 'GET',
    params,
  });
}

// Publish prompt
export async function publishPrompt(params: PromptAPI.PublishPromptParams) {
  return request<PromptAPI.PublishPromptResult>(`${API_PATH}/prompt`, {
    method: 'POST',
    data: params,
  });
}

// Update prompt
export async function updatePrompt(params: PromptAPI.UpdatePromptParams) {
  return request<PromptAPI.UpdatePromptResult>(`${API_PATH}/prompt`, {
    method: 'PUT',
    data: params,
  });
}

// Delete prompt
export async function deletePrompt(params: PromptAPI.DeletePromptParams) {
  return request<boolean>(`${API_PATH}/prompt`, {
    method: 'DELETE',
    params,
  });
}

// Prompt version list
// api/prompt/versions, sorted by publish time descending
export async function getPromptVersions(params: PromptAPI.GetPromptVersionsParams) {
  return request<PromptAPI.GetPromptVersionsResult>(`${API_PATH}/prompt/versions`, {
    method: 'GET',
    params,
  });
}


// Query prompt version
export async function getPromptVersion(params: PromptAPI.GetPromptVersionParams) {
  return request<PromptAPI.GetPromptVersionResult>(`${API_PATH}/prompt/version`, {
    method: 'GET',
    params,
  });
}

// Publish prompt version
export async function publishPromptVersion(params: PromptAPI.PublishPromptVersionParams) {
  return request<PromptAPI.PublishPromptVersionResult>(`${API_PATH}/prompt/version`, {
    method: 'POST',
    data: params,
  });
}

// Interactive Prompt debugging
// Supports continuous interactive Prompt debugging, can perform single debugging or multi-turn dialogue. Interface returns structured streaming results, supports session management.
// POST /api/prompt/run
// Request headers:
//        Content-Type: application/json
//        Accept: application/x-ndjson
export async function runPrompt(params: PromptAPI.RunPromptParams) {
  return request<PromptAPI.RunPromptResult>(`${API_PATH}/prompt/run`, {
    method: 'POST',
    data: params,
  });
}

// Get session details
// GET /api/prompt/session/{sessionId}
export async function getPromptSession(sessionId: string) {
  return request<PromptAPI.GetPromptSessionResult>(`${API_PATH}/prompt/session`, {
    method: 'GET',
    params: {
      sessionId,
    },
  });
}

// Delete session
// DELETE /api/prompt/session/{sessionId}
export async function deletePromptSession(sessionId: string) {
  return request<PromptAPI.DeletePromptSessionResult>(`${API_PATH}/prompt/session`, {
    method: 'DELETE',
    params: {
      sessionId,
    },
  });
}


// Prompt templates list
export async function getPromptTemplates(params: PromptAPI.GetPromptTemplatesParams) {
  return request<PromptAPI.GetPromptTemplatesResult>(`${API_PATH}/prompt/templates`, {
    method: 'GET',
    params,
  });
}

// Prompt template details
export async function getPromptTemplate(params: { promptTemplateKey: string }) {
  return request<PromptAPI.GetPromptTemplateResult>(`${API_PATH}/prompt/template`, {
    method: 'GET',
    params,
  });
}

// Get model configuration list
// Get model list, replaces deprecated getModelList interface
// Returns paginated data format, supports search and filter functions
export async function getModels(params?: PromptAPI.GetModelsParams) {
  // Use new ModelService API for enabled models
  const { getEnabledModels } = await import('@/services/modelService');
  try {
    const response = await getEnabledModels();
    if (response?.data) {
      // Convert to legacy format
      return {
        code: 200,
        data: {
          totalCount: response.data.length,
          totalPage: 1,
          pageNumber: 1,
          pageSize: response.data.length,
          pageItems: response.data,
        },
      } as PromptAPI.GetModelsResult;
    }
  } catch (error) {
    console.error('Failed to get enabled models from ModelService, falling back to legacy API:', error);
  }

  // Fallback to legacy API
  return request<PromptAPI.GetModelsResult>(`${API_PATH}/models`, {
    method: 'GET',
    params,
  });
}
