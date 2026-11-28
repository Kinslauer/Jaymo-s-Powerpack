package com.jaymoskingdoms.sailing;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.PowersCompat;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What a gun or siege engine's hit looks like where it lands (Andy, 2026-11-28: "i especially want better fx for cannon
 * and catapult impacts"; our own take on Creeper's Dislikes' blast looks, which is All Rights Reserved, so none of its code
 * or art). The server sends one small {@link Hit} per hit to the players near it, and each one's client draws the lot
 * ({@code client.ImpactFxClient}): a rising plume of smoke, a fireball flash for shells, a dust cloud rolling out along the
 * ground in the colour of what was hit, chunks of it thrown out on arcs, and up close a haze of dust over the eyes. The
 * shake is the server's ({@link PowersCompat#shakeAround}). Nothing here touches the world: the crater is still
 * {@link Bombardment}'s.
 * <p>Hits from {@link com.jaymoskingdoms.Cannonball} (wall cannons, field guns, mortar shells, the musketoon),
 * {@link ShipShot} (ships' guns) and {@link com.jaymoskingdoms.SiegeStone} (the mangonel).
 */
public final class ImpactFx {
	/** How far (blocks) a hit is sent: a shell's plume is seen from well off. */
	public static final double REACH = 96.0;

	/** What kind of hit it is, which sets how it looks and how hard it shakes. */
	public enum Look {
		/** A round shot, or a plain ball (the musketoon's): a short puff. */
		ROUND(10.0, 0.9F),
		/** Heavy shot: a bigger puff and more thrown ground. */
		HEAVY(14.0, 1.3F),
		/** An explosive shell: the fireball, the tall plume, the most ground thrown. */
		SHELL(24.0, 2.2F),
		/** A field mortar's shell: as a shell, a little smaller. */
		MORTAR(22.0, 1.9F),
		/** Chain shot: little smoke, a scuff of ground. */
		CHAIN(6.0, 0.5F),
		/** Grape shot: a spray of small kicks, little smoke. */
		GRAPE(6.0, 0.5F),
		/** A thrown stone (the mangonel's, a ship's stone): it shatters into rubble and a ring of stone dust. */
		STONE(20.0, 1.6F),
		/** A fire ball: a small flash and embers. */
		FIRE(8.0, 0.6F);

		/** The shake's reach (blocks) and peak (degrees, {@code core.Shakes} in Powers). */
		public final double shakeRange;
		public final float shakeStrength;

		Look(double shakeRange, float shakeStrength) {
			this.shakeRange = shakeRange;
			this.shakeStrength = shakeStrength;
		}

		/** The look of a shot of {@code kind}; a plain ball (no kind) is a round shot's. */
		public static Look of(@Nullable ShotKind kind) {
			if (kind == null) {
				return ROUND;
			}
			return switch (kind) {
				case ROUND -> ROUND;
				case HEAVY -> HEAVY;
				case SHELL -> SHELL;
				case CHAIN -> CHAIN;
				case GRAPE -> GRAPE;
				case STONE -> STONE;
				case FIRE -> FIRE;
			};
		}
	}

	/**
	 * Server → nearby clients: a hit at {@code x, y, z} of look {@code look} ({@link Look#ordinal}), {@code size} its burst
	 * (blocks), {@code ground} the block it struck ({@link Block#getId}, 0 for none: in the air or on a body).
	 */
	public record Hit(double x, double y, double z, int look, float size, int ground) implements CustomPacketPayload {
		public static final Type<Hit> TYPE = new Type<>(JaymosKingdoms.id("impact_fx"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Hit> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeDouble(p.x);
				buf.writeDouble(p.y);
				buf.writeDouble(p.z);
				buf.writeVarInt(p.look);
				buf.writeFloat(p.size);
				buf.writeVarInt(p.ground);
			},
			buf -> new Hit(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readFloat(), buf.readVarInt()));

		@Override
		public Type<Hit> type() {
			return TYPE;
		}
	}

	private ImpactFx() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Hit.TYPE, Hit.CODEC);
	}

	/** A hit of {@code look} at {@code at}, bursting {@code size} blocks round: shown to everyone near, and felt. */
	public static void hit(ServerLevel level, Vec3 at, Look look, double size) {
		hit(level, at, look, size, null);
	}

	/** As above; {@code struck}, when it hit a block, is that block (else the ground just under {@code at} is used). */
	public static void hit(ServerLevel level, Vec3 at, Look look, double size, @Nullable BlockPos struck) {
		BlockState ground = ground(level, at, struck);
		Hit hit = new Hit(at.x, at.y, at.z, look.ordinal(), (float) size, ground == null ? 0 : Block.getId(ground));
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(at) <= REACH * REACH && ServerPlayNetworking.canSend(player, Hit.TYPE)) {
				ServerPlayNetworking.send(player, hit);
			}
		}
		PowersCompat.shakeAround(level, at, look.shakeRange, look.shakeStrength);
	}

	/** The solid block the hit landed on: {@code struck}, or the first one at or up to two under {@code at}; null in the air. */
	private static @Nullable BlockState ground(ServerLevel level, Vec3 at, @Nullable BlockPos struck) {
		if (struck != null) {
			BlockState state = level.getBlockState(struck);
			if (!state.isAir()) {
				return state;
			}
		}
		BlockPos pos = BlockPos.containing(at.x, at.y - 0.05, at.z);
		for (int i = 0; i < 3; i++, pos = pos.below()) {
			BlockState state = level.getBlockState(pos);
			if (!state.isAir() && !state.getCollisionShape(level, pos).isEmpty()) {
				return state;
			}
		}
		return null;
	}
}
