import React, { useState, useEffect, useCallback, useContext } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  Card,
  Button,
  Row,
  Col,
  Typography,
  Form,
  Input,
  Space,
  Tag,
  Descriptions,
  Spin,
  Alert,
  Divider,
  message
} from 'antd';
import {
  ArrowLeftOutlined,
  PlayCircleOutlined,
  ClearOutlined,
} from '@ant-design/icons';
import { handleApiError, notifySuccess } from '../../../../utils/notification';
import API from '../../../../services';
import './index.css';
import { ModelsContext } from '../../../../context/models';

const { Title, Text } = Typography;

function EvaluatorDebug() {
  const navigate = useNavigate();
  const location = useLocation();
  const [form] = Form.useForm();

  // Get evaluator configuration from route state
  const debugConfig = location.state || {};

  // State management
  const [loading, setLoading] = useState(false);
  const [evaluator, setEvaluator] = useState<any>(null);
  const { models, modelNameMap } = useContext(ModelsContext);
  const [evaluationResult, setEvaluationResult] = useState<any>(null);
  const [evaluationLoading, setEvaluationLoading] = useState(false);


  // Load evaluator details (if ID exists)
  const loadEvaluatorDetail = useCallback(async () => {
    if (!debugConfig.evaluatorId) return;

    setLoading(true);
    try {
      const response = await API.getEvaluator({ id: debugConfig.evaluatorId });
      if (response.code === 200) {
        setEvaluator(response.data);
      }
    } catch (error) {
      handleApiError(error, 'Load evaluator details');
    } finally {
      setLoading(false);
    }
  }, [debugConfig.evaluatorId]);

  // Get model name
  const getModelName = useCallback((modelId: string) => {
    const name = modelNameMap[Number(modelId)];
    return name || modelId || '-';
  }, [modelNameMap]);

  // Extract model information from configuration
  const getModelConfig = useCallback(() => {
    if (debugConfig.modelConfig) {
      return debugConfig.modelConfig;
    }
    return {
    };
  }, [debugConfig]);

  // Get template variables from evaluator details
  const getTemplateVariables = useCallback(() => {
    if (debugConfig && debugConfig.variables) {
      try {
        return debugConfig.variables;
      } catch (error) {
        console.log('Error parsing evaluator variables:', error);
        return {};
      }
    }
    return {};
  }, [debugConfig]);

  // Handle clear form
  const handleClear = () => {
    form.resetFields();
    setEvaluationResult(null);

    // Reset variable values to default
    const templateVariables = getTemplateVariables();
    console.log('Resetting form with templateVariables:', templateVariables);
    if (templateVariables && Object.keys(templateVariables).length > 0) {
      const initialValues: any = {};
      Object.entries(templateVariables).forEach(([key, value]) => {
        initialValues[key] = value || '';
      });
      form.setFieldsValue(initialValues);

    }

    message.success('Form cleared');
  };

  // Handle run evaluation
  const handleRun = async () => {
    try {
      // First perform form validation
      await form.validateFields();

      const modelConfig = getModelConfig();

      // Build unified variables parameter including all variables and test data


      const { systemPrompt, ...otherConfig } = (debugConfig?.modelConfig) || {};
      console.log(form.getFieldsValue(), 'asd...')
      // Build request parameters
      const params: EvaluatorsAPI.DebugEvaluatorParams = {
        modelConfig: JSON.stringify({
          modelId: modelConfig.modelId,
          ...otherConfig
        }),
        prompt: debugConfig.systemPrompt,
        variables: JSON.stringify(form.getFieldsValue()) // Put all parameters unified into variables
      };

      setEvaluationLoading(true);
      setEvaluationResult(null);

      const response = await API.debugEvaluator(params);

      if (response.code === 200) {
        setEvaluationResult(response.data);
        notifySuccess({ message: 'Evaluation completed' });
      } else {
        throw new Error(response.message || 'Evaluation failed');
      }
    } catch (error: any) {
      if (error.errorFields) {
        message.error('Please fill in the required test data');
      } else {
        handleApiError(error, 'Run evaluation');
      }
    } finally {
      setEvaluationLoading(false);
    }
  };

  // Initialize
  useEffect(() => {
    loadEvaluatorDetail();
  }, []);

  // Initialize variable form values
  useEffect(() => {
    if (evaluator) {
      const templateVariables = getTemplateVariables();
      console.log('Initializing form with templateVariables:', templateVariables);
      if (templateVariables && Object.keys(templateVariables).length > 0) {
        const initialValues: any = {};
        Object.entries(templateVariables).forEach(([key, value]) => {
          initialValues[key] = value || '';
        });
        console.log('Setting form initial values:', initialValues);
        form.setFieldsValue(initialValues);

        // Verify form values are correctly set
        setTimeout(() => {
          const currentValues = form.getFieldsValue();
          console.log('Form current values after initialization:', currentValues);
        }, 100);
      }
    }
  }, [evaluator, getTemplateVariables, form]);

  if (loading && !evaluator) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <Spin size="large" />
      </div>
    );
  }

  // Return to previous page
  const goBackPageFun = () => {
    const targetPathname = debugConfig?.prePathname;
    if(debugConfig && targetPathname) {
      // Create new state object, passing systemPrompt as top-level property
      navigate(targetPathname, {
        state: {
          ...debugConfig,
          prePathname: location.pathname,
        }
      });
    } else {
      navigate(-1);
    }
  };

  const { modelId, ...otherConfig } = debugConfig.modelConfig;

  return (
    <div className="p-8 fade-in evaluator-debug-page">
      {/* Page header */}
      <div className="mb-8">
        <div className='flex mb-2'>
          <Button
            type="text"
            icon={<ArrowLeftOutlined />}
            onClick={goBackPageFun}
            size="large"
          />
          <Title level={2} className='m-0'>Evaluator Debug</Title>
        </div>
        <Text type="secondary">Test and debug the evaluation logic of the evaluator</Text>
      </div>

      <Row gutter={[24, 24]}>
        {/* Left: Evaluator configuration info */}
        <Col xs={24} lg={12}>
          <Card title="Evaluator Configuration" style={{ height: 'fit-content' }}>
            {evaluator && (
              <Descriptions column={3} size="small">
                <Descriptions.Item label="Evaluator Name">
                  <Text strong>{evaluator.name}</Text>
                </Descriptions.Item>
                <Descriptions.Item label="Description">
                  <Text>{evaluator.description || '-'}</Text>
                </Descriptions.Item>
                <Descriptions.Item label="Current Version">
                  {evaluator.latestVersion ? (
                    <Tag color="blue">{evaluator.latestVersion}</Tag>
                  ) : (
                    <Tag color="default">No Version</Tag>
                  )}
                </Descriptions.Item>
              </Descriptions>
            )}

            <Divider orientation="left">Model Configuration</Divider>

            <Descriptions column={3} size="small">
              <Descriptions.Item span={24} label="Model">
                <Tag color="geekblue">{getModelName(modelId)}</Tag>
              </Descriptions.Item>
              {
                Object.entries(otherConfig).map(([key, value]) => {
                  return (
                    <Descriptions.Item key={key} label={key}>
                      <Text>{value as string}</Text>
                    </Descriptions.Item>
                  )
                })
              }
            </Descriptions>

            <Divider orientation="left">Prompt</Divider>

            <div className='mb-4'>
              <Text type="secondary" className='text-sm mb-2 block'>
                System Prompt
              </Text>
              <div
                style={{
                  background: '#f5f5f5',
                  padding: 12,
                  borderRadius: 6,
                  maxHeight: 200,
                  overflow: 'auto',
                  fontFamily: 'monospace',
                  fontSize: '13px',
                  lineHeight: '1.5'
                }}
                className="prompt-display"
              >
                {debugConfig.systemPrompt || 'System prompt not configured'}
              </div>
            </div>

            {/* Display variables and their values */}
            {debugConfig.variables && Object.keys(debugConfig.variables).length > 0 && (
              <>
                <Divider orientation="left">Variable Configuration</Divider>
                <div className='mb-4'>
                  <Text type="secondary" className='text-sm mb-2 block'>
                    Detected Variables ({Object.keys(debugConfig.variables).length})
                  </Text>
                  <div className="p-3 bg-[#f9f9f9] border border-[#e8e8e8] rounded-md">
                    <Space direction="vertical" className='w-full' size="small">
                      {Object.entries(debugConfig.variables).map(([key]) => (
                        <div key={key} className='flex justify-between items-center'>
                          <Tag color="blue" className='m-0'>{key}</Tag>
                        </div>
                      ))}
                    </Space>
                  </div>
                </div>
              </>
            )}
          </Card>
        </Col>

        {/* Right side: Test data area */}
        <Col xs={24} lg={12}>
          <Card
            title="Test Data"
            extra={
              <Space>
                <Button
                  icon={<ClearOutlined />}
                  onClick={handleClear}
                  disabled={evaluationLoading}
                >
                  Clear
                </Button>
                <Button
                  type="primary"
                  icon={<PlayCircleOutlined />}
                  onClick={handleRun}
                  loading={evaluationLoading}
                >
                  Run
                </Button>
              </Space>
            }
          >
            <Form form={form} layout="vertical">
              {/* Evaluator template variable input boxes */}
              {(() => {
                const templateVariables = getTemplateVariables();
                return templateVariables && Object.keys(templateVariables).length > 0 ? (
                  <>
                    <div className="template-variables-section">
                      <div className="template-variables-title">
                        Template Variables Configuration
                      </div>
                      <div className="template-variables-description">
                        Please set values for the variables in the evaluator template
                      </div>

                      {Object.entries(templateVariables).map(([variableName, defaultValue]) => (
                        <Form.Item
                          key={variableName}
                          className="variable-input-item"
                          label={
                            <div>
                              <Text strong>{variableName}</Text>
                              <Tag color="blue" className="ml-2">Template Variable</Tag>
                            </div>
                          }
                          name={variableName}
                          initialValue={defaultValue || ''}
                          rules={[
                            {
                              required: true,
                              whitespace: true,
                              message: `Please enter the value for ${variableName}`,
                            }
                          ]}
                        >
                          <Input
                            placeholder={`Enter the value for ${variableName}`}
                            showCount
                            maxLength={500}
                          />
                        </Form.Item>
                      ))}
                    </div>

                    <Divider className="variables-divider" />
                  </>
                ) : null;
              })()}

            </Form>

            {/* Evaluation result */}
            {evaluationResult && (
              <>
                <Divider orientation="left">Evaluation Result</Divider>
                <Alert
                  message="Evaluation Completed"
                  description={
                    <div>
                      <Row gutter={[16, 8]}>
                        <Col span={12}>
                          <Text strong>Evaluation Score:</Text>
                          <Tag
                            color={evaluationResult.score >= 0.8 ? 'success' : evaluationResult.score >= 0.6 ? 'warning' : 'error'}
                            style={{ marginLeft: 8 }}
                          >
                            {evaluationResult.score}
                          </Tag>
                        </Col>
                      </Row>
                      <div className='mt-3'>
                        <Text strong>Evaluation Reason:</Text>
                        <div
                          className='mt-2 p-3 bg-[#f9f9f9] border border-[#e8e8e8] rounded-md'
                        >
                          <Text>{evaluationResult.reason || 'No detailed reason'}</Text>
                        </div>
                      </div>
                    </div>
                  }
                  type="success"
                  showIcon
                  className='mt-4'
                />
              </>
            )}

            {/* Prompt info */}
            {!debugConfig.evaluatorId && (
              <Alert
                message="Configuration Tip"
                description="Currently using default configuration for debugging. It is recommended to enter from the evaluator details page to use complete configuration."
                type="info"
                showIcon
                className='mt-4'
              />
            )}
          </Card>
        </Col>
      </Row>
    </div>
  );
}

export default EvaluatorDebug;
