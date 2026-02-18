import React, { useEffect, useState, useMemo } from 'react';
import { Drawer, Spin, Form, Input } from 'antd';
import { Button, message } from '@spark-ai/design';
import { getDocumentDetail, updateDocumentMetadata } from '@/services/knowledge';
import $i18n from '@/i18n';
import styles from './index.module.less';

interface MetadataEditDrawerProps {
  visible: boolean;
  syncId: string;
  docId: string;
  docName: string;
  onClose: () => void;
  onSaved?: () => void;
}

// Fields that should NOT be editable (system/internal/ACL fields)
const NON_EDITABLE_KEYS = new Set([
  'content', // document body
  'allow_token_document', 'deny_token_document',
  'allow_token_parent', 'deny_token_parent',
  'allow_token_share', 'deny_token_share',
  'authorities',
]);

const MetadataEditDrawer: React.FC<MetadataEditDrawerProps> = ({
  visible,
  syncId,
  docId,
  docName,
  onClose,
  onSaved,
}) => {
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  const [originalDoc, setOriginalDoc] = useState<Record<string, any>>({});

  // Derive editable fields dynamically from the document data
  const editableFields = useMemo(() => {
    return Object.keys(originalDoc)
      .filter((k) => !NON_EDITABLE_KEYS.has(k))
      .sort()
      .map((k) => ({ key: k, label: k }));
  }, [originalDoc]);

  useEffect(() => {
    if (visible && syncId && docId) {
      setLoading(true);
      getDocumentDetail(syncId, docId)
        .then((data: any) => {
          setOriginalDoc(data || {});
          const formValues: Record<string, any> = {};
          Object.keys(data || {}).forEach((k) => {
            if (!NON_EDITABLE_KEYS.has(k)) {
              const val = data[k];
              formValues[k] = val !== null && val !== undefined ? String(val) : '';
            }
          });
          form.setFieldsValue(formValues);
        })
        .catch(() => {
          message.error('Failed to load document metadata');
        })
        .finally(() => {
          setLoading(false);
        });
    }
  }, [visible, syncId, docId]);

  const handleSave = async () => {
    const values = form.getFieldsValue();
    // Only send changed fields
    const changed: Record<string, any> = {};
    editableFields.forEach((f) => {
      const newVal = values[f.key] ?? '';
      const oldVal = originalDoc[f.key] !== null && originalDoc[f.key] !== undefined
        ? String(originalDoc[f.key]) : '';
      if (newVal !== oldVal) {
        changed[f.key] = newVal;
      }
    });

    if (Object.keys(changed).length === 0) {
      message.info('No changes to save');
      return;
    }

    setSaving(true);
    try {
      await updateDocumentMetadata(syncId, docId, changed);
      message.success('Metadata updated successfully');
      onSaved?.();
      onClose();
    } catch {
      message.error('Failed to update metadata');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Drawer
      open={visible}
      title={`Edit Metadata: ${docName}`}
      onClose={onClose}
      width={520}
      footer={
        <div className={styles.footer}>
          <Button onClick={onClose}>
            {$i18n.get({ id: 'metadata.cancel', dm: 'Cancel' })}
          </Button>
          <Button type="primary" onClick={handleSave} loading={saving}>
            {$i18n.get({ id: 'metadata.save', dm: 'Save' })}
          </Button>
        </div>
      }
    >
      <Spin spinning={loading}>
        <Form form={form} layout="vertical">
          {editableFields.map((field) => (
            <Form.Item key={field.key} name={field.key} label={field.label}>
              <Input placeholder={`Enter ${field.label}`} />
            </Form.Item>
          ))}
        </Form>
      </Spin>
    </Drawer>
  );
};

export default MetadataEditDrawer;
