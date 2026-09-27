package com.sony.scalar.sysutil;

/** Test double of the camera's {@code ScalarInput.KeyStatus}: two public int fields, as on the camera. */
public class KeyStatus {
    public static final int VALID = 1, INVALID = 0, STATUS_ON = 1, STATUS_OFF = 0, STATUS_INVALID = 0x7fffffff;
    public int valid, status;
}
