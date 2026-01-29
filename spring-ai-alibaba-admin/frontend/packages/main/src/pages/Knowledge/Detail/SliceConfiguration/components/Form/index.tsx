import SliderInput from '@/components/SliderInput';
import $i18n from '@/i18n';
import { ChunkType } from '@/pages/Knowledge/components/StepThree';
import { Form, IconFont } from '@spark-ai/design';
import { Button, Input } from 'antd';
import classNames from 'classnames';
import React, { useState } from 'react';
import styles from './index.module.less';
interface FormProps {
  /**
   * Custom style
   */
  className?: string;
  /**
   * Form instance
   */
  formRef: React.RefObject<HTMLFormElement>;
  /**
   * Preview callback
   */
  onSubmit: (values: any) => void;
}

const initialValues = {
  chunk_size: 600,
  chunk_overlap: 100,
  input: '',
  chunk_type: 'length',
};

const TestForm: React.FC<FormProps> = ({ formRef, className, onSubmit }) => {
  const [form] = Form.useForm();
  const [chunkType, setchunkType] = useState('length');

  return (
    <div className={classNames(styles['form-container'], className)}>
      <Form
        form={form}
        layout="vertical"
        onFinish={onSubmit}
        // @ts-ignore
        ref={formRef}
        className={styles['form']}
        initialValues={initialValues}
      >
        <Form.Item
          label={$i18n.get({
            id: 'main.pages.Knowledge.Detail.SliceConfiguration.components.Form.index.chunkSplittingMethod',
            dm: 'Chunk Splitting Method',
          })}
          name="chunk_type"
        >
          <ChunkType
            className={styles['chunk-type']}
            onChange={(value: any) => {
              setchunkType(value);
            }}
          />
        </Form.Item>
        {chunkType === 'regex' && (
          <Form.Item
            label={$i18n.get({
              id: 'main.pages.Knowledge.components.StepThree.index.inputRegularExpression',
              dm: 'Enter Regular Expression',
            })}
            rules={[
              {
                required: true,
                message: $i18n.get({
                  id: 'main.pages.Knowledge.Detail.SliceConfiguration.components.Form.index.enterRegularExpression',
                  dm: 'Please enter regular expression',
                }),
              },
            ]}
            name="regex"
          >
            <Input.TextArea style={{ height: 58 }} />
          </Form.Item>
        )}
        {chunkType !== 'regex' && (
          <Form.Item
            label={$i18n.get({
              id: 'main.pages.Knowledge.Detail.SliceConfiguration.components.Form.index.estimatedChunkLength',
              dm: 'Estimated Chunk Length',
            })}
            name="chunk_size"
            rules={[
              {
                required: true,
                message: $i18n.get({
                  id: 'main.pages.Knowledge.Detail.SliceConfiguration.components.Form.index.enterEstimatedChunkLength',
                  dm: 'Please enter estimated chunk length',
                }),
              },
            ]}
          >
            <SliderInput min={10} max={6000} step={1} isShowMarker={true} />
          </Form.Item>
        )}

        {(chunkType === 'length' || chunkType === 'regex') && (
          <Form.Item
            label={$i18n.get({
              id: 'main.pages.Knowledge.Detail.SliceConfiguration.components.Form.index.chunkOverlapLength',
              dm: 'Chunk Overlap Length',
            })}
            name="chunk_overlap"
            rules={[
              {
                required: true,
                message: $i18n.get({
                  id: 'main.pages.Knowledge.Detail.SliceConfiguration.components.Form.index.enterChunkOverlapLength',
                  dm: 'Please enter chunk overlap length',
                }),
              },
            ]}
          >
            <SliderInput min={1} max={1024} step={1} isShowMarker />
          </Form.Item>
        )}

        <Form.Item>
          <Button
            type="default"
            icon={
              <IconFont
                type="spark-visable-line"
                className={styles['submit-icon']}
              />
            }
            className={styles['submit-button']}
            htmlType="submit"
          >
            {$i18n.get({
              id: 'main.pages.Knowledge.Detail.SliceConfiguration.components.Form.index.previewNow',
              dm: 'Preview Now',
            })}
          </Button>
        </Form.Item>
      </Form>
    </div>
  );
};

export default TestForm;
