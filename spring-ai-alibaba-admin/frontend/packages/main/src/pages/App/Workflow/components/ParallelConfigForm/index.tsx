import $i18n from '@/i18n';
import { SliderSelector } from '@spark-ai/design';
import { Flex } from 'antd';
import { memo } from 'react';
import { IParallelNodeParam } from '../../types';
import InfoIcon from '../InfoIcon';
import styles from './index.module.less';

export interface IParallelConfigFormProps {
  value: IParallelNodeParam;
  onChange: (value: Partial<IParallelNodeParam>) => void;
}

export default memo(function ParallelConfigForm({
  value,
  onChange,
}: IParallelConfigFormProps) {
  return (
    <div className={styles.form}>
      <Flex align="center" gap={8}>
        <Flex className={styles['label-wrap']} gap={4} align="center">
          <span>
            {$i18n.get({
              id: 'main.pages.App.Workflow.components.ParallelConfigForm.index.maxBatchCount',
              dm: 'Batch Processing Limit',
            })}
          </span>
          <InfoIcon
            tip={$i18n.get({
              id: 'main.pages.App.Workflow.components.ParallelConfigForm.index.batchExecutionLimit',
              dm: 'Batch processing runs should not exceed the limit',
            })}
          />
        </Flex>
        <SliderSelector
          min={1}
          max={200}
          step={1}
          value={value.batch_size}
          inputNumberWrapperStyle={{ width: 54 }}
          className="flex-1"
          onChange={(val) => onChange({ batch_size: val as number })}
        />
      </Flex>
      <Flex align="center" gap={8}>
        <Flex className={styles['label-wrap']} gap={4} align="center">
          <span>
            {$i18n.get({
              id: 'main.pages.App.Workflow.components.ParallelConfigForm.index.parallelCount',
              dm: 'Parallel Execution Count',
            })}
          </span>
          <InfoIcon
            tip={$i18n.get({
              id: 'main.pages.App.Workflow.components.ParallelConfigForm.index.concurrentLimit',
              dm: 'Batch concurrency limit, set to 1 for serial execution',
            })}
          />
        </Flex>
        <SliderSelector
          min={1}
          max={10}
          step={1}
          inputNumberWrapperStyle={{ width: 54 }}
          className="flex-1"
          value={value.concurrent_size}
          onChange={(val) => onChange({ concurrent_size: val as number })}
        />
      </Flex>
    </div>
  );
});
