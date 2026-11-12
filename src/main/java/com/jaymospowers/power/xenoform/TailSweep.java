package com.jaymospowers.power.xenoform;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.darktide.DarktideStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

/**
 * A tail sweep: the tail whipped round the body, and everyone within {@link #RADIUS} blocks is knocked senseless for a
 * moment (the stun Darktide uses, {@link DarktideStatus#stun}). Shared by the tailed aliens (Prowl on sneak+G; Howl,
 * Slipfin and Gnasher on a tap of Z) and the Warrior-born Z Fighter whose tail is out (sneak+Z in the air). The body
 * spins once while it plays: every client reads {@link #SWEEP} (the game time it began) to pose it.
 */
public final class TailSweep {
	// ---- tuning ----
	/** Who is caught: everything this close (blocks). */
	public static final double RADIUS = 2.0;
	/** How long they are stunned (ticks), and the small push outward and up. */
	public static final int STUN_TICKS = 24;
	public static final double SHOVE_OUT = 0.35, SHOVE_UP = 0.12;
	/** Ticks between sweeps, and how long the spin plays (the client reads this too). */
	public static final int COOLDOWN = 120, SPIN_TICKS = 12;

	/** The game time the last sweep began, for every client's pose. Not saved. */
	public static final AttachmentType<Long> SWEEP = AttachmentRegistry.create(JaymosPowers.id("tail_sweep"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	/** When each player may sweep again (game time). */
	private static final Map<UUID, Long> NEXT = new HashMap<>();

	private TailSweep() {
	}

	public static boolean ready(ServerPlayer player) {
		return NEXT.getOrDefault(player.getUUID(), 0L) <= player.level().getGameTime();
	}

	/**
	 * Whips the tail round {@code player} (in {@code color}): everyone near is stunned. False (nothing happens) while it
	 * recharges.
	 */
	public static boolean sweep(ServerPlayer player, int color) {
		if (!ready(player)) {
			return false;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		NEXT.put(player.getUUID(), now + COOLDOWN);
		player.setAttached(SWEEP, now);
		List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RADIUS),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && !e.isPassengerOfSameVehicle(player)
				&& !(e instanceof OwnableEntity pet && pet.getOwner() == player) && !player.isAlliedTo(e) && e.distanceToSqr(player) <= RADIUS * RADIUS);
		for (LivingEntity e : caught) {
			DarktideStatus.stun(e, STUN_TICKS);
			Vec3 push = e.position().subtract(player.position()).horizontal();
			push = push.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : push.normalize();
			e.setDeltaMovement(e.getDeltaMovement().add(push.scale(SHOVE_OUT)).add(0.0, SHOVE_UP, 0.0));
			e.syncVelocity = true;
		}
		Vec3 centre = player.position().add(0.0, player.getBbHeight() * 0.35, 0.0);
		Vec3 forward = Vec3.directionFromRotation(0.0F, player.getYRot());
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		MagicFx.swing(level, centre, forward, side, RADIUS * 0.85, 340.0, 0.12F, color, 8);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.7F);
		return true;
	}

	/** Forgets a player's recharge (they left). */
	public static void forget(ServerPlayer player) {
		NEXT.remove(player.getUUID());
	}
}
