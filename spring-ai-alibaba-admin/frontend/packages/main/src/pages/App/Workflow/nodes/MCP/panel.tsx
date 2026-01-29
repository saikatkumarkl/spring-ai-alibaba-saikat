import $i18n from '@/i18n';
import { getMcpServer } from '@/services/mcp';
import {
  CustomInputsControl,
  OutputParamsTree,
  useNodeDataUpdate,
  useNodesOutputParams,
  useNodesReadOnly,
  useReactFlowStore,
} from '@spark-ai/flow';
import { useSetState } from 'ahooks';
import { Flex, Spin } from 'antd';
import { memo, useEffect, useMemo } from 'react';
import InfoIcon from '../../components/InfoIcon';
import { useWorkflowAppStore } from '../../context/WorkflowAppProvider';
import { IMCPNodeData } from '../../types';
import { getMCPNodeInputParams } from '../../utils';

export default memo(function MCPPanel(props: {
  id: string;
  data: IMCPNodeData;
}) {
  const { handleNodeDataUpdate } = useNodeDataUpdate();
  const { getVariableList } = useNodesOutputParams();
  const globalVariableList = useWorkflowAppStore(
    (state) => state.globalVariableList,
  );
  const nodes = useReactFlowStore((store) => store.nodes);
  const edges = useReactFlowStore((store) => store.edges);
  const { nodesReadOnly } = useNodesReadOnly();
  const [state, setState] = useSetState({
    loading: true,
  });

  const flowVariableList = useMemo(() => {
    return getVariableList({
      nodeId: props.id,
    });
  }, [props.id, nodes, edges]);

  const variableList = useMemo(() => {
    return [...globalVariableList, ...flowVariableList];
  }, [globalVariableList, flowVariableList]);

  useEffect(() => {
    if (!props.data.node_param.tool_name) {
      setState({
        loading: false,
      });
      return;
    }
    setState({
      loading: true,
    });

    getMcpServer({
      server_code: props.data.node_param.server_code,
      need_tools: true,
    })
      .then((detail) => {
        const toolItem = detail.data.tools?.find(
          (item) => item.name === props.data.node_param.tool_name,
        );
        if (toolItem) {
          handleNodeDataUpdate({
            id: props.id,
            data: {
              input_params: getMCPNodeInputParams(
                toolItem,
                props.data.input_params,
              ),
              node_param: {
                ...props.data.node_param,
                tool_name: toolItem.name,
                server_name: detail.data.name,
              },
            },
          });
        }
        setState({
          loading: false,
        });
      })
      .finally(() => {
        setState({
          loading: false,
        });
      });
  }, [props.id]);

  if (state.loading) {
    return <Spin spinning className="loading-center" />;
  }

  return (
    <>
      <div className="spark-flow-panel-form-section">
        <Flex vertical gap={12}>
          <div className="spark-flow-panel-form-title">
            {$i18n.get({
              id: 'main.pages.App.Workflow.nodes.MCP.panel.input',
              dm: 'Input',
            })}

            <InfoIcon
              tip={$i18n.get({
                id: 'main.pages.App.Workflow.nodes.MCP.panel.inputVariables',
                dm: 'Input variables, the input parameters for the MCP tool.',
              })}
            />
          </div>
          <CustomInputsControl
            disabledKey
            onChange={(payload) => {
              handleNodeDataUpdate({
                id: props.id,
                data: {
                  input_params: payload,
                },
              });
            }}
            value={props.data.input_params}
            variableList={variableList}
            disabled={nodesReadOnly}
          />
        </Flex>
      </div>
      <div className="spark-flow-panel-form-section">
        <Flex vertical gap={12}>
          <div className="spark-flow-panel-form-title">
            {$i18n.get({
              id: 'main.pages.App.Workflow.nodes.MCP.panel.output',
              dm: 'Output',
            })}

            <InfoIcon
              tip={$i18n.get({
                id: 'main.pages.App.Workflow.nodes.MCP.panel.outputVariables',
                dm: 'Output variables for the processing results of this node, used for subsequent nodes to identify and process the results.',
              })}
            />
          </div>
          <OutputParamsTree data={props.data.output_params} />
        </Flex>
      </div>
    </>
  );
});
