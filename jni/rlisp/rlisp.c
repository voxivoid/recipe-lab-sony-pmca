/*
 * rlisp - Recipe Lab's root helper for picture profiles on CXD90014 bodies (the A6000 family).
 *
 * RAM only. It writes the ISP's gamma table and colour matrix (MMIO SRAM) and stops the camera's own setters from
 * overwriting them by patching each setter's first instruction to `bx lr` in av-cam's code (DDR, through /dev/mem).
 * Nothing touches flash; a power cycle gives the stock camera back. The app starts it through the platform's
 * shell_exec_async (it runs as root, outside the Android chroot, so every path it gets carries the /android prefix).
 *
 *   rlisp ping    <dir>     write "RESULT PONG uid=<n>" to <dir>/log: is the root shell there?
 *   rlisp apply   <dir>     apply <dir>/profile.bin (written by IspProfiles.encode)
 *   rlisp reapply <dir>     like apply, but skip the write if gamma already matches (boot retries)
 *   rlisp off     <dir>     restore the setters; the camera puts its own look back at its next event
 *   rlisp status  <dir>     setter states, first gamma words, the matrix
 *   rlisp probe   <dir>     read only: which row of bodies.h this camera is, where av-cam sits, the live tables
 *
 * <dir>/body holds "model\nfirmware\n" (IspBodies.bodyFile). The helper acts only on a body and firmware listed in
 * bodies.h, and only once the 32 bytes at both setters match that row (original, or patched by us). Anything else gets
 * "RESULT REFUSE ..." with nothing touched; a refusal that cannot change until the camera does also drops <dir>/stop,
 * which ends the boot loop (IspProfiles.bootCommand) after its first run.
 *
 * The log always ends with one "RESULT ..." line, which is what the app waits for.
 *
 * Freestanding (no libc): built by build.sh/build.cmd with the NDK's gcc and shipped as lib/armeabi/librlisp.so so
 * the installer unpacks it executable. Register map and evidence: docs/PICTURE-PROFILES.md.
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

#include "bodies.h"

#define NG 96
#define NM 9
#define PAGE 0x1000UL
/* the gamma leaf's first 12 bytes (add.w r0,#0x15000; mov.w r2,#0x180; add.w r0,#0x300) and the matrix setter's first
 * 6 (push {r4-r6,lr}; mov r4,r1; mov r5,r0), 0x1cc bytes after it on most av-cams scanned (not the A6500 or RX100 V):
 * what probe looks for on a body that has no row yet */
static const u8 LEAF[12] = { 0x00, 0xf5, 0xa8, 0x30, 0x4f, 0xf4, 0xc0, 0x72, 0x00, 0xf5, 0x40, 0x70 };
static const u8 MSET[6] = { 0x70, 0xb5, 0x0c, 0x46, 0x05, 0x46 };
#define MSET_AFTER_LEAF 0x1cc

#define ST_ORIG 0
#define ST_PATCHED 1
#define ST_UNKNOWN 2

static ul slen(const char *s) { ul n = 0; while (s[n]) n++; return n; }
static int streq(const char *a, const char *b) { while (*a && *a == *b) a++, b++; return *a == *b; }
static int bytes_eq(const u8 *a, const u8 *b, ul n) { while (n--) if (*a++ != *b++) return 0; return 1; }

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
static void outx(ul v, int digits)
{
    char b[9]; int i;
    for (i = digits - 1; i >= 0; i--) { b[i] = "0123456789abcdef"[v & 15]; v >>= 4; }
    b[digits] = 0; out(b);
}
static void outhex(const u8 *p, ul n) { while (n--) outx(*p++, 2); }

static const char *g_dir;
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
    long w = n ? sc(SYS_write, fd, (long)buf, n, 0, 0) : 0;
    sc(SYS_fchmod, fd, 0666, 0, 0, 0);
    sc(SYS_close, fd, 0, 0, 0, 0);
    return w == (long)n ? 0 : -1;
}

static void finish(const char *result, int code)
{
    out("RESULT "); out(result); out("\n");
    if (g_log != 1) sc(SYS_close, g_log, 0, 0, 0, 0);
    sc(SYS_exit, code, 0, 0, 0, 0);
}
static void fail(const char *why) { char b[96]; ul i = 0; const char *p = "ERR "; while (*p) b[i++] = *p++; while (*why && i < 95) b[i++] = *why++; b[i] = 0; finish(b, 1); }
/* a refusal only a different camera or firmware can change: the boot loop must not try 60 more times */
static void refuse_for_good(const char *result)
{
    write_file(path(g_dir, "stop"), "", 0);
    finish(result, 2);
}

/* ---- which camera: <dir>/body, then its row ---- */
static char g_model[24], g_fw[16];
static void read_body(void)
{
    char b[64]; long n = read_file(path(g_dir, "body"), b, sizeof b - 1);
    ul i = 0, k = 0;
    g_model[0] = g_fw[0] = 0;
    if (n <= 0) return;
    b[n] = 0;
    while (b[i] && b[i] != '\n' && k < sizeof g_model - 1) g_model[k++] = b[i++];
    g_model[k] = 0; k = 0;
    while (b[i] && b[i] != '\n') i++;
    if (b[i]) i++;
    while (b[i] && b[i] != '\n' && k < sizeof g_fw - 1) g_fw[k++] = b[i++];
    g_fw[k] = 0;
}
/* the row for this body and firmware; null with *same_model set to a row of the same model when only the fw differs */
static const struct body *find_row(const struct body **same_model)
{
    *same_model = 0;
    for (ul i = 0; i < NBODIES; i++) {
        if (!streq(BODIES[i].model, g_model)) continue;
        if (streq(BODIES[i].fw, g_fw)) return &BODIES[i];
        *same_model = &BODIES[i];
    }
    return 0;
}

/* ---- System RAM ranges from /proc/iomem: /dev/mem is only read inside them ---- */
#define MAXRAM 8
static ul g_ram[MAXRAM][2]; static int g_nram;
static ul parse_hex(const char **s) { ul v = 0; for (;;) { char c = **s; int d = c >= '0' && c <= '9' ? c - '0' : c >= 'a' && c <= 'f' ? c - 'a' + 10 : -1; if (d < 0) return v; v = v * 16 + d; (*s)++; } }
static void read_ram(void)
{
    static char b[4096];
    long n = read_file("/proc/iomem", b, sizeof b - 1);
    if (n <= 0) fail("read /proc/iomem");
    b[n] = 0;
    for (const char *s = b; *s && g_nram < MAXRAM;) {
        const char *line = s;
        while (*s && *s != '\n') s++;
        if (*s) s++;
        if (*line == ' ') continue;                         /* nested resources, not top level */
        const char *p = line; ul lo = parse_hex(&p);
        if (*p != '-') continue;
        p++; ul hi = parse_hex(&p);
        const char *q = p; while (q < s && *q != ':') q++;
        if (q + 12 <= s && bytes_eq((const u8 *)q, (const u8 *)": System RAM", 12)) { g_ram[g_nram][0] = lo; g_ram[g_nram][1] = hi; g_nram++; }
    }
    if (!g_nram) fail("no System RAM in /proc/iomem");
}
static int in_ram(ul pa, ul n)
{
    for (int i = 0; i < g_nram; i++) if (pa >= g_ram[i][0] && pa + n - 1 <= g_ram[i][1]) return 1;
    return 0;
}

/* ---- av-cam code bytes (DDR) through /dev/mem read/write, inside System RAM only ---- */
static long g_mem = -1;
static void mem_at(ul pa)
{
    long off;
    if (g_mem < 0 && (g_mem = sc(SYS_open, (long)"/dev/mem", 2 /*O_RDWR*/, 0, 0, 0)) < 0) fail("open /dev/mem");
    if (sc(SYS_llseek, g_mem, 0, (long)pa, (long)&off, 0) < 0) fail("llseek /dev/mem");
}
static void mem_read(ul pa, void *b, ul n)
{
    if (!in_ram(pa, n)) fail("read outside System RAM");
    mem_at(pa);
    if (sc(SYS_read, g_mem, (long)b, n, 0, 0) != (long)n) fail("read /dev/mem");
}
/* the scans' read: a range the kernel keeps from /dev/mem is skipped, not fatal */
static int mem_try(ul pa, void *b, ul n)
{
    long off;
    if (!in_ram(pa, n)) return 0;
    if (g_mem < 0 && (g_mem = sc(SYS_open, (long)"/dev/mem", 2 /*O_RDWR*/, 0, 0, 0)) < 0) fail("open /dev/mem");
    if (sc(SYS_llseek, g_mem, 0, (long)pa, (long)&off, 0) < 0) return 0;
    return sc(SYS_read, g_mem, (long)b, n, 0, 0) == (long)n;
}

/* the bytes at pa against a row's signature: the camera's own, ours (bx lr + the rest), or neither */
static int sig_state(const u8 *b, const u8 *sig)
{
    if (bytes_eq(b, sig, SIG)) return ST_ORIG;
    if (b[0] == 0x70 && b[1] == 0x47 && bytes_eq(b + 2, sig + 2, SIG - 2)) return ST_PATCHED;
    return ST_UNKNOWN;
}
static int base_fits(const struct body *r, ul base)
{
    u8 b[SIG];
    for (int i = 0; i < 2; i++) {
        if (!mem_try(base + r->off[i], b, SIG) || sig_state(b, r->sig[i]) == ST_UNKNOWN) return 0;
    }
    return 1;
}
/* where av-cam is loaded: the row's base, else the cached one, else the one page-aligned base where both setters match */
static const char *g_base_how;
static ul find_base(const struct body *r)
{
    ul cached = 0, found = 0; int n = 0;
    if (r->base && base_fits(r, r->base)) { g_base_how = "row"; return r->base; }
    if (read_file(path(g_dir, "base"), &cached, sizeof cached) == (long)sizeof cached && cached && base_fits(r, cached)) {
        g_base_how = "cache"; return cached;
    }
    for (int k = 0; k < g_nram; k++) {
        u8 b[SIG];
        for (ul base = (g_ram[k][0] + PAGE - 1) & ~(PAGE - 1); base + r->off[0] + SIG - 1 <= g_ram[k][1]; base += PAGE) {
            if (!mem_try(base + r->off[0], b, SIG) || sig_state(b, r->sig[0]) == ST_UNKNOWN || !base_fits(r, base)) continue;
            found = base; if (++n > 1) break;
        }
    }
    if (n != 1) { g_base_how = n ? "ambiguous" : "none"; return 0; }
    write_file(path(g_dir, "base"), &found, sizeof found);
    g_base_how = "scan";
    return found;
}

/* ---- the setters of the row in use ---- */
static const struct body *g_row;
static ul g_set[2];
static int setter_state(int i)
{
    u8 b[SIG];
    mem_read(g_set[i], b, SIG);
    return sig_state(b, g_row->sig[i]);
}
static void setter_set(int i, int patch)
{
    u8 b[4] = { g_row->sig[i][0], g_row->sig[i][1], g_row->sig[i][2], g_row->sig[i][3] };
    if (patch) { b[0] = 0x70; b[1] = 0x47; }
    if (!in_ram(g_set[i], 4)) fail("setter outside System RAM");
    mem_at(g_set[i]);
    if (sc(SYS_write, g_mem, (long)b, 4, 0, 0) != 4) fail("write setter");
    if (setter_state(i) != (patch ? ST_PATCHED : ST_ORIG)) fail("setter readback");
}

/* ---- ISP registers (MMIO) through an mmap of /dev/mem: both tables sit in one page ---- */
static volatile ul *g_isp;
static volatile ul *isp(ul pa)
{
    ul page = g_row->gamma_pa & ~(PAGE - 1);
    if ((g_row->matrix_pa & ~(PAGE - 1)) != page || pa < page || pa >= page + PAGE) fail("ISP tables not in one page");
    if (!g_isp) {
        long fd = sc(SYS_open, (long)"/dev/mem", 2 | 0x101000 /*O_RDWR|O_SYNC*/, 0, 0, 0);
        if (fd < 0) fail("open /dev/mem");
        long m = sc6(SYS_mmap2, 0, PAGE, 3, 1 /*MAP_SHARED*/, fd, (long)(page >> 12));
        if ((ul)m > (ul)-4096) fail("mmap ISP");
        g_isp = (volatile ul *)m;
    }
    return g_isp + ((pa - page) >> 2);
}
static int s16(ul w) { w &= 0xFFFF; return w & 0x8000 ? (int)w - 65536 : (int)w; }

/*
 * Does the live gamma table look like one? Every word is 10-bit (all zero is allowed: before ISP init the table is
 * empty); a page that is not the ISP's fails. The matrix is not judged: the A6000 holds rows summing anywhere from
 * about 690 to 1950 (Q10) in normal use, and is not monotone in its gamma either, so neither says "not the ISP".
 * The setters' 32-byte signatures are what prove the row is this camera's.
 */
static int isp_sane(void)
{
    volatile ul *g = isp(g_row->gamma_pa);
    for (int i = 0; i < NG; i++) if (g[i] & ~0x3FFUL) return 0;
    return 1;
}

static void print_tables(void);
static const char *state_name(int s) { return s == ST_ORIG ? "original" : s == ST_PATCHED ? "patched" : "UNKNOWN"; }
/* the row for this camera, its setters found and checked; refuses otherwise. Nothing is written before this passes */
static void check_body(int st[2], int need_enabled)
{
    const struct body *same;
    read_body();
    if (!g_model[0]) fail("no body file");
    g_row = find_row(&same);
    if (!g_row) {
        if (same) { char b[40] = "REFUSE fw "; ul i = 10; for (const char *p = same->fw; *p && i < 39; ) b[i++] = *p++; b[i] = 0; refuse_for_good(b); }
        refuse_for_good("REFUSE body");
    }
    if (need_enabled && !g_row->enabled) refuse_for_good("REFUSE untested");
    read_ram();
    ul base = find_base(g_row);
    if (!base) refuse_for_good("REFUSE sig");
    for (int i = 0; i < 2; i++) {
        g_set[i] = base + g_row->off[i];
        st[i] = setter_state(i);
        out(i ? "SETTER matrix " : "SETTER gamma "); out(state_name(st[i])); out("\n");
    }
    if (st[0] == ST_UNKNOWN || st[1] == ST_UNKNOWN) refuse_for_good("REFUSE sig");
    if (!isp_sane()) { print_tables(); finish("REFUSE isp", 2); }   /* not for good: the ISP may still be starting */
}

/* ---- the profile the app wrote (IspProfiles.encode) ---- */
#define F_GAMMA 1
#define F_MATRIX 2
struct profile { u8 magic[4]; ul version, flags; ul gamma[NG]; long t[NM]; /* Q14 */ };
/* what apply remembers between runs: the stock matrix it built on, and the matrix it last wrote */
struct state { u8 magic[4]; long stock[NM], last[NM]; };

static long round_q14(long x) { return x >= 0 ? (x + 8192) >> 14 : -((-x + 8192) >> 14); }

/*
 * Is this a creative style's matrix? Every one seen sums to exactly 1024 per row (white stays white). The A6000 also
 * shows transient ones (1837 -17 -8 / -35 1341 -6 / -4 -2 1950, rows 1812/1300/1944) while a style settles; built on,
 * forcing the rows back to 1024 cuts the diagonal so far that a lower saturation swaps hues (orange <-> blue).
 */
#define STOCK_SLACK 16
/* the A6000's Standard creative style matrix: the stock when no clean one can be read (after Off the camera shows only
 * transients like the one above until its next style event). A guess on other bodies, still better than a transient */
static const long STD_MATRIX[NM] = { 1292, -180, -88, -44, 1340, -272, -24, -108, 1156 };
static int stock_ok(const long *s)
{
    for (int r = 0; r < 3; r++) {
        long sum = s[r * 3] + s[r * 3 + 1] + s[r * 3 + 2];
        if (sum < 1024 - STOCK_SLACK || sum > 1024 + STOCK_SLACK) return 0;
    }
    return 1;
}

static void read_profile(struct profile *p)
{
    if (read_file(path(g_dir, "profile.bin"), p, sizeof *p) != (long)sizeof *p || p->magic[0] != 'R' || p->magic[1] != 'L'
        || p->magic[2] != 'P' || p->magic[3] != 'P' || p->version != 1)
        fail("profile.bin missing or not version 1");
}

static void cmd_apply(const struct profile *pp, const int st[2])
{
    const struct profile p = *pp;
    long live[NM], m[NM];
    for (int i = 0; i < NM; i++) live[i] = s16(isp(g_row->matrix_pa)[i]);
    if (p.flags & F_MATRIX) {
        /* The stock matrix is the creative style's own. It is only read clean while both setters are the camera's and
         * the register does not still hold what we wrote last (Off leaves our values until the camera's next event),
         * and only when it looks like a style's (stock_ok): a transient is never built on. A saved stock that fails
         * (one an older helper took) is dropped. With no good stock at all, Standard's is used. */
        static struct state s;
        int have = read_file(path(g_dir, "state.bin"), &s, sizeof s) == (long)sizeof s && s.magic[0] == 'R' && s.magic[3] == 'T';
        int ours = have;
        for (int i = 0; i < NM && ours; i++) if (live[i] != s.last[i]) ours = 0;
        int kept = have && stock_ok(s.stock);
        if (kept && st[0] == ST_ORIG && st[1] == ST_ORIG) {
            out("STOCK kept\n");
        } else if (!ours && stock_ok(live)) {
            for (int i = 0; i < NM; i++) s.stock[i] = live[i];
            out(st[0] == ST_ORIG ? "STOCK captured\n" : "STOCK recaptured\n");
        } else if (kept) {
            out(ours ? "STOCK kept\n" : "STOCK kept, live not a style's\n");
        } else {
            print_tables();
            for (int i = 0; i < NM; i++) s.stock[i] = STD_MATRIX[i];
            out("STOCK standard, live not a style's\n");
        }
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
        if (write_file(path(g_dir, "state.bin"), &s, sizeof s)) fail("write state.bin");
    }

    /* freeze first, then write: the camera's writer cannot race us afterwards */
    if ((p.flags & F_GAMMA) && st[0] != ST_PATCHED) setter_set(0, 1);
    if ((p.flags & F_MATRIX) && st[1] != ST_PATCHED) setter_set(1, 1);
    volatile ul *g = isp(g_row->gamma_pa), *mx = isp(g_row->matrix_pa);
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
    int st[2]; check_body(st, 0);
    for (int i = 0; i < 2; i++) if (st[i] == ST_PATCHED) setter_set(i, 0);
    finish("OFF", 0);
}

static void cmd_reapply(const struct profile *p, const int st[2])
{
    if (p->flags & F_GAMMA) {
        volatile ul *g = isp(g_row->gamma_pa);
        int match = 1;
        for (int i = 0; i < NG && match; i++) if (g[i] != (p->gamma[i] & 0x3FF)) match = 0;
        if (match) {
            out("GAMMA "); for (int i = 0; i < 6; i++) { outd(g[i]); out(" "); } out("\n");
            finish("MATCH", 0);
        }
    }
    cmd_apply(p, st);
}

static void print_tables(void)
{
    volatile ul *g = isp(g_row->gamma_pa), *m = isp(g_row->matrix_pa);
    int mono = 1; ul max = 0;
    for (int i = 0; i < NG; i++) { if (i && g[i] < g[i - 1]) mono = 0; if (g[i] > max) max = g[i]; }
    out("GAMMA "); for (int i = 0; i < 6; i++) { outd(g[i]); out(" "); } out("max="); outd(max); out(" mono="); outd(mono); out("\n");
    out("MATRIX "); for (int i = 0; i < NM; i++) { outd(s16(m[i])); out(" "); }
    out("sums"); for (int r = 0; r < 3; r++) { out(" "); outd(s16(m[r * 3]) + s16(m[r * 3 + 1]) + s16(m[r * 3 + 2])); } out("\n");
}

static void cmd_status(void)
{
    int st[2]; check_body(st, 0);
    print_tables();
    finish(st[0] == ST_PATCHED || st[1] == ST_PATCHED ? "STATUS on" : "STATUS off", 0);
}

/* a body with no row: where the gamma leaf sits in RAM, and whether the matrix setter follows it as on most av-cams
 * scanned. Read only; the two 32-byte runs it prints are what a new row of bodies.h needs */
static void probe_search(void)
{
    static u8 buf[0x10000 + 16];
    int hits = 0;
    for (int k = 0; k < g_nram && hits < 4; k++) {
        for (ul pa = g_ram[k][0]; pa + sizeof buf - 1 <= g_ram[k][1] && hits < 4; pa += 0x10000) {
            if (!mem_try(pa, buf, sizeof buf)) continue;
            for (ul i = 0; i < 0x10000 && hits < 4; i += 2) {
                if (!bytes_eq(buf + i, LEAF, sizeof LEAF)) continue;
                ul at = pa + i; u8 g[SIG], m[SIG];
                hits++;
                out("LEAF "); outx(at, 8);
                if (!in_ram(at + MSET_AFTER_LEAF, SIG)) { out(" matrix out of RAM\n"); continue; }
                if (!mem_try(at, g, SIG) || !mem_try(at + MSET_AFTER_LEAF, m, SIG)) { out(" unreadable\n"); continue; }
                out(bytes_eq(m, MSET, sizeof MSET) ? " matrix+1cc yes\n" : " matrix+1cc no\n");
                out("  sig gamma  "); outhex(g, SIG); out("\n");
                out("  sig matrix "); outhex(m, SIG); out("\n");
            }
        }
    }
    out("LEAVES "); outd(hits); out("\n");
}

/* read only: never writes the setters or the ISP, never drops stop */
static void cmd_probe(void)
{
    const struct body *same;
    int ok = 0;
    read_body();
    out("BODY "); out(g_model[0] ? g_model : "?"); out(" "); out(g_fw[0] ? g_fw : "?"); out("\n");
    read_ram();
    out("RAM"); for (int i = 0; i < g_nram; i++) { out(" "); outx(g_ram[i][0], 8); out("-"); outx(g_ram[i][1], 8); } out("\n");
    g_row = find_row(&same);
    if (!g_row) {
        out(same ? "ROW fw-differs want " : "ROW none\n"); if (same) { out(same->fw); out("\n"); }
        probe_search();
        finish("PROBE fail", 0);
    }
    out(g_row->enabled ? "ROW enabled\n" : "ROW disabled\n");
    ul base = find_base(g_row);
    out("BASE "); outx(base, 8); out(" "); out(g_base_how); out("\n");
    if (base) {
        int st[2];
        for (int i = 0; i < 2; i++) {
            u8 b[SIG];
            g_set[i] = base + g_row->off[i];
            mem_read(g_set[i], b, SIG);
            st[i] = sig_state(b, g_row->sig[i]);
            out(i ? "SETTER matrix " : "SETTER gamma "); out(state_name(st[i])); out(" "); outx(g_set[i], 8); out(" "); outhex(b, 16); out("\n");
        }
        if (st[0] != ST_UNKNOWN && st[1] != ST_UNKNOWN) {
            print_tables();
            ok = isp_sane();
            out(ok ? "ISP sane\n" : "ISP not sane\n");
        }
    }
    finish(ok ? "PROBE ok" : "PROBE fail", 0);
}

void cmain(int argc, char **argv)
{
    if (argc < 3) { out("usage: rlisp ping|apply|reapply|off|status|probe <dir>\n"); sc(SYS_exit, 64, 0, 0, 0, 0); }
    const char *op = argv[1];
    g_dir = argv[2];
    long fd = sc(SYS_open, (long)path(g_dir, "log"), 1 | 0100 | 01000, 0666, 0, 0);
    if (fd >= 0) { g_log = fd; sc(SYS_fchmod, fd, 0666, 0, 0, 0); }
    out("RLISP 2 "); out(op); out("\n");
    if (streq(op, "ping")) { out("RESULT PONG uid="); outd(sc(SYS_getuid32, 0, 0, 0, 0, 0)); out("\n"); sc(SYS_exit, 0, 0, 0, 0, 0); }
    else if (streq(op, "apply") || streq(op, "reapply")) {
        static struct profile p;
        int st[2];
        read_profile(&p);
        check_body(st, 1);
        if (streq(op, "apply")) cmd_apply(&p, st); else cmd_reapply(&p, st);
    }
    else if (streq(op, "off")) cmd_off();
    else if (streq(op, "status")) cmd_status();
    else if (streq(op, "probe")) cmd_probe();
    fail("unknown command");
}

__attribute__((naked, section(".text.start"))) void _start(void)
{
    asm volatile("ldr r0, [sp]\n add r1, sp, #4\n bl cmain\n b .");
}
