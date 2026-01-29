import InnerLayout from '@/components/InnerLayout';
import SliderInput from '@/components/SliderInput';
import $i18n from '@/i18n';
import ModelSelector from '@/pages/Knowledge/components/ModelSelector';
import { getKnowledgeDetail, updateKnowledge } from '@/services/knowledge';
import { IKnowledgeDetail } from '@/types/knowledge';
import {
  Button,
  Form,
  IconFont,
  Input,
  message,
  Tooltip,
} from '@spark-ai/design';
import { useRequest, useSetState } from 'ahooks';
import { Modal as AntModal } from 'antd';
import { useRef } from 'react';
import { useParams } from 'react-router-dom';
import { history } from 'umi';
import styles from './index.module.less';

export default function Editor() {
  const { kb_id } = useParams<{ kb_id: string }>();
  const formRef = useRef<any>(null);
  const [state, setState] = useSetState({
    name: '',
    description: '',
    embedding_value: '',
    embedding_model: '',
    embedding_provider: '',
    rerank_value: '',
    rerank_model: '',
    rerank_provider: '',
    similarity_threshold: 0.2,
    top_k: 3,
  });
  useRequest(() => getKnowledgeDetail(kb_id || ''), {
    onSuccess: (data: IKnowledgeDetail) => {
      const { index_config, search_config } = data;
      setState({
        name: data.name,
        description: data.description,
        embedding_value: index_config.embedding_model
          ? `${index_config.embedding_provider}@@@${index_config.embedding_model}`
          : '',
        embedding_model: index_config?.embedding_model,
        embedding_provider: index_config?.embedding_provider,
        rerank_value: search_config.rerank_model
          ? `${search_config.rerank_provider}@@@${search_config.rerank_model}`
          : '',
        rerank_model: search_config?.rerank_model,
        rerank_provider: search_config?.rerank_provider,
        similarity_threshold: search_config.similarity_threshold,
        top_k: search_config.top_k,
      });
    },
  });

  const changeFormValue = (payload: any) => {
    setState((prev) => ({
      ...prev,
      ...payload,
    }));
  };
  const handleSave = () => {
    validatedFormValues()
      .then(() => {
        const {
          top_k,
          similarity_threshold,
          rerank_provider,
          rerank_model,
          embedding_provider,
          embedding_model,
          ...rest
        } = state;
        const params = {
          kb_id: kb_id?.toString() || '',
          search_config: {
            top_k,
            similarity_threshold,
            rerank_provider,
            rerank_model,
          },
          index_config: {
            embedding_provider,
            embedding_model,
          },
          ...rest,
        };
        updateKnowledge(params).then(() => {
          message.success(
            $i18n.get({
              id: 'main.pages.Knowledge.Editor.index.saveSuccess',
              dm: 'Saved successfully',
            }),
          );
          history.push('/knowledge');
        });
      })
      .catch((err) => {
        message.error(err.message);
      });
  };
  const validatedFormValues = () => {
    return new Promise((resolve, reject) => {
      if (!state.name?.trim()) {
        reject(
          $i18n.get({
            id: 'main.pages.Knowledge.Create.index.pleaseEnterKnowledgeBaseName',
            dm: 'Please enter knowledge base name',
          }),
        );
        return;
      }
      if (!state.embedding_value?.trim()) {
        reject(
          $i18n.get({
            id: 'main.pages.Knowledge.Create.index.pleaseSelectEmbeddingModel',
            dm: 'Please select Embedding model',
          }),
        );
        return;
      }

      if (!state.rerank_value?.trim()) {
        reject(
          $i18n.get({
            id: 'main.pages.Knowledge.Create.index.pleaseSelectRerankModel',
            dm: 'Please select Rerank model',
          }),
        );
        return;
      }
      resolve(state);
    });
  };
  const handleCancel = () => {
    AntModal.confirm({
      title: (
        <span className={styles['confirm-title']}>
          {$i18n.get({
            id: 'main.pages.Knowledge.Editor.index.confirmDiscardEditing',
            dm: 'Confirm to discard editing knowledge base?',
          })}
        </span>
      ),

      icon: (
        <IconFont
          type="spark-warningCircle-line"
          className={styles['warning-icon']}
        />
      ),

      content: (
        <span className={styles['confirm-content']}>
          {$i18n.get({
            id: 'main.pages.Knowledge.Editor.index.discardChangesDataNotSaved',
            dm: 'After discarding, the data you just filled in will not be saved. Please proceed with caution.',
          })}
        </span>
      ),

      okText: $i18n.get({
        id: 'main.pages.Knowledge.Editor.index.confirmDiscard',
        dm: 'Confirm Discard',
      }),
      cancelText: $i18n.get({
        id: 'main.pages.Knowledge.Editor.index.continueEditing',
        dm: 'Continue Editing',
      }),
      onOk: () => {
        history.push('/knowledge');
      },
    });
  };
  return (
    <InnerLayout
      breadcrumbLinks={[
        {
          title: $i18n.get({
            id: 'main.pages.Knowledge.Editor.index.knowledgeBase',
            dm: 'Knowledge Base',
          }),
          path: '/knowledge',
        },
        {
          title: $i18n.get({
            id: 'main.pages.Knowledge.Editor.index.editKnowledgeBase',
            dm: 'Edit Knowledge Base',
          }),
        },
      ]}
      bottom={
        <div className={styles['footer']}>
          <div className={styles['footer-btn']}>
            <Button
              type="primary"
              onClick={() => {
                validatedFormValues()
                  .then(() => {
                    handleSave();
                  })
                  .catch((errInfo) => message.warning(errInfo));
              }}
            >
              {$i18n.get({
                id: 'main.pages.Knowledge.Editor.index.save',
                dm: 'Save',
              })}
            </Button>
            <Button onClick={handleCancel}>
              {$i18n.get({
                id: 'main.pages.Knowledge.Editor.index.cancel',
                dm: 'Cancel',
              })}
            </Button>
          </div>
        </div>
      }
    >
      <div className={styles['container']}>
        <Form layout="vertical" ref={formRef}>
          <Form.Item
            label={$i18n.get({
              id: 'main.pages.Knowledge.Editor.index.knowledgeBaseName',
              dm: 'Knowledge Base Name',
            })}
            required
          >
            <Input
              placeholder={$i18n.get({
                id: 'main.pages.Knowledge.Editor.index.enterKnowledgeBaseName',
                dm: 'Please enter knowledge base name',
              })}
              value={state.name}
              onChange={(e) => {
                changeFormValue({ name: e.target.value });
              }}
            />
          </Form.Item>
          <Form.Item
            label={$i18n.get({
              id: 'main.pages.Knowledge.Editor.index.knowledgeBaseDescription',
              dm: 'Knowledge Base Description',
            })}
          >
            <Input.TextArea
              placeholder={$i18n.get({
                id: 'main.pages.Knowledge.Editor.index.enterKnowledgeBaseDescription',
                dm: 'Please enter knowledge base description',
              })}
              value={state.description}
              onChange={(e) => {
                changeFormValue({ description: e.target.value });
              }}
            />
          </Form.Item>
          <Form.Item
            label={
              <div className={styles['form-item-label']}>
                <span>
                  {$i18n.get({
                    id: 'main.pages.Knowledge.Editor.index.embeddingModel',
                    dm: 'Embedding Model',
                  })}
                </span>
                <Tooltip
                  title={$i18n.get({
                    id: 'main.pages.Knowledge.Editor.index.modelConvertTextToVector',
                    dm: 'A model that converts text into vector representations, mapping text information to low-dimensional dense vector spaces for computer understanding of text semantics, supporting subsequent similarity calculations.',
                  })}
                >
                  <IconFont
                    type="spark-info-line"
                    className={styles['info-icon']}
                  />
                </Tooltip>
              </div>
            }
            required
          >
            <ModelSelector
              value={state.embedding_value}
              modelType="text_embedding"
              onChange={(val: string) => {
                changeFormValue({
                  embedding_value: val,
                  embedding_model: val.split('@@@')[1],
                  embedding_provider: val.split('@@@')[0],
                });
              }}
            />
          </Form.Item>
          <Form.Item
            label={
              <div className={styles['form-item-label']}>
                <span>
                  {$i18n.get({
                    id: 'main.pages.Knowledge.Editor.index.rerankModel',
                    dm: 'Rerank Model',
                  })}
                </span>
                <Tooltip
                  title={$i18n.get({
                    id: 'main.pages.Knowledge.Editor.index.rerankModelReorderResults',
                    dm: 'The Rerank model reorders search results after retrieval, adjusting result order with more precise algorithms to place more relevant content at the top, improving search result quality.',
                  })}
                >
                  <IconFont
                    type="spark-info-line"
                    className={styles['info-icon']}
                  />
                </Tooltip>
              </div>
            }
            required
          >
            <ModelSelector
              value={state.rerank_value}
              modelType="rerank"
              onChange={(val: string) => {
                changeFormValue({
                  rerank_value: val,
                  rerank_model: val.split('@@@')[1],
                  rerank_provider: val.split('@@@')[0],
                });
              }}
            />
          </Form.Item>
          <Form.Item
            label={
              <div className={styles['form-item-label']}>
                <span>
                  {$i18n.get({
                    id: 'main.pages.Knowledge.Editor.index.similarityThreshold',
                    dm: 'Similarity Threshold',
                  })}
                </span>
                <Tooltip
                  title={$i18n.get({
                    id: 'main.pages.Knowledge.Editor.index.thresholdMeasureSimilarity',
                    dm: 'A threshold value used to measure the degree of similarity between texts or data. When the calculated text similarity reaches or exceeds this value, the text will be returned.',
                  })}
                >
                  <IconFont
                    type="spark-info-line"
                    className={styles['info-icon']}
                  />
                </Tooltip>
              </div>
            }
          >
            <SliderInput
              min={0.01}
              max={0.99}
              step={0.01}
              style={{ width: 480 }}
              value={state.similarity_threshold}
              onChange={(val) => {
                changeFormValue({ similarity_threshold: val });
              }}
            />
          </Form.Item>
          <Form.Item
            label={
              <div className={styles['form-item-label']}>
                <span>Topk</span>
                <Tooltip
                  title={$i18n.get({
                    id: 'main.pages.Knowledge.Editor.index.topKReturnObjects',
                    dm: 'Top-k represents the number of objects that meet similarity requirements returned after reranking',
                  })}
                >
                  <IconFont
                    type="spark-info-line"
                    className={styles['info-icon']}
                  />
                </Tooltip>
              </div>
            }
          >
            <SliderInput
              min={1}
              max={10}
              step={1}
              style={{ width: 480 }}
              value={state.top_k}
              onChange={(val) => {
                changeFormValue({ top_k: val });
              }}
            />
          </Form.Item>
        </Form>
      </div>
    </InnerLayout>
  );
}
