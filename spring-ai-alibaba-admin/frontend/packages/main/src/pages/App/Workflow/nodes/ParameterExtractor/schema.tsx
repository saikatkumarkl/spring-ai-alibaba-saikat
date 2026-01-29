import $i18n from '@/i18n';
import { INodeSchema } from '@spark-ai/flow';
import {
  SELECTED_MODEL_PARAMS_DEFAULT,
  SHORT_MEMORY_CONFIG_DEFAULT,
} from '../../constant';
import {
  IParameterExtractorNodeData,
  IParameterExtractorNodeParam,
} from '../../types';
import {
  checkLLMData,
  checkShortMemory,
  getVariablesFromText,
  transformInputParams,
} from '../../utils';

const checkValid = (data: IParameterExtractorNodeData) => {
  const errorMsg: { label: string; error: string }[] = [];
  checkLLMData(data.node_param.model_config, errorMsg);
  if (!data.input_params[0]?.value) {
    errorMsg.push({
      label: $i18n.get({
        id: 'main.pages.App.Workflow.nodes.ParameterExtractor.schema.input',
        dm: 'Input',
      }),
      error: $i18n.get({
        id: 'main.pages.App.Workflow.nodes.ParameterExtractor.schema.notNull',
        dm: 'cannot be empty',
      }),
    });
  }
  if (!data.node_param.extract_params.length) {
    errorMsg.push({
      label: $i18n.get({
        id: 'main.pages.App.Workflow.nodes.ParameterExtractor.schema.extractParameters',
        dm: 'Extract Parameters',
      }),
      error: $i18n.get({
        id: 'main.pages.App.Workflow.nodes.ParameterExtractor.schema.notNull',
        dm: 'cannot be empty',
      }),
    });
  }
  checkShortMemory(data.node_param.short_memory, errorMsg);
  return errorMsg;
};

const getRefVariables = (data: IParameterExtractorNodeData) => {
  const variableKeyMap: Record<string, boolean> = {};
  transformInputParams(data.input_params, variableKeyMap);
  getVariablesFromText(data.node_param.instruction, variableKeyMap);
  if (data.node_param.model_config.vision_config.enable) {
    transformInputParams(
      data.node_param.model_config.vision_config.params,
      variableKeyMap,
    );
  }
  return Object.keys(variableKeyMap);
};

export const ParameterExtractorSchema: INodeSchema = {
  type: 'ParameterExtractor',
  title: $i18n.get({
    id: 'main.pages.App.Workflow.nodes.ParameterExtractor.schema.parameterExtraction',
    dm: 'Parameter Extraction',
  }),
  desc: $i18n.get({
    id: 'main.pages.App.Workflow.nodes.ParameterExtractor.schema.extractStructuredParameters',
    dm: 'Extract structured parameters from text using a model.',
  }),
  iconType: 'spark-config-line',
  groupLabel: $i18n.get({
    id: 'main.pages.App.Workflow.nodes.ParameterExtractor.schema.variable',
    dm: 'Variable',
  }),
  defaultParams: {
    input_params: [
      {
        key: 'input',
        value_from: 'refer',
        type: 'String',
        value: '',
      },
    ],

    output_params: [
      {
        key: 'city',
        type: 'String',
        desc: $i18n.get({
          id: 'main.pages.App.Workflow.nodes.ParameterExtractor.schema.city',
          dm: 'City',
        }),
      },
      {
        key: 'date',
        type: 'String',
        desc: $i18n.get({
          id: 'main.pages.App.Workflow.nodes.ParameterExtractor.schema.date',
          dm: 'Date',
        }),
      },
      {
        key: '_is_completed',
        type: 'Boolean',
        desc: $i18n.get({
          id: 'main.pages.App.Workflow.nodes.ParameterExtractor.schema.completeParsing',
          dm: 'Whether parsing is complete',
        }),
      },
      {
        key: '_reason',
        type: 'String',
        desc: $i18n.get({
          id: 'main.pages.App.Workflow.nodes.ParameterExtractor.schema.unsuccessfulReason',
          dm: 'Reason for unsuccessful parsing',
        }),
      },
    ],

    node_param: {
      model_config: SELECTED_MODEL_PARAMS_DEFAULT,
      instruction: '',
      extract_params: [
        {
          key: 'city',
          type: 'String',
          required: true,
          desc: $i18n.get({
            id: 'main.pages.App.Workflow.nodes.ParameterExtractor.schema.city',
            dm: 'City',
          }),
        },
        {
          key: 'date',
          type: 'String',
          required: true,
          desc: $i18n.get({
            id: 'main.pages.App.Workflow.nodes.ParameterExtractor.schema.date',
            dm: 'Date',
          }),
        },
      ],

      short_memory: SHORT_MEMORY_CONFIG_DEFAULT,
    } as IParameterExtractorNodeParam,
  },
  isSystem: false,
  allowSingleTest: true,
  disableConnectSource: true,
  disableConnectTarget: true,
  bgColor: 'var(--ag-ant-color-pink-hover)',
  checkValid: (data) => checkValid(data as IParameterExtractorNodeData),
  getRefVariables: (data) =>
    getRefVariables(data as IParameterExtractorNodeData),
};
