package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Crawling, for every player (no power needed): double-tap Jump + Sneak together and you drop flat for as long as you
 * hold Jump, which fits you through one-block-high gaps. Let go and you stand back up once there's room (vanilla keeps
 * you crawling while the ceiling is too low). The client spots the gesture and tells the server; the server's flag is
 * synced to everyone so every client draws the same pose and hitbox.
 *
 * <p>While crawling, double-tapping Left or Right (A / D) rolls you one block that way: stop, drop and roll. A roll puts
 * out the fire on you and any fire you roll through; holding the key keeps you rolling. The client moves the player (movement is the client's); the
 * server puts the fire out and syncs the roll so everyone sees the body turn over.
 */
public final class Crawl {
	/** How long a roll takes, in ticks (the client spreads the one-block slide over these): quick, about walking pace. */
	public static final int ROLL_TICKS = 5;
	/** How far a roll carries you sideways, in blocks. */
	public static final double ROLL_DISTANCE = 1.0;

	/** On a crawling player: present while they crawl (synced to everyone). */
	public static final AttachmentType<Boolean> CRAWLING = AttachmentRegistry.create(JaymosPowers.id("crawling"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** A roll in progress: when it started (game time) and which way ({@code right}). Synced to everyone for the animation. */
	public record Rolling(long start, boolean right) {
		public static final StreamCodec<ByteBuf, Rolling> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Rolling::start,
			ByteBufCodecs.BOOL, Rolling::right,
			Rolling::new);
	}

	/** On a player who's rolling (or last rolled): see {@link Rolling}. */
	public static final AttachmentType<Rolling> ROLLING = AttachmentRegistry.create(JaymosPowers.id("crawl_rolling"),
		builder -> builder.syncWith(Rolling.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/**
	 * Client only: the local player's entity id while they crawl, so their own pose flips the tick they do the gesture
	 * instead of a round trip later. -1 when not crawling.
	 */
	public static volatile int localCrawler = -1;

	/** Client → server: the player started ({@code true}) or stopped crawling. */
	public record Key(boolean down) implements CustomPacketPayload {
		public static final Type<Key> TYPE = new Type<>(JaymosPowers.id("crawl_key"));
		public static final StreamCodec<ByteBuf, Key> CODEC = ByteBufCodecs.BOOL.map(Key::new, Key::down);

		@Override
		public Type<Key> type() {
			return TYPE;
		}
	}

	/** Client → server: the crawling player rolls left or right ({@code right}). */
	public record Roll(boolean right) implements CustomPacketPayload {
		public static final Type<Roll> TYPE = new Type<>(JaymosPowers.id("crawl_roll"));
		public static final StreamCodec<ByteBuf, Roll> CODEC = ByteBufCodecs.BOOL.map(Roll::new, Roll::right);

		@Override
		public Type<Roll> type() {
			return TYPE;
		}
	}

	private Crawl() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Key.TYPE, Key.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Roll.TYPE, Roll.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Key.TYPE, (payload, ctx) -> set(ctx.player(), payload.down()));
		ServerPlayNetworking.registerGlobalReceiver(Roll.TYPE, (payload, ctx) -> roll(ctx.player(), payload.right()));
	}

	private static void set(ServerPlayer player, boolean down) {
		if (down && canCrawl(player)) {
			player.setAttached(CRAWLING, true);
		} else {
			player.removeAttached(CRAWLING);
		}
	}

	private static void roll(ServerPlayer player, boolean right) {
		Level level = player.level();
		long now = level.getGameTime();
		Rolling last = player.getAttached(ROLLING);
		// a held key chains rolls back to back, so allow a couple of ticks of network jitter
		if (!player.hasAttached(CRAWLING) || !canCrawl(player) || last != null && now - last.start() < ROLL_TICKS - 2) {
			return;
		}
		player.setAttached(ROLLING, new Rolling(now, right));
		boolean doused = player.isOnFire();
		player.clearFire();
		// the fire you're lying in and the fire you roll into
		double yaw = Math.toRadians(player.getYRot());
		double side = right ? ROLL_DISTANCE : -ROLL_DISTANCE;
		AABB swept = player.getBoundingBox().expandTowards(-Math.cos(yaw) * side, 0.0, -Math.sin(yaw) * side);
		for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(swept.minX), Mth.floor(swept.minY), Mth.floor(swept.minZ),
			Mth.floor(swept.maxX), Mth.floor(swept.maxY), Mth.floor(swept.maxZ))) {
			if (level.getBlockState(pos).is(BlockTags.FIRE)) {
				level.removeBlock(pos, false);
				doused = true;
			}
		}
		if (doused) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS,
				0.6F, 1.2F);
		}
	}

	/**
	 * How far through a roll this player is (0 to 1) at {@code gameTime + partialTick}, or null when they aren't
	 * rolling. Positive while rolling right, negative while rolling left.
	 */
	public static @Nullable Float rollProgress(Player player, long gameTime, float partialTick) {
		Rolling rolling = player.getAttached(ROLLING);
		if (rolling == null) {
			return null;
		}
		float t = (gameTime - rolling.start() + partialTick) / ROLL_TICKS;
		if (t < 0.0F || t >= 1.0F) {
			return null;
		}
		return rolling.right() ? t : -t;
	}

	/** Whether a crawl can hold this player flat right now (not in water, flying, gliding, riding or spectating). */
	public static boolean canCrawl(Player player) {
		return !player.isSpectator() && !player.isPassenger() && !player.isFallFlying() && !player.getAbilities().flying
			&& !player.isInWater() && !player.isSleeping();
	}

	/** Whether this player is crawling by the gesture (on either side). */
	public static boolean crawling(Player player) {
		if (player.level().isClientSide() && player.getId() == localCrawler) {
			return true;
		}
		return player.hasAttached(CRAWLING);
	}
}
