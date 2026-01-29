import $i18n from '@/i18n';
import { INodeSchema } from '@spark-ai/flow';

export const IteratorEndSchema: INodeSchema = {
  type: 'IteratorEnd',
  title: $i18n.get({
    id: 'main.pages.App.Workflow.nodes.IteratorEnd.schema.iterationEnd',
    dm: 'Iteration End',
  }),
  desc: $i18n.get({
    id: 'main.pages.App.Workflow.nodes.IteratorEnd.schema.iterationEndNode',
    dm: 'Iteration end node',
  }),
  iconType: 'spark-flag-line',
  groupLabel: $i18n.get({
    id: 'main.pages.App.Workflow.nodes.IteratorEnd.schema.logic',
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
