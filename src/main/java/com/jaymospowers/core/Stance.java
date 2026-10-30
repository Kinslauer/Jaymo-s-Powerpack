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
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/**
 * (0.18.57) Andy: "shift + scroll should adjust you through stances, from prone to a crouch to a sneak to standing, in 4
 * or 5 gradual stages". Holding Sneak, the wheel steps you down (scroll down) or up (scroll up) through four stances, and
 * you stay in the one you pick once you let go of Sneak (the client's {@code StanceScroll}):
 * <ol>
 * <li>{@link #PRONE}: flat on your belly, the crawl ({@link Crawl});</li>
 * <li>{@link #LOW}: a low crouch, knees deep, {@link #LOW_HEIGHT} blocks tall so it gets under a gap a sneak can't,
 * moving at a sneak's pace (0.18.77: Andy, "you should still be able to move like vanilla when sneaking. its the kneel
 * that stops you"); standing still in it you go down on one knee;</li>
 * <li>{@link #SNEAK}: vanilla's sneak, held without the key (0.18.66: the arms-out sway and the kneel are the low crouch's now);</li>
 * <li>{@link #STAND}: on your feet.</li>
 * </ol>
 * Only the low crouch is new to the server: the crawl has its own flag, and a sneak is the Sneak key the client sends. Its
 * flag is synced to everyone so every client draws it and boxes it the same.
 */
public final class Stance {
	/** The stances, lowest first: the wheel steps one at a time. */
	public static final int PRONE = 0, LOW = 1, SNEAK = 2, STAND = 3;
	/** The low crouch's height (blocks; a sneak is 1.5, the crawl 0.6). */
	public static final float LOW_HEIGHT = 1.25F;
	/** A sneak's height (blocks), vanilla's crouching box. */
	public static final float SNEAK_HEIGHT = 1.5F;
	/** The low crouch's walking speed, as a share of a sneak's (0.18.77: a sneak's own; was 0.6). */
	public static final float LOW_SPEED = 1.0F;

	/** On a player in the low crouch: present while they hold it (synced to everyone). */
	public static final AttachmentType<Boolean> LOW_CROUCH = AttachmentRegistry.create(JaymosPowers.id("low_crouch"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** Client only: the local player's entity id while they hold the low crouch, so it takes the tick they pick it. */
	public static volatile int localLow = -1;

	/** Client → server: the player went into ({@code true}) or out of the low crouch. */
	public record Low(boolean low) implements CustomPacketPayload {
		public static final Type<Low> TYPE = new Type<>(JaymosPowers.id("stance_low"));
		public static final StreamCodec<ByteBuf, Low> CODEC = ByteBufCodecs.BOOL.map(Low::new, Low::low);

		@Override
		public Type<Low> type() {
			return TYPE;
		}
	}

	private Stance() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Low.TYPE, Low.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Low.TYPE, (payload, ctx) -> set(ctx.player(), payload.low()));
	}

	private static void set(ServerPlayer player, boolean low) {
		if (low == player.hasAttached(LOW_CROUCH)) {
			return;
		}
		if (low && Crawl.canCrawl(player)) {
			player.setAttached(LOW_CROUCH, true);
		} else {
			player.removeAttached(LOW_CROUCH);
		}
		player.refreshDimensions();
	}

	/** Whether this player holds the low crouch (on either side). Only counts while they're crouched. */
	public static boolean low(Player player) {
		if (player.level().isClientSide() && player.getId() == localLow) {
			return true;
		}
		return player.hasAttached(LOW_CROUCH);
	}

	/** A crouching player's box, cut down to the low crouch's height when they hold it (from {@code StanceDimensionsMixin}). */
	public static EntityDimensions dimensions(Player player, Pose pose, EntityDimensions crouched) {
		if (pose != Pose.CROUCHING || !low(player) || crouched.height() <= LOW_HEIGHT) {
			return crouched;
		}
		return crouched.scale(1.0F, LOW_HEIGHT / crouched.height());
	}
}
