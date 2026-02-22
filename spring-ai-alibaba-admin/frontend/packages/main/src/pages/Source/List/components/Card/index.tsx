import ProCard from '@/components/Card/ProCard';
import $i18n from '@/i18n';
import { Button, Dropdown, IconButton, IconFont } from '@spark-ai/design';
import classNames from 'classnames';
import dayjs from 'dayjs';
import React from 'react';
import { history } from 'umi';
import styles from './index.module.less';

interface SourceCardProps {
  source_id: string;
  name: string;
  description: string;
  connector_type: string;
  status: number;
  test_result: string;
  mcf_job_status: string;
  docs_total: number;
  docs_processed: number;
  docs_failed: number;
  last_sync_time: string;
  gmt_modified: string;
  handleClickAction?: (key: string, id: string) => void;
}

const getStatusInfo = (jobStatus: string) => {
  switch (jobStatus) {
    case 'running':
    case 'starting':
    case 'active':
    case 'active_paused':
      return { className: styles['status-running'], label: 'Syncing' };
    case 'done':
    case 'completed':
      return { className: styles['status-done'], label: 'Synced' };
    case 'error':
    case 'aborting':
      return { className: styles['status-error'], label: 'Error' };
    case 'paused':
      return { className: styles['status-idle'], label: 'Paused' };
    default:
      return { className: styles['status-done'], label: 'Connected' };
  }
};

const SourceCard: React.FC<SourceCardProps> = ({
  source_id,
  name,
  description,
  connector_type,
  mcf_job_status,
  docs_total,
  docs_processed,
  gmt_modified,
  handleClickAction,
}) => {
  const statusInfo = getStatusInfo(mcf_job_status);
  const progress = docs_total > 0 ? Math.round((docs_processed / docs_total) * 100) : 0;

  return (
    <ProCard
      title={name}
      logo={
        <IconFont
          type="spark-cloud-line"
          className={styles['logo']}
          style={{ fontSize: 32, color: 'var(--ag-ant-color-primary)' }}
        />
      }
      statusNode={
        <span className={styles['connector-badge']}>{connector_type}</span>
      }
      info={[
        {
          label: $i18n.get({
            id: 'main.pages.Source.List.components.Card.description',
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
                  id: 'main.pages.Source.List.components.Card.noDescription',
                  dm: 'No description',
                })}
            </div>
          ),
        },
      ]}
      footerDescNode={
        <div className={styles['card-footer']}>
          <div className={styles['sync-status']}>
            <span className={classNames(styles['status-dot'], statusInfo.className)} />
            <span className={styles['status-text']}>{statusInfo.label}</span>
            {docs_total > 0 && (
              <span className={styles['count']}>
                {docs_processed}/{docs_total}
              </span>
            )}
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
              handleClickAction && handleClickAction('test', source_id);
            }}
          >
            {$i18n.get({
              id: 'main.pages.Source.List.components.Card.testConnection',
              dm: 'Test',
            })}
          </Button>

          <Button
            className={styles['operate-button']}
            onClick={(e) => {
              e.stopPropagation();
              e.preventDefault();
              handleClickAction && handleClickAction('edit', source_id);
            }}
          >
            {$i18n.get({
              id: 'main.pages.Source.List.components.Card.editConfig',
              dm: 'Edit Config',
            })}
          </Button>
          <div onClick={(e) => { e.stopPropagation(); e.preventDefault(); }}>
            <Dropdown
              getPopupContainer={(ele) => ele}
              menu={{
                items: [
                  {
                    label: $i18n.get({
                      id: 'main.pages.Source.List.components.Card.copy',
                      dm: 'Copy',
                    }),
                    key: 'copy',
                    onClick: (e: any) => {
                      e.domEvent?.stopPropagation();
                      handleClickAction && handleClickAction('copy', source_id);
                    },
                  },
                  {
                    danger: true,
                    label: $i18n.get({
                      id: 'main.pages.Source.List.components.Card.delete',
                      dm: 'Delete',
                    }),
                    key: 'delete',
                    onClick: (e: any) => {
                      e.domEvent?.stopPropagation();
                      handleClickAction && handleClickAction('delete', source_id);
                    },
                  },
                ],
              }}
            >
              <IconButton shape="default" icon="spark-more-line" />
            </Dropdown>
          </div>
        </>
      }
      onClick={() => history.push(`/source/${source_id}`)}
      className={styles['source-card']}
    />
  );
};

export default SourceCard;
