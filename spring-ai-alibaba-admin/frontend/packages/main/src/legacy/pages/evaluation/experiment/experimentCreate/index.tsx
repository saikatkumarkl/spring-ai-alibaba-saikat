import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Form, Input, Button, Card, Select, message, Tag, AutoComplete } from 'antd';
import { ArrowLeftOutlined, EyeOutlined, PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import API from '../../../../services';
import { getLegacyPath, buildLegacyPath } from '../../../../utils/path';
import './index.css';

const { TextArea } = Input;
const { Option } = Select;



// Form data interface
interface ExperimentCreateForm {
  // Step 1: Configuration info
  name: string;
  description: string;

  // Step 2: Configure dataset
  datasetId: string;
  datasetVersionId?: string;

  // Step 3: Configure evaluation object
  objectType: string;
  promptKey?: string;
  version?: string;

  // Step 4: Configure evaluator
  evaluatorId: string;
}

// Object type options
const objectTypes = [
  { value: 'prompt', label: 'Prompt' }
];

// Component props interface
interface GatherCreateProps {
  onCancel?: () => void;
  onSuccess?: () => void;
  hideTitle?: boolean; // Add hideTitle property to control whether to hide title
}

const ExperimentCreate: React.FC<GatherCreateProps> = ({ onCancel, onSuccess, hideTitle = false }) => {
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [selectedDataset, setSelectedDataset] = useState<any>(null); // Selected dataset
  const [datasets, setDatasets] = useState<any[]>([]); // Dataset list data
  const [datasetsLoading, setDatasetsLoading] = useState(false); // Dataset data loading status
  const [datasetVersions, setDatasetVersions] = useState<any[]>([]); // Dataset version list
  const [datasetVersionsLoading, setDatasetVersionsLoading] = useState(false); // Dataset version loading status

  // Prompts related state
  const [prompts, setPrompts] = useState<any[]>([]); // Prompts list data
  const [promptsLoading, setPromptsLoading] = useState(false); // Prompts data loading status
  const [selectedPrompt, setSelectedPrompt] = useState<any>(null); // Selected Prompt
  const [promptVersions, setPromptVersions] = useState<any[]>([]); // Selected Prompt version list
  const [promptVersionsLoading, setPromptVersionsLoading] = useState(false); // Version data loading status
  console.log(promptVersions, 'zxc...')

  // Evaluator related state
  const [evaluators, setEvaluators] = useState<any[]>([]); // Evaluator list data
  const [evaluatorsLoading, setEvaluatorsLoading] = useState(false); // Evaluator data loading status
  const [selectedEvaluators, setSelectedEvaluators] = useState<any[]>([]); // Selected evaluator list

  // Evaluator version related state
  const [evaluatorVersions, setEvaluatorVersions] = useState<Record<string, any[]>>({}); // Version list stored by evaluator ID
  const [evaluatorVersionsLoading, setEvaluatorVersionsLoading] = useState<Record<string, boolean>>({}); // Loading status stored by evaluator ID
  const [selectedEvaluatorVersions, setSelectedEvaluatorVersions] = useState<Record<string, string>>({}); // Selected version stored by evaluator ID

  // Evaluator config mode state
  const [evaluatorConfigMode, setEvaluatorConfigMode] = useState<Record<string, boolean>>({}); // Config mode stored by evaluator ID (true for config mode, false for mapping mode)
  const [evaluatorParams, setEvaluatorParams] = useState<Record<string, string[]>>({}); // Parameter list stored by evaluator ID
  const [evaluatorParamMappings, setEvaluatorParamMappings] = useState<Record<string, Record<string, string>>>({}); // Parameter mapping stored by evaluator ID

  // Prompt version detail related state
  const [promptVersionDetail, setPromptVersionDetail] = useState<any>(null); // Selected Prompt version detail
  const [promptVersionDetailLoading, setPromptVersionDetailLoading] = useState(false); // Prompt version detail loading status

  // Dataset detail related state
  const [datasetDetail, setDatasetDetail] = useState<any>(null); // Selected dataset detail
  const [datasetDetailLoading, setDatasetDetailLoading] = useState(false); // Dataset detail loading status

  // Field mapping config state
  const [fieldMapping, setFieldMapping] = useState<Record<string, string>>({}); // Field mapping config {promptParam: datasetField}

  // Object type selection state
  const [selectedObjectType, setSelectedObjectType] = useState<string>(''); // Selected object type

  // Return to list page
  const handleGoBack = () => {
    if (onCancel) {
      onCancel();
    } else {
      navigate('/evaluation-experiment');
    }
  };

  // Get dataset list
  const fetchDatasets = async () => {
    try {
      setDatasetsLoading(true);
      const response = await API.getDatasets({
        pageNumber: 1,
        pageSize: 100 // Get more data to ensure all datasets are retrieved
      });

      if (response.code === 200 && response.data) {
        const responseData = response.data as any;
        const dataItems = responseData.pageItems || responseData.records || [];

        // If API returns empty data, set to empty array
        const transformedDatasets = dataItems.map((item: any) => {
          // Parse columnsConfig to get column info
          let columns = ['input', 'reference_output']; // Default columns
          try {
            if (item.columnsConfig) {
              const parsedConfig = JSON.parse(item.columnsConfig);
              if (Array.isArray(parsedConfig) && parsedConfig.length > 0) {
                columns = parsedConfig.map((col: any) => col.name || col);
              }
            }
          } catch (e) {
            // Parse columnsConfig failed
          }

          return {
            id: item.id.toString(),
            name: item.name,
            description: item.description || '',
            dataCount: item.dataCount || 0,
            versions: ['v1.0.0'], // Default version, can be extended to dynamic fetch later
            columns: columns,
            createTime: item.createTime,
            updateTime: item.updateTime
          };
        });
        setDatasets(transformedDatasets);
      } else {
        // API call failed, set to empty array
        setDatasets([]);
      }
    } catch (error) {
      // Set to empty array on error
      setDatasets([]);
      message.error('Failed to fetch dataset list, please try again');
    } finally {
      setDatasetsLoading(false);
    }
  };

  // Get Prompts list
  const fetchPrompts = async () => {
    try {
      setPromptsLoading(true);
      const response = await API.getPrompts({
        pageNo: 1,
        pageSize: 100 // Get more data
      });

      if (response.code === 200 && response.data) {
        const promptsData = (response.data as any).pageItems || [];
        setPrompts(promptsData);
      } else {
        setPrompts([]);
        message.error('Failed to fetch Prompts list');
      }
    } catch (error) {
      setPrompts([]);
      message.error('Failed to fetch Prompts list, please try again');
    } finally {
      setPromptsLoading(false);
    }
  };

  // Get evaluator list
  const fetchEvaluators = async () => {
    try {
      setEvaluatorsLoading(true);
      const response = await API.getEvaluators({
        pageNumber: 1,
        pageSize: 100 // Get more data
      });

      if (response.code === 200 && response.data) {
        const evaluatorsData = (response.data as any).pageItems || [];
        setEvaluators(evaluatorsData);
      } else {
        setEvaluators([]);
        message.error('Failed to fetch evaluator list');
      }
    } catch (error) {
      setEvaluators([]);
      message.error('Failed to fetch evaluator list, please try again');
    } finally {
      setEvaluatorsLoading(false);
    }
  };

  // Get dataset version list
  const fetchDatasetVersions = async (datasetId: string) => {
    try {
      setDatasetVersionsLoading(true);
      const response = await API.getDatasetVersions({
        datasetId: Number(datasetId),
        pageNumber: 1,
        pageSize: 50
      });

      if (response.code === 200 && response.data) {
        const versionsData = (response.data as any).pageItems || [];
        setDatasetVersions(versionsData);
      } else {
        setDatasetVersions([]);
        message.error('Failed to fetch dataset version list');
      }
    } catch (error) {
      setDatasetVersions([]);
      message.error('Failed to fetch dataset version list');
    } finally {
      setDatasetVersionsLoading(false);
    }
  };

  // Handle Prompt version selection
  const handlePromptVersionChange = async (version: string) => {
    const promptKey = form.getFieldValue('promptKey');
    if (promptKey && version) {
      await fetchPromptVersionDetail(promptKey, version);
    }
  };

  // Handle field mapping change
  const handleFieldMappingChange = (promptParam: string, datasetField: string) => {
        // Ensure actual value
    const actualValue = datasetField || 'input';
    setFieldMapping(prev => ({
      ...prev,
      [promptParam]: actualValue
    }));
  };

  // Handle dataset selection
  const handleDatasetChange = async (datasetId: string) => {
    const dataset = datasets.find(d => d.id === datasetId);
    setSelectedDataset(dataset);
    // Clear version selection
    form.setFieldValue('datasetVersionId', undefined);
    setDatasetVersions([]);
    setDatasetDetail(null);

    if (dataset) {
      // Get version list for this dataset
      await fetchDatasetVersions(datasetId);
      // Get dataset detail
      await fetchDatasetDetail(datasetId);
    }
  };

  // Smart match field mapping default values
  const generateDefaultFieldMapping = (promptParams: string[], datasetFields: string[]): Record<string, string> => {
    const mapping: Record<string, string> = {};

    // Define common field mapping rules
    const mappingRules = [
      // Exact match
      { pattern: /^input$/i, target: 'input' },
      { pattern: /^output$/i, target: 'output' },
      { pattern: /^reference_output$/i, target: 'reference_output' },
      { pattern: /^expected_output$/i, target: 'reference_output' },
      { pattern: /^answer$/i, target: 'reference_output' },
      { pattern: /^question$/i, target: 'input' },
      { pattern: /^query$/i, target: 'input' },
      { pattern: /^text$/i, target: 'input' },
      { pattern: /^content$/i, target: 'input' },

      // Fuzzy match
      { pattern: /input|question|query|prompt/i, target: 'input' },
      { pattern: /output|answer|response|result/i, target: 'reference_output' },
      { pattern: /reference|expected|target|ground_truth/i, target: 'reference_output' }
    ];

    promptParams.forEach(param => {
      let matchedField = '';

      // First try exact match
      for (const rule of mappingRules) {
        if (rule.pattern.test(param) && datasetFields.includes(rule.target)) {
          matchedField = rule.target;
          break;
        }
      }

      // If no match, try finding similar keywords in dataset fields
      if (!matchedField) {
        for (const field of datasetFields) {
          if (param.toLowerCase().includes(field.toLowerCase()) ||
              field.toLowerCase().includes(param.toLowerCase())) {
            matchedField = field;
            break;
          }
        }
      }

      // If still no match, must set a valid default field
      if (!matchedField && datasetFields.length > 0) {
        // Prioritize input field, then reference_output, finally first field
        if (datasetFields.includes('input')) {
          matchedField = 'input';
        } else if (datasetFields.includes('reference_output')) {
          matchedField = 'reference_output';
        } else {
          matchedField = datasetFields[0];
        }
      }

      // Ensure there's always a valid value, even if no dataset fields available
      if (!matchedField || matchedField === '') {
        matchedField = 'input'; // Final safety default value
      }

      mapping[param] = matchedField;

    });

    return mapping;
  };

  // Update field mapping (called after getting dataset detail)
  const updateFieldMappingWithDefaults = () => {
    if (promptVersionDetail && datasetDetail) {
      try {
        const variables = JSON.parse(promptVersionDetail.variables || '{}');
        const promptParams = Object.keys(variables);

        // Parse dataset fields
        let datasetFields: string[] = [];
        try {
          const columnsConfig = JSON.parse(datasetDetail.columnsConfig || '[]');
          datasetFields = Array.isArray(columnsConfig) ? columnsConfig.map((col: any) => col.name || col) : [];
        } catch (e) {
          datasetFields = ['input', 'reference_output']; // Default fields
        }

        // Generate default mapping
        const defaultMapping = generateDefaultFieldMapping(promptParams, datasetFields);
                // Confirm all mapping values are non-empty again
        Object.keys(defaultMapping).forEach(key => {
          if (!defaultMapping[key] || defaultMapping[key] === '') {
            defaultMapping[key] = datasetFields[0] || 'input';
          }
        });

        // Force update state
        setFieldMapping({});
        setTimeout(() => {
          setFieldMapping(defaultMapping);
        }, 10);
      } catch (e) {
        // Update field mapping default values failed
      }
    }
  };

  // Get Prompt version detail
  const fetchPromptVersionDetail = async (promptKey: string, version: string) => {
    try {
      setPromptVersionDetailLoading(true);
      const response = await API.getPromptVersion({
        promptKey,
        version
      });

      if (response.code === 200 && response.data) {
        setPromptVersionDetail(response.data);

        // Parse variables field to get parameter info
        try {
          const variables = JSON.parse(response.data.variables || '{}');
          // Initialize field mapping as empty, set default after dataset detail loads
          const initialMapping: Record<string, string> = {};
          const promptParams = Object.keys(variables);

          // Even if dataset detail not loaded, set initial field value
          promptParams.forEach(param => {
            initialMapping[param] = 'input'; // Default use input field
          });
          setFieldMapping(initialMapping);

          // If dataset detail already loaded, update default values immediately
          if (datasetDetail) {
            setTimeout(() => updateFieldMappingWithDefaults(), 100);
          }
        } catch (e) {
          setFieldMapping({});
        }
      } else {
        setPromptVersionDetail(null);
        message.error('Failed to fetch Prompt version details');
      }
    } catch (error) {
      setPromptVersionDetail(null);
      message.error('Failed to fetch Prompt version details');
    } finally {
      setPromptVersionDetailLoading(false);
    }
  };

  // Get dataset detail
  const fetchDatasetDetail = async (datasetId: string) => {
    try {
      setDatasetDetailLoading(true);
      const response = await API.getDataset({
        datasetId: Number(datasetId)
      });

      if (response.code === 200 && response.data) {
        setDatasetDetail(response.data);

        // If Prompt version detail already loaded, update field mapping default values immediately
        if (promptVersionDetail) {
          setTimeout(() => updateFieldMappingWithDefaults(), 100);
        }
      } else {
        setDatasetDetail(null);
        message.error('Failed to fetch dataset details');
      }
    } catch (error) {
      setDatasetDetail(null);
      message.error('Failed to fetch dataset details');
    } finally {
      setDatasetDetailLoading(false);
    }
  };

  // Handle Prompt Key selection
  const handlePromptKeyChange = async (promptKey: string) => {
    const prompt = prompts.find(p => p.promptKey === promptKey);
    setSelectedPrompt(prompt);

    // Clear version and detail selection
    form.setFieldValue('version', undefined);
    setPromptVersionDetail(null);
    setFieldMapping({});

    if (prompt) {
      // Get version list for this Prompt
      try {
        setPromptVersionsLoading(true);
        const response = await API.getPromptVersions({
          promptKey: prompt.promptKey,
          pageNo: 1,
          pageSize: 50
        });

        if (response.code === 200 && response.data) {
          setPromptVersions((response.data as any).pageItems || []);
        } else {
          setPromptVersions([]);
          message.error('Failed to fetch Prompt versions');
        }
      } catch (error) {
        setPromptVersions([]);
        message.error('Failed to fetch Prompt versions');
      } finally {
        setPromptVersionsLoading(false);
      }
    } else {
      setPromptVersions([]);
    }
  };

  // Get evaluator parameters
  const fetchEvaluatorParams = async (evaluatorId: string, versionId: string) => {
    try {
      // Call interface to get evaluator detail
      const response = await API.getEvaluator({
        id: Number(evaluatorId)
      });

      if (response.code === 200 && response.data) {
        // Get evaluator parameters
        const defaultParams = ['input', 'output', 'reference_output'];
        let params: string[] = [];

        try {
          // Try to parse variables field
          const variables = JSON.parse(response.data.variables || '{}');
          params = Object.keys(variables);

          if (params.length === 0) {
            params = defaultParams;
          }
        } catch (e) {
          params = defaultParams;
        }

        // Update evaluator params
        setEvaluatorParams(prev => ({
          ...prev,
          [evaluatorId]: params
        }));

        // Initialize mapping relationship
        const initialMappings: Record<string, string> = {};
        const dataSourceFields = getDataSourceFields();

        // Smart match data source based on parameters
        params.forEach(param => {
          // Default mapping relationship
          if (param === 'input') {
            initialMappings[param] = 'input';
          } else if (param === 'output') {
            // For output param, map to actual_output field
            initialMappings[param] = 'actual_output';
          } else if (param === 'reference_output') {
            initialMappings[param] = 'reference_output';
          } else if (dataSourceFields.some(item => item.field === param)) {
            // If data source has same name field, map directly
            initialMappings[param] = param;
          } else {
            // Find similar field
            let matchedField = '';

            // Define common field mapping rules
            const mappingRules = [
              { pattern: /input|question|query|prompt/i, target: 'input' },
              { pattern: /output|answer|response|result/i, target: 'actual_output' },
              { pattern: /reference|expected|target|ground_truth/i, target: 'reference_output' }
            ];

            // Try to match
            for (const rule of mappingRules) {
              if (rule.pattern.test(param)) {
                // Check if matched target field exists in data source
                const targetField = dataSourceFields.find(item => item.field === rule.target);
                if (targetField) {
                  matchedField = rule.target;
                  break;
                }
              }
            }

            // If no match, try finding similar keywords in data source fields
            if (!matchedField) {
              for (const field of dataSourceFields) {
                if (param.toLowerCase().includes(field.field.toLowerCase()) ||
                    field.field.toLowerCase().includes(param.toLowerCase())) {
                  matchedField = field.field;
                  break;
                }
              }
            }

            // If still no match, use first available field or default input
            if (!matchedField && dataSourceFields.length > 0) {
              matchedField = dataSourceFields[0].field;
            }

            initialMappings[param] = matchedField || 'input';
          }
        });

        // Update evaluator parameter mapping
        setEvaluatorParamMappings(prev => ({
          ...prev,
          [evaluatorId]: initialMappings
        }));

        // Switch to mapping mode
        setEvaluatorConfigMode(prev => ({
          ...prev,
          [evaluatorId]: false // Set to non-config mode, show mapping interface
        }));

        return true;
      } else {
        message.error(`Failed to fetch evaluator ${evaluatorId} parameters`);
        return false;
      }
    } catch (error) {
      message.error(`Failed to fetch evaluator ${evaluatorId} parameters`);
      return false;
    }
  };

  // Get dataset fields
  const getDatasetFields = (): string[] => {
    if (!datasetDetail) return ['input', 'reference_output'];

    try {
      const columnsConfig = JSON.parse(datasetDetail.columnsConfig || '[]');
      const fields = Array.isArray(columnsConfig) ? columnsConfig.map((col: any) => col.name || col) : [];

      // Ensure at least default fields
      if (fields.length === 0) {
        return ['input', 'reference_output'];
      }

      return fields;
    } catch (e) {
      return ['input', 'reference_output'];
    }
  };

  // Define data source field interface, includes field name and source info
  interface DataSourceField {
    field: string;
    source: string;
    displayName: string;
  }

  // Get data source fields (only use dataset fields, add fixed actual_output field)
  const getDataSourceFields = (): DataSourceField[] => {
    let fields: DataSourceField[] = [];

    // Get dataset fields
    if (datasetDetail) {
      try {
        const columnsConfig = JSON.parse(datasetDetail.columnsConfig || '[]');
        const datasetFields = Array.isArray(columnsConfig) ? columnsConfig.map((col: any) => col.name || col) : [];
        // Add dataset source info
        fields = [
          ...fields,
          ...datasetFields.map(field => ({
            field,
            source: 'Dataset',
            displayName: `${field} (Dataset)`
          }))
        ];
      } catch (e) {
        // Handle dataset field parsing failure
      }
    }

    // Add fixed actual_output field, data source is "Evaluation Target"
    fields = [
      ...fields,
      {
        field: 'actual_output',
        source: 'Evaluation Target',
        displayName: 'actual_output (Evaluation Target)'
      }
    ];

    // Deduplicate (based on field)
    const uniqueFields = Array.from(
      new Map(fields.map(item => [item.field, item])).values()
    );

    return uniqueFields;
  };

  // Toggle evaluator config mode
  const toggleEvaluatorConfigMode = (evaluatorId: string) => {
    setEvaluatorConfigMode(prev => ({
      ...prev,
      [evaluatorId]: !prev[evaluatorId]
    }));

    // Clear evaluator and version selection
    setSelectedEvaluators(prev => prev.map(evaluator =>
      evaluator.evaluatorId === evaluatorId
        ? { ...evaluator, evaluatorId: '', versionId: '' }
        : evaluator
    ));

    // Clear selected evaluator version
    setSelectedEvaluatorVersions(prev => ({
      ...prev,
      [evaluatorId]: ''
    }));
  };

  // Handle evaluator parameter mapping change
  const handleEvaluatorParamMappingChange = (evaluatorId: string, param: string, fieldValue: string) => {
    // Get complete data source field object
    const dataSourceFields = getDataSourceFields();
    const selectedField = dataSourceFields.find(item => item.field === fieldValue);

    setEvaluatorParamMappings(prev => ({
      ...prev,
      [evaluatorId]: {
        ...prev[evaluatorId],
        [param]: fieldValue
      }
    }));
  };

  // Get evaluator version list
  const fetchEvaluatorVersions = async (evaluatorId: string) => {
    try {
      // Set loading state for corresponding evaluator
      setEvaluatorVersionsLoading(prev => ({ ...prev, [evaluatorId]: true }));

      // Call interface to get evaluator version list
      const response = await API.getEvaluatorVersions({
        evaluatorId: Number(evaluatorId),
        pageNumber: 1,
        pageSize: 50
      });

      if (response.code === 200 && response.data) {
        // Get version list
        const versionsData = (response.data as any).pageItems || [];

        // Update version list state
        setEvaluatorVersions(prev => ({
          ...prev,
          [evaluatorId]: versionsData
        }));

        // No longer auto-select first version, let user choose themselves
        // Clear currently selected version
        setSelectedEvaluatorVersions(prev => ({
          ...prev,
          [evaluatorId]: ''
        }));
      } else {
        // Set empty array
        setEvaluatorVersions(prev => ({
          ...prev,
          [evaluatorId]: []
        }));
        message.error(`Failed to fetch evaluator ${evaluatorId} version list`);
      }
    } catch (error) {
      // Set empty array
      setEvaluatorVersions(prev => ({
        ...prev,
        [evaluatorId]: []
      }));
      message.error(`Failed to fetch evaluator ${evaluatorId} version list`);
    } finally {
      // Reset loading state
      setEvaluatorVersionsLoading(prev => ({ ...prev, [evaluatorId]: false }));
    }
  };

  // Handle evaluator selection change
  const handleEvaluatorSelectChange = async (index: number, evaluatorId: string) => {
    // Update evaluator ID at specified index, preserve other evaluators' state
    const newEvaluators = [...selectedEvaluators];
    newEvaluators[index] = { ...newEvaluators[index], evaluatorId: evaluatorId };
    setSelectedEvaluators(newEvaluators);

    // Initialize config mode as true (config mode) for current evaluator instance
    // Use index and evaluator ID combination as key to ensure each instance is independent
    const instanceKey = `${index}-${evaluatorId}`;
    setEvaluatorConfigMode(prev => ({
      ...prev,
      [instanceKey]: true
    }));

    // Clear version selection for current evaluator instance
    setSelectedEvaluatorVersions(prev => ({
      ...prev,
      [instanceKey]: ''
    }));

    // If evaluator ID is valid, get its version list
    if (evaluatorId) {
      await fetchEvaluatorVersions(evaluatorId);
    }
  };

  // Handle evaluator version selection change
  const handleEvaluatorVersionChange = async (index: number, evaluatorId: string, versionId: string) => {
    // Use index and evaluator ID combination as key to ensure each instance is independent
    const instanceKey = `${index}-${evaluatorId}`;

    // Update selected version
    setSelectedEvaluatorVersions(prev => ({
      ...prev,
      [instanceKey]: versionId
    }));

    // If evaluator and version selected, get evaluator parameters
    if (evaluatorId && versionId && datasetDetail) {
      // Get parameters and initialize mapping
      const success = await fetchEvaluatorParams(evaluatorId, versionId);

      if (success) {
        // Already switched to mapping mode in fetchEvaluatorParams
        // Field mapping already auto-configured
        setEvaluatorConfigMode(prev => ({
          ...prev,
          [instanceKey]: false // Switch to mapping mode
        }));
      }
    } else if (evaluatorId && versionId && !datasetDetail) {
      // Have evaluator and version but no dataset detail, still switch to mapping mode
      setEvaluatorConfigMode(prev => ({
        ...prev,
        [instanceKey]: false // Switch to mapping mode
      }));

      // Get parameters and initialize mapping
      await fetchEvaluatorParams(evaluatorId, versionId);
    } else {
      // Didn't select complete evaluator and version, keep config mode
      if (evaluatorId) {
        setEvaluatorConfigMode(prev => ({
          ...prev,
          [instanceKey]: true
        }));
      }
    }
  };

  // Add evaluator
  const handleAddEvaluator = () => {
    // Create a new evaluator item
    const newEvaluator = {
      id: Date.now().toString(), // Temporary ID, will be replaced by actual selected evaluator ID when submitting
      evaluatorId: '',
      versionId: ''
    };

    // Add to evaluator list
    setSelectedEvaluators(prev => [...prev, newEvaluator]);

    // Initialize state for newly added evaluator
    const newIndex = selectedEvaluators.length;
    const instanceKey = `${newIndex}-`;

    // Initialize config mode as true (config mode)
    setEvaluatorConfigMode(prev => ({
      ...prev,
      [instanceKey]: true
    }));

    // Initialize version selection as empty
    setSelectedEvaluatorVersions(prev => ({
      ...prev,
      [instanceKey]: ''
    }));
  };

  // Remove evaluator
  const handleRemoveEvaluator = (index: number) => {
    setSelectedEvaluators(prev => prev.filter((_, i) => i !== index));
  };

  // View evaluator detail
  const handleViewEvaluatorDetail = (evaluatorId: string) => {
    navigate(getLegacyPath(`/evaluation/evaluator/${evaluatorId}`));
  };

  // View Prompt detail
  const handleViewPromptDetail = () => {
    const promptKey = form.getFieldValue('promptKey');
    if (promptKey) {
      navigate(buildLegacyPath('/prompt-detail', { promptKey }));
    }
  };

  // View dataset detail
  const handleViewDatasetDetail = () => {
    if (selectedDataset) {
      navigate(getLegacyPath(`/evaluation/gather/detail/${selectedDataset.id}`));
    }
  };

  // Fetch data when component loads
  useEffect(() => {
    fetchDatasets();
    fetchPrompts();
    fetchEvaluators();
  }, []);

  // Listen to field mapping state changes
  useEffect(() => {
    if (promptVersionDetail && datasetDetail) {
      const variables = JSON.parse(promptVersionDetail.variables || '{}');
      const promptParams = Object.keys(variables);

      // Check if there are parameters without mapping values
      let hasEmptyMapping = false;
      promptParams.forEach(param => {
        if (!fieldMapping[param]) {
          hasEmptyMapping = true;
        }
      });

      // If there are empty values, regenerate default mapping
      if (hasEmptyMapping) {
        updateFieldMappingWithDefaults();
      }
    }
  }, [promptVersionDetail, datasetDetail, fieldMapping]);

  // Submit form
  const handleSubmit = async (values: any) => {
    try {
      setLoading(true);

      // Check if there is a valid evaluator configuration
      if (selectedEvaluators.length === 0) {
        message.error('Please add at least one evaluator');
        setLoading(false);
        return;
      }

      // Check if all evaluators have a version selected
      // Use instanceKey to correctly check version selection state for each evaluator instance
      const missingVersions = selectedEvaluators.some((evaluator, index) => {
        const instanceKey = `${index}-${evaluator.evaluatorId}`;
        return !evaluator.evaluatorId || !selectedEvaluatorVersions[instanceKey];
      });
      if (missingVersions) {
        message.error('Please ensure all evaluators have a version selected');
        setLoading(false);
        return;
      }

      // Check if evaluator config is valid
      const invalidEvaluators = selectedEvaluators.some((evaluator, index) => {
        const instanceKey = `${index}-${evaluator.evaluatorId}`;
        return !evaluator.evaluatorId || !selectedEvaluatorVersions[instanceKey];
      });
      if (invalidEvaluators) {
        message.error('Please ensure all evaluators are properly configured');
        setLoading(false);
        return;
      }

      // Construct evaluation object config
      // Parse Prompt parameter variables
      let promptVariables = {};
      try {
        promptVariables = JSON.parse(promptVersionDetail?.variables || '{}');
      } catch (e) {
        // If parsing fails, use empty object
      }

      // Construct field mapping relationship
      const variableMap = Object.keys(promptVariables).map(param => ({
        promptVariable: param,
        datasetVolumn: fieldMapping[param] || 'input'
      }));

      // Construct evaluation object config (put variableMap inside config, at same level as promptKey and version)
      const evaluationObjectConfig = {
        type: values.objectType,
        config: {
          promptKey: values.promptKey,
          version: values.version, // Use prompt's version field
          variableMap: variableMap // Put variableMap inside config
        }
      };

      // Construct evaluator configuration
      const evaluatorConfig = selectedEvaluators.map((evaluator, index) => {
        // Get evaluator name
        const evaluatorInfo = evaluators.find(e => e.id.toString() === evaluator.evaluatorId);

        // Use instanceKey to get correct version information
        const instanceKey = `${index}-${evaluator.evaluatorId}`;

        // Get evaluator version name
        let evaluatorVersionName = '';
        if (evaluator.evaluatorId && selectedEvaluatorVersions[instanceKey]) {
          const versions = evaluatorVersions[evaluator.evaluatorId];
          if (versions) {
            const selectedVersion = versions.find(v => v.id.toString() === selectedEvaluatorVersions[instanceKey]);
            if (selectedVersion) {
              evaluatorVersionName = selectedVersion.version || '';
            }
          }
        }

        // Construct evaluator parameter mapping relationship
        const variableMap: { evaluatorVariable: string; source: string; dataSource?: string }[] = [];
        if (evaluatorParams[evaluator.evaluatorId]) {
          evaluatorParams[evaluator.evaluatorId].forEach(param => {
            // Get parameter mapping source field
            const sourceField = evaluatorParamMappings[evaluator.evaluatorId]?.[param] || 'input';

            // Get source field origin information
            const dataSourceFields = getDataSourceFields();
            const sourceFieldInfo = dataSourceFields.find(item => item.field === sourceField);

            variableMap.push({
              evaluatorVariable: param,
              source: sourceField,
              dataSource: sourceFieldInfo?.source || 'Default' // Add data source information
            });
          });
        }

        return {
          evaluatorId: Number(evaluator.evaluatorId),
          evaluatorVersionId: Number(selectedEvaluatorVersions[instanceKey]),
          variableMap: variableMap,
          evaluatorName: evaluatorInfo?.name || '',
          evaluatorVersionName: evaluatorVersionName // Add version name
        };
      }).filter(e => e.evaluatorId && e.evaluatorVersionId); // Filter out invalid evaluator configurations

      // Get correct datasetVersion value
      let datasetVersionValue = '';
      if (values.datasetVersionId) {
        const selectedVersion = datasetVersions.find(version => version.id === values.datasetVersionId);
        if (selectedVersion) {
          datasetVersionValue = selectedVersion.version;
        }
      }

      // Use JSON.stringify conversion only at final submission
      const submitData = {
        name: values.name,
        description: values.description,
        datasetId: Number(values.datasetId),
        datasetVersionId: Number(values.datasetVersionId),
        datasetVersion: datasetVersionValue, // Use version field value found in datasetVersions
        evaluationObjectConfig: JSON.stringify(evaluationObjectConfig),
        evaluatorConfig: JSON.stringify(evaluatorConfig),
      };

      // Call create experiment API
      await API.createExperiment(submitData);

      message.success('Experiment created successfully');

      // If onSuccess callback is provided, call it, otherwise navigate to list page
      if (onSuccess) {
        onSuccess();
      } else {
        navigate('/evaluation-experiment');
      }
    } catch (error) {
      message.error('Creation failed, please try again');

    } finally {
      setLoading(false);
    }
  };

  // Cancel creation
  const handleCancel = () => {
    if (onCancel) {
      onCancel();
    } else {
      navigate('/evaluation-experiment');
    }
  };



  return (
    <div className="experiment-create-page">
      {/* Page header - fixed at top */}
      {!hideTitle && (
        <div className="experiment-create-header">
          <div className="flex items-center mb-4">
            <Button
              type="text"
              icon={<ArrowLeftOutlined />}
              onClick={handleGoBack}
              className="mr-3"
            >
              Back
            </Button>
            <h1 className="text-2xl font-semibold mb-0">Create Experiment</h1>
          </div>
        </div>
      )}

      {/* Form area - scrollable area */}
      <div className={`experiment-create-content ${hideTitle ? 'pt-6' : ''}`}>
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
        >
          {/* Step 1: Configuration */}
          <Card title="Step 1: Configuration" className="mb-6">
            <Form.Item
              name="name"
              label="Experiment Name"
              rules={[
                { required: true, message: 'Please enter experiment name' },
                { max: 100, message: 'Name cannot exceed 100 characters' }
              ]}
            >
              <Input placeholder="e.g., Q&A Bot Experiment" />
            </Form.Item>

            <Form.Item
              name="description"
              label="Experiment Description"
              rules={[{ max: 500, message: 'Description cannot exceed 500 characters' }]}
            >
              <TextArea
                placeholder="Describe the purpose and content of the experiment"
                rows={4}
                showCount
                maxLength={500}
              />
            </Form.Item>
          </Card>

          {/* Step 2: Configure Dataset */}
          <Card title="Step 2: Configure Dataset" className="mb-6">
            <Form.Item
              name="datasetId"
              label="Select Dataset"
              rules={[{ required: true, message: 'Please select a dataset' }]}
            >
              <Select
                placeholder="Select a created dataset"
                onChange={handleDatasetChange}
                loading={datasetsLoading}
                notFoundContent={datasetsLoading ? 'Loading...' : 'No data available'}
              >
                {datasets.map(dataset => (
                  <Option key={dataset.id} value={dataset.id}>
                    {dataset.name}
                  </Option>
                ))}
              </Select>
            </Form.Item>

            {/* When dataset is selected, show version selection and dataset info */}
            {selectedDataset && (
              <>
                <Form.Item
                  name="datasetVersionId"
                  label="Select Version"
                  rules={[{ required: true, message: 'Please select a dataset version' }]}
                >
                  <Select
                    placeholder="Select dataset version"
                    loading={datasetVersionsLoading}
                    notFoundContent={datasetVersionsLoading ? 'Loading...' : 'No version data available'}
                  >
                    {datasetVersions.map((version: any) => (
                      <Option key={version.id} value={version.id}>
                        {version.version} - {version.description}
                      </Option>
                    ))}
                  </Select>
                </Form.Item>

                {/* Dataset info */}
                <div className="bg-gray-50 rounded-lg p-4 mt-4">
                  <div className="flex justify-between items-start mb-3">
                    <h4 className="text-base font-medium text-gray-900">Dataset Info</h4>
                    <Button
                      type="link"
                      icon={<EyeOutlined />}
                      className="text-blue-600 hover:text-blue-800 font-medium"
                      onClick={handleViewDatasetDetail}
                    >
                      View Details
                    </Button>
                  </div>

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <span className="text-sm text-gray-600">Description:</span>
                      <span className="ml-2 text-sm text-gray-900">{selectedDataset.description}</span>
                    </div>
                    <div>
                      <span className="text-sm text-gray-600">Data Count:</span>
                      <span className="ml-2 text-sm text-gray-900">{selectedDataset.dataCount} items</span>
                    </div>
                  </div>

                  <div className="mt-3">
                    <span className="text-sm text-gray-600">Columns:</span>
                    <div className="mt-1 flex flex-wrap gap-2">
                      {selectedDataset.columns.map((column: string) => (
                        <span
                          key={column}
                          className="inline-block bg-blue-100 text-blue-800 text-xs px-2 py-1 rounded"
                        >
                          {column}
                        </span>
                      ))}
                    </div>
                  </div>
                </div>
              </>
            )}
          </Card>

          {/* Step 3: Configure Evaluation Target */}
          <Card title="Step 3: Configure Evaluation Target" className="mb-6">
            <div className="grid grid-cols-1 md:grid-cols-1 gap-4 mb-4">
              <Form.Item
                name="objectType"
                label="Object Type"
                rules={[{ required: true, message: 'Please select object type' }]}
              >
                <Select
                  placeholder="Select evaluation object type"
                  onChange={(value) => {
                    setSelectedObjectType(value);
                    // When switching object type, clear related fields
                    if (value !== 'prompt') {
                      form.setFieldsValue({
                        promptKey: undefined,
                        version: undefined
                      });
                      setSelectedPrompt(null);
                      setPromptVersions([]);
                      setPromptVersionDetail(null);
                      setFieldMapping({});
                    }
                  }}
                >
                  {objectTypes.map(type => (
                    <Option key={type.value} value={type.value}>
                      {type.label}
                    </Option>
                  ))}
                </Select>
              </Form.Item>

              {/* Only show Prompt related configuration when prompt type is selected */}
              {selectedObjectType === 'prompt' && (
                <>
                  <Form.Item
                    name="promptKey"
                    label="Prompt Key"
                    rules={[{ required: true, message: 'Please enter or select Prompt Key' }]}
                  >
                    <AutoComplete
                      placeholder="Enter or select Prompt Key"
                      onChange={handlePromptKeyChange}
                      filterOption={(inputValue, option) => {
                        if (!option || !option.value) return false;
                        const value = option.value.toString().toLowerCase();
                        const input = inputValue.toLowerCase();
                        return value.indexOf(input) !== -1;
                      }}
                      notFoundContent={promptsLoading ? 'Loading...' : 'No data available'}
                    >
                      {prompts.map(prompt => (
                        <AutoComplete.Option key={prompt.promptKey} value={prompt.promptKey}>
                          {prompt.promptKey} { prompt.promptDescription ? " - " : ""} {prompt.promptDescription}
                        </AutoComplete.Option>
                      ))}
                    </AutoComplete>
                  </Form.Item>
                </>
              )}
            </div>

            {/* Only show version selection when prompt type is selected */}
            {selectedObjectType === 'prompt' && (
              <div className="grid grid-cols-1 md:grid-cols-1 gap-4">
                <Form.Item
                  name="version"
                  label="Version"
                  rules={[{ required: true, message: 'Please select a version' }]}
                >
                  <Select
                    placeholder="Select version"
                    loading={promptVersionsLoading}
                    disabled={!selectedPrompt}
                    onChange={handlePromptVersionChange}
                    notFoundContent={promptVersionsLoading ? 'Loading...' : 'Please select Prompt Key first'}
                  >
                    {promptVersions.map(version => {
                      const versionStatus = version.status;
                      return (
                        <Option key={version.version} value={version.version}>
                          <span className='mr-2'>
                            {version.version} {version.versionDescription ? " - " : ""} {version.versionDescription}
                          </span>
                          <Tag color={versionStatus === "release" ? "green" : "blue"}>{version.status === "release" ? "Release" : "PRE"}</Tag>
                        </Option>
                      )
                    })}
                  </Select>
                </Form.Item>
              </div>
            )}

            {/* Only show Prompt version detail card when prompt type is selected */}
            {selectedObjectType === 'prompt' && promptVersionDetail && (
              <div className="bg-gray-50 rounded-lg p-4 mt-4">
                <div className="flex justify-between items-start mb-3">
                  <h4 className="text-base font-medium text-gray-900">Prompt Version Details</h4>
                  <Button
                    type="link"
                    icon={<EyeOutlined />}
                    className="text-blue-600 hover:text-blue-800 font-medium"
                    onClick={handleViewPromptDetail}
                  >
                    View Full Details
                  </Button>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <span className="text-sm" style={{color: 'rgba(0, 0, 0, 0.45)'}}>Version:</span>
                    <span className="ml-2 text-sm text-gray-900">{promptVersionDetail.version}</span>
                  </div>
                  <div>
                    <span className="text-sm" style={{color: 'rgba(0, 0, 0, 0.45)'}}>Version Description:</span>
                    <span className="ml-2 text-sm text-gray-900">{promptVersionDetail.versionDescription}</span>
                  </div>
                </div>

                <div className="mt-3">
                  <span className="text-sm" style={{color: 'rgba(0, 0, 0, 0.45)'}}>Template:</span>
                  <div className="mt-1 bg-white rounded border p-3 text-sm text-gray-900 max-h-32 overflow-y-auto">
                    {promptVersionDetail.template || 'No template content'}
                  </div>
                </div>

                {promptVersionDetail.variables && (
                  <div className="mt-3">
                    <span className="text-sm" style={{color: 'rgba(0, 0, 0, 0.45)'}}>Parameters:</span>
                    <div className="mt-1 flex flex-wrap gap-2">
                      {Object.keys(JSON.parse(promptVersionDetail.variables || '{}')).map((param: string) => (
                        <span
                          key={param}
                          className="inline-block bg-blue-100 text-blue-800 text-xs px-2 py-1 rounded"
                        >
                          {param}
                        </span>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* Only show field mapping configuration card when prompt type is selected */}
            {selectedObjectType === 'prompt' && selectedDataset && promptVersionDetail && datasetDetail && (
              <div className="bg-gray-50 rounded-lg p-4 mt-4">
                <h4 className="text-base font-medium text-gray-900 mb-4">Field Mapping Configuration</h4>
                <div className="flex items-center justify-between mb-4">
                  <p className="text-sm text-gray-600">Configure the mapping between Prompt parameters and dataset fields:</p>
                  <div className="text-xs text-blue-600 bg-blue-50 px-2 py-1 rounded">
                    ✨ System has auto-matched default values
                  </div>
                </div>

                <div className="grid grid-cols-3 gap-4 items-center">
                  <span className="text-sm" style={{color: 'rgba(0, 0, 0, 0.45)'}}>Prompt Parameter:</span>
                  <div></div>
                  <span className="text-sm" style={{color: 'rgba(0, 0, 0, 0.45)'}}>Map to Dataset Field:</span>
                </div>
                <div className="space-y-3">
                  {Object.keys(JSON.parse(promptVersionDetail.variables || '{}')).map((param: string) => {
                    // Parse dataset field list
                    let datasetFields: string[] = [];
                    try {
                      const columnsConfig = JSON.parse(datasetDetail.columnsConfig || '[]');
                      datasetFields = Array.isArray(columnsConfig) ? columnsConfig.map((col: any) => col.name || col) : [];
                      // Ensure at least one field
                      if (datasetFields.length === 0) {
                        datasetFields = ['input', 'reference_output'];
                      }
                    } catch (e) {
                      datasetFields = ['input', 'reference_output']; // Default fields
                    }

                    // Ensure current parameter has mapping value
                    const currentValue = fieldMapping[param] || datasetFields[0];
                    if (!fieldMapping[param] && datasetFields.length > 0) {
                      // Immediately set a default value
                      setTimeout(() => {
                        handleFieldMappingChange(param, datasetFields[0]);
                      }, 0);
                    }

                    return (
                      <div key={param} className="grid grid-cols-3 gap-4 items-center">
                        <div>
                          <Input
                            value={param}
                            readOnly
                            className="mt-1"
                            placeholder="Prompt parameters"
                          />
                        </div>
                        <div style={{textAlign: 'center'}}>↔</div>
                        <div>
                          <Select
                            defaultActiveFirstOption
                            defaultValue={currentValue}
                            value={currentValue}
                            onChange={(value) => handleFieldMappingChange(param, value)}
                            className="mt-1 w-full"
                            showSearch
                            optionFilterProp="children"
                          >
                            {datasetFields.map(field => (
                              <Option key={field} value={field}>
                                {field}
                              </Option>
                            ))}
                          </Select>
                        </div>
                      </div>
                    );
                  })}
                </div>

                {Object.keys(JSON.parse(promptVersionDetail.variables || '{}')).length === 0 && (
                  <div className="text-center py-4 text-gray-500">
                    This Prompt version has no parameters to map
                  </div>
                )}

                {/* Field mapping instructions */}
                <div className="mt-6 bg-blue-50 rounded-lg p-4">
                  <div className="flex items-start">
                    <span className="mr-2 text-blue-500 text-xl">💡</span>
                    <div>
                      <div className="text-base font-medium text-gray-900 mb-2">Field Mapping Instructions:</div>
                      <ul className="list-disc pl-5 space-y-1 text-sm text-gray-600">
                        <li>System has auto-detected Prompt parameter variables and attempted smart matching with dataset fields</li>
                        <li>Please confirm each mapping is correct to ensure data is properly passed during experiment execution</li>
                        <li>Mappings will be used during experiment execution to fill Prompt parameters with dataset data</li>
                      </ul>
                    </div>
                  </div>
                </div>
              </div>
            )}
          </Card>

          {/* Step 4: Configure Evaluators */}
          <Card title="Step 4: Configure Evaluators" className="mb-6">
            <p className="text-gray-600 mb-4">Add evaluators, each evaluator needs a version and field mapping configuration</p>

            {/* Add evaluator button */}
            <div className="text-center border-2 border-dashed rounded-lg p-4 mb-6 cursor-pointer hover:bg-gray-50"
              onClick={handleAddEvaluator}>
              <PlusOutlined className="text-xl text-gray-500" />
              <div className="mt-2 text-gray-600">Add Evaluator</div>
            </div>

            {/* Configured evaluator list */}
            {selectedEvaluators.length > 0 && (
              <div className="mt-4">
                <h4 className="text-base font-medium text-gray-900 mb-4">Configured Evaluators:</h4>
                <div className="space-y-6">
                  {selectedEvaluators.map((evaluator, index) => {
                    // Get current evaluator ID
                    const evaluatorId = evaluator.evaluatorId;
                    // Use index and evaluator ID combination as key, ensuring each instance is independent
                    const instanceKey = `${index}-${evaluatorId}`;
                    // Determine if in mapping mode (evaluator and version both selected, and config mode is false)
                    const isMappingMode = evaluatorId &&
                                              selectedEvaluatorVersions[instanceKey] &&
                                              !evaluatorConfigMode[instanceKey];

                    return (
                      <div key={index} className="bg-gray-50 rounded-lg p-4 relative">
                        <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                          <div style={{ display: 'flex' }}>
                            <h5 className="text-base font-medium text-gray-900 mb-4 mr-4">
                              Evaluator {index + 1}
                            </h5>
                            {isMappingMode && <>
                              <h5 className="text-base font-medium text-blue-500 mb-4 mr-4">
                                {evaluators.find(e => e.id.toString() === evaluatorId)?.name}
                              </h5>
                              <h5 className="text-base font-medium text-blue-500 mb-4">
                                {evaluatorVersions[evaluatorId]?.find((v: any) => v.id.toString() === selectedEvaluatorVersions[instanceKey])?.version}
                              </h5>
                            </>}
                          </div>
                          <div className="top-2 right-2">
                            <Button
                              type="text"
                              danger
                              icon={<DeleteOutlined />}
                              onClick={() => handleRemoveEvaluator(index)}
                            >
                              Remove
                            </Button>
                          </div>
                        </div>

                        {isMappingMode ? (
                          // Mapping mode: show field mapping configuration
                          <div>
                            {/* View details, Prompt details buttons, reconfigure button */}
                            <div className="flex mb-4">
                              <div
                                className="flex items-center text-blue-500 cursor-pointer mr-6"
                                style={{marginRight: '6px'}}
                                onClick={() => handleViewEvaluatorDetail(evaluatorId)}
                              >
                                <EyeOutlined className="mr-1" /> View Details
                              </div>
                              <div
                                className="text-blue-500 cursor-pointer mr-6"
                                style={{marginRight: '6px'}}
                                onClick={handleViewPromptDetail}
                              >
                                Prompt Details
                              </div>
                              <div
                                className="text-blue-500 cursor-pointer"
                                onClick={() => {
                                  // Use instance key to toggle config mode
                                  setEvaluatorConfigMode(prev => ({
                                    ...prev,
                                    [instanceKey]: true
                                  }));

                                  // Clear current evaluator instance version selection
                                  setSelectedEvaluatorVersions(prev => ({
                                    ...prev,
                                    [instanceKey]: ''
                                  }));
                                }}
                              >
                                Reconfigure
                              </div>
                            </div>

                            {/* Evaluator info display */}
                            <div className="bg-white rounded-lg p-4 mb-4">
                              <div className="grid grid-cols-1 gap-2">
                                <div>
                                  <span className="text-gray-600">Description:</span>
                                  <span className="ml-2 text-gray-900">
                                    {evaluators.find(e => e.id.toString() === evaluatorId)?.description || '-'}
                                  </span>
                                </div>
                                <div>
                                  <span className="text-gray-600">Model:</span>
                                  <span className="ml-2">
                                    <span className="inline-block bg-blue-100 text-blue-800 text-xs px-2 py-1 rounded">
                                      {evaluators.find(e => e.id.toString() === evaluatorId)?.modelName || '-'}
                                    </span>
                                  </span>
                                </div>
                              </div>
                            </div>

                            {/* Field mapping configuration */}
                            <div className="bg-white rounded-lg p-4">
                              <h4 className="text-base font-medium text-gray-900 mb-4">Field Mapping Configuration</h4>
                              <div className="space-y-3">
                                {/* Mapping table header */}
                                <div className="grid grid-cols-3 gap-4 font-medium text-gray-700">
                                  <div>Evaluator Parameter</div>
                                  <div style={{textAlign: 'center'}}>Mapping</div>
                                  <div>Data Source</div>
                                </div>

                                {/* Mapping items */}
                                {evaluatorParams[evaluatorId] && evaluatorParams[evaluatorId].length > 0 ? (
                                  evaluatorParams[evaluatorId].map((param) => {
                                    // Get data source fields
                                    const dataSourceFields = getDataSourceFields();
                                    // Get current mapping value
                                    const currentMapping = evaluatorParamMappings[evaluatorId]?.[param] || dataSourceFields[0]?.field || 'input';
                                    // Find current selected data source field object
                                    const currentDataSourceField = dataSourceFields.find(item => item.field === currentMapping);

                                    return (
                                      <div key={param} className="grid grid-cols-3 gap-4 items-center">
                                        <div>
                                          <Input
                                            value={param}
                                            readOnly
                                            className="w-full"
                                          />
                                        </div>
                                        <div className="text-center">
                                          <span className="text-gray-500">↔</span>
                                        </div>
                                        <div>
                                          <Select
                                            defaultValue={currentMapping}
                                            value={currentMapping}
                                            onChange={(value) => handleEvaluatorParamMappingChange(evaluatorId, param, value)}
                                            className="w-full"
                                            showSearch
                                            optionFilterProp="children"
                                          >
                                            {dataSourceFields.map(field => (
                                              <Option key={field.field} value={field.field}>
                                                {field.displayName}
                                              </Option>
                                            ))}
                                          </Select>
                                        </div>
                                      </div>
                                    );
                                  })
                                ) : (
                                  <div className="text-center py-4 text-gray-500">
                                    This evaluator has no parameters to map
                                  </div>
                                )}
                              </div>
                            </div>

                            {/* Field mapping instructions */}
                            <div className="mt-6 bg-blue-50 rounded-lg p-4">
                              <div className="flex items-start">
                                <span className="mr-2 text-blue-500 text-xl">💡</span>
                                <div>
                                  <div className="text-base font-medium text-gray-900 mb-2">Mapping Instructions:</div>
                                  <ul className="list-disc pl-5 space-y-1 text-sm text-gray-600">
                                    <li>Evaluator parameters are mapped to corresponding data source fields</li>
                                    <li>Available data sources include dataset fields and evaluation output (actual_output)</li>
                                    <li>Ensure each mapping is reasonable for proper evaluation</li>
                                  </ul>
                                </div>
                              </div>
                            </div>

                          </div>
                        ) : (
                          // Config mode: show evaluator and version selection
                          <div>
                            <div className="grid grid-cols-2 gap-4">
                              {/* Select evaluator */}
                              <div>
                                <div className="mb-1 text-sm" style={{color: 'rgba(0, 0, 0, 0.85)'}}>
                                  <span className="text-red-500">*</span> Select Evaluator
                                </div>
                                <Select
                                  className="w-full"
                                  placeholder="Select evaluator"
                                  value={evaluator.evaluatorId || undefined}
                                  onChange={(value) => {
                                    // Update evaluator ID
                                    const newEvaluators = [...selectedEvaluators];
                                    newEvaluators[index] = { ...newEvaluators[index], evaluatorId: value };
                                    setSelectedEvaluators(newEvaluators);

                                    // Get version list for this evaluator
                                    handleEvaluatorSelectChange(index, value);
                                  }}
                                  loading={evaluatorsLoading}
                                  notFoundContent={evaluatorsLoading ? 'Loading...' : 'No data available'}
                                >
                                  {evaluators.map(e => (
                                    <Option key={e.id} value={e.id.toString()}>
                                      {e.name}
                                    </Option>
                                  ))}
                                </Select>
                              </div>

                              {/* Select version */}
                              <div>
                                <div className="mb-1 text-sm" style={{color: 'rgba(0, 0, 0, 0.85)'}}>
                                  <span className="text-red-500">*</span> Select Version
                                </div>
                                <Select
                                  className="w-full"
                                  placeholder="Select version"
                                  value={evaluator.evaluatorId ? selectedEvaluatorVersions[instanceKey] : undefined}
                                  onChange={(value) => handleEvaluatorVersionChange(index, evaluator.evaluatorId, value)}
                                  disabled={!evaluator.evaluatorId}
                                  loading={evaluator.evaluatorId ? evaluatorVersionsLoading[evaluator.evaluatorId] : false}
                                  notFoundContent={
                                    !evaluator.evaluatorId ? 'Please select evaluator first' :
                                    evaluatorVersionsLoading[evaluator.evaluatorId] ? 'Loading...' :
                                    evaluatorVersions[evaluator.evaluatorId]?.length === 0 ? 'No version data' : 'Please select version'
                                  }
                                >
                                    {evaluator.evaluatorId && evaluatorVersions[evaluator.evaluatorId]?.map((version: any) => {
                                      console.log(version, 'zxc...')
                                      return (
                                        <Option key={version.id} value={version.id.toString()}>
                                          {version.version} - {version.description}
                                        </Option>
                                      )
                                    })}
                                </Select>
                              </div>
                            </div>

                            {/* View details button */}
                            {evaluator.evaluatorId && (
                              <div className="mt-4 flex justify-end">
                                <div
                                  className="flex items-center text-blue-500 cursor-pointer"
                                  onClick={() => handleViewEvaluatorDetail(evaluator.evaluatorId)}
                                >
                                  <EyeOutlined className="mr-1" /> View Details
                                </div>
                              </div>
                            )}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>
            )}
          </Card>
        </Form>
      </div>

      {/* Bottom action buttons - fixed at bottom */}
      <div className="experiment-create-footer">
        <div className="flex justify-end space-x-4">
          <Button size="large" onClick={handleCancel}>
            Cancel
          </Button>
          <Button
            type="primary"
            size="large"
            htmlType="submit"
            loading={loading}
            onClick={() => form.submit()}
          >
            Start Experiment
          </Button>
        </div>
      </div>
    </div>
  );
};

export default ExperimentCreate;
