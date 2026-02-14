import InnerLayout from '@/components/InnerLayout';
import SliderInput from '@/components/SliderInput';
import $i18n from '@/i18n';
import ModelSelector from '@/pages/Knowledge/components/ModelSelector';
import { getKnowledgeDetail, updateKnowledge, getDocumentsList, deleteDocuments } from '@/services/knowledge';
import { IKnowledgeDetail } from '@/types/knowledge';
import { IFileItem } from '@/pages/Knowledge/Detail/type';
import { getPreviewUrl, downloadFile } from '@/request/upload';
import {
  Button,
  Form,
  IconFont,
  Input,
  message,
  Tooltip,
} from '@spark-ai/design';
import { useRequest, useSetState } from 'ahooks';
import { Modal as AntModal, Table, Space, Tag, Empty, Spin } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useRef, useEffect, useState, useCallback } from 'react';
import { useParams } from 'react-router-dom';
import { history } from 'umi';
import styles from './index.module.less';

export default function Editor() {
  const { kb_id } = useParams<{ kb_id: string }>();
  const formRef = useRef<any>(null);
  const [state, setState] = useSetState({
    name: '',
    description: '',
    embedding_value: '',
    embedding_model: '',
    embedding_provider: '',
    rerank_value: '',
    rerank_model: '',
    rerank_provider: '',
    similarity_threshold: 0.2,
    top_k: 3,
  });

  // Documents state
  const [docs, setDocs] = useState<IFileItem[]>([]);
  const [docsLoading, setDocsLoading] = useState(false);
  const [docsPagination, setDocsPagination] = useState({ current: 1, pageSize: 5, total: 0 });

  const fetchDocs = useCallback((page = 1, pageSize = 5) => {
    if (!kb_id) return;
    setDocsLoading(true);
    getDocumentsList({ current: page, size: pageSize, kb_id })
      .then((res: any) => {
        setDocs(res.records || []);
        setDocsPagination({ current: res.current || page, pageSize: res.size || pageSize, total: res.total || 0 });
      })
      .finally(() => setDocsLoading(false));
  }, [kb_id]);

  useEffect(() => {
    fetchDocs();
  }, [fetchDocs]);

  useRequest(() => getKnowledgeDetail(kb_id || ''), {
    onSuccess: (data: IKnowledgeDetail) => {
      const { index_config, search_config } = data;
      setState({
        name: data.name,
        description: data.description,
        embedding_value: index_config.embedding_model
          ? `${index_config.embedding_provider}@@@${index_config.embedding_model}`
          : '',
        embedding_model: index_config?.embedding_model,
        embedding_provider: index_config?.embedding_provider,
        rerank_value: search_config.rerank_model
          ? `${search_config.rerank_provider}@@@${search_config.rerank_model}`
          : '',
        rerank_model: search_config?.rerank_model,
        rerank_provider: search_config?.rerank_provider,
        similarity_threshold: search_config.similarity_threshold,
        top_k: search_config.top_k,
      });
    },
  });

  const changeFormValue = (payload: any) => {
    setState((prev) => ({
      ...prev,
      ...payload,
    }));
  };
  const handleSave = () => {
    validatedFormValues()
      .then(() => {
        const {
          top_k,
          similarity_threshold,
          rerank_provider,
          rerank_model,
          embedding_provider,
          embedding_model,
          ...rest
        } = state;
        const params = {
          kb_id: kb_id?.toString() || '',
          search_config: {
            top_k,
            similarity_threshold,
            rerank_provider,
            rerank_model,
          },
          index_config: {
            embedding_provider,
            embedding_model,
          },
          ...rest,
        };
        updateKnowledge(params).then(() => {
          message.success(
            $i18n.get({
              id: 'main.pages.Knowledge.Editor.index.saveSuccess',
              dm: 'Saved successfully',
            }),
          );
          history.push('/knowledge');
        });
      })
      .catch((err) => {
        message.error(err.message);
      });
  };
  const validatedFormValues = () => {
    return new Promise((resolve, reject) => {
      if (!state.name?.trim()) {
        reject(
          $i18n.get({
            id: 'main.pages.Knowledge.Create.index.pleaseEnterKnowledgeBaseName',
            dm: 'Please enter knowledge base name',
          }),
        );
        return;
      }
      if (!state.embedding_value?.trim()) {
        reject(
          $i18n.get({
            id: 'main.pages.Knowledge.Create.index.pleaseSelectEmbeddingModel',
            dm: 'Please select Embedding model',
          }),
        );
        return;
      }

      if (!state.rerank_value?.trim()) {
        reject(
          $i18n.get({
            id: 'main.pages.Knowledge.Create.index.pleaseSelectRerankModel',
            dm: 'Please select Rerank model',
          }),
        );
        return;
      }
      resolve(state);
    });
  };
  const handleCancel = () => {
    AntModal.confirm({
      title: (
        <span className={styles['confirm-title']}>
          {$i18n.get({
            id: 'main.pages.Knowledge.Editor.index.confirmDiscardEditing',
            dm: 'Confirm to discard editing knowledge base?',
          })}
        </span>
      ),

      icon: (
        <IconFont
          type="spark-warningCircle-line"
          className={styles['warning-icon']}
        />
      ),

      content: (
        <span className={styles['confirm-content']}>
          {$i18n.get({
            id: 'main.pages.Knowledge.Editor.index.discardChangesDataNotSaved',
            dm: 'After discarding, the data you just filled in will not be saved. Please proceed with caution.',
          })}
        </span>
      ),

      okText: $i18n.get({
        id: 'main.pages.Knowledge.Editor.index.confirmDiscard',
        dm: 'Confirm Discard',
      }),
      cancelText: $i18n.get({
        id: 'main.pages.Knowledge.Editor.index.continueEditing',
        dm: 'Continue Editing',
      }),
      onOk: () => {
        history.push('/knowledge');
      },
    });
  };

  const handleDeleteDoc = (doc: IFileItem) => {
    AntModal.confirm({
      title: 'Delete Document',
      content: `Are you sure you want to delete "${doc.name}"? This will also remove all its chunks.`,
      okText: 'Delete',
      okButtonProps: { danger: true },
      cancelText: 'Cancel',
      onOk: () => {
        deleteDocuments(doc.kb_id, doc.doc_id).then(() => {
          message.success('Document deleted');
          fetchDocs(docsPagination.current, docsPagination.pageSize);
        });
      },
    });
  };

  const handlePreviewDoc = async (doc: IFileItem) => {
    if (!doc.path) {
      message.warning('Document path not available for preview');
      return;
    }
    try {
      const url = await getPreviewUrl(doc.path);
      if (url) {
        window.open(url, '_blank');
      } else {
        message.warning('Could not generate preview URL');
      }
    } catch {
      message.error('Failed to generate preview URL');
    }
  };

  const handleDownloadDoc = async (doc: IFileItem) => {
    if (!doc.path) {
      message.warning('Document path not available for download');
      return;
    }
    try {
      await downloadFile(doc.path, doc.name);
    } catch {
      message.error('Failed to download file');
    }
  };

  const formatFileSize = (size: number) => {
    if (size / 1024 / 1024 >= 1) return `${(size / 1024 / 1024).toFixed(2)} MB`;
    return `${(size / 1024).toFixed(2)} KB`;
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'completed': return 'green';
      case 'processing': return 'blue';
      case 'pending': case 'uploaded': return 'orange';
      case 'failed': return 'red';
      default: return 'default';
    }
  };

  const docColumns: ColumnsType<IFileItem> = [
    {
      title: 'Document Name',
      dataIndex: 'name',
      key: 'name',
      ellipsis: true,
    },
    {
      title: 'Status',
      dataIndex: 'index_status',
      key: 'index_status',
      width: 110,
      render: (status: string) => (
        <Tag color={getStatusColor(status)}>{status}</Tag>
      ),
    },
    {
      title: 'Format',
      dataIndex: 'format',
      key: 'format',
      width: 70,
      render: (f: string) => <span style={{ textTransform: 'uppercase', fontSize: 12 }}>{f}</span>,
    },
    {
      title: 'Size',
      dataIndex: 'size',
      key: 'size',
      width: 100,
      render: (size: number) => formatFileSize(size),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 200,
      render: (_: any, record: IFileItem) => (
        <Space size={4}>
          <Button
            type="link"
            size="small"
            onClick={() => handlePreviewDoc(record)}
            title="Open in browser (if supported)"
          >
            Preview
          </Button>
          <Button
            type="link"
            size="small"
            onClick={() => handleDownloadDoc(record)}
          >
            Download
          </Button>
          <Button
            type="link"
            size="small"
            danger
            onClick={() => handleDeleteDoc(record)}
          >
            Delete
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <InnerLayout
      breadcrumbLinks={[
        {
          title: $i18n.get({
            id: 'main.pages.Knowledge.Editor.index.knowledgeBase',
            dm: 'Knowledge Base',
          }),
          path: '/knowledge',
        },
        {
          title: $i18n.get({
            id: 'main.pages.Knowledge.Editor.index.editKnowledgeBase',
            dm: 'Edit Knowledge Base',
          }),
        },
      ]}
      bottom={
        <div className={styles['footer']}>
          <div className={styles['footer-btn']}>
            <Button
              type="primary"
              onClick={() => {
                validatedFormValues()
                  .then(() => {
                    handleSave();
                  })
                  .catch((errInfo) => message.warning(errInfo));
              }}
            >
              {$i18n.get({
                id: 'main.pages.Knowledge.Editor.index.save',
                dm: 'Save',
              })}
            </Button>
            <Button onClick={handleCancel}>
              {$i18n.get({
                id: 'main.pages.Knowledge.Editor.index.cancel',
                dm: 'Cancel',
              })}
            </Button>
          </div>
        </div>
      }
    >
      <div className={styles['container']}>
        <Form layout="vertical" ref={formRef}>
          <Form.Item
            label={$i18n.get({
              id: 'main.pages.Knowledge.Editor.index.knowledgeBaseName',
              dm: 'Knowledge Base Name',
            })}
            required
          >
            <Input
              placeholder={$i18n.get({
                id: 'main.pages.Knowledge.Editor.index.enterKnowledgeBaseName',
                dm: 'Please enter knowledge base name',
              })}
              value={state.name}
              onChange={(e) => {
                changeFormValue({ name: e.target.value });
              }}
            />
          </Form.Item>
          <Form.Item
            label={$i18n.get({
              id: 'main.pages.Knowledge.Editor.index.knowledgeBaseDescription',
              dm: 'Knowledge Base Description',
            })}
          >
            <Input.TextArea
              placeholder={$i18n.get({
                id: 'main.pages.Knowledge.Editor.index.enterKnowledgeBaseDescription',
                dm: 'Please enter knowledge base description',
              })}
              value={state.description}
              onChange={(e) => {
                changeFormValue({ description: e.target.value });
              }}
            />
          </Form.Item>
          <Form.Item
            label={
              <div className={styles['form-item-label']}>
                <span>
                  {$i18n.get({
                    id: 'main.pages.Knowledge.Editor.index.embeddingModel',
                    dm: 'Embedding Model',
                  })}
                </span>
                <Tooltip
                  title={$i18n.get({
                    id: 'main.pages.Knowledge.Editor.index.modelConvertTextToVector',
                    dm: 'A model that converts text into vector representations, mapping text information to low-dimensional dense vector spaces for computer understanding of text semantics, supporting subsequent similarity calculations.',
                  })}
                >
                  <IconFont
                    type="spark-info-line"
                    className={styles['info-icon']}
                  />
                </Tooltip>
              </div>
            }
            required
          >
            <ModelSelector
              value={state.embedding_value}
              modelType="text_embedding"
              onChange={(val: string) => {
                changeFormValue({
                  embedding_value: val,
                  embedding_model: val.split('@@@')[1],
                  embedding_provider: val.split('@@@')[0],
                });
              }}
            />
          </Form.Item>
          <Form.Item
            label={
              <div className={styles['form-item-label']}>
                <span>
                  {$i18n.get({
                    id: 'main.pages.Knowledge.Editor.index.rerankModel',
                    dm: 'Rerank Model',
                  })}
                </span>
                <Tooltip
                  title={$i18n.get({
                    id: 'main.pages.Knowledge.Editor.index.rerankModelReorderResults',
                    dm: 'The Rerank model reorders search results after retrieval, adjusting result order with more precise algorithms to place more relevant content at the top, improving search result quality.',
                  })}
                >
                  <IconFont
                    type="spark-info-line"
                    className={styles['info-icon']}
                  />
                </Tooltip>
              </div>
            }
            required
          >
            <ModelSelector
              value={state.rerank_value}
              modelType="rerank"
              onChange={(val: string) => {
                changeFormValue({
                  rerank_value: val,
                  rerank_model: val.split('@@@')[1],
                  rerank_provider: val.split('@@@')[0],
                });
              }}
            />
          </Form.Item>
          <Form.Item
            label={
              <div className={styles['form-item-label']}>
                <span>
                  {$i18n.get({
                    id: 'main.pages.Knowledge.Editor.index.similarityThreshold',
                    dm: 'Similarity Threshold',
                  })}
                </span>
                <Tooltip
                  title={$i18n.get({
                    id: 'main.pages.Knowledge.Editor.index.thresholdMeasureSimilarity',
                    dm: 'A threshold value used to measure the degree of similarity between texts or data. When the calculated text similarity reaches or exceeds this value, the text will be returned.',
                  })}
                >
                  <IconFont
                    type="spark-info-line"
                    className={styles['info-icon']}
                  />
                </Tooltip>
              </div>
            }
          >
            <SliderInput
              min={0.01}
              max={0.99}
              step={0.01}
              style={{ width: 480 }}
              value={state.similarity_threshold}
              onChange={(val) => {
                changeFormValue({ similarity_threshold: val });
              }}
            />
          </Form.Item>
          <Form.Item
            label={
              <div className={styles['form-item-label']}>
                <span>Topk</span>
                <Tooltip
                  title={$i18n.get({
                    id: 'main.pages.Knowledge.Editor.index.topKReturnObjects',
                    dm: 'Top-k represents the number of objects that meet similarity requirements returned after reranking',
                  })}
                >
                  <IconFont
                    type="spark-info-line"
                    className={styles['info-icon']}
                  />
                </Tooltip>
              </div>
            }
          >
            <SliderInput
              min={1}
              max={10}
              step={1}
              style={{ width: 480 }}
              value={state.top_k}
              onChange={(val) => {
                changeFormValue({ top_k: val });
              }}
            />
          </Form.Item>
        </Form>

        {/* Documents Section */}
        <div className={styles['docs-section']}>
          <div className={styles['docs-header']}>
            <h3 className={styles['docs-title']}>
              <IconFont type="spark-document-line" style={{ marginRight: 8 }} />
              Attached Documents
            </h3>
            <div className={styles['docs-actions']}>
              <Button
                type="default"
                size="small"
                onClick={() => history.push(`/knowledge/${kb_id}`)}
              >
                Manage Documents
              </Button>
            </div>
          </div>
          {docsLoading ? (
            <div style={{ textAlign: 'center', padding: 32 }}>
              <Spin />
            </div>
          ) : docs.length > 0 ? (
            <Table
              dataSource={docs}
              columns={docColumns}
              rowKey="doc_id"
              size="small"
              pagination={{
                current: docsPagination.current,
                pageSize: docsPagination.pageSize,
                total: docsPagination.total,
                showSizeChanger: false,
                size: 'small',
                onChange: (page, pageSize) => fetchDocs(page, pageSize),
              }}
            />
          ) : (
            <Empty
              description="No documents attached yet"
              image={Empty.PRESENTED_IMAGE_SIMPLE}
            >
              <Button
                type="primary"
                size="small"
                onClick={() => history.push(`/knowledge/${kb_id}`)}
              >
                Upload Documents
              </Button>
            </Empty>
          )}
        </div>
      </div>
    </InnerLayout>
  );
}
