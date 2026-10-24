package com.voxivoid.recipelab.transfer;

/**
 * Protocol 4 constants and the JSON document that identify the camera. The phone app (Recipe Lab Studio)
 * requires exactly this app name and version; change them only together with it.
 *
 * <p>Protocol 2 over Photo Studio Link's protocol 1: the app is "recipe-lab", JPEGs are listed and previewed beside
 * the ARWs, and {@code GET /api/recipes} lists the custom recipe files. Protocol 3 over 2: {@code PUT} writes a custom
 * recipe file, so the phone can send recipes as well as fetch them; everything protocol 2 had is unchanged.
 * Protocol 4 over 3: {@code DELETE} removes a custom recipe file.
 */
public final class Protocol {
    public static final String APP = "recipe-lab";
    public static final int VERSION = 4;
    public static final int HTTP_PORT = 8080;
    /** The camera's own address when it runs the hotspot (Sony's Direct mode). */
    public static final String HOTSPOT_IP = "192.168.122.1";
    /** Transfer buffer. Large buffers have crashed cameras; the link is ~1 MB/s anyway. */
    public static final int BUFFER_SIZE = 32 * 1024;
    /** The largest recipe file a PUT may write: the size past which Recipe Lab does not read one. */
    public static final int MAX_RECIPE_BYTES = 8192;

    private Protocol() {}

    /** Body of {@code GET /api/info}. */
    public static String infoJson(String model, String name) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("{\"app\":");
        Json.quote(sb, APP);
        sb.append(",\"protocol\":").append(VERSION).append(",\"model\":");
        Json.quote(sb, model);
        sb.append(",\"name\":");
        Json.quote(sb, name);
        sb.append('}');
        return sb.toString();
    }

    /** "ILCE-6000" becomes "Sony α6000"; anything unrecognised is returned as "Sony &lt;model&gt;". */
    public static String friendlyName(String model) {
        if (model == null || model.length() == 0) return "Sony camera";
        if (model.startsWith("ILCE-") && model.length() == 9) {
            String digits = model.substring(5);
            boolean allDigits = true;
            for (int i = 0; i < digits.length(); i++) if (!Character.isDigit(digits.charAt(i))) allDigits = false;
            if (allDigits) return "Sony α" + digits;
        }
        return "Sony " + model;
    }
}
