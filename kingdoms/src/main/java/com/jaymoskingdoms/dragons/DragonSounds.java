package com.jaymoskingdoms.dragons;

import com.jaymoskingdoms.JaymosKingdoms;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;

/**
 * The dragons' own sounds and rubble, from Dragonforge: Four Elements by RochStudio (used with Andy's permission, see
 * {@code licenses/dragonforge-NOTICE.txt}): the rider's bite, the bond on taming, the calling horn, a roar for each breath
 * (its dive slam) and the stone tremor's rumble; and {@link #RUBBLE}, the tremor's flying grit (its earth breath frames,
 * drawn by {@code client.dragons.RubbleParticle}).
 */
public final class DragonSounds {
	public static Holder.Reference<SoundEvent> BITE, TAME, HORN, FIRE_ROAR, ICE_ROAR, LIGHTNING_ROAR, EARTH_ROAR, EARTH_BREATH;
	public static SimpleParticleType RUBBLE;

	private DragonSounds() {
	}

	static void register() {
		BITE = sound("dragon.bite");
		TAME = sound("dragon.tame");
		HORN = sound("dragon.horn");
		FIRE_ROAR = sound("dragon.fire_roar");
		ICE_ROAR = sound("dragon.ice_roar");
		LIGHTNING_ROAR = sound("dragon.lightning_roar");
		EARTH_ROAR = sound("dragon.earth_roar");
		EARTH_BREATH = sound("dragon.earth_breath");
		RUBBLE = Registry.register(BuiltInRegistries.PARTICLE_TYPE, JaymosKingdoms.id("dragon_rubble"), FabricParticleTypes.simple(false));
	}

	/** The roar that goes with a dragon's breath (its dive slam, its call). */
	public static Holder<SoundEvent> roar(WindDragon.Element element) {
		return switch (element) {
			case FIRE -> FIRE_ROAR;
			case FROST -> ICE_ROAR;
			case EARTH -> EARTH_ROAR;
			default -> LIGHTNING_ROAR;
		};
	}

	private static Holder.Reference<SoundEvent> sound(String name) {
		return Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, JaymosKingdoms.id(name), SoundEvent.createVariableRangeEvent(JaymosKingdoms.id(name)));
	}
}
