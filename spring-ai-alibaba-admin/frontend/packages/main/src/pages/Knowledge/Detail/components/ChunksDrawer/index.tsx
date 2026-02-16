import React, { useEffect, useState, useCallback } from 'react';
import { Drawer, Spin, List, Pagination, Input, Card, Tag } from 'antd';
import { Button, message } from '@spark-ai/design';
import { getDocumentChunks, updateChunkContent } from '@/services/knowledge';
import $i18n from '@/i18n';
import styles from './index.module.less';

const { TextArea } = Input;

interface ChunkRecord {
  chunk_id: string;
  content: string;
  chunk_index: number;
  parent_doc_id: string;
  parent_doc_name: string;
}

interface ChunksDrawerProps {
  visible: boolean;
  syncId: string;
  docId: string;
  docName: string;
  onClose: () => void;
}

const PAGE_SIZE = 10;

const ChunksDrawer: React.FC<ChunksDrawerProps> = ({
  visible,
  syncId,
  docId,
  docName,
  onClose,
}) => {
  const [loading, setLoading] = useState(false);
  const [chunks, setChunks] = useState<ChunkRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [editingChunkId, setEditingChunkId] = useState<string | null>(null);
  const [editContent, setEditContent] = useState('');
  const [saving, setSaving] = useState(false);

  const fetchChunks = useCallback((page: number) => {
    if (!syncId || !docId) return;
    setLoading(true);
    getDocumentChunks(syncId, docId, { current: page, size: PAGE_SIZE })
      .then((data: any) => {
        setChunks(data?.records || []);
        setTotal(data?.total || 0);
        setCurrent(data?.current || page);
      })
      .catch(() => {
        message.error('Failed to load chunks');
        setChunks([]);
        setTotal(0);
      })
      .finally(() => {
        setLoading(false);
      });
  }, [syncId, docId]);

  useEffect(() => {
    if (visible) {
      setCurrent(1);
      setEditingChunkId(null);
      fetchChunks(1);
    }
  }, [visible, fetchChunks]);

  const handlePageChange = (page: number) => {
    setEditingChunkId(null);
    fetchChunks(page);
  };

  const handleStartEdit = (chunk: ChunkRecord) => {
    setEditingChunkId(chunk.chunk_id);
    setEditContent(chunk.content);
  };

  const handleCancelEdit = () => {
    setEditingChunkId(null);
    setEditContent('');
  };

  const handleSaveChunk = async () => {
    if (!editingChunkId) return;
    setSaving(true);
    try {
      await updateChunkContent(syncId, editingChunkId, editContent);
      message.success('Chunk updated successfully');
      setEditingChunkId(null);
      // Refresh current page to show updated content
      fetchChunks(current);
    } catch {
      message.error('Failed to update chunk');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Drawer
      open={visible}
      title={`Chunks: ${docName}`}
      onClose={onClose}
      width={640}
      footer={
        <div className={styles.footer}>
          <span className={styles.totalInfo}>
            {total} chunk{total !== 1 ? 's' : ''} total
          </span>
          <Button onClick={onClose}>
            {$i18n.get({ id: 'chunks.close', dm: 'Close' })}
          </Button>
        </div>
      }
    >
      <Spin spinning={loading}>
        {chunks.length === 0 && !loading ? (
          <div className={styles.emptyContent}>
            No chunks found for this document. Make sure RAG processing has completed.
          </div>
        ) : (
          <>
            <List
              dataSource={chunks}
              renderItem={(chunk) => (
                <Card
                  key={chunk.chunk_id}
                  size="small"
                  className={styles.chunkCard}
                  title={
                    <div className={styles.chunkHeader}>
                      <Tag color="blue">#{chunk.chunk_index ?? '—'}</Tag>
                      <span className={styles.chunkId}>{chunk.chunk_id}</span>
                    </div>
                  }
                  extra={
                    editingChunkId === chunk.chunk_id ? (
                      <div className={styles.editActions}>
                        <Button size="small" onClick={handleCancelEdit}>
                          Cancel
                        </Button>
                        <Button
                          size="small"
                          type="primary"
                          onClick={handleSaveChunk}
                          loading={saving}
                        >
                          Save
                        </Button>
                      </div>
                    ) : (
                      <Button
                        size="small"
                        type="link"
                        onClick={() => handleStartEdit(chunk)}
                      >
                        Edit
                      </Button>
                    )
                  }
                >
                  {editingChunkId === chunk.chunk_id ? (
                    <div>
                      <TextArea
                        value={editContent}
                        onChange={(e) => setEditContent(e.target.value)}
                        autoSize={{ minRows: 3, maxRows: 12 }}
                        className={styles.editTextArea}
                      />
                      <div className={styles.charCount}>
                        {editContent.length} characters
                      </div>
                    </div>
                  ) : (
                    <div className={styles.chunkContent}>
                      {chunk.content || '(empty)'}
                    </div>
                  )}
                </Card>
              )}
            />
            {total > PAGE_SIZE && (
              <div className={styles.pagination}>
                <Pagination
                  current={current}
                  total={total}
                  pageSize={PAGE_SIZE}
                  onChange={handlePageChange}
                  showSizeChanger={false}
                  size="small"
                />
              </div>
            )}
          </>
        )}
      </Spin>
    </Drawer>
  );
};

export default ChunksDrawer;
