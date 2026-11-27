package com.jaymoskingdoms;

import com.jaymoskingdoms.creatures.OldRules;
import com.mojang.serialization.Codec;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

/**
 * Sleep the day away (Night's Blood, patch 6): a vampire lying in a coffin by day ({@link OldRules#coffin}, rested in the way
 * {@link BedRest} lays anyone down by day) skips the day to dusk once every player is asleep or resting in a bed, as beds skip
 * the night to dawn: each must have lain there {@link #LIE_TICKS} (vanilla's sleep time), and as many as the sleeping-percentage
 * rule asks for. The clock jumps to {@link Vampires#NIGHT_FROM}, the settlements make up the hours ({@link Nights}), and whoever
 * was lying down wakes. A stake into a sleeper still kills for good ({@link OldRules#stakeAsleep}: a resting vampire is asleep to it).
 * <p>
 * Where a vampire last lay in a coffin is kept as their lair ({@link #LAIR}): what the hunters of {@link LairHunts} look for.
 */
public final class CoffinRest {
	/** Ticks a sleeper must have been in bed before the day can skip (vanilla's {@code Player.SLEEP_DURATION}). */
	static final int LIE_TICKS = net.minecraft.world.entity.player.Player.SLEEP_DURATION;
	/** How often (ticks) the sleepers are counted. */
	static final int EVERY = 10;

	/** On a vampire player: the coffin they last lay in or woke in (the head of its bed). Kept, through death. */
	public static final AttachmentType<GlobalPos> LAIR = AttachmentRegistry.create(JaymosKingdoms.id("vampire_lair"),
		b -> b.persistent(GlobalPos.CODEC).copyOnDeath());

	private CoffinRest() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(CoffinRest::tick);
	}

	/** {@code player}'s lair is the coffin at {@code head}. */
	public static void claim(ServerPlayer player, ServerLevel level, BlockPos head) {
		player.setAttached(LAIR, GlobalPos.of(level.dimension(), head.immutable()));
	}

	/** {@code player}'s lair, if it is in {@code level}. */
	public static @Nullable BlockPos lair(ServerPlayer player, ServerLevel level) {
		GlobalPos at = player.getAttached(LAIR);
		return at != null && at.dimension() == level.dimension() ? at.pos() : null;
	}

	/** Whether {@code player} is a full vampire lying in a coffin. */
	static boolean inCoffin(ServerPlayer player) {
		Optional<BlockPos> bed = player.getSleepingPos();
		return player.isSleeping() && bed.isPresent() && Vampires.isVampire(player) && !VampirePlayers.dhampir(player)
			&& OldRules.coffin(player.level(), bed.get());
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		long clock = level.getOverworldClockTime();
		if (Vampires.night(clock) || Timelapse.running() || level.players().isEmpty()) {
			return;
		}
		boolean coffin = false;
		int counted = 0, lying = 0;
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator()) {
				continue;
			}
			counted++;
			// one riding a thrall by day looks out through its eyes and is not lying down to sleep the day away
			if (p.isSleeping() && p.getSleepTimer() >= LIE_TICKS && !ThrallRide.riding(p)) {
				lying++;
				if (inCoffin(p)) {
					coffin = true;
					Optional<BlockPos> bed = p.getSleepingPos();
					bed.ifPresent(pos -> claim(p, level, pos));
				}
			}
		}
		int percent = level.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
		if (!coffin || counted == 0 || lying * 100 < counted * Math.max(1, percent)
			|| !level.getGameRules().get(GameRules.ADVANCE_TIME)) {
			return;
		}
		long t = Math.floorMod(clock, 24000L);
		long to = clock + (Vampires.NIGHT_FROM - t);
		level.dimensionType().defaultClock().ifPresent(c -> server.clockManager().setTotalTicks(c, to));
		for (ServerPlayer p : level.players()) {
			if (p.isSleeping()) {
				p.stopSleepInBed(true, true);
			}
			level.playSound(null, p.blockPosition(), SoundEvents.CANDLE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 0.6F);
		}
	}
}
