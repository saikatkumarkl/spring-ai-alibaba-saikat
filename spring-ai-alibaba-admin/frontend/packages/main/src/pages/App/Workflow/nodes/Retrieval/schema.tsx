import $i18n from '@/i18n';
import { INodeSchema } from '@spark-ai/flow';
import { IRetrievalNodeData, IRetrievalNodeParam } from '../../types';
import { transformInputParams } from '../../utils';

const getRetrievalCheckValid = (data: IRetrievalNodeData) => {
  const errMsgs: { label: string; error: string }[] = [];
  const { input_params } = data;
  if (input_params.some((item) => !item.value)) {
    errMsgs.push({
      label: $i18n.get({
        id: 'main.pages.App.Workflow.nodes.Retrieval.schema.input',
        dm: 'Input',
      }),
      error: $i18n.get({
        id: 'main.pages.App.Workflow.nodes.Retrieval.schema.requiredField',
        dm: 'Cannot be empty',
      }),
    });
  }

  if (!data.node_param.knowledge_base_ids?.length) {
    errMsgs.push({
      label: $i18n.get({
        id: 'main.pages.App.Workflow.nodes.Retrieval.schema.knowledgeBase',
        dm: 'Knowledge Base',
      }),
      error: $i18n.get({
        id: 'main.pages.App.Workflow.nodes.Retrieval.schema.requiredField',
        dm: 'Cannot be empty',
      }),
    });
  }

  return errMsgs;
};

const getRetrievalRefVariables = (data: IRetrievalNodeData) => {
  const variableKeyMap: Record<string, boolean> = {};

  transformInputParams(data.input_params, variableKeyMap);

  return Object.keys(variableKeyMap);
};

export const RetrievalSchema: INodeSchema = {
  type: 'Retrieval',
  title: $i18n.get({
    id: 'main.pages.App.Workflow.nodes.Retrieval.schema.knowledgeBase',
    dm: 'Knowledge Base',
  }),
  iconType: 'spark-book-line',
  desc: $i18n.get({
    id: 'main.pages.App.Workflow.nodes.Retrieval.schema.retrieveMatchingInfo',
    dm: 'Recall the most matching information from selected knowledge based on input variables.',
  }),
  defaultParams: {
    input_params: [
      {
        key: 'input',
        type: 'String',
        value_from: 'refer',
        value: void 0,
      },
    ],

    output_params: [
      {
        key: 'chunk_list',
        type: 'Array<Object>',
        properties: [
          {
            key: 'doc_id',
            type: 'String',
          },
          {
            key: 'doc_name',
            type: 'String',
          },
          {
            key: 'title',
            type: 'String',
          },
          {
            key: 'text',
            type: 'String',
          },
          {
            key: 'score',
            type: 'Number',
          },
          {
            key: 'page_number',
            type: 'Number',
          },
          {
            key: 'chunk_id',
            type: 'String',
          },
        ],
      },
    ],

    node_param: {
      knowledge_base_ids: [],
      prompt_strategy: '',
      top_k: 10,
      similarity_threshold: 0.8,
    } as IRetrievalNodeParam,
  },
  isSystem: false,
  allowSingleTest: true,
  disableConnectSource: true,
  disableConnectTarget: true,
  groupLabel: $i18n.get({
    id: 'main.pages.App.Workflow.nodes.Retrieval.schema.basic',
    dm: 'Basic',
  }),
  checkValid: (val) => getRetrievalCheckValid(val as IRetrievalNodeData),
  bgColor: 'var(--ag-ant-color-purple-hover)',
  getRefVariables: (val) => getRetrievalRefVariables(val as IRetrievalNodeData),
};
