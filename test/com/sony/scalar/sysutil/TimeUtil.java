package com.sony.scalar.sysutil;

/**
 * Test double of the camera's {@code TimeUtil}, shaped like the OpenMemories-Framework stub (static
 * {@code getCurrentCalendar()} → {@code PlainCalendar}, static {@code getCurrentTimeZone()} → {@code PlainTimeZone}).
 * Until a test sets a time it throws, so the other tests see the "off the camera" system clock.
 */
public class TimeUtil {
    private static PlainCalendar cal;
    private static PlainTimeZone tz;

    public static void set(int year, int month, int day, int hour, int minute, int second, int gmtDiff, int summerTimeDiff) {
        cal = new PlainCalendar();
        cal.year = year; cal.month = month; cal.day = day; cal.hour = hour; cal.minute = minute; cal.second = second;
        tz = new PlainTimeZone();
        tz.gmtDiff = gmtDiff; tz.summerTimeDiff = summerTimeDiff;
    }

    public static void clear() { cal = null; tz = null; }

    public static PlainCalendar getCurrentCalendar() {
        if (cal == null) throw new UnsupportedOperationException("no camera clock");
        return cal;
    }

    public static PlainTimeZone getCurrentTimeZone() {
        if (tz == null) throw new UnsupportedOperationException("no camera time zone");
        return tz;
    }
}
