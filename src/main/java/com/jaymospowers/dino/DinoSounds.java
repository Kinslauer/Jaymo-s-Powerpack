package com.jaymospowers.dino;

import com.jaymospowers.dino.entity.Species;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** The dinosaurs' sounds: events are {@code jaymospowers:dino.<species>.<voice>} and {@code dino.<name>}, files under {@code sounds/dino/}. */
public final class DinoSounds {
	private static final Map<Species, SoundEvent> AMBIENT = new EnumMap<>(Species.class);
	private static final Map<Species, SoundEvent> HURT = new EnumMap<>(Species.class);
	private static final Map<Species, SoundEvent> DEATH = new EnumMap<>(Species.class);
	private static final Map<Species, SoundEvent> ROAR = new EnumMap<>(Species.class);
	public static SoundEvent BITE, BITE_BIG, STEP_HEAVY, STOMP, KNOCKED_OUT, TAMED, LEVEL_UP, EAT, SPIT, FRILL, DART_FIRE, DART_HIT;

	private DinoSounds() {
	}

	private static SoundEvent sound(String name) {
		Identifier id = Dino.id("dino." + name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static SoundEvent ambient(Species s) {
		return AMBIENT.get(s);
	}

	public static SoundEvent hurt(Species s) {
		return HURT.get(s);
	}

	public static SoundEvent death(Species s) {
		return DEATH.get(s);
	}

	public static SoundEvent roar(Species s) {
		return ROAR.get(s);
	}

	public static void init() {
		for (Species s : Species.values()) {
			AMBIENT.put(s, sound(s.id + ".ambient"));
			HURT.put(s, sound(s.id + ".hurt"));
			DEATH.put(s, sound(s.id + ".death"));
			ROAR.put(s, sound(s.id + ".roar"));
		}
		BITE = sound("bite");
		BITE_BIG = sound("bite_big");
		STEP_HEAVY = sound("step_heavy");
		STOMP = sound("stomp");
		KNOCKED_OUT = sound("knocked_out");
		TAMED = sound("tamed");
		LEVEL_UP = sound("level_up");
		EAT = sound("eat");
		SPIT = sound("dilophosaurus.spit");
		FRILL = sound("dilophosaurus.frill");
		DART_FIRE = sound("tranq_rifle.fire");
		DART_HIT = sound("tranq_dart.hit");
	}
}
