import $i18n from '@/i18n';
import { getOllamaModels } from '@/services/modelService';
import { ICreateModelParams, IModel, IProviderConfigInfo, MODEL_TAGS } from '@/types/modelService';
import { Button, Checkbox, Form, Input, Modal, Select, message } from '@spark-ai/design';
import React, { useEffect, useState } from 'react';
import styles from './index.module.less';

interface ModelConfigModalProps {
  open: boolean;
  onCancel: () => void;
  onOk: (modelInfo: ICreateModelParams) => void;
  model?: IModel;
  title?: string;
  provider?: IProviderConfigInfo;
}

const ModelConfigModal: React.FC<ModelConfigModalProps> = ({
  open,
  onCancel,
  onOk,
  model,
  title,
  provider,
}) => {
  const [form] = Form.useForm();
  const isEdit = !!model?.model_id;
  const [availableModels, setAvailableModels] = useState<string[]>([]);
  const [loadingModels, setLoadingModels] = useState(false);
  const [useDropdown, setUseDropdown] = useState(false);

  useEffect(() => {
    if (open) {
      if (model) {
        form.setFieldsValue({
          name: model.name || '',
          tags: model.tags || [],
        });
      } else {
        form.resetFields();
        setAvailableModels([]);
        setUseDropdown(false);
        // Auto-load models for Ollama providers when adding new model
        if (!isEdit && provider?.credential?.endpoint && provider?.protocol === 'openai') {
          fetchAvailableModels();
        }
      }
    }
  }, [open, model, form, provider]);

  const fetchAvailableModels = async () => {
    if (!provider?.credential?.endpoint) {
      message.warning(
        $i18n.get({
          id: 'main.pages.Setting.ModelService.components.ModelConfigModal.index.noEndpoint',
          dm: 'No endpoint configured for this provider',
        }),
      );
      return;
    }

    setLoadingModels(true);
    try {
      const response = await getOllamaModels(provider.credential.endpoint);
      if (response?.data && response.data.length > 0) {
        setAvailableModels(response.data);
        setUseDropdown(true);
        message.success(
          $i18n.get({
            id: 'main.pages.Setting.ModelService.components.ModelConfigModal.index.modelsLoaded',
            dm: `Found ${response.data.length} models`,
          }),
        );
      } else {
        message.info(
          $i18n.get({
            id: 'main.pages.Setting.ModelService.components.ModelConfigModal.index.noModels',
            dm: 'No models found',
          }),
        );
      }
    } catch (error) {
      message.error(
        $i18n.get({
          id: 'main.pages.Setting.ModelService.components.ModelConfigModal.index.fetchFailed',
          dm: 'Failed to fetch models. Check endpoint URL.',
        }),
      );
    } finally {
      setLoadingModels(false);
    }
  };

  const handleSubmit = () => {
    form.validateFields().then((values) => {
      const _model: ICreateModelParams = {
        ...(model || {}),
        name: values.name,
        model_id: values.name,
        tags: values.tags,
      };
      onOk(_model);
    });
  };

  return (
    <Modal
      title={
        title ||
        (isEdit
          ? $i18n.get({
              id: 'main.pages.Setting.ModelService.components.ModelConfigModal.index.editModel',
              dm: 'Edit Model',
            })
          : $i18n.get({
              id: 'main.pages.Setting.ModelService.components.ModelConfigModal.index.addModel',
              dm: 'Add Model',
            }))
      }
      open={open}
      onCancel={onCancel}
      footer={[
        <Button key="cancel" onClick={onCancel}>
          {$i18n.get({
            id: 'main.pages.Setting.ModelService.components.ModelConfigModal.index.cancel',
            dm: 'Cancel',
          })}
        </Button>,
        <Button key="submit" type="primary" onClick={handleSubmit}>
          {$i18n.get({
            id: 'main.pages.Setting.ModelService.components.ModelConfigModal.index.confirm',
            dm: 'Confirm',
          })}
        </Button>,
      ]}
      width={480}
      destroyOnClose={true}
    >
      <div className={styles.container}>
        <Form form={form} layout="vertical" requiredMark={false}>
          <Form.Item
            name="name"
            label={
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%' }}>
                <span>
                  {$i18n.get({
                    id: 'main.pages.Setting.ModelService.components.ModelConfigModal.index.modelName',
                    dm: 'Model Name',
                  })}
                </span>
                {!isEdit && provider?.credential?.endpoint && useDropdown && availableModels.length > 0 && (
                  <Button
                    type="link"
                    size="small"
                    onClick={() => {
                      setUseDropdown(false);
                      setAvailableModels([]);
                    }}
                    style={{ padding: 0, height: 'auto' }}
                  >
                    Use manual input
                  </Button>
                )}
              </div>
            }
            rules={[
              {
                required: true,
                message: $i18n.get({
                  id: 'main.pages.Setting.ModelService.components.ModelConfigModal.index.enterModelName',
                  dm: 'Please enter model name',
                }),
              },
            ]}
          >
            {loadingModels ? (
              <Select
                placeholder={$i18n.get({
                  id: 'main.pages.Setting.ModelService.components.ModelConfigModal.index.loadingModels',
                  dm: 'Loading available models...',
                })}
                loading={true}
                disabled={true}
              />
            ) : useDropdown && availableModels.length > 0 ? (
              <Select
                placeholder={$i18n.get({
                  id: 'main.pages.Setting.ModelService.components.ModelConfigModal.index.selectModel',
                  dm: 'Select a model',
                })}
                showSearch
                options={availableModels.map(modelName => ({
                  label: modelName,
                  value: modelName,
                }))}
              />
            ) : (
              <Input
                placeholder={$i18n.get({
                  id: 'main.pages.Setting.ModelService.components.ModelConfigModal.index.enterModelName',
                  dm: 'Enter model name (e.g., llama3.2, gpt-oss:120b-cloud)',
                })}
                maxLength={50}
                showCount
              />
            )}
          </Form.Item>
          <Form.Item
            name="tags"
            label={$i18n.get({
              id: 'main.pages.Setting.ModelService.components.ModelConfigModal.index.modelAbility',
              dm: 'Model Capabilities',
            })}
            rules={[
              {
                required: true,
                message: $i18n.get({
                  id: 'main.pages.Setting.ModelService.components.ModelConfigModal.index.selectAtLeastOneAbility',
                  dm: 'Please select at least one model capability',
                }),
              },
            ]}
          >
            <Checkbox.Group>
              <div className={styles['capability-options']}>
                {Object.entries(MODEL_TAGS).map(([key, value]) => (
                  <Checkbox key={key} value={key}>
                    {value}
                  </Checkbox>
                ))}
              </div>
            </Checkbox.Group>
          </Form.Item>
        </Form>
      </div>
    </Modal>
  );
};

export default ModelConfigModal;
