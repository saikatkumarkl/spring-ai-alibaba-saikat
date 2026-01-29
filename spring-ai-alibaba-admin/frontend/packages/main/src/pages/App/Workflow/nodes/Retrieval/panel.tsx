import $i18n from '@/i18n';
import { KnowledgeSelectorModal } from '@/pages/App/components/KnowledgeSelector';
import { getKnowledgeListByCodes } from '@/services/knowledge';
import { IKnowledgeListItem } from '@/types/knowledge';
import { Button, IconFont, SliderSelector } from '@spark-ai/design';
import {
  filterVarItemsByType,
  IVarItem,
  IVarTreeItem,
  OutputParamsTree,
  useNodeDataUpdate,
  useNodesOutputParams,
  useNodesReadOnly,
  useReactFlowStore,
  VariableSelector,
} from '@spark-ai/flow';
import { useSetState } from 'ahooks';
import { Flex, Spin, Typography } from 'antd';
import classNames from 'classnames';
import { memo, useCallback, useEffect, useMemo } from 'react';
import InfoIcon from '../../components/InfoIcon';
import { useWorkflowAppStore } from '../../context/WorkflowAppProvider';
import { IRetrievalNodeData, IRetrievalNodeParam } from '../../types';
import styles from './index.module.less';

export default memo(function RetrievalPanel({
  id,
  data,
}: {
  id: string;
  data: IRetrievalNodeData;
}) {
  const { handleNodeDataUpdate } = useNodeDataUpdate();
  const [state, setState] = useSetState({
    list: [] as IKnowledgeListItem[],
    loading: true,
    showKnowledgeSelect: false,
  });
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

  const init = useCallback(() => {
    if (!data.node_param.knowledge_base_ids.length) {
      setState({
        list: [],
        loading: false,
      });
      return;
    }
    getKnowledgeListByCodes(data.node_param.knowledge_base_ids).then((res) => {
      setState({
        list: res,
        loading: false,
      });
    });
  }, [id]);

  const changeNodeParam = useCallback(
    (payload: Partial<IRetrievalNodeParam>) => {
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
    [data.node_param],
  );

  useEffect(() => {
    init();
  }, [id, init]);

  const removeKnowledge = useCallback(
    (kb_id: string) => {
      setState({
        list: state.list.filter((item) => item.kb_id !== kb_id),
      });
      changeNodeParam({
        knowledge_base_ids: data.node_param.knowledge_base_ids.filter(
          (item) => item !== kb_id,
        ),
      });
    },
    [data.node_param.knowledge_base_ids, state.list],
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
    [id, data.input_params],
  );

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

  return (
    <>
      <div className="spark-flow-panel-form-section">
        <Flex vertical gap={12}>
          <div className="spark-flow-panel-form-title">
            {$i18n.get({
              id: 'main.pages.App.Workflow.nodes.Retrieval.panel.input',
              dm: 'Input',
            })}

            <InfoIcon
              tip={$i18n.get({
                id: 'main.pages.App.Workflow.nodes.Retrieval.panel.matchedInformation',
                dm: 'Enter the information to be matched from the knowledge base.',
              })}
            />
          </div>
          <VariableSelector
            disabled={nodesReadOnly}
            onChange={changeInputVariable}
            value={data.input_params[0]}
            variableList={inputVariableList}
            prefix="String"
          />
        </Flex>
      </div>
      <div className="spark-flow-panel-form-section">
        {state.loading ? (
          <div className={styles['loading-container']}>
            <Spin className="loading-center" />
          </div>
        ) : (
          <Flex vertical gap={12}>
            <div className="flex-justify-between">
              <div className="spark-flow-panel-form-title">
                {$i18n.get({
                  id: 'main.pages.App.Workflow.nodes.Retrieval.panel.knowledgeBase',
                  dm: 'Knowledge Base',
                })}

                <InfoIcon
                  tip={$i18n.get({
                    id: 'main.pages.App.Workflow.nodes.Retrieval.panel.contentFromConfigured',
                    dm: 'Content will only be retrieved from the configured knowledge bases.',
                  })}
                />
              </div>
              {!!state.list.length && (
                <Button
                  disabled={nodesReadOnly}
                  className={styles['right-add']}
                  size="small"
                  icon={<IconFont type="spark-plus-line" />}
                  type="text"
                  onClick={() => setState({ showKnowledgeSelect: true })}
                >
                  {$i18n.get({
                    id: 'main.pages.App.Workflow.nodes.Retrieval.panel.knowledgeBase',
                    dm: 'Knowledge Base',
                  })}
                </Button>
              )}
            </div>
            {!state.list.length ? (
              <Button
                disabled={nodesReadOnly}
                onClick={() => setState({ showKnowledgeSelect: true })}
                icon={<IconFont type="spark-plus-line" />}
                type="dashed"
                className={styles['add-knowledge-btn']}
              >
                {$i18n.get({
                  id: 'main.pages.App.Workflow.nodes.Retrieval.panel.selectKnowledgeBase',
                  dm: 'Select Knowledge Base',
                })}
              </Button>
            ) : (
              <Flex vertical gap={12}>
                {state.list.map((item) => (
                  <Flex
                    key={item.kb_id}
                    className={styles['knowledge-item']}
                    gap={12}
                    justify="space-between"
                    align="center"
                  >
                    <Flex className="flex-1 w-[1px]" align="center" gap={4}>
                      <IconFont size="small" type="spark-book-line" />
                      <Typography.Text
                        ellipsis={{ tooltip: item.name }}
                        className={classNames(styles['knowledge-name'])}
                      >
                        {item.name}
                      </Typography.Text>
                    </Flex>
                    <IconFont
                      size="small"
                      onClick={() => removeKnowledge(item.kb_id)}
                      type="spark-delete-line"
                      className={nodesReadOnly ? 'disabled-icon-btn' : ''}
                      isCursorPointer={!nodesReadOnly}
                    />
                  </Flex>
                ))}
              </Flex>
            )}
            <div className={styles['panel-form-area']}>
              <div className="flex items-center gap-[12px]">
                <div className={styles['panel-form-area-label']}>
                  <span>topK</span>
                  <InfoIcon
                    tip={$i18n.get({
                      id: 'main.pages.App.Workflow.nodes.Retrieval.panel.topMatches',
                      dm: 'The number of top similar matches returned from each knowledge base.',
                    })}
                  />
                </div>
                <SliderSelector
                  disabled={nodesReadOnly}
                  value={data.node_param.top_k}
                  onChange={(value) => {
                    changeNodeParam({
                      top_k: value as number,
                    });
                  }}
                  step={1}
                  className="flex-1 ml-[20px] mr-[12px]"
                  min={1}
                  precision={0}
                  max={20}
                  inputNumberWrapperStyle={{ width: 54 }}
                />
              </div>
              <div className="flex items-center gap-[12px]">
                <div className={styles['panel-form-area-label']}>
                  <span>
                    {$i18n.get({
                      id: 'main.pages.App.Workflow.nodes.Retrieval.panel.similarityThreshold',
                      dm: 'Similarity Threshold',
                    })}
                  </span>
                  <InfoIcon
                    tip={$i18n.get({
                      id: 'main.pages.App.Workflow.nodes.Retrieval.panel.returnOnlyAboveThreshold',
                      dm: 'Only content with similarity above this threshold will be returned',
                    })}
                  />
                </div>
                <SliderSelector
                  disabled={nodesReadOnly}
                  value={data.node_param.similarity_threshold}
                  onChange={(value) => {
                    changeNodeParam({
                      similarity_threshold: value as number,
                    });
                  }}
                  step={0.01}
                  className="flex-1 ml-[20px] mr-[12px]"
                  min={0.01}
                  precision={2}
                  max={1}
                  inputNumberWrapperStyle={{ width: 54 }}
                />
              </div>
            </div>
          </Flex>
        )}
      </div>
      <div className="spark-flow-panel-form-section">
        <Flex vertical gap={12}>
          <div className="spark-flow-panel-form-title">
            {$i18n.get({
              id: 'main.pages.App.Workflow.nodes.Retrieval.panel.output',
              dm: 'Output',
            })}

            <InfoIcon
              tip={$i18n.get({
                id: 'main.pages.App.Workflow.nodes.Retrieval.panel.matchedContent',
                dm: 'Output the matched content and key fields.',
              })}
            />
          </div>
          <OutputParamsTree data={data.output_params} />
        </Flex>
      </div>
      {state.showKnowledgeSelect && (
        <KnowledgeSelectorModal
          value={state.list}
          onClose={() => setState({ showKnowledgeSelect: false })}
          onOk={(list) => {
            setState({
              list: list,
              showKnowledgeSelect: false,
            });
            changeNodeParam({
              knowledge_base_ids: list.map((item) => item.kb_id),
            });
          }}
        />
      )}
    </>
  );
});
