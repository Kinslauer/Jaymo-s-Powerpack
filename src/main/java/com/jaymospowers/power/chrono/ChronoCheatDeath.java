package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.core.wounds.Mortality;
import com.jaymospowers.core.wounds.Wounds;
import com.jaymospowers.power.zfighter.ZFighterAfterlife;
import io.netty.buffer.ByteBuf;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.50) Chrono cheats death. Andy: "I want Chrono to have the strongest potential, starting with the ability to cheat
 * death ... they should be prompted with R upon ... dying, that black screen would then revert them the maximum, deleting
 * items they gained [since] that time. no drops no corpse, no death." Then: "they can still be executed", and "they have
 * to press R, if they dont they still die."
 *
 * <p>Once Rewind (R) is learned, time keeps a checkpoint of you every {@link #CHECKPOINT_EVERY} ticks while you stand
 * somewhere safe: where you were, how you looked, your health, hunger, experience, effects, and what you carried.
 * Andy (2026-11-04): "the Chrono is NOT told anywhere that they can revive here, it just is possible during the regular
 * death animation or wakeup or whatever happens, they have a window now, no warning", "make it 4s". So nothing is shown:
 * <ul>
 * <li>A death (after the downed state and death by cause have had their say) is held back {@link #WINDOW_TICKS} ticks
 * while you see it as any death (the death screen and its message, sent as the real ones are), held unseen where you
 * fell. Pressing R then rewinds you. Otherwise (the time runs out, Respawn is clicked, you leave) the death goes ahead
 * exactly as decided ({@code Downed.letDie}, not rolled again): corpse, drops, final death and all.</li>
 * <li>Left for dead instead (death by cause), the same {@link #WINDOW_TICKS} once you're out: R then rewinds you and
 * there's no waking anywhere.</li>
 * </ul>
 * R takes you back to the oldest checkpoint your power reaches ({@link #window}): one minute at first, up to seven for a
 * Timeless Paragon. An execution's killing blow (a finisher, a hostile's, the symbiote's or a Z Fighter's, all through
 * {@code Downed.execute}) and /kill still kill, with no window after. But (Andy: "you can still use the R, before the
 * fatal damage like the cancel, and the executioner will be perplexed and react in their own way") R works all the while
 * an execution is coming or under way: you're gone from under them, a hostile left staring at the empty ground
 * ({@link #perplexed}), a player's finisher stopped as when anyone is spared.
 *
 * <p>Rewound, nothing of the death happened: no drops, no corpse, no death counted, no final death or waking in the wild.
 * Body wounds are undone (Time Bloom's healing, run to the end). Whatever you gained since the checkpoint is gone: of every
 * kind of item you keep at most what you carried then, and experience never comes back higher than it was. What you lost
 * meanwhile (stored, dropped, used up) stays lost, so nothing is ever doubled. Writs and the Judge still rewrite a story as
 * before; this is only for deaths.
 *
 * <p>The client ({@code ChronoCheatDeathClient}), with Respawn Recap installed (MIT, shipped unchanged), plays its memory
 * replay as the rewind ({@link Rewound}); that replay is Chrono's alone, never shown for anyone's real death.
 */
public final class ChronoCheatDeath {
	// ---- tuning ----
	/** A checkpoint this often (5 s), whenever you're somewhere safe to come back to. */
	static final int CHECKPOINT_EVERY = 100;
	/** How far back the rewind reaches: this much once Rewind is learned... */
	static final int BASE_WINDOW = 60 * 20;
	/** ...this much more for each further ability learned... */
	static final int PER_ABILITY = 30 * 20;
	/** ...this much more for each Mastery rank (Adept to Paragon)... */
	static final int PER_RANK = 30 * 20;
	/** ...and this much more Timeless (ascended). Seven minutes in all at the very top. */
	static final int TIMELESS_BONUS = 120 * 20;
	/** The longest any window can be: checkpoints older than this are dropped. */
	static final int MAX_WINDOW = BASE_WINDOW + PER_ABILITY * (Mastery.ALL - 1) + PER_RANK * 5 + TIMELESS_BONUS;
	/** How long a death (or a waking) stays open to R: 4 s (Andy). */
	static final int WINDOW_TICKS = 80;
	/** (0.20.117) Never back within this many blocks of where you fell (Andy: "at least 12 blocks away")... */
	static final double AWAY = 12.0;
	/** ...and no further than this, when no checkpoint is far enough. */
	static final double AWAY_MAX = 20.0;
	/** Back from a rewind with at least this much health. */
	static final float MIN_HEALTH = 6.0F;

	/** Our death phase: after the downed state (which puts players down rather than letting them die)... */
	static final Identifier PHASE = JaymosPowers.id("chrono_cheat_death");

	private record Checkpoint(long time, ResourceKey<Level> dimension, Vec3 pos, float yRot, float xRot, float health, int food,
		float saturation, int xpLevel, float xpProgress, int xpTotal, List<MobEffectInstance> effects, ItemStack[] slots) {
	}

	private static final class Held {
		final ResourceKey<Level> dimension;
		final Vec3 pos;
		final boolean wasInvisible;
		final long since;
		/** What the death was coming from (credited when it goes ahead). */
		final @Nullable DamageSource felled;
		/** Left for dead (death by cause) rather than dying: nothing held, the waking cancelled by R. */
		final boolean wake;

		Held(ServerPlayer player, long now, @Nullable DamageSource felled, boolean wake) {
			this.felled = felled;
			this.wake = wake;
			this.dimension = player.level().dimension();
			this.pos = player.position();
			this.wasInvisible = player.isInvisible();
			this.since = now;
		}
	}

	/** Player → their checkpoints, oldest first. Not saved (a fresh window after every log-in). */
	private static final Map<UUID, ArrayDeque<Checkpoint>> CHECKPOINTS = new HashMap<>();
	/** Players held out of time at the black screen, waiting for R. */
	private static final Map<UUID, Held> HELD = new HashMap<>();
	/**
	 * (0.20.50) Another mod's say on a perplexed executioner ({@link #perplexed}), given it and the one it lost: Jaymo's
	 * Kingdoms has its humanoids speak of it. Added to at start-up.
	 */
	public static final List<java.util.function.BiConsumer<net.minecraft.world.entity.Mob, ServerPlayer>> PERPLEXED = new ArrayList<>();

	/** Players left for dead whose window has been opened (once a waking). */
	private static final Set<UUID> WAKES_OFFERED = new java.util.HashSet<>();
	/** Players whose death is going ahead right now (no R): let through. */
	private static final Set<UUID> LETTING_GO = new java.util.HashSet<>();

	private ChronoCheatDeath() {
	}

	/** Server → client, silent: R is open for {@code ticks} (0: closed). Nothing is drawn for it. */
	public record Window(int ticks) implements CustomPacketPayload {
		public static final Type<Window> TYPE = new Type<>(JaymosPowers.id("chrono_cheat_death_window"));
		public static final StreamCodec<ByteBuf, Window> CODEC = ByteBufCodecs.VAR_INT.map(Window::new, Window::ticks);

		@Override
		public Type<Window> type() {
			return TYPE;
		}
	}

	/** Client → server: R inside the window. */
	public record Rewind() implements CustomPacketPayload {
		public static final Type<Rewind> TYPE = new Type<>(JaymosPowers.id("chrono_cheat_death_rewind"));
		public static final StreamCodec<ByteBuf, Rewind> CODEC = StreamCodec.unit(new Rewind());

		@Override
		public Type<Rewind> type() {
			return TYPE;
		}
	}

	/** Server → client: your death was rewound, {@code seconds} back (the client plays Respawn Recap's replay over it). */
	public record Rewound(int seconds) implements CustomPacketPayload {
		public static final Type<Rewound> TYPE = new Type<>(JaymosPowers.id("chrono_cheat_death"));
		public static final StreamCodec<ByteBuf, Rewound> CODEC = ByteBufCodecs.VAR_INT.map(Rewound::new, Rewound::seconds);

		@Override
		public Type<Rewound> type() {
			return TYPE;
		}
	}

	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Rewound.TYPE, Rewound.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Window.TYPE, Window.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Rewind.TYPE, Rewind.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Rewind.TYPE, (payload, ctx) -> pressed(ctx.player()));
		// after Downed's last word and death by cause's roll, before the killing blow's dismemberment and the symbiote
		// taking your things
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(JaymosPowers.id("downed_last"), PHASE);
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(JaymosPowers.id("mortality"), PHASE);
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(PHASE, JaymosPowers.id("wounds_killing_blow"));
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(PHASE, JaymosPowers.id("web_symbiote_life"));
		ServerLivingEntityEvents.ALLOW_DEATH.register(PHASE, ChronoCheatDeath::allowDeath);
		// held back from death, nothing touches you (but /kill still does)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			Held held = entity instanceof ServerPlayer ? HELD.get(entity.getUUID()) : null;
			return held == null || held.wake || source.is(DamageTypes.GENERIC_KILL);
		});
		ServerTickEvents.END_SERVER_TICK.register(ChronoCheatDeath::tick);
		// leaving inside the window: no R, so the death goes ahead
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			if (HELD.containsKey(id)) {
				letDie(handler.player);
			}
			HELD.remove(id);
			CHECKPOINTS.remove(id);
			WAKES_OFFERED.remove(id);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			// (no one is saved held back from death: as leaving, the death goes ahead)
			for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
				if (HELD.containsKey(player.getUUID())) {
					letDie(player);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HELD.clear();
			CHECKPOINTS.clear();
		});
	}

	// ---- who has it --------------------------------------------------------------------------------------------------

	/** Whether {@code player} can cheat death: a Chrono who has learned Rewind. */
	public static boolean has(ServerPlayer player) {
		return Powers.has(player, ChronoPower.INSTANCE) && !Mastery.starterOnly(player) && Mastery.isUnlocked(player, PowerKey.ABILITY_1);
	}

	/** How far back (ticks) {@code player}'s rewind reaches, by how far their power has come. */
	public static int window(ServerPlayer player) {
		int window = BASE_WINDOW + PER_ABILITY * Math.max(0, Mastery.unlocked(player) - 1) + PER_RANK * Mastery.rank(player);
		if (Fate.isAscended(player)) {
			window += TIMELESS_BONUS;
		}
		return Math.min(window, MAX_WINDOW);
	}

	/** Whether {@code player} is held back from a death, inside the window. */
	public static boolean held(LivingEntity entity) {
		return HELD.containsKey(entity.getUUID());
	}

	// ---- the checkpoints -----------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Held held = HELD.get(player.getUUID());
			if (held != null) {
				if (now - held.since >= WINDOW_TICKS || held.wake && !Mortality.waking(player)) {
					letDie(player);
				} else if (!held.wake) {
					hold(player, held, now);
				}
				continue;
			}
			if (now % 20 == 0 && has(player) && downedOpen(player)) {
				// an execution coming or under way, or down where Rewind itself can't answer: R stays open (silently)
				ServerPlayNetworking.send(player, new Window(30));
			}
			if (Mortality.waking(player)) {
				// left for dead: the window opens once, as they go out
				if (has(player) && WAKES_OFFERED.add(player.getUUID())) {
					HELD.put(player.getUUID(), new Held(player, now, null, true));
					ServerPlayNetworking.send(player, new Window(WINDOW_TICKS));
				}
				continue;
			}
			WAKES_OFFERED.remove(player.getUUID());
			if (now % 20 != player.getId() % 20 && !CHECKPOINTS.containsKey(player.getUUID())) {
				continue; // (no checkpoints yet: look once a second, spread over players)
			}
			if (!has(player)) {
				CHECKPOINTS.remove(player.getUUID());
				continue;
			}
			ArrayDeque<Checkpoint> points = CHECKPOINTS.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
			Checkpoint last = points.peekLast();
			if ((last == null || now - last.time() >= CHECKPOINT_EVERY) && safe(player)) {
				points.addLast(checkpoint(player, now));
				while (!points.isEmpty() && now - points.peekFirst().time() > MAX_WINDOW) {
					points.removeFirst();
				}
			}
		}
	}

	/**
	 * Somewhere worth coming back to: alive and up, on your feet on solid ground (or swimming), nothing burning or falling.
	 * (0.20.92) However hurt: a long fight below three hearts (or with wounds keeping your most health low) left no
	 * checkpoint at all, so a death had nothing to go back to and went ahead; the rewind gives back at least
	 * {@link #MIN_HEALTH} anyway.
	 */
	private static boolean safe(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && !player.isCreative() && !Downed.isDowned(player)
			&& (player.onGround() || player.isInWater() && player.getAirSupply() >= player.getMaxAirSupply())
			&& !player.isInLava() && !player.isOnFire() && player.fallDistance < 3.0
			&& !player.level().getBlockState(player.blockPosition()).is(net.minecraft.world.level.block.Blocks.POWDER_SNOW);
	}

	private static Checkpoint checkpoint(ServerPlayer player, long now) {
		List<MobEffectInstance> effects = new ArrayList<>();
		for (MobEffectInstance effect : player.getActiveEffects()) {
			effects.add(new MobEffectInstance(effect));
		}
		Inventory inventory = player.getInventory();
		ItemStack[] slots = new ItemStack[inventory.getContainerSize()];
		for (int i = 0; i < slots.length; i++) {
			slots[i] = inventory.getItem(i).copy();
		}
		return new Checkpoint(now, player.level().dimension(), player.position(), player.getYRot(), player.getXRot(), player.getHealth(),
			player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel(), player.experienceLevel, player.experienceProgress,
			player.totalExperience, effects, slots);
	}

	// ---- the death that isn't ------------------------------------------------------------------------------------------

	private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || source.is(DamageTypes.GENERIC_KILL) || Mortality.executing(entity)
			|| LETTING_GO.contains(entity.getUUID())) {
			// (an execution ends you all the same; Andy: "they can still be executed")
			HELD.remove(entity.getUUID());
			return true;
		}
		if (HELD.containsKey(player.getUUID())) {
			player.setHealth(1.0F);
			return false;
		}
		if (!has(player) || player.isCreative() || player.isSpectator() || ZFighterAfterlife.among(player)) {
			return true;
		}
		// (an open chest or bag closes, what was on the cursor back into your pockets)
		player.closeContainer();
		if (player.isPassenger()) {
			player.stopRiding();
		}
		player.setHealth(1.0F);
		player.clearFire();
		player.resetFallDistance();
		player.setDeltaMovement(Vec3.ZERO);
		ServerLevel level = player.level();
		long now = level.getServer().overworld().getGameTime();
		HELD.put(player.getUUID(), new Held(player, now, source, false));
		player.setInvisible(true);
		// to them it's a death like any other: health 0 and the death screen with its message (the real 1 health is
		// noted as sent, so it isn't sent over it)
		((com.jaymospowers.power.chrono.mixin.ChronoServerPlayerAccessor) player).jaymospowers$setLastSentHealth(player.getHealth());
		player.connection.send(new ClientboundSetHealthPacket(0.0F, player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel()));
		player.connection.send(new ClientboundPlayerCombatKillPacket(player.getId(), source.getLocalizedDeathMessage(player)));
		ServerPlayNetworking.send(player, new Window(WINDOW_TICKS));
		JaymosPowers.LOGGER.info("{} died ({}), held back {} ticks for R", player.getScoreboardName(), source.getMsgId(), WINDOW_TICKS);
		return false;
	}

	/** Out of time: unhurt, unmoving, breathing, held where you fell. */
	private static void hold(ServerPlayer player, Held held, long now) {
		if (player.getHealth() < 1.0F) {
			player.setHealth(1.0F);
		}
		player.clearFire();
		player.resetFallDistance();
		player.setAirSupply(player.getMaxAirSupply());
		player.setDeltaMovement(Vec3.ZERO);
		if (player.level().dimension() == held.dimension && player.position().distanceToSqr(held.pos) > 0.01) {
			player.teleportTo(player.level(), held.pos.x, held.pos.y, held.pos.z, Set.of(), player.getYRot(), player.getXRot(), false);
		}
	}

	/** Whether someone is coming to execute {@code player}, or doing it (before the killing blow). */
	static boolean executed(ServerPlayer player) {
		return Downed.isDowned(player) && (com.jaymospowers.core.wounds.MobExecutions.doomed(player)
			|| com.jaymospowers.core.finisher.Finishers.targeted(player) || com.jaymospowers.power.web.WebExecutions.targeted(player));
	}

	/**
	 * (0.20.92) Down, R goes to cheating death whenever Rewind itself can't answer it: out cold, on its cooldown, or
	 * someone coming to finish you. Andy spammed R lying down and out cold and nothing happened, then died for good.
	 * (Down with Rewind ready and awake, R is Rewind's own, as since 0.15.83.)
	 */
	static boolean downedOpen(ServerPlayer player) {
		return Downed.isDowned(player) && (Downed.passedOut(player) || executed(player) || !ChronoPower.rewindReady(player));
	}

	/** R from the client: inside a held death or a waking, or down where Rewind can't answer (out cold, an execution). */
	static void pressed(ServerPlayer player) {
		if (HELD.containsKey(player.getUUID())) {
			rewind(player);
			return;
		}
		if (!has(player) || !downedOpen(player) || !player.isAlive()) {
			return;
		}
		long now = player.level().getServer().overworld().getGameTime();
		boolean execution = executed(player);
		net.minecraft.world.entity.Mob mob = com.jaymospowers.core.wounds.MobExecutions.executioner(player);
		Vec3 was = player.position();
		HELD.put(player.getUUID(), new Held(player, now, null, true));
		rewind(player);
		if (execution && mob != null && mob.isAlive()) {
			perplexed(mob, was);
			for (java.util.function.BiConsumer<net.minecraft.world.entity.Mob, ServerPlayer> say : PERPLEXED) {
				try {
					say.accept(mob, player);
				} catch (RuntimeException e) {
					JaymosPowers.LOGGER.warn("A perplexed executioner's hook failed", e);
				}
			}
		}
		JaymosPowers.LOGGER.info("{} slipped {}", player.getScoreboardName(), execution ? "an execution" : "being down");
	}

	/**
	 * A hostile whose victim was gone from under it: it stops dead over the empty ground, looks about and gives voice its
	 * own way, then goes back to whatever it was doing ({@code MobExecutions} lets it go and won't let it try again soon).
	 */
	static void perplexed(net.minecraft.world.entity.Mob mob, Vec3 was) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}
		mob.getNavigation().stop();
		mob.setTarget(null);
		mob.setAggressive(false);
		mob.getLookControl().setLookAt(was.x, was.y, was.z);
		float turn = (mob.getRandom().nextBoolean() ? 1 : -1) * (60.0F + mob.getRandom().nextFloat() * 60.0F);
		mob.setYRot(mob.getYRot() + turn);
		mob.setYHeadRot(mob.getYRot());
		mob.playAmbientSound();
		level.sendParticles(ParticleTypes.POOF, was.x, was.y + 0.3, was.z, 10, 0.3, 0.1, 0.3, 0.01);
		level.playSound(null, was.x, was.y, was.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.6F, 1.6F);
	}

	/** Respawn clicked on the death screen inside the window: the death goes ahead now (and the respawn follows it). */
	public static void respawnPressed(ServerPlayer player) {
		Held held = HELD.get(player.getUUID());
		if (held != null && !held.wake) {
			letDie(player);
		}
	}

	/** No R: the death goes ahead as it was decided (not rolled again; the body falls, things drop). Left for dead, they wake. */
	private static void letDie(ServerPlayer player) {
		Held held = HELD.remove(player.getUUID());
		if (held == null) {
			return;
		}
		if (ServerPlayNetworking.canSend(player, Window.TYPE)) {
			ServerPlayNetworking.send(player, new Window(0));
		}
		if (held.wake) {
			return;
		}
		player.setInvisible(held.wasInvisible);
		Mortality.letThrough(player);
		LETTING_GO.add(player.getUUID());
		try {
			Downed.letDie(player.level(), player, held.felled);
		} finally {
			LETTING_GO.remove(player.getUUID());
		}
		JaymosPowers.LOGGER.info("{} let the rewind pass", player.getScoreboardName());
	}

	/**
	 * The oldest checkpoint inside {@code player}'s window, counted back from {@code died}, that they'd fit back into
	 * (blocks may stand there now).
	 */
	private static @Nullable Checkpoint pick(ServerPlayer player, long died) {
		ArrayDeque<Checkpoint> points = CHECKPOINTS.get(player.getUUID());
		if (points == null || points.isEmpty()) {
			return null;
		}
		int window = window(player);
		for (Checkpoint point : points) {
			if (died - point.time() <= window && fits(player, point)) {
				return point;
			}
		}
		// (0.20.92) none inside the window: the newest one older than it, rather than none
		for (java.util.Iterator<Checkpoint> it = points.descendingIterator(); it.hasNext();) {
			Checkpoint point = it.next();
			if (fits(player, point)) {
				return point;
			}
		}
		return null;
	}

	/**
	 * (0.20.117) Somewhere to stand {@link #AWAY} to {@link #AWAY_MAX} blocks from {@code fell}, on loaded ground, away from
	 * whatever was on them if it can be; null if nowhere fits.
	 */
	static @Nullable Vec3 away(ServerLevel level, Vec3 fell, ServerPlayer player) {
		net.minecraft.util.RandomSource random = player.getRandom();
		net.minecraft.world.entity.Mob mob = com.jaymospowers.core.wounds.MobExecutions.executioner(player);
		// (first facing away from the one on them, then anywhere)
		double from = mob != null ? Math.atan2(fell.z - mob.getZ(), fell.x - mob.getX()) : random.nextDouble() * Math.PI * 2;
		for (int i = 0; i < 48; i++) {
			double spread = i < 16 ? Math.PI / 2 : Math.PI;
			double angle = from + (random.nextDouble() * 2 - 1) * spread;
			double r = AWAY + random.nextDouble() * (AWAY_MAX - AWAY);
			int x = net.minecraft.util.Mth.floor(fell.x + Math.cos(angle) * r);
			int z = net.minecraft.util.Mth.floor(fell.z + Math.sin(angle) * r);
			net.minecraft.core.BlockPos feet = Mortality.ground(level, x, z, false);
			if (feet == null || Math.abs(feet.getY() - fell.y) > 24) {
				continue;
			}
			Vec3 at = new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
			if (at.distanceToSqr(fell) >= AWAY * AWAY
				&& level.noCollision(player, player.getDimensions(net.minecraft.world.entity.Pose.STANDING).makeBoundingBox(at))) {
				return at;
			}
		}
		// (last: straight out the given way, made safe)
		Vec3 out = fell.add(Math.cos(from) * AWAY, 0, Math.sin(from) * AWAY);
		net.minecraft.core.BlockPos safe = Mortality.safe(level, net.minecraft.core.BlockPos.containing(out));
		Vec3 at = new Vec3(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
		return at.distanceToSqr(fell) >= (AWAY - 2) * (AWAY - 2) ? at : null;
	}

	private static boolean fits(ServerPlayer player, Checkpoint point) {
		ServerLevel level = player.level().getServer().getLevel(point.dimension());
		return level != null && level.noCollision(player, player.getDimensions(net.minecraft.world.entity.Pose.STANDING).makeBoundingBox(point.pos()));
	}

	/** R: back to the checkpoint, as if none of it happened. */
	static void rewind(ServerPlayer player) {
		Held held = HELD.remove(player.getUUID());
		if (held == null) {
			return;
		}
		if (held.wake) {
			// no waking anywhere: up, and back
			Mortality.cancelWake(player);
		} else {
			player.setInvisible(held.wasInvisible);
		}
		// (a death decided final is undone with the rest, however it came: (0.20.117) a second death while out cold was
		// decided final under the waking's window, and its rebirth went ahead after the R)
		Mortality.undoFinal(player);
		WAKES_OFFERED.remove(player.getUUID());
		Checkpoint point = pick(player, held.since);
		if (point == null) {
			// (no checkpoint yet, or every place you'd go back to is built over now: mended, as now)
			point = checkpoint(player, held.since);
		}
		ServerLevel from = player.level();
		ServerLevel to = from.getServer().getLevel(point.dimension());
		if (to == null) {
			to = from;
		}
		// (0.20.117) Andy: "they should revert as far back as possible everytime, and if not, at least 12 blocks away":
		// never back up where they fell
		if (to.dimension() == held.dimension && point.pos().distanceToSqr(held.pos) < AWAY * AWAY) {
			Vec3 away = away(to, held.pos, player);
			if (away != null) {
				point = new Checkpoint(point.time(), point.dimension(), away, point.yRot(), point.xRot(), point.health(), point.food(),
					point.saturation(), point.xpLevel(), point.xpProgress(), point.xpTotal(), point.effects(), point.slots());
			}
		}
		from.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0F, 0.8F);
		ChronoFx.particles(from, player, ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.3, 0.6, 0.3, 0.05);

		if (Downed.isDowned(player)) {
			Downed.undo(from, player);
		}
		if (player.isPassenger()) {
			player.stopRiding();
		}
		player.teleportTo(to, point.pos().x, point.pos().y, point.pos().z, Set.of(), point.yRot(), point.xRot(), false);
		player.setDeltaMovement(Vec3.ZERO);
		player.resetFallDistance();
		player.clearFire();
		player.setAirSupply(player.getMaxAirSupply());

		// the body as it was: wounds undone, then the health, hunger and effects of then
		Wounds.undoAll(player);
		player.setHealth(Math.min(player.getMaxHealth(), Math.max(point.health(), MIN_HEALTH)));
		Wounds.reconcile(player);
		player.getFoodData().setFoodLevel(point.food());
		player.getFoodData().setSaturation(point.saturation());
		player.removeAllEffects();
		for (MobEffectInstance effect : point.effects()) {
			player.addEffect(new MobEffectInstance(effect));
		}
		// experience gained since is gone (never given back, so none is ever doubled)
		if (player.experienceLevel > point.xpLevel() || player.experienceLevel == point.xpLevel() && player.experienceProgress > point.xpProgress()) {
			player.setExperienceLevels(point.xpLevel()); // (and the bar is sent again next tick)
			player.experienceProgress = point.xpProgress();
			player.totalExperience = point.xpTotal();
		}
		trimGains(player, point.slots());
		player.containerMenu.broadcastChanges();

		// a clean slate: the jump itself is no part of the next one
		ArrayDeque<Checkpoint> points = CHECKPOINTS.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
		points.clear();
		points.addLast(checkpoint(player, to.getServer().overworld().getGameTime()));
		ChronoMemory.of(player).stoppedTime += (int) Math.min(Integer.MAX_VALUE / 2, Math.max(0, held.since - point.time()));

		to.playSound(null, point.pos().x, point.pos().y, point.pos().z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0F, 0.9F);
		ChronoFx.particles(to, player, ParticleTypes.END_ROD, point.pos().x, point.pos().y + 1.0, point.pos().z, 30, 0.4, 0.8, 0.4, 0.02);
		int seconds = (int) Math.max(0, (held.since - point.time()) / 20);
		ServerPlayNetworking.send(player, new Rewound(seconds));
		JaymosPowers.LOGGER.info("{} rewound {} s to cheat death", player.getScoreboardName(), seconds);
	}

	/**
	 * Of every kind of item, keep at most what was carried at the checkpoint ({@code then}): first in the slots that held
	 * that kind then, then anywhere else; the rest is gone. Matched by item alone, so a sword worn down since is still yours.
	 */
	static void trimGains(ServerPlayer player, ItemStack[] then) {
		Inventory inventory = player.getInventory();
		Map<Item, Integer> allowed = new HashMap<>();
		for (ItemStack stack : then) {
			if (!stack.isEmpty()) {
				allowed.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
		int size = inventory.getContainerSize();
		int[] keep = new int[size];
		// first pass: what sits where it sat
		for (int i = 0; i < size; i++) {
			ItemStack now = inventory.getItem(i);
			if (now.isEmpty() || i >= then.length || then[i].isEmpty() || then[i].getItem() != now.getItem()) {
				continue;
			}
			int left = allowed.getOrDefault(now.getItem(), 0);
			keep[i] = Math.min(left, Math.min(now.getCount(), then[i].getCount()));
			allowed.put(now.getItem(), left - keep[i]);
		}
		// second pass: the rest, wherever it ended up
		for (int i = 0; i < size; i++) {
			ItemStack now = inventory.getItem(i);
			if (now.isEmpty()) {
				continue;
			}
			int left = allowed.getOrDefault(now.getItem(), 0);
			int more = Math.min(left, now.getCount() - keep[i]);
			keep[i] += more;
			allowed.put(now.getItem(), left - more);
			if (keep[i] <= 0) {
				inventory.setItem(i, ItemStack.EMPTY);
			} else if (keep[i] < now.getCount()) {
				now.setCount(keep[i]);
			}
		}
	}

	static void forget(ServerPlayer player) {
		Held held = HELD.remove(player.getUUID());
		if (held != null) {
			player.setInvisible(held.wasInvisible);
		}
		CHECKPOINTS.remove(player.getUUID());
	}

}
