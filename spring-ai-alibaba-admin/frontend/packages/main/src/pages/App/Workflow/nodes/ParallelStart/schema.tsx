import $i18n from '@/i18n';
import { INodeSchema } from '@spark-ai/flow';

export const ParallelStartSchema: INodeSchema = {
  type: 'ParallelStart',
  title: $i18n.get({
    id: 'main.pages.App.Workflow.nodes.ParallelStart.schema.parallelStart',
    dm: 'Parallel Start',
  }),
  desc: $i18n.get({
    id: 'main.pages.App.Workflow.nodes.ParallelStart.schema.parallelStartNode',
    dm: 'Parallel Start Node',
  }),
  iconType: 'spark-processStart-line',
  groupLabel: $i18n.get({
    id: 'main.pages.App.Workflow.nodes.ParallelStart.schema.logic',
    dm: 'Logic',
  }),
  defaultParams: {
    input_params: [],
    output_params: [],
    node_param: {},
  },
  isSystem: true,
  hideInMenu: true,
  allowSingleTest: false,
  disableConnectSource: true,
  disableConnectTarget: true,
  notAllowConfig: true,
  bgColor: 'var(--ag-ant-color-purple-hover)',
};
