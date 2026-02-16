import ProCard from '@/components/Card/ProCard';
import $i18n from '@/i18n';
import { Button, Dropdown, IconButton, IconFont } from '@spark-ai/design';
import { Tag } from 'antd';
import classNames from 'classnames';
import dayjs from 'dayjs';
import React from 'react';
import { history } from 'umi';
import styles from './index.module.less';

/**
 * Map numeric/string status to display label and color
 */
const getStatusDisplay = (status?: number | string): { label: string; color: string } => {
  // CommonStatus enum: 0=DISABLED, 1=ENABLED (default), 2=PROCESSING
  switch (Number(status)) {
    case 0:
      return { label: 'Disabled', color: 'default' };
    case 1:
      return { label: 'Active', color: 'green' };
    case 2:
      return { label: 'Processing', color: 'blue' };
    default:
      return { label: 'Active', color: 'green' };
  }
};

/**
 * Knowledge base list component props interface
 */
interface KnowledgeCardProps {
  /**
   * Loading state
   */
  loading?: boolean;
  /**
   * Custom class name for overriding default styles
   */
  className?: string;
  /**
   * Knowledge base ID
   */
  kb_id: string;
  /**
   * Knowledge base name
   */
  name: string;
  /**
   * Knowledge base description
   */
  description: string;
  /**
   * Update time
   */
  gmt_modified: string;
  /**
   * Number of documents
   */
  total_docs: number;
  /**
   * Status of the knowledge base
   */
  status?: number | string;
  /**
   * Click action handler
   */
  handleClickAction?: (key: string, id: string) => void;
}

const KnowledgeCard: React.FC<KnowledgeCardProps> = ({
  name,
  kb_id,
  description,
  gmt_modified,
  total_docs,
  status,
  handleClickAction,
}) => {
  const statusDisplay = getStatusDisplay(status);
  return (
    <ProCard
      title={name}
      logo={<img className={styles['logo']} src={'/images/knowledge.svg'} />}
      statusNode={
        <Tag color={statusDisplay.color} style={{ marginLeft: 8 }}>
          {statusDisplay.label}
        </Tag>
      }
      info={[
        {
          label: $i18n.get({
            id: 'main.pages.Knowledge.List.components.Card.index.knowledgeBaseDescription',
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
                  id: 'main.pages.Knowledge.List.components.Card.index.nullName',
                  dm: 'No description',
                })}
            </div>
          ),
        },
        {
          label: $i18n.get({
            id: 'main.pages.Knowledge.List.components.Card.index.knowledgeBaseId',
            dm: 'ID',
          }),
          content: kb_id,
        },
      ]}
      footerDescNode={
        <div className={styles['card-footer']}>
          <div className={styles['update-time']}>
            {$i18n.get({
              id: 'main.pages.Knowledge.List.components.Card.index.updatedAt',
              dm: 'Updated at ',
            })}
            {dayjs(gmt_modified).format('YYYY-MM-DD HH:mm:ss')}
          </div>
          <div className={styles['document-count']}>
            <div className={styles['count-wrapper']}>
              <IconFont
                type="spark-document-line"
                className={styles['doc-icon']}
              />

              <div className={styles['count']}>{total_docs}</div>
            </div>
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
              history.push(`/knowledge/edit/${kb_id}`);
            }}
          >
            {$i18n.get({
              id: 'main.pages.Knowledge.List.components.Card.index.edit',
              dm: 'Edit',
            })}
          </Button>
          <Button
            type="default"
            className={styles['operate-button']}
            onClick={(e) => {
              e.stopPropagation();
              e.preventDefault();
              history.push(`/knowledge/test/${kb_id}`);
            }}
          >
            {$i18n.get({
              id: 'main.pages.Knowledge.List.components.Card.index.hitTest',
              dm: 'Hit Test',
            })}
          </Button>
          <div onClick={(e) => e.stopPropagation()}>
            <Dropdown
              getPopupContainer={(ele) => ele}
              menu={{
                items: [
                  {
                    danger: true,
                    label: $i18n.get({
                      id: 'main.pages.Knowledge.List.components.Card.index.delete',
                      dm: 'Delete',
                    }),
                    key: 'delete',
                    onClick: () =>
                      handleClickAction && handleClickAction('delete', kb_id),
                  },
                ],
              }}
            >
              <IconButton shape="default" icon="spark-more-line" />
            </Dropdown>
          </div>
        </>
      }
      onClick={() => history.push(`/knowledge/${kb_id}`)}
      className={styles['knowledge-card']}
    />
  );
};

export default KnowledgeCard;
