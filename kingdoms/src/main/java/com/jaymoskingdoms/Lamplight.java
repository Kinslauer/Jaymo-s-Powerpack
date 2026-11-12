package com.jaymoskingdoms;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * Lamplight follows people (Andy, 2026-11-12: "this entire field went dark way too early... there should be villagers
 * that relight them as they walk past. Unused roadways will go dark after some time, but people in proximity will be
 * implied by more lighting").
 *
 * <p><b>Passing.</b> Folk on their way somewhere (villagers, MCA's too, and a settlement's guards, carters and workers)
 * look after the {@link Lanterns lanterns} and {@link Torches torches} they walk by, without stopping: a lit lantern
 * under half of {@link Lanterns#KEPT_FILL} is filled back up to it, and a burnt lantern or torch is lit again. So the
 * lights along the paths people use never go out, and a caravan coming down a road lights it as it goes.
 *
 * <p><b>Unseen.</b> Where no player is near to see it, a place people use is assumed to be looked after
 * ({@link #kept}): a settlement's land, and a King's Road that caravans travel (one end serves the other, or a caravan is
 * on it now). A lantern there that ran dry while nobody was about is found still burning, and a burnt one there is
 * lit again before long ({@link #burntTick}). A road between towns that have nothing to do with each other, or whose
 * towns have fallen, gets none of that: its lanterns burn through their fill and go dark one by one.
 */
public final class Lamplight {
	/** How often (ticks) passers-by are looked over, and how far round each player (blocks). */
	static final int EVERY = 20;
	static final double SCAN = 96.0;
	/** A passer-by tends a light this near (blocks, measured flat), from this far below it to this far above. */
	static final double PASS_REACH = 4.0;
	static final int REACH_BELOW = 2, REACH_ABOVE = 4;
	/** A lantern that ran dry more than this long (ticks) before anyone noticed went dry unseen. */
	static final long UNSEEN = 6000L;
	/** No player within this many blocks: nobody sees a burnt light lit by someone who isn't there. */
	static final double SEEN = 40.0;
	/** A burnt light somewhere kept is lit again on one random tick in this many (about ten minutes). */
	static final int UNSEEN_LIGHT = 9;
	/** A lantern this near (blocks) a road's line stands on that road. */
	static final double ROAD_NEAR = 8.0;

	private Lamplight() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Lamplight::tick);
	}

	/** {@code who} can reach the light at {@code pos}: within {@code reach} across, a little below it or a few blocks under it. */
	static boolean reaches(Entity who, BlockPos pos, double reach) {
		double dx = pos.getX() + 0.5 - who.getX(), dz = pos.getZ() + 0.5 - who.getZ();
		int dy = pos.getY() - who.blockPosition().getY();
		return dx * dx + dz * dz <= (reach + 0.5) * (reach + 0.5) && dy >= -REACH_BELOW && dy <= REACH_ABOVE;
	}

	/** Whether people live or travel at {@code pos}, so its lights are looked after when nobody is there to see. */
	static boolean kept(ServerLevel level, BlockPos pos) {
		MinecraftServer server = level.getServer();
		if (level != Factions.level(server)) {
			return false;
		}
		return Theft.lawAt(level, pos) != null || onTravelledRoad(server, pos);
	}

	/** {@code pos} stands by a King's Road that caravans use: one end serves the other, or a caravan runs between them now. */
	private static boolean onTravelledRoad(MinecraftServer server, BlockPos pos) {
		double x = pos.getX() + 0.5, z = pos.getZ() + 0.5;
		List<Caravans.Caravan> caravans = null;
		for (Roads.Road road : Roads.network(server).roads()) {
			Roads.Line line = Roads.line(road);
			if (!line.near(x, z, ROAD_NEAR) || line.project(x, z)[1] > ROAD_NEAR) {
				continue;
			}
			Faction a = Factions.get(server, road.a()), b = Factions.get(server, road.b());
			if (a == null || b == null || a.fallen() || b.fallen()) {
				continue;
			}
			if (a.overlord() == b.id() || b.overlord() == a.id()) {
				return true;
			}
			if (caravans == null) {
				caravans = Caravans.all(server);
			}
			for (Caravans.Caravan c : caravans) {
				if (c.home() == a.id() && c.lord() == b.id() || c.home() == b.id() && c.lord() == a.id()) {
					return true;
				}
			}
		}
		return false;
	}

	/** A burnt torch's or lantern's random tick: somewhere kept, with no player near, someone lights it before long. */
	static void burntTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (random.nextInt(UNSEEN_LIGHT) != 0
			|| level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SEEN, false) != null
			|| !kept(level, pos)) {
			return;
		}
		Torches.kindle(level, pos, state);
	}

	/** Someone who looks after the lights they pass: a grown, waking villager, or a settled faction's own folk. */
	private static boolean passer(PathfinderMob mob) {
		if (!mob.isAlive() || mob.isBaby()) {
			return false;
		}
		if (mob instanceof Villager v) {
			return !v.isSleeping();
		}
		if (mob instanceof Worker w && w.factionId() >= 0) {
			Faction f = Factions.get(mob.level().getServer(), w.factionId());
			return f != null && !f.fallen() && f.type() != FactionType.BARBARIAN;
		}
		return false;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.players().isEmpty()) {
				continue;
			}
			long now = level.getGameTime();
			Set<PathfinderMob> seen = new HashSet<>();
			for (ServerPlayer player : level.players()) {
				for (PathfinderMob mob : level.getEntitiesOfClass(PathfinderMob.class, player.getBoundingBox().inflate(SCAN), Lamplight::passer)) {
					if (seen.add(mob)) {
						pass(level, mob, now);
					}
				}
			}
		}
	}

	/** {@code mob} tends the first light in reach that wants it (one a pass): tops up a low lantern, or lights a burnt one. */
	private static void pass(ServerLevel level, PathfinderMob mob, long now) {
		int r = (int) Math.ceil(PASS_REACH) + 1;
		BlockPos at = mob.blockPosition();
		for (int cx = (at.getX() - r) >> 4; cx <= (at.getX() + r) >> 4; cx++) {
			for (int cz = (at.getZ() - r) >> 4; cz <= (at.getZ() + r) >> 4; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				Map<String, List<Long>> fuel = chunk.getAttached(Lanterns.FUEL);
				if (fuel != null) {
					for (String key : List.copyOf(fuel.keySet())) {
						BlockPos pos = BlockPos.of(Long.parseLong(key));
						if (reaches(mob, pos, PASS_REACH) && Lanterns.topUp(level, pos, now)) {
							tend(mob, pos);
							level.playSound(null, pos, SoundEvents.LANTERN_HIT, SoundSource.NEUTRAL, 0.5F, 1.2F);
							return;
						}
					}
				}
				List<Long> burnt = chunk.getAttached(Torches.BURNT);
				if (burnt != null) {
					for (long packed : List.copyOf(burnt)) {
						BlockPos pos = BlockPos.of(packed);
						BlockState state = level.getBlockState(pos);
						if (Torches.burnt(state) && reaches(mob, pos, PASS_REACH)) {
							tend(mob, pos);
							Torches.kindle(level, pos, state);
							return;
						}
					}
				}
			}
		}
	}

	private static void tend(PathfinderMob mob, BlockPos pos) {
		mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		mob.getLookControl().setLookAt(Vec3.atCenterOf(pos));
	}
}
