package com.voxivoid.recipelab.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** The network name and password read off a hotspot configuration the app cannot name. */
class HotspotConfigTest {
    static class Direct {
        public String getSsid() { return "\"DIRECT-ab-ILCE-6000\""; }
        public String getPreSharedKey() { return "a1b2c3d4"; }
    }

    static class Unset {
        public String getSsid() { return null; }
        public Object getPreSharedKey() { return new StringBuilder("k3y"); }
    }

    static class Broken {
        public String getSsid() { throw new IllegalStateException("not ready"); }
    }

    @Test void theGettersAreRead() {
        assertEquals("\"DIRECT-ab-ILCE-6000\"", HotspotConfig.ssid(new Direct()), "the quotes are TransferState's to take off");
        assertEquals("a1b2c3d4", HotspotConfig.password(new Direct()));
    }

    @Test void aValueIsReadAsText() { assertEquals("k3y", HotspotConfig.password(new Unset())); }

    @Test void whatTheConfigurationDoesNotSayIsNull() {
        assertNull(HotspotConfig.ssid(null));
        assertNull(HotspotConfig.password(null));
        assertNull(HotspotConfig.ssid(new Unset()), "unset");
        assertNull(HotspotConfig.ssid(new Broken()), "the getter throws");
        assertNull(HotspotConfig.password(new Broken()), "no such getter");
        assertNull(HotspotConfig.ssid("a string has no getSsid"));
    }
}
