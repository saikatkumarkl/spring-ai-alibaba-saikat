import type { IWorkFlowNode, NodeProps } from '@cordondata/flow';
import { BaseNode } from '@cordondata/flow';
import { memo } from 'react';

export default memo(function MCPNode(props: NodeProps<IWorkFlowNode>) {
  return <BaseNode {...props}></BaseNode>;
});
