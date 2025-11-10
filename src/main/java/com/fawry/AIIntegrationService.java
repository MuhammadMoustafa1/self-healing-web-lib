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
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public String analyzeAndGenerateXPath(String damagedXPath, String htmlSnapshotPath) {
        try {
            String htmlSnapshotContent = readFileContent(htmlSnapshotPath);
            if (htmlSnapshotContent == null) htmlSnapshotContent = "";

            String prompt = createAnalysisPrompt(damagedXPath, htmlSnapshotContent);
            Log.info("Sending request to AI model with prompt:\n" + prompt);

            String aiResponse = callQwenMoeAPI(prompt);
            String xpath = extractXPathFromAIResponse(aiResponse);

            if (xpath == null || xpath.isEmpty()) {
                Log.info("Failed to extract valid XPath from AI response");
                return null;
            }

            Log.info("Generated XPath from AI analysis: " + xpath);
            return xpath;

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

    private String createAnalysisPrompt(String damagedLocator, String htmlSnapshot) {
        boolean hasContains = damagedLocator != null && damagedLocator.contains("contains(");

        return String.format(
                "ROLE: You are an expert Selenium locator repair engine.\n\n" +
                        "INPUT:\n" +
                        "1) Damaged Locator (could be XPath, id, cssSelector, className, tagName, linkText, or partialLinkText):\n%s\n\n" +
                        "2) HTML Snapshot:\n'''\n%s\n'''\n\n" +
                        "TASK:\n" +
                        "- Attempt to locate the intended target element in the provided HTML.\n" +
                        "- If the element still exists but the locator no longer matches, generate a corrected and stable locator of the SAME TYPE if possible.\n" +
                        "- If the element is not present or cannot be confidently matched, generate a **new** stable locator (preferably of the same type, otherwise fallback to XPath) that best identifies the element **based on the original locator’s intent**.\n\n" +
                        "LOCATOR CONSTRUCTION RULES:\n" +
                        "- Prefer short, stable, attribute-based locators.\n" +
                        "- Attribute priority order: @id → @name → stable part of @class → visible text → other attributes.\n" +
                        "- Only use contains() in XPath when exact attribute match is not possible.\n" +
                        (hasContains
                                ? "- The original damaged XPath uses contains(); if possible, preserve or adapt contains() logic in the corrected XPath.\n"
                                : "") +
                        "- When matching visible text, if the text in HTML contains leading or trailing spaces, keep them exactly as they appear (do not trim spaces).\n" +
                        "- Avoid absolute paths like /html/body in XPath.\n" +
                        "- Locator must uniquely identify the element.\n\n" +
                        "OUTPUT FORMAT (IMPORTANT):\n" +
                        "- Output **only** the corrected/new locator value.\n" +
                        "- No explanations, no reasoning, no markdown, no labels.\n",
                damagedLocator,
                htmlSnapshot
        );
    }

    private String extractXPathFromAIResponse(String response) {
        String cleanedResponse = response.replaceAll("^```", "")
                .replaceAll("```$", "")
                .trim();
        // Extract the first non-empty line as the XPath
        for (String line : cleanedResponse.split("\\r?\\n")) {
            String xpath = line.trim();
            if (!xpath.isEmpty()) {
                Log.info("Extracted XPath: " + xpath);
                return xpath;
            }
        }
        Log.info("AI response doesn't contain valid XPath: " + response);
        return null;
    }

    public String autoAnalyzeAndFix(String damagedXPath) {
        String timestamp = LocalDateTime.now().format(FILE_TIMESTAMP_FORMAT);
        String htmlSnapshotPath = "html_snapshots/snapshot_" + timestamp + ".html";
        return analyzeAndGenerateXPath(damagedXPath, htmlSnapshotPath);
    }
}
