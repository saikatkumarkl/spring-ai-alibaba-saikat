import $i18n from '@/i18n';
import { Markdown } from '@spark-ai/chat';
import {
  CodeBlock,
  CollapsePanel,
  IconFont,
  message,
  parseJsonSafely,
} from '@spark-ai/design';
import styles from './index.module.less';

export default (props: {
  params: {
    arguments?: string; // input parameters
    output?: string; // output parameters
  };
}) => {
  const { params } = props;
  const inputIsJson = params.arguments
    ? parseJsonSafely(params.arguments, false, true) !== null
    : false;
  const outputIsJson = params.output
    ? parseJsonSafely(params.output, false, true) !== null
    : false;

  const handleCopy = async (value: string) => {
    try {
      await navigator.clipboard.writeText(value);
      message.success(
        $i18n.get({
          id: 'main.components.SparkChat.components.Steps.Plugin.index.copySuccess',
          dm: 'Copy successful',
        }),
      );
    } catch (error) {
      message.error(
        $i18n.get({
          id: 'main.components.SparkChat.components.Steps.Plugin.index.copyFailed',
          dm: 'Copy failed',
        }),
      );
    }
  };

  return (
    <div className={styles.container}>
      {!!params.arguments?.length && (
        <CollapsePanel
          title={$i18n.get({
            id: 'main.components.SparkChat.components.Steps.Plugin.index.inputParameters',
            dm: 'Input Parameters',
          })}
          collapsedHeight={64}
          expandedHeight={200}
          expandOnPanelClick={true}
          extra={
            <IconFont
              type="spark-copy-line"
              style={{ fontSize: '16px' }}
              onClick={() => handleCopy(params.arguments!)}
            />
          }
        >
          {inputIsJson ? (
            <CodeBlock language={'json'} value={params.arguments} />
          ) : (
            <div className="p-[12px]">
              <Markdown content={params.arguments || ''} baseFontSize={12} />
            </div>
          )}
        </CollapsePanel>
      )}
      {!!params.output?.length && (
        <CollapsePanel
          title={$i18n.get({
            id: 'main.components.SparkChat.components.Steps.Plugin.index.outputParameters',
            dm: 'Output Parameters',
          })}
          collapsedHeight={64}
          expandedHeight={200}
          expandOnPanelClick={true}
          extra={
            <IconFont
              type="spark-copy-line"
              style={{ fontSize: '16px' }}
              onClick={() => handleCopy(params.output!)}
            />
          }
        >
          {outputIsJson ? (
            <CodeBlock language={'json'} value={params.output} />
          ) : (
            <div className="p-[12px]">
              <Markdown content={params.output || ''} baseFontSize={12} />
            </div>
          )}
        </CollapsePanel>
      )}
    </div>
  );
};
