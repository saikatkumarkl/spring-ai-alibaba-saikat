import InnerLayout from '@/components/InnerLayout';
import $i18n from '@/i18n';
import {
  listKnowledgeSyncs,
  getKnowledgeSyncStatus,
  startKnowledgeSync,
  updateKnowledgeSyncCron,
  getKnowledgeDetail,
  syncKnowledgeDocuments,
  reindexKnowledgeRag,
  hardResetSync,
  stopSync,
} from '@/services/knowledge';
import { getSourceList } from '@/services/source';
import { getDestinationList } from '@/services/destination';
import {
  Button,
  IconFont,
  message,
  Input,
  Select,
  Tooltip,
} from '@spark-ai/design';
import {
  Progress,
  Tag,
  Descriptions,
  Spin,
  Alert,
  Modal as AntModal,
  Space,
} from 'antd';
import { useEffect, useState, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { CRON_PRESETS } from '../utils/constant';
import styles from './index.module.less';

interface SyncData {
  sync_id: string;
  status: string;
  index_name?: string;
  authority_index_name?: string;
  rag_index_name?: string;
  index_progress: number;
  rag_progress: number;
  overall_progress: number;
  total_docs: number;
  indexed_docs: number;
  rag_docs: number;
  authority_count: number;
  failed_docs: number;
  error_message?: string;
  last_sync_time?: string;
  source_id?: string;
  destination_id?: string;
  sync_cron?: string;
}

const STATUS_CONFIG: Record<string, { label: string; color: string; icon: string }> = {
  pending: { label: 'Pending', color: 'orange', icon: 'spark-time-line' },
  indexing: { label: 'Indexing Documents', color: 'blue', icon: 'spark-loading-line' },
  authority_syncing: { label: 'Syncing Authorities', color: 'blue', icon: 'spark-loading-line' },
  rag_processing: { label: 'RAG Processing', color: 'blue', icon: 'spark-loading-line' },
  completed: { label: 'Completed', color: 'green', icon: 'spark-checkCircle-line' },
  failed: { label: 'Failed', color: 'red', icon: 'spark-closeCircle-line' },
};

export default function SyncStatus() {
  const { kb_id } = useParams<{ kb_id: string }>();
  const navigate = useNavigate();
  const [kbName, setKbName] = useState('');
  const [syncData, setSyncData] = useState<SyncData | null>(null);
  const [syncMeta, setSyncMeta] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [polling, setPolling] = useState(false);
  const [sources, setSources] = useState<{ label: string; value: string }[]>([]);
  const [destinations, setDestinations] = useState<{ label: string; value: string }[]>([]);
  const [cronMode, setCronMode] = useState<string>('');
  const [editingCron, setEditingCron] = useState<string>('');
  const pollRef = useRef<NodeJS.Timeout | null>(null);

  // Fetch knowledge base name
  useEffect(() => {
    if (kb_id) {
      getKnowledgeDetail(kb_id).then((data) => setKbName(data.name)).catch(() => {});
    }
  }, [kb_id]);

  // Fetch sources & destinations for display labels
  useEffect(() => {
    getSourceList({ current: 1, size: 100 })
      .then((res) => {
        setSources(res.records.filter((s: any) => s.status >= 0).map((s: any) => ({ label: s.name, value: s.source_id })));
      })
      .catch(() => {});
    getDestinationList({ current: 1, size: 100 })
      .then((res) => {
        setDestinations(res.records.filter((d: any) => d.status >= 0).map((d: any) => ({ label: `${d.name} (${d.provider_type})`, value: d.destination_id })));
      })
      .catch(() => {});
  }, []);

  const fetchSyncData = useCallback(async () => {
    if (!kb_id) return;
    try {
      const syncs = await listKnowledgeSyncs(kb_id);
      if (syncs && syncs.length > 0) {
        const sync = syncs[0];
        setSyncMeta(sync);
        // Set cron editing state
        const cron = sync.sync_cron || '';
        const isPreset = CRON_PRESETS.find((p) => p.value === cron && p.value !== 'custom');
        setCronMode(isPreset ? cron : cron ? 'custom' : '');
        setEditingCron(cron);

        // Now fetch live status for progress
        try {
          const status = await getKnowledgeSyncStatus(sync.sync_id);
          setSyncData(status);
          return status;
        } catch {
          // If status endpoint fails, build data from meta
          setSyncData({
            sync_id: sync.sync_id,
            status: sync.status || 'pending',
            index_name: sync.index_name,
            authority_index_name: sync.authority_index_name,
            rag_index_name: sync.rag_index_name,
            index_progress: sync.index_progress || 0,
            rag_progress: sync.rag_progress || 0,
            overall_progress: ((sync.index_progress || 0) + (sync.rag_progress || 0)) / 2,
            total_docs: sync.total_docs || 0,
            indexed_docs: sync.indexed_docs || 0,
            rag_docs: sync.rag_docs || 0,
            authority_count: sync.authority_count || 0,
            failed_docs: sync.failed_docs || 0,
            error_message: sync.error_message,
            last_sync_time: sync.last_sync_time,
            source_id: sync.source_id,
            destination_id: sync.destination_id,
            sync_cron: sync.sync_cron,
          });
          return sync;
        }
      } else {
        setSyncData(null);
        setSyncMeta(null);
      }
    } catch {
      setSyncData(null);
      setSyncMeta(null);
    } finally {
      setLoading(false);
    }
  }, [kb_id]);

  // Initial fetch
  useEffect(() => {
    fetchSyncData();
  }, [fetchSyncData]);

  // Auto-polling when sync is in progress
  useEffect(() => {
    const isActive = syncData?.status === 'indexing' || syncData?.status === 'authority_syncing' || syncData?.status === 'rag_processing';
    setPolling(isActive);

    if (isActive) {
      pollRef.current = setInterval(() => {
        fetchSyncData();
      }, 3000);
    } else if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }

    return () => {
      if (pollRef.current) {
        clearInterval(pollRef.current);
      }
    };
  }, [syncData?.status, fetchSyncData]);

  const handleStartSync = () => {
    if (!syncData?.sync_id) return;
    AntModal.confirm({
      title: 'Start Sync',
      content: 'This will start indexing documents from the source system and then process RAG embeddings. Continue?',
      okText: 'Start Sync',
      onOk: () => {
        startKnowledgeSync(syncData.sync_id)
          .then(() => {
            message.success('Sync started');
            fetchSyncData();
          })
          .catch((err) => {
            const detail = err?.response?.data?.message || 'Failed to start sync';
            message.error(detail.length > 120 ? detail.substring(0, 120) + '...' : detail);
            fetchSyncData(); // Refresh to show error in the error section
          });
      },
    });
  };

  const handleSyncDocumentsOnly = () => {
    if (!syncData?.sync_id) return;
    AntModal.confirm({
      title: 'Sync Documents Only',
      content: 'This will start the ManifoldCF crawl to sync documents from the source system into OpenSearch. Continue?',
      okText: 'Sync Documents',
      onOk: () => {
        syncKnowledgeDocuments(syncData.sync_id)
          .then(() => {
            message.success('Document sync started');
            fetchSyncData();
          })
          .catch((err) => {
            const detail = err?.response?.data?.message || 'Failed to start document sync';
            message.error(detail.length > 120 ? detail.substring(0, 120) + '...' : detail);
            fetchSyncData();
          });
      },
    });
  };

  const handleReindexRagOnly = () => {
    if (!syncData?.sync_id) return;
    AntModal.confirm({
      title: 'Reindex RAG',
      content: 'This will regenerate vector embeddings from the OpenSearch document index for RAG retrieval. Continue?',
      okText: 'Reindex RAG',
      onOk: () => {
        reindexKnowledgeRag(syncData.sync_id)
          .then(() => {
            message.success('RAG reindex started');
            fetchSyncData();
          })
          .catch((err) => {
            const detail = err?.response?.data?.message || 'Failed to start RAG reindex';
            message.error(detail.length > 120 ? detail.substring(0, 120) + '...' : detail);
            fetchSyncData();
          });
      },
    });
  };

  const handleStopSync = () => {
    if (!syncData?.sync_id) return;
    AntModal.confirm({
      title: 'Stop Sync',
      content: 'This will abort the running MCF crawl job and mark the sync as failed. You can restart it later.',
      okText: 'Stop Sync',
      okButtonProps: { danger: true },
      onOk: () => {
        stopSync(syncData.sync_id)
          .then(() => {
            message.success('Sync stopped');
            fetchSyncData();
          })
          .catch(() => message.error('Failed to stop sync'));
      },
    });
  };

  const handleHardReset = () => {
    if (!syncData?.sync_id) return;
    AntModal.confirm({
      title: 'Hard Reset',
      content: 'This will delete ALL indices (document, authority, RAG) and reset the sync to pending. All indexed data will be lost. This cannot be undone.',
      okText: 'Hard Reset',
      okButtonProps: { danger: true },
      onOk: () => {
        hardResetSync(syncData.sync_id)
          .then(() => {
            message.success('Hard reset completed. Ready for fresh sync.');
            fetchSyncData();
          })
          .catch(() => message.error('Failed to hard reset'));
      },
    });
  };

  const getStepState = (phase: 'index' | 'authority' | 'rag'): 'waiting' | 'active' | 'done' | 'failed' => {
    if (!syncData) return 'waiting';
    const { status } = syncData;

    if (phase === 'index') {
      if (status === 'indexing') return 'active';
      if (['authority_syncing', 'rag_processing', 'completed'].includes(status)) return 'done';
      if (status === 'failed' && syncData.index_progress >= 100) return 'done';
      if (status === 'failed') return 'failed';
      return 'waiting';
    }

    if (phase === 'authority') {
      if (status === 'authority_syncing') return 'active';
      if (['rag_processing', 'completed'].includes(status)) return 'done';
      if (status === 'failed' && syncData.index_progress >= 100 && (syncData.authority_count || 0) > 0) return 'done';
      if (status === 'failed' && syncData.index_progress >= 100) return 'failed';
      return 'waiting';
    }

    // phase === 'rag'
    if (status === 'rag_processing') return 'active';
    if (status === 'completed') return 'done';
    if (status === 'failed' && syncData.rag_progress > 0) return 'failed';
    if (status === 'failed' && ['rag_processing'].includes(status)) return 'failed';
    return 'waiting';
  };

  const getProgressStatus = (state: string): 'active' | 'success' | 'exception' | 'normal' => {
    switch (state) {
      case 'active': return 'active';
      case 'done': return 'success';
      case 'failed': return 'exception';
      default: return 'normal';
    }
  };

  const statusConfig = syncData ? STATUS_CONFIG[syncData.status] || STATUS_CONFIG.pending : STATUS_CONFIG.pending;
  const indexState = getStepState('index');
  const authorityState = getStepState('authority');
  const ragState = getStepState('rag');

  if (loading) {
    return (
      <InnerLayout
        breadcrumbLinks={[
          { title: 'Knowledge Base', path: '/knowledge' },
          { title: kbName || 'Loading...', path: `/knowledge/${kb_id}` },
          { title: 'Sync Status' },
        ]}
      >
        <div style={{ textAlign: 'center', padding: 80 }}>
          <Spin size="large" />
        </div>
      </InnerLayout>
    );
  }

  // No sync configured
  if (!syncData) {
    return (
      <InnerLayout
        breadcrumbLinks={[
          { title: 'Knowledge Base', path: '/knowledge' },
          { title: kbName || kb_id || '', path: `/knowledge/${kb_id}` },
          { title: 'Sync Status' },
        ]}
      >
        <div className={styles.container}>
          <div className={styles['empty-state']}>
            <div className={styles['empty-icon']}>
              <IconFont type="spark-sync-line" style={{ fontSize: 64 }} />
            </div>
            <div className={styles['empty-title']}>No Sync Configured</div>
            <div className={styles['empty-description']}>
              This knowledge base doesn&apos;t have a sync job set up yet. Configure a source system
              to automatically sync documents into this knowledge base.
            </div>
            <Button type="primary" onClick={() => navigate(`/knowledge/edit/${kb_id}`)}>
              Go to Settings
            </Button>
          </div>
        </div>
      </InnerLayout>
    );
  }

  return (
    <InnerLayout
      breadcrumbLinks={[
        { title: 'Knowledge Base', path: '/knowledge' },
        { title: kbName || kb_id || '', path: `/knowledge/${kb_id}` },
        { title: 'Sync Status' },
      ]}
      right={
        <div className={styles['header-actions']}>
          {polling && (
            <div className={styles['auto-refresh']}>
              <span className={styles.pulse} />
              Auto-refreshing
            </div>
          )}
          <Button
            type="default"
            onClick={() => fetchSyncData()}
          >
            Refresh
          </Button>
        </div>
      }
    >
      <div className={styles.container}>
        {/* Status Banner */}
        <div className={styles['status-banner']}>
          <IconFont
            type={statusConfig.icon}
            className={`${styles['status-icon']} ${styles[`status-icon-${syncData.status}`]}`}
          />
          <div className={styles['status-info']}>
            <div className={styles['status-title']}>{statusConfig.label}</div>
            <div className={styles['status-subtitle']}>
              {syncData.status === 'pending' && 'Sync job is ready. Click "Start Sync" to begin.'}
              {syncData.status === 'indexing' && `Crawling documents from source... ${syncData.indexed_docs} items processed`}
              {syncData.status === 'authority_syncing' && `Extracting users & groups from document ACLs...`}
              {syncData.status === 'rag_processing' && `Chunking documents for RAG retrieval... ${syncData.rag_progress}% complete`}
              {syncData.status === 'completed' && `${syncData.total_docs} documents, ${syncData.authority_count || 0} authorities, ${syncData.rag_docs} RAG chunks`}
              {syncData.status === 'failed' && (
                syncData.error_message
                  ? `Error: ${syncData.error_message.length > 100 ? syncData.error_message.substring(0, 100) + '...' : syncData.error_message}`
                  : 'Sync encountered an error. Check the error details below.'
              )}
              {syncData.last_sync_time && (
                <span style={{ marginLeft: 12, opacity: 0.7 }}>
                  Last sync: {new Date(syncData.last_sync_time).toLocaleString()}
                </span>
              )}
            </div>
          </div>
          <div className={styles['overall-progress']}>
            <Progress
              type="circle"
              percent={Math.round(syncData.overall_progress || 0)}
              size={80}
              status={syncData.status === 'failed' ? 'exception' : syncData.status === 'completed' ? 'success' : 'active'}
              strokeWidth={8}
            />
          </div>
        </div>

        {/* Error Alert */}
        {syncData.error_message && (
          <div className={styles['error-section']}>
            <Alert
              type="error"
              showIcon
              message="Sync Error"
              description={syncData.error_message}
            />
          </div>
        )}

        {/* Pipeline Steps */}
        <div className={styles['pipeline-section']}>
          <div className={styles['pipeline-title']}>
            <IconFont type="spark-flowChart-line" />
            Pipeline Progress
          </div>
          <div className={styles['pipeline-steps']}>
            {/* Step 1: Document Indexing */}
            <div className={`${styles['pipeline-step']} ${
              indexState === 'active' ? styles['pipeline-step-active'] :
              indexState === 'done' ? styles['pipeline-step-done'] :
              indexState === 'failed' ? styles['pipeline-step-failed'] : ''
            }`}>
              <div className={styles['step-header']}>
                <div className={`${styles['step-number']} ${styles[`step-number-${indexState}`]}`}>
                  {indexState === 'done' ? '✓' : indexState === 'failed' ? '✕' : '1'}
                </div>
                <span className={styles['step-label']}>Document Crawl</span>
                {indexState === 'active' && (
                  <Tag color="blue" style={{ marginLeft: 'auto' }}>In Progress</Tag>
                )}
                {indexState === 'done' && (
                  <Tag color="green" style={{ marginLeft: 'auto' }}>Complete</Tag>
                )}
                {indexState === 'failed' && (
                  <Tag color="red" style={{ marginLeft: 'auto' }}>Failed</Tag>
                )}
              </div>
              <div className={styles['step-description']}>
                Crawling and indexing documents from the source system into OpenSearch
              </div>
              <div className={styles['step-progress']}>
                <Progress
                  percent={syncData.index_progress}
                  status={getProgressStatus(indexState)}
                  strokeLinecap="round"
                  size="small"
                />
              </div>
              <div className={styles['step-stats']}>
                <span>
                  {indexState === 'active'
                    ? `${syncData.indexed_docs} items crawled...`
                    : `${syncData.total_docs} documents indexed`}
                </span>
                {syncData.index_name && (
                  <Tooltip title="OpenSearch index name">
                    <span style={{ opacity: 0.6 }}>{syncData.index_name}</span>
                  </Tooltip>
                )}
              </div>
            </div>

            <div className={styles['pipeline-arrow']}>→</div>

            {/* Step 2: Authority Sync */}
            <div className={`${styles['pipeline-step']} ${
              authorityState === 'active' ? styles['pipeline-step-active'] :
              authorityState === 'done' ? styles['pipeline-step-done'] :
              authorityState === 'failed' ? styles['pipeline-step-failed'] : ''
            }`}>
              <div className={styles['step-header']}>
                <div className={`${styles['step-number']} ${styles[`step-number-${authorityState}`]}`}>
                  {authorityState === 'done' ? '✓' : authorityState === 'failed' ? '✕' : '2'}
                </div>
                <span className={styles['step-label']}>Authority Sync</span>
                {authorityState === 'active' && (
                  <Tag color="blue" style={{ marginLeft: 'auto' }}>In Progress</Tag>
                )}
                {authorityState === 'done' && (
                  <Tag color="green" style={{ marginLeft: 'auto' }}>Complete</Tag>
                )}
                {authorityState === 'failed' && (
                  <Tag color="red" style={{ marginLeft: 'auto' }}>Failed</Tag>
                )}
              </div>
              <div className={styles['step-description']}>
                Extracting unique users and groups from document ACL tokens
              </div>
              <div className={styles['step-stats']}>
                <span>{syncData.authority_count || 0} users & groups</span>
                {syncData.authority_index_name && (
                  <Tooltip title="Authority index name">
                    <span style={{ opacity: 0.6 }}>{syncData.authority_index_name}</span>
                  </Tooltip>
                )}
              </div>
            </div>

            <div className={styles['pipeline-arrow']}>→</div>

            {/* Step 3: RAG Processing */}
            <div className={`${styles['pipeline-step']} ${
              ragState === 'active' ? styles['pipeline-step-active'] :
              ragState === 'done' ? styles['pipeline-step-done'] :
              ragState === 'failed' ? styles['pipeline-step-failed'] : ''
            }`}>
              <div className={styles['step-header']}>
                <div className={`${styles['step-number']} ${styles[`step-number-${ragState}`]}`}>
                  {ragState === 'done' ? '✓' : ragState === 'failed' ? '✕' : '3'}
                </div>
                <span className={styles['step-label']}>RAG Chunking</span>
                {ragState === 'active' && (
                  <Tag color="blue" style={{ marginLeft: 'auto' }}>In Progress</Tag>
                )}
                {ragState === 'done' && (
                  <Tag color="green" style={{ marginLeft: 'auto' }}>Complete</Tag>
                )}
                {ragState === 'failed' && (
                  <Tag color="red" style={{ marginLeft: 'auto' }}>Failed</Tag>
                )}
              </div>
              <div className={styles['step-description']}>
                Splitting documents into chunks with metadata and ACL tokens for RAG retrieval
              </div>
              <div className={styles['step-progress']}>
                <Progress
                  percent={syncData.rag_progress}
                  status={getProgressStatus(ragState)}
                  strokeLinecap="round"
                  size="small"
                />
              </div>
              <div className={styles['step-stats']}>
                <span>{syncData.rag_docs} chunks created</span>
                {syncData.rag_index_name && (
                  <Tooltip title="RAG index name">
                    <span style={{ opacity: 0.6 }}>{syncData.rag_index_name}</span>
                  </Tooltip>
                )}
              </div>
            </div>
          </div>
        </div>

        {/* Document Stats */}
        <div className={styles['stats-section']}>
          <div className={styles['stats-grid']}>
            <div className={styles['stat-card']}>
              <div className={`${styles['stat-value']} ${styles['stat-value-total']}`}>
                {syncData.total_docs}
              </div>
              <Tooltip title="Total documents indexed in OpenSearch from the source system.">
                <div className={styles['stat-label']} style={{ cursor: 'help', borderBottom: '1px dashed var(--ag-ant-color-text-tertiary)' }}>
                  Documents
                  <IconFont type="spark-info-line" style={{ marginLeft: 4, fontSize: 12, color: 'var(--ag-ant-color-text-tertiary)' }} />
                </div>
              </Tooltip>
            </div>
            <div className={styles['stat-card']}>
              <div className={`${styles['stat-value']} ${styles['stat-value-indexed']}`}>
                {syncData.authority_count || 0}
              </div>
              <Tooltip title="Unique users and groups extracted from document ACL tokens. These are used for permission-aware retrieval.">
                <div className={styles['stat-label']} style={{ cursor: 'help' }}>
                  Users & Groups
                </div>
              </Tooltip>
            </div>
            <div className={styles['stat-card']}>
              <div className={`${styles['stat-value']} ${styles['stat-value-rag']}`}>
                {syncData.rag_docs}
              </div>
              <Tooltip title="Number of text chunks created from documents for RAG retrieval. Each document is split into ~1000 character chunks with metadata and ACL tokens.">
                <div className={styles['stat-label']} style={{ cursor: 'help' }}>
                  RAG Chunks
                </div>
              </Tooltip>
            </div>
            <div className={styles['stat-card']}>
              <div className={`${styles['stat-value']} ${styles['stat-value-failed']}`}>
                {syncData.failed_docs}
              </div>
              <div className={styles['stat-label']}>Failed</div>
            </div>
          </div>
        </div>

        {/* Sync Details */}
        <div className={styles['details-section']}>
          <div className={styles['details-card']}>
            <div className={styles['details-title']}>
              <IconFont type="spark-info-line" />
              Sync Details
            </div>
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="Sync ID">
                <span style={{ fontSize: 12, fontFamily: 'monospace' }}>{syncData.sync_id}</span>
              </Descriptions.Item>
              <Descriptions.Item label="Source">
                {sources.find((s) => s.value === syncData.source_id)?.label || syncData.source_id || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Destination">
                {destinations.find((d) => d.value === syncData.destination_id)?.label || syncData.destination_id || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Status">
                <Tag color={statusConfig.color}>{statusConfig.label}</Tag>
              </Descriptions.Item>
              {syncData.index_name && (
                <Descriptions.Item label="Index Name">
                  <span style={{ fontFamily: 'monospace', fontSize: 12 }}>{syncData.index_name}</span>
                </Descriptions.Item>
              )}
              {syncData.authority_index_name && (
                <Descriptions.Item label="Authority Index">
                  <span style={{ fontFamily: 'monospace', fontSize: 12 }}>{syncData.authority_index_name}</span>
                </Descriptions.Item>
              )}
              {syncData.rag_index_name && (
                <Descriptions.Item label="RAG Index">
                  <span style={{ fontFamily: 'monospace', fontSize: 12 }}>{syncData.rag_index_name}</span>
                </Descriptions.Item>
              )}
              {syncData.last_sync_time && (
                <Descriptions.Item label="Last Sync">
                  {new Date(syncData.last_sync_time).toLocaleString()}
                </Descriptions.Item>
              )}
            </Descriptions>
          </div>
        </div>

        {/* Schedule Configuration */}
        <div className={styles['schedule-section']}>
          <div className={styles['schedule-card']}>
            <div className={styles['schedule-title']}>
              <IconFont type="spark-time-line" />
              Sync Schedule
            </div>
            <Select
              value={cronMode}
              onChange={(value: string) => {
                setCronMode(value);
                if (value !== 'custom') {
                  setEditingCron(value);
                }
              }}
              placeholder="Select a sync schedule (optional)"
              style={{ width: '100%', marginBottom: cronMode === 'custom' ? 8 : 0 }}
              allowClear
              onClear={() => {
                setCronMode('');
                setEditingCron('');
              }}
            >
              {CRON_PRESETS.map((preset) => (
                <Select.Option key={preset.value} value={preset.value}>
                  <div>
                    <span>{preset.label}</span>
                    {preset.value !== 'custom' && (
                      <span style={{ color: 'var(--ag-ant-color-text-tertiary)', marginLeft: 8, fontSize: 12 }}>
                        {preset.description}
                      </span>
                    )}
                  </div>
                </Select.Option>
              ))}
            </Select>
            {cronMode === 'custom' && (
              <Input
                value={editingCron}
                onChange={(e: any) => setEditingCron(e.target.value)}
                placeholder="0 0 */6 * * ?"
                style={{ marginTop: 4 }}
              />
            )}
            <div style={{ fontSize: 12, color: 'var(--ag-ant-color-text-tertiary)', marginTop: 8 }}>
              Cron format: second minute hour day month weekday. Example: &quot;0 0 */6 * * ?&quot; = every 6 hours
            </div>
            {editingCron !== (syncMeta?.sync_cron || '') && (
              <Button
                type="primary"
                size="small"
                style={{ marginTop: 8 }}
                onClick={() => {
                  updateKnowledgeSyncCron(syncData.sync_id, editingCron)
                    .then(() => {
                      message.success('Sync schedule updated');
                      fetchSyncData();
                    })
                    .catch(() => message.error('Failed to update schedule'));
                }}
              >
                Update Schedule
              </Button>
            )}
          </div>
        </div>

        {/* Action Buttons */}
        <div style={{ display: 'flex', gap: 12, justifyContent: 'center', padding: '16px 0 32px', flexWrap: 'wrap' }}>
          {(syncData.status === 'indexing' || syncData.status === 'authority_syncing' || syncData.status === 'rag_processing') ? (
            <Button
              danger
              onClick={handleStopSync}
            >
              Stop Sync
            </Button>
          ) : (
            <>
              <Button
                type="primary"
                onClick={handleStartSync}
              >
                {syncData.status === 'completed' || syncData.status === 'failed' ? 'Re-run Full Sync' : 'Start Full Sync'}
              </Button>
              <Button
                type="default"
                onClick={handleSyncDocumentsOnly}
              >
                Sync Documents Only
              </Button>
              <Button
                type="default"
                onClick={handleReindexRagOnly}
              >
                Reindex RAG Only
              </Button>
            </>
          )}
          <Button
            onClick={() => navigate(`/knowledge/edit/${kb_id}`)}
          >
            Back to Settings
          </Button>
          <Button
            danger
            onClick={handleHardReset}
            disabled={syncData.status === 'indexing' || syncData.status === 'authority_syncing' || syncData.status === 'rag_processing'}
          >
            Hard Reset
          </Button>
        </div>
      </div>
    </InnerLayout>
  );
}
