import React, { useEffect, useState } from 'react';
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

// Editable metadata fields (non-system, non-ACL)
const EDITABLE_FIELDS = [
  { key: 'file_title', label: 'Title' },
  { key: 'cm:title', label: 'CM Title' },
  { key: 'file_name', label: 'File Name' },
  { key: 'file_path', label: 'File Path' },
  { key: 'file_type', label: 'MIME Type' },
  { key: 'cmis:name', label: 'CMIS Name' },
  { key: 'cmis:createdBy', label: 'Created By' },
];

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

  useEffect(() => {
    if (visible && syncId && docId) {
      setLoading(true);
      getDocumentDetail(syncId, docId)
        .then((data: any) => {
          setOriginalDoc(data || {});
          const formValues: Record<string, any> = {};
          EDITABLE_FIELDS.forEach((f) => {
            const val = data?.[f.key];
            formValues[f.key] = val !== null && val !== undefined ? String(val) : '';
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
    EDITABLE_FIELDS.forEach((f) => {
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
          {EDITABLE_FIELDS.map((field) => (
            <Form.Item key={field.key} name={field.key} label={field.label}>
              <Input placeholder={`Enter ${field.label.toLowerCase()}`} />
            </Form.Item>
          ))}
        </Form>
      </Spin>
    </Drawer>
  );
};

export default MetadataEditDrawer;
