#include "canvas.h"

#define CANVAS_MAX 16384

int canvas_fits(const int *g, int w, int h)
{
    long long need;
    if (!g || w < 2 || h < 1 || (w & 1) || w > CANVAS_MAX || h > CANVAS_MAX) return 0;
    if (g[CANVAS_W] != w || g[CANVAS_H] != h) return 0;
    if (g[CANVAS_STRIDE_W] > CANVAS_MAX || g[CANVAS_STRIDE_H] > CANVAS_MAX || (g[CANVAS_STRIDE_W] & 1)) return 0;
    if (g[CANVAS_X] < 0 || g[CANVAS_Y] < 0 || (g[CANVAS_X] & 1)) return 0;     /* a pair never straddles the picture's edge */
    if ((long long) g[CANVAS_X] + w > g[CANVAS_STRIDE_W] || (long long) g[CANVAS_Y] + h > g[CANVAS_STRIDE_H]) return 0;
    need = (long long) g[CANVAS_OFFSET] + 2LL * g[CANVAS_STRIDE_W] * g[CANVAS_STRIDE_H];
    return g[CANVAS_OFFSET] >= 0 && g[CANVAS_SIZE] > 0 && need <= g[CANVAS_SIZE];
}

void canvas_row(const int *g, const unsigned char *src, int components, unsigned char *out)
{
    int i, stride = g[CANVAS_STRIDE_W], w = g[CANVAS_W];
    unsigned char *p;
    for (i = 0; i < stride; i += 2) { out[2 * i] = 128; out[2 * i + 1] = 0; out[2 * i + 2] = 128; out[2 * i + 3] = 0; }
    p = out + 2 * g[CANVAS_X];
    for (i = 0; i < w; i += 2, p += 4) {
        if (components == 1) {
            p[0] = 128; p[1] = src[i]; p[2] = 128; p[3] = src[i + 1];
        } else {
            const unsigned char *a = src + 3 * i, *b = a + 3;
            p[0] = (unsigned char) ((a[1] + b[1] + 1) >> 1);
            p[1] = a[0];
            p[2] = (unsigned char) ((a[2] + b[2] + 1) >> 1);
            p[3] = b[0];
        }
    }
}

/* libjpeg's RGB to YCbCr (jccolor.c): 16-bit fixed point, rounded */
#define FIX(x) ((int) ((x) * 65536.0 + 0.5))
static unsigned char luma(const unsigned char *p) { return (unsigned char) ((FIX(0.29900) * p[0] + FIX(0.58700) * p[1] + FIX(0.11400) * p[2] + 32768) >> 16); }
static int cb(const unsigned char *p) { return (-FIX(0.16874) * p[0] - FIX(0.33126) * p[1] + 32768 * p[2] + (128 << 16) + 32767) >> 16; }
static int cr(const unsigned char *p) { return (32768 * p[0] - FIX(0.41869) * p[1] - FIX(0.08131) * p[2] + (128 << 16) + 32767) >> 16; }

void canvas_row_rgb(const int *g, const unsigned char *rgb, unsigned char *out)
{
    int i, stride = g[CANVAS_STRIDE_W], w = g[CANVAS_W];
    unsigned char *p;
    for (i = 0; i < stride; i += 2) { out[2 * i] = 128; out[2 * i + 1] = 0; out[2 * i + 2] = 128; out[2 * i + 3] = 0; }
    p = out + 2 * g[CANVAS_X];
    for (i = 0; i < w; i += 2, p += 4) {
        const unsigned char *a = rgb + 3 * i, *b = a + 3;
        p[0] = (unsigned char) ((cb(a) + cb(b) + 1) >> 1);
        p[1] = luma(a);
        p[2] = (unsigned char) ((cr(a) + cr(b) + 1) >> 1);
        p[3] = luma(b);
    }
}

long long canvas_row_at(const int *g, int y)
{
    return (long long) g[CANVAS_OFFSET] + 2LL * g[CANVAS_STRIDE_W] * ((long long) g[CANVAS_Y] + y);
}
