package com.fawry;

import com.fawry.utilities.Log;
import org.openqa.selenium.*;
import org.openqa.selenium.JavascriptExecutor;
import java.util.*;

/**
 * WebElementValidator
 * --------------------
 * A robust validator that:
 *  1. Checks element presence
 *  2. Scrolls to reveal elements
 *  3. Uses AI self-healing if any locators fail
 *  4. Returns a healed list of By locators
 */
public class WebElementValidator {

    private   WebDriver driver;

    public WebElementValidator(WebDriver driver) {
        this.driver = driver;
    }

    /**
     * Check if an element exists and is displayed.
     */
    private  boolean isElementPresent(By locator) {
        try {
            List<WebElement> elements = driver.findElements(locator);
            return !elements.isEmpty() && elements.get(0).isDisplayed();
        } catch (Exception e) {
            Log.info("[WARN] Failed to check element presence: " + locator);
            return false;
        }
    }

    /**
     * Scroll to try to locate the element up to 5 times.
     */
    private  boolean scrollToFindElement(By locator) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        int maxScrolls = 5;

        for (int i = 0; i < maxScrolls; i++) {
            if (isElementPresent(locator)) return true;
            try {
                js.executeScript("window.scrollBy(0, 400);");
                Thread.sleep(500);
            } catch (Exception e) {
                Log.info("[WARN] Scroll attempt " + (i + 1) + " failed for " + locator);
            }
        }
        return false;
    }
    /**
     * Scrolls back to the top of the page (for Web context).
     */
    private  void scrollToTop() {
        try {
            // Works for WebDriver-based browsers
            ((org.openqa.selenium.JavascriptExecutor) driver)
                    .executeScript("window.scrollTo(0, 0);");
            Thread.sleep(1000); // small pause for stability
            System.out.println("[INFO] Page scrolled back to top.");
        } catch (Exception e) {
            System.out.println("[WARN] Failed to scroll to top: " + e.getMessage());
        }
    }


    /**
     * Validate all locators and attempt to heal missing ones using AI.
     */
    public  List<By> validateAndHealElements(List<By> originalElements) {
        List<By> updatedElements = new ArrayList<>(Collections.nCopies(originalElements.size(), null));
        List<Integer> missingIndexes = new ArrayList<>();
        List<String> damagedXpaths = new ArrayList<>();

        try {
            Log.info("***** Element Validation Started *****");

            HtmlGenerator htmlGenerator = new HtmlGenerator();
            AIIntegrationService aiService = new AIIntegrationService();

            // === Step 1: Initial validation (with scrolling) ===
            for (int i = 0; i < originalElements.size(); i++) {
                By element = originalElements.get(i);
                if (scrollToFindElement(element)) {
                    Log.info("[FOUND] Element found: " + element);
                    updatedElements.set(i, element);
                } else {
                    Log.info("[MISSING] Element not found (even after scrolling): " + element);
                    missingIndexes.add(i);
                    damagedXpaths.add(element.toString().replace("By.xpath: ", ""));

                    // ✅ Scroll to top after missing element
                    scrollToTop();
                }
            }

            // === Step 2: AI-based healing for missing elements ===
            if (!missingIndexes.isEmpty()) {
                Log.info("[AI] Healing " + missingIndexes.size() + " missing elements...");
                htmlGenerator.generatePageHTML(driver.getPageSource());

                List<String> fixedXpaths = aiService.autoAnalyzeAndFix(damagedXpaths);

                for (int j = 0; j < missingIndexes.size(); j++) {
                    int idx = missingIndexes.get(j);
                    String healedXpath = (fixedXpaths != null && j < fixedXpaths.size())
                            ? fixedXpaths.get(j)
                            : null;

                    if (healedXpath != null && !healedXpath.trim().isEmpty()) {
                        updatedElements.set(idx, By.xpath(healedXpath.trim()));
                        Log.info("[AI FIXED] New XPath at index " + idx + ": " + healedXpath.trim());
                    } else {
                        updatedElements.set(idx, originalElements.get(idx));
                        Log.info("[FALLBACK] Using original XPath for index " + idx);
                    }
                }
            }

            // === Step 3: Final validation (post-healing) ===
            for (By element : updatedElements) {
                if (element == null || !scrollToFindElement(element)) {
                    Log.error("[FAIL] Element still not found after healing: " + element);
                    return Collections.emptyList();
                }
            }

            Log.info("***** Element Validation Completed Successfully *****");
            return updatedElements;

        } catch (Exception e) {
            Log.error("[ERROR] Exception in validateAndHealElements: " + e.getMessage(), e);
            return Collections.emptyList();
        }
    }
}
