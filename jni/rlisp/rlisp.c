/*
 * rlisp - Recipe Lab's root helper for picture profiles on the A6000 (FW 3.21).
 *
 * RAM only. It writes the ISP's gamma table and colour matrix (MMIO SRAM) and stops the camera's own setters from
 * overwriting them by patching each setter's first instruction to `bx lr` in av-cam's code (DDR, through /dev/mem).
 * Nothing touches flash; a power cycle gives the stock camera back. The app starts it through the platform's
 * shell_exec_async (it runs as root, outside the Android chroot, so every path it gets carries the /android prefix).
 *
 *   rlisp ping   <dir>      write "RESULT PONG uid=<n>" to <dir>/log: is the root shell there?
 *   rlisp apply  <dir>      apply <dir>/profile.bin (written by IspProfiles.encode)
 *   rlisp reapply <dir>     like apply, but skip the write if gamma already matches (boot retries)
 *   rlisp off    <dir>      restore the setters; the camera puts its own look back at its next event
 *   rlisp status <dir>      setter states, first gamma words, the matrix
 *
 * The log always ends with one "RESULT ..." line, which is what the app waits for. Before anything is written the
 * setter bytes are compared with FW 3.21's: anything else (another body, another firmware) gets "RESULT REFUSE fw"
 * and nothing is touched.
 *
 * Freestanding (no libc): built by build.sh/build.cmd with the NDK's gcc and shipped as lib/armeabi/librlisp.so so
 * the installer unpacks it executable. Register map and evidence: docs/DEVELOPMENT.md "Picture profiles".
 */
typedef unsigned long ul;
typedef unsigned char u8;

static long sc(long n, long a, long b, long c, long d, long e)
{
    register long r0 asm("r0") = a, r1 asm("r1") = b, r2 asm("r2") = c, r3 asm("r3") = d, r4 asm("r4") = e, r7 asm("r7") = n;
    asm volatile("svc 0" : "+r"(r0) : "r"(r1), "r"(r2), "r"(r3), "r"(r4), "r"(r7) : "memory");
    return r0;
}
static long sc6(long n, long a, long b, long c, long d, long e, long f)
{
    register long r0 asm("r0") = a, r1 asm("r1") = b, r2 asm("r2") = c, r3 asm("r3") = d, r4 asm("r4") = e, r5 asm("r5") = f,
                  r7 asm("r7") = n;
    asm volatile("svc 0" : "+r"(r0) : "r"(r1), "r"(r2), "r"(r3), "r"(r4), "r"(r5), "r"(r7) : "memory");
    return r0;
}
#define SYS_exit 1
#define SYS_read 3
#define SYS_write 4
#define SYS_open 5
#define SYS_close 6
#define SYS_fchmod 94
#define SYS_llseek 140
#define SYS_mmap2 192
#define SYS_getuid32 199

/* the compiler may emit these for struct/array copies */
void *memcpy(void *d, const void *s, ul n) { u8 *a = d; const u8 *b = s; while (n--) *a++ = *b++; return d; }
void *memset(void *d, int c, ul n) { u8 *a = d; while (n--) *a++ = (u8)c; return d; }

/* ---- FW 3.21 facts (a6000-qemu docs/A6000_ISP_REGISTER_MAP.md) ---- */
#define GAMMA_PA 0xF3215300UL   /* 96 x u32, 10-bit output per knot */
#define MATRIX_PA 0xF321550CUL  /* 9 x u32, s16 Q10 in the low half, row-major */
#define NG 96
#define NM 9
/* setter PA (av-cam VA - 0x40000000) and its first 4 bytes; patched = 70 47 (bx lr) + the original second halfword */
static const ul SET_PA[2] = { 0x83b67414UL, 0x83b675e0UL };
static const u8 SET_ORIG[2][4] = { { 0x00, 0xf5, 0xa8, 0x30 }, { 0x70, 0xb5, 0x0c, 0x46 } };
/* the Standard creative style's matrix, used as the stock base when it cannot be read clean */
static const int STD_MATRIX[NM] = { 1292, -180, -88, -44, 1340, -272, -24, -108, 1156 };

#define ST_ORIG 0
#define ST_PATCHED 1
#define ST_UNKNOWN 2

static ul slen(const char *s) { ul n = 0; while (s[n]) n++; return n; }
static int streq(const char *a, const char *b) { while (*a && *a == *b) a++, b++; return *a == *b; }

static long g_log = 1;
static void out(const char *s) { sc(SYS_write, g_log, (long)s, slen(s), 0, 0); }
static void outd(long v)
{
    char t[12], b[14]; int n = 0, k = 0; ul u = v < 0 ? (ul)-v : (ul)v;
    if (!u) t[n++] = '0';
    while (u) { t[n++] = '0' + u % 10; u /= 10; }
    if (v < 0) b[k++] = '-';
    while (n) b[k++] = t[--n];
    b[k] = 0; out(b);
}
static void finish(const char *result, int code)
{
    out("RESULT "); out(result); out("\n");
    if (g_log != 1) sc(SYS_close, g_log, 0, 0, 0, 0);
    sc(SYS_exit, code, 0, 0, 0, 0);
}
static void fail(const char *why) { char b[96]; ul i = 0; const char *p = "ERR "; while (*p) b[i++] = *p++; while (*why && i < 95) b[i++] = *why++; b[i] = 0; finish(b, 1); }

static char g_path[256];
static const char *path(const char *dir, const char *name)
{
    ul i = 0;
    while (*dir && i < 200) g_path[i++] = *dir++;
    g_path[i++] = '/';
    while (*name && i < 255) g_path[i++] = *name++;
    g_path[i] = 0; return g_path;
}

static long read_file(const char *p, void *buf, ul max)
{
    long fd = sc(SYS_open, (long)p, 0, 0, 0, 0), n = 0, r;
    if (fd < 0) return -1;
    while ((ul)n < max && (r = sc(SYS_read, fd, (long)((u8 *)buf + n), max - n, 0, 0)) > 0) n += r;
    sc(SYS_close, fd, 0, 0, 0, 0); return n;
}
static int write_file(const char *p, const void *buf, ul n)
{
    long fd = sc(SYS_open, (long)p, 1 | 0100 | 01000 /*O_WRONLY|O_CREAT|O_TRUNC*/, 0666, 0, 0);
    if (fd < 0) return -1;
    long w = sc(SYS_write, fd, (long)buf, n, 0, 0);
    sc(SYS_fchmod, fd, 0666, 0, 0, 0);
    sc(SYS_close, fd, 0, 0, 0, 0);
    return w == (long)n ? 0 : -1;
}

/* ---- av-cam code bytes (DDR) through /dev/mem read/write ---- */
static long g_mem = -1;
static void mem_at(ul pa)
{
    long off;
    if (g_mem < 0 && (g_mem = sc(SYS_open, (long)"/dev/mem", 2 /*O_RDWR*/, 0, 0, 0)) < 0) fail("open /dev/mem");
    if (sc(SYS_llseek, g_mem, 0, (long)pa, (long)&off, 0) < 0) fail("llseek /dev/mem");
}
static int setter_state(int i)
{
    u8 b[4];
    mem_at(SET_PA[i]);
    if (sc(SYS_read, g_mem, (long)b, 4, 0, 0) != 4) fail("read setter");
    int orig = 1, patched = b[0] == 0x70 && b[1] == 0x47;
    for (int k = 0; k < 4; k++) if (b[k] != SET_ORIG[i][k]) orig = 0;
    for (int k = 2; k < 4; k++) if (b[k] != SET_ORIG[i][k]) patched = 0;
    return orig ? ST_ORIG : patched ? ST_PATCHED : ST_UNKNOWN;
}
static void setter_set(int i, int patch)
{
    u8 b[4] = { SET_ORIG[i][0], SET_ORIG[i][1], SET_ORIG[i][2], SET_ORIG[i][3] };
    if (patch) { b[0] = 0x70; b[1] = 0x47; }
    mem_at(SET_PA[i]);
    if (sc(SYS_write, g_mem, (long)b, 4, 0, 0) != 4) fail("write setter");
    if (setter_state(i) != (patch ? ST_PATCHED : ST_ORIG)) fail("setter readback");
}

/* ---- ISP registers (MMIO) through an mmap of /dev/mem: both tables sit in page 0xF3215000 ---- */
static volatile ul *g_isp;
static volatile ul *isp(ul pa)
{
    if (!g_isp) {
        long fd = sc(SYS_open, (long)"/dev/mem", 2 | 0x101000 /*O_RDWR|O_SYNC*/, 0, 0, 0);
        if (fd < 0) fail("open /dev/mem");
        long m = sc6(SYS_mmap2, 0, 0x1000, 3, 1 /*MAP_SHARED*/, fd, 0xF3215000UL >> 12);
        if ((ul)m > (ul)-4096) fail("mmap ISP");
        g_isp = (volatile ul *)m;
    }
    return g_isp + ((pa - 0xF3215000UL) >> 2);
}
static int s16(ul w) { w &= 0xFFFF; return w & 0x8000 ? (int)w - 65536 : (int)w; }

static const char *state_name(int s) { return s == ST_ORIG ? "original" : s == ST_PATCHED ? "patched" : "UNKNOWN"; }
static void check_fw(int st[2])
{
    for (int i = 0; i < 2; i++) {
        st[i] = setter_state(i);
        out(i ? "SETTER matrix " : "SETTER gamma "); out(state_name(st[i])); out("\n");
    }
    if (st[0] == ST_UNKNOWN || st[1] == ST_UNKNOWN) finish("REFUSE fw", 2);
}

/* ---- the profile the app wrote (IspProfiles.encode) ---- */
#define F_GAMMA 1
#define F_MATRIX 2
struct profile { u8 magic[4]; ul version, flags; ul gamma[NG]; long t[NM]; /* Q14 */ };
/* what apply remembers between runs: the stock matrix it built on, and the matrix it last wrote */
struct state { u8 magic[4]; long stock[NM], last[NM]; };

static long round_q14(long x) { return x >= 0 ? (x + 8192) >> 14 : -((-x + 8192) >> 14); }

static void cmd_apply(const char *dir)
{
    static struct profile p;
    if (read_file(path(dir, "profile.bin"), &p, sizeof p) != (long)sizeof p || p.magic[0] != 'R' || p.magic[1] != 'L'
        || p.magic[2] != 'P' || p.magic[3] != 'P' || p.version != 1)
        fail("profile.bin missing or not version 1");
    int st[2]; check_fw(st);

    long live[NM], m[NM];
    for (int i = 0; i < NM; i++) live[i] = s16(isp(MATRIX_PA)[i]);
    if (p.flags & F_MATRIX) {
        /* The stock matrix is the creative style's own. It is only read clean while both setters are the camera's and
         * the register does not still hold what we wrote last (Off leaves our values until the camera's next event). */
        static struct state s;
        int have = read_file(path(dir, "state.bin"), &s, sizeof s) == (long)sizeof s && s.magic[0] == 'R' && s.magic[3] == 'T';
        int ours = have;
        for (int i = 0; i < NM && ours; i++) if (live[i] != s.last[i]) ours = 0;
        if (have && st[0] == ST_ORIG && st[1] == ST_ORIG) {
            out("STOCK kept\n");
        } else if (!ours) {
            for (int i = 0; i < NM; i++) s.stock[i] = live[i];
            out(st[0] == ST_ORIG ? "STOCK captured\n" : "STOCK recaptured\n");
        } else out("STOCK kept\n");
        for (int r = 0; r < 3; r++) {
            long sum = 0;
            for (int c = 0; c < 3; c++) {
                long acc = 0;
                for (int k = 0; k < 3; k++) acc += p.t[r * 3 + k] * s.stock[k * 3 + c];
                m[r * 3 + c] = round_q14(acc); sum += m[r * 3 + c];
            }
            m[r * 3 + r] += 1024 - sum;                       /* white stays white */
        }
        s.magic[0] = 'R'; s.magic[1] = 'L'; s.magic[2] = 'S'; s.magic[3] = 'T';
        for (int i = 0; i < NM; i++) s.last[i] = m[i];
        if (write_file(path(dir, "state.bin"), &s, sizeof s)) fail("write state.bin");
    }

    /* freeze first, then write: the camera's writer cannot race us afterwards */
    if ((p.flags & F_GAMMA) && st[0] != ST_PATCHED) setter_set(0, 1);
    if ((p.flags & F_MATRIX) && st[1] != ST_PATCHED) setter_set(1, 1);
    volatile ul *g = isp(GAMMA_PA), *mx = isp(MATRIX_PA);
    if (p.flags & F_GAMMA) for (int i = 0; i < NG; i++) g[i] = p.gamma[i] & 0x3FF;
    if (p.flags & F_MATRIX) for (int i = 0; i < NM; i++) mx[i] = (ul)m[i] & 0xFFFF;

    int bad = 0;
    if (p.flags & F_GAMMA) for (int i = 0; i < NG; i++) if (g[i] != (p.gamma[i] & 0x3FF)) bad++;
    if (p.flags & F_MATRIX) for (int i = 0; i < NM; i++) if (s16(mx[i]) != m[i]) bad++;
    out("GAMMA "); for (int i = 0; i < 6; i++) { outd(g[i]); out(" "); } out("\n");
    out("MATRIX "); for (int i = 0; i < NM; i++) { outd(s16(mx[i])); out(" "); } out("\n");
    if (bad) { out("MISMATCH "); outd(bad); out("\n"); finish("MISMATCH", 3); }
    finish("OK", 0);
}

static void cmd_off(void)
{
    int st[2]; check_fw(st);
    for (int i = 0; i < 2; i++) if (st[i] == ST_PATCHED) setter_set(i, 0);
    finish("OFF", 0);
}

static void cmd_reapply(const char *dir)
{
    static struct profile p;
    if (read_file(path(dir, "profile.bin"), &p, sizeof p) != (long)sizeof p || p.magic[0] != 'R' || p.magic[1] != 'L'
        || p.magic[2] != 'P' || p.magic[3] != 'P' || p.version != 1)
        fail("profile.bin missing or not version 1");
    int st[2]; check_fw(st);
    if (p.flags & F_GAMMA) {
        volatile ul *g = isp(GAMMA_PA);
        int match = 1;
        for (int i = 0; i < NG && match; i++) if (g[i] != (p.gamma[i] & 0x3FF)) match = 0;
        if (match) {
            out("GAMMA "); for (int i = 0; i < 6; i++) { outd(g[i]); out(" "); } out("\n");
            finish("MATCH", 0);
        }
    }
    cmd_apply(dir);
}

static void cmd_status(void)
{
    int st[2]; check_fw(st);
    out("GAMMA "); for (int i = 0; i < 6; i++) { outd(isp(GAMMA_PA)[i]); out(" "); } out("\n");
    out("MATRIX "); for (int i = 0; i < NM; i++) { outd(s16(isp(MATRIX_PA)[i])); out(" "); } out("\n");
    finish(st[0] == ST_PATCHED || st[1] == ST_PATCHED ? "STATUS on" : "STATUS off", 0);
}

void cmain(int argc, char **argv)
{
    if (argc < 3) { out("usage: rlisp ping|apply|off|status <dir>\n"); sc(SYS_exit, 64, 0, 0, 0, 0); }
    const char *op = argv[1], *dir = argv[2];
    long fd = sc(SYS_open, (long)path(dir, "log"), 1 | 0100 | 01000, 0666, 0, 0);
    if (fd >= 0) { g_log = fd; sc(SYS_fchmod, fd, 0666, 0, 0, 0); }
    out("RLISP 1 "); out(op); out("\n");
    if (streq(op, "ping")) { out("RESULT PONG uid="); outd(sc(SYS_getuid32, 0, 0, 0, 0, 0)); out("\n"); sc(SYS_exit, 0, 0, 0, 0, 0); }
    else if (streq(op, "apply")) cmd_apply(dir);
    else if (streq(op, "reapply")) cmd_reapply(dir);
    else if (streq(op, "off")) cmd_off();
    else if (streq(op, "status")) cmd_status();
    fail("unknown command");
}

__attribute__((naked, section(".text.start"))) void _start(void)
{
    asm volatile("ldr r0, [sp]\n add r1, sp, #4\n bl cmain\n b .");
}
