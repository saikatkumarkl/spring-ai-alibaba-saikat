import IconFile from '@/components/Icon/IconFile';
import $i18n from '@/i18n';
import upload, { getPreviewUrl } from '@/request/upload';
import type { FileType } from '@/types/base';
import { Form, IconFont, message } from '@spark-ai/design';
import { Flex, Upload } from 'antd';
import React, { useRef } from 'react';
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

export default function StepTwo({
  formRef,
  setFileList,
  fileList,
  setUploadingCount,
  uploadingCount,
}: StepOneProps) {
  const isLimitReachedRef = useRef<boolean>(false);

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
              setUploadingCount((prev) => prev + 1); // Increase the number of files being uploaded
              upload({
                file: options.file,
                category: 'document',
                onProgress({ percent }) {
                  options.file.percent = percent;
                  // Update upload progress
                  options.onProgress?.({
                    percent,
                  });
                },
              })
                .then(async (res: any) => {
                  // @ts-ignore
                  res.percent = 1;
                  options.file.url = await getPreviewUrl(res.path); // Store the preview URL in the file object
                  options.onSuccess?.(res);
                  setFileList((prevFileList: any) => [...prevFileList, res]);
                })
                .catch((err) => {
                  options.onError?.(err);
                })
                .finally(() => {
                  setUploadingCount((prev) => prev - 1); // Decrease the number of files being uploaded
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
              const isFileSizeValid = file.size / 1024 / 1024 <= 100; // Maximum file size is 100MB
              const isImageSizeValid = /\.(png|jpg|jpeg|gif)$/i.test(file.name)
                ? file.size / 1024 / 1024 <= 20
                : true; // Maximum image size is 20MB

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
      </Form>
    </div>
  );
}
