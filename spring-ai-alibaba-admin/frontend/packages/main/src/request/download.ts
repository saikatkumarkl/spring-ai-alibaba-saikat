import request, { baseURL, session } from './request';
import { UPLOAD_METHOD } from './upload';

export async function getPreviewUrl(filePath: string) {
  const upload_method = window.g_config.config.upload_method;

  if (upload_method === UPLOAD_METHOD.OSS) {
    const response = await request({
      url: `/console/v1/files/get-preview-url`,
      method: 'GET',
      params: {
        path: filePath,
      },
    });

    return response.data.data;
  }
  if (upload_method === UPLOAD_METHOD.FILE)
    return `${baseURL.get()}/console/v1/files/download?path=${encodeURIComponent(
      filePath,
    )}&preview=true&access_token=${session.get()}`;
}

/**
 * Download a file (triggers browser download with Content-Disposition: attachment)
 */
export async function downloadFile(filePath: string, fileName?: string) {
  const upload_method = window.g_config.config.upload_method;

  if (upload_method === UPLOAD_METHOD.OSS) {
    const response = await request({
      url: `/console/v1/files/get-preview-url`,
      method: 'GET',
      params: { path: filePath },
    });
    const url = response.data.data;
    const a = document.createElement('a');
    a.href = url;
    if (fileName) a.download = fileName;
    a.target = '_blank';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  } else {
    const url = `${baseURL.get()}/console/v1/files/download?path=${encodeURIComponent(
      filePath,
    )}&preview=false&access_token=${session.get()}`;
    const a = document.createElement('a');
    a.href = url;
    if (fileName) a.download = fileName;
    a.target = '_blank';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
  }
}
