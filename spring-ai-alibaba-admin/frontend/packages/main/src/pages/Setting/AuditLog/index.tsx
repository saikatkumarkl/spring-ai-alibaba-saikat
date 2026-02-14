import InnerLayout from '@/components/InnerLayout';
import $i18n from '@/i18n';
import {
  getAuditUsers,
  getAuditUserDetail,
  getAuditChatDetail,
  IAuditUserSummary,
  IAuditLogEntry,
  IAppAccess,
  IChatMessage,
  IRagDoc,
  IRagRetrieval,
} from '@/services/auditLog';
import { Button, Pagination, Tag } from '@spark-ai/design';
import type { TableProps } from 'antd';
import { Input, Table, Modal, Tooltip, Spin } from 'antd';
import {
  ArrowLeftOutlined,
  LoginOutlined,
  MessageOutlined,
  DeleteOutlined,
  UploadOutlined,
  SearchOutlined,
  AppstoreOutlined,
  EyeOutlined,
  CloseCircleOutlined,
  DatabaseOutlined,
} from '@ant-design/icons';
import { useEffect, useState, useCallback } from 'react';
import styles from './index.module.less';

const { Search } = Input;

const ACTION_COLORS: Record<string, string> = {
  login: 'green',
  logout: 'orange',
  chat: 'blue',
  delete: 'red',
  upload: 'cyan',
  search: 'geekblue',
  file: 'purple',
};

function getActionColor(action: string): string {
  const lower = action.toLowerCase();
  for (const [key, color] of Object.entries(ACTION_COLORS)) {
    if (lower.includes(key)) return color;
  }
  return 'default';
}

function formatTime(val: string | number | null): string {
  if (!val) return '-';
  try {
    return new Date(val).toLocaleString();
  } catch {
    return String(val);
  }
}

function timeAgo(val: string | number | null): string {
  if (!val) return 'Never';
  const diff = Date.now() - new Date(val).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return 'Just now';
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days}d ago`;
  return formatTime(val);
}

/** Extract conversation ID from audit detail string */
function extractConversationId(details: string | null): string | null {
  if (!details) return null;
  const match = details.match(/conversation=([a-f0-9-]+)/);
  return match ? match[1] : null;
}

/** Extract prompt from audit detail string */
function extractPrompt(details: string | null): string | null {
  if (!details) return null;
  const match = details.match(/\|prompt=(.+)$/);
  return match ? match[1] : null;
}

// ===================== USER LIST VIEW =====================

function UserListView({
  users,
  loading,
  searchFilter,
  onSearchChange,
  onSelectUser,
}: {
  users: IAuditUserSummary[];
  loading: boolean;
  searchFilter: string;
  onSearchChange: (v: string) => void;
  onSelectUser: (email: string) => void;
}) {
  const filtered = searchFilter
    ? users.filter(
        (u) =>
          u.email.toLowerCase().includes(searchFilter.toLowerCase()) ||
          u.full_name.toLowerCase().includes(searchFilter.toLowerCase()),
      )
    : users;

  return (
    <>
      <div className={styles.filters}>
        <Search
          placeholder="Search users by name or email..."
          allowClear
          onSearch={onSearchChange}
          onChange={(e) => {
            if (!e.target.value) onSearchChange('');
          }}
          style={{ width: 350 }}
        />
      </div>

      {loading ? (
        <div style={{ textAlign: 'center', padding: 40 }}>
          <Spin />
        </div>
      ) : (
        filtered.map((user) => (
          <div
            key={user.email}
            className={styles.userCard}
            onClick={() => onSelectUser(user.email)}
          >
            <div className={styles.userCardHeader}>
              <div>
                <span className={styles.userName}>{user.full_name}</span>
                <span className={styles.userEmail}>{user.email}</span>
              </div>
              <span className={styles.lastActivity}>
                {user.last_activity ? timeAgo(user.last_activity) : 'No activity'}
              </span>
            </div>
            <div className={styles.userStats}>
              <span className={styles.stat}>
                <LoginOutlined />
                <span className={styles.statValue}>{user.login_count}</span> logins
              </span>
              <span className={styles.stat}>
                <MessageOutlined />
                <span className={styles.statValue}>{user.chat_count}</span> chats
              </span>
              <span className={styles.stat}>
                <SearchOutlined />
                <span className={styles.statValue}>{user.search_count}</span> searches
              </span>
              <span className={styles.stat}>
                <UploadOutlined />
                <span className={styles.statValue}>{user.upload_count}</span> uploads
              </span>
              <span className={styles.stat}>
                <DeleteOutlined />
                <span className={styles.statValue}>{user.delete_count}</span> deleted
              </span>
              <span className={styles.stat}>
                <AppstoreOutlined />
                <span className={styles.statValue}>{user.app_count}</span> apps
              </span>
            </div>
          </div>
        ))
      )}
      {!loading && filtered.length === 0 && (
        <div style={{ textAlign: 'center', padding: 40, color: 'var(--ag-ant-color-text-tertiary)' }}>
          No users found
        </div>
      )}
    </>
  );
}

// ===================== USER DETAIL VIEW =====================

function UserDetailView({
  email,
  onBack,
}: {
  email: string;
  onBack: () => void;
}) {
  const [loading, setLoading] = useState(true);
  const [userName, setUserName] = useState('');
  const [userApps, setUserApps] = useState<IAppAccess[]>([]);
  const [logs, setLogs] = useState<IAuditLogEntry[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(30);

  // Chat detail modal
  const [chatModalOpen, setChatModalOpen] = useState(false);
  const [chatLoading, setChatLoading] = useState(false);
  const [chatMessages, setChatMessages] = useState<IChatMessage[]>([]);
  const [chatAvailable, setChatAvailable] = useState(true);
  const [chatDeletedMsg, setChatDeletedMsg] = useState('');
  const [chatConvId, setChatConvId] = useState('');
  const [chatAppName, setChatAppName] = useState('');
  const [chatRagRetrievals, setChatRagRetrievals] = useState<IRagRetrieval[]>([]);

  // Prompt detail modal
  const [promptModalOpen, setPromptModalOpen] = useState(false);
  const [promptContent, setPromptContent] = useState('');

  const fetchDetail = useCallback(
    async (p: number, ps: number) => {
      setLoading(true);
      try {
        const response = await getAuditUserDetail({ email, page: p, pageSize: ps });
        const data = response?.data?.data;
        if (data) {
          setUserName(data.user?.full_name || email);
          setUserApps(data.apps || []);
          setLogs(data.logs || []);
          setTotal(data.totalCount || 0);
          setPage(data.page || 1);
          setPageSize(data.pageSize || 30);
        }
      } catch (err) {
        console.error('Failed to fetch user detail:', err);
      } finally {
        setLoading(false);
      }
    },
    [email],
  );

  useEffect(() => {
    fetchDetail(1, pageSize);
  }, [email]);

  const handleViewChat = async (conversationId: string) => {
    setChatConvId(conversationId);
    setChatModalOpen(true);
    setChatLoading(true);
    try {
      const response = await getAuditChatDetail({ email, conversationId });
      const data = response?.data?.data;
      if (data) {
        setChatAvailable(data.available);
        setChatMessages(data.messages || []);
        setChatDeletedMsg(data.message || '');
        setChatAppName(data.app?.app_name || '');
        setChatRagRetrievals(data.ragRetrievals || []);
      }
    } catch (err) {
      console.error('Failed to fetch chat detail:', err);
      setChatAvailable(false);
      setChatDeletedMsg('Failed to load chat details.');
    } finally {
      setChatLoading(false);
    }
  };

  /** Build a reason string: "User has access to App X → used it in this conversation" */
  const getAccessReason = (log: IAuditLogEntry): string | null => {
    if (log.action !== 'CHAT_MESSAGE') return null;
    const appName = log.app_name || log.resource_id;
    const matchingApp = userApps.find((a) => a.app_id === log.resource_id);
    if (matchingApp) {
      return `User has access to "${matchingApp.name}" → accessed data through this app`;
    }
    return appName ? `Accessed via app: ${appName}` : null;
  };

  const columns: TableProps<IAuditLogEntry>['columns'] = [
    {
      title: 'Time',
      dataIndex: 'created_at',
      key: 'created_at',
      width: 170,
      render: (val: string) => formatTime(val),
    },
    {
      title: 'Action',
      dataIndex: 'action',
      key: 'action',
      width: 160,
      render: (action: string, record: IAuditLogEntry) => (
        <span style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <Tag color={getActionColor(action)} className={styles.actionTag}>
            {action}
          </Tag>
          {record.has_rag && (
            <Tooltip title="RAG documents were retrieved for this chat">
              <DatabaseOutlined style={{ color: 'var(--ag-ant-color-purple, #722ed1)', fontSize: 14 }} />
            </Tooltip>
          )}
        </span>
      ),
    },
    {
      title: 'Resource',
      key: 'resource',
      width: 200,
      render: (_: any, record: IAuditLogEntry) => {
        if (record.action === 'CHAT_MESSAGE') {
          return (
            <Tooltip title={getAccessReason(record)}>
              <span>
                {record.app_name || record.resource_id || '-'}
                {getAccessReason(record) && (
                  <span style={{ marginLeft: 4, fontSize: 11, color: 'var(--ag-ant-color-success)' }}>✓</span>
                )}
              </span>
            </Tooltip>
          );
        }
        if (record.resource_type === 'conversation') {
          return <span style={{ fontFamily: 'monospace', fontSize: 12 }}>Conv: {record.resource_id?.substring(0, 8)}...</span>;
        }
        if (record.resource_type === 'file') {
          return <span>{record.resource_id || '-'}</span>;
        }
        if (record.resource_type === 'session') {
          return <span style={{ color: 'var(--ag-ant-color-text-tertiary)', fontSize: 12 }}>Session</span>;
        }
        return <span>{record.resource_id || '-'}</span>;
      },
    },
    {
      title: 'Details',
      key: 'details',
      render: (_: any, record: IAuditLogEntry) => {
        const convId = extractConversationId(record.details);
        const prompt = extractPrompt(record.details);

        if (record.action === 'CHAT_MESSAGE' && convId) {
          return (
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              {/* Show conversation ID as clickable link */}
              {record.chat_available ? (
                <span
                  className={styles.conversationLink}
                  onClick={() => handleViewChat(convId)}
                >
                  <EyeOutlined style={{ marginRight: 3 }} />
                  {convId.substring(0, 8)}...
                </span>
              ) : (
                <Tooltip title="Chat deleted by user">
                  <span style={{ color: 'var(--ag-ant-color-text-tertiary)', fontSize: 12, fontFamily: 'monospace' }}>
                    <CloseCircleOutlined style={{ marginRight: 3, color: 'var(--ag-ant-color-error)' }} />
                    {convId.substring(0, 8)}...
                  </span>
                </Tooltip>
              )}
              {/* Show prompt preview — click to expand */}
              {prompt && (
                <span
                  className={styles.promptPreview}
                  onClick={() => {
                    setPromptContent(prompt);
                    setPromptModalOpen(true);
                  }}
                  title="Click to view full prompt"
                >
                  "{prompt}"
                </span>
              )}
              {/* If no prompt recorded (old format), just show "searched something" */}
              {!prompt && (
                <span style={{ color: 'var(--ag-ant-color-text-tertiary)', fontSize: 12 }}>Sent a message</span>
              )}
            </div>
          );
        }
        if (record.action === 'FILE_SEARCH' && record.details) {
          const queryMatch = record.details.match(/query=([^,]+)/);
          const query = queryMatch ? queryMatch[1] : null;
          return (
            <span style={{ fontSize: 12 }}>
              Searched: <strong>{query || '...'}</strong>
            </span>
          );
        }
        if (record.action === 'UPLOAD_FILE' && record.details) {
          return <span style={{ fontSize: 12 }}>{record.details}</span>;
        }
        return <span style={{ color: 'var(--ag-ant-color-text-tertiary)', fontSize: 12 }}>{record.details || '-'}</span>;
      },
    },
  ];

  return (
    <>
      <div className={styles.backButton}>
        <Button
          type="text"
          icon={<ArrowLeftOutlined />}
          onClick={onBack}
        >
          Back to Users
        </Button>
      </div>

      <div className={styles.userDetailHeader}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <span className={styles.userName} style={{ fontSize: 18 }}>{userName}</span>
            <span className={styles.userEmail} style={{ fontSize: 14 }}>{email}</span>
          </div>
          <span style={{ fontSize: 13, color: 'var(--ag-ant-color-text-tertiary)' }}>{total} total actions</span>
        </div>
        {userApps.length > 0 && (
          <div style={{ marginTop: 12 }}>
            <span style={{ fontSize: 12, color: 'var(--ag-ant-color-text-secondary)', marginRight: 8 }}>
              App access (per-chat document retrieval shown in <DatabaseOutlined /> chat details):
            </span>
            <div className={styles.appList}>
              {userApps.map((app) => (
                <Tooltip key={app.app_id} title={app.description || app.type}>
                  <Tag color="blue" className={styles.appTag}>
                    {app.name}
                  </Tag>
                </Tooltip>
              ))}
            </div>
          </div>
        )}
      </div>

      <Table
        columns={columns}
        dataSource={logs}
        loading={loading}
        rowKey={(record, index) => `${record.id || record.created_at}-${index}`}
        pagination={false}
        size="small"
      />

      <div className={styles.pagination} style={{ marginTop: 16 }}>
        <Pagination
          hideTips
          current={page}
          pageSize={pageSize}
          total={total}
          onChange={async (p: number, ps: number) => {
            await fetchDetail(p, ps);
          }}
        />
      </div>

      {/* Chat Detail Modal */}
      <Modal
        title={
          <span>
            Chat History {chatAppName && <Tag color="blue">{chatAppName}</Tag>}
            <span style={{ fontSize: 12, color: 'var(--ag-ant-color-text-tertiary)', marginLeft: 8 }}>
              {chatConvId.substring(0, 12)}...
            </span>
          </span>
        }
        open={chatModalOpen}
        onCancel={() => setChatModalOpen(false)}
        footer={null}
        width={700}
      >
        {chatLoading ? (
          <div style={{ textAlign: 'center', padding: 40 }}>
            <Spin />
          </div>
        ) : chatAvailable ? (
          <div>
            {/* RAG Retrieval Section — shows which docs were pulled per chat turn */}
            {chatRagRetrievals.length > 0 && (
              <div className={styles.ragSection}>
                <div className={styles.ragTitle}>
                  <DatabaseOutlined /> Documents Retrieved by RAG
                </div>
                <div className={styles.ragSubtitle}>
                  These are the specific documents the AI pulled from the knowledge base during this conversation.
                </div>
                {chatRagRetrievals.map((retrieval, rIdx) => {
                  let docs: IRagDoc[] = [];
                  try {
                    docs = JSON.parse(retrieval.docs_json);
                  } catch { /* skip */ }
                  return (
                    <div key={rIdx} className={styles.ragRetrieval}>
                      <div className={styles.ragTimestamp}>
                        Retrieved at {formatTime(retrieval.retrieved_at)}
                      </div>
                      <div className={styles.ragDocList}>
                        {docs.map((doc, dIdx) => (
                          <div key={dIdx} className={styles.ragDocItem}>
                            <span className={styles.ragDocName}>{doc.doc_name || doc.doc_id}</span>
                            <span className={styles.ragScore}>Score: {Number(doc.score).toFixed(3)}</span>
                            {doc.chunk_id && (
                              <span className={styles.ragChunkId}>Chunk: {doc.chunk_id.substring(0, 8)}...</span>
                            )}
                          </div>
                        ))}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
            {/* Chat Messages */}
            <div className={styles.chatMessages}>
            {chatMessages.map((msg, i) => (
              <div
                key={i}
                className={`${styles.chatMessage} ${
                  msg.role === 'user' ? styles.chatUser : styles.chatAssistant
                }`}
              >
                <div className={styles.chatRole}>{msg.role}</div>
                <div>{msg.content}</div>
                <div className={styles.chatTime}>{formatTime(msg.created_at)}</div>
              </div>
            ))}
            </div>
          </div>
        ) : (
          <div className={styles.chatDeleted}>
            <CloseCircleOutlined style={{ fontSize: 32, color: 'var(--ag-ant-color-error)', marginBottom: 12 }} />
            <div>{chatDeletedMsg}</div>
          </div>
        )}
      </Modal>

      {/* Prompt Detail Modal */}
      <Modal
        title="User Prompt"
        open={promptModalOpen}
        onCancel={() => setPromptModalOpen(false)}
        footer={null}
        width={600}
      >
        <div
          style={{
            background: 'var(--ag-ant-color-bg-layout)',
            padding: 16,
            borderRadius: 8,
            whiteSpace: 'pre-wrap',
            fontFamily: 'monospace',
            fontSize: 13,
            maxHeight: 400,
            overflow: 'auto',
          }}
        >
          {promptContent}
        </div>
      </Modal>
    </>
  );
}

// ===================== MAIN PAGE =====================

export default function AuditLog() {
  const [users, setUsers] = useState<IAuditUserSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchFilter, setSearchFilter] = useState('');
  const [selectedUser, setSelectedUser] = useState<string | null>(null);

  const fetchUsers = async () => {
    setLoading(true);
    try {
      const response = await getAuditUsers();
      const data = response?.data?.data;
      if (Array.isArray(data)) {
        setUsers(data);
      }
    } catch (err) {
      console.error('Failed to fetch audit users:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers();
  }, []);

  const breadcrumbs = [
    {
      title: $i18n.get({ id: 'main.pages.App.index.home', dm: 'Home' }),
      path: '/',
    },
    {
      title: $i18n.get({ id: 'main.pages.Setting.AuditLog.title', dm: 'Audit Log' }),
      ...(selectedUser ? { path: '/setting/auditLog' } : {}),
    },
  ];

  if (selectedUser) {
    breadcrumbs.push({
      title: selectedUser,
    });
  }

  return (
    <InnerLayout breadcrumbLinks={breadcrumbs}>
      <div className={styles.container}>
        {selectedUser ? (
          <UserDetailView
            email={selectedUser}
            onBack={() => setSelectedUser(null)}
          />
        ) : (
          <UserListView
            users={users}
            loading={loading}
            searchFilter={searchFilter}
            onSearchChange={setSearchFilter}
            onSelectUser={setSelectedUser}
          />
        )}
      </div>
    </InnerLayout>
  );
}

