import InnerLayout from '@/components/InnerLayout';
import { useInnerLayout } from '@/components/InnerLayout/utils';
import $i18n from '@/i18n';
import {
  enableTool,
  getPlugin,
  getPluginToolList,
  removeTool,
} from '@/services/plugin';
import { PluginTool } from '@/types/plugin';
import {
  AlertDialog,
  Button,
  Empty,
  IconFont,
  Switch,
  Tag,
  Tooltip,
  message,
} from '@spark-ai/design';
import { useRequest } from 'ahooks';
import { Dropdown, Flex, Table } from 'antd';
import dayjs from 'dayjs';
import { useState } from 'react';
import { history, useParams } from 'umi';
import styles from './index.module.less';

export default function () {
  const { rightPortal } = useInnerLayout();
  const [token, setToken] = useState(0);
  const { id = '' } = useParams<{ id: string }>();
  const { data: pluginData } = useRequest(() => getPlugin(id));
  const { data: toolData, loading } = useRequest(() => getPluginToolList(id), {
    refreshDeps: [token],
  });
  const toolListData: PluginTool[] = toolData?.data?.records || [];

  const statusTag = (record: PluginTool) => {
    switch (record.status) {
      case 'draft':
        return (
          <Tag color="mauve">
            {$i18n.get({
              id: 'main.pages.Component.Plugin.Tools.List.draft',
              dm: 'Draft',
            })}
          </Tag>
        );

      case 'published':
        return (
          <Tag color="blue">
            {$i18n.get({
              id: 'main.pages.Component.Plugin.Tools.List.published',
              dm: 'Published',
            })}
          </Tag>
        );

      case 'published_editing':
        return (
          <Tag color="blue">
            {$i18n.get({
              id: 'main.pages.Component.Plugin.Tools.List.editedPublished',
              dm: 'Published (Editing)',
            })}
          </Tag>
        );

      default:
        return null;
    }
  };

  const testStatus = (status: string | undefined) => {
    if (!status) return '-';
    switch (status) {
      case 'passed':
        return (
          <>
            <IconFont
              type="spark-checkCircle-fill"
              style={{ color: 'var(--ag-ant-color-success-base)' }}
            />

            <span>
              {$i18n.get({
                id: 'main.pages.Component.Plugin.Tools.List.success',
                dm: 'Success',
              })}
            </span>
          </>
        );

      case 'failed':
        return (
          <>
            <IconFont
              type="spark-errorCircle-fill"
              style={{ color: 'var(--ag-ant-color-error-base)' }}
            />

            <span>
              {$i18n.get({
                id: 'main.pages.Component.Plugin.Tools.List.failure',
                dm: 'Failure',
              })}
            </span>
          </>
        );

      case 'not_test':
        return (
          <>
            <IconFont
              type="spark-delete02-fill"
              style={{ color: 'var(--ag-ant-color-text-description)' }}
            />
            <span>
              {$i18n.get({
                id: 'main.pages.Component.Plugin.Tools.List.notTested',
                dm: 'Not Tested',
              })}
            </span>
          </>
        );

      default:
        return '-';
    }
  };

  const columns = [
    {
      title: $i18n.get({
        id: 'main.pages.Component.Plugin.Tools.List.toolName',
        dm: 'Tool Name',
      }),
      key: 'name',
      width: 500,
      render: (_: any, record: PluginTool) => (
        <Flex gap={10} align="center">
          <IconFont type="spark-tool-line" className={styles['tool-icon']} />
          <div>
            <Flex align="center" gap={8}>
              <div className={styles.name}>{record.name}</div>
              {statusTag(record)}
            </Flex>
            <div className={styles.desc}>{record.description}</div>
          </div>
        </Flex>
      ),
    },
    {
      title: $i18n.get({
        id: 'main.pages.Component.Plugin.Tools.List.inputParameters',
        dm: 'Input Parameters',
      }),
      key: 'input_params',
      render: (_: any, record: PluginTool) => {
        return (record.config?.input_params || [])
          .map((item) => item.key)
          .join(',');
      },
      width: 200,
      ellipsis: true,
    },
    {
      title: $i18n.get({
        id: 'main.pages.Component.Plugin.Tools.List.debugStatus',
        dm: 'Debug Status',
      }),
      key: 'test_status',
      render: (_: any, record: PluginTool) => {
        return (
          <Flex align="center" gap={4}>
            {testStatus(record.test_status)}
          </Flex>
        );
      },
    },
    {
      title: $i18n.get({
        id: 'main.pages.Component.Plugin.Tools.List.updateTime',
        dm: 'Update Time',
      }),
      key: 'update_time',
      render: (_: any, record: PluginTool) => {
        return dayjs(record.gmt_modified).format('YYYY-MM-DD HH:mm');
      },
    },
    {
      title: $i18n.get({
        id: 'main.pages.Component.Plugin.Tools.List.enable',
        dm: 'Enable',
      }),
      key: 'enabled',
      render: (_: any, record: PluginTool) => {
        return (
          <Flex align="center" gap={8}>
            <Switch
              size="small"
              checked={record.enabled}
              disabled={record.status === 'draft'}
              onChange={(v) => {
                enableTool(record.tool_id as string, v).then(() => {
                  setToken(token + 1);
                  message.success(
                    $i18n.get({
                      id: 'main.pages.Component.Plugin.Tools.List.operationSuccess',
                      dm: 'Operation successful',
                    }),
                  );
                });
              }}
            />

            <div>
              {record.enabled
                ? $i18n.get({
                    id: 'main.pages.Component.Plugin.Tools.List.enabled',
                    dm: 'Enabled',
                  })
                : $i18n.get({
                    id: 'main.pages.Component.Plugin.Tools.List.disabled',
                    dm: 'Disabled',
                  })}
            </div>
          </Flex>
        );
      },
    },
    {
      title: $i18n.get({
        id: 'main.pages.Component.Plugin.Tools.List.operation',
        dm: 'Actions',
      }),
      key: 'action',
      render: (_: any, record: PluginTool) => {
        return (
          <Flex>
            <Button
              onClick={() => {
                history.push(`/component/plugin/${id}/tool/${record.tool_id}`);
              }}
              size="small"
              type="link"
            >
              {$i18n.get({
                id: 'main.pages.Component.Plugin.Tools.List.edit',
                dm: 'Edit',
              })}
            </Button>
            <Flex align="center">
              <Dropdown
                menu={{
                  items: [
                    {
                      label: $i18n.get({
                        id: 'main.pages.Component.Plugin.Tools.List.delete',
                        dm: 'Delete',
                      }),
                      key: 'delete',
                      danger: true,
                      onClick: () => handleDelete(record),
                    },
                  ],
                }}
              >
                <Button className="gap-1" size="small" type="link">
                  {$i18n.get({
                    id: 'main.pages.Component.Plugin.Tools.List.more',
                    dm: 'More',
                  })}

                  <IconFont type="spark-down-line" />
                </Button>
              </Dropdown>
            </Flex>
          </Flex>
        );
      },
    },
  ];

  const handleDelete = (record: PluginTool) =>
    AlertDialog.warning({
      title: $i18n.get({
        id: 'main.pages.Component.Plugin.Tools.List.confirmDeleteTool',
        dm: 'Delete this tool?',
      }),
      children: $i18n.get({
        id: 'main.pages.Component.Plugin.Tools.List.deleteWithoutSave',
        dm: 'After deletion, tool information will not be saved. Confirm?',
      }),
      onOk() {
        removeTool(id, record.tool_id || '').then(() => {
          message.success(
            $i18n.get({
              id: 'main.pages.Component.Plugin.Tools.List.successDelete',
              dm: 'Delete successful',
            }),
          );
          setToken(token + 1);
        });
      },
    });

  return (
    <InnerLayout
      breadcrumbLinks={[
        {
          title: $i18n.get({
            id: 'main.pages.Component.Plugin.Tools.List.componentManagement',
            dm: 'Component Management',
          }),
          path: `/component/plugin`,
        },
        {
          title: pluginData?.data.name,
        },
      ]}
      left={
        <Tooltip
          title={$i18n.get({
            id: 'main.pages.Component.Plugin.Tools.List.definePluginInterface',
            dm: 'Define your plugin interface. For details, see the plugin interface protocol.',
          })}
        >
          <IconFont type="spark-warningCircle-line" />
        </Tooltip>
      }
    >
      <div className={styles.container}>
        {!loading && toolListData.length === 0 ? (
          <Flex className="h-full" justify="center" align="center">
            <Empty
              title={$i18n.get({
                id: 'main.pages.Component.Plugin.Tools.List.noData',
                dm: 'No data',
              })}
            >
              <Button
                className="mt-[12px]"
                type="primary"
                icon={<IconFont type="spark-plus-line" />}
                onClick={() =>
                  history.push(`/component/plugin/${id}/tool/create`)
                }
              >
                {$i18n.get({
                  id: 'main.pages.Component.Plugin.Tools.List.createTool',
                  dm: 'Create Tool',
                })}
              </Button>
            </Empty>
          </Flex>
        ) : (
          <>
            <Flex
              justify="space-between"
              align="center"
              className={styles['content-header']}
            >
              <span className={styles.title}>
                {$i18n.get({
                  id: 'main.pages.Component.Plugin.Tools.List.toolList',
                  dm: 'Tool List',
                })}
              </span>
              <Button
                iconType="spark-plus-line"
                type="primary"
                onClick={() =>
                  history.push(`/component/plugin/${id}/tool/create`)
                }
              >
                {$i18n.get({
                  id: 'main.pages.Component.Plugin.Tools.List.createTool',
                  dm: 'Create Tool',
                })}
              </Button>
            </Flex>
            <Table
              columns={columns}
              dataSource={toolListData}
              rowKey="tool_id"
              pagination={false}
              loading={loading}
            />
          </>
        )}
      </div>
      {rightPortal(
        <>
          <Button
            iconType="spark-setting-line"
            onClick={() => history.push(`/component/plugin/${id}`)}
          >
            {$i18n.get({
              id: 'main.pages.Component.Plugin.Tools.List.editPlugin',
              dm: 'Edit Plugin',
            })}
          </Button>
        </>,
      )}
    </InnerLayout>
  );
}
