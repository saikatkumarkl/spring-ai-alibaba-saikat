import InnerLayout from '@/components/InnerLayout';
import $i18n from '@/i18n';
import { convertDifyToSpringAI } from '@/services/difyConverter';
import { Button, Upload, message } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import { useRequest } from 'ahooks';
import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import styles from './index.module.less';

const { Dragger } = Upload;

const DifyConverter: React.FC = () => {
  const navigate = useNavigate();
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [convertResult, setConvertResult] = useState<string[]>([]);

  const { loading: converting, runAsync } = useRequest(convertDifyToSpringAI, {
    manual: true,
  });

  const handleFileChange = (info: any) => {
    const { status, file } = info;

    // Since we return false in beforeUpload, the file won't actually be uploaded
    // So we need to handle file selection directly here
    if (file) {
      setSelectedFile(file.originFileObj || file);
      message.success(`${file.name} file selected successfully`);
    }
  };

  const handleBeforeUpload = (file: File) => {
    // Check file type
    const isYaml = file.type === 'application/x-yaml' ||
                   file.type === 'text/yaml' ||
                   file.name.endsWith('.yaml') ||
                   file.name.endsWith('.yml');
    if (!isYaml) {
      message.error('Only YAML format Dify DSL files are supported!');
      return false;
    }

    // Directly set the selected file because we want to prevent automatic upload
    setSelectedFile(file);
    message.success(`${file.name} file selected successfully`);

    // Prevent automatic upload, only do file selection
    return false;
  };

  const handleConvert = async () => {
    if (!selectedFile) {
      message.warning('Please select a Dify DSL file first');
      return;
    }

    try {
      // Read the original content of the user uploaded file
      const fileContent = await readFileContent(selectedFile);

      // Prepare request parameters
      const params = {
        dependencies: 'spring-ai-alibaba-graph,web,spring-ai-alibaba-starter-dashscope',
        appMode: 'workflow',
        dslDialectType: 'dify',
        type: 'maven-project',
        language: 'java',
        bootVersion: '3.5.0',
        baseDir: 'demo',
        groupId: 'com.example',
        artifactId: 'demo',
        name: 'demo',
        description: 'Demo project for Spring Boot',
        packageName: 'com.example.demo',
        packaging: 'jar',
        javaVersion: '17',
        dsl: fileContent,
      };

      // Call conversion service
      const response = await runAsync(params);

      // Handle zip file download
      const blob = response.data;
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = 'spring-ai-alibaba-demo.zip';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);

      message.success('Conversion successful! Project file download started');
      setConvertResult([
        'Spring AI Alibaba project generated',
        'Project Type: Maven Project',
        'Language: Java 17',
        'Includes Dependencies: spring-ai-alibaba-graph, web, spring-ai-alibaba-starter-dashscope',
        'Application Mode: workflow'
      ]);

    } catch (error) {
      console.error('Conversion failed:', error);
      message.error(`Conversion failed: ${error.message || 'Please try again'}`);
    }
  };

  // Helper function to read file content
  const readFileContent = (file: File): Promise<string> => {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = (e) => {
        const content = e.target?.result as string;
        resolve(content);
      };
      reader.onerror = () => {
        reject(new Error('File read failed'));
      };
      reader.readAsText(file, 'utf-8');
    });
  };

  const handleGoBack = () => {
    navigate('/');
  };

  return (
    <InnerLayout
      breadcrumbLinks={[
        {
          title: $i18n.get({
            id: 'main.pages.App.index.home',
            dm: 'Home',
          }),
          path: '/',
        },
        {
          title: 'DIFY Application Conversion',
        },
      ]}
    >
      <div className={styles.container}>
        <div className={styles.header}>
          <h2>Convert DIFY Application to Spring AI Alibaba Project</h2>
        </div>

        {/* Description area */}
        <div className={styles.description}>
          <h3>Instructions</h3>
          <div className={styles.instructionList}>
            <div className={styles.instruction}>
              <span className={styles.step}>1.</span>
              <span>Export your agent application's DSL configuration file from the Dify platform (YAML format)</span>
            </div>
            <div className={styles.instruction}>
              <span className={styles.step}>2.</span>
              <span>Drag and drop the DSL file to the file selection area below, or click to select file</span>
            </div>
            <div className={styles.instruction}>
              <span className={styles.step}>3.</span>
              <span>Click the "Start Conversion" button, the system will automatically parse the DSL and generate a Spring AI Alibaba project</span>
            </div>
            <div className={styles.instruction}>
              <span className={styles.step}>4.</span>
              <span>After conversion, you can download the generated project source code and import it into your IDE for development</span>
            </div>
          </div>
        </div>

        {/* File selection area */}
        <div className={styles.uploadSection}>
          <h3>Select Dify DSL File</h3>
          <Dragger
            name="file"
            multiple={false}
            beforeUpload={handleBeforeUpload}
            onChange={handleFileChange}
            className={styles.uploader}
            accept=".yaml,.yml"
          >
            <p className="ant-upload-drag-icon">
              <InboxOutlined />
            </p>
            <p className="ant-upload-text">Click or drag Dify DSL file to this area</p>
            <p className="ant-upload-hint">
              Supports YAML format Dify DSL configuration files (.yaml or .yml)
            </p>
          </Dragger>

          {selectedFile && (
            <div className={styles.selectedFile}>
              <span>Selected file: </span>
              <span className={styles.fileName}>{selectedFile.name}</span>
            </div>
          )}
        </div>

        {/* Convert button */}
        <div className={styles.actionSection}>
          <Button
            type="primary"
            size="large"
            loading={converting}
            disabled={!selectedFile}
            onClick={handleConvert}
            className={styles.convertButton}
          >
            {converting ? 'Converting...' : 'Start Conversion'}
          </Button>
        </div>

        {/* Result display area */}
        {convertResult.length > 0 && (
          <div className={styles.resultSection}>
            <h3>Conversion Result</h3>
            <div className={styles.resultContent}>
              <p className={styles.successText}>✅ Conversion successful! Generated files:</p>
              <div className={styles.fileList}>
                {convertResult.map((filePath, index) => (
                  <div key={index} className={styles.fileItem}>
                    <span className={styles.filePath}>{filePath}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}
      </div>
    </InnerLayout>
  );
};

export default DifyConverter;
