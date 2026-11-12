package com.AdamNew.dynamicwaters.registry;

import com.AdamNew.dynamicwaters.DynamicWaters;
import com.AdamNew.dynamicwaters.platform.Registrar;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.registries.Registries;

public class ModSounds {
    public static final Registrar<SoundEvent> SOUND_EVENTS = Registrar.create(Registries.SOUND_EVENT);

    public static final Supplier<SoundEvent> RIVER_FLOW = registerSoundEvent("river_flow");

    private static Supplier<SoundEvent> registerSoundEvent(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(DynamicWaters.MODID, name)));
    }
}
