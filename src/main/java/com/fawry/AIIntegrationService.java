package com.fawry;

import com.fawry.utilities.Log;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class AIIntegrationService {
    private static final String QWENMOE_API_URL = "http://10.100.55.98:8660/v1/chat/completions";
    private static final String MODEL = "./qwenmoe/content/qwenmoe/";
    private static final DateTimeFormatter FILE_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;

    public AIIntegrationService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
        this.mapper = new ObjectMapper();
    }

    public String extractRelevantHtml(String fullHtml, List<String> damagedXpaths) {
        try {
            Document doc = Jsoup.parse(fullHtml);
            StringBuilder trimmedHtml = new StringBuilder();
            trimmedHtml.append("<root>\n");

            for (String xpath : damagedXpaths) {
                // Simple XPath to CSS conversion (for demo purposes)
                String cssSelector = xpathToCss(xpath);
                if (cssSelector == null) continue;
                Elements elements = doc.select(cssSelector);
                for (Element el : elements) {
                    trimmedHtml.append(el.outerHtml()).append("\n");
                }
            }

            trimmedHtml.append("</root>");
            return trimmedHtml.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return fullHtml.substring(0, Math.min(fullHtml.length(), 20000));
        }
    }

    private String xpathToCss(String xpath) {
        // Example: //div[@id='main'] -> div#main
        if (xpath.matches("//([a-zA-Z0-9]+)\\[@id='([^']+)'\\]")) {
            return xpath.replaceAll("//([a-zA-Z0-9]+)\\[@id='([^']+)'\\]", "$1#$2");
        }
        // Example: //span[@class='foo'] -> span.foo
        if (xpath.matches("//([a-zA-Z0-9]+)\\[@class='([^']+)'\\]")) {
            return xpath.replaceAll("//([a-zA-Z0-9]+)\\[@class='([^']+)'\\]", "$1.$2");
        }
        // Example: //button -> button
        if (xpath.matches("//([a-zA-Z0-9]+)")) {
            return xpath.replaceAll("//([a-zA-Z0-9]+)", "$1");
        }
        // Add more rules as needed
        return null;
    }

    public List<String> analyzeAndGenerateXPaths(List<String> damagedXPaths, String htmlSnapshotPath) {
        try {
            String htmlSnapshotContent = readFileContent(htmlSnapshotPath);
            if (htmlSnapshotContent == null) htmlSnapshotContent = "";

            // Use the new method to trim the snapshot
            String relevantHtml = extractRelevantHtml(htmlSnapshotContent, damagedXPaths);

            // Print the relevant HTML snapshot content
            Log.info("HTML snapshot content sent to AI model:\n" + relevantHtml);

            String prompt = createAnalysisPrompt(damagedXPaths, relevantHtml);
            Log.info("Sending request to AI model with prompt:\n" + prompt);

            String aiResponse = callQwenMoeAPI(prompt);
            List<String> xpaths = extractXPathFromAIResponse(aiResponse);

            if (xpaths == null || xpaths.isEmpty()) {
                Log.info("Failed to extract valid XPaths from AI response");
                return null;
            }

            Log.info("Generated XPaths from AI analysis: " + xpaths);
            return xpaths;

        } catch (Exception e) {
            Log.error("AI analysis failed", e);
            return null;
        }
    }


    private String callQwenMoeAPI(String prompt) throws IOException {
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.put("model", MODEL);

        ArrayNode messages = mapper.createArrayNode();
        ObjectNode message = mapper.createObjectNode();
        message.put("role", "user");
        message.put("content", prompt);
        messages.add(message);

        requestBody.set("messages", messages);

        Request request = new Request.Builder()
                .url(QWENMOE_API_URL)
                .post(RequestBody.create(
                        requestBody.toString(),
                        MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer EMPTY")
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "empty body";
                Log.info("API request failed. Status: " + response.code() + ", Body: " + errorBody);
                throw new IOException("Unexpected response: " + response);
            }

            String responseBody = response.body().string();
            Log.info("Raw API response: " + responseBody);

            JsonNode root = mapper.readTree(responseBody);
            String aiResponse = root.path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText()
                    .trim();

            Log.info("AI model response: " + aiResponse);
            return aiResponse;
        }
    }

    private String readFileContent(String filePath) {
        Path path = Paths.get(filePath);
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append(System.lineSeparator());
            }
            return content.toString().trim();
        } catch (IOException e) {
            Log.error("Unable to read file " + filePath, e);
            return null;
        }
    }

    /**
     * Create prompt for the AI with damaged XPaths and HTML snapshot.
     */
//    private String createAnalysisPrompt(List<String> damagedXPaths, String htmlSnapshot) {
//        StringBuilder listBuilder = new StringBuilder();
//        for (int i = 0; i < damagedXPaths.size(); i++) {
//            listBuilder.append(i + 1).append(". ").append(damagedXPaths.get(i)).append("\n");
//        }
//
//        return String.format(
//                "ROLE: You are a locator repair assistant for Selenium Web UI automation.\n" +
//                        "TASK: Repair the XPath locators that no longer match elements, using the provided HTML snapshot.\n\n" +
//                        "INPUT:\n" +
//                        "1) Damaged XPath List:\n%s\n\n" +
//                        "2) HTML Snapshot:\n'''\n%s\n'''\n\n" +
//                        "REPAIR RULES:\n" +
//                        "- For each damaged XPath, find the closest corresponding element in the HTML.\n" +
//                        "- Construct a corrected XPath that uniquely identifies the element.\n" +
//                        "- Prioritize attributes in this order: @id → @name → stable part of @class → visible text → other attributes.\n" +
//                        "- Only use contains() when exact attribute match is not possible.\n" +
//                        "- Avoid long absolute XPaths like /html/body/... unless no better option exists.\n" +
//                        "- Final XPath must be short, readable, and stable.\n\n" +
//                        "OUTPUT FORMAT (STRICT):\n" +
//                        "- Output only the corrected XPath expressions.\n" +
//                        "- One XPath per line.\n" +
//                        "- Do not include explanations, comments, or extra text.\n",
//                listBuilder.toString().trim(),
//                htmlSnapshot
//        );
//    }

    private String createAnalysisPrompt(List<String> damagedXPaths, String htmlSnapshot) {
        StringBuilder listBuilder = new StringBuilder();
        for (int i = 0; i < damagedXPaths.size(); i++) {
            listBuilder.append(i + 1).append(". ").append(damagedXPaths.get(i)).append("\n");
        }

        return String.format(
                "ROLE: You are an expert Selenium XPath locator repair engine.\n\n" +
                        "INPUT:\n" +
                        "1) Damaged XPath List:\n%s\n\n" +
                        "2) HTML Snapshot:\n'''\n%s\n'''\n\n" +
                        "TASK:\n" +
                        "- For each XPath in the list, attempt to locate the intended target element in the provided HTML.\n" +
                        "- If the element still exists but the XPath no longer matches, generate a corrected and stable XPath.\n" +
                        "- If the element is not present in the snapshot or cannot be confidently matched, generate a **new** stable XPath that best identifies the element **based on the original locator’s intent**.\n\n" +
                        "XPATH CONSTRUCTION RULES:\n" +
                        "- Prefer short, stable, attribute-based XPath.\n" +
                        "- Attribute priority order: @id → @name → stable part of @class → visible text → other attributes.\n" +
                        "- Only use contains() when exact attribute match is not possible.\n" +
                        "- Avoid absolute paths like /html/body.\n" +
                        "- XPath must uniquely identify the element.\n\n" +
                        "OUTPUT FORMAT (IMPORTANT):\n" +
                        "- Output **only** the corrected/new XPath values.\n" +
                        "- One XPath per line.\n" +
                        "- No explanations, no reasoning, no markdown, no labels.\n",
                listBuilder.toString().trim(),
                htmlSnapshot
        );
    }


    private List<String> extractXPathFromAIResponse(String response) {
        String cleanedResponse = response.replaceAll("^```", "")
                .replaceAll("```$", "")
                .trim();
        Pattern xpathPattern = Pattern.compile("(//[^\\s]+\\[(?:@[^\\]]+|contains\\([^\\]]+\\))\\])");
        Matcher matcher = xpathPattern.matcher(cleanedResponse);
        List<String> xpaths = new java.util.ArrayList<>();
        while (matcher.find()) {
            String xpath = matcher.group(1);
            xpaths.add(xpath);
            Log.info("Extracted XPath: " + xpath);
        }

        if (xpaths.isEmpty()) {
            Log.info("AI response doesn't contain valid XPath: " + response);
        }
        return xpaths;
    }

    /**
     * Example runner: auto-load latest HTML snapshot with dummy damaged list
     */
    public List<String> autoAnalyzeAndFix(List<String> damagedXPaths) {
        String timestamp = LocalDateTime.now().format(FILE_TIMESTAMP_FORMAT);
        String htmlSnapshotPath = "html_snapshots/snapshot_" + timestamp + ".html";
        return analyzeAndGenerateXPaths(damagedXPaths, htmlSnapshotPath);
    }
}