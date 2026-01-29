import React from 'react';
import { Button, Tag, Collapse, Statistic, Row, Col } from 'antd';
import { ClearOutlined } from '@ant-design/icons';
import { useDebugContext } from '../contexts/DebugContext';
import { useChatContext } from '../contexts/ChatContext';
import styles from '../index.module.less';

const { Panel } = Collapse;

const DebugPanel: React.FC = () => {
  const { debugState, clearLogs, addDebugLog } = useDebugContext();
  const { state } = useChatContext();

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'connected':
        return '#52c41a';
      case 'connecting':
        return '#faad14';
      case 'disconnected':
        return '#ff4d4f';
      default:
        return '#d9d9d9';
    }
  };

  const formatLogTime = (date: Date) => {
    return date.toLocaleTimeString('en-US', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      fractionalSecondDigits: 3,
    });
  };

  const getLogColor = (level: string) => {
    switch (level) {
      case 'error':
        return '#ff4d4f';
      case 'warning':
        return '#faad14';
      default:
        return '#1890ff';
    }
  };

  return (
    <div className={styles.debugPanel}>
      <div className={styles.debugHeader}>
        <h4 style={{ margin: 0, fontSize: 14, fontWeight: 600 }}>Debug Panel</h4>
        <Button
          type="text"
          size="small"
          icon={<ClearOutlined />}
          onClick={clearLogs}
          title="Clear Logs"
        />
      </div>

      <div className={styles.debugContent}>
        {/* Connection Status */}
        <div className={styles.debugSection}>
          <div className={styles.debugSectionTitle}>Connection Status</div>
          <div style={{ display: 'flex', alignItems: 'center', marginBottom: 8 }}>
            <span
              className={`${styles.statusIndicator} ${styles[debugState.connectionStatus.status]}`}
            />
            <span style={{ fontSize: 12, textTransform: 'capitalize' }}>
              {debugState.connectionStatus.status}
            </span>
          </div>
          {debugState.connectionStatus.lastConnected && (
            <div style={{ fontSize: 11, color: '#666' }}>
              Last connected: {debugState.connectionStatus.lastConnected.toLocaleString()}
            </div>
          )}
          {debugState.connectionStatus.errorMessage && (
            <div style={{ fontSize: 11, color: '#ff4d4f', marginTop: 4 }}>
              Error: {debugState.connectionStatus.errorMessage}
            </div>
          )}
        </div>

        {/* Metrics */}
        <div className={styles.debugSection}>
          <div className={styles.debugSectionTitle}>Statistics</div>
          <Row gutter={8}>
            <Col span={12}>
              <Statistic
                title="Messages"
                value={debugState.metrics.messagesCount}
                valueStyle={{ fontSize: 14 }}
              />
            </Col>
            <Col span={12}>
              <Statistic
                title="Errors"
                value={debugState.metrics.errorCount}
                valueStyle={{ fontSize: 14, color: '#ff4d4f' }}
              />
            </Col>
          </Row>
          <Row gutter={8} style={{ marginTop: 8 }}>
            <Col span={12}>
              <Statistic
                title="Avg Response"
                value={debugState.metrics.averageResponseTime}
                suffix="ms"
                valueStyle={{ fontSize: 14 }}
              />
            </Col>
            <Col span={12}>
              <div style={{ fontSize: 12, color: '#666' }}>
                Last activity:
                <br />
                {debugState.metrics.lastActivity
                  ? debugState.metrics.lastActivity.toLocaleTimeString()
                  : 'None'}
              </div>
            </Col>
          </Row>
        </div>

        {/* Current Session Info */}
        {state.currentSessionId && (
          <div className={styles.debugSection}>
            <div className={styles.debugSectionTitle}>Current Session</div>
            <div className={styles.debugInfo}>
              <div>Session ID: {state.currentSessionId}</div>
              <div>Loading: {state.isLoading ? 'Yes' : 'No'}</div>
              <div>Streaming: {state.isStreaming ? 'Yes' : 'No'}</div>
              {state.error && <div>Error: {state.error}</div>}
            </div>
          </div>
        )}

        {/* Debug Logs */}
        <div className={styles.debugSection}>
          <div className={styles.debugSectionTitle}>Debug Logs</div>
          <Collapse size="small" ghost>
            <Panel header={`Logs (${debugState.debugLogs.length})`} key="1">
              <div style={{ maxHeight: 200, overflowY: 'auto' }}>
                {debugState.debugLogs.length === 0 ? (
                  <div style={{ color: '#999', fontSize: 12, textAlign: 'center', padding: 16 }}>
                    No logs
                  </div>
                ) : (
                  debugState.debugLogs.map((log: any, index: number) => (
                    <div
                      key={index}
                      style={{
                        marginBottom: 8,
                        padding: 6,
                        backgroundColor: '#f9f9f9',
                        borderRadius: 4,
                        fontSize: 11,
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                        <Tag
                          color={getLogColor(log.level)}
                          style={{ margin: 0, fontSize: 10 }}
                        >
                          {log.level.toUpperCase()}
                        </Tag>
                        <span style={{ color: '#666' }}>
                          {formatLogTime(log.timestamp)}
                        </span>
                      </div>
                      <div style={{ marginTop: 4 }}>{log.message}</div>
                      {log.data && (
                        <pre
                          style={{
                            margin: '4px 0 0 0',
                            fontSize: 10,
                            color: '#666',
                            whiteSpace: 'pre-wrap',
                          }}
                        >
                          {JSON.stringify(log.data, null, 2)}
                        </pre>
                      )}
                    </div>
                  ))
                )}
              </div>
            </Panel>
          </Collapse>
        </div>

        {/* Test Actions */}
        <div className={styles.debugSection}>
          <div className={styles.debugSectionTitle}>Test Operations</div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <Button
              size="small"
              onClick={() =>
                addDebugLog({
                  level: 'info',
                  message: 'Test info log',
                  data: { timestamp: new Date().toISOString() },
                })
              }
            >
              Add Test Log
            </Button>
            <Button
              size="small"
              onClick={() =>
                addDebugLog({
                  level: 'warning',
                  message: 'Test warning log',
                })
              }
            >
              Add Warning Log
            </Button>
            <Button
              size="small"
              onClick={() =>
                addDebugLog({
                  level: 'error',
                  message: 'Test error log',
                  data: { error: 'simulated error' },
                })
              }
            >
              Add Error Log
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default DebugPanel;
