import $i18n from '@/i18n';
import { Form, IconFont, Input, Tooltip } from '@spark-ai/design';
import React from 'react';
import ModelSelector from '../ModelSelector';
import styles from './index.module.less';

interface FormValue {
  name: string;
  description?: string;
  embedding_value?: string;
  embedding_model?: string;
  embedding_provider?: string;
  rerank_value?: string;
  rerank_model?: string;
  rerank_provider?: string;
  enable_rewrite?: boolean;
}

interface StepOneProps {
  formRef: React.RefObject<any>;
  changeFormValue: (value: Partial<FormValue>) => void;
  formValue: FormValue;
}

export default function StepOne({
  formRef,
  changeFormValue,
  formValue,
}: StepOneProps) {
  return (
    <div className={styles['step-one']}>
      <Form layout="vertical" ref={formRef}>
        <Form.Item
          label={$i18n.get({
            id: 'main.pages.Knowledge.components.StepOne.index.knowledgeBaseName',
            dm: 'Knowledge Base Name',
          })}
          required
        >
          <Input
            className={styles.input}
            value={formValue.name}
            onChange={(e) => {
              const newName = e.target.value;
              changeFormValue({ name: newName });
            }}
            showCount
            maxLength={15}
            placeholder={$i18n.get({
              id: 'main.pages.Knowledge.components.StepOne.index.enterKnowledgeBaseName',
              dm: 'Please enter knowledge base name',
            })}
          />
        </Form.Item>
        <Form.Item
          label={$i18n.get({
            id: 'main.pages.Knowledge.components.StepOne.index.knowledgeBaseDescription',
            dm: 'Knowledge Base Description',
          })}
          name="description"
        >
          <Input.TextArea
            placeholder={$i18n.get({
              id: 'main.pages.Knowledge.components.StepOne.index.enterKnowledgeBaseDescription',
              dm: 'Please enter knowledge base description',
            })}
            value={formValue.description}
            style={{ height: 100 }}
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
                  id: 'main.pages.Knowledge.components.StepOne.index.embeddingModel',
                  dm: 'Embedding Model',
                })}
              </span>

              <Tooltip
                title={$i18n.get({
                  id: 'main.pages.Knowledge.components.StepOne.index.modelConvertTextToVector',
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
            value={formValue.embedding_value}
            modelType="text_embedding"
            onChange={(val: string) => {
              changeFormValue({
                embedding_value: val,
                embedding_model: val?.split('@@@')[1],
                embedding_provider: val?.split('@@@')[0],
              });
            }}
          />
        </Form.Item>
        <Form.Item
          label={
            <div className={styles['form-item-label']}>
              <span>
                {$i18n.get({
                  id: 'main.pages.Knowledge.components.StepOne.index.rerankModel',
                  dm: 'Rerank Model',
                })}
              </span>
              <Tooltip
                title={$i18n.get({
                  id: 'main.pages.Knowledge.components.StepOne.index.rerankModelReorderResults',
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
            value={formValue.rerank_value}
            modelType="rerank"
            onChange={(val: string) => {
              changeFormValue({
                rerank_value: val,
                rerank_model: val?.split('@@@')[1],
                rerank_provider: val?.split('@@@')[0],
              });
            }}
          />
        </Form.Item>
      </Form>
    </div>
  );
}
