import InnerLayout from '@/components/InnerLayout';
import $i18n from '@/i18n';
import {
  createDestination,
  getDestinationDetail,
  testConnectionInline,
  updateDestination,
} from '@/services/destination';
import { Button, Form, Input, message, Select } from '@spark-ai/design';
import { useMount, useSetState } from 'ahooks';
import { InputNumber } from 'antd';
import React from 'react';
import { history, useParams } from 'umi';
import styles from './index.module.less';

interface FormState {
  name: string;
  description: string;
  provider_type: string;
  url: string;
  username: string;
  password: string;
  index_name: string;
}

interface PageState {
  loading: boolean;
  saving: boolean;
  testing: boolean;
  testResult: { status: string; message: string; details?: string } | null;
  isEdit: boolean;
  destinationId: string;
}

export default function DestinationCreate() {
  const params = useParams<{ id?: string }>();
  const isEdit = !!params.id;

  const [form, setForm] = useSetState<FormState>({
    name: '',
    description: '',
    provider_type: 'opensearch',
    url: '',
    username: '',
    password: '',
    index_name: '',
  });

  const [state, setState] = useSetState<PageState>({
    loading: false,
    saving: false,
    testing: false,
    testResult: null,
    isEdit,
    destinationId: params.id || '',
  });

  useMount(() => {
    if (isEdit && params.id) {
      setState({ loading: true });
      getDestinationDetail(params.id)
        .then((detail) => {
          const config = detail.connection_config || {};
          setForm({
            name: detail.name || '',
            description: detail.description || '',
            provider_type: detail.provider_type || 'opensearch',
            url: config.url || '',
            username: config.username || '',
            password: config.password || '',
            index_name: config.index_name || '',
          });
          setState({ loading: false });
        })
        .catch(() => {
          message.error('Failed to load destination details');
          setState({ loading: false });
        });
    }
  });

  const buildConnectionConfig = () => ({
    url: form.url,
    username: form.username,
    password: form.password,
    index_name: form.index_name,
    provider_type: form.provider_type,
  });

  const handleTestConnection = () => {
    if (!form.url) {
      message.warning('Please enter the OpenSearch URL');
      return;
    }
    setState({ testing: true, testResult: null });
    testConnectionInline({
      provider_type: form.provider_type || 'opensearch',
      connection_config: buildConnectionConfig(),
    })
      .then((result) => {
        setState({
          testing: false,
          testResult: {
            status: result.status,
            message: result.message,
            details: result.cluster_name
              ? `Cluster: ${result.cluster_name}, Version: ${result.version}`
              : undefined,
          },
        });
      })
      .catch((err) => {
        setState({
          testing: false,
          testResult: {
            status: 'FAIL',
            message: err?.response?.data?.error || 'Connection test failed',
          },
        });
      });
  };

  const handleSave = () => {
    if (!form.name?.trim()) {
      message.warning('Please enter a destination name');
      return;
    }
    if (!form.url?.trim()) {
      message.warning('Please enter the OpenSearch URL');
      return;
    }

    setState({ saving: true });

    if (isEdit && params.id) {
      updateDestination({
        destination_id: params.id,
        name: form.name,
        description: form.description,
        connection_config: buildConnectionConfig(),
      })
        .then(() => {
          message.success('Destination updated successfully');
          history.push('/destination');
        })
        .catch(() => {
          message.error('Failed to update destination');
        })
        .finally(() => setState({ saving: false }));
    } else {
      createDestination({
        name: form.name,
        description: form.description,
        provider_type: form.provider_type,
        connection_config: buildConnectionConfig(),
      })
        .then(() => {
          message.success('Destination created successfully');
          history.push('/destination');
        })
        .catch(() => {
          message.error('Failed to create destination');
        })
        .finally(() => setState({ saving: false }));
    }
  };

  return (
    <InnerLayout
      breadcrumbLinks={[
        {
          title: $i18n.get({ id: 'main.pages.Destination.destinations', dm: 'Destinations' }),
          path: '/destination',
        },
        {
          title: isEdit
            ? $i18n.get({ id: 'main.pages.Destination.Create.editDestination', dm: 'Edit Destination' })
            : $i18n.get({ id: 'main.pages.Destination.Create.createDestination', dm: 'Create Destination' }),
        },
      ]}
      bottom={
        <div className={styles['footer']}>
          <Button onClick={() => history.push('/destination')}>
            {$i18n.get({ id: 'main.pages.Destination.Create.cancel', dm: 'Cancel' })}
          </Button>
          <Button onClick={handleTestConnection} loading={state.testing}>
            {$i18n.get({ id: 'main.pages.Destination.Create.testConnection', dm: 'Test Connection' })}
          </Button>
          <Button type="primary" onClick={handleSave} loading={state.saving}>
            {isEdit
              ? $i18n.get({ id: 'main.pages.Destination.Create.save', dm: 'Save' })
              : $i18n.get({ id: 'main.pages.Destination.Create.create', dm: 'Create' })}
          </Button>
        </div>
      }
    >
      <div className={styles['container']}>
        <div className={styles['form-section']}>
          <div className={styles['section-title']}>
            {$i18n.get({ id: 'main.pages.Destination.Create.basicInfo', dm: 'Basic Information' })}
          </div>
          <Form layout="vertical">
            <Form.Item
              label={$i18n.get({ id: 'main.pages.Destination.Create.name', dm: 'Name' })}
              required
            >
              <Input
                value={form.name}
                onChange={(e) => setForm({ name: e.target.value })}
                placeholder="Enter destination name"
                maxLength={50}
                showCount
              />
            </Form.Item>
            <Form.Item
              label={$i18n.get({ id: 'main.pages.Destination.Create.description', dm: 'Description' })}
            >
              <Input.TextArea
                value={form.description}
                onChange={(e) => setForm({ description: e.target.value })}
                placeholder="Enter description (optional)"
                style={{ height: 80 }}
              />
            </Form.Item>
            <Form.Item
              label={$i18n.get({ id: 'main.pages.Destination.Create.providerType', dm: 'Provider Type' })}
              required
            >
              <Select
                value={form.provider_type}
                onChange={(val) => setForm({ provider_type: val })}
                options={[
                  { label: 'OpenSearch', value: 'opensearch' },
                ]}
                style={{ width: '100%' }}
              />
            </Form.Item>
          </Form>
        </div>

        <div className={styles['form-section']}>
          <div className={styles['section-title']}>
            {$i18n.get({ id: 'main.pages.Destination.Create.connectionDetails', dm: 'Connection Details' })}
          </div>
          <Form layout="vertical">
            <Form.Item
              label={$i18n.get({ id: 'main.pages.Destination.Create.url', dm: 'OpenSearch URL' })}
              required
            >
              <Input
                value={form.url}
                onChange={(e) => setForm({ url: e.target.value })}
                placeholder="http://localhost:9200"
              />
            </Form.Item>
            <Form.Item
              label={$i18n.get({ id: 'main.pages.Destination.Create.username', dm: 'Username' })}
            >
              <Input
                value={form.username}
                onChange={(e) => setForm({ username: e.target.value })}
                placeholder="Enter username (optional)"
              />
            </Form.Item>
            <Form.Item
              label={$i18n.get({ id: 'main.pages.Destination.Create.password', dm: 'Password' })}
            >
              <Input.Password
                value={form.password}
                onChange={(e) => setForm({ password: e.target.value })}
                placeholder="Enter password (optional)"
              />
            </Form.Item>
          </Form>
        </div>

        {state.testResult && (
          <div
            className={`${styles['test-result']} ${
              state.testResult.status === 'PASS'
                ? styles['test-success']
                : styles['test-failure']
            }`}
          >
            <div>{state.testResult.message}</div>
            {state.testResult.details && (
              <div className={styles['test-info']}>{state.testResult.details}</div>
            )}
          </div>
        )}
      </div>
    </InnerLayout>
  );
}
