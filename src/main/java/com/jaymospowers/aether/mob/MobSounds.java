package com.jaymospowers.aether.mob;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Vanilla animal sounds looked up by id; 26.3 no longer has adult pig, cow or chicken constants. */
final class MobSounds {
	private MobSounds() {
	}

	static SoundEvent get(String id) {
		return BuiltInRegistries.SOUND_EVENT.getValue(Identifier.withDefaultNamespace(id));
	}
}
