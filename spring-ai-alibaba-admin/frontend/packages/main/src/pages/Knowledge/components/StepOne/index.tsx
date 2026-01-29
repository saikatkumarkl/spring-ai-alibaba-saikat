import SliderInput from '@/components/SliderInput';
import $i18n from '@/i18n';
import { Form, IconFont, Input, Tooltip } from '@spark-ai/design';
import React, { useRef } from 'react';
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
  similarity_threshold?: number;
  top_k?: number;
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
  const timerRef = useRef<NodeJS.Timeout | null>(null);
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
            onBlur={() => {
              if (timerRef.current) {
                clearTimeout(timerRef.current);
                timerRef.current = null;
              }
            }}
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
        <Form.Item
          label={
            <div className={styles['form-item-label']}>
              <span>
                {$i18n.get({
                  id: 'main.pages.Knowledge.components.StepOne.index.similarityThreshold',
                  dm: 'Similarity Threshold',
                })}
              </span>
              <Tooltip
                title={$i18n.get({
                  id: 'main.pages.Knowledge.components.StepOne.index.thresholdMeasureSimilarity',
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
            value={formValue.similarity_threshold}
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
                  id: 'main.pages.Knowledge.components.StepOne.index.topKReturnObjects',
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
            value={formValue.top_k}
            onChange={(val) => {
              changeFormValue({ top_k: val });
            }}
          />
        </Form.Item>
      </Form>
    </div>
  );
}
