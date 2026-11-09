package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.Downed;
import io.netty.buffer.ByteBuf;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "certain small animals should ride on you when carried, freeing your hands, like the butterfly or parrot or cat
 * or cricket." Sneak + right-click one of {@link #RIDES_ON_SHOULDER} (a parrot, a cat, a butterfly, a cricket, a mouse)
 * and it climbs onto you instead of into your arms: perched on your left shoulder, or on your head if it's one of
 * {@link #RIDES_ON_HEAD}, or (as wide as a cat, {@link #DRAPE_WIDTH}) draped across the back of your shoulders. Your
 * hands stay free: it isn't a carry ({@link Carry}), so you use items and fight as ever. It stays until you let it down
 * (sneak + use with an empty hand, at the air or a block) or a hit of {@link #SHAKE_OFF_DAMAGE} or more knocks it off.
 * <p>
 * It really rides you (a passenger of the player, so every client moves it with you and draws it for everyone): where
 * it sits is {@link #ridingPosition} (the {@code PerchRidingMixin}s), and in your own first-person view it's drawn a
 * little forward so you see it at the edge of your view ({@link #setFirstPerson}).
 */
public final class Perch {
	/** Small animals that ride on your shoulder (or head, or draped) when picked up, instead of in your arms. */
	public static final TagKey<EntityType<?>> RIDES_ON_SHOULDER = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("rides_on_shoulder"));
	/** The tiniest of them sit on your head instead (a cricket, a mouse). */
	public static final TagKey<EntityType<?>> RIDES_ON_HEAD = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("rides_on_head"));

	/** Where it sits. */
	public static final int SHOULDER = 0, HEAD = 1, DRAPED = 2;
	/** This wide (blocks) or wider, it's draped across your shoulders rather than sitting on one (a cat is 0.6). */
	public static final float DRAPE_WIDTH = 0.5F;
	/** A hit doing this much damage (half-hearts, after armour) knocks it off. */
	public static final float SHAKE_OFF_DAMAGE = 6.0F;
	/** Let down: not picked up again for this long (ticks). */
	static final int REPERCH_TICKS = 20;
	/** Let down: a little hop ahead of you (b/t), and up. */
	static final double HOP_AHEAD = 0.15, HOP_UP = 0.2;

	/** Where it sits, as shares of your height (so it follows a crouch), and out to the side / forward (blocks). */
	static final double SHOULDER_SIDE = 0.34, SHOULDER_UP = 0.78, SHOULDER_FORWARD = 0.0;
	static final double HEAD_UP = 1.02, HEAD_FORWARD = 0.0;
	static final double DRAPE_UP = 0.74, DRAPE_FORWARD = -0.14;
	/**
	 * In your own first-person view: brought this far forward (blocks), and for a head rider this far below the top of
	 * your head, so it sits at the edge of what you see (a parrot at the lower left, a cricket at the brim). A draped cat
	 * comes round onto your left shoulder.
	 */
	static final double FP_FORWARD = 0.32, FP_HEAD_DROP = 0.2, FP_DRAPE_SIDE = 0.36;

	/** On a perched animal (synced to everyone, not saved): where it sits ({@link #SHOULDER}, {@link #HEAD}, {@link #DRAPED}). */
	public static final AttachmentType<Integer> PERCHED = AttachmentRegistry.create(JaymosPowers.id("perched"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** On an animal let down: not before this game time does it perch again (not synced). */
	static final AttachmentType<Long> DOWN_UNTIL = AttachmentRegistry.create(JaymosPowers.id("perch_down_until"));

	/** Client → server: let it down. */
	public record Off() implements CustomPacketPayload {
		public static final Type<Off> TYPE = new Type<>(JaymosPowers.id("perch_off"));
		public static final StreamCodec<ByteBuf, Off> CODEC = StreamCodec.unit(new Off());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server: every animal perched now (to notice one that stopped riding some other way). */
	private static final java.util.Set<Entity> PERCHERS = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

	/** Client only: whether a player is the camera in first person (set by the client, so this needn't touch client classes). */
	private static volatile @Nullable Predicate<Player> firstPerson;

	private Perch() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Off.TYPE, Off.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Off.TYPE, (payload, ctx) -> {
			Entity rider = riderOf(ctx.player());
			if (rider != null) {
				letDown(ctx.player(), rider);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && taken >= SHAKE_OFF_DAMAGE) {
				Entity rider = riderOf(player);
				if (rider != null) {
					letDown(player, rider);
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Perch::tick);
	}

	// ---- queries (both sides)

	/** Whether this kind of animal rides on you rather than in your arms. */
	public static boolean rides(Entity entity) {
		return entity instanceof LivingEntity && entity.getType().builtInRegistryHolder().is(RIDES_ON_SHOULDER);
	}

	/** Where {@code entity} is perched on a player, or -1 if it isn't. */
	public static int spot(Entity entity) {
		Integer spot = entity.getAttached(PERCHED);
		return spot != null && entity.getVehicle() instanceof Player ? spot : -1;
	}

	/** Whether {@code entity} is perched on a player. */
	public static boolean perched(Entity entity) {
		return spot(entity) >= 0;
	}

	/** What's perched on {@code player}, if anything. */
	public static @Nullable Entity riderOf(Player player) {
		for (Entity passenger : player.getPassengers()) {
			if (passenger.getAttached(PERCHED) != null) {
				return passenger;
			}
		}
		return null;
	}

	/** Client: how this side tells whether a player is its own camera in first person. */
	public static void setFirstPerson(@Nullable Predicate<Player> hook) {
		firstPerson = hook;
	}

	/** Where a perched animal's feet go on {@code player}, this tick. */
	public static Vec3 ridingPosition(Player player, Entity rider, int spot) {
		double height = player.getBbHeight();
		float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
		Vec3 ahead = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
		Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
		Predicate<Player> fp = firstPerson;
		boolean own = fp != null && fp.test(player);
		double side, up, forward;
		switch (spot) {
			case HEAD -> {
				side = 0.0;
				up = height * HEAD_UP - (own ? FP_HEAD_DROP : 0.0);
				forward = HEAD_FORWARD + (own ? FP_FORWARD : 0.0);
			}
			case DRAPED -> {
				side = own ? -FP_DRAPE_SIDE : 0.0;
				up = height * DRAPE_UP;
				forward = own ? FP_FORWARD : DRAPE_FORWARD;
			}
			default -> {
				// the left shoulder (the right hand is the busy one)
				side = -SHOULDER_SIDE;
				up = height * SHOULDER_UP;
				forward = SHOULDER_FORWARD + (own ? FP_FORWARD : 0.0);
			}
		}
		return player.position().add(right.scale(side)).add(ahead.scale(forward)).add(0.0, up, 0.0);
	}

	/** After it's put in place: it faces the way you do (across you, draped). */
	public static void face(Player player, Entity rider, int spot) {
		float yaw = player.yBodyRot + (spot == DRAPED ? 90.0F : 0.0F);
		rider.setYRot(yaw);
		rider.yRotO = yaw;
		if (rider instanceof LivingEntity living) {
			living.yBodyRot = yaw;
			living.yBodyRotO = yaw;
			if (spot == DRAPED) {
				living.setYHeadRot(yaw);
			}
		}
	}

	// ---- perching

	/** Sneak + use on a small animal of {@link #RIDES_ON_SHOULDER}: it climbs on. True if it did (or will, client side). */
	public static boolean tryPerch(Player player, Entity target) {
		if (!rides(target) || !target.isAlive() || player.isSpectator() || player.isPassenger() || Carry.isCarried(player)
			|| Downed.isDowned(player) || riderOf(player) != null || target.isPassenger() || target.isVehicle()
			|| Carry.isCarried(target) || Carry.isCarrying(target)) {
			return false;
		}
		if (!(player.level() instanceof ServerLevel level)) {
			return true;
		}
		Long until = target.getAttached(DOWN_UNTIL);
		if (until != null && level.getGameTime() < until) {
			return false;
		}
		int spot = target.getType().builtInRegistryHolder().is(RIDES_ON_HEAD) ? HEAD
			: target.getBbWidth() >= DRAPE_WIDTH ? DRAPED : SHOULDER;
		target.setAttached(PERCHED, spot);
		if (!target.startRiding(player, true, true)) {
			target.removeAttached(PERCHED);
			return false;
		}
		PERCHERS.add(target);
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.4F, 1.6F);
		return true;
	}

	/** It hops down, a little ahead of you. */
	public static void letDown(ServerPlayer player, Entity rider) {
		rider.stopRiding();
		rider.removeAttached(PERCHED);
		PERCHERS.remove(rider);
		rider.setAttached(DOWN_UNTIL, player.level().getGameTime() + REPERCH_TICKS);
		float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
		Vec3 ahead = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
		rider.setDeltaMovement(ahead.scale(HOP_AHEAD).add(0.0, HOP_UP, 0.0));
		rider.syncVelocity = true;
		rider.resetFallDistance();
	}

	/** Anything that stopped riding some other way (a portal, the player gone) is no longer perched. */
	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Entity rider = riderOf(player);
			if (rider != null && (!player.isAlive() || player.isSpectator() || player.isSleeping() || Carry.isCarried(player))) {
				letDown(player, rider);
			}
		}
		if (!PERCHERS.isEmpty()) {
			for (Entity entity : java.util.List.copyOf(PERCHERS)) {
				if (entity.isRemoved() || !(entity.getVehicle() instanceof Player)) {
					entity.removeAttached(PERCHED);
					PERCHERS.remove(entity);
				}
			}
		}
	}
}
