import { message } from 'antd';
import { MockTool } from '../pages/prompts/prompt-detail/hooks/useFunctions';

/**
 * Common streaming prompt execution utility
 * Handles the shared logic between PlaygroundPage and PromptDetailPage
 */

export interface StreamingPromptConfig {
  promptId: string | number;
  content: string;
  parameterValues: Record<string, any>;
  selectedModel: string;
  modelParams: Record<string, string>;
  sessionId?: string;
  promptKey?: string;
  version?: string;
  mockTools?: MockTool[];
}

export interface StreamingPromptCallbacks {
  onUpdateChatHistory: (promptId: string | number, updater: (chatHistory: any[]) => any[]) => void;
  onUpdateSessionId: (promptId: string | number, sessionId: string) => void;
  onUpdateMetrics: (promptId: string | number, data: {
    usage: Record<string, any>;
    traceId: string;
  }) => void;
  formatTime: (timestamp: number) => string;
  replaceParameters: (content: string, parameterValues: Record<string, any>) => string;
}

export interface EventSourceRef {
  close: () => void;
}

/**
 * Execute streaming prompt with shared logic
 */
export const executeStreamingPrompt = async (
  config: StreamingPromptConfig,
  inputText: string,
  callbacks: StreamingPromptCallbacks,
  eventSourceRefs: Record<string | number, EventSourceRef>
): Promise<void> => {
  const {
    promptId,
    content,
    parameterValues,
    selectedModel,
    modelParams,
    sessionId: initialSessionId,
    promptKey = '',
    version = '1.0',
    mockTools = []
  } = config;

  const {
    onUpdateChatHistory,
    onUpdateSessionId,
    formatTime,
    replaceParameters,
  } = callbacks;

  // Replace parameters in content
  const processedContent = content;

  // Prepare model config
  const modelConfig = {
    modelId: selectedModel,
    ...config.modelParams
  };

  // Generate sessionId for first call
  let sessionId = initialSessionId;
  const isNewSession = !sessionId;

  if (!sessionId) {
    sessionId = ``;
  }

  try {
    // Create request parameters
    const requestParams = {
      sessionId: sessionId,
      promptKey,
      version,
      template: processedContent,
      variables: JSON.stringify(parameterValues),
      modelConfig: JSON.stringify(modelConfig),
      message: inputText,
      newSession: isNewSession,
      mockTools: mockTools
    };

    let currentMessage = {
      id: Date.now(),
      promptId,
      type: 'assistant', // Keep type aligned with UI expectations
      content: '',
      isLoading: true,
      timestamp: formatTime(Date.now()),
      modelParams,
      sessionId: sessionId,
      model: selectedModel
    };

    // Add loading message to chat history and update sessionId if newly generated
    onUpdateChatHistory(promptId, (chatHistory) => [...(chatHistory || []), currentMessage]);

    if (!initialSessionId) {
      onUpdateSessionId(promptId, sessionId);
    }

    // Use fetch for streaming POST request
    const response = await fetch('/api/prompt/run', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/x-ndjson'
      },
      body: JSON.stringify(requestParams)
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const reader = response.body?.getReader();
    if (!reader) {
      throw new Error('Unable to read response stream');
    }

    const decoder = new TextDecoder();
    let buffer = '';
    let updateTimeoutId: NodeJS.Timeout | null = null;

    // Store reader reference for cleanup
    eventSourceRefs[promptId] = { close: () => reader.cancel() };

    const updateChatHistory = (data = {}) => {
      onUpdateChatHistory(promptId, (chatHistory) =>
        chatHistory.map(msg =>
          msg.id === currentMessage.id ? {
            ...msg,
            content: currentMessage.content,
            isLoading: currentMessage.isLoading,
            ...data
          } : msg
        )
      );
    };

    const scheduleUpdate = () => {
      if (updateTimeoutId) {
        clearTimeout(updateTimeoutId);
      }
      // Throttle updates to avoid overly frequent UI refreshes
      updateTimeoutId = setTimeout(updateChatHistory, 50);
    };

    const readStream = async () => {
      try {
        while (true) {
          const { done, value } = await reader.read();

          if (done) {
            // Stream completed
            currentMessage.isLoading = false;
            // Clear pending updates and apply final update immediately
            if (updateTimeoutId) {
              clearTimeout(updateTimeoutId);
              updateTimeoutId = null;
            }
            updateChatHistory();
            delete eventSourceRefs[promptId];
            break;
          }

          // Process the chunk
          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split('\n');
          buffer = lines.pop() || ''; // Keep incomplete line in buffer

          for (const line of lines) {
            if (line.trim()) {
              try {
                const data = JSON.parse(line);

                if (data.type === 'session' || data.type === 'session_info') {
                  // Update session ID if backend returns a different one
                  const backendSessionId = data.sessionId;
                  if (backendSessionId && backendSessionId !== sessionId) {
                    sessionId = backendSessionId;
                    currentMessage.sessionId = backendSessionId;
                    onUpdateSessionId(promptId, backendSessionId);
                  }
                } else if (data.type === "metrics") {
                  updateChatHistory(data.metrics);
                } else if (data.type === 'content' || data.type === 'message') {
                  // Append content incrementally for streaming display
                  currentMessage.content += data.content || '';
                  // Use throttled updates to keep streaming smooth
                  scheduleUpdate();
                } else if (data.type === 'end') {
                  // Streaming finished
                  currentMessage.isLoading = false;
                  // Clear pending updates and apply final update immediately
                  if (updateTimeoutId) {
                    clearTimeout(updateTimeoutId);
                    updateTimeoutId = null;
                  }
                  updateChatHistory();
                  delete eventSourceRefs[promptId];
                  return;
                } else if (data.type === 'error') {
                  // Handle error
                  currentMessage.content = `Error: ${data.error || 'Unknown error'}`;
                  currentMessage.isLoading = false;
                  // Clear pending updates and apply error update immediately
                  if (updateTimeoutId) {
                    clearTimeout(updateTimeoutId);
                    updateTimeoutId = null;
                  }
                  updateChatHistory();
                  delete eventSourceRefs[promptId];
                  message.error(data.error || 'Request failed');
                  return;
                }
              } catch (parseError) {
                console.error('Error parsing stream data:', parseError, 'Line:', line);
              }
            }
          }
        }
      } catch (streamError) {
        console.error('Stream reading error:', streamError);
        currentMessage.content = 'Connection error, please try again later';
        currentMessage.isLoading = false;
        // Clear pending updates and apply error update immediately
        if (updateTimeoutId) {
          clearTimeout(updateTimeoutId);
          updateTimeoutId = null;
        }
        updateChatHistory();
        delete eventSourceRefs[promptId];
        message.error('Connection failed');
      }
    };

    readStream();

  } catch (error) {
    console.error('Run prompt error:', error);
    message.error('Request failed');

    // Update the loading message to show error
    onUpdateChatHistory(promptId, (chatHistory) =>
      chatHistory.map(msg =>
        msg.isLoading && msg.promptId === promptId
          ? { ...msg, content: 'Request failed, please try again later', isLoading: false }
          : msg
      )
    );

    // Clean up
    delete eventSourceRefs[promptId];
  }
};
