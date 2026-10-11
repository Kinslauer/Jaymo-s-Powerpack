package com.jaymoskingdoms;

import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Time that passes in a moment (0.5.1): when the players sleep through the night (or an operator moves the clock on),
 * the Overworld's clock jumps while the game's own ticks don't, so nothing that works by the tick would see those hours.
 * Every settlement makes them up at once: its builders lay what they would have laid in that time
 * ({@link Construction#advance}: a block per {@link Construction#CATCH_UP_TICKS_PER_BLOCK} per builder, paid from storage
 * as ever), and its fields grow a stage per {@link #CROP_TICKS_PER_STAGE}. Since 0.5.2 its mine is dug on
 * ({@link Mines#catchUp}) and its miners and woodcutters bring in what they would have ({@link Gatherers#overnight}). A day's production is counted by the clock's
 * day already ({@link Production#day}), so sleeping brings the next day's goods as well. Only what's loaded catches up.
 */
public final class Nights {
	/** A jump of the clock shorter than this (ticks) isn't a night slept through. */
	static final long MIN_JUMP = 1200L;
	/** The most made up at once (ticks; a day). */
	static final long MOST = 24000L;
	/** A crop grows a stage in about this many ticks (vanilla's random ticks on watered farmland). */
	static final int CROP_TICKS_PER_STAGE = 2500;
	/** Players this close to a seat are told what got done. */
	static final double TELL_RANGE = 160.0;

	private static long lastClock = Long.MIN_VALUE, lastTick;

	private Nights() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Nights::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> lastClock = Long.MIN_VALUE);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long clock = level.getOverworldClockTime(), now = level.getGameTime();
		if (lastClock != Long.MIN_VALUE) {
			long jump = (clock - lastClock) - (now - lastTick);
			if (jump >= MIN_JUMP) {
				pass(level, Math.min(MOST, jump));
			}
		}
		lastClock = clock;
		lastTick = now;
	}

	/** {@code ticks} of every settlement's work, done at once. */
	static void pass(ServerLevel level, long ticks) {
		MinecraftServer server = level.getServer();
		// the growing trees grown on (0.5.4)
		Trees.grow(level, ticks);
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			int laid = 0;
			List<Works.Project> projects = Works.of(server, f.id()).projects();
			int crew = Builders.crew(level, f).size();
			if (!projects.isEmpty() && crew > 0 && Storage.loaded(level, f) && Economy.atPeace(server, f)) {
				int blocks = (int)Math.min(Construction.CATCH_UP_MOST, ticks * crew / Construction.CATCH_UP_TICKS_PER_BLOCK);
				for (Works.Project p : projects) {
					laid += Construction.advance(level, f, p, blocks - laid);
				}
				Construction.stopWaiting(f);
			}
			// its mine dug on (all hands while nothing else is built, else its diggers), its gatherers' takings (0.5.2)
			int dug = 0;
			if (crew > 0 && Mines.digging(server, f) && Storage.loaded(level, f) && Economy.atPeace(server, f)) {
				int diggers = projects.isEmpty() ? crew : Builders.crewCount(f) >= Mines.CREW_TO_SHARE ? Math.min(Mines.DIGGERS, crew) : 0;
				dug = Mines.catchUp(level, f, (int)Math.min(Construction.CATCH_UP_MOST, ticks * diggers / Mines.CATCH_UP_TICKS_PER_BLOCK));
			}
			int gathered = Gatherers.overnight(level, f, ticks);
			int grown = grow(level, f.seat(), (int)(ticks / CROP_TICKS_PER_STAGE));
			JaymosKingdoms.LOGGER.debug("{} made up {} ticks overnight: {} blocks laid, {} dug, {} gathered, {} crops grown", f.name(), ticks, laid, dug,
				gathered, grown);
			if (dug + gathered > 0) {
				Component news = Component.translatable("message.jaymoskingdoms.overnight_gathered", f.name(), dug, gathered).withStyle(ChatFormatting.GRAY);
				for (ServerPlayer player : level.players()) {
					if (player.distanceToSqr(f.seat().getX(), f.seat().getY(), f.seat().getZ()) <= TELL_RANGE * TELL_RANGE) {
						player.sendSystemMessage(news);
					}
				}
			}
			if (laid > 0) {
				Component news = Component.translatable("message.jaymoskingdoms.overnight", f.name(), laid).withStyle(ChatFormatting.GRAY);
				for (ServerPlayer player : level.players()) {
					if (player.distanceToSqr(f.seat().getX(), f.seat().getY(), f.seat().getZ()) <= TELL_RANGE * TELL_RANGE) {
						player.sendSystemMessage(news);
					}
				}
			}
		}
	}

	/** The crops round {@code seat} (the survey's reach) grown {@code stages}; returns how many grew. */
	static int grow(ServerLevel level, BlockPos seat, int stages) {
		if (stages <= 0) {
			return 0;
		}
		int grown = 0;
		int r = Production.FARM_RANGE;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				BlockPos column = seat.offset(dx, 0, dz);
				if (!level.isLoaded(column)) {
					continue;
				}
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
				BlockState state = level.getBlockState(top);
				if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)) {
					level.setBlock(top, crop.getStateForAge(Math.min(crop.getMaxAge(), crop.getAge(state) + stages)), Block.UPDATE_CLIENTS);
					grown++;
				}
			}
		}
		return grown;
	}
}
