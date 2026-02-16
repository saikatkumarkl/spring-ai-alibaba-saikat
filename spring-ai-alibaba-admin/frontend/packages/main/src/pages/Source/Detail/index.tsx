import InnerLayout from '@/components/InnerLayout';
import $i18n from '@/i18n';
import {
  getSourceDetail,
  testConnection,
} from '@/services/source';
import { ISourceDetail } from '@/types/source';
import { Button, message } from '@spark-ai/design';
import { useMount, useSetState } from 'ahooks';
import { Spin } from 'antd';
import classNames from 'classnames';
import { useParams } from 'umi';
import styles from './index.module.less';

interface State {
  source: ISourceDetail | null;
  loading: boolean;
  testResult: string | null;
  testPassed: boolean;
}

export default function SourceDetail() {
  const { id } = useParams<{ id: string }>();

  const [state, setState] = useSetState<State>({
    source: null,
    loading: true,
    testResult: null,
    testPassed: false,
  });

  const fetchDetail = () => {
    if (!id) return;
    getSourceDetail(id)
      .then((data) => {
        setState({
          source: data,
          loading: false,
        });
      })
      .catch(() => setState({ loading: false }));
  };

  useMount(() => fetchDetail());

  const handleTestConnection = () => {
    if (!id) return;
    message.loading({ content: 'Testing connection...', key: 'test' });
    testConnection(id)
      .then((result) => {
        const passed =
          result.result?.includes('Connection working') ?? false;
        setState({ testResult: result.result, testPassed: passed });
        if (passed) {
          message.success({ content: 'Connection working', key: 'test' });
        } else {
          message.warning({
            content: result.result || 'Test returned unexpected result',
            key: 'test',
          });
        }
      })
      .catch(() => {
        setState({ testResult: 'Connection test failed', testPassed: false });
        message.error({ content: 'Connection test failed', key: 'test' });
      });
  };

  if (state.loading) {
    return (
      <InnerLayout
        breadcrumbLinks={[
          { title: 'Sources', path: '/source' },
          { title: 'Detail' },
        ]}
        loading
      >
        <Spin />
      </InnerLayout>
    );
  }

  const src = state.source;
  if (!src) {
    return (
      <InnerLayout
        breadcrumbLinks={[
          { title: 'Sources', path: '/source' },
          { title: 'Not Found' },
        ]}
      >
        <div style={{ padding: 24, textAlign: 'center' }}>
          Source not found.
        </div>
      </InnerLayout>
    );
  }

  return (
    <InnerLayout
      breadcrumbLinks={[
        {
          title: $i18n.get({ id: 'main.pages.Source.Detail.sources', dm: 'Sources' }),
          path: '/source',
        },
        { title: src.name },
      ]}
      right={
        <Button onClick={handleTestConnection}>
          {$i18n.get({
            id: 'main.pages.Source.Detail.testConnection',
            dm: 'Test Connection',
          })}
        </Button>
      }
    >
      <div className={styles['container']}>
        {/* Source Info */}
        <div className={styles['section']}>
          <div className={styles['section-title']}>
            {$i18n.get({ id: 'main.pages.Source.Detail.sourceInfo', dm: 'Source Information' })}
          </div>
          <div className={styles['info-grid']}>
            <div className={styles['info-item']}>
              <span className={styles['info-label']}>Name</span>
              <span className={styles['info-value']}>{src.name}</span>
            </div>
            <div className={styles['info-item']}>
              <span className={styles['info-label']}>Connector</span>
              <span className={styles['info-value']}>
                {src.connector_type}
              </span>
            </div>
            <div className={styles['info-item']}>
              <span className={styles['info-label']}>Source ID</span>
              <span className={styles['info-value']}>{src.source_id}</span>
            </div>
            <div className={styles['info-item']}>
              <span className={styles['info-label']}>MCF Connection</span>
              <span className={styles['info-value']}>
                {src.mcf_connection_name}
              </span>
            </div>
            {src.description && (
              <div className={styles['info-item']} style={{ gridColumn: '1 / -1' }}>
                <span className={styles['info-label']}>Description</span>
                <span className={styles['info-value']}>
                  {src.description}
                </span>
              </div>
            )}
          </div>

          {state.testResult && (
            <div
              className={classNames(
                styles['test-result'],
                state.testPassed ? styles['test-pass'] : styles['test-fail'],
              )}
            >
              {state.testResult}
            </div>
          )}
        </div>

        {/* Connection Config */}
        <div className={styles['section']}>
          <div className={styles['section-title']}>
            {$i18n.get({
              id: 'main.pages.Source.Detail.connectionConfig',
              dm: 'Connection Configuration',
            })}
          </div>
          <pre className={styles['config-pre']}>
            {JSON.stringify(src.connection_config || {}, null, 2)}
          </pre>
        </div>
      </div>
    </InnerLayout>
  );
}
