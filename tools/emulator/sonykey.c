/*
 * sonykey: presses camera keys in the Android emulator, by their Sony scan codes.
 *
 * The emulator's own keyboard only declares the usual keys, so the kernel drops MENU (514), Fn (520), the wheel
 * (522 / 523), the shutter (516 / 518) and the rest before Android sees them. This makes a throwaway uinput keyboard
 * that declares every code, presses the ones it is given in order and goes away again. Recipe Lab then gets the
 * same scan codes a camera sends; Android's key code for them is UNKNOWN, which the app never reads.
 *
 *   sonykey CODE[:HOLD_MS] ...        e.g.  sonykey 514:800    (hold MENU)     sonykey 522 522 522   (three clicks)
 *
 * Needs root (adb root) for /dev/uinput. Built static by tools/emulator.sh, so it needs nothing from Android's libc.
 */
#include <fcntl.h>
#include <linux/uinput.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <time.h>
#include <unistd.h>

static void sleep_ms(long ms) {
    struct timespec t = { ms / 1000, (ms % 1000) * 1000000L };
    nanosleep(&t, NULL);
}

static void emit(int fd, int type, int code, int value) {
    struct input_event e;
    memset(&e, 0, sizeof e);
    e.type = type; e.code = code; e.value = value;
    if (write(fd, &e, sizeof e) != sizeof e) perror("write");
}

int main(int argc, char **argv) {
    if (argc < 2) { fprintf(stderr, "usage: sonykey CODE[:HOLD_MS] ...\n"); return 2; }
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) { perror("/dev/uinput (run adb root first)"); return 1; }
    ioctl(fd, UI_SET_EVBIT, EV_KEY);
    /* every key code, but none of the buttons in between (BTN_MISC up to KEY_OK): with BTN_TOUCH declared Android takes
       the device for a stylus, and with the gamepad buttons for a gamepad, and neither passes keys on */
    for (int k = 1; k < KEY_MAX; k++) if (k < BTN_MISC || k >= KEY_OK) ioctl(fd, UI_SET_KEYBIT, k);
    struct uinput_user_dev dev;
    memset(&dev, 0, sizeof dev);
    strncpy(dev.name, "sonykey", UINPUT_MAX_NAME_SIZE - 1);
    dev.id.bustype = BUS_VIRTUAL; dev.id.vendor = 0x054c; dev.id.product = 1;   /* 054c: Sony's USB vendor id */
    if (write(fd, &dev, sizeof dev) != sizeof dev || ioctl(fd, UI_DEV_CREATE) < 0) { perror("UI_DEV_CREATE"); return 1; }
    sleep_ms(500);                                         /* Android has to find the new keyboard before it listens */
    for (int i = 1; i < argc; i++) {
        char *colon = strchr(argv[i], ':');
        int code = atoi(argv[i]);
        long hold = colon ? atol(colon + 1) : 60;
        if (code <= 0 || code >= KEY_MAX) { fprintf(stderr, "sonykey: bad code %s\n", argv[i]); continue; }
        emit(fd, EV_KEY, code, 1); emit(fd, EV_SYN, SYN_REPORT, 0);
        sleep_ms(hold);
        emit(fd, EV_KEY, code, 0); emit(fd, EV_SYN, SYN_REPORT, 0);
        sleep_ms(80);                                      /* a gap, so two presses of one key stay two */
    }
    sleep_ms(200);
    ioctl(fd, UI_DEV_DESTROY);
    close(fd);
    return 0;
}
