import InnerLayout from '@/components/InnerLayout';
import $i18n from '@/i18n';
import { IAppType } from '@/services/appComponent';
import { compact } from 'lodash-es';
import { history, useParams } from 'umi';
import AppComponent from './AppComponent';
import PluginList from './Plugin/List';

const tabs = compact([
  process.env.BACK_END !== 'python' && {
    label: $i18n.get({
      id: 'main.pages.Component.index.plugin',
      dm: 'Plugins',
    }),
    key: 'plugin',
    children: <PluginList />,
  },
  {
    label: $i18n.get({
      id: 'main.pages.Component.index.workflow',
      dm: 'Workflows',
    }),
    key: 'flow',
    children: <AppComponent type={IAppType.WORKFLOW} />,
  },
  {
    label: $i18n.get({
      id: 'main.pages.Component.index.intelligentAgent',
      dm: 'Agents',
    }),
    key: 'agent',
    children: <AppComponent type={IAppType.AGENT} />,
  },
]);

export default function () {
  const params = useParams<{ tab: string }>();

  return (
    <InnerLayout
      activeTab={params.tab}
      tabs={tabs}
      onTabChange={(tab) => {
        history.push('/component/' + tab);
      }}
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
            id: 'main.pages.Component.index.componentManagement',
            dm: 'Component Management',
          }),
        },
      ]}
    />
  );
}
