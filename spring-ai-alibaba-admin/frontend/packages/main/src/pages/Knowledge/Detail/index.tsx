import InnerLayout from '@/components/InnerLayout';
import {
  deleteDocuments,
  getDocumentsList,
  getKnowledgeDetail,
  listKnowledgeSyncs,
  browseDocumentIndex,
} from '@/services/knowledge';
import { useRequest, useSetState } from 'ahooks';
import { Modal } from 'antd';
import { Button, IconFont } from '@spark-ai/design';
import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { history } from 'umi';
import FileList from './components/FileList';
import Search from './components/Search';
import styles from './index.module.less';
import type { IFileItem } from './type';

import $i18n from '@/i18n';
import UploadModal from './components/UploadModal';

interface State {
  /** Current page number */
  current: number;
  /** Number of items per page */
  pageSize: number;
  /** Batch operation */
  operationable: boolean;
  /** Selected row key array */
  selectedRowKeys: React.Key[];
  /** File list data */
  list: IFileItem[];
  /** Search keyword */
  name: string;
  /** Total count */
  total: number;
  /** Index status */
  index_status: string;
  /** Document format */
  format: string;
}

const KnowledgeDetail: React.FC = () => {
  const { kb_id } = useParams<{ kb_id: string }>();
  const [state, setState] = useSetState<State>({
    current: 1,
    pageSize: 10,
    operationable: false,
    selectedRowKeys: [],
    list: [],
    name: '',
    total: 0,
    index_status: '',
    format: '',
  });
  const [knowledgeNmae, setKnowledgeNmae] = useState('');
  const [uploadModalVisible, setUploadModalVisible] = useState(false);
  const [isSourceBased, setIsSourceBased] = useState<boolean | null>(null);
  const [syncId, setSyncId] = useState<string | null>(null);
  const [indexInfo, setIndexInfo] = useState<{ index_name?: string; authority_index_name?: string; rag_index_name?: string }>({});

  // Check if KB is source-based by looking for sync records
  useEffect(() => {
    if (kb_id) {
      listKnowledgeSyncs(kb_id)
        .then((syncs) => {
          if (syncs && syncs.length > 0) {
            setIsSourceBased(true);
            setSyncId(syncs[0].sync_id);
          } else {
            setIsSourceBased(false);
          }
        })
        .catch(() => {
          setIsSourceBased(false);
        });
    }
  }, [kb_id]);

  const getList = () => {
    if (isSourceBased === null) return; // Wait for source check to complete

    if (isSourceBased && syncId) {
      // Browse the document index via the sync's actual index
      browseDocumentIndex(syncId, {
        current: state.current,
        size: state.pageSize,
        query: state.name || undefined,
      }).then((res: any) => {
        setState({
          list: res.records || [],
          total: res.total || 0,
          current: res.current || state.current,
          pageSize: res.size || state.pageSize,
        });
        setIndexInfo({
          index_name: res.index_name,
          authority_index_name: res.authority_index_name,
          rag_index_name: res.rag_index_name,
        });
      }).catch(() => {
        setState({ list: [], total: 0 });
      });
    } else {
      // Fetch documents from MySQL for file-upload-based KBs
      getDocumentsList({
        current: state.current,
        size: state.pageSize,
        kb_id: kb_id || '',
        name: state.name || undefined,
        index_status: state.index_status || undefined,
      }).then((res: any) => {
        setState({
          list: res.records,
          total: res.total,
          current: res.current,
          pageSize: res.size,
        });
      });
    }
  };

  useEffect(() => {
    getList();
  }, [
    isSourceBased,
    state.current,
    state.pageSize,
    state.name,
    state.index_status,
    state.format,
  ]);

  const handlePaginationChange = (newCurrent: number, newPageSize: number) => {
    setState({
      current: newCurrent,
      pageSize: newPageSize,
    });
  };

  const handleSelectionChange = (newSelectedRowKeys: React.Key[]) => {
    setState({ selectedRowKeys: newSelectedRowKeys });
  };

  const handleSearch = (value: string) => {
    setState({
      name: value,
      current: 1,
    });
  };

  const handleFilter = (type: string, value: string | string[]) => {
    setState((prevState) => ({
      ...prevState,
      [type]: value,
      current: 1,
    }));
  };

  const handleBatchOperation = () => {
    setState({
      operationable: !state.operationable,
    });
  };
  const handleDelete = (kb_id: string, doc_id: string) => {
    Modal.confirm({
      title: $i18n.get({
        id: 'main.pages.Knowledge.Detail.index.deleteData',
        dm: 'Delete Data',
      }),
      content: $i18n.get({
        id: 'main.pages.Knowledge.Detail.index.confirmDeleteData',
        dm: 'Are you sure you want to delete this data?',
      }),
      onOk: () => {
        deleteDocuments(kb_id, doc_id).then(() => {
          let current = state.current;
          if (state.list.length === 1 && current > 1) {
            current -= 1;
            setState({
              current,
            });
          }
          getList();
        });
      },
    });
  };
  const handleClickAction = (key: string, kb_id: string, doc_id: string) => {
    switch (key) {
      case 'delete':
        handleDelete(kb_id, doc_id);
        break;
      default:
        break;
    }
  };

  useRequest(() => getKnowledgeDetail(kb_id as string), {
    onSuccess(res) {
      setKnowledgeNmae(res.name);
    },
  });

  const refreshList = () => {
    if (state.current !== 1) {
      setState({
        current: 1,
      });
    } else {
      getList();
    }
    handleSelectionChange([]);
  };

  return (
    <InnerLayout
      breadcrumbLinks={[
        {
          title: knowledgeNmae,
          path: '/knowledge',
        },
        {
          title: $i18n.get({
            id: 'main.pages.Knowledge.Detail.index.fileList',
            dm: 'File List',
          }),
        },
      ]}
      right={
        <div style={{ display: 'flex', gap: 8 }}>
          <Button
            type="default"
            icon={<IconFont type="spark-sync-line" />}
            onClick={() => history.push(`/knowledge/sync/${kb_id}`)}
          >
            {$i18n.get({
              id: 'main.pages.Knowledge.Detail.index.syncStatus',
              dm: 'Sync Status',
            })}
          </Button>
          <Button
            type="default"
            icon={<IconFont type="spark-edit-line" />}
            onClick={() => history.push(`/knowledge/edit/${kb_id}`)}
          >
            {$i18n.get({
              id: 'main.pages.Knowledge.Detail.index.editSettings',
              dm: 'Edit Settings',
            })}
          </Button>
        </div>
      }
    >
      <div className={styles.container}>
        {/* Index info banner for source-based KBs */}
        {isSourceBased && indexInfo.index_name && (
          <div style={{
            background: 'var(--ag-ant-color-fill-quaternary, #fafafa)',
            borderRadius: 8,
            padding: '10px 16px',
            marginBottom: 12,
            display: 'flex',
            gap: 24,
            fontSize: 12,
            color: 'var(--ag-ant-color-text-secondary)',
            alignItems: 'center',
          }}>
            <span><strong>Document Index:</strong> <code>{indexInfo.index_name}</code></span>
            {indexInfo.authority_index_name && (
              <span><strong>Authority Index:</strong> <code>{indexInfo.authority_index_name}</code></span>
            )}
            {indexInfo.rag_index_name && (
              <span><strong>RAG Index:</strong> <code>{indexInfo.rag_index_name}</code></span>
            )}
          </div>
        )}

        <Search
          className={styles.search}
          onSearch={handleSearch}
          onFilter={handleFilter}
          onBatchOperation={handleBatchOperation}
          setUploadModalVisible={setUploadModalVisible}
          isSourceBased={isSourceBased === true}
          {...state}
        />

        <FileList
          onSelectionChange={handleSelectionChange}
          onPaginationChange={handlePaginationChange}
          onExitOperation={handleBatchOperation}
          handleClickAction={handleClickAction}
          refreshList={refreshList}
          isSourceBased={isSourceBased === true}
          syncId={syncId}
          {...state}
        />
      </div>
      {uploadModalVisible && !isSourceBased && (
        <UploadModal
          onClose={() => setUploadModalVisible(false)}
          refreshList={refreshList}
        />
      )}
    </InnerLayout>
  );
};

export default KnowledgeDetail;
