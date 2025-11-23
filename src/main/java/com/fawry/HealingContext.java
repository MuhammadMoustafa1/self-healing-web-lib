package com.fawry;

public class HealingContext {

    // Thread-safe flag so each test thread can enable/disable healing independently
    private static final ThreadLocal<Boolean> healingEnabled = ThreadLocal.withInitial(() -> true);

    /**
     * Enable healing globally in the current thread
     */
    public static void enableHealing() {
        healingEnabled.set(true);
    }

    /**
     * Disable healing temporarily in the current thread
     */
    public static void disableHealing() {
        healingEnabled.set(false);
    }

    /**
     * Check if healing is enabled for this thread
     */
    public static boolean isHealingEnabled() {
        return healingEnabled.get();
    }

    /**
     * Utility: execute a block of code with healing off
     */
    public static void runWithoutHealing(Runnable runnable) {
        boolean previousState = healingEnabled.get();
        try {
            healingEnabled.set(false);
            runnable.run();
        } finally {
            healingEnabled.set(previousState);
        }
    }

    /*
    HealingContext.disableHealing();
    driver.findElement(By.id("nonHealingElement")).click();
    HealingContext.enableHealing();
    HealingContext.runWithoutHealing(() -> {
    driver.findElement(By.id("strictLocator")).click();
});

     */
}
