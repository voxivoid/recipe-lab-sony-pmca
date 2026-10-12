package com.voxivoid.recipelab.transfer;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Running counters the server updates from its threads and the UI polls once a second. */
public final class Stats {
    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong bytesSent = new AtomicLong();
    private final AtomicInteger active = new AtomicInteger();
    private volatile String lastRequest = "";

    // rate meter state, touched only by the polling thread
    private long lastSampleMs = -1, lastSampleBytes;
    private double rate;

    public long requests() { return requests.get(); }
    public long bytesSent() { return bytesSent.get(); }
    public int active() { return active.get(); }
    public String lastRequest() { return lastRequest; }

    void requestStarted(String summary) {
        requests.incrementAndGet();
        active.incrementAndGet();
        lastRequest = summary;
    }

    void requestFinished(String summary) {
        active.decrementAndGet();
        lastRequest = summary;
    }

    void addBytes(long n) {
        bytesSent.addAndGet(n);
    }

    /**
     * Bytes per second since the previous call, lightly smoothed. Call it from one thread at a steady interval;
     * the first call returns 0.
     */
    public double sampleRate(long nowMs) {
        long bytes = bytesSent.get();
        if (lastSampleMs >= 0 && nowMs > lastSampleMs) {
            double instant = (bytes - lastSampleBytes) * 1000.0 / (nowMs - lastSampleMs);
            rate = rate * 0.4 + instant * 0.6;
            if (rate < 1) rate = 0;
        }
        lastSampleMs = nowMs;
        lastSampleBytes = bytes;
        return rate;
    }

    /** "12.3 MB" (decimal megabytes, like the phone's file sizes). */
    public static String megabytes(long bytes) {
        return String.format(Locale.US, "%.1f MB", bytes / 1e6);
    }

    /** "0.98 MB/s", or "123 KB/s" below 1 MB/s. */
    public static String rate(double bytesPerSecond) {
        if (bytesPerSecond >= 1e6) return String.format(Locale.US, "%.2f MB/s", bytesPerSecond / 1e6);
        return String.format(Locale.US, "%.0f KB/s", bytesPerSecond / 1e3);
    }
}
