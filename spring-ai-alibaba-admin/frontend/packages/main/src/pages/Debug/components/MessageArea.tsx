import React, { useEffect, useRef } from 'react';
import { Button } from 'antd';
import { SettingOutlined, ClearOutlined } from '@ant-design/icons';
import { useChatContext } from '../contexts/ChatContext';
import { useConfigContext } from '../contexts/ConfigContext';
import MessageList from './MessageList';
import MessageInput from './MessageInput';
import styles from '../index.module.less';

const MessageArea: React.FC = () => {
  const { currentSession } = useChatContext();
  const { config, toggleDebugInfo } = useConfigContext();
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    if (config.autoScroll && messagesEndRef.current) {
      messagesEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  };

  useEffect(() => {
    scrollToBottom();
  }, [currentSession?.messages, config.autoScroll]);

  const handleClearChat = () => {
    if (currentSession && window.confirm('Are you sure you want to clear the current conversation?')) {
      // Clear messages in current session
      // This would need to be implemented in the context
    }
  };

  return (
    <>
      <div className={styles.chatHeader}>
        <h3 className={styles.chatTitle}>
          {currentSession ? currentSession.title : 'Select or create a conversation'}
        </h3>
        <div className={styles.headerActions}>
          <Button
            type="text"
            icon={<SettingOutlined />}
            onClick={toggleDebugInfo}
            size="small"
            title="Debug Panel"
          />
          <Button
            type="text"
            icon={<ClearOutlined />}
            onClick={handleClearChat}
            size="small"
            title="Clear Conversation"
            disabled={!currentSession || currentSession.messages.length === 0}
          />
        </div>
      </div>

      <div className={styles.messageContainer}>
        {currentSession ? (
          <>
            <MessageList messages={currentSession.messages} />
            <div ref={messagesEndRef} />
          </>
        ) : (
          <div style={{
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            height: '100%',
            flexDirection: 'column',
            color: '#999'
          }}>
            <div style={{ fontSize: 16, marginBottom: 8 }}>🤖</div>
            <div>Welcome to Agent Chat UI</div>
            <div style={{ fontSize: 12, marginTop: 4 }}>Please create or select a conversation to start chatting</div>
          </div>
        )}
      </div>

      {currentSession && (
        <div className={styles.inputArea}>
          <MessageInput />
        </div>
      )}
    </>
  );
};

export default MessageArea;
