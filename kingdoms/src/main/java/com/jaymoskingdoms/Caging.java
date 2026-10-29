package com.jaymoskingdoms;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.332) Lock yourself in. When a forced change hits a werewolf player shut in a room, the beast is caged: the room is
 * the cells of air a flood-fill reaches from where they stand without finding the sky, a closed door or any other wall
 * stopping it ({@link #enclosure}: at most {@link #MAX_CELLS} cells and {@link #RADIUS} blocks out, or it counts as open).
 * While it holds, the wolf's whim-hunts only go for what is in the room with it ({@link #allows}, asked by
 * {@link FeralCurse}), and now and then it claws the walls: a scrape, block-breaking particles, no damage to the block.
 * Walk out (a door opened, a wall broken) and the cage is forgotten.
 */
public final class Caging {
	// ---- tuning --------------------------------------------------------------------------------------------------------

	/** The room may hold at most this many cells of air and reach this far (blocks) from where they stand. */
	static final int MAX_CELLS = 400, RADIUS = 6;
	/** The beast claws a wall this often (ticks): at least, and up to this much more. */
	static final int CLAW_EVERY = 50, CLAW_SPREAD = 80;
	/** It only claws walls this near (blocks). */
	static final double CLAW_REACH = 4.0;

	/** A room: its cells (packed block positions), and when the beast next claws a wall. */
	private static final class Cage {
		final Set<Long> cells;
		long nextClaw;

		Cage(Set<Long> cells, long nextClaw) {
			this.cells = cells;
			this.nextClaw = nextClaw;
		}
	}

	private static final Map<UUID, Cage> CAGES = new HashMap<>();

	private Caging() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Caging::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CAGES.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CAGES.clear());
	}

	// ---- the cage ------------------------------------------------------------------------------------------------------

	/** The beast tore out of {@code player} ({@code forced}: the moon's or the curse's own change, not one at will): are they shut in? */
	static void toreOut(ServerLevel level, ServerPlayer player, boolean forced) {
		CAGES.remove(player.getUUID());
		if (!forced) {
			return;
		}
		Set<Long> cells = enclosure(level, player.blockPosition());
		if (cells == null) {
			return;
		}
		CAGES.put(player.getUUID(), new Cage(cells, level.getGameTime() + CLAW_EVERY));
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.feral.caged").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
		level.playSound(null, player.blockPosition(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 0.6F, 0.5F);
	}

	/** The beast is gone: the cage with it. */
	static void end(ServerPlayer player) {
		CAGES.remove(player.getUUID());
	}

	/** Whether {@code player}'s beast may go for {@code e}: always, unless it's caged and {@code e} is outside its room. */
	static boolean allows(ServerPlayer player, Entity e) {
		Cage cage = CAGES.get(player.getUUID());
		return cage == null || inside(cage, e.blockPosition()) || inside(cage, BlockPos.containing(e.getEyePosition()));
	}

	private static boolean inside(Cage cage, BlockPos pos) {
		return cage.cells.contains(pos.asLong());
	}

	/**
	 * The room {@code start} is in: every cell of air (no collision) reached by stepping along the six directions, closed
	 * doors, gates, bars and the rest being walls. Null if it is no room: the sky shows through a cell, a cell lies in an
	 * unloaded chunk or more than {@link #RADIUS} blocks from {@code start}, or there are more than {@link #MAX_CELLS}.
	 */
	static @Nullable Set<Long> enclosure(ServerLevel level, BlockPos start) {
		Set<Long> seen = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		seen.add(start.asLong());
		queue.add(start);
		while (!queue.isEmpty()) {
			BlockPos pos = queue.poll();
			if (level.canSeeSky(pos)) {
				return null;
			}
			for (Direction d : Direction.values()) {
				BlockPos next = pos.relative(d);
				if (seen.contains(next.asLong())) {
					continue;
				}
				if (Math.abs(next.getX() - start.getX()) > RADIUS || Math.abs(next.getY() - start.getY()) > RADIUS
					|| Math.abs(next.getZ() - start.getZ()) > RADIUS || !level.isLoaded(next)) {
					// (open to the outside, or too big to be a room)
					if (level.isLoaded(next) && !open(level, next)) {
						continue;
					}
					return null;
				}
				if (!open(level, next)) {
					continue;
				}
				seen.add(next.asLong());
				if (seen.size() > MAX_CELLS) {
					return null;
				}
				queue.add(next);
			}
		}
		return seen;
	}

	/** Nothing solid in the cell: a beast could be there. */
	private static boolean open(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	// ---- every tick ----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (CAGES.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Cage cage = CAGES.get(player.getUUID());
			if (cage == null) {
				continue;
			}
			if (!WerewolfPlayers.beastUp(player)) {
				continue;
			}
			if (now % 10 == 0 && !inside(cage, player.blockPosition()) && !inside(cage, player.blockPosition().above())) {
				// (out of the room: a door opened, or a wall gave)
				CAGES.remove(player.getUUID());
				continue;
			}
			if (now >= cage.nextClaw && !Seizures.held(player)) {
				cage.nextClaw = now + CLAW_EVERY + player.getRandom().nextInt(CLAW_SPREAD);
				claw(player, cage);
			}
		}
	}

	/** The beast rakes a wall near it: a scrape and flying bits of the block, no harm done to it. */
	private static void claw(ServerPlayer player, Cage cage) {
		ServerLevel level = player.level();
		BlockPos from = player.blockPosition();
		for (int tries = 0; tries < 12; tries++) {
			BlockPos cell = from.offset(player.getRandom().nextInt(9) - 4, player.getRandom().nextInt(4) - 1, player.getRandom().nextInt(9) - 4);
			if (!inside(cage, cell)) {
				continue;
			}
			Direction face = Direction.getRandom(player.getRandom());
			BlockPos wall = cell.relative(face);
			if (inside(cage, wall)) {
				continue;
			}
			BlockState state = level.getBlockState(wall);
			if (state.isAir() || !open(level, cell) || Vec3.atCenterOf(wall).distanceToSqr(player.position()) > CLAW_REACH * CLAW_REACH) {
				continue;
			}
			Vec3 at = Vec3.atCenterOf(wall).add(Vec3.atLowerCornerOf(face.getOpposite().getUnitVec3i()).scale(0.5));
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 12, 0.2, 0.2, 0.2, 0.05);
			level.playSound(null, wall, SoundEvents.GRINDSTONE_USE, SoundSource.HOSTILE, 0.5F, 1.5F + player.getRandom().nextFloat() * 0.3F);
			level.playSound(null, wall, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.8F, 0.6F);
			WerewolfPlayers.clawAt(player);
			return;
		}
	}
}
