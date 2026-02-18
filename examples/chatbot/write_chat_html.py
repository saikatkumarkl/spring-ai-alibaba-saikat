#!/usr/bin/env python3
"""Write the new dual-mode chat.html file."""
import os

content = r'''<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>AI Chatbot</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
            background: #f5f5f5; height: 100vh; display: flex; flex-direction: column;
        }
        .header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white; padding: 12px 24px; display: flex; justify-content: space-between;
            align-items: center; box-shadow: 0 2px 8px rgba(0,0,0,0.1); flex-shrink: 0;
        }
        .header h1 { font-size: 20px; font-weight: 600; }
        .user-info { display: flex; align-items: center; gap: 12px; }
        .user-email { font-size: 14px; opacity: 0.9; }
        .header-btn {
            background: rgba(255,255,255,0.2); color: white; border: 1px solid rgba(255,255,255,0.3);
            padding: 7px 14px; border-radius: 6px; cursor: pointer; font-size: 13px; transition: all 0.3s;
        }
        .header-btn:hover { background: rgba(255,255,255,0.3); }
        .app-selector {
            background: rgba(255,255,255,0.2); color: white; border: 1px solid rgba(255,255,255,0.3);
            padding: 7px 12px; border-radius: 6px; font-size: 13px; cursor: pointer; max-width: 180px;
        }
        .app-selector option { color: #333; background: white; }

        /* Mode Tabs */
        .mode-tabs {
            display: flex; background: white; border-bottom: 2px solid #e0e0e0; flex-shrink: 0;
        }
        .mode-tab {
            flex: 1; padding: 12px 20px; text-align: center; cursor: pointer; font-size: 14px;
            font-weight: 500; color: #666; border-bottom: 3px solid transparent; transition: all 0.3s;
        }
        .mode-tab:hover { background: #f9f9ff; color: #667eea; }
        .mode-tab.active { color: #667eea; border-bottom-color: #667eea; background: #f9f9ff; }

        /* Main Content */
        .main-content { flex: 1; display: flex; overflow: hidden; }

        /* ========== AI Chat Mode ========== */
        .chat-mode { flex: 1; display: flex; overflow: hidden; }
        .chat-mode.hidden { display: none; }
        .sidebar {
            width: 240px; background: white; border-right: 1px solid #e0e0e0;
            display: flex; flex-direction: column; flex-shrink: 0;
        }
        .sidebar-header {
            padding: 14px 16px; font-weight: 600; font-size: 13px; color: #555;
            border-bottom: 1px solid #e0e0e0; text-transform: uppercase; letter-spacing: 0.5px;
        }
        .conversations-list { flex: 1; overflow-y: auto; padding: 8px; }
        .conversation-item {
            padding: 10px 12px; border-radius: 8px; cursor: pointer; margin-bottom: 3px;
            transition: all 0.2s; font-size: 13px; position: relative;
        }
        .conversation-item:hover { background: #f0f0f0; }
        .conversation-item:hover .conv-delete { opacity: 1; }
        .conv-delete {
            position: absolute; right: 8px; top: 50%; transform: translateY(-50%);
            background: none; border: none; cursor: pointer; font-size: 13px; color: #c33;
            opacity: 0; transition: opacity 0.2s; padding: 3px 5px; border-radius: 4px;
        }
        .conv-delete:hover { background: #fee; }
        .conversation-item.active {
            background: linear-gradient(135deg, #667eea20 0%, #764ba220 100%);
            border-left: 3px solid #667eea;
        }
        .conv-preview { color: #333; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        .conv-meta { color: #999; font-size: 11px; margin-top: 3px; }
        .no-conversations { padding: 20px 16px; text-align: center; color: #999; font-size: 13px; }
        .chat-container { flex: 1; display: flex; flex-direction: column; background: white; min-width: 0; }
        .messages {
            flex: 1; overflow-y: auto; padding: 20px; display: flex; flex-direction: column; gap: 14px;
        }
        .message { display: flex; gap: 10px; max-width: 80%; animation: fadeIn 0.3s ease; }
        @keyframes fadeIn { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }
        .message.user { align-self: flex-end; flex-direction: row-reverse; }
        .message-avatar {
            width: 32px; height: 32px; border-radius: 50%; display: flex; align-items: center;
            justify-content: center; font-size: 16px; flex-shrink: 0;
        }
        .message.user .message-avatar { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
        .message.assistant .message-avatar { background: #e0e0e0; }
        .message-content { padding: 10px 14px; border-radius: 12px; line-height: 1.5; word-wrap: break-word; }
        .message.user .message-content {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; border-bottom-right-radius: 4px;
        }
        .message.assistant .message-content { background: #f0f0f0; color: #333; border-bottom-left-radius: 4px; }
        .typing-indicator { display: flex; gap: 4px; padding: 10px 14px; }
        .typing-indicator span { width: 7px; height: 7px; background: #999; border-radius: 50%; animation: typing 1.4s infinite; }
        .typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
        .typing-indicator span:nth-child(3) { animation-delay: 0.4s; }
        @keyframes typing { 0%,60%,100% { transform: translateY(0); opacity: 0.7; } 30% { transform: translateY(-8px); opacity: 1; } }
        .input-area { padding: 12px 20px; border-top: 1px solid #e0e0e0; background: white; }
        .input-form { display: flex; gap: 10px; }
        .input-field {
            flex: 1; padding: 10px 16px; border: 2px solid #e0e0e0; border-radius: 24px;
            font-size: 14px; font-family: inherit; transition: all 0.3s;
        }
        .input-field:focus { outline: none; border-color: #667eea; }
        .send-btn {
            padding: 10px 20px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white; border: none; border-radius: 24px; font-size: 14px; font-weight: 600;
            cursor: pointer; transition: all 0.3s;
        }
        .send-btn:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(102,126,234,0.4); }
        .send-btn:disabled { opacity: 0.5; cursor: not-allowed; }

        /* ========== Document Explorer Mode ========== */
        .doc-mode { flex: 1; display: flex; flex-direction: column; overflow: hidden; background: white; }
        .doc-mode.hidden { display: none; }

        .doc-toolbar {
            display: flex; gap: 10px; padding: 14px 20px; border-bottom: 1px solid #e0e0e0;
            align-items: center; flex-wrap: wrap; background: #fafafa;
        }
        .doc-search-input {
            flex: 1; min-width: 200px; padding: 9px 14px; border: 2px solid #e0e0e0;
            border-radius: 8px; font-size: 14px; transition: border-color 0.3s;
        }
        .doc-search-input:focus { outline: none; border-color: #667eea; }
        .doc-search-btn {
            padding: 9px 18px; background: linear-gradient(135deg, #667eea, #764ba2);
            color: white; border: none; border-radius: 8px; font-size: 13px; font-weight: 600;
            cursor: pointer; transition: all 0.3s;
        }
        .doc-search-btn:hover { transform: translateY(-1px); box-shadow: 0 3px 10px rgba(102,126,234,0.3); }

        .doc-content { flex: 1; display: flex; overflow: hidden; }

        /* Facets Panel */
        .facets-panel {
            width: 220px; background: #fafafa; border-right: 1px solid #e0e0e0;
            overflow-y: auto; padding: 14px; flex-shrink: 0;
        }
        .facet-group { margin-bottom: 18px; }
        .facet-title { font-size: 12px; font-weight: 600; color: #555; text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 8px; }
        .facet-item {
            display: flex; justify-content: space-between; align-items: center; padding: 5px 8px;
            border-radius: 4px; cursor: pointer; font-size: 13px; transition: background 0.2s;
        }
        .facet-item:hover { background: #e8e8ff; }
        .facet-item.active { background: #667eea20; color: #667eea; font-weight: 600; }
        .facet-count {
            background: #e0e0e0; border-radius: 10px; padding: 1px 7px; font-size: 11px; color: #666;
        }
        .facet-item.active .facet-count { background: #667eea30; color: #667eea; }

        /* Document List */
        .doc-list { flex: 1; overflow-y: auto; padding: 0; }
        .doc-item {
            display: flex; align-items: flex-start; gap: 14px; padding: 14px 20px;
            border-bottom: 1px solid #f0f0f0; cursor: pointer; transition: background 0.2s;
        }
        .doc-item:hover { background: #f9f9ff; }
        .doc-icon { font-size: 28px; flex-shrink: 0; margin-top: 2px; }
        .doc-info { flex: 1; min-width: 0; }
        .doc-name { font-weight: 600; font-size: 14px; color: #333; word-break: break-word; }
        .doc-meta { display: flex; gap: 14px; flex-wrap: wrap; margin-top: 4px; font-size: 12px; color: #888; }
        .doc-meta span { display: flex; align-items: center; gap: 3px; }

        /* Document Detail Modal */
        .doc-detail-overlay {
            display: none; position: fixed; top: 0; left: 0; right: 0; bottom: 0;
            background: rgba(0,0,0,0.5); z-index: 1000; justify-content: center; align-items: center;
        }
        .doc-detail-overlay.show { display: flex; }
        .doc-detail-panel {
            background: white; border-radius: 12px; width: 600px; max-height: 80vh;
            overflow-y: auto; box-shadow: 0 20px 60px rgba(0,0,0,0.3); padding: 24px;
        }
        .doc-detail-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
        .doc-detail-title { font-size: 18px; font-weight: 700; color: #333; word-break: break-word; flex: 1; }
        .doc-detail-close {
            background: none; border: none; font-size: 24px; cursor: pointer; color: #999;
            padding: 4px 8px; border-radius: 4px;
        }
        .doc-detail-close:hover { background: #f0f0f0; color: #333; }
        .doc-detail-section { margin-bottom: 16px; }
        .doc-detail-section h3 { font-size: 13px; color: #667eea; text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 8px; }
        .doc-detail-table { width: 100%; border-collapse: collapse; }
        .doc-detail-table td {
            padding: 6px 10px; font-size: 13px; border-bottom: 1px solid #f0f0f0; vertical-align: top;
        }
        .doc-detail-table td:first-child { font-weight: 600; color: #555; width: 40%; white-space: nowrap; }
        .doc-detail-table td:last-child { color: #333; word-break: break-word; }

        /* Pagination */
        .doc-pagination {
            display: flex; justify-content: center; align-items: center; gap: 8px;
            padding: 12px; border-top: 1px solid #e0e0e0; background: #fafafa;
        }
        .page-btn {
            padding: 6px 14px; border: 1px solid #ddd; border-radius: 6px; background: white;
            cursor: pointer; font-size: 13px; transition: all 0.2s;
        }
        .page-btn:hover:not(:disabled) { border-color: #667eea; color: #667eea; }
        .page-btn:disabled { opacity: 0.4; cursor: not-allowed; }
        .page-info { font-size: 13px; color: #666; }

        /* Error banner */
        .error-banner {
            background: #fee; color: #c33; padding: 10px 24px; text-align: center;
            border-bottom: 2px solid #c33; display: none;
        }
        .error-banner.show { display: block; }

        /* Empty state */
        .empty-state { padding: 60px 20px; text-align: center; color: #999; }
        .empty-state .empty-icon { font-size: 48px; margin-bottom: 16px; }
        .empty-state h3 { font-size: 16px; color: #666; margin-bottom: 8px; }
        .empty-state p { font-size: 13px; }

        .doc-stats { font-size: 12px; color: #888; white-space: nowrap; }
    </style>
</head>
<body>
    <div class="header">
        <h1>AI Chatbot</h1>
        <div class="user-info">
            <select id="appSelector" class="app-selector" title="Select App">
                <option value="">Loading apps...</option>
            </select>
            <button class="header-btn" onclick="startNewChat()">+ New Chat</button>
            <span class="user-email" id="userEmail">Loading...</span>
            <button class="header-btn" onclick="logout()">Logout</button>
        </div>
    </div>

    <div class="error-banner" id="errorBanner"></div>

    <!-- Mode Tabs -->
    <div class="mode-tabs">
        <div class="mode-tab active" data-mode="chat" onclick="switchMode('chat')">AI Chat</div>
        <div class="mode-tab" data-mode="documents" onclick="switchMode('documents')">Document Explorer</div>
    </div>

    <div class="main-content">
        <!-- ========== AI Chat Mode ========== -->
        <div class="chat-mode" id="chatMode">
            <div class="sidebar" id="sidebar">
                <div class="sidebar-header">Chat History</div>
                <div class="conversations-list" id="conversationsList">
                    <div class="no-conversations">Select an app to see history</div>
                </div>
            </div>
            <div class="chat-container">
                <div class="messages" id="messages">
                    <div class="message assistant">
                        <div class="message-avatar">&#x1F916;</div>
                        <div class="message-content">Hello! I am your AI assistant. How can I help you today?</div>
                    </div>
                </div>
                <div class="input-area">
                    <form class="input-form" id="chatForm">
                        <input type="text" class="input-field" id="messageInput"
                            placeholder="Type your message..." autocomplete="off" required>
                        <button type="submit" class="send-btn" id="sendBtn">Send</button>
                    </form>
                </div>
            </div>
        </div>

        <!-- ========== Document Explorer Mode ========== -->
        <div class="doc-mode hidden" id="docMode">
            <div class="doc-toolbar">
                <input type="text" class="doc-search-input" id="docSearchInput"
                    placeholder="Search documents by content or filename..." onkeydown="if(event.key==='Enter')searchDocs()">
                <button class="doc-search-btn" onclick="searchDocs()">Search</button>
                <button class="doc-search-btn" onclick="clearDocSearch()" style="background:#888;">Clear</button>
                <span class="doc-stats" id="docStats"></span>
            </div>
            <div class="doc-content">
                <div class="facets-panel" id="facetsPanel">
                    <div class="facet-group">
                        <div class="facet-title">File Type</div>
                        <div id="facetMimeType"><span style="color:#999;font-size:12px">Loading...</span></div>
                    </div>
                    <div class="facet-group">
                        <div class="facet-title">Created By</div>
                        <div id="facetCreatedBy"><span style="color:#999;font-size:12px">Loading...</span></div>
                    </div>
                </div>
                <div style="flex:1;display:flex;flex-direction:column;overflow:hidden;">
                    <div class="doc-list" id="docList">
                        <div class="empty-state">
                            <div class="empty-icon">&#x1F4C2;</div>
                            <h3>Select an app to browse documents</h3>
                            <p>Documents you have access to will appear here.</p>
                        </div>
                    </div>
                    <div class="doc-pagination" id="docPagination" style="display:none;">
                        <button class="page-btn" id="prevPageBtn" onclick="changePage(-1)">Previous</button>
                        <span class="page-info" id="pageInfo"></span>
                        <button class="page-btn" id="nextPageBtn" onclick="changePage(1)">Next</button>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <!-- Document Detail Modal -->
    <div class="doc-detail-overlay" id="docDetailOverlay" onclick="if(event.target===this)closeDocDetail()">
        <div class="doc-detail-panel" id="docDetailPanel"></div>
    </div>

    <script>
        // ==================== Global State ====================
        var messagesContainer = document.getElementById('messages');
        var chatForm = document.getElementById('chatForm');
        var messageInput = document.getElementById('messageInput');
        var sendBtn = document.getElementById('sendBtn');
        var userEmailSpan = document.getElementById('userEmail');
        var errorBanner = document.getElementById('errorBanner');
        var appSelector = document.getElementById('appSelector');
        var conversationsListEl = document.getElementById('conversationsList');

        var isTyping = false;
        var currentAppId = null;
        var userApps = [];
        var activeConversationId = null;
        var currentMode = 'chat';

        // Document explorer state
        var docCurrentPage = 0;
        var docPageSize = 20;
        var docTotalHits = 0;
        var docCurrentQuery = '';
        var docActiveMimeType = null;
        var docActiveCreatedBy = null;

        // ==================== Session & Init ====================
        function checkSession() {
            fetch('/api/auth/session')
                .then(function(response) { return response.json(); })
                .then(function(data) {
                    if (!data.success || !data.data) { window.location.href = '/index.html'; return; }
                    userEmailSpan.textContent = data.data.email || 'User';
                    userApps = data.data.apps || [];
                    appSelector.innerHTML = '';
                    if (userApps.length === 0) {
                        appSelector.innerHTML = '<option value="">No apps available</option>';
                        showError('No apps available. Contact administrator.'); return;
                    }
                    userApps.forEach(function(app) {
                        var opt = document.createElement('option');
                        opt.value = app.app_id;
                        opt.textContent = app.name || app.app_id;
                        appSelector.appendChild(opt);
                    });
                    currentAppId = userApps[0].app_id;
                    loadConversations(currentAppId);
                    if (currentMode === 'documents') loadDocuments();
                })
                .catch(function(error) { console.error('Session check failed:', error); window.location.href = '/index.html'; });
        }

        appSelector.addEventListener('change', function(e) {
            currentAppId = e.target.value;
            activeConversationId = null;
            loadConversations(currentAppId);
            fetch('/api/chat/new-conversation?appId=' + encodeURIComponent(currentAppId), { method: 'POST' });
            messagesContainer.innerHTML = '<div class="message assistant"><div class="message-avatar">&#x1F916;</div>' +
                '<div class="message-content">Switched to app: ' + escapeHtml(appSelector.options[appSelector.selectedIndex].text) + '. How can I help you?</div></div>';
            if (currentMode === 'documents') { resetDocFilters(); loadDocuments(); }
        });

        function logout() {
            fetch('/api/auth/logout', { method: 'POST' }).catch(function(){});
            window.location.href = '/index.html';
        }

        function showError(msg) {
            errorBanner.textContent = msg; errorBanner.classList.add('show');
            setTimeout(function() { errorBanner.classList.remove('show'); }, 5000);
        }

        // ==================== Mode Switching ====================
        function switchMode(mode) {
            currentMode = mode;
            document.querySelectorAll('.mode-tab').forEach(function(t) { t.classList.toggle('active', t.dataset.mode === mode); });
            document.getElementById('chatMode').classList.toggle('hidden', mode !== 'chat');
            document.getElementById('docMode').classList.toggle('hidden', mode !== 'documents');
            if (mode === 'documents' && currentAppId) loadDocuments();
        }

        // ==================== AI Chat Mode ====================
        function addMessage(role, content) {
            var div = document.createElement('div');
            div.className = 'message ' + role;
            div.innerHTML = '<div class="message-avatar">' + (role === 'user' ? '&#x1F464;' : '&#x1F916;') + '</div>' +
                '<div class="message-content"></div>';
            div.querySelector('.message-content').textContent = content;
            messagesContainer.appendChild(div);
            messagesContainer.scrollTop = messagesContainer.scrollHeight;
            return div.querySelector('.message-content');
        }

        function showTypingIndicator() {
            var div = document.createElement('div');
            div.className = 'message assistant'; div.id = 'typingIndicator';
            div.innerHTML = '<div class="message-avatar">&#x1F916;</div>' +
                '<div class="typing-indicator"><span></span><span></span><span></span></div>';
            messagesContainer.appendChild(div);
            messagesContainer.scrollTop = messagesContainer.scrollHeight;
        }

        function hideTypingIndicator() { var el = document.getElementById('typingIndicator'); if (el) el.remove(); }

        function loadConversations(appId) {
            if (!appId) return;
            fetch('/api/chat/conversations?appId=' + encodeURIComponent(appId))
                .then(function(resp) { return resp.json(); })
                .then(function(result) {
                    var convs = result.data || [];
                    if (convs.length === 0) {
                        conversationsListEl.innerHTML = '<div class="no-conversations">No chat history yet.<br>Start a conversation!</div>';
                        return;
                    }
                    conversationsListEl.innerHTML = '';
                    convs.forEach(function(conv) {
                        var item = document.createElement('div');
                        item.className = 'conversation-item' + (conv.conversation_id === activeConversationId ? ' active' : '');
                        item.dataset.conversationId = conv.conversation_id;
                        var date = new Date(conv.last_message_at || conv.started_at);
                        var dateStr = date.toLocaleDateString() + ' ' + date.toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'});
                        item.innerHTML = '<div class="conv-preview">Chat (' + conv.message_count + ' msgs)</div>' +
                            '<div class="conv-meta">' + dateStr + '</div>' +
                            '<button class="conv-delete" onclick="deleteConversation(event,\'' + conv.conversation_id + '\')" title="Delete">X</button>';
                        item.onclick = function(e) { if (!e.target.classList.contains('conv-delete')) loadConversationHistory(appId, conv.conversation_id, this); };
                        conversationsListEl.appendChild(item);
                    });
                })
                .catch(function(e) { console.error('Failed to load conversations:', e); });
        }

        function loadConversationHistory(appId, conversationId, itemEl) {
            activeConversationId = conversationId;
            document.querySelectorAll('.conversation-item').forEach(function(el) { el.classList.remove('active'); });
            if (itemEl) itemEl.classList.add('active');
            fetch('/api/chat/history?appId=' + encodeURIComponent(appId) + '&conversationId=' + encodeURIComponent(conversationId))
                .then(function(resp) { return resp.json(); })
                .then(function(result) {
                    var msgs = result.data || [];
                    messagesContainer.innerHTML = '';
                    if (msgs.length === 0) { addMessage('assistant', 'No messages in this conversation yet.'); return; }
                    msgs.forEach(function(msg) { addMessage(msg.role, msg.content); });
                })
                .catch(function(e) { console.error('Failed to load history:', e); showError('Failed to load conversation history'); });
        }

        function startNewChat() {
            if (!currentAppId) { showError('Please select an app first'); return; }
            activeConversationId = null;
            fetch('/api/chat/new-conversation?appId=' + encodeURIComponent(currentAppId), { method: 'POST' })
                .then(function() { loadConversations(currentAppId); });
            messagesContainer.innerHTML = '<div class="message assistant"><div class="message-avatar">&#x1F916;</div>' +
                '<div class="message-content">New conversation started. How can I help you?</div></div>';
            document.querySelectorAll('.conversation-item').forEach(function(el) { el.classList.remove('active'); });
        }

        chatForm.addEventListener('submit', function(e) {
            e.preventDefault();
            if (isTyping || !currentAppId) return;
            var message = messageInput.value.trim();
            if (!message) return;
            addMessage('user', message);
            messageInput.value = '';
            showTypingIndicator();
            isTyping = true; sendBtn.disabled = true;

            fetch('/api/chat/stream?message=' + encodeURIComponent(message) + '&appId=' + encodeURIComponent(currentAppId), {
                method: 'POST', headers: { 'Accept': 'text/event-stream' }
            })
            .then(function(resp) {
                if (!resp.ok) throw new Error('HTTP ' + resp.status);
                var contentDiv = null, fullResponse = '', firstChunk = false;
                var reader = resp.body.getReader();
                var decoder = new TextDecoder();
                function readChunk() {
                    return reader.read().then(function(result) {
                        if (result.done) {
                            if (!firstChunk) hideTypingIndicator();
                            if (fullResponse && fullResponse.indexOf('Error') !== 0) loadConversations(currentAppId);
                            isTyping = false; sendBtn.disabled = false; messageInput.focus();
                            return;
                        }
                        var chunk = decoder.decode(result.value, { stream: true });
                        var lines = chunk.split('\n');
                        for (var i = 0; i < lines.length; i++) {
                            var trimmed = lines[i].trim();
                            if (trimmed.indexOf('data:') !== 0) continue;
                            try {
                                var jsonStr = trimmed.indexOf('data: ') === 0 ? trimmed.substring(6) : trimmed.substring(5);
                                var data = JSON.parse(jsonStr);
                                if (data.error) {
                                    var errMsg = typeof data.error === 'object' ? (data.error.message || JSON.stringify(data.error)) : String(data.error);
                                    if (!firstChunk) { hideTypingIndicator(); firstChunk = true; }
                                    if (!contentDiv) contentDiv = addMessage('assistant', '');
                                    fullResponse = 'Error: ' + errMsg;
                                    contentDiv.textContent = fullResponse;
                                    contentDiv.style.color = '#c33';
                                    continue;
                                }
                                if (data.message && data.message.content && data.status !== 'completed') {
                                    if (!firstChunk) { hideTypingIndicator(); contentDiv = addMessage('assistant', ''); firstChunk = true; }
                                    fullResponse += data.message.content;
                                    contentDiv.textContent = fullResponse;
                                    messagesContainer.scrollTop = messagesContainer.scrollHeight;
                                } else if (data.choices && data.choices[0] && data.choices[0].delta) {
                                    if (!firstChunk) { hideTypingIndicator(); contentDiv = addMessage('assistant', ''); firstChunk = true; }
                                    fullResponse += data.choices[0].delta.content || '';
                                    contentDiv.textContent = fullResponse;
                                    messagesContainer.scrollTop = messagesContainer.scrollHeight;
                                }
                            } catch(ex) {}
                        }
                        return readChunk();
                    });
                }
                return readChunk();
            })
            .catch(function(error) {
                console.error('Chat error:', error);
                hideTypingIndicator();
                showError('Failed to send message. Please try again.');
                isTyping = false; sendBtn.disabled = false; messageInput.focus();
            });
        });

        function deleteConversation(event, conversationId) {
            event.stopPropagation();
            if (!confirm('Delete this conversation?')) return;
            fetch('/api/chat/conversation?conversationId=' + encodeURIComponent(conversationId), { method: 'DELETE' })
                .then(function(resp) {
                    if (resp.ok) {
                        if (conversationId === activeConversationId) {
                            activeConversationId = null;
                            messagesContainer.innerHTML = '<div class="message assistant"><div class="message-avatar">&#x1F916;</div>' +
                                '<div class="message-content">Conversation deleted. Start a new one!</div></div>';
                        }
                        loadConversations(currentAppId);
                    } else showError('Failed to delete conversation');
                })
                .catch(function(e) { console.error('Delete failed:', e); showError('Failed to delete conversation'); });
        }

        // ==================== Document Explorer Mode ====================
        function resetDocFilters() {
            docCurrentPage = 0; docCurrentQuery = ''; docActiveMimeType = null; docActiveCreatedBy = null;
            document.getElementById('docSearchInput').value = '';
        }

        function loadDocuments() {
            if (!currentAppId) return;
            var docList = document.getElementById('docList');
            docList.innerHTML = '<div class="empty-state"><div class="empty-icon">&#x231B;</div><h3>Loading documents...</h3></div>';
            var url = '/api/chat/documents?appId=' + encodeURIComponent(currentAppId) + '&from=' + (docCurrentPage * docPageSize) + '&size=' + docPageSize;
            if (docCurrentQuery) url += '&query=' + encodeURIComponent(docCurrentQuery);
            if (docActiveMimeType) url += '&mimeType=' + encodeURIComponent(docActiveMimeType);
            if (docActiveCreatedBy) url += '&createdBy=' + encodeURIComponent(docActiveCreatedBy);
            fetch(url)
                .then(function(resp) {
                    if (!resp.ok) throw new Error('HTTP ' + resp.status);
                    return resp.json();
                })
                .then(function(data) {
                    docTotalHits = data.total || 0;
                    renderDocuments(data.documents || []);
                    renderFacets(data.facets || {});
                    renderPagination();
                    document.getElementById('docStats').textContent = docTotalHits + ' document' + (docTotalHits !== 1 ? 's' : '') + ' found';
                })
                .catch(function(e) {
                    console.error('Failed to load documents:', e);
                    docList.innerHTML = '<div class="empty-state"><div class="empty-icon">&#x274C;</div><h3>Failed to load documents</h3><p>' + escapeHtml(e.message) + '</p></div>';
                });
        }

        function renderDocuments(docs) {
            var docList = document.getElementById('docList');
            if (docs.length === 0) {
                docList.innerHTML = '<div class="empty-state"><div class="empty-icon">&#x1F4ED;</div>' +
                    '<h3>No documents found</h3><p>' + (docCurrentQuery ? 'Try different search terms.' : 'No documents available for your account.') + '</p></div>';
                return;
            }
            var html = '';
            for (var i = 0; i < docs.length; i++) {
                var doc = docs[i];
                var icon = getFileIcon(doc.mimeType || '');
                var size = formatSize(parseInt(doc.fileSize) || 0);
                var date = doc.createdDate ? new Date(doc.createdDate).toLocaleDateString() : '';
                html += '<div class="doc-item" data-doc-index="' + i + '">' +
                    '<div class="doc-icon">' + icon + '</div>' +
                    '<div class="doc-info">' +
                    '<div class="doc-name">' + escapeHtml(doc.fileName || 'Unknown') + '</div>' +
                    '<div class="doc-meta">' +
                    '<span>&#x1F4C5; ' + date + '</span>' +
                    '<span>&#x1F464; ' + escapeHtml(doc.createdBy || '') + '</span>' +
                    '<span>&#x1F4BE; ' + size + '</span>' +
                    '<span>&#x1F4CE; ' + escapeHtml(shortMimeType(doc.mimeType || '')) + '</span>' +
                    '</div></div></div>';
            }
            docList.innerHTML = html;
            // Attach click handlers via closure
            var items = docList.querySelectorAll('.doc-item');
            items.forEach(function(item, idx) {
                item.onclick = function() { showDocDetail(docs[idx]); };
            });
        }

        function renderFacets(facets) {
            // Mime types
            var mimeEl = document.getElementById('facetMimeType');
            var mimeItems = facets.mime_types || [];
            if (mimeItems.length === 0) {
                mimeEl.innerHTML = '<span style="color:#999;font-size:12px">No data</span>';
            } else {
                var html = '';
                for (var i = 0; i < mimeItems.length; i++) {
                    var f = mimeItems[i];
                    var active = docActiveMimeType === f.value ? ' active' : '';
                    html += '<div class="facet-item' + active + '" data-facet-type="mimeType" data-facet-value="' + escapeAttr(f.value) + '">' +
                        '<span>' + getFileIcon(f.value) + ' ' + shortMimeType(f.value) + '</span>' +
                        '<span class="facet-count">' + f.count + '</span></div>';
                }
                mimeEl.innerHTML = html;
                mimeEl.querySelectorAll('.facet-item').forEach(function(el) {
                    el.onclick = function() { toggleFacet('mimeType', el.dataset.facetValue); };
                });
            }
            // Created by
            var cbEl = document.getElementById('facetCreatedBy');
            var cbItems = facets.created_by || [];
            if (cbItems.length === 0) {
                cbEl.innerHTML = '<span style="color:#999;font-size:12px">No data</span>';
            } else {
                var html2 = '';
                for (var j = 0; j < cbItems.length; j++) {
                    var g = cbItems[j];
                    var active2 = docActiveCreatedBy === g.value ? ' active' : '';
                    html2 += '<div class="facet-item' + active2 + '" data-facet-type="createdBy" data-facet-value="' + escapeAttr(g.value) + '">' +
                        '<span>&#x1F464; ' + escapeHtml(g.value) + '</span>' +
                        '<span class="facet-count">' + g.count + '</span></div>';
                }
                cbEl.innerHTML = html2;
                cbEl.querySelectorAll('.facet-item').forEach(function(el) {
                    el.onclick = function() { toggleFacet('createdBy', el.dataset.facetValue); };
                });
            }
        }

        function toggleFacet(type, value) {
            if (type === 'mimeType') {
                docActiveMimeType = docActiveMimeType === value ? null : value;
            } else if (type === 'createdBy') {
                docActiveCreatedBy = docActiveCreatedBy === value ? null : value;
            }
            docCurrentPage = 0;
            loadDocuments();
        }

        function renderPagination() {
            var pag = document.getElementById('docPagination');
            if (docTotalHits <= docPageSize) { pag.style.display = 'none'; return; }
            pag.style.display = 'flex';
            var totalPages = Math.ceil(docTotalHits / docPageSize);
            document.getElementById('prevPageBtn').disabled = docCurrentPage === 0;
            document.getElementById('nextPageBtn').disabled = docCurrentPage >= totalPages - 1;
            document.getElementById('pageInfo').textContent = 'Page ' + (docCurrentPage + 1) + ' of ' + totalPages;
        }

        function changePage(delta) {
            docCurrentPage += delta;
            if (docCurrentPage < 0) docCurrentPage = 0;
            loadDocuments();
        }

        function searchDocs() {
            docCurrentQuery = document.getElementById('docSearchInput').value.trim();
            docCurrentPage = 0;
            loadDocuments();
        }

        function clearDocSearch() {
            resetDocFilters();
            loadDocuments();
        }

        function showDocDetail(doc) {
            var panel = document.getElementById('docDetailPanel');
            var meta = doc.metadata || {};
            var skipKeys = { content:1, url:1, indexed:1, created:1, 'last-modified':1 };
            var metaRows = '';
            var keys = Object.keys(meta);
            for (var i = 0; i < keys.length; i++) {
                var key = keys[i];
                var val = meta[key];
                if (skipKeys[key] || val === null || val === undefined || val === '') continue;
                var displayKey = key.replace('cmis:', '').replace(/_/g, ' ');
                metaRows += '<tr><td>' + escapeHtml(displayKey) + '</td><td>' + escapeHtml(String(val)) + '</td></tr>';
            }
            panel.innerHTML =
                '<div class="doc-detail-header">' +
                '<div class="doc-detail-title">' + getFileIcon(doc.mimeType || '') + ' ' + escapeHtml(doc.fileName || 'Unknown') + '</div>' +
                '<button class="doc-detail-close" onclick="closeDocDetail()">X</button>' +
                '</div>' +
                '<div class="doc-detail-section">' +
                '<h3>Overview</h3>' +
                '<table class="doc-detail-table">' +
                '<tr><td>File Name</td><td>' + escapeHtml(doc.fileName || '') + '</td></tr>' +
                '<tr><td>MIME Type</td><td>' + escapeHtml(doc.mimeType || '') + '</td></tr>' +
                '<tr><td>File Size</td><td>' + formatSize(parseInt(doc.fileSize) || 0) + '</td></tr>' +
                '<tr><td>Created By</td><td>' + escapeHtml(doc.createdBy || '') + '</td></tr>' +
                '<tr><td>Created Date</td><td>' + (doc.createdDate ? new Date(doc.createdDate).toLocaleString() : '') + '</td></tr>' +
                '<tr><td>Last Modified</td><td>' + (doc.lastModified ? new Date(doc.lastModified).toLocaleString() : '') + '</td></tr>' +
                '<tr><td>Object ID</td><td style="font-family:monospace;font-size:11px">' + escapeHtml(doc.objectId || '') + '</td></tr>' +
                '</table></div>' +
                (metaRows ? '<div class="doc-detail-section"><h3>All Metadata</h3><table class="doc-detail-table">' + metaRows + '</table></div>' : '');
            document.getElementById('docDetailOverlay').classList.add('show');
        }

        function closeDocDetail() { document.getElementById('docDetailOverlay').classList.remove('show'); }

        // ==================== Utilities ====================
        function getFileIcon(mimeType) {
            if (!mimeType) return '&#x1F4C4;';
            if (mimeType.indexOf('pdf') >= 0) return '&#x1F4D5;';
            if (mimeType.indexOf('word') >= 0 || mimeType.indexOf('docx') >= 0) return '&#x1F4D8;';
            if (mimeType.indexOf('sheet') >= 0 || mimeType.indexOf('excel') >= 0 || mimeType.indexOf('xlsx') >= 0) return '&#x1F4D7;';
            if (mimeType.indexOf('presentation') >= 0 || mimeType.indexOf('pptx') >= 0) return '&#x1F4D9;';
            if (mimeType.indexOf('image') >= 0) return '&#x1F5BC;';
            if (mimeType.indexOf('video') >= 0) return '&#x1F3AC;';
            if (mimeType.indexOf('audio') >= 0) return '&#x1F3B5;';
            if (mimeType.indexOf('zip') >= 0 || mimeType.indexOf('tar') >= 0 || mimeType.indexOf('gzip') >= 0) return '&#x1F4E6;';
            return '&#x1F4C4;';
        }

        function shortMimeType(mime) {
            if (!mime) return '';
            var map = {
                'application/pdf': 'PDF',
                'application/octet-stream': 'Binary',
                'application/msword': 'Word',
                'application/vnd.openxmlformats-officedocument.wordprocessingml.document': 'Word',
                'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': 'Excel',
                'application/vnd.openxmlformats-officedocument.presentationml.presentation': 'PowerPoint',
                'text/plain': 'Text',
                'text/html': 'HTML',
                'image/jpeg': 'JPEG',
                'image/png': 'PNG'
            };
            return map[mime] || mime.split('/').pop().substring(0, 15);
        }

        function formatSize(bytes) {
            if (bytes < 1024) return bytes + ' B';
            if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
            return (bytes / 1048576).toFixed(1) + ' MB';
        }

        function escapeHtml(text) {
            var div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        }

        function escapeAttr(text) {
            return text.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/'/g, '&#39;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
        }

        // ==================== Init ====================
        checkSession();

        document.addEventListener('keydown', function(e) {
            if (e.key === 'Escape') closeDocDetail();
        });
    </script>
</body>
</html>
'''

target = os.path.join(os.path.dirname(__file__), 'src', 'main', 'resources', 'static', 'chat.html')
with open(target, 'w', encoding='utf-8') as f:
    f.write(content.lstrip('\n'))
print(f"Wrote {len(content.splitlines())} lines to {target}")
