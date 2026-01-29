import { TopExpandBtn } from '@/components/ExpandBtn';
import $i18n from '@/i18n';
import { HelpIcon } from '@spark-ai/design';
import { IValueType } from '@spark-ai/flow';
import { Flex, Input, Select } from 'antd';
import classNames from 'classnames';
import { useState } from 'react';
import styles from './index.module.less';

export interface IParamItem {
  field: string;
  type: IValueType;
  description: string;
  default_value: any;
  alias: string;
  required: boolean;
  display: boolean;
  source: 'biz' | 'model';
}

export interface IUserParamItem {
  code: string;
  name: string;
  params: IParamItem[];
}

export interface IOutputParamItem {
  field: string;
  type: IValueType;
  description: string;
}

export interface IConfigInput {
  user_params: IUserParamItem[];
  system_params: IParamItem[];
}

interface IProps {
  input: IConfigInput;
  onChange: (val: Partial<IConfigInput>) => void;
  disabled?: boolean;
}

interface IInputCompItemProps {
  params: IParamItem[];
  name: string;
  onChange: (val: IParamItem[]) => void;
  disabled?: boolean;
}

export function InputCompItem(props: IInputCompItemProps) {
  const [expand, setExpand] = useState(true);
  const changeRowItem = (payload: Partial<IParamItem>, ind: number) => {
    props.onChange(
      props.params.map((item, index) => ({
        ...item,
        ...(ind === index ? payload : {}),
      })),
    );
  };
  return (
    <div className="flex flex-col gap-3">
      <Flex className={styles['title-wrap']} gap={8}>
        <span className={styles.title}>{props.name}</span>
        <TopExpandBtn setExpand={setExpand} expand={expand} />
      </Flex>
      {expand && (
        <Flex className={styles['inputs-form']} vertical gap={8}>
          <Flex className={styles['key-label']} gap={8}>
            <span className={styles.name}>
              {$i18n.get({
                id: 'main.pages.Component.AppComponent.components.InputParamsComp.index.parameterName',
                dm: 'Parameter Name',
              })}
            </span>
            <span className={classNames(styles.alias, 'flex items-center')}>
              <span>
                {$i18n.get({
                  id: 'main.pages.Component.AppComponent.components.InputParamsComp.index.alias',
                  dm: 'Alias',
                })}
              </span>
              <HelpIcon
                content={$i18n.get({
                  id: 'main.pages.Component.AppComponent.components.InputParamsComp.index.youNeedToDefineAliasForComponentInputParameters',
                  dm: 'You need to define an alias for the component input parameters. Users will only see the alias when referencing the component, not the actual parameter name.',
                })}
              />
              {!props.disabled && <img src="/images/require.svg" alt="" />}
            </span>
            <span className={styles.desc}>
              {$i18n.get({
                id: 'main.pages.Component.AppComponent.components.InputParamsComp.index.parameterDescription',
                dm: 'Parameter Description',
              })}
            </span>
            <span className={styles.type}>
              {$i18n.get({
                id: 'main.pages.Component.AppComponent.components.InputParamsComp.index.type',
                dm: 'Type',
              })}
            </span>
            <span className={styles.required}>
              {$i18n.get({
                id: 'main.pages.Component.AppComponent.components.InputParamsComp.index.isRequired',
                dm: 'Required',
              })}
            </span>
            <span className={styles.enable}>
              {$i18n.get({
                id: 'main.pages.Component.AppComponent.components.InputParamsComp.index.isVisible',
                dm: 'Visible',
              })}
            </span>
            <span className={styles.source}>
              {$i18n.get({
                id: 'main.pages.Component.AppComponent.components.InputParamsComp.index.passingMethod',
                dm: 'Passing Method',
              })}
            </span>
            <span className={styles['default-value']}>
              {$i18n.get({
                id: 'main.pages.Component.AppComponent.components.InputParamsComp.index.defaultValue',
                dm: 'Default Value',
              })}
            </span>
          </Flex>
          {props.params.map((item, index) => (
            <Flex
              key={`${item.field}_${index}`}
              className={styles['form-row-item']}
              gap={8}
              align="center"
            >
              <Input value={item.field} className={styles.name} disabled />
              <Input
                onChange={(e) =>
                  changeRowItem({ alias: e.target.value }, index)
                }
                value={item.alias}
                className={styles.alias}
                placeholder={$i18n.get({
                  id: 'main.pages.Component.AppComponent.components.InputParamsComp.index.enterParameterAlias',
                  dm: 'Enter parameter alias',
                })}
                disabled={props.disabled}
              />

              <Input
                onChange={(e) =>
                  changeRowItem({ description: e.target.value }, index)
                }
                value={item.description}
                className={styles.desc}
                disabled={props.disabled}
                placeholder={
                  props.disabled
                    ? ''
                    : $i18n.get({
                        id: 'main.pages.Component.AppComponent.components.InputParamsComp.index.enterParameterDescription',
                        dm: 'Enter parameter description',
                      })
                }
              />

              <Input value={item.type} className={styles.type} disabled />
              <Select
                value={item.required}
                className={styles.required}
                disabled={props.disabled}
                options={[
                  {
                    label: $i18n.get({
                      id: 'main.pages.Component.AppComponent.components.InputParamsComp.index.yes',
                      dm: 'Yes',
                    }),
                    value: true,
                  },
                  {
                    label: $i18n.get({
                      id: 'main.pages.Component.AppComponent.components.InputParamsComp.index.no',
                      dm: 'No',
                    }),
                    value: false,
                  },
                ]}
                onChange={(val) => changeRowItem({ required: val }, index)}
              />

              <Select
                value={item.display}
                className={styles.enable}
                options={[
                  {
                    label: $i18n.get({
                      id: 'main.pages.Component.AppComponent.components.InputParamsComp.index.yes',
                      dm: 'Yes',
                    }),
                    value: true,
                  },
                  {
                    label: $i18n.get({
                      id: 'main.pages.Component.AppComponent.components.InputParamsComp.index.no',
                      dm: 'No',
                    }),
                    value: false,
                  },
                ]}
                disabled={props.disabled}
                onChange={(val) => changeRowItem({ display: val }, index)}
              />

              <Select
                value={item.source}
                className={styles.source}
                disabled={props.disabled}
                onChange={(val) => changeRowItem({ source: val }, index)}
                options={[
                  {
                    label: $i18n.get({
                      id: 'main.pages.Component.AppComponent.components.InputParamsComp.index.businessPassThrough',
                      dm: 'Business Passthrough',
                    }),
                    value: 'biz',
                  },
                  {
                    label: $i18n.get({
                      id: 'main.pages.Component.AppComponent.components.InputParamsComp.index.modelRecognition',
                      dm: 'Model Recognition',
                    }),
                    value: 'model',
                  },
                ]}
              />

              <Input
                onChange={(e) =>
                  changeRowItem({ default_value: e.target.value }, index)
                }
                value={item.default_value}
                className={styles['default-value']}
                disabled={props.disabled}
                placeholder={
                  props.disabled
                    ? ''
                    : $i18n.get({
                        id: 'main.pages.Component.AppComponent.components.InputParamsComp.index.inputDefaultValue',
                        dm: 'Enter default value',
                      })
                }
              />
            </Flex>
          ))}
        </Flex>
      )}
    </div>
  );
}

export default function InputParamsComp(props: IProps) {
  const handleChangeUserParams = (params: IParamItem[], code: string) => {
    const newUserParams = props.input.user_params.map((item) => {
      if (item.code === code)
        return {
          ...item,
          params,
        };
      return item;
    });
    props.onChange({
      user_params: newUserParams,
    });
  };

  return (
    <Flex vertical gap={20}>
      {props.input.user_params.map((item) => (
        <InputCompItem
          onChange={(val) => handleChangeUserParams(val, item.code)}
          name={item.name}
          key={item.code}
          params={item.params}
          disabled={props.disabled}
        />
      ))}
      <InputCompItem
        name={$i18n.get({
          id: 'main.pages.Component.AppComponent.components.InputParamsComp.index.systemParameter',
          dm: 'System Parameters',
        })}
        onChange={(val) => props.onChange({ system_params: val })}
        params={props.input.system_params}
        disabled={props.disabled}
      />
    </Flex>
  );
}
