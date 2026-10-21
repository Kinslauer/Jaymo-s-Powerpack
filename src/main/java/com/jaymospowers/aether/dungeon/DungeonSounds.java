package com.jaymospowers.aether.dungeon;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Vanilla sounds looked up by id (the dungeon's mobs borrow them; see each class for which). */
final class DungeonSounds {
	private DungeonSounds() {
	}

	static SoundEvent get(String id) {
		return BuiltInRegistries.SOUND_EVENT.getValue(Identifier.withDefaultNamespace(id));
	}
}
