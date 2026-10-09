package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A symbiote's grabbing tendrils, on the swing keys while the suit is on: R (right) and G (left). Held on a creature, an
 * item, a corpse or another player, the tendril snatches it and holds it up in front of you (everyone sees the cords);
 * let go and it's hurled where you look, hard enough to hurt when it hits. Aimed at nothing grabbable, the keys swing
 * on the tendrils instead (a short-range swing; see {@link WebSymbiote#swingScale}). The radial menu's Tendril Grab
 * snatches from further and hurls on its own after a moment.
 */
public final class WebTendrils {
	/** Reach of a grab on the keys (and of the radial grab). */
	public static final double GRAB_RANGE = 9.0;
	public static final double RADIAL_GRAB_RANGE = 20.0;
	/** Where it's held: this far ahead, this far to its side, this far below the eyes. */
	static final double HOLD_FORWARD = 2.1, HOLD_SIDE = 0.8, HOLD_DROP = 0.35;
	static final double HOLD_STEER = 0.45, HOLD_MAX_SPEED = 1.4;
	/** Longest it can be held, and how long the radial grab holds before it throws by itself. */
	static final int MAX_HOLD_TICKS = 300;
	static final int RADIAL_HOLD_TICKS = 50;
	/** The throw: speed along the look, plus a lift; and the impact damage of a thrown body hitting something. */
	static final double THROW_SPEED = 2.4, THROW_LIFT = 0.25;
	static final float IMPACT_DAMAGE_MIN = 4.0F, IMPACT_DAMAGE_PER_SPEED = 6.0F;
	static final int THROWN_TICKS = 40;

	private static final Identifier HOLD_GRAVITY = JaymosPowers.id("web_tendril_hold");

	/** What each tendril holds (entity ids, -1 for nothing): synced to everyone, who draw the cords. */
	public record Grips(int right, int left) {
		public static final Grips NONE = new Grips(-1, -1);
		public static final StreamCodec<ByteBuf, Grips> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Grips::right, ByteBufCodecs.VAR_INT, Grips::left, Grips::new);

		public int get(int hand) {
			return hand == WebNet.OFF ? left : right;
		}

		Grips with(int hand, int id) {
			return hand == WebNet.OFF ? new Grips(right, id) : new Grips(id, left);
		}

		boolean empty() {
			return right < 0 && left < 0;
		}
	}

	public static final AttachmentType<Grips> GRIPS = AttachmentRegistry.create(JaymosPowers.id("web_tendril_grips"),
		builder -> builder.syncWith(Grips.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** Client → server: a tendril key on something (grab it), or let go (entity -1: throw). */
	public record Grab(int hand, int entityId) implements CustomPacketPayload {
		public static final Type<Grab> TYPE = new Type<>(JaymosPowers.id("web_tendril_grab"));
		public static final StreamCodec<ByteBuf, Grab> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Grab::hand, ByteBufCodecs.VAR_INT, Grab::entityId, Grab::new);

		@Override
		public Type<Grab> type() {
			return TYPE;
		}
	}

	private record Hold(UUID player, int hand, long since, long throwAt) {
	}

	private record Thrown(ResourceKey<Level> dimension, UUID entity, UUID thrower, long until) {
	}

	private static final java.util.Map<UUID, Hold[]> HOLDS = new java.util.HashMap<>();
	private static final List<Thrown> THROWN = new ArrayList<>();

	private WebTendrils() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Grab.TYPE, Grab.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Grab.TYPE, (payload, ctx) -> {
			ServerPlayer player = ctx.player();
			int hand = payload.hand() == WebNet.OFF ? WebNet.OFF : WebNet.MAIN;
			if (payload.entityId() < 0) {
				release(player, hand, true);
			} else if (WebSymbiote.suitShown(player) && player.isAlive() && !player.isSpectator()) {
				Entity target = player.level().getEntity(payload.entityId());
				if (target != null && target.distanceTo(player) <= GRAB_RANGE + 2.5) {
					grab(player, hand, target, -1L);
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(WebTendrils::serverTick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				releaseAll(player, false);
			}
			HOLDS.clear();
			THROWN.clear();
		});
	}

	/** Anything a tendril can take: creatures, players (not in creative), items, corpses. Not yourself. */
	public static boolean grabbable(Player player, Entity entity) {
		return entity != player && entity.isAlive() && !entity.isSpectator() && !(entity instanceof ArmorStand)
			&& !(entity instanceof Player p && p.isCreative()) && entity.getVehicle() == null && !entity.isVehicle()
			&& (entity instanceof LivingEntity || entity instanceof net.minecraft.world.entity.item.ItemEntity
				|| com.jaymospowers.power.gravity.GravityPower.isCorpse(entity));
	}

	/** Instinct: a tendril lashes out at an attacker, snatches it and flings it away a moment later. */
	static void snatch(ServerPlayer player, Entity target) {
		int hand = player.getAttachedOrElse(GRIPS, Grips.NONE).left() < 0 ? WebNet.OFF : WebNet.MAIN;
		release(player, hand, false);
		grab(player, hand, target, player.level().getGameTime() + INSTINCT_HOLD_TICKS);
	}

	/** How long an instinctive snatch holds before it flings. */
	static final int INSTINCT_HOLD_TICKS = 12;

	/** The radial menu's Tendril Grab: the right tendril snatches from afar and hurls it on its own after a moment. */
	static boolean radialGrab(ServerPlayer player) {
		Entity target = WebPower.entityInSight(player, RADIAL_GRAB_RANGE);
		if (target == null || !grabbable(player, target)) {
			return false;
		}
		release(player, WebNet.MAIN, false);
		grab(player, WebNet.MAIN, target, player.level().getGameTime() + RADIAL_HOLD_TICKS);
		return true;
	}

	private static void grab(ServerPlayer player, int hand, Entity target, long throwAt) {
		if (!grabbable(player, target) || heldBySomeone(target)) {
			return;
		}
		release(player, hand, false);
		weightless(target, true);
		Hold[] holds = HOLDS.computeIfAbsent(player.getUUID(), k -> new Hold[2]);
		holds[hand] = new Hold(player.getUUID(), hand, player.level().getGameTime(), throwAt);
		Grips grips = player.getAttachedOrElse(GRIPS, Grips.NONE);
		player.setAttached(GRIPS, grips.with(hand, target.getId()));
		ServerLevel level = player.level();
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SLIME_ATTACK, SoundSource.PLAYERS, 1.0F, 0.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 0.8F, 0.7F);
	}

	private static boolean heldBySomeone(Entity target) {
		if (!(target.level() instanceof ServerLevel level)) {
			return false;
		}
		for (ServerPlayer other : level.players()) {
			Grips grips = other.getAttached(GRIPS);
			if (grips != null && (grips.right() == target.getId() || grips.left() == target.getId())) {
				return true;
			}
		}
		return false;
	}

	/** Lets go of what a tendril holds: thrown where you look ({@code hurl}), or just dropped. */
	static void release(ServerPlayer player, int hand, boolean hurl) {
		Grips grips = player.getAttached(GRIPS);
		Hold[] holds = HOLDS.get(player.getUUID());
		if (holds != null) {
			holds[hand] = null;
		}
		if (grips == null || grips.get(hand) < 0) {
			return;
		}
		Entity target = player.level().getEntity(grips.get(hand));
		Grips next = grips.with(hand, -1);
		if (next.empty()) {
			player.removeAttached(GRIPS);
		} else {
			player.setAttached(GRIPS, next);
		}
		if (target == null) {
			return;
		}
		weightless(target, false);
		if (hurl) {
			Vec3 v = player.getLookAngle().scale(THROW_SPEED).add(0.0, THROW_LIFT, 0.0);
			target.setDeltaMovement(v);
			target.needsSync = true;
			target.syncVelocity = true;
			if (target instanceof ServerPlayer other) {
				ServerPlayNetworking.send(other, new WebNet.Nudge(v));
			}
			THROWN.removeIf(t -> t.entity().equals(target.getUUID()));
			THROWN.add(new Thrown(player.level().dimension(), target.getUUID(), player.getUUID(), player.level().getGameTime() + THROWN_TICKS));
			ServerLevel level = player.level();
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 0.5F);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.0F, 0.5F);
		}
	}

	static void releaseAll(ServerPlayer player, boolean hurl) {
		release(player, WebNet.MAIN, hurl);
		release(player, WebNet.OFF, hurl);
		HOLDS.remove(player.getUUID());
	}

	/** Held: no weight (so a held player isn't kicked for floating, and nothing sags). */
	private static void weightless(Entity entity, boolean on) {
		if (entity instanceof LivingEntity living) {
			AttributeInstance gravity = living.getAttribute(Attributes.GRAVITY);
			if (gravity != null) {
				if (on) {
					gravity.addOrUpdateTransientModifier(new AttributeModifier(HOLD_GRAVITY, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				} else {
					gravity.removeModifier(HOLD_GRAVITY);
				}
			}
		} else {
			entity.setNoGravity(on);
		}
	}

	/** Every tick, per web slinger: whatever the tendrils hold hangs where it's held (and the radial grab throws). */
	static void tick(ServerPlayer player) {
		Grips grips = player.getAttached(GRIPS);
		if (grips == null) {
			return;
		}
		if (!player.isAlive() || player.isSpectator() || !WebSymbiote.suitShown(player) && !WebSymbiote.isToggled(player, WebSymbioteState.ARMOR)) {
			releaseAll(player, false);
			return;
		}
		Hold[] holds = HOLDS.computeIfAbsent(player.getUUID(), k -> new Hold[2]);
		long now = player.level().getGameTime();
		for (int hand = WebNet.MAIN; hand <= WebNet.OFF; hand++) {
			int id = grips.get(hand);
			if (id < 0) {
				continue;
			}
			Entity target = player.level().getEntity(id);
			Hold hold = holds[hand];
			if (target == null || !target.isAlive() || target.level() != player.level() || target.distanceTo(player) > GRAB_RANGE * 2.0
				|| hold != null && now - hold.since() > MAX_HOLD_TICKS) {
				release(player, hand, false);
				continue;
			}
			if (hold != null && hold.throwAt() > 0 && now >= hold.throwAt()) {
				release(player, hand, true);
				continue;
			}
			Vec3 look = player.getLookAngle();
			Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
			right = right.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
			double side = hand == WebNet.OFF ? -1.0 : 1.0;
			Vec3 point = player.getEyePosition().add(look.scale(HOLD_FORWARD + target.getBbWidth() * 0.5)).add(right.scale(side * HOLD_SIDE))
				.add(0.0, -HOLD_DROP, 0.0);
			Vec3 to = point.subtract(target.getBoundingBox().getCenter());
			Vec3 v = to.scale(HOLD_STEER);
			double speed = v.length();
			if (speed > HOLD_MAX_SPEED) {
				v = v.scale(HOLD_MAX_SPEED / speed);
			}
			target.setDeltaMovement(v);
			target.needsSync = true;
			target.syncVelocity = true;
			target.resetFallDistance();
			if (target instanceof Mob mob) {
				mob.getNavigation().stop();
			}
			if (target instanceof ServerPlayer other && now % 2 == 0) {
				ServerPlayNetworking.send(other, new WebNet.Nudge(v));
			}
		}
	}

	/** Thrown bodies: the first hard knock (a wall, the ground) hurts by how fast they were going. */
	private static void serverTick(MinecraftServer server) {
		if (THROWN.isEmpty()) {
			return;
		}
		for (Iterator<Thrown> it = THROWN.iterator(); it.hasNext(); ) {
			Thrown thrown = it.next();
			ServerLevel level = server.getLevel(thrown.dimension());
			Entity entity = level == null ? null : level.getEntity(thrown.entity());
			if (!(entity instanceof LivingEntity living) || !living.isAlive() || level.getGameTime() >= thrown.until()) {
				it.remove();
				continue;
			}
			double speed = living.getDeltaMovement().length();
			if (living.horizontalCollision || living.verticalCollision && living.getDeltaMovement().y <= 0.0 && level.getGameTime() + THROWN_TICKS - thrown.until() > 3) {
				it.remove();
				float damage = (float)Math.max(IMPACT_DAMAGE_MIN, speed * IMPACT_DAMAGE_PER_SPEED);
				ServerPlayer thrower = server.getPlayerList().getPlayer(thrown.thrower());
				living.hurtServer(level, thrower != null ? level.damageSources().playerAttack(thrower) : level.damageSources().flyIntoWall(), damage);
				level.sendParticles(ParticleTypes.POOF, living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(), 8, 0.3, 0.3, 0.3, 0.05);
				level.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.PLAYER_BIG_FALL, SoundSource.PLAYERS, 1.0F, 0.8F);
			}
		}
	}

	static void forget(ServerPlayer player) {
		HOLDS.remove(player.getUUID());
	}

	static double clampSide(double v) {
		return Mth.clamp(v, -1.0, 1.0);
	}
}
