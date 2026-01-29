import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Form, Input, Button, Card, Select, message, Space, Divider } from 'antd';
import { ArrowLeftOutlined, PlusOutlined, MinusCircleOutlined } from '@ant-design/icons';
import API from '../../../../services';
import './index.css';

const { TextArea } = Input;
const { Option } = Select;

// Data type options
const DATA_TYPES = [
  { value: 'String', label: 'String' },
  { value: 'Number', label: 'Number' },
  { value: 'Boolean', label: 'Boolean' },
  { value: 'Array', label: 'Array' },
  { value: 'Object', label: 'Object' }
];

// View format options
const VIEW_FORMATS = [
  { value: 'PlainText', label: 'PlainText' },
  { value: 'JSON', label: 'JSON' },
  { value: 'Markdown', label: 'Markdown' },
  { value: 'HTML', label: 'HTML' }
];

// Column configuration interface
interface ColumnConfig {
  name: string;
  dataType: string;
  displayFormat: string;
  description: string;
  required: boolean;
}

// Form data interface
interface CreateDatasetForm {
  name: string;
  description: string;
  columns: ColumnConfig[];
}

// Component props interface
interface GatherCreateProps {
  onCancel?: () => void;
  onSuccess?: () => void;
  hideTitle?: boolean; // Add hideTitle property to control whether to hide title
}

const GatherCreate: React.FC<GatherCreateProps> = ({ onCancel, onSuccess, hideTitle = false }) => {
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  // Return to list page
  const handleGoBack = () => {
    if (onCancel) {
      onCancel();
    } else {
      navigate('/evaluation-gather');
    }
  };

  // Submit form
  const handleSubmit = async (values: CreateDatasetForm) => {
    try {
      setLoading(true);

      // Build submit data
      const submitData = {
        name: values.name,
        description: values.description,
        columnsConfig: values.columns.map(column => ({
          ...column,
          required: true as const // API requires required field to be true
        })),
      };

      console.log('Submit data:', submitData);

      // Call create dataset API here
      await API.createDataset(submitData);

      message.success('Dataset created successfully');

      // If onSuccess callback is provided, call it, otherwise navigate to list page
      if (onSuccess) {
        onSuccess();
      } else {
        navigate('/evaluation-gather');
      }
    } catch (error) {
      message.error('Creation failed, please try again');
      console.error('Failed to create dataset:', error);
    } finally {
      setLoading(false);
    }
  };

  // Cancel creation
  const handleCancel = () => {
    if (onCancel) {
      onCancel();
    } else {
      navigate('/evaluation-gather');
    }
  };

  return (
    <div className="gather-create-page">
      {/* Page header - fixed at top */}
      {!hideTitle && (
        <div className="gather-create-header">
          <div className="flex items-center">
            <Button
              type="text"
              icon={<ArrowLeftOutlined />}
              onClick={handleGoBack}
              className="mr-3"
            >
            </Button>
            <h1 className="text-2xl font-semibold mb-0">Create Dataset</h1>
          </div>
        </div>
      )}

      {/* Page content - scrollable area */}
      <div className={`gather-create-content ${hideTitle ? 'pt-6' : ''}`}>
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          initialValues={{
            columns: [
              {
                name: 'input',
                dataType: 'String',
                displayFormat: 'PlainText',
                description: 'Actual input (passed to the evaluation target as input)',
                required: true
              },
              {
                name: 'reference_output',
                dataType: 'String',
                displayFormat: 'PlainText',
                description: 'Reference output answer (expected ideal output, can be used as reference standard during evaluation)',
                required: true
              }
            ]
          }}
        >
          {/* Basic information */}
          <Card title="Basic Information" className="mb-6">
            <Form.Item
              name="name"
              label="Dataset Name"
              rules={[
                { required: true, message: 'Please enter dataset name' },
                { max: 100, message: 'Name cannot exceed 100 characters' }
              ]}
            >
              <Input placeholder="e.g., Q&A Bot" />
            </Form.Item>

            <Form.Item
              name="description"
              label="Dataset Description"
              rules={[
                { max: 500, message: 'Description cannot exceed 500 characters' }
              ]}
            >
              <TextArea
                placeholder="Optional dataset description"
                rows={4}
                showCount
                maxLength={500}
              />
            </Form.Item>
          </Card>

          {/* Dataset column configuration */}
          <Form.List name="columns">
            {(fields, { add, remove }) => {
              const formValues = form.getFieldsValue();

              return (
                <Card
                  title="Dataset Column Configuration"
                  extra={
                    <Button
                      type="primary"
                      onClick={() => add({
                        name: '',
                        dataType: 'String',
                        displayFormat: 'PlainText',
                        description: '',
                        required: false
                      })}
                      icon={<PlusOutlined />}
                      size="small"
                    >
                      Add Column
                    </Button>
                  }
                  className="mb-6"
                >
                    {fields.map(({ key, name, ...restField }) => {
                      const currentColumn = formValues?.columns?.[name];
                      const isRequired = currentColumn?.required;

                      return (
                        <Card
                          key={key}
                          type="inner"
                          className="mb-4"
                          title={
                            <Form.Item
                              {...restField}
                              name={[name, 'name']}
                              className="mb-0"
                            >
                              <Input
                                placeholder="Column Name"
                                variant="borderless"
                                className="font-medium"
                              />
                            </Form.Item>
                          }
                          extra={
                            !isRequired && (
                              <Button
                                type="text"
                                danger
                                icon={<MinusCircleOutlined />}
                                onClick={() => remove(name)}
                              />
                            )
                          }
                        >
                          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
                            <Form.Item
                              {...restField}
                              name={[name, 'name']}
                              label="Column Name"
                              rules={[{ required: true, message: 'Please enter column name' }]}
                            >
                              <Input placeholder="e.g., input" />
                            </Form.Item>

                            <Form.Item
                              {...restField}
                              name={[name, 'dataType']}
                              label="Data Type"
                              rules={[{ required: true, message: 'Please select data type' }]}
                            >
                              <Select placeholder="Please select">
                                {DATA_TYPES.map(type => (
                                  <Option key={type.value} value={type.value}>
                                    {type.label}
                                  </Option>
                                ))}
                              </Select>
                            </Form.Item>

                            <Form.Item
                              {...restField}
                              name={[name, 'displayFormat']}
                              label="View Format"
                              rules={[{ required: true, message: 'Please select view format' }]}
                            >
                              <Select placeholder="Please select">
                                {VIEW_FORMATS.map(format => (
                                  <Option key={format.value} value={format.value}>
                                    {format.label}
                                  </Option>
                                ))}
                              </Select>
                            </Form.Item>
                          </div>

                          <Form.Item
                            {...restField}
                            name={[name, 'description']}
                            label="Column Description"
                            rules={[{ required: true, message: 'Please enter column description' }]}
                          >
                            <TextArea
                              placeholder="Please enter column description"
                              rows={3}
                            />
                          </Form.Item>

                          {/* Hidden required field */}
                          <Form.Item
                            {...restField}
                            name={[name, 'required']}
                            hidden
                          >
                            <Input />
                          </Form.Item>
                        </Card>
                      );
                    })}
                </Card>
                );
              }}
            </Form.List>
        </Form>
      </div>

      {/* Bottom action buttons - fixed at bottom */}
      <div className="gather-create-footer">
        <div className="flex justify-end space-x-4">
          <Button size="large" onClick={handleCancel}>
            Cancel
          </Button>
          <Button
            type="primary"
            size="large"
            htmlType="submit"
            loading={loading}
            onClick={() => form.submit()}
          >
            Create
          </Button>
        </div>
      </div>
    </div>
  );
};

export default GatherCreate;
