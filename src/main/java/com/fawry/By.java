package com.fawry;

import com.fawry.utilities.Log;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class By extends org.openqa.selenium.By {
    private static WebDriver driver; // static driver for all instances
    private final org.openqa.selenium.By originalBy;
    private static final Map<String, org.openqa.selenium.By> healedCache = new ConcurrentHashMap<>();

    // Set the driver once after WebDriver initialization
    public static void setDriver(WebDriver webDriver) {
        driver = webDriver;
    }

    private By(org.openqa.selenium.By by) {
        this.originalBy = by;
    }

    // Factory methods
    public static By xpath(String xpath) { return new By(org.openqa.selenium.By.xpath(xpath)); }
    public static By id(String id) { return new By(org.openqa.selenium.By.id(id)); }
    public static By name(String name) { return new By(org.openqa.selenium.By.name(name)); }
    public static By cssSelector(String selector) { return new By(org.openqa.selenium.By.cssSelector(selector)); }
    public static By className(String className) { return new By(org.openqa.selenium.By.className(className)); }
    public static By tagName(String tagName) { return new By(org.openqa.selenium.By.tagName(tagName)); }
    public static By linkText(String linkText) { return new By(org.openqa.selenium.By.linkText(linkText)); }
    public static By partialLinkText(String partialLinkText) { return new By(org.openqa.selenium.By.partialLinkText(partialLinkText)); }

    @Override
    public WebElement findElement(SearchContext context) {
        String locatorKey = originalBy.toString();
        try {
            if (healedCache.containsKey(locatorKey)) {
                return healedCache.get(locatorKey).findElement(context);
            }
            return originalBy.findElement(context);
        } catch (NoSuchElementException | TimeoutException | InvalidElementStateException e) {
            Log.info("⚠️ Element issue detected for locator: " + locatorKey);
            Log.info("🧠 Triggering healing process...");
            org.openqa.selenium.By healedBy = healLocator(locatorKey);
            if (healedBy != null) {
                healedCache.put(locatorKey, healedBy);
                Log.info("✅ Healing successful. Cached healed locator: " + healedBy);
                return healedBy.findElement(context);
            }
            throw new NoSuchElementException("Failed to heal locator: " + locatorKey, e);
        }
    }

    @Override
    public List<WebElement> findElements(SearchContext context) {
        String locatorKey = originalBy.toString();
        try {
            if (healedCache.containsKey(locatorKey)) {
                return healedCache.get(locatorKey).findElements(context);
            }
            return originalBy.findElements(context);
        } catch (NoSuchElementException | TimeoutException | InvalidElementStateException e) {
            Log.info("⚠️ Elements issue detected for locator: " + locatorKey);
            Log.info("🧠 Triggering healing process...");
            org.openqa.selenium.By healedBy = healLocator(locatorKey);
            if (healedBy != null) {
                healedCache.put(locatorKey, healedBy);
                Log.info("✅ Healing successful for elements. Cached healed locator: " + healedBy);
                return healedBy.findElements(context);
            }
            throw new NoSuchElementException("Failed to heal elements for locator: " + locatorKey, e);
        }
    }

    private org.openqa.selenium.By healLocator(String rawLocator) {
        try {
            if (driver == null) {
                Log.info("❌ WebDriver not set. Cannot capture page source for healing.");
                return null;
            }
            String cleanedLocator = rawLocator.replace("By.xpath: ", "").trim();
            HtmlGenerator htmlGenerator = new HtmlGenerator();
            htmlGenerator.clearHTMLSnapshotsDirectory();

            htmlGenerator.generatePageHTML(driver.getPageSource());

            String healedXpath = new AIIntegrationService().autoAnalyzeAndFix(cleanedLocator);
            if (healedXpath != null && !healedXpath.isEmpty()) {
                return org.openqa.selenium.By.xpath(healedXpath.trim());
            }
        } catch (Exception e) {
            Log.info("❌ Healing process failed for: " + rawLocator);
            e.printStackTrace();
        }
        return null;
    }


    @Override
    public String toString() {
        return "ByHealable(" + originalBy.toString() + ")";
    }
}
