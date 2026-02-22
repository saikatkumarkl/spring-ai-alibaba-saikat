import { request } from '@/request';
import { IApiResponse } from '@/types/common';

export interface KeycloakUser {
  id: string;
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  enabled: boolean;
  createdTimestamp: number;
  roles: string[];
}

/**
 * List all users from Keycloak realm
 */
export async function listKeycloakUsers(
  first = 0,
  max = 100,
): Promise<IApiResponse<KeycloakUser[]>> {
  const response = await request({
    url: '/console/v1/keycloak-users',
    method: 'GET',
    params: { first, max },
  });
  return response.data as IApiResponse<KeycloakUser[]>;
}

/**
 * Get roles for a specific user
 */
export async function getUserRoles(
  userId: string,
): Promise<IApiResponse<string[]>> {
  const response = await request({
    url: `/console/v1/keycloak-users/${userId}/roles`,
    method: 'GET',
  });
  return response.data as IApiResponse<string[]>;
}

/**
 * Assign a realm role to a user
 */
export async function assignRole(
  userId: string,
  roleName: string,
): Promise<IApiResponse<string>> {
  const response = await request({
    url: `/console/v1/keycloak-users/${userId}/roles`,
    method: 'POST',
    data: { roleName },
  });
  return response.data as IApiResponse<string>;
}

/**
 * Remove a realm role from a user
 */
export async function removeRole(
  userId: string,
  roleName: string,
): Promise<IApiResponse<string>> {
  const response = await request({
    url: `/console/v1/keycloak-users/${userId}/roles`,
    method: 'DELETE',
    data: { roleName },
  });
  return response.data as IApiResponse<string>;
}
