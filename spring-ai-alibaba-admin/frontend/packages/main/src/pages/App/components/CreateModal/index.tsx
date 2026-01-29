import $i18n from '@/i18n';
import { IAppType } from '@/services/appComponent';
import { createApp } from '@/services/appManage';
import uniqueId from '@/utils/uniqueId';
import { Button, getCommonConfig, message, Modal } from '@spark-ai/design';
import { useSetState } from 'ahooks';
import { Flex } from 'antd';
import classNames from 'classnames';
import { initAppConfig } from '../../utils';
import styles from './index.module.less';

interface ICreateModalProps {
  onCancel: () => void;
  onOk: (val: { type: IAppType; app_id: string }) => void;
}

const options = [
  {
    label: $i18n.get({
      id: 'main.pages.App.components.CreateModal.index.intelligentAgentApp',
      dm: 'Agent Application',
    }),
    name: $i18n.get({
      id: 'main.pages.App.components.CreateModal.index.intelligentAgent',
      dm: 'Agent',
    }),
    value: 'basic',
  },
  {
    label: $i18n.get({
      id: 'main.pages.App.components.Card.index.workflowApp',
      dm: 'Workflow Application',
    }),
    name: $i18n.get({
      id: 'main.pages.App.components.CreateModal.index.workflow',
      dm: 'Workflow',
    }),
    value: 'workflow',
  },
];

export default function CreateModal(props: ICreateModalProps) {
  const darkMode = getCommonConfig().isDarkMode;

  const [state, setState] = useSetState({
    activeRecord: options[0],
    createLoading: false,
  });
  const createAppByCode = (activeRecord: { name: string; value: IAppType }) => {
    if (state.createLoading) return;

    setState({ createLoading: true });
    createApp({
      name: `${activeRecord.name}_${uniqueId(4)}`,
      type: activeRecord.value as IAppType,
      config: initAppConfig(activeRecord.value as IAppType),
    })
      .then((res) => {
        message.success(
          $i18n.get({
            id: 'main.pages.App.components.CreateModal.index.createSuccess',
            dm: 'Created successfully',
          }),
        );
        props.onOk({
          type: activeRecord.value as IAppType,
          app_id: res,
        });
        setState({ createLoading: false });
      })
      .catch(() => {
        setState({ createLoading: false });
      });
  };
  return (
    <Modal
      open
      className={styles['app-create-modal']}
      footer={null}
      width={888}
      onCancel={props.onCancel}
      title={$i18n.get({
        id: 'main.pages.App.components.CreateModal.index.createApp',
        dm: 'Create App',
      })}
      styles={{
        body: {
          padding: 40,
        },
      }}
    >
      <Flex gap={40}>
        <div className={classNames(styles['item'])}>
          <img src={`/images/createAgent${darkMode ? 'Dark' : ''}.png`} />
          <div className={styles['header']}>
            <div className={styles['title']}>
              {$i18n.get({
                id: 'main.pages.App.components.CreateModal.index.intelligentAgentApp',
                dm: 'Agent Application',
              })}
            </div>
            <Button
              iconType="spark-plus-line"
              type="primary"
              onClick={() =>
                createAppByCode(options[0] as { name: string; value: IAppType })
              }
            >
              {$i18n.get({
                id: 'main.pages.App.components.CreateModal.index.create',
                dm: 'Create',
              })}
            </Button>
          </div>
          <div className={styles['desc']}>
            {$i18n.get({
              id: 'main.pages.App.components.CreateModal.index.buildIntelligentAgentApp',
              dm: 'Build agent applications that connect knowledge, data, and services. Powerful RAG, MCP, plugins, memory, and component capabilities. Compatible with multiple models, suitable for intelligent assistant and conversational scenarios.',
            })}
          </div>
        </div>
        <div className={classNames(styles['item'])}>
          <img src={`/images/createFlow${darkMode ? 'Dark' : ''}.png`} />
          <div className={styles['header']}>
            <div className={styles['title']}>
              {$i18n.get({
                id: 'main.pages.App.components.CreateModal.index.workflowApp',
                dm: 'Workflow Application',
              })}
            </div>
            <Button
              iconType="spark-plus-line"
              type="primary"
              onClick={() =>
                createAppByCode(options[1] as { name: string; value: IAppType })
              }
            >
              {$i18n.get({
                id: 'main.pages.App.components.CreateModal.index.create',
                dm: 'Create',
              })}
            </Button>
          </div>
          <div className={styles['desc']}>
            {$i18n.get({
              id: 'main.pages.App.components.CreateModal.index.designWorkflow',
              dm: 'Design custom workflows on canvas, quickly implement business logic design and validation. Supports LLM, agent, component, API, and other node types. Suitable for multi-agent collaboration and process-oriented scenarios.',
            })}
          </div>
        </div>
      </Flex>
    </Modal>
  );
}
