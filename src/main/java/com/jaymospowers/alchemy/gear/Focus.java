package com.jaymospowers.alchemy.gear;

import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.Phials;
import com.jaymospowers.alchemy.flux.FluxEffects;
import java.util.Optional;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What a focus does when the {@link RodItem Artificer's Rod} that holds it is used. Each cast draws its essence from the
 * phials the caster carries, leaves a little flux where it was cast, and only happens (and only costs) if there is
 * something to cast it at. Tuning is in the constants of each entry and at the top of this class.
 */
public enum Focus {
	/** Drags the creature or dropped item you aim at toward you. */
	PULL("pull", EssenceList.of(Essence.MOTION, 1, Essence.AIR, 1), 20, 24.0),
	/** Shoves what you aim at away, hard. */
	PUSH("push", EssenceList.of(Essence.MOTION, 1, Essence.EARTH, 1), 20, 24.0),
	/** Lifts the block you aim at, with whatever it holds, into a bound block you carry. */
	LIFT("lift", EssenceList.of(Essence.EARTH, 2, Essence.SPIRIT, 1), 30, 8.0),
	/** A small bolt of fire. */
	EMBER("ember", EssenceList.of(Essence.FIRE, 1, Essence.SPARK, 1), 12, 30.0),
	/** A bolt of cold that slows what it strikes. */
	FROST("frost", EssenceList.of(Essence.FROST, 1, Essence.WATER, 1), 16, 24.0);

	// ---- tuning ----
	/** How hard Pull yanks (blocks per tick of speed it gives), and Push shoves, with the lift it adds. */
	public static final double PULL_SPEED = 1.3, PUSH_SPEED = 2.0, PUSH_LIFT = 0.45;
	/** The frost bolt: damage, ticks of Slowness (level III) and of frostbite it adds. */
	public static final float FROST_DAMAGE = 2.0F;
	public static final int FROST_SLOW_TICKS = 120, FROST_SLOW_LEVEL = 2, FROST_FREEZE_TICKS = 160;
	/** The ember bolt: how long what it hits burns (seconds). */
	public static final int EMBER_BURN_SECONDS = 4;
	/** Flux left where a cast is made. */
	public static final int FLUX = 1;

	public final String id;
	public final EssenceList cost;
	/** Ticks the rod needs to recover. */
	public final int cooldown;
	/** How far the caster can aim. */
	public final double range;

	Focus(String id, EssenceList cost, int cooldown, double range) {
		this.id = id;
		this.cost = cost;
		this.cooldown = cooldown;
		this.range = range;
	}

	/** The research that lets this focus be used (and its infusion made). */
	public String research() {
		return "focus_" + id;
	}

	public static @Nullable Focus byId(String id) {
		for (Focus f : values()) {
			if (f.id.equals(id)) {
				return f;
			}
		}
		return null;
	}

	/** Casts it. Returns whether it happened (and so was paid for). Tells the caster, vaguely, why not. */
	public boolean cast(ServerPlayer player, ServerLevel level) {
		return switch (this) {
			case PULL, PUSH -> move(player, level);
			case LIFT -> lift(player, level);
			case EMBER -> ember(player, level);
			case FROST -> frost(player, level);
		};
	}

	private boolean move(ServerPlayer player, ServerLevel level) {
		Entity target = aimedEntity(player, level, range);
		if (target == null || !(target instanceof LivingEntity || target instanceof ItemEntity)) {
			player.sendOverlayMessage(RodItem.text("nothing_there"));
			return false;
		}
		if (!spend(player, level)) {
			return false;
		}
		Vec3 toward = player.getEyePosition().subtract(target.position().add(0.0, target.getBbHeight() / 2.0, 0.0)).normalize();
		Vec3 velocity = this == PULL ? toward.scale(PULL_SPEED).add(0.0, 0.15, 0.0)
			: player.getLookAngle().scale(PUSH_SPEED).add(0.0, PUSH_LIFT, 0.0);
		target.setDeltaMovement(velocity);
		target.needsSync = true;
		target.resetFallDistance();
		level.sendParticles(this == PULL ? ParticleTypes.PORTAL : ParticleTypes.CLOUD, target.getX(), target.getY() + target.getBbHeight() / 2.0,
			target.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
		level.playSound(null, target.blockPosition(), SoundEvents.ENDER_EYE_LAUNCH, SoundSource.PLAYERS, 0.8F, this == PULL ? 1.4F : 0.7F);
		return true;
	}

	private boolean lift(ServerPlayer player, ServerLevel level) {
		BlockHitResult hit = aimedBlock(player, level, range);
		if (hit == null) {
			player.sendOverlayMessage(RodItem.text("nothing_there"));
			return false;
		}
		if (!BoundBlockItem.bindable(level, hit.getBlockPos(), level.getBlockState(hit.getBlockPos()))
			|| !player.mayBuild() || !level.mayInteract(player, hit.getBlockPos())) {
			player.sendOverlayMessage(RodItem.text("wont_lift"));
			return false;
		}
		if (!spend(player, level)) {
			return false;
		}
		BoundBlockItem.bind(level, hit.getBlockPos(), player);
		return true;
	}

	private boolean ember(ServerPlayer player, ServerLevel level) {
		if (!spend(player, level)) {
			return false;
		}
		Vec3 look = player.getLookAngle();
		SmallFireball bolt = new SmallFireball(level, player, look);
		bolt.setPos(player.getEyePosition().add(look.scale(0.8)));
		level.addFreshEntity(bolt);
		level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.8F, 1.3F);
		return true;
	}

	private boolean frost(ServerPlayer player, ServerLevel level) {
		if (!spend(player, level)) {
			return false;
		}
		Vec3 from = player.getEyePosition(), look = player.getLookAngle();
		Entity target = aimedEntity(player, level, range);
		double reach = target == null ? range : Math.min(range, target.position().distanceTo(from));
		BlockHitResult wall = level.clip(new ClipContext(from, from.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (wall.getType() != HitResult.Type.MISS) {
			reach = Math.min(reach, wall.getLocation().distanceTo(from));
		}
		for (double d = 1.0; d < reach; d += 0.6) {
			Vec3 at = from.add(look.scale(d));
			level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y - 0.1, at.z, 2, 0.05, 0.05, 0.05, 0.0);
		}
		if (target instanceof LivingEntity living) {
			living.hurtServer(level, level.damageSources().indirectMagic(player, player), FROST_DAMAGE);
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, FROST_SLOW_TICKS, FROST_SLOW_LEVEL), player);
			living.setTicksFrozen(Math.max(living.getTicksFrozen(), FROST_FREEZE_TICKS));
			level.sendParticles(ParticleTypes.SNOWFLAKE, living.getX(), living.getY() + living.getBbHeight() / 2.0, living.getZ(), 14, 0.3, 0.4, 0.3, 0.05);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 0.9F, 1.5F);
		return true;
	}

	/** Pays the cast out of carried phials and leaves its flux; false (with a word) if there isn't enough essence. */
	private boolean spend(ServerPlayer player, ServerLevel level) {
		if (!Phials.pay(player, cost)) {
			player.sendOverlayMessage(RodItem.text("no_essence"));
			return false;
		}
		FluxEffects.burst(level, player.blockPosition(), FLUX);
		return true;
	}

	/** The nearest creature or dropped item along the caster's gaze within {@code range}, if nothing solid is in the way. */
	static @Nullable Entity aimedEntity(ServerPlayer player, ServerLevel level, double range) {
		Vec3 from = player.getEyePosition(), dir = player.getLookAngle(), to = from.add(dir.scale(range));
		double nearest = range;
		BlockHitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (wall.getType() != HitResult.Type.MISS) {
			nearest = wall.getLocation().distanceTo(from);
		}
		Entity best = null;
		AABB box = player.getBoundingBox().expandTowards(dir.scale(range)).inflate(1.0);
		for (Entity e : level.getEntities(player, box, x -> !x.isSpectator() && (x.isPickable() || x instanceof ItemEntity))) {
			Optional<Vec3> hit = e.getBoundingBox().inflate(0.3).clip(from, to);
			if (hit.isPresent()) {
				double d = hit.get().distanceTo(from);
				if (d < nearest) {
					nearest = d;
					best = e;
				}
			}
		}
		return best;
	}

	/** The block along the caster's gaze within {@code range}, or null. */
	static @Nullable BlockHitResult aimedBlock(ServerPlayer player, ServerLevel level, double range) {
		Vec3 from = player.getEyePosition();
		BlockHitResult hit = level.clip(new ClipContext(from, from.add(player.getLookAngle().scale(range)), ClipContext.Block.OUTLINE,
			ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.BLOCK ? hit : null;
	}
}
