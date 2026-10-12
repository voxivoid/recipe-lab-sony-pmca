package com.voxivoid.recipelab.transfer;

/**
 * What the Transfer to phone page says, and how it moves with the events {@link TransferMode} feeds it: the camera's
 * hotspot (Sony's Direct mode) starting, up with its network name and password, or failed and why; plus what is wrong
 * with the server itself and the last hotspot event. No android.* import may appear in this class (tools/test.sh):
 * the camera half only reports events, and the page's words come from DevTools in the display language.
 */
public final class TransferState {
    /** the status: starting, ready (the phone can join), or one of the reasons the hotspot did not start */
    public static final int STARTING = 0, READY = 1, UNAVAILABLE = 2, REFUSED = 3, NO_CONFIG = 4, ERROR = 5, FAILED = 6,
            TIMEOUT = 7;

    /** what is wrong with the server itself: nothing, no memory card, the port would not open */
    public static final int SERVER_OK = 0, SERVER_NO_CARD = 1, SERVER_PORT = 2;

    /** a hotspot event worth a line while no request has come in yet */
    public static final int NOTE_NONE = 0, NOTE_PHONE_JOINED = 1, NOTE_PHONE_LEFT = 2;

    /** how long the hotspot gets to start before the page says it did not */
    public static final long START_TIMEOUT_MS = 30000;

    private int status = STARTING, server = SERVER_OK, note = NOTE_NONE;
    private String ssid, password, detail, serverDetail;
    private long since;

    public int status() { return status; }
    public boolean ready() { return status == READY; }
    /** true once the hotspot has failed: the page says why, and MENU is the way out */
    public boolean failed() { return status >= UNAVAILABLE; }
    /** the hotspot's network name, once it is up; null otherwise (or when the camera did not say) */
    public String ssid() { return ssid; }
    /** the hotspot's password, once it is up; null otherwise */
    public String password() { return password; }
    /** what the failure said (an error code, an exception's name); null when it has none */
    public String detail() { return detail; }
    /** {@code ip:port} the phone connects to: fixed, since the camera is the access point */
    public static String address() { return Protocol.HOTSPOT_IP + ":" + Protocol.HTTP_PORT; }
    public int server() { return server; }
    public String serverDetail() { return serverDetail; }
    public int note() { return note; }

    /** The hotspot is starting, at {@code now} (elapsed ms): nothing known yet. */
    public void start(long now) {
        status = STARTING;
        ssid = password = detail = null;
        note = NOTE_NONE;
        since = now;
    }

    /** The hotspot is up: the phone joins {@code name} with {@code pass} (either may be unknown). */
    public void up(String name, String pass) {
        status = READY;
        ssid = unquote(name);
        password = unquote(pass);
        detail = null;
    }

    /** The hotspot could not start: UNAVAILABLE, REFUSED, NO_CONFIG, ERROR or FAILED, with its detail. */
    public void failed(int why, String what) {
        status = why;
        ssid = password = null;
        detail = what;
    }

    public void note(int n) { note = n; }

    /** What stops the server from serving; SERVER_OK clears it. */
    public void serverProblem(int problem, String about) {
        server = problem;
        serverDetail = about;
    }

    /** Once a second: a hotspot that is taking too long says so. */
    public void tick(long now) {
        if (status == STARTING && now - since > START_TIMEOUT_MS) status = TIMEOUT;
    }

    /** Android hands SSIDs out in quotes: "Cam" → Cam. Null stays null, an empty one is unknown. */
    static String unquote(String s) {
        if (s == null) return null;
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
        return s.length() == 0 ? null : s;
    }
}
