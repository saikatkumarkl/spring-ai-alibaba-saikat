import ProCard from '@/components/Card/ProCard';
import InnerLayout from '@/components/InnerLayout';
import $i18n from '@/i18n';
import React from 'react';
import { useNavigate } from 'react-router-dom';
import styles from './index.module.less';

const HomePage: React.FC = () => {
  const navigate = useNavigate();

  const handleFirstCardClick = () => {
    // Navigate to the original app list page
    navigate('/app');
  };

  const handleSecondCardClick = () => {
    // Navigate to Dify converter page
    navigate('/dify');
  };

  const handleThirdCardClick = () => {
    // Navigate to debugging tools page
    navigate('/debug');
  };

  const handleCopilotCardClick = () => {
    // TODO: Navigate to Copilot creation page
    console.log('Navigate to Copilot creation page');
  };

  const handleGraphDebugCardClick = () => {
    // TODO: Navigate to Graph workflow debug page
    console.log('Navigate to Graph workflow debug page');
  };

  const handleAgentManagementCardClick = () => {
    // TODO: Navigate to Agent management page (not implemented yet)
    console.log('Agent management feature not implemented yet');
  };

  const handleTracingCardClick = () => {
    // TODO: Navigate to Tracing page (not implemented yet)
    console.log('Tracing feature not implemented yet');
  };

  const handleEvaluationCardClick = () => {
    // TODO: Navigate to Evaluation page (not implemented yet)
    console.log('Evaluation feature not implemented yet');
  };

  const handlePromptEngineeringCardClick = () => {
    // TODO: Navigate to Prompt Engineering page (not implemented yet)
    console.log('Prompt Engineering feature not implemented yet');
  };

  const handleGithubImportCardClick = () => {
    // TODO: Navigate to GitHub import page (not implemented yet)
    console.log('GitHub import feature not implemented yet');
  };

  const handleAgentSchemaCardClick = () => {
    // Navigate to Agent Schema creation page
    navigate('/agent-schema');
  };

  return (
    <InnerLayout
      breadcrumbLinks={[
        {
          title: $i18n.get({
            id: 'main.pages.App.index.home',
            dm: 'Home',
          }),
        },
      ]}
    >
      <div className={styles.homeContainer}>
        {/* Section 1: Create Agent */}
        <div className={styles.section}>
          <h2 className={styles.sectionTitle}>Create Agent</h2>
          <div className={styles.cardGrid}>
            <div className={styles.cardItem}>
              <ProCard
                title="Low-Code Platform Agent Development"
                logo={
                  <div className={styles.cardIcon}>
                    <img
                      src="/images/agentLogo.svg"
                      alt="Spring AI Alibaba Platform"
                      className={styles.iconImage}
                    />
                  </div>
                }
                info={[
                  {
                    content: $i18n.get({
                      id: 'main.pages.App.index.platformDescription',
                      dm: 'Provides visual agent development, debugging, deployment, and export capabilities, supporting chat assistant, workflow, and other modes.',
                    }),
                  },
                ]}
                onClick={handleFirstCardClick}
                className={styles.clickableCard}
              />
            </div>

            <div className={styles.cardItem}>
              <ProCard
                title="Convert DIFY App to SAA Project"
                logo={
                  <div className={styles.cardIcon}>
                    <img
                      src="/images/workflowLogo.svg"
                      alt="Dify DSL Generation"
                      className={styles.iconImage}
                    />
                  </div>
                }
                info={[
                  {
                    content: $i18n.get({
                      id: 'main.pages.App.index.difyDescription',
                      dm: 'Convert agents developed on Dify platform to Spring AI Alibaba applications, and then import them into IDE for development and maintenance.',
                    }),
                  },
                ]}
                onClick={handleSecondCardClick}
                className={styles.clickableCard}
              />
            </div>

            <div className={styles.cardItem}>
              <ProCard
                title="Create Agent with Copilot"
                logo={
                  <div className={styles.cardIcon}>
                    <img
                      src="/images/copilot.svg"
                      alt="Copilot Agent Creation"
                      className={styles.iconImage}
                    />
                  </div>
                }
                info={[
                  {
                    content: 'Quickly create and configure agents through AI Copilot intelligent guidance, providing natural language interaction for agent development experience',
                  },
                ]}
                onClick={handleCopilotCardClick}
                className={styles.clickableCard}
              />
            </div>

            <div className={styles.cardItem}>
              <ProCard
                title="Create Agent via Agent Schema"
                logo={
                  <div className={styles.cardIcon}>
                    <img
                      src="/images/agentSchema.svg"
                      alt="Agent Schema Agent Creation"
                      className={styles.iconImage}
                    />
                  </div>
                }
                info={[
                  {
                    content: 'Quickly create and configure agents through Agent Schema',
                  },
                ]}
                onClick={handleAgentSchemaCardClick}
                className={styles.clickableCard}
              />
            </div>

            <div className={styles.cardItem}>
              <ProCard
                title="Import Agent from GitHub"
                logo={
                  <div className={styles.cardIconDisabled}>
                    <img
                      src="/images/github.svg"
                      alt="GitHub Import"
                      className={styles.iconImage}
                    />
                  </div>
                }
                info={[
                  {
                    content: $i18n.get({
                      id: 'main.pages.App.index.githubImportDescription',
                      dm: 'Import existing agent projects from GitHub repositories, supporting automatic recognition of various project structures and configuration files.',
                    }),
                  },
                ]}
                onClick={handleGithubImportCardClick}
                className={styles.disabledCard}
              />
            </div>
          </div>
        </div>

        {/* Section 2: Debug Agent */}
        <div className={styles.section}>
          <h2 className={styles.sectionTitle}>Debug Agent</h2>
          <div className={styles.cardGrid}>
            <div className={styles.cardItem}>
              <ProCard
                title="Agent Chat UI"
                logo={
                  <div className={styles.cardIcon}>
                    <img
                      src="/images/tool.svg"
                      alt="Debug Tools"
                      className={styles.iconImage}
                    />
                  </div>
                }
                info={[
                  {
                    content: $i18n.get({
                      id: 'main.pages.App.index.debugDescription',
                      dm: 'Interactive UI chat interface compatible with AG-UI specification, supporting conversation and debugging with agents.',
                    }),
                  },
                ]}
                onClick={handleThirdCardClick}
                className={styles.clickableCard}
              />
            </div>

            <div className={styles.cardItem}>
              <ProCard
                title="Graph Visual Debugging"
                logo={
                  <div className={styles.cardIcon}>
                    <img
                      src="/images/graph.svg"
                      alt="Graph Workflow Debug"
                      className={styles.iconImage}
                    />
                  </div>
                }
                info={[
                  {
                    content: 'Visually display the execution process of Graph workflows, providing node status monitoring, data flow tracking, and other features.',
                  },
                ]}
                onClick={handleGraphDebugCardClick}
                className={styles.clickableCard}
              />
            </div>

            <div className={styles.cardItem}>
              <ProCard
                title="Tracing"
                logo={
                  <div className={styles.cardIconDisabled}>
                    <img
                      src="/images/tracing.svg"
                      alt="Tracing"
                      className={styles.iconImage}
                    />
                  </div>
                }
                info={[
                  {
                    content: 'Full-chain tracing of agent execution process, providing detailed call chain analysis and performance monitoring',
                  },
                ]}
                onClick={handleTracingCardClick}
                className={styles.disabledCard}
              />
            </div>

            <div className={styles.cardItem}>
              <ProCard
                title="Evaluation"
                logo={
                  <div className={styles.cardIconDisabled}>
                    <img
                      src="/images/evaluation.svg"
                      alt="Evaluation"
                      className={styles.iconImage}
                    />
                  </div>
                }
                info={[
                  {
                    content: 'Agent effectiveness evaluation and testing framework, supporting multi-dimensional evaluation metrics and automated testing',
                  },
                ]}
                onClick={handleEvaluationCardClick}
                className={styles.disabledCard}
              />
            </div>

            <div className={styles.cardItem}>
              <ProCard
                title="Prompt Engineering"
                logo={
                  <div className={styles.cardIconDisabled}>
                    <img
                      src="/images/prompt.svg"
                      alt="Prompt Engineering"
                      className={styles.iconImage}
                    />
                  </div>
                }
                info={[
                  {
                    content: 'Prompt engineering and optimization tools, supporting prompt template management, testing, and effectiveness analysis',
                  },
                ]}
                onClick={handlePromptEngineeringCardClick}
                className={styles.disabledCard}
              />
            </div>
          </div>
        </div>

        {/* Section 3: Agent Management */}
        <div className={styles.section}>
          <h2 className={styles.sectionTitle}>Agent Management</h2>
          <div className={styles.cardGrid}>
            <div className={styles.cardItem}>
              <ProCard
                title="Agent Management"
                logo={
                  <div className={styles.cardIconDisabled}>
                    <img
                      src="/images/management.svg"
                      alt="Agent Management"
                      className={styles.iconImage}
                    />
                  </div>
                }
                info={[
                  {
                    content: 'Centrally manage agent lifecycle, including creation, configuration, deployment, monitoring, and version control features',
                  },
                ]}
                onClick={handleAgentManagementCardClick}
                className={styles.disabledCard}
              />
            </div>
          </div>
        </div>
      </div>
    </InnerLayout>
  );
};

export default HomePage;
