import $i18n from '@/i18n';
import { IAppDetail } from '@/types/appManage';
import classNames from 'classnames';
import { memo, useMemo } from 'react';
import APIDoc from '../APIDoc';
import styles from './index.module.less';
import PublishComponentCard from './PublishComponentCard';

export default memo(function ChannelConfig(
  props: Pick<IAppDetail, 'app_id' | 'status' | 'type'>,
) {
  const disabled = useMemo(() => {
    return props.status === 'draft';
  }, [props.status]);

  return (
    <div className={styles.container}>
      <div className={styles.title}>
        {$i18n.get({
          id: 'main.pages.App.components.ChannelConfig.index.callMethod',
          dm: 'Invocation Method',
        })}
      </div>
      <div
        className={classNames(styles.card, {
          [styles.disabled]: disabled,
        })}
      >
        <div className={styles['img-con']}>
          <img alt="applicationApi" src="/images/applicationApi.svg" />
        </div>
        <div className="flex flex-col gap-[4px] flex-1">
          <div className={styles.name}>
            {$i18n.get({
              id: 'main.pages.App.components.ChannelConfig.index.apiCall',
              dm: 'API Call',
            })}
          </div>
          <div className={styles.desc}>
            {$i18n.get({
              id: 'main.pages.App.components.ChannelConfig.index.callWithApiKey',
              dm: 'Call agent/workflow applications via API. Requires passing application ID, API-KEY and other parameters. Supports requests via OpenAI-compatible HTTP protocol',
            })}
          </div>
        </div>
        <APIDoc type={props.type} appId={props.app_id} />
      </div>
      <PublishComponentCard
        type={props.type}
        disabled={disabled}
        app_id={props.app_id}
      />
    </div>
  );
});
