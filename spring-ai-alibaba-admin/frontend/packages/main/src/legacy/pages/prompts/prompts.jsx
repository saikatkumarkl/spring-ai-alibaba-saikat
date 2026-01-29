import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Spin,
  Button,
  Alert,
  Empty,
  Table,
  Input,
  Card,
  Tag,
  Space,
  Typography,
  Tooltip
} from 'antd';
import {
  PlusOutlined,
  ClearOutlined,
  EyeOutlined,
  DeleteOutlined,
  ShareAltOutlined,
  ClockCircleOutlined,
  CheckCircleOutlined,
  ExperimentOutlined,
  QuestionCircleOutlined
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { handleApiError, notifySuccess } from '../../utils/notification';
import CreatePromptModal from '../../components/CreatePromptModal';
import DeleteConfirmModal from '../../components/DeleteConfirmModal';
// import ElementSelector from '../../components/ElementSelector';
import API from '../../services';
import usePagination from '../../hooks/usePagination';
import { safeJSONParse } from '../../utils/util';
import { buildLegacyPath } from '../../utils/path';

const { Title, Paragraph } = Typography;
const { Search } = Input;

const PromptsPage = () => {
  const navigate = useNavigate();

  const { pagination, onPaginationChange, onShowSizeChange, setPagination } = usePagination();

  const [prompts, setPrompts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // Search and filter state
  const [searchName, setSearchName] = useState('');
  const [searchTag, setSearchTag] = useState('');
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [selectedPrompt, setSelectedPrompt] = useState(null);

  // Parse tags string to array
  const parseTags = (tagsString) => {
    if (!tagsString) return [];
    return safeJSONParse(tagsString, () => tagsString.split(',').map(tag => tag.trim()).filter(tag => tag));
  };

  // Load Prompts list
  const loadPrompts = async (page = 1, pagesize = pagination.pageSize) => {
    setLoading(true);
    setError(null);

    try {
      const params = {
        pageNo: page,
        pageSize: pagesize,
      };

      // Add search conditions
      if (searchName) {
        params.promptKey = searchName;
        params.search = 'blur';
      }

      if (searchTag) {
        params.tag = searchTag;
      }

      const response = await API.getPrompts(params);

      if (response.code === 200) {
        setPrompts(response.data.pageItems || []);
        setPagination({
          ...pagination,
          total: response.data.totalCount || 0,
          totalPage: response.data.totalPage || 0,
        });
      } else {
        throw new Error(response.message || 'Failed to load');
      }
    } catch (err) {
      console.error('Failed to load Prompts:', err);
      handleApiError(err, 'Load Prompts List');
      setError(err.message || 'Network error, please try again later');
    } finally {
      setLoading(false);
    }
  };

  const handleSearchName = useCallback((searchName) => {
    setSearchName(searchName);
  }, []);

  const handleSearchTag = useCallback((searchTag) => {
    setSearchTag(searchTag);
  }, []);

  // Reload when search conditions change
  useEffect(() => {
    onPaginationChange(1, pagination.pageSize);
    loadPrompts(1, pagination.pageSize);
  }, [searchName, searchTag]);

  // Load when pagination changes
  useEffect(() => {
    loadPrompts(pagination.current, pagination.pageSize);
  }, [pagination.current, pagination.pageSize]);

  const handleDelete = (prompt, event) => {
    event.stopPropagation();
    setSelectedPrompt(prompt);
    setShowDeleteModal(true);
  };

  const handleView = (prompt) => {
    localStorage.removeItem("prompt-sessions");
    navigate(buildLegacyPath('/prompt-detail', { promptKey: prompt.promptKey }));
  };

  const confirmDelete = async () => {
    if (!selectedPrompt) return;

    try {
      const response = await API.deletePrompt({ promptKey: selectedPrompt.promptKey });

      if (response.code === 200) {
        // Reload list after successful deletion
        notifySuccess({
          message: 'Prompt Deleted Successfully',
          description: `Prompt "${selectedPrompt.promptKey}" has been deleted`
        });
        await loadPrompts(pagination.current);
        setShowDeleteModal(false);
        setSelectedPrompt(null);
      } else {
        throw new Error(response.message || 'Failed to delete');
      }
    } catch (err) {
      console.error('Failed to delete Prompt:', err);
      handleApiError(err, 'Delete Prompt');
      setError(err.message || 'Failed to delete, please try again later');
    }
  };

  // Render status badge
  const renderStatusBadge = (prompt) => {
    if (!prompt.latestVersion || !prompt.latestVersionStatus) {
      return (
        <Tag color="warning" icon={<ClockCircleOutlined />}>
          No Version
        </Tag>
      );
    }

    // Display different status based on latestVersionStatus
    if (prompt.latestVersionStatus === 'release') {
      return (
        <Tag color="success" icon={<CheckCircleOutlined />}>
          Release
        </Tag>
      );
    } else if (prompt.latestVersionStatus === 'pre') {
      return (
        <Tag color="processing" icon={<ExperimentOutlined />}>
          Pre-release
        </Tag>
      );
    } else {
      return (
        <Tag color="default" icon={<QuestionCircleOutlined />}>
          Unknown
        </Tag>
      );
    }
  };

  // Table columns configuration
  const columns = [
    {
      title: 'Prompt Key',
      dataIndex: 'promptKey',
      key: 'promptKey',
      render: (text) => <span style={{ fontWeight: 500 }}>{text}</span>,
    },
    {
      title: 'Description',
      dataIndex: 'promptDescription',
      key: 'promptDescription',
      ellipsis: {
        showTitle: false,
      },
      render: (text) => (
        <Tooltip placement="topLeft" title={text}>
          {text || 'No description'}
        </Tooltip>
      ),
    },
    {
      title: 'Latest Version',
      dataIndex: 'latestVersion',
      key: 'latestVersion',
      render: (version) => (
        version ? (
          <Tag color="blue">{version}</Tag>
        ) : (
          <Tag color="default">No Version</Tag>
        )
      ),
    },
    {
      title: 'Status',
      key: 'status',
      render: (_, record) => renderStatusBadge(record),
    },
    {
      title: 'Tags',
      dataIndex: 'tags',
      key: 'tags',
      render: (tags) => (
        <Space size={[0, 4]} wrap>
          {parseTags(tags).map((tag, index) => (
            <Tag key={index} color="geekblue">
              {tag}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: 'Created At',
      dataIndex: 'createTime',
      key: 'createTime',
      render: (time) => dayjs(time).format('YYYY-MM-DD HH:mm:ss'),
    },
    {
      title: 'Updated At',
      dataIndex: 'updateTime',
      key: 'updateTime',
      render: (time) => dayjs(time).format('YYYY-MM-DD HH:mm:ss'),
    },
    {
      title: 'Actions',
      key: 'action',
      render: (_, record) => (
        <Space size="middle">
          <Tooltip title="View Details">
            <Button
              type="text"
              icon={<EyeOutlined />}
              onClick={(e) => {
                e.stopPropagation();
                handleView(record);
              }}
            />
          </Tooltip>
          <Tooltip title="View Trace">
            <Button
              type="text"
              icon={<ShareAltOutlined />}
              onClick={(e) => {
                e.stopPropagation();
                navigate(buildLegacyPath("/tracing"), {
                  state: {
                    adv: {
                      "spring.ai.alibaba.prompt.key":record.promptKey
                    }
                  }
                });
              }}
            />
          </Tooltip>
          <Tooltip title="Delete">
            <Button
              type="text"
              danger
              icon={<DeleteOutlined />}
              onClick={(e) => handleDelete(record, e)}
            />
          </Tooltip>
        </Space>
      ),
    },
  ];

  const handleElementSelect = (element, elementInfo) => {
  };

  return (
    <div>
    {/* <ElementSelector onSelect={handleElementSelect} debug={true}>*/}
      <div className="p-8 fade-in">
      <div className="mb-8">
        <Title level={2} style={{ marginBottom: 8 }}>Prompts Management</Title>
        <Paragraph type="secondary">Manage and organize your AI prompt templates</Paragraph>
      </div>

      {/* Error alert */}
      {error && (
        <Alert
          message="Loading Error"
          description={error}
          type="error"
          showIcon
          style={{ marginBottom: 24 }}
        />
      )}

      {/* Search and create area */}
      <Card style={{ marginBottom: 24 }}>
        <div style={{ display: 'flex', gap: 16, alignItems: 'end', flexWrap: 'wrap' }}>
          <div style={{ flex: 1, minWidth: 256 }}>
            <label style={{ display: 'block', marginBottom: 8, fontWeight: 500 }}>
              Search by Prompt Key
            </label>
            <Search
              placeholder="Enter Prompt Key..."
              onSearch={handleSearchName}
              allowClear
            />
          </div>

          <div style={{ flex: 1, minWidth: 256 }}>
            <label style={{ display: 'block', marginBottom: 8, fontWeight: 500 }}>
              Search by Tag
            </label>
            <Search
              placeholder="Enter tag..."
              onSearch={handleSearchTag}
              allowClear
            />
          </div>

          <Space>
            <Button
              type="primary"
              onClick={() => setShowCreateModal(true)}
              icon={<PlusOutlined />}
            >
              Create Prompt
            </Button>
          </Space>
        </div>
      </Card>

      {/* Prompts list */}
      <Card>
        <Table
          columns={columns}
          dataSource={prompts}
          loading={loading}
          rowKey="promptKey"
          onRow={(record) => ({
            onClick: () => handleView(record),
            style: { cursor: 'pointer' },
          })}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="No matching prompts found"
              >
                <Button type="primary" onClick={() => setShowCreateModal(true)}>
                  Create Your First Prompt
                </Button>
              </Empty>
            ),
          }}
          pagination={{
            ...pagination,
            onChange: onPaginationChange,
            onShowSizeChange: onShowSizeChange
          }}
          scroll={{ x: 800 }}
        />
      </Card>


      {/* Modal dialogs */}
      {showCreateModal && (
        <CreatePromptModal
          onClose={() => setShowCreateModal(false)}
          onSuccess={() => {
            setShowCreateModal(false);
            loadPrompts(pagination.current); // Refresh list after successful creation
          }}
        />
      )}

      {showDeleteModal && selectedPrompt && (
        <DeleteConfirmModal
          prompt={selectedPrompt}
          onConfirm={confirmDelete}
          onClose={() => setShowDeleteModal(false)}
        />
      )}
    </div>
    {/* </ElementSelector>*/}
    </div>
  );
};

export default PromptsPage;
