import $i18n from '@/i18n';
import { Form, Input, Modal, Select } from '@spark-ai/design';
import { VALUE_TYPE_OPTIONS } from '@spark-ai/flow';
import { Switch } from 'antd';
import { IParameterExtractorNodeParam } from '../../types';
import styles from './index.module.less';

interface ExtractParamEditModalProps {
  onCancel: () => void;
  onOk: (
    values: IParameterExtractorNodeParam['extract_params'][number],
  ) => void;
  initialValues?: IParameterExtractorNodeParam['extract_params'][number];
  extractParams: IParameterExtractorNodeParam['extract_params'];
}

export default function ExtractParamEditModal({
  onCancel,
  onOk,
  initialValues,
  extractParams,
}: ExtractParamEditModalProps) {
  const [form] =
    Form.useForm<IParameterExtractorNodeParam['extract_params'][number]>();

  const handleOk = async () => {
    try {
      const values = await form.validateFields();
      onOk(values);
    } catch (error) {
      console.error('Validate Failed:', error);
    }
  };

  return (
    <Modal
      title={
        initialValues
          ? $i18n.get({
              id: 'main.pages.App.Workflow.components.ExtractParamEditModal.index.editParam',
              dm: 'Edit Parameter',
            })
          : $i18n.get({
              id: 'main.pages.App.Workflow.components.ExtractParamEditModal.index.addParam',
              dm: 'Add Parameter',
            })
      }
      open
      onCancel={onCancel}
      onOk={handleOk}
      destroyOnClose
      width={640}
      maskClosable={false}
      className={styles['extract-param-edit-modal']}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={
          initialValues || {
            key: '',
            type: 'String',
            desc: '',
            required: false,
          }
        }
        preserve={false}
      >
        <Form.Item
          label={$i18n.get({
            id: 'main.pages.App.Workflow.components.ExtractParamEditModal.index.name',
            dm: 'Name',
          })}
          name="key"
          rules={[
            {
              required: true,
              message: $i18n.get({
                id: 'main.pages.App.Workflow.components.ExtractParamEditModal.index.enterParamName',
                dm: 'Please enter parameter name',
              }),
            },
            {
              pattern: /^[a-zA-Z_$][a-zA-Z0-9_$]*$/,
              message: $i18n.get({
                id: 'main.pages.App.Workflow.components.ExtractParamEditModal.index.nameFormat',
                dm: 'Can only contain letters, numbers, underscores, and $, and cannot start with a number',
              }),
            },
            {
              validator: (_, value) => {
                if (!value) return Promise.resolve();

                // check whether the key is duplicated
                const hasDuplicate = extractParams.some(
                  (param) =>
                    param.key === value &&
                    // if it is in edit mode, exclude itself
                    (initialValues === undefined ||
                      initialValues.key !== value),
                );

                return hasDuplicate
                  ? Promise.reject(
                      new Error(
                        $i18n.get({
                          id: 'main.pages.App.Workflow.components.ExtractParamEditModal.index.paramNameExists',
                          dm: 'Parameter name already exists',
                        }),
                      ),
                    )
                  : Promise.resolve();
              },
            },
          ]}
        >
          <Input
            placeholder={$i18n.get({
              id: 'main.pages.App.Workflow.components.ExtractParamEditModal.index.enter',
              dm: 'Please enter',
            })}
          />
        </Form.Item>

        <Form.Item
          label={$i18n.get({
            id: 'main.pages.App.Workflow.components.ExtractParamEditModal.index.type',
            dm: 'Type',
          })}
          name="type"
          rules={[
            {
              required: true,
              message: $i18n.get({
                id: 'main.pages.App.Workflow.components.ExtractParamEditModal.index.index.selectParameterType',
                dm: 'Please select parameter type',
              }),
            },
          ]}
        >
          <Select
            placeholder={$i18n.get({
              id: 'main.pages.App.Workflow.components.ExtractParamEditModal.index.selectType',
              dm: 'Select type',
            })}
            options={VALUE_TYPE_OPTIONS.map((option) => ({
              label: option.label,
              value: option.value,
              disabled: option.disabled,
            }))}
          />
        </Form.Item>

        <Form.Item
          label={$i18n.get({
            id: 'main.pages.App.Workflow.components.ExtractParamEditModal.index.description',
            dm: 'Description',
          })}
          name="desc"
          rules={[
            {
              required: true,
              message: $i18n.get({
                id: 'main.pages.App.Workflow.components.ExtractParamEditModal.index.enterDescription',
                dm: 'Please enter parameter description',
              }),
            },
          ]}
        >
          <Input.TextArea
            placeholder={$i18n.get({
              id: 'main.pages.App.Workflow.components.ExtractParamEditModal.index.paramDescription',
              dm: 'Parameter description',
            })}
            style={{ height: '100px', resize: 'none' }}
            showCount
            maxLength={1000}
          />
        </Form.Item>

        <Form.Item
          layout="horizontal"
          colon={false}
          className={styles['spark-flow-horizontal-form-item']}
          label={
            <div className="flex items-center gap-[8px]">
              <span>
                {$i18n.get({
                  id: 'main.pages.App.Workflow.components.ExtractParamEditModal.index.required',
                  dm: 'Required',
                })}
              </span>
              <span className="text-desc">
                {$i18n.get({
                  id: 'main.pages.App.Workflow.components.ExtractParamEditModal.index.requiredReferenceOnly',
                  dm: 'Required is only used as a reference for model inference, not for mandatory validation of parameter output.',
                })}
              </span>
            </div>
          }
          name="required"
        >
          <Switch />
        </Form.Item>
      </Form>
    </Modal>
  );
}
