import TipBox from '@/components/TipBox';
import $i18n from '@/i18n';
import { API_KEY_TIP_SECTIONS } from '@/pages/Setting/utils';
import { createProvider, getProviderProtocols } from '@/services/modelService';
import type { ICreateProviderParams } from '@/types/modelService';
import { Button, Form, Input, Modal, Radio, message } from '@spark-ai/design';
import { Space } from 'antd';
import React, { useEffect, useState } from 'react';
import styles from './index.module.less';

interface ModelServiceProviderModalProps {
  open: boolean;
  onCancel: () => void;
  onSuccess: () => void;
}

const ModelServiceProviderModal: React.FC<ModelServiceProviderModalProps> = ({
  open,
  onCancel,
  onSuccess,
}) => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [protocols, setProtocols] = useState<string[]>([]);

  useEffect(() => {
    getProviderProtocols().then((res) => {
      setProtocols(res.data);
      form.setFieldsValue({
        protocol: res.data[0],
      });
    });
  }, []);

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);

      const params: ICreateProviderParams = {
        name: values.name,
        protocol: values.protocol,
        credential_config: {
          api_key: values.api_key,
          endpoint: values.endpoint,
        },
      };

      const res = await createProvider(params);

      if (res) {
        message.success(
          $i18n.get({
            id: 'main.pages.Setting.ModelService.components.ModelServiceProviderModal.index.createSuccess',
            dm: 'Model service provider created successfully',
          }),
        );
        form.resetFields();
        onSuccess();
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      title={$i18n.get({
        id: 'main.pages.Setting.ModelService.components.ModelServiceProviderModal.index.addServiceProvider',
        dm: 'Add Model Service Provider',
      })}
      open={open}
      onCancel={() => {
        form.resetFields();
        onCancel();
      }}
      footer={
        <div className={styles['form-footer']}>
          <Button
            onClick={() => {
              form.resetFields();
              onCancel();
            }}
          >
            {$i18n.get({
              id: 'main.pages.Setting.ModelService.components.ModelServiceProviderModal.index.cancel',
              dm: 'Cancel',
            })}
          </Button>
          <Button type="primary" loading={loading} onClick={handleSubmit}>
            {$i18n.get({
              id: 'main.pages.Setting.ModelService.components.ModelServiceProviderModal.index.confirm',
              dm: 'Confirm',
            })}
          </Button>
        </div>
      }
      width={640}
      destroyOnClose
    >
      <Form
        form={form}
        layout="vertical"
        requiredMark={false}
        className={styles['provider-modal']}
      >
        <Form.Item
          name="name"
          label={$i18n.get({
            id: 'main.pages.Setting.ModelService.components.ModelServiceProviderModal.index.serviceProviderName',
            dm: 'Service Provider Name',
          })}
          rules={[
            {
              required: true,
              message: $i18n.get({
                id: 'main.pages.Setting.ModelService.components.ModelServiceProviderModal.index.enterServiceProviderName',
                dm: 'Please enter service provider name',
              }),
            },
          ]}
        >
          <Input
            placeholder={$i18n.get({
              id: 'main.pages.Setting.ModelService.components.ModelServiceProviderModal.index.enterYourServiceProviderName',
              dm: 'Enter your service provider name',
            })}
            maxLength={15}
            showCount
          />
        </Form.Item>

        <Form.Item
          name="api_key"
          label="API-KEY"
          rules={[
            {
              required: true,
              message: $i18n.get({
                id: 'main.pages.Setting.ModelService.components.ModelServiceProviderModal.index.enterApiKey',
                dm: 'Please enter API-KEY',
              }),
            },
          ]}
          required
        >
          <Input.Password
            placeholder={$i18n.get({
              id: 'main.pages.Setting.ModelService.components.ModelServiceProviderModal.index.enterYourApiKey',
              dm: 'Enter your API-KEY',
            })}
            maxLength={100}
          />
        </Form.Item>

        <Form.Item
          name="endpoint"
          label="API URL"
          rules={[
            {
              required: true,
              message: $i18n.get({
                id: 'main.pages.Setting.ModelService.components.ModelServiceProviderModal.index.enterApiUrl',
                dm: 'Please enter API URL',
              }),
            },
            {
              type: 'url',
              message: $i18n.get({
                id: 'main.pages.Setting.ModelService.components.ModelServiceProviderModal.index.enterValidUrl',
                dm: 'Please enter a valid URL',
              }),
            },
          ]}
        >
          <Input.TextArea
            rows={2}
            placeholder={$i18n.get({
              id: 'main.pages.Setting.ModelService.components.ModelServiceProviderModal.index.enterYourApiUrl',
              dm: 'Enter your API URL from provider docs, e.g., https://dashscope.aliyuncs.com/compatible-mode',
            })}
          />
        </Form.Item>

        <Form.Item
          name="protocol"
          label={$i18n.get({
            id: 'main.pages.Setting.ModelService.components.ModelServiceProviderModal.index.serviceProviderType',
            dm: 'Service Provider Type',
          })}
          rules={[
            {
              required: true,
              message: $i18n.get({
                id: 'main.pages.Setting.ModelService.components.ModelServiceProviderModal.index.selectServiceProviderType',
                dm: 'Please select service provider type',
              }),
            },
          ]}
        >
          <Radio.Group className={styles['protocol-type-radio']}>
            <Space>
              {protocols.map((protocol) => (
                <Radio key={protocol} value={protocol}>
                  {protocol}
                </Radio>
              ))}
            </Space>
            <TipBox
              title={$i18n.get({
                id: 'main.pages.Setting.ModelService.components.ModelServiceProviderModal.index.howToGetModelServiceApi',
                dm: 'How to get Model Service API?',
              })}
              sections={API_KEY_TIP_SECTIONS}
            />
          </Radio.Group>
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default ModelServiceProviderModal;
