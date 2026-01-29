import $i18n from '@/i18n';
import { IconFont, Input, InputProps } from '@spark-ai/design';
import classNames from 'classnames';
import React from 'react';
import styles from './Password.module.less';

const Password: React.FC<InputProps> = (props) => {
  const {
    placeholder = $i18n.get({
      id: 'main.pages.Login.components.Form.Password.enterYourPassword',
      dm: 'Enter your password',
    }),
    className,
    ...restProps
  } = props;

  return (
    <Input.Password
      className={classNames(styles['password'], className)}
      placeholder={placeholder}
      prefix={
        <IconFont type="spark-apiKey-line" className={styles['prefix-icon']} />
      }
      {...restProps}
    />
  );
};

export default Password;
