package com.jaymospowers.stamina;

import com.jaymospowers.combat.BladeSounds;
import com.jaymospowers.combat.ClashFx;
import com.jaymospowers.core.ArmourKind;
import com.jaymospowers.core.wounds.Adrenaline;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.core.wounds.DroppedArms;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Fighting stamina (Andy's pick from Knights &amp; Heraldry, ideas only: all rights reserved, so nothing of it is copied):
 * armour tires whoever fights and runs in it. Not a second bar: it wears down the feathers ({@link Feathers#drain}).
 * <ul>
 * <li><b>The load</b> ({@link ArmourKind#load}): 0 with nothing on, 1 in plate from head to foot; mail weighs
 * {@code 0.6} of plate, padding {@code 0.2}, piece by piece. Everything below is times the load, so the unarmoured
 * tire of none of it and the row is theirs for dodges as before.</li>
 * <li><b>What tires</b>: sprinting ({@link #SPRINT_DRAIN} a second, on foot or swimming), each jump ({@link #JUMP_DRAIN}),
 * each swing of a weapon or fist at something ({@link #SWING_DRAIN}; a Better Combat swing counts when it starts, hit
 * or miss), and each blow taken from someone or something ({@link #HIT_DRAIN}; a blocked one costs what the guard
 * already charges).</li>
 * <li><b>Run dry</b> ({@link Feathers#spent}: the row and its red strain both gone, by this or by spending) and the next
 * blow that lands knocks the weapon out of the hand ({@link DroppedArms#knock}): it lands on the ground a few blocks
 * off, whole, to be picked up again. Once each time they run dry.</li>
 * <li><b>Adrenaline</b> ({@link Adrenaline#active}) eases all of it to {@link #ADRENALINE_EASE} and keeps the hand
 * closed: nothing is knocked from it while the rush lasts.</li>
 * <li><b>The downed</b> ({@link Downed#isDowned}) tire of nothing and get their breath back lying there; what they
 * held, they dropped going down ({@link DroppedArms}).</li>
 * </ul>
 * Survival and adventure only.
 */
public final class Exertion {
	// ===== the knobs (half-feathers, in plate from head to foot) ======================================================
	/** Sprinting wears this much off the row each second (regrowth is 2 a second). */
	public static final float SPRINT_DRAIN = 2.6F;
	/** A jump. */
	public static final float JUMP_DRAIN = 1.0F;
	/** A swing at something. */
	public static final float SWING_DRAIN = 1.5F;
	/** A blow taken. */
	public static final float HIT_DRAIN = 2.0F;
	/** Swings closer together than this (ticks) are one swing (a sweep that strikes many, both hands at once). */
	public static final int SWING_GAP = 4;
	/** A blow landing this soon (ticks) after a Better Combat swing started is that swing, counted already. */
	public static final int SWING_LANDS = 20;
	/** With adrenaline running, everything wears this share as much. */
	public static final float ADRENALINE_EASE = 0.5F;
	// ================================================================================================================

	/** Server, by player: the jumps counted last tick, and the game times of the last swing counted and the last one Better Combat started. */
	private static final Map<UUID, long[]> SEEN = new HashMap<>();

	private Exertion() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Exertion::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && !blocked && damageTaken > 0.0F) {
				struck(player, source);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SEEN.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> SEEN.clear());
	}

	/** What {@code amount} (in full plate) comes to for {@code player}: by their load, eased by adrenaline; 0 for one it never tires. */
	private static float cost(ServerPlayer player, float amount) {
		if (player.isCreative() || player.isSpectator() || !player.isAlive() || Downed.isDowned(player)) {
			return 0.0F;
		}
		return amount * ArmourKind.load(player) * (Adrenaline.active(player) ? ADRENALINE_EASE : 1.0F);
	}

	private static long[] seen(ServerPlayer player) {
		return SEEN.computeIfAbsent(player.getUUID(), id -> new long[] {jumps(player), Long.MIN_VALUE / 2, Long.MIN_VALUE / 2});
	}

	private static int jumps(ServerPlayer player) {
		return player.getStats().getValue(Stats.CUSTOM.get(Stats.JUMP));
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			long[] seen = seen(player);
			int jumps = jumps(player);
			int jumped = (int)Math.max(0L, jumps - seen[0]);
			seen[0] = jumps;
			float drain = jumped * JUMP_DRAIN;
			if (player.isSprinting() && !player.isPassenger() && !player.isFallFlying()) {
				drain += SPRINT_DRAIN / 20.0F;
			}
			if (drain > 0.0F) {
				Feathers.drain(player, cost(player, drain));
			}
		}
	}

	/**
	 * {@code player} swings: {@code started} as a Better Combat swing starts (hit or miss), else as a blow of theirs lands
	 * on something ({@code Player.attack}; the blow of a swing already counted at its start isn't counted again).
	 */
	public static void swung(ServerPlayer player, boolean started) {
		long[] seen = seen(player);
		long now = player.level().getGameTime();
		if (started) {
			seen[2] = now;
		} else if (now - seen[2] <= SWING_LANDS) {
			return;
		}
		if (now - seen[1] < SWING_GAP) {
			return;
		}
		seen[1] = now;
		Feathers.drain(player, cost(player, SWING_DRAIN));
	}

	/** A blow from someone or something got through to {@code player}. */
	private static void struck(ServerPlayer player, DamageSource source) {
		Entity by = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
		if (by == null || by == player || !player.isAlive() || Downed.isDowned(player)) {
			return;
		}
		if (Feathers.spent(player) && !Adrenaline.active(player) && player.level() instanceof ServerLevel level) {
			Entity striker = source.getEntity() != null ? source.getEntity() : by;
			ItemStack lost = DroppedArms.knock(level, player, striker.position());
			if (!lost.isEmpty()) {
				Feathers.gripLost(player);
				Vec3 hand = player.position().add(0.0, player.getBbHeight() * 0.6, 0.0);
				BladeSounds.clash(level, hand, 0.9F, 0.8F);
				ClashFx.sparks(level, hand, hand.subtract(striker.position()), 8, 0.7F);
			}
		}
		Feathers.drain(player, cost(player, HIT_DRAIN));
	}
}
