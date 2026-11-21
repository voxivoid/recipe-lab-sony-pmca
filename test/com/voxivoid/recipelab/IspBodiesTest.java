package com.voxivoid.recipelab;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Which camera runs a picture profile, and the helper's table (jni/rlisp/bodies.h) agreeing with the app's. */
class IspBodiesTest {

    @Test void theA6000On321IsSupported() {
        assertEquals(IspBodies.SUPPORTED, IspBodies.status("ILCE-6000", "3.21"));
        assertEquals(IspBodies.SUPPORTED, IspBodies.status(" ILCE-6000 ", "3.21\n"));
        assertTrue(IspBodies.bootAllowed("ILCE-6000", "3.21"));
        assertNull(IspBodies.refusal("ILCE-6000", "3.21"));
    }

    @Test void aKnownModelOnOtherFirmwareIsToldWhichToInstall() {
        assertEquals(IspBodies.OLD_FW, IspBodies.status("ILCE-6000", "3.20"));
        assertArrayEquals(new String[] { "isp_update_fw", "3.21" }, IspBodies.refusal("ILCE-6000", "1.00"));
        assertFalse(IspBodies.bootAllowed("ILCE-6000", "3.20"));
        assertEquals(IspBodies.OLD_FW, IspBodies.status("ILCE-6000", null), "a firmware the camera does not say is not assumed");
    }

    @Test void anUnknownBodyIsNotYetSupported() {
        assertEquals(IspBodies.NO, IspBodies.status("NEX-6", "1.03"));
        assertEquals(IspBodies.NO, IspBodies.status(null, "3.21"), "a body that does not say what it is never runs it");
        assertEquals(IspBodies.NO, IspBodies.status("", ""));
        assertArrayEquals(new String[] { "isp_not_yet", null }, IspBodies.refusal("NEX-6", "1.03"));
        assertFalse(IspBodies.bootAllowed(null, null));
    }

    @Test void aDisabledRowIsUntested() {
        for (Object[] r : IspBodies.ROWS) {
            if ((Boolean) r[2]) continue;
            assertEquals(IspBodies.UNTESTED, IspBodies.status((String) r[0], (String) r[1]));
            assertFalse(IspBodies.bootAllowed((String) r[0], (String) r[1]));
            assertArrayEquals(new String[] { "isp_untested", null }, IspBodies.refusal((String) r[0], (String) r[1]));
        }
    }

    @Test void theBodyFileIsModelThenFirmware() {
        assertEquals("ILCE-6000\n3.21\n", IspBodies.bodyFile(" ILCE-6000", "3.21 "));
        assertEquals("?\n?\n", IspBodies.bodyFile(null, ""));
    }

    @Test void theHelpersRefusalsBecomeMessages() {
        assertArrayEquals(new String[] { "isp_update_fw", "3.21" }, IspBodies.refusal("REFUSE fw 3.21"));
        assertArrayEquals(new String[] { "isp_untested", null }, IspBodies.refusal("REFUSE untested"));
        assertArrayEquals(new String[] { "isp_not_ready", null }, IspBodies.refusal("REFUSE isp"));
        assertArrayEquals(new String[] { "isp_not_yet", null }, IspBodies.refusal("REFUSE body"));
        assertArrayEquals(new String[] { "isp_not_yet", null }, IspBodies.refusal("REFUSE sig"));
        assertNull(IspBodies.refusal("OK"));
        assertNull(IspBodies.refusal((String) null));
    }

    @Test void everyMessageIsInEnglish() {
        for (String k : new String[] { "isp_update_fw", "isp_untested", "isp_not_ready", "isp_not_yet", "report_probing" })
            assertTrue(hasKey(k), k);
    }

    private static boolean hasKey(String k) {
        for (String[] row : TextEn.TEXT) if (row[0].equals(k)) return true;
        return false;
    }

    /** the rows of bodies.h, as model, fw, enabled */
    static List<Object[]> helperRows() throws IOException {
        String h = new String(Files.readAllBytes(Paths.get("jni/rlisp/bodies.h")), "UTF-8");
        String table = h.substring(h.indexOf("BODIES[] = {"), h.indexOf("};", h.indexOf("BODIES[] = {")));
        Matcher m = Pattern.compile("\\{ \"([^\"]+)\", \"([^\"]+)\",.*?0x[0-9A-Fa-f]+UL, ([01]) \\},", Pattern.DOTALL).matcher(table);
        List<Object[]> rows = new ArrayList<>();
        while (m.find()) rows.add(new Object[] { m.group(1), m.group(2), m.group(3).equals("1") });
        return rows;
    }

    @Test void theAppAndTheHelperKnowTheSameBodies() throws IOException {
        List<Object[]> helper = helperRows();
        assertFalse(helper.isEmpty(), "bodies.h parsed no row");
        assertEquals(helper.size(), IspBodies.ROWS.length, "bodies.h and IspBodies.ROWS differ in length");
        for (int i = 0; i < helper.size(); i++)
            assertArrayEquals(helper.get(i), IspBodies.ROWS[i], "row " + i + " of bodies.h and IspBodies.ROWS differ");
    }
}
