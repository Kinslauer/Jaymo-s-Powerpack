package com.jaymospowers.combat;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.BladeBlood;
import com.jaymospowers.core.Dismember;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * What a blow sounds like by what it hit: bone (a skeleton's rattle), metal (a golem's ring), slime (a squelch) and flesh
 * (a wet cut), and what a limb breaking sounds like, bone or metal. They are our own sound ids ({@code sounds.json},
 * with subtitles) that for now point at fitting vanilla sounds, so real recordings can replace them without touching code.
 * A blow that rings on metal with sparks ({@link MetalHits}) keeps its own sound.
 */
public final class HitSounds {
	public static final SoundEvent HIT_FLESH = event("wound.hit.flesh"), HIT_BONE = event("wound.hit.bone"),
		HIT_METAL = event("wound.hit.metal"), HIT_SLIME = event("wound.hit.slime"),
		BREAK_BONE = event("wound.break.bone"), BREAK_METAL = event("wound.break.metal"), DISARM = event("wound.disarm");
	/** A blow of less than this does not sound by its material. */
	static final float MIN_DAMAGE = 2.0F;
	/** Loudness: a base, plus this much per point of damage, to a ceiling; and how far the pitch wanders either way. */
	static final float VOLUME = 0.45F, VOLUME_PER_DAMAGE = 0.03F, VOLUME_MAX = 1.0F, PITCH_SPREAD = 0.1F;

	/** What a body is made of, for how it sounds hit or broken. */
	public enum Material {
		FLESH, BONE, METAL, SLIME
	}

	private HitSounds() {
	}

	private static SoundEvent event(String path) {
		return SoundEvent.createVariableRangeEvent(JaymosPowers.id(path));
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (!blocked && base >= MIN_DAMAGE && victim.level() instanceof ServerLevel level) {
				struck(level, victim, source, base);
			}
		});
	}

	/** What {@code body} is made of. */
	public static Material material(LivingEntity body) {
		if (body instanceof Slime) {
			return Material.SLIME;
		}
		if (MetalHits.metalBody(body)) {
			return Material.METAL;
		}
		return body instanceof AbstractSkeleton ? Material.BONE : Material.FLESH;
	}

	private static void struck(ServerLevel level, LivingEntity victim, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_DROWNING)
			|| source.getDirectEntity() == null) {
			return;
		}
		Material material = material(victim);
		SoundEvent sound;
		switch (material) {
			case SLIME -> sound = HIT_SLIME;
			case BONE -> sound = HIT_BONE;
			case METAL -> {
				// (a hard weapon on a metal body already rings with sparks: MetalHits)
				if (source.getDirectEntity() instanceof LivingEntity attacker && MetalHits.hard(attacker.getMainHandItem())) {
					return;
				}
				sound = HIT_METAL;
			}
			default -> {
				ItemStack weapon = source.getWeaponItem();
				boolean cut = weapon != null && Dismember.blade(weapon) || !(source.getDirectEntity() instanceof LivingEntity);
				if (!cut || !BladeBlood.bleeds(victim)) {
					return;
				}
				sound = HIT_FLESH;
			}
		}
		Vec3 at = victim.getBoundingBox().getCenter();
		play(level, at, sound, Mth.clamp(VOLUME + amount * VOLUME_PER_DAMAGE, VOLUME, VOLUME_MAX), 1.0F);
	}

	/** A limb of {@code body} has just broken at {@code at}: bone snaps, metal buckles. */
	public static void broke(ServerLevel level, LivingEntity body, Vec3 at) {
		play(level, at, material(body) == Material.METAL ? BREAK_METAL : BREAK_BONE, 1.0F, 1.0F);
	}

	private static void play(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		float bend = (level.getRandom().nextFloat() - 0.5F) * 2.0F * PITCH_SPREAD;
		level.playSound(null, at.x, at.y, at.z, sound, net.minecraft.sounds.SoundSource.HOSTILE, volume, pitch + bend);
	}

	/** Whether {@code entity} is made of something that rings (for callers outside this package). */
	public static boolean rings(Entity entity) {
		return entity instanceof LivingEntity body && material(body) == Material.METAL;
	}
}
