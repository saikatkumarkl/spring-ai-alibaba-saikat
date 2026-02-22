import React, { useState, useCallback } from 'react';
import { Table, Tag, Button, Switch, message, Space, Typography, Input, Card } from 'antd';
import { ReloadOutlined, SearchOutlined, UserOutlined, CrownOutlined } from '@ant-design/icons';
import { useRequest } from 'ahooks';
import {
  listKeycloakUsers,
  assignRole,
  removeRole,
  KeycloakUser,
} from '@/services/keycloakUsers';
import $i18n from '@/i18n';

const { Title, Text } = Typography;

/**
 * User Management page — lists Keycloak users and allows
 * admins to grant/revoke the "admin" role.
 */
const UsersPage: React.FC = () => {
  const [searchText, setSearchText] = useState('');

  const {
    data: usersData,
    loading,
    refresh,
  } = useRequest(() => listKeycloakUsers(0, 200), {
    onError(err) {
      message.error('Failed to load users: ' + (err as Error).message);
    },
  });

  const users: KeycloakUser[] = usersData?.data ?? [];

  const filteredUsers = users.filter((u) => {
    if (!searchText) return true;
    const q = searchText.toLowerCase();
    return (
      u.username?.toLowerCase().includes(q) ||
      u.email?.toLowerCase().includes(q) ||
      u.firstName?.toLowerCase().includes(q) ||
      u.lastName?.toLowerCase().includes(q)
    );
  });

  const handleToggleAdmin = useCallback(
    async (user: KeycloakUser, checked: boolean) => {
      try {
        if (checked) {
          await assignRole(user.id, 'admin');
          message.success(`Granted admin role to ${user.username}`);
        } else {
          await removeRole(user.id, 'admin');
          message.success(`Revoked admin role from ${user.username}`);
        }
        refresh();
      } catch (err) {
        message.error('Failed to update role: ' + (err as Error).message);
      }
    },
    [refresh],
  );

  const columns = [
    {
      title: 'Username',
      dataIndex: 'username',
      key: 'username',
      render: (text: string) => (
        <Space>
          <UserOutlined />
          <Text strong>{text}</Text>
        </Space>
      ),
    },
    {
      title: 'Email',
      dataIndex: 'email',
      key: 'email',
      render: (text: string) => text || <Text type="secondary">—</Text>,
    },
    {
      title: 'Name',
      key: 'name',
      render: (_: unknown, record: KeycloakUser) => {
        const name = [record.firstName, record.lastName].filter(Boolean).join(' ');
        return name || <Text type="secondary">—</Text>;
      },
    },
    {
      title: 'Roles',
      dataIndex: 'roles',
      key: 'roles',
      render: (roles: string[]) => (
        <Space wrap>
          {roles?.map((role) => (
            <Tag
              key={role}
              color={role === 'admin' ? 'gold' : 'blue'}
              icon={role === 'admin' ? <CrownOutlined /> : undefined}
            >
              {role}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 90,
      render: (enabled: boolean) => (
        <Tag color={enabled ? 'green' : 'red'}>{enabled ? 'Active' : 'Disabled'}</Tag>
      ),
    },
    {
      title: 'Admin Access',
      key: 'adminToggle',
      width: 130,
      render: (_: unknown, record: KeycloakUser) => {
        const isAdmin = record.roles?.includes('admin');
        return (
          <Switch
            checked={isAdmin}
            onChange={(checked) => handleToggleAdmin(record, checked)}
            checkedChildren="Admin"
            unCheckedChildren="User"
          />
        );
      },
    },
    {
      title: 'Registered',
      dataIndex: 'createdTimestamp',
      key: 'createdTimestamp',
      width: 180,
      render: (ts: number) =>
        ts ? new Date(ts).toLocaleString() : <Text type="secondary">—</Text>,
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Card bordered={false}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
          <div>
            <Title level={4} style={{ margin: 0 }}>
              <CrownOutlined style={{ marginRight: 8 }} />
              {$i18n.get({ id: 'main.pages.Users.title', dm: 'User Management' })}
            </Title>
            <Text type="secondary" style={{ marginTop: 4, display: 'block' }}>
              {$i18n.get({
                id: 'main.pages.Users.subtitle',
                dm: 'Manage Keycloak users and control admin access to the Control Center',
              })}
            </Text>
          </div>
          <Space>
            <Input
              placeholder="Search users..."
              prefix={<SearchOutlined />}
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
              style={{ width: 240 }}
              allowClear
            />
            <Button icon={<ReloadOutlined />} onClick={refresh} loading={loading}>
              Refresh
            </Button>
          </Space>
        </div>

        <Table
          dataSource={filteredUsers}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={{
            showSizeChanger: true,
            showTotal: (total) => `Total ${total} users`,
            pageSize: 20,
          }}
          size="middle"
        />
      </Card>
    </div>
  );
};

export default UsersPage;
