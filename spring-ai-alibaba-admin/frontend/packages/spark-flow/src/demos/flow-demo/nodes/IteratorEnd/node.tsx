import type { IWorkFlowNode } from '@cordondata/flow';
import { BaseNode } from '@cordondata/flow';
import { NodeProps } from '@xyflow/react';
import React, { memo } from 'react';

export default memo(function IteratorEndNode(props: NodeProps<IWorkFlowNode>) {
  return <BaseNode disableAction disableShowSourceHandle {...props}></BaseNode>;
});
