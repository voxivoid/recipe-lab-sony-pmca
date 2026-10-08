package com.voxivoid.recipelab;

/** Sony's three settings-store bytes for the ordinary continuous movie limit (OpenMemories-Tweak). */
final class RecordingLimit {
    private RecordingLimit() {}

    static final int ID_HOURS = 0x003c0373, ID_MINUTES = 0x003c0374, ID_SECONDS = 0x003c0375;
    static final int ID_4K = 0x003c04b6;
    static final int UNKNOWN = -1, STANDARD = 0, LONG = 1, OTHER = 2;

    static int state(int hours, int minutes, int seconds) {
        if (hours == 13 && minutes == 1 && seconds == 0) return LONG;
        if (hours == 0 && minutes == 29 && seconds == 50) return STANDARD;
        return OTHER;
    }

    static int[] value(boolean longRecording) {
        return longRecording ? new int[] { 13, 1, 0 } : new int[] { 0, 29, 50 };
    }

    static int[] ids() { return new int[] { ID_HOURS, ID_MINUTES, ID_SECONDS }; }

    /** The compact bodies whose manuals specify a separate five-minute 4K limit. */
    static boolean hasShort4kLimit(String model) {
        return "DSC-RX100M4".equals(model) || "DSC-RX100M5".equals(model);
    }

    static int state4k(byte[] value) {
        if (value == null || value.length != 2) return UNKNOWN;
        int seconds = (value[0] & 0xff) | ((value[1] & 0xff) << 8);
        return seconds == 0x7fff ? LONG : seconds == 300 ? STANDARD : OTHER;
    }

    static byte[] value4k(boolean longRecording) {
        return longRecording ? new byte[] { (byte) 0xff, 0x7f } : new byte[] { 0x2c, 0x01 };
    }
}
