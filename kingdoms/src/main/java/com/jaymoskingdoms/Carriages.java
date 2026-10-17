package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.14) A wealthy king's carriage ({@link Wealth}): a cabin in his colours drawn by a horse in iron barding, a
 * coachman in the saddle. It waits at his gate; when he comes out on his round ({@link Royals}) he gets in, and the
 * coachman drives him from stop to stop (he greets his people from the window), his escort riding and marching behind.
 * At dusk he's driven back to his gate and walks in to his throne. A fight comes first: attacked, he gets down to
 * fight. Bought from the treasury ({@link #COST}); a carriage lost is bought again after a day.
 */
final class Carriages {
	/** Its price (emeralds), and how long after buying one a realm waits before buying another. */
	static final int COST = 40;
	static final long REBUY_TICKS = 24000L;
	/** Seen to every this many ticks; looked for this far round the seat. */
	static final int CHECK_TICKS = 100;
	static final double RANGE = Royals.ROAM_RADIUS + 16.0;
	/** He gets in within this of it, and out at his gate within this of it. */
	static final double BOARD_REACH = 6.0, ALIGHT_REACH = 7.0;
	/** Seated, a stop counts as reached this much further off (he sits behind the horse). */
	static final double SEATED_REACH = 4.5;
	/** The coachman keeps this close to the gate while it waits. */
	static final int PARKED_RADIUS = 3;
	/**
	 * (0.7.90) Where a carriage can go: open ground (sky overhead, so no hall, porch or tree canopy), firm and level
	 * ({@code OPEN} blocks round the spot rising or falling at most a block, no fences or walls), looked for this far
	 * round a stop ({@code OPEN_SEARCH}, every {@code OPEN_STEP} blocks). Two of his stops are out on the town's
	 * outskirts, {@code OUTSKIRTS} from the seat.
	 */
	static final int OPEN = 3, OPEN_SEARCH = 14, OPEN_STEP = 2;
	static final int OUTSKIRT_STOPS = 2;
	static final double OUTSKIRTS_MIN = 40.0, OUTSKIRTS_MAX = 60.0;
	/** A task for its coachman (not a caravan's). */
	static final String TASK = "coach";

	/** When each realm last bought one (game time, by realm id): kept with the world, so a restart doesn't buy another
	 *  before the old one's chunk has loaded. */
	static final AttachmentType<Map<String, Long>> BOUGHT = AttachmentRegistry.create(JaymosKingdoms.id("carriages_bought"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));

	private Carriages() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ServerLevel level = Factions.level(server);
			if (level.getGameTime() % CHECK_TICKS == 0) {
				tick(server, level);
			}
		});
	}

	/** (0.7.90) The nearest open ground to {@code near} a carriage can stand on (see {@link #OPEN}), or null. */
	static @Nullable BlockPos open(ServerLevel level, BlockPos near) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int dx = -OPEN_SEARCH; dx <= OPEN_SEARCH; dx += OPEN_STEP) {
			for (int dz = -OPEN_SEARCH; dz <= OPEN_SEARCH; dz += OPEN_STEP) {
				int x = near.getX() + dx, z = near.getZ() + dz;
				BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
				double distance = at.distSqr(near);
				if (distance < bestDistance && roomy(level, at)) {
					bestDistance = distance;
					best = at;
				}
			}
		}
		return best;
	}

	/** Open sky over every block round {@code at} ({@code OPEN} out), the ground firm, dry and level, nothing built in it. */
	private static boolean roomy(ServerLevel level, BlockPos at) {
		for (int dx = -OPEN; dx <= OPEN; dx++) {
			for (int dz = -OPEN; dz <= OPEN; dz++) {
				int x = at.getX() + dx, z = at.getZ() + dz;
				int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
				if (Math.abs(top - at.getY()) > 1) {
					return false;
				}
				BlockPos ground = new BlockPos(x, top - 1, z);
				BlockState state = level.getBlockState(ground);
				if (!state.getFluidState().isEmpty() || state.is(BlockTags.FENCES) || state.is(BlockTags.WALLS) || state.is(BlockTags.FENCE_GATES)
					|| state.is(BlockTags.LEAVES) || !state.isFaceSturdy(level, ground, Direction.UP)) {
					return false;
				}
			}
		}
		return true;
	}

	/** (0.7.90) Open ground out on the town's outskirts ({@link #OUTSKIRTS_MIN} to {@link #OUTSKIRTS_MAX} from the seat), or null. */
	static @Nullable BlockPos outskirts(ServerLevel level, BlockPos seat) {
		var random = level.getRandom();
		for (int tries = 0; tries < 6; tries++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = OUTSKIRTS_MIN + random.nextDouble() * (OUTSKIRTS_MAX - OUTSKIRTS_MIN);
			BlockPos rough = seat.offset((int)Math.round(Math.cos(angle) * distance), 0, (int)Math.round(Math.sin(angle) * distance));
			BlockPos spot = open(level, rough);
			if (spot != null) {
				return spot;
			}
		}
		return null;
	}

	/** {@code f}'s carriage, if it's about. */
	static @Nullable Wagon of(ServerLevel level, Faction f) {
		List<Wagon> found = level.getEntitiesOfClass(Wagon.class, new AABB(f.seat()).inflate(RANGE),
			w -> w.isAlive() && w.kind() == Wagon.CARRIAGE && w.faction() == f.id());
		return found.isEmpty() ? null : found.getFirst();
	}

	/** The carriage {@code king} sits in (or, 0.5.26, the rickshaw), or null. */
	static @Nullable Wagon seat(Folk king) {
		return king.getVehicle() instanceof Wagon w && (w.kind() == Wagon.CARRIAGE || w.kind() == Wagon.RICKSHAW) ? w : null;
	}

	static boolean riding(Folk king) {
		return seat(king) != null;
	}

	/** Its coachman (in its horse's saddle), or a rickshaw's puller (0.5.26), or null. */
	static @Nullable PathfinderMob coachman(Wagon carriage) {
		if (carriage.kind() == Wagon.RICKSHAW) {
			return Rickshaws.puller(carriage);
		}
		Entity horse = carriage.horse();
		return horse != null && horse.getFirstPassenger() instanceof Folk f ? f : null;
	}

	/** Out on his round: he gets in, if his carriage is at hand and waiting (or, without one, a rickshaw come for him). */
	static void board(ServerLevel level, Faction f, Folk king) {
		if (king.isPassenger() || king.getTarget() != null) {
			return;
		}
		Wagon carriage = of(level, f);
		if (carriage == null) {
			carriage = Rickshaws.waitingFor(level, king);
		}
		if (carriage == null || carriage.isVehicle() || coachman(carriage) == null || king.distanceTo(carriage) > BOARD_REACH) {
			return;
		}
		king.getNavigation().stop();
		if (king.startRiding(carriage)) {
			level.playSound(null, carriage.blockPosition(), SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.NEUTRAL, 0.8F, 1.1F);
			if (carriage.kind() == Wagon.CARRIAGE) {
				level.playSound(null, carriage.blockPosition(), SoundEvents.HORSE_AMBIENT, SoundSource.NEUTRAL, 0.8F, 1.0F);
			}
		}
	}

	/** He gets down (at his gate, or to fight); the carriage waits there. */
	static void alight(Folk king) {
		Wagon carriage = seat(king);
		if (carriage == null) {
			return;
		}
		king.stopRiding();
		PathfinderMob coachman = coachman(carriage);
		if (coachman != null) {
			coachman.getNavigation().stop();
		}
		if (king.level() instanceof ServerLevel level) {
			level.playSound(null, carriage.blockPosition(), SoundEvents.WOODEN_DOOR_OPEN, SoundSource.NEUTRAL, 0.8F, 1.1F);
		}
	}

	/**
	 * Takes {@code king} toward {@code (x, y, z)}: on foot, or, seated, his coachman drives there. A stop is reached
	 * {@link #SEATED_REACH} further off when he's seated.
	 */
	static void moveTo(Folk king, double x, double y, double z, double speed) {
		Wagon carriage = seat(king);
		PathfinderMob coachman = carriage == null ? null : coachman(carriage);
		if (coachman == null) {
			king.getNavigation().moveTo(x, y, z, speed);
			return;
		}
		if (carriage.kind() == Wagon.CARRIAGE) {
			coachman.setHomeTo(coachman.blockPosition(), Royals.ROAM_RADIUS);
		}
		if (coachman.getNavigation().isDone() || king.tickCount % 20 == 0) {
			coachman.getNavigation().moveTo(x, y, z, Math.max(speed, Royals.KING_SPEED) * 1.3);
		}
	}

	static void stop(Folk king) {
		Wagon carriage = seat(king);
		PathfinderMob coachman = carriage == null ? null : coachman(carriage);
		(coachman != null ? coachman : king).getNavigation().stop();
	}

	/** Seated, he and his carriage set down at {@code (x, y, z)} together (penned in, as he would be on foot). */
	static void teleport(Folk king, double x, double y, double z) {
		Wagon carriage = seat(king);
		Entity horse = carriage == null ? null : carriage.horse();
		if (horse == null) {
			king.stopRiding();
			king.teleportTo(x, y, z);
			return;
		}
		horse.teleportTo(x, y, z);
		carriage.snapTo(x, y, z, horse.getYRot(), 0.0F);
	}

	static double reach(Folk king) {
		return riding(king) ? SEATED_REACH : 0.0;
	}

	// ---- upkeep ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server, ServerLevel level) {
		long now = level.getGameTime();
		for (Faction f : Factions.all(server, FactionType.KINGDOM)) {
			if (f.fallen() || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			Castle.Keep keep = Factions.keep(server, f.id());
			if (keep == null) {
				continue;
			}
			Wagon carriage = of(level, f);
			unhorsed(level, f, carriage);
			if (carriage != null) {
				tend(level, f, keep, carriage);
				continue;
			}
			if (Wealth.of(server, f) != Wealth.Tier.WEALTHY || now - level.getAttachedOrElse(BOUGHT, Map.of()).getOrDefault(String.valueOf(f.id()), -REBUY_TICKS) < REBUY_TICKS
				|| f.treasury() < Cavalry.TREASURY_KEPT + COST || !level.isPositionEntityTicking(keep.gate())) {
				continue;
			}
			Map<String, Long> bought = new HashMap<>(level.getAttachedOrElse(BOUGHT, Map.of()));
			bought.put(String.valueOf(f.id()), now);
			level.setAttached(BOUGHT, bought);
			Factions.update(server, f.id(), x -> x.withTreasury(Math.max(0L, x.treasury() - COST)));
			if (build(level, f, keep) != null) {
				for (ServerPlayer p : level.players()) {
					if (p.blockPosition().closerThan(f.seat(), 96.0)) {
						p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.carriage.bought", f.name()).withStyle(ChatFormatting.GOLD));
					}
				}
				JaymosKingdoms.LOGGER.info("{} bought its king a carriage", f.name());
			}
		}
	}

	/** A coachman, his horse and the carriage, at the gate. */
	static @Nullable Wagon build(ServerLevel level, Faction f, Castle.Keep keep) {
		BlockPos at = Construction.standable(level, keep.gate(), 6);
		Folk coachman = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (coachman == null) {
			return null;
		}
		coachman.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		coachman.setup(Caravans.CARTER, f, at, PARKED_RADIUS);
		coachman.setTask(TASK);
		coachman.setCustomName(Component.translatable("entity.jaymoskingdoms.coachman_of", f.name()));
		level.addFreshEntity(coachman);
		AbstractHorse horse = Cavalry.mount(level, coachman, f, false);
		if (horse == null) {
			coachman.discard();
			return null;
		}
		return Wagon.hitch(level, Wagon.CARRIAGE, f, horse, -1, 0);
	}

	/**
	 * A coachman out of the saddle: back on his carriage's horse if it has none driving it, else (his carriage is gone)
	 * he goes home, gone from the world.
	 */
	private static void unhorsed(ServerLevel level, Faction f, @Nullable Wagon carriage) {
		for (Folk coachman : level.getEntitiesOfClass(Folk.class, new AABB(f.seat()).inflate(RANGE),
			x -> x.isAlive() && x.factionId() == f.id() && TASK.equals(x.task()) && !x.isPassenger())) {
			Entity horse = carriage == null ? null : carriage.horse();
			if (horse == null || horse.isVehicle()) {
				coachman.discard();
			} else if (!coachman.isInWater()) {
				coachman.teleportTo(horse.getX(), horse.getY(), horse.getZ());
				coachman.startRiding(horse);
			}
		}
	}

	/** A carriage between rounds waits at the gate; with its king inside it's driven ({@link #moveTo}); attacked, he gets down. */
	private static void tend(ServerLevel level, Faction f, Castle.Keep keep, Wagon carriage) {
		PathfinderMob coachman = coachman(carriage);
		Entity rider = carriage.getFirstPassenger();
		if (rider instanceof Folk king) {
			if (king.getTarget() != null || king.getLastHurtByMob() != null && king.tickCount - king.getLastHurtByMobTimestamp() < 60) {
				alight(king);
			}
			return;
		}
		if (coachman == null) {
			// (its coachman fell: a horse of the realm's, with no one to drive it, waits; the next carriage replaces it a day on)
			Entity horse = carriage.horse();
			if (horse == null && carriage.horseLost() > Wagon.LOST_HORSE_TICKS * 4) {
				carriage.discard();
			}
			return;
		}
		coachman.setHomeTo(Construction.standable(level, keep.gate(), 6), PARKED_RADIUS);
	}
}
