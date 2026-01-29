import React, { useState } from 'react';
import {
  Modal,
  Card,
  Typography,
  Button,
  Input,
  Alert,
  Space,
  Form,
  message
} from 'antd';
import {
  CloseOutlined,
  PlusOutlined,
  ExclamationCircleOutlined,
  InfoCircleOutlined
} from '@ant-design/icons';
import { notifyError, notifySuccess, handleApiError } from '../utils/notification';
import API from '../services';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;

const CreateEvaluatorModal = ({ onClose, onSuccess }) => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);
      setError(null);

      const response = await API.createEvaluator({
        name: values.name,
        description: values.description || ''
      });

      if (response.code === 200) {
        notifySuccess({
          message: 'Evaluator created successfully',
          description: `Evaluator "${values.name}" has been successfully created`
        });
        form.resetFields();
        onSuccess?.(response.data);
        onClose();
      } else {
        throw new Error(response.message || 'Creation failed');
      }
    } catch (error) {
      console.error('Failed to create evaluator:', error);
      if (error.errorFields) {
        // Form validation error
        setError('Please check if the form is filled correctly');
      } else {
        handleApiError(error, 'Create Evaluator');
        setError(error.message || 'Creation failed, please try again later');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleCancel = () => {
    form.resetFields();
    onClose();
  };

  return (
    <Modal
      title={
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{
            width: 40,
            height: 40,
            backgroundColor: '#f6ffed',
            borderRadius: '50%',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
          }}>
            <PlusOutlined style={{ color: '#52c41a', fontSize: 20 }} />
          </div>
          <Title level={3} style={{ margin: 0 }}>
            Create New Evaluator
          </Title>
        </div>
      }
      open={true}
      onCancel={handleCancel}
      width={600}
      centered
      style={{
        maxHeight: 'calc(100vh - 40px)'
      }}
      bodyStyle={{
        maxHeight: 'calc(100vh - 200px)',
        overflowY: 'auto'
      }}
      footer={[
        <Button key="cancel" onClick={handleCancel}>
          Cancel
        </Button>,
        <Button
          key="submit"
          type="primary"
          loading={loading}
          onClick={handleSubmit}
          icon={<PlusOutlined />}
        >
          {loading ? 'Creating...' : 'Create Evaluator'}
        </Button>
      ]}
      closeIcon={<CloseOutlined />}
    >
      <Space direction="vertical" size={24} style={{ width: '100%' }}>
        {error && (
          <Alert
            message="Creation Failed"
            description={error}
            type="error"
            icon={<ExclamationCircleOutlined />}
            showIcon
          />
        )}

        <Form
          form={form}
          layout="vertical"
          requiredMark="optional"
          style={{ width: '100%' }}
        >
          <Form.Item
            label="Evaluator Name"
            name="name"
            rules={[
              { required: true, message: 'Please enter evaluator name' },
              { max: 50, message: 'Name cannot exceed 50 characters' },
              {
                pattern: /^[a-zA-Z0-9\u4e00-\u9fa5_-]+$/,
                message: 'Name can only contain Chinese, English, numbers, underscores and hyphens'
              }
            ]}
          >
            <Input
              placeholder="Enter evaluator name"
              size="large"
              showCount
              maxLength={50}
            />
          </Form.Item>

          <Form.Item
            label="Description"
            name="description"
            rules={[
              { max: 500, message: 'Description cannot exceed 500 characters' }
            ]}
          >
            <TextArea
              placeholder="Enter evaluator description (optional)"
              rows={4}
              showCount
              maxLength={500}
            />
          </Form.Item>
        </Form>

        {/* Notification Information */}
        <Alert
          message="Configuration Steps After Creation"
          description={
            <div>
              <Paragraph style={{ margin: 0, marginBottom: 8 }}>
                After creating the evaluator, you can configure detailed version information on the details page, including:
              </Paragraph>
              <ul style={{ margin: 0, paddingLeft: 20 }}>
                <li>Judge model selection (GPT-4, Claude, etc.)</li>
                <li>Evaluation Prompt content</li>
                <li>Model parameter configuration</li>
                <li>Version management and publishing</li>
              </ul>
            </div>
          }
          type="info"
          icon={<InfoCircleOutlined />}
          showIcon
        />
      </Space>
    </Modal>
  );
};

export default CreateEvaluatorModal;
