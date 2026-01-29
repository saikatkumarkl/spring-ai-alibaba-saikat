import TipBox from '@/components/TipBox';
import $i18n from '@/i18n';
import { API_KEY_TIP_SECTIONS } from '@/pages/Setting/utils';
import { updateProvider } from '@/services/modelService';
import type { IProviderConfigInfo } from '@/types/modelService';
import { AlertDialog, Button, Form, Input, message } from '@spark-ai/design';
import React, { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { ProviderAvatar } from '../ProviderAvatar';
import styles from './index.module.less';
interface ProviderInfoFormProps {
  provider: IProviderConfigInfo | null;
  providerId: string;
  onRefresh?: () => void;
}

interface ProviderNameProps {
  provider: IProviderConfigInfo | null;
  onEnableService: (enable: boolean) => void;
}

const ProviderName: React.FC<ProviderNameProps> = ({
  provider,
  onEnableService,
}) => {
  return (
    <div className={styles.label}>
      <div className={styles.labelContent}>
        {$i18n.get({
          id: 'main.pages.Setting.ModelService.components.ProviderInfoForm.index.modelServiceProviderName',
          dm: 'Model Service Provider Name',
        })}

        <div className={styles.required}>*</div>
      </div>
      <div className={styles.actions}>
        <div
          className={styles['status-tag']}
          data-status={provider?.enable ? 'enabled' : 'disabled'}
        >
          <span className={styles.dot}></span>
          <span>
            {provider?.enable
              ? $i18n.get({
                  id: 'main.pages.Setting.ModelService.components.ProviderInfoForm.index.started',
                  dm: 'Started',
                })
              : $i18n.get({
                  id: 'main.pages.Setting.ModelService.components.ProviderInfoForm.index.stopped',
                  dm: 'Stopped',
                })}
          </span>
        </div>
        <Button onClick={() => onEnableService(!provider?.enable)}>
          {provider?.enable
            ? $i18n.get({
                id: 'main.pages.Setting.ModelService.components.ProviderInfoForm.index.stopService',
                dm: 'Stop Service',
              })
            : $i18n.get({
                id: 'main.pages.Setting.ModelService.components.ProviderInfoForm.index.startService',
                dm: 'Start Service',
              })}
        </Button>
      </div>
    </div>
  );
};

const ProviderInfoForm: React.FC<ProviderInfoFormProps> = ({
  provider,
  providerId,
  onRefresh,
}) => {
  const [form] = Form.useForm();
  const navigate = useNavigate();
  const isPreset = provider?.source === 'preset';

  useEffect(() => {
    initForm();
  }, [provider, form]);

  const initForm = () => {
    if (!provider) return;
    form.setFieldsValue({
      name: provider?.name,
      apiKey: provider?.credential?.api_key || '',
      endpoint: provider?.credential?.endpoint || '',
    });
  };

  const handleFormSubmit = () => {
    form.validateFields().then((values) => {
      if (!providerId || !provider) return;

      updateProvider(providerId, {
        ...provider,
        name: values.name,
        credential_config: {
          api_key: values.apiKey,
          endpoint: values.endpoint,
        },
      }).then((response) => {
        if (response) {
          message.success(
            $i18n.get({
              id: 'main.pages.Setting.ModelService.components.ProviderInfoForm.index.configurationUpdated',
              dm: 'Configuration updated',
            }),
          );
          onRefresh?.();
        }
      });
    });
  };

  const enableService = async (enable: boolean) => {
    if (!provider) return;

    await form.validateFields();

    AlertDialog.warning({
      title: enable
        ? $i18n.get({
            id: 'main.pages.Setting.ModelService.components.ProviderInfoForm.index.startService',
            dm: 'Start Service',
          })
        : $i18n.get({
            id: 'main.pages.Setting.ModelService.components.ProviderInfoForm.index.stopService',
            dm: 'Stop Service',
          }),
      children: enable
        ? $i18n.get({
            id: 'main.pages.Setting.ModelService.components.ProviderInfoForm.index.confirmStartService',
            dm: 'Are you sure you want to start this service?',
          })
        : $i18n.get({
            id: 'main.pages.Setting.ModelService.components.ProviderInfoForm.index.confirmStopService',
            dm: 'Are you sure you want to stop this service? You will not be able to use models from this provider after stopping.',
          }),

      onOk: () => {
        updateProvider(providerId, {
          ...provider,
          enable,
          credential_config: provider.credential,
        }).then((response) => {
          if (response) {
            message.success(
              $i18n.get({
                id: 'main.pages.Setting.ModelService.components.ProviderInfoForm.index.stopServiceSuccess',
                dm: 'Service stopped successfully',
              }),
            );
            onRefresh?.();
          }
        });
      },
    });
  };

  return (
    <div className={styles.wrapper}>
      <div className={styles.content}>
        <Form
          form={form}
          layout="vertical"
          onFinish={handleFormSubmit}
          className={styles.form}
        >
          <div className={styles['form-row']}>
            <ProviderAvatar
              className={styles['provider-avatar']}
              provider={provider}
            />

            <Form.Item
              name="name"
              label={
                <ProviderName
                  provider={provider}
                  onEnableService={enableService}
                />
              }
              rules={[
                {
                  required: true,
                  message: $i18n.get({
                    id: 'main.pages.Setting.ModelService.components.ProviderInfoForm.index.enterModelServiceProviderName',
                    dm: 'Please enter model service provider name',
                  }),
                },
              ]}
              required={false}
              className={styles['form-item']}
            >
              <Input
                placeholder={$i18n.get({
                  id: 'main.pages.Setting.ModelService.components.ProviderInfoForm.index.inputModelServiceProviderName',
                  dm: 'Enter model service provider name',
                })}
                maxLength={30}
                disabled={isPreset}
              />
            </Form.Item>
          </div>
          <Form.Item
            name="apiKey"
            label="API-KEY (Optional)"
            tooltip="Leave empty for local services like Ollama"
          >
            <Input.Password
              placeholder={$i18n.get({
                id: 'main.pages.Setting.ModelService.components.ProviderInfoForm.index.enterApiKey',
                dm: 'Enter your API-KEY',
              })}
            />
          </Form.Item>

          <Form.Item
            name="endpoint"
            label="API URL"
            rules={[
              {
                required: true,
                message: $i18n.get({
                  id: 'main.pages.Setting.ModelService.components.ProviderInfoForm.index.apiUrlRequired',
                  dm: 'API URL is required to call remote model services',
                }),
              },
            ]}
            required={true}
          >
            <Input.TextArea
              rows={2}
              placeholder={$i18n.get({
                id: 'main.pages.Setting.ModelService.components.ProviderInfoForm.index.enterApiUrl',
                dm: 'Enter your API URL from provider docs, e.g., https://dashscope.aliyuncs.com/compatible-mode',
              })}
            />
          </Form.Item>
          <Form.Item>
            <TipBox
              title={$i18n.get({
                id: 'main.pages.Setting.ModelService.components.ProviderInfoForm.index.howToGetModelServiceApi',
                dm: 'How to get Model Service API?',
              })}
              sections={API_KEY_TIP_SECTIONS}
            />
          </Form.Item>
          <Form.Item>
            <div className={styles.actions}>
              <Button type="primary" htmlType="submit">
                {$i18n.get({
                  id: 'main.pages.Setting.ModelService.components.ProviderInfoForm.index.save',
                  dm: 'Save',
                })}
              </Button>
              <Button
                htmlType="button"
                onClick={() => navigate('/setting/modelService')}
              >
                {$i18n.get({
                  id: 'main.pages.Setting.ModelService.components.ProviderInfoForm.index.cancel',
                  dm: 'Cancel',
                })}
              </Button>
            </div>
          </Form.Item>
        </Form>
      </div>
    </div>
  );
};

export default ProviderInfoForm;
