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
 * Crawling, for every player (no power needed): press the Crawl key (Down Arrow) and you drop flat until you press it
 * again (or Jump) with room to stand. The client spots all of that ({@code CrawlTaps}) and tells the server; the
 * server's flag is synced to everyone so every client draws the same pose and hitbox.
 *
 * <p>While crawling, double-tapping Left or Right (A / D) rolls you one block that way: stop, drop and roll. Holding the
 * key keeps you rolling, but only for {@link #ROLL_STREAK_TICKS} one way; then that way is spent until you roll back the
 * other way. Rolling back (reversing) is what puts out the fire on you; any fire you roll through is smothered either way.
 * The client moves the player (movement is the client's); the server puts the fire out and syncs the roll so everyone
 * sees the body turn over.
 * <p>(0.18.85) Andy: "while laying in prone, pressing shift again will toggle an animation of you moving to your back, and
 * vice versa. kind of like metal gear solid V". Prone is on the belly or, {@link #SUPINE}, on the back, feet toward where you
 * look: the better view down a weapon, and the only way to draw a bow lying down ({@code ProneBowMixin}).
 */
public final class Crawl {
	/** How long a roll takes, in ticks (the client spreads the one-block slide over these): quick, about walking pace. */
	public static final int ROLL_TICKS = 5;
	/**
	 * (0.19.28) Andy: "on your back, you shuffle forward with your hips, so you move slower than you would prone on your
	 * stomach". Prone on the back moves at this share of the belly crawl's pace (the client's input is scaled).
	 */
	public static final float SUPINE_SPEED = 0.45F;
	/** How far a roll carries you sideways, in blocks. */
	public static final double ROLL_DISTANCE = 1.0;
	/** How long you can keep rolling one way (2 s) before you must roll back the other way. */
	public static final int ROLL_STREAK_TICKS = 40;
	/** Lie still this long after a roll and the spent direction is fresh again (and the next roll isn't a reversal). */
	public static final int ROLL_REST_TICKS = 40;

	/** On a crawling player: present while they crawl (synced to everyone). */
	public static final AttachmentType<Boolean> CRAWLING = AttachmentRegistry.create(JaymosPowers.id("crawling"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/**
	 * A roll in progress: when it started (game time), which way ({@code right}) and when the run of rolls that way began
	 * ({@code streakStart}). Synced to everyone for the animation.
	 */
	public record Rolling(long start, boolean right, long streakStart) {
		public static final StreamCodec<ByteBuf, Rolling> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Rolling::start,
			ByteBufCodecs.BOOL, Rolling::right,
			ByteBufCodecs.VAR_LONG, Rolling::streakStart,
			Rolling::new);

		/** Whether this roll ended long enough before {@code now} that the next one starts fresh. */
		boolean rested(long now) {
			return now - (start + ROLL_TICKS) > ROLL_REST_TICKS;
		}
	}

	/**
	 * The roll that would start at {@code now} going {@code right} after {@code last} (null if none yet), or null when
	 * that way is spent: you've rolled it for {@link #ROLL_STREAK_TICKS} and must roll back first. Shared by both sides.
	 */
	public static @Nullable Rolling nextRoll(@Nullable Rolling last, long now, boolean right) {
		if (last == null || last.rested(now) || last.right() != right) {
			return new Rolling(now, right, now);
		}
		return now - last.streakStart() >= ROLL_STREAK_TICKS ? null : new Rolling(now, right, last.streakStart());
	}

	/** Whether rolling {@code right} at {@code now} reverses a roll the other way (the back-and-forth that douses you). */
	public static boolean reverses(@Nullable Rolling last, long now, boolean right) {
		return last != null && !last.rested(now) && last.right() != right;
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

	/** (0.18.85) On a prone player lying on their back (synced to everyone). Gone whenever the crawl ends or starts afresh. */
	public static final AttachmentType<Boolean> SUPINE = AttachmentRegistry.create(JaymosPowers.id("crawl_supine"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** (0.18.85) Client only: the local player's entity id while they lie on their back (ahead of the server's say). */
	public static volatile int localSupine = -1;

	/** (0.18.85) Client → server: the prone player turned onto their back ({@code true}) or belly. */
	public record Supine(boolean back) implements CustomPacketPayload {
		public static final Type<Supine> TYPE = new Type<>(JaymosPowers.id("crawl_supine"));
		public static final StreamCodec<ByteBuf, Supine> CODEC = ByteBufCodecs.BOOL.map(Supine::new, Supine::back);

		@Override
		public Type<Supine> type() {
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
		PayloadTypeRegistry.serverboundPlay().register(Supine.TYPE, Supine.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Supine.TYPE, (payload, ctx) -> {
			ServerPlayer player = ctx.player();
			if (payload.back() && player.hasAttached(CRAWLING) && !com.jaymospowers.core.wounds.Downed.isDowned(player)) {
				player.setAttached(SUPINE, true);
			} else {
				player.removeAttached(SUPINE);
			}
		});
	}

	private static void set(ServerPlayer player, boolean down) {
		if (com.jaymospowers.core.wounds.Downed.isDowned(player)) {
			// (0.14.1) the downed state holds the crawl: Jump or the key don't stand them up
			return;
		}
		if (!down && com.jaymospowers.core.wounds.Breaks.crawlOnly(player)) {
			// (0.14.2) two broken legs don't stand: crawling is all that's left
			return;
		}
		// (0.18.85) a fresh crawl starts on the belly (a Supine packet right after turns it over)
		player.removeAttached(SUPINE);
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
		if (!player.hasAttached(CRAWLING) || !canCrawl(player) || com.jaymospowers.core.wounds.Downed.passedOut(player) || last != null && now - last.start() < ROLL_TICKS - 2) {
			return;
		}
		Rolling next = nextRoll(last, now, right);
		if (next == null) {
			return;
		}
		player.setAttached(ROLLING, next);
		// rolling one way only fans the flames; it's rolling back over that smothers them
		boolean doused = false;
		if (reverses(last, now, right) && player.isOnFire()) {
			player.clearFire();
			doused = true;
		}
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

	/** (0.18.85) Whether this player lies prone on their back (on either side). */
	public static boolean supine(Player player) {
		if (!crawling(player)) {
			return false;
		}
		if (player.level().isClientSide() && player.getId() == localCrawler) {
			// the local player's own crawl: their turn over shows at once
			return player.getId() == localSupine;
		}
		return player.hasAttached(SUPINE);
	}

	/** Whether this player is crawling by the gesture (on either side). */
	public static boolean crawling(Player player) {
		if (player.level().isClientSide() && player.getId() == localCrawler) {
			return true;
		}
		return player.hasAttached(CRAWLING);
	}
}
