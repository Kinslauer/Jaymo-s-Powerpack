package com.jaymoskingdoms.glowwood.creatures;

import com.jaymoskingdoms.JaymosKingdoms;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/** The glow-wood creatures', the Myceliarch's and its items' sound events (their files are the ported Spore &amp; Stem ones). */
public final class GlowSounds {
	public static SoundEvent BLOATSHROOM_AMBIENT,
		BLOATSHROOM_BURST,
		BLOATSHROOM_DEATH,
		BLOATSHROOM_HURT,
		BLOATSHROOM_SWELL,
		GLOWMOTH_AMBIENT,
		GLOWMOTH_DEATH,
		GLOWMOTH_HURT,
		MIMICAP_AMBIENT,
		MIMICAP_BITE,
		MIMICAP_DEATH,
		MIMICAP_HURT,
		MIMICAP_STEP,
		MIMICAP_WAKE,
		MYCELIARCH_AMBIENT,
		MYCELIARCH_DEATH,
		MYCELIARCH_EMERGE,
		MYCELIARCH_HURT,
		MYCELIARCH_ROAR,
		MYCELIARCH_ROOT_ERUPT,
		MYCELIARCH_SLAM,
		MYCELIARCH_SPORE_NOVA,
		MYCELIARCH_STEP,
		ROOT_SPIKE_ERUPT,
		SHAMBLER_AMBIENT,
		SHAMBLER_ATTACK,
		SHAMBLER_DEATH,
		SHAMBLER_HURT,
		SHAMBLER_RISE,
		SPORE_GLOB_SPLAT,
		SPORELING_AMBIENT,
		SPORELING_DEATH,
		SPORELING_HAPPY,
		SPORELING_HURT,
		SPORELING_SNEEZE,
		TRUFFLER_AMBIENT,
		TRUFFLER_DEATH,
		TRUFFLER_DIG,
		TRUFFLER_HURT,
		TRUFFLER_SNIFF,
		BLOOMREAVER_BURST,
		FRUITING_HEART_USE;

	private GlowSounds() {
	}

	static void register() {
		BLOATSHROOM_AMBIENT = sound("entity.bloatshroom.ambient");
		BLOATSHROOM_BURST = sound("entity.bloatshroom.burst");
		BLOATSHROOM_DEATH = sound("entity.bloatshroom.death");
		BLOATSHROOM_HURT = sound("entity.bloatshroom.hurt");
		BLOATSHROOM_SWELL = sound("entity.bloatshroom.swell");
		GLOWMOTH_AMBIENT = sound("entity.glowmoth.ambient");
		GLOWMOTH_DEATH = sound("entity.glowmoth.death");
		GLOWMOTH_HURT = sound("entity.glowmoth.hurt");
		MIMICAP_AMBIENT = sound("entity.mimicap.ambient");
		MIMICAP_BITE = sound("entity.mimicap.bite");
		MIMICAP_DEATH = sound("entity.mimicap.death");
		MIMICAP_HURT = sound("entity.mimicap.hurt");
		MIMICAP_STEP = sound("entity.mimicap.step");
		MIMICAP_WAKE = sound("entity.mimicap.wake");
		MYCELIARCH_AMBIENT = sound("entity.myceliarch.ambient");
		MYCELIARCH_DEATH = sound("entity.myceliarch.death");
		MYCELIARCH_EMERGE = sound("entity.myceliarch.emerge");
		MYCELIARCH_HURT = sound("entity.myceliarch.hurt");
		MYCELIARCH_ROAR = sound("entity.myceliarch.roar");
		MYCELIARCH_ROOT_ERUPT = sound("entity.myceliarch.root_erupt");
		MYCELIARCH_SLAM = sound("entity.myceliarch.slam");
		MYCELIARCH_SPORE_NOVA = sound("entity.myceliarch.spore_nova");
		MYCELIARCH_STEP = sound("entity.myceliarch.step");
		ROOT_SPIKE_ERUPT = sound("entity.root_spike.erupt");
		SHAMBLER_AMBIENT = sound("entity.shambler.ambient");
		SHAMBLER_ATTACK = sound("entity.shambler.attack");
		SHAMBLER_DEATH = sound("entity.shambler.death");
		SHAMBLER_HURT = sound("entity.shambler.hurt");
		SHAMBLER_RISE = sound("entity.shambler.rise");
		SPORE_GLOB_SPLAT = sound("entity.spore_glob.splat");
		SPORELING_AMBIENT = sound("entity.sporeling.ambient");
		SPORELING_DEATH = sound("entity.sporeling.death");
		SPORELING_HAPPY = sound("entity.sporeling.happy");
		SPORELING_HURT = sound("entity.sporeling.hurt");
		SPORELING_SNEEZE = sound("entity.sporeling.sneeze");
		TRUFFLER_AMBIENT = sound("entity.truffler.ambient");
		TRUFFLER_DEATH = sound("entity.truffler.death");
		TRUFFLER_DIG = sound("entity.truffler.dig");
		TRUFFLER_HURT = sound("entity.truffler.hurt");
		TRUFFLER_SNIFF = sound("entity.truffler.sniff");
		BLOOMREAVER_BURST = sound("item.bloomreaver.burst");
		FRUITING_HEART_USE = sound("item.fruiting_heart.use");
	}

	private static SoundEvent sound(String name) {
		return Registry.register(BuiltInRegistries.SOUND_EVENT, JaymosKingdoms.id(name), SoundEvent.createVariableRangeEvent(JaymosKingdoms.id(name)));
	}
}
