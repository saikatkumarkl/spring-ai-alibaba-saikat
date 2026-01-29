import $i18n from '@/i18n';
import { INodeSchema } from '@spark-ai/flow';
import { START_NODE_OUTPUT_PARAMS_DEFAULT } from '../../constant';

export const StartSchema: INodeSchema = {
  type: 'Start',
  title: $i18n.get({
    id: 'main.pages.App.Workflow.nodes.Start.schema.start',
    dm: 'Start',
  }),
  iconType: 'spark-processStart-line',
  desc: $i18n.get({
    id: 'main.pages.App.Workflow.nodes.Start.schema.startNode',
    dm: 'Start Node',
  }),
  defaultParams: {
    input_params: [],
    output_params: START_NODE_OUTPUT_PARAMS_DEFAULT,
    node_param: {},
  },
  isSystem: true,
  hideInMenu: true,
  allowSingleTest: false,
  disableConnectSource: true,
  groupLabel: $i18n.get({
    id: 'main.pages.App.Workflow.nodes.Start.schema.basis',
    dm: 'Basic',
  }),
  checkValid: () => [],
  bgColor: 'var(--ag-ant-color-purple-hover)',
};
