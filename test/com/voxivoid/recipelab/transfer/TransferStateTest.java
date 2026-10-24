package com.voxivoid.recipelab.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The Transfer to phone page's state: the camera hotspot starting, up or failed, and the start timeout. */
class TransferStateTest {
    private static TransferState started() {
        TransferState s = new TransferState();
        s.start(1000);
        return s;
    }

    @Test void itStartsWithNothingKnown() {
        TransferState s = started();
        assertEquals(TransferState.STARTING, s.status());
        assertFalse(s.ready());
        assertFalse(s.failed());
        assertNull(s.ssid());
        assertNull(s.password());
    }

    @Test void upGivesTheNetworkAndPasswordWithoutQuotes() {
        TransferState s = started();
        s.up("\"DIRECT-ab:ILCE-6000\"", "\"s3cret\"");
        assertEquals(TransferState.READY, s.status());
        assertTrue(s.ready());
        assertEquals("DIRECT-ab:ILCE-6000", s.ssid());
        assertEquals("s3cret", s.password());
        assertEquals("192.168.122.1:8080", TransferState.address(), "the camera is the access point: a fixed address");
    }

    @Test void anUnknownNetworkOrPasswordStaysUnknown() {
        TransferState s = started();
        s.up(null, "\"\"");
        assertTrue(s.ready());
        assertNull(s.ssid());
        assertNull(s.password());
    }

    @Test void aFailureSaysWhyAndForgetsTheNetwork() {
        TransferState s = started();
        s.up("Cam", "pw");
        s.failed(TransferState.FAILED, "3");
        assertEquals(TransferState.FAILED, s.status());
        assertTrue(s.failed());
        assertEquals("3", s.detail());
        assertNull(s.ssid());
        assertNull(s.password());
    }

    @Test void startingTooLongTimesOutButAnUpHotspotDoesNot() {
        TransferState slow = started();
        slow.tick(1000 + TransferState.START_TIMEOUT_MS);
        assertEquals(TransferState.STARTING, slow.status(), "not yet");
        slow.tick(1000 + TransferState.START_TIMEOUT_MS + 1);
        assertEquals(TransferState.TIMEOUT, slow.status());
        assertTrue(slow.failed());
        TransferState up = started();
        up.up("Cam", "pw");
        up.tick(1000 + TransferState.START_TIMEOUT_MS + 1);
        assertEquals(TransferState.READY, up.status());
        TransferState failed = started();
        failed.failed(TransferState.UNAVAILABLE, null);
        failed.tick(1000 + TransferState.START_TIMEOUT_MS + 1);
        assertEquals(TransferState.UNAVAILABLE, failed.status(), "a failure keeps its own reason");
    }

    @Test void startingAgainForgetsTheLastRun() {
        TransferState s = started();
        s.up("Cam", "pw");
        s.note(TransferState.NOTE_PHONE_JOINED);
        s.start(5000);
        assertEquals(TransferState.STARTING, s.status());
        assertNull(s.ssid());
        assertNull(s.password());
        assertEquals(TransferState.NOTE_NONE, s.note());
    }

    @Test void aServerProblemStaysAcrossARestart() {
        TransferState s = started();
        s.serverProblem(TransferState.SERVER_NO_CARD, "removed");
        s.start(2000);
        assertEquals(TransferState.SERVER_NO_CARD, s.server());
        assertEquals("removed", s.serverDetail());
    }

    @Test void ssidQuotesComeOff() {
        assertEquals("Cam", TransferState.unquote("\"Cam\""));
        assertEquals("Cam", TransferState.unquote("Cam"));
        assertNull(TransferState.unquote("\"\""));
        assertNull(TransferState.unquote(null));
    }
}
