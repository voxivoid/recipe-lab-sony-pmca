package com.voxivoid.recipelab.transfer;

import java.io.UnsupportedEncodingException;

/** UTF-8 conversions without java.nio.charset.StandardCharsets (API 19; the camera is API 10). */
public final class Utf8 {
    private Utf8() {}

    public static byte[] bytes(String s) {
        try {
            return s.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String string(byte[] b) {
        try {
            return new String(b, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String string(byte[] b, int off, int len) {
        try {
            return new String(b, off, len, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
