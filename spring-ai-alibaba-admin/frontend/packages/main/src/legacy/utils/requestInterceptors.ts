import { requestInterceptors } from './request';
import { notifyError, notifyWarning } from './notification';

// Global error handling interceptor
export const setupGlobalErrorHandling = () => {
  // Add global error handling interceptor
  requestInterceptors.use({
    responseError: (error) => {
      // Handle different status codes
      if (error.code === 401) {
        notifyWarning({
          message: 'Authentication failed',
          description: 'Please sign in again to continue',
          duration: 5,
        });

        // Optional: redirect to login page
        // window.location.href = '/login';
      } else if (error.code === 403) {
        notifyError({
          message: 'Access denied',
          description: 'You do not have permission to perform this action',
          duration: 5,
        });
      } else if (error.code === 404) {
        notifyError({
          message: 'Resource not found',
          description: 'The requested resource could not be located',
        });
      } else if (error.code >= 500) {
        notifyError({
          message: 'Internal server error',
          description: 'The server encountered an error. Please try again later',
          duration: 6,
        });
      } else if (error.code === 0 || !error.code) {
        // Network error
        notifyError({
          message: 'Network connection failed',
          description: 'Check your network connection and try again',
          duration: 6,
        });
      }

      // Re-throw so individual components can handle
      throw error;
    },

    request: async (config) => {
      // Add global request headers here if needed
      return config;
    },
  });
};

// Initialize global error handling
setupGlobalErrorHandling();
