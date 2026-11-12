package com.AdamNew.dynamicwaters.client;

public class LoadingProgressTracker {
    public static volatile int progress = 0;
    public static volatile String state = "";
    public static volatile boolean isGenerating = false;

    private static final java.util.concurrent.ConcurrentHashMap<Long, Integer> regionProgresses = new java.util.concurrent.ConcurrentHashMap<>();

    public static void setProgress(long regionKey, int p, String s) {
        regionProgresses.put(regionKey, p);
        state = s;

        int total = regionProgresses.size() * 100;
        int current = 0;
        for (int val : regionProgresses.values()) {
            current += val;
        }
        progress = total == 0 ? 0 : (current * 100) / total;
        isGenerating = true;
    }

    public static void completeRegion(long regionKey) {
        regionProgresses.remove(regionKey);
        if (regionProgresses.isEmpty()) {
            progress = 100;
            state = "Done! Game may freeze momentarily...";
        }
    }
}
