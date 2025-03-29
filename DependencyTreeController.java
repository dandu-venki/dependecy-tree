package com.hackthon.dependecy;



import org.springframework.web.bind.annotation.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import org.xml.sax.InputSource;
import java.io.*;

@RestController
@RequestMapping("/dependency-tree")
public class DependencyTreeController {

    private com.hackthon.dependecy.DependencyTreeGenerator DependencyTreeGenerator;

    /**
     * Accepts an XML dependency snippet and returns the dependency tree.
     *
     * Example XML input:
     * <dependency>
     *     <groupId>org.flowable</groupId>
     *     <artifactId>flowable-engine</artifactId>
     *     <version>7.1.0</version>
     *     <exclusions>
     *         <exclusion>
     *             <groupId>org.springframework</groupId>
     *             <artifactId>spring-beans</artifactId>
     *         </exclusion>
     *     </exclusions>
     * </dependency>
     */
    @PostMapping(consumes = "application/xml", produces = "text/plain")
    public String generateDependencyTree(@RequestBody String dependencyXml) {
        try {
            // Parse the XML input
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            InputSource is = new InputSource(new StringReader(dependencyXml));
            Document doc = builder.parse(is);
            Element dependencyElement = doc.getDocumentElement();

            // Extract required values: groupId, artifactId, version
            String groupId = getElementValue(dependencyElement, "groupId");
            String artifactId = getElementValue(dependencyElement, "artifactId");
            String version = getElementValue(dependencyElement, "version");

            if (groupId == null || artifactId == null || version == null) {
                return "Invalid dependency XML. Ensure groupId, artifactId, and version are provided.";
            }

            // Build the exclusions block if any exclusions are provided
            String exclusionsBlock = "";
            NodeList exclusionsList = dependencyElement.getElementsByTagName("exclusions");
            if (exclusionsList.getLength() > 0) {
                // There should be one <exclusions> element containing one or more <exclusion> children.
                Element exclusionsElement = (Element) exclusionsList.item(0);
                NodeList exclusionNodes = exclusionsElement.getElementsByTagName("exclusion");
                if (exclusionNodes.getLength() > 0) {
                    StringBuilder exclusionsBuilder = new StringBuilder();
                    exclusionsBuilder.append("<exclusions>");
                    for (int i = 0; i < exclusionNodes.getLength(); i++) {
                        Element exclusion = (Element) exclusionNodes.item(i);
                        String exGroupId = getElementValue(exclusion, "groupId");
                        String exArtifactId = getElementValue(exclusion, "artifactId");
                        if (exGroupId != null && exArtifactId != null) {
                            exclusionsBuilder.append("""
                                <exclusion>
                                    <groupId>%s</groupId>
                                    <artifactId>%s</artifactId>
                                </exclusion>
                                """.formatted(exGroupId, exArtifactId));
                        }
                    }
                    exclusionsBuilder.append("</exclusions>");
                    exclusionsBlock = exclusionsBuilder.toString();
                }
            }

            // Generate dependency tree using the provided values and exclusions
            return DependencyTreeGenerator.generateTree(groupId, artifactId, version, exclusionsBlock);
        } catch (Exception e) {
            e.printStackTrace();
            return "Error processing dependency: " + e.getMessage();
        }
    }

    // Helper method to extract the text content of a given element tag name
    private String getElementValue(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        if (list.getLength() > 0) {
            return list.item(0).getTextContent().trim();
        }
        return null;
    }
}
