import CardList from '@/components/Card/List';
import { useInnerLayout } from '@/components/InnerLayout/utils';
import $i18n from '@/i18n';
import {
  deleteMcpServer,
  listMcpServers,
  updateMcpServer,
} from '@/services/mcp';
import { IMcpServer, IPagingList, McpStatus } from '@/types/mcp';
import {
  AlertDialog,
  Button,
  ButtonProps,
  IconFont,
  message,
} from '@spark-ai/design';
import { useMount, useSetState } from 'ahooks';
import { memo } from 'react';
import { useNavigate } from 'react-router-dom';
import { history } from 'umi';
import McpCard from './components/McpCard';
import styles from './Manage.module.less';

export const CreateMcpBtn = memo(
  (props: {
    buttonProps?: ButtonProps;
    text?: string;
    isOpenNew?: boolean;
  }) => {
    const handleCreateMcp = () => {
      if (props.isOpenNew) {
        window.open('/mcp/create');
      } else {
        history.push('/mcp/create');
      }
    };

    return (
      <>
        <Button
          onClick={handleCreateMcp}
          type="primary"
          icon={<IconFont type="spark-plus-line" />}
          {...props.buttonProps}
        >
          {props.text ||
            $i18n.get({
              id: 'main.pages.MCP.Manage.createMcpService',
              dm: 'Create MCP Service',
            })}
        </Button>
      </>
    );
  },
);

export default function McpManage() {
  const { rightPortal } = useInnerLayout();
  const navigate = useNavigate();

  const [state, setState] = useSetState<{
    list: IMcpServer[];
    pageNo: number;
    pageSize: number;
    total: number;
    loading: boolean;
  }>({
    list: [],
    pageNo: 1,
    pageSize: 50,
    total: 0,
    loading: false,
  });

  const fetchList = async (
    extraParams = {} as Partial<{ pageNo: number; pageSize: number }>,
  ) => {
    setState({ loading: true });
    try {
      const queryParams = {
        current: extraParams.pageNo ?? state.pageNo,
        size: extraParams.pageSize ?? state.pageSize,
        need_tools: false,
      };

      const response = await listMcpServers(queryParams);
      if (response && response.data) {
        const pagingData = response.data as IPagingList<IMcpServer>;
        setState({
          list: pagingData.records || [],
          total: pagingData.total || 0,
          pageNo: queryParams.current,
          pageSize: queryParams.size,
        });
      }
    } finally {
      setState({ loading: false });
    }
  };

  useMount(() => {
    fetchList();
  });

  const handleConfirmDelete = (item: IMcpServer) => {
    AlertDialog.warning({
      title: $i18n.get({
        id: 'main.pages.MCP.Manage.confirmDeleteThisMcpService',
        dm: 'Confirm delete this MCP service?',
      }),
      children: $i18n.get({
        id: 'main.pages.MCP.Manage.deleteWillNotBeRecoverableAlreadyAddedServicesMayFailPleaseProceedWithCaution',
        dm: 'Deletion cannot be recovered. Agents that have added this service may fail. Please proceed with caution.',
      }),

      danger: true,
      onOk: async () => {
        await deleteMcpServer(item.server_code);
        message.success(
          $i18n.get({
            id: 'main.pages.MCP.Manage.deletionSuccessful',
            dm: 'Deleted successfully',
          }),
        );
        fetchList();
      },
    });
  };

  const handleConfirmStop = (item: IMcpServer) => {
    AlertDialog.warning({
      title: $i18n.get({
        id: 'main.pages.MCP.Manage.confirmStopDeploymentOfThisService',
        dm: 'Confirm stop deployment of this service?',
      }),
      cancelText: $i18n.get({
        id: 'main.pages.MCP.Manage.cancel',
        dm: 'Cancel',
      }),
      okText: $i18n.get({
        id: 'main.pages.MCP.Manage.stopDeployment',
        dm: 'Stop Deployment',
      }),
      onOk: async () => {
        await updateMcpServer({
          ...item,
          status: McpStatus.DISABLED,
        });
        message.success(
          $i18n.get({
            id: 'main.pages.MCP.Manage.stoppingSuccessful',
            dm: 'Stopped successfully',
          }),
        );
        fetchList();
      },
    });
  };

  const handleConfirmDeploy = (item: IMcpServer) => {
    AlertDialog.warning({
      title: $i18n.get({
        id: 'main.pages.MCP.Manage.confirmStartDeploymentOfThisService',
        dm: 'Confirm start deployment of this service?',
      }),
      cancelText: $i18n.get({
        id: 'main.pages.MCP.Manage.cancel',
        dm: 'Cancel',
      }),
      okText: $i18n.get({
        id: 'main.pages.MCP.Manage.startDeployment',
        dm: 'Start Deployment',
      }),
      onOk: async () => {
        await updateMcpServer({
          ...item,
          status: McpStatus.ENABLED,
        });
        message.success(
          $i18n.get({
            id: 'main.pages.MCP.Manage.startingSuccessful',
            dm: 'Started successfully',
          }),
        );
        fetchList();
      },
    });
  };

  const handleAction = (action?: string, item?: IMcpServer) => {
    if (!action || !item) return;
    switch (action) {
      case 'delete':
        handleConfirmDelete(item);
        break;
      case 'start':
        handleConfirmDeploy(item);
        break;
      case 'stop':
        handleConfirmStop(item);
        break;
      case 'edit':
        navigate(`/mcp/edit/${item.server_code}`);
        break;
      case 'detail':
        navigate(`/mcp/detail/${item.server_code}`);
        break;
      default:
        navigate(`/mcp/detail/${item.server_code}`);
    }
  };

  const handlePageChange = (page: number, pageSize: number) => {
    fetchList({ pageNo: page, pageSize });
  };

  return (
    <div className={styles.container}>
      {state?.list?.length > 0 && rightPortal(<CreateMcpBtn />)}
      <CardList
        loading={state.loading}
        pagination={{
          current: state.pageNo,
          total: state.total,
          pageSize: state.pageSize,
          onChange: handlePageChange,
        }}
        emptyAction={<CreateMcpBtn />}
      >
        {state.list.map((item: IMcpServer) => (
          <McpCard key={item.server_code} data={item} onClick={handleAction} />
        ))}
      </CardList>
    </div>
  );
}
