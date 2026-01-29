import $i18n from '@/i18n';
import { INodeSchema } from '@spark-ai/flow';
import { IJudgeNodeParam } from '../../types';

export const JudgeSchema: INodeSchema = {
  type: 'Judge',
  title: $i18n.get({
    id: 'main.pages.App.Workflow.nodes.Judge.schema.conditionJudgment',
    dm: 'Condition Judgment',
  }),
  desc: $i18n.get({
    id: 'main.pages.App.Workflow.nodes.Judge.schema.decideBranchBasedOnConditions',
    dm: 'Decide branch based on set conditions. If the set condition is met, only the corresponding branch runs; if none are met, the "Other" branch runs.',
  }),
  iconType: 'spark-conditionalJudgment-line',
  groupLabel: $i18n.get({
    id: 'main.pages.App.Workflow.nodes.Judge.schema.logic',
    dm: 'Logic',
  }),
  defaultParams: {
    input_params: [],
    output_params: [],
    node_param: {
      branches: [
        {
          id: 'default',
          label: $i18n.get({
            id: 'main.pages.App.Workflow.nodes.Judge.schema.defaultCondition',
            dm: 'Default Condition',
          }),
        },
      ],
    } as IJudgeNodeParam,
  },
  isSystem: false,
  allowSingleTest: false,
  disableConnectSource: true,
  disableConnectTarget: true,
  bgColor: 'var(--ag-ant-color-orange-hover)',
};
