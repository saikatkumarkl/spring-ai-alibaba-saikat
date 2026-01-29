import React, { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { Menu, Layout as AntLayout } from 'antd';
import {
  BulbOutlined,
  ExperimentOutlined,
  LineChartOutlined,
  UnorderedListOutlined,
  PlayCircleOutlined,
  BarChartOutlined,
  NodeIndexOutlined,
  SettingOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined
} from '@ant-design/icons';

const { Sider, Content } = AntLayout;

// Get the menu item key that should be highlighted
const getSelectedMenuKey = (pathname) => {
  // Remove /admin prefix for matching
  const path = pathname.replace(/^\/admin/, '') || '/';

  // Evaluation dataset related pages
  if (path.startsWith('/evaluation/gather')) {
    return '/admin/evaluation/gather';
  }

  // Evaluator related pages
  if (path.startsWith('/evaluation/evaluator') || path === '/evaluation/debug') {
    return '/admin/evaluation/evaluator';
  }

  // Experiment related pages
  if (path.startsWith('/evaluation/experiment')) {
    return '/admin/evaluation/experiment';
  }

  // Prompt related pages
  if (path.startsWith('/prompt') || path === '/prompts' || path === '/playground' || path === '/version-history') {
    // Return corresponding menu item based on specific path
    if (path === '/playground') {
      return '/admin/playground';
    }
    if (path === '/version-history') {
      return '/admin/prompts'; // Version history page belongs to Prompts menu
    }
    return '/admin/prompts'; // Default to Prompts menu
  }

  // Tracing page
  if (path.startsWith('/tracing')) {
    return '/admin/tracing';
  }

  // Default case, return current path
  return pathname;
};

const Layout = ({ children }) => {
  const navigate = useNavigate();
  const location = useLocation();
  const [collapsed, setCollapsed] = useState(false);

  // Get the menu item key that should be highlighted
  const selectedKey = getSelectedMenuKey(location.pathname);

  const menuItems = [
    {
      key: 'prompt',
      label: 'Prompt Engineering',
      icon: <BulbOutlined />,
      children: [
        {
          key: '/admin/prompts',
          label: 'Prompts',
          icon: <UnorderedListOutlined />
        },
        {
          key: '/admin/playground',
          label: 'Playground',
          icon: <PlayCircleOutlined />
        }
      ]
    },
    {
      key: 'evaluation',
      label: 'Evaluation',
      icon: <ExperimentOutlined />,
      children: [
        {
          key: '/admin/evaluation/gather',
          label: 'Evaluation Sets',
          icon: <UnorderedListOutlined />
        },
        {
          key: '/admin/evaluation/evaluator',
          label: 'Evaluators',
          icon: <BarChartOutlined />
        },
        {
          key: '/admin/evaluation/experiment',
          label: 'Experiments',
          icon: <ExperimentOutlined />
        }
      ]
    },
    {
      key: 'observability',
      label: 'Observability',
      icon: <LineChartOutlined />,
      children: [
        {
          key: '/admin/tracing',
          label: 'Tracing',
          icon: <NodeIndexOutlined />
        }
      ]
    }
  ];

  const handleMenuClick = ({ key }) => {
    navigate(key);
  };

  return (
    <AntLayout className="h-screen">
      <Sider
        width={256}
        collapsedWidth={80}
        collapsed={collapsed}
        theme="light"
        className="shadow-lg border-r border-gray-200"
      >
        <div className="p-6 border-b border-gray-200">
          <h1 className="text-xl font-bold text-gray-800 flex items-center whitespace-nowrap overflow-hidden">
            <SettingOutlined className="mr-1 text-blue-500" />
            {!collapsed && "SAA Admin"}
          </h1>
        </div>

        <Menu
          mode="inline"
          selectedKeys={[selectedKey]}
          defaultOpenKeys={collapsed ? [] : ['prompt', 'evaluation', 'observability']}
          items={menuItems}
          onClick={handleMenuClick}
          className="border-r-0 mt-6"
          inlineCollapsed={collapsed}
        />

        <div className="absolute bottom-0 left-0 right-0 border-t border-gray-200 bg-white">
          <div
            className="flex items-center justify-center p-4 cursor-pointer hover:bg-gray-50 transition-colors"
            onClick={() => setCollapsed(!collapsed)}
          >
            {collapsed ?
              <MenuUnfoldOutlined className="text-gray-600 text-lg" /> :
              <MenuFoldOutlined className="text-gray-600 text-lg" />
            }
            {!collapsed && <span className="ml-2 text-gray-600">Collapse Menu</span>}
          </div>
        </div>
      </Sider>

      <Content className="overflow-hidden">
        <div className="h-full overflow-y-auto bg-gray-50">
          {children}
        </div>
      </Content>
    </AntLayout>
  );
};

export default Layout;
