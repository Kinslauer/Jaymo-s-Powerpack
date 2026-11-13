package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Six steps of exhaustion (fifth edition's Exhaustion, Andy's pick B3: "we also pass out from hunger but the progression
 * here is better"). A body worn past what it can take gains a step; each step is felt, and they add up:
 * <ol>
 * <li>heavy legs: a little slower afoot;</li>
 * <li>slow hands: digging and chopping go slower;</li>
 * <li>weak arms: blows land softer and come slower;</li>
 * <li>tunnel vision: the edges of the sight go dark (the faint's own darkness, {@code FaintHud});</li>
 * <li>stumbling: they may swoon and go down where they stand at any moment, as a starving body does ({@link Fainting});</li>
 * <li>collapse: they drop where they stand and lie out cold ({@link Downed#knockOut}); never death.</li>
 * </ol>
 * Steps come from going without sleep (the first after two days awake, one more each day after), from running flat out
 * for {@link #SPRINT_STEP} without a breather, from going {@link #STARVE_STEP} near empty of food or water (the
 * hunger's own faints now come from this ladder), and from {@link #COLD_STEP} frozen through. A night's sleep takes the
 * sleepless steps away and one other besides; lying out from a collapse is worth half a night.
 */
public final class Exhaustion {
	/** The most steps there are; at this many they collapse. */
	public static final int MAX = 6;
	/** Awake this long (ticks) is the first sleepless step; every {@link #DAY} more is another. */
	static final int AWAKE_FIRST = 48000, DAY = 24000;
	/** Sprinting this long without a breather (ticks) is a step; a breather is {@link #BREATHER} without sprinting. */
	static final int SPRINT_STEP = 20 * 60 * 3, BREATHER = 20 * 10;
	/** Near empty (three food or water or less) this long is a step (ticks). */
	static final int STARVE_STEP = 20 * 60 * 2;
	/** Frozen through this long is a step (ticks). */
	static final int COLD_STEP = 20 * 60;
	/** Out cold from a collapse for this long (ticks). */
	static final int COLLAPSE_TICKS = 20 * 60 * 2;
	/** What each step takes: speed afoot, digging, blows (damage and speed). Shares, multiplied in. */
	static final double SLOW = -0.06, DIG = -0.25, BLOW = -0.12;
	/** How dark the edges go from the fourth step, and how faint the fifth makes them (the faint's own weakness, 0-1). */
	static final float TUNNEL = 0.35F, STUMBLE = 0.6F;

	/** Steps from strain (running, starving, the cold), kept; the sleepless ones are worked out from the time awake. */
	public static final AttachmentType<Integer> STRAIN = AttachmentRegistry.create(JaymosPowers.id("exhaustion_strain"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());
	/** All the steps a player is on just now (0-6), synced to them for the darkness at the edges. Not kept. */
	public static final AttachmentType<Integer> STEPS = AttachmentRegistry.create(JaymosPowers.id("exhaustion_steps"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	private static final Identifier SLOW_ID = JaymosPowers.id("exhaustion_slow"), DIG_ID = JaymosPowers.id("exhaustion_dig"),
		BLOW_ID = JaymosPowers.id("exhaustion_blow"), PACE_ID = JaymosPowers.id("exhaustion_pace");

	/** Server, by player: ticks sprinted, ticks since the last sprint, ticks near empty, ticks frozen, the time awake last seen. */
	private static final Map<UUID, int[]> COUNTS = new HashMap<>();

	private Exhaustion() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Exhaustion::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> COUNTS.clear());
	}

	/** The steps {@code player} is on (0-6), on either side. */
	public static int steps(Player player) {
		Integer i = player.getAttached(STEPS);
		return i == null ? 0 : i;
	}

	/** The faint's weakness (0-1) the steps alone give: dark edges from the fourth, swooning from the fifth. Either side. */
	public static float weakness(Player player) {
		int s = steps(player);
		return s >= 5 ? STUMBLE : s >= 4 ? TUNNEL : 0.0F;
	}

	/** Adds {@code by} steps of strain to {@code player} (a forced march of another kind: a spell, a curse, a long swim). */
	public static void strain(ServerPlayer player, int by) {
		Integer had = player.getAttached(STRAIN);
		player.setAttached(STRAIN, Math.max(0, Math.min(MAX, (had == null ? 0 : had) + by)));
	}

	private static int awake(ServerPlayer player) {
		return player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
	}

	private static int sleepless(int awake) {
		return awake < AWAKE_FIRST ? 0 : 1 + (awake - AWAKE_FIRST) / DAY;
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
				if (player.hasAttached(STEPS)) {
					player.removeAttached(STEPS);
					apply(player, 0);
				}
				continue;
			}
			int[] c = COUNTS.computeIfAbsent(player.getUUID(), id -> new int[] {0, 0, 0, 0, -1});
			int awake = awake(player);
			if (c[4] >= 0 && awake < c[4] - 200) {
				// a night's sleep: the sleepless steps are gone with the time awake, and one other besides
				strain(player, -1);
				c[0] = c[2] = c[3] = 0;
			}
			c[4] = awake;
			if (player.isSprinting()) {
				c[1] = 0;
				if (++c[0] >= SPRINT_STEP) {
					c[0] = 0;
					strain(player, 1);
					winded(player);
				}
			} else if (++c[1] >= BREATHER) {
				c[0] = 0;
			}
			if (Fainting.hungerWeakness(player) >= 0.5F) {
				if (++c[2] >= STARVE_STEP) {
					c[2] = 0;
					strain(player, 1);
				}
			} else {
				c[2] = 0;
			}
			if (player.isFullyFrozen()) {
				if (++c[3] >= COLD_STEP) {
					c[3] = 0;
					strain(player, 1);
				}
			} else {
				c[3] = 0;
			}
			if (player.tickCount % 20 != 0) {
				continue;
			}
			Integer strain = player.getAttached(STRAIN);
			int steps = Math.min(MAX, sleepless(awake) + (strain == null ? 0 : strain));
			if (steps != steps(player)) {
				if (steps == 0) {
					player.removeAttached(STEPS);
				} else {
					player.setAttached(STEPS, steps);
				}
			}
			apply(player, steps);
			if (steps >= MAX && !Downed.isDowned(player) && player.level() instanceof ServerLevel level
				&& Downed.knockOut(level, player, COLLAPSE_TICKS, false, "message.jaymospowers.exhaustion.collapse")) {
				// lying out is worth half a night: a step of strain off, or a day's worth of the time awake
				if (strain != null && strain > 0) {
					strain(player, -1);
				} else {
					player.getStats().setValue(player, Stats.CUSTOM.get(Stats.TIME_SINCE_REST), Math.max(0, awake - DAY));
					c[4] = Math.max(0, awake - DAY);
				}
			}
		}
	}

	private static void winded(ServerPlayer player) {
		player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1.0F, 0.6F);
	}

	private static void apply(Player player, int steps) {
		set(player, Attributes.MOVEMENT_SPEED, SLOW_ID, steps >= 1 ? SLOW : 0.0);
		set(player, Attributes.BLOCK_BREAK_SPEED, DIG_ID, steps >= 2 ? DIG : 0.0);
		set(player, Attributes.ATTACK_DAMAGE, BLOW_ID, steps >= 3 ? BLOW : 0.0);
		set(player, Attributes.ATTACK_SPEED, PACE_ID, steps >= 3 ? BLOW : 0.0);
	}

	private static void set(Player player, Holder<Attribute> attribute, Identifier id, double amount) {
		AttributeInstance inst = player.getAttribute(attribute);
		if (inst == null) {
			return;
		}
		if (amount == 0.0) {
			inst.removeModifier(id);
		} else {
			inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}
}
