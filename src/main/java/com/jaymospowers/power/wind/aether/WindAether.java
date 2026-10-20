package com.jaymospowers.power.wind.aether;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.wind.WindPower;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * (0.13.36) Wind's way into the Aether ({@code jaymospowers:aether}, a datapack dimension of floating holystone islands under
 * an eternal noon, in four skyroot biomes; see {@code docs/aether.md} and {@code docs/wind.md}).
 * <ul>
 * <li>A Wind user flying in the Overworld who climbs to within {@link #ASCEND_MARGIN} blocks of the top of the world, with
 * the fourth ability (B) unlocked, rises into the Aether with every player within {@link #COMPANION_RADIUS} blocks.</li>
 * <li>They arrive at the same x/z standing on an island there (on the ground, not on a tree: {@link #standAt}), or the
 * nearest island within {@link #SEARCH_RADIUS} blocks, or (no island anywhere near) in the open air at
 * {@link #EMPTY_ARRIVAL_Y} with Slow Falling.</li>
 * <li>The way home is the old Aether's: anyone (Wind or not) who falls below {@link #FALL_OUT_Y} lands back in the
 * Overworld, {@link #ARRIVE_ABOVE_GROUND} blocks above the ground at the same x/z, floating down on Slow Falling.</li>
 * </ul>
 * Dying takes you home as usual (your own spawn).
 */
public final class WindAether {
	public static final ResourceKey<Level> AETHER = ResourceKey.create(Registries.DIMENSION, JaymosPowers.id("aether"));
	/** How close to the top of the Overworld (its highest buildable block) a flying Wind user must climb to rise. */
	static final int ASCEND_MARGIN = 4;
	/** Who comes along: every player this close to the one rising. */
	static final double COMPANION_RADIUS = 4.0;
	/** After rising, this many ticks before the same player can rise again (so it can't loop). */
	static final int ASCEND_COOLDOWN = 200;
	/** How far (blocks) to search outward for an island when the arrival column is empty air, and the step between probes (a chunk). */
	static final int SEARCH_RADIUS = 96, SEARCH_STEP = 16;
	/** With no island anywhere near: arrive in the air at this height (above every island: the terrain tops out near y 115),
	 * floating for this many ticks. */
	static final int EMPTY_ARRIVAL_Y = 128, EMPTY_SLOW_FALL_TICKS = 400;
	/** How far (blocks) from a probed column to look for ground that is clear of trees. */
	static final int STAND_SEARCH = 3;
	/** Falling below this height in the Aether takes you home (the lowest islands and cloud banks sit above y 2). */
	static final int FALL_OUT_Y = 4;
	/** Home: this many blocks above the ground (never closer than ARRIVE_BELOW_TOP to the world's top), floating down for
	 * this many ticks, long enough to land (fall damage applies again once it ends). */
	static final int ARRIVE_ABOVE_GROUND = 48, ARRIVE_BELOW_TOP = 8, SLOW_FALL_TICKS = 600;

	/** Has this player been told what the Aether is? */
	public static final AttachmentType<Boolean> VISITED = AttachmentRegistry.create(JaymosPowers.id("aether_visited"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());

	/** Server tick when each player may next rise. */
	private static final Map<UUID, Long> READY_AT = new HashMap<>();

	private WindAether() {
	}

	/** Called from {@code WindPower.init}. */
	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(WindAether::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> READY_AT.clear());
	}

	private static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		ServerLevel aether = server.getLevel(AETHER);
		if (aether == null) {
			return;
		}
		for (ServerPlayer player : new ArrayList<>(aether.players())) {
			if (player.isAlive() && player.getY() < FALL_OUT_Y) {
				fallHome(player, server);
			}
		}
		if (now % 2 != 0) {
			return;
		}
		ServerLevel overworld = server.overworld();
		for (ServerPlayer player : new ArrayList<>(overworld.players())) {
			if (player.getY() >= overworld.getMaxY() - ASCEND_MARGIN && canRise(player) && now >= READY_AT.getOrDefault(player.getUUID(), 0L)) {
				rise(player, aether);
				READY_AT.put(player.getUUID(), now + ASCEND_COOLDOWN);
			}
		}
	}

	/** A living Wind user, in flight, with the fourth ability unlocked. */
	private static boolean canRise(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && Powers.has(player, WindPower.INSTANCE)
			&& Mastery.isUnlocked(player, PowerKey.ABILITY_4) && WindPower.flying(player);
	}

	// ---------------------------------------------------------------- up

	private static void rise(ServerPlayer leader, ServerLevel aether) {
		List<ServerPlayer> going = new ArrayList<>();
		going.add(leader);
		for (ServerPlayer other : leader.level().getEntitiesOfClass(ServerPlayer.class, leader.getBoundingBox().inflate(COMPANION_RADIUS))) {
			if (other != leader && other.isAlive() && !other.isSpectator()) {
				going.add(other);
			}
		}
		int x = leader.getBlockX();
		int z = leader.getBlockZ();
		int[] spot = findIsland(aether, x, z);
		for (int i = 0; i < going.size(); i++) {
			arrive(going.get(i), aether, x, z, spot, i, going.size());
		}
	}

	/** Somewhere to stand on the island at (x, z), else on the nearest one found spiralling out; {x, y, z}, or null if none. */
	private static int[] findIsland(ServerLevel aether, int x, int z) {
		for (int r = 0; r <= SEARCH_RADIUS; r += SEARCH_STEP) {
			// the square ring at distance r (just the centre when r is 0), a chunk between probes
			for (int dx = -r; dx <= r; dx += SEARCH_STEP) {
				for (int dz = -r; dz <= r; dz += SEARCH_STEP) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int[] spot = standAt(aether, x + dx, z + dz);
					if (spot != null) {
						return spot;
					}
				}
			}
		}
		return null;
	}

	/** The height just above the top solid block at (x, z), loading the chunk; the world's floor if the column is empty. */
	private static int topAt(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
	}

	/**
	 * Ground to stand on at (x, z) or close beside it (within {@link #STAND_SEARCH}): {x, y, z} with y the first free
	 * block above the top of the ground, or null. Trees stand on the islands, so the top block of a column is often a log
	 * (the heightmap does not skip logs); a column counts only if its top is bare solid ground with room for a player.
	 */
	private static int[] standAt(ServerLevel level, int x, int z) {
		for (int r = 0; r <= STAND_SEARCH; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
						int y = standY(level, x + dx, z + dz);
						if (y >= 0) {
							return new int[] {x + dx, y, z + dz};
						}
					}
				}
			}
		}
		return null;
	}

	/** The height to stand at in the column (x, z): just above bare ground with two free blocks over it; -1 if there is none. */
	private static int standY(ServerLevel level, int x, int z) {
		int y = topAt(level, x, z);
		if (y <= level.getMinY() + 1) {
			return -1;
		}
		BlockPos below = new BlockPos(x, y - 1, z);
		BlockState ground = level.getBlockState(below);
		if (!ground.isFaceSturdy(level, below, Direction.UP) || ground.is(BlockTags.LOGS) || ground.is(BlockTags.LEAVES)) {
			return -1;
		}
		for (int i = 0; i < 2; i++) {
			BlockPos at = new BlockPos(x, y + i, z);
			if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty() || !level.getFluidState(at).isEmpty()) {
				return -1;
			}
		}
		return y;
	}

	private static void arrive(ServerPlayer player, ServerLevel aether, int x, int z, int[] spot, int index, int of) {
		ServerLevel from = player.level();
		if (player.isPassenger()) {
			player.stopRiding();
		}
		burst(from, player.position());
		// the leader (index 0) stands on the found column; companions ring around it
		boolean companion = index > 0 && of > 1;
		double angle = companion ? Math.PI * 2.0 * (index - 1) / (of - 1) : 0.0;
		double radius = companion ? 1.5 : 0.0;
		int dx = (int) Math.round(Math.cos(angle) * radius);
		int dz = (int) Math.round(Math.sin(angle) * radius);
		double ax;
		double ay;
		double az;
		if (spot != null) {
			ax = spot[0] + dx + 0.5;
			az = spot[2] + dz + 0.5;
			int own = standY(aether, spot[0] + dx, spot[2] + dz);
			// a companion's own column may be off the island's edge or under a tree: use the first one's height
			ay = own >= 0 ? own : spot[1];
		} else {
			ax = x + dx + 0.5;
			az = z + dz + 0.5;
			ay = EMPTY_ARRIVAL_Y;
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, EMPTY_SLOW_FALL_TICKS, 0, false, false));
		}
		player.teleportTo(aether, ax, ay, az, Set.of(), player.getYRot(), player.getXRot(), true);
		burst(aether, player.position());
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.aether.arrived").withStyle(ChatFormatting.AQUA));
		if (!Boolean.TRUE.equals(player.getAttached(VISITED))) {
			player.setAttached(VISITED, true);
			player.sendSystemMessage(Component.translatable("message.jaymospowers.aether.lore").withStyle(ChatFormatting.GRAY));
		}
	}

	// ---------------------------------------------------------------- down

	/** Fell out of the Aether: back to the Overworld, same x/z, high above the ground, floating down. */
	private static void fallHome(ServerPlayer player, MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		if (player.isPassenger()) {
			player.stopRiding();
		}
		int ground = topAt(overworld, player.getBlockX(), player.getBlockZ());
		Vec3 at = new Vec3(player.getX(), Math.min(ground + ARRIVE_ABOVE_GROUND, overworld.getMaxY() - ARRIVE_BELOW_TOP), player.getZ());
		burst(player.level(), player.position());
		player.teleportTo(overworld, at.x, at.y, at.z, Set.of(), player.getYRot(), player.getXRot(), true);
		player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALL_TICKS, 0, false, false));
		// the drop out of the Aether doesn't count; the slow fall's end starts a fresh fall
		player.resetFallDistance();
		burst(overworld, at);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.aether.home").withStyle(ChatFormatting.AQUA));
	}

	private static void burst(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 1.0, at.z, 40, 0.5, 0.9, 0.5, 0.05);
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.0, at.z, 20, 0.4, 0.8, 0.4, 0.04);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 0.6F, 1.4F);
	}
}
