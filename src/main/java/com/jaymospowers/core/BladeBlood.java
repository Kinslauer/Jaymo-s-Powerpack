package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Blood (Andy): every blade's blow sprays it, on every weapon, held or thrown ({@link Dismember#blade}), and the powers'
 * own blades call {@link #splash} for theirs ({@link Dismember#cut} does for every power's cut). Harder blows spray more; a kill bursts. Nothing that has no blood
 * ({@link #BLOODLESS}: bones, golems, spirits) bleeds.
 */
public final class BladeBlood {
	/** The blood's colour and drop size (a kill's bigger). */
	static final int COLOR = 0x8A0303;
	static final float SIZE = 1.1F, KILL_SIZE = 1.6F;
	/** Drops on a hit: this many, plus this many per point of damage, at most this many. */
	static final int HIT_DROPS = 6, HIT_DROPS_MAX = 24;
	static final float DROPS_PER_DAMAGE = 1.5F;
	/** A kill: this many drops and this many flecks of gore thrown out. */
	static final int KILL_DROPS = 45, KILL_GORE = 20;
	/** How far the gore flies on a kill. */
	static final double KILL_GORE_SPEED = 0.3;

	/** What has no blood to spill. */
	public static final TagKey<EntityType<?>> BLOODLESS = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("bloodless"));

	/** Who bled this tick (the game time): a power's blade blow that also reads as a held blade bleeds only once. */
	private static final Map<LivingEntity, Long> SPLASHED = new WeakHashMap<>();

	private BladeBlood() {
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(BladeBlood::afterDamage);
	}

	private static void afterDamage(LivingEntity target, DamageSource source, float baseDamage, float damage, boolean blocked) {
		if (blocked || damage <= 0.0F || source.getEntity() == target) {
			return;
		}
		ItemStack weapon = source.getWeaponItem();
		if (weapon == null || !Dismember.blade(weapon)) {
			return;
		}
		splash(target, damage);
	}

	/** A blade's blow just landed on {@code target} for {@code damage}: blood, and a burst of it if that killed it. */
	public static void splash(LivingEntity target, float damage) {
		if (!(target.level() instanceof ServerLevel level) || damage <= 0.0F || target.getType().builtInRegistryHolder().is(BLOODLESS)) {
			return;
		}
		long now = level.getGameTime();
		boolean killed = target.isDeadOrDying();
		Long last = SPLASHED.put(target, now);
		if (last != null && last == now && !killed) {
			return;
		}
		Vec3 at = target.getBoundingBox().getCenter();
		double w = target.getBbWidth() * 0.35, h = target.getBbHeight() * 0.25;
		int drops = Math.min(HIT_DROPS_MAX, HIT_DROPS + Mth.floor(damage * DROPS_PER_DAMAGE));
		level.sendParticles(new DustParticleOptions(COLOR, SIZE), at.x, at.y, at.z, drops, w, h, w, 0.0);
		level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y, at.z, 1 + drops / 8, w, h, w, 0.1);
		if (!killed) {
			return;
		}
		level.sendParticles(new DustParticleOptions(COLOR, KILL_SIZE), at.x, at.y, at.z, KILL_DROPS, w * 1.4, h * 1.4, w * 1.4, 0.0);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), at.x, at.y, at.z,
			KILL_GORE, w, h, w, KILL_GORE_SPEED);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 0.9F, 0.6F);
	}
}
