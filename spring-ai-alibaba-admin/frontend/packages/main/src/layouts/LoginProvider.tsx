import { getAccountInfo } from '@/services/account';
import { keycloakLogout, getKeycloak } from '@/request/keycloak';
import { session } from '@/request/session';
import { useRequest } from 'ahooks';
import { Spin, Result, Button } from 'antd';
import { history } from 'umi';
import { useState } from 'react';

/**
 * Sign out — if the user logged in via Keycloak SSO, do a Keycloak
 * logout; otherwise just clear the local session and redirect to /login.
 */
function handleSignOut() {
  const kc = getKeycloak();
  if (kc) {
    keycloakLogout(window.location.origin + '/login');
  } else {
    session.clear();
    history.replace('/login');
  }
}

export default function (props: {
  children: React.ReactNode | React.ReactNode[];
}) {
  const [forbidden, setForbidden] = useState(false);

  const { loading } = useRequest(getAccountInfo, {
    onSuccess(res) {
      window.g_config.user = res.data;
    },
    onError(err: any) {
      if (new URL(window.location.href).searchParams.get('ignore-login'))
        return;

      // If the backend returns 403, the user is authenticated but lacks admin role
      const status = err?.response?.status ?? err?.status;
      if (status === 403) {
        setForbidden(true);
        return;
      }

      history.replace('/login');
    },
  });

  if (loading)
    return (
      <div className="loading-center">
        <Spin />
      </div>
    );

  if (forbidden)
    return (
      <div style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        minHeight: '100vh',
        background: 'var(--ag-ant-color-bg-layout, #f5f5f5)',
      }}>
        <Result
          status="403"
          title="Access Denied"
          subTitle="Your account does not have the admin role required to access the Control Center. Please contact an administrator to request access."
          extra={
            <Button
              type="primary"
              onClick={handleSignOut}
            >
              Sign Out
            </Button>
          }
        />
      </div>
    );

  return props.children;
}
