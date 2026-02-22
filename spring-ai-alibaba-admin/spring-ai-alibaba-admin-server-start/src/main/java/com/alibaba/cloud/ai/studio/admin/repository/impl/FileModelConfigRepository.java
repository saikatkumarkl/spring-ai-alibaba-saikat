package com.alibaba.cloud.ai.studio.admin.repository.impl;

import com.alibaba.cloud.ai.studio.admin.entity.ModelConfigDO;
import com.alibaba.cloud.ai.studio.admin.repository.ModelConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Model configuration repository implementation based on YAML files. Search path: read the environment variable MODEL_CONFIG_FILE first; otherwise, the default ./model-config.yml supports WatchService to monitor hot updates (file changes in the same directory).
 * @author Sunrisea
 */
@Repository
@Slf4j
public class FileModelConfigRepository implements ModelConfigRepository, InitializingBean {
    
    private static final String ENV_KEY = "MODEL_CONFIG_FILE";
    
    private static final String DEFAULT_FILE = "model-config.yml";
    
    private final Environment environment;
    
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    
    private final AtomicReference<Map<Long, ModelConfigDO>> snapshot = new AtomicReference<>(new ConcurrentHashMap<>());
    
    private Path configPath;
    
    public FileModelConfigRepository(Environment environment) {
        this.environment = environment;
    }
    
    @Override
    public void afterPropertiesSet() throws Exception {
        resolveConfigPath();
        loadFileOrFail();
        startWatchService();
    }
    
    private void resolveConfigPath() {
        String configured = environment.getProperty(ENV_KEY);
        if (configured != null && !configured.isBlank()) {
            this.configPath = Paths.get(configured).toAbsolutePath().normalize();
        } else {
            this.configPath = Paths.get("./" + DEFAULT_FILE).toAbsolutePath().normalize();
        }
        log.info("Model configuration file path: {}", this.configPath);
    }
    
    private void loadFileOrFail() {
        try {
            if (!Files.exists(this.configPath)) {
                this.snapshot.set(Collections.unmodifiableMap(new HashMap<>()));
                log.warn("Model configuration file not detected: {}, started with empty configuration (the file can be created later to trigger hot reloading)", this.configPath);
                return;
            }
            Map<Long, ModelConfigDO> data = loadFromFile(this.configPath);
            this.snapshot.set(Collections.unmodifiableMap(data));
            log.info("Model configuration loaded successfully, quantity: {}", data.size());
        } catch (Exception e) {
            //Allow empty configuration to start: if parsing fails, it will still start with empty configuration
            this.snapshot.set(Collections.unmodifiableMap(new HashMap<>()));
            log.error("Failed to load model configuration at startup and will start with empty configuration: {}", e.getMessage(), e);
        }
    }
    
    private Map<Long, ModelConfigDO> loadFromFile(Path file) throws IOException {
        //This method assumes that the caller has determined that the file exists
        byte[] bytes = Files.readAllBytes(file);
        YamlRoot root = yamlMapper.readValue(bytes, YamlRoot.class);
        if (root == null || root.models == null) {
            return new HashMap<>();
        }
        
        Map<Long, ModelConfigDO> map = new HashMap<>();
        Set<Long> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (YamlModel m : root.models) {
            validateYamlModel(m, ids, names);
            ModelConfigDO entity = toEntity(m);
            map.put(entity.getId(), entity);
        }
        return map;
    }
    
    private void validateYamlModel(YamlModel m, Set<Long> ids, Set<String> names) {
        if (m.id == null) {
            throw new IllegalArgumentException("Model is missing id");
        }
        if (m.name == null || m.name.isBlank()) {
            throw new IllegalArgumentException("Model is missing name");
        }
        if (m.provider == null || m.provider.isBlank()) {
            throw new IllegalArgumentException("Model is missing provider");
        }
        if (m.modelName == null || m.modelName.isBlank()) {
            throw new IllegalArgumentException("Model is missing modelName");
        }
        //if (m.baseUrl == null || m.baseUrl.isBlank()) throw new IllegalArgumentException("Model is missing baseUrl");
        if (m.apiKey == null || m.apiKey.isBlank()) {
            throw new IllegalArgumentException("Model is missing apiKey");
        }
        if (!ids.add(m.id)) {
            throw new IllegalArgumentException("Duplicate model id:" + m.id);
        }
        if (!names.add(m.name)) {
            throw new IllegalArgumentException("Duplicate model name:" + m.name);
        }
        if (m.status == null) {
            m.status = 1;
        }
    }
    
    private ModelConfigDO toEntity(YamlModel m) {
        ModelConfigDO.ModelConfigDOBuilder b = ModelConfigDO.builder().id(m.id).name(m.name)
                .provider(m.provider.toLowerCase()).modelName(m.modelName).baseUrl(m.baseUrl)
                .apiKey(environment != null ? environment.resolvePlaceholders(m.apiKey) : m.apiKey).status(m.status)
                .createTime(LocalDateTime.now()).updateTime(LocalDateTime.now());
        
        if (m.defaultParameters != null) {
            try {
                b.defaultParameters(new ObjectMapper().writeValueAsString(m.defaultParameters));
            } catch (Exception e) {
                throw new IllegalArgumentException("Serialization of defaultParameters failed", e);
            }
        }
        if (m.supportedParameters != null) {
            try {
                b.supportedParameters(new ObjectMapper().writeValueAsString(m.supportedParameters));
            } catch (Exception e) {
                throw new IllegalArgumentException("Serialization of supportedParameters failed", e);
            }
        }
        return b.build();
    }
    
    private void startWatchService() {
        try {
            Path dir = this.configPath.getParent();
            if (dir == null) {
                return;
            }
            WatchService ws = FileSystems.getDefault().newWatchService();
            dir.register(ws, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);
            ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "model-config-watch"));
            executor.submit(() -> {
                log.info("Start listening for model configuration file changes: {}", configPath);
                while (true) {
                    WatchKey key = ws.take();
                    try {
                        for (WatchEvent<?> event : key.pollEvents()) {
                            Path changed = (Path) event.context();
                            if (changed != null && configPath.getFileName().equals(changed.getFileName())) {
                                try {
                                    Map<Long, ModelConfigDO> data = loadFromFile(configPath);
                                    snapshot.set(Collections.unmodifiableMap(data));
                                    log.info("Model configuration hot update successful, quantity: {}", data.size());
                                } catch (Exception e) {
                                    log.error("Model configuration hot update failed, the old configuration will be used: {}", e.getMessage(), e);
                                }
                            }
                        }
                    } finally {
                        key.reset();
                    }
                }
            });
        } catch (Exception e) {
            log.warn("Failed to start WatchService, hot update will not be performed: {}", e.getMessage());
        }
    }
    
    @Override
    public ModelConfigDO findById(Long id) {
        return snapshot.get().get(id);
    }
    
    @Override
    public boolean existsById(Long id) {
        return snapshot.get().containsKey(id);
    }
    
    @Override
    public List<ModelConfigDO> list(String name, String provider, Integer status, int offset, int limit) {
        List<ModelConfigDO> all = new ArrayList<>(snapshot.get().values());
        return all.stream().filter(m -> name == null || m.getName().contains(name))
                .filter(m -> provider == null || provider.isBlank() || provider.equalsIgnoreCase(m.getProvider()))
                .filter(m -> status == null || Objects.equals(status, m.getStatus()))
                .sorted(Comparator.comparing(ModelConfigDO::getId)).skip(Math.max(offset, 0)).limit(Math.max(limit, 0))
                .collect(Collectors.toList());
    }
    
    @Override
    public int count(String name, String provider, Integer status) {
        List<ModelConfigDO> all = new ArrayList<>(snapshot.get().values());
        long cnt = all.stream().filter(m -> name == null || m.getName().contains(name))
                .filter(m -> provider == null || provider.isBlank() || provider.equalsIgnoreCase(m.getProvider()))
                .filter(m -> status == null || Objects.equals(status, m.getStatus())).count();
        return (int) cnt;
    }
    
    @Override
    public List<ModelConfigDO> listEnabled() {
        return snapshot.get().values().stream().filter(m -> Objects.equals(1, m.getStatus()))
                .sorted(Comparator.comparing(ModelConfigDO::getId)).collect(Collectors.toList());
    }
    
    //YAML mapping structure
    public static class YamlRoot {
        
        public List<YamlModel> models;
    }
    
    public static class YamlModel {
        
        public Long id;
        
        public String name;
        
        public String provider;
        
        public String modelName;
        
        public String baseUrl;
        
        public String apiKey;
        
        public Integer status;
        
        public Map<String, Object> defaultParameters;
        
        public List<Map<String, Object>> supportedParameters;
    }
}


