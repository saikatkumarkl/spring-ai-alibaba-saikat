import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Table, Input, Select, Button, Space, Tag, Checkbox, Pagination, Spin, message, Tooltip, Modal, Card, Drawer, Typography } from 'antd';
import { SearchOutlined, PlusOutlined, EyeOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { handleApiError, notifySuccess } from '../../../utils/notification';
import API from '../../../services';
import GatherCreate from './gatherCreate';
import { getLegacyPath } from '../../../utils/path';
import './index.css';
import usePagination from '../../../hooks/usePagination';

const { Title } = Typography;

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

interface DatasetRecord {
  id: number;
  name: string;
  description: string;
  dataCount?: number; // Data count (optional)
  version?: string; // Version info (optional)
  columnsConfig: string;
  createTime: string;
  updateTime: string;
}



const EvaluationGather = () => {
    const navigate = useNavigate();
    const [loading, setLoading] = useState(false);
    const [dataSource, setDataSource] = useState<DatasetRecord[]>([]);
    const [searchText, setSearchText] = useState(''); // Text in input field
    const [queryText, setQueryText] = useState(''); // Actual text used for query
    const [showCreateDrawer, setShowCreateDrawer] = useState(false); // Drawer state
    const { pagination, setPagination, onPaginationChange, onShowSizeChange } = usePagination();

    // Fetch dataset list
    const fetchDatasets = useCallback(async () => {
        try {
            setLoading(true);
            const params = {
                pageNumber: pagination.current,
                pageSize: pagination.pageSize,
                datasetName: queryText || undefined, // Use query text instead of input text
            };

            const response = await API.getDatasets(params);

            if (response.code === 200) {
                const responseData = response.data as any;
                // Prefer pageItems, fallback to records if not available
                const dataItems = responseData.pageItems || responseData.records || [];

                // Use real data, map according to new API structure
                const datasets: DatasetRecord[] = dataItems.map((item: any, index: number) => ({
                    id: item.id,
                    name: item.name,
                    description: item.description || '',
                    // API doesn't return dataCount and version yet, use default values
                    // Later can fetch this info via separate interface
                    dataCount: item.dataCount || undefined,
                    version: item.version || 'v1.0.0',
                    columnsConfig: item.columnsConfig || '',
                    createTime: item.createTime,
                    updateTime: item.updateTime || item.createTime
                }));

                setDataSource(datasets);
                setPagination(prev => ({
                    ...prev,
                    total: responseData.totalCount || datasets.length,
                    current: responseData.pageNumber || pagination.current
                }));
            } else {
                throw new Error(response.message || 'Loading failed');
            }
        } catch (error) {
            handleApiError(error, 'Failed to get dataset list');
            // Set empty list on error
            setDataSource([]);
            setPagination(prev => ({
                ...prev,
                total: 0
            }));
        } finally {
            setLoading(false);
        }
    }, [pagination.current, pagination.pageSize, queryText]); // Depend on query text instead of input text

    useEffect(() => {
        fetchDatasets();
    }, [fetchDatasets]);

    // Handle pagination
    const handleTableChange = (page: number, pageSize: number) => {
        onPaginationChange(page, pageSize);
    };

    // Handle search input change (only update input state, don't trigger search)
    const handleSearchChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        setSearchText(e.target.value);
    };

    // Handle search (only triggered when search button is clicked or enter key is pressed)
    const handleSearch = (value: string) => {
        setSearchText(value);
        setQueryText(value); // Update actual query parameter
        setPagination(prev => ({ ...prev, current: 1 }));
    };

    // Create new dataset - open drawer
    const handleCreateDataset = () => {
        setShowCreateDrawer(true);
    };

    // Close drawer
    const handleCloseCreateDrawer = () => {
        setShowCreateDrawer(false);
    };

    // View dataset details
    const handleViewDataset = (record: DatasetRecord) => {
        // Navigate to dataset detail page with id parameter
        navigate(getLegacyPath(`/evaluation/gather/detail/${record.id}`));
    };

    // Delete dataset
    const handleDeleteDataset = async (record: DatasetRecord) => {
        Modal.confirm({
            title: 'Confirm Delete',
            content: `Are you sure you want to delete dataset "${record.name}"? This action cannot be undone.`,
            okText: 'Confirm Delete',
            okType: 'danger',
            cancelText: 'Cancel',
            onOk: async () => {
                try {
                    await API.deleteDataset({ datasetId: record.id });
                    notifySuccess({ message: 'Dataset deleted' });
                    fetchDatasets();
                } catch (error) {
                    handleApiError(error, 'Failed to delete dataset');
                }
            }
        });
    };

    const columns = [
        {
            title: 'Dataset Name',
            dataIndex: 'name',
            key: 'name',
            render: (text: string, record: DatasetRecord) => (
                <div
                    className="font-medium text-blue-600 cursor-pointer hover:text-blue-800 hover:underline"
                    onClick={() => handleViewDataset(record)}
                >
                    {text}
                </div>
            )
        },
        {
            title: 'Description',
            dataIndex: 'description',
            key: 'description',
            ellipsis: {
                showTitle: false,
            },
            render: (text: string) => (
                <Tooltip placement="topLeft" title={text || '-'}>
                    <span>{text || '-'}</span>
                </Tooltip>
            ),
        },
        {
            title: 'Version',
            dataIndex: 'version',
            key: 'version',
            render: (version: string) => (
                <Tag color="blue">{version || 'v1.0.0'}</Tag>
            )
        },
        // {
        //     title: 'Created By',
        //     dataIndex: 'creator',
        //     key: 'creator',
        //     width: 100
        // },
        {
            title: 'Data Count',
            dataIndex: 'dataCount',
            key: 'dataCount',
            render: (count: number) => (
                <span className="font-medium">
                    {count ? count.toLocaleString() : '-'}
                </span>
            )
        },
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
            width: 120,
            fixed: 'right' as const,
            render: (_: any, record: DatasetRecord) => (
                <Space size="middle">
                    <Tooltip title="Details">
                        <Button
                            type="link"
                            icon={<EyeOutlined />}
                            onClick={() => handleViewDataset(record)}
                        />
                    </Tooltip>
                    <Tooltip title="Delete">
                        <Button
                            type="link"
                            icon={<DeleteOutlined />}
                            onClick={() => handleDeleteDataset(record)}
                            danger
                        />
                    </Tooltip>
                </Space>
            )
        }
    ];



    return (
        <div className="evaluation-gather-page p-8 fade-in">
            {/* Page Title */}
            <div className="mb-8">
                <Title level={2} style={{ marginBottom: 8 }}>Dataset Management</Title>
            </div>

            {/* Search Area */}
            <Card className='mb-4'>
                <div className="flex gap-4 justify-between" style={{flexWrap: 'wrap'}}>
                    <Input.Search
                        placeholder="Search name"
                        allowClear
                        style={{ width: 280 }}
                        className='mr-4'
                        value={searchText}
                        onChange={handleSearchChange}
                        onSearch={handleSearch}
                    />
                    {/* <Input
                        placeholder="Search creator"
                        allowClear
                        style={{ width: 280 }}
                        value={searchCreator}
                        onChange={(e) => {
                            setSearchCreator(e.target.value);
                            // Real-time search, trigger search when input changes
                            if (e.target.value !== searchCreator) {
                                setPagination(prev => ({ ...prev, current: 1 }));
                            }
                        }}
                    /> */}
                    <Button
                        type="primary"
                        icon={<PlusOutlined />}
                        onClick={handleCreateDataset}
                    >
                        Create Dataset
                    </Button>
                </div>
            </Card>

            {/* Data Table */}
            <Card>
                <div className="evaluation-gather-table bg-white rounded-lg">
                    <Table
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

            {/* Create Dataset Drawer */}
            <Drawer
                title="Create Dataset"
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
                    <GatherCreate
                      hideTitle={true} // Hide title
                      onCancel={handleCloseCreateDrawer}
                      onSuccess={() => {
                        handleCloseCreateDrawer();
                        fetchDatasets(); // Reload data
                      }}
                    />
                </div>
            </Drawer>
        </div>
    );
};

export default EvaluationGather;
