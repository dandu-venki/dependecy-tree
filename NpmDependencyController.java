package com.hackthon.dependecy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/npm-dependency")
public class NpmDependencyController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String NPM_REGISTRY_URL = "https://artifacthub-iad.oci.oraclecorp.com/api/npm/npmjs-registry/";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ConcurrentHashMap<String, String> dependencyCache = new ConcurrentHashMap<>();
    private static final int CACHE_EXPIRY_MINUTES = 60;
    private final ConcurrentHashMap<String, Long> cacheTimestamps = new ConcurrentHashMap<>();

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @PostMapping("/tree")
    public ResponseEntity<String> generateNpmDependencyTree(@RequestBody DependencyRequest request) {
        try {
            String result = getNpmDependencyTree(request.getDependency(), request.getVersion());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body("{\"error\": \"Failed to generate dependency tree\", \"details\": \"" +
                            e.getMessage().replace("\"", "\\\"") + "\"}");
        }
    }

    private String getNpmDependencyTree(String packageName, String version) {
        String packageKey = packageName + (version != null && !version.isEmpty() ? "@" + version : "");

        String cachedResult = checkCache(packageKey);
        if (cachedResult != null) {
            return cachedResult;
        }

        try {
            String url = NPM_REGISTRY_URL + packageName;
            if (version != null && !version.isEmpty()) {
                url += "/" + version;
            }

            String response = restTemplate.getForObject(url, String.class);

            if (response == null) {
                return "{\"message\": \"Package not found\"}";
            }

            JsonNode rootNode = OBJECT_MAPPER.readTree(response);
            JsonNode dependencies = version != null && !version.isEmpty()
                    ? rootNode.path("dependencies")
                    : rootNode.path("versions").path(rootNode.path("dist-tags").path("latest").asText()).path("dependencies");

            String result;
            if (dependencies.isMissingNode() || dependencies.size() == 0) {
                result = "{\"message\": \"No known dependencies for this package.\"}";
            } else {
                result = OBJECT_MAPPER.writeValueAsString(dependencies);
            }

            cacheResult(packageKey, result);
            return result;

        } catch (Exception e) {
            return "{\"error\": \"Failed to fetch dependencies\", \"details\": \"" +
                    e.getMessage().replace("\"", "\\\"") + "\"}";
        }
    }

    private String checkCache(String key) {
        String cached = dependencyCache.get(key);
        if (cached != null) {
            Long timestamp = cacheTimestamps.get(key);
            if (timestamp != null &&
                    (System.currentTimeMillis() - timestamp) < CACHE_EXPIRY_MINUTES * 60 * 1000) {
                return cached;
            } else {
                dependencyCache.remove(key);
                cacheTimestamps.remove(key);
            }
        }
        return null;
    }

    private void cacheResult(String key, String result) {
        dependencyCache.put(key, result);
        cacheTimestamps.put(key, System.currentTimeMillis());
    }

    static class DependencyRequest {
        private String dependency;
        private String version;

        public String getDependency() { return dependency; }
        public void setDependency(String dependency) { this.dependency = dependency; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
    }
}