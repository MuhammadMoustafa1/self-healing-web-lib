package com.fawry;

import com.fawry.utilities.Log;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class HtmlGenerator {
    private static final String HTML_OUTPUT_DIR = "html_snapshots";
    private static final DateTimeFormatter FILE_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static boolean filesCleaned = false;

    public void generatePageHTML(String htmlSource) throws Exception {
        initializeHtmlDirectory();

        Document document = Jsoup.parse(htmlSource);
        List<String> xpaths = generateAllXPaths(document);
        String enhancedHtml = createEnhancedHtml(document, xpaths);
        saveHtmlToFile(enhancedHtml, xpaths);
    }

    private void initializeHtmlDirectory() {
        try {
            Files.createDirectories(Paths.get(HTML_OUTPUT_DIR));
            if (!filesCleaned) {
                cleanUpPreviousFiles();
                filesCleaned = true;
            }
        } catch (IOException e) {
            Log.error("Failed to initialize HTML output directory:");
            e.printStackTrace();
        }
    }

    private void cleanUpPreviousFiles() throws IOException {
        Files.walk(Paths.get(HTML_OUTPUT_DIR))
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".html"))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        Log.error("Failed to delete file: " + path);
                    }
                });
    }

    private List<String> generateAllXPaths(Document document) {
        List<String> xpaths = new ArrayList<>();
        Elements allElements = document.getAllElements();

        for (Element element : allElements) {
            if (!element.tagName().equals("#root")) {
                String xpath = generateXPath(element);
                if (!xpath.isEmpty()) {
                    xpaths.add(xpath);
                }
            }
        }
        return xpaths;
    }

    private String generateXPath(Element element) {
        Deque<String> hierarchy = new ArrayDeque<>();
        Element current = element;
        while (current != null && !current.tagName().equals("#root")) {
            String tag = current.tagName();
            String id = current.id();
            if (!id.isEmpty()) {
                hierarchy.addFirst(tag + "[@id='" + id + "']");
                break; // id is unique, stop here
            } else {
                int index = getElementIndex(current);
                hierarchy.addFirst(index > 1 ? tag + "[" + index + "]" : tag);
            }
            current = current.parent();
        }
        StringBuilder xpath = new StringBuilder();
        for (String level : hierarchy) {
            xpath.append("/").append(level);
        }
        return xpath.toString();
    }

    private int getElementIndex(Element element) {
        if (element.parent() == null) return 1;
        int index = 1;
        for (Element sibling : element.parent().children()) {
            if (sibling == element) break;
            if (sibling.tagName().equals(element.tagName())) index++;
        }
        return index;
    }

    private String createEnhancedHtml(Document document, List<String> xpaths) {
        String xpathComment = "<!-- \n" +
                "Generated HTML with all available XPaths\n" +
                "Total XPaths found: " + xpaths.size() + "\n" +
                "Sample XPaths:\n" +
                String.join("\n", xpaths.subList(0, Math.min(10, xpaths.size()))) +
                "\n-->\n";
        return xpathComment + document.outerHtml();
    }

    private void saveHtmlToFile(String htmlContent, List<String> xpaths) throws IOException {
        String timestamp = LocalDateTime.now().format(FILE_TIMESTAMP_FORMAT);
        String filename = HTML_OUTPUT_DIR + "/snapshot_" + timestamp + ".html";
        Path filePath = Paths.get(filename);

        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write(htmlContent);
            System.out.println("Saved HTML snapshot to: " + filePath.toAbsolutePath());
        }
    }

}