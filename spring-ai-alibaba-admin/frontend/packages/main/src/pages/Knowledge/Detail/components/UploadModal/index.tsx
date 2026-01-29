import IconFile from '@/components/Icon/IconFile';
import $i18n from '@/i18n';
import upload, { getPreviewUrl } from '@/request/upload';
import { createDocuments } from '@/services/knowledge';
import type { FileType } from '@/types/base';
import { ICreateDocumentParams } from '@/types/knowledge';
import { IconFont, Modal, message } from '@spark-ai/design';
import { Flex, Upload } from 'antd';
import { useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import styles from './index.module.less';

interface IUploadModalProps {
  onClose: () => void;
  refreshList: () => void;
}
export default function UploadModal(props: IUploadModalProps) {
  const { kb_id } = useParams<{ kb_id: string }>();

  const { onClose, refreshList } = props;
  interface FileItem {
    extension: string;
    name: string;
    path: string;
    size: number;
  }

  const [fileList, setFileList] = useState<FileItem[]>([]);
  const isLimitReachedRef = useRef(false);
  const [uploadingCount, setUploadingCount] = useState(0);

  const handleSubmit = () => {
    const params: ICreateDocumentParams = {
      type: 'file',
      files: fileList,
      kb_id: kb_id as string,
    };
    createDocuments(params).then(() => {
      message.success(
        $i18n.get({
          id: 'main.pages.Knowledge.Detail.components.UploadModal.index.fileImportSuccess',
          dm: 'File import successful',
        }),
      );
      onClose();
      refreshList();
    });
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
    <Modal
      title={$i18n.get({
        id: 'main.pages.Knowledge.Detail.components.UploadModal.index.uploadFile',
        dm: 'Upload File',
      })}
      open={true}
      onCancel={onClose}
      okText={$i18n.get({
        id: 'main.pages.Knowledge.Detail.components.UploadModal.index.confirm',
        dm: 'Confirm',
      })}
      onOk={handleSubmit}
      okButtonProps={{
        disabled: uploadingCount > 0 || fileList.length === 0,
      }}
    >
      <div className={styles['upload-modal-content']}>
        <div className={styles['upload-dragger-progress']}>
          {$i18n.get(
            {
              id: 'main.pages.Knowledge.Detail.components.UploadModal.index.uploadVar1Of50',
              dm: 'Upload ({var1}/50)',
            },
            { var1: fileList.length },
          )}
        </div>
        <Upload.Dragger
          multiple
          disabled={fileList.length >= 50 || uploadingCount > 0}
          accept=".pdf,.doc,.txt,.md,.ppt,.docx,.pptx"
          customRequest={(options: any) => {
            setUploadingCount((prev) => prev + 1);

            upload({
              file: options.file,
              category: 'document',
              onProgress({ percent }) {
                // @ts-ignore
                options.file.percent = percent;
                // Update upload progress
                options.onProgress?.({
                  percent,
                });
              },
            })
              .then(async (res) => {
                // @ts-ignore
                res.percent = 1;
                // @ts-ignore
                options.file.url = await getPreviewUrl(res.path); // Store preview URL in file object
                options.onSuccess?.(res);
                setFileList((prevFileList) => [...prevFileList, res]);
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
                    id: 'main.pages.Knowledge.Detail.components.UploadModal.index.maxUpload50Files',
                    dm: 'You can upload a maximum of 50 files',
                  }),
                );
                isLimitReachedRef.current = true;
              }
              return Upload.LIST_IGNORE;
            }

            const isSupportedFormat = /\.(pdf|doc|txt|md|ppt|docx|pptx)$/i.test(
              file.name,
            );
            const isFileSizeValid = file.size / 1024 / 1024 <= 100; // Single file max 100MB
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
                  id: 'main.pages.Knowledge.Detail.components.UploadModal.index.fileSizeExceeds100MB',
                  dm: 'Single file size cannot exceed 100MB',
                }),
              );
              return Upload.LIST_IGNORE;
            }

            if (!isImageSizeValid) {
              message.error(
                $i18n.get({
                  id: 'main.pages.Knowledge.Detail.components.UploadModal.index.imageSizeExceeds20MB',
                  dm: 'Single image size cannot exceed 20MB',
                }),
              );
              return Upload.LIST_IGNORE;
            }

            return true;
          }}
          maxCount={50}
          listType="picture"
          onRemove={(file) => {
            const newFileList = fileList.filter(
              (item: { path: string }) => item.path !== file.response?.path,
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
            const fileType = name?.split('.')?.pop()?.toUpperCase();
            return renderFileIcon(fileType || '');
          }}
        >
          <div className={styles['upload-dragger-wrapper']}>
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
                  id: 'main.pages.Knowledge.Detail.components.UploadModal.index.clickOrDragUploadLocalFile',
                  dm: 'Click or drag to upload local file',
                })}
              </div>
              <div className={styles['upload-dragger-desc']}>
                {$i18n.get({
                  id: 'main.pages.Knowledge.Detail.components.UploadModal.index.supportedFormatsAndMaxSize',
                  dm: 'Supports .pdf, .doc, .txt, .md, .ppt, .docx, .pptx format files,',
                })}
              </div>
              <div className={styles['upload-dragger-desc']}>
                {$i18n.get({
                  id: 'main.pages.Knowledge.Detail.components.UploadModal.index.fileSizeAndPageLimit',
                  dm: 'Single file max 100MB or 1000 pages, single image max 20MB',
                })}
              </div>
            </Flex>
          </div>
        </Upload.Dragger>
      </div>
    </Modal>
  );
}
