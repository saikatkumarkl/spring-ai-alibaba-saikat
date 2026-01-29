import $i18n from '@/i18n';
import { IconButton, IconFont } from '@spark-ai/design';
import { Flex, Form, Input, Space } from 'antd';
import styles from './index.module.less';

export default function HeadersEditForm() {
  return (
    <Flex align="flex-start" vertical gap={8} className={styles.form}>
      <Flex gap={8} className={styles.label}>
        <div style={{ width: 300 }}>Key</div>
        <div style={{ width: 300 }}>Value</div>
      </Flex>
      <Form.List initialValue={[]} name={['config', 'headers']}>
        {(fields, { add, remove }) => (
          <>
            {fields.map(({ key, name, ...restField }) => {
              return (
                <Space key={key} style={{ display: 'flex' }} align="start">
                  <Form.Item
                    {...restField}
                    name={[name, 'name']}
                    validateFirst
                    rules={[
                      {
                        required: true,
                        message: $i18n.get({
                          id: 'main.pages.Component.Plugin.components.HeadersEditForm.index.parameterNameCannotBeEmpty',
                          dm: 'Parameter name cannot be empty',
                        }),
                      },
                      {
                        message: $i18n.get({
                          id: 'main.pages.Component.Plugin.components.HeadersEditForm.index.onlyLettersNumbersOrUnderscoresAndStartWithLetterOrUnderscore',
                          dm: 'Can only contain letters, numbers, or underscores, and must start with a letter or underscore',
                        }),

                        pattern: /^[a-zA-Z_][a-zA-Z0-9_-]*$/,
                      },
                    ]}
                  >
                    <Input
                      style={{ width: 300 }}
                      placeholder={$i18n.get({
                        id: 'main.pages.Component.Plugin.components.HeadersEditForm.index.enterParameterName',
                        dm: 'Enter parameter name',
                      })}
                    />
                  </Form.Item>
                  <Form.Item
                    {...restField}
                    name={[name, 'value']}
                    rules={[
                      {
                        required: true,
                        message: $i18n.get({
                          id: 'main.pages.Component.Plugin.components.HeadersEditForm.index.parameterNameCannotBeEmpty',
                          dm: 'Parameter name cannot be empty',
                        }),
                      },
                    ]}
                  >
                    <Input
                      placeholder={$i18n.get({
                        id: 'main.pages.Component.Plugin.components.HeadersEditForm.index.enterValue',
                        dm: 'Please enter Value',
                      })}
                      style={{ width: 300 }}
                    />
                  </Form.Item>
                  <IconButton
                    bordered={false}
                    onClick={() => remove(name)}
                    icon="spark-delete-line"
                  />
                </Space>
              );
            })}

            <a className="flex align-center gap-1" onClick={() => add()}>
              <IconFont type="spark-plus-line" />
              {$i18n.get({
                id: 'main.pages.Component.Plugin.components.HeadersEditForm.index.addInputParameter',
                dm: 'Add Input Parameter',
              })}
            </a>
          </>
        )}
      </Form.List>
    </Flex>
  );
}
