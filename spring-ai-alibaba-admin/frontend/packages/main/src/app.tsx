import '../tailwind.css';
import '@src/legacy/styles/tailwind.css';
import '@src/legacy/styles/index.css';
import $i18n from '@/i18n';
import { matchRoutes } from 'umi';

console.log(
  // @ts-ignore
  `%cBUILD_ID: ${BUILD_ID}`,
  'color: #fff; background: #615ced; font-size: 10px;border-radius:6px;padding:2px 4px;',
);

// Initialize window.g_config
// @ts-ignore
window.g_config = {
  user: {},
  config: {},
};

// @ts-ignore
export function onRouteChange({ clientRoutes, location }) {
  const route = matchRoutes(clientRoutes, location.pathname)?.pop()?.route;

  const firstLevelRouteMaps = {
    '/app': $i18n.get({
      id: 'main.layouts.MenuList.application',
      dm: 'Applications',
    }),
    '/mcp': 'MCP',
    '/component': $i18n.get({
      id: 'main.pages.Component.AppComponent.index.component',
      dm: 'Components',
    }),
    '/knowledge': $i18n.get({
      id: 'main.pages.Knowledge.Test.index.knowledgeBase',
      dm: 'Knowledge Base',
    }),
    '/setting': $i18n.get({
      id: 'main.pages.Setting.ModelService.Detail.setting',
      dm: 'Settings',
    }),
    '/login': $i18n.get({
      id: 'main.pages.Login.components.Register.index.login',
      dm: 'Login',
    }),
    '/debug': $i18n.get({
      id: 'main.pages.Debug.index.title',
      dm: 'Agent Chat UI',
    }),
    '/dify': $i18n.get({
      id: 'main.pages.Dify.index.title',
      dm: 'Dify Converter',
    }),
  };

  Object.entries(firstLevelRouteMaps).some((item) => {
    if (route?.path?.startsWith(item[0])) {
      document.title = `SAA - ${item[1]}`;
      return true;
    } else {
      return false;
    }
  });
}
