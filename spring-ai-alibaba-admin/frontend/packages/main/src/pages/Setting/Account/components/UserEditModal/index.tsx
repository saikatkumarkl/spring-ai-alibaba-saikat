import $i18n from '@/i18n';
import { createAccount, updateAccount } from '@/services/account';
import { Button, Form, Input, message, Modal } from '@spark-ai/design';
import React, { useEffect, useState } from 'react';
import styles from './index.module.less';

export interface UserEditData {
  key?: string;
  name: string;
}

interface UserFormValues extends UserEditData {
  newPassword?: string;
  confirmPassword?: string;
}

interface UserEditModalProps {
  open: boolean;
  onCancel: () => void;
  onOk: () => void;
  initialValues?: UserEditData | null;
}

const UserEditModal: React.FC<UserEditModalProps> = ({
  open,
  onCancel,
  onOk,
  initialValues,
}) => {
  const [form] = Form.useForm<UserFormValues>();
  const isEditMode = !!initialValues;
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (open) {
      if (initialValues) {
        form.setFieldsValue({
          ...initialValues,
          newPassword: '',
          confirmPassword: '',
        });
      } else {
        form.resetFields();
      }
    } else {
      form.resetFields();
    }
  }, [open, initialValues, form]);

  const handleOk = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);

      if (isEditMode) {
        if (values.newPassword) {
          await updateAccount(initialValues?.key as string, {
            nickname: values.name,
            password: values.newPassword,
          });
          message.success(
            $i18n.get({
              id: 'main.pages.Setting.Account.components.UserEditModal.index.updateSuccess',
              dm: 'Updated successfully',
            }),
          );
        }
      } else {
        await createAccount({
          username: values.name,
          password: values.newPassword as string,
        });
        message.success(
          $i18n.get({
            id: 'main.pages.Setting.Account.components.UserEditModal.index.userCreateSuccess',
            dm: 'User created successfully',
          }),
        );
      }

      onOk();
    } finally {
      setLoading(false);
    }
  };

  const modalTitle = isEditMode
    ? $i18n.get({
        id: 'main.pages.Setting.Account.components.UserEditModal.index.editUser',
        dm: 'Edit User',
      })
    : $i18n.get({
        id: 'main.pages.Setting.Account.components.UserEditModal.index.addUser',
        dm: 'Add User',
      });

  return (
    <Modal
      title={modalTitle}
      open={open}
      onCancel={onCancel}
      footer={[
        <Button key="cancel" onClick={onCancel}>
          {$i18n.get({
            id: 'main.pages.Setting.Account.components.UserEditModal.index.cancel',
            dm: 'Cancel',
          })}
        </Button>,
        <Button
          key="submit"
          type="primary"
          onClick={handleOk}
          loading={loading}
        >
          {$i18n.get({
            id: 'main.pages.Setting.Account.components.UserEditModal.index.confirm',
            dm: 'Confirm',
          })}
        </Button>,
      ]}
      width={640}
    >
      <Form form={form} layout="vertical" requiredMark={false} colon={false}>
        <Form.Item
          name="name"
          label={$i18n.get({
            id: 'main.pages.Setting.Account.components.UserEditModal.index.userName',
            dm: 'Username',
          })}
          rules={[
            {
              required: true,
              message: $i18n.get({
                id: 'main.pages.Setting.Account.components.UserEditModal.index.enterUserName',
                dm: 'Please enter username',
              }),
            },
          ]}
        >
          <Input
            placeholder={$i18n.get({
              id: 'main.pages.Setting.Account.components.UserEditModal.index.enterUserName',
              dm: 'Please enter username',
            })}
            maxLength={30}
            showCount
            disabled={isEditMode}
          />
        </Form.Item>

        <div className={styles['section-title']}>
          {isEditMode
            ? $i18n.get({
                id: 'main.pages.Setting.Account.components.UserEditModal.index.changePassword',
                dm: 'Change Password',
              })
            : $i18n.get({
                id: 'main.pages.Setting.Account.components.UserEditModal.index.confirmPassword',
                dm: 'Set Password',
              })}
        </div>

        <Form.Item
          name="newPassword"
          label={$i18n.get({
            id: 'main.pages.Setting.Account.components.UserEditModal.index.newPassword',
            dm: 'New Password',
          })}
          rules={[
            {
              required: !isEditMode,
              message: $i18n.get({
                id: 'main.pages.Login.components.Register.index.enterPassword',
                dm: 'Please enter password',
              }),
            },
          ]}
        >
          <Input.Password
            placeholder={
              isEditMode
                ? $i18n.get({
                    id: 'main.pages.Setting.Account.components.UserEditModal.index.enterNewPassword',
                    dm: 'Enter new password to change',
                  })
                : $i18n.get({
                    id: 'main.pages.Setting.Account.components.UserEditModal.index.enterPassword',
                    dm: 'Enter password',
                  })
            }
          />
        </Form.Item>

        <Form.Item
          name="confirmPassword"
          label={$i18n.get({
            id: 'main.pages.Setting.Account.components.UserEditModal.index.confirmPassword',
            dm: 'Confirm Password',
          })}
          dependencies={['newPassword']}
          rules={[
            {
              required: true,
              message: $i18n.get({
                id: 'main.pages.Setting.Account.components.UserEditModal.index.confirmPassword',
                dm: 'Please confirm password',
              }),
            },
            ({ getFieldValue }) => ({
              validator(_, value) {
                const newPassword = getFieldValue('newPassword');

                if (!newPassword) {
                  return Promise.reject(
                    new Error(
                      $i18n.get({
                        id: 'main.pages.Setting.Account.components.UserEditModal.index.enterPasswordFirst',
                        dm: 'Please enter password first',
                      }),
                    ),
                  );
                }

                return newPassword === value
                  ? Promise.resolve()
                  : Promise.reject(
                      new Error(
                        $i18n.get({
                          id: 'main.pages.Setting.Account.components.UserEditModal.index.passwordNotMatch',
                          dm: 'The two passwords do not match',
                        }),
                      ),
                    );
              },
            }),
          ]}
        >
          <Input.Password
            placeholder={
              isEditMode
                ? $i18n.get({
                    id: 'main.pages.Setting.Account.components.UserEditModal.index.confirmNewPassword',
                    dm: 'Confirm new password',
                  })
                : $i18n.get({
                    id: 'main.pages.Setting.Account.components.UserEditModal.index.confirmPassword',
                    dm: 'Confirm password',
                  })
            }
          />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default UserEditModal;
