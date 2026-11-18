package com.voxivoid.recipelab;

/**
 * JNI binding to the camera's root shell (librecipelab.so): the command runs as root outside Android's root, so a path
 * the app sees as /x is /android/x there ({@link IspProfiles#command}). It returns at once and prints nothing; the
 * command has to report through a file. 255 characters at most.
 */
public class NativeShell {
    static { System.loadLibrary("recipelab"); }

    public static native void exec(String cmd) throws NativeException;
}
