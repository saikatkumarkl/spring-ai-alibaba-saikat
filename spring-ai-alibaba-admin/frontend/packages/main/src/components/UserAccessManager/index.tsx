import React, { useEffect, useState } from 'react';
import { Select, Card, Typography, Button, message, Space, Tag, Spin, Empty, Alert } from 'antd';
import { UserOutlined, SaveOutlined, ReloadOutlined } from '@ant-design/icons';
import { getAppUsers, updateAppAccess, getAllUsers } from '@/services/chatbotAuth';
import $i18n from '@/i18n';

interface UserAccessManagerProps {
  appId: string;
}

interface ChatbotUser {
  email: string;
  full_name: string;
}

const UserAccessManager: React.FC<UserAccessManagerProps> = ({ appId }) => {
  const [allUsers, setAllUsers] = useState<ChatbotUser[]>([]);
  const [selectedEmails, setSelectedEmails] = useState<string[]>([]);
  const [originalEmails, setOriginalEmails] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const fetchData = async () => {
    setLoading(true);
    try {
      const [users, currentAccess] = await Promise.all([
        getAllUsers(),
        getAppUsers(appId),
      ]);
      setAllUsers(users || []);
      setSelectedEmails(currentAccess || []);
      setOriginalEmails(currentAccess || []);
    } catch (error) {
      console.error('Failed to fetch user access data:', error);
      message.error('Failed to load user access data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (appId) {
      fetchData();
    }
  }, [appId]);

  const handleSave = async () => {
    setSaving(true);
    try {
      await updateAppAccess(appId, selectedEmails);
      setOriginalEmails([...selectedEmails]);
      message.success('User access updated successfully');
    } catch (error) {
      console.error('Failed to update user access:', error);
      message.error('Failed to update user access');
    } finally {
      setSaving(false);
    }
  };

  const hasChanges = JSON.stringify(selectedEmails.sort()) !== JSON.stringify(originalEmails.sort());

  const options = allUsers.map((user) => ({
    label: `${user.full_name} (${user.email})`,
    value: user.email,
  }));

  return (
    <div style={{ padding: '24px', maxWidth: 800 }}>
      <Card
        title={
          <Space>
            <UserOutlined />
            <span>
              {$i18n.get({
                id: 'main.components.UserAccessManager.title',
                dm: 'User Access Control',
              })}
            </span>
          </Space>
        }
        extra={
          <Space>
            <Button
              icon={<ReloadOutlined />}
              onClick={fetchData}
              disabled={loading}
            >
              {$i18n.get({
                id: 'main.components.UserAccessManager.refresh',
                dm: 'Refresh',
              })}
            </Button>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              onClick={handleSave}
              loading={saving}
              disabled={!hasChanges}
            >
              {$i18n.get({
                id: 'main.components.UserAccessManager.save',
                dm: 'Save',
              })}
            </Button>
          </Space>
        }
      >
        <Alert
          message={$i18n.get({
            id: 'main.components.UserAccessManager.description',
            dm: 'Select which users can access this application via the chatbot. Only selected users will be able to use this app after logging in.',
          })}
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
        />

        <Spin spinning={loading}>
          {allUsers.length === 0 && !loading ? (
            <Empty
              description={$i18n.get({
                id: 'main.components.UserAccessManager.noUsers',
                dm: 'No chatbot users found. Users are created via the init SQL script.',
              })}
            />
          ) : (
            <>
              <Typography.Text
                strong
                style={{ display: 'block', marginBottom: 8 }}
              >
                {$i18n.get({
                  id: 'main.components.UserAccessManager.selectUsers',
                  dm: 'Authorized Users',
                })}
              </Typography.Text>
              <Select
                mode="multiple"
                style={{ width: '100%' }}
                placeholder={$i18n.get({
                  id: 'main.components.UserAccessManager.placeholder',
                  dm: 'Select users by email...',
                })}
                value={selectedEmails}
                onChange={setSelectedEmails}
                options={options}
                optionFilterProp="label"
                showSearch
                allowClear
                size="large"
                tagRender={(props) => {
                  const { label, closable, onClose } = props;
                  return (
                    <Tag
                      color="blue"
                      closable={closable}
                      onClose={onClose}
                      style={{ marginRight: 3 }}
                    >
                      {label}
                    </Tag>
                  );
                }}
              />
              <Typography.Text
                type="secondary"
                style={{ display: 'block', marginTop: 8 }}
              >
                {selectedEmails.length}{' '}
                {$i18n.get({
                  id: 'main.components.UserAccessManager.userCount',
                  dm: 'user(s) have access to this app',
                })}
              </Typography.Text>
            </>
          )}
        </Spin>
      </Card>
    </div>
  );
};

export default UserAccessManager;
