package com.fawry;

import com.fawry.utilities.Log;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class By extends org.openqa.selenium.By {
    private static WebDriver driver;
    private final org.openqa.selenium.By originalBy;
    private static final Map<String, org.openqa.selenium.By> healedCache = new ConcurrentHashMap();
    private static final Duration DEFAULT_WAIT = Duration.ofSeconds(10L);

    private By(org.openqa.selenium.By by) {
        this.originalBy = by;
    }

    public static By xpath(String xpath) {
        return new By(org.openqa.selenium.By.xpath(xpath));
    }

    public static By id(String id) {
        return new By(org.openqa.selenium.By.id(id));
    }

    public static By name(String name) {
        return new By(org.openqa.selenium.By.name(name));
    }

    public static By cssSelector(String selector) {
        return new By(org.openqa.selenium.By.cssSelector(selector));
    }

    public static By className(String className) {
        return new By(org.openqa.selenium.By.className(className));
    }

    public static By tagName(String tagName) {
        return new By(org.openqa.selenium.By.tagName(tagName));
    }

    public static By linkText(String linkText) {
        return new By(org.openqa.selenium.By.linkText(linkText));
    }

    public static By partialLinkText(String partialLinkText) {
        return new By(org.openqa.selenium.By.partialLinkText(partialLinkText));
    }

    public static void setDriver(WebDriver webDriver) {
        driver = webDriver;
    }

    public WebElement findElement(SearchContext context) {
        String locatorKey = this.originalBy.toString();
        if (!HealingContext.isHealingEnabled()) {
            return this.originalBy.findElement(context);
        }
        try {
            if (healedCache.containsKey(locatorKey)) {
                return ((org.openqa.selenium.By) healedCache.get(locatorKey)).findElement(context);
            } else {
                WebDriverWait wait = new WebDriverWait(driver, DEFAULT_WAIT);
                return (WebElement) wait.until(ExpectedConditions.presenceOfElementLocated(this.originalBy));
            }
        } catch (InvalidElementStateException | NoSuchElementException | TimeoutException e) {
            Log.info("⚠️ Element not found after wait: " + locatorKey);
            Log.info("\ud83d\udd01 Attempting healing...");
            org.openqa.selenium.By healedBy = this.healLocator(locatorKey);
            if (healedBy != null) {
                healedCache.put(locatorKey, healedBy);
                Log.info("✅ Healing successful. Cached: " + String.valueOf(healedBy));
                WebDriverWait wait = new WebDriverWait(driver, DEFAULT_WAIT);
                return (WebElement) wait.until(ExpectedConditions.presenceOfElementLocated(healedBy));
            } else {
                throw new NoSuchElementException("❌ Failed to heal locator: " + locatorKey, e);
            }
        }
    }

    public List<WebElement> findElements(SearchContext context) {
        String locatorKey = this.originalBy.toString();
        if (!HealingContext.isHealingEnabled()) {
            return this.originalBy.findElements(context);
        }
        try {
            if (healedCache.containsKey(locatorKey)) {
                return ((org.openqa.selenium.By) healedCache.get(locatorKey)).findElements(context);
            } else {
                WebDriverWait wait = new WebDriverWait(driver, DEFAULT_WAIT);
                return (List) wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(this.originalBy));
            }
        } catch (InvalidElementStateException | NoSuchElementException | TimeoutException e) {
            Log.info("⚠️ Elements not found after wait: " + locatorKey);
            Log.info("\ud83d\udd01 Attempting healing...");
            org.openqa.selenium.By healedBy = this.healLocator(locatorKey);
            if (healedBy != null) {
                healedCache.put(locatorKey, healedBy);
                Log.info("✅ Healing successful for elements. Cached: " + String.valueOf(healedBy));
                WebDriverWait wait = new WebDriverWait(driver, DEFAULT_WAIT);
                return (List) wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(healedBy));
            } else {
                throw new NoSuchElementException("❌ Failed to heal elements for locator: " + locatorKey, e);
            }
        }
    }

    private org.openqa.selenium.By healLocator(String rawLocator) {
        try {
            if (driver == null) {
                Log.info("❌ WebDriver not set. Cannot capture page source.");
                return null;
            }

            Log.info("\ud83e\udd16 Healing locator: " + rawLocator);
            String cleanedLocator = rawLocator.replace("By.xpath: ", "").replace("By.id: ", "").replace("By.name: ", "").replace("By.cssSelector: ", "").replace("By.className: ", "").replace("By.tagName: ", "").replace("By.linkText: ", "").replace("By.partialLinkText: ", "").trim();
            HtmlGenerator htmlGenerator = new HtmlGenerator();
            htmlGenerator.clearHTMLSnapshotsDirectory();
            htmlGenerator.generatePageHTML(driver.getPageSource());
            String healedLocator = (new AIIntegrationService()).autoAnalyzeAndFix(cleanedLocator);
            if (healedLocator != null && !healedLocator.isEmpty()) {
                Log.info("\ud83c\udf10 AI returned healed locator: " + healedLocator);
                if (healedLocator.startsWith("By.id(")) {
                    String value = healedLocator.substring(7, healedLocator.length() - 2);
                    return org.openqa.selenium.By.id(value);
                }

                if (healedLocator.startsWith("By.xpath(")) {
                    String value = healedLocator.substring(9, healedLocator.length() - 2);
                    return org.openqa.selenium.By.xpath(value);
                }

                if (healedLocator.startsWith("By.name(")) {
                    String value = healedLocator.substring(8, healedLocator.length() - 2);
                    return org.openqa.selenium.By.name(value);
                }

                if (healedLocator.startsWith("By.cssSelector(")) {
                    String value = healedLocator.substring(15, healedLocator.length() - 2);
                    return org.openqa.selenium.By.cssSelector(value);
                }

                if (healedLocator.startsWith("By.className(")) {
                    String value = healedLocator.substring(13, healedLocator.length() - 2);
                    return org.openqa.selenium.By.className(value);
                }

                if (healedLocator.startsWith("By.tagName(")) {
                    String value = healedLocator.substring(11, healedLocator.length() - 2);
                    return org.openqa.selenium.By.tagName(value);
                }

                if (healedLocator.startsWith("By.linkText(")) {
                    String value = healedLocator.substring(11, healedLocator.length() - 2);
                    return org.openqa.selenium.By.linkText(value);
                }

                if (healedLocator.startsWith("By.partialLinkText(")) {
                    String value = healedLocator.substring(11, healedLocator.length() - 2);
                    return org.openqa.selenium.By.partialLinkText(value);
                }

                Log.info("⚠️ Unknown locator type. Defaulting to XPath.");
                return org.openqa.selenium.By.xpath(healedLocator);
            }
        } catch (Exception e) {
            Log.info("❌ Healing process failed for: " + rawLocator);
            e.printStackTrace();
        }

        return null;
    }

    public String toString() {
        return "ByHealable(" + this.originalBy.toString() + ")";
    }
}
