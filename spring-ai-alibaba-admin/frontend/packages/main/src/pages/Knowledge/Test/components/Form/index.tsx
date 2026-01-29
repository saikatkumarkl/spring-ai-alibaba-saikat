import SliderInput from '@/components/SliderInput';
import $i18n from '@/i18n';
import { Button, Form, IconFont, Input, Tooltip } from '@spark-ai/design';
import classNames from 'classnames';
import React, { useEffect } from 'react';
import styles from './index.module.less';
interface FormProps {
  /**
   * Custom style
   */
  className?: string;
  /**
   * Submit callback
   */
  onSubmit?: (values: any) => void;
  /**
   * Similarity threshold
   */
  similarity_threshold?: number;
}

const initialValues = {
  similarity_threshold: 0.2,
  input: '',
};
const TestForm: React.FC<FormProps> = ({
  onSubmit,
  className,
  similarity_threshold,
}) => {
  const [form] = Form.useForm();

  useEffect(() => {
    form.setFieldsValue({ similarity_threshold });
  }, [similarity_threshold]);

  return (
    <div className={classNames(styles['form-container'], className)}>
      <Form
        form={form}
        layout="vertical"
        onFinish={onSubmit}
        initialValues={initialValues}
      >
        <div className={styles['form-title']}>
          {$i18n.get({
            id: 'main.pages.Knowledge.Test.components.Form.index.databaseConfigDebug',
            dm: 'Database Configuration Debug',
          })}
        </div>
        <Form.Item
          label={
            <div className={styles['form-item-label']}>
              <span>
                {$i18n.get({
                  id: 'main.pages.Knowledge.Test.components.Form.index.similarityThreshold',
                  dm: 'Similarity Threshold',
                })}
              </span>
              <Tooltip
                title={$i18n.get({
                  id: 'main.pages.Knowledge.Test.components.Form.index.thresholdMeasureSimilarity',
                  dm: 'A threshold value used to measure the degree of similarity between texts or data. When the calculated text similarity reaches or exceeds this value, the text will be returned.',
                })}
                placement="rightBottom"
              >
                <IconFont
                  type="spark-info-line"
                  className={styles['info-icon']}
                />
              </Tooltip>
            </div>
          }
          name="similarity_threshold"
          rules={[
            {
              required: true,
              message: $i18n.get({
                id: 'main.pages.Knowledge.Test.components.Form.index.enterSimilarityThreshold',
                dm: 'Please enter similarity threshold',
              }),
            },
          ]}
        >
          <SliderInput min={0.01} max={0.99} step={0.01} />
        </Form.Item>

        <Form.Item
          label={$i18n.get({
            id: 'main.pages.Knowledge.Test.components.Form.index.input',
            dm: 'Input',
          })}
          name="query"
          rules={[
            {
              required: true,
              message: $i18n.get({
                id: 'main.pages.Knowledge.Test.components.Form.index.enter',
                dm: 'Please enter',
              }),
            },
          ]}
        >
          <Input.TextArea
            placeholder={$i18n.get({
              id: 'main.pages.Knowledge.Test.components.Form.index.enter',
              dm: 'Please enter',
            })}
            className={styles['text-area']}
            autoSize={{ minRows: 4, maxRows: 4 }}
          />
        </Form.Item>

        <Form.Item className={styles['form-item-submit']}>
          <Button
            type="default"
            icon={<IconFont type="spark-testing-line" />}
            className={styles['submit-button']}
            htmlType="submit"
          >
            {$i18n.get({
              id: 'main.pages.Knowledge.Test.components.Form.index.test',
              dm: 'Test',
            })}
          </Button>
        </Form.Item>
      </Form>
    </div>
  );
};

export default TestForm;
