package com.jaymospowers.power.ember;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Flames;
import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Soak and return, the fire users' bracing (Emberborn of any power, and Pyre). Brace: sneak on the ground, standing
 * still with both hands empty. While braced:
 * <ul>
 * <li>Blows (a strike, a blast, a bolt that is not caught) lose {@link #SOAK_SHARE} of their damage into a store (the
 * HUD's thin bar under the heat bar) and the soaked heat feeds {@link RunningHot} too. The rest lands as normal.</li>
 * <li>A projectile that hits you (a fireball, a ki blast, a magic bolt) is caught: it does no harm, it is gone, and a
 * stronger bolt of your own flies back along your look ({@link #CATCH_MULT} times its damage, at least {@link #CATCH_MIN}).</li>
 * </ul>
 * Let go of sneak with at least {@link #RETURN_MIN} stored and the store goes out in a blast round you, scaled by how
 * full it was; whoever struck you last is hit again as well. The store leaks away slowly when you have not been braced.
 * Server only; the bolt is not an entity, the server moves and draws it.
 */
public final class EmberSoak {
	// ---- tuning ----
	public static final int MAX = 100;
	/** Standing braced this long (ticks) before it holds. */
	static final int BRACE_TICKS = 5;
	/** Share of a blow soaked, store gained per point soaked, the least a caught bolt adds. */
	static final float SOAK_SHARE = 0.6F, STORE_PER_DAMAGE = 5.0F, CATCH_STORE_MIN = 10.0F;
	/** Heat the soak gives the gauge per point soaked. */
	static final float HEAT_PER_SOAK = 3.0F;
	/** Ticks unbraced before the store leaks, and the leak a second. */
	static final int LEAK_AFTER = 60;
	static final float LEAK = 2.0F;
	/** A caught bolt: its damage times this, never under this; the blast on arrival, speed, life. */
	static final float CATCH_MULT = 1.5F, CATCH_MIN = 8.0F;
	static final double BOLT_RADIUS = 3.0, BOLT_SPEED = 1.4;
	static final int BOLT_LIFE = 50, BOLT_BURN = 6;
	/** The return blast: least store, reach at empty and the extra at full, damage at full, burning seconds, the push; the last attacker's extra hit. */
	static final int RETURN_MIN = 20, RETURN_BURN = 5;
	static final double RETURN_REACH = 3.0, RETURN_EXTRA = 3.0, RETURN_PUSH = 1.1;
	static final float RETURN_DAMAGE = 14.0F, LAST_ATTACKER_SHARE = 0.5F;
	static final double LAST_ATTACKER_RANGE = 12.0;
	static final int COLOR = 0xFF8A1E, WHITE = 0xFFE9B0;

	/** The store, 0 to {@link #MAX}, synced to its owner for the HUD; absent when empty. */
	public static final AttachmentType<Integer> STORE = AttachmentRegistry.create(JaymosPowers.id("ember_soak"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	private static final class Live {
		float store;
		int still;
		long lastBraced;
		boolean braced;
		Vec3 lastPos = Vec3.ZERO;
		UUID attacker;
		long attackedAt;
		int shown;
	}

	private static final class Bolt {
		final ServerLevel level;
		final ServerPlayer owner;
		Vec3 pos;
		final Vec3 velocity;
		final float damage;
		int age;

		Bolt(ServerLevel level, ServerPlayer owner, Vec3 pos, Vec3 velocity, float damage) {
			this.level = level;
			this.owner = owner;
			this.pos = pos;
			this.velocity = velocity;
			this.damage = damage;
		}
	}

	private static final Map<UUID, Live> LIVE = new HashMap<>();
	private static final List<Bolt> BOLTS = new ArrayList<>();
	private static boolean passing;

	private EmberSoak() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (RunningHot.user(player) && player.isAlive() && !player.isSpectator()) {
					tick(player, LIVE.computeIfAbsent(player.getUUID(), k -> new Live()));
				} else if (LIVE.remove(player.getUUID()) != null) {
					player.removeAttached(STORE);
				}
			}
			for (Iterator<Bolt> it = BOLTS.iterator(); it.hasNext();) {
				if (step(it.next())) {
					it.remove();
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LIVE.clear();
			BOLTS.clear();
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (passing || amount <= 0.0F || !(entity instanceof ServerPlayer player)) {
				return true;
			}
			Live live = LIVE.get(player.getUUID());
			if (live == null || !live.braced || !player.isAlive()) {
				return true;
			}
			if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING)
				|| source.getEntity() == player) {
				return true;
			}
			if (source.getEntity() != null) {
				live.attacker = source.getEntity().getUUID();
				live.attackedAt = player.level().getGameTime();
			}
			if (source.getDirectEntity() instanceof Projectile bolt) {
				catchBolt(player, live, bolt, amount);
				return false;
			}
			float soaked = amount * SOAK_SHARE;
			fill(live, soaked * STORE_PER_DAMAGE);
			RunningHot.add(player, soaked * HEAT_PER_SOAK);
			player.level().sendParticles(MagicFx.spark(COLOR, 0.8F), player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.4, 0.5, 0.4, 0.06);
			if (amount - soaked <= 0.0F) {
				return false;
			}
			passing = true;
			try {
				player.hurtServer(player.level(), source, amount - soaked);
			} finally {
				passing = false;
			}
			return false;
		});
	}

	public static int store(net.minecraft.world.entity.player.Player player) {
		Integer value = player.getAttached(STORE);
		return value == null ? 0 : value;
	}

	private static void fill(Live live, float amount) {
		live.store = Math.min(MAX, live.store + amount);
	}

	private static boolean standing(ServerPlayer player) {
		return player.isShiftKeyDown() && player.onGround() && player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()
			&& !EmberKindle.kindling(player);
	}

	private static void tick(ServerPlayer player, Live live) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Vec3 moved = player.position().subtract(live.lastPos);
		live.lastPos = player.position();
		boolean still = standing(player) && moved.horizontalDistanceSqr() < 0.0004;
		live.still = still ? live.still + 1 : 0;
		boolean braced = live.still >= BRACE_TICKS;
		if (braced) {
			live.lastBraced = now;
			if (!live.braced) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.8F, 0.6F);
			}
			if (player.tickCount % 5 == 0) {
				MagicFx.orb(level, player.position().add(0.0, 1.0, 0.0), 1.2F, COLOR, 6);
			}
		} else if (live.braced && live.store >= RETURN_MIN && !player.isShiftKeyDown()) {
			release(player, live);
		} else if (now - live.lastBraced > LEAK_AFTER && live.store > 0.0F && player.tickCount % 20 == 0) {
			live.store = Math.max(0.0F, live.store - LEAK);
		}
		live.braced = braced;
		int shown = Math.round(live.store);
		if (shown != live.shown) {
			live.shown = shown;
			if (shown <= 0) {
				player.removeAttached(STORE);
			} else {
				player.setAttached(STORE, shown);
			}
		}
	}

	/** A bolt hit the braced user: caught, and sent back stronger along the look. */
	private static void catchBolt(ServerPlayer player, Live live, Projectile bolt, float amount) {
		ServerLevel level = player.level();
		Vec3 at = bolt.position();
		bolt.discard();
		fill(live, Math.max(CATCH_STORE_MIN, amount * STORE_PER_DAMAGE * 0.5F));
		RunningHot.add(player, amount * HEAT_PER_SOAK);
		MagicFx.blast(level, at, 1.2F, WHITE);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2F, 1.4F);
		Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(0.9)).subtract(0.0, 0.3, 0.0);
		BOLTS.add(new Bolt(level, player, from, player.getLookAngle().scale(BOLT_SPEED), Math.max(CATCH_MIN, amount * CATCH_MULT)));
	}

	/** One tick of a returned bolt's flight; true once it has burst. */
	private static boolean step(Bolt bolt) {
		ServerLevel level = bolt.level;
		bolt.age++;
		if (bolt.owner.level() != level || bolt.age > BOLT_LIFE) {
			return true;
		}
		Vec3 from = bolt.pos;
		Vec3 to = from.add(bolt.velocity);
		HitResult clip = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, bolt.owner));
		Vec3 end = clip.getType() == HitResult.Type.MISS ? to : clip.getLocation();
		boolean hit = clip.getType() != HitResult.Type.MISS;
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(0.7), e -> FireKit.foe(bolt.owner, e))) {
			if (living.getBoundingBox().inflate(0.4).clip(from, end).isPresent()) {
				hit = true;
				break;
			}
		}
		MagicFx.beam(level, from, end, 0.18F, COLOR, 3);
		if (bolt.age % 2 == 0) {
			MagicFx.orb(level, end, 0.6F, WHITE, 4);
		}
		bolt.pos = end;
		if (!hit) {
			return false;
		}
		MagicFx.blast(level, end, (float)BOLT_RADIUS, COLOR);
		level.playSound(null, end.x, end.y, end.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0F, 1.3F);
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(end, end).inflate(BOLT_RADIUS), e -> FireKit.foe(bolt.owner, e))) {
			FireKit.hurt(bolt.owner, living, bolt.damage);
			Flames.touch(level, living, BOLT_BURN, bolt.owner);
		}
		return true;
	}

	/** The store goes out in a blast round the user. */
	private static void release(ServerPlayer player, Live live) {
		ServerLevel level = player.level();
		float share = live.store / MAX;
		live.store = 0.0F;
		double reach = RETURN_REACH + RETURN_EXTRA * share;
		Vec3 at = player.position().add(0.0, 0.8, 0.0);
		MagicFx.blast(level, at, (float)reach, WHITE);
		MagicFx.ring(level, player.position(), (float)(reach * 1.5), COLOR);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2F, 1.0F);
		float damage = RETURN_DAMAGE * share;
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach), e -> FireKit.foe(player, e))) {
			FireKit.hurt(player, living, damage);
			Flames.touch(level, living, RETURN_BURN, player);
			Vec3 away = living.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
			if (away.lengthSqr() > 1.0E-4) {
				living.push(away.normalize().scale(RETURN_PUSH * (0.5 + share)).add(0.0, 0.25, 0.0));
				living.syncVelocity = true;
			}
		}
		if (live.attacker != null && level.getGameTime() - live.attackedAt < 200 && level.getEntity(live.attacker) instanceof LivingEntity last
			&& FireKit.foe(player, last) && last.distanceTo(player) > reach && last.distanceTo(player) <= LAST_ATTACKER_RANGE) {
			Vec3 target = last.getBoundingBox().getCenter();
			MagicFx.beam(level, at, target, 0.2F, COLOR, 6);
			FireKit.hurt(player, last, damage * LAST_ATTACKER_SHARE);
			Flames.touch(level, last, RETURN_BURN, player);
		}
	}
}
