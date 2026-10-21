package com.jaymospowers.power.cryo;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.smoke.SmokeField;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.14.0) V, Ice Shards: a fan of {@link #COUNT} shards of ice thrown along your look. Each is a snowball wearing an ice
 * shard (as Smoke's bomb wears a firework star: no new entity), tracked here: whatever it meets is hit, slowed and
 * chilled, and it shatters. Smoke a shard flies through turns to freezing fog. Snow mode throws plain snowballs that
 * slow and blind and never hurt.
 */
public final class CryoShards {
	/** How many, the angle between two (degrees), how fast (blocks a tick) and how long one flies at most (ticks). */
	static final int COUNT = 5;
	static final float FAN = 8.0F;
	static final double SPEED = 1.7;
	static final int LIFE = 40;
	/** On a hit: damage, Slowness II this long, freezing added; snow mode: blinded this long. */
	static final float DAMAGE = 4.0F;
	static final int SLOW_TICKS = 60;
	static final int FREEZE = 70;
	static final int SNOW_BLIND_TICKS = 40;
	/** How near (blocks) a shard's path has to pass a creature to hit it. */
	static final double HIT_SLACK = 0.3;
	/** Smoke this near a flying shard freezes. */
	static final double FOG_RADIUS = 1.2;
	public static final int COOLDOWN = 80;
	public static final int COOLDOWN_MASTERED = 60;

	/** The shard the snowballs wear (only a look: it has no use of its own). */
	public static Item ICE_SHARD;

	private static final class Shard {
		final Snowball ball;
		final ServerPlayer owner;
		final boolean snow;
		final float strength;
		Vec3 last;
		int age;

		Shard(Snowball ball, ServerPlayer owner, boolean snow, float strength) {
			this.ball = ball;
			this.owner = owner;
			this.snow = snow;
			this.strength = strength;
			this.last = ball.position();
		}
	}

	private static final List<Shard> SHARDS = new ArrayList<>();

	private CryoShards() {
	}

	static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("cryo_ice_shard"));
		ICE_SHARD = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
		ServerTickEvents.END_SERVER_TICK.register(CryoShards::tick);
	}

	static void fire(ServerPlayer player) {
		ServerLevel level = player.level();
		boolean snow = CryoPower.snow(player);
		float strength = CryoPower.strength(player);
		Vec3 look = player.getViewVector(1.0F);
		for (int i = 0; i < COUNT; i++) {
			float turn = (i - (COUNT - 1) / 2.0F) * FAN * net.minecraft.util.Mth.DEG_TO_RAD;
			Vec3 dir = look.yRot(turn).normalize();
			Snowball ball = new Snowball(level, player, new ItemStack(snow ? Items.SNOWBALL : ICE_SHARD));
			ball.setDeltaMovement(dir.scale(SPEED));
			level.addFreshEntity(ball);
			SHARDS.add(new Shard(ball, player, snow, strength));
		}
		CryoPower.cooldown(player, CryoState.SHARDS, CryoPower.mastered(player) ? COOLDOWN_MASTERED : COOLDOWN);
		CryoMoves.begin(player, CryoMoves.SHARDS);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.8F, 0.6F);
		if (!snow) {
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 0.8F, 1.5F);
		}
	}

	private static void tick(MinecraftServer server) {
		if (SHARDS.isEmpty()) {
			return;
		}
		SHARDS.removeIf(shard -> {
			if (!(shard.ball.level() instanceof ServerLevel level)) {
				return true;
			}
			if (shard.ball.isRemoved()) {
				// it met something on its own: whatever's right there takes the hit
				LivingEntity near = nearest(level, shard, new AABB(shard.last, shard.last).inflate(1.0));
				if (near != null) {
					hit(level, shard, near);
				}
				shatter(level, shard, shard.last);
				return true;
			}
			if (++shard.age > LIFE) {
				shatter(level, shard, shard.ball.position());
				shard.ball.discard();
				return true;
			}
			Vec3 pos = shard.ball.position();
			Vec3 next = pos.add(shard.ball.getDeltaMovement());
			shard.last = pos;
			if (!shard.snow) {
				SmokeField.freeze(level, pos, FOG_RADIUS);
			}
			// the path ahead: a creature on it is hit before the snowball would only bump it
			for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos, next).inflate(HIT_SLACK + 0.5),
				e -> e != shard.owner && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
				AABB body = living.getBoundingBox().inflate(HIT_SLACK);
				if (body.contains(pos) || body.clip(pos, next).isPresent()) {
					hit(level, shard, living);
					shatter(level, shard, pos);
					shard.ball.discard();
					return true;
				}
			}
			return false;
		});
	}

	private static LivingEntity nearest(ServerLevel level, Shard shard, AABB box) {
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box,
			e -> e != shard.owner && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
			double d = living.getBoundingBox().distanceToSqr(shard.last);
			if (d < bestDistance) {
				bestDistance = d;
				best = living;
			}
		}
		return best;
	}

	private static void hit(ServerLevel level, Shard shard, LivingEntity living) {
		living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, 1), shard.owner);
		if (shard.snow) {
			living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, SNOW_BLIND_TICKS, 0), shard.owner);
			return;
		}
		living.hurtServer(level, level.damageSources().thrown(shard.ball, shard.owner), DAMAGE * shard.strength);
		if (living.canFreeze()) {
			living.setTicksFrozen(Math.min(living.getTicksRequiredToFreeze() + FREEZE, living.getTicksFrozen() + Math.round(FREEZE * shard.strength)));
		}
	}

	private static void shatter(ServerLevel level, Shard shard, Vec3 at) {
		if (shard.snow) {
			level.sendParticles(ParticleTypes.ITEM_SNOWBALL, at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0.05);
			return;
		}
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()), at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.08);
		level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 4, 0.2, 0.2, 0.2, 0.02);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.6F, 1.6F);
	}

	static void clear() {
		SHARDS.clear();
	}
}
