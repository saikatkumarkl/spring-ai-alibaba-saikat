import $i18n from '@/i18n';
import { getAppComponentInputAndOutputParams } from '@/services/appComponent';
import { IconFont } from '@spark-ai/design';
import {
  CustomInputsControl,
  OutputParamsTree,
  useNodeDataUpdate,
  useNodesOutputParams,
  useNodesReadOnly,
  useReactFlowStore,
} from '@spark-ai/flow';
import { useSetState } from 'ahooks';
import { Flex, Spin, Switch, Typography } from 'antd';
import classNames from 'classnames';
import { memo, useCallback, useEffect, useMemo } from 'react';
import InfoIcon from '../../components/InfoIcon';
import ShortMemoryForm from '../../components/ShortMemoryForm';
import { useWorkflowAppStore } from '../../context/WorkflowAppProvider';
import { IAppComponentNodeData, IAppComponentNodeParam } from '../../types';
import styles from './index.module.less';

export default memo(function AppComponentPanel(props: {
  id: string;
  data: IAppComponentNodeData;
}) {
  const { handleNodeDataUpdate } = useNodeDataUpdate();
  const globalVariableList = useWorkflowAppStore(
    (state) => state.globalVariableList,
  );
  const { getVariableList } = useNodesOutputParams();
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

  const changeNodeParam = useCallback(
    (payload: Partial<IAppComponentNodeParam>) => {
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
    [props.data.node_param],
  );

  useEffect(() => {
    if (!props.data.node_param.code) {
      setState({
        loading: false,
      });
      return;
    }
    setState({
      loading: true,
    });

    getAppComponentInputAndOutputParams(props.data.node_param.code)
      .then((res) => {
        handleNodeDataUpdate({
          id: props.id,
          data: {
            input_params: res.input.map((item) => {
              const targetOldValue =
                props.data.input_params.find((i) => i.key === item.alias) || {};
              return {
                key: item.alias,
                type: item.type,
                value_from: 'refer',
                value: void 0,
                ...targetOldValue,
              };
            }),
            output_params: res.output.map((item) => ({
              key: item.field,
              type: item.type,
              desc: item.description,
            })),
          },
        });
        setState({
          loading: false,
        });
      })
      .finally(() => {
        setState({
          loading: false,
        });
      });
  }, [props.data.node_param.code]);

  if (state.loading) {
    return <Spin spinning className="loading-center" />;
  }

  return (
    <>
      <div className="spark-flow-panel-form-section">
        <Flex vertical gap={12}>
          <div className="spark-flow-panel-form-title">
            {$i18n.get({
              id: 'main.pages.App.Workflow.nodes.AppComponent.panel.component',
              dm: 'Component',
            })}
          </div>
          <Flex
            className={styles['app-component-item']}
            gap={4}
            justify="space-between"
            align="center"
          >
            <IconFont size="small" type="spark-agent-line" />
            <Typography.Text
              ellipsis={{ tooltip: props.data.node_param.name }}
              className={classNames(styles['app-component-name'])}
            >
              {props.data.node_param.name}
            </Typography.Text>
          </Flex>
        </Flex>
      </div>
      <div className="spark-flow-panel-form-section">
        <Flex vertical gap={12}>
          <div className="spark-flow-panel-form-title">
            {$i18n.get({
              id: 'main.pages.App.Workflow.nodes.AppComponent.panel.inputVariables',
              dm: 'Input Variables',
            })}
            <InfoIcon
              tip={$i18n.get({
                id: 'main.pages.App.Workflow.nodes.AppComponent.panel.inputVariablesForThisNode',
                dm: 'Input variables that this node needs to process, used to identify the content to be processed, supports referencing previous nodes.',
              })}
            />
          </div>
          <CustomInputsControl
            disabledKey
            disabled={nodesReadOnly}
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
          />
        </Flex>
      </div>
      <div className="spark-flow-panel-form-section">
        <Flex justify="space-between">
          <div className="spark-flow-panel-form-title">
            {$i18n.get({
              id: 'main.pages.App.Workflow.nodes.AppComponent.panel.streamSwitch',
              dm: 'Stream Switch',
            })}

            <InfoIcon
              tip={$i18n.get({
                id: 'main.pages.App.Workflow.nodes.AppComponent.panel.outputInStream',
                dm: 'The output content of this node model will be output in streaming mode.',
              })}
            />
          </div>
          <Switch
            disabled={nodesReadOnly}
            checked={props.data.node_param.stream_switch}
            onChange={(val) =>
              changeNodeParam({
                stream_switch: val,
              })
            }
          />
        </Flex>
      </div>
      <div className="spark-flow-panel-form-section">
        <ShortMemoryForm
          variableList={variableList}
          value={props.data.node_param.short_memory!}
          onChange={(val) =>
            changeNodeParam({
              short_memory: val,
            })
          }
        />
      </div>
      <div className="spark-flow-panel-form-section">
        <Flex vertical gap={12}>
          <div className="spark-flow-panel-form-title">
            {$i18n.get({
              id: 'main.pages.App.Workflow.nodes.AppComponent.panel.output',
              dm: 'Output',
            })}

            <InfoIcon
              tip={$i18n.get({
                id: 'main.pages.App.Workflow.nodes.AppComponent.panel.outputVariables',
                dm: 'Output variables for this node processing results, used for subsequent nodes to identify and process the results.',
              })}
            />
          </div>
          <OutputParamsTree data={props.data.output_params} />
        </Flex>
      </div>
    </>
  );
});
