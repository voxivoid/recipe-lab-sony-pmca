package com.sony.scalar.sysutil;

import java.util.HashMap;
import java.util.Map;

/**
 * Test double of the camera's {@code com.sony.scalar.sysutil.ScalarInput}, shaped like the OpenMemories-Framework stub
 * (static {@code getKeyStatus(int)} → {@code KeyStatus}, static {@code getKeyLogicCode(int, int, int)} → int), so
 * KeyProbe's reflection is exercised against the real signatures. A key nobody set up throws, the way a missing class
 * or a firmware without the call fails — so a test that sets nothing up sees the "off the camera" answers.
 */
public class ScalarInput {
    private static final Map<Integer, int[]> STATUS = new HashMap<Integer, int[]>();
    private static final Map<Integer, Integer> LOGIC = new HashMap<Integer, Integer>();

    public static void setKey(int scan, int valid, int status) { STATUS.put(scan, new int[] { valid, status }); }
    public static void setLogic(int scan, int code) { LOGIC.put(scan, code); }

    public static KeyStatus getKeyStatus(int scan) {
        int[] s = STATUS.get(scan);
        if (s == null) throw new UnsupportedOperationException("no key " + scan);
        KeyStatus k = new KeyStatus();
        k.valid = s[0]; k.status = s[1];
        return k;
    }

    public static int getKeyLogicCode(int scan, int pressed, int mode) {
        Integer c = LOGIC.get(scan);
        if (c == null || pressed != 1) throw new UnsupportedOperationException("no logic code for " + scan);
        return c;
    }
}
