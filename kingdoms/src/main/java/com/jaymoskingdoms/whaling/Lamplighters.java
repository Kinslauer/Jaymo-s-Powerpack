package com.jaymoskingdoms.whaling;

import com.jaymoskingdoms.Faction;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Storage;
import com.jaymoskingdoms.Theft;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.206) Lamplighters: the folk of a settlement tend the {@link OilLampBlock oil lamps} on its land, the way they
 * relight its burnt torches ({@code Torches}). After dark the nearest grown, waking villager walks to each lamp that's out
 * and lights it, filling it first from the settlement's stores if it's dry (a bottle of whale oil each time, so a lit
 * town burns through its oil); at daybreak they snuff them to spare the oil. While its stores hold whale oil, a burnt
 * floor torch they come to relight is replaced with a lit oil lamp instead ({@link #upgrade}), so a town that trades for
 * oil comes to be lamp-lit. Lamps outside any settlement are their owners' to tend.
 */
public final class Lamplighters {
	/** How often (ticks) lamps are looked over, and how far round each player (chunks). */
	static final int EVERY = 40, CHUNK_RADIUS = 6;
	/** A villager this close (blocks) walks over to a lamp; within {@link #REACH} tends it. */
	static final double RANGE = 24.0, REACH = 2.5;

	/** Per chunk: packed positions of its oil lamps. Saved. */
	static final AttachmentType<List<Long>> LAMPS = AttachmentRegistry.create(JaymosKingdoms.id("oil_lamps"),
		builder -> builder.persistent(Codec.LONG.listOf()));

	private Lamplighters() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Lamplighters::tick);
	}

	/** A lamp was set down at {@code pos}. */
	static void know(ServerLevel level, BlockPos pos) {
		LevelChunk chunk = level.getChunkAt(pos);
		List<Long> here = chunk.getAttachedOrElse(LAMPS, List.of());
		if (!here.contains(pos.asLong())) {
			List<Long> more = new ArrayList<>(here);
			more.add(pos.asLong());
			chunk.setAttached(LAMPS, more);
		}
	}

	private static void forget(ServerLevel level, BlockPos pos) {
		LevelChunk chunk = level.getChunkAt(pos);
		List<Long> here = chunk.getAttached(LAMPS);
		if (here != null && here.contains(pos.asLong())) {
			List<Long> left = new ArrayList<>(here);
			left.remove(pos.asLong());
			if (left.isEmpty()) {
				chunk.removeAttached(LAMPS);
			} else {
				chunk.setAttached(LAMPS, left);
			}
		}
	}

	/**
	 * (Torches' lamplighters, at a burnt floor torch in a settlement) Hang an oil lamp there instead, lit and full, if the
	 * settlement's stores have whale oil to spare; true if they did.
	 */
	public static boolean upgrade(ServerLevel level, BlockPos pos) {
		Faction f = Theft.lawAt(level, pos);
		if (f == null || Storage.take(level, f, Whaling.WHALE_OIL, 1).isEmpty()) {
			return false;
		}
		level.setBlockAndUpdate(pos, Whaling.OIL_LAMP.defaultBlockState().setValue(OilLampBlock.OIL, OilLampBlock.MOST_OIL).setValue(OilLampBlock.LIT, true));
		return true;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Set<ChunkPos> seen = new HashSet<>();
			Set<Villager> busy = new HashSet<>();
			boolean dark = level.isDarkOutside();
			for (ServerPlayer player : level.players()) {
				ChunkPos at = player.chunkPosition();
				for (int x = -CHUNK_RADIUS; x <= CHUNK_RADIUS; x++) {
					for (int z = -CHUNK_RADIUS; z <= CHUNK_RADIUS; z++) {
						ChunkPos pos = new ChunkPos(at.x() + x, at.z() + z);
						if (seen.add(pos)) {
							LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
							if (chunk != null && chunk.hasAttached(LAMPS)) {
								for (long packed : List.copyOf(chunk.getAttachedOrElse(LAMPS, List.of()))) {
									tend(level, BlockPos.of(packed), dark, busy);
								}
							}
						}
					}
				}
			}
		}
	}

	/** The lamp at {@code pos}, if it wants tending (lit after dark, out by day) and its settlement can: the nearest free villager sees to it. */
	private static void tend(ServerLevel level, BlockPos pos, boolean dark, Set<Villager> busy) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(Whaling.OIL_LAMP)) {
			forget(level, pos);
			return;
		}
		boolean lit = state.getValue(OilLampBlock.LIT);
		if (dark == lit) {
			return;
		}
		Faction f = Theft.lawAt(level, pos);
		if (f == null) {
			return;
		}
		boolean dry = state.getValue(OilLampBlock.OIL) == 0;
		if (dark && dry && Storage.count(level, f, Whaling.WHALE_OIL) == 0) {
			return;
		}
		Vec3 at = Vec3.atCenterOf(pos);
		Villager lighter = null;
		double best = Double.MAX_VALUE;
		for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(RANGE),
				v -> v.isAlive() && !v.isBaby() && !v.isSleeping() && !busy.contains(v))) {
			double d = v.distanceToSqr(at);
			if (d < best) {
				best = d;
				lighter = v;
			}
		}
		if (lighter == null) {
			return;
		}
		busy.add(lighter);
		// (a hung lamp is reached from below)
		if (best > (REACH + 1.0) * (REACH + 1.0)) {
			lighter.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, 0.5F, 1));
			return;
		}
		lighter.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		lighter.getLookControl().setLookAt(at);
		if (!dark) {
			OilLampBlock.setLit(level, pos, state, false);
			return;
		}
		if (dry) {
			ItemStack oil = Storage.take(level, f, Whaling.WHALE_OIL, 1);
			if (oil.isEmpty()) {
				return;
			}
			OilLampBlock.fill(level, pos, state);
			state = level.getBlockState(pos);
		}
		OilLampBlock.setLit(level, pos, state, true);
	}
}
