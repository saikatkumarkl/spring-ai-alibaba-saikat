import InnerLayout from '@/components/InnerLayout';
import $i18n from '@/i18n';
import { deleteApiKey, getApiKey, listApiKeys } from '@/services/apiKey';
import { IApiKey } from '@/types/apiKey';
import {
  AlertDialog,
  Button,
  IconFont,
  message,
  Pagination,
} from '@spark-ai/design';
import { Table } from 'antd';
import copy from 'copy-to-clipboard';
import dayjs from 'dayjs';
import { useEffect, useState } from 'react';
import CreateModal from './components/CreateModal';
import styles from './index.module.less';

export default function APIKeys() {
  const [loading, setLoading] = useState(false);
  const [apiKeys, setApiKeys] = useState<IApiKey[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
  const [isKeyVisible, setIsKeyVisible] = useState<
    Record<string, string | boolean>
  >({});

  const fetchApiKeys = async (page = current, size = pageSize) => {
    setLoading(true);
    try {
      const res = await listApiKeys({
        current: page,
        size: size,
      });
      if (res.data) {
        setApiKeys(res.data.records);
        setTotal(res.data.total);
        setCurrent(res.data.current);
        setPageSize(res.data.size);
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchApiKeys();
  }, []);

  const handleDeleteApiKey = (id: number | string) => {
    AlertDialog.warning({
      title: $i18n.get({
        id: 'main.pages.Setting.APIKeys.index.deleteApiKey',
        dm: 'Delete API KEY',
      }),
      children: $i18n.get({
        id: 'main.pages.Setting.APIKeys.index.confirmDelete',
        dm: 'This API KEY will no longer be usable after deletion. Are you sure you want to delete it?',
      }),
      onOk: async () => {
        const res = await deleteApiKey(id);
        if (res) {
          message.success(
            $i18n.get({
              id: 'main.pages.Setting.APIKeys.index.deleteSuccess',
              dm: 'Deleted successfully',
            }),
          );
          fetchApiKeys();
        }
      },
    });
  };

  const handleCreateApiKey = () => {
    setIsCreateModalOpen(true);
  };

  const handleCreateSuccess = () => {
    setIsCreateModalOpen(false);
    fetchApiKeys();
  };

  const showApiKey = async (id: number | string) => {
    const res = await getApiKey(id);
    if (res?.data?.api_key) {
      setIsKeyVisible({
        [id]: res.data.api_key,
      });
    }
  };

  const columns = [
    {
      title: 'API KEY',
      dataIndex: 'api_key',
      key: 'api_key',
      width: 500,
      render: (text: string, record: IApiKey) => {
        const id = record.id || '';
        return (
          <div className={styles['api-key-cell']}>
            <span className={styles['api-key-text']}>
              {isKeyVisible[id] ? isKeyVisible[id] : text}
            </span>
          </div>
        );
      },
    },
    {
      title: $i18n.get({
        id: 'main.pages.Setting.APIKeys.index.description',
        dm: 'Description',
      }),
      dataIndex: 'description',
      key: 'description',
    },
    {
      title: $i18n.get({
        id: 'main.pages.Setting.APIKeys.index.createTime',
        dm: 'Created At',
      }),
      dataIndex: 'gmt_create',
      key: 'gmt_create',
      render: (text: string) => {
        return (
          <span>{text ? dayjs(text).format('YYYY-MM-DD HH:mm:ss') : ''}</span>
        );
      },
    },
    {
      title: $i18n.get({
        id: 'main.pages.Setting.APIKeys.index.operation',
        dm: 'Actions',
      }),
      key: 'action',
      render: (_: any, record: IApiKey) => {
        const id = record.id || '';
        return (
          <div className={styles['action-column']}>
            {isKeyVisible[id] ? (
              <Button
                type="link"
                onClick={() => {
                  copy(isKeyVisible[id] as string);
                  message.success(
                    $i18n.get({
                      id: 'main.utils.base.copySuccess',
                      dm: 'Copied successfully',
                    }),
                  );
                }}
              >
                {$i18n.get({
                  id: 'main.pages.Setting.APIKeys.index.copy',
                  dm: 'Copy',
                })}
              </Button>
            ) : (
              <Button type="link" onClick={() => showApiKey(id)}>
                {$i18n.get({
                  id: 'main.pages.Setting.APIKeys.index.view',
                  dm: 'View',
                })}
              </Button>
            )}
            <Button type="link" onClick={() => handleDeleteApiKey(id)}>
              {$i18n.get({
                id: 'main.pages.Setting.APIKeys.index.delete',
                dm: 'Delete',
              })}
            </Button>
          </div>
        );
      },
    },
  ];

  const pagination = (
    <div className={styles.pagination}>
      <Pagination
        hideTips
        current={current}
        pageSize={pageSize}
        total={total}
        onChange={async (current, pageSize) => {
          await fetchApiKeys(current, pageSize);
        }}
      />
    </div>
  );

  return (
    <InnerLayout
      breadcrumbLinks={[
        {
          title: $i18n.get({
            id: 'main.pages.App.index.home',
            dm: 'Home',
          }),
          path: '/',
        },
        {
          title: $i18n.get({
            id: 'main.pages.Setting.APIKeys.index.apiKeyManagement',
            dm: 'API KEY Management',
          }),
        },
      ]}
      left={total}
      right={
        <Button
          type="primary"
          icon={<IconFont type="spark-plus-line" />}
          onClick={handleCreateApiKey}
        >
          {$i18n.get({
            id: 'main.pages.Setting.APIKeys.index.createApiKey',
            dm: 'Create API KEY',
          })}
        </Button>
      }
      bottom={pagination}
    >
      <div className={styles.container}>
        <Table
          loading={loading}
          columns={columns}
          dataSource={apiKeys}
          rowKey="id"
          pagination={false}
        />

        <CreateModal
          open={isCreateModalOpen}
          onCancel={() => setIsCreateModalOpen(false)}
          onSuccess={handleCreateSuccess}
        />
      </div>
    </InnerLayout>
  );
}
