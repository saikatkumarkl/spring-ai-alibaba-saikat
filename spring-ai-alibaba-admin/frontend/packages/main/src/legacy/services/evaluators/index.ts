import { request } from '../../utils/request';
import { API_PATH } from '../const';

// Create evaluation dataset
export async function createDataset(params: EvaluatorsAPI.CreateDatasetParams) {
  return request<EvaluatorsAPI.CreateDatasetResult>(`${API_PATH}/dataset/dataset`, {
    method: 'POST',
    data: params,
  });
}

// Get evaluation dataset list
// /dataset/datasets
export async function getDatasets(params: EvaluatorsAPI.GetDatasetsParams) {
  return request<EvaluatorsAPI.GetDatasetsResult>(`${API_PATH}/dataset/datasets`, {
    method: 'GET',
    params,
  });
}

// Get evaluation dataset details
// /dataset/dataset
export async function getDataset(params: EvaluatorsAPI.GetDatasetParams) {
  return request<EvaluatorsAPI.GetDatasetResult>(`${API_PATH}/dataset/dataset`, {
    method: 'GET',
    params,
  });
}

// Update evaluation dataset
// /dataset/dataset
export async function updateDataset(params: EvaluatorsAPI.UpdateDatasetParams) {
  return request<EvaluatorsAPI.UpdateDatasetResult>(`${API_PATH}/dataset/dataset`, {
    method: 'PUT',
    data: params,
  });
}

// Delete evaluation dataset
// DELETE /api/dataset/dataset
export async function deleteDataset(params: EvaluatorsAPI.DeleteDatasetParams) {
  return request<EvaluatorsAPI.DeleteDatasetResult>(`${API_PATH}/dataset/dataset?datasetId=${params.datasetId}`, {
    method: 'DELETE',
    data: params,
  });
}

// -------------- Evaluation dataset data item management interfaces --------------

// Create data item
// POST /api/dataset/dataItem
export async function createDatasetDataItem(params: EvaluatorsAPI.CreateDatasetDataItemParams) {
  return request<EvaluatorsAPI.CreateDatasetDataItemResult | EvaluatorsAPI.BatchCreateDatasetDataItemResult>(`${API_PATH}/dataset/dataItem`, {
    method: 'POST',
    data: params,
  });
}

// Get data item list
// GET /api/dataset/dataItems
export async function getDatasetDataItems(params: EvaluatorsAPI.GetDatasetDataItemsParams) {
  return request<EvaluatorsAPI.GetDatasetDataItemsResult>(`${API_PATH}/dataset/dataItems`, {
    method: 'GET',
    params,
  });
}

// Get data item details
// GET /api/dataset/dataItem
export async function getDatasetDataItem(params: EvaluatorsAPI.GetDatasetDataItemParams) {
  return request<EvaluatorsAPI.GetDatasetDataItemResult>(`${API_PATH}/dataset/dataItem`, {
    method: 'GET',
    params,
  });
}

// Update data item
// PUT /api/dataset/dataItem
export async function updateDatasetDataItem(params: EvaluatorsAPI.UpdateDatasetDataItemParams) {
  return request<EvaluatorsAPI.UpdateDatasetDataItemResult>(`${API_PATH}/dataset/dataItem`, {
    method: 'PUT',
    data: params,
  });
}

// Delete data item
// DELETE /api/dataset/dataItem
export async function deleteDatasetDataItem(params: EvaluatorsAPI.DeleteDatasetDataItemParams) {
  return request<EvaluatorsAPI.DeleteDatasetDataItemResult>(`${API_PATH}/dataset/dataItem`, {
    method: 'DELETE',
    data: params,
  });
}

// Batch delete data items
// DELETE /api/dataset/dataItems
export async function deleteDatasetDataItems(params: EvaluatorsAPI.DeleteDatasetDataItemsParams) {
  return request<EvaluatorsAPI.DeleteDatasetDataItemsResult>(`${API_PATH}/dataset/dataItems`, {
    method: 'DELETE',
    data: params,
  });
}

// Add data item to dataset from Trace
// POST /api/dataset/dataItemFromTrace
export async function createDatasetDataItemFromTrace(params: EvaluatorsAPI.CreateDatasetDataItemFromTraceParams) {
  return request<EvaluatorsAPI.CreateDatasetDataItemFromTraceResult>(`${API_PATH}/dataset/dataItemFromTrace`, {
    method: 'POST',
    data: params,
  });
}

// -------------- Evaluation dataset version management interfaces --------------

// Create evaluation dataset version
// POST /api/dataset/datasetVersion
export async function createDatasetVersion(params: EvaluatorsAPI.CreateDatasetVersionParams) {
  return request<EvaluatorsAPI.CreateDatasetVersionResult>(`${API_PATH}/dataset/datasetVersion`, {
    method: 'POST',
    data: params,
  });
}

// Get evaluation dataset version list
// GET /api/dataset/datasetVersions
export async function getDatasetVersions(params: EvaluatorsAPI.GetDatasetVersionsParams) {
  return request<EvaluatorsAPI.GetDatasetVersionsResult>(`${API_PATH}/dataset/datasetVersions`, {
    method: 'GET',
    params,
  });
}

// Update evaluation dataset version
// PUT /api/dataset/datasetVersion
export async function updateDatasetVersion(params: EvaluatorsAPI.UpdateDatasetVersionParams) {
  return request<EvaluatorsAPI.UpdateDatasetVersionResult>(`${API_PATH}/dataset/datasetVersion`, {
    method: 'PUT',
    data: params,
  });
}


// -------------- Evaluator management interfaces --------------

// Create evaluator
// POST /api/evaluator
export async function createEvaluator(params: EvaluatorsAPI.CreateEvaluatorParams) {
  return request<EvaluatorsAPI.CreateEvaluatorResult>(`${API_PATH}/evaluator/evaluator`, {
    method: 'POST',
    data: params,
  });
}

// Create evaluator version
// POST /api/evaluatorVersion
export async function createEvaluatorVersion(params: EvaluatorsAPI.CreateEvaluatorVersionParams) {
  return request<EvaluatorsAPI.CreateEvaluatorVersionResult>(`${API_PATH}/evaluator/evaluatorVersion`, {
    method: 'POST',
    data: params,
  });
}

// Get evaluators list
// GET /api/evaluators
export async function getEvaluators(params: EvaluatorsAPI.GetEvaluatorsParams) {
  return request<EvaluatorsAPI.GetEvaluatorsResult>(`${API_PATH}/evaluator/evaluators`, {
    method: 'GET',
    params,
  });
}

// Get evaluator details
// GET /api/evaluator
export async function getEvaluator(params: EvaluatorsAPI.GetEvaluatorParams) {
  return request<EvaluatorsAPI.GetEvaluatorResult>(`${API_PATH}/evaluator/evaluator`, {
    method: 'GET',
    params,
  });
}

// Update evaluator
// PUT /api/evaluator
export async function updateEvaluator(params: EvaluatorsAPI.UpdateEvaluatorParams) {
  return request<EvaluatorsAPI.UpdateEvaluatorResult>(`${API_PATH}/evaluator/evaluator`, {
    method: 'PUT',
    data: params,
  });
}

// Delete evaluator
// DELETE /api/evaluator
export async function deleteEvaluator(params: EvaluatorsAPI.DeleteEvaluatorParams) {
  return request<EvaluatorsAPI.DeleteEvaluatorResult>(`${API_PATH}/evaluator/evaluator`, {
    method: 'DELETE',
    params: params,
  });
}

// Debug evaluator
// POST /api/evaluator/debug
export async function debugEvaluator(params: EvaluatorsAPI.DebugEvaluatorParams) {
  return request<EvaluatorsAPI.DebugEvaluatorResult>(`${API_PATH}/evaluator/debug`, {
    method: 'POST',
    data: params,
  });
}

// Get evaluator templates list
// GET /api/evaluator/templates
export async function getEvaluatorTemplates() {
  return request<EvaluatorsAPI.GetEvaluatorTemplatesResult>(`${API_PATH}/evaluator/templates`, {
    method: 'GET',
  });
}

// Get evaluator template details
// GET /api/evaluator/templates
export async function getEvaluatorTemplate(params: { templateId: number }) {
  return request<EvaluatorsAPI.GetEvaluatorTemplateResult>(`${API_PATH}/evaluator/template`, {
    method: 'GET',
    params
  });
}

// Get evaluator versions list
export async function getEvaluatorVersions(params: EvaluatorsAPI.GetEvaluatorVersionsParams) {
  return request<EvaluatorsAPI.GetEvaluatorVersionsResult>(`${API_PATH}/evaluator/evaluatorVersions`, {
    method: 'GET',
    params,
  });
}


// Get experiments associated with evaluator
// GET /api/evaluator/experiments
export async function getEvaluatorExperiments(params: EvaluatorsAPI.GetEvaluatorExperimentsParams) {
  return request<EvaluatorsAPI.GetEvaluatorExperimentsResult>(`${API_PATH}/evaluator/experiments`, {
    method: 'GET',
    params,
  });
}

// Get evaluator version details
// export async function getEvaluatorVersion(params: EvaluatorsAPI.GetEvaluatorVersionParams) {
//   return request<EvaluatorsAPI.GetEvaluatorVersionResult>(`${API_PATH}/evaluator/version`, {
//     method: 'GET',
//     params,
//   });
// }

// -------------- Experiment Management APIs --------------

// Create experiment
// POST /api/experiment
export async function createExperiment(params: EvaluatorsAPI.CreateExperimentParams) {
  return request<EvaluatorsAPI.CreateExperimentResult>(`${API_PATH}/experiment`, {
    method: 'POST',
    data: params,
  });
}

// Get experiments list
// GET /api/experiments
export async function getExperiments(params: EvaluatorsAPI.GetExperimentsParams) {
  return request<EvaluatorsAPI.GetExperimentsResult>(`${API_PATH}/experiments`, {
    method: 'GET',
    params,
  });
}

// Get experiment details
// GET /api/experiment
export async function getExperiment(params: EvaluatorsAPI.GetExperimentParams) {
  return request<EvaluatorsAPI.GetExperimentResult>(`${API_PATH}/experiment`, {
    method: 'GET',
    params,
  });
}

// Get experiment result
// GET /api/experiment/result
export async function getExperimentResult(params: EvaluatorsAPI.GetExperimentResultParams) {
  return request<EvaluatorsAPI.GetExperimentResultResult>(`${API_PATH}/experiment/result`, {
    method: 'GET',
    params,
  });
}

// Stop experiment
// PUT /api/experiment/stop
export async function stopExperiment(params: EvaluatorsAPI.StopExperimentParams) {
  return request<EvaluatorsAPI.StopExperimentResult>(`${API_PATH}/experiment/stop?experimentId=${params.experimentId}`, {
    method: 'PUT',
    data: params,
  });
}

// Delete experiment
// DELETE /api/experiment
export async function deleteExperiment(params: EvaluatorsAPI.DeleteExperimentParams) {
  return request<EvaluatorsAPI.DeleteExperimentResult>(`${API_PATH}/experiment?experimentId=${params.experimentId}`, {
    method: 'DELETE',
    data: params,
  });
}

// Get experiment overview results
// GET /api/experiment/overview
export async function getExperimentOverview(params: EvaluatorsAPI.GetExperimentOverviewParams) {
  return request<EvaluatorsAPI.GetExperimentOverviewResult>(`${API_PATH}/experiment/results`, {
    method: 'GET',
    params,
  });
}
