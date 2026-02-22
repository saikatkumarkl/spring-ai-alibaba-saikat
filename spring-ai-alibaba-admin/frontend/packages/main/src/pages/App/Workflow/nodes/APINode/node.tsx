import type { IWorkFlowNode, NodeProps } from '@cordondata/flow';
import { BaseNode } from '@cordondata/flow';
import { memo } from 'react';
import { IApiNodeParam } from '../../types';

export default memo(function ApiNode(props: NodeProps<IWorkFlowNode>) {
  return (
    <BaseNode
      hasFailBranch={
        (props.data.node_param as IApiNodeParam).try_catch_config.strategy ===
        'failBranch'
      }
      {...props}
    ></BaseNode>
  );
});
