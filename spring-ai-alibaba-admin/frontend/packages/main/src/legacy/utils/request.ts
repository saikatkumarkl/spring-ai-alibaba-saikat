// Interceptor interface
interface RequestInterceptor {
  request?: (config: RequestConfig) => RequestConfig | Promise<RequestConfig>;
  response?: <T>(response: ApiResponse<T>) => ApiResponse<T> | Promise<ApiResponse<T>>;
  requestError?: (error: any) => any;
  responseError?: (error: RequestError) => any;
}

// Request config interface
interface RequestConfig {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  params?: Record<string, any>;
  data?: Record<string, any> | any;
  headers?: Record<string, string>;
  timeout?: number;
  skipInterceptors?: boolean; // Whether to skip interceptors
}

// API response shape
interface ApiResponse<T = any> {
  code: number;
  message: string;
  data: T;
}

// Request error class
class RequestError extends Error {
  public code: number;
  public response?: any;

  constructor(message: string, code: number, response?: any) {
    super(message);
    this.name = 'RequestError';
    this.code = code;
    this.response = response;
  }
}

// Interceptor manager
class InterceptorManager {
  private interceptors: (RequestInterceptor | null)[] = [];

  // Add interceptor
  use(interceptor: RequestInterceptor): number {
    this.interceptors.push(interceptor);
    return this.interceptors.length - 1;
  }

  // Remove interceptor
  eject(id: number): void {
    if (this.interceptors[id]) {
      this.interceptors[id] = null;
    }
  }

  // Run request interceptors
  async processRequest(config: RequestConfig): Promise<RequestConfig> {
    let processedConfig = config;

    for (const interceptor of this.interceptors) {
      if (interceptor && interceptor.request) {
        try {
          processedConfig = await interceptor.request(processedConfig);
        } catch (error) {
          if (interceptor.requestError) {
            throw interceptor.requestError(error);
          }
          throw error;
        }
      }
    }

    return processedConfig;
  }

  // Run response interceptors
  async processResponse<T>(response: ApiResponse<T>): Promise<ApiResponse<T>> {
    let processedResponse = response;

    for (const interceptor of this.interceptors) {
      if (interceptor && interceptor.response) {
        try {
          processedResponse = await interceptor.response(processedResponse);
        } catch (error) {
          if (interceptor.responseError && error instanceof RequestError) {
            throw interceptor.responseError(error);
          }
          throw error;
        }
      }
    }

    return processedResponse;
  }

  // Run error interceptors
  async processError(error: RequestError): Promise<never> {
    for (const interceptor of this.interceptors) {
      if (interceptor && interceptor.responseError) {
        try {
          throw interceptor.responseError(error);
        } catch (processedError) {
          // If an interceptor returns a new error, keep throwing that
          error = processedError instanceof RequestError ? processedError : error;
        }
      }
    }
    throw error;
  }

  // Clear all interceptors
  clear(): void {
    this.interceptors = [];
  }
}

// Global interceptor manager
const interceptors = new InterceptorManager();

// Default interceptor - auto-add auth headers
interceptors.use({
  request: async (config: RequestConfig) => {
    // Add auth token here if present
    const token = localStorage.getItem('authToken');
    if (token) {
      config.headers = {
        ...config.headers,
        'Authorization': `Bearer ${token}`
      };
    }

    // Add common request headers
    config.headers = {
      'X-Client-Version': '1.0.0',
      'X-Request-ID': Math.random().toString(36).substring(2),
      ...config.headers,
    };

    return config;
  },

  responseError: (error: RequestError) => {
    // Handle 401 unauthorized errors
    if (error.code === 401) {
      console.warn('Authentication failed, redirecting to login...');
      // Optionally handle login redirect here
      localStorage.removeItem('authToken');
    }

    return error;
  }
});

// Convert params object to query string
function buildQueryString(params: Record<string, any>): string {
  const searchParams = new URLSearchParams();

  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null) {
      if (typeof value === 'object') {
        searchParams.append(key, JSON.stringify(value));
      } else {
        searchParams.append(key, String(value));
      }
    }
  });

  return searchParams.toString();
}

// Base URL configuration (optional)
let baseURL = '';

// Core request function
async function baseRequest<T = any>(
  url: string,
  config: RequestConfig = {}
): Promise<ApiResponse<T>> {
  let processedConfig = { ...config };
  const {
    method = 'GET',
    skipInterceptors = false,
    timeout = 10000
  } = processedConfig;

  try {
    // Apply request interceptors
    if (!skipInterceptors) {
      processedConfig = await interceptors.processRequest(processedConfig);
    }

    const {
      params,
      data,
      headers = {},
    } = processedConfig;

    // Build full URL
    let fullUrl = baseURL && !url.startsWith('http') ? baseURL + url : url;
    if (params && Object.keys(params).length > 0) {
      const queryString = buildQueryString(params);
      fullUrl += (fullUrl.includes('?') ? '&' : '?') + queryString;
    }

    // Prepare fetch config
    const fetchConfig: RequestInit = {
      method,
      headers: {
        'Content-Type': 'application/json',
        ...headers,
      },
    };

    // Add request body (for non-GET requests)
    if (method !== 'GET' && data) {
      if (typeof data === 'object') {
        fetchConfig.body = JSON.stringify(data);
      } else {
        fetchConfig.body = data;
      }
    }

    // Create timeout controller
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeout);
    fetchConfig.signal = controller.signal;

    console.log(`[REQUEST] ${method} ${fullUrl}`, {
      params,
      data,
      headers: fetchConfig.headers
    });

    // Send request
    const response = await fetch(fullUrl, fetchConfig);

    // Clear timeout
    clearTimeout(timeoutId);

    // Check HTTP status
    if (!response.ok) {
      const errorText = await response.text();
      console.error(`[REQUEST ERROR] ${method} ${fullUrl} - ${response.status}`, errorText);

      const requestError = new RequestError(
        `HTTP ${response.status}: ${response.statusText}`,
        response.status,
        errorText
      );

      // Process error interceptors
      if (!skipInterceptors) {
        await interceptors.processError(requestError);
      }

      throw requestError;
    }

    // Parse response
    let responseData = await response.json();

    console.log(`[RESPONSE] ${method} ${fullUrl}`, responseData);

    // Check business status code
    if (responseData.code !== undefined && responseData.code !== 200 && responseData.code !== 0) {
      const requestError = new RequestError(
        responseData.message || 'Request failed',
        responseData.code,
        responseData
      );

      // Process error interceptors
      if (!skipInterceptors) {
        await interceptors.processError(requestError);
      }

      throw requestError;
    }

    // Process response interceptors
    if (!skipInterceptors) {
      responseData = await interceptors.processResponse(responseData);
    }

    return responseData;

  } catch (error: unknown) {
    if (error instanceof RequestError) {
      throw error;
    }

    if (error instanceof Error && error.name === 'AbortError') {
      console.error(`[REQUEST TIMEOUT] ${method} ${url}`);
      const timeoutError = new RequestError('Request timed out', 408);

      // Process error interceptors
      if (!skipInterceptors) {
        await interceptors.processError(timeoutError);
      }

      throw timeoutError;
    }

    console.error(`[REQUEST ERROR] ${method} ${url}`, error);

    // Network error or other errors
    const networkError = new RequestError(
      error instanceof Error ? error.message : 'Network request failed',
      0,
      error
    );

    // Process error interceptors
    if (!skipInterceptors) {
      await interceptors.processError(networkError);
    }

    throw networkError;
  }
}

// Primary request function
export const request = baseRequest;

// Convenience helpers
export const get = <T = any>(url: string, params?: Record<string, any>, config?: Omit<RequestConfig, 'method' | 'params'>) => {
  return request<T>(url, { ...config, method: 'GET', params });
};

export const post = <T = any>(url: string, data?: any, config?: Omit<RequestConfig, 'method' | 'data'>) => {
  return request<T>(url, { ...config, method: 'POST', data });
};

export const put = <T = any>(url: string, data?: any, config?: Omit<RequestConfig, 'method' | 'data'>) => {
  return request<T>(url, { ...config, method: 'PUT', data });
};

export const del = <T = any>(url: string, data?: any, config?: Omit<RequestConfig, 'method' | 'data'>) => {
  return request<T>(url, { ...config, method: 'DELETE', data });
};

// Interceptor management interface
export const requestInterceptors = {
  // Add interceptor
  use: (interceptor: RequestInterceptor) => interceptors.use(interceptor),

  // Remove interceptor
  eject: (id: number) => interceptors.eject(id),

  // Clear all interceptors
  clear: () => interceptors.clear()
};

// Base URL helpers
export const setBaseURL = (url: string) => {
  baseURL = url;
};

export const getBaseURL = () => baseURL;

// Default export
export default {
  request,
  get,
  post,
  put,
  delete: del,
  RequestError,
  interceptors: requestInterceptors,
  setBaseURL,
  getBaseURL
};

/*
Usage example:

// Basic usage
import { request, get, post } from './utils/request';

// GET request
const prompts = await get('/api/prompts', { pageSize: 10 });

// POST request
const newPrompt = await post('/api/prompt', {
  promptKey: 'test',
  promptDescription: 'Test prompt'
});

// Use interceptors
import { requestInterceptors } from './utils/request';

// Add request interceptor
const interceptorId = requestInterceptors.use({
  request: (config) => {
    config.headers = { ...config.headers, 'Custom-Header': 'value' };
    return config;
  },
  response: (response) => {
    console.log('Response received:', response);
    return response;
  }
});

// Remove interceptor
requestInterceptors.eject(interceptorId);

// Set base URL
import { setBaseURL } from './utils/request';
setBaseURL('https://api.example.com');
*/
