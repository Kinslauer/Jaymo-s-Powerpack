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
 * (0.20.42) Chrono cheats death. Andy: "I want Chrono to have the strongest potential, starting with the ability to cheat
 * death ... revert them the maximum, deleting items they gained [since] that time. no drops no corpse, no death." Then:
 * "they can still be executed, the player should not be prompted at all, this is all just possible for them now."
 *
 * <p>Once Rewind (R) is learned, time keeps a checkpoint of you every {@link #CHECKPOINT_EVERY} ticks while you stand
 * somewhere safe: where you were, how you looked, your health, hunger, experience, effects, and what you carried. A blow
 * that would kill you (after the downed state has had its say, before death by cause rolls) is undone instead, with no
 * asking: for a tick you're held out of time where you fell, then taken back to the oldest checkpoint your power reaches
 * ({@link #window}): one minute at first, up to seven for a Timeless Paragon. An execution (a finisher, a hostile's,
 * the symbiote's or a Z Fighter's, all through {@code Downed.execute}) still kills.
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

		Held(ServerPlayer player, long now) {
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

	private ChronoCheatDeath() {
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
		// after Downed's last word, before death by cause, the killing blow's dismemberment and the symbiote taking your things
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(JaymosPowers.id("downed_last"), PHASE);
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(PHASE, JaymosPowers.id("mortality"));
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(PHASE, JaymosPowers.id("wounds_killing_blow"));
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(PHASE, JaymosPowers.id("web_symbiote_life"));
		ServerLivingEntityEvents.ALLOW_DEATH.register(PHASE, ChronoCheatDeath::allowDeath);
		// out of time (the tick before the rewind), nothing touches you (but /kill still does)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!HELD.containsKey(entity.getUUID()) || !(entity instanceof ServerPlayer) || source.is(DamageTypes.GENERIC_KILL));
		ServerTickEvents.END_SERVER_TICK.register(ChronoCheatDeath::tick);
		// leaving in that tick: rewound all the same, there was never going to be a death
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			if (HELD.containsKey(id)) {
				rewind(handler.player);
			}
			HELD.remove(id);
			CHECKPOINTS.remove(id);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			// (no one is saved standing where they fell)
			for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
				if (HELD.containsKey(player.getUUID())) {
					rewind(player);
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

	/** Whether {@code player} is out of time, about to be rewound. */
	public static boolean held(LivingEntity entity) {
		return HELD.containsKey(entity.getUUID());
	}

	// ---- the checkpoints -----------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Held held = HELD.get(player.getUUID());
			if (held != null) {
				rewind(player);
				continue;
			}
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

	/** Somewhere worth coming back to: alive and well, on your feet on solid ground (or swimming), nothing burning or falling. */
	private static boolean safe(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && !player.isCreative() && !Downed.isDowned(player)
			&& (player.onGround() || player.isInWater() && player.getAirSupply() >= player.getMaxAirSupply())
			&& !player.isInLava() && !player.isOnFire() && player.fallDistance < 3.0 && player.getHealth() >= MIN_HEALTH
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
		if (!(entity instanceof ServerPlayer player) || source.is(DamageTypes.GENERIC_KILL) || Mortality.executing(entity)) {
			// (an execution ends you all the same; Andy: "they can still be executed")
			HELD.remove(entity.getUUID());
			return true;
		}
		if (HELD.containsKey(player.getUUID())) {
			player.setHealth(1.0F);
			return false;
		}
		if (!has(player) || player.isCreative() || player.isSpectator() || Mortality.isFinal(player) || ZFighterAfterlife.among(player)
			|| pick(player, player.level().getServer().overworld().getGameTime()) == null) {
			return true;
		}
		// the fall the downed state noted (for death by cause) never comes now
		Mortality.spare(player);
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
		HELD.put(player.getUUID(), new Held(player, now));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0F, 0.6F);
		ChronoFx.particles(level, player, ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.8, 0.4, 0.02);
		JaymosPowers.LOGGER.info("{} cheated death ({})", player.getScoreboardName(), source.getMsgId());
		return false;
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
		MinecraftServer server = player.level().getServer();
		int window = window(player);
		for (Checkpoint point : points) {
			if (died - point.time() > window) {
				continue;
			}
			ServerLevel level = server.getLevel(point.dimension());
			if (level == null) {
				continue;
			}
			AABB box = player.getDimensions(net.minecraft.world.entity.Pose.STANDING).makeBoundingBox(point.pos());
			if (level.noCollision(player, box)) {
				return point;
			}
		}
		return null;
	}

	/** R: back to the checkpoint, as if none of it happened. */
	static void rewind(ServerPlayer player) {
		Held held = HELD.remove(player.getUUID());
		if (held == null) {
			return;
		}
		player.setInvisible(held.wasInvisible);
		Checkpoint point = pick(player, held.since);
		if (point == null) {
			// (every place you'd go back to is built over now: you rise where you fell)
			point = checkpoint(player, held.since);
		}
		ServerLevel from = player.level();
		ServerLevel to = from.getServer().getLevel(point.dimension());
		if (to == null) {
			to = from;
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
