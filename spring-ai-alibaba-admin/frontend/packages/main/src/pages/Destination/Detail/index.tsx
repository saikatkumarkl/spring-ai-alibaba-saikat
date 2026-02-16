import InnerLayout from '@/components/InnerLayout';
import $i18n from '@/i18n';
import { deleteDestination, getDestinationDetail, testConnection } from '@/services/destination';
import { IDestinationDetail } from '@/types/destination';
import { AlertDialog, Button, message } from '@spark-ai/design';
import { useMount, useSetState } from 'ahooks';
import dayjs from 'dayjs';
import { history, useParams } from 'umi';
import styles from './index.module.less';

export default function DestinationDetail() {
  const params = useParams<{ id: string }>();
  const [state, setState] = useSetState<{
    loading: boolean;
    detail: IDestinationDetail | null;
    testing: boolean;
  }>({
    loading: true,
    detail: null,
    testing: false,
  });

  const fetchDetail = () => {
    if (!params.id) return;
    setState({ loading: true });
    getDestinationDetail(params.id)
      .then((detail) => {
        setState({ detail, loading: false });
      })
      .catch(() => {
        message.error('Failed to load destination');
        setState({ loading: false });
      });
  };

  useMount(() => {
    fetchDetail();
  });

  const handleTest = () => {
    if (!params.id) return;
    setState({ testing: true });
    message.loading({ content: 'Testing connection...', key: 'test' });
    testConnection(params.id)
      .then((result) => {
        if (result.status === 'PASS') {
          message.success({ content: result.message || 'Connection successful', key: 'test' });
        } else {
          message.warning({ content: result.message || 'Connection test failed', key: 'test' });
        }
        fetchDetail();
      })
      .catch(() => {
        message.error({ content: 'Connection test failed', key: 'test' });
      })
      .finally(() => setState({ testing: false }));
  };

  const handleDelete = () => {
    if (!params.id) return;
    AlertDialog.warning({
      title: 'Delete Destination',
      children: 'Are you sure you want to delete this destination?',
      danger: true,
      okText: 'Confirm Delete',
      onOk: () => {
        deleteDestination(params.id!).then(() => {
          message.success('Destination deleted');
          history.push('/destination');
        });
      },
    });
  };

  const { detail } = state;
  const config = detail?.connection_config || {};
  const isConnected = detail?.test_result?.toUpperCase() === 'PASS' || detail?.test_result?.toLowerCase().includes('success');

  return (
    <InnerLayout
      breadcrumbLinks={[
        {
          title: $i18n.get({ id: 'main.pages.Destination.destinations', dm: 'Destinations' }),
          path: '/destination',
        },
        {
          title: detail?.name || 'Detail',
        },
      ]}
    >
      <div className={styles['container']}>
        <div className={styles['header']}>
          <div className={styles['title']}>{detail?.name || '...'}</div>
          <div className={styles['actions']}>
            <Button onClick={handleTest} loading={state.testing}>
              Test Connection
            </Button>
            <Button onClick={() => history.push(`/destination/edit/${params.id}`)}>
              Edit
            </Button>
            <Button danger onClick={handleDelete}>
              Delete
            </Button>
          </div>
        </div>

        <div className={styles['section']}>
          <div className={styles['section-title']}>Basic Information</div>
          <div className={styles['info-row']}>
            <div className={styles['info-label']}>Name</div>
            <div className={styles['info-value']}>{detail?.name}</div>
          </div>
          <div className={styles['info-row']}>
            <div className={styles['info-label']}>Description</div>
            <div className={styles['info-value']}>{detail?.description || 'No description'}</div>
          </div>
          <div className={styles['info-row']}>
            <div className={styles['info-label']}>Provider Type</div>
            <div className={styles['info-value']}>{detail?.provider_type}</div>
          </div>
          <div className={styles['info-row']}>
            <div className={styles['info-label']}>Status</div>
            <div className={styles['info-value']}>
              <span
                className={`${styles['status-badge']} ${
                  isConnected ? styles['status-connected'] : styles['status-not-tested']
                }`}
              >
                {isConnected ? 'Connected' : 'Not Tested'}
              </span>
            </div>
          </div>
          <div className={styles['info-row']}>
            <div className={styles['info-label']}>Created</div>
            <div className={styles['info-value']}>
              {detail?.gmt_create ? dayjs(detail.gmt_create).format('YYYY-MM-DD HH:mm:ss') : '-'}
            </div>
          </div>
          <div className={styles['info-row']}>
            <div className={styles['info-label']}>Last Modified</div>
            <div className={styles['info-value']}>
              {detail?.gmt_modified ? dayjs(detail.gmt_modified).format('YYYY-MM-DD HH:mm:ss') : '-'}
            </div>
          </div>
        </div>

        <div className={styles['section']}>
          <div className={styles['section-title']}>Connection Details</div>
          <div className={styles['info-row']}>
            <div className={styles['info-label']}>URL</div>
            <div className={styles['info-value']}>{config.url || '-'}</div>
          </div>
          <div className={styles['info-row']}>
            <div className={styles['info-label']}>Username</div>
            <div className={styles['info-value']}>{config.username || '-'}</div>
          </div>
          <div className={styles['info-row']}>
            <div className={styles['info-label']}>Password</div>
            <div className={styles['info-value']}>{config.password ? '••••••••' : '-'}</div>
          </div>
          {detail?.test_result && (
            <div className={styles['info-row']}>
              <div className={styles['info-label']}>Last Test Result</div>
              <div className={styles['info-value']}>{detail.test_result}</div>
            </div>
          )}
        </div>
      </div>
    </InnerLayout>
  );
}
