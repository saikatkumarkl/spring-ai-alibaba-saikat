import $i18n from '@/i18n';
import {
  CustomInputsControl,
  filterVarItemsByType,
  IVarTreeItem,
  useNodeDataUpdate,
  useNodesOutputParams,
  useNodesReadOnly,
  useReactFlowStore,
} from '@spark-ai/flow';
import { Flex, Select } from 'antd';
import { memo, useCallback, useMemo } from 'react';
import InfoIcon from '../../components/InfoIcon';
import ParallelConfigForm from '../../components/ParallelConfigForm';
import { IParallelNodeData, IParallelNodeParam } from '../../types';

const options = [
  {
    label: $i18n.get({
      id: 'main.pages.App.Workflow.nodes.Parallel.panel.terminateOnError',
      dm: 'Terminate on error',
    }),
    value: 'terminated',
  },
  {
    label: $i18n.get({
      id: 'main.pages.App.Workflow.nodes.Parallel.panel.ignoreErrorContinue',
      dm: 'Ignore error and continue',
    }),
    value: 'continueOnError',
  },
  {
    label: $i18n.get({
      id: 'main.pages.App.Workflow.nodes.Parallel.panel.removeErrorOutput',
      dm: 'Remove error output',
    }),
    value: 'removeErrorOutput',
  },
];

export default memo(function ParallelPanel(props: {
  id: string;
  data: IParallelNodeData;
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
    (payload: Partial<IParallelNodeParam>) => {
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

  return (
    <>
      <div className="spark-flow-panel-form-section">
        <Flex vertical gap={12}>
          <div className="spark-flow-panel-form-title">
            {$i18n.get({
              id: 'main.pages.App.Workflow.nodes.Parallel.panel.batchSettings',
              dm: 'Batch Settings',
            })}
          </div>
          <ParallelConfigForm
            value={props.data.node_param}
            onChange={changeNodeParam}
          />
        </Flex>
      </div>
      <div className="spark-flow-panel-form-section">
        <Flex vertical gap={12}>
          <div className="spark-flow-panel-form-title">
            {$i18n.get({
              id: 'main.pages.App.Workflow.nodes.Parallel.panel.batchArray',
              dm: 'Batch Array',
            })}

            <InfoIcon
              tip={$i18n.get({
                id: 'main.pages.App.Workflow.nodes.Parallel.panel.batchInput',
                dm: 'Batch input must be array (List) type data. The workflow will execute in array index order.',
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
            variableList={inputVariableList}
            value={props.data.input_params}
          />
        </Flex>
      </div>
      <div className="spark-flow-panel-form-section">
        <Flex vertical gap={12}>
          <div className="spark-flow-panel-form-title">
            {$i18n.get({
              id: 'main.pages.App.Workflow.nodes.Parallel.panel.outputVariable',
              dm: 'Output Variable',
            })}
          </div>
          <CustomInputsControl
            disabled={nodesReadOnly}
            disabledValueFrom
            value={props.data.output_params}
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
      <div className="spark-flow-panel-form-section">
        <Flex vertical gap={12}>
          <div className="spark-flow-panel-form-title">
            {$i18n.get({
              id: 'main.pages.App.Workflow.nodes.Parallel.panel.errorResponseMethod',
              dm: 'Error Response Method',
            })}

            <InfoIcon
              tip={$i18n.get({
                id: 'main.pages.App.Workflow.nodes.Parallel.panel.errorHandling',
                dm: 'When a sub-node fails during batch processing, handle according to the user-selected method.',
              })}
            />
          </div>
          <Select
            disabled={nodesReadOnly}
            value={props.data.node_param.error_strategy}
            onChange={(val) =>
              changeNodeParam({
                error_strategy: val as IParallelNodeParam['error_strategy'],
              })
            }
            options={options}
            placeholder={$i18n.get({
              id: 'main.pages.App.Workflow.nodes.Parallel.panel.selectErrorMethod',
              dm: 'Please select error response method',
            })}
          />
        </Flex>
      </div>
    </>
  );
});
