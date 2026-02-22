import { getCommonConfig } from '@spark-ai/design';
import { history } from 'umi';
import styles from './index.module.less';

type TChildren = React.ReactNode | React.ReactNode[];

export default function (props: {
  logo?: string;
  children?: TChildren;
  right?: TChildren;
}) {
  const darkMode = getCommonConfig().isDarkMode;

  return (
    <div
      className={styles['header']}
      style={darkMode ? { backgroundColor: 'rgba(30, 30, 30, 0.8)', borderBottomColor: 'rgba(255, 255, 255, 0.08)' } : undefined}
    >
      <img
        className={styles['header-logo']}
        onClick={() => history.push('/')}
          src={darkMode ? '/images/logoBlack.png?v=24' : '/images/logoWhite.png?v=24'}
      />
      {props.children}
      <div className={styles['header-right']}>{props.right}</div>
    </div>
  );
}
