#include <jni.h>
#include <dlfcn.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "canvas.h"

/*
 * Writes a picture into an image the camera holds in its own memory (an OptimizedImage, from Sony's scaler), so the
 * camera can save it as one of its own photos (Playback.java): a recipe card, drawn by the app. Rows are packed into
 * the canvas (canvas.c) and go in 64 KB at a time through the camera's own buffer call: DeviceMemoryDiadem::writeBuffer
 * in libcameraseq.so, the library behind com.sony.scalar.hardware.DeviceMemory, whose mNativeMemory field holds the
 * object. From Recipe Lab Overlay (jni/playback.c there), which proved it on the A6000; the rows come from Java here
 * (NativePlayback) instead of a JPEG, so no libjpeg.
 */

enum { SINK_RGB };

#define BLOCK (64 * 1024)
#define MEMORY_CLASS "com/sony/scalar/hardware/DeviceMemory"

typedef int (*type_fn)(void *self);
typedef int (*property_fn)(void *self, int key, int *value);
typedef int (*write_fn)(void *self, const void *data, unsigned count, unsigned offset);

struct canvas_sink {
    void *lib, *self;
    write_fn put;
    int g[CANVAS_FIELDS], size, stride, rows, first, n, done, failed;
    unsigned char *block;
    char msg[128];
};

static void sink_fail(struct canvas_sink *s, const char *msg)
{
    if (s->failed) return;
    s->failed = 1;
    snprintf(s->msg, sizeof s->msg, "%s", msg);
}

static void flush(struct canvas_sink *s)
{
    long long at;
    unsigned count = (unsigned) (s->n * s->stride);
    if (!s->n || s->failed) { s->n = 0; return; }
    at = canvas_row_at(s->g, s->first);
    if (at < 0 || at + count > (unsigned long long) s->size || s->put(s->self, s->block, count, (unsigned) at) != (int) count)
        sink_fail(s, "the camera took only part of the picture");
    s->n = 0;
}

static void sink_free(struct canvas_sink *s)
{
    if (!s) return;
    free(s->block);
    if (s->lib) dlclose(s->lib);
    free(s);
}

static struct canvas_sink *sink_open(JNIEnv *env, jobject image, jintArray geometry, int w, int h, char *err, size_t err_len)
{
    struct canvas_sink *s;
    jint jg[CANVAS_FIELDS];
    jclass memory;
    jfieldID handle;
    jmethodID valid;
    type_fn type;
    property_fn property;
    int i;

    err[0] = 0;
    if (sizeof(void *) != 4) { snprintf(err, err_len, "not a 32-bit camera"); return NULL; }
    if (!image || !geometry || (*env)->GetArrayLength(env, geometry) != CANVAS_FIELDS) { snprintf(err, err_len, "bad arguments"); return NULL; }
    s = (struct canvas_sink *) calloc(1, sizeof *s);
    if (!s) { snprintf(err, err_len, "out of memory"); return NULL; }
    (*env)->GetIntArrayRegion(env, geometry, 0, CANVAS_FIELDS, jg);
    for (i = 0; i < CANVAS_FIELDS; i++) s->g[i] = (int) jg[i];
    if (!canvas_fits(s->g, w, h)) { snprintf(err, err_len, "the picture does not fit the camera's image"); goto fail; }

    memory = (*env)->FindClass(env, MEMORY_CLASS);
    if (!memory) { (*env)->ExceptionClear(env); snprintf(err, err_len, "no DeviceMemory"); goto fail; }
    if (!(*env)->IsInstanceOf(env, image, memory)) { snprintf(err, err_len, "not camera image memory"); goto fail; }
    valid = (*env)->GetMethodID(env, memory, "isValid", "()Z");
    handle = (*env)->GetFieldID(env, memory, "mNativeMemory", "I");
    if (!valid || !handle) { (*env)->ExceptionClear(env); snprintf(err, err_len, "DeviceMemory is not as expected"); goto fail; }
    if (!(*env)->CallBooleanMethod(env, image, valid)) { snprintf(err, err_len, "the image was released"); goto fail; }
    s->self = (void *) (uintptr_t) (uint32_t) (*env)->GetIntField(env, image, handle);
    if (!s->self) { snprintf(err, err_len, "the image has no memory"); goto fail; }

    s->lib = dlopen("libcameraseq.so", RTLD_NOW | RTLD_LOCAL);
    if (!s->lib) { snprintf(err, err_len, "no libcameraseq.so"); goto fail; }
    *(void **) &type = dlsym(s->lib, "_ZN6scalar18DeviceMemoryDiadem7getTypeEv");   /* POSIX: no cast from void * to a function pointer */
    *(void **) &property = dlsym(s->lib, "_ZN6scalar18DeviceMemoryDiadem17getPropertyDiademEiPi");
    *(void **) &s->put = dlsym(s->lib, "_ZN6scalar18DeviceMemoryDiadem11writeBufferEPKvjj");
    /* the image memory (type 2), and as large as the DSP said (property 2): anything else is not what this writes to */
    if (!type || !property || !s->put || type(s->self) != 2 || !property(s->self, 2, &s->size) || s->size != s->g[CANVAS_SIZE]) {
        snprintf(err, err_len, "the camera's image memory is not as expected");
        goto fail;
    }
    s->stride = 2 * s->g[CANVAS_STRIDE_W];
    s->rows = BLOCK / s->stride > 0 ? BLOCK / s->stride : 1;
    s->block = (unsigned char *) malloc((size_t) s->stride * s->rows);
    if (!s->block) { snprintf(err, err_len, "out of memory"); goto fail; }
    return s;
fail:
    sink_free(s);
    return NULL;
}

static void sink_row(struct canvas_sink *s, int y, const unsigned char *row)
{
    unsigned char *out;
    if (s->failed) return;
    if (y != s->done) { sink_fail(s, "rows out of order"); return; }
    if (!s->n) s->first = y;
    out = s->block + (size_t) s->n * s->stride;
    canvas_row_rgb(s->g, row, out);
    s->n++; s->done++;
    if (s->n == s->rows) flush(s);
}

static int sink_close(struct canvas_sink *s, int rows_expected, char *err, size_t err_len)
{
    int failed;
    if (!s) { snprintf(err, err_len, "no image"); return 1; }
    flush(s);
    if (!s->failed && s->done != rows_expected) sink_fail(s, "the picture stopped short");
    failed = s->failed;
    if (failed) snprintf(err, err_len, "%s", s->msg);
    sink_free(s);
    return failed;
}


static void throw_runtime(JNIEnv *env, const char *msg)
{
    jclass c;
    if ((*env)->ExceptionCheck(env)) return;
    c = (*env)->FindClass(env, "java/lang/RuntimeException");
    if (c) (*env)->ThrowNew(env, c, msg);
}

/* NativePlayback.open: the image, ready for a w x h picture's rows; a handle for rows and close, or throws why not */
JNIEXPORT jint JNICALL Java_com_voxivoid_recipelab_NativePlayback_open(JNIEnv *env, jclass clazz, jobject image,
        jintArray geometry, jint w, jint h)
{
    char err[160];
    struct canvas_sink *s = sink_open(env, image, geometry, (int) w, (int) h, err, sizeof err);
    (void) clazz;
    if (!s) { throw_runtime(env, err); return 0; }
    return (jint) (uintptr_t) s;
}

/* NativePlayback.rows: count rows of ARGB pixels (as Bitmap.getPixels gives them), the first being row y */
JNIEXPORT void JNICALL Java_com_voxivoid_recipelab_NativePlayback_rows(JNIEnv *env, jclass clazz, jint handle,
        jintArray argb, jint width, jint y, jint count)
{
    struct canvas_sink *s = (struct canvas_sink *) (uintptr_t) (uint32_t) handle;
    jint *px;
    unsigned char *rgb;
    int r, i;
    (void) clazz;
    if (!s || !argb || width <= 0 || count <= 0 || (*env)->GetArrayLength(env, argb) < width * count) { throw_runtime(env, "bad rows"); return; }
    rgb = (unsigned char *) malloc((size_t) width * 3);
    if (!rgb) { throw_runtime(env, "out of memory"); return; }
    px = (*env)->GetIntArrayElements(env, argb, NULL);
    if (!px) { free(rgb); return; }
    for (r = 0; r < count; r++) {
        const jint *row = px + (size_t) r * width;
        for (i = 0; i < width; i++) {
            unsigned int p = (unsigned int) row[i];
            rgb[3 * i] = (unsigned char) (p >> 16);
            rgb[3 * i + 1] = (unsigned char) (p >> 8);
            rgb[3 * i + 2] = (unsigned char) p;
        }
        sink_row(s, (int) y + r, rgb);
    }
    (*env)->ReleaseIntArrayElements(env, argb, px, JNI_ABORT);
    free(rgb);
}

/* NativePlayback.close: the last rows in; null when every row went in, else why not. The handle is gone after */
JNIEXPORT jstring JNICALL Java_com_voxivoid_recipelab_NativePlayback_close(JNIEnv *env, jclass clazz, jint handle, jint rows)
{
    char err[160];
    (void) clazz;
    if (sink_close((struct canvas_sink *) (uintptr_t) (uint32_t) handle, (int) rows, err, sizeof err)) return (*env)->NewStringUTF(env, err);
    return NULL;
}
