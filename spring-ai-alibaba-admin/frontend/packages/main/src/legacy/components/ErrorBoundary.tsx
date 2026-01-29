import React from 'react';
import { Result, Button } from 'antd';

interface ErrorBoundaryState {
  hasError: boolean;
  error?: Error;
  errorInfo?: React.ErrorInfo;
}

interface ErrorBoundaryProps {
  children: React.ReactNode;
  fallback?: React.ReactNode;
  onError?: (error: Error, errorInfo: React.ErrorInfo) => void;
}

class ErrorBoundary extends React.Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    this.setState({
      error,
      errorInfo,
    });

    // Call error callback
    if (this.props.onError) {
      this.props.onError(error, errorInfo);
    }

    // Log recording
    console.error('ErrorBoundary caught an error:', error, errorInfo);
  }

  handleRetry = () => {
    this.setState({ hasError: false, error: undefined, errorInfo: undefined });
  };

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <div className="min-h-[400px] flex items-center justify-center">
          <Result
            status="error"
            title="Page Error"
            subTitle={this.state.error?.message || 'Sorry, the page encountered an error'}
            extra={[
              <Button type="primary" key="retry" onClick={this.handleRetry}>
                Try Again
              </Button>,
              <Button key="home" onClick={() => window.location.href = '/'}>
                Return Home
              </Button>,
            ]}
          />
        </div>
      );
    }

    return this.props.children;
  }
}

export default ErrorBoundary;
