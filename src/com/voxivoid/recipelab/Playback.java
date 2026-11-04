package com.voxivoid.recipelab;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Puts a recipe card in the camera's playback by having the camera save it as one of its own photos. From Recipe Lab
 * Overlay (its Playback), which found the route on the A6000 after brycewalkerdev's Grain Lab found it on the A5000.
 *
 * Playback lists the camera's picture database (AVINDEX), which only the camera's own saving fills: a JPEG the app
 * writes is not in it, and nothing the app can call adds one. What the camera does save is an image in its own memory
 * (an OptimizedImage), given to its JPEG exporter with the card's media id: it encodes it, names it as a shot
 * (DCIM/100MSDCF/DSC0nnnn.JPG) and files it in the database. So:
 *
 *  1. the camera decodes one of its photos — any it took, so in the database (by its avindex:// id);
 *  2. its scaler makes a new image of the card's size from it (ImageCanvas.steps);
 *  3. the card's pixels are written over that image's (NativePlayback), into the memory the DSP describes
 *     (ImageImageCanvas.GEOMETRY), once it is sure the new image's memory is not the photo's (ImageCanvas.separate);
 *  4. the exporter saves it, and calls back; the new file is looked for in DCIM and in the database.
 *
 * Steps 1 and 2 are {@link #prepare}, 4 is {@link #export}; the caller does 3 in between. The camera must be closed:
 * while it is open the decoder refuses every photo. A card with no photo the camera took cannot be saved this way.
 *
 * Everything is reached by reflection: com.sony.scalar.graphics (stillPlay) and com.sony.scalar.hardware
 * (sony.cameraex) are on the camera's boot class path, not in an SDK. Runs on a Handler's thread, which must have a
 * Looper: the exporter calls back on it.
 */
final class Playback {
    private Playback() {}

    /** what came of it, on the Handler's thread: the new photo's path, or null with why not */
    interface Done { void done(String path, String why); }

    /** a line for the log */
    interface Log { void line(String s); }

    private static final long CALLBACK_MS = 90000, LOOK_MS = 400;
    private static final int LOOKS = 10;
    /** the photos tried as the image to write into, at most: a decode that fails costs little, but a card holds thousands */
    private static final int TEMPLATES = 8;

    /** the images, filters and exporter made on the way, released together, filters first, once the camera is done */
    private static final class Held {
        final List<Object> filters = new ArrayList<Object>(), images = new ArrayList<Object>();
        Object exporter;
        void release() {
            Playback.release(exporter); exporter = null;
            for (Object f : filters) Playback.release(f);
            for (int i = images.size() - 1; i >= 0; i--) Playback.release(images.get(i));
            filters.clear(); images.clear();
        }
    }

    /** the camera's image, made for the card's rows to be written into (NativePlayback) */
    static final class Target {
        final Held held = new Held();
        Object image, frame;
        int[] geometry;
        String media;
        Set<String> before;
    }

    /**
     * Makes the image a {@code w} × {@code h} card will be written into, from a photo in the camera's database. The camera
     * must be closed. Throws, with why, when it cannot.
     */
    static Target prepare(ContentResolver cr, File dcim, int w, int h, Log log) throws Exception {
        Target t = new Target();
        try {
            Class<?> store = Class.forName("com.sony.scalar.provider.AvindexStore");
            String[] cards = (String[]) store.getMethod("getExternalMediaIds").invoke(null);
            if (cards == null || cards.length == 0) throw new IllegalStateException("no card in the camera's database");
            t.media = cards[0];
            t.before = new HashSet<String>(jpegs(dcim));

            // a photo in the database, the newest first: the image is only a vessel for the card's pixels, but the camera
            // files the card under that photo's date
            List<String> dates = new ArrayList<String>();
            List<String> ids = catalogIds(cr, t.media, dates);

            int tried = 0;
            for (int k = 0; k < ids.size(); k++) {
                String id = ids.get(k);
                if (tried++ >= TEMPLATES) break;
                StringBuilder why = new StringBuilder();
                Object frame = decode(id, why);
                if (frame == null) { log.line("playback: the camera could not decode " + id + ": " + why); continue; }
                t.held.images.add(frame);
                int fw = (Integer) call(frame, "getWidth"), fh = (Integer) call(frame, "getHeight");
                int[][] steps = ImageCanvas.steps(w, h, fw, fh);
                if (steps == null) {
                    log.line("playback: " + fw + "x" + fh + " cannot make " + w + "x" + h);
                    release(frame); t.held.images.remove(frame);
                    continue;
                }
                Object from = frame;
                for (int[] s : steps) {
                    Object next = scale(t.held, from, s[0], s[1]);
                    if (from != frame) { release(from); t.held.images.remove(from); }
                    from = next;
                }
                t.frame = frame; t.image = from;
                log.line("playback: made " + w + "x" + h + " from " + id + " (" + fw + "x" + fh + ", taken " + dates.get(k) + ")");
                break;
            }
            if (t.image == null) throw new IllegalStateException("no photo the camera could make the picture from");
            t.geometry = geometry(t.image, t.frame);
            return t;
        } catch (Exception e) {
            t.held.release();
            throw e;
        }
    }

    /** lets go of a target the merge did not use */
    static void discard(Target t) { if (t != null) t.held.release(); }

    /**
     * Has the camera save {@code t}'s image, the card's rows written into it, as one of its photos. Call it on
     * {@code thread}; {@code done} is called on it, once.
     */
    static void export(final Target t, final ContentResolver cr, final File dcim,
                       final Handler thread, final Log log, final Done done) {
        final boolean[] over = new boolean[1];
        try {
            Class<?> exporterType = Class.forName("com.sony.scalar.graphics.JpegExporter");
            Class<?> options = Class.forName("com.sony.scalar.graphics.JpegExporter$Options");
            Class<?> listener = Class.forName("com.sony.scalar.graphics.JpegExporter$onExportEventListener");
            Object settings = options.newInstance();
            options.getField("quality").setInt(settings, options.getField("FINE").getInt(null));
            final Runnable timeout = new Runnable() {
                public void run() {
                    if (over[0]) return;
                    over[0] = true;                            // the images stay held: the camera may still be using them
                    done.done(null, "the camera did not answer in " + CALLBACK_MS / 1000 + " s");
                }
            };
            Object callback = Proxy.newProxyInstance(listener.getClassLoader(), new Class<?>[] { listener }, new InvocationHandler() {
                public Object invoke(Object proxy, Method m, Object[] args) {
                    String n = m.getName();
                    if (n.equals("hashCode")) return System.identityHashCode(proxy);
                    if (n.equals("equals")) return proxy == args[0];
                    if (n.equals("toString")) return "Playback export";
                    if (n.equals("onExported")) {
                        thread.removeCallbacks(timeout);
                        t.held.release();
                        int code = args != null && args.length > 0 && args[0] instanceof Integer ? (Integer) args[0] : -1;
                        log.line("playback: the camera's export said " + code);
                        if (over[0]) return null;
                        if (code != 0) { over[0] = true; done.done(null, "the camera's export failed (" + code + ")"); }
                        else look(cr, dcim, t.media, t.before, thread, log, done, over, 0);
                    }
                    return null;
                }
            });
            t.held.exporter = exporterType.newInstance();
            exporterType.getMethod("encode", Class.forName("com.sony.scalar.graphics.OptimizedImage"), String.class, options, listener)
                    .invoke(t.held.exporter, t.image, t.media, settings, callback);
            thread.postDelayed(timeout, CALLBACK_MS);
        } catch (Throwable e) {
            t.held.release();
            if (!over[0]) { over[0] = true; done.done(null, String.valueOf(cause(e))); }
        }
    }

    /** after the export: the new JPEG in DCIM that the database has, looked for a few times as the camera files it */
    private static void look(final ContentResolver cr, final File dcim, final String media, final Set<String> before,
                             final Handler thread, final Log log, final Done done, final boolean[] over, final int tries) {
        List<String> fresh = new ArrayList<String>();
        for (String p : jpegs(dcim)) if (!before.contains(p)) fresh.add(p);
        for (String p : fresh)
            if (catalogId(cr, media, p) != null) { over[0] = true; done.done(p, null); return; }
        if (tries + 1 >= LOOKS) {
            over[0] = true;
            done.done(null, "the camera saved it, but it is not in its database yet" + (fresh.isEmpty() ? "" : ": " + fresh));
            return;
        }
        thread.postDelayed(new Runnable() {
            public void run() { look(cr, dcim, media, before, thread, log, done, over, tries + 1); }
        }, LOOK_MS);
    }

    /** a photo's id in the camera's database (avindex://…), by its DCF numbers, or null when the database has no JPEG for it */
    static String catalogId(ContentResolver cr, String media, String path) {
        int[] n = dcfNumbers(path);
        if (n == null) return null;
        Cursor c = null;
        try {
            Uri uri = (Uri) Class.forName("com.sony.scalar.provider.AvindexStore$Images$Media").getMethod("getContentUri", String.class).invoke(null, media);
            c = cr.query(uri, new String[] { "_data", "dcf_folder_number", "dcf_file_number", "exist_jpeg" }, null, null, null);
            if (c == null) return null;
            int data = c.getColumnIndex("_data"), folder = c.getColumnIndex("dcf_folder_number"), file = c.getColumnIndex("dcf_file_number"),
                    jpeg = c.getColumnIndex("exist_jpeg");
            if (data < 0 || folder < 0 || file < 0 || jpeg < 0) return null;
            while (c.moveToNext())
                if (c.getInt(folder) == n[0] && c.getInt(file) == n[1] && c.getInt(jpeg) != 0) {
                    String id = c.getString(data);
                    return id != null && id.startsWith("avindex://") ? id : null;
                }
            return null;
        } catch (Throwable t) {
            return null;
        } finally {
            if (c != null) c.close();
        }
    }

    /**
     * Deletes a photo the camera took the way playback's own delete does: {@code AvindexStore.Images.Media.deleteImage},
     * which takes the files and the database's entry, so playback does not keep a picture it cannot display. Found in the
     * A6000's database_library (deleteImage(ContentResolver, Uri, long _id) → boolean). One entry is the shot, JPG and
     * ARW together. Returns what happened, for the log; false in {@code done[0]} when the camera would not, and the
     * caller deletes the files itself.
     */
    static String deleteShot(ContentResolver cr, String path, boolean[] done) {
        done[0] = false;
        int[] n = dcfNumbers(path);
        if (n == null) return "not a camera file name";
        Cursor c = null;
        try {
            Class<?> store = Class.forName("com.sony.scalar.provider.AvindexStore");
            String[] cards = (String[]) store.getMethod("getExternalMediaIds").invoke(null);
            if (cards == null || cards.length == 0) return "no card in the camera's database";
            Class<?> media = Class.forName("com.sony.scalar.provider.AvindexStore$Images$Media");
            Uri uri = (Uri) media.getMethod("getContentUri", String.class).invoke(null, cards[0]);
            c = cr.query(uri, new String[] { "_id", "dcf_folder_number", "dcf_file_number" }, null, null, null);
            if (c == null) return "the database would not list";
            int id = c.getColumnIndex("_id"), folder = c.getColumnIndex("dcf_folder_number"), file = c.getColumnIndex("dcf_file_number");
            if (id < 0 || folder < 0 || file < 0) return "the database has no _id";
            long found = -1;
            while (c.moveToNext()) if (c.getInt(folder) == n[0] && c.getInt(file) == n[1]) { found = c.getLong(id); break; }
            c.close(); c = null;
            if (found < 0) return "not in the database";
            Object ok = media.getMethod("deleteImage", ContentResolver.class, Uri.class, long.class).invoke(null, cr, uri, found);
            done[0] = Boolean.TRUE.equals(ok);
            return "deleteImage " + found + ": " + ok;
        } catch (Throwable t) {
            return "deleteImage failed: " + cause(t);
        } finally {
            if (c != null) c.close();
        }
    }

    /** the database's column for when a photo was taken; it decides where Save image's card is filed */
    static final String TAKEN = "content_created_utc_date_time";

    /**
     * Every JPEG in the database, by id, the newest taken first (ImageCanvas.newestFirst). A database without the date
     * column is listed as well, by folder and file; {@code dates}, when given, gets each one's date in the same order.
     */
    static List<String> catalogIds(ContentResolver cr, String media, List<String> dates) {
        List<String> ids = new ArrayList<String>();
        Cursor c = null;
        try {
            Uri uri = (Uri) Class.forName("com.sony.scalar.provider.AvindexStore$Images$Media").getMethod("getContentUri", String.class).invoke(null, media);
            String[] withDate = { "_data", "dcf_folder_number", "dcf_file_number", "exist_jpeg", TAKEN };
            try { c = cr.query(uri, withDate, null, null, null); } catch (Throwable t) { c = null; }
            if (c == null || c.getColumnIndex(TAKEN) < 0) {
                if (c != null) c.close();
                c = cr.query(uri, new String[] { "_data", "dcf_folder_number", "dcf_file_number", "exist_jpeg" }, null, null, null);
            }
            if (c == null) return ids;
            int data = c.getColumnIndex("_data"), folder = c.getColumnIndex("dcf_folder_number"), file = c.getColumnIndex("dcf_file_number"),
                    jpeg = c.getColumnIndex("exist_jpeg"), taken = c.getColumnIndex(TAKEN);
            if (data < 0 || folder < 0 || file < 0 || jpeg < 0) return ids;
            List<String> all = new ArrayList<String>(), when = new ArrayList<String>();
            List<Integer> folders = new ArrayList<Integer>(), files = new ArrayList<Integer>();
            while (c.moveToNext()) {
                String id = c.getString(data);
                if (c.getInt(jpeg) == 0 || id == null || !id.startsWith("avindex://")) continue;
                all.add(id);
                when.add(taken < 0 ? null : c.getString(taken));
                folders.add(c.getInt(folder));
                files.add(c.getInt(file));
            }
            int n = all.size();
            int[] fo = new int[n], fi = new int[n];
            for (int i = 0; i < n; i++) { fo[i] = folders.get(i); fi[i] = files.get(i); }
            for (int i : ImageCanvas.newestFirst(when.toArray(new String[n]), fo, fi)) {
                ids.add(all.get(i));
                if (dates != null) dates.add(when.get(i));
            }
        } catch (Throwable t) {
            // none, then
        } finally {
            if (c != null) c.close();
        }
        return ids;
    }

    /** the full-size image of a photo in the database, or null with what went wrong in {@code why} */
    private static Object decode(String id, StringBuilder why) throws Exception {
        Class<?> factory = Class.forName("com.sony.scalar.graphics.OptimizedImageFactory");
        Class<?> options = Class.forName("com.sony.scalar.graphics.OptimizedImageFactory$Options");
        Object image = null;
        try {
            Object o = options.newInstance();
            options.getField("imageType").setInt(o, options.getField("MAIN").getInt(null));
            image = factory.getMethod("decodeImage", String.class, options).invoke(null, id, o);
            if (image == null) why.append("MAIN gave nothing");
            else if (!Boolean.TRUE.equals(call(image, "isValid"))) { why.append("MAIN gave an invalid image"); release(image); image = null; }
        } catch (Throwable t) { why.append("MAIN: ").append(cause(t)); image = null; }
        if (image != null) return image;
        try {                                                      // the plain overload: whatever the camera decodes by default
            image = factory.getMethod("decodeImage", String.class).invoke(null, id);
            if (image == null) why.append("; default gave nothing");
            else if (!Boolean.TRUE.equals(call(image, "isValid"))) { why.append("; default gave an invalid image"); release(image); image = null; }
        } catch (Throwable t) { why.append("; default: ").append(cause(t)); image = null; }
        return image;
    }

    /** a new image, w × h, from the scaler: never the one it is made from */
    private static Object scale(Held held, Object from, int w, int h) throws Exception {
        Class<?> type = Class.forName("com.sony.scalar.graphics.imagefilter.ScaleImageFilter");
        Class<?> image = Class.forName("com.sony.scalar.graphics.OptimizedImage");
        Object f = type.newInstance();
        held.filters.add(f);
        if (!Boolean.TRUE.equals(call(f, "isSupported"))) throw new IllegalStateException("the camera's scaler is not there");
        type.getMethod("setSource", image, boolean.class).invoke(f, from, false);
        type.getMethod("setDestSize", int.class, int.class).invoke(f, w, h);
        if (!Boolean.TRUE.equals(call(f, "execute"))) throw new IllegalStateException("the camera's scaler failed at " + w + "x" + h);
        Object out = call(f, "getOutput");
        if (out == null || out == from || !Boolean.TRUE.equals(call(out, "isValid"))) throw new IllegalStateException("the scaler made no new image");
        held.images.add(out);
        return out;
    }

    /** the image's memory, as the DSP describes it (ImageCanvas.GEOMETRY), once sure it is not the frame's */
    private static int[] geometry(Object image, Object frame) throws Exception {
        Class<?> dsp = Class.forName("com.sony.scalar.hardware.DSP"), memory = Class.forName("com.sony.scalar.hardware.DeviceMemory");
        String device = (String) Class.forName("com.sony.scalar.hardware.DSP$DeviceType").getField("SONY_DI_DSP").get(null);
        Object p = dsp.getMethod("createProcessor", String.class).invoke(null, device);
        if (p == null) throw new IllegalStateException("no DSP");
        try {
            Method get = dsp.getMethod("getProperty", memory, String.class);
            String[] v = new String[ImageCanvas.GEOMETRY.length];
            for (int i = 0; i < v.length; i++) v[i] = (String) get.invoke(p, image, ImageCanvas.GEOMETRY[i]);
            int[] g = ImageCanvas.geometry(v);
            if (g == null) throw new IllegalStateException("the DSP did not describe the image: " + java.util.Arrays.toString(v));
            String frameAt = (String) get.invoke(p, frame, "memory-address"), imageAt = (String) get.invoke(p, image, "memory-address");
            long frameSize;
            try { frameSize = Long.parseLong(((String) get.invoke(p, frame, "memory-size")).trim()); } catch (RuntimeException e) { frameSize = -1; }
            if (!ImageCanvas.separate(frameAt, frameSize, imageAt, g[ImageCanvas.SIZE]))
                throw new IllegalStateException("the new image's memory may be the frame's (" + frameAt + "+" + frameSize + ", " + imageAt + "+" + g[ImageCanvas.SIZE] + ")");
            return g;
        } finally {
            release(p);
        }
    }

    private static Object call(Object o, String method) throws Exception { return o.getClass().getMethod(method).invoke(o); }

    private static void release(Object o) {
        if (o == null) return;
        try { call(o, "release"); } catch (Throwable t) {}
    }

    private static Object cause(Throwable t) { return t instanceof InvocationTargetException && t.getCause() != null ? t.getCause() : t; }

    /** every JPEG in the folders under DCIM, by path */
    static List<String> jpegs(File dcim) {
        List<String> out = new ArrayList<String>();
        File[] folders = dcim == null ? null : dcim.listFiles();
        if (folders == null) return out;
        for (File f : folders) {
            if (!f.isDirectory()) continue;
            String[] names = f.list();
            if (names == null) continue;
            for (String n : names) { String u = n.toUpperCase(java.util.Locale.US); if (u.endsWith(".JPG") || u.endsWith(".JPEG")) out.add(new File(f, n).getPath()); }
        }
        return out;
    }

    /**
     * A DCF photo's numbers, as the camera's picture database keys it: DCIM/100MSDCF/DSC01234.JPG → { 100, 1234 }, the
     * folder's first three digits and the name's last four; null for a path that is not named so.
     */
    static int[] dcfNumbers(String path) {
        if (path == null) return null;
        File f = new File(path), dir = f.getParentFile();
        String name = f.getName(), folder = dir == null ? "" : dir.getName();
        int dot = name.lastIndexOf('.');
        if (dot != 8 || folder.length() != 8) return null;
        String fd = folder.substring(0, 3), nd = name.substring(4, 8);
        if (!fd.matches("[1-9][0-9]{2}") || !nd.matches("[0-9]{4}")) return null;
        return new int[] { Integer.parseInt(fd), Integer.parseInt(nd) };
    }
}
