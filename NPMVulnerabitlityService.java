package com.hackthon.dependecy;


import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.io.*;
        import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
        import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NPMVulnerabitlityService {

    private static final String API_URL = "https://ossindex.sonatype.org/api/v3/component-report";
    private static final int BATCH_SIZE = 128; // OSS Index API limit
    private static final Gson GSON = new Gson(); // Reuse Gson instance

    // Enum to distinguish package ecosystems
    public enum PackageType {
        MAVEN, NPM
    }

    // Convert JSON string to map
    private static HashMap<String, Object> jsonStringToMap(String jsonString) {
        Type type = new TypeToken<List<HashMap<String, Object>>>(){}.getType();
        List<HashMap<String, Object>> result = GSON.fromJson(jsonString, type);
        HashMap<String, Object> responseMap = new HashMap<>();
        responseMap.put("result", result);
        return responseMap;
    }

    // Batch API call for multiple coordinates
    private static HashMap<String, Object> checkVulnerabilitiesBatch(List<String> coordinates) {
        HashMap<String, Object> responseMap = new HashMap<>();
        try {
            String payload = "{\"coordinates\": [" + coordinates.stream()
                    .map(coord -> "\"" + coord + "\"")
                    .collect(Collectors.joining(",")) + "]}";

            URL url = new URL(API_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "VulnerabilityChecker/1.0");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = payload.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            responseMap.put("responseCode", String.valueOf(responseCode));

            if (responseCode == 200) {
                try (Scanner scanner = new Scanner(connection.getInputStream(), "utf-8")) {
                    String responseBody = scanner.useDelimiter("\\A").next();
                    return jsonStringToMap(responseBody);
                }
            } else {
                responseMap.put("error", "Received response code " + responseCode);
            }

            connection.disconnect();
        } catch (Exception e) {
            responseMap.put("error", "Error checking vulnerabilities: " + e.getMessage());
        }
        return responseMap;
    }

    // Fetch vulnerabilities for Maven
    public static List<HashMap<String, Object>> fetchMavenVulnerabilities(String groupId, String artifactId, String version, String exclusionsBlock)
            throws IOException, InterruptedException {
        return fetchVulnerabilities(PackageType.MAVEN, groupId, artifactId, version, exclusionsBlock, null);
    }

    // Fetch vulnerabilities for npm
    public static List<HashMap<String, Object>> fetchNpmVulnerabilities(String packageName, String version)
            throws IOException, InterruptedException {
        return fetchVulnerabilities(PackageType.NPM, null, packageName, version, null, null);
    }

    // Generic fetch vulnerabilities method
    private static List<HashMap<String, Object>> fetchVulnerabilities(PackageType packageType, String groupId, String artifactIdOrName,
                                                                      String version, String exclusionsBlock, String dependencyTree)
            throws IOException, InterruptedException {
        List<String> coordinates = new ArrayList<>();

        if (packageType == PackageType.MAVEN) {
            String mavenDependencyTree = dependencyTree != null ? dependencyTree : DependencyTreeGenerator.generateTree(groupId, artifactIdOrName, version, exclusionsBlock);
            String regex = "\\s*[-+\\\\| ]*([\\w.-]+):([\\w.-]+):[\\w.-]+:([\\d.]+):[\\w.-]+";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(mavenDependencyTree);

            while (matcher.find()) {
                String groupId1 = matcher.group(1);
                String artifactId1 = matcher.group(2);
                String version1 = matcher.group(3);
                String packageCoordinates = "pkg:maven/" + groupId1 + "/" + artifactId1 + "@" + version1;
                coordinates.add(packageCoordinates);
                System.out.println("Added: " + packageCoordinates);
            }
        } else if (packageType == PackageType.NPM) {
            // For npm, directly use the package name and version
            String packageCoordinates = "pkg:npm/" + artifactIdOrName + "@" + version;
            coordinates.add(packageCoordinates);
            System.out.println("Added: " + packageCoordinates);
        }

        // Process in batches
        List<HashMap<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < coordinates.size(); i += BATCH_SIZE) {
            List<String> batch = coordinates.subList(i, Math.min(i + BATCH_SIZE, coordinates.size()));
            HashMap<String, Object> batchResult = checkVulnerabilitiesBatch(batch);
            if (batchResult.containsKey("result")) {
                @SuppressWarnings("unchecked")
                List<HashMap<String, Object>> batchVulnerabilities = (List<HashMap<String, Object>>) batchResult.get("result");
                results.addAll(batchVulnerabilities);
            } else {
                results.add(batchResult); // Add error response if any
            }
        }

        return results;
    }

    // Parallel fetch vulnerabilities for Maven
    public static List<HashMap<String, Object>> fetchMavenVulnerabilitiesParallel(String groupId, String artifactId, String version, String exclusionsBlock)
            throws IOException, InterruptedException {
        return fetchVulnerabilitiesParallel(PackageType.MAVEN, groupId, artifactId, version, exclusionsBlock, null);
    }

    // Parallel fetch vulnerabilities for npm
    public static List<HashMap<String, Object>> fetchNpmVulnerabilitiesParallel(String packageName, String version)
            throws IOException, InterruptedException {
        return fetchVulnerabilitiesParallel(PackageType.NPM, null, packageName, version, null, null);
    }

    // Generic parallel fetch vulnerabilities method
    private static List<HashMap<String, Object>> fetchVulnerabilitiesParallel(PackageType packageType, String groupId, String artifactIdOrName,
                                                                              String version, String exclusionsBlock, String dependencyTree)
            throws IOException, InterruptedException {
        List<String> coordinates = new ArrayList<>();

        if (packageType == PackageType.MAVEN) {
            String mavenDependencyTree = dependencyTree != null ? dependencyTree : DependencyTreeGenerator.generateTree(groupId, artifactIdOrName, version, exclusionsBlock);
            String regex = "\\s*[-+\\\\| ]*([\\w.-]+):([\\w.-]+):[\\w.-]+:([\\d.]+):[\\w.-]+";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(mavenDependencyTree);

            while (matcher.find()) {
                String groupId1 = matcher.group(1);
                String artifactId1 = matcher.group(2);
                String version1 = matcher.group(3);
                coordinates.add("pkg:maven/" + groupId1 + "/" + artifactId1 + "@" + version1);
            }
        } else if (packageType == PackageType.NPM) {
            coordinates.add("pkg:npm/" + artifactIdOrName + "@" + version);
        }

        return coordinates.parallelStream()
                .collect(Collectors.groupingByConcurrent(
                        coord -> coordinates.indexOf(coord) / BATCH_SIZE)) // Group into batches
                .values().parallelStream()
                .map(batch -> checkVulnerabilitiesBatch(batch))
                .flatMap(result -> {
                    if (result.containsKey("result")) {
                        @SuppressWarnings("unchecked")
                        List<HashMap<String, Object>> vulnerabilities = (List<HashMap<String, Object>>) result.get("result");
                        return vulnerabilities.stream();
                    }
                    return Stream.of(result);
                })
                .collect(Collectors.toList());
    }
}