import IconFile from '@/components/Icon/IconFile';
import $i18n from '@/i18n';
import upload, { getPreviewUrl } from '@/request/upload';
import { getDestinationList } from '@/services/destination';
import { getSourceList } from '@/services/source';
import type { FileType } from '@/types/base';
import { Button, Form, IconFont, Input, message, Select } from '@spark-ai/design';
import { Flex, Radio, Tooltip, Upload } from 'antd';
import React, { useEffect, useRef, useState } from 'react';
import styles from './index.module.less';

interface StepOneProps {
  formRef: React.RefObject<any>;
  changeFormValue: (value: any) => void;
  formValue: any;
  setFileList: (fileList: any) => void;
  fileList: any;
  setUploadingCount: React.Dispatch<React.SetStateAction<number>>;
  uploadingCount: number;
}

const CRON_PRESETS = [
  { label: 'Every Hour', value: '0 0 * * * ?', description: 'Runs at the start of every hour' },
  { label: 'Every 6 Hours', value: '0 0 */6 * * ?', description: 'Runs every 6 hours' },
  { label: 'Daily at Midnight', value: '0 0 0 * * ?', description: 'Runs once a day at 00:00' },
  { label: 'Daily at 6 AM', value: '0 0 6 * * ?', description: 'Runs once a day at 06:00' },
  { label: 'Weekly (Sunday)', value: '0 0 0 ? * SUN', description: 'Runs every Sunday at midnight' },
  { label: 'Monthly (1st)', value: '0 0 0 1 * ?', description: 'Runs on the 1st of every month' },
  { label: 'Custom', value: 'custom', description: 'Enter a custom cron expression' },
];

export default function StepTwo({
  formRef,
  setFileList,
  fileList,
  setUploadingCount,
  uploadingCount,
  changeFormValue,
  formValue,
}: StepOneProps) {
  const isLimitReachedRef = useRef<boolean>(false);
  const [dataSourceType, setDataSourceType] = useState<'file' | 'source'>(
    formValue?.dataSourceType || 'file',
  );
  const [sources, setSources] = useState<{ label: string; value: string }[]>([]);
  const [destinations, setDestinations] = useState<{ label: string; value: string }[]>([]);
  const [cronMode, setCronMode] = useState<string>(
    formValue?.sync_cron && !CRON_PRESETS.find((p) => p.value === formValue.sync_cron)
      ? 'custom'
      : formValue?.sync_cron || '',
  );

  useEffect(() => {
    // Fetch sources
    getSourceList({ current: 1, size: 100 })
      .then((res) => {
        setSources(
          res.records
            .filter((s) => s.status >= 0)
            .map((s) => ({ label: s.name, value: s.source_id })),
        );
      })
      .catch(() => {});

    // Fetch destinations
    getDestinationList({ current: 1, size: 100 })
      .then((res) => {
        setDestinations(
          res.records
            .filter((d) => d.status >= 0)
            .map((d) => ({ label: `${d.name} (${d.provider_type})`, value: d.destination_id })),
        );
      })
      .catch(() => {});
  }, []);

  const handleDataSourceChange = (type: 'file' | 'source') => {
    setDataSourceType(type);
    changeFormValue({ dataSourceType: type });
  };

  const handleCronPresetChange = (value: string) => {
    setCronMode(value);
    if (value !== 'custom') {
      changeFormValue({ sync_cron: value });
    }
  };

  const renderFileIcon = (type: string) => {
    const normalizedType =
      type?.toUpperCase() === 'PPTX' ? 'PPT' : type?.toUpperCase();
    return (
      <IconFile
        type={normalizedType as FileType}
        className={styles['upload-dragger-format']}
      />
    );
  };

  return (
    <div className={styles['step-two']}>
      <Form layout="vertical" ref={formRef}>
        {/* Data Source Type Selector — hidden once "Source System" is committed */}
        {dataSourceType !== 'source' ? (
          <Form.Item
            label={$i18n.get({
              id: 'main.pages.Knowledge.components.StepTwo.dataSourceType',
              dm: 'Data Source',
            })}
          >
            <Radio.Group
              value={dataSourceType}
              onChange={(e) => handleDataSourceChange(e.target.value)}
              optionType="button"
              buttonStyle="solid"
            >
              <Radio.Button value="file">
                {$i18n.get({
                  id: 'main.pages.Knowledge.components.StepTwo.localFiles',
                  dm: 'Local Files',
                })}
              </Radio.Button>
              <Radio.Button value="source">
                {$i18n.get({
                  id: 'main.pages.Knowledge.components.StepTwo.sourceSystem',
                  dm: 'Source System',
                })}
              </Radio.Button>
            </Radio.Group>
          </Form.Item>
        ) : (
          <Form.Item
            label={$i18n.get({
              id: 'main.pages.Knowledge.components.StepTwo.dataSourceType',
              dm: 'Data Source',
            })}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <span style={{ fontWeight: 500 }}>
                {$i18n.get({
                  id: 'main.pages.Knowledge.components.StepTwo.sourceSystem',
                  dm: 'Source System',
                })}
              </span>
              <Button
                type="link"
                size="small"
                onClick={() => handleDataSourceChange('file')}
                style={{ padding: 0, height: 'auto' }}
              >
                {$i18n.get({
                  id: 'main.pages.Knowledge.components.StepTwo.switchToLocal',
                  dm: 'Switch to Local Files',
                })}
              </Button>
            </div>
          </Form.Item>
        )}

        {/* File Upload (existing) */}
        {dataSourceType === 'file' && (
          <Form.Item
            label={
              $i18n.get({
                id: 'main.pages.Knowledge.components.StepTwo.index.dataUpload',
                dm: 'Data Upload',
              }) + `(${fileList?.length}/50)`
            }
            valuePropName="upload"
          >
            <Upload.Dragger
              multiple
              disabled={fileList.length >= 50 || uploadingCount > 0}
              defaultFileList={fileList}
              accept=".pdf,.doc,.txt,.md,.ppt,.docx,.pptx"
              customRequest={(options: any) => {
                setUploadingCount((prev) => prev + 1);
                upload({
                  file: options.file,
                  category: 'document',
                  onProgress({ percent }) {
                    options.file.percent = percent;
                    options.onProgress?.({ percent });
                  },
                })
                  .then(async (res: any) => {
                    res.percent = 1;
                    options.file.url = await getPreviewUrl(res.path);
                    options.onSuccess?.(res);
                    setFileList((prevFileList: any) => [...prevFileList, res]);
                  })
                  .catch((err) => {
                    options.onError?.(err);
                  })
                  .finally(() => {
                    setUploadingCount((prev) => prev - 1);
                  });
              }}
              beforeUpload={(file, files) => {
                isLimitReachedRef.current = false;
                if (fileList.length + files.length > 50 || files.length > 50) {
                  if (!isLimitReachedRef.current) {
                    message.destroy();
                    message.error(
                      $i18n.get({
                        id: 'main.pages.Knowledge.components.StepTwo.index.maxUpload50Files',
                        dm: 'You can upload a maximum of 50 files',
                      }),
                    );
                    isLimitReachedRef.current = true;
                  }
                  return Upload.LIST_IGNORE;
                }
                const isSupportedFormat =
                  /\.(pdf|doc|txt|md|ppt|pptx|docx)$/i.test(file.name);
                const isFileSizeValid = file.size / 1024 / 1024 <= 100;
                const isImageSizeValid = /\.(png|jpg|jpeg|gif)$/i.test(file.name)
                  ? file.size / 1024 / 1024 <= 20
                  : true;

                if (!isSupportedFormat) {
                  message.error(
                    $i18n.get({
                      id: 'main.pages.Knowledge.Detail.components.UploadModal.index.supportedFormats',
                      dm: 'Only .pdf, .doc, .txt, .md, .ppt, .docx, .pptx format files are supported',
                    }),
                  );
                  return Upload.LIST_IGNORE;
                }
                if (!isFileSizeValid) {
                  message.error(
                    $i18n.get({
                      id: 'main.pages.Knowledge.components.StepTwo.index.singleFileSizeLimit100MB',
                      dm: 'Single file size cannot exceed 100MB',
                    }),
                  );
                  return Upload.LIST_IGNORE;
                }
                if (!isImageSizeValid) {
                  message.error(
                    $i18n.get({
                      id: 'main.pages.Knowledge.components.StepTwo.index.singleImageSizeLimit20MB',
                      dm: 'Single image size cannot exceed 20MB',
                    }),
                  );
                  return Upload.LIST_IGNORE;
                }
                return true;
              }}
              listType="picture"
              onRemove={(file) => {
                const newFileList = fileList.filter(
                  (item: any) => item.path !== file.response?.path,
                );
                setFileList(newFileList);
                if (newFileList.length < 50) {
                  isLimitReachedRef.current = false;
                }
              }}
              showUploadList={{
                removeIcon: (
                  <IconFont
                    type="spark-delete-line"
                    className={styles['remove-icon']}
                    size={20}
                  />
                ),
                showRemoveIcon: true,
              }}
              iconRender={(file) => {
                const { name } = file;
                const fileType = name?.split('.')?.pop()?.toUpperCase() || 'TXT';
                return renderFileIcon(fileType);
              }}
            >
              <Flex
                vertical
                align="center"
                justify="center"
                className={styles['upload-dragger']}
              >
                <IconFont
                  type="spark-upload-line"
                  className={styles['upload-dragger-icon']}
                />
                <div className={styles['upload-dragger-title']}>
                  {$i18n.get({
                    id: 'main.pages.Knowledge.components.StepTwo.index.clickOrDragToUploadLocalFile',
                    dm: 'Click or drag to upload local file',
                  })}
                </div>
                <div className={styles['upload-dragger-desc']}>
                  {$i18n.get({
                    id: 'main.pages.Knowledge.components.StepTwo.index.supportedFileFormats',
                    dm: 'Supports .pdf, .doc, .txt, .md, .ppt, .docx, .pptx format files,',
                  })}
                </div>
                <div className={styles['upload-dragger-desc']}>
                  {$i18n.get({
                    id: 'main.pages.Knowledge.components.StepTwo.index.singleFileMax100MBOr1000PagesSingleImageMax20MB',
                    dm: 'Single file max 100MB or 1000 pages, single image max 20MB',
                  })}
                </div>
              </Flex>
            </Upload.Dragger>
          </Form.Item>
        )}

        {/* Source System Selection */}
        {dataSourceType === 'source' && (
          <>
            <Form.Item
              label={$i18n.get({
                id: 'main.pages.Knowledge.components.StepTwo.selectSource',
                dm: 'Select Source',
              })}
              required
            >
              <Select
                value={formValue.source_id}
                onChange={(val) => changeFormValue({ source_id: val })}
                options={sources}
                placeholder={$i18n.get({
                  id: 'main.pages.Knowledge.components.StepTwo.selectSourcePlaceholder',
                  dm: 'Select a source system to pull data from',
                })}
                style={{ width: '100%' }}
                showSearch
                optionFilterProp="label"
              />
            </Form.Item>

            <Form.Item
              label={
                <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                  <span>
                    {$i18n.get({
                      id: 'main.pages.Knowledge.components.StepTwo.selectDestination',
                      dm: 'Select Destination',
                    })}
                  </span>
                  <Tooltip
                    title={$i18n.get({
                      id: 'main.pages.Knowledge.components.StepTwo.destinationTooltip',
                      dm: 'The OpenSearch destination where indexed data and RAG vectors will be stored',
                    })}
                  >
                    <IconFont type="spark-info-line" style={{ color: 'var(--ag-ant-color-text-tertiary)', cursor: 'pointer' }} />
                  </Tooltip>
                </div>
              }
              required
            >
              <Select
                value={formValue.destination_id}
                onChange={(val) => changeFormValue({ destination_id: val })}
                options={destinations}
                placeholder={$i18n.get({
                  id: 'main.pages.Knowledge.components.StepTwo.selectDestinationPlaceholder',
                  dm: 'Select a destination for storing indexed data',
                })}
                style={{ width: '100%' }}
                showSearch
                optionFilterProp="label"
              />
            </Form.Item>

            <Form.Item
              label={
                <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                  <span>
                    {$i18n.get({
                      id: 'main.pages.Knowledge.components.StepTwo.syncSchedule',
                      dm: 'Sync Schedule',
                    })}
                  </span>
                  <Tooltip
                    title={$i18n.get({
                      id: 'main.pages.Knowledge.components.StepTwo.syncScheduleTooltip',
                      dm: 'Set a cron schedule for automatic sync. Data from the source will be indexed to {knowledge}_index and processed for RAG in {knowledge}_rag.',
                    })}
                  >
                    <IconFont type="spark-info-line" style={{ color: 'var(--ag-ant-color-text-tertiary)', cursor: 'pointer' }} />
                  </Tooltip>
                </div>
              }
            >
              <Select
                value={cronMode}
                onChange={handleCronPresetChange}
                placeholder={$i18n.get({
                  id: 'main.pages.Knowledge.components.StepTwo.selectSchedule',
                  dm: 'Select a sync schedule (optional)',
                })}
                style={{ width: '100%', marginBottom: cronMode === 'custom' ? 8 : 0 }}
                allowClear
                onClear={() => {
                  setCronMode('');
                  changeFormValue({ sync_cron: '' });
                }}
              >
                {CRON_PRESETS.map((preset) => (
                  <Select.Option key={preset.value} value={preset.value}>
                    <div>
                      <span>{preset.label}</span>
                      {preset.value !== 'custom' && (
                        <span style={{ color: 'var(--ag-ant-color-text-tertiary)', marginLeft: 8, fontSize: 12 }}>
                          {preset.description}
                        </span>
                      )}
                    </div>
                  </Select.Option>
                ))}
              </Select>
              {cronMode === 'custom' && (
                <Input
                  value={formValue.sync_cron}
                  onChange={(e) => changeFormValue({ sync_cron: e.target.value })}
                  placeholder="0 0 */6 * * ?"
                  style={{ marginTop: 4 }}
                />
              )}
              <div style={{ fontSize: 12, color: 'var(--ag-ant-color-text-tertiary)', marginTop: 4 }}>
                {$i18n.get({
                  id: 'main.pages.Knowledge.components.StepTwo.cronHelp',
                  dm: 'Cron format: second minute hour day month weekday. Example: "0 0 */6 * * ?" = every 6 hours',
                })}
              </div>
            </Form.Item>
          </>
        )}
      </Form>
    </div>
  );
}
