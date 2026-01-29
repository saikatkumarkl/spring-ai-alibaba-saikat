import $i18n from '@/i18n';
import { SliderSelector } from '@spark-ai/design';
import { IVarTreeItem, SelectWithDesc, VariableSelector } from '@spark-ai/flow';
import { Flex, Switch } from 'antd';
import classNames from 'classnames';
import { memo } from 'react';
import { IShortMemoryConfig } from '../../types';
import InfoIcon from '../InfoIcon';
import styles from './index.module.less';

const memoryOptions = [
  {
    label: $i18n.get({
      id: 'main.pages.App.Workflow.components.ShortMemoryForm.index.nodeCache',
      dm: 'Node Cache',
    }),
    value: 'self',
    desc: $i18n.get({
      id: 'main.pages.App.Workflow.components.ShortMemoryForm.index.contextualInfoOnlyInNode',
      dm: 'The model will only remember contextual information within this node.',
    }),
  },
  {
    label: $i18n.get({
      id: 'main.pages.App.Workflow.components.ShortMemoryForm.index.customCache',
      dm: 'Custom Cache',
    }),
    value: 'custom',
    desc: $i18n.get({
      id: 'main.pages.App.Workflow.components.ShortMemoryForm.index.globalContextualInfo',
      dm: 'The model will remember global contextual information.',
    }),
  },
];

export default memo(function ShortMemoryForm({
  value,
  onChange,
  variableList,
  disabled,
}: {
  value: IShortMemoryConfig;
  onChange: (value: IShortMemoryConfig) => void;
  variableList: IVarTreeItem[];
  disabled?: boolean;
}) {
  return (
    <>
      <Flex vertical gap={12}>
        <div className={classNames('flex items-center justify-between')}>
          <div className="spark-flow-panel-form-title">
            {$i18n.get({
              id: 'main.pages.App.Workflow.components.ShortMemoryForm.index.memory',
              dm: 'Memory',
            })}
          </div>
          <Switch
            value={value.enabled}
            onChange={(val) => onChange({ ...value, enabled: val })}
            disabled={disabled}
          />
        </div>
        {value.enabled && (
          <SelectWithDesc
            value={value.type}
            onChange={(val) => onChange({ ...value, type: val })}
            options={memoryOptions}
            disabled={disabled}
          />
        )}
      </Flex>
      {value.enabled &&
        (value.type === 'custom' ? (
          <Flex vertical gap={12}>
            <div className="spark-flow-panel-form-title">
              {$i18n.get({
                id: 'main.pages.App.Workflow.components.ShortMemoryForm.index.contextVariables',
                dm: 'Context Variables',
              })}
            </div>
            <VariableSelector
              disabled={disabled}
              variableList={variableList}
              value={value.param}
              onChange={(val) =>
                onChange({
                  ...value,
                  param: {
                    ...value.param,
                    ...val,
                  },
                })
              }
            />
          </Flex>
        ) : (
          <Flex vertical gap={12}>
            <div className="spark-flow-panel-form-title">
              <span>
                {$i18n.get({
                  id: 'main.pages.App.Workflow.components.ShortMemoryForm.index.memoryRounds',
                  dm: 'Memory Rounds',
                })}
              </span>
              <InfoIcon
                tip={$i18n.get({
                  id: 'main.pages.App.Workflow.components.ShortMemoryForm.index.memoryRoundsDefinition',
                  dm: 'Represents the number of memory rounds, where one input and one output represent one round.',
                })}
              />
            </div>
            <SliderSelector
              disabled={disabled}
              min={1}
              max={50}
              step={1}
              value={value.round}
              onChange={(val) =>
                onChange({
                  ...value,
                  round: val as IShortMemoryConfig['round'],
                })
              }
              inputNumberWrapperStyle={{ width: 54 }}
              className={classNames('flex-1', styles['slider-selector'])}
            />
          </Flex>
        ))}
    </>
  );
});
