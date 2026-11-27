package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.wounds.Downed;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Feeding lunge (Legends patch 4, {@code sy_lunge}): in a surge (the symbiote has lent you everything it has,
 * {@link WebSymbiote#empowered}, or Spike Burst has just gone off), click at a foe you have not hit yet and the symbiote
 * throws you at it ({@link #RANGE}), a blow where you land. It is a bite on our satiety, but the symbiote never feeds on
 * anything living: the bite draws nothing, and only when a foe the lunge struck dies within {@link #FEED_TICKS} does the
 * symbiote take its meal from the body (a little satiety and {@link #HEAL} health). Each foe counts once per surge. It ends
 * on death, downed, disconnect, a change of world and power loss ({@link WebPose#LUNGE} draws the body).
 */
public final class WebLunge {
	/** How far the symbiote throws you (blocks), the aim it forgives (cosine floor of the angle off the look), the lunge's top speed and longest flight (ticks). */
	static final double RANGE = 14.0, AIM = 0.97, SPEED = 1.7;
	static final int FLIGHT_TICKS = 14, COOLDOWN = 24;
	/** The blow, how close counts as landing (blocks) and the knock it carries. */
	static final float DAMAGE = 6.0F;
	static final double LAND = 2.3, KNOCK = 0.5;
	/** A surge: how long Spike Burst's lasts after it goes off (ticks). */
	static final int SPIKE_SURGE_TICKS = 160;
	/** The meal from a body: how long after the blow the foe may die for it to count, the health and the satiety (and calm) it brings. */
	static final int FEED_TICKS = 200, FEED_SATIETY = 5, FEED_CALM = 2;
	static final float HEAL = 3.0F;

	/** The click (sent when the attack key goes down in a surge). */
	public record Click() implements CustomPacketPayload {
		public static final Type<Click> TYPE = new Type<>(JaymosPowers.id("web_lunge"));
		public static final StreamCodec<ByteBuf, Click> CODEC = StreamCodec.unit(new Click());

		@Override
		public Type<Click> type() {
			return TYPE;
		}
	}

	private static final class Live {
		final ServerLevel level;
		final long start;
		final int target;

		Live(ServerLevel level, long start, int target) {
			this.level = level;
			this.start = start;
			this.target = target;
		}
	}

	private record Meal(UUID owner, long until) {
	}

	private static final Map<UUID, Live> LIVE = new HashMap<>();
	private static final Map<UUID, Long> READY = new HashMap<>();
	/** When Spike Burst last went off for each player (game time). */
	private static final Map<UUID, Long> SPIKED = new HashMap<>();
	/** The foes already struck in this player's current surge. */
	private static final Map<UUID, Set<UUID>> STRUCK = new HashMap<>();
	/** Foes struck that the symbiote will take a meal of if they die: foe → who struck them. */
	private static final Map<UUID, Meal> MEALS = new HashMap<>();

	private WebLunge() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Click.TYPE, Click.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Click.TYPE, (payload, ctx) -> click(ctx.player()));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (MEALS.isEmpty()) {
				return;
			}
			Meal meal = MEALS.remove(entity.getUUID());
			if (meal == null || entity.level().getGameTime() > meal.until()
				|| !(entity.level().getServer().getPlayerList().getPlayer(meal.owner()) instanceof ServerPlayer owner) || !owner.isAlive()
				|| owner.level() != entity.level() || owner.distanceToSqr(entity) > 24.0 * 24.0 || !WebSymbiote.isBonded(owner)) {
				return;
			}
			// kill, then feed: the meal comes off the body
			owner.heal(HEAL);
			WebSymbiote.feed(owner, FEED_SATIETY, FEED_CALM);
			ServerLevel level = owner.level();
			Vec3 at = entity.getBoundingBox().getCenter();
			MagicFx.stroke(level, at, owner.position().add(0.0, owner.getBbHeight() * 0.6, 0.0), 0.05F, 0x14141C);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 0.8F, 0.7F);
		});
	}

	/** Whether this is a surge right now: lent everything, or Spike Burst just went off. */
	static boolean surge(ServerPlayer player) {
		if (WebSymbiote.empowered(player)) {
			return true;
		}
		long now = player.level().getGameTime();
		WebPose pose = player.getAttached(WebPower.POSE);
		if (pose != null && pose.kind() == WebPose.SPIKES) {
			SPIKED.put(player.getUUID(), now);
			return true;
		}
		Long at = SPIKED.get(player.getUUID());
		return at != null && now - at < SPIKE_SURGE_TICKS;
	}

	private static boolean able(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && !Downed.isDowned(player) && Powers.has(player, WebPower.INSTANCE)
			&& !Powers.silenced(player) && WebSymbiote.isBonded(player) && WebSymbiote.tendrilKeys(player) && !WebSymbiote.driving(player)
			&& !player.isPassenger() && !player.isInWater() && !player.getAbilities().flying;
	}

	private static void click(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (LIVE.containsKey(player.getUUID()) || !able(player) || !surge(player) || now < READY.getOrDefault(player.getUUID(), 0L)
			|| WebHeldGrab.holding(player) || WebGrind.active(player) || WebStilts.up(player) || WebPower.ragdolled(player)) {
			return;
		}
		Set<UUID> struck = STRUCK.computeIfAbsent(player.getUUID(), k -> new HashSet<>());
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		LivingEntity best = null;
		double bestDot = AIM;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye.add(look.scale(RANGE))).inflate(3.0),
			e -> e != player && e.isAlive() && !e.isSpectator() && !struck.contains(e.getUUID()) && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand)
				&& !(e instanceof net.minecraft.world.entity.player.Player other && other.isCreative()))) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double d = to.length();
			if (d > RANGE || d < 1.0E-3) {
				continue;
			}
			double dot = to.scale(1.0 / d).dot(look);
			if (dot > bestDot && player.hasLineOfSight(e)) {
				best = e;
				bestDot = dot;
			}
		}
		if (best == null) {
			return;
		}
		READY.put(player.getUUID(), now + COOLDOWN);
		LIVE.put(player.getUUID(), new Live(level, now, best.getId()));
		WebPower.pose(player, WebPose.LUNGE, 1);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.0F, 0.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 0.8F, 0.6F);
	}

	/** Thrown at a foe right now: the movement check leaves them be. */
	static boolean flying(ServerPlayer player) {
		return LIVE.containsKey(player.getUUID());
	}

	/** Every tick, per web slinger. */
	static void tick(ServerPlayer player) {
		if (!STRUCK.isEmpty() && STRUCK.containsKey(player.getUUID()) && !surge(player)) {
			// the surge is over: every foe counts as new again
			STRUCK.remove(player.getUUID());
		}
		if (!MEALS.isEmpty() && player.tickCount % 100 == 0) {
			long now = player.level().getGameTime();
			MEALS.values().removeIf(meal -> now > meal.until());
		}
		Live live = LIVE.get(player.getUUID());
		if (live == null) {
			return;
		}
		ServerLevel level = live.level;
		long t = level.getGameTime() - live.start;
		if (!able(player) || level != player.level() || !(level.getEntity(live.target) instanceof LivingEntity foe) || !foe.isAlive()) {
			LIVE.remove(player.getUUID());
			return;
		}
		Vec3 to = foe.getBoundingBox().getCenter().subtract(player.position().add(0.0, player.getBbHeight() * 0.5, 0.0));
		double d = to.length();
		if (d <= LAND + foe.getBbWidth() * 0.5 || t >= FLIGHT_TICKS) {
			LIVE.remove(player.getUUID());
			if (d <= LAND + foe.getBbWidth() * 0.5 + 1.0) {
				land(player, foe, level);
			}
			// the rush is spent: what is left is a fall, not a flight
			WebHaul.push(player, player.getDeltaMovement().scale(0.25));
			return;
		}
		Vec3 aim = to.scale(1.0 / Math.max(d, 1.0E-3));
		WebHaul.push(player, aim.scale(SPEED).add(0.0, 0.08, 0.0));
		player.resetFallDistance();
		if (t % 2 == 0) {
			MagicFx.stroke(level, player.position().add(0.0, player.getBbHeight() * 0.7, 0.0), foe.getBoundingBox().getCenter(), 0.05F, 0x14141C);
		}
	}

	private static void land(ServerPlayer player, LivingEntity foe, ServerLevel level) {
		Vec3 at = foe.getBoundingBox().getCenter();
		foe.hurtServer(level, level.damageSources().playerAttack(player), DAMAGE);
		Vec3 away = at.subtract(player.position());
		Vec3 flat = new Vec3(away.x, 0.0, away.z);
		flat = flat.lengthSqr() < 1.0E-4 ? player.getLookAngle().horizontal() : flat.normalize();
		if (foe.isAlive() && WebHaul.haulable(foe)) {
			WebHaul.push(foe, foe.getDeltaMovement().add(flat.scale(KNOCK)).add(0.0, 0.2, 0.0));
		}
		Set<UUID> struck = STRUCK.computeIfAbsent(player.getUUID(), k -> new HashSet<>());
		if (struck.add(foe.getUUID())) {
			// a new foe is a bite on our satiety: the meal comes only if it dies of this, off the body
			MEALS.put(foe.getUUID(), new Meal(player.getUUID(), level.getGameTime() + FEED_TICKS));
		}
		level.sendParticles(WebSymbiote.dust(1.4F), at.x, at.y, at.z, 20, 0.3, 0.4, 0.3, 0.0);
		MagicFx.blast(level, at, 1.0F, 0x1B1B26);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.1F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PHANTOM_BITE, SoundSource.PLAYERS, 0.8F, 0.6F);
		Shakes.send(player, 0.5F, at);
	}

	static void forget(ServerPlayer player) {
		LIVE.remove(player.getUUID());
		READY.remove(player.getUUID());
		SPIKED.remove(player.getUUID());
		STRUCK.remove(player.getUUID());
		MEALS.values().removeIf(meal -> meal.owner().equals(player.getUUID()));
	}

	static void clearAll() {
		LIVE.clear();
		READY.clear();
		SPIKED.clear();
		STRUCK.clear();
		MEALS.clear();
	}
}
