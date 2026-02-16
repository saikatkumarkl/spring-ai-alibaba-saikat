import ProCard from '@/components/Card/ProCard';
import $i18n from '@/i18n';
import { Button, Dropdown, IconButton, IconFont } from '@spark-ai/design';
import classNames from 'classnames';
import dayjs from 'dayjs';
import React from 'react';
import { history } from 'umi';
import styles from './index.module.less';

interface DestinationCardProps {
  destination_id: string;
  name: string;
  description: string;
  provider_type: string;
  status: number;
  test_result: string;
  gmt_modified: string;
  handleClickAction?: (key: string, id: string) => void;
}

const DestinationCard: React.FC<DestinationCardProps> = ({
  destination_id,
  name,
  description,
  provider_type,
  status,
  test_result,
  gmt_modified,
  handleClickAction,
}) => {
  const isConnected = test_result && (test_result.toUpperCase() === 'PASS' || test_result.toLowerCase().includes('success'));

  return (
    <ProCard
      title={name}
      logo={
        <IconFont
          type="spark-database-line"
          className={styles['logo']}
          style={{ fontSize: 32, color: 'var(--ag-ant-color-primary)' }}
        />
      }
      statusNode={
        <span className={styles['provider-badge']}>{provider_type}</span>
      }
      info={[
        {
          label: $i18n.get({
            id: 'main.pages.Destination.List.Card.description',
            dm: 'Description',
          }),
          content: (
            <div
              className={classNames(styles['description'], {
                [styles['no-description']]: !description,
              })}
            >
              {description ||
                $i18n.get({
                  id: 'main.pages.Destination.List.Card.noDescription',
                  dm: 'No description',
                })}
            </div>
          ),
        },
      ]}
      footerDescNode={
        <div className={styles['card-footer']}>
          <div className={styles['connection-status']}>
            <span
              className={classNames(styles['status-dot'], {
                [styles['status-active']]: isConnected,
                [styles['status-inactive']]: !isConnected,
              })}
            />
            <span className={styles['status-text']}>
              {isConnected
                ? $i18n.get({ id: 'main.pages.Destination.List.Card.connected', dm: 'Connected' })
                : $i18n.get({ id: 'main.pages.Destination.List.Card.notTested', dm: 'Not Tested' })}
            </span>
          </div>
          <div className={styles['update-time']}>
            {dayjs(gmt_modified).format('YYYY-MM-DD HH:mm')}
          </div>
        </div>
      }
      footerOperateNode={
        <>
          <Button
            type="primary"
            className={styles['operate-button']}
            onClick={(e) => {
              e.stopPropagation();
              e.preventDefault();
              handleClickAction && handleClickAction('test', destination_id);
            }}
          >
            {$i18n.get({
              id: 'main.pages.Destination.List.Card.testConnection',
              dm: 'Test',
            })}
          </Button>
          <Dropdown
            getPopupContainer={(ele) => ele}
            menu={{
              items: [
                {
                  label: $i18n.get({
                    id: 'main.pages.Destination.List.Card.edit',
                    dm: 'Edit',
                  }),
                  key: 'edit',
                  onClick: (e: any) => {
                    e.domEvent?.stopPropagation();
                    handleClickAction && handleClickAction('edit', destination_id);
                  },
                },
                {
                  danger: true,
                  label: $i18n.get({
                    id: 'main.pages.Destination.List.Card.delete',
                    dm: 'Delete',
                  }),
                  key: 'delete',
                  onClick: (e: any) => {
                    e.domEvent?.stopPropagation();
                    handleClickAction && handleClickAction('delete', destination_id);
                  },
                },
              ],
            }}
          >
            <IconButton shape="default" icon="spark-more-line" />
          </Dropdown>
        </>
      }
      onClick={() => history.push(`/destination/${destination_id}`)}
      className={styles['destination-card']}
    />
  );
};

export default DestinationCard;
