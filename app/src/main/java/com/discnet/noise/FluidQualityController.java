package com.discnet.noise;

/** Pixel budgets and sustained-load hysteresis; frame measurements include EGL backpressure. */
final class FluidQualityController {
    static final int ECONOMY = 0, BALANCED = 1, HIGH = 2;
    private static final int[] PIXELS = {120000, 270000, 520000};
    private int level = BALANCED, ceiling = HIGH, slow, fast;
    private long changedAt;
    private double averageMs = 12;
    private String previousPreference = "auto";
    void configure(String preference, boolean constrained, long now) {
        int cap = "eco".equals(preference) ? ECONOMY : "balanced".equals(preference) ? BALANCED : HIGH;
        if (constrained) cap = ECONOMY;
        if (!preference.equals(previousPreference)) {
            previousPreference = preference;
            level = "auto".equals(preference) ? Math.min(BALANCED, cap) : cap;
            changedAt = now; slow = fast = 0;
        }
        if (cap != ceiling) { ceiling = cap; slow = fast = 0; }
        if (level > ceiling) { level = ceiling; changedAt = now; }
    }
    void record(double elapsedMs, long now) {
        averageMs += .06 * (elapsedMs - averageMs);
        double budget = level == ECONOMY ? 33.3 : 16.67;
        slow = averageMs > budget * 1.18 ? slow + 1 : Math.max(0, slow - 1);
        fast = averageMs < budget * .78 ? fast + 1 : 0;
        if (now - changedAt < 8_000_000_000L) return;
        if (slow >= 60 && level > ECONOMY) {
            level--; changedAt = now; slow = fast = 0;
        } else if (fast >= 900 && level < ceiling) {
            level++; changedAt = now; slow = fast = 0;
        }
    }
    int level() { return level; }
    int pixelBudget() { return PIXELS[level]; }
    long frameIntervalNanos() { return level == ECONOMY ? 33_333_333L : 16_666_667L; }
    double averageMs() { return averageMs; }
}
