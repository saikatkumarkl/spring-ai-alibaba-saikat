import ProCard from '@/components/Card/ProCard';
import $i18n from '@/i18n';
import { IProvider } from '@/types/modelService';
import { Button, Dropdown, IconButton, Tag } from '@spark-ai/design';
import dayjs from 'dayjs';
import { ProviderAvatar } from '../ProviderAvatar';
import styles from './index.module.less';
interface ModelServiceCardProps {
  service: IProvider;
  reachable?: boolean;
  onClick?: (action?: string, data?: IProvider) => void;
}

const ModelServiceCard = ({ service, reachable, onClick }: ModelServiceCardProps) => {
  // Determine status: Stopped (disabled), Online (enabled + reachable), Unreachable (enabled + not reachable)
  let color: string;
  let text: string;
  if (!service.enable) {
    color = 'error';
    text = $i18n.get({
      id: 'main.pages.Setting.ModelService.components.ModelServiceCard.index.stopped',
      dm: 'Stopped',
    });
  } else if (reachable === false) {
    color = 'warning';
    text = $i18n.get({
      id: 'main.pages.Setting.ModelService.components.ModelServiceCard.index.unreachable',
      dm: 'Unreachable',
    });
  } else {
    color = 'success';
    text = $i18n.get({
      id: 'main.pages.Setting.ModelService.components.ModelServiceCard.index.online',
      dm: 'Online',
    });
  }
  const updatedAt = service.gmt_modified
    ? dayjs(service.gmt_modified).format('YYYY-MM-DD HH:mm:ss')
    : '';

  const handleButtonClick = (action: string, e: React.MouseEvent) => {
    e.stopPropagation();
    onClick?.(action, service);
  };

  const handleDropdownClick = (info: { key: string }) => {
    onClick?.(info.key, service);
  };

  const renderActions = () => {
    const menuItems: {
      key: string;
      label: React.ReactNode;
      danger?: boolean;
    }[] = [];

    if (service.source !== 'preset') {
      menuItems.push({
        key: 'delete',
        label: $i18n.get({
          id: 'main.pages.Setting.ModelService.components.ModelServiceCard.index.delete',
          dm: 'Delete',
        }),
        danger: true,
      });
    }

    return (
      <>
        {service.enable ? (
          <Button
            className="flex-1"
            type="primary"
            onClick={(e) => handleButtonClick('stop', e)}
          >
            {$i18n.get({
              id: 'main.pages.Setting.ModelService.components.ModelServiceCard.index.stopService',
              dm: 'Stop Service',
            })}
          </Button>
        ) : (
          <Button
            className="flex-1"
            type="primary"
            onClick={(e) => handleButtonClick('start', e)}
          >
            {$i18n.get({
              id: 'main.pages.Setting.ModelService.components.ModelServiceCard.index.startService',
              dm: 'Start Service',
            })}
          </Button>
        )}
        <Button
          className="flex-1"
          type="default"
          onClick={(e) => handleButtonClick('edit', e)}
        >
          {$i18n.get({
            id: 'main.pages.Setting.ModelService.components.ModelServiceCard.index.editService',
            dm: 'Edit Service',
          })}
        </Button>
        {menuItems.length > 0 && (
          <Dropdown
            trigger={['click']}
            menu={{ items: menuItems, onClick: handleDropdownClick }}
          >
            <div onClick={(e) => e.stopPropagation()}>
              <IconButton shape="default" icon="spark-more-line" />
            </div>
          </Dropdown>
        )}
      </>
    );
  };

  const model_count = service.model_count?.toString() || '0';

  return (
    <ProCard
      title={service.name}
      logo={
        <ProviderAvatar
          provider={service}
          className={styles['provider-avatar']}
        />
      }
      statusNode={
        <div className={styles['status-tag']} data-color={color}>
          <span className={styles.dot}></span>
          <span>{text}</span>
        </div>
      }
      info={[
        {
          label: $i18n.get({
            id: 'main.pages.Setting.ModelService.components.ModelServiceCard.index.modelCount',
            dm: 'Model Count',
          }),
          content: $i18n.get(
            {
              id: 'main.pages.Setting.ModelService.components.ModelServiceCard.index.numberOfModels',
              dm: '{var1} models',
            },
            { var1: model_count },
          ),
        },
        {
          label: $i18n.get({
            id: 'main.pages.Setting.ModelService.components.ModelServiceCard.index.modelType',
            dm: 'Model Type',
          }),
          content: (
            <div className={styles['model-type']}>
              {service.supported_model_types?.map(
                (capability: string, index: number) => (
                  <Tag className={styles['type-tag']} key={index} color="mauve">
                    {capability}
                  </Tag>
                ),
              )}
            </div>
          ),
        },
      ]}
      footerDescNode={
        <div className={styles['footer-desc-node']}>
          {$i18n.get({
            id: 'main.pages.Setting.ModelService.components.ModelServiceCard.index.updatedAt',
            dm: 'Updated at ',
          })}
          {updatedAt}
        </div>
      }
      footerOperateNode={renderActions()}
      className={styles['service-card']}
      onClick={() => onClick?.('detail', service)}
    ></ProCard>
  );
};

export default ModelServiceCard;
