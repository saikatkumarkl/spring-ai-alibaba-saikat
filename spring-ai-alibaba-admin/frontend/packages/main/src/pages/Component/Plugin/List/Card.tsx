import ProCard from '@/components/Card/ProCard';
import $i18n from '@/i18n';
import { removePlugin } from '@/services/plugin';
import { Plugin } from '@/types/plugin';
import { AlertDialog, IconButton, message } from '@spark-ai/design';
import { Button, Dropdown } from 'antd';
import dayjs from 'dayjs';
import { history } from 'umi';
import styles from './index.module.less';
export default function PluginCard(props: Plugin & { reload: () => void }) {
  return (
    <ProCard
      title={props.name}
      className={styles['card']}
      info={[
        {
          label: $i18n.get({
            id: 'main.pages.Component.Plugin.List.Card.pluginDescription',
            dm: 'Description',
          }),
          content: props.description,
        },
        {
          label: $i18n.get({
            id: 'main.pages.Component.Plugin.List.Card.pluginId',
            dm: 'ID',
          }),
          content: props.plugin_id || '',
        },
      ]}
      logo={<img className={styles['logo']} src={'/images/plugin.svg'} />}
      onClick={() => {}}
      footerDescNode={
        <div className={styles['update-time']}>
          {$i18n.get({
            id: 'main.pages.Component.Plugin.List.Card.updatedAt',
            dm: 'Updated at ',
          })}
          {dayjs(props.gmt_modified).format('YYYY-MM-DD HH:mm:ss')}
        </div>
      }
      footerOperateNode={
        <>
          <Button
            type="primary"
            className="flex-1"
            onClick={() => {
              history.push(`/component/plugin/${props.plugin_id}/tools`);
            }}
          >
            {$i18n.get({
              id: 'main.pages.Component.Plugin.List.Card.viewTools',
              dm: 'View Tools',
            })}
          </Button>
          <Button
            className="flex-1"
            onClick={() => {
              history.push(`/component/plugin/${props.plugin_id}`);
            }}
          >
            {$i18n.get({
              id: 'main.pages.Component.Plugin.List.Card.edit',
              dm: 'Edit',
            })}
          </Button>

          <Dropdown
            getPopupContainer={(ele) => ele}
            menu={{
              onClick: () => {
                AlertDialog.warning({
                  title: $i18n.get({
                    id: 'main.pages.Component.Plugin.List.Card.deletePlugin',
                    dm: 'Delete Plugin',
                  }),
                  children: $i18n.get({
                    id: 'main.pages.Component.Plugin.List.Card.confirmDeletePlugin',
                    dm: 'Are you sure you want to delete this plugin?',
                  }),
                  onOk: () => {
                    removePlugin(props.plugin_id as string).then(() => {
                      message.success(
                        $i18n.get({
                          id: 'main.pages.Component.Plugin.List.Card.successDelete',
                          dm: 'Deleted successfully',
                        }),
                      );
                      props.reload();
                    });
                  },
                });
              },
              items: [
                {
                  label: $i18n.get({
                    id: 'main.pages.Component.Plugin.List.Card.delete',
                    dm: 'Delete',
                  }),
                  key: 'delete',
                  danger: true,
                },
              ],
            }}
          >
            <IconButton shape="default" icon="spark-more-line" />
          </Dropdown>
        </>
      }
    ></ProCard>
  );
}
