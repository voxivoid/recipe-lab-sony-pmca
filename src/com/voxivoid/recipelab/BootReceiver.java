package com.voxivoid.recipelab;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * Applies the stored picture profile at boot, so it survives a power cycle. ScalarA's
 * {@code DAConnectionManagerService} sends its own boot-completed event to receivers that register for it, in place of
 * Android's standard {@code BOOT_COMPLETED}. The app keeps the profile as a {@code look.txt} tag on the SD card
 * ({@link #dir}): SharedPreferences live on a ramdisk and are lost at power-off.
 *
 * <p>The receiver starts {@link IspProfiles#bootCommand}: apply, then reapply every 2 s for about 2 minutes, which
 * catches the camera's ISP init rewriting gamma 30–60 s after boot. The same loop runs when the app is left
 * ({@link #startLoop}). While it runs the OSAL shell is busy, so opening the app drops an {@code app} file that ends it.
 *
 * <p>ScalarA sends the event again each time the app opens, after {@code onResume}; {@link #appOpened} marks that, so
 * the receiver does not start a second loop (which would delete the {@code app} file and hold the shell).
 */
public class BootReceiver extends BroadcastReceiver {

    private static volatile boolean appSeen;

    /** the profile directory on the SD card: persistent across boots, readable without SharedPreferences */
    static File dir() {
        File root = Environment.getExternalStorageDirectory();
        return root == null ? null : new File(new File(root, CardLog.DIR), "ISP");
    }

    /** the app is in front: no loop from the receiver in this process, and a running one ends at its next check */
    static void appOpened() {
        appSeen = true;
        File d = dir();
        if (d == null) return;
        try { d.mkdirs(); new FileOutputStream(new File(d, "app")).close(); } catch (Throwable ignored) {}
    }

    @Override
    public void onReceive(Context c, Intent i) {
        File dir = dir();
        if (dir == null) return;
        if (appSeen) { logBoot(dir, "app open, skipping"); return; }
        appSeen = true;
        try {
            String lookText = readSmall(new File(dir, "look.txt"));
            if (lookText == null) { logBoot(dir, "no look.txt"); return; }
            IspProfiles.Look look = Params.lookFrom(lookText.trim());
            if (look == null) { logBoot(dir, "look parse failed: " + lookText.trim()); return; }
            logBoot(dir, startLoop(c, look) ? "sent: " + look : "not sent: " + look);
        } catch (Throwable t) {
            logBoot(dir, "onReceive crashed: " + t);
        }
    }

    /** writes {@code look} for the helper and starts the keep-alive loop; false when it could not */
    static boolean startLoop(Context c, IspProfiles.Look look) {
        File dir = dir();
        if (dir == null) return false;
        try {
            String cmd = IspProfiles.bootCommand(c.getApplicationInfo().nativeLibraryDir + "/librlisp.so", dir.getPath());
            if (cmd == null) return false;
            dir.mkdirs();
            FileOutputStream o = new FileOutputStream(new File(dir, "profile.bin"));
            try { o.write(IspProfiles.encode(look)); } finally { o.close(); }
            new File(dir, "stop").delete();
            new File(dir, "app").delete();
            new File(dir, "log").delete();
            NativeShell.exec(cmd);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** one line to boot.log in the profile dir, and to LOG.TXT on the card */
    private static void logBoot(File dir, String msg) {
        String line = CardLog.line("BOOT", msg);
        try {
            FileOutputStream o = new FileOutputStream(new File(dir, "boot.log"), true);
            try { o.write(line.getBytes()); } finally { o.close(); }
        } catch (Throwable t) {}
        try { CardLog.append(CardLog.dir(Environment.getExternalStorageDirectory()), CardLog.LOG, line); } catch (Throwable t) {}
    }

    private static String readSmall(File f) {
        if (!f.exists()) return null;
        try {
            byte[] b = new byte[(int) Math.min(f.length(), 1024)];
            FileInputStream in = new FileInputStream(f);
            try { int n = in.read(b); return n > 0 ? new String(b, 0, n) : null; }
            finally { in.close(); }
        } catch (Throwable t) { return null; }
    }
}
