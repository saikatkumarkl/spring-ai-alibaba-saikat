import SliderInput from '@/components/SliderInput';
import $i18n from '@/i18n';
import { Form, Input } from '@spark-ai/design';
import { Flex } from 'antd';
import classNames from 'classnames';
import React from 'react';
import RadioItem from '../RadioItem';
import styles from './index.module.less';

interface StepThreeProps {
  formRef: React.RefObject<any>;
  changeFormValue: (value: any) => void;
  formValue: any;
}
const chunkOpts = [
  {
    label: $i18n.get({
      id: 'main.pages.Knowledge.components.StepThree.index.splitByLength',
      dm: 'Split by Length',
    }),
    value: 'length',
    desc: $i18n.get({
      id: 'main.pages.Knowledge.components.StepThree.index.suitableForTokenCount',
      dm: 'Suitable for scenarios with strict token count requirements, such as when using models with smaller context lengths.',
    }),
  },
  {
    label: $i18n.get({
      id: 'main.pages.Knowledge.components.StepThree.index.splitByPage',
      dm: 'Split by Page',
    }),
    value: 'page',
    desc: $i18n.get({
      id: 'main.pages.Knowledge.components.StepThree.index.suitableForIndependentPages',
      dm: 'Suitable for documents where each page conveys an independent topic, requiring content from different pages not to be mixed in the same text chunk.',
    }),
  },
  {
    label: $i18n.get({
      id: 'main.pages.Knowledge.components.StepThree.index.splitByTitle',
      dm: 'Split by Title',
    }),
    value: 'title',
    desc: $i18n.get({
      id: 'main.pages.Knowledge.components.StepThree.index.suitableForIndependentTitles',
      dm: 'Suitable for documents divided by titles conveying independent topics, requiring content under different heading levels not to be mixed in the same text chunk.',
    }),
  },
  {
    label: $i18n.get({
      id: 'main.pages.Knowledge.components.StepThree.index.splitByRegex',
      dm: 'Split by Regex',
    }),
    value: 'regex',
    desc: $i18n.get({
      id: 'main.pages.Knowledge.components.StepThree.index.splitByRegexExpression',
      dm: 'Split the text in the knowledge base based on the configured regular expression.',
    }),
  },
];

export const ChunkType = ({ value, onChange, disabled, className }: any) => {
  return (
    <Flex gap={12} wrap>
      {chunkOpts.map((item) => (
        <RadioItem
          className={classNames(styles.mcpInstallTypeItem, className)}
          onSelect={() => {
            onChange(item.value);
          }}
          isActive={value === item.value}
          disabled={disabled}
          {...item}
          key={item.value}
        />
      ))}
    </Flex>
  );
};
export default function StepThree({
  formRef,
  changeFormValue,
  formValue,
}: StepThreeProps) {
  return (
    <div className={styles['step-three']}>
      <Form layout="vertical" ref={formRef}>
        <Form.Item
          label={$i18n.get({
            id: 'main.pages.Knowledge.components.StepThree.index.chunkSplittingMethod',
            dm: 'Chunk Splitting Method',
          })}
        >
          <ChunkType
            onChange={(value: any) => {
              changeFormValue({
                chunk_type: value,
              });
            }}
            value={formValue.chunk_type}
          />
        </Form.Item>
        {formValue.chunk_type === 'regex' && (
          <Form.Item
            label={$i18n.get({
              id: 'main.pages.Knowledge.components.StepThree.index.inputRegularExpression',
              dm: 'Enter Regular Expression',
            })}
            required
          >
            <Input.TextArea
              style={{ height: 58 }}
              value={formValue.regex}
              onChange={(e) => {
                changeFormValue({
                  regex: e.target.value,
                });
              }}
            />
          </Form.Item>
        )}
        {formValue.chunk_type !== 'regex' && (
          <Form.Item
            label={$i18n.get({
              id: 'main.pages.Knowledge.components.StepThree.index.segmentEstimatedLength',
              dm: 'Segment Estimated Length',
            })}
          >
            <SliderInput
              min={10}
              max={6000}
              step={1}
              style={{ width: 480 }}
              isShowMarker={true}
              value={formValue.chunk_size}
              onChange={(value) => {
                changeFormValue({
                  chunk_size: value,
                });
              }}
            />
          </Form.Item>
        )}
        {(formValue.chunk_type === 'length' ||
          formValue.chunk_type === 'regex') && (
          <Form.Item
            label={$i18n.get({
              id: 'main.pages.Knowledge.components.StepThree.index.segmentOverlapLength',
              dm: 'Segment Overlap Length',
            })}
          >
            <SliderInput
              min={1}
              max={1024}
              step={1}
              style={{ width: 480 }}
              isShowMarker={true}
              value={formValue.chunk_overlap}
              onChange={(value) => {
                changeFormValue({
                  chunk_overlap: value,
                });
              }}
            />
          </Form.Item>
        )}
      </Form>
    </div>
  );
}
