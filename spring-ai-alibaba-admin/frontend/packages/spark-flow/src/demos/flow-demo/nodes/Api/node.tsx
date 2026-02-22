import type { IWorkFlowNode } from '@cordondata/flow';
import { BaseNode } from '@cordondata/flow';
import { NodeProps } from '@xyflow/react';
import React, { memo } from 'react';
import { IApiNodeParam } from '../../types/flow';

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
