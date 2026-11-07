package com.hunterbuddy.pet;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.sound.SoundEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class PetSfxPackTest {
    private static ResourceManager real() throws Exception {
        String json;
        try (InputStream stream = PetSfxPackTest.class.getResourceAsStream("/pet-sfx.json")) {
            json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        return (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(), new Class<?>[]{ResourceManager.class},
            (proxy, method, args) -> new Resource(null, () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void theRealSfxFileParsesIntoEveryActionAndLogsWhatItLoaded() throws Exception {
        List<String> lines = new ArrayList<>();
        PetSfxBank bank = PetSfxBank.load(real(), SoundEvent::of, lines::add);
        assertNotSame(PetSfxBank.EMPTY, bank);
        int sounds = 0;
        for (PetSfxAction action : PetSfxAction.ALL) {
            assertNotNull(bank.samples[action.ordinal()], action.key);
            assertTrue(bank.samples[action.ordinal()].length > 0, action.key);
            sounds += bank.samples[action.ordinal()].length;
        }
        assertEquals(1, lines.size());
        assertEquals("[pet] sfx loaded " + PetSfxAction.ALL.length + " actions, " + sounds + " sounds", lines.get(0));
    }

    @Test
    void ownNamespaceIsAcceptedAndForeignNamespacesAreIgnored() {
        List<String> lines = new ArrayList<>();
        String entry = "{\"volume\": 1, \"pitch\": [1, 1], \"sound\": \"%s\"}";
        String json = "{\"step\": [" + entry.formatted("hunterbuddy:pet.sfx_step") + "," + entry.formatted("other:pet.x") + "]}";
        PetSfxBank bank = PetSfxBank.load(PetSfxTest.resources(json), id -> null, lines::add);
        assertEquals(1, bank.samples[PetSfxAction.STEP.ordinal()].length);
        assertTrue(lines.get(0).contains("1 unknown"), lines.get(0));
    }

    @Test
    void anUnparsableFileIsReportedInsteadOfSilentlyIgnored() {
        List<String> lines = new ArrayList<>();
        assertSame(PetSfxBank.EMPTY, PetSfxBank.load(PetSfxTest.resources("{"), SoundEvent::of, lines::add));
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).startsWith("[pet] sfx not loaded"), lines.get(0));
    }

    @Test
    void theLogNamesTheReasonAndSpeaksAtMostOncePerActionPerMinute() {
        List<String> lines = new ArrayList<>();
        PetSfx sfx = new PetSfx(new PetSfx.Backend() {
            public boolean active(int slot) { return false; }
            public boolean start(PetSfxAction action, int slot, float volume) { return false; }
            public void stop(int slot) { }
            public String refusal() { return "no sound loaded"; }
        }, new PetSfxLog(lines::add));
        sfx.update(0, false, false, 1);
        for (int i = 0; i < 100; i++) sfx.play(PetSfxAction.STEP);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).startsWith("[pet] sfx step skipped: disabled"), lines.get(0));
        sfx.update(30000, true, false, 1);
        sfx.play(PetSfxAction.STEP);
        assertEquals(1, lines.size());
        sfx.update(60000, true, false, 1);
        sfx.play(PetSfxAction.STEP);
        assertEquals("[pet] sfx step skipped: no sound loaded", lines.get(1));
        sfx.update(61000, true, true, 1);
        sfx.play(PetSfxAction.JUMP);
        assertEquals("[pet] sfx jump skipped: silenced by combat, regear or landing", lines.get(2));
    }
}
