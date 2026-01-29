import InnerLayout from '@/components/InnerLayout';
import $i18n from '@/i18n';
import { deleteMcpServer, getMcpServer } from '@/services/mcp';
import { IMcpServer, McpStatus } from '@/types/mcp';
import {
  AlertDialog,
  Button,
  Dropdown,
  IconButton,
  message,
  Tooltip,
} from '@spark-ai/design';
import { Empty } from 'antd';
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import MCPTools from './components/McpTools';
import Overview from './components/Overview';
import styles from './Detail.module.less';

const McpDetail = () => {
  const navigate = useNavigate();
  const { id: serverCode } = useParams<{ id: string }>();

  const [activeTab, setActiveTab] = useState('mcp_overview');
  const [detail, setDetail] = useState<IMcpServer | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    getDetail();
  }, [serverCode]);

  const getDetail = async () => {
    if (serverCode) {
      setLoading(true);
      const detail = await getMcpServer({
        server_code: serverCode,
        need_tools: true,
      });
      setDetail(detail.data);
      setLoading(false);
    }
  };

  const handleDelete = () => {
    if (!detail) return;

    AlertDialog.warning({
      title: $i18n.get({
        id: 'main.pages.MCP.Detail.confirmDeleteThisMcpService',
        dm: 'Confirm delete this MCP service?',
      }),
      children: $i18n.get({
        id: 'main.pages.MCP.Detail.deleteWillNotBeRecoverableAlreadyAddedServicesMayFailPleaseProceedWithCaution',
        dm: 'Deletion cannot be recovered. Agents that have added this service may fail. Please proceed with caution.',
      }),

      danger: true,
      onOk: () => {
        deleteMcpServer(detail.server_code).then(() => {
          message.success(
            $i18n.get({
              id: 'main.pages.MCP.Detail.deletionSuccessful',
              dm: 'Deleted successfully',
            }),
          );
          navigate('/mcp');
        });
      },
    });
  };

  const operations = () => {
    return (
      <>
        <Dropdown
          getPopupContainer={(ele) => ele}
          menu={{
            items: [
              {
                onClick: () => handleDelete(),
                danger: true,
                label: $i18n.get({
                  id: 'main.pages.MCP.Detail.delete',
                  dm: 'Delete',
                }),
                key: 'delete',
              },
            ],
          }}
        >
          <IconButton icon="spark-more-line" bordered={false} />
        </Dropdown>
        <Button
          type="primary"
          onClick={() => navigate(`/mcp/edit/${serverCode}`)}
        >
          {$i18n.get({
            id: 'main.pages.MCP.Detail.edit',
            dm: 'Edit',
          })}
        </Button>
      </>
    );
  };

  const breadcrumbLinks = [
    {
      title: $i18n.get({
        id: 'main.pages.MCP.Detail.mcpManagement',
        dm: 'MCP Management',
      }),
      path: `/mcp`,
    },
    {
      title:
        detail?.name ||
        $i18n.get({
          id: 'main.pages.MCP.Detail.mcpDetails',
          dm: 'MCP Details',
        }),
    },
  ];

  const renderOverview = () => {
    return <>{detail && <Overview detail={detail} />}</>;
  };

  return (
    <>
      <InnerLayout
        breadcrumbLinks={detail ? breadcrumbLinks : []}
        loading={loading}
        tabs={[
          {
            label: $i18n.get({
              id: 'main.pages.MCP.Detail.overview',
              dm: 'Overview',
            }),
            key: 'mcp_overview',
            children: renderOverview(),
          },
          {
            label: $i18n.get({
              id: 'main.pages.MCP.Detail.tools',
              dm: 'Tools',
            }),
            key: 'mcp_tools',
            disabled: detail?.status === McpStatus.DISABLED,
            children: detail ? (
              <div className={styles['tools-content']}>
                <MCPTools
                  tools={detail?.tools || []}
                  activated={detail?.status === McpStatus.ENABLED}
                  code={detail?.server_code}
                />
              </div>
            ) : (
              <Empty />
            ),
          },
          {
            label: (
              <Tooltip
                title={$i18n.get({
                  id: 'main.pages.MCP.Detail.comingSoon',
                  dm: 'Coming soon',
                })}
              >
                {$i18n.get({
                  id: 'main.pages.MCP.Detail.resources',
                  dm: 'Resources',
                })}
              </Tooltip>
            ),
            key: 'mcp_resource',
            disabled: true,
          },
          {
            label: (
              <Tooltip
                title={$i18n.get({
                  id: 'main.pages.MCP.Detail.comingSoon',
                  dm: 'Coming soon',
                })}
              >
                {$i18n.get({
                  id: 'main.pages.MCP.Detail.promptWords',
                  dm: 'Prompts',
                })}
              </Tooltip>
            ),
            key: 'mcp_prompt',
            disabled: true,
          },
        ]}
        right={operations()}
        activeTab={activeTab}
        onTabChange={(key) => {
          setActiveTab(key);
        }}
      ></InnerLayout>
    </>
  );
};

export default McpDetail;
