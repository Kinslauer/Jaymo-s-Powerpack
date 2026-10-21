package com.jaymospowers.freerun;

import com.jaymospowers.JaymosPowers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/**
 * (0.15.12) The slide, for everyone: in a fast run ({@link FastRun}), HOLD Sneak for {@link #HOLD_TICKS} ticks (a tap is the
 * dodge's double-tap, left alone) and you drop into a crawl-height slide for {@link #TICKS} ticks, keeping your speed with
 * friction ({@code FreerunClient} moves the body; client-authoritative like the other moves). Low enough for a 1-high gap;
 * if a block is overhead when it ends, vanilla's own crawl keeps you down until there is room. The server checks the fast
 * run and the ground, syncs {@link #SLIDING} (the 1-block pose and hitbox, {@code CrawlPlayerMixin}) and ends a slide
 * that isn't reported finished.
 */
public final class Slide {
	// ===== feel: the knobs to tweak after playtesting ===============================================================
	/** Ticks Sneak must be held (in a fast run) before the slide begins. */
	public static final int HOLD_TICKS = 4;
	/** How long a slide lasts (ticks). */
	public static final int TICKS = 15;
	/** The slide's speed (blocks/tick) at the start, as a share of the run speed (at least {@link #MIN_SPEED}, at most {@link #MAX_SPEED}), and the friction each tick. */
	public static final double BOOST = 1.05, MIN_SPEED = 0.3, MAX_SPEED = 0.65, FRICTION = 0.93;
	/** Slower than this (blocks/tick) the slide is over. */
	public static final double STOP_SPEED = 0.1;
	/** A jump out of a slide: up (a vanilla jump is 0.42), and the speed kept along the ground. */
	public static final double JUMP_UP = 0.45, JUMP_KEEP = 1.15;
	/** Ticks before another slide. */
	public static final int COOLDOWN = 12;
	/** Hunger the slide costs. */
	public static final float EXHAUSTION = 0.3F;
	/** Server: a slide not reported over ends this long past its length (ticks). */
	static final int SLACK_TICKS = 10;
	// ================================================================================================================

	/** On a sliding player (synced to everyone): the 1-block pose. */
	public static final AttachmentType<Boolean> SLIDING = AttachmentRegistry.create(JaymosPowers.id("freerun_sliding"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));


	private static final Map<UUID, Integer> TICKS_SO_FAR = new HashMap<>();
	private static final Map<UUID, Long> ENDED = new HashMap<>();

	private Slide() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Slide::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			TICKS_SO_FAR.remove(handler.player.getUUID());
			ENDED.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			TICKS_SO_FAR.clear();
			ENDED.clear();
		});
	}

	/** Sliding (either side; the local player's own copy on their client). */
	public static boolean sliding(Player player) {
		return player.hasAttached(SLIDING);
	}

	/** Server: whether a slide ended within the last {@code ticks} ticks. */
	static boolean justEnded(ServerPlayer player, int ticks) {
		Long at = ENDED.get(player.getUUID());
		return at != null && player.level().getGameTime() - at <= ticks;
	}

	/** Server: whether a slide may begin: in a fast run, on the ground, Sneak down, free to freerun. */
	static boolean valid(ServerPlayer player) {
		return FastRun.active(player) && player.onGround() && player.isShiftKeyDown() && Freerun.canMove(player) && !sliding(player)
			&& !justEnded(player, COOLDOWN - 1);
	}

	static void start(ServerPlayer player) {
		TICKS_SO_FAR.put(player.getUUID(), 0);
		player.setAttached(SLIDING, true);
		player.causeFoodExhaustion(EXHAUSTION);
		Freerun.moved(player);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SAND_STEP, SoundSource.PLAYERS, 0.8F, 0.7F);
	}

	static void end(ServerPlayer player) {
		if (TICKS_SO_FAR.remove(player.getUUID()) != null) {
			ENDED.put(player.getUUID(), player.level().getGameTime());
		}
		player.removeAttached(SLIDING);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Integer ticks = TICKS_SO_FAR.get(player.getUUID());
			if (ticks == null) {
				if (player.hasAttached(SLIDING)) {
					player.removeAttached(SLIDING);
				}
				continue;
			}
			if (!player.isAlive() || ticks > TICKS + SLACK_TICKS || !Freerun.canMove(player)) {
				end(player);
				continue;
			}
			TICKS_SO_FAR.put(player.getUUID(), ticks + 1);
			if (ticks % 2 == 0 && player.onGround()) {
				ServerLevel level = player.level();
				BlockState state = level.getBlockState(BlockPos.containing(player.getX(), player.getY() - 0.05, player.getZ()));
				if (!state.isAir()) {
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), player.getX(), player.getY() + 0.1, player.getZ(), 4, 0.25, 0.02, 0.25, 0.08);
				}
			}
		}
	}
}
