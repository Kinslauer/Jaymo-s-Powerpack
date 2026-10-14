package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Powers;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Chronokinesis: golden clockwork time magic. Everything is decided here, on the server.
 * <ul>
 * <li>R, Rewind: snap back to where you were 3 s ago (health restored if it was higher, fire put out).</li>
 * <li>G, Stasis Field: stop time for mobs and projectiles in a 6-block bubble for 4 s.</li>
 * <li>V, Overclock: 6 s as a speedster: Speed V, Haste II and faster attacks, everyone near you slowed hard. Since
 * 0.10.28 the Flash's speed too ({@link ChronoSpeed}): a far faster sprint, running up walls and across water, and V
 * again to phase through a wall.</li>
 * <li>B, Time Bloom: 5 s of fast-forward for crops, saplings, furnaces, smokers and ovens within 5 blocks, and for
 * your own wounds (they mend {@code BLOOM_HEAL} every {@code BLOOM_HEAL_EVERY} ticks meanwhile).</li>
 * </ul>
 * The HUD is fed by {@link ChronoSync}.
 *
 * <p>0.2.0: <b>echoes</b>, Chrono's curse (one turns up after every level-up; see {@link ChronoEchoes} and
 * {@link ChronoEcho}), and <b>ascension</b>: enough time spent in Stasis or rewound, a night's sleep, and the Stopped
 * Clock appears in your hands ({@link StoppedClockItem}). Ascended (Timeless), cooldowns recover faster and C is
 * {@link ChronoPunch}. Chrono has no corruption meter: the echoes are its corruption. Timeless and corrupted both, Z
 * enters {@link ChronoFlow}, free-flowing combat.
 */
public final class ChronoPower implements Power {
	public static final ChronoPower INSTANCE = new ChronoPower();
	public static final Identifier ID = JaymosPowers.id("chrono");

	// ---- tuning (ticks) ----
	public static final int REWIND_TICKS = 60;
	/** Rewind needs at least this much history (0.5 s), so it can't be spammed right after a teleport or respawn. */
	static final int REWIND_MIN_HISTORY = 10;
	static final int REWIND_COOLDOWN = 300;
	static final int STASIS_COOLDOWN = 600;
	static final int OVERCLOCK_TICKS = 120;
	static final int OVERCLOCK_COOLDOWN = 500;
	/** Attack speed while overclocked: x1.6 of the total (a sword goes from 1.6 to ~2.56 swings a second). */
	static final double OVERCLOCK_ATTACK_SPEED = 0.6;
	static final int BLOOM_COOLDOWN = 800;
	/** Time Bloom mends you too: this much health every this many ticks while it runs (5 hearts over its 5 s). */
	static final float BLOOM_HEAL = 1.0F;
	static final int BLOOM_HEAL_EVERY = 10;
	/** Overclock makes you a speedster: Speed amplifier (4 = Speed V). */
	static final int OVERCLOCK_SPEED_AMPLIFIER = 4;
	/** Overclock's drag on everyone around you: radius, Slowness amplifier (3 = Slowness IV), refreshed this often. */
	static final double OVERCLOCK_DRAG_RADIUS = 8.0;
	static final int OVERCLOCK_DRAG_AMPLIFIER = 3;
	static final int OVERCLOCK_DRAG_EVERY = 10;
	/** The starter, Nudge: reach, how long Slowness I / Speed I lasts, and the cooldown. */
	static final double NUDGE_RANGE = 16.0;
	static final int NUDGE_TICKS = 80;
	static final int NUDGE_COOLDOWN = 300;

	// ---- 0.2.0 tuning ----
	/** Time in Stasis or rewound (ticks) before the Stopped Clock can come: 10 minutes. */
	public static final int ASCEND_STOPPED_TICKS = 10 * 1200;
	/** Timeless: cooldowns recover this many times as fast (1.25 = 25% faster). Chrono Punch keeps its own. */
	static final double TIMELESS_COOLDOWN_RATE = 1.25;
	/** How often the held item is noted, and how often (and how far apart) walked positions are. */
	static final int HELD_SAMPLE_INTERVAL = 20;
	static final int PATH_SAMPLE_INTERVAL = 40;
	static final double PATH_MIN_STEP = 3.0;

	static final int REWIND = 0;
	static final int STASIS = 1;
	static final int OVERCLOCK = 2;
	static final int BLOOM = 3;
	private static final int[] COOLDOWNS = {REWIND_COOLDOWN, STASIS_COOLDOWN, OVERCLOCK_COOLDOWN, BLOOM_COOLDOWN};

	static final Identifier OVERCLOCK_MODIFIER = JaymosPowers.id("chrono_overclock");
	/** Resend the HUD state this often while anything is counting, to correct drift. */
	private static final int SYNC_INTERVAL = 20;

	private static final Map<UUID, ChronoState> STATES = new HashMap<>();

	private ChronoPower() {
	}

	/** Called once from {@link JaymosPowers#onInitialize()}: register the power and everything it needs. */
	public static void init() {
		Powers.register(INSTANCE);
		PayloadTypeRegistry.clientboundPlay().register(ChronoSync.TYPE, ChronoSync.CODEC);
		StasisField.registerLocks();
		ChronoEchoes.init();
		StoppedClockItem.init();
		// touch the attachment types so they register at startup
		JaymosPowers.LOGGER.debug("chrono attachments: {} {}", ChronoMemory.MEMORY, ChronoEchoes.ORPHANS);
		ServerTickEvents.END_SERVER_TICK.register(ChronoPunch::tick);
		ChronoFlow.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ChronoDebugCommand.register(dispatcher));
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer serverPlayer && Powers.has(serverPlayer, INSTANCE)) {
				ChronoMemory.count(ChronoMemory.of(serverPlayer).mined, BuiltInRegistries.BLOCK.getKey(state.getBlock()));
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ChronoState state = STATES.get(handler.player.getUUID());
			if (state != null) {
				stopAll(handler.player, state);
				state.clearHistory();
				state.synced = false;
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (ChronoState state : STATES.values()) {
				if (state.stasis != null) {
					state.stasis.end();
					state.stasis = null;
				}
			}
			StasisField.releaseAll();
			ChronoPunch.clear();
			ChronoFlow.clear();
			STATES.clear();
		});
	}

	@Override
	public Identifier id() {
		return ID;
	}

	/** Rewind first; Stasis Field, which stops other players too, comes last. */
	@Override
	public PowerKey[] unlockOrder() {
		return new PowerKey[] {PowerKey.ABILITY_1, PowerKey.ABILITY_3, PowerKey.ABILITY_4, PowerKey.ABILITY_2};
	}

	@Override
	public int color() {
		return 0xF2C14E;
	}

	@Override
	public ItemStack icon() {
		return new ItemStack(Items.CLOCK);
	}

	private static ChronoState state(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), uuid -> new ChronoState());
	}

	private static int now(ServerPlayer player) {
		return player.level().getServer().getTickCount();
	}

	@Override
	public void onGained(ServerPlayer player) {
		ChronoState state = state(player);
		state.clearHistory();
		state.synced = false;
	}

	@Override
	public void onLost(ServerPlayer player) {
		ChronoState state = STATES.get(player.getUUID());
		if (state != null) {
			if (state.overclockLeft > 0) {
				removeOverclockEffect(player, MobEffects.SPEED, OVERCLOCK_SPEED_AMPLIFIER, state.overclockLeft);
				removeOverclockEffect(player, MobEffects.HASTE, 1, state.overclockLeft);
			}
			stopAll(player, state);
			STATES.remove(player.getUUID());
		}
		ChronoFlow.stop(player);
		// the echoes go with the power, and so does everything time remembered
		ChronoEchoes.fadeAll(player);
		ChronoMemory.forget(player);
		// clear the HUD state on the client
		if (ServerPlayNetworking.canSend(player, ChronoSync.TYPE)) {
			int[] zero = new int[ChronoSync.ABILITIES];
			ServerPlayNetworking.send(player, new ChronoSync(zero, zero, zero, zero, 0, 0));
		}
	}

	/** Every level-up (mastery 2-4, ascension 5) owes the world an echo. */
	@Override
	public void onLevelUp(ServerPlayer player, int level) {
		ChronoEchoes.owe(player);
	}

	/** Time Flow: in and out (Timeless and corrupted both). */
	@Override
	public void onMode(ServerPlayer player, boolean down) {
		if (down) {
			ChronoFlow.toggle(player);
		}
	}

	/** Chrono Punch. */
	@Override
	public void onUltimate(ServerPlayer player, boolean down) {
		if (!down) {
			return;
		}
		ChronoState state = state(player);
		int now = now(player);
		if (state.ultimateLeft(now) > 0) {
			return; // the client shows "not ready" itself
		}
		if (ChronoPunch.punch(player)) {
			state.ultimateReadyAt = now + ChronoPunch.COOLDOWN;
			state.ultimateTotal = ChronoPunch.COOLDOWN;
			state.synced = false;
			sync(player, state, now);
		}
	}

	/** Ends everything running (stasis, bloom, overclock, trail). Cooldowns are kept. */
	private static void stopAll(ServerPlayer player, ChronoState state) {
		if (state.stasis != null) {
			state.stasis.end();
			state.stasis = null;
		}
		state.bloom = null;
		state.trail = null;
		state.overclockLeft = 0;
		setOverclockModifier(player, false);
		ChronoSpeed.clear(player);
		state.synced = false;
	}

	@Override
	public void tick(ServerPlayer player) {
		ChronoState state = state(player);
		ServerLevel level = player.level();
		int now = now(player);
		ChronoMemory memory = ChronoMemory.of(player);

		// echoes close behind follow through dimension changes
		if (state.lastLevel != null && state.lastLevel != level && state.lastPos != null) {
			ChronoEchoes.follow(player, state.lastLevel, state.lastPos);
		}
		state.lastLevel = level;
		state.lastPos = player.position();
		ChronoEchoes.tick(player, memory);

		if (!player.isAlive() || player.isSpectator()) {
			if (state.stasis != null || state.bloom != null || state.overclockLeft > 0) {
				stopAll(player, state);
			}
			state.clearHistory();
			sync(player, state, now);
			return;
		}

		state.record(player.position(), player.getYRot(), player.getXRot(), player.getHealth(), level.dimension());
		remember(player, memory, level);
		sleep(player, state, memory, level);

		if (state.trail != null && !state.trail.tick()) {
			state.trail = null;
		}
		if (state.stasis != null && memory.stoppedTime < Integer.MAX_VALUE) {
			memory.stoppedTime++;
		}
		if (state.stasis != null && (state.stasis.level != level || !state.stasis.tick())) {
			if (state.stasis.ticksLeft > 0) {
				state.stasis.end(); // left the dimension
			}
			state.stasis = null;
			state.synced = false;
		}
		if (state.bloom != null && (state.bloom.level != level || !state.bloom.tick())) {
			state.bloom = null;
			state.synced = false;
		}
		if (state.bloom != null && player.tickCount % BLOOM_HEAL_EVERY == 0 && player.getHealth() < player.getMaxHealth()) {
			// your own wounds run forward too, to healed
			player.heal(BLOOM_HEAL);
			ChronoFx.particles(level, player, ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.3, 0.5, 0.3, 0.0);
		}
		ChronoFlow.tickPlayer(player);
		if (state.overclockLeft > 0) {
			state.overclockLeft--;
			overclockFx(player, level, state.overclockLeft);
			if (state.overclockLeft % OVERCLOCK_DRAG_EVERY == 0) {
				overclockDrag(player, level);
			}
			if (state.overclockLeft == 0) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 1.7F);
				state.synced = false;
			}
		}
		setOverclockModifier(player, state.overclockLeft > 0);
		ChronoSpeed.tick(player, level, state.overclockLeft > 0);
		sync(player, state, now);
	}

	/** Whether Overclock is running for {@code player} (server side). */
	static boolean overclocked(ServerPlayer player) {
		ChronoState state = STATES.get(player.getUUID());
		return state != null && state.overclockLeft > 0;
	}

	@Override
	public void onKey(ServerPlayer player, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		ChronoState state = state(player);
		int ability = key.ordinal();
		int now = now(player);
		if (ability == OVERCLOCK && state.overclockLeft > 0) {
			// V again mid-Overclock: phase through the wall ahead
			if (now >= state.phaseReadyAt && ChronoSpeed.phase(player)) {
				state.phaseReadyAt = now + ChronoSpeed.PHASE_GAP;
			}
			return;
		}
		if (state.cooldownLeft(ability, now) > 0) {
			return; // the client plays the "not ready" feedback itself
		}
		if (com.jaymospowers.core.Mastery.starterOnly(player)) {
			// a fresh awakening can only nudge time a little
			if (ability == REWIND && nudge(player)) {
				state.readyAt[ability] = now + NUDGE_COOLDOWN;
				state.cooldownTotal[ability] = NUDGE_COOLDOWN;
				state.synced = false;
				sync(player, state, now);
			}
			return;
		}
		boolean used = switch (ability) {
			case REWIND -> rewind(player, state);
			case STASIS -> stasis(player, state);
			case OVERCLOCK -> overclock(player, state);
			case BLOOM -> bloom(player, state);
			default -> false;
		};
		if (used) {
			int cooldown = cooldown(player, ability);
			state.readyAt[ability] = now + cooldown;
			state.cooldownTotal[ability] = cooldown;
			state.synced = false;
			sync(player, state, now);
		}
	}

	/** The starter, on R: time nudged slightly. */
	@Override
	public PowerKey starterKey() {
		return PowerKey.ABILITY_1;
	}

	/**
	 * The starter, Nudge: you're sped up slightly; sneaking, the creature under the crosshair (within
	 * {@code NUDGE_RANGE}) is slowed slightly instead (with nothing there, you're sped up after all).
	 */
	private static boolean nudge(ServerPlayer player) {
		ServerLevel level = player.level();
		net.minecraft.world.entity.LivingEntity target = player.isShiftKeyDown() ? lookedAt(player, NUDGE_RANGE) : null;
		if (target != null) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, NUDGE_TICKS, 0), player);
			ChronoFx.particles(level, player, ParticleTypes.ENCHANT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 20, 0.3, 0.4, 0.3, 0.4);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.4F, 0.6F);
		} else {
			player.addEffect(new MobEffectInstance(MobEffects.SPEED, NUDGE_TICKS, 0), player);
			ChronoFx.particles(level, player, ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.3, 0.5, 0.3, 0.4);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.4F, 1.6F);
		}
		return true;
	}

	private static net.minecraft.world.entity.@org.jspecify.annotations.Nullable LivingEntity lookedAt(ServerPlayer player, double range) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 end = eye.add(look.scale(range));
		net.minecraft.world.phys.BlockHitResult block = level(player).clip(new net.minecraft.world.level.ClipContext(eye, end,
			net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
		if (block.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
			end = block.getLocation();
		}
		net.minecraft.world.phys.EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, end,
			player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0), e -> e instanceof net.minecraft.world.entity.LivingEntity
				&& e.isAlive() && !e.isSpectator(), eye.distanceToSqr(end));
		return hit == null ? null : (net.minecraft.world.entity.LivingEntity)hit.getEntity();
	}

	private static ServerLevel level(ServerPlayer player) {
		return player.level();
	}

	/** An ability's cooldown: shorter for the Timeless (ascended). */
	private static int cooldown(ServerPlayer player, int ability) {
		return Fate.isAscended(player) ? (int) Math.round(COOLDOWNS[ability] / TIMELESS_COOLDOWN_RATE) : COOLDOWNS[ability];
	}

	// ---------------------------------------------------------------- habits and the clock

	/** Notes what the player holds and where they walk (for their echoes). */
	private static void remember(ServerPlayer player, ChronoMemory memory, ServerLevel level) {
		if (player.tickCount % HELD_SAMPLE_INTERVAL == 0) {
			ItemStack held = player.getMainHandItem();
			if (!held.isEmpty()) {
				ChronoMemory.count(memory.held, BuiltInRegistries.ITEM.getKey(held.getItem()));
			}
		}
		if (player.tickCount % PATH_SAMPLE_INTERVAL == 0 && player.onGround() && !player.getAbilities().flying && !player.isPassenger()) {
			ChronoMemory.PathPoint last = memory.lastPathPoint();
			if (last == null || last.dimension() != level.dimension()
				|| last.pos().distToCenterSqr(player.position()) > PATH_MIN_STEP * PATH_MIN_STEP) {
				memory.addPathPoint(player.blockPosition(), level.dimension());
			}
		}
	}

	/** Sleeping through to morning, having spent long enough outside time: they wake holding the Stopped Clock. */
	private static void sleep(ServerPlayer player, ChronoState state, ChronoMemory memory, ServerLevel level) {
		if (player.isSleeping()) {
			if (player.isSleepingLongEnough()) {
				state.restedFully = true;
			}
			return;
		}
		if (!state.restedFully) {
			return;
		}
		state.restedFully = false;
		if (!level.isBrightOutside() || memory.stoppedTime < ASCEND_STOPPED_TICKS || !Fate.canAscend(player)
			|| player.getInventory().contains(stack -> stack.is(StoppedClockItem.STOPPED_CLOCK))) {
			return;
		}
		ItemStack clock = new ItemStack(StoppedClockItem.STOPPED_CLOCK);
		if (player.getMainHandItem().isEmpty()) {
			player.setItemInHand(InteractionHand.MAIN_HAND, clock);
		} else if (!player.getInventory().add(clock)) {
			player.drop(clock, false, Prediction.SERVER_ONLY);
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.chrono_clock_woke").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
		ChronoEchoes.soundFor(player, SoundEvents.COMPARATOR_CLICK, 0.8F, 0.5F);
	}

	// ---------------------------------------------------------------- Rewind

	private static boolean rewind(ServerPlayer player, ChronoState state) {
		ServerLevel level = player.level();
		if (state.size() < REWIND_MIN_HISTORY) {
			return fail(player, "message.jaymospowers.chrono_rewind_short");
		}
		if (state.spansOtherDimension(level.dimension())) {
			return fail(player, "message.jaymospowers.chrono_rewind_tangled");
		}
		// the oldest recorded spot you'd fit into again (blocks may have been placed there since)
		int chosen = -1;
		Vec3 here = player.position();
		for (int age = state.size() - 1; age >= REWIND_MIN_HISTORY - 1; age--) {
			AABB box = player.getBoundingBox().move(state.pos(age).subtract(here));
			if (level.noCollision(player, box)) {
				chosen = age;
				break;
			}
		}
		if (chosen < 0) {
			return fail(player, "message.jaymospowers.chrono_rewind_blocked");
		}
		Vec3 target = state.pos(chosen);
		float pastHealth = state.health(chosen);
		var path = state.path(chosen);

		level.playSound(null, here.x, here.y, here.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0F, 1.5F);
		ChronoFx.particles(level, player, ParticleTypes.ELECTRIC_SPARK, here.x, here.y + 1.0, here.z, 16, 0.3, 0.6, 0.3, 0.05);

		if (player.isPassenger()) {
			player.stopRiding();
		}
		player.teleportTo(level, target.x, target.y, target.z, Set.of(), state.yRot(chosen), state.xRot(chosen), false);
		player.resetFallDistance();
		player.extinguishFire();
		if (pastHealth > player.getHealth()) {
			player.setHealth(Math.min(pastHealth, player.getMaxHealth()));
			ChronoFx.particles(level, player, ParticleTypes.HAPPY_VILLAGER, target.x, target.y + 1.0, target.z, 6, 0.3, 0.5, 0.3, 0.0);
		}
		level.playSound(null, target.x, target.y, target.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8F, 1.4F);

		ChronoMemory.of(player).stoppedTime += chosen; // time undone counts as time outside time
		state.clearHistory(); // the jump itself shouldn't be part of the next rewind
		state.trail = new RewindTrail(level, path, ChronoFx.style(player));
		return true;
	}

	private static boolean fail(ServerPlayer player, String key) {
		player.sendOverlayMessage(Component.translatable(key).withStyle(ChatFormatting.GOLD));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.COMPARATOR_CLICK, SoundSource.PLAYERS, 0.6F, 0.6F);
		return false;
	}

	// ---------------------------------------------------------------- Stasis

	private static boolean stasis(ServerPlayer player, ChronoState state) {
		if (state.stasis != null) {
			state.stasis.end();
		}
		state.stasis = new StasisField(player.level(), player.position(), player.getUUID(), StasisField.duration(player), ChronoFx.style(player));
		state.stasis.start();
		return true;
	}

	// ---------------------------------------------------------------- Overclock

	private static boolean overclock(ServerPlayer player, ChronoState state) {
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, OVERCLOCK_TICKS, OVERCLOCK_SPEED_AMPLIFIER, false, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.HASTE, OVERCLOCK_TICKS, 1, false, false, true));
		state.overclockLeft = OVERCLOCK_TICKS;
		setOverclockModifier(player, true);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 2.0F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.8F);
		ChronoFx.particles(level, player, ParticleTypes.WAX_ON, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.7, 0.4, 0.6);
		return true;
	}

	/**
	 * While overclocked, time around you drags: every creature and player close by is slowed, so your own speed stands
	 * out all the more. Refreshed as you move, so it follows you.
	 */
	private static void overclockDrag(ServerPlayer player, ServerLevel level) {
		AABB area = player.getBoundingBox().inflate(OVERCLOCK_DRAG_RADIUS);
		for (net.minecraft.world.entity.LivingEntity living : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, area,
			e -> e != player && e.isAlive() && !e.isSpectator() && e.distanceToSqr(player) <= OVERCLOCK_DRAG_RADIUS * OVERCLOCK_DRAG_RADIUS
				&& !(e instanceof net.minecraft.world.entity.decoration.ArmorStand))) {
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, OVERCLOCK_DRAG_EVERY + 10, OVERCLOCK_DRAG_AMPLIFIER, false, false, true), player);
			if (level.getRandom().nextInt(3) == 0) {
				ChronoFx.particles(level, player, ParticleTypes.ENCHANT, living.getX(), living.getY() + living.getBbHeight() * 0.6, living.getZ(), 3, 0.3, 0.3, 0.3, 0.2);
			}
		}
	}

	private static void setOverclockModifier(ServerPlayer player, boolean on) {
		AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
		if (attackSpeed == null || attackSpeed.hasModifier(OVERCLOCK_MODIFIER) == on) {
			return;
		}
		if (on) {
			// transient: never saved, so a crash or relog can't leave it behind
			attackSpeed.addTransientModifier(new AttributeModifier(OVERCLOCK_MODIFIER, OVERCLOCK_ATTACK_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else {
			attackSpeed.removeModifier(OVERCLOCK_MODIFIER);
		}
	}

	/** On losing the power mid-Overclock: take back our effects (only if they still look like ours). */
	private static void removeOverclockEffect(ServerPlayer player, Holder<MobEffect> effect, int amplifier, int left) {
		MobEffectInstance instance = player.getEffect(effect);
		if (instance != null && instance.getAmplifier() == amplifier && instance.getDuration() <= left + 1) {
			player.removeEffect(effect);
		}
	}

	private static void overclockFx(ServerPlayer player, ServerLevel level, int left) {
		if (left % 2 != 0) {
			return;
		}
		double a = left * 0.55;
		double r = 0.55;
		ChronoFx.particles(level, player, ParticleTypes.WAX_ON, player.getX() + Math.cos(a) * r, player.getY() + 0.3 + (left % 10) * 0.15,
			player.getZ() + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
		if (left % 6 == 0) {
			ChronoFx.particles(level, player, ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 0.2, player.getZ(), 2, 0.25, 0.1, 0.25, 0.02);
		}
		if (left % 20 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.COMPARATOR_CLICK, SoundSource.PLAYERS, 0.35F, 2.0F);
		}
	}

	// ---------------------------------------------------------------- Time Bloom

	private static boolean bloom(ServerPlayer player, ChronoState state) {
		state.bloom = new TimeBloom(player.level(), player.blockPosition(), ChronoFx.style(player));
		state.bloom.start();
		return true;
	}

	// ---------------------------------------------------------------- HUD sync

	private static void sync(ServerPlayer player, ChronoState state, int now) {
		boolean due = !state.synced || (now - state.lastSync >= SYNC_INTERVAL && state.anythingTicking(now));
		if (!due || !ServerPlayNetworking.canSend(player, ChronoSync.TYPE)) {
			return;
		}
		int[] cooldownLeft = new int[ChronoSync.ABILITIES];
		int[] activeLeft = new int[ChronoSync.ABILITIES];
		int[] activeTotal = new int[ChronoSync.ABILITIES];
		for (int i = 0; i < ChronoSync.ABILITIES; i++) {
			cooldownLeft[i] = state.cooldownLeft(i, now);
		}
		if (state.stasis != null) {
			activeLeft[STASIS] = state.stasis.ticksLeft;
			activeTotal[STASIS] = state.stasis.duration;
		}
		if (state.overclockLeft > 0) {
			activeLeft[OVERCLOCK] = state.overclockLeft;
			activeTotal[OVERCLOCK] = OVERCLOCK_TICKS;
		}
		if (state.bloom != null) {
			activeLeft[BLOOM] = state.bloom.ticksLeft;
			activeTotal[BLOOM] = TimeBloom.DURATION;
		}
		ServerPlayNetworking.send(player, new ChronoSync(cooldownLeft, state.cooldownTotal.clone(), activeLeft, activeTotal,
			state.ultimateLeft(now), state.ultimateTotal));
		state.synced = true;
		state.lastSync = now;
	}
}
