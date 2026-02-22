import type { IWorkFlowNode, NodeProps } from '@cordondata/flow';
import { BaseNode } from '@cordondata/flow';
import { memo } from 'react';
import { IScriptNodeParam } from '../../types';

export default memo(function Script(props: NodeProps<IWorkFlowNode>) {
  return (
    <BaseNode
      hasFailBranch={
        (props.data.node_param as IScriptNodeParam).try_catch_config
          .strategy === 'failBranch'
      }
      {...props}
    ></BaseNode>
  );
});
