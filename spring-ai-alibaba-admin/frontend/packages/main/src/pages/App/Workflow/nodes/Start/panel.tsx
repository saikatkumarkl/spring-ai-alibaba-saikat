import $i18n from '@/i18n';
import {
  CustomOutputsFormWrap,
  useNodeDataUpdate,
  useNodesReadOnly,
} from '@spark-ai/flow';
import { Flex } from 'antd';
import { memo } from 'react';
import { IStartNodeData } from '../../types';

export default memo((props: { id: string; data: IStartNodeData }) => {
  const { handleNodeDataUpdate } = useNodeDataUpdate();
  const { nodesReadOnly } = useNodesReadOnly();

  return (
    <>
      <div className="spark-flow-panel-form-section">
        <Flex gap={12} vertical>
          <div className="spark-flow-panel-form-title">
            {$i18n.get({
              id: 'main.pages.App.Workflow.nodes.Start.panel.input',
              dm: 'Input',
            })}
          </div>
          <div className="spark-flow-panel-form-second-title">
            {$i18n.get({
              id: 'main.pages.App.Workflow.nodes.Start.panel.customVariables',
              dm: 'Custom Variables',
            })}
          </div>
          <CustomOutputsFormWrap
            readyOnly={nodesReadOnly}
            value={props.data.output_params}
            onChange={(output_params) => {
              handleNodeDataUpdate({
                id: props.id,
                data: {
                  output_params,
                },
              });
            }}
          />
        </Flex>
      </div>
      <div className="spark-flow-panel-form-section">
        <Flex gap={12} vertical>
          <div className="spark-flow-panel-form-second-title">
            {$i18n.get({
              id: 'main.pages.App.Workflow.nodes.Start.panel.predefinedVariables',
              dm: 'Predefined Variables',
            })}
          </div>
          <CustomOutputsFormWrap
            readyOnly
            value={[
              {
                key: 'query',
                type: 'String',
                desc: $i18n.get({
                  id: 'main.pages.App.Workflow.nodes.Start.panel.userQuery',
                  dm: 'User query',
                }),
              },
            ]}
          />
        </Flex>
      </div>
    </>
  );
});
