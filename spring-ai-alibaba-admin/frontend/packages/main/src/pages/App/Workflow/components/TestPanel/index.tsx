import $i18n from '@/i18n';
import {
  DragPanel,
  PanelContainer,
  SelectWithDesc,
  useFlowDebugInteraction,
  useStore,
} from '@spark-ai/flow';
import { message } from 'antd';
import { useMemo, useState } from 'react';
import { useWorkflowAppStore } from '../../context/WorkflowAppProvider';
import ChatTestPanel from '../ChatTestPanel';
import TaskTestPanel from '../TaskTestPanel';
import styles from './index.module.less';

const TEST_OPTIONS = [
  {
    label: $i18n.get({
      id: 'main.pages.App.Workflow.components.TestPanel.index.textChat',
      dm: 'Text Conversation',
    }),
    value: 'chat',
    desc: $i18n.get({
      id: 'main.pages.App.Workflow.components.TestPanel.index.chatBasedInteraction',
      dm: 'LLM-based conversational interaction, suitable for complex multi-turn dialogues',
    }),
  },
  {
    label: $i18n.get({
      id: 'main.pages.App.Workflow.components.TestPanel.index.textGeneration',
      dm: 'Text Generation',
    }),
    value: 'task',
    desc: $i18n.get({
      id: 'main.pages.App.Workflow.components.TestPanel.index.singleRoundInteraction',
      dm: 'Single-turn generative interaction, suitable for information extraction and text creation',
    }),
  },
];

export default function TestPanel() {
  const showTest = useWorkflowAppStore((state) => state.showTest);
  const setShowTest = useWorkflowAppStore((state) => state.setShowTest);
  const [testType, setTestType] = useState('task');
  const taskStore = useStore((state) => state.taskStore);
  const { clearTaskStore } = useFlowDebugInteraction();

  const memoTitle = useMemo(() => {
    return (
      <div className="flex gap-[8px] items-center">
        <span>
          {$i18n.get({
            id: 'main.pages.App.Workflow.components.TestPanel.index.test',
            dm: 'Test',
          })}
        </span>
        <SelectWithDesc
          className={styles.testTypeSelector}
          onChange={(val) => {
            if (
              taskStore?.task_status === 'executing' ||
              taskStore?.task_status === 'pause'
            ) {
              message.warning(
                $i18n.get({
                  id: 'main.pages.App.Workflow.components.TestPanel.executingWorkflow',
                  dm: 'Workflow is running, please stop first',
                }),
              );
              return;
            }
            setTestType(val);
          }}
          value={testType}
          variant="borderless"
          options={TEST_OPTIONS}
          popupMatchSelectWidth={false}
        />
      </div>
    );
  }, [testType, taskStore?.task_status]);

  const handleClose = () => {
    if (
      taskStore?.task_status === 'executing' ||
      taskStore?.task_status === 'pause'
    ) {
      message.warning(
        $i18n.get({
          id: 'main.pages.App.Workflow.components.TestPanel.executingWorkflow',
          dm: 'Workflow is running, please stop first',
        }),
      );
      return;
    }
    setShowTest(false);
    clearTaskStore();
  };

  if (!showTest) return null;

  return (
    <DragPanel minWidth={500} defaultWidth={500} maxWidth={600}>
      <PanelContainer noPadding onClose={handleClose} title={memoTitle}>
        {testType === 'chat' ? <ChatTestPanel /> : <TaskTestPanel />}
      </PanelContainer>
    </DragPanel>
  );
}
