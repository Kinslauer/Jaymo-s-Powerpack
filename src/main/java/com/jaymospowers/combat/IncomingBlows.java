package com.jaymospowers.combat;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.LockOn;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Andy (2026-11-05): "is it possible to add an indicator for pvp? as of right now it is hard to parry or defend at all".
 * A player's swing gave no warning: monsters glint as they wind up ({@link AttackTells}), but a player's Better Combat
 * swing landed on its upswing with nothing but the animation to read. Now a blow coming at you is told to you alone:
 * <ul>
 * <li>A player's swing ({@code compat/mixin/IncomingSwingMixin}, as Better Combat's swing starts): everyone it's aimed at
 * (the one the swinger is locked onto, or anyone in the weapon's reach inside {@link #CONE} degrees of their look) sees
 * the white star at the swinger's weapon hand and hears the warning bell. Nobody else does, so PvE swings don't glint
 * at the whole server.</li>
 * <li>Every blow coming at you, a player's or a mob's ({@link AttackTells#windUp}, Kingdoms' fighters and gunners
 * through its {@code PowersCompat}), shows a cue round your crosshair ({@code IncomingBlowsClient}): an arc on the side
 * the blow comes from, closing on the crosshair as the blow comes in, white (parry it) or red (too heavy: block or dodge),
 * and gold in its last stretch: a guard raised while it's gold is a perfect parry.</li>
 * </ul>
 * No chat text, no FOV change.
 */
public final class IncomingBlows {
	/** A player's swing is aimed at someone this far round the swinger's look (degrees each side). */
	public static final double CONE = 50.0;
	/** Reach slack (blocks) past the weapon's range, for a foe stepping in. */
	public static final double REACH_SLACK = 1.5;
	/** The cue's length is clamped to this (ticks). */
	public static final int MIN_TICKS = 3, MAX_TICKS = 30;

	/**
	 * Server → the one a blow is coming at: from {@code attacker} (entity id), landing in {@code ticks}, too {@code heavy}
	 * to parry; {@code tell}: the client draws the star and rings the bell itself (a player's swing, whose tell nobody else
	 * gets), otherwise the server already did (a mob's).
	 */
	public record Incoming(int attacker, int ticks, boolean heavy, boolean tell) implements CustomPacketPayload {
		public static final Type<Incoming> TYPE = new Type<>(JaymosPowers.id("incoming_blow"));
		public static final StreamCodec<ByteBuf, Incoming> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Incoming::attacker,
			ByteBufCodecs.VAR_INT, Incoming::ticks,
			ByteBufCodecs.BOOL, Incoming::heavy,
			ByteBufCodecs.BOOL, Incoming::tell,
			Incoming::new);

		@Override
		public Type<Incoming> type() {
			return TYPE;
		}
	}

	private IncomingBlows() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Incoming.TYPE, Incoming.CODEC);
	}

	/**
	 * A player's swing begins (Better Combat's attack animation): {@code upswing} ticks to the blow, {@code range} the
	 * weapon's reach (0 or less: the player's own). Told to every player it's aimed at.
	 */
	public static void swing(ServerPlayer attacker, int upswing, double range) {
		if (!(attacker.level() instanceof ServerLevel level) || attacker.isSpectator() || !attacker.isAlive()) {
			return;
		}
		double reach = (range > 0.0 ? range : attacker.entityInteractionRange()) + REACH_SLACK;
		Vec3 eye = attacker.getEyePosition();
		Vec3 look = attacker.getLookAngle();
		LivingEntity locked = LockOn.target(attacker);
		double cos = Math.cos(Math.toRadians(CONE));
		for (ServerPlayer target : level.players()) {
			if (target == attacker || target.isSpectator() || !target.isAlive()) {
				continue;
			}
			if (target.getBoundingBox().distanceToSqr(eye) > reach * reach) {
				continue;
			}
			if (target != locked) {
				Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
				if (to.lengthSqr() > 1.0E-4 && to.normalize().dot(look) < cos) {
					continue;
				}
			}
			send(target, attacker, upswing, false, true);
		}
	}

	/**
	 * A blow from {@code attacker} at {@code target}, landing in {@code ticks}, its tell already shown by the caller. Public
	 * for Kingdoms (its {@code PowersCompat}): its fighters' swings and gunners' aims.
	 */
	public static void aimed(LivingEntity attacker, LivingEntity target, int ticks, boolean heavy) {
		if (target instanceof ServerPlayer player && player != attacker) {
			send(player, attacker, ticks, heavy, false);
			// ((0.21.20) for a parry to turn you toward, Crowds)
			Crowds.incoming(attacker, player, ticks);
		}
	}

	private static void send(ServerPlayer target, LivingEntity attacker, int ticks, boolean heavy, boolean tell) {
		if (ServerPlayNetworking.canSend(target, Incoming.TYPE)) {
			ServerPlayNetworking.send(target, new Incoming(attacker.getId(), Mth.clamp(ticks, MIN_TICKS, MAX_TICKS), heavy, tell));
		}
	}
}
