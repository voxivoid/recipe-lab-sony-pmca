#ifndef RECIPELAB_CANVAS_H
#define RECIPELAB_CANVAS_H

/*
 * The camera's image memory, as a merged picture is written into it for the camera to save (Playback.java,
 * jni/playback.c): UYVY, two bytes a pixel — U Y0 V Y1 for each pair, full range as a JPEG decodes — in a canvas that
 * may be wider and taller than the picture, which sits at an offset in it.
 *
 * The geometry is what the camera's DSP reports for the image, in this order (CANVAS_*): the picture's width and
 * height, the canvas's width and height, the byte where the canvas starts, the picture's x and y in the canvas, and
 * the memory's size in bytes.
 *
 * No JNI in here, so tools/test-native.sh can build and test it on the build machine.
 */
enum { CANVAS_W, CANVAS_H, CANVAS_STRIDE_W, CANVAS_STRIDE_H, CANVAS_OFFSET, CANVAS_X, CANVAS_Y, CANVAS_SIZE, CANVAS_FIELDS };

/* whether a w x h picture fits that geometry: the sizes agree, it is inside the canvas, the canvas inside the memory */
int canvas_fits(const int *g, int w, int h);

/*
 * One canvas row, canvas-width x 2 bytes, from one decoded row of the picture: w pixels of YCbCr (components 3) or
 * grey (1). Each pair's chroma is the mean of its two pixels; the canvas around the picture is black.
 */
void canvas_row(const int *g, const unsigned char *src, int components, unsigned char *out);

/*
 * The same from a row of RGB, as the merge has it before encoding: converted to YCbCr as libjpeg converts (JFIF, full
 * range), so the camera's copy gets the colours the merged file decodes to, without the JPEG's loss.
 */
void canvas_row_rgb(const int *g, const unsigned char *rgb, unsigned char *out);

/* the byte in the memory where the picture's row y starts its canvas row */
long long canvas_row_at(const int *g, int y);

#endif
