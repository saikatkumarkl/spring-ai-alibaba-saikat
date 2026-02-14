import { request } from '@/request';

export interface IAuditLogEntry {
  id: number;
  user_email: string;
  action: string;
  resource_type: string | null;
  resource_id: string | null;
  details: string | null;
  ip_address: string | null;
  created_at: string;
  app_name?: string | null;
  chat_available?: boolean | null;
  has_rag?: boolean;
}

export interface IAuditLogResponse {
  logs: IAuditLogEntry[];
  totalCount: number;
  page: number;
  pageSize: number;
}

export interface IAuditUserSummary {
  email: string;
  full_name: string;
  last_login: string | null;
  total_actions: number;
  last_activity: string | null;
  login_count: number;
  chat_count: number;
  search_count: number;
  delete_count: number;
  upload_count: number;
  app_count: number;
}

export interface IAppAccess {
  app_id: string;
  name: string;
  description: string | null;
  type: string;
}

export interface IAuditUserDetail {
  user: {
    email: string;
    full_name: string;
    last_login: string | null;
    created_at: string;
  } | null;
  apps: IAppAccess[];
  logs: IAuditLogEntry[];
  totalCount: number;
  page: number;
  pageSize: number;
}

export interface IChatMessage {
  role: string;
  content: string;
  created_at: string;
}

export interface IRagDoc {
  doc_id: string;
  doc_name: string;
  score: number;
  chunk_id: string;
}

export interface IRagRetrieval {
  retrieved_at: string;
  docs_json: string; // JSON string of IRagDoc[]
}

export interface IChatDetail {
  available: boolean;
  message?: string;
  messages: IChatMessage[];
  conversationId: string;
  app?: { app_id: string; app_name: string };
  ragRetrievals?: IRagRetrieval[];
}

/**
 * Get audit logs with optional email filter and pagination
 */
export async function getAuditLogs(params: {
  email?: string;
  page?: number;
  pageSize?: number;
}): Promise<{ data: { data: IAuditLogResponse } }> {
  const queryParams = new URLSearchParams();
  if (params.email) queryParams.set('email', params.email);
  if (params.page) queryParams.set('page', String(params.page));
  if (params.pageSize) queryParams.set('pageSize', String(params.pageSize));

  const response = await request({
    url: `/console/v1/chatbot/audit-log?${queryParams.toString()}`,
    method: 'GET',
  });
  return response as any;
}

/**
 * Get aggregated user list for audit dashboard
 */
export async function getAuditUsers(): Promise<{ data: { data: IAuditUserSummary[] } }> {
  const response = await request({
    url: '/console/v1/chatbot/audit-log/users',
    method: 'GET',
  });
  return response as any;
}

/**
 * Get detailed audit trail for a specific user
 */
export async function getAuditUserDetail(params: {
  email: string;
  page?: number;
  pageSize?: number;
}): Promise<{ data: { data: IAuditUserDetail } }> {
  const queryParams = new URLSearchParams();
  queryParams.set('email', params.email);
  if (params.page) queryParams.set('page', String(params.page));
  if (params.pageSize) queryParams.set('pageSize', String(params.pageSize));

  const response = await request({
    url: `/console/v1/chatbot/audit-log/user-detail?${queryParams.toString()}`,
    method: 'GET',
  });
  return response as any;
}

/**
 * Get chat conversation detail for audit (if not deleted)
 */
export async function getAuditChatDetail(params: {
  email: string;
  conversationId: string;
}): Promise<{ data: { data: IChatDetail } }> {
  const queryParams = new URLSearchParams();
  queryParams.set('email', params.email);
  queryParams.set('conversationId', params.conversationId);

  const response = await request({
    url: `/console/v1/chatbot/audit-log/chat-detail?${queryParams.toString()}`,
    method: 'GET',
  });
  return response as any;
}

