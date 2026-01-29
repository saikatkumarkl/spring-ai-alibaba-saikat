import $i18n from '@/i18n';
import { Button, IconFont } from '@spark-ai/design';
import type { IValueType, IVarItem, IVarTreeItem } from '@spark-ai/flow';
import {
  OutputParamsTree,
  VarInputTextArea,
  VariableSelector,
  filterVarItemsByType,
  useNodeDataUpdate,
  useNodesOutputParams,
  useNodesReadOnly,
  useReactFlowStore,
} from '@spark-ai/flow';
import { useSetState } from 'ahooks';
import { Flex } from 'antd';
import { memo, useCallback, useMemo } from 'react';
import ExtractParamEditModal from '../../components/ExtractParamEditModal';
import ExtractParamItem from '../../components/ExtractParamItem';
import InfoIcon from '../../components/InfoIcon';
import ModelConfigFormWrap from '../../components/ModelConfigFormWrap';
import ShortMemoryForm from '../../components/ShortMemoryForm';
import { useWorkflowAppStore } from '../../context/WorkflowAppProvider';
import {
  IParameterExtractorNodeData,
  IParameterExtractorNodeParam,
} from '../../types';

export default memo(function ParameterExtractorPanel({
  id,
  data,
}: {
  id: string;
  data: IParameterExtractorNodeData;
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
    showExtractParamEditModal: false,
    editExtractParam: undefined as
      | IParameterExtractorNodeParam['extract_params'][number]
      | undefined,
  });

  const flowVariableList = useMemo(() => {
    return getVariableList({
      nodeId: id,
    });
  }, [id, nodes, edges]);

  const variableList = useMemo(() => {
    return [...globalVariableList, ...flowVariableList];
  }, [globalVariableList, flowVariableList]);

  const changeNodeParam = useCallback(
    (payload: Partial<IParameterExtractorNodeParam>) => {
      handleNodeDataUpdate({
        id: id,
        data: {
          node_param: {
            ...data.node_param,
            ...payload,
          },
        },
      });
    },
    [id, data.node_param],
  );

  const changeExtractParam = useCallback(
    (payload: IParameterExtractorNodeParam['extract_params']) => {
      const newOutputParams = [
        {
          key: '_is_completed',
          type: 'Boolean' as IValueType,
          desc: $i18n.get({
            id: 'main.pages.App.Workflow.nodes.ParameterExtractor.panel.completeParsing',
            dm: 'Whether parsing is complete',
          }),
        },
        {
          key: '_reason',
          type: 'String' as IValueType,
          desc: $i18n.get({
            id: 'main.pages.App.Workflow.nodes.ParameterExtractor.panel.unsuccessfulReason',
            dm: 'Reason for unsuccessful parsing',
          }),
        },
      ];

      payload.forEach((item) => {
        newOutputParams.unshift({
          key: item.key,
          type: item.type as IValueType,
          desc: item.desc || '',
        });
      });
      handleNodeDataUpdate({
        id: id,
        data: {
          node_param: {
            ...data.node_param,
            extract_params: payload,
          },
          output_params: newOutputParams,
        },
      });
    },
    [changeNodeParam, data.node_param],
  );

  const handleAdd = useCallback(() => {
    setState({
      showExtractParamEditModal: true,
    });
  }, []);

  const handleEditExtractParams = useCallback(
    (item: IParameterExtractorNodeParam['extract_params'][number]) => {
      setState({
        showExtractParamEditModal: true,
        editExtractParam: item,
      });
    },
    [],
  );

  const handleDeleteExtractParams = useCallback(
    (key: string) => {
      changeExtractParam(
        data.node_param.extract_params.filter((item) => item.key !== key),
      );
    },
    [id, data],
  );

  const changeExtractParamItem = useCallback(
    (payload: IParameterExtractorNodeParam['extract_params'][number]) => {
      const { editExtractParam } = state;
      if (editExtractParam) {
        changeExtractParam(
          data.node_param.extract_params.map((item) =>
            item.key === editExtractParam.key ? payload : item,
          ),
        );
      } else {
        changeExtractParam([...data.node_param.extract_params, payload]);
      }
      setState({
        showExtractParamEditModal: false,
        editExtractParam: undefined,
      });
    },
    [changeExtractParam, data.node_param, state.editExtractParam],
  );

  const changeInputVariable = useCallback(
    (val: Partial<IVarItem>) => {
      handleNodeDataUpdate({
        id: id,
        data: {
          input_params: [
            {
              key: 'input',
              value_from: 'refer',
              type: 'String',
              ...val,
            },
          ],
        },
      });
    },
    [id, data.input_params, handleNodeDataUpdate],
  );

  const variableListByArrayType = useMemo(() => {
    const list: IVarTreeItem[] = [];
    variableList.forEach((item) => {
      const params = filterVarItemsByType(item.children, [
        'Array<String>',
        'Array<Object>',
      ]);
      if (!params.length) return;
      list.push({
        ...item,
        children: params,
      });
    });
    return list;
  }, [variableList]);

  const inputVariableList = useMemo(() => {
    const list: IVarTreeItem[] = [];
    variableList.forEach((item) => {
      const subList = filterVarItemsByType(item.children, ['String']);
      if (subList.length > 0) {
        list.push({
          ...item,
          children: subList,
        });
      }
    });
    return list;
  }, [variableList]);

  const fileVariableList = useMemo(() => {
    const list: IVarTreeItem[] = [];
    variableList.forEach((item) => {
      const subList = filterVarItemsByType(item.children, [
        'File',
        'Array<File>',
      ]);
      if (subList.length > 0) {
        list.push({
          ...item,
          children: subList,
        });
      }
    });
    return list;
  }, [variableList]);

  return (
    <>
      <div className="spark-flow-panel-form-section">
        <Flex vertical gap={12}>
          <div className="spark-flow-panel-form-title">
            {$i18n.get({
              id: 'main.pages.App.Workflow.nodes.ParameterExtractor.panel.input',
              dm: 'Input',
            })}

            <InfoIcon
              tip={$i18n.get({
                id: 'main.pages.App.Workflow.nodes.ParameterExtractor.panel.enterText',
                dm: 'Please enter the text content for parameter extraction.',
              })}
            />
          </div>
          <VariableSelector
            disabled={nodesReadOnly}
            variableList={inputVariableList}
            value={data.input_params[0]}
            onChange={changeInputVariable}
            prefix="String"
          />
        </Flex>
        <Flex vertical gap={12}>
          <div className="spark-flow-panel-form-title">
            {$i18n.get({
              id: 'main.pages.App.Workflow.nodes.ParameterExtractor.panel.modelSelection',
              dm: 'Model Selection',
            })}

            <InfoIcon
              tip={$i18n.get({
                id: 'main.pages.App.Workflow.nodes.ParameterExtractor.panel.modelChoice',
                dm: 'Model for parameter extraction. Choose according to your business needs.',
              })}
            />
          </div>
          <ModelConfigFormWrap
            disabled={nodesReadOnly}
            variableList={fileVariableList}
            value={data.node_param.model_config}
            onChange={(val) => changeNodeParam({ model_config: val })}
          />
        </Flex>
      </div>
      <div className="spark-flow-panel-form-section">
        <Flex vertical gap={12}>
          <div className="flex-justify-between">
            <div className="spark-flow-panel-form-title">
              {$i18n.get({
                id: 'main.pages.App.Workflow.nodes.ParameterExtractor.panel.extractParameters',
                dm: 'Extract Parameters',
              })}

              <InfoIcon
                tip={$i18n.get({
                  id: 'main.pages.App.Workflow.nodes.ParameterExtractor.panel.parameterExtraction',
                  dm: 'The model will extract parameters from the input based on name, type, and description.',
                })}
              />
            </div>
            <Button
              disabled={nodesReadOnly}
              type="link"
              onClick={handleAdd}
              size="small"
              className="self-start spark-flow-text-btn"
              icon={<IconFont type="spark-plus-line" />}
            >
              {$i18n.get({
                id: 'main.pages.App.Workflow.nodes.ParameterExtractor.panel.addVariable',
                dm: 'Add Variable',
              })}
            </Button>
          </div>
          {data.node_param.extract_params.map((item) => (
            <ExtractParamItem
              disabled={nodesReadOnly}
              onEdit={() => handleEditExtractParams(item)}
              key={item.key}
              data={item}
              onDelete={() => handleDeleteExtractParams(item.key)}
            />
          ))}
        </Flex>
        <Flex vertical gap={12}>
          <div className="spark-flow-panel-form-title">
            {$i18n.get({
              id: 'main.pages.App.Workflow.nodes.ParameterExtractor.panel.prompt',
              dm: 'Prompt',
            })}

            <InfoIcon
              tip={$i18n.get({
                id: 'main.pages.App.Workflow.nodes.ParameterExtractor.panel.additionalRules',
                dm: 'Additional rules to assist the model in parameter extraction.',
              })}
            />
          </div>
          <VarInputTextArea
            disabled={nodesReadOnly}
            variableList={variableList}
            value={data.node_param.instruction}
            onChange={(val) => changeNodeParam({ instruction: val })}
            maxLength={Number.MAX_SAFE_INTEGER}
          />
        </Flex>
      </div>
      <div className="spark-flow-panel-form-section">
        <ShortMemoryForm
          disabled={nodesReadOnly}
          onChange={(val) => changeNodeParam({ short_memory: val })}
          value={data.node_param.short_memory}
          variableList={variableListByArrayType}
        />
      </div>
      <div className="spark-flow-panel-form-section">
        <Flex vertical gap={12}>
          <div className="spark-flow-panel-form-title">
            {$i18n.get({
              id: 'main.pages.App.Workflow.nodes.ParameterExtractor.panel.output',
              dm: 'Output',
            })}

            <InfoIcon
              tip={$i18n.get({
                id: 'main.pages.App.Workflow.nodes.ParameterExtractor.panel.outputParameters',
                dm: 'Parameters extracted by the model will be returned as output parameters.',
              })}
            />
          </div>
          <OutputParamsTree data={data.output_params} />
        </Flex>
      </div>
      {state.showExtractParamEditModal && (
        <ExtractParamEditModal
          onCancel={() => setState({ showExtractParamEditModal: false })}
          onOk={changeExtractParamItem}
          extractParams={data.node_param.extract_params}
          initialValues={state.editExtractParam}
        />
      )}
    </>
  );
});
