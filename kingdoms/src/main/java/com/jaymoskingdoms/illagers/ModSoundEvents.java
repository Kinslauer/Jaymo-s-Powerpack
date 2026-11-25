package com.jaymoskingdoms.illagers;

import com.jaymoskingdoms.JaymosKingdoms;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/** The Illager Invasion kinds' sounds (MIT, its own recordings), as {@code jaymoskingdoms:illager.<kind>.<sound>}. */
public final class ModSoundEvents {
	public static final Holder.Reference<SoundEvent> SURRENDERED_AMBIENT_SOUND_EVENT = register("illager.surrendered.ambient");
	public static final Holder.Reference<SoundEvent> SURRENDERED_HURT_SOUND_EVENT = register("illager.surrendered.hurt");
	public static final Holder.Reference<SoundEvent> SURRENDERED_CHARGE_SOUND_EVENT = register("illager.surrendered.charge");
	public static final Holder.Reference<SoundEvent> SURRENDERED_DEATH_SOUND_EVENT = register("illager.surrendered.death");
	public static final Holder.Reference<SoundEvent> ARCHIVIST_AMBIENT_SOUND_EVENT = register("illager.archivist.ambient");
	public static final Holder.Reference<SoundEvent> ARCHIVIST_HURT_SOUND_EVENT = register("illager.archivist.hurt");
	public static final Holder.Reference<SoundEvent> ARCHIVIST_DEATH_SOUND_EVENT = register("illager.archivist.death");
	public static final Holder.Reference<SoundEvent> INVOKER_FANGS_SOUND_EVENT = register("illager.invoker.fangs");
	public static final Holder.Reference<SoundEvent> INVOKER_HURT_SOUND_EVENT = register("illager.invoker.hurt");
	public static final Holder.Reference<SoundEvent> INVOKER_DEATH_SOUND_EVENT = register("illager.invoker.death");
	public static final Holder.Reference<SoundEvent> INVOKER_AMBIENT_SOUND_EVENT = register("illager.invoker.ambient");
	public static final Holder.Reference<SoundEvent> INVOKER_COMPLETE_CAST_SOUND_EVENT = register("illager.invoker.complete_cast");
	public static final Holder.Reference<SoundEvent> INVOKER_TELEPORT_CAST_SOUND_EVENT = register("illager.invoker.teleport_cast");
	public static final Holder.Reference<SoundEvent> INVOKER_FANGS_CAST_SOUND_EVENT = register("illager.invoker.fangs_cast");
	public static final Holder.Reference<SoundEvent> INVOKER_BIG_CAST_SOUND_EVENT = register("illager.invoker.big_cast");
	public static final Holder.Reference<SoundEvent> INVOKER_SUMMON_CAST_SOUND_EVENT = register("illager.invoker.summon_cast");
	public static final Holder.Reference<SoundEvent> INVOKER_SHIELD_BREAK_SOUND_EVENT = register("illager.invoker.shield_break");
	public static final Holder.Reference<SoundEvent> ILLAGER_BRUTE_AMBIENT_SOUND_EVENT = register("illager.illager_brute.ambient");
	public static final Holder.Reference<SoundEvent> ILLAGER_BRUTE_HURT_SOUND_EVENT = register("illager.illager_brute.hurt");
	public static final Holder.Reference<SoundEvent> ILLAGER_BRUTE_DEATH_SOUND_EVENT = register("illager.illager_brute.death");
	public static final Holder.Reference<SoundEvent> PROVOKER_AMBIENT_SOUND_EVENT = register("illager.provoker.idle");
	public static final Holder.Reference<SoundEvent> PROVOKER_HURT_SOUND_EVENT = register("illager.provoker.hurt");
	public static final Holder.Reference<SoundEvent> PROVOKER_DEATH_SOUND_EVENT = register("illager.provoker.death");
	public static final Holder.Reference<SoundEvent> PROVOKER_CELEBRATE_SOUND_EVENT = register("illager.provoker.celebrate");
	public static final Holder.Reference<SoundEvent> BASHER_AMBIENT_SOUND_EVENT = register("illager.basher.idle");
	public static final Holder.Reference<SoundEvent> BASHER_HURT_SOUND_EVENT = register("illager.basher.hurt");
	public static final Holder.Reference<SoundEvent> BASHER_DEATH_SOUND_EVENT = register("illager.basher.death");
	public static final Holder.Reference<SoundEvent> BASHER_CELEBRATE_SOUND_EVENT = register("illager.basher.celebrate");
	public static final Holder.Reference<SoundEvent> FIRECALLER_AMBIENT_SOUND_EVENT = register("illager.firecaller.idle");
	public static final Holder.Reference<SoundEvent> FIRECALLER_HURT_SOUND_EVENT = register("illager.firecaller.hurt");
	public static final Holder.Reference<SoundEvent> FIRECALLER_DEATH_SOUND_EVENT = register("illager.firecaller.death");
	public static final Holder.Reference<SoundEvent> FIRECALLER_CAST_SOUND_EVENT = register("illager.firecaller.cast");
	public static final Holder.Reference<SoundEvent> SORCERER_CAST_SOUND_EVENT = register("illager.sorcerer.cast");
	public static final Holder.Reference<SoundEvent> SORCERER_COMPLETE_CAST_SOUND_EVENT = register("illager.sorcerer.complete_cast");

	private ModSoundEvents() {
	}

	static void bootstrap() {
	}

	private static Holder.Reference<SoundEvent> register(String name) {
		return Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, JaymosKingdoms.id(name), SoundEvent.createVariableRangeEvent(JaymosKingdoms.id(name)));
	}
}
