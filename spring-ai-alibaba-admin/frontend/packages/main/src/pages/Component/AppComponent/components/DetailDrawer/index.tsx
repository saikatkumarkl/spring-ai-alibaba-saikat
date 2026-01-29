import $i18n from '@/i18n';
import { getAppComponentDetailByCode } from '@/services/appComponent';
import { IAppComponentListItem } from '@/types/appComponent';
import { Drawer } from '@spark-ai/design';
import { useMount, useSetState } from 'ahooks';
import { Flex, Spin } from 'antd';
import classNames from 'classnames';
import dayjs from 'dayjs';
import InputParamsComp, {
  IConfigInput,
  IOutputParamItem,
} from '../InputParamsComp';
import OutputParamsComp from '../OutputParamsComp';
import styles from './index.module.less';

interface IProps {
  data: IAppComponentListItem;
  onClose: () => void;
}

export default function DetailDrawer(props: IProps) {
  const [state, setState] = useSetState({
    loading: !!props.data.code,
    input: {
      system_params: [],
      user_params: [],
    } as IConfigInput,
    output: [] as IOutputParamItem[],
  });

  useMount(async () => {
    try {
      const ret = await getAppComponentDetailByCode(props.data.code!);
      const componentDetailCfg = JSON.parse(ret.config);
      setState({
        input: componentDetailCfg.input,
        output: componentDetailCfg.output,
      });
    } finally {
      setState({
        loading: false,
      });
    }
  });

  return (
    <Drawer
      className={styles['form-drawer']}
      width={960}
      open
      onClose={props.onClose}
      title={$i18n.get({
        id: 'main.pages.Component.AppComponent.components.DetailDrawer.index.detailView',
        dm: 'View Details',
      })}
    >
      {state.loading ? (
        <Spin className="loading-center" />
      ) : (
        <>
          <div
            className={classNames(styles['form-con'], 'flex flex-col gap-5')}
          >
            <div className={styles['form-title']}>
              {$i18n.get({
                id: 'main.pages.Component.AppComponent.components.DetailDrawer.index.basicInformation',
                dm: 'Basic Info',
              })}
            </div>
            <Flex vertical gap={16}>
              <div className={styles['form-item']}>
                <div className={styles.label}>
                  {$i18n.get({
                    id: 'main.pages.Component.AppComponent.components.DetailDrawer.index.componentName',
                    dm: 'Component Name',
                  })}
                </div>
                <div className={styles.value}>{props.data.name}</div>
              </div>
              <div className={styles['form-item']}>
                <div className={styles.label}>
                  {$i18n.get({
                    id: 'main.pages.Component.AppComponent.components.DetailDrawer.index.componentId',
                    dm: 'Component ID',
                  })}
                </div>
                <div className={styles.value}>{props.data.code}</div>
              </div>
              <div className={styles['form-item']}>
                <div className={styles.label}>
                  {$i18n.get({
                    id: 'main.pages.Component.AppComponent.components.DetailDrawer.index.componentDescription',
                    dm: 'Component Description',
                  })}
                </div>
                <div className={styles.value}>{props.data.description}</div>
              </div>
              <div className={styles['form-item']}>
                <div className={styles.label}>
                  {$i18n.get({
                    id: 'main.pages.Component.AppComponent.components.DetailDrawer.index.updateTime',
                    dm: 'Updated Time',
                  })}
                </div>
                <div className={styles.value}>
                  {dayjs(props.data.gmt_modified).format('YYYY-MM-DD HH:mm:ss')}
                </div>
              </div>
              <div className={styles['form-item']}>
                <div className={styles.label}>
                  {$i18n.get({
                    id: 'main.pages.Component.AppComponent.components.DetailDrawer.index.createTime',
                    dm: 'Created Time',
                  })}
                </div>
                <div className={styles.value}>
                  {dayjs(props.data.gmt_create).format('YYYY-MM-DD HH:mm:ss')}
                </div>
              </div>
            </Flex>
          </div>
          <div
            className={classNames(styles['form-con'], 'flex flex-col gap-5')}
          >
            <div className={styles['form-title']}>
              <span>
                {$i18n.get({
                  id: 'main.pages.Component.AppComponent.components.DetailDrawer.index.inputParameters',
                  dm: 'Input Parameters',
                })}
              </span>
            </div>
            <InputParamsComp
              disabled
              input={state.input}
              onChange={(val) =>
                setState({
                  input: {
                    ...state.input,
                    ...val,
                  },
                })
              }
            />
          </div>
          <div
            className={classNames(styles['form-con'], 'flex flex-col gap-5')}
          >
            <div className={styles['form-title']}>
              <span>
                {$i18n.get({
                  id: 'main.pages.Component.AppComponent.components.DetailDrawer.index.outputParameters',
                  dm: 'Output Parameters',
                })}
              </span>
            </div>
            <OutputParamsComp output={state.output} />
          </div>
        </>
      )}
    </Drawer>
  );
}
