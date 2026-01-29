import React, { useState } from 'react';
import {
  Modal,
  Card,
  Typography,
  Input,
  Select,
  Button,
  Alert,
  Tag,
  Space,
  Row,
  Col,
  Divider,
  Spin
} from 'antd';
import {
  CloseOutlined,
  ExclamationCircleOutlined,
  RocketOutlined,
  ExperimentOutlined,
  RobotOutlined,
  GoldOutlined,
  FireOutlined,
  AppstoreOutlined,
  InfoCircleOutlined
} from '@ant-design/icons';
import { handleApiError, handleValidationError, notifySuccess } from '../utils/notification';
import PublishSuccessModal from './PublishSuccessModal';
import API from '../services';

const { Title, Text } = Typography;
const { TextArea } = Input;
const { Option } = Select;

const PublishVersionModal = ({ prompt, newContent, modelConfig, models = [], onClose, onSuccess, variables }) => {
  // Helper function to get model information by ID
  const getModelById = (modelId) => {
    return models.find(m => m.id === modelId) || null;
  };

  // Helper function to get model name by ID
  const getModelName = (modelId) => {
    const model = getModelById(modelId);
    return model ? model.name : modelId || '-';
  };

  // Helper function to get display parameters (filtering out model identifiers)
  const getDisplayModelParams = (config) => {
    if (!config || typeof config !== 'object') return {};

    // Filter out model identifier fields
    const { model, modelId, ...filteredParams } = config;
    return filteredParams;
  };


  // Function to calculate next version number
  const calculateNextVersion = (currentVersion) => {
    if (!currentVersion) return '1.0.0';

    // Try to parse version number
    let versionStr = String(currentVersion).trim();

    // Handle version prefix (e.g., v1.5.0)
    if (versionStr.toLowerCase().startsWith('v')) {
      versionStr = versionStr.substring(1);
    }

    // Handle common version number formats
    if (versionStr.includes('.')) {
      const parts = versionStr.split('.');

      if (parts.length >= 3) {
        // Three-digit version format (e.g., 1.5.0, 2.1.3)
        const major = parseInt(parts[0]) || 0;
        const minor = parseInt(parts[1]) || 0;
        const patch = parseInt(parts[2]) || 0;
        return `${major}.${minor}.${patch + 1}`;
      } else if (parts.length === 2) {
        // Two-digit version format (e.g., 1.5, 2.1) - convert to three digits and increment patch
        const major = parseInt(parts[0]) || 0;
        const minor = parseInt(parts[1]) || 0;
        return `${major}.${minor}.1`;
      } else if (parts.length === 1) {
        // Single version with decimal point (e.g., "1.")
        const major = parseInt(parts[0]) || 0;
        return `${major}.0.1`;
      }
    }

    // If pure number, treat as major version and default increment patch version
    const num = parseInt(versionStr);
    if (!isNaN(num)) {
      return `${num}.0.1`;
    }

    // Default case
    return '0.0.1';
  };

  const [formData, setFormData] = useState({
    version: calculateNextVersion(prompt.latestVersion),
    description: '',
    status: 'release' // Default to publish release version
  });

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [showSuccessModal, setShowSuccessModal] = useState(false);

  // Extract parameters from passed content
  const parameters = Object.entries(variables).map(([key, value]) => ({ key, value }));

  const handleSubmit = async () => {
    if (!formData.version.trim()) {
      handleValidationError('Please enter version number');
      return;
    }

    if (!newContent || !newContent.trim()) {
      handleValidationError('Please fill in Prompt content in the editor');
      return;
    }

    setLoading(true);
    setError(null);

    try {

      // Call publish version API
      const response = await API.publishPromptVersion({
        promptKey: prompt.promptKey,
        version: formData.version,
        versionDescription: formData.description,
        template: newContent,
        variables: JSON.stringify(variables),
        modelConfig: JSON.stringify(modelConfig || {}),
        status: formData.status
      });

      if (response.code === 200) {
        notifySuccess({
          message: 'Version published successfully',
          description: `Successfully published ${formData.status === 'release' ? 'release' : 'PRE'} version ${formData.version}`
        });
        setShowSuccessModal(true);
      } else {
        throw new Error(response.message || 'Publication failed');
      }
    } catch (err) {
      console.error('Failed to publish version:', err);
      handleApiError(err, 'Publish version');
      setError(err.message || 'Publication failed, please try again later');
    } finally {
      setLoading(false);
    }
  };

  const handleSuccessClose = () => {
    setShowSuccessModal(false);
    if (onSuccess) {
      onSuccess();
    } else {
      onClose();
    }
  };

  return (
    <>
      <Modal
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <RocketOutlined />
            <span>Publish New Version</span>
          </div>
        }
        open={true}
        onCancel={onClose}
        width={800}
        footer={null}
        destroyOnHidden
        style={{ top: 20 }}
        styles={{
          body: {
            maxHeight: 'calc(100vh - 200px)',
            overflowY: 'auto',
            padding: 0
          }
        }}
      >
        {error && (
          <Alert
            message="Publication Failed"
            description={error}
            type="error"
            showIcon
            style={{ marginBottom: 16 }}
          />
        )}

        <div style={{ padding: 24, paddingBottom: 0 }}>
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            {/* Current Prompt Information */}
            <Card size="small">
              <Row gutter={[16, 16]}>
                <Col span={12}>
                  <div>
                    <Text type="secondary" style={{ fontSize: '12px', textTransform: 'uppercase' }}>Current Prompt</Text>
                    <div style={{ marginTop: 4 }}>
                      <Text strong>{prompt.promptKey}</Text>
                    </div>
                  </div>
                </Col>
                <Col span={12}>
                  <div>
                    <Text type="secondary" style={{ fontSize: '12px', textTransform: 'uppercase' }}>Current Version</Text>
                    <div style={{ marginTop: 4 }}>
                      {prompt.latestVersion ? (
                        <Tag color="blue">{prompt.latestVersion}</Tag>
                      ) : (
                        <Tag color="default">No Version</Tag>
                      )}
                    </div>
                  </div>
                </Col>
              </Row>
            </Card>

            {/* Version Configuration */}
            <Card title="Version Configuration" size="small">
              <Row gutter={[16, 16]}>
                <Col span={12}>
                  <div>
                    <Text strong style={{ marginBottom: 8, display: 'block' }}>New Version Number *</Text>
                    <Input
                      value={formData.version}
                      onChange={(e) => setFormData(prev => ({ ...prev, version: e.target.value }))}
                      placeholder="1.0.0"
                    />
                  </div>
                </Col>
                <Col span={12}>
                  <div>
                    <Text strong style={{ marginBottom: 8, display: 'block' }}>Version Type *</Text>
                    <Select
                      value={formData.status}
                      onChange={(value) => setFormData(prev => ({ ...prev, status: value }))}
                      style={{ width: '100%' }}
                    >
                      <Option value="release">Release Version</Option>
                      <Option value="pre">PRE Version</Option>
                    </Select>
                  </div>
                </Col>
              </Row>
            </Card>

            {/* Content Preview */}
            <Card title="Version Content Preview" size="small">
              {newContent && newContent.trim() ? (
                <div style={{
                  padding: 12,
                  backgroundColor: '#f5f5f5',
                  borderRadius: 6,
                  fontFamily: 'monospace',
                  fontSize: '12px',
                  maxHeight: 150,
                  overflowY: 'auto',
                  whiteSpace: 'pre-wrap',
                  border: '1px solid #d9d9d9'
                }}>
                  {newContent}
                </div>
              ) : (
                <Alert
                  message="Please fill in Prompt content in the editor"
                  type="warning"
                  showIcon
                  icon={<ExclamationCircleOutlined />}
                />
              )}
            </Card>

            {/* Parameter Preview */}
            {parameters.length > 0 && (
              <Card title="Detected Parameters: Key-Value Pairs" size="small">
                <Space size={[8, 8]} wrap>
                  {parameters.map((param, index) => (
                    <Tag key={index} color="blue">
                      {param.key}{param.value ? `:  ${param.value}` : ''}
                    </Tag>
                  ))}
                </Space>
              </Card>
            )}

            {/* Model Configuration Preview */}
            {modelConfig && (
              <Card title="Model Configuration" size="small">
                <Row gutter={[16, 8]}>
                  {/* Display model name instead of ID */}
                  <Col span={24} style={{ marginBottom: 8 }}>
                    <Space>
                      <Text strong>Model：</Text>
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

            {/* Version Type Description */}
            <Alert
              message="Version Type Description"
              description={
                <div style={{ marginTop: 8 }}>
                  <div style={{ marginBottom: 4 }}>
                    <Text strong>Release Version:</Text>
                    <Text style={{ marginLeft: 8 }}>Stable production version, will update current version pointer</Text>
                  </div>
                  <div>
                    <Text strong>PRE Version:</Text>
                    <Text style={{ marginLeft: 8 }}>Pre-release version for testing and validation</Text>
                  </div>
                </div>
              }
              type="info"
              showIcon
              icon={<InfoCircleOutlined />}
            />

            {/* Version Description */}
            <Card title="Version Description" size="small">
              <TextArea
                value={formData.description}
                onChange={(e) => setFormData(prev => ({ ...prev, description: e.target.value }))}
                placeholder="Describe the changes in this version..."
                rows={3}
              />
            </Card>
          </Space>
        </div>

        {/* Bottom Buttons */}
        <div style={{
          padding: 24,
          paddingTop: 16,
          borderTop: '1px solid #f0f0f0',
          textAlign: 'right',
          marginTop: 16,
          backgroundColor: '#fff',
          position: 'sticky',
          bottom: 0
        }}>
          <Space>
            <Button onClick={onClose}>
              Cancel
            </Button>
            <Button
              type="primary"
              icon={loading ? <Spin size="small" /> : (formData.status === 'release' ? <RocketOutlined /> : <ExperimentOutlined />)}
              onClick={handleSubmit}
              disabled={loading || !newContent || !newContent.trim() || !formData.version.trim()}
              style={{
                backgroundColor: formData.status === 'release' ? '#52c41a' : '#fa8c16',
                borderColor: formData.status === 'release' ? '#52c41a' : '#fa8c16'
              }}
            >
              {loading
                ? 'Publishing...'
                : `Publish ${formData.status === 'release' ? 'Release' : 'PRE'} Version`
              }
            </Button>
          </Space>
        </div>
      </Modal>

      {/* Publish Success Modal */}
      {showSuccessModal && (
        <PublishSuccessModal
          prompt={{
            ...prompt,
            latestVersionStatus: formData.status
          }}
          version={formData.version}
          onClose={handleSuccessClose}
        />
      )}
    </>
  );
};

export default PublishVersionModal;
