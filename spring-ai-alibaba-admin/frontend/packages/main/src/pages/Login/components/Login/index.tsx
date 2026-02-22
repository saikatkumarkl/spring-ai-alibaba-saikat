import $i18n from '@/i18n';
import { initKeycloak, keycloakLogin, KeycloakConfig } from '@/request/keycloak';
import { LockOutlined, UserOutlined, LoginOutlined } from '@ant-design/icons';
import { Button, Divider, Form, Input, message } from 'antd';
import React, { useEffect, useState } from 'react';
import { history } from 'umi';
import styles from './index.module.less';

interface LoginForm {
  username: string;
  password: string;
}

interface IProps {
  loading: boolean;
  onSubmit: (values: LoginForm) => void;
}

/**
 * Login component — dual-login pattern:
 *   1. Username / password form for built-in accounts (admin/admin, user/user)
 *   2. A single "Login via Enterprise SSO" button that redirects to the
 *      Keycloak login page. Keycloak handles everything internally — LDAP,
 *      Azure AD, Google, SAML, etc. The user never needs to know which
 *      providers are configured; Keycloak presents the appropriate options.
 *
 * This is the industry-standard pattern used by Slack, GitHub, Jira, etc.
 */
const Login: React.FC<IProps> = ({ onSubmit, loading }) => {
  const [ssoLoading, setSsoLoading] = useState(false);
  const [keycloakEnabled, setKeycloakEnabled] = useState(false);
  const [keycloakConfig, setKeycloakConfig] = useState<KeycloakConfig | null>(null);

  // Check if Keycloak is enabled on mount
  useEffect(() => {
    const baseUrl = process.env.WEB_SERVER || '';
    fetch(`${baseUrl}/console/v1/system/keycloak-config`)
      .then(res => res.json())
      .then(json => {
        const config: KeycloakConfig = json?.data;
        if (config?.enabled) {
          setKeycloakEnabled(true);
          setKeycloakConfig(config);
        }
      })
      .catch(() => {
        // Keycloak not available — SSO button stays hidden
      });
  }, []);

  /** Redirect to Keycloak login page (handles LDAP, Azure AD, Google, etc.) */
  const handleSsoLogin = async () => {
    if (!keycloakConfig) {
      message.error('SSO configuration not available.');
      return;
    }

    try {
      setSsoLoading(true);

      const kc = initKeycloak(keycloakConfig, 'admin-ui');
      if (!kc) {
        message.error('Failed to initialize authentication.');
        setSsoLoading(false);
        return;
      }

      // No idpHint — Keycloak shows its own login page with all configured options
      const authenticated = await keycloakLogin(kc);
      if (authenticated) {
        history.replace('/app');
      } else {
        // keycloakLogin returns false when it triggers a redirect — that's expected
        setSsoLoading(false);
      }
    } catch (err) {
      console.error('SSO login error:', err);
      message.error('Unable to connect to authentication server.');
      setSsoLoading(false);
    }
  };

  return (
    <div className={styles['login-container']}>
      <div className={styles['login-title']}>
        {$i18n.get({
          id: 'main.pages.Login.components.Login.index.welcomeToAgentScope',
          dm: '🎉 Welcome to CordonData',
        })}
      </div>

      <div className={styles['login-form']}>
        <Form onFinish={onSubmit} autoComplete="off" size="large">
          <Form.Item
            name="username"
            rules={[{ required: true, message: 'Please enter your username' }]}
          >
            <Input
              prefix={<UserOutlined />}
              placeholder={$i18n.get({
                id: 'main.pages.Login.components.Login.index.username',
                dm: 'Username',
              })}
            />
          </Form.Item>
          <Form.Item
            name="password"
            rules={[{ required: true, message: 'Please enter your password' }]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder={$i18n.get({
                id: 'main.pages.Login.components.Login.index.password',
                dm: 'Password',
              })}
            />
          </Form.Item>
          <Form.Item>
            <Button
              className={styles['login-button']}
              type="primary"
              htmlType="submit"
              loading={loading}
            >
              {$i18n.get({
                id: 'main.pages.Login.components.Login.index.login',
                dm: 'Sign In',
              })}
            </Button>
          </Form.Item>
        </Form>

        {keycloakEnabled && (
          <>
            <Divider plain style={{ margin: '8px 0 16px' }}>
              {$i18n.get({
                id: 'main.pages.Login.components.Login.index.orSso',
                dm: 'or',
              })}
            </Divider>

            <Button
              className={styles['other-login']}
              icon={<LoginOutlined />}
              onClick={handleSsoLogin}
              loading={ssoLoading}
              block
            >
              Login via Enterprise SSO
            </Button>
          </>
        )}
      </div>
    </div>
  );
};

export default Login;
