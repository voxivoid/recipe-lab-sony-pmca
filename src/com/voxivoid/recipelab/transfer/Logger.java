package com.voxivoid.recipelab.transfer;

/** Logging seam: android.util.Log on the camera, stderr or nothing on the host. */
public interface Logger {
    void info(String msg);

    void error(String msg, Throwable t);

    Logger NONE = new Logger() {
        @Override public void info(String msg) {}
        @Override public void error(String msg, Throwable t) {}
    };
}
