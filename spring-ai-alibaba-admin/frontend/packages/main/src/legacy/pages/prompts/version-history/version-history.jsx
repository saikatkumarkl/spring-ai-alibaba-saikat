import React, { useState, useEffect, useContext, useMemo } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import {
  Spin,
  Result,
  Button,
  Alert,
  Empty,
  Table,
  Checkbox,
  Tag,
  Typography,
  Card,
  Space,
  Modal,
  Row,
  Col,
  Tooltip
} from 'antd';
import {
  LoadingOutlined,
  ArrowLeftOutlined,
  InfoCircleOutlined,
  CheckCircleOutlined,
  ExperimentOutlined,
  BranchesOutlined,
  UndoOutlined,
  EyeOutlined
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { handleApiError, notifyError } from '../../../utils/notification';
import VersionCompareModal from '../../../components/VersionCompareModal';
import API from '../../../services';
import { ModelsContext } from '../../../context/models';
import usePagination from '../../../hooks/usePagination';
import { getLegacyPath } from '../../../utils/path';

const { Title, Text, Paragraph } = Typography;

const VersionHistoryPage = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { models } = useContext(ModelsContext);
  const { pagination, onPaginationChange, onShowSizeChange, setPagination } = usePagination();

  const promptKey = searchParams.get('promptKey');
  const targetWindowId = searchParams.get('targetWindowId');

  // State for API data
  const [currentPrompt, setCurrentPrompt] = useState(null);
  const [versions, setVersions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [versionDetailsCache, setVersionDetailsCache] = useState({}); // Cache for version details

  // State for UI interactions
  const [selectedVersions, setSelectedVersions] = useState([]);
  const [showCompare, setShowCompare] = useState(false);
  const [selectedVersion, setSelectedVersion] = useState(null);
  const [showVersionDetail, setShowVersionDetail] = useState(false);
  const [loadingVersionDetail, setLoadingVersionDetail] = useState(false);

  // Time formatting function
  const formatTime = (timestamp) => {
    if (!timestamp) return '';
    return dayjs(timestamp).format('YYYY-MM-DD HH:mm:ss');
  };

  // Load prompt basic info and versions
  const loadPromptData = async (page = pagination.current, pageSize = pagination.pageSize) => {
    if (!promptKey) {
      navigate(getLegacyPath('/prompts'));
      return;
    }

    setLoading(true);
    setError(null);

    try {
      // Load prompt basic info
      const promptResponse = await API.getPrompt({ promptKey });
      if (promptResponse.code !== 200) {
        throw new Error(promptResponse.message || 'Failed to get Prompt info');
      }
      setCurrentPrompt(promptResponse.data);

      // Load version list with pagination
      const versionsResponse = await API.getPromptVersions({
        promptKey,
        pageNo: page,
        pageSize: pageSize // Load all versions for client-side pagination
      });

      if (versionsResponse.code !== 200) {
        throw new Error(versionsResponse.message || 'Failed to get version list');
      }

      setVersions(versionsResponse.data.pageItems || []);
      setPagination({
        ...pagination,
        total: versionsResponse.data.totalCount || 0,
        totalPage: versionsResponse.data.totalPage || 0,
      });
    } catch (err) {
      console.error('Failed to load data:', err);
      handleApiError(err, 'Load version data');
      setError(err.message || 'Failed to load, please try again later');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadPromptData();
  }, [promptKey]);

  // Load version detail with caching
  const loadVersionDetail = async (version) => {
    const cacheKey = `${promptKey}-${version.version}`;

    // Check cache first
    if (versionDetailsCache[cacheKey]) {
      setSelectedVersion({
        ...version,
        ...versionDetailsCache[cacheKey]
      });
      setShowVersionDetail(true);
      return;
    }

    setLoadingVersionDetail(true);
    try {
      const response = await API.getPromptVersion({
        promptKey,
        version: version.version
      });

      if (response.code !== 200) {
        throw new Error(response.message || 'Failed to get version details');
      }

      const versionDetail = response.data;

      // Parse JSON strings
      const variables = versionDetail.variables ? JSON.parse(versionDetail.variables) : {};
      const modelConfig = versionDetail.modelConfig ? JSON.parse(versionDetail.modelConfig) : null;
      const parameters = Object.keys(variables);

      // Create enhanced version object
      const enhancedVersion = {
        ...version,
        template: versionDetail.template,
        variables,
        modelConfig,
        parameters,
        content: versionDetail.template, // For backward compatibility with existing UI
        description: versionDetail.versionDescription,
        versionType: version.status // Map status to versionType for UI compatibility
      };

      // Cache the result
      setVersionDetailsCache(prev => ({
        ...prev,
        [cacheKey]: {
          template: versionDetail.template,
          variables,
          modelConfig,
          parameters,
          content: versionDetail.template,
          description: versionDetail.versionDescription,
          versionType: version.status
        }
      }));

      setSelectedVersion(enhancedVersion);
      setShowVersionDetail(true);
    } catch (err) {
      console.error('Failed to load version details:', err);
      handleApiError(err, 'Load version details');
    } finally {
      setLoadingVersionDetail(false);
    }
  };

  const modelConfig = selectedVersion?.modelConfig || {};

  const { modelId, ...otherModelConfig } = modelConfig;


  const currentModel = useMemo(() => {
    return models.find(model => model.id === modelConfig.modelId);
  }, [models, modelConfig]);


  useEffect(() => {
    loadPromptData();
  }, [pagination.current, pagination.pageSize])

  if (loading) {
    return (
      <div className="p-8 fade-in">
        <div className="flex items-center justify-center h-64">
          <Spin
            indicator={<LoadingOutlined style={{ fontSize: 48 }} spin />}
            size="large"
          >
            <div className="text-center pt-4">
              <p className="text-gray-600 mt-4">Loading version data...</p>
            </div>
          </Spin>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-8 fade-in">
        <Result
          status="error"
          title="Failed to load version data"
          subTitle={error}
          extra={[
            <Button type="primary" key="retry" onClick={() => loadPromptData()}>
              Retry
            </Button>,
            <Button key="back" onClick={() => navigate('/prompts')}>
              Return to List
            </Button>,
          ]}
        />
      </div>
    );
  }

  if (!currentPrompt) {
    return (
      <div className="p-8 fade-in">
        <Result
          status="404"
          title="Prompt not found"
          subTitle="The specified Prompt was not found. It may have been deleted or does not exist."
          extra={
            <Button type="primary" onClick={() => navigate('/prompts')}>
              Return to List
            </Button>
          }
        />
      </div>
    );
  }

  const handleVersionClick = (version) => {
    // Load and display version details when clicking version row
    loadVersionDetail(version);
  };


  // Load two version details and start comparison
  const loadVersionsAndCompare = async (version1, version2) => {
    setLoadingVersionDetail(true);
    try {
      // Parallel load two version details
      const [detail1Response, detail2Response] = await Promise.all([
        API.getPromptVersion({ promptKey, version: version1.version }),
        API.getPromptVersion({ promptKey, version: version2.version })
      ]);

      if (detail1Response.code !== 200 || detail2Response.code !== 200) {
        throw new Error('Failed to get version details');
      }

      // Process version 1 data
      const detail1 = detail1Response.data;
      const variables1 = detail1.variables ? JSON.parse(detail1.variables) : {};
      const modelConfig1 = detail1.modelConfig ? JSON.parse(detail1.modelConfig) : null;

      const enhancedVersion1 = {
        ...version1,
        template: detail1.template,
        variables: variables1,
        modelConfig: modelConfig1,
        parameters: Object.keys(variables1),
        content: detail1.template,
        description: detail1.versionDescription,
        versionType: version1.status
      };

      // Process version 2 data
      const detail2 = detail2Response.data;
      const variables2 = detail2.variables ? JSON.parse(detail2.variables) : {};
      const modelConfig2 = detail2.modelConfig ? JSON.parse(detail2.modelConfig) : null;

      const enhancedVersion2 = {
        ...version2,
        template: detail2.template,
        variables: variables2,
        modelConfig: modelConfig2,
        parameters: Object.keys(variables2),
        content: detail2.template,
        description: detail2.versionDescription,
        versionType: version2.status
      };

      // Update cache
      const cache1Key = `${promptKey}-${version1.version}`;
      const cache2Key = `${promptKey}-${version2.version}`;
      setVersionDetailsCache(prev => ({
        ...prev,
        [cache1Key]: {
          template: detail1.template,
          variables: variables1,
          modelConfig: modelConfig1,
          parameters: Object.keys(variables1),
          content: detail1.template,
          description: detail1.versionDescription,
          versionType: version1.status
        },
        [cache2Key]: {
          template: detail2.template,
          variables: variables2,
          modelConfig: modelConfig2,
          parameters: Object.keys(variables2),
          content: detail2.template,
          description: detail2.versionDescription,
          versionType: version2.status
        }
      }));

      // Set enhanced versions and start comparison
      setSelectedVersions([enhancedVersion1, enhancedVersion2]);
      setShowCompare(true);
    } catch (err) {
      console.error('Failed to load version comparison data:', err);
      handleApiError(err, 'Load version comparison data');
    } finally {
      setLoadingVersionDetail(false);
    }
  };

  const handleCheckboxChange = (version, event) => {
    event.stopPropagation(); // Prevent event bubbling to row click

    if (selectedVersions.some(v => v.version === version.version)) {
      // Deselect
      setSelectedVersions(prev => prev.filter(v => v.version !== version.version));
    } else {
      if (selectedVersions.length < 2) {
        // Add selection
        const newSelection = [...selectedVersions, version];
        setSelectedVersions(newSelection);

        // If two versions are selected, load details and start comparison
        if (newSelection.length === 2) {
          loadVersionsAndCompare(newSelection[0], newSelection[1]);
        }
      } else {
        // Two versions already selected, replace first one and start comparison
        const newSelection = [selectedVersions[1], version];
        setSelectedVersions(newSelection);
        loadVersionsAndCompare(newSelection[0], newSelection[1]);
      }
    }
  };

  const handleVersionDetailCompare = async () => {
    if (!selectedVersion) return;

    // Find previous version (sorted by creation time)
    const sortedVersions = [...versions].sort((a, b) => a.createTime - b.createTime);

    const currentIndex = sortedVersions.findIndex(v => v.version === selectedVersion.version);
    if (currentIndex > 0) {
      const previousVersion = sortedVersions[currentIndex - 1];

      // Load detailed information for two versions for comparison
      try {
        setLoadingVersionDetail(true);

        const [prevDetailResponse, currDetailResponse] = await Promise.all([
          API.getPromptVersion({ promptKey, version: previousVersion.version }),
          selectedVersion.template ? Promise.resolve({ code: 200, data: selectedVersion }) :
            API.getPromptVersion({ promptKey, version: selectedVersion.version })
        ]);

        if (prevDetailResponse.code !== 200) {
          throw new Error('Failed to get previous version details');
        }

        // Process previous version data
        const prevDetail = prevDetailResponse.data;
        const prevVariables = prevDetail.variables ? JSON.parse(prevDetail.variables) : {};
        const prevModelConfig = prevDetail.modelConfig ? JSON.parse(prevDetail.modelConfig) : null;

        const enhancedPrevVersion = {
          ...previousVersion,
          template: prevDetail.template,
          variables: prevVariables,
          modelConfig: prevModelConfig,
          parameters: Object.keys(prevVariables),
          content: prevDetail.template,
          description: prevDetail.versionDescription,
          versionType: previousVersion.status
        };

        setSelectedVersions([enhancedPrevVersion, selectedVersion]);
        setShowVersionDetail(false);
        setShowCompare(true);
      } catch (err) {
        console.error('Failed to load version comparison data:', err);
        handleApiError(err, 'Load version comparison data');
      } finally {
        setLoadingVersionDetail(false);
      }
    } else {
      notifyError({ message: 'No previous version available for comparison' });
    }
  };

  const handleRestoreVersion = () => {
    if (!selectedVersion) return;

    console.log('=== Starting version restore ===');
    console.log('Selected version:', selectedVersion);
    console.log('Target window ID:', targetWindowId);
    console.log('Current Prompt Key:', promptKey);

    // Build restore URL using promptKey and version
    let restoreUrl = `/prompt-detail?promptKey=${promptKey}&restoreVersionId=${selectedVersion.version}`;
    if (targetWindowId) {
      restoreUrl += `&targetWindowId=${targetWindowId}`;
    }

    console.log('=== Redirect URL ===', restoreUrl);

    // Navigate to Prompt detail page and pass version to restore and target window ID
    navigate(restoreUrl);

    // Close version detail modal
    setShowVersionDetail(false);
  };



  const clearSelection = () => {
    setSelectedVersions([]);
  };


  return (
    <div style={{ padding: 32 }}>
      {/* Page header */}
      <div style={{ marginBottom: 32 }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 8 }}>
              <Button
                type="text"
                icon={<ArrowLeftOutlined />}
                onClick={() => navigate('/prompts')}
                size="large"
              />
              <Title level={1} style={{ margin: 0 }}>Version History</Title>
            </div>
            <Paragraph style={{ margin: 0, color: '#595959' }}>
              <Text strong>{currentPrompt.promptKey}</Text> version release records
            </Paragraph>
          </div>
        </div>
      </div>

      {/* Operation tips */}
      <Alert
        message={
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <Text>Check two versions to compare, or click the "Details" button in the actions column to view version details</Text>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              {selectedVersions.length > 0 && (
                <Button
                  type="link"
                  size="small"
                  onClick={clearSelection}
                >
                  Clear selection ({selectedVersions.length})
                </Button>
              )}
            </div>
          </div>
        }
        type="info"
        icon={<InfoCircleOutlined />}
        showIcon
        style={{ marginBottom: 24 }}
      />

      {/* Version history list */}
      <Card>
        <Table
          dataSource={versions.map((version, index) => ({
            ...version,
            key: version.version,
            actualIndex: index,
            cachedDetail: versionDetailsCache[`${promptKey}-${version.version}`]
          }))}
          columns={[
            {
              title: 'Select',
              key: 'select',
              width: 60,
              align: 'center',
              render: (_, record) => (
                <Checkbox
                  checked={selectedVersions.some(v => v.version === record.version)}
                  disabled={selectedVersions.length >= 2 && !selectedVersions.some(v => v.version === record.version)}
                  onChange={(e) => {
                    e.stopPropagation();
                    handleCheckboxChange(record, { stopPropagation: () => { } });
                  }}
                />
              )
            },
            {
              title: 'Version',
              key: 'version',
              width: 150,
              render: (_, record) => (
                <Space direction="vertical" size={4}>
                  <Tag color="blue">{record.version}</Tag>
                  {record.actualIndex === 0 && (
                    <Tag color="success" size="small">Current Version</Tag>
                  )}
                  {selectedVersions.some(v => v.version === record.version) && (
                    <Tag color="blue" size="small" icon={<CheckCircleOutlined />}>Selected</Tag>
                  )}
                </Space>
              )
            },
            {
              title: 'Release Time',
              key: 'createTime',
              width: 150,
              render: (_, record) => formatTime(record.createTime)
            },
            {
              title: 'Version Description',
              key: 'description',
              width: 200,
              ellipsis: {
                showTitle: false
              },
              render: (_, record) => (
                <Tooltip title={record.versionDescription || record.cachedDetail?.description || 'No description'}>
                  <Text ellipsis>
                    {record.versionDescription || record.cachedDetail?.description || 'No description'}
                  </Text>
                </Tooltip>
              )
            },
            // {
            //   title: 'Model Configuration',
            //   key: 'modelConfig',
            //   width: 180,
            //   render: (_, record) => {
            //     if (record.cachedDetail?.modelConfig) {
            //       return (
            //         <div>
            //           <div style={{ fontSize: '12px' }}>
            //             <Text strong>Model:</Text>
            //             <Text style={{ marginLeft: 4 }}>{record.cachedDetail.modelConfig.modelId}</Text>
            //           </div>
            //           <div style={{ fontSize: '12px', color: '#8c8c8c' }}>
            //             <Text>Tokens: {record.cachedDetail.modelConfig.maxTokens}</Text>
            //             <Text style={{ marginLeft: 8 }}>Temp: {record.cachedDetail.modelConfig.temperature}</Text>
            //           </div>
            //         </div>
            //       );
            //     }
            //     return <Text type="secondary" style={{ fontSize: '12px' }}>Click to view configuration</Text>;
            //   }
            // },
            // {
            //   title: 'Parameter Configuration',
            //   key: 'parameters',
            //   width: 180,
            //   render: (_, record) => {
            //     if (record.cachedDetail?.parameters && record.cachedDetail.parameters.length > 0) {
            //       return (
            //         <Space size={[4, 4]} wrap>
            //           {record.cachedDetail.parameters.slice(0, 3).map((param, index) => (
            //             <Tag key={index} color="blue" size="small">{param}</Tag>
            //           ))}
            //           {record.cachedDetail.parameters.length > 3 && (
            //             <Text type="secondary" style={{ fontSize: '12px' }}>+{record.cachedDetail.parameters.length - 3}</Text>
            //           )}
            //         </Space>
            //       );
            //     }
            //     return <Text type="secondary" style={{ fontSize: '12px' }}>Click to view parameters</Text>;
            //   }
            // },
            {
              title: 'Status',
              key: 'status',
              width: 120,
              render: (_, record) => {
                if (record.status === 'release') {
                  return (
                    <Tag color="success" icon={<CheckCircleOutlined />}>
                      Release Version
                    </Tag>
                  );
                }
                return (
                  <Tag color="warning" icon={<ExperimentOutlined />}>
                    Pre-release Version
                  </Tag>
                );
              }
            },
            // {
            //   title: 'Content Preview',
            //   key: 'content',
            //   width: 250,
            //   ellipsis: {
            //     showTitle: false
            //   },
            //   render: (_, record) => {
            //     const content = record.cachedDetail?.content;
            //     if (content) {
            //       const previewText = `${content.substring(0, 80)}${content.length > 80 ? '...' : ''}`;
            //       return (
            //         <Tooltip title={content}>
            //           <div style={{
            //             fontFamily: 'monospace',
            //             fontSize: '12px',
            //             backgroundColor: '#fafafa',
            //             padding: 8,
            //             borderRadius: 4,
            //             border: '1px solid #f0f0f0'
            //           }}>
            //             {previewText}
            //           </div>
            //         </Tooltip>
            //       );
            //     }
            //     return <Text type="secondary" style={{ fontSize: '12px' }}>Click to view content</Text>;
            //   }
            // },
            {
              title: 'Actions',
              key: 'actions',
              width: 100,
              align: 'center',
              render: (_, record) => (
                <Button
                  type="text"
                  size="small"
                  icon={<EyeOutlined />}
                  onClick={(e) => {
                    e.stopPropagation();
                    handleVersionClick(record);
                  }}
                  title="View Details"
                >
                  Details
                </Button>
              )
            }
          ]}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="No version records"
              >
                <Button type="primary" onClick={() => navigate(`/prompt-detail?promptKey=${promptKey}`)}>
                  Start creating version
                </Button>
              </Empty>
            )
          }}
          pagination={{
            ...pagination,
            onChange: onPaginationChange,
            onShowSizeChange: onShowSizeChange
          }}
          scroll={{ x: 1300 }}
        />
      </Card>

      {/* Pagination and bottom action area */}
      <Space direction="vertical" size={16} style={{ width: '100%', marginTop: 24 }}>
        {/* Bottom action area */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Text type="secondary">
            Total {pagination.total} versions, sorted by release time in descending order
          </Text>

          <Space>
            {selectedVersions.length === 2 && (
              <Button
                type="primary"
                icon={<BranchesOutlined />}
                loading={loadingVersionDetail}
                onClick={() => loadVersionsAndCompare(selectedVersions[0], selectedVersions[1])}
              >
                {loadingVersionDetail ? 'Loading...' : 'Compare selected versions'}
              </Button>
            )}
            <div>
              {selectedVersions.length === 0 && (
                <Text type="secondary">Please select two versions to compare</Text>
              )}
              {selectedVersions.length === 1 && (
                <Text type="secondary">1 version selected, please select 1 more</Text>
              )}
              {selectedVersions.length === 2 && !loadingVersionDetail && (
                <Text type="secondary">Selected versions {selectedVersions[0].version} and {selectedVersions[1].version}</Text>
              )}
              {loadingVersionDetail && (
                <Text type="secondary">
                  <LoadingOutlined style={{ marginRight: 8 }} />
                  Loading version details for comparison...
                </Text>
              )}
            </div>
          </Space>
        </div>
      </Space>

      {/* Version detail modal */}
      <Modal
        title={`Version Details - ${selectedVersion?.version}`}
        open={showVersionDetail && selectedVersion}
        onCancel={() => setShowVersionDetail(false)}
        width={1000}
        style={{
          top: 20,
          maxHeight: 'calc(100vh - 40px)'
        }}
        bodyStyle={{
          maxHeight: 'calc(100vh - 200px)',
          overflowY: 'auto'
        }}
        footer={[
          <Space key="actions">
            <Button
              type="primary"
              icon={<UndoOutlined />}
              onClick={handleRestoreVersion}
            >
              Restore to editor
            </Button>
            <Button
              icon={<BranchesOutlined />}
              onClick={handleVersionDetailCompare}
            >
              Compare with previous version
            </Button>
            <Button onClick={() => setShowVersionDetail(false)}>
              Close
            </Button>
          </Space>
        ]}
      >
        {loadingVersionDetail ? (
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '48px 0' }}>
            <Spin
              indicator={<LoadingOutlined style={{ fontSize: 32 }} spin />}
              size="large"
            >
              <div style={{ textAlign: 'center', paddingTop: 16 }}>
                <Text type="secondary">Loading version details...</Text>
              </div>
            </Spin>
          </div>
        ) : (
          <Space direction="vertical" size={24} style={{ width: '100%' }}>
            {/* Version basic information */}
            <Row gutter={24}>
              <Col span={12}>
                <div>
                  <Text strong style={{ display: 'block', marginBottom: 8, color: '#262626' }}>Version</Text>
                  <div style={{ padding: '8px 16px', backgroundColor: '#fafafa', borderRadius: 6 }}>
                    <Tag color="blue">{selectedVersion?.version}</Tag>
                  </div>
                </div>
              </Col>
              <Col span={12}>
                <div>
                  <Text strong style={{ display: 'block', marginBottom: 8, color: '#262626' }}>Created</Text>
                  <div style={{ padding: '8px 16px', backgroundColor: '#fafafa', borderRadius: 6, color: '#262626' }}>
                    {formatTime(selectedVersion?.createTime)}
                  </div>
                </div>
              </Col>
            </Row>

            {/* Version description */}
            <div>
              <Text strong style={{ display: 'block', marginBottom: 8, color: '#262626' }}>Version Description</Text>
              <div style={{ padding: '12px 16px', backgroundColor: '#fafafa', borderRadius: 6, color: '#262626' }}>
                {selectedVersion?.description || selectedVersion?.versionDescription || 'No description'}
              </div>
            </div>

            {/* Model configuration */}
            {selectedVersion?.modelConfig && (
              <div>
                <Text strong style={{ display: 'block', marginBottom: 8, color: '#262626' }}>Model Configuration</Text>
                <div style={{ padding: '12px 16px', backgroundColor: '#fafafa', borderRadius: 6 }}>
                  <Row gutter={16}>
                    <Col span={12}>
                      <Text strong style={{ color: '#595959' }}>Model:</Text>
                      <Tag color="geekblue">{currentModel?.name}</Tag>
                    </Col>
                    {
                      Object.keys(otherModelConfig).map((key) => (
                        <Col span={12}>
                          <Text strong style={{ color: '#595959' }}>{key}:</Text>
                          <Text style={{ marginLeft: 8, color: '#262626' }}>{otherModelConfig[key]}</Text>
                        </Col>
                      ))
                    }
                  </Row>
                </div>
              </div>
            )}

            {/* Parameter list */}
            {selectedVersion?.parameters && selectedVersion.parameters.length > 0 && (
              <div>
                <Text strong style={{ display: 'block', marginBottom: 8, color: '#262626' }}>Parameter List</Text>
                <div style={{ padding: '12px 16px', backgroundColor: '#fafafa', borderRadius: 6 }}>
                  <Space size={[8, 8]} wrap>
                    {selectedVersion.parameters.map((param, index) => (
                      <Tag key={index} color="blue">{param}</Tag>
                    ))}
                  </Space>
                </div>
              </div>
            )}

            {/* Version status */}
            <div>
              <Text strong style={{ display: 'block', marginBottom: 8, color: '#262626' }}>Version Status</Text>
              <div style={{ padding: '8px 16px', backgroundColor: '#fafafa', borderRadius: 6 }}>
                {(selectedVersion?.versionType || selectedVersion?.status) === 'release' ? (
                  <Tag color="success" icon={<CheckCircleOutlined />}>
                    Release Version
                  </Tag>
                ) : (
                  <Tag color="warning" icon={<ExperimentOutlined />}>
                    Pre-release Version
                  </Tag>
                )}
              </div>
            </div>

            {/* Version content */}
            <div>
              <Text strong style={{ display: 'block', marginBottom: 8, color: '#262626' }}>Version Content</Text>
              <div style={{
                padding: '12px 16px',
                backgroundColor: '#fafafa',
                borderRadius: 6,
                color: '#262626',
                whiteSpace: 'pre-wrap',
                fontFamily: 'monospace',
                fontSize: '13px',
                maxHeight: 256,
                overflowY: 'auto'
              }}>
                {selectedVersion?.content || selectedVersion?.template || 'No content'}
              </div>
            </div>
          </Space>
        )}
      </Modal>

      {/* Version comparison modal */}
      {showCompare && selectedVersions.length === 2 && (
        <VersionCompareModal
          prompt={currentPrompt}
          version1={selectedVersions[0]}
          version2={selectedVersions[1]}
          onClose={() => {
            setShowCompare(false);
            setSelectedVersions([]);
          }}
        />
      )}
    </div>
  );
};

export default VersionHistoryPage;
