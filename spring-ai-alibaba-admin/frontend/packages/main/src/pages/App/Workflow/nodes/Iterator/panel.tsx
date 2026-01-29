import $i18n from '@/i18n';
import { SliderSelector } from '@spark-ai/design';
import {
  CustomInputsControl,
  filterVarItemsByType,
  IValueType,
  IVarItem,
  IVarTreeItem,
  JudgeForm,
  useNodeDataUpdate,
  useNodesOutputParams,
  useNodesReadOnly,
  useReactFlowStore,
} from '@spark-ai/flow';
import { Flex, Select } from 'antd';
import { memo, useCallback, useMemo } from 'react';
import InfoIcon from '../../components/InfoIcon';
import IteratorVariableForm from '../../components/IteratorVariableForm';
import { IIteratorNodeData, IIteratorNodeParam } from '../../types';

const options = [
  {
    label: $i18n.get({
      id: 'main.pages.App.Workflow.nodes.Iterator.panel.useArrayLoop',
      dm: 'Use Array Loop',
    }),
    value: 'byArray',
  },
  {
    label: $i18n.get({
      id: 'main.pages.App.Workflow.nodes.Iterator.panel.specifyLoopCount',
      dm: 'Specify Loop Count',
    }),
    value: 'byCount',
  },
];

export default memo(function IteratorPanel(props: {
  id: string;
  data: IIteratorNodeData;
}) {
  const nodes = useReactFlowStore((store) => store.nodes);
  const edges = useReactFlowStore((store) => store.edges);
  const { handleNodeDataUpdate } = useNodeDataUpdate();
  const { getSubNodesVariables, getVariableList } = useNodesOutputParams();
  const { nodesReadOnly } = useNodesReadOnly();

  const flowVariableList = useMemo(() => {
    return getVariableList({
      nodeId: props.id,
    });
  }, [props.id, nodes, edges]);

  const inputVariableList = useMemo(() => {
    const list: IVarTreeItem[] = [];
    flowVariableList.forEach((item) => {
      const subList = filterVarItemsByType(item.children, [
        'Array<String>',
        'Array<Boolean>',
        'Array<File>',
        'Array<Number>',
        'Array<String>',
      ]);
      if (subList.length > 0) {
        list.push({
          ...item,
          children: subList,
        });
      }
    });
    return list;
  }, [props.id, flowVariableList]);

  const subNodesVariables = useMemo(() => {
    return getSubNodesVariables(props.id);
  }, [props.id, nodes]);

  const changeNodeParam = useCallback(
    (payload: Partial<IIteratorNodeParam>) => {
      handleNodeDataUpdate({
        id: props.id,
        data: {
          node_param: {
            ...props.data.node_param,
            ...payload,
          },
        },
      });
    },
    [props.data.node_param, handleNodeDataUpdate],
  );

  const variableParameters = useMemo(() => {
    const params: IVarItem[] = [];
    props.data.node_param.variable_parameters?.forEach((item) => {
      if (!item.value) return;
      params.push({
        label: item.key,
        value: `\${${props.id}.${item.key}}`,
        type: item.type as IValueType,
      });
    });
    if (!params.length) return [];
    return [
      {
        label: props.data.label,
        nodeId: props.id,
        nodeType: 'Iterator',
        children: params,
      },
    ];
  }, [props.data.node_param.variable_parameters, props.data.label, props.id]);

  return (
    <>
      <div className="spark-flow-panel-form-section">
        <Flex vertical gap={12}>
          <div className="spark-flow-panel-form-title">
            {$i18n.get({
              id: 'main.pages.App.Workflow.nodes.Iterator.panel.loopType',
              dm: 'Loop Type',
            })}

            <InfoIcon
              tip={$i18n.get({
                id: 'main.pages.App.Workflow.nodes.Iterator.panel.twoTypesOfLoops',
                dm: 'There are two types: specify loop count and array loop. The difference is in the number of iterations - the former has a fixed count, while the latter uses the array length.',
              })}
            />
          </div>
          <Select
            disabled={nodesReadOnly}
            options={options}
            value={props.data.node_param.iterator_type}
            onChange={(val) => changeNodeParam({ iterator_type: val })}
          />
        </Flex>

        {props.data.node_param.iterator_type === 'byArray' ? (
          <Flex vertical gap={12}>
            <div className="spark-flow-panel-form-title">
              {$i18n.get({
                id: 'main.pages.App.Workflow.nodes.Iterator.panel.loopArray',
                dm: 'Loop Array',
              })}

              <InfoIcon
                tip={$i18n.get({
                  id: 'main.pages.App.Workflow.nodes.Iterator.panel.loopBodyInput',
                  dm: 'The input for the loop body must be an array (List) type data. The loop executes in the order of array indices.',
                })}
              />
            </div>
            <CustomInputsControl
              disabled={nodesReadOnly}
              onChange={(val) =>
                handleNodeDataUpdate({
                  id: props.id,
                  data: {
                    input_params: val,
                  },
                })
              }
              defaultType="Array<String>"
              variableList={inputVariableList}
              disabledTypes={[
                'String',
                'Boolean',
                'Number',
                'File',
                'Object',
                'Array<File>',
              ]}
              value={props.data.input_params}
            />
          </Flex>
        ) : (
          <Flex vertical gap={12}>
            <div className="spark-flow-panel-form-title">
              {$i18n.get({
                id: 'main.pages.App.Workflow.nodes.Iterator.panel.loopCount',
                dm: 'Loop Count',
              })}
            </div>
            <SliderSelector
              disabled={nodesReadOnly}
              value={props.data.node_param.count_limit}
              onChange={(val) =>
                changeNodeParam({ count_limit: val as number })
              }
              min={1}
              max={500}
              step={1}
              inputNumberWrapperStyle={{ width: 54 }}
            />
          </Flex>
        )}
      </div>
      <div className="spark-flow-panel-form-section">
        <Flex vertical gap={12}>
          <div className="spark-flow-panel-form-title">
            {$i18n.get({
              id: 'main.pages.App.Workflow.nodes.Iterator.panel.middleVariable',
              dm: 'Intermediate Variables',
            })}

            <InfoIcon
              tip={$i18n.get({
                id: 'main.pages.App.Workflow.nodes.Iterator.panel.variableUsedInLoopBody',
                dm: 'Variables used in the loop body can be utilized within the loop.',
              })}
            />
          </div>
          <IteratorVariableForm
            disabled={nodesReadOnly}
            value={props.data.node_param.variable_parameters}
            onChange={(val) => changeNodeParam({ variable_parameters: val })}
            variableList={flowVariableList}
          />
        </Flex>
      </div>
      <div className="spark-flow-panel-form-section">
        <div>
          <div className="spark-flow-panel-form-title">
            {$i18n.get({
              id: 'main.pages.App.Workflow.nodes.Iterator.panel.terminationCondition',
              dm: 'Termination Condition',
            })}

            <InfoIcon
              tip={$i18n.get({
                id: 'main.pages.App.Workflow.nodes.Iterator.panel.setTerminationCondition',
                dm: 'User-defined loop exit condition. By using the variable setting node to update intermediate variables in the loop body to meet the preset termination condition, the loop can exit early. When no termination condition is set, the exit is determined by the loop type.',
              })}
            />
          </div>
          <JudgeForm
            areaStyle={{ padding: '20px 0' }}
            disabled={nodesReadOnly}
            value={props.data.node_param.terminations}
            onChange={(val) => changeNodeParam({ terminations: val })}
            leftVariableList={variableParameters}
            rightVariableList={flowVariableList}
          />
        </div>
      </div>
      <div className="spark-flow-panel-form-section">
        <Flex vertical gap={12}>
          <div className="spark-flow-panel-form-title">
            {$i18n.get({
              id: 'main.pages.App.Workflow.nodes.Iterator.panel.outputVariable',
              dm: 'Output Variables',
            })}
          </div>
          <CustomInputsControl
            disabled={nodesReadOnly}
            value={props.data.output_params}
            disabledValueFrom
            variableList={subNodesVariables}
            onChange={(val) => {
              handleNodeDataUpdate({
                id: props.id,
                data: {
                  output_params: val,
                },
              });
            }}
          />
        </Flex>
      </div>
    </>
  );
});
