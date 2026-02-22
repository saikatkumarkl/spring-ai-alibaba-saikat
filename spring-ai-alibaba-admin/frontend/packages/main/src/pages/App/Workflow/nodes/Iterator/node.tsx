import type { IWorkFlowNode, NodeProps } from '@cordondata/flow';
import { GroupNode } from '@cordondata/flow';
import { memo } from 'react';

export default memo(function IteratorNode(props: NodeProps<IWorkFlowNode>) {
  return <GroupNode {...props} />;
});
