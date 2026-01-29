import InnerLayout from '@/components/InnerLayout';
import { useInnerLayout } from '@/components/InnerLayout/utils';
import ScrollMenu from '@/components/ScrollMenu';
import $i18n from '@/i18n';
import { getPlugin, getTool, publishTool, saveTool } from '@/services/plugin';
import { Button, Form, message } from '@spark-ai/design';
import { useRequest, useSetState } from 'ahooks';
import { Divider, Flex, Input, Select } from 'antd';
import classNames from 'classnames';
import { useRef, useState } from 'react';
import { history, useNavigate, useParams } from 'umi';
import ExampleConfigForm, {
  IExampleItem,
} from '../components/ExampleConfigForm';
import InputParamsConfig, {
  InputParamItem,
} from '../components/InputParamsConfig';
import OutputParamsConfig, {
  IOutputParamItem,
} from '../components/OutputParamsConfig';
import styles from './index.module.less';
import Test from './Test';
export default function () {
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const menuRef = useRef<HTMLDivElement[]>([]);

  const [state, setState] = useSetState({
    inputParams: [] as InputParamItem[],
    outputParams: [] as IOutputParamItem[],
    examples: [] as Array<IExampleItem>,
  });
  const [activeIndex, setActiveIndex] = useState(0);

  const { id = '', toolId } = useParams<{ id: string; toolId?: string }>();
  const { data } = useRequest(() => getPlugin(id));
  useRequest(() => getTool(id, toolId || ''), {
    onSuccess(data) {
      if (data?.data) {
        form.setFieldsValue(data.data);
        setState({
          // @ts-ignore
          inputParams: data.data.config?.input_params || [],
          // @ts-ignore
          outputParams: data.data.config?.output_params || [],
          examples: data.data.config?.examples || [],
        });
      }
    },
  });

  const { bottomPortal } = useInnerLayout();
  const requestMethod = Form.useWatch(['config', 'request_method'], form);

  const save = async function () {
    const values = await form.validateFields();
    const { inputParams, outputParams, examples } = state;

    const requestData = {
      tool_id: toolId,
      plugin_id: id,
      ...values,
      config: {
        ...values.config,
        input_params: inputParams,
        output_params: outputParams,
        examples: (examples || []).map((item) => {
          return {
            ...item,
            path: values.config.path,
          };
        }),
      },
    };

    return saveTool(requestData).then((res) => {
      if (!toolId) {
        history.replace(`/component/plugin/${id}/tool/${res}`);
      }
      return res;
    });
  };

  const handleMenuClick = (index: number) => {
    setActiveIndex(index);

    menuRef.current[index]?.scrollIntoView({
      behavior: 'smooth',
      block: 'start',
    });
  };

  const saveButton = (
    <Button
      onClick={async () => {
        save().then(() => {
          message.success(
            $i18n.get({
              id: 'main.pages.Component.Plugin.Tools.Edit.successSave',
              dm: 'Saved successfully',
            }),
          );
        });
      }}
    >
      {$i18n.get({
        id: 'main.pages.Component.Plugin.Tools.Edit.save',
        dm: 'Save',
      })}
    </Button>
  );

  const publishButton = (
    <Button
      type="primary"
      onClick={async () => {
        const toolId = (await save()) as string;
        publishTool(id, toolId).then(() => {
          message.success(
            $i18n.get({
              id: 'main.pages.Component.Plugin.Tools.Edit.successPublish',
              dm: 'Published successfully',
            }),
          );
        });
      }}
    >
      {$i18n.get({
        id: 'main.pages.Component.Plugin.Tools.Edit.saveAndPublish',
        dm: 'Save and Publish',
      })}
    </Button>
  );

  const cancelButton = (
    <Button
      onClick={() => {
        navigate('/component/plugin');
      }}
    >
      {$i18n.get({
        id: 'main.pages.Component.Plugin.Tools.Edit.cancel',
        dm: 'Cancel',
      })}
    </Button>
  );

  return (
    <InnerLayout
      breadcrumbLinks={[
        {
          title: $i18n.get({
            id: 'main.pages.Component.Plugin.Tools.Edit.componentManagement',
            dm: 'Component Management',
          }),
        },
        {
          title: data?.data.name,
          path: `/component/plugin/${id}/tools`,
        },
        {
          title: $i18n.get({
            id: 'main.pages.Component.Plugin.Tools.Edit.editTool',
            dm: 'Edit Tool',
          }),
        },
      ]}
      simplifyBreadcrumb
    >
      <div className={styles.form}>
        <div className={styles['form-content']}>
          <Form
            id="content-scroll"
            form={form}
            labelCol={{ span: 14 }}
            wrapperCol={{ span: 14 }}
            layout="vertical"
            colon={false}
          >
            <Flex vertical>
              <div
                ref={(el: HTMLDivElement) => {
                  menuRef.current[0] = el;
                }}
                className={styles['form-area']}
              >
                <div className={styles.title}>
                  {$i18n.get({
                    id: 'main.pages.Component.Plugin.Tools.Edit.toolInfo',
                    dm: 'Tool Info',
                  })}
                </div>
                <Form.Item
                  name="name"
                  rules={[
                    {
                      required: true,
                      message: $i18n.get({
                        id: 'main.pages.Component.Plugin.Tools.Edit.enterToolName',
                        dm: 'Please enter tool name',
                      }),
                    },
                  ]}
                  label={$i18n.get({
                    id: 'main.pages.Component.Plugin.Tools.Edit.toolName',
                    dm: 'Tool Name',
                  })}
                >
                  <Input
                    placeholder={$i18n.get({
                      id: 'main.pages.Component.Plugin.Tools.Edit.enterToolName',
                      dm: 'Please enter tool name',
                    })}
                    showCount
                    maxLength={128}
                  />
                </Form.Item>

                <Form.Item
                  name="description"
                  rules={[
                    {
                      required: true,
                      message: $i18n.get({
                        id: 'main.pages.Component.Plugin.Tools.Edit.enterToolDescription',
                        dm: 'Please enter tool description to help users understand its functionality and use cases',
                      }),
                    },
                  ]}
                  label={$i18n.get({
                    id: 'main.pages.Component.Plugin.Tools.Edit.toolDescription',
                    dm: 'Tool Description',
                  })}
                >
                  <Input.TextArea
                    autoSize={{
                      minRows: 4,
                      maxRows: 4,
                    }}
                    showCount
                    maxLength={200}
                    placeholder={$i18n.get({
                      id: 'main.pages.Component.Plugin.Tools.Edit.enterToolDescription',
                      dm: 'Please enter tool description to help users understand its functionality and use cases',
                    })}
                  />
                </Form.Item>

                <Form.Item
                  name={['config', 'path']}
                  rules={[
                    {
                      required: true,
                      message: $i18n.get({
                        id: 'main.pages.Component.Plugin.Tools.Edit.enterToolPath',
                        dm: 'Please enter tool path',
                      }),
                    },
                  ]}
                  label={$i18n.get({
                    id: 'main.pages.Component.Plugin.Tools.Edit.toolPath',
                    dm: 'Tool Path',
                  })}
                >
                  <Input addonBefore={data?.data.config?.server} />
                </Form.Item>

                <Form.Item
                  name={['config', 'request_method']}
                  rules={[
                    {
                      required: true,
                      message: $i18n.get({
                        id: 'main.pages.Component.Plugin.Tools.Edit.selectRequestMethod',
                        dm: 'Please select request method',
                      }),
                    },
                  ]}
                  label={$i18n.get({
                    id: 'main.pages.Component.Plugin.Tools.Edit.requestMethod',
                    dm: 'Request Method',
                  })}
                >
                  <Select
                    options={['GET', 'POST'].map((item) => ({
                      value: item,
                      label: item,
                    }))}
                  />
                </Form.Item>

                {requestMethod === 'POST' && (
                  <Form.Item
                    name={['config', 'content_type']}
                    rules={[
                      {
                        required: true,
                        message: $i18n.get({
                          id: 'main.pages.Component.Plugin.Tools.Edit.selectSubmitMethod',
                          dm: 'Please select submit method',
                        }),
                      },
                    ]}
                    label={$i18n.get({
                      id: 'main.pages.Component.Plugin.Tools.Edit.submitMethod',
                      dm: 'Submit Method',
                    })}
                    style={{ marginBottom: 0 }}
                  >
                    <Select
                      options={[
                        'application/json',
                        'application/x-www-form-urlencoded',
                      ].map((item) => ({ value: item, label: item }))}
                    />
                  </Form.Item>
                )}
              </div>
              <Divider />
              <div
                ref={(el: HTMLDivElement) => {
                  menuRef.current[1] = el;
                }}
                className={styles['form-area']}
              >
                <div className={classNames(styles.title, styles.required)}>
                  {$i18n.get({
                    id: 'main.pages.Component.Plugin.Tools.Edit.configureInputParameters',
                    dm: 'Configure Input Parameters',
                  })}
                </div>
                <InputParamsConfig
                  requestMethod={requestMethod}
                  params={state.inputParams}
                  onChange={(val) => {
                    setState({ inputParams: val });
                  }}
                />
              </div>
              <Divider />
              <div
                ref={(el: HTMLDivElement) => {
                  menuRef.current[2] = el;
                }}
                className={styles['form-area']}
              >
                <div className={classNames(styles.title, styles.required)}>
                  {$i18n.get({
                    id: 'main.pages.Component.Plugin.Tools.Edit.configureOutputParameters',
                    dm: 'Configure Output Parameters',
                  })}
                </div>
                <OutputParamsConfig
                  params={state.outputParams}
                  onChange={(val) => {
                    setState({ outputParams: val });
                  }}
                />
              </div>
              <Divider />
              <div
                ref={(el: HTMLDivElement) => {
                  menuRef.current[3] = el;
                }}
                className={styles['form-area']}
              >
                <Flex justify="space-between" align="center">
                  <span className={styles.title}>
                    {$i18n.get({
                      id: 'main.pages.Component.Plugin.Tools.Edit.advancedConfiguration',
                      dm: 'Advanced Configuration',
                    })}
                  </span>
                </Flex>
                <div className={styles.tip}>
                  {$i18n.get({
                    id: 'main.pages.Component.Plugin.Tools.Edit.addCallExamples',
                    dm: '(Optional) Add call examples for the model to improve plugin invocation accuracy',
                  })}
                </div>
                <ExampleConfigForm
                  inputParams={state.inputParams}
                  onChange={(val) => setState({ examples: val })}
                  examples={state.examples}
                />
              </div>
              {bottomPortal(
                <Flex className={styles['bottom-bar']} align="center" gap={12}>
                  {publishButton}
                  {saveButton}
                  {cancelButton}
                  <div className={styles.divider} />
                  <Test
                    pluginId={id}
                    toolId={toolId as string}
                    inputParams={state.inputParams}
                  />
                </Flex>,
              )}
            </Flex>
          </Form>
          <div className={styles['scroll-control']}>
            <ScrollMenu
              activeMenuCode={activeIndex}
              handleMenuClick={handleMenuClick}
              menus={[
                $i18n.get({
                  id: 'main.pages.Component.Plugin.Tools.Edit.toolInfo',
                  dm: 'Tool Info',
                }),
                $i18n.get({
                  id: 'main.pages.Component.Plugin.Tools.Edit.inputParameters',
                  dm: 'Input Parameters',
                }),
                $i18n.get({
                  id: 'main.pages.Component.Plugin.Tools.Edit.outputParameters',
                  dm: 'Output Parameters',
                }),
                $i18n.get({
                  id: 'main.pages.Component.Plugin.Tools.Edit.advancedConfiguration',
                  dm: 'Advanced Configuration',
                }),
              ]}
            />
          </div>
        </div>
      </div>
    </InnerLayout>
  );
}
