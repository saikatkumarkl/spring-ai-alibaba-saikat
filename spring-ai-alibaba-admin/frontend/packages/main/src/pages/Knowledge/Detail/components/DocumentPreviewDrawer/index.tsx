import React, { useEffect, useState, useMemo } from 'react';
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

// Fields to always show at the top of metadata (in this order)
const PRIORITY_METADATA_KEYS = [
  'cmis:name', 'cm:title', 'file_title', 'file_name', 'file_path',
  'file_type', 'file_size', 'cmis:contentStreamMimeType', 'cmis:contentStreamLength',
  'cmis:contentStreamFileName', 'cmis:objectId', 'cmis:objectTypeId',
  'cmis:baseTypeId', 'cmis:createdBy', 'cmis:lastModifiedBy',
  'cmis:creationDate', 'cmis:lastModificationDate',
  'created_at', 'updated_at',
];

// Fields to exclude from metadata tab (shown elsewhere or internal)
const EXCLUDED_KEYS = new Set([
  'content', // shown in Content tab
  'allow_token_document', 'deny_token_document', // shown in ACL tab
  'allow_token_parent', 'deny_token_parent',
  'allow_token_share', 'deny_token_share',
  'authorities', // shown in ACL tab
]);

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

  // Build metadata entries dynamically from all document fields
  const metadataEntries = useMemo(() => {
    const entries: { key: string; label: string; value: any }[] = [];
    const addedKeys = new Set<string>();

    // First, add priority keys in order (if they exist in the doc)
    for (const key of PRIORITY_METADATA_KEYS) {
      if (key in doc && !EXCLUDED_KEYS.has(key)) {
        entries.push({ key, label: key, value: doc[key] });
        addedKeys.add(key);
      }
    }

    // Then add all remaining keys alphabetically
    const remainingKeys = Object.keys(doc)
      .filter((k) => !addedKeys.has(k) && !EXCLUDED_KEYS.has(k))
      .sort();
    for (const key of remainingKeys) {
      entries.push({ key, label: key, value: doc[key] });
    }

    return entries;
  }, [doc]);

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
          {metadataEntries.map((entry) => (
            <Descriptions.Item key={entry.key} label={entry.label}>
              {formatValue(entry.value)}
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
