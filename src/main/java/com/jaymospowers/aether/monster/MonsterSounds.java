package com.jaymospowers.aether.monster;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Vanilla sounds looked up by id (the Aether's monsters borrow them; see each class for which). */
final class MonsterSounds {
	private MonsterSounds() {
	}

	static SoundEvent get(String id) {
		return BuiltInRegistries.SOUND_EVENT.getValue(Identifier.withDefaultNamespace(id));
	}
}
