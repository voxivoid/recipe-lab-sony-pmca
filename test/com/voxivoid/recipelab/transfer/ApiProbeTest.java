package com.voxivoid.recipelab.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** ApiProbe: what the card log says about a Wi-Fi service. */
class ApiProbeTest {
    static class Base {
        public void setDirectEnabled(boolean on) {}
    }

    static class Manager extends Base {
        public boolean startGo(int id) { return true; }
        public void createGroup(Object channel, Runnable listener, int id) {}
        void hidden() {}
    }

    @Test void absentService() {
        assertEquals("wifi-direct: none", ApiProbe.describe("wifi-direct", null));
    }

    @Test void classSuperAndPublicMethods() {
        String name = Manager.class.getName();
        assertEquals("wifip2p: " + name + " extends " + Base.class.getName() + "\n"
                + "createGroup(Object,Runnable,int) setDirectEnabled(boolean) startGo(int)",
            ApiProbe.describe("wifip2p", new Manager()));
    }

    @Test void plainObjectNamesNoSuperclass() {
        assertEquals("x: " + Base.class.getName() + "\nsetDirectEnabled(boolean)", ApiProbe.describe("x", new Base()));
    }

    @Test void wrapsBeforeTheWidth() {
        assertEquals("aaa bbb\nccc", ApiProbe.wrap(Arrays.asList("aaa", "bbb", "ccc"), 7));
        assertEquals("aaa\nbbb", ApiProbe.wrap(Arrays.asList("aaa", "bbb"), 6));
        assertEquals("toolongitem\nx", ApiProbe.wrap(Arrays.asList("toolongitem", "x"), 5));
        assertEquals("", ApiProbe.wrap(Arrays.<String>asList(), 5));
    }
}
