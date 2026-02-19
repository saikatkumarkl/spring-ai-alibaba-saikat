import React, { useState, useMemo, useEffect } from 'react';
import { useLocation, useNavigate } from 'umi';
import { Layout as AntLayout, Menu } from 'antd';
import {
  AppstoreOutlined,
  BulbOutlined,
  ExperimentOutlined,
  LineChartOutlined,
  UnorderedListOutlined,
  PlayCircleOutlined,
  BarChartOutlined,
  NodeIndexOutlined,
  SettingOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  ApiOutlined,
  DatabaseOutlined,
  ToolOutlined,
  SwapOutlined,
  FileSearchOutlined,
  CloudServerOutlined,
  HddOutlined,
} from '@ant-design/icons';
import $i18n from '@/i18n';
import Header from './Header';
import styles from './index.module.less';
import LangSelect from './LangSelect';
import LoginProvider from './LoginProvider';
import SettingDropdown from './SettingDropdown';
import ThemeSelect from './ThemeSelect';
import UserAccountModal from '@/components/UserAccountModal';
import PureLayout from './Pure';
import { ModelsContext } from '@/legacy/context/models';
import PromptAPI from '@/legacy/services';

const { Sider, Content } = AntLayout;

// Get the menu item key that should be highlighted
const getSelectedMenuKey = (pathname: string): string => {
  // App-related pages
  if (pathname.startsWith('/app')) {
    return '/app';
  }

  // MCP-related pages
  if (pathname.startsWith('/mcp')) {
    return '/mcp';
  }

  // Component-related pages
  if (pathname.startsWith('/component')) {
    return '/component';
  }

  // Knowledge base related pages
  if (pathname.startsWith('/knowledge')) {
    return '/knowledge';
  }

  // Source management pages
  if (pathname.startsWith('/source')) {
    return '/source';
  }

  // Destination management pages
  if (pathname.startsWith('/destination')) {
    return '/destination';
  }

  // Audit Log page (must check before generic /setting)
  if (pathname.startsWith('/setting/auditLog')) {
    return '/setting/auditLog';
  }

  // Settings-related pages
  if (pathname.startsWith('/setting')) {
    return '/setting';
  }

  // Debug pages
  if (pathname.startsWith('/debug')) {
    return '/debug';
  }

  // Dify conversion pages
  if (pathname.startsWith('/dify')) {
    return '/dify';
  }

  // Agent Schema pages
  if (pathname.startsWith('/agent-schema')) {
    return '/agent-schema';
  }

  // Dataset-related pages
  if (pathname.startsWith('/admin/evaluation/gather')) {
    return '/admin/evaluation/gather';
  }

  // Evaluator-related pages
  if (pathname.startsWith('/admin/evaluation/evaluator') || pathname === '/admin/evaluation/debug') {
    return '/admin/evaluation/evaluator';
  }

  // Experiment-related pages
  if (pathname.startsWith('/admin/evaluation/experiment')) {
    return '/admin/evaluation/experiment';
  }

  // Prompt-related pages
  if (
    pathname.startsWith('/admin/prompt') ||
    pathname === '/admin/prompts' ||
    pathname === '/admin/playground' ||
    pathname === '/admin/version-history'
  ) {
    if (pathname === '/admin/playground') {
      return '/admin/playground';
    }
    return '/admin/prompts';
  }

  // Tracing pages
  if (pathname.startsWith('/admin/tracing')) {
    return '/admin/tracing';
  }

  // Default case, return current path directly
  return pathname;
};

export default function SideMenuLayout({ children }: { children: React.ReactNode }) {
  const location = useLocation();
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(false);
  const [models, setModels] = useState<any[]>([]);
  const [modelNameMap, setModelNameMap] = useState<Record<number, string>>({});

  // Load model list (for legacy pages)
  useEffect(() => {
    PromptAPI.getModels()
      .then((res) => {
        const nameMap = res.data.pageItems.reduce((acc: Record<number, string>, item: any) => {
          acc[item.id] = item.name;
          return acc;
        }, {});
        setModelNameMap(nameMap);
        setModels(res.data.pageItems);
      })
      .catch((err) => {
        console.error('Failed to load models:', err);
      });
  }, []);

  // Get the menu item key that should be highlighted
  const selectedKey = useMemo(() => getSelectedMenuKey(location.pathname), [location.pathname]);

  // Build menu items
  const menuItems = useMemo(
    () => [
      {
        key: 'studio',
        label: $i18n.get({
          id: 'main.layouts.SideMenu.studio',
          dm: ' Agent Builder',
        }),
        icon: <AppstoreOutlined />,
        children: [
          {
            key: '/app',
            label: $i18n.get({
              id: 'main.layouts.MenuList.application',
              dm: 'Applications',
            }),
            icon: <AppstoreOutlined />,
          },
          {
            key: '/mcp',
            label: 'MCP',
            icon: <ApiOutlined />,
          },
          {
            key: '/component',
            label: $i18n.get({
              id: 'main.pages.Component.AppComponent.index.component',
              dm: 'Components',
            }),
            icon: <ToolOutlined />,
          },
          {
            key: '/knowledge',
            label: $i18n.get({
              id: 'main.pages.Knowledge.Test.index.knowledgeBase',
              dm: 'Knowledge Base',
            }),
            icon: <DatabaseOutlined />,
          },
          {
            key: '/dify',
            label: $i18n.get({
              id: 'main.layouts.SideMenu.dify',
              dm: 'Dify To Graph',
            }),
            icon: <SwapOutlined />,
          },
        ],
      },
      {
        key: 'prompt',
        label: 'Prompt Engineering',
        icon: <BulbOutlined />,
        children: [
          {
            key: '/admin/prompts',
            label: 'Prompts',
            icon: <UnorderedListOutlined />,
          },
          {
            key: '/admin/playground',
            label: 'Playground',
            icon: <PlayCircleOutlined />,
          },
        ],
      },
      {
        key: 'evaluation',
        label: 'Evaluation',
        icon: <ExperimentOutlined />,
        children: [
          {
            key: '/admin/evaluation/gather',
            label: 'Dataset',
            icon: <UnorderedListOutlined />,
          },
          {
            key: '/admin/evaluation/evaluator',
            label: 'Evaluator',
            icon: <BarChartOutlined />,
          },
          {
            key: '/admin/evaluation/experiment',
            label: 'Experiment',
            icon: <ExperimentOutlined />,
          },
        ],
      },
      {
        key: 'observability',
        label: 'Observability',
        icon: <LineChartOutlined />,
        children: [
          {
            key: '/admin/tracing',
            label: 'Tracing',
            icon: <NodeIndexOutlined />,
          },
        ],
      },
      {
        key: '/source',
        label: $i18n.get({
          id: 'main.layouts.SideMenu.sources',
          dm: 'Sources',
        }),
        icon: <CloudServerOutlined />,
      },
      {
        key: '/destination',
        label: $i18n.get({
          id: 'main.layouts.SideMenu.destinations',
          dm: 'Destinations',
        }),
        icon: <HddOutlined />,
      },
      {
        key: '/setting',
        label: $i18n.get({
          id: 'main.pages.Setting.ModelService.Detail.setting',
          dm: 'Settings',
        }),
        icon: <SettingOutlined />,
      },
      {
        key: '/setting/auditLog',
        label: $i18n.get({
          id: 'main.pages.Setting.AuditLog.title',
          dm: 'Audit Log',
        }),
        icon: <FileSearchOutlined />,
      },
    ],
    [],
  );

  const handleMenuClick = ({ key }: { key: string }) => {
    navigate(key);
  };

  // Determine whether to hide sidebar (login page, home page, etc.)
  const shouldHideSidebar = ['/login', '/', '/home'].includes(location.pathname);

  if (shouldHideSidebar) {
    return (
      <PureLayout>
        <LoginProvider>
          <Header
            right={
              <>
                <ThemeSelect />
                <LangSelect />
                <SettingDropdown />
                <UserAccountModal avatarProps={{ className: styles.avatar }} />
              </>
            }
          />
          <div className={styles['body']}>{children}</div>
        </LoginProvider>
      </PureLayout>
    );
  }

  return (
    <PureLayout>
      <LoginProvider>
        <ModelsContext.Provider
          value={{
            models,
            modelNameMap,
            setModels,
          }}
        >
          <AntLayout className="h-screen">
            <Sider
              width={240}
              collapsedWidth={72}
              collapsed={collapsed}
              theme="light"
              style={{
                height: '100vh',
                position: 'fixed',
                left: 0,
                top: 0,
                bottom: 0,
                display: 'flex',
                flexDirection: 'column',
                borderRight: '0.5px solid var(--ag-ant-color-border-secondary)',
                backgroundColor: 'var(--ag-ant-color-bg-layout)',
                backdropFilter: 'saturate(180%) blur(20px)',
                WebkitBackdropFilter: 'saturate(180%) blur(20px)',
              }}
            >
              {/* Sidebar header */}
              <div style={{
                padding: collapsed ? '16px 12px 12px' : '16px 16px 12px',
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                minHeight: 48,
                flexShrink: 0,
              }}>
                <h1 style={{
                  margin: 0,
                  fontSize: collapsed ? 14 : 18,
                  fontWeight: 700,
                  color: 'var(--ag-ant-color-text)',
                  letterSpacing: '-0.02em',
                  whiteSpace: 'nowrap',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  fontFamily: '-apple-system, BlinkMacSystemFont, "SF Pro Display", system-ui, sans-serif',
                }}>
                  {collapsed ? 'CC' : 'Control Center'}
                </h1>
              </div>

              {/* Scrollable menu area */}
              <div style={{ flex: 1, overflowY: 'auto', overflowX: 'hidden', minHeight: 0 }}>
                <Menu
                  mode="inline"
                  selectedKeys={[selectedKey]}
                  defaultOpenKeys={collapsed ? [] : ['studio']}
                  items={menuItems}
                  onClick={handleMenuClick}
                  className="border-r-0"
                  inlineCollapsed={collapsed}
                  style={{
                    border: 'none',
                    backgroundColor: 'transparent',
                    padding: '0 4px',
                    fontSize: 13,
                    fontWeight: 500,
                  }}
                />
              </div>

              {/* Collapse toggle — fixed at bottom, never overlapped */}
              <div style={{
                flexShrink: 0,
                borderTop: '0.5px solid var(--ag-ant-color-border-secondary)',
                backgroundColor: 'var(--ag-ant-color-bg-layout)',
              }}>
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: collapsed ? 'center' : 'flex-start',
                    padding: collapsed ? '10px' : '10px 20px',
                    cursor: 'pointer',
                    transition: 'background-color 0.15s ease',
                  }}
                  onClick={() => setCollapsed(!collapsed)}
                  onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'var(--ag-ant-color-fill-secondary)'}
                  onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                >
                  {collapsed ? (
                    <MenuUnfoldOutlined style={{ color: 'var(--ag-ant-color-text-tertiary)', fontSize: 16 }} />
                  ) : (
                    <MenuFoldOutlined style={{ color: 'var(--ag-ant-color-text-tertiary)', fontSize: 16 }} />
                  )}
                  {!collapsed && <span style={{ marginLeft: 10, color: 'var(--ag-ant-color-text-tertiary)', fontSize: 12, fontWeight: 500 }}>Collapse</span>}
                </div>
              </div>
            </Sider>

            <AntLayout style={{ marginLeft: collapsed ? 72 : 240, transition: 'margin-left 0.25s cubic-bezier(0.4, 0, 0.2, 1)' }}>
              <Header
                right={
                  <>
                    <ThemeSelect />
                    <LangSelect />
                    <SettingDropdown />
                    <UserAccountModal avatarProps={{ className: styles.avatar }} />
                  </>
                }
              />
              <Content className="overflow-hidden">
                <div className="h-full overflow-y-auto" style={{ minHeight: 'calc(100vh - 52px)', backgroundColor: 'var(--ag-ant-color-bg-base)' }}>
                  {children}
                </div>
              </Content>
            </AntLayout>
          </AntLayout>
        </ModelsContext.Provider>
      </LoginProvider>
    </PureLayout>
  );
}

