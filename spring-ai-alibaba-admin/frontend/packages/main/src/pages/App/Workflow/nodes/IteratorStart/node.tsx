import type { IWorkFlowNode, NodeProps } from '@cordondata/flow';
import { BaseNode } from '@cordondata/flow';
import { memo } from 'react';

export default memo(function ParallelStartNode(
  props: NodeProps<IWorkFlowNode>,
) {
  return <BaseNode disableAction disableShowTargetHandle {...props}></BaseNode>;
});
