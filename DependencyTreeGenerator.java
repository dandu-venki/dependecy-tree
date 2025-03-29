package com.hackthon.dependecy;

import java.io.*;

public class DependencyTreeGenerator{
    public static String generateTree(String groupId, String artifactId, String version, String exclusionsBlock)
            throws IOException, InterruptedException {
        if (groupId == null || artifactId == null || version == null)
            return "Invalid input. groupId, artifactId and version must be provided.";

        // If no exclusions provided, use an empty string.
        if (exclusionsBlock == null) {
            exclusionsBlock = "";
        }

        // Create a temporary pom.xml including the exclusions block (if any)
        String pomContent = """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>temp-project</artifactId>
                <version>1.0-SNAPSHOT</version>
                <dependencies>
                    <dependency>
                        <groupId>%s</groupId>
                        <artifactId>%s</artifactId>
                        <version>%s</version>
                        %s
                    </dependency>
                </dependencies>
            </project>
            """.formatted(groupId, artifactId, version, exclusionsBlock);

        File pomFile = new File("temp-pom.xml");
        try (FileWriter writer = new FileWriter(pomFile)) {
            writer.write(pomContent);
        }

        // Execute Maven dependency:tree
        ProcessBuilder pb = new ProcessBuilder("mvn", "dependency:tree", "-f", pomFile.getAbsolutePath());
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Wait for Maven process to complete
        process.waitFor();

        // Capture and filter output to only include dependency tree details
        StringBuilder treeOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Filter for tree markers such as "+-" or "\-"
                if (line.contains("+-") || line.contains("\\-")) {
                    // Remove any leading "[INFO]" prefix
                    line = line.replaceFirst("^\\[INFO\\]\\s*", "");
                    treeOutput.append(line).append("\n");
                }
            }
        }

        // Clean up temporary file
        pomFile.delete();
        return treeOutput.toString();
    }
}
