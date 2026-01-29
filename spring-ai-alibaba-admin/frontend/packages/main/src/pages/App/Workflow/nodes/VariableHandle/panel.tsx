import $i18n from '@/i18n';
import { Button, IconFont } from '@spark-ai/design';
import type {
  INodeDataInputParamItem,
  INodeDataOutputParamItem,
} from '@spark-ai/flow';
import {
  CustomInputsControl,
  OutputParamsTree,
  SelectWithDesc,
  VarInputTextArea,
  generateUniqueName,
  uniqueId,
  useNodeDataUpdate,
  useNodesOutputParams,
  useNodesReadOnly,
  useReactFlowStore,
} from '@spark-ai/flow';
import { Flex } from 'antd';
import { memo, useCallback, useMemo } from 'react';
import GroupVariableForm from '../../components/GroupVariableForm';
import { useWorkflowAppStore } from '../../context/WorkflowAppProvider';
import {
  IVariableHandleGroupItem,
  IVariableHandleNodeData,
  IVariableHandleNodeParam,
} from '../../types';

const OUTPUT_MODE_OPTIONS = [
  {
    label: $i18n.get({
      id: 'main.pages.App.Workflow.nodes.VariableHandle.panel.aggregateGroup',
      dm: 'Aggregate Group',
    }),
    value: 'group',
    desc: $i18n.get({
      id: 'main.pages.App.Workflow.nodes.VariableHandle.panel.controlReturn',
      dm: 'Controls return values within groups according to aggregation strategy',
    }),
  },
  {
    label: $i18n.get({
      id: 'main.pages.App.Workflow.nodes.VariableHandle.panel.textOutput',
      dm: 'Text Output',
    }),
    value: 'template',
    desc: $i18n.get({
      id: 'main.pages.App.Workflow.nodes.VariableHandle.panel.textType',
      dm: 'Output text type',
    }),
  },
  {
    label: $i18n.get({
      id: 'main.pages.App.Workflow.nodes.VariableHandle.panel.jsonOutput',
      dm: 'JSON Output',
    }),
    value: 'json',
    desc: $i18n.get({
      id: 'main.pages.App.Workflow.nodes.VariableHandle.panel.jsonType',
      dm: 'Output formatted JSON type',
    }),
  },
];

const GROUP_STRATEGY_OPTIONS = [
  {
    label: $i18n.get({
      id: 'main.pages.App.Workflow.nodes.VariableHandle.panel.firstNonEmptyValue',
      dm: 'Return the first non-empty value in each group',
    }),
    value: 'firstNotNull',
  },
  {
    label: $i18n.get({
      id: 'main.pages.App.Workflow.nodes.VariableHandle.panel.lastNonEmptyValue',
      dm: 'Return the last non-empty value in each group',
    }),
    value: 'lastNotNull',
  },
];

export default memo(function VariableHandlePanel({
  id,
  data,
}: {
  id: string;
  data: IVariableHandleNodeData;
}) {
  const { handleNodeDataUpdate } = useNodeDataUpdate();
  const { getVariableList } = useNodesOutputParams();
  const globalVariableList = useWorkflowAppStore(
    (state) => state.globalVariableList,
  );
  const nodes = useReactFlowStore((store) => store.nodes);
  const edges = useReactFlowStore((store) => store.edges);
  const { nodesReadOnly } = useNodesReadOnly();

  const flowVariableList = useMemo(() => {
    return getVariableList({
      nodeId: id,
    });
  }, [id, nodes, edges]);

  const variableList = useMemo(() => {
    return [...globalVariableList, ...flowVariableList];
  }, [globalVariableList, flowVariableList]);

  const changeGroup = useCallback(
    (newGroups: IVariableHandleGroupItem[]) => {
      handleNodeDataUpdate({
        id,
        data: {
          ...data,
          node_param: {
            ...data.node_param,
            groups: newGroups,
          } as IVariableHandleNodeParam,
          output_params: newGroups.map((group) => ({
            key: group.group_name,
            type: group.output_type,
          })),
        },
      });
    },
    [data],
  );

  const deleteGroup = useCallback(
    (group_id: string) => {
      changeGroup(
        data.node_param.groups.filter((group) => group.group_id !== group_id),
      );
    },
    [data],
  );

  const changeGroupData = useCallback(
    (group: IVariableHandleGroupItem) => {
      changeGroup(
        data.node_param.groups.map((g) =>
          g.group_id === group.group_id ? group : g,
        ),
      );
    },
    [data],
  );

  const addGroup = useCallback(() => {
    const newGroup: IVariableHandleGroupItem = {
      group_id: uniqueId(4),
      group_name: generateUniqueName(
        'Group',
        data.node_param.groups.map((group) => group.group_name),
      ),
      output_type: 'String',
      variables: [
        {
          id: uniqueId(4),
          type: 'String',
          value_from: 'refer',
          value: void 0,
        },
      ],
    };
    changeGroup([...data.node_param.groups, newGroup]);
  }, [data]);

  const changeType = useCallback(
    (value: string) => {
      let newOutputParams: INodeDataOutputParamItem[] = [
        { key: 'output', type: 'String' },
      ];

      if (value === 'group') {
        newOutputParams = data.node_param.groups.map((group) => ({
          key: group.group_name,
          type: group.output_type,
        }));
      } else if (value === 'json') {
        newOutputParams = data.node_param.json_params.map((param) => ({
          key: param.key,
          type: param.type,
        }));
      }
      handleNodeDataUpdate({
        id,
        data: {
          node_param: {
            ...data.node_param,
            type: value,
          } as IVariableHandleNodeParam,
          output_params: newOutputParams,
        },
      });
    },
    [data],
  );

  const changeJsonParams = useCallback(
    (value: INodeDataInputParamItem[]) => {
      handleNodeDataUpdate({
        id,
        data: {
          node_param: { ...data.node_param, json_params: value },
          output_params: [...value],
        },
      });
    },
    [data],
  );

  const handleCheckGroupName = (
    val: string,
    group: IVariableHandleGroupItem,
  ): Promise<boolean> => {
    if (!val.trim())
      return Promise.reject(
        $i18n.get({
          id: 'main.pages.App.Workflow.nodes.VariableHandle.panel.enterGroupName',
          dm: 'Please enter group name',
        }),
      );
    if (
      data.node_param.groups.some(
        (g) => g.group_name === val && g.group_id !== group.group_id,
      )
    )
      return Promise.reject(
        $i18n.get({
          id: 'main.pages.App.Workflow.nodes.VariableHandle.panel.groupNameExists',
          dm: 'Group name already exists',
        }),
      );

    if (!/^[a-zA-Z_$][a-zA-Z0-9_$]*$/.test(val))
      return Promise.reject(
        $i18n.get({
          id: 'main.pages.App.Workflow.nodes.VariableHandle.panel.onlyLettersNumbersUnderscoreAndDollar',
          dm: 'Can only contain letters, numbers, underscores, and $, and cannot start with a number',
        }),
      );
    return Promise.resolve(true);
  };

  const changeNodeParam = useCallback(
    (payload: Partial<IVariableHandleNodeParam>) => {
      handleNodeDataUpdate({
        id,
        data: {
          node_param: {
            ...data.node_param,
            ...payload,
          },
        },
      });
    },
    [data, id, handleNodeDataUpdate],
  );

  return (
    <>
      <div className="spark-flow-panel-form-section">
        <Flex vertical gap={12}>
          <div className="spark-flow-panel-form-title">
            {$i18n.get({
              id: 'main.pages.App.Workflow.nodes.VariableHandle.panel.outputMode',
              dm: 'Output Mode',
            })}
          </div>
          <SelectWithDesc
            disabled={nodesReadOnly}
            options={OUTPUT_MODE_OPTIONS}
            value={data.node_param.type}
            onChange={changeType}
          />
        </Flex>
        {data.node_param.type === 'template' && (
          <VarInputTextArea
            disabled={nodesReadOnly}
            variableList={variableList}
            value={data.node_param.template_content}
            onChange={(val) =>
              changeNodeParam({
                template_content: val,
              })
            }
            maxLength={Number.MAX_SAFE_INTEGER}
          />
        )}
        {data.node_param.type === 'json' && (
          <CustomInputsControl
            disabled={nodesReadOnly}
            value={data.node_param.json_params}
            onChange={changeJsonParams}
            variableList={variableList}
          />
        )}
        {data.node_param.type === 'group' && (
          <Flex vertical gap={12}>
            <div className="spark-flow-panel-form-title">
              {$i18n.get({
                id: 'main.pages.App.Workflow.nodes.VariableHandle.panel.groupingStrategy',
                dm: 'Grouping Strategy',
              })}
            </div>
            <SelectWithDesc
              disabled={nodesReadOnly}
              options={GROUP_STRATEGY_OPTIONS}
              value={data.node_param.group_strategy}
              onChange={(val) => changeNodeParam({ group_strategy: val })}
            />

            {data.node_param.groups?.map((group) => (
              <GroupVariableForm
                disabled={nodesReadOnly}
                variableList={variableList}
                data={group}
                handleCheckGroupName={(val) => handleCheckGroupName(val, group)}
                onDelete={() => deleteGroup(group.group_id)}
                key={group.group_id}
                onChange={changeGroupData}
              />
            ))}
            <Button
              disabled={nodesReadOnly}
              onClick={addGroup}
              variant="dashed"
              icon={<IconFont type="spark-plus-line" />}
            >
              {$i18n.get({
                id: 'main.pages.App.Workflow.nodes.VariableHandle.panel.addGroup',
                dm: 'Add Group',
              })}
            </Button>
          </Flex>
        )}
      </div>
      <div className="spark-flow-panel-form-section">
        <Flex vertical gap={12}>
          <div className="spark-flow-panel-form-title">
            {$i18n.get({
              id: 'main.pages.App.Workflow.nodes.VariableHandle.panel.output',
              dm: 'Output',
            })}
          </div>
          <OutputParamsTree data={data.output_params} />
        </Flex>
      </div>
    </>
  );
});
