import $i18n from '@/i18n';
import {
  INodeDataInputParamItem,
  INodeDataOutputParamItem,
} from '@spark-ai/flow';
import {
  IRetryConfig,
  ISelectedModelParams,
  IShortMemoryConfig,
  ITryCatchConfig,
} from '../types';

export const START_NODE_OUTPUT_PARAMS_DEFAULT: INodeDataOutputParamItem[] = [
  {
    key: 'name',
    type: 'String',
    desc: '',
  },
  {
    key: 'age',
    type: 'Number',
    desc: '',
  },
];

export const END_NODE_OUTPUT_PARAMS_DEFAULT: INodeDataInputParamItem[] = [
  {
    key: 'output',
    value_from: 'refer',
    type: 'String',
    value: void 0,
  },
];

export const LLM_NODE_OUTPUT_PARAMS_DEFAULT: INodeDataOutputParamItem[] = [
  {
    key: 'output',
    type: 'String',
    desc: $i18n.get({
      id: 'main.pages.App.Workflow.constant.index.textOutput',
      dm: 'Text Output',
    }),
  },
];

export const LLM_WITH_REASONING_NODE_OUTPUT_PARAMS_DEFAULT: INodeDataOutputParamItem[] =
  [
    {
      key: 'output',
      type: 'String',
      desc: $i18n.get({
        id: 'main.pages.App.Workflow.constant.index.textOutput',
        dm: 'Text Output',
      }),
    },
    {
      key: 'reasoning_content',
      type: 'String',
      desc: $i18n.get({
        id: 'main.pages.App.Workflow.constant.index.depthThinkingContent',
        dm: 'Deep Thinking Content',
      }),
    },
  ];

export const SCRIPT_NODE_INPUT_PARAMS_DEFAULT: INodeDataInputParamItem[] = [
  {
    key: 'input1',
    value_from: 'input',
    value: '1',
    type: 'Number',
  },
  {
    key: 'input2',
    value_from: 'input',
    value: '2',
    type: 'Number',
  },
];

export const SCRIPT_NODE_OUTPUT_PARAMS_DEFAULT: INodeDataOutputParamItem[] = [
  {
    key: 'output',
    type: 'Number',
    desc: $i18n.get({
      id: 'main.pages.App.Workflow.constant.index.twoNumbersSum',
      dm: 'Result of adding two numbers',
    }),
  },
];

export const RETRY_CONFIG_DEFAULT: IRetryConfig = {
  retry_enabled: false,
  max_retries: 3,
  retry_interval: 500,
};

export const TRY_CATCH_CONFIG_DEFAULT: ITryCatchConfig = {
  strategy: 'noop',
};

export const getDefaultTryCatchConfig = (
  default_values: ITryCatchConfig['default_values'],
): ITryCatchConfig => {
  return {
    strategy: 'noop',
    default_values,
  };
};

export const SHORT_MEMORY_CONFIG_DEFAULT: IShortMemoryConfig = {
  enabled: false,
  type: 'self',
  round: 3,
  param: {
    key: 'historyList',
    type: 'Array<String>',
    value_from: 'refer',
    value: void 0,
  },
};

export const SELECTED_MODEL_PARAMS_DEFAULT: ISelectedModelParams = {
  model_id: '',
  model_name: '',
  mode: 'chat',
  provider: '',
  params: [],
  vision_config: {
    enable: false,
    params: [
      {
        key: 'imageContent',
        value_from: 'refer',
        type: 'File',
        value: void 0,
      },
    ],
  },
};

export const OPERATOR_OPTS_MAP = {
  Number: [
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.equal',
        dm: 'Equals',
      }),
      value: 'equals',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.notEqual',
        dm: 'Not Equal',
      }),
      value: 'notEquals',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.empty',
        dm: 'Is Empty',
      }),
      value: 'isNull',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.notEmpty',
        dm: 'Not Empty',
      }),
      value: 'isNotNull',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.greaterThan',
        dm: 'Greater Than',
      }),
      value: 'greater',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.greaterThanOrEqual',
        dm: 'Greater Than or Equal',
      }),
      value: 'greaterAndEqual',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lessThan',
        dm: 'Less Than',
      }),
      value: 'less',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lessThanOrEqual',
        dm: 'Less Than or Equal',
      }),
      value: 'lessAndEqual',
    },
  ],

  'Array<Boolean>': [
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.empty',
        dm: 'Is Empty',
      }),
      value: 'isNull',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.notEmpty',
        dm: 'Not Empty',
      }),
      value: 'isNotNull',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lengthEqual',
        dm: 'Length Equals',
      }),
      value: 'lengthEquals',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lengthGreaterThan',
        dm: 'Length Greater Than',
      }),
      value: 'lengthGreater',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lengthGreaterThanOrEqual',
        dm: 'Length Greater Than or Equal',
      }),
      value: 'lengthGreaterAndEqual',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lengthLessThan',
        dm: 'Length Less Than',
      }),
      value: 'lengthLess',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lengthLessThanOrEqual',
        dm: 'Length Less Than or Equal',
      }),
      value: 'lengthLessAndEqual',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.contains',
        dm: 'Contains',
      }),
      value: 'contains',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.notContains',
        dm: 'Not Contains',
      }),
      value: 'notContains',
    },
  ],

  Boolean: [
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.equal',
        dm: 'Equals',
      }),
      value: 'equals',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.notEqual',
        dm: 'Not Equal',
      }),
      value: 'notEquals',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.empty',
        dm: 'Is Empty',
      }),
      value: 'isNull',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.notEmpty',
        dm: 'Not Empty',
      }),
      value: 'isNotNull',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.isTrue',
        dm: 'Is True',
      }),
      value: 'isTrue',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.isFalse',
        dm: 'Is False',
      }),
      value: 'isFalse',
    },
  ],

  'Array<File>': [
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.empty',
        dm: 'Is Empty',
      }),
      value: 'isNull',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.notEmpty',
        dm: 'Not Empty',
      }),
      value: 'isNotNull',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lengthEqual',
        dm: 'Length Equals',
      }),
      value: 'lengthEquals',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lengthGreaterThan',
        dm: 'Length Greater Than',
      }),
      value: 'lengthGreater',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lengthGreaterThanOrEqual',
        dm: 'Length Greater Than or Equal',
      }),
      value: 'lengthGreaterAndEqual',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lengthLessThan',
        dm: 'Length Less Than',
      }),
      value: 'lengthLess',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lengthLessThanOrEqual',
        dm: 'Length Less Than or Equal',
      }),
      value: 'lengthLessAndEqual',
    },
  ],

  'Array<Object>': [
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.empty',
        dm: 'Is Empty',
      }),
      value: 'isNull',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.notEmpty',
        dm: 'Not Empty',
      }),
      value: 'isNotNull',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lengthEqual',
        dm: 'Length Equals',
      }),
      value: 'lengthEquals',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lengthGreaterThan',
        dm: 'Length Greater Than',
      }),
      value: 'lengthGreater',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lengthGreaterThanOrEqual',
        dm: 'Length Greater Than or Equal',
      }),
      value: 'lengthGreaterAndEqual',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lengthLessThan',
        dm: 'Length Less Than',
      }),
      value: 'lengthLess',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lengthLessThanOrEqual',
        dm: 'Length Less Than or Equal',
      }),
      value: 'lengthLessAndEqual',
    },
  ],

  String: [
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.equal',
        dm: 'Equals',
      }),
      value: 'equals',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.notEqual',
        dm: 'Not Equal',
      }),
      value: 'notEquals',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.empty',
        dm: 'Is Empty',
      }),
      value: 'isNull',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.notEmpty',
        dm: 'Not Empty',
      }),
      value: 'isNotNull',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lengthEqual',
        dm: 'Length Equals',
      }),
      value: 'lengthEquals',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lengthGreaterThan',
        dm: 'Length Greater Than',
      }),
      value: 'lengthGreater',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lengthGreaterThanOrEqual',
        dm: 'Length Greater Than or Equal',
      }),
      value: 'lengthGreaterAndEqual',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lengthLessThan',
        dm: 'Length Less Than',
      }),
      value: 'lengthLess',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lengthLessThanOrEqual',
        dm: 'Length Less Than or Equal',
      }),
      value: 'lengthLessAndEqual',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.contains',
        dm: 'Contains',
      }),
      value: 'contains',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.notContains',
        dm: 'Not Contains',
      }),
      value: 'notContains',
    },
  ],

  File: [
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.empty',
        dm: 'Is Empty',
      }),
      value: 'isNull',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.notEmpty',
        dm: 'Not Empty',
      }),
      value: 'isNotNull',
    },
  ],

  'Array<String>': [
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.empty',
        dm: 'Is Empty',
      }),
      value: 'isNull',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.notEmpty',
        dm: 'Not Empty',
      }),
      value: 'isNotNull',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lengthEqual',
        dm: 'Length Equals',
      }),
      value: 'lengthEquals',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lengthGreaterThan',
        dm: 'Length Greater Than',
      }),
      value: 'lengthGreater',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lengthGreaterThanOrEqual',
        dm: 'Length Greater Than or Equal',
      }),
      value: 'lengthGreaterAndEqual',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lengthLessThan',
        dm: 'Length Less Than',
      }),
      value: 'lengthLess',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lengthLessThanOrEqual',
        dm: 'Length Less Than or Equal',
      }),
      value: 'lengthLessAndEqual',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.contains',
        dm: 'Contains',
      }),
      value: 'contains',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.notContains',
        dm: 'Not Contains',
      }),
      value: 'notContains',
    },
  ],

  'Array<Number>': [
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.empty',
        dm: 'Is Empty',
      }),
      value: 'isNull',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.notEmpty',
        dm: 'Not Empty',
      }),
      value: 'isNotNull',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lengthEqual',
        dm: 'Length Equals',
      }),
      value: 'lengthEquals',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lengthGreaterThan',
        dm: 'Length Greater Than',
      }),
      value: 'lengthGreater',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lengthGreaterThanOrEqual',
        dm: 'Length Greater Than or Equal',
      }),
      value: 'lengthGreaterAndEqual',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lengthLessThan',
        dm: 'Length Less Than',
      }),
      value: 'lengthLess',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.lengthLessThanOrEqual',
        dm: 'Length Less Than or Equal',
      }),
      value: 'lengthLessAndEqual',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.contains',
        dm: 'Contains',
      }),
      value: 'contains',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.notContains',
        dm: 'Not Contains',
      }),
      value: 'notContains',
    },
  ],

  Object: [
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.empty',
        dm: 'Is Empty',
      }),
      value: 'isNull',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.notEmpty',
        dm: 'Not Empty',
      }),
      value: 'isNotNull',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.contains',
        dm: 'Contains',
      }),
      value: 'contains',
    },
    {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.constant.notContains',
        dm: 'Not Contains',
      }),
      value: 'notContains',
    },
  ],
};
