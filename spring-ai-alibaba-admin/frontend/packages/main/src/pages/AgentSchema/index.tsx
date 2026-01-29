import InnerLayout from '@/components/InnerLayout';
import $i18n from '@/i18n';
import { Button, Form, Input, Select, Card, message, Space, Typography, List, Checkbox, Tooltip, Divider } from 'antd';
import { PlusOutlined, CopyOutlined, SaveOutlined, CloseOutlined, DownOutlined, UpOutlined, DeleteOutlined } from '@ant-design/icons';
import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import styles from './index.module.less';
import { AgentSchemaService } from '@/services/agentSchema';
import { ToolService } from '@/services/tool';
import { listModels, getModelSelector } from '@/services/modelService';
import { IAgentSchema, AgentType as IAgentType } from '@/types/agentSchema';
import { ITool } from '@/types/tool';
import { IModel } from '@/types/modelService';
import { session } from '@/request/session';

const { Title, Text } = Typography;
const { TextArea } = Input;
const { Option } = Select;

// Agent type definition
type LocalAgentType = 'ReactAgent' | 'ParallelAgent' | 'SequentialAgent' | 'LLMRoutingAgent' | 'LoopAgent';

// State strategy type
type StateStrategy = 'replace' | 'append';

// Model configuration
interface ModelConfig {
  name: string;
  url: string;
  'api-key': string;
}

// Common handle configuration
interface CommonHandleConfig {
  chat_options?: Record<string, any>;
  compile_config?: Record<string, any>;
  state?: {
    strategies: Record<string, StateStrategy>;
  };
}

// ReactAgent specific configuration
interface ReactAgentHandleConfig extends CommonHandleConfig {
  model?: ModelConfig;
  max_iterations?: number;
  tools?: string[];
  resolver?: string;
  pre_llm_hook?: string;
  post_llm_hook?: string;
  pre_tool_hook?: string;
  post_tool_hook?: string;
  should_continue_func?: string;
}

// ParallelAgent specific configuration
interface ParallelAgentHandleConfig extends CommonHandleConfig {
  merge_strategy?: string;
  max_concurrency?: number;
  separator?: string;
}

// LoopAgent specific configuration
interface LoopAgentHandleConfig extends CommonHandleConfig {
  loop_mode: 'COUNT' | 'CONDITION';
  loop_count?: number;
}

// Handle configuration union type
type HandleConfig =
  | ReactAgentHandleConfig
  | ParallelAgentHandleConfig
  | LoopAgentHandleConfig
  | CommonHandleConfig;

// Sub-agent reference method
interface SubAgentDirectRef {
  agent: AgentSchema;
}

interface SubAgentFileRef {
  config_path: string;
}

interface SubAgentCodeRef {
  code: string;
}

type SubAgentRef = SubAgentDirectRef | SubAgentFileRef | SubAgentCodeRef;

// Main Agent Schema interface
interface AgentSchema {
  type: LocalAgentType;
  name: string;
  description: string;
  instruction: string;
  input_keys: string[];
  output_key: string;
  handle: HandleConfig;
  sub_agents?: SubAgentRef[];
}

// Form data interface
interface AgentSchemaForm {
  agentType: LocalAgentType;
  name: string;
  description: string;
  instruction: string;
  inputKey: string;
  outputKey: string;
  model: string;
  handle: any;
  subAgents?: string[];
  tools?: string[];
}

// Saved agent data interface
interface SavedAgent {
  id: string;
  schema: AgentSchema;
}

const AgentSchemaCreator: React.FC = () => {
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [selectedAgentId, setSelectedAgentId] = useState<number | null>(null);
  const [yamlContent, setYamlContent] = useState<string>('');
  const [savedAgents, setSavedAgents] = useState<IAgentSchema[]>([]);
  const [availableTools, setAvailableTools] = useState<ITool[]>([]);
  const [availableModels, setAvailableModels] = useState<IModel[]>([]);
  const [agentsLoading, setAgentsLoading] = useState(false);
  const [toolsLoading, setToolsLoading] = useState(false);
  const [modelsLoading, setModelsLoading] = useState(false);
  const [subAgentsExpanded, setSubAgentsExpanded] = useState(false);
  const [toolsExpanded, setToolsExpanded] = useState(false);
  const [nameError, setNameError] = useState<string | null>(null);

  // Debounced name validation
  const nameCheckTimeoutRef = useRef<NodeJS.Timeout | null>(null);

  // Debounced duplicate name check
  const checkNameDuplicate = useCallback((name: string) => {
    if (!name || savedAgents.length === 0) {
      setNameError(null);
      return;
    }

    // Clear previous timer
    if (nameCheckTimeoutRef.current) {
      clearTimeout(nameCheckTimeoutRef.current);
    }

    // Set new timer (500ms debounce)
    nameCheckTimeoutRef.current = setTimeout(() => {
      const isDuplicate = savedAgents.some(agent => {
        const currentAgentId = selectedAgentId?.toString();
        const existingAgentId = agent.id?.toString();
        return agent.name === name && existingAgentId !== currentAgentId;
      });

      if (isDuplicate) {
        setNameError('Agent name already exists, please use a different name');
      } else {
        setNameError(null);
      }
    }, 500);
  }, [savedAgents, selectedAgentId]);

  // Fetch saved agent list
  const fetchSavedAgents = async () => {
    setAgentsLoading(true);
    try {
      const response = await AgentSchemaService.getAgentSchemas();

      let agents: any[] = [];

      // Check response format - backend returns Result<List<AgentSchemaEntity>>
      if (Array.isArray(response)) {
        // If it's directly an array, use it directly
        agents = response;
      } else if (response && typeof response === 'object') {
        // Check if it's a wrapped response object
        if ((response as any).data && Array.isArray((response as any).data)) {
          // Standard Result<T> format
          agents = (response as any).data;
        } else if ((response as any).records && Array.isArray((response as any).records)) {
          // Paginated response format
          agents = (response as any).records;
        } else {
        }
      } else {
      }


      setSavedAgents(agents);
    } catch (error) {
      message.error('Failed to fetch agent list');
      console.error('Failed to fetch agents:', error);
      setSavedAgents([]); // Set to empty array on error
    } finally {
      setAgentsLoading(false);
    }
  };

  // Fetch available tools list
  const fetchAvailableTools = async () => {
    setToolsLoading(true);
    try {
      const tools = await ToolService.getTools();
      // Ensure tools is an array
      if (Array.isArray(tools)) {
        setAvailableTools(tools);
      } else {
        setAvailableTools([]);
      }
    } catch (error) {
      message.error('Failed to get tool list');
      setAvailableTools([]); // Set to empty array on error
    } finally {
      setToolsLoading(false);
    }
  };

  // Fetch available models list
  const fetchAvailableModels = async () => {
    setModelsLoading(true);
    try {
      // Use getModelSelector to get models from all enabled providers
      const modelSelectorData = await getModelSelector('llm');


      // Extract all models
      const allModels: IModel[] = [];
      if (modelSelectorData.data) {
        modelSelectorData.data.forEach(providerGroup => {
          if (providerGroup.models) {
            allModels.push(...providerGroup.models);
          }
        });
      }

      setAvailableModels(allModels);
    } catch (error) {
      message.error('Failed to get model list');
      setAvailableModels([]);
    } finally {
      setModelsLoading(false);
    }
  };

  // Initialize and load data
  useEffect(() => {
    fetchSavedAgents();
    fetchAvailableTools();
    fetchAvailableModels();
  }, []);

  // When savedAgents finishes loading, reprocess the subAgents of the currently selected agent
  useEffect(() => {
    if (selectedAgentId && savedAgents.length > 0) {
      const currentAgent = savedAgents.find(agent => agent.id === selectedAgentId);
      if (currentAgent && currentAgent.subAgents) {
        try {
          const subAgentsData = JSON.parse(currentAgent.subAgents);
          if (Array.isArray(subAgentsData)) {
            const subAgentsNames = subAgentsData.map(subAgent => {
              // If in { agent: { id, name } } format, prioritize name
              if (subAgent.agent) {
                if (subAgent.agent.name) {
                  return subAgent.agent.name;
                } else if (subAgent.agent.id) {
                  // If only has ID, find corresponding name
                  const foundAgent = savedAgents.find(sa => sa.id === subAgent.agent.id);
                  return foundAgent ? foundAgent.name : subAgent.agent.id.toString();
                }
              }
              return subAgent.toString();
            }).filter(name => name && name !== 'undefined' && name !== 'null');

            if (subAgentsNames.length > 0) {
              form.setFieldsValue({ subAgents: subAgentsNames });
            }
          }
        } catch (error) {
        }
      }
    }
  }, [savedAgents, selectedAgentId, form]);

  // Agent type options
  const agentTypeOptions = [
    { label: 'React Agent', value: 'ReactAgent' },
    { label: 'Parallel Agent', value: 'ParallelAgent' },
    { label: 'Sequential Agent', value: 'SequentialAgent' },
    { label: 'LLM Routing Agent', value: 'LLMRoutingAgent' },
    { label: 'Loop Agent', value: 'LoopAgent' },
  ];

  // State strategy options
  const stateStrategyOptions = [
    { label: 'Replace', value: 'replace' },
    { label: 'Append', value: 'append' },
  ];

  // Loop mode options
  const loopModeOptions = [
    { label: 'Count-based', value: 'COUNT' },
    { label: 'Condition-based', value: 'CONDITION' },
  ];

  // Sub-agent reference type options
  const subAgentRefTypeOptions = [
    { label: 'Direct Embed', value: 'direct' },
    { label: 'File Reference', value: 'file' },
    { label: 'Code Reference', value: 'code' },
  ];

  // Tool options - fetched from API
  const toolOptions = Array.isArray(availableTools) ? availableTools.map(tool => ({
    label: tool.name,
    value: tool.toolId || tool.id?.toString() || tool.name,
  })) : [];

  // Sub-agent options - fetched from saved agents, filter out currently selected agent
  const subAgentOptions = Array.isArray(savedAgents) ? savedAgents.filter(agent => {
    // Filter out currently selected agent to prevent self-reference
    return agent.id !== selectedAgentId;
  }).map(agent => ({
    label: agent.name,
    value: agent.name, // Use name as value
    id: agent.id?.toString() // Save ID for later use
  })) : [];

  // Generate YAML content
  const generateYaml = async (values: AgentSchemaForm): Promise<string> => {

    const instruction = values.instruction || '';
    const inputKeys = values.inputKey ? [values.inputKey] : ['input'];
    const subAgents = values.subAgents || [];

    // Generate handle configuration
    const generateHandleYaml = (handle: any, formValues: AgentSchemaForm): string => {
      if (!handle) {
        return 'handle:\n  state:\n    strategies:\n      input: "replace"\n      output: "replace"\n';
      }

      let yaml = 'handle:\n';

      // Common configuration
      if (handle.chat_options && Object.keys(handle.chat_options).length > 0) {
        yaml += `  chat_options:\n`;
        Object.entries(handle.chat_options).forEach(([key, value]) => {
          yaml += `    ${key}: ${typeof value === 'string' ? `"${value}"` : value}\n`;
        });
      }

      if (handle.compile_config && Object.keys(handle.compile_config).length > 0) {
        yaml += `  compile_config:\n`;
        Object.entries(handle.compile_config).forEach(([key, value]) => {
          yaml += `    ${key}: ${typeof value === 'string' ? `"${value}"` : value}\n`;
        });
      }

      if (handle.state?.strategies && Object.keys(handle.state.strategies).length > 0) {
        yaml += `  state:\n`;
        yaml += `    strategies:\n`;
        Object.entries(handle.state.strategies).forEach(([key, strategy]) => {
          yaml += `      ${key}: "${strategy}"\n`;
        });
      }

      // ReactAgent specific configuration - prioritize model value from form, fallback to model in handle
      const modelName = formValues.model || (handle.model ? handle.model.name : null);

      // If there's model info, generate model configuration
      if (modelName || handle.model) {
        const finalModelName = modelName || handle.model?.name || 'qwen2.5-72b-instruct';

        yaml += `  model:\n`;
        yaml += `    name: "${finalModelName}"\n`;

        // If handle has complete model config, prioritize handle config
        if (handle.model && handle.model.url) {
          yaml += `    url: "${handle.model.url}"\n`;
          yaml += `    api-key: "${handle.model['api-key'] || 'your-api-key'}"\n`;
        } else {
          // Try to find selected model info from dynamic model list
          const selectedModel = availableModels.find(m =>
            m.model_id === finalModelName ||
            m.name === finalModelName ||
            m.model_id === formValues.model ||
            m.name === formValues.model
          );
          if (selectedModel) {
            // Set different default URLs based on model provider
            const defaultUrl = selectedModel.provider === 'Tongyi'
              ? 'https://dashscope.aliyuncs.com/api/v1'
              : selectedModel.provider === 'OpenAI'
              ? 'https://api.openai.com/v1'
              : 'https://api.example.com/v1';
            yaml += `    url: "${defaultUrl}"\n`;
            yaml += `    api-key: "your-api-key"\n`;
          } else {
            // Use URL from handle, or default value
            const defaultUrl = handle.model?.url || 'https://api.example.com/v1';
            const defaultApiKey = handle.model?.['api-key'] || 'your-api-key';
            yaml += `    url: "${defaultUrl}"\n`;
            yaml += `    api-key: "${defaultApiKey}"\n`;
          }
        }
      }

      if ('max_iterations' in handle && handle.max_iterations) {
        yaml += `  max_iterations: ${handle.max_iterations}\n`;
      }

      // Tool configuration - prioritize tools value from form
      const toolsList = formValues.tools || handle.tools || [];
      if (toolsList.length > 0) {
        yaml += `  tools:\n`;
        toolsList.forEach((tool: string) => {
          yaml += `    - "${tool}"\n`;
        });
      }

      // ParallelAgent specific configuration
      if ('merge_strategy' in handle && handle.merge_strategy) {
        yaml += `  merge_strategy: "${handle.merge_strategy}"\n`;
      }

      if ('max_concurrency' in handle && handle.max_concurrency) {
        yaml += `  max_concurrency: ${handle.max_concurrency}\n`;
      }

      if ('separator' in handle && handle.separator) {
        yaml += `  separator: "${handle.separator}"\n`;
      }

      // LoopAgent specific configuration
      if ('loop_mode' in handle && handle.loop_mode) {
        yaml += `  loop_mode: "${handle.loop_mode}"\n`;
      }

      if ('loop_count' in handle && handle.loop_count) {
        yaml += `  loop_count: ${handle.loop_count}\n`;
      }

      // Other hook function configurations
      const hookFields = ['resolver', 'pre_llm_hook', 'post_llm_hook', 'pre_tool_hook', 'post_tool_hook', 'should_continue_func'];
      hookFields.forEach(field => {
        if (field in handle && handle[field]) {
          yaml += `  ${field}: "${handle[field]}"\n`;
        }
      });

      return yaml;
    };

    // Generate sub-agent configuration
    const generateSubAgentsYaml = (subAgents: string[]): string => {
      if (!subAgents || !Array.isArray(subAgents) || subAgents.length === 0) return '';

      // Filter out invalid names
      const validAgentNames = subAgents.filter(name =>
        name &&
        typeof name === 'string' &&
        name.trim() !== '' &&
        name !== 'undefined' &&
        name !== 'null'
      );

      if (validAgentNames.length === 0) return '';

      let yaml = 'sub_agents:\n';

      validAgentNames.forEach(agentName => {
        // Use name directly, as the form now stores names
        yaml += `  - agent: ${agentName.trim()}\n`;
      });

      return yaml;
    };

    const handleYaml = values.handle ? generateHandleYaml(values.handle, values) : 'handle:\n  state:\n    strategies:\n      input: "replace"\n      output: "replace"\n';
    const subAgentsYaml = subAgents.length > 0 ? generateSubAgentsYaml(subAgents) : '';
    const yaml = `agent:\n  type: "${values.agentType || 'ReactAgent'}"\n  name: "${values.name || ''}"\n  description: "${values.description || ''}"\n  instruction: |\n    ${instruction.replace(/\n/g, '\n    ')}\n  input_keys:\n${inputKeys.length > 0 ? inputKeys.map(key => `    - "${key}"`).join('\n') : '    - "input"'}\n  output_key: "${values.outputKey || 'output'}"\n${handleYaml}${subAgentsYaml}`;

    return yaml;
  };

  // Listen to form changes and update YAML in real-time
  useEffect(() => {
    const updateYaml = async () => {
      const subscription = form.getFieldsValue() as AgentSchemaForm;
      if (subscription.name) {
        const yaml = await generateYaml(subscription);
        setYamlContent(yaml);
      }
    };
    updateYaml();
  }, [form, selectedAgentId, availableModels]);

  // Select agent
  const handleSelectAgent = async (agent: IAgentSchema) => {

    setSelectedAgentId(agent.id || null);

    // Convert backend data to frontend form format
    let subAgentsNames: string[] = [];

    // Process subAgents synchronously to ensure conversion is complete when setting form values
    if (agent.subAgents) {

      try {
        let subAgentsData;

        // Check if subAgents is already an object or array
        if (typeof agent.subAgents === 'string') {
          // If it's a JSON string, parse it
          subAgentsData = JSON.parse(agent.subAgents);
        } else if (Array.isArray(agent.subAgents)) {
          // If it's already an array, use it directly
          subAgentsData = agent.subAgents;
        } else {
          subAgentsData = [];
        }


        if (Array.isArray(subAgentsData)) {
          subAgentsNames = subAgentsData.map(subAgent => {
            // If in { agent: { id, name } } format, prioritize name
            if (subAgent.agent) {
              if (subAgent.agent.name) {
                return subAgent.agent.name;
              } else if (subAgent.agent.id && savedAgents.length > 0) {
                // If has ID and savedAgents is loaded, find corresponding name
                const foundAgent = savedAgents.find(sa => sa.id === subAgent.agent.id);
                return foundAgent ? foundAgent.name : subAgent.agent.id.toString();
              }
            }
            // If it's directly a name, return it
            return subAgent.toString();
          }).filter(name => name && name !== 'undefined' && name !== 'null');
        }

      } catch (error) {
      }
    } else {
    }

    // Ensure subAgents name array is valid

    // If subAgents parsing failed and savedAgents isn't fully loaded, set a callback to reprocess
    const needsRetry = subAgentsNames.length === 0 && agent.subAgents && savedAgents.length === 0;
    if (needsRetry) {
    }

    // Parse handle configuration and extract model info
    let modelName = 'qwen2.5-72b-instruct'; // Default model
    let tools: string[] = [];

    try {
      const handleData = JSON.parse(agent.handle || '{}');

      if (handleData.model && handleData.model.name) {
        modelName = handleData.model.name;
        // Check if model exists in current model list, if not, add it to the list
        const modelExists = availableModels.some(m => m.model_id === modelName || m.name === modelName);
        if (!modelExists && modelName) {
          // If model is not in current list, add a temporary model item
          const tempModel: IModel = {
            model_id: modelName,
            name: modelName,
            provider: 'unknown',
            type: 'llm'
          };
          setAvailableModels(prev => [...prev, tempModel]);
        }
      } else {
      }
      if (handleData.tools && Array.isArray(handleData.tools)) {
        tools = handleData.tools;
      }
    } catch (error) {
    }

    // Build complete handle configuration, ensuring it includes model info
    let parsedHandle: any = {};
    try {
      parsedHandle = JSON.parse(agent.handle || '{}');
    } catch (error) {
    }

    const completeHandle = {
      ...parsedHandle,
      state: parsedHandle.state || {
        strategies: {
          input: 'replace',
          output: 'replace'
        }
      },
      model: {
        name: modelName,
        url: parsedHandle.model?.url || 'https://api.example.com/v1',
        'api-key': parsedHandle.model?.['api-key'] || 'your-api-key'
      },
      tools: tools || []
    };

    // Parse inputKeys
    let inputKey = 'input';
    if (agent.inputKeys) {
      try {
        let inputKeysArray: string[];
        if (typeof agent.inputKeys === 'string') {
          // If it's a JSON string, parse it
          inputKeysArray = JSON.parse(agent.inputKeys);
        } else if (Array.isArray(agent.inputKeys)) {
          // If it's already an array, use it directly
          inputKeysArray = agent.inputKeys;
        } else {
          inputKeysArray = ['input'];
        }

        if (Array.isArray(inputKeysArray) && inputKeysArray.length > 0) {
          inputKey = inputKeysArray[0];
        }
      } catch (error) {
        // Use default value if parsing fails
      }
    }

    const formData = {
      name: agent.name,
      description: agent.description || '',
      agentType: agent.type,
      instruction: agent.instruction,
      inputKey: inputKey,
      outputKey: agent.outputKey || 'output',
      model: modelName,
      handle: completeHandle,
      subAgents: subAgentsNames,
      tools: tools,
    };


    // Set form values
    form.setFieldsValue(formData);

    // Wait for form update to complete before generating YAML, ensuring subAgents values are correctly set
    setTimeout(async () => {
      try {
        // Re-fetch form values to ensure they include latest subAgents and complete handle
        const updatedFormValues = form.getFieldsValue();

        // Ensure handle includes complete model info
        if (updatedFormValues.handle && !updatedFormValues.handle.model) {
          updatedFormValues.handle.model = {
            name: updatedFormValues.model || modelName,
            url: 'https://api.example.com/v1',
            'api-key': 'your-api-key'
          };
        }

        // Ensure subAgents data exists
        if (!updatedFormValues.subAgents || updatedFormValues.subAgents.length === 0) {
          // If form doesn't have subAgents but original data has them, try to restore
          if (subAgentsNames && subAgentsNames.length > 0) {
            updatedFormValues.subAgents = subAgentsNames;
          }
        }

        const yaml = await generateYaml(updatedFormValues as AgentSchemaForm);
        setYamlContent(yaml);
      } catch (error) {
      }
    }, 100); // Allow time for form update
  };

  // Create new agent
  const handleNewAgent = () => {
    setSelectedAgentId(null);
    form.resetFields();
    setYamlContent('');
  };

  // Delete agent
  const handleDeleteAgent = async (agent: IAgentSchema, e: React.MouseEvent) => {
    e.stopPropagation(); // Prevent event bubbling to avoid triggering selection

    if (!agent.id) {
      message.error('Unable to delete: Invalid agent ID');
      return;
    }

    // Show confirmation dialog
    const confirmed = window.confirm(`Are you sure you want to delete agent "${agent.name}"? This action cannot be undone.`);

    if (!confirmed) {
      return;
    }

    try {
      await AgentSchemaService.deleteAgentSchema(agent.id);

      message.success(`Agent "${agent.name}" deleted successfully`);

      // If deleted agent is the currently selected one, clear the form
      if (selectedAgentId === agent.id) {
        setSelectedAgentId(null);
        form.resetFields();
        setYamlContent('');
      }

      // Reload agent list
      await fetchSavedAgents();

    } catch (error) {
      message.error(`Failed to delete agent "${agent.name}"`);
    }
  };

  // Save agent
  const handleSaveAgent = async () => {
    setLoading(true);
    try {
      const rawValues = form.getFieldsValue();

      // Ensure all required fields have values
      const values: AgentSchemaForm = {
        agentType: rawValues.agentType || 'ReactAgent',
        name: rawValues.name || '',
        description: rawValues.description || '',
        instruction: rawValues.instruction || '',
        inputKey: rawValues.inputKey || 'input',
        outputKey: rawValues.outputKey || 'output',
        model: rawValues.model || 'qwen2.5-72b-instruct',
        handle: rawValues.handle || {
          state: {
            strategies: {
              input: 'replace',
              output: 'replace'
            }
          }
        },
        subAgents: rawValues.subAgents || [],
        tools: rawValues.tools || []
      };

      // Manually validate required fields
      if (!values.name) {
        message.error('Please enter agent name');
        return;
      }
      if (!values.instruction) {
        message.error('Please enter system instructions');
        return;
      }

      // Validate agentName duplication

      // Use already loaded savedAgents for validation to avoid duplicate API calls
      if (savedAgents.length > 0) {

        const isDuplicate = savedAgents.some(agent => {
          // Ensure type matching: convert all IDs to strings for comparison
          const currentAgentId = selectedAgentId?.toString();
          const existingAgentId = agent.id?.toString();


          return agent.name === values.name && existingAgentId !== currentAgentId;
        });


        if (isDuplicate) {
          message.error('Agent name already exists, please use a different name');
          setLoading(false); // Reset loading state
          return;
        }
      } else {
        // If agent list isn't loaded, temporarily skip frontend validation and rely on backend validation
      }

      // Check for self-reference
      if (values.subAgents && values.subAgents.length > 0) {
        const currentAgentName = values.name;
        if (values.subAgents.includes(currentAgentName)) {
          message.error('Cannot set current agent as its own sub-agent');
          setLoading(false);
          return;
        }
      }

      let yaml: string;
      try {
        yaml = await generateYaml(values);
      } catch (yamlError) {
        message.error('Failed to generate YAML');
        return;
      }

      // Build data format to save to backend - preserve existing handle configuration
      let existingHandle: any = {};
      try {
        // Try to get existing handle configuration of currently selected agent
        if (selectedAgentId) {
          const selectedAgent = savedAgents.find(agent => agent.id === selectedAgentId);
          if (selectedAgent && selectedAgent.handle) {
            existingHandle = JSON.parse(selectedAgent.handle);
          }
        }
      } catch (error) {
      }

      // Merge configuration: preserve existing config, update model and tools
      const updatedHandle = {
        ...existingHandle,
        state: existingHandle.state || {
          strategies: {
            input: 'replace',
            output: 'replace'
          }
        },
        model: {
          name: values.model,
          url: existingHandle.model?.url || 'https://api.example.com/v1',
          'api-key': existingHandle.model?.['api-key'] || 'your-api-key'
        },
        tools: values.tools || []
      };

      const agentData: any = {
        name: values.name,
        description: values.description,
        type: values.agentType || 'ReactAgent',
        instruction: values.instruction,
        inputKeys: JSON.stringify(values.inputKey ? [values.inputKey] : ['input']), // Convert to JSON string
        outputKey: values.outputKey || 'output',
        handle: JSON.stringify(updatedHandle),
        subAgents: (values.subAgents && values.subAgents.length > 0) ? JSON.stringify(values.subAgents.map((agentName: string) => {
          // Find corresponding ID by name
          const agent = savedAgents.find(sa => sa.name === agentName);
          if (agent && agent.id) {
            return {
              agent: {
                id: agent.id,
                name: agentName // Save name as well for YAML generation and display
              }
            };
          } else {
            // If agent not found, log warning and use name as fallback
            return {
              agent: {
                name: agentName // Use name as fallback
              }
            };
          }
        })) : null,
        yamlSchema: yaml,
      };


      // Check authentication status
      const token = await session.asyncGet();

      if (selectedAgentId) {
        const updatedAgent = await AgentSchemaService.updateAgentSchema(selectedAgentId, agentData);
        message.success('Agent Schema updated successfully!');
      } else {
        try {
          const createdAgent = await AgentSchemaService.createAgentSchema(agentData);
          message.success('Agent Schema created successfully!');
        } catch (apiError) {
          // Don't re-throw error here, let outer catch handle it
          throw apiError;
        }
      }

      await fetchSavedAgents();

    } catch (error) {
      message.error('Failed to save, please try again');
    } finally {
      setLoading(false);
    }
  };

  // Copy YAML
  const handleCopyYaml = () => {
    navigator.clipboard.writeText(yamlContent);
    message.success('YAML copied to clipboard');
  };

  // Download YAML
  const handleDownloadYaml = async () => {
    const values = form.getFieldsValue();
    const yaml = await generateYaml(values);
    const blob = new Blob([yaml], { type: 'text/yaml' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${values.name || 'agent'}-schema.yaml`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
  };

  const handleGoBack = () => {
    navigate('/');
  };

  // Custom selector component
  const CustomSelector: React.FC<{
    options: Array<{ value: string; label: string; description?: string }>;
    value?: string[];
    onChange?: (value: string[]) => void;
    expanded: boolean;
    onExpandChange: (expanded: boolean) => void;
    maxVisible?: number;
    icon?: string;
  }> = ({
    options,
    value = [],
    onChange,
    expanded,
    onExpandChange,
    maxVisible = 3,
    icon = 'F'
  }) => {
    const visibleOptions = expanded ? options : options.slice(0, maxVisible);
    const hasMore = options.length > maxVisible;

    const handleItemClick = (optionValue: string) => {
      const newValue = value.includes(optionValue)
        ? value.filter(v => v !== optionValue)
        : [...value, optionValue];
      onChange?.(newValue);
    };

    return (
      <div className={styles.selectorGroup}>
        {visibleOptions.map((option) => {
          const isSelected = value.includes(option.value);
          return (
            <div
              key={option.value}
              className={`${styles.selectorItem} ${isSelected ? styles.selected : ''}`}
              onClick={() => handleItemClick(option.value)}
            >
              <div className={styles.selectorItemIcon}>
                {icon}
              </div>
              <div className={styles.selectorItemContent}>
                <div className={styles.selectorItemTitle}>{option.label}</div>
                {option.description && (
                  <div className={styles.selectorItemDescription}>{option.description}</div>
                )}
              </div>
              <div className={styles.selectorCheckbox}>
                <Checkbox checked={isSelected} />
              </div>
            </div>
          );
        })}

        {hasMore && (
          <div
            className={styles.expandButton}
            onClick={() => onExpandChange(!expanded)}
          >
            {expanded ? <UpOutlined /> : <DownOutlined />}
            {expanded ? `Show less` : `${options.length - maxVisible} more`}
          </div>
        )}
      </div>
    );
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
          title: 'Agent Schema Creator',
        },
      ]}
    >
      <div className={styles.container}>
        {/* Three-column layout */}
        <div className={styles.threeColumnLayout}>
          {/* Left panel: Saved agents */}
          <div className={styles.leftPanel}>
            <Card title="Saved Agents" className={styles.savedAgentsCard}>
              <div
                className={styles.listContainer}
                style={{
                  height: '320px', // Reduced height, more compact
                  overflowY: 'scroll',
                  overflowX: 'hidden',
                  padding: '0 4px',
                  // CSS trick to force scrollbar display
                  scrollbarGutter: 'stable'
                }}
              >
                <List
                  loading={agentsLoading}
                  dataSource={savedAgents}
                  renderItem={(agent) => (
                  <List.Item
                    className={`${styles.agentItem} ${selectedAgentId === agent.id ? styles.selectedAgent : ''}`}
                    onClick={() => handleSelectAgent(agent)}
                    actions={[
                      <Tooltip title="Delete Agent" key="delete">
                        <Button
                          type="text"
                          size="small"
                          icon={<DeleteOutlined />}
                          onClick={(e) => handleDeleteAgent(agent, e)}
                          className={styles.deleteButton}
                          style={{ color: '#ff4d4f' }}
                        />
                      </Tooltip>
                    ]}
                  >
                    <div className={styles.agentInfo}>
                      <Text strong className={styles.agentName}>{agent.name}</Text>
                      <Text type="secondary" className={styles.agentType}>{agent.type}</Text>
                      <Text type="secondary" className={styles.agentDescription}>
                        {agent.description}
                      </Text>
                    </div>
                  </List.Item>
                )}
                />
              </div>
              <Divider />
              <Button
                type="dashed"
                block
                icon={<PlusOutlined />}
                onClick={handleNewAgent}
                className={styles.newAgentButton}
              >
                + new Agent
              </Button>
            </Card>
          </div>

          {/* Center panel: Agent configuration */}
          <div className={styles.centerPanel}>
            <Card title="Agent Configuration" className={styles.configCard}>
              <div className={styles.independentScrollContainer}>
                <div className={styles.formScrollContainer}>
                  <Form
                    form={form}
                    layout="vertical"
                    className={styles.form}
                  initialValues={{
                    agentType: 'ReactAgent',
                    inputKey: 'input',
                    outputKey: 'output',
                    model: 'qwen2.5-72b-instruct',
                    handle: {
                      state: {
                        strategies: {
                          input: 'replace',
                          output: 'replace'
                        }
                      },
                      model: {
                        name: 'qwen2.5-72b-instruct',
                        url: 'https://api.example.com/v1',
                        'api-key': 'your-api-key'
                      }
                    },
                    subAgents: [],
                    tools: []
                  }}
                  onValuesChange={(changedValues, allValues) => {

                    if (allValues.name) {
                      // Sync model selection to handle configuration
                      if (changedValues.model) {
                        const currentHandle = allValues.handle || {} as any;
                        const updatedHandle = {
                          ...currentHandle,
                          // Ensure state configuration is included
                          state: currentHandle.state || {
                            strategies: {
                              input: 'replace',
                              output: 'replace'
                            }
                          },
                          model: {
                            name: changedValues.model,
                            url: currentHandle.model?.url || 'https://api.example.com/v1',
                            'api-key': currentHandle.model?.['api-key'] || 'your-api-key'
                          },
                          // Ensure tools configuration is preserved
                          tools: currentHandle.tools || allValues.tools || []
                        };
                        form.setFieldsValue({ handle: updatedHandle });

                        // Generate YAML using updated handle
                        generateYaml({ ...allValues, handle: updatedHandle } as AgentSchemaForm).then(yaml => {
                          setYamlContent(yaml);
                        }).catch(error => {
                        });
                      } else {
                        // Asynchronously update YAML without blocking form interaction
                        // Ensure handle includes complete model info
                        const currentHandle = allValues.handle || {} as any;

                        const completeValues = {
                          ...allValues,
                          handle: {
                            ...currentHandle,
                            // Ensure state configuration is included
                            state: currentHandle.state || {
                              strategies: {
                                input: 'replace',
                                output: 'replace'
                              }
                            },
                            // Ensure existing model configuration is preserved
                            model: currentHandle.model || {
                              name: allValues.model || 'qwen2.5-72b-instruct',
                              url: 'https://api.example.com/v1',
                              'api-key': 'your-api-key'
                            },
                            // Ensure tools configuration is preserved
                            tools: currentHandle.tools || allValues.tools || []
                          }
                        };


                        generateYaml(completeValues as AgentSchemaForm).then(yaml => {
                          setYamlContent(yaml);
                        }).catch(error => {
                        });
                      }
                    }
                  }}
                >
                <Form.Item
                  label={
                    <span>
                      Agent Name
                      <Tooltip title="A unique name for your agent (required)">
                        <Text type="secondary" className={styles.tooltipText}> ℹ️</Text>
                      </Tooltip>
                    </span>
                  }
                  name="name"
                  rules={[
                      { required: true, message: 'Please enter agent name' },
                      {
                        validator: (_, value) => {
                          if (!value) return Promise.resolve();

                          // Basic format validation: only allow letters, numbers, underscores, hyphens, spaces and Chinese characters
                          if (!/^[a-zA-Z0-9_\-\s\u4e00-\u9fa5]+$/.test(value)) {
                            return Promise.reject('Agent name can only contain letters, numbers, underscores, hyphens, spaces and Chinese characters');
                          }

                          // Length validation
                          if (value.length < 2 || value.length > 50) {
                            return Promise.reject('Agent name length should be between 2-50 characters');
                          }

                          return Promise.resolve();
                        },
                      },
                  ]}
                  help={nameError || undefined}
                  validateStatus={nameError ? 'error' : undefined}
                >
                  <Input
                    placeholder="Enter agent name"
                    onChange={(e) => checkNameDuplicate(e.target.value)}
                  />
                </Form.Item>


                <Form.Item
                  label={
                    <span>
                      Model Selection
                      <Tooltip title="Select the LLM model for this agent">
                        <Text type="secondary" className={styles.tooltipText}> ℹ️</Text>
                      </Tooltip>
                    </span>
                  }
                  name="model"
                  rules={[{ required: true, message: 'Please select model' }]}
                >
                  <Select
                    placeholder="Select model"
                    loading={modelsLoading}
                    disabled={modelsLoading}
                  >
                    {availableModels.map(model => (
                      <Option key={model.model_id} value={model.model_id}>
                        {model.name}
                      </Option>
                    ))}
                  </Select>
                </Form.Item>

                <Form.Item
                  label={
                    <span>
                      Agent Type
                      <Tooltip title="Functional type of the agent">
                        <Text type="secondary" className={styles.tooltipText}> ℹ️</Text>
                      </Tooltip>
                    </span>
                  }
                  name="agentType"
                >
                  <Select placeholder="Select agent type">
                    {agentTypeOptions.map(option => (
                      <Option key={option.value} value={option.value}>
                        {option.label}
                      </Option>
                    ))}
                  </Select>
                </Form.Item>

                <Form.Item
                  label={
                    <span>
                      Description
                      <Tooltip title="Brief summary of agent capabilities (optional)">
                        <Text type="secondary" className={styles.tooltipText}> ℹ️</Text>
                      </Tooltip>
                    </span>
                  }
                  name="description"
                >
                  <TextArea
                    rows={3}
                    placeholder="Enter agent description"
                  />
                </Form.Item>

                <Form.Item
                  label={
                    <span>
                      Instructions (System Prompt)
                      <Tooltip title="System instructions to guide agent behavior">
                        <Text type="secondary" className={styles.tooltipText}> ℹ️</Text>
                      </Tooltip>
                    </span>
                  }
                  name="instruction"
                  rules={[{ required: true, message: 'Please enter system instructions' }]}
                >
                  <TextArea
                    rows={8}
                    placeholder="Enter system instructions..."
                  />
                </Form.Item>

                <div className={styles.keyRow}>
                  <Form.Item
                    label={
                      <span>
                        Input Key
                        <Tooltip title="Key for input data in agent interactions">
                          <Text type="secondary" className={styles.tooltipText}> ℹ️</Text>
                        </Tooltip>
                      </span>
                    }
                    name="inputKey"
                    className={styles.keyItem}
                  >
                    <Input placeholder="user_query" />
                  </Form.Item>
                  <Form.Item
                    label={
                      <span>
                        Output Key
                        <Tooltip title="Key for output data from agent responses">
                          <Text type="secondary" className={styles.tooltipText}> ℹ️</Text>
                        </Tooltip>
                      </span>
                    }
                    name="outputKey"
                    className={styles.keyItem}
                  >
                    <Input placeholder="agent_response" />
                  </Form.Item>
                </div>

                <Form.Item
                  label={
                    <span>
                      Sub Agents
                      <Tooltip title="Select sub-agents to enable collaboration">
                        <Text type="secondary" className={styles.tooltipText}> ℹ️</Text>
                      </Tooltip>
                    </span>
                  }
                  name="subAgents"
                  rules={[
                    {
                      validator: (_, value) => {
                        if (!value || !Array.isArray(value) || value.length === 0) {
                          return Promise.resolve();
                        }

                        // Get currently selected agent name
                        const currentAgentName = form.getFieldValue('name');
                        if (!currentAgentName) {
                          return Promise.resolve();
                        }

                        // Check if current agent is selected as its own sub-agent
                        if (value.includes(currentAgentName)) {
                          return Promise.reject('Cannot set current agent as its own sub-agent');
                        }

                        return Promise.resolve();
                      }
                    }
                  ]}
                >
                  <CustomSelector
                    options={subAgentOptions.map(option => {
                      const agent = savedAgents.find(sa => sa.name === option.value);
                      return {
                        value: option.value,
                        label: option.label,
                        description: `Type: ${agent?.type || 'Unknown'}`
                      };
                    })}
                    expanded={subAgentsExpanded}
                    onExpandChange={setSubAgentsExpanded}
                    maxVisible={3}
                    icon="A"
                  />
                </Form.Item>

                <Form.Item
                  label={
                    <span>
                      Tools
                      <Tooltip title="Tools available for the agent to use">
                        <Text type="secondary" className={styles.tooltipText}> ℹ️</Text>
                      </Tooltip>
                    </span>
                  }
                  name="tools"
                >
                  {toolsLoading ? (
                    <div>Loading tools...</div>
                  ) : (
                    <CustomSelector
                      options={toolOptions.map(option => ({
                        value: option.value,
                        label: option.label,
                        description: `Tool: ${option.value}`
                      }))}
                      expanded={toolsExpanded}
                      onExpandChange={setToolsExpanded}
                      maxVisible={8}
                      icon="F"
                    />
                  )}
                </Form.Item>
                </Form>
                </div>

                <div className={styles.configActions}>
                  <Button onClick={handleGoBack} icon={<CloseOutlined />}>
                    Cancel
                  </Button>
                  <Button
                    type="primary"
                    onClick={handleSaveAgent}
                    loading={loading}
                    icon={<SaveOutlined />}
                  >
                    Save Agent
                  </Button>
                </div>
              </div>
            </Card>
          </div>

          {/* Right panel: Agent Schema (YAML) */}
          <div className={styles.rightPanel}>
            <Card
              title={
                <div className={styles.yamlHeader}>
                  Agent Schema (YAML)
                  <Button
                    type="text"
                    icon={<CopyOutlined />}
                    onClick={handleCopyYaml}
                    className={styles.copyButton}
                  >
                    Copy
                  </Button>
                </div>
              }
              className={styles.yamlCard}
            >
              <pre className={styles.yamlContent}>
                {yamlContent || '// YAML will appear here as you configure the agent'}
              </pre>
            </Card>
          </div>
        </div>
      </div>
    </InnerLayout>
  );
};

export default AgentSchemaCreator;
