import React, { useState, useEffect, useCallback, useMemo, useContext } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import {
  Card,
  Button,
  Descriptions,
  Tag,
  Tabs,
  Form,
  Input,
  Select,
  InputNumber,
  Slider,
  Space,
  Table,
  Spin,
  Alert,
  message,
  Modal,
  Tooltip,
  Row,
  Col,
  Typography,
  Divider
} from 'antd';
import {
  ArrowLeftOutlined,
  EditOutlined,
  SaveOutlined,
  CloseOutlined,
  BugOutlined,
  RocketOutlined,
  InfoCircleOutlined,
  ExclamationCircleOutlined,
  CheckCircleOutlined
} from '@ant-design/icons';
import { handleApiError, notifySuccess, notifyError } from '../../../../utils/notification';
import API from '../../../../services';
import './index.css';
import usePagination from '../../../../hooks/usePagination';
import { ModelsContext } from '../../../../context/models';

const { TextArea } = Input;
const { Option } = Select;
const { Title, Text } = Typography;

// Format date time display
const formatDateTime = (dateTimeString: string) => {
  if (!dateTimeString) return '-';
  try {
    const date = new Date(dateTimeString);
    return date.toLocaleString('en-US', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });
  } catch {
    return dateTimeString;
  }
};


function EvaluatorDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const stateFromDebug = location.state || {};
  const [form] = Form.useForm();
  const [configForm] = Form.useForm();
  const [publishForm] = Form.useForm();
  const formValues = Form.useWatch([], configForm);

  // Basic state
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [evaluator, setEvaluator] = useState<EvaluatorsAPI.GetEvaluatorResult | null>(null);
  const { models, modelNameMap } = useContext(ModelsContext);

  // Edit state
  const [isEditing, setIsEditing] = useState(false);
  const [editLoading, setEditLoading] = useState(false);

  // Tab-related state
  const [activeTab, setActiveTab] = useState('config');
  const [versions, setVersions] = useState<EvaluatorsAPI.GetEvaluatorVersionsResult["pageItems"]>([]);
  const [versionsLoading, setVersionsLoading] = useState(false);

  // Version publishing related state
  const [showPublishModal, setShowPublishModal] = useState(false);
  const [publishLoading, setPublishLoading] = useState(false);
  const [variableValues, setVariableValues] = useState<Record<string, string>>({});
  const [modelConf, setModelConf] = useState<Record<string, any>>({});
  const [defaultUsedModelId, setDefaultUsedModelId] = useState(-1);
  const [defaultUsedModelConfig, setDefaultUsedModelConfig] = useState<Record<string, any>>({});

  // Template import related state
  const [showTemplateModal, setShowTemplateModal] = useState(false);
  const [templates, setTemplates] = useState<EvaluatorsAPI.GetEvaluatorTemplatesResult["pageItems"]>([]);
  const [selectedTemplateId, setSelectedTemplateId] = useState<number | null>(null);
  const [templatesLoading, setTemplatesLoading] = useState(false);
  const [templateDetailLoading, setTemplateDetailLoading] = useState(false);
  const [selectedTemplateDetail, setSelectedTemplateDetail] = useState<EvaluatorsAPI.GetEvaluatorTemplateResult | null>(null);

  const [experiments, setExperiments] = useState<EvaluatorsAPI.GetEvaluatorExperimentsResult["pageItems"]>([]);

  const [experimentsLoading, setExperimentsLoading] = useState(false);
  const [experimentSearch, setExperimentSearch] = useState('');

  const {
    pagination: experimentPagination,
    onPaginationChange: onExperimentPaginationChange,
    onShowSizeChange: onExperimentShowSizeChange,
    setPagination: setExperimentPagination
  } = usePagination();


  const {
    pagination: versionPagination,
    setPagination: setVersionPagination,
    onPaginationChange: onVersionPaginationChange,
    onShowSizeChange: onVersionShowSizeChange
  } = usePagination();

  const handleModelChange = (newModelId: number) => {
    const selectedModel = models.find(m => m.id === newModelId);
    if (!selectedModel) return;

    const defaultParams = selectedModel.id === defaultUsedModelId ? defaultUsedModelConfig : selectedModel.defaultParameters || {};
    const currentValues = configForm.getFieldsValue();

    const paramKeys = Object.keys(currentValues).filter(
      key => key !== 'modelId' && key !== 'systemPrompt'
    );

    const fieldsToClear: Record<string, any> = {};
    paramKeys.forEach(key => {
      fieldsToClear[key] = undefined;
    });

    setModelConf(defaultParams);
    configForm.setFieldsValue({ ...fieldsToClear, ...defaultParams });
  };


  // Load evaluator details
  const loadEvaluatorDetail = useCallback(async () => {
    if (!id) return;

    setLoading(true);
    setError(null);

    try {
      // Get evaluator basic information
      const evaluatorResponse = await API.getEvaluator({ id: parseInt(id) });

      if (evaluatorResponse.code !== 200) {
        throw new Error(evaluatorResponse.message || 'Failed to get evaluator details');
      }

      const evaluatorData = evaluatorResponse.data;
      setEvaluator(evaluatorData);
      try {
        const isFromEvaluationDebug = stateFromDebug?.prePathname === '/evaluation-debug';
        const modelConfig = isFromEvaluationDebug ? (stateFromDebug?.modelConfig || {}) : JSON.parse(evaluatorData.modelConfig || "{}");
        const { modelId, ...conf } = modelConfig;
        const defaultModelParams = !isFromEvaluationDebug && models[0]?.defaultParameters || {};
        const promptVal = isFromEvaluationDebug ? stateFromDebug.systemPrompt : evaluatorData.prompt;
        setModelConf({ ...defaultModelParams, ...conf });
        setDefaultUsedModelConfig({ ...defaultModelParams, ...conf });
        setDefaultUsedModelId(modelId);
        configForm.setFieldsValue({ modelId, systemPrompt: promptVal, ...{ ...defaultModelParams, ...conf } });

        navigate(location.pathname, {replace: true, state: {}});
      } catch (error) {

      }

      // Set form initial values
      form.setFieldsValue({
        name: evaluatorData.name,
        description: evaluatorData.description
      });

      // Config form will be set in useEffect to use correct model configuration

    } catch (err: any) {
      console.error('Failed to load evaluator details:', err);
      handleApiError(err, 'Load evaluator details');
      setError(err.message || 'Loading failed, please try again later');
    } finally {
      setLoading(false);
    }
  }, [id, form]);

  // Load version list
  const loadVersions = useCallback(async () => {
    if (!id) return;

    setVersionsLoading(true);

    try {
      const response = await API.getEvaluatorVersions({
        evaluatorId: parseInt(id),
        pageNumber: versionPagination.current,
        pageSize: versionPagination.pageSize
      });

      if (response.code === 200) {
        const responseData = response.data;
        setVersions(responseData.pageItems || []);
        setVersionPagination(prev => ({
          ...prev,
          total: responseData.totalCount || 0
        }));
      } else {
        throw new Error(response.message || 'Failed to get version list');
      }
    } catch (err: any) {
      console.error('Failed to load version list:', err);
      handleApiError(err, 'Load version list');
    } finally {
      setVersionsLoading(false);
    }
  }, [id, versionPagination.current, versionPagination.pageSize]);

  // Handle basic info edit save
  const handleSaveBasicInfo = async () => {
    if (!evaluator) return;

    try {
      const values = await form.validateFields();
      setEditLoading(true);

      const response = await API.updateEvaluator({
        id: evaluator.id,
        name: values.name,
        description: values.description
      });

      if (response.code === 200) {
        notifySuccess({ message: 'Evaluator information updated successfully' });
        setEvaluator(prev => ({
          ...prev!,
          ...values,
          updateTime: new Date().toISOString()
        }));
        setIsEditing(false);
      } else {
        throw new Error(response.message || 'Update failed');
      }
    } catch (error: any) {
      if (error.errorFields) {
        message.error('Please check if the form is filled correctly');
      } else {
        handleApiError(error, 'Update evaluator information');
      }
    } finally {
      setEditLoading(false);
    }
  };

  // Load evaluator template list
  const loadEvaluatorTemplates = useCallback(async () => {
    setTemplatesLoading(true);
    try {
      const response = await API.getEvaluatorTemplates();
      if (response.code === 200) {
        setTemplates(response.data.pageItems || []);
      } else {
        throw new Error(response.message || 'Failed to get template list');
      }
    } catch (error: any) {
      console.error('Failed to load template list:', error);
      handleApiError(error, 'Load template list');
    } finally {
      setTemplatesLoading(false);
    }
  }, []);

  // Load template details
  const loadTemplateDetail = useCallback(async (templateId: number) => {
    setTemplateDetailLoading(true);
    try {
      const response = await API.getEvaluatorTemplate({ templateId });
      if (response.code === 200) {
        setSelectedTemplateDetail(response.data);
      } else {
        throw new Error(response.message || 'Failed to get template details');
      }
    } catch (error: any) {
      console.error('Failed to load template details:', error);
      handleApiError(error, 'Load template details');
    } finally {
      setTemplateDetailLoading(false);
    }
  }, []);

  // Handle template selection
  const handleTemplateSelect = (templateId: number) => {
    setSelectedTemplateId(templateId);
    loadTemplateDetail(templateId);
  };

  // Handle template import
  const handleTemplateImport = () => {
    if (!selectedTemplateDetail) return;

    // Set system prompt
    configForm.setFieldsValue({
      systemPrompt: selectedTemplateDetail.template
    });

    // Parse and set model configuration
    if (selectedTemplateDetail.modelConfig) {
      try {
        const modelConfig = JSON.parse(selectedTemplateDetail.modelConfig);
        const { modelId, ...otherConfig } = modelConfig;

        configForm.setFieldsValue({
          modelId: Number(modelId),
          ...otherConfig
        });

        setModelConf(otherConfig);
      } catch (error) {
        console.warn('Failed to parse template model config:', error);
      }
    }

    // Extract variables and set variable values
    const variables = extractVariablesFromPrompt(selectedTemplateDetail.template || '');
    const newVariableValues: Record<string, string> = {};
    variables.forEach(varName => {
      newVariableValues[varName] = '';
    });
    setVariableValues(newVariableValues);

    // Close modal and reset state
    setShowTemplateModal(false);
    setSelectedTemplateId(null);
    setSelectedTemplateDetail(null);

    // Show success message
    message.success('Template imported successfully');
  };

  // Open template import modal
  const handleOpenTemplateModal = () => {
    setShowTemplateModal(true);
    loadEvaluatorTemplates();
  };

  // Handle debug navigation
  const handleDebug = () => {
    const configValues = configForm.getFieldsValue();
    const { systemPrompt, ...otherValues } = configValues;

    console.log(configValues, 'asd...2')
    // Extract variables from prompt and generate variable object
    const promptContent = systemPrompt;
    const variablesWithValues = generateVariablesWithValues(promptContent, variableValues);

    // Build debug page configuration parameters
    const debugConfig = {
      evaluatorId: evaluator?.id,
      modelConfig: {
        ...otherValues,
      },
      variables: variablesWithValues, // Pass variables and their values
      systemPrompt: systemPrompt,
      prePathname: location.pathname,
    };

    // Navigate to debug page with modified configuration
    navigate('/evaluation-debug', { state: debugConfig });
  };

  // Calculate next version number
  const getNextVersion = useCallback(() => {
    if (!versions || versions.length === 0) {
      return '0.0.1';
    }

    // Parse version numbers and find the maximum version
    const versionNumbers = versions
      .map(v => {
        const match = v.version.match(/^(\d+)\.(\d+)\.(\d+)$/);
        if (match) {
          return {
            major: parseInt(match[1]),
            minor: parseInt(match[2]),
            patch: parseInt(match[3]),
            original: v.version
          };
        }
        return null;
      })
      .filter((item): item is NonNullable<typeof item> => item !== null)
      .sort((a, b) => {
        if (a.major !== b.major) return b.major - a.major;
        if (a.minor !== b.minor) return b.minor - a.minor;
        return b.patch - a.patch;
      });

    if (versionNumbers.length === 0) {
      return '0.0.1';
    }

    const latest = versionNumbers[0];
    return `${latest.major}.${latest.minor}.${latest.patch + 1}`;
  }, [versions]);

  // Handle publish new version
  const handlePublishVersion = () => {
    const nextVersion = getNextVersion();
    publishForm.setFieldsValue({
      version: nextVersion,
      description: ''
    });
    setShowPublishModal(true);
  };

  // Extract variables from prompt
  const extractVariablesFromPrompt = useCallback((prompt: string): string[] => {
    if (!prompt) return [];

    // Match variables in double braces, e.g. {{variable_name}}
    const variableMatches = prompt.match(/\{\{\s*([^}]+)\s*\}\}/g);
    if (!variableMatches) return [];

    const variableNames: string[] = [];
    variableMatches.forEach(match => {
      // Extract variable name, remove braces and whitespace
      const variableName = match.replace(/\{\{\s*|\s*\}\}/g, '').trim();
      if (variableName && !variableNames.includes(variableName)) {
        variableNames.push(variableName);
      }
    });

    return variableNames;
  }, []);

  // Generate variables object with user values
  const generateVariablesWithValues = useCallback((prompt: string, userValues: Record<string, string>) => {
    const variableNames = extractVariablesFromPrompt(prompt);
    const variables: Record<string, string> = {};

    variableNames.forEach(name => {
      variables[name] = userValues[name] || '';
    });

    return variables;
  }, [extractVariablesFromPrompt]);

  // Handle publish version confirmation
  const handlePublishConfirm = async () => {
    try {
      const values = await publishForm.validateFields();
      const configValues = configForm.getFieldsValue();

      // Check if version already exists
      const existingVersion = versions.find(v => v.version === values.version);
      if (existingVersion) {
        message.error(`Version ${values.version} already exists, please use a different version number`);
        return;
      }

      setPublishLoading(true);

      // Extract prompt variables and get user input values

      const { systemPrompt, ...otherModelConfig } = configValues;
      const variablesWithValues = generateVariablesWithValues(systemPrompt, variableValues);

      // Call create version API
      const response = await API.createEvaluatorVersion({
        evaluatorId: evaluator!.id.toString(),
        version: values.version,
        description: values.description || '',
        modelConfig: JSON.stringify(otherModelConfig),
        prompt: systemPrompt,
        variables: JSON.stringify(variablesWithValues)
      });

      if (response.code === 200) {
        message.success(`Version ${values.version} published successfully`);
        setShowPublishModal(false);
        loadVersions(); // Reload version list
        loadEvaluatorDetail(); // Reload evaluator details
      } else {
        throw new Error(response.message || 'Publish failed');
      }
    } catch (error: any) {
      if (error.errorFields) {
        // Form validation error, no additional handling needed
        return;
      }
      handleApiError(error, 'Publish new version');
    } finally {
      setPublishLoading(false);
    }
  };


  // Tab change handling
  const handleTabChange = (key: string) => {
    setActiveTab(key);
    if (key === 'versions' && versions.length === 0) {
      loadVersions();
    }
  };

  // Initialize loading
  useEffect(() => {
    loadEvaluatorDetail();
    // Also load version data to get current model info
    loadVersions();
  }, []);


  // Reload version list when pagination changes
  useEffect(() => {
    if (activeTab === 'versions') {
      loadVersions();
    }
  }, [loadVersions, activeTab]);

  // Extract modelId and modelName from modelConfig
  const extractModelInfoFromConfig = useCallback((modelConfig: string) => {
    try {
      const config = JSON.parse(modelConfig);
      const name = modelNameMap[config.modelId];
      const modelId = config.modelId || '';
      return {
        modelId,
        modelName: name || modelId || '-'
      };
    } catch {
      return {
        modelId: '',
        modelName: '-'
      };
    }
  }, [models]);

  // Get current model info (extracted from latest version's modelConfig)
  const getCurrentModelInfo = useCallback(() => {
    if (!versions || versions.length === 0) {
      // If no version data, try to get from evaluator's modelConfig
      if (evaluator?.modelConfig) {
        return extractModelInfoFromConfig(evaluator.modelConfig);
      }
      return { modelId: '', modelName: '-' };
    }

    // Sort by create time and get latest version
    const latestVersion = versions
      .slice()
      .sort((a, b) => new Date(b.createTime).getTime() - new Date(a.createTime).getTime())[0];

    if (latestVersion?.modelConfig) {
      return extractModelInfoFromConfig(latestVersion.modelConfig);
    }

    return { modelId: '', modelName: '-' };
  }, [versions, evaluator, extractModelInfoFromConfig]);

  // Update config form when version list and model list are loaded
  useEffect(() => {
    // Ensure models and evaluator are loaded and not in loading state
    if (models.length > 0 && evaluator && !loading) {
      // Get latest version config or use default values
      let modelConfig = null;
      let variablesData = null;

      // Get config from latest version first
      if (versions && versions.length > 0) {
        const latestVersion = versions
          .slice()
          .sort((a, b) => new Date(b.createTime).getTime() - new Date(a.createTime).getTime())[0];

        if (latestVersion?.modelConfig) {
          try {
            modelConfig = JSON.parse(latestVersion.modelConfig);
          } catch (error) {
            console.warn('Failed to parse modelConfig from latest version:', error);
          }
        }

        if (latestVersion?.variables) {
          try {
            variablesData = JSON.parse(latestVersion.variables);
            console.log('Found variables in latest version:', variablesData);
          } catch (error) {
            console.warn('Failed to parse variables from latest version:', error);
          }
        }
      }

      // If no version data, try to get from evaluator's modelConfig and variables
      if (!modelConfig && evaluator.modelConfig) {
        try {
          modelConfig = JSON.parse(evaluator.modelConfig);
        } catch (error) {
          console.warn('Failed to parse modelConfig from evaluator:', error);
        }
      }

      if (!variablesData && evaluator.variables) {
        try {
          variablesData = JSON.parse(evaluator.variables);
          console.log('Found variables in evaluator:', variablesData);
        } catch (error) {
          console.warn('Failed to parse variables from evaluator:', error);
        }
      }
      // Set variable values - improve variable value setting logic
      if (variablesData && typeof variablesData === 'object') {
        console.log('Setting variable values from API:', variablesData);
        // Ensure variable values object is not empty and contains valid data
        const validVariables = Object.keys(variablesData).length > 0 ? variablesData : {};
        setVariableValues(validVariables);
      } else {
        // If no API variable data, extract variables from systemPrompt and initialize empty values
        const promptContent = formValues.systemPrompt;
        if (promptContent) {
          const detectedVariables = extractVariablesFromPrompt(promptContent);
          if (detectedVariables.length > 0) {
            const emptyVariables: Record<string, string> = {};
            detectedVariables.forEach(varName => {
              emptyVariables[varName] = '';
            });
            console.log('Initializing empty variable values from prompt:', emptyVariables);
            setVariableValues(emptyVariables);
          }
        }
      }
    }
  }, [models, versions, evaluator, loading, configForm, getCurrentModelInfo, extractVariablesFromPrompt]);

  // Check if current config can publish version
  const canPublishVersion = useMemo(() => {
    const currentModelId = configForm.getFieldValue('modelId')
    const currentPrompt = configForm.getFieldValue('systemPrompt') || '';
    const variables = extractVariablesFromPrompt(currentPrompt);
    return variables.length > 0 && currentModelId !== undefined;
  }, [configForm, extractVariablesFromPrompt, formValues]);

  const modelConfigList = useMemo(() => {
    return Object.entries(modelConf).map(([key, value]) => {
      return { key, value }
    })
  }, [modelConf]);


  // Version record table column config
  const versionColumns = [
    {
      title: 'Version',
      dataIndex: 'version',
      key: 'version',
      render: (text: string) => <Tag color="blue">{text}</Tag>
    },
    {
      title: 'Description',
      dataIndex: 'description',
      key: 'description',
      render: (text: string) => text || '-'
    },
    {
      title: 'Judge Model',
      dataIndex: 'modelConfig',
      key: 'modelConfig',
      render: (modelConfig: string) => {
        const { modelName } = extractModelInfoFromConfig(modelConfig);
        return <Tag color="geekblue">{modelName}</Tag>;
      }
    },
    {
      title: 'Created',
      dataIndex: 'createTime',
      key: 'createTime',
      render: (text: string) => formatDateTime(text)
    }
  ];


  const handleExperimentSearch = (value: string) => {
    setExperimentPagination(prev => ({
      ...prev,
      current: 1,
    }));
    setExperimentSearch(value);
  }

  const loadExperiments = (search = experimentSearch, page = { pageNumber: experimentPagination.current, pageSize: experimentPagination.pageSize }) => {
    setExperimentsLoading(true);
    if (!evaluator) return;
    API.getEvaluatorExperiments({
      pageNumber: page.pageNumber,
      pageSize: page.pageSize,
      evaluatorId: evaluator!.id,
    }).then(({ data }) => {
      const dataPageItems = data.pageItems || [];
      dataPageItems.map((item: any) => {
        const itemEvaluatorConfig = JSON.parse(item.evaluatorConfig || '[]');
        const findItemEvaluatorConfig = itemEvaluatorConfig.find((c: any) =>
          c.evaluatorId === evaluator!.id
        )
        item.version = findItemEvaluatorConfig?.evaluatorVersionName || '';
        return item;
      })
      setExperiments(dataPageItems);
      setExperimentPagination({
        ...experimentPagination,
        current: data.pageNumber,
        pageSize: data.pageSize,
        total: data.totalCount,
      });
    }).finally(() => {
      setExperimentsLoading(false);
    })
  }

  useEffect(() => {
    loadExperiments();
  }, [evaluator?.id, experimentPagination.current, experimentPagination.pageSize])

  if (loading) {
    return (
      <div className="p-6">
        <div className="flex items-center justify-center h-64">
          <Spin size="large">
            <div className="text-center pt-4">
              <p className="text-gray-600 mt-4">Loading evaluator details...</p>
            </div>
          </Spin>
        </div>
      </div>
    );
  }

  if (error || !evaluator) {
    return (
      <div className="p-6">
        <Alert
          message="Loading Failed"
          description={error || 'Evaluator does not exist'}
          type="error"
          showIcon
          action={
            <Space>
              <Button size="small" onClick={loadEvaluatorDetail}>
                Retry
              </Button>
              <Button size="small" onClick={() => navigate('/evaluation-evaluator')}>
                Back to List
              </Button>
            </Space>
          }
        />
      </div>
    );
  }

  return (
    <div className="evaluator-detail-page p-8 fade-in">
      {/* Page header */}
      <div className="flex mb-6">
          <Button
            type="text"
            icon={<ArrowLeftOutlined />}
            onClick={() => navigate('/evaluation-evaluator')}
            size="large"
          />
          <Title level={2} className="m-0">Evaluator Details</Title>
      </div>

      {/* Evaluator basic info */}
      <Card
        title={
          <div className="flex justify-between items-center">
            <span>Basic Information</span>
            <div>
              {isEditing ? (
                <Space>
                  <Button
                    size="small"
                    onClick={() => {
                      setIsEditing(false);
                      form.setFieldsValue({
                        name: evaluator.name,
                        description: evaluator.description
                      });
                    }}
                    icon={<CloseOutlined />}
                  >
                    Cancel
                  </Button>
                  <Button
                    type="primary"
                    size="small"
                    loading={editLoading}
                    onClick={handleSaveBasicInfo}
                    icon={<SaveOutlined />}
                  >
                    Save
                  </Button>
                </Space>
              ) : (
                <Button
                  size="small"
                  onClick={() => setIsEditing(true)}
                  icon={<EditOutlined />}
                >
                  Edit
                </Button>
              )}
            </div>
          </div>
        }
        className="mb-6"
      >
        {isEditing ? (
          <Form form={form} layout="vertical">
            <Row gutter={24}>
              <Col span={12}>
                <Form.Item
                  label="Name"
                  name="name"
                  rules={[
                    { required: true, message: 'Please enter evaluator name' },
                    { max: 50, message: 'Name cannot exceed 50 characters' }
                  ]}
                >
                  <Input placeholder="Enter evaluator name" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item
                  label="Description"
                  name="description"
                  rules={[{ max: 500, message: 'Description cannot exceed 500 characters' }]}
                >
                  <TextArea
                    placeholder="Enter evaluator description (optional)"
                    rows={3}
                    showCount
                    maxLength={500}
                  />
                </Form.Item>
              </Col>
            </Row>
          </Form>
        ) : (
          <Row gutter={[24, 16]}>
            <Col xs={24} sm={12} lg={6}>
              <div>
                <Text type="secondary" style={{ textTransform: 'uppercase', letterSpacing: '0.05em', fontSize: '12px' }}>
                  Evaluator Name
                </Text>
                <div style={{ marginTop: 4 }}>
                  <Text strong style={{ fontSize: '16px' }}>{evaluator.name}</Text>
                </div>
              </div>
            </Col>

            <Col xs={24} sm={12} lg={6}>
              <div>
                <Text type="secondary" style={{ textTransform: 'uppercase', letterSpacing: '0.05em', fontSize: '12px' }}>
                  Current Version
                </Text>
                <div style={{ marginTop: 4 }}>
                  {evaluator.latestVersion ? (
                    <Tag color="blue">{evaluator.latestVersion}</Tag>
                  ) : (
                    <Tag color="default">No Version</Tag>
                  )}
                </div>
              </div>
            </Col>

            <Col xs={24} sm={12} lg={6}>
              <div>
                <Text type="secondary" style={{ textTransform: 'uppercase', letterSpacing: '0.05em', fontSize: '12px' }}>
                  Current Model
                </Text>
                <div style={{ marginTop: 4 }}>
                  {(() => {
                    const { modelName } = getCurrentModelInfo();
                    return (
                      <Tag color="geekblue">{modelName}</Tag>
                    );
                  })()}
                </div>
              </div>
            </Col>
          </Row>
        )}

        {!isEditing && evaluator.description && (
          <div style={{ marginTop: 16 }}>
            <Text type="secondary" style={{ textTransform: 'uppercase', letterSpacing: '0.05em', fontSize: '12px' }}>
              Description
            </Text>
            <div style={{ marginTop: 8 }}>
              <Text>{evaluator.description}</Text>
            </div>
          </div>
        )}

        {!isEditing && (
          <>
            <Divider />
            <Row gutter={[16, 8]}>
              <Col span={12}>
                <Text type="secondary">
                  Created: {formatDateTime(evaluator.createTime)}
                </Text>
              </Col>
              <Col span={12}>
                <Text type="secondary">
                  Updated: {formatDateTime(evaluator.updateTime)}
                </Text>
              </Col>
            </Row>
          </>
        )}
      </Card>

      {/* Bottom Tab area */}
      <Card>
        <Tabs
          activeKey={activeTab}
          onChange={handleTabChange}
          items={[
            {
              key: 'config',
              label: 'Model Configuration',
              children: (
                <div>
                  <div className="flex justify-between items-center mb-4">
                    <Title level={4} className="m-0">Model Configuration</Title>
                    <Space>
                      <Button onClick={handleOpenTemplateModal}>Import from Template</Button>
                      <Tooltip title="Go to debug page">
                        <Button
                          icon={<BugOutlined />}
                          onClick={handleDebug}
                          disabled={!canPublishVersion}
                        >
                          Debug
                        </Button>
                      </Tooltip>
                      <Tooltip title={canPublishVersion ? "Publish new version" : "System Prompt must contain variables to publish a version"}>
                        <Button
                          type="primary"
                          icon={<RocketOutlined />}
                          onClick={handlePublishVersion}
                          disabled={!canPublishVersion}
                        >
                          Publish New Version
                        </Button>
                      </Tooltip>
                    </Space>
                  </div>

                  <Form form={configForm} layout="vertical">
                    <Row gutter={24}>
                      <Col span={24}>
                        <Form.Item label="Judge Model" name="modelId" required>
                          <Select placeholder="Select model" onChange={handleModelChange}>
                            {models.map(model => (
                              <Option key={model.id} value={model.id}>
                                {model.name}
                              </Option>
                            ))}
                          </Select>
                        </Form.Item>
                      </Col>
                      {
                        modelConfigList.map(({ key, value }) => {
                          const isNumber = !isNaN(Number(value))
                          if (isNumber) {
                            return (
                              <Col span={12} key={key}>
                                <Form.Item label={key} name={key}>
                                  <InputNumber
                                    style={{ width: '100%' }}
                                    placeholder={value}
                                  />
                                </Form.Item>
                              </Col>
                            )
                          }
                          return (
                            <Col span={12} key={key}>
                              <Form.Item label={key} name={key}>
                                <Input
                                  style={{ width: '100%' }}
                                  placeholder={value}
                                />
                              </Form.Item>
                            </Col>
                          )
                        })
                      }
                      <Col span={24}>
                        <Form.Item
                          required
                          label={
                            <div className="flex items-center gap-2">
                              <span>System Prompt</span>
                              <Tooltip title="Use {{variable_name}} format to define variables">
                                <InfoCircleOutlined className="text-gray-400" />
                              </Tooltip>
                            </div>
                          }
                          name="systemPrompt"
                        >
                          <TextArea
                            rows={3}
                            placeholder="Enter system prompt, use {{variable_name}} to define variables"
                            onChange={(e) => {
                              // Real-time display of detected variables
                              const newPrompt = e.target.value;
                              const variables = extractVariablesFromPrompt(newPrompt);

                              // Clean up variable values that no longer exist
                              setVariableValues(prev => {
                                const newValues: Record<string, string> = {};
                                variables.forEach(name => {
                                  newValues[name] = prev[name] || '';
                                });
                                return newValues;
                              });
                            }}
                          />
                        </Form.Item>
                        {/* Display detected variables */}
                        <Form.Item required noStyle>
                          <Form.Item required dependencies={['systemPrompt']} noStyle>
                            {({ getFieldValue }) => {
                              const promptValue = getFieldValue('systemPrompt') || '';
                              const variableNames = extractVariablesFromPrompt(promptValue);

                              if (variableNames.length > 0) {
                                return (
                                  <div className="mb-4">
                                    <div className="p-3 bg-blue-50 border border-blue-200 rounded mb-4">
                                      <div className="flex items-center gap-2 mb-2">
                                        <InfoCircleOutlined className="text-blue-500" />
                                        <span className="text-sm font-medium text-blue-700">
                                          Detected Variables ({variableNames.length})
                                        </span>
                                      </div>
                                      <div className="flex flex-wrap gap-2">
                                        {variableNames.map(name => (
                                          <Tag key={name} color="blue" className="mb-1">
                                            {name}
                                          </Tag>
                                        ))}
                                      </div>
                                    </div>
                                  </div>
                                );
                              } else if (promptValue.trim()) {
                                // If has prompt content but no variables, show warning
                                return (
                                  <div className="mb-4">
                                    <Alert
                                      message="No Variables Detected"
                                      description="No variables detected in System Prompt (format: {{variable_name}}). Variables must be added to publish a version."
                                      type="warning"
                                      showIcon
                                      icon={<ExclamationCircleOutlined />}
                                    />
                                  </div>
                                );
                              }
                              return null;
                            }}
                          </Form.Item>
                        </Form.Item>
                      </Col>
                    </Row>
                  </Form>
                </div>
              )
            },
            {
              key: 'versions',
              label: 'Version Records',
              children: (
                <Table
                  columns={versionColumns}
                  dataSource={versions}
                  loading={versionsLoading}
                  rowKey="id"
                  pagination={{
                    ...versionPagination,
                    onChange: onVersionPaginationChange,
                    onShowSizeChange: onVersionShowSizeChange
                  }}
                />
              )
            },
            {
              key: 'experiments',
              label: 'Related Experiments',
              children: (
                <div>
                  {/* <Row className="mb-4">
                    <Col span={6}>
                      <Input.Search
                        placeholder="Search experiments"
                        onSearch={handleExperimentSearch}
                      />
                    </Col>
                  </Row> */}
                  <Table
                    dataSource={experiments}
                    rowKey="id"
                    loading={experimentsLoading}
                    pagination={{
                      ...experimentPagination,
                      onChange: onExperimentPaginationChange,
                      onShowSizeChange: onExperimentShowSizeChange,
                    }}
                    columns={[
                      {
                        title: 'Version',
                        dataIndex: 'version',
                        width: '15%',
                        render: (version: string) => (
                          <Tag color="cyan">{version}</Tag>
                        )
                      },
                      {
                        title: 'Experiment Name',
                        dataIndex: 'name',
                        width: '10%',
                        render: (name: string, record: any) => (
                          <span
                            className="text-blue-600 cursor-pointer hover:text-blue-800 hover:underline font-medium"
                            onClick={() => {
                              // Navigate to experiment detail page
                              navigate(`/evaluation-experiment/detail/${record.id}`);
                            }}
                          >
                            {name}
                          </span>
                        )
                      },
                      {
                        title: "Description",
                        dataIndex: 'description',
                        width: '25%',
                        ellipsis: true,
                        render: (description: string) => (
                          <Tooltip title={description}>
                            <span className="text-xs truncate">{description}</span>
                          </Tooltip>
                        )
                      },
                      {
                        title: 'Status',
                        dataIndex: 'status',
                        width: '15%',
                        render: (status: string) => {
                          const statusConfig = {
                            'RUNNING': { color: 'processing', text: 'Running' },
                            'COMPLETED': { color: 'success', text: 'Completed' },
                            'FAILED': { color: 'error', text: 'Stopped' },
                            'WAITING': { color: 'default', text: 'Waiting' }
                          };
                          const config = statusConfig[status as keyof typeof statusConfig] || statusConfig['WAITING'];
                          return <Tag color={config.color}>{config.text}</Tag>;
                        }
                      },
                      {
                        title: 'Created',
                        dataIndex: 'createTime',
                        width: '30%',
                        render: (text: string) => formatDateTime(text)
                      }
                    ]}
                  />
                </div>
              )
            }
          ]}
        />
      </Card>

      {/* Version publish modal */}
      <Modal
        title={
          <div className="flex items-center gap-3">
            <RocketOutlined className="text-blue-500" />
            <span>Publish New Version</span>
          </div>
        }
        open={showPublishModal}
        onCancel={() => {
          setShowPublishModal(false);
          publishForm.resetFields();
        }}
        onOk={handlePublishConfirm}
        confirmLoading={publishLoading}
        okText="Publish Version"
        cancelText="Cancel"
        width={520}
        centered
      >
        <div className="pt-4">
          <Form
            form={publishForm}
            layout="vertical"
          >
            <Form.Item
              label="Version Number"
              name="version"
              rules={[
                { required: true, message: 'Please enter version number' },
                {
                  pattern: /^\d+\.\d+\.\d+$/,
                  message: 'Version format should be x.y.z (e.g., 1.0.0)'
                }
              ]}
            >
              <Input placeholder="Enter version number, e.g., 1.0.0" />
            </Form.Item>

            <Form.Item
              label="Version Description"
              name="description"
              rules={[
                { max: 200, message: 'Description cannot exceed 200 characters' }
              ]}
            >
              <TextArea
                placeholder="Enter version description (optional)"
                rows={3}
                showCount
                maxLength={200}
              />
            </Form.Item>
          </Form>
        </div>
      </Modal>

      {/* Template import modal */}
      <Modal
        title={
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 bg-blue-50 rounded-full flex items-center justify-center">
              <InfoCircleOutlined className="text-blue-500 text-xl" />
            </div>
            <div>
              <Title level={3} className="m-0">Import from Template</Title>
              <Text type="secondary">Select a preset template to quickly configure the evaluator</Text>
            </div>
          </div>
        }
        open={showTemplateModal}
        onCancel={() => {
          setShowTemplateModal(false);
          setSelectedTemplateId(null);
          setSelectedTemplateDetail(null);
        }}
        width={1000}
        centered
        footer={[
          <Button key="cancel" onClick={() => {
            setShowTemplateModal(false);
            setSelectedTemplateId(null);
            setSelectedTemplateDetail(null);
          }}>
            Cancel
          </Button>,
          <Button
            key="import"
            type="primary"
            disabled={!selectedTemplateDetail}
            onClick={handleTemplateImport}
            icon={<SaveOutlined />}
          >
            Import Template
          </Button>
        ]}
      >
        <Row gutter={24}>
          {/* Left side: Template list */}
          <Col span={16}>
            <Card
              title={
                <div className="flex items-center gap-2">
                  <span>Select Template</span>
                  <Text type="secondary">({templates.length} templates)</Text>
                </div>
              }
              size="small"
            >
              <Spin spinning={templatesLoading}>
                {templates.length > 0 ? (
                  <Row gutter={[16, 16]}>
                    {templates.map(template => (
                      <Col span={12} key={template.id}>
                        <Card
                          size="small"
                          hoverable
                          onClick={() => handleTemplateSelect(template.id)}
                          className={selectedTemplateId === template.id ? 'border-blue-500 bg-blue-50' : ''}
                          classNames={{
                            body: 'p-3'
                          }}
                        >
                          <div className="flex justify-between items-start mb-2">
                            <Text strong className="text-sm">{template.templateDesc}</Text>
                            {selectedTemplateId === template.id && (
                              <CheckCircleOutlined className="text-blue-500" />
                            )}
                          </div>
                          <Text type="secondary" className="text-xs block mb-2" ellipsis>
                            {template.evaluatorTemplateKey}
                          </Text>
                        </Card>
                      </Col>
                    ))}
                  </Row>
                ) : (
                  <div className="text-center py-16">
                    <Text type="secondary">No template data available</Text>
                  </div>
                )}
              </Spin>
            </Card>
          </Col>

          {/* Right side: Template preview */}
          <Col span={8}>
            <Card
              title={
                <div className="flex items-center gap-2">
                  <InfoCircleOutlined />
                  <span>Template Preview</span>
                </div>
              }
              size="small"
            >
              <Spin spinning={templateDetailLoading}>
                {selectedTemplateDetail ? (
                  <div className="space-y-4">
                    <div>
                      <Text strong className="block mb-1">Template Name</Text>
                      <Text>{selectedTemplateDetail.templateDesc}</Text>
                    </div>

                    <div>
                      <Text strong className="block mb-1">Template Key</Text>
                      <Text>{selectedTemplateDetail.evaluatorTemplateKey}</Text>
                    </div>

                    {selectedTemplateDetail.template && (
                      <div>
                        <Text strong className="block mb-2">Prompt Content</Text>
                        <div className="bg-gray-50 p-3 rounded border text-xs font-mono max-h-48 overflow-y-auto whitespace-pre-wrap">
                          {selectedTemplateDetail.template}
                        </div>
                      </div>
                    )}

                    {selectedTemplateDetail.modelConfig && (
                      <div>
                        <Text strong className="block mb-2">Model Configuration</Text>
                        <div className="bg-gray-50 p-3 rounded border text-xs font-mono">
                          {JSON.stringify(JSON.parse(selectedTemplateDetail.modelConfig), null, 2)}
                        </div>
                      </div>
                    )}

                    {selectedTemplateDetail.variables && (
                      <div>
                        <Text strong className="block mb-2">Variables</Text>
                        <div className="bg-gray-50 p-3 rounded border text-xs font-mono">
                          {selectedTemplateDetail.variables}
                        </div>
                      </div>
                    )}
                  </div>
                ) : (
                  <div className="text-center py-12">
                    <InfoCircleOutlined className="text-4xl text-gray-300 mb-4" />
                    <br />
                    <Text type="secondary">Click a template on the left to view details</Text>
                  </div>
                )}
              </Spin>
            </Card>
          </Col>
        </Row>
      </Modal>
    </div>
  );
}

export default EvaluatorDetail;
