import $i18n from '@/i18n';
import { Button } from '@spark-ai/design';
import { useStore } from '@spark-ai/flow';
import { Tooltip } from 'antd';
import { memo, useMemo } from 'react';

interface IVersionManageBtnProps {
  setShowHistoryPanel: (show: boolean) => void;
}

export default memo(function VersionManagerBtn(props: IVersionManageBtnProps) {
  const taskStore = useStore((state) => state.taskStore);
  const isFlushing = useMemo(() => {
    return ['executing', 'pause'].includes(taskStore?.task_status || '');
  }, [taskStore?.task_status]);

  return (
    <Tooltip
      trigger={'hover'}
      title={
        isFlushing
          ? $i18n.get({
              id: 'main.pages.App.AssistantAppEdit.components.AppActions.index.dialogProcessingProhibitedSwitchVersion',
              dm: 'Conversation in progress, version switching disabled',
            })
          : $i18n.get({
              id: 'main.components.HistoryPanel.index.historyVersion',
              dm: 'History Versions',
            })
      }
    >
      <Button
        iconType="spark-auditLog-line"
        onClick={() => {
          props.setShowHistoryPanel(true);
        }}
        disabled={isFlushing}
      >
        {$i18n.get({
          id: 'main.pages.App.AssistantAppEdit.components.AppActions.index.versionManagement',
          dm: 'Version Management',
        })}
      </Button>
    </Tooltip>
  );
});
