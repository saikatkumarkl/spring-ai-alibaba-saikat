import { notification } from 'antd';

// Configure global notification styles
notification.config({
  placement: 'topRight',
  top: 50,
  duration: 4.5,
  rtl: false,
});

export interface NotificationOptions {
  message: string;
  description?: string;
  duration?: number;
  placement?: 'top' | 'topLeft' | 'topRight' | 'bottom' | 'bottomLeft' | 'bottomRight';
}

// Success notification
export const notifySuccess = (options: NotificationOptions) => {
  notification.success({
    message: options.message,
    description: options.description,
    duration: options.duration || 3,
    placement: options.placement || 'topRight',
  });
};

// Error notification
export const notifyError = (options: NotificationOptions) => {
  notification.error({
    message: options.message,
    description: options.description,
    duration: options.duration || 5,
    placement: options.placement || 'topRight',
  });
};

// Warning notification
export const notifyWarning = (options: NotificationOptions) => {
  notification.warning({
    message: options.message,
    description: options.description,
    duration: options.duration || 4,
    placement: options.placement || 'topRight',
  });
};

// Info notification
export const notifyInfo = (options: NotificationOptions) => {
  notification.info({
    message: options.message,
    description: options.description,
    duration: options.duration || 3,
    placement: options.placement || 'topRight',
  });
};

// API error handling
export const handleApiError = (error: any, context: string = 'Operation') => {
  let message = 'Operation failed';
  let description = 'Please try again later';

  if (error && typeof error === 'object') {
    // Handle different types of errors
    if (error.message) {
      message = `${context} failed`;
      description = error.message;
    } else if (error.code && error.code !== 200) {
      message = `${context} failed (error code: ${error.code})`;
      description = error.message || 'Server returned an error';
    } else if (typeof error === 'string') {
      message = `${context} failed`;
      description = error;
    }
  } else if (typeof error === 'string') {
    message = `${context} failed`;
    description = error;
  }

  notifyError({ message, description });
};

// Network error handling
export const handleNetworkError = (context: string = 'Operation') => {
  notifyError({
    message: `${context} failed`,
    description: 'Network connection error, please check your network and try again',
    duration: 6,
  });
};

// Form validation error handling
export const handleValidationError = (message: string, description?: string) => {
  notifyWarning({
    message: 'Input validation failed',
    description: description || message,
  });
};
