import $i18n from '@/i18n';
import { authGithubLogin } from '@/services/login';
import { GithubFilled } from '@ant-design/icons';
import { Button } from '@spark-ai/design';
import styles from './index.module.less';

export default function () {
  return (
    <Button
      onClick={async () => {
        const { data: authUrl } = await authGithubLogin();
        location.href = authUrl;
      }}
      icon={<GithubFilled />}
      className={styles['other-login']}
      block
    >
      {$i18n.get({
        id: 'main.pages.Login.components.Login.index.useGithubLogin',
        dm: 'Login with GitHub',
      })}
    </Button>
  );
}
