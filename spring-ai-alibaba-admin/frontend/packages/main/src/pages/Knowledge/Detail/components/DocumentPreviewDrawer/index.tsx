import React, { useEffect, useState } from 'react';
import { Drawer, Spin, Descriptions, Tabs } from 'antd';
import { Button, message } from '@spark-ai/design';
import { getDocumentDetail } from '@/services/knowledge';
import $i18n from '@/i18n';
import styles from './index.module.less';

interface DocumentPreviewDrawerProps {
  visible: boolean;
  syncId: string;
  docId: string;
  docName: string;
  onClose: () => void;
}

const METADATA_FIELDS = [
  { key: 'cmis:name', label: 'Name' },
  { key: 'cm:title', label: 'Title' },
  { key: 'file_path', label: 'Path' },
  { key: 'file_type', label: 'MIME Type' },
  { key: 'cmis:contentStreamMimeType', label: 'Content Type' },
  { key: 'file_size', label: 'Size' },
  { key: 'cmis:contentStreamLength', label: 'Stream Length' },
  { key: 'cmis:objectId', label: 'Object ID' },
  { key: 'cmis:createdBy', label: 'Created By' },
  { key: 'created_at', label: 'Created At' },
  { key: 'updated_at', label: 'Updated At' },
];

const ACL_FIELDS = [
  { key: 'allow_token_document', label: 'Allow (Document)' },
  { key: 'deny_token_document', label: 'Deny (Document)' },
  { key: 'allow_token_parent', label: 'Allow (Parent)' },
  { key: 'deny_token_parent', label: 'Deny (Parent)' },
];

const DocumentPreviewDrawer: React.FC<DocumentPreviewDrawerProps> = ({
  visible,
  syncId,
  docId,
  docName,
  onClose,
}) => {
  const [loading, setLoading] = useState(false);
  const [doc, setDoc] = useState<Record<string, any>>({});

  useEffect(() => {
    if (visible && syncId && docId) {
      setLoading(true);
      getDocumentDetail(syncId, docId)
        .then((data: any) => {
          setDoc(data || {});
        })
        .catch(() => {
          message.error('Failed to load document detail');
        })
        .finally(() => {
          setLoading(false);
        });
    }
  }, [visible, syncId, docId]);

  const formatValue = (val: any): string => {
    if (val === null || val === undefined) return '—';
    if (Array.isArray(val)) return val.join(', ');
    if (typeof val === 'number' && val > 1000) {
      // Format as file size if likely bytes
      if (val > 1024 * 1024) return `${(val / 1024 / 1024).toFixed(2)} MB`;
      if (val > 1024) return `${(val / 1024).toFixed(2)} KB`;
    }
    return String(val);
  };

  const content = doc.content || '';

  const tabItems = [
    {
      key: 'content',
      label: $i18n.get({ id: 'preview.tab.content', dm: 'Content' }),
      children: (
        <div className={styles.contentArea}>
          {content ? (
            <pre className={styles.contentPre}>{content}</pre>
          ) : (
            <div className={styles.emptyContent}>No content available</div>
          )}
        </div>
      ),
    },
    {
      key: 'metadata',
      label: $i18n.get({ id: 'preview.tab.metadata', dm: 'Metadata' }),
      children: (
        <Descriptions column={1} size="small" bordered>
          {METADATA_FIELDS.map((field) => (
            <Descriptions.Item key={field.key} label={field.label}>
              {formatValue(doc[field.key])}
            </Descriptions.Item>
          ))}
        </Descriptions>
      ),
    },
    {
      key: 'acl',
      label: $i18n.get({ id: 'preview.tab.acl', dm: 'ACL' }),
      children: (
        <Descriptions column={1} size="small" bordered>
          {ACL_FIELDS.map((field) => (
            <Descriptions.Item key={field.key} label={field.label}>
              {formatValue(doc[field.key])}
            </Descriptions.Item>
          ))}
        </Descriptions>
      ),
    },
  ];

  return (
    <Drawer
      open={visible}
      title={`Preview: ${docName}`}
      onClose={onClose}
      width={640}
      footer={
        <div className={styles.footer}>
          <span className={styles.charCount}>
            {content.length} characters
          </span>
          <Button onClick={onClose}>
            {$i18n.get({ id: 'preview.close', dm: 'Close' })}
          </Button>
        </div>
      }
    >
      <Spin spinning={loading}>
        <Tabs items={tabItems} defaultActiveKey="content" />
      </Spin>
    </Drawer>
  );
};

export default DocumentPreviewDrawer;
