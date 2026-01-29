import React, { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Card,
  Button,
  Descriptions,
  Table,
  Tag,
  Spin,
  message,
  Tabs,
  Alert,
  Modal,
  Form,
  Typography,
  Input,
  Tooltip
} from 'antd';
import {
  ArrowLeftOutlined,
  EditOutlined,
  DeleteOutlined,
  EyeOutlined,
  DownloadOutlined,
  PlusOutlined,
  SaveOutlined,
  CloseOutlined,
  CheckOutlined
} from '@ant-design/icons';
import API from '../../../../services';
import usePagination from '../../../../hooks/usePagination';
import './index.css';

const { TabPane } = Tabs;
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

// Dataset details interface
interface DatasetDetail {
  id: number;
  name: string;
  description: string;
  columnsConfig: Array<{
    name: string;
    dataType: string;
    displayFormat: string;
    description?: string;
    required: boolean;
  }>;
  dataCount: number;
  createTime: string;
  updateTime: string;
  latestVersionId: number;
  versions: any;
  experiments: any;
}

// Data item interface
interface DataItem {
  id: number;
  datasetId: number;
  dataContent: string;
  remark: string;
  createTime: string;
  updateTime: string;
}

const GatherDetail: React.FC = () => {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const [loading, setLoading] = useState(true);
  const [detail, setDetail] = useState<DatasetDetail | null>(null);
  const [dataItems, setDataItems] = useState<any[]>([]);
  const [dataLoading, setDataLoading] = useState(false);
  const [activeTab, setActiveTab] = useState('data');
  const [isEditing, setIsEditing] = useState(false); // Edit mode state
  const [editForm] = Form.useForm(); // Edit form
  const [saving, setSaving] = useState(false); // Save state
  // Add selection state management
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  // Add version data and related experiments data state
  const [versions, setVersions] = useState<any[]>([]);
  const [versionsLoading, setVersionsLoading] = useState(false);
  const [experiments, setExperiments] = useState<any[]>([]);
  const [experimentsLoading, setExperimentsLoading] = useState(false);
  // Add data modal related state
  const [addDataModalVisible, setAddDataModalVisible] = useState(false);
  const [addDataForm] = Form.useForm();
  const [addingData, setAddingData] = useState(false);
  // Add inline editing related state
  const [editingRowId, setEditingRowId] = useState<number | null>(null);
  const [editingData, setEditingData] = useState<Record<string, any>>({});
  const [updatingData, setUpdatingData] = useState(false);
  // Add pending data state
  const [pendingDataItems, setPendingDataItems] = useState<any[]>([]);

  // Create independent pagination state for three tabs
  const { pagination: dataPagination, setPagination: setDataPagination, onPaginationChange: onDataPaginationChange, onShowSizeChange: onDataShowSizeChange } = usePagination();
  const { pagination: versionsPagination, setPagination: setVersionsPagination, onPaginationChange: onVersionsPaginationChange, onShowSizeChange: onVersionsShowSizeChange } = usePagination();
  const { pagination: experimentsPagination, setPagination: setExperimentsPagination, onPaginationChange: onExperimentsPaginationChange, onShowSizeChange: onExperimentsShowSizeChange } = usePagination();

  // Dynamically generate table columns
  const generateTableColumns = () => {
    const dynamicColumns: any[] = [];

    // Generate dynamic columns based on columnsConfig
    detail?.columnsConfig?.forEach((column, index) => {
      dynamicColumns.push({
        title: column.name,
        dataIndex: column.name,
        ellipsis: true,
        render: (text: string, record: any) => {
          if (editingRowId === record.id) {
            return (
              <Input.TextArea
                value={editingData[column.name] || ''}
                onChange={(e) => handleEditDataChange(column.name, e.target.value)}
                rows={2}
                maxLength={1000}
                placeholder={`Enter ${column.description || column.name}`}
              />
            );
          }
          return <Tooltip placement="topLeft" title={text}>
            <span>{text}</span>
          </Tooltip>
        }
      });
    });

    // Add fixed columns: create time, update time, actions
    dynamicColumns.push(
      {
        title: 'Created',
        dataIndex: 'createTime',
        render: (text: string) => formatDateTime(text)
      },
      {
        title: 'Updated',
        dataIndex: 'updateTime',
        render: (text: string) => formatDateTime(text)
      },
      {
        title: 'Actions',
        width: '10%',
        render: (_: any, record: any) => {
          if (editingRowId === record.id) {
            return (
              <div className="flex space-x-1">
                <Button
                  type="text"
                  icon={<CloseOutlined />}
                  size="small"
                  title="Cancel"
                  onClick={handleCancelEdit}
                  disabled={updatingData}
                />
                <Button
                  type="text"
                  icon={<CheckOutlined />}
                  size="small"
                  title="Confirm"
                  loading={updatingData}
                  onClick={() => handleConfirmEdit(record)}
                  style={{ color: '#52c41a' }}
                />
              </div>
            );
          }
          return (
            <div className="flex space-x-1">
              <Button
                type="text"
                icon={<EditOutlined />}
                size="small"
                title="Edit"
                onClick={() => handleEditRow(record)}
              />
              <Button
                type="text"
                icon={<DeleteOutlined />}
                size="small"
                danger
                title="Delete"
                onClick={() => handleDeleteRow(record)}
              />
            </div>
          );
        }
      }
    );

    return dynamicColumns;
  };

  // Selection handling
  const onSelectChange = (newSelectedRowKeys: React.Key[]) => {
    setSelectedRowKeys(newSelectedRowKeys);
  };

  const rowSelection = {
    selectedRowKeys,
    onChange: onSelectChange,
  };

  // Add data
  const handleAddData = () => {
    setAddDataModalVisible(true);
    addDataForm.resetFields();
  };

  // Handle data add submission
  const handleAddDataSubmit = async () => {
    try {
      setAddingData(true);
      const values = await addDataForm.validateFields();

      // Dynamically construct dataContent object based on columnsConfig
      const dataContent: Record<string, any> = {};

      // Iterate through each field in columnsConfig, add form values to dataContent
      detail?.columnsConfig?.forEach(column => {
        if (values[column.name] !== undefined) {
          dataContent[column.name] = values[column.name];
        }
      });

      // Add remark field (if any)
      if (values.remark) {
        dataContent.remark = values.remark;
      }

      // Create temporary data item (don't call API, add to local list)
      const newItem = {
        id: `pending_${Date.now()}`, // Temporary ID, use special prefix to identify
        ...dataContent,
        createTime: new Date().toISOString(),
        updateTime: new Date().toISOString()
      };

      // Add to pending data list
      setPendingDataItems(prev => [...prev, newItem]);

      // Also add to display list
      setDataItems(prev => [...prev, newItem]);

      // Update pagination info - increase total
      setDataPagination(prev => ({
        ...prev,
        total: prev.total + 1
      }));

      message.success('Data added to pending list, please submit new version to save changes');
      setAddDataModalVisible(false);
      addDataForm.resetFields();
    } catch (error) {
      console.error('Data add failed:', error);
      message.error('Data add failed, please try again');
    } finally {
      setAddingData(false);
    }
  };

  // Cancel add data
  const handleAddDataCancel = () => {
    setAddDataModalVisible(false);
    addDataForm.resetFields();
  };

  // Start editing row
  const handleEditRow = (record: any) => {
    setEditingRowId(record.id);

    // Initialize edit data, include all columnsConfig fields
    const editData: Record<string, any> = {};
    detail?.columnsConfig?.forEach(column => {
      editData[column.name] = record[column.name] || '';
    });

    setEditingData(editData);
  };

  // Cancel edit
  const handleCancelEdit = () => {
    setEditingRowId(null);
    setEditingData({});
  };

  // Confirm edit
  const handleConfirmEdit = async (record: any) => {
    try {
      setUpdatingData(true);

      // Construct updated dataContent, include all columnsConfig fields
      const dataContent: Record<string, any> = {};
      detail?.columnsConfig?.forEach(column => {
        dataContent[column.name] = editingData[column.name] || '';
      });

      // Call API to update data
      const response = await API.updateDatasetDataItem({
        id: record.id,
        dataContent: JSON.stringify(dataContent),
      });

      if (response.code === 200) {
        message.success('Data updated successfully');
        setEditingRowId(null);
        setEditingData({});
        // Refresh data list
        fetchDataItems();
      } else {
        throw new Error(response.message || 'Data update failed');
      }
    } catch (error) {
      console.error('Data update failed:', error);
      message.error('Data update failed, please try again');
    } finally {
      setUpdatingData(false);
    }
  };

  // Handle edit data change
  const handleEditDataChange = (field: string, value: string) => {
    setEditingData(prev => ({
      ...prev,
      [field]: value
    }));
  };

  // Delete single row data (frontend delete)
  const handleDeleteRow = (record: any) => {
    Modal.confirm({
      title: 'Confirm Delete',
      content: `Are you sure you want to delete this data? This operation only takes effect on the frontend, you need to click "Submit New Version" to save to the backend.`,
      okText: 'Delete',
      okType: 'danger',
      cancelText: 'Cancel',
      onOk: () => {
        // Check if it's pending data
        const isPendingData = pendingDataItems.some(item => item.id === record.id);

        if (isPendingData) {
          // If it's pending data, remove from pending list
          setPendingDataItems(prev => prev.filter(item => item.id !== record.id));
        }

        // Remove data from frontend list
        setDataItems(prev => prev.filter(item => item.id !== record.id));

        // If data is in selected list, also remove it
        setSelectedRowKeys(prev => prev.filter(key => key !== record.id));

        // Update pagination info - decrease total
        setDataPagination(prev => ({
          ...prev,
          total: prev.total - 1
        }));

        message.success(isPendingData ? 'Pending data deleted' : 'Data deleted (frontend)');
      }
    });
  };

  // Batch delete
  const handleBatchDelete = () => {
    if (selectedRowKeys.length === 0) {
      message.warning('Please select data to delete');
      return;
    }
    Modal.confirm({
      title: 'Confirm Delete',
      content: `Are you sure you want to delete the selected ${selectedRowKeys.length} items? This operation only takes effect on the frontend, you need to click "Submit New Version" to save to the backend.`,
      okText: 'Delete',
      okType: 'danger',
      cancelText: 'Cancel',
      onOk: () => {
        // Remove selected data from frontend list
        const deletedCount = selectedRowKeys.length;
        setDataItems(prev => prev.filter(item => !selectedRowKeys.includes(item.id)));

        // Remove selected data from pending list (if any)
        setPendingDataItems(prev => prev.filter(item => !selectedRowKeys.includes(item.id)));

        // Update pagination info - decrease total
        setDataPagination(prev => ({
          ...prev,
          total: prev.total - deletedCount
        }));

        message.success(`${deletedCount} items deleted (frontend)`);
        setSelectedRowKeys([]);
      }
    });
  };

  // Submit new version
  const handleSubmitVersion = async () => {
    Modal.confirm({
      title: 'Submit New Version',
      content: (
        <div>
          <p>Are you sure you want to submit a new version based on the current data?</p>
          <div className="text-sm text-gray-500 mt-3 p-3 bg-gray-50 rounded">
            <div>Dataset: {detail?.name}</div>
            <div>Data Count: {dataItems.length} items</div>
            <div>Column Config: {detail?.columnsConfig?.length || 0} columns</div>
            {pendingDataItems.length > 0 && (
              <div className="mt-2 text-orange-600">
                Note: Will also submit {pendingDataItems.length} new data items
              </div>
            )}
          </div>
        </div>
      ),
      okText: 'Submit',
      cancelText: 'Cancel',
      onOk: async () => {
        try {
          // Collect existing data item IDs on current page (excluding pending data)
          const existingDatasetItemIds = dataItems
            .filter(item => !pendingDataItems.some(pending => pending.id === item.id))
            .map(item => item.id);

          // If there's pending data, batch submit it first
          let allDatasetItemIds = [...existingDatasetItemIds];
          if (pendingDataItems.length > 0) {
            // Batch submit pending data
            const dataContentArray = pendingDataItems.map(item => {
              const dataContent: Record<string, any> = {};
              detail?.columnsConfig?.forEach(column => {
                if (item[column.name] !== undefined) {
                  dataContent[column.name] = item[column.name];
                }
              });
              if (item.remark) {
                dataContent.remark = item.remark;
              }
              return JSON.stringify(dataContent);
            });

            const response: any = await API.createDatasetDataItem({
              datasetId: Number(id),
              dataContent: dataContentArray, // now passing array
              columnsConfig: detail?.columnsConfig || []
            });

            if (response.code !== 200) {
              throw new Error(response.message || 'Data submission failed');
            }

            // Get newly added data item IDs
            if (response.data) {
              // If batch submit, response.data should be an array
              if (Array.isArray(response.data)) {
                const newIds = response.data.map((item: any) => item.id);
                allDatasetItemIds = [...allDatasetItemIds, ...newIds];
              } else if (response.data.id) {
                // If single submit, response.data should be an object
                allDatasetItemIds = [...allDatasetItemIds, response.data.id];
              }
            }

            message.success(`Successfully submitted ${pendingDataItems.length} new data items`);
            // Clear pending data list
            setPendingDataItems([]);
          }

          // Call API to submit version, use all data item IDs (including newly added)
          const versionResponse = await API.createDatasetVersion({
            datasetId: Number(id),
            description: `New version created based on current data - ${new Date().toLocaleString()}`,
            columnsConfig: detail?.columnsConfig || [],
            datasetItems: allDatasetItemIds, // Use all data item IDs
            status: 'draft'
          });

          console.log('Create new version API returned data:', versionResponse);

          if (versionResponse.code === 200) {
            message.success('New version submitted successfully');

            // Get new version ID
            const newVersionId = versionResponse.data?.id;
            console.log('Newly created version ID:', newVersionId);

            // Refresh dataset detail to update page info
            await fetchDatasetDetail();

            // Use new version ID to get data items
            if (newVersionId) {
              fetchDataItemsWithVersionId(newVersionId);
            } else {
              // If new version ID not obtained, refresh data using regular method
              fetchDataItems(dataPagination.current, dataPagination.pageSize);
            }

            // Refresh version info
            fetchVersions();
          } else {
            throw new Error(versionResponse.message || 'Version submission failed');
          }
        } catch (error) {
          const errMsg = error instanceof Error ? error.message : String(error);
          message.error(`Submission failed: ${errMsg || 'Please try again'}`);
          // If data submission failed, don't continue with version submission
          return Promise.reject(error);
        }
      }
    });
  };

  // Get dataset detail
  const fetchDatasetDetail = async () => {
    try {
      setLoading(true);

      const response = await API.getDataset({ datasetId: Number(id) });
      if (response.code === 200) {
        const apiData = response.data as any; // Use any type to bypass type checking
        // Convert API data to component required type
        const detailData: DatasetDetail = {
          id: apiData.id,
          name: apiData.name,
          description: apiData.description,
          columnsConfig: [],
          dataCount: apiData.dataCount,
          createTime: apiData.createTime,
          updateTime: apiData.updateTime,
          latestVersionId: apiData.latestVersionId,
          versions: apiData.versions,
          experiments: apiData.experiments
        };

        // Parse columnsConfig string to object
        try {
          detailData.columnsConfig = JSON.parse(apiData.columnsConfig || '[]');
        } catch {
          detailData.columnsConfig = [];
        }

        setDetail(detailData);
      } else {
        throw new Error(response.message || 'Failed to get detail');
      }
    } catch (error) {
      console.error('Failed to get dataset detail:', error);
      // Set to empty state on error
      setDetail(null);
      setLoading(false);
    } finally {
      setLoading(false);
    }
  };

  // Modify get data items list function, support pagination
  const fetchDataItems = async (pageNumber: number = 1, pageSize: number = 10) => {
    try {
      setDataLoading(true);

      console.log('Call getDatasetDataItems API, parameters:', {
        datasetVersionId: detail?.latestVersionId,
        pageNumber: pageNumber,
        pageSize: pageSize
      });

      const response = await API.getDatasetDataItems({
        datasetVersionId: detail?.latestVersionId,
        pageNumber: pageNumber,
        pageSize: pageSize
      });

      console.log('getDatasetDataItems API returned data:', response);

      if (response.code === 200 && response.data) {
        const dataResponse = response.data;

        console.log('Parsed dataResponse:', dataResponse);

        // Use pageItems field
        if (dataResponse.pageItems && Array.isArray(dataResponse.pageItems)) {
          console.log('Got pageItems array:', dataResponse.pageItems);

          // Transform API data to component required format
          const transformedData = dataResponse.pageItems.map((item: any) => {
            try {
              console.log('Processing data item:', item);
              const parsedContent = JSON.parse(item.dataContent);
              console.log('Parsed dataContent:', parsedContent);

              // Dynamically construct data object based on columnsConfig
              const dataObject: any = {
                id: item.id,
                createTime: item.createTime,
                updateTime: item.updateTime
              };

              // Add dynamic fields
              detail?.columnsConfig?.forEach(column => {
                dataObject[column.name] = parsedContent[column.name] || '';
              });

              console.log('Transformed data object:', dataObject);
              return dataObject;
            } catch (e) {
              console.error('Failed to parse dataContent:', e, item.dataContent);
              // If parsing fails, return basic data structure
              const dataObject: any = {
                id: item.id,
                createTime: item.createTime,
                updateTime: item.updateTime
              };

              // Set default values for all config fields
              detail?.columnsConfig?.forEach(column => {
                dataObject[column.name] = '';
              });

              return dataObject;
            }
          });

          console.log('Final transformed data items array:', transformedData);
          setDataItems(transformedData);

          // Update pagination info
          setDataPagination(prev => ({
            ...prev,
            current: dataResponse.pageNumber || pageNumber,
            pageSize: dataResponse.pageSize || pageSize,
            total: dataResponse.totalCount || 0
          }));
        } else {
          console.warn('API returned data pageItems field is not array or does not exist:', dataResponse);
          setDataItems([]);
          setDataPagination(prev => ({
            ...prev,
            current: 1,
            total: 0
          }));
        }
      } else {
        console.error('API returned error:', response.code, response.message);
        throw new Error(response.message || 'Failed to get data items');
      }
    } catch (error) {
      console.error('Failed to get data items:', error);
      // Set to empty array on error
      setDataItems([]);
      setDataPagination(prev => ({
        ...prev,
        current: 1,
        total: 0
      }));
    } finally {
      setDataLoading(false);
    }
  };

  // Get data items list using specified version ID
  const fetchDataItemsWithVersionId = async (versionId: number) => {
    try {
      setDataLoading(true);

      console.log('Call getDatasetDataItems API with new version ID, parameters:', {
        datasetVersionId: versionId,
        pageNumber: 1,
        pageSize: 10
      });

      const response = await API.getDatasetDataItems({
        datasetVersionId: versionId,
        pageNumber: 1,
        pageSize: 10
      });

      console.log('New version getDatasetDataItems API returned data:', response);

      if (response.code === 200 && response.data) {
        const dataResponse = response.data;

        console.log('New version parsed dataResponse:', dataResponse);

        // Use pageItems field
        if (dataResponse.pageItems && Array.isArray(dataResponse.pageItems)) {
          console.log('New version got pageItems array:', dataResponse.pageItems);

          // Transform API data to component required format
          const transformedData = dataResponse.pageItems.map((item: any) => {
            try {
              console.log('Processing data item:', item);
              const parsedContent = JSON.parse(item.dataContent);
              console.log('Parsed dataContent:', parsedContent);

              // Dynamically construct data object based on columnsConfig
              const dataObject: any = {
                id: item.id,
                createTime: item.createTime,
                updateTime: item.updateTime
              };

              // Add dynamic fields
              detail?.columnsConfig?.forEach(column => {
                dataObject[column.name] = parsedContent[column.name] || '';
              });

              console.log('Transformed data object:', dataObject);
              return dataObject;
            } catch (e) {
              console.error('Failed to parse dataContent:', e, item.dataContent);
              // If parsing fails, return basic data structure
              const dataObject: any = {
                id: item.id,
                createTime: item.createTime,
                updateTime: item.updateTime
              };

              // Set default values for all config fields
              detail?.columnsConfig?.forEach(column => {
                dataObject[column.name] = '';
              });

              return dataObject;
            }
          });

          console.log('New version final transformed data items array:', transformedData);
          setDataItems(transformedData);
        } else {
          console.warn('New version API returned data pageItems field is not array or does not exist:', dataResponse);
          setDataItems([]);
        }
      } else {
        console.error('New version API returned error:', response.code, response.message);
        throw new Error(response.message || 'Failed to get data items');
      }
    } catch (error) {
      console.error('Failed to get data items using new version ID:', error);
      // Set to empty array on error
      setDataItems([]);
    } finally {
      setDataLoading(false);
    }
  };

  // Modify get version data list function, support pagination
  const fetchVersions = async (pageNumber: number = 1, pageSize: number = 10) => {
    try {
      setVersionsLoading(true);

      const response = await API.getDatasetVersions({
        datasetId: Number(id),
        pageNumber: pageNumber,
        pageSize: pageSize
      });

      if (response.code === 200 && response.data) {
        const dataResponse = response.data;
        const versionsData = dataResponse.pageItems || [];
        setVersions(versionsData);

        // Update pagination info
        setVersionsPagination(prev => ({
          ...prev,
          current: dataResponse.pageNumber || pageNumber,
          pageSize: dataResponse.pageSize || pageSize,
          total: dataResponse.totalCount || 0
        }));
      } else {
        throw new Error(response.message || 'Failed to get version data');
      }
    } catch (error) {
      console.error('Failed to get version data:', error);
      // Set to empty array on error
      setVersions([]);
      setVersionsPagination(prev => ({
        ...prev,
        current: 1,
        total: 0
      }));
    } finally {
      setVersionsLoading(false);
    }
  };

  // Modify get related experiments list function, support pagination
  const fetchExperiments = async (pageNumber: number = 1, pageSize: number = 10) => {
    try {
      setExperimentsLoading(true);

      const response = await API.getDatasetExperiments({
        datasetId: Number(id),
        pageNumber: pageNumber,
        pageSize: pageSize
      });

      if (response.code === 200 && response.data) {
        const dataResponse = response.data;
        const experimentsData = dataResponse.pageItems || [];
        // Transform to component required format
        const transformedExperiments = experimentsData.map((exp: any) => ({
          id: exp.id,
          name: exp.name,
          version: exp.datasetVersion,
          status: exp.status,
          createTime: exp.createTime
        }));
        setExperiments(transformedExperiments);

        // Update pagination info
        setExperimentsPagination(prev => ({
          ...prev,
          current: dataResponse.pageNumber || pageNumber,
          pageSize: dataResponse.pageSize || pageSize,
          total: dataResponse.totalCount || 0
        }));
      } else {
        throw new Error(response.message || 'Failed to get related experiments');
      }
    } catch (error) {
      console.error('Failed to get related experiments:', error);
      // Set to empty array on error
      setExperiments([]);
      setExperimentsPagination(prev => ({
        ...prev,
        current: 1,
        total: 0
      }));
    } finally {
      setExperimentsLoading(false);
    }
  };

  useEffect(() => {
    if (id) {
      fetchDatasetDetail();
      // Load related experiments data directly on page load
      fetchExperiments(experimentsPagination.current, experimentsPagination.pageSize);
    }
  }, [id]);

  useEffect(() => {
    if (detail) {
      // After detail loading completes, immediately get version data
      fetchVersions(versionsPagination.current, versionsPagination.pageSize);
      // If currently on data management tab, also get data items
      if (activeTab === 'data') {
        fetchDataItems(dataPagination.current, dataPagination.pageSize);
      }
    }
  }, [detail]);

  useEffect(() => {
    // Only get data items when switching to data management tab
    if (detail && activeTab === 'data') {
      fetchDataItems(dataPagination.current, dataPagination.pageSize);
    }
    // Get version data when switching to version records tab
    else if (detail && activeTab === 'version') {
      fetchVersions(versionsPagination.current, versionsPagination.pageSize);
    }
    // Get experiments data when switching to related experiments tab
    else if (detail && activeTab === 'experiment') {
      // Directly get related experiments data
      fetchExperiments(experimentsPagination.current, experimentsPagination.pageSize);
    }
  }, [activeTab]);

  // Return to list page
  const handleGoBack = () => {
    navigate('/evaluation-gather');
  };

  // Enter edit mode
  const handleEdit = () => {
    setIsEditing(true);
    // Set form initial values
    editForm.setFieldsValue({
      name: detail?.name,
      description: detail?.description
    });
  };

  // Save edit
  const handleSave = async () => {
    try {
      setSaving(true);
      const values = await editForm.validateFields();

      // Call API to save changes
      const response = await API.updateDataset({
        datasetId: Number(id),
        name: values.name,
        description: values.description,
        columnsConfig: detail?.columnsConfig || []
      });

      if (response.code === 200) {
        message.success('Saved successfully');
        // Update local data
        setDetail(prev => prev ? {
          ...prev,
          name: values.name,
          description: values.description
        } : null);
        setIsEditing(false);
      } else {
        throw new Error(response.message || 'Save failed');
      }
    } catch (error) {
      console.error('Save failed:', error);
      message.error('Save failed, please try again');
    } finally {
      setSaving(false);
    }
  };

  // Cancel edit
  const handleCancel = () => {
    setIsEditing(false);
    editForm.resetFields();
  };

  // Delete dataset
  const handleDelete = async () => {
    Modal.confirm({
      title: 'Confirm Delete',
      content: `Are you sure you want to delete dataset "${detail?.name}"? This action cannot be undone.`,
      okText: 'Delete',
      okType: 'danger',
      cancelText: 'Cancel',
      onOk: async () => {
        try {
          await API.deleteDataset({ datasetId: Number(id) });
          message.success('Dataset deleted');
          navigate('/evaluation-gather');
        } catch (error) {
          message.error('Delete failed, please try again');
          console.error('Failed to delete dataset:', error);
        }
      }
    });
  };

  // Render data type tag
  const renderDataTypeTag = (dataType: string) => {
    const colorMap: Record<string, string> = {
      'String': 'blue',
      'Number': 'green',
      'Boolean': 'orange',
      'Array': 'purple',
      'Object': 'red'
    };
    return <Tag color={colorMap[dataType] || 'default'}>{dataType}</Tag>;
  };



  if (loading) {
    return (
      <div className="gather-detail-page">
        <div className="loading-container" style={{
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
      <div className="gather-detail-page">
        <div className="error-container" style={{
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          alignItems: 'center',
          height: '100vh',
          width: '100%'
        }}>
          <h3>Dataset detail does not exist</h3>
          <Button onClick={handleGoBack}>Back to List</Button>
        </div>
      </div>
    );
  }

  return (
    <div className="gather-detail-page p-8 fade-in">
      {/* Page header */}
      <div className="flex mb-6">
          <Button
            type="text"
            icon={<ArrowLeftOutlined />}
            onClick={handleGoBack}
            size="large"
          />
          <Title level={2} className="m-0">{detail?.name}</Title>
      </div>

      {/* Dataset info display area */}
      {detail && (
        <Card
          title="Dataset Information"
          extra={
            !isEditing ? (
              <Button
                type="primary"
                icon={<EditOutlined />}
                onClick={handleEdit}
              >
                Edit
              </Button>
            ) : (
              <div className="flex gap-2">
                <Button
                  onClick={handleCancel}
                  icon={<CloseOutlined />}
                >
                  Cancel
                </Button>
                <Button
                  type="primary"
                  loading={saving}
                  icon={<SaveOutlined />}
                  onClick={handleSave}
                >
                  Save
                </Button>
              </div>
            )
          }
          className="mb-6"
        >
          {!isEditing ? (
            // Display mode
            <>
              <Descriptions column={2} labelStyle={{ fontWeight: 500 }}>
                <Descriptions.Item label="Name">{detail.name || '-'}</Descriptions.Item>
                {/* <Descriptions.Item label="Creator">{detail.creator}</Descriptions.Item> */}
                <Descriptions.Item label="Description">{detail.description || '-'}</Descriptions.Item>
                <Descriptions.Item label="Data Count">{detail.dataCount || 0} items</Descriptions.Item>
                <Descriptions.Item label="Created">{formatDateTime(detail.createTime)}</Descriptions.Item>
                <Descriptions.Item label="Updated">{formatDateTime(detail.updateTime)}</Descriptions.Item>
              </Descriptions>
            </>
          ) : (
            // Edit mode
            <Form
              form={editForm}
              layout="vertical"
              className="edit-form"
            >
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
                <Form.Item
                  label="Dataset Name"
                  name="name"
                  rules={[
                    { required: true, message: 'Please enter dataset name' },
                    { max: 100, message: 'Name cannot exceed 100 characters' }
                  ]}
                >
                  <Input placeholder="Please enter dataset name" />
                </Form.Item>
              </div>

              <Form.Item
                label="Description"
                name="description"
                rules={[
                  { max: 500, message: 'Description cannot exceed 500 characters' }
                ]}
              >
                <Input.TextArea
                  rows={4}
                  placeholder="Please enter dataset description"
                  showCount
                  maxLength={500}
                />
              </Form.Item>
            </Form>
          )}
        </Card>
      )}

      {/* Tab navigation area */}
      <Card>
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          className="mb-6"
        >
          <TabPane tab="Data Management" key="data">
            {/* Action buttons area */}
            <div className="mb-4 flex gap-4 justify-between items-center" style={{flexWrap: 'wrap'}}>
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={handleAddData}
              >
                Add Data
              </Button>
              <Button
                onClick={handleBatchDelete}
                disabled={selectedRowKeys.length === 0}
              >
                Batch Delete
              </Button>
              <div style={{flex: 1}}></div>
              <Button
                type="primary"
                onClick={handleSubmitVersion}
              >
                Submit New Version
              </Button>
            </div>

            {/* Data table */}
            <Table
                rowSelection={rowSelection}
                dataSource={dataItems}
                rowKey="id"
                loading={dataLoading}
                columns={generateTableColumns()}
                pagination={{
                  current: dataPagination.current,
                  pageSize: dataPagination.pageSize,
                  total: dataPagination.total,
                  showTotal: dataPagination.showTotal,
                  showSizeChanger: dataPagination.showSizeChanger,
                  showQuickJumper: dataPagination.showQuickJumper,
                  pageSizeOptions: dataPagination.pageSizeOptions,
                  onChange: (page, pageSize) => {
                    onDataPaginationChange(page, pageSize);
                    fetchDataItems(page, pageSize || 10);
                  },
                  onShowSizeChange: (page, pageSize) => {
                    onDataShowSizeChange(page, pageSize);
                    fetchDataItems(page, pageSize);
                  }
                }}
              />
          </TabPane>

          <TabPane tab="Version Records" key="version">
            <Table
                dataSource={versions}
                rowKey="id"
                loading={versionsLoading}
                columns={[
                  {
                    title: 'Version',
                    dataIndex: 'version',
                    width: '15%',
                    render: (version: string) => (
                      <Tag color="blue">{version}</Tag>
                    )
                  },
                  {
                    title: 'Description',
                    dataIndex: 'description',
                    width: '35%'
                  },
                  {
                    title: 'Data Count',
                    dataIndex: 'dataCount',
                    width: '15%',
                    render: (count: number) => `${count} items`
                  },
                  // {
                  //   title: 'Creator',
                  //   dataIndex: 'creator',
                  //   width: '15%'
                  // },
                  {
                    title: 'Created',
                    dataIndex: 'createTime',
                    width: '20%',
                    render: (text: string) => formatDateTime(text)
                  }
                ]}
                pagination={{
                  current: versionsPagination.current,
                  pageSize: versionsPagination.pageSize,
                  total: versionsPagination.total,
                  showTotal: versionsPagination.showTotal,
                  showSizeChanger: versionsPagination.showSizeChanger,
                  showQuickJumper: versionsPagination.showQuickJumper,
                  pageSizeOptions: versionsPagination.pageSizeOptions,
                  onChange: (page, pageSize) => {
                    onVersionsPaginationChange(page, pageSize);
                    fetchVersions(page, pageSize || 10);
                  },
                  onShowSizeChange: (page, pageSize) => {
                    onVersionsShowSizeChange(page, pageSize);
                    fetchVersions(page, pageSize);
                  }
                }}
              />

              {!versionsLoading && versions.length === 0 && (
                <div className="text-center py-8 text-gray-500">
                  <div className="text-lg mb-2">No version records</div>
                  <div>Will display here after creating a new version</div>
                </div>
              )}
          </TabPane>

          <TabPane tab="Related Experiments" key="experiment">
            <Table
                dataSource={experiments}
                rowKey="id"
                loading={experimentsLoading}
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
                    width: '40%',
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
                    title: 'Status',
                    dataIndex: 'status',
                    width: '15%',
                    render: (status: string) => {
                      const statusConfig = {
                        'RUNNING': { color: 'processing', text: 'Running' },
                        'COMPLETED': { color: 'success', text: 'Completed' },
                        'FAILED': { color: 'error', text: 'Stopped' },
                        'WAITING': { color: 'default', text: 'Waiting' },
                        'running': { color: 'processing', text: 'Running' },
                        'completed': { color: 'success', text: 'Completed' },
                        'stopped': { color: 'error', text: 'Stopped' },
                        'waiting': { color: 'default', text: 'Waiting' }
                      };
                      const config = statusConfig[status as keyof typeof statusConfig] || statusConfig['waiting'];
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
                pagination={{
                  current: experimentsPagination.current,
                  pageSize: experimentsPagination.pageSize,
                  total: experimentsPagination.total,
                  showTotal: experimentsPagination.showTotal,
                  showSizeChanger: experimentsPagination.showSizeChanger,
                  showQuickJumper: experimentsPagination.showQuickJumper,
                  pageSizeOptions: experimentsPagination.pageSizeOptions,
                  onChange: (page, pageSize) => {
                    onExperimentsPaginationChange(page, pageSize);
                    fetchExperiments(page, pageSize || 10);
                  },
                  onShowSizeChange: (page, pageSize) => {
                    onExperimentsShowSizeChange(page, pageSize);
                    fetchExperiments(page, pageSize);
                  }
                }}
              />

              {!experimentsLoading && experiments.length === 0 && (
                <div className="text-center py-8 text-gray-500">
                  <div className="text-lg mb-2">No related experiments</div>
                  <div>Will display here after creating experiments</div>
                </div>
              )}
          </TabPane>
        </Tabs>
      </Card>

      {/* Add data modal */}
      <Modal
        title="Add Data"
        open={addDataModalVisible}
        onCancel={handleAddDataCancel}
        footer={[
          <Button key="cancel" onClick={handleAddDataCancel}>
            Cancel
          </Button>,
          <Button
            key="submit"
            type="primary"
            loading={addingData}
            onClick={handleAddDataSubmit}
          >
            Confirm
          </Button>,
        ]}
        width={700}
        style={{
          maxHeight: '90vh',
          top: 20
        }}
        bodyStyle={{
          maxHeight: 'calc(90vh - 110px)',
          overflowY: 'auto',
          padding: '20px 24px'
        }}
        destroyOnClose
      >
        <div className="add-data-form-container">
          <Form
            form={addDataForm}
            layout="vertical"
          >
            {/* Dynamically generate form fields based on columnsConfig */}
            {detail?.columnsConfig?.map((column, index) => (
              <Form.Item
                key={column.name}
                label={column.name}
                name={column.name}
                rules={[
                  ...(column.required ? [{ required: true, message: `Please enter ${column.description || column.name} content` }] : []),
                  { max: 1000, message: `${column.description || column.name} content cannot exceed 1000 characters` }
                ]}
              >
                <Input.TextArea
                  rows={4}
                  placeholder={`Enter ${column.description || column.name} content`}
                  showCount
                  maxLength={1000}
                  style={{ resize: 'none' }}
                />
              </Form.Item>
            ))}

            <Form.Item
              label="Remarks (Optional)"
              name="remark"
              rules={[
                { max: 200, message: 'Remarks cannot exceed 200 characters' }
              ]}
            >
              <Input.TextArea
                rows={2}
                placeholder="Enter remarks (optional)"
                showCount
                maxLength={200}
              />
            </Form.Item>
          </Form>
        </div>
      </Modal>
    </div>
  );
};

export default GatherDetail;
