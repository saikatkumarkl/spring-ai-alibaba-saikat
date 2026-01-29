import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Table, Input, Select, Button, Space, Tag, Checkbox, Pagination, Spin, message, Tooltip, Modal, Card, Drawer, Typography } from 'antd';
import { SearchOutlined, PlusOutlined, SyncOutlined, EyeOutlined, StopOutlined, ReloadOutlined, PlayCircleOutlined, DeleteOutlined, BarChartOutlined } from '@ant-design/icons';
import { handleApiError, notifySuccess } from '../../../utils/notification';
import API from '../../../services';
import ExperimentCreate from './experimentCreate';
import usePagination from '../../../hooks/usePagination';
import { getLegacyPath } from '../../../utils/path';
import './index.css';

const { Option } = Select;
const { Title } = Typography;

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



interface ExperimentRecord {
  id: number;
  name: string;
  description: string;
  datasetId: number;
  datasetVersion: string;
  evaluationObjectConfig: string;
  evaluatorVersionIds: number[];
  evaluatorConfig: string;
  status: 'RUNNING' | 'COMPLETED' | 'FAILED' | 'WAITING' | 'STOPPED';
  progress: number;
  completeTime: string;
  createTime: string;
  updateTime: string;
  result: string;
}

const Experiment = () => {
    const navigate = useNavigate();
    const [loading, setLoading] = useState(false);
    const [dataSource, setDataSource] = useState<ExperimentRecord[]>([]);
    const [searchText, setSearchText] = useState(''); // Text in input box
    const [queryText, setQueryText] = useState(''); // Actual text used for query
    const [statusFilter, setStatusFilter] = useState<string>('');
    const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
    const [showCreateDrawer, setShowCreateDrawer] = useState(false); // Drawer state
    const { pagination, setPagination, onPaginationChange, onShowSizeChange } = usePagination();

    // Get experiments list
    const fetchExperiments = useCallback(async () => {
        try {
            setLoading(true);
            const params = {
                pageNumber: pagination.current,
                pageSize: pagination.pageSize,
                name: queryText || undefined, // Use query text instead of input text
                status: statusFilter || undefined,
            };

            const response = await API.getExperiments(params);

            if (response.code === 200) {
                // Prefer pageItems, fallback to records if not available
                const responseData = response.data as any;
                const dataItems = responseData.pageItems || responseData.records || [];

                // Use real data
                const experiments: ExperimentRecord[] = dataItems.map((item: any) => ({
                    id: item.id,
                    name: item.name,
                    description: item.description,
                    datasetId: item.datasetId,
                    datasetVersion: item.datasetVersion || '',
                    evaluationObjectConfig: item.evaluationObjectConfig || '',
                    evaluatorVersionIds: item.evaluatorVersionIds || [],
                    evaluatorConfig: item.evaluatorConfig || '',
                    status: item.status,
                    progress: item.progress || 0,
                    completeTime: item.completeTime || '',
                    createTime: item.createTime,
                    updateTime: item.updateTime || item.createTime,
                    result: item.result || ''
                }));

                setDataSource(experiments);
                setPagination(prev => ({
                    ...prev,
                    total: responseData.totalCount || experiments.length,
                    current: responseData.pageNumber || pagination.current
                }));
            } else {
                throw new Error(response.message || 'Loading failed');
            }
        } catch (error) {
            handleApiError(error, 'Failed to get experiment list');
            // Set to empty list on error
            setDataSource([]);
            setPagination(prev => ({
                ...prev,
                total: 0
            }));
        } finally {
            setLoading(false);
        }
    }, [pagination.current, pagination.pageSize, queryText, statusFilter]); // Depend on query text instead of input text

    useEffect(() => {
        fetchExperiments();
    }, [fetchExperiments]);

    // Handle search input change (only updates input box state, doesn't trigger search)
    const handleSearchChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        setSearchText(e.target.value);
    };

    // Handle search (only triggered when clicking search button or pressing enter)
    const handleSearch = (value: string) => {
        setSearchText(value);
        setQueryText(value); // Update actual query parameter
        setPagination(prev => ({ ...prev, current: 1 }));
    };

    // Handle status filter
    const handleStatusFilter = (value: string) => {
        setStatusFilter(value);
        setPagination(prev => ({ ...prev, current: 1 }));
    };

    // Handle pagination
    const handleTableChange = (page: number, pageSize: number) => {
        onPaginationChange(page, pageSize);
    };

    // Handle selection
    const handleSelectChange = (selectedKeys: React.Key[]) => {
        setSelectedRowKeys(selectedKeys);
    };

    // Refresh data
    const handleRefresh = () => {
        fetchExperiments();
    };

    // Create new experiment - open drawer
    const handleCreateExperiment = () => {
        setShowCreateDrawer(true);
    };

    // Close drawer
    const handleCloseCreateDrawer = () => {
        setShowCreateDrawer(false);
    };

    // View experiment details
    const handleViewExperiment = (record: ExperimentRecord) => {
        // Navigate to experiment details page with id parameter and evaluatorConfig data
        navigate(getLegacyPath(`/evaluation/experiment/detail/${record.id}`), {
            state: { evaluatorConfig: record.evaluatorConfig }
        });
    };

    // Stop experiment
    const handleStopExperiment = async (record: ExperimentRecord) => {
        Modal.confirm({
            title: 'Confirm Stop',
            content: `Are you sure you want to stop experiment "${record.name}"? The experiment status will become failed after stopping.`,
            okText: 'Confirm Stop',
            okType: 'danger',
            cancelText: 'Cancel',
            onOk: async () => {
                try {
                    await API.stopExperiment({ experimentId: record.id });
                    notifySuccess({ message: 'Experiment stopped' });
                    fetchExperiments();
                } catch (error) {
                    handleApiError(error, 'Failed to stop experiment');
                }
            }
        });
    };

    // Rerun experiment
    const handleRerunExperiment = async (record: ExperimentRecord) => {
        try {
            // Should call rerun experiment API here
            // await API.rerunExperiment({ id: record.id });
            message.info(`Rerun experiment: ${record.name}`);
            // fetchExperiments();
        } catch (error) {
            handleApiError(error, 'Failed to rerun experiment');
        }
    };

    // View experiment results
    const handleViewResult = (record: ExperimentRecord) => {
        // Navigate to experiment details page, show evaluation results tab by default, carry id and evaluatorConfig data and activeTab
        navigate(`/evaluation-experiment/detail/${record.id}`, {
            state: { evaluatorConfig: record.evaluatorConfig, activeTab: 'results' }
        });
    };

    // Delete experiment
    const handleDeleteExperiment = async (record: ExperimentRecord) => {
        Modal.confirm({
            title: 'Confirm Delete',
            content: `Are you sure you want to delete experiment "${record.name}"? This action cannot be undone.`,
            okText: 'Confirm Delete',
            okType: 'danger',
            cancelText: 'Cancel',
            onOk: async () => {
                try {
                    await API.deleteExperiment({ experimentId: record.id });
                    notifySuccess({ message: 'Experiment deleted successfully' });
                    fetchExperiments();
                } catch (error) {
                    handleApiError(error, 'Failed to delete experiment');
                }
            }
        });
    };

    // Render status tag
    const renderStatus = (status: string, progress?: number) => {
        switch (status) {
            case 'WAITING':
                return <Tag color="default">Waiting</Tag>;
            case 'RUNNING':
                return (
                    <div>
                        <Tag color="blue">Running</Tag>
                        <div style={{fontSize: '12px', color: 'rgb(102, 102, 102)', marginTop: '4px'}}>
                            {progress !== undefined && <span>Progress: {progress}%</span>}
                        </div>
                    </div>
                );
            case 'COMPLETED':
                return <Tag color="green">Completed</Tag>;
            case 'FAILED':
                return <Tag color="red">Failed</Tag>;
            case 'STOPPED':
                return <Tag color="orange">Stopped</Tag>;
            default:
                return <Tag>{status}</Tag>;
        }
    };

    const columns = [
        {
            title: 'Experiment Name',
            dataIndex: 'name',
            key: 'name',
            render: (text: string, record: ExperimentRecord) => (
                <div
                    className="font-medium text-blue-600 cursor-pointer hover:text-blue-800 hover:underline"
                    onClick={() => handleViewExperiment(record)}
                >
                    {text}
                </div>
            )
        },
        {
            title: 'Description',
            dataIndex: 'description',
            ellipsis: true,
            render: (text: string) => (
                <Tooltip title={text} placement="topLeft">
                    <span>{text}</span>
                </Tooltip>
            )
        },
        {
            title: 'Dataset',
            dataIndex: 'datasetVersion',
            key: 'datasetVersion',
            render: (text: string, record: ExperimentRecord) => (
                <div>
                    <div className="font-medium">{record.datasetId}</div>
                    <div className="text-sm text-gray-500">{text}</div>
                </div>
            )
        },
        {
            title: 'Evaluator',
            dataIndex: 'evaluatorConfig',
            key: 'evaluatorConfig',
            render: (evaluatorConfig: string, record: ExperimentRecord) => {
                // Parse evaluator info from evaluatorConfig field
                let evaluatorNames: string[] = [];
                try {
                    const evaluatorConfigs = JSON.parse(evaluatorConfig || '[]');
                    evaluatorNames = evaluatorConfigs.map((config: any) => config.evaluatorName || `ID: ${config.evaluatorId}`);
                } catch (e) {
                    // If parsing fails, fall back to using evaluatorVersionIds
                    if (record.evaluatorVersionIds && record.evaluatorVersionIds.length > 0) {
                        evaluatorNames = record.evaluatorVersionIds.map(id => `ID: ${id}`);
                    }
                }

                if (evaluatorNames.length === 0) {
                    return <span className="text-gray-400">None</span>;
                }

                // Join all evaluator names with commas
                const allEvaluatorNames = evaluatorNames.join('，');

                return (
                    <Tooltip title={`All evaluators:\n${allEvaluatorNames}`} placement="topLeft">
                        <div className="text-sm text-gray-600 mt-1 truncate" style={{ maxWidth: '200px' }}>
                            {allEvaluatorNames}
                        </div>
                    </Tooltip>
                );
            }
        },
        {
            title: 'Status',
            dataIndex: 'status',
            key: 'status',
            render: (status: string, record: ExperimentRecord) => renderStatus(status, record.progress)
        },
        // {
        //     title: 'Created By',
        //     dataIndex: 'creator',
        //     key: 'creator'
        // },
        {
            title: 'Created At',
            dataIndex: 'createTime',
            key: 'createTime',
            render: (text: string) => formatDateTime(text)
        },
        {
            title: 'Updated At',
            dataIndex: 'updateTime',
            key: 'updateTime',
            render: (text: string) => formatDateTime(text)
        },
        {
            title: 'Actions',
            key: 'action',
            width: 160,
            fixed: 'right' as const,
            render: (_: any, record: ExperimentRecord) => {
                // Render second action button (differs based on status)
                const renderSecondAction = () => {
                    switch (record.status) {
                        case 'RUNNING':
                            return (
                                <Tooltip title="Stop">
                                    <Button
                                        type="link"
                                        icon={<StopOutlined />}
                                        onClick={() => handleStopExperiment(record)}
                                        danger
                                    />
                                </Tooltip>
                            );
                        case 'COMPLETED':
                            return (
                                <Tooltip title="View Results">
                                    <Button
                                        type="link"
                                        icon={<BarChartOutlined />}
                                        onClick={() => handleViewResult(record)}
                                    />
                                </Tooltip>
                            );
                        case 'FAILED':
                            return (
                                <Tooltip title="Rerun">
                                    <Button
                                        type="link"
                                        icon={<PlayCircleOutlined />}
                                        onClick={() => handleRerunExperiment(record)}
                                    />
                                </Tooltip>
                            );
                        case 'WAITING':
                            // Not sure about waiting state yet, return null
                            return null;
                        case 'STOPPED':
                            return (
                                <Tooltip title="Rerun">
                                    <Button
                                        type="link"
                                        icon={<PlayCircleOutlined />}
                                        onClick={() => handleRerunExperiment(record)}
                                    />
                                </Tooltip>
                            );
                        default:
                            return null;
                    }
                };

                return (
                    <Space size="middle">
                        <Tooltip title="View Details">
                            <Button
                                type="link"
                                icon={<EyeOutlined />}
                                onClick={() => handleViewExperiment(record)}
                            />
                        </Tooltip>
                        {renderSecondAction()}
                        <Tooltip title="Delete">
                            <Button
                                type="link"
                                icon={<DeleteOutlined />}
                                onClick={() => handleDeleteExperiment(record)}
                                danger
                            />
                        </Tooltip>
                    </Space>
                );
            }
        }
    ];

    const rowSelection = {
        selectedRowKeys,
        onChange: handleSelectChange
    };

    return (
        <div className="experiment-page p-8 fade-in">
            {/* Page Title */}
            <div className="mb-8">
                <Title level={2} style={{ marginBottom: 8 }}>Experiment Management</Title>
            </div>

            {/* Search and Filter Area */}
            <Card className='mb-4'>
                <div className="flex gap-4 justify-between" style={{flexWrap: 'wrap'}}>
                    <Input.Search
                        placeholder="Search name"
                        allowClear
                        style={{ width: 280 }}
                        value={searchText}
                        onChange={handleSearchChange}
                        onSearch={handleSearch}
                    />
                    <Select
                        placeholder="Status: Select"
                        allowClear
                        style={{ width: 200 }}
                        value={statusFilter}
                        onChange={handleStatusFilter}
                    >
                        <Option value="RUNNING">Running</Option>
                        <Option value="COMPLETED">Completed</Option>
                        <Option value="FAILED">Failed</Option>
                        <Option value="WAITING">Waiting</Option>
                        <Option value="STOPPED">Stopped</Option>
                    </Select>
                    <div style={{flex: 1}}></div>
                    <Button
                        icon={<SyncOutlined />}
                        onClick={handleRefresh}
                    >
                        Refresh
                    </Button>
                    <Button
                        type="primary"
                        icon={<PlusOutlined />}
                        onClick={handleCreateExperiment}
                    >
                        Create Experiment
                    </Button>
                </div>
            </Card>

            {/* Data Table */}
            <Card>
                <div className="experiment-table bg-white rounded-lg">
                    <Table
                        rowSelection={rowSelection}
                        columns={columns}
                        dataSource={dataSource}
                        loading={loading}
                        rowKey="id"
                        className="border-0"
                        pagination={{
                            ...pagination,
                            onChange: onPaginationChange,
                            onShowSizeChange: onShowSizeChange
                        }}
                        scroll={{ x: 800 }}
                    />

                </div>
            </Card>

            {/* Create Experiment Drawer */}
            <Drawer
                title="Create Experiment"
                placement="right"
                width="90%"
                open={showCreateDrawer}
                onClose={handleCloseCreateDrawer}
                destroyOnClose={true}
                style={{ zIndex: 1000 }}
                styles={{
                    body: { padding: 0, height: '100%', display: 'flex', flexDirection: 'column' }
                }}
            >
                <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
                    <ExperimentCreate
                      hideTitle={true} // Hide title
                      onCancel={handleCloseCreateDrawer}
                      onSuccess={() => {
                        handleCloseCreateDrawer();
                        fetchExperiments(); // Reload data
                      }}
                    />
                </div>
            </Drawer>
        </div>
    );
};

export default Experiment;
