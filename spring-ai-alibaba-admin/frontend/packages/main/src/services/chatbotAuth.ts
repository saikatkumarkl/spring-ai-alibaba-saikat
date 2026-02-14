import { request } from '@/request';

/**
 * Get users who have access to an app
 */
export const getAppUsers = (appId: string) => {
  return request({
    url: '/console/v1/chatbot/app-users',
    method: 'GET',
    params: { appId },
  }).then((res) => res.data.data as string[]);
};

/**
 * Update app user access (set which users can access an app)
 */
export const updateAppAccess = (appId: string, userEmails: string[]) => {
  return request({
    url: '/console/v1/chatbot/app-access',
    method: 'POST',
    data: { appId, userEmails },
  }).then((res) => res.data);
};

/**
 * Get all registered chatbot users
 */
export const getAllUsers = () => {
  return request({
    url: '/console/v1/chatbot/users',
    method: 'GET',
  }).then((res) => res.data.data as Array<{ email: string; full_name: string }>);
};
