package com.jaymospowers.tome;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

/**
 * The four elements of the {@link TomeItem spell tome}: how each looks (colour, trail, cast sound) and what its bolt does is
 * in {@link TomeBolt}. The item's {@link Tome#ELEMENT} component holds {@link #id}.
 */
public enum TomeElement {
	FIRE("fire", 0xFF7A1A, ParticleTypes.FLAME, SoundEvents.BLAZE_SHOOT, 1.5F, 0.5F),
	FROST("frost", 0x7FD8FF, ParticleTypes.SNOWFLAKE, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.3F, 0.9F),
	LIGHTNING("lightning", 0xFFF06A, ParticleTypes.ELECTRIC_SPARK, SoundEvents.LIGHTNING_BOLT_IMPACT, 2.0F, 0.3F),
	ARCANE("arcane", 0xB06BFF, ParticleTypes.WITCH, SoundEvents.ILLUSIONER_CAST_SPELL, 1.5F, 0.6F);

	/** The id kept in the item's component, and the end of its lang keys. */
	public final String id;
	/** RGB: the bar, the name and the item's tint. */
	public final int color;
	public final ParticleOptions trail;
	public final SoundEvent castSound;
	public final float castPitch, castVolume;

	TomeElement(String id, int color, ParticleOptions trail, SoundEvent castSound, float castPitch, float castVolume) {
		this.id = id;
		this.color = color;
		this.trail = trail;
		this.castSound = castSound;
		this.castPitch = castPitch;
		this.castVolume = castVolume;
	}

	public TomeElement next() {
		return values()[(this.ordinal() + 1) % values().length];
	}

	/** The element with this id; fire for anything unknown or missing. */
	public static TomeElement byId(@Nullable String id) {
		for (TomeElement element : values()) {
			if (element.id.equals(id)) {
				return element;
			}
		}
		return FIRE;
	}

	public String langKey() {
		return "tome.jaymospowers.element." + this.id;
	}
}
