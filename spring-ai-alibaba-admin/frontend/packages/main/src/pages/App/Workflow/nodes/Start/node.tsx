import type { IWorkFlowNode, NodeProps } from '@cordondata/flow';
import { BaseNode } from '@cordondata/flow';
import { memo } from 'react';

export default memo(function StartNode(props: NodeProps<IWorkFlowNode>) {
  return <BaseNode disableShowTargetHandle {...props}></BaseNode>;
});
