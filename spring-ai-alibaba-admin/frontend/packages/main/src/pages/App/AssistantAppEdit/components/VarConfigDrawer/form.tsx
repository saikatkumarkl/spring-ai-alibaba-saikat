import { Flex, Spin, Typography } from 'antd';

import $i18n from '@/i18n';
import {
  Empty,
  Form,
  Input,
  InputNumber,
  renderTooltip,
  Select,
} from '@spark-ai/design';
import { BizVarItem } from '.';
import styles from './form.module.less';

export function ConfigFormItem(props: { item: BizVarItem }) {
  const { params } = props.item;
  const renderFormInput = (type: string) => {
    switch (type) {
      case 'Boolean':
        return (
          <Select
            options={[
              {
                value: 'true',
                label: $i18n.get({
                  id: 'main.pages.App.AssistantAppEdit.components.VarConfigDrawer.form.yes',
                  dm: 'Yes',
                }),
              },
              {
                value: 'false',
                label: $i18n.get({
                  id: 'main.pages.App.AssistantAppEdit.components.VarConfigDrawer.form.no',
                  dm: 'No',
                }),
              },
            ]}
            placeholder={$i18n.get({
              id: 'main.pages.App.AssistantAppEdit.components.VarConfigDrawer.form.select',
              dm: 'Please select',
            })}
          />
        );

      case 'Number':
        return (
          <InputNumber
            placeholder={$i18n.get({
              id: 'main.pages.App.AssistantAppEdit.components.VarConfigDrawer.form.enterValue',
              dm: 'Please enter value',
            })}
          />
        );
      default:
        return (
          <Input
            placeholder={$i18n.get({
              id: 'main.pages.App.AssistantAppEdit.components.VarConfigDrawer.form.enterValue',
              dm: 'Please enter value',
            })}
          />
        );
    }
  };
  return (
    <div className={styles.configFormConMulti}>
      {params.map((paramItem) => (
        <div className={styles.paramItem} key={paramItem.field}>
          <Form.Item
            label={
              <Typography.Text
                ellipsis={{
                  tooltip: renderTooltip(paramItem.field),
                }}
                className={styles.labelMulti}
              >
                {paramItem.field}
              </Typography.Text>
            }
            name={['user_defined_params', props.item.code, paramItem.field]}
            layout="vertical"
          >
            {renderFormInput(paramItem.type)}
          </Form.Item>
        </div>
      ))}
    </div>
  );
}

export interface VarConfigFormProps {
  loading: boolean;
  bizVarList: BizVarItem[];
  userPromptParamsList: any[];
  bizVars: any;
  onBizVarsFormValuesChange: (changedValues: any, allValues: any) => void;
}
export default function AgentVarConfigForm(props: VarConfigFormProps) {
  const {
    loading,
    bizVarList,
    userPromptParamsList,
    bizVars,
    onBizVarsFormValuesChange,
  } = props;

  if (loading) return <Spin spinning className={styles.loading} />;
  if (!bizVarList.length && !userPromptParamsList.length)
    return (
      <Flex justify="center" className="mt-[20px]">
        <Empty
          description={$i18n.get({
            id: 'main.pages.App.AssistantAppEdit.components.VarConfigDrawer.form.noVariables',
            dm: 'No variables to fill in',
          })}
        />
      </Flex>
    );
  return (
    <Form
      onValuesChange={onBizVarsFormValuesChange}
      labelCol={{
        flex: '140px',
      }}
      initialValues={bizVars}
    >
      {!!userPromptParamsList.length && (
        <div className="mb-[24px]">
          <p className="text-[14px] leading-[24px] font-medium m-[20px_0_12px]">
            {$i18n.get({
              id: 'main.pages.App.AssistantAppEdit.components.VarConfigDrawer.form.customVariable',
              dm: 'Custom Variables',
            })}
          </p>

          <>
            {userPromptParamsList.map((item) => (
              <div className={styles.parameterWrapper} key={`${item.name}`}>
                <div className={styles.paramItem}>
                  <Form.Item
                    label={
                      <Flex vertical gap={4}>
                        <Typography.Text
                          className="text-[13px] leading-[20px] font-medium"
                          ellipsis={{
                            tooltip: renderTooltip(item.name),
                          }}
                        >
                          {item.name}
                        </Typography.Text>
                        <Typography.Text
                          className="text-[12px] leading-[20px]"
                          style={{ color: 'var(--ag-ant-color-text-tertiary)' }}
                          ellipsis={{
                            tooltip: renderTooltip(item.description),
                          }}
                        >
                          {item.description}
                        </Typography.Text>
                      </Flex>
                    }
                    name={['prompt_variables', item.name]}
                    layout="vertical"
                  >
                    <Input className={styles.input} />
                  </Form.Item>
                </div>
              </div>
            ))}
          </>
        </div>
      )}
      {!!bizVarList.length && (
        <div className="mb-[24px]">
          <p className="text-[14px] leading-[24px] font-medium m-[20px_0_12px]">
            {$i18n.get({
              id: 'main.pages.App.AssistantAppEdit.components.VarConfigDrawer.form.builtinVariable',
              dm: 'Built-in Variables',
            })}
          </p>

          <>
            {bizVarList.map((item) => (
              <div className={styles.parameterWrapper} key={`${item.code}`}>
                <div className={styles.title}>{item.name}</div>
                <ConfigFormItem item={item} />
              </div>
            ))}
          </>
        </div>
      )}
    </Form>
  );
}
