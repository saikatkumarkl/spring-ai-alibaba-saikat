import $i18n from '@/i18n';
import { INodeSchema } from '@spark-ai/flow';
import { IParallelNodeData, IParallelNodeParam } from '../../types';
import {
  checkInputParams,
  getParentNodeVariableList,
  transformInputParams,
} from '../../utils';

const checkValid = (data: IParallelNodeData) => {
  const errorMsg: { label: string; error: string }[] = [];
  checkInputParams(data.input_params, errorMsg, {
    label: $i18n.get({
      id: 'main.pages.App.Workflow.nodes.Parallel.schema.batchProcessingArray',
      dm: 'Batch Array',
    }),
  });
  checkInputParams(data.output_params, errorMsg, {
    label: $i18n.get({
      id: 'main.pages.App.Workflow.nodes.Parallel.schema.output',
      dm: 'Output',
    }),
    checkValue: true,
  });
  return errorMsg;
};

const getRefVariables = (data: IParallelNodeData) => {
  const variableKeyMap: Record<string, boolean> = {};
  transformInputParams(data.input_params, variableKeyMap);
  return Object.keys(variableKeyMap);
};

export const ParallelSchema: INodeSchema = {
  type: 'Parallel',
  title: $i18n.get({
    id: 'main.pages.App.Workflow.nodes.Parallel.schema.batchProcessing',
    dm: 'Batch Processing',
  }),
  desc: $i18n.get({
    id: 'main.pages.App.Workflow.nodes.Parallel.schema.bulkProcessing',
    dm: 'Supports batch processing of arrays to improve speed.',
  }),
  iconType: 'spark-summary-line',
  groupLabel: $i18n.get({
    id: 'main.pages.App.Workflow.nodes.Parallel.schema.logic',
    dm: 'Logic',
  }),
  defaultParams: {
    input_params: [],
    output_params: [],
    node_param: {
      batch_size: 100,
      concurrent_size: 5,
      error_strategy: 'terminated',
    } as IParallelNodeParam,
  },
  isSystem: false,
  allowSingleTest: true,
  disableConnectSource: true,
  disableConnectTarget: true,
  bgColor: 'var(--ag-ant-color-orange-hover)',
  isGroup: true,
  checkValid: (data) => checkValid(data as IParallelNodeData),
  getRefVariables: (data) => getRefVariables(data as IParallelNodeData),
  getParentNodeVariableList: getParentNodeVariableList,
};
