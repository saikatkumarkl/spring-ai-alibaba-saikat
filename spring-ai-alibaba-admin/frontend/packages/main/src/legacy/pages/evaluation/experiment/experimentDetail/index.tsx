import React, { useState, useEffect } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import { Button, Card, Progress, Tag, Alert, Spin, Table, message, Typography, Tooltip } from 'antd';
import { ArrowLeftOutlined, StopOutlined, ReloadOutlined } from '@ant-design/icons';
import API from '../../../../services';
import usePagination from '../../../../hooks/usePagination';
import './index.css';

const { Title, Text } = Typography;

// CSS style for hiding scrollbar
const scrollbarHideStyle = `
  .scrollbar-hide::-webkit-scrollbar {
    display: none;
  }
  .scrollbar-hide {
    -ms-overflow-style: none;
    scrollbar-width: none;
  }
`;

// Format time display
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

// Evaluation result data interface
interface EvaluationResult {
  id: number;
  input: string;
  actualOutput: string;
  referenceOutput: string;
  score: number;
  status: 'success' | 'failed';
}

// Execution log data interface
interface ExecutionLog {
  id: number;
  timestamp: string;
  message: string;
  type: 'info' | 'success' | 'error' | 'processing';
  score?: number;
}

// Experiment overview result data interface
interface ExperimentOverview {
  experimentId: number;
  averageScore: number;
  evaluatorVersionId: number;
  progress: number;
  completeItemsCount: number;
  totalItemsCount: number; // Add total data items count field
}

// Tab type
type TabType = 'overview' | 'results';

// Experiment detail data interface
interface ExperimentDetail {
  id: number;
  name: string;
  description: string;
  status: 'RUNNING' | 'COMPLETED' | 'FAILED' | 'WAITING' | 'STOPPED';
  progress: number;
  totalProgress: string;
  averageScore: number;
  createTime: string;
  startTime: string;
  endTime?: string;
  completeTime?: string;
  dataset: {
    name: string;
    id: string;
    columns: string[];
  };
  evaluationObject: {
    type: string;
    promptKey: string;
    version: string;
    promptDetail: string;
    promptContent: string;
    inputTemplate: string;
  };
  evaluators: Array<{
    id: string;
    name: string;
    version: string;
    dataCount: number;
    columns: string[];
  }>;
  evaluationResults: {
    schema: string;
    mapping: Record<string, string>;
    progress: number;
  };
}

// Evaluator configuration interface
interface EvaluatorConfig {
  evaluatorId: number;
  evaluatorVersionId: number;
  evaluatorName: string;
  variableMap: Array<{
    evaluatorVariable: string;
    source: string;
  }>;
  versionName?: string; // Add version name field
}

const ExperimentDetail: React.FC = () => {
  // Add style tag to hide scrollbar
  React.useEffect(() => {
    const style = document.createElement('style');
    style.innerHTML = `
      .scrollbar-hide::-webkit-scrollbar {
        display: none;
      }
      .scrollbar-hide {
        -ms-overflow-style: none;
        scrollbar-width: none;
      }
    `;
    document.head.appendChild(style);

    return () => {
      document.head.removeChild(style);
    };
  }, []);

  const navigate = useNavigate();
  const location = useLocation();
  const { id } = useParams<{ id: string }>();
  // Get evaluatorConfig data passed from location.state
  const passedEvaluatorConfig = location.state?.evaluatorConfig;
  const defaultActiveTab = location.state?.activeTab;
  const [loading, setLoading] = useState(true);
  const [detail, setDetail] = useState<ExperimentDetail | null>(null);
  const [stopping, setStopping] = useState(false);
  const [activeTab, setActiveTab] = useState<TabType>(defaultActiveTab || 'overview');
  const [activeEvaluatorTab, setActiveEvaluatorTab] = useState<string>('test'); // Evaluator sub-tab state
  const [overviewData, setOverviewData] = useState<ExperimentOverview[]>([]); // Overview result data
  const [overviewLoading, setOverviewLoading] = useState(false); // Overview data loading state

  // Evaluation result related state
  const [resultData, setResultData] = useState<EvaluationResult[]>([]); // Current evaluator result data
  const [resultLoading, setResultLoading] = useState(false); // Result data loading state
  const { pagination, setPagination, onPaginationChange, onShowSizeChange } = usePagination();

  // Get evaluation results data
  const fetchExperimentResult = async (evaluatorVersionId: number, pageNumber?: number, pageSize?: number) => {
    if (!id) return;

    try {
      setResultLoading(true);

      // If no pagination parameters, use current pagination state
      const current = pageNumber || pagination.current;
      const size = pageSize || pagination.pageSize;

      const response = await API.getExperimentResult({
        experimentId: Number(id),
        evaluatorVersionId,
        pageNumber: current,
        pageSize: size
      });

      if (response.code === 200 && response.data) {
        const { pageItems, totalCount, pageNumber: currentPage, pageSize: currentPageSize } = response.data as any;

        // Convert data format
        const results: EvaluationResult[] = (pageItems || []).map((item: any) => ({
          id: item.id,
          input: item.input,
          actualOutput: item.actualOutput,
          referenceOutput: item.referenceOutput,
          score: item.score,
          status: item.score > 0.5 ? 'success' : 'failed',
          reason: item.reason || 'No reason available'
        }));

        setResultData(results);
        // Update pagination state
        setPagination(prev => ({
          ...prev,
          current: currentPage || current,
          pageSize: currentPageSize || size,
          total: totalCount || 0
        }));
      } else {
        throw new Error(response.message || 'Failed to fetch evaluation results');
      }
    } catch (error) {
      console.error('Failed to fetch evaluation results:', error);
      // Use empty data on error
      setResultData([]);
      setPagination(prev => ({
        ...prev,
        current: 1,
        total: 0
      }));
    } finally {
      setResultLoading(false);
    }
  };

  // Get experiment overview results
  const fetchExperimentOverview = async () => {
    if (!id) return;

    try {
      setOverviewLoading(true);

      const response = await API.getExperimentOverview({ experimentId: Number(id) });

      if (response.code === 200 && response.data) {
        // response.data is already array format
        const overviewList = response.data as any;
        setOverviewData(overviewList);
      } else {
        throw new Error(response.message || 'Failed to fetch overview data');
      }
    } catch (error) {
      console.error('Failed to fetch overview data:', error);
      // Use empty data on error
      setOverviewData([]);
    } finally {
      setOverviewLoading(false);
    }
  };

  // Get experiment details
  const fetchExperimentDetail = async () => {
    try {
      setLoading(true);

      // Call details API
      const response = await API.getExperiment({ experimentId: Number(id) });

      if (response.code === 200 && response.data) {
        const apiData = response.data as any;

        // Parse evaluationObjectConfig
        let evaluationObject;
        try {
          evaluationObject = JSON.parse(apiData.evaluationObjectConfig || '{}');
        } catch (e) {
          console.warn('Failed to parse evaluationObjectConfig:', e);
          evaluationObject = { type: '', config: {} };
        }

        // Parse evaluatorConfig
        let evaluatorConfigs: EvaluatorConfig[] = [];
        try {
          evaluatorConfigs = JSON.parse(apiData.evaluatorConfig || '[]');
        } catch (e) {
          console.warn('Failed to parse evaluatorConfig from API:', e);
          evaluatorConfigs = [];
        }

        // If has passed evaluatorConfig data, try to parse and merge
        if (passedEvaluatorConfig) {
          try {
            const passedConfigs = JSON.parse(passedEvaluatorConfig || '[]');
            // Merge logic: use passed data to supplement missing fields in API data
            evaluatorConfigs = evaluatorConfigs.map(apiConfig => {
              const passedConfig = passedConfigs.find((c: any) =>
                c.evaluatorId === apiConfig.evaluatorId && c.evaluatorVersionId === apiConfig.evaluatorVersionId
              );
              // If corresponding passed config is found, merge data
              if (passedConfig) {
                return {
                  ...passedConfig,
                  ...apiConfig // API data takes priority, overrides passed data
                };
              }
              return apiConfig;
            });

            // Add configurations present in passed data but not in API data
            passedConfigs.forEach((passedConfig: any) => {
              const exists = evaluatorConfigs.some(apiConfig =>
                apiConfig.evaluatorId === passedConfig.evaluatorId && apiConfig.evaluatorVersionId === passedConfig.evaluatorVersionId
              );
              if (!exists) {
                evaluatorConfigs.push(passedConfig);
              }
            });
          } catch (e) {
            console.warn('Failed to parse passed evaluatorConfig:', e);
          }
        }

        // Convert API data to format required by component
        const detailData: ExperimentDetail = {
          id: apiData.id,
          name: apiData.name,
          description: apiData.description,
          status: apiData.status as 'RUNNING' | 'COMPLETED' | 'FAILED' | 'WAITING' | 'STOPPED',
          progress: apiData.progress || 0,
          totalProgress: `${apiData.progress || 0}%`,
          averageScore: 0.85, // Default value, can be fetched from results API later
          createTime: apiData.createTime,
          startTime: apiData.createTime,
          endTime: apiData.completeTime || undefined,
          completeTime: apiData.completeTime || undefined,
          dataset: {
            name: 'Default Evaluation Dataset', // Default value, can be fetched via datasetId query later
            id: apiData.datasetId.toString(),
            columns: ['input', 'reference_output']
          },
          evaluationObject: {
            type: evaluationObject.type || 'Prompt',
            promptKey: evaluationObject.config?.promptKey || '',
            version: evaluationObject.config?.versionId || '',
            promptDetail: evaluationObject.config?.promptDescription || '',
            promptContent: 'Default Prompt Content', // Default value
            inputTemplate: '{{input}}'
          },
          evaluators: evaluatorConfigs.map((config: EvaluatorConfig, index: number) => ({
            // Use unique ID, combine with index to ensure uniqueness
            id: `${config.evaluatorId}-${config.evaluatorVersionId}-${index}`,
            name: config.evaluatorName,
            version: config.versionName || (config as any).evaluatorVersionName || '1.0.0', // Prioritize versionName field, then evaluatorVersionName field
            dataCount: 150,
            columns: ['input', 'reference_output']
          })),
          evaluationResults: {
            schema: 'Field Mapping',
            mapping: {
              'evaluator.input': 'dataset.input',
              'evaluator.output': 'evaluation_object.output', // Remove hardcoded actual_output
              'evaluator.reference_output': 'dataset.reference_output'
            },
            progress: apiData.progress || 0
          }
        };

        setDetail(detailData);
      } else {
        throw new Error(response.message || 'Failed to fetch experiment details');
      }
    } catch (error) {
      console.error('Failed to fetch experiment details:', error);

      // Error handling, set to empty state
      setDetail(null);
      setLoading(false);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (id) {
      fetchExperimentDetail();
    }
  }, [id]);

  // After detail loading completes, fetch overview data if on overview tab or evaluation results tab
  useEffect(() => {
    if (detail && (activeTab === 'overview' || activeTab === 'results')) {
      fetchExperimentOverview();
    }
  }, [detail, activeTab]);

  // In evaluation results tab, fetch corresponding evaluator result data when sub-tab switches
  useEffect(() => {
    if (detail && activeTab === 'results') {
      // Find corresponding evaluator based on activeEvaluatorTab
      const currentEvaluator = detail.evaluators.find((evaluator, index) =>
        `evaluator-${evaluator.id}-${evaluator.version}-${index}` === activeEvaluatorTab
      ) || detail.evaluators[0];

      if (currentEvaluator) {
        // Use current evaluator's ID as evaluatorVersionId parameter to call API
        // Note: Here we use evaluatorVersionId instead of id
        const evaluatorVersionId = parseInt(currentEvaluator.id.split('-')[1]); // Extract evaluatorVersionId from id
        // Reset pagination to page 1, page size 10
        setPagination(prev => ({
          ...prev,
          current: 1,
          pageSize: 10,
          total: prev.total
        }));
        // Fetch data using reset pagination
        fetchExperimentResult(evaluatorVersionId, 1, 10);
      }
    }
  }, [detail, activeTab, activeEvaluatorTab]); // Re-fetch data when activeEvaluatorTab changes

  // When entering from view experiment results and evaluation results tab is selected, automatically select first evaluator sub-tab
  useEffect(() => {
    if (detail && activeTab === 'results') {
      // Automatically select first evaluator sub-tab
      if (detail.evaluators && detail.evaluators.length > 0) {
        const firstEvaluator = detail.evaluators[0];
        // Use new unique tabKey
        const firstTabKey = `evaluator-${firstEvaluator.id}-${firstEvaluator.version}-0`;
        setActiveEvaluatorTab(firstTabKey);
      } else {
        // If no evaluator data, set default tab
        setActiveEvaluatorTab('test');
      }
    }
  }, [detail, activeTab]);

  // Return to list page
  const handleGoBack = () => {
    navigate('/evaluation-experiment');
  };

  // Stop experiment
  const handleStopExperiment = async () => {
    try {
      setStopping(true);

      // Call stop experiment API
      const response = await API.stopExperiment({ experimentId: Number(id) });

      if (response.code === 200) {
        // Stop successful, update experiment status
        if (detail) {
          setDetail({ ...detail, status: 'FAILED' });
        }
        // Re-fetch details to get latest status
        await fetchExperimentDetail();
      } else {
        throw new Error(response.message || 'Failed to stop experiment');
      }
    } catch (error) {
      console.error('Failed to stop experiment:', error);

      // Error handling
      message.error('Failed to stop experiment, please try again');
    } finally {
      setStopping(false);
    }
  };

  // Render status tag
  const renderStatusTag = (status: string) => {
    const statusConfig = {
      RUNNING: { color: 'blue', text: 'Running' },
      COMPLETED: { color: 'green', text: 'Completed' },
      FAILED: { color: 'red', text: 'Failed' },
      WAITING: { color: 'default', text: 'Waiting' },
      STOPPED: { color: 'orange', text: 'Stopped' }
    };
    const config = statusConfig[status as keyof typeof statusConfig];
    return <Tag color={config?.color || 'default'}>{config?.text || status}</Tag>;
  };

  if (loading) {
    return (
      <div className="experiment-detail-page">
        <div className="p-6 text-center" style={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          height: '100vh',
          width: '100%',
          position: 'fixed',
          top: 0,
          left: 0,
          zIndex: 1000,
          backgroundColor: 'white'
        }}>
          <Spin size="large" />
        </div>
      </div>
    );
  }

  if (!detail && !loading) {
    return (
      <div className="experiment-detail-page">
        <div className="p-6 text-center" style={{
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          alignItems: 'center',
          height: '100vh',
          width: '100%'
        }}>
          <p style={{ marginBottom: '20px' }}>Experiment details not found</p>
          <Button onClick={handleGoBack}>Return to List</Button>
        </div>
      </div>
    );
  }

  return (
    <div className="experiment-detail-page p-8 fade-in">
      {/* Page header */}
      <div className="flex justify-between items-center mb-6">
        <div className="flex">
          <Button
            type="text"
            icon={<ArrowLeftOutlined />}
            onClick={handleGoBack}
            size="large"
          >
          </Button>
          <div className="flex">
            <Title level={2} className="mr-4">{detail?.name}</Title>
            <span className="text-2xl font-semibold mb-0">{detail && renderStatusTag(detail.status)}</span>
          </div>
        </div>

        {detail?.status === 'RUNNING' && (
          <div>
            <Button
              icon={<ReloadOutlined />}
              onClick={fetchExperimentDetail}
              className="mr-4"
              title="Refresh"
            >
              Refresh
            </Button>
            <Button
              danger
              icon={<StopOutlined />}
              loading={stopping}
              onClick={handleStopExperiment}
            >
              Stop Experiment
            </Button>
          </div>
        )}
      </div>

      {/* Experiment status information */}
      {/* {detail.status === 'RUNNING' && (
        <Alert
          message="Experiment is running"
          description={`Current progress: ${detail.progress}%, processed ${detail.totalProgress} items`}
          type="info"
          showIcon
          className="mb-6"
        />
      )} */}

      {/* Experiment information area */}
      <Card className="mb-6">
        <div className="grid grid-cols-2 gap-8">
          {/* Left column */}
          <div className="space-y-4">
            <div className="flex items-center">
              <span className="text-sm font-medium flex-shrink-0" style={{ color: 'rgba(0, 0, 0, 0.45)' }}>Experiment Name:</span>
              <span className="text-base text-gray-900" style={{ wordBreak: 'break-word' }}>{detail?.name || '-'}</span>
            </div>
            <div className="flex items-center">
              <span className="text-sm font-medium flex-shrink-0" style={{ color: 'rgba(0, 0, 0, 0.45)' }}>Description:</span>
              <span className="text-base text-gray-900" style={{ wordBreak: 'break-word' }}>{detail?.description || '-'}</span>
            </div>
            <div className="flex items-center">
              <span className="text-sm font-medium flex-shrink-0" style={{ color: 'rgba(0, 0, 0, 0.45)' }}>Dataset:</span>
              <span className="text-base text-gray-900" style={{ wordBreak: 'break-word' }}>{detail?.dataset?.name || '-'}</span>
            </div>
            {/* Evaluators field and content displayed on one line */}
            <div className="flex items-center">
              <span className="text-sm font-medium flex-shrink-0" style={{ color: 'rgba(0, 0, 0, 0.45)' }}>Evaluators:</span>
              <div>
                {detail?.evaluators && detail?.evaluators?.length > 0 ? (
                  detail?.evaluators.map((evaluator, index) => (
                    <Tag key={index} color="blue" className="mr-2">
                      {evaluator.name}
                    </Tag>
                  ))
                ) : (
                  <span className="text-base text-gray-900">-</span>
                )}
              </div>
            </div>
          </div>

          {/* Right column */}
          <div className="space-y-4">
            <div className="flex items-center">
              <span className="text-sm font-medium flex-shrink-0" style={{ color: 'rgba(0, 0, 0, 0.45)' }}>Status:</span>
              <span className="text-base">{detail && renderStatusTag(detail.status)}</span>
            </div>
            {/* <div className="flex items-center">
              <span className="text-sm font-medium" style={{ color: 'rgba(0, 0, 0, 0.45)' }}>Creator:</span>
              <span className="text-base text-gray-900">{detail.creator}</span>
            </div> */}
            <div className="flex items-center">
              <span className="text-sm font-medium flex-shrink-0" style={{ color: 'rgba(0, 0, 0, 0.45)' }}>Evaluation Target:</span>
              <span className="text-base text-gray-900" style={{ wordBreak: 'break-word' }}>{detail?.evaluationObject?.type || '-'}</span>
            </div>
            <div className="flex items-center">
              <span className="text-sm font-medium flex-shrink-0" style={{ color: 'rgba(0, 0, 0, 0.45)' }}>Update Time:</span>
              <span className="text-base text-gray-900">{detail && formatDateTime(detail.startTime)}</span>
            </div>
            <div className="flex items-center">
              <span className="text-sm font-medium flex-shrink-0" style={{ color: 'rgba(0, 0, 0, 0.45)' }}>Create Time:</span>
              <span className="text-base text-gray-900">{detail && formatDateTime(detail.createTime)}</span>
            </div>
          </div>
        </div>
      </Card>

      {/* Tab navigation */}
      <Card>
        <div className="mb-6">
          <div className="border-b border-gray-200 mb-4">
            <div className="flex space-x-8">
              <div
                className={`pb-2 cursor-pointer font-medium ${
                  activeTab === 'overview'
                    ? 'border-b-2 border-blue-500 text-blue-600'
                    : 'text-gray-500 hover:text-gray-700'
                }`}
                onClick={() => setActiveTab('overview')}
              >
                Overview
              </div>
              <div
                className={`pb-2 cursor-pointer font-medium ${
                  activeTab === 'results'
                    ? 'border-b-2 border-blue-500 text-blue-600'
                    : 'text-gray-500 hover:text-gray-700'
                }`}
                onClick={() => {
                  setActiveTab('results');
                  // When switching to evaluation results tab, automatically select first evaluator sub-tab
                  if (detail?.evaluators && detail.evaluators.length > 0) {
                    const firstEvaluator = detail.evaluators[0];
                    // Use new unique tabKey
                    const firstTabKey = `evaluator-${firstEvaluator.id}-${firstEvaluator.version}-0`;
                    setActiveEvaluatorTab(firstTabKey);
                  } else {
                    // If no evaluator data, set default tab
                    setActiveEvaluatorTab('test');
                  }
                }}
              >
                Evaluation Results
              </div>
            </div>
          </div>

          {/* Tab content area */}
          {activeTab === 'overview' && (
            <>
              {/* Evaluator results overview */}
              <div className="mb-6">
                <h3 className="text-lg font-medium mb-4">Evaluator Results Overview</h3>
                <div className="grid grid-cols-2 md:grid-cols-2 lg:grid-cols-3 gap-6">
                  {detail?.evaluators && detail.evaluators.length > 0 ? (
                    detail.evaluators.map((evaluator, index) => {
                      // Get corresponding overview data
                      const overview = overviewData[index] || {
                        evaluatorVersionId: parseInt(evaluator.id),
                        averageScore: 0,
                        progress: 0,
                        completeItemsCount: 0,
                        totalItemsCount: 0
                      };

                      // Calculate progress percentage
                      const progressPercent = overview.totalItemsCount > 0
                        ? Math.round((overview.completeItemsCount / overview.totalItemsCount) * 100)
                        : Math.round(overview.progress);

                      // Determine color based on score
                      const scoreColor = overview.averageScore >= 0.8 ? 'text-green-600' : 'text-orange-500';

                      return (
                        <Card key={evaluator.id} className="p-4">
                          <div className="flex justify-between items-start mb-4">
                            <div>
                              <h4 className="text-base font-medium text-gray-900 mb-1">{evaluator.name}</h4>
                              <p className="text-sm" style={{ color: 'rgba(0, 0, 0, 0.45)' }}>Evaluator Description</p>
                            </div>
                            <Tag color="blue">{evaluator.version}</Tag>
                          </div>

                          <div className="space-y-3">
                            <div>
                              <div className="flex justify-between items-center mb-1">
                                <span className="text-sm" style={{ color: 'rgba(0, 0, 0, 0.45)' }}>Progress</span>
                                <span className="text-sm font-medium">{overview.completeItemsCount}/{overview.totalItemsCount || evaluator.dataCount}</span>
                              </div>
                              <Progress percent={progressPercent} strokeColor="#1677ff" size="small" />
                              <div className="text-right mt-1">
                                <span className="text-sm font-medium">{progressPercent}%</span>
                              </div>
                            </div>

                            <div className="flex justify-between items-center">
                              <span className="text-sm" style={{ color: 'rgba(0, 0, 0, 0.45)' }}>Average Score</span>
                              <span className={`text-lg font-semibold ${scoreColor}`}>{overview.averageScore.toFixed(2)}</span>
                            </div>

                            <div className="text-xs" style={{ color: 'rgba(0, 0, 0, 0.45)' }}>
                              Based on {overview.completeItemsCount} completed evaluations
                            </div>
                          </div>
                        </Card>
                      );
                    })
                  ) : (
                    <div className="col-span-full text-center py-8 text-gray-500">
                      No evaluator data available
                    </div>
                  )}
                </div>
              </div>
            </>
          )}

          {activeTab === 'results' && (
            <>
              {/* Evaluator sub-tab navigation */}
              <div className="border-b border-gray-200 mb-4 ml-4">
                <div className="flex space-x-8 overflow-x-auto whitespace-nowrap scrollbar-hide -mx-4">
                  {detail?.evaluators?.map((evaluator, index) => {
                    // Use unique tabKey, combine evaluator ID, version and index to ensure uniqueness
                    const tabKey = `evaluator-${evaluator.id}-${evaluator.version}-${index}`;
                    const tabName = evaluator.name; // Only display evaluator name, not version number

                    return (
                      <div
                        key={tabKey}
                        className={`pb-2 cursor-pointer font-medium flex-shrink-0 ${
                          activeEvaluatorTab === tabKey
                            ? 'border-b-2 border-blue-500 text-blue-600'
                            : 'text-gray-500 hover:text-gray-700'
                        }`}
                        onClick={() => setActiveEvaluatorTab(tabKey)}
                      >
                        {tabName}
                      </div>
                    );
                  })}

                  {/* If no evaluator data, don't display any tabs */}
                  {(!detail?.evaluators || detail.evaluators.length === 0) && (
                    <div className="text-gray-500 py-2">
                      No evaluator data available
                    </div>
                  )}
                </div>
              </div>

              {/* Current evaluator result content */}
              <div className="mb-4">
                {/* Evaluator information */}
                {detail?.evaluators && detail.evaluators.length > 0 && (() => {
                  // Find corresponding evaluator based on activeEvaluatorTab
                  const currentEvaluator = detail.evaluators.find((evaluator, index) =>
                    `evaluator-${evaluator.id}-${evaluator.version}-${index}` === activeEvaluatorTab
                  ) || detail.evaluators[0];

                  if (currentEvaluator) {
                    // Get corresponding overview data, use same data source as overview page
                    const evaluatorIndex = detail.evaluators.findIndex((evaluator, index) =>
                      `evaluator-${evaluator.id}-${evaluator.version}-${index}` === activeEvaluatorTab
                    );
                    const overview = overviewData[evaluatorIndex] || {
                      evaluatorVersionId: parseInt(currentEvaluator.id),
                      averageScore: 0,
                      progress: 0,
                      completeItemsCount: 0,
                      totalItemsCount: 0
                    };

                    // Calculate progress percentage, consistent with overview page
                    const progressPercent = overview.totalItemsCount > 0
                      ? Math.round((overview.completeItemsCount / overview.totalItemsCount) * 100)
                      : Math.round(overview.progress);

                    // Determine color based on score, consistent with overview page
                    const scoreColor = overview.averageScore >= 0.8 ? 'text-green-600' : 'text-orange-500';

                    return (
                      <div className="mb-4">
                        <div className="text-base font-medium mb-2">{currentEvaluator.name}</div>
                        <div className="flex items-center space-x-6 text-sm">
                          <div>
                            <span style={{ color: 'rgba(0, 0, 0, 0.45)' }}>Average Score:</span>
                            <span className={`text-lg font-semibold ml-2 ${scoreColor}`}>
                              {overview.averageScore.toFixed(2)}
                            </span>
                          </div>
                          <div>
                            <span style={{ color: 'rgba(0, 0, 0, 0.45)' }}>Progress:</span>
                            <span className="font-medium ml-2">
                              {overview.completeItemsCount}/{overview.totalItemsCount || currentEvaluator.dataCount}
                            </span>
                          </div>
                        </div>
                      </div>
                    );
                  }
                  return null;
                })()}

                {/* Detailed results table */}
                <Table
                  dataSource={resultData}
                  loading={resultLoading}
                  rowKey="id"
                  pagination={{
                    current: pagination.current,
                    pageSize: pagination.pageSize,
                    total: pagination.total,
                    showTotal: pagination.showTotal,
                    showSizeChanger: pagination.showSizeChanger,
                    showQuickJumper: pagination.showQuickJumper,
                    pageSizeOptions: pagination.pageSizeOptions,
                    onChange: (page, pageSize) => {
                      // Update pagination state
                      onPaginationChange(page, pageSize);
                      // Find corresponding evaluator based on activeEvaluatorTab
                      const currentEvaluator = detail?.evaluators?.find((evaluator, index) =>
                        `evaluator-${evaluator.id}-${evaluator.version}-${index}` === activeEvaluatorTab
                      ) || detail?.evaluators?.[0];

                      if (currentEvaluator) {
                        // Use current evaluator's ID as evaluatorVersionId parameter to call API
                        const evaluatorVersionId = parseInt(currentEvaluator.id.split('-')[1]);
                        // Fetch data
                        fetchExperimentResult(evaluatorVersionId, page, pageSize);
                      }
                    },
                    onShowSizeChange: (page, pageSize) => {
                      // Update pagination state
                      onShowSizeChange(page, pageSize);
                      // Find corresponding evaluator based on activeEvaluatorTab
                      const currentEvaluator = detail?.evaluators?.find((evaluator, index) =>
                        `evaluator-${evaluator.id}-${evaluator.version}-${index}` === activeEvaluatorTab
                      ) || detail?.evaluators?.[0];

                      if (currentEvaluator) {
                        // Use current evaluator's ID as evaluatorVersionId parameter to call API
                        const evaluatorVersionId = parseInt(currentEvaluator.id.split('-')[1]);
                        // Fetch data
                        fetchExperimentResult(evaluatorVersionId, page, pageSize);
                      }
                    }
                  }}
                  columns={[
                    {
                      title: 'Input',
                      dataIndex: 'input',
                      width: '25%',
                      ellipsis: true,
                      render: (text: string) => (
                        <Tooltip title={text} placement="topLeft">
                          <span>{text}</span>
                        </Tooltip>
                      )
                    },
                    {
                      title: 'Actual Output',
                      dataIndex: 'actualOutput',
                      width: '25%',
                      ellipsis: true,
                      render: (text: string) => (
                        <Tooltip title={text} placement="topLeft">
                          <span>{text}</span>
                        </Tooltip>
                      )
                    },
                    {
                      title: 'Reference Output',
                      dataIndex: 'referenceOutput',
                      width: '25%',
                      ellipsis: true,
                      render: (text: string) => (
                        <Tooltip title={text} placement="topLeft">
                          <span>{text}</span>
                        </Tooltip>
                      )
                    },
                    {
                      title: 'Score',
                      dataIndex: 'score',
                      width: '10%',
                      render: (score: number) => {
                        const scoreColor = score >= 0.8 ? 'text-green-600' : score >= 0.5 ? 'text-orange-500' : 'text-red-500';
                        return (
                          <span className={`font-medium ${scoreColor}`}>
                            {score.toFixed(2)}
                          </span>
                        );
                      }
                    },
                    {
                      title: 'Reason',
                      dataIndex: 'reason',
                      width: '15%',
                      ellipsis: true,
                      render: (text: string) => (
                        <Tooltip title={text} placement="rightTop">
                          <span>{text}</span>
                        </Tooltip>
                      )
                    }
                  ]}
                />
              </div>
            </>
          )}
        </div>
      </Card>
    </div>
  );
};

export default ExperimentDetail;
