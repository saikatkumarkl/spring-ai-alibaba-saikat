import $i18n from '@/i18n';
import { INodeSchema } from '@spark-ai/flow';
import { END_NODE_OUTPUT_PARAMS_DEFAULT } from '../../constant';
import { IEndNodeData, IEndNodeParam } from '../../types';
import { checkInputParams } from '../../utils';

const checkEndNodeDataValid = (data: IEndNodeData) => {
  const errorMsg: { label: string; error: string }[] = [];
  if (data.node_param.output_type === 'json') {
    checkInputParams(data.node_param.json_params, errorMsg, {
      label: $i18n.get({
        id: 'main.pages.App.Workflow.nodes.End.schemaOutput',
        dm: 'Output',
      }),
    });
  }
  if (data.node_param.output_type === 'text') {
    if (!data.node_param.text_template) {
      errorMsg.push({
        label: $i18n.get({
          id: 'main.pages.App.Workflow.nodes.End.schemaTextTemplate',
          dm: 'Text Template',
        }),
        error: $i18n.get({
          id: 'main.pages.App.Workflow.nodes.End.schemaCannotBeEmpty',
          dm: 'Cannot be empty',
        }),
      });
    }
  }
  return errorMsg;
};

export const EndSchema: INodeSchema = {
  type: 'End',
  title: $i18n.get({
    id: 'main.pages.App.Workflow.nodes.End.schema.end',
    dm: 'End',
  }),
  iconType: 'spark-flag-line',
  desc: $i18n.get({
    id: 'main.pages.App.Workflow.nodes.End.schema.endNode',
    dm: 'End Node',
  }),
  defaultParams: {
    input_params: [],
    output_params: [],
    node_param: {
      output_type: 'text',
      text_template: '',
      json_params: END_NODE_OUTPUT_PARAMS_DEFAULT,
      stream_switch: false,
    } as IEndNodeParam,
  },
  isSystem: true,
  hideInMenu: true,
  allowSingleTest: false,
  disableConnectSource: true,
  groupLabel: $i18n.get({
    id: 'main.pages.App.Workflow.nodes.End.schema.basic',
    dm: 'Basic',
  }),
  bgColor: 'var(--ag-ant-color-purple-hover)',
  checkValid: (data) => checkEndNodeDataValid(data as IEndNodeData),
};
