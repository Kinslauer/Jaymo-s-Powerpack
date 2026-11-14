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
import com.jaymospowers.core.wounds.Giants;
import com.jaymospowers.core.wounds.Tripped;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.15.12) The slide, for everyone: in a fast run ({@link FastRun}), HOLD Sneak for {@link #HOLD_TICKS} ticks and you
 * drop into a crawl-height slide for {@link #TICKS} ticks, keeping your speed with
 * friction ({@code FreerunClient} moves the body; client-authoritative like the other moves). Low enough for a 1-high gap;
 * if a block is overhead when it ends, vanilla's own crawl keeps you down until there is room. The server checks the fast
 * run and the ground, syncs {@link #SLIDING} (the 1-block pose and hitbox, {@code CrawlPlayerMixin}) and ends a slide
 * that isn't reported finished.
 * <p>(0.18.85) Andy: "when in a sprint, (the fast one after a while of movement) you do a full baseball slide on your
 * stomach, and holding shift or scrolling down keeps you in the prone. from the regular sprint, pressing shift will still do
 * the back slide, holding or scrolling will keep you in it, on your back". A plain sprint slides too now (feet first, on the
 * back, as before); out of a fast run it's a head-first dive onto the belly ({@link #BELLY}). Held into prone, each lies the
 * way it slid ({@code Crawl.SUPINE}).
 * <p>(0.21.36) Andy: "sprint slide should be activated after 1.5s of holding, it still activates too easy". The hold is
 * {@link #HOLD_TICKS} = 30 ticks, and through it you keep running at full pace ({@code FreerunClient.slideWindup}).
 * <p>Slide tackle: a slide that runs into a mob ahead of you ({@link #tackle}, checked here on the server) knocks it away,
 * over onto its back for anything built like a person ({@link Tripped#sweep}), and you lose your speed; a giant
 * ({@link Giants#giant}) stops you dead and stays on its feet. Slopes: a slide that leaves the ground restarts its timer, so
 * a hillside can be ridden down, gaining {@link #SLOPE_GAIN} a tick in the air. Water: a slide that reaches water skips off
 * it and slides on, {@link #MAX_SKIPS} times at most ({@link #waterSkip}).
 */
public final class Slide {
	// ===== feel: the knobs to tweak after playtesting ===============================================================
	/** Ticks Sneak must be held (in a run) before the slide begins. (0.21.36) 30 (1.5 s); you keep running while it's held. */
	public static final int HOLD_TICKS = 30;
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
	/** (0.18.85) A plain sprint this recent (ticks) still counts for a slide (Sneak stops the sprint while it's held). */
	public static final int SPRINT_GRACE_TICKS = 8;
	/** Server: a slide not reported over ends this long past its length (ticks). */
	static final int SLACK_TICKS = 10;
	/** Slide tackle: the speed (blocks/tick) the slide must still have to knock anything over, how far ahead of the body a mob counts as hit (blocks), and how hard it is shoved. */
	public static final double TACKLE_MIN_SPEED = 0.2, TACKLE_REACH = 0.7, TACKLE_SHOVE = 0.9;
	/** How long (ticks) a mob that is swept off its feet by a tackle lies there. */
	public static final int TACKLE_TRIP_TICKS = 50;
	/** Slope: speed (blocks/tick) a slide gains for each tick it is in the air above ground below; how far below the feet (blocks) ground still counts as a slope rather than a drop. */
	public static final double SLOPE_GAIN = 0.04, SLOPE_DROP = 1.25;
	/** Server: a slide may be airborne this many ticks in a row (a slope's hops) before it ends. */
	static final int MAX_AIR_TICKS = 20;
	/** Water skip: skips a slide gets, the speed (blocks/tick) it needs to skip at all, what share of speed a skip keeps, and the hop up (blocks/tick). */
	public static final int MAX_SKIPS = 2;
	public static final double SKIP_MIN_SPEED = 0.25, SKIP_KEEP = 0.85, SKIP_UP = 0.3;
	/** Ticks after a skip before the water can take another: the hop clears the surface first. */
	public static final int SKIP_GAP_TICKS = 2;
	/** A slide may be in water this many ticks (the skip's own) before the water takes it. */
	public static final int WET_GRACE_TICKS = 3;
	// ================================================================================================================

	/** On a sliding player (synced to everyone): the 1-block pose. */
	public static final AttachmentType<Boolean> SLIDING = AttachmentRegistry.create(JaymosPowers.id("freerun_sliding"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));


	/** (0.18.85) On a sliding player: the slide is a head-first dive onto the belly (out of a fast run), not feet first. */
	public static final AttachmentType<Boolean> BELLY = AttachmentRegistry.create(JaymosPowers.id("freerun_slide_belly"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	private static final Map<UUID, Integer> TICKS_SO_FAR = new HashMap<>();
	/** (0.18.85) When each player last sprinted on the ground (game time). */
	private static final Map<UUID, Long> SPRINTED = new HashMap<>();
	private static final Map<UUID, Long> ENDED = new HashMap<>();
	/** Server: skips made, ticks in the air in a row and ticks in water in a row, in the slide under way. */
	private static final Map<UUID, Integer> SKIPS = new HashMap<>(), AIR = new HashMap<>(), WET = new HashMap<>();

	private Slide() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Slide::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			TICKS_SO_FAR.remove(handler.player.getUUID());
			ENDED.remove(handler.player.getUUID());
			SPRINTED.remove(handler.player.getUUID());
			forgetSlide(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			TICKS_SO_FAR.clear();
			ENDED.clear();
			SPRINTED.clear();
			SKIPS.clear();
			AIR.clear();
			WET.clear();
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

	/** (0.18.85) Whether this player slides belly first (a head-first dive) rather than feet first on the back. */
	public static boolean belly(Player player) {
		return player.hasAttached(BELLY);
	}

	/** Server: whether a slide may begin: in a fast run (or, 0.18.85, a plain sprint just now), on the ground, Sneak down, free to freerun. */
	static boolean valid(ServerPlayer player) {
		Long sprinted = SPRINTED.get(player.getUUID());
		boolean running = FastRun.active(player) || player.isSprinting()
			|| sprinted != null && player.level().getGameTime() - sprinted <= SPRINT_GRACE_TICKS
			|| com.jaymospowers.freerun.moves.Breach.recent(player, com.jaymospowers.freerun.moves.Breach.SLIDE_WINDOW);
		// (0.21.36) no Sneak check: the client keeps the Sneak bit off through the 1.5 s hold so the run goes on
		return running && player.onGround() && Freerun.canMove(player) && !sliding(player)
			&& !justEnded(player, COOLDOWN - 1);
	}

	private static void forgetSlide(UUID id) {
		SKIPS.remove(id);
		AIR.remove(id);
		WET.remove(id);
	}

	/** Server: whether {@code player}, sprinting on the ground within the last {@code ticks} ticks, may still be counted as running. */
	public static boolean sprintedWithin(ServerPlayer player, int ticks) {
		Long at = SPRINTED.get(player.getUUID());
		return at != null && player.level().getGameTime() - at <= ticks;
	}

	static void start(ServerPlayer player) {
		forgetSlide(player.getUUID());
		TICKS_SO_FAR.put(player.getUUID(), 0);
		if (FastRun.active(player) || com.jaymospowers.freerun.moves.Breach.recent(player, com.jaymospowers.freerun.moves.Breach.SLIDE_WINDOW)) {
			player.setAttached(BELLY, true);
		} else {
			player.removeAttached(BELLY);
		}
		player.setAttached(SLIDING, true);
		player.causeFoodExhaustion(EXHAUSTION);
		Freerun.moved(player);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SAND_STEP, SoundSource.PLAYERS, 0.8F, 0.7F);
	}

	static void end(ServerPlayer player) {
		if (TICKS_SO_FAR.remove(player.getUUID()) != null) {
			ENDED.put(player.getUUID(), player.level().getGameTime());
		}
		forgetSlide(player.getUUID());
		player.removeAttached(SLIDING);
		player.removeAttached(BELLY);
	}

	/**
	 * Server: a mob ahead of the slide (within {@link #TACKLE_REACH} of the body along the way it moves), the slide still fast
	 * enough ({@link #TACKLE_MIN_SPEED}): a giant stops the slide dead and shrugs; anything else is shoved away and, if built like a
	 * person, swept onto its back ({@link Tripped#sweep}); the slide ends and the speed with it. Players, pets of the slider
	 * and the downed are left alone.
	 */
	private static void tackle(ServerPlayer player) {
		Vec3 motion = player.getKnownMovement();
		double speed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
		if (speed < TACKLE_MIN_SPEED || !player.onGround()) {
			return;
		}
		Vec3 dir = new Vec3(motion.x / speed, 0.0, motion.z / speed);
		AABB reach = player.getBoundingBox().expandTowards(dir.x * TACKLE_REACH, 0.0, dir.z * TACKLE_REACH).inflate(0.1, 0.0, 0.1);
		LivingEntity hit = null;
		for (Mob mob : player.level().getEntitiesOfClass(Mob.class, reach)) {
			if (!mob.isAlive() || com.jaymospowers.core.wounds.Downed.isDowned(mob) || mob.isPassenger() || mob.isVehicle() || mob instanceof OwnableEntity owned && owned.getOwner() == player) {
				continue;
			}
			Vec3 to = mob.position().subtract(player.position());
			if (to.x * dir.x + to.z * dir.z > -0.1) {
				hit = mob;
				break;
			}
		}
		if (hit == null) {
			return;
		}
		ServerLevel level = player.level();
		if (Giants.giant(hit)) {
			level.playSound(null, hit.getX(), hit.getY(), hit.getZ(), SoundEvents.PLAYER_BIG_FALL, SoundSource.PLAYERS, 1.0F, 0.6F);
		} else {
			hit.knockback(TACKLE_SHOVE, -dir.x, -dir.z, player.damageSources().playerAttack(player), 0.0F);
			Tripped.sweep(hit, TACKLE_TRIP_TICKS);
			level.playSound(null, hit.getX(), hit.getY(), hit.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.9F);
		}
		level.sendParticles(ParticleTypes.POOF, hit.getX(), hit.getY() + 0.3, hit.getZ(), 6, 0.3, 0.1, 0.3, 0.03);
		// the speed is gone: the client's slide stops moving the body once the flag is off, and its motion is zeroed
		player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), new Vec3(0.0, player.getKnownMovement().y, 0.0)));
		player.addTag(Freerun.XP_TAG);
		end(player);
	}

	/**
	 * Server, from the client's {@code RunMoves.WATER_SKIP}: a slide that reached water bounces off the surface and slides on
	 * ({@link #MAX_SKIPS} at most, each restarting the timer), with a splash and a skipping sound. False if it can't (not
	 * sliding, no water at the feet, or out of skips).
	 */
	public static boolean waterSkip(ServerPlayer player) {
		if (!sliding(player) || !TICKS_SO_FAR.containsKey(player.getUUID()) || SKIPS.getOrDefault(player.getUUID(), 0) >= MAX_SKIPS) {
			return false;
		}
		ServerLevel level = player.level();
		if (!level.containsAnyLiquid(player.getBoundingBox().inflate(0.0, 0.3, 0.0))) {
			return false;
		}
		SKIPS.merge(player.getUUID(), 1, Integer::sum);
		TICKS_SO_FAR.put(player.getUUID(), 0);
		WET.remove(player.getUUID());
		level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 0.1, player.getZ(), 16, 0.3, 0.05, 0.3, 0.2);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.7F, 1.2F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 0.6F, 1.5F);
		player.addTag(Freerun.XP_TAG);
		return true;
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSprinting() && player.onGround()) {
				SPRINTED.put(player.getUUID(), player.level().getGameTime());
			}
			Integer ticks = TICKS_SO_FAR.get(player.getUUID());
			if (ticks == null) {
				if (player.hasAttached(SLIDING)) {
					player.removeAttached(SLIDING);
					player.removeAttached(BELLY);
				}
				continue;
			}
			// water only holds a slide for the skip's own few ticks (canMove rules water out)
			int wet = player.isInWater() && !player.isInLava() ? WET.merge(player.getUUID(), 1, Integer::sum) : 0;
			if (wet == 0) {
				WET.remove(player.getUUID());
			}
			boolean skimming = wet > 0 && wet <= WET_GRACE_TICKS;
			// a slide in the air (a slope's hops, a skip) goes on; the client ends it when the ground is too far below
			int air = player.onGround() ? 0 : AIR.merge(player.getUUID(), 1, Integer::sum);
			if (air == 0) {
				AIR.remove(player.getUUID());
			}
			if (!player.isAlive() || ticks > TICKS + SLACK_TICKS || air > MAX_AIR_TICKS || !Freerun.canMove(player) && !skimming) {
				end(player);
				continue;
			}
			TICKS_SO_FAR.put(player.getUUID(), air > 0 ? 0 : ticks + 1);
			tackle(player);
			if (!TICKS_SO_FAR.containsKey(player.getUUID())) {
				continue;
			}
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
