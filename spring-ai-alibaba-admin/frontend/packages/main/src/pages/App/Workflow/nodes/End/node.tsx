import type { IWorkFlowNode, NodeProps } from '@cordondata/flow';
import { BaseNode } from '@cordondata/flow';
import { memo } from 'react';

export default memo(function EndNode(props: NodeProps<IWorkFlowNode>) {
  return <BaseNode disableShowSourceHandle {...props}></BaseNode>;
});
