import React, { useMemo, useState } from 'react';
import {
  Modal,
  Card,
  Typography,
  Button,
  Input,
  Select,
  Alert,
  Space,
  Row,
  Col,
  Tag,
  Divider,
  Spin,
  message
} from 'antd';
import {
  CloseOutlined,
  RocketOutlined,
  ExclamationCircleOutlined,
  PlusOutlined,
  ExperimentOutlined,
  RobotOutlined,
  GoldOutlined,
  FireOutlined,
  AppstoreOutlined
} from '@ant-design/icons';
import API from '../services';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;
const { Option } = Select;

const CreatePromptModal = (props) => {
  const {
    onClose, onSuccess, initialData = {}, quickCreate = false, modelConfig = null,
    models = [],
  } = props;

  const { variablesWithValues = {} } = initialData;

  const getModelById = (modelId) => {
    return models.find(m => m.id === modelId) || null;
  };

  // Helper function to get model name by ID
  const getModelName = (modelId) => {
    const model = getModelById(modelId);
    return model ? model.name : modelId || '-';
  };

  const variablesWithValueList = useMemo(() => {
    return Object.entries(variablesWithValues).map(([key, value]) => ({
      key, value
    }));
  }, [variablesWithValues]);

  const [formData, setFormData] = useState({
    promptKey: '',
    tags: '',
    promptDescription: ''
  });

  // Version information in quick create mode
  const [versionData, setVersionData] = useState({
    version: '0.0.1',
    versionDescription: 'Initial version',
    status: 'release' // release or pre
  });

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // Helper function to extract parameters
  const extractParameters = (content) => {
    const regex = /\{\{(\w+)\}\}/g;
    const parameters = [];
    let match;
    while ((match = regex.exec(content)) !== null) {
      if (!parameters.includes(match[1])) {
        parameters.push(match[1]);
      }
    }
    return parameters;
  };

  const handleSubmit = async () => {
    if (!formData.promptKey.trim()) {
      message.error('Please enter a Prompt Key');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      // Prepare tag data
      const tagsArray = formData.tags.split(',').map(tag => tag.trim()).filter(tag => tag);
      const tagsString = JSON.stringify(tagsArray);

      // Create Prompt
      const createParams = {
        promptKey: formData.promptKey.trim(),
        promptDescription: formData.promptDescription.trim(),
        tags: tagsString
      };

      const createResponse = await API.publishPrompt(createParams);

      if (createResponse.code !== 200) {
        throw new Error(createResponse.message || 'Creation failed');
      }

      // If quick create mode with content, also create version
      if (quickCreate && initialData.content && initialData.content.trim()) {
        if (!versionData.version.trim()) {
          message.error('Please enter version number');
          setLoading(false);
          return;
        }

        const versionParams = {
          promptKey: formData.promptKey.trim(),
          version: versionData.version,
          versionDescription: versionData.versionDescription,
          template: initialData.content,
          variables: JSON.stringify(variablesWithValues),
          modelConfig: JSON.stringify(modelConfig || {}),
          status: versionData.status
        };

        const versionResponse = await API.publishPromptVersion(versionParams);

        if (versionResponse.code !== 200) {
          throw new Error(versionResponse.message || 'Version creation failed');
        }
      }

      // Successfully completed
      message.success({
        content: quickCreate ? 'Prompt created and version published successfully' : 'Prompt created successfully',
        description: quickCreate
          ? `Created Prompt "${formData.promptKey}" and published version ${versionData.version}`
          : `Created Prompt "${formData.promptKey}"`
      });

      if (onSuccess) {
        onSuccess();
      } else {
        onClose();
      }
    } catch (err) {
      console.error('Creation failed:', err);
      message.error(err.message || 'Creation failed, please try again later');
      setError(err.message || 'Creation failed, please try again later');
    } finally {
      setLoading(false);
    }
  };

    // Helper function to get display parameters (filtering out model identifiers)
    const getDisplayModelParams = (config) => {
      if (!config || typeof config !== 'object') return {};

      // Filter out model identifier fields
      const { model, modelId, ...filteredParams } = config;
      return filteredParams;
    };

  return (
    <Modal
      title={
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{
            width: 40,
            height: 40,
            backgroundColor: quickCreate ? '#e6f7ff' : '#f6ffed',
            borderRadius: '50%',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
          }}>
            {quickCreate ? (
              <RocketOutlined style={{ color: '#1890ff', fontSize: 20 }} />
            ) : (
              <PlusOutlined style={{ color: '#52c41a', fontSize: 20 }} />
            )}
          </div>
          <Title level={3} style={{ margin: 0 }}>
            {quickCreate ? 'Quick Create New Prompt' : 'Create New Prompt'}
          </Title>
        </div>
      }
      open={true}
      onCancel={onClose}
      width={800}
      centered
      style={{
        maxHeight: 'calc(100vh - 40px)'
      }}
      bodyStyle={{
        maxHeight: 'calc(100vh - 200px)',
        overflowY: 'auto'
      }}
      footer={[
        <Button key="cancel" onClick={onClose}>
          Cancel
        </Button>,
        <Button
          key="submit"
          type="primary"
          loading={loading}
          disabled={!formData.promptKey.trim() || (quickCreate && (!initialData.content || !initialData.content.trim() || !versionData.version.trim()))}
          onClick={handleSubmit}
          icon={quickCreate && versionData.status === 'release' ? <RocketOutlined /> :
            quickCreate && versionData.status === 'pre' ? <ExperimentOutlined /> : <PlusOutlined />}
          style={{
            backgroundColor: quickCreate && versionData.status === 'release' ? '#52c41a' :
              quickCreate && versionData.status === 'pre' ? '#fa8c16' : undefined
          }}
        >
          {loading
            ? 'Creating...'
            : quickCreate
              ? `Quick Create and Publish ${versionData.status === 'release' ? 'Release' : 'PRE'} Version`
              : 'Create Prompt'
          }
        </Button>
      ]}
      closeIcon={<CloseOutlined />}
    >
      <Space direction="vertical" size={24} style={{ width: '100%' }}>
        {quickCreate && (
          <Alert
            message="Quick Create Mode"
            description="Will create a new Prompt and publish the first version simultaneously"
            type="info"
            icon={<RocketOutlined />}
            showIcon
          />
        )}

        {error && (
          <Alert
            message="Creation Failed"
            description={error}
            type="error"
            icon={<ExclamationCircleOutlined />}
            showIcon
          />
        )}

        <Card title="Basic Information" size="small">
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <div>
              <Text strong style={{ display: 'block', marginBottom: 8 }}>Prompt Key <span className='text-red-700'>*</span></Text>
              <Input
                value={formData.promptKey}
                onChange={(e) => {
                  const value = e.target.value;
                  const validValue = value.replace(/[^a-zA-Z0-9_-]/g, '');
                  setFormData(prev => ({ ...prev, promptKey: validValue }));
                }}
                placeholder="Enter Prompt Key (letters, numbers, underscores, hyphens only)..."
                size="large"
              />
              <Text type="secondary" style={{ fontSize: '12px', display: 'block', marginTop: 4 }}>
                Only English letters, numbers, underscores (_), and hyphens (-) are supported
              </Text>
            </div>

            <div>
              <Text strong style={{ display: 'block', marginBottom: 8 }}>Tags</Text>
              <Input
                value={formData.tags}
                onChange={(e) => setFormData(prev => ({ ...prev, tags: e.target.value }))}
                placeholder="Separate multiple tags with commas, e.g.: marketing, copywriting, creative"
                size="large"
              />
            </div>

            <div>
              <Text strong style={{ display: 'block', marginBottom: 8 }}>Description</Text>
              <TextArea
                value={formData.promptDescription}
                onChange={(e) => setFormData(prev => ({ ...prev, promptDescription: e.target.value }))}
                placeholder="Describe the purpose and features of this Prompt..."
                rows={3}
                size="large"
              />
            </div>
          </Space>
        </Card>

        {quickCreate && (
          <Card title="Version Information" size="small">
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              <Row gutter={16}>
                <Col span={8}>
                  <Text strong style={{ display: 'block', marginBottom: 8 }}>Version Number *</Text>
                  <Input
                    value={versionData.version}
                    onChange={(e) => setVersionData(prev => ({ ...prev, version: e.target.value }))}
                    placeholder="1.0"
                    size="large"
                  />
                </Col>

                <Col span={8}>
                  <Text strong style={{ display: 'block', marginBottom: 8 }}>Version Type *</Text>
                  <Select
                    value={versionData.status}
                    onChange={(value) => setVersionData(prev => ({ ...prev, status: value }))}
                    style={{ width: '100%' }}
                    size="large"
                  >
                    <Option value="release">Release Version</Option>
                    <Option value="pre">PRE Version</Option>
                  </Select>
                </Col>

                <Col span={8}>
                  <Text strong style={{ display: 'block', marginBottom: 8 }}>Version Description</Text>
                  <Input
                    value={versionData.versionDescription}
                    onChange={(e) => setVersionData(prev => ({ ...prev, versionDescription: e.target.value }))}
                    placeholder="Initial version"
                    size="large"
                  />
                </Col>
              </Row>

              <div>
                <Text strong style={{ display: 'block', marginBottom: 8 }}>Version Content Preview</Text>
                {initialData.content && initialData.content.trim() ? (
                  <div style={{
                    padding: 16,
                    backgroundColor: '#fafafa',
                    borderRadius: 6,
                    fontFamily: 'monospace',
                    fontSize: '13px',
                    maxHeight: 128,
                    overflowY: 'auto',
                    border: '1px solid #d9d9d9',
                    whiteSpace: 'pre-wrap'
                  }}>
                    {initialData.content}
                  </div>
                ) : (
                  <Alert
                    message="Please enter Prompt content in the editor"
                    type="warning"
                    icon={<ExclamationCircleOutlined />}
                    showIcon
                  />
                )}
              </div>

              {/* Parameter preview */}
              {variablesWithValueList.length > 0 && (
                <Card title="Detected Parameters: Key-Value Pairs" size="small">
                  <Space size={[8, 8]} wrap>
                    {variablesWithValueList.map((param, index) => (
                      <Tag key={index} color="blue">
                        {param.key}{param.value ? `:  ${param.value}` : ''}
                      </Tag>
                    ))}
                  </Space>
                </Card>
              )}

              {/* Model configuration preview */}
              {modelConfig && (
                <Card title="Model Configuration" size="small">
                  <Row gutter={[16, 8]}>
                    {/* Display model name instead of ID */}
                    <Col span={24} style={{ marginBottom: 8 }}>
                      <Space>
                        <Text strong>Model:</Text>
                        <Text code>{getModelName(modelConfig.modelId)}</Text>
                      </Space>
                    </Col>

                    {/* Dynamically display model parameters */}
                    {(() => {
                      const displayParams = getDisplayModelParams(modelConfig);
                      const paramEntries = Object.entries(displayParams);

                      if (paramEntries.length === 0) {
                        return (
                          <Col span={24}>
                            <Text type="secondary" style={{ fontStyle: 'italic' }}>
                              No model parameters configured
                            </Text>
                          </Col>
                        );
                      }

                      return paramEntries.map(([key, value], index) => {
                        return (
                          <Col span={12} key={key}>
                            <Space>
                              <Text strong>{key}：</Text>
                              <Text code>{value}</Text>
                            </Space>
                          </Col>
                        );
                      });
                    })()
                    }
                  </Row>
                </Card>
              )}

              <Alert
                message={versionData.status === 'release' ? 'Release Version Notes' : 'PRE Version Notes'}
                description={
                  <div>
                    {versionData.status === 'release' ? (
                      <div>
                        <Paragraph style={{ margin: 0, marginBottom: 4 }}>
                          <Text strong>Release Version:</Text> Stable production version, will update the current version pointer
                        </Paragraph>
                        <Text>Suitable for production use, fully tested and validated</Text>
                      </div>
                    ) : (
                      <div>
                        <Paragraph style={{ margin: 0, marginBottom: 4 }}>
                          <Text strong>PRE Version:</Text> Pre-release version for testing and validation
                        </Paragraph>
                        <Text>Suitable for testing environment, will not update the current version pointer</Text>
                      </div>
                    )}
                  </div>
                }
                type={versionData.status === 'release' ? 'success' : 'warning'}
                icon={versionData.status === 'release' ? <RocketOutlined /> : <ExperimentOutlined />}
                showIcon
              />
            </Space>
          </Card>
        )}
      </Space>
    </Modal>
  );
};

export default CreatePromptModal;
