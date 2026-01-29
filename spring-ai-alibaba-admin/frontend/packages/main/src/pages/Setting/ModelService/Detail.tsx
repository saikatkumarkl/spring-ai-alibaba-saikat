import InnerLayout from '@/components/InnerLayout';
import $i18n from '@/i18n';
import {
  createModel,
  deleteModel,
  getProviderDetail,
  listModels,
  updateModel,
} from '@/services/modelService';
import {
  ICreateModelParams,
  IModel,
  IProviderConfigInfo,
  MODEL_TAGS,
} from '@/types/modelService';
import {
  AlertDialog,
  Button,
  Empty,
  Switch,
  Tag,
  message,
} from '@spark-ai/design';
import { Spin, Table } from 'antd';
import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'umi';
import styles from './Detail.module.less';
import ModelConfigModal from './components/ModelConfigModal';
import ProviderInfoForm from './components/ProviderInfoForm';

const ModelServiceDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [isModelConfigModalVisible, setIsModelConfigModalVisible] =
    useState<boolean>(false);
  const [currentModel, setCurrentModel] = useState<IModel | undefined>(
    undefined,
  );
  const [provider, setProvider] = useState<IProviderConfigInfo | null>(null);
  const [models, setModels] = useState<IModel[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [modelLoading, setModelLoading] = useState<boolean>(true);

  const fetchProviderDetail = () => {
    if (!id) return;

    setLoading(true);
    getProviderDetail(id)
      .then((response) => {
        if (response?.data) {
          setProvider(response.data);
        }
      })
      .finally(() => {
        setLoading(false);
      });
  };

  useEffect(() => {
    fetchProviderDetail();
  }, [id]);

  const fetchModels = () => {
    if (!id) return;

    setModelLoading(true);
    listModels(id)
      .then((response) => {
        if (response?.data) {
          setModels(response.data);
        }
      })
      .finally(() => {
        setModelLoading(false);
      });
  };

  useEffect(() => {
    fetchModels();
  }, [id]);

  const columns = [
    {
      title: $i18n.get({
        id: 'main.pages.Setting.ModelService.Detail.modelName',
        dm: 'Model Name',
      }),
      dataIndex: 'name',
      key: 'name',
      render: (text: string) => (
        <div className={styles['model-name']}>
          <span>{text}</span>
        </div>
      ),
    },
    {
      title: $i18n.get({
        id: 'main.pages.Setting.ModelService.Detail.modelType',
        dm: 'Model Type',
      }),
      dataIndex: 'type',
      key: 'type',
    },
    {
      title: $i18n.get({
        id: 'main.pages.Setting.ModelService.Detail.modelAbility',
        dm: 'Model Capabilities',
      }),
      dataIndex: 'tags',
      key: 'tags',
      render: (tags: string[]) => {
        if (!tags) return null;
        return (
          <div className={styles['capabilities']}>
            {tags.map((tag, index) => (
              <Tag key={index} color="mauve">
                {MODEL_TAGS[tag as keyof typeof MODEL_TAGS]}
              </Tag>
            ))}
          </div>
        );
      },
    },
    {
      title: $i18n.get({
        id: 'main.pages.Setting.ModelService.Detail.enable',
        dm: 'Enable',
      }),
      dataIndex: 'enable',
      key: 'enable',
      render: (enable: boolean, record: IModel) => (
        <Switch
          checked={enable}
          onChange={(checked) =>
            updateModelInfo({ ...record, enable: checked })
          }
        />
      ),
    },
    {
      title: $i18n.get({
        id: 'main.pages.Setting.ModelService.Detail.operation',
        dm: 'Actions',
      }),
      key: 'action',
      render: (_: any, record: IModel) => {
        const isPreset = record.source === 'preset';
        return (
          <div className={styles['action-buttons']}>
            <a onClick={() => handleConfigModel(record)}>
              {$i18n.get({
                id: 'main.pages.Setting.ModelService.Detail.setting',
                dm: 'Settings',
              })}
            </a>
            <a
              className={isPreset ? styles['disabled'] : ''}
              onClick={() => handleDeleteModel(record)}
            >
              {$i18n.get({
                id: 'main.pages.Setting.ModelService.Detail.delete',
                dm: 'Delete',
              })}
            </a>
          </div>
        );
      },
    },
  ];

  const handleConfigModel = (record: IModel) => {
    setCurrentModel(record);
    setIsModelConfigModalVisible(true);
  };

  const handleDeleteModel = (record: IModel) => {
    AlertDialog.warning({
      title: $i18n.get({
        id: 'main.pages.Setting.ModelService.Detail.deleteModel',
        dm: 'Delete Model',
      }),
      children: $i18n.get(
        {
          id: 'main.pages.Setting.ModelService.Detail.confirmDeleteModel',
          dm: 'Are you sure you want to delete model {var1}? This action cannot be undone.',
        },
        { var1: record.name },
      ),
      onOk: () => {
        if (!record.model_id) {
          message.error(
            $i18n.get({
              id: 'main.pages.Setting.ModelService.Detail.modelIdNotExist',
              dm: 'Model ID does not exist',
            }),
          );
          return;
        }
        if (!id) {
          message.error(
            $i18n.get({
              id: 'main.pages.Setting.ModelService.Detail.providerIdNotExist',
              dm: 'Provider ID does not exist',
            }),
          );
          return;
        }
        deleteModel(id, record.model_id).then((response) => {
          if (response) {
            message.success(
              $i18n.get({
                id: 'main.pages.Setting.ModelService.Detail.deleteModelSuccess',
                dm: 'Model deleted successfully',
              }),
            );
            fetchModels();
          }
        });
      },
    });
  };

  const handleAddModel = () => {
    setCurrentModel(undefined);
    setIsModelConfigModalVisible(true);
  };

  const updateModelInfo = async (modelInfo: IModel) => {
    if (!id) {
      message.error(
        $i18n.get({
          id: 'main.pages.Setting.ModelService.Detail.providerIdNotExist',
          dm: 'Provider ID does not exist',
        }),
      );
      return;
    }

    if (!modelInfo.model_id) {
      message.error(
        $i18n.get({
          id: 'main.pages.Setting.ModelService.Detail.modelIdNotExist',
          dm: 'Model ID does not exist',
        }),
      );
      return;
    }

    const updateParams: ICreateModelParams = {
      ...modelInfo,
      name: modelInfo.name,
    };

    const response = await updateModel(id, modelInfo.model_id, updateParams);
    if (response) {
      message.success(
        $i18n.get({
          id: 'main.pages.Setting.ModelService.Detail.updateModelSuccess',
          dm: 'Model updated successfully',
        }),
      );
      setIsModelConfigModalVisible(false);
      fetchModels();
    }
  };

  const createModelInfo = async (modelInfo: ICreateModelParams) => {
    if (!id) {
      message.error(
        $i18n.get({
          id: 'main.pages.Setting.ModelService.Detail.providerIdNotExist',
          dm: 'Provider ID does not exist',
        }),
      );
      return;
    }

    const createParams: ICreateModelParams = {
      ...modelInfo,
      name: modelInfo.name,
    };

    const response = await createModel(id, createParams);
    if (response) {
      message.success(
        $i18n.get({
          id: 'main.pages.Setting.ModelService.Detail.addModelSuccess',
          dm: 'Model added successfully',
        }),
      );
      setIsModelConfigModalVisible(false);
      fetchModels();
    }
  };

  const handleModelConfigSubmit = async (modelInfo: ICreateModelParams) => {
    if (!id) {
      message.error(
        $i18n.get({
          id: 'main.pages.Setting.ModelService.Detail.providerIdNotExist',
          dm: 'Provider ID does not exist',
        }),
      );
      return;
    }

    if (currentModel) {
      await updateModelInfo({ ...currentModel, ...modelInfo });
    } else {
      await createModelInfo(modelInfo);
    }
  };

  if (loading) {
    return (
      <InnerLayout
        breadcrumbLinks={[
          {
            title: $i18n.get({
              id: 'main.pages.Setting.ModelService.Detail.modelServiceManagement',
              dm: 'Model Service Management',
            }),
            path: `/setting/modelService`,
          },
          {
            title: $i18n.get({
              id: 'main.pages.Setting.ModelService.Detail.modelServiceDetail',
              dm: 'Model Service Details',
            }),
          },
        ]}
      >
        <div className={styles.container}>
          <Spin
            spinning={loading}
            size="large"
            tip={$i18n.get({
              id: 'main.pages.Setting.ModelService.Detail.loading',
              dm: 'Loading...',
            })}
          >
            <div style={{ minHeight: '400px' }}></div>
          </Spin>
        </div>
      </InnerLayout>
    );
  }

  return (
    <InnerLayout
      breadcrumbLinks={[
        {
          title: $i18n.get({
            id: 'main.pages.Setting.ModelService.Detail.modelServiceManagement',
            dm: 'Model Service Management',
          }),
          path: `/setting/modelService`,
        },
        {
          title:
            provider?.name ||
            $i18n.get({
              id: 'main.pages.Setting.ModelService.Detail.modelServiceDetail',
              dm: 'Model Service Details',
            }),
        },
      ]}
      loading={modelLoading}
      bottom={
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px' }}>
          <Button onClick={() => navigate('/setting/modelService')}>
            {$i18n.get({
              id: 'main.pages.Setting.ModelService.Detail.cancel',
              dm: 'Cancel',
            })}
          </Button>
        </div>
      }
    >
      <div className={styles.container}>
        <div className={styles.info}>
          <div className={styles.title}>
            {$i18n.get({
              id: 'main.pages.Setting.ModelService.Detail.serviceConfiguration',
              dm: 'Service Configuration',
            })}
          </div>
          <ProviderInfoForm
            provider={provider}
            providerId={id || ''}
            onRefresh={fetchProviderDetail}
          />
        </div>
        <div className={styles['table-container']}>
          <div className={styles['table-header']}>
            <div className={styles['table-title']}>
              <span>
                {$i18n.get({
                  id: 'main.pages.Setting.ModelService.Detail.modelConfiguration',
                  dm: 'Model Configuration',
                })}
              </span>
              <span className={styles.count}>
                （{models.length}
                {$i18n.get({
                  id: 'main.pages.Setting.ModelService.Detail.models',
                  dm: ' models',
                })}
                ）
              </span>
            </div>
            <Button type="link" onClick={handleAddModel}>
              {$i18n.get({
                id: 'main.pages.Setting.ModelService.Detail.addModel',
                dm: 'Add Model',
              })}
            </Button>
          </div>
          {models.length > 0 ? (
            <Table
              columns={columns}
              dataSource={models}
              rowKey="model_id"
              pagination={false}
              className={styles['table']}
            />
          ) : (
            <Empty
              description={$i18n.get({
                id: 'main.pages.Setting.ModelService.Detail.noModel',
                dm: 'No models yet',
              })}
            />
          )}
        </div>
      </div>

      <ModelConfigModal
        open={isModelConfigModalVisible}
        onCancel={() => setIsModelConfigModalVisible(false)}
        onOk={handleModelConfigSubmit}
        model={currentModel}
        provider={provider || undefined}
      />
    </InnerLayout>
  );
};

export default ModelServiceDetail;
