package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Crawling, for every player (no power needed): double-tap Jump + Sneak together and you drop flat for as long as you
 * hold Jump, which fits you through one-block-high gaps. Let go and you stand back up once there's room (vanilla keeps
 * you crawling while the ceiling is too low). The client spots the gesture and tells the server; the server's flag is
 * synced to everyone so every client draws the same pose and hitbox.
 */
public final class Crawl {
	/** On a crawling player: present while they crawl (synced to everyone). */
	public static final AttachmentType<Boolean> CRAWLING = AttachmentRegistry.create(JaymosPowers.id("crawling"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

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

	private Crawl() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Key.TYPE, Key.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Key.TYPE, (payload, ctx) -> set(ctx.player(), payload.down()));
	}

	private static void set(ServerPlayer player, boolean down) {
		if (down && canCrawl(player)) {
			player.setAttached(CRAWLING, true);
		} else {
			player.removeAttached(CRAWLING);
		}
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
