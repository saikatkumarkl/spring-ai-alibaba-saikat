import type { IWorkFlowNode, NodeProps } from '@cordondata/flow';
import { BaseNode } from '@cordondata/flow';
import { memo } from 'react';
import { IPluginNodeParam } from '../../types';

export default memo(function PluginNode(props: NodeProps<IWorkFlowNode>) {
  return (
    <BaseNode
      hasFailBranch={
        (props.data.node_param as IPluginNodeParam).try_catch_config
          .strategy === 'failBranch'
      }
      {...props}
    ></BaseNode>
  );
});
