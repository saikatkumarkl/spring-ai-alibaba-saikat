-- Migrate from Tongyi to Ollama
DELETE FROM model WHERE provider = 'Tongyi';
DELETE FROM provider WHERE provider = 'Tongyi';

-- Ollama provider (models are auto-discovered at runtime from the endpoint)
INSERT INTO provider (workspace_id, icon, name, description, provider, enable, source, credential, supported_model_types, protocol, gmt_create, gmt_modified, creator, modifier) VALUES ('1', null, 'Ollama', 'Ollama local LLM server', 'ollama', 1, 'preset', '{"endpoint":"http://ollama:11434"}', null, 'OpenAI', now(), now(), null, null);
-- No model rows needed — ModelController auto-syncs models from Ollama's /api/tags
