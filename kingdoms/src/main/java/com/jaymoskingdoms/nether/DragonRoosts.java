package com.jaymoskingdoms.nether;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Stacked;
import com.jaymoskingdoms.dragons.Dragons;
import com.jaymoskingdoms.dragons.IgnivorusDragon;
import com.jaymoskingdoms.dragons.WindDragon;
import com.jaymoskingdoms.films.BossIntros;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.jspecify.annotations.Nullable;

/**
 * The Ignivorus' roosts: blackstone watchtowers of the old war, left standing in the Nether and held by the fortress'
 * dead (wither skeletons, a pair on every floor). Like the war forts ({@link NetherForts}) one rises now and then near
 * a player, out of sight, where there is level ground and open air high enough over it. A stair winds up the inside
 * to an open roof. Whoever climbs out onto the roof calls the dragon: a wild Ignivorus near by (or a new one, some way
 * off) flies in and comes down on the tower ({@link WindDragon#comeDownOn}), and as it nears, everyone on the roof
 * who has never met one gets its entrance film staged for them ({@link BossIntros#stage}): the fly-in, the landing,
 * the roar. Once it is down the roof is its own, and it goes for anyone on it. Ops: {@code /kingdom roost found | call | info}.
 */
public final class DragonRoosts {
	/** How often a roost may rise near each player in the Nether (ticks), the chance it does, and how many places are tried. */
	static final int FOUND_EVERY = 20 * 60 * 3, SITE_TRIES = 8;
	static final float FOUND_CHANCE = 0.25F;
	/** No new roost while one stands this near the player (blocks). */
	static final double ROOST_AREA = 480.0;
	/** The tower: half its width (walls), half the roof's width, its least and greatest height, and the open air kept over the roof. */
	static final int WALL = 4, ROOF = 5, MIN_HEIGHT = 12, MAX_HEIGHT = 26, ROOF_ROOM = 10;
	/** A floor every this many blocks up, and the dead that stand on each. */
	static final int FLOOR_EVERY = 6, GUARDS_PER_FLOOR = 2;
	/** The stair's last steps come up through a roof left open over them. */
	static final int STAIR_MOUTH = 7;
	/** How far under the walls the footing is carried down through gaps. */
	static final int FOOTING = 4;
	/** One part in this many of the wall is cracked. */
	static final int CRACKED_ONE_IN = 5;
	/** A roost answers a climber at most this often (ticks). */
	static final long CALL_EVERY = 24000L;
	/** A wild Ignivorus this near answers the call; otherwise one turns up this far off, this much above the roof. */
	static final double CALL_RANGE = 96.0;
	static final int ARRIVE_FROM = 44, ARRIVE_ABOVE = 6;
	/** The film starts when the dragon is this near the roof; a call that hasn't got there in this long is dropped (ticks). */
	static final double STAGE_RANGE = 26.0, WATCH_RANGE = 24.0;
	static final int CALL_TIMEOUT = 20 * 60;
	/** A dragon perched within this of the roof's middle counts as on it, and defends it. */
	static final double DEFEND_RANGE = 10.0;

	/** A roost: the ground block under its middle, how tall it is, and when it last called its dragon. */
	public record Roost(int id, BlockPos base, int height, long lastCall) {
		public static final Codec<Roost> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("id").forGetter(Roost::id),
				BlockPos.CODEC.fieldOf("base").forGetter(Roost::base),
				Codec.INT.fieldOf("height").forGetter(Roost::height),
				Codec.LONG.fieldOf("last_call").forGetter(Roost::lastCall))
			.apply(i, Roost::new));

		/** The roof's middle block: the dragon lands on it. */
		BlockPos top() {
			return base.above(height + 1);
		}
	}

	public static final AttachmentType<List<Roost>> ROOSTS = AttachmentRegistry.create(JaymosKingdoms.id("dragon_roosts"),
		builder -> builder.persistent(Roost.CODEC.listOf()));

	/** A dragon on its way down to a roost, and when it was called. */
	private record Call(WindDragon dragon, long at) {
	}

	private static final Map<Integer, Call> CALLS = new HashMap<>();

	private DragonRoosts() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(DragonRoosts::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CALLS.clear());
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("roost").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("found").executes(DragonRoosts::foundHere))
				.then(Commands.literal("call").executes(DragonRoosts::callNow))
				.then(Commands.literal("info").executes(DragonRoosts::info)))));
	}

	static List<Roost> roosts(ServerLevel nether) {
		List<Roost> all = nether.getAttached(ROOSTS);
		return all == null ? List.of() : all;
	}

	private static void put(ServerLevel nether, Roost roost) {
		List<Roost> all = new ArrayList<>(roosts(nether));
		all.removeIf(r -> r.id() == roost.id());
		all.add(roost);
		nether.setAttached(ROOSTS, List.copyOf(all));
	}

	// ---- each tick -----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel nether = Stacked.level(server, Level.NETHER);
		if (nether == null) {
			return;
		}
		long now = nether.getGameTime();
		if (now % 5 == 0 && !CALLS.isEmpty()) {
			arrivals(nether, now);
		}
		if (now % 20 == 0) {
			for (Roost roost : roosts(nether)) {
				guardRoof(nether, roost);
				if (now - roost.lastCall() < CALL_EVERY && roost.lastCall() != 0L || CALLS.containsKey(roost.id())) {
					continue;
				}
				for (ServerPlayer player : Stacked.players(server, Level.NETHER)) {
					if (!player.isSpectator() && onRoof(roost, player)) {
						call(nether, roost, now);
						break;
					}
				}
			}
		}
		if (now % FOUND_EVERY == 0) {
			RandomSource random = nether.getRandom();
			for (ServerPlayer player : Stacked.players(server, Level.NETHER)) {
				if (!player.isSpectator() && random.nextFloat() < FOUND_CHANCE) {
					tryFound(nether, player);
				}
			}
		}
	}

	/** A dragon sitting on the roost is violently territorial (Andy): it turns on whoever is up there with it. */
	private static void guardRoof(ServerLevel nether, Roost roost) {
		BlockPos top = roost.top();
		for (ServerPlayer player : Stacked.players(nether.getServer(), Level.NETHER)) {
			if (player.isSpectator() || !onRoof(roost, player)) {
				continue;
			}
			for (IgnivorusDragon dragon : nether.getEntitiesOfClass(IgnivorusDragon.class, new net.minecraft.world.phys.AABB(top).inflate(DEFEND_RANGE))) {
				dragon.defend(player);
			}
		}
	}

	private static boolean onRoof(Roost roost, Entity entity) {
		BlockPos top = roost.top();
		return Math.abs(entity.getX() - top.getX() - 0.5) <= ROOF + 0.5 && Math.abs(entity.getZ() - top.getZ() - 0.5) <= ROOF + 0.5
			&& entity.getY() >= top.getY() + 0.9 && entity.getY() <= top.getY() + 6.0;
	}

	// ---- the call --------------------------------------------------------------------------------------------------------

	/** The roost's dragon is sent for: a wild Ignivorus near by, or a new one some way off. False if none could come. */
	private static boolean call(ServerLevel nether, Roost roost, long now) {
		BlockPos top = roost.top();
		WindDragon dragon = null;
		for (IgnivorusDragon near : nether.getEntitiesOfClass(IgnivorusDragon.class, new net.minecraft.world.phys.AABB(top).inflate(CALL_RANGE),
			d -> d.isAlive() && !d.isTamed())) {
			if (near.comeDownOn(top)) {
				dragon = near;
				break;
			}
		}
		if (dragon == null) {
			dragon = arrive(nether, top);
		}
		if (dragon == null) {
			return false;
		}
		BossIntros.hold(dragon);
		CALLS.put(roost.id(), new Call(dragon, now));
		put(nether, new Roost(roost.id(), roost.base(), roost.height(), now));
		return true;
	}

	/** A new Ignivorus in open air some way off from the roof, already bound for it. Null if there's no room for one. */
	private static @Nullable WindDragon arrive(ServerLevel nether, BlockPos top) {
		RandomSource random = nether.getRandom();
		for (int i = 0; i < 24; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int distance = ARRIVE_FROM - (i / 6) * 8;
			BlockPos at = BlockPos.containing(top.getX() + 0.5 + Math.cos(angle) * distance, top.getY() + ARRIVE_ABOVE - (i % 3) * 2,
				top.getZ() + 0.5 + Math.sin(angle) * distance);
			if (!nether.isLoaded(at)) {
				continue;
			}
			IgnivorusDragon dragon = Dragons.IGNIVORUS_DRAGON.create(nether, EntitySpawnReason.EVENT);
			if (dragon == null) {
				return null;
			}
			float yaw = (float) (Math.toDegrees(angle) + 90.0);
			dragon.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
			if (!nether.noCollision(dragon) || nether.containsAnyLiquid(dragon.getBoundingBox())) {
				continue;
			}
			dragon.finalizeSpawn(nether, nether.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
			nether.addFreshEntity(dragon);
			if (dragon.comeDownOn(top)) {
				return dragon;
			}
			dragon.discard();
			return null;
		}
		return null;
	}

	/** Dragons on their way: once one is near its roof, the film for those on it; a lost or late one is let go. */
	private static void arrivals(ServerLevel nether, long now) {
		for (Iterator<Map.Entry<Integer, Call>> it = CALLS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Call> entry = it.next();
			Call call = entry.getValue();
			WindDragon dragon = call.dragon();
			Roost roost = null;
			for (Roost r : roosts(nether)) {
				if (r.id() == entry.getKey()) {
					roost = r;
				}
			}
			if (roost == null || !dragon.isAlive() || dragon.isRemoved() || now - call.at() > CALL_TIMEOUT) {
				BossIntros.unhold(dragon);
				it.remove();
				continue;
			}
			BlockPos top = roost.top();
			if (dragon.distanceToSqr(top.getX() + 0.5, top.getY() + 1.0, top.getZ() + 0.5) > STAGE_RANGE * STAGE_RANGE) {
				continue;
			}
			for (ServerPlayer player : Stacked.players(nether.getServer(), Level.NETHER)) {
				if (!player.isSpectator() && player.blockPosition().closerThan(top, WATCH_RANGE)) {
					BossIntros.stage(player, dragon);
				}
			}
			BossIntros.unhold(dragon);
			it.remove();
		}
	}

	// ---- founding ------------------------------------------------------------------------------------------------------

	private static void tryFound(ServerLevel nether, ServerPlayer player) {
		for (Roost r : roosts(nether)) {
			if (r.base().closerThan(player.blockPosition(), ROOST_AREA)) {
				return;
			}
		}
		RandomSource random = nether.getRandom();
		for (int i = 0; i < SITE_TRIES; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int dist = NetherForts.SITE_MIN + random.nextInt(NetherForts.SITE_MAX - NetherForts.SITE_MIN);
			BlockPos at = player.blockPosition().offset((int) (Math.cos(angle) * dist), 0, (int) (Math.sin(angle) * dist));
			if (found(nether, at, player.getBlockY(), false) != null) {
				return;
			}
		}
	}

	/** A roost near {@code at}: built and manned, or null if there is no level ground with enough open air over it. */
	static @Nullable Roost found(ServerLevel nether, BlockPos at, int nearY, boolean anywhere) {
		BlockPos base = NetherForts.nearSite(nether, at.getX(), at.getZ(), nearY, ROOF, anywhere, false);
		if (base == null) {
			return null;
		}
		int clear = Integer.MAX_VALUE;
		for (int dx = -ROOF; dx <= ROOF; dx += ROOF) {
			for (int dz = -ROOF; dz <= ROOF; dz += ROOF) {
				clear = Math.min(clear, clearOver(nether, base.offset(dx, NetherForts.MAX_SPREAD, dz), MAX_HEIGHT + ROOF_ROOM));
			}
		}
		int height = Math.min(MAX_HEIGHT, clear + NetherForts.MAX_SPREAD - ROOF_ROOM);
		if (height < MIN_HEIGHT) {
			return null;
		}
		int id = roosts(nether).stream().mapToInt(Roost::id).max().orElse(0) + 1;
		Roost roost = new Roost(id, base, height, 0L);
		build(nether, roost);
		put(nether, roost);
		JaymosKingdoms.LOGGER.info("A dragon's roost, {} high, rose at {}", height, base);
		return roost;
	}

	/** How many blocks over {@code from} are free of anything solid or wet, up to {@code most}. */
	private static int clearOver(ServerLevel nether, BlockPos from, int most) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int h = 1; h <= most; h++) {
			BlockState state = nether.getBlockState(pos.set(from.getX(), from.getY() + h, from.getZ()));
			if (state.isSolid() || !state.getFluidState().isEmpty()) {
				return h - 1;
			}
		}
		return most;
	}

	/** The cells of the stair's ring (one in from the walls), in the order the stair climbs them, starting beside the door. */
	private static List<int[]> ring() {
		int r = WALL - 1;
		List<int[]> cells = new ArrayList<>();
		for (int x = 1; x <= r; x++) {
			cells.add(new int[] {x, r});
		}
		for (int z = r - 1; z >= -r; z--) {
			cells.add(new int[] {r, z});
		}
		for (int x = r - 1; x >= -r; x--) {
			cells.add(new int[] {x, -r});
		}
		for (int z = -r + 1; z <= r; z++) {
			cells.add(new int[] {-r, z});
		}
		for (int x = -r + 1; x <= 0; x++) {
			cells.add(new int[] {x, r});
		}
		return cells;
	}

	/**
	 * Raises the tower: footing, walls with a door to the south and slits, floors with the dead on them, the stair up
	 * the inside, the roof with its low battlement, lamps and a chest.
	 */
	private static void build(ServerLevel nether, Roost roost) {
		RandomSource random = nether.getRandom();
		BlockPos base = roost.base();
		int h = roost.height();
		BlockState brick = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		BlockState cracked = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		BlockState pillar = Blocks.POLISHED_BASALT.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		// the footing, and the inside cleared
		for (int dx = -WALL; dx <= WALL; dx++) {
			for (int dz = -WALL; dz <= WALL; dz++) {
				for (int dy = 0; dy >= -FOOTING; dy--) {
					pos.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
					if (dy == 0 || !nether.getBlockState(pos).isSolid()) {
						nether.setBlock(pos, Blocks.BLACKSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
				boolean wall = Math.abs(dx) == WALL || Math.abs(dz) == WALL;
				boolean corner = Math.abs(dx) == WALL && Math.abs(dz) == WALL;
				for (int dy = 1; dy <= h; dy++) {
					pos.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
					BlockState state = air;
					if (corner) {
						state = pillar;
					} else if (wall) {
						boolean door = dz == WALL && dx == 0 && dy <= 3;
						boolean slit = (dx == 0 || dz == 0) && dy > 3 && dy % FLOOR_EVERY >= 2 && dy % FLOOR_EVERY <= 3;
						state = door || slit ? air : random.nextInt(CRACKED_ONE_IN) == 0 ? cracked : brick;
					} else if (dy % FLOOR_EVERY == 0 && dy < h && Math.abs(dx) <= WALL - 2 && Math.abs(dz) <= WALL - 2) {
						state = brick;
					}
					nether.setBlock(pos, state, Block.UPDATE_CLIENTS);
				}
			}
		}
		// the roof and the air kept over it
		for (int dx = -ROOF; dx <= ROOF; dx++) {
			for (int dz = -ROOF; dz <= ROOF; dz++) {
				boolean edge = Math.abs(dx) == ROOF || Math.abs(dz) == ROOF;
				boolean corner = Math.abs(dx) == ROOF && Math.abs(dz) == ROOF;
				nether.setBlock(pos.set(base.getX() + dx, base.getY() + h + 1, base.getZ() + dz), corner ? pillar : brick, Block.UPDATE_CLIENTS);
				for (int dy = 2; dy <= ROOF_ROOM; dy++) {
					pos.set(base.getX() + dx, base.getY() + h + dy, base.getZ() + dz);
					BlockState state = air;
					if (dy == 2 && corner) {
						state = pillar;
					} else if (dy == 3 && corner) {
						state = Blocks.SOUL_LANTERN.defaultBlockState();
					} else if (dy == 2 && edge && (dx + dz & 1) == 0) {
						state = Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState();
					}
					nether.setBlock(pos, state, Block.UPDATE_CLIENTS);
				}
			}
		}
		// the stair: half a block a step round the inside, out through the roof
		List<int[]> ring = ring();
		BlockState low = Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState();
		BlockState high = low.setValue(SlabBlock.TYPE, SlabType.TOP);
		for (int k = 0; k <= 2 * h; k++) {
			int[] cell = ring.get(k % ring.size());
			int y = base.getY() + 1 + k / 2;
			pos.set(base.getX() + cell[0], y, base.getZ() + cell[1]);
			nether.setBlock(pos, (k & 1) == 0 ? low : high, Block.UPDATE_CLIENTS);
			for (int up = 1; up <= 3; up++) {
				pos.set(base.getX() + cell[0], y + up, base.getZ() + cell[1]);
				if (k > 2 * h - STAIR_MOUTH || nether.getBlockState(pos).is(Blocks.POLISHED_BLACKSTONE_BRICKS) && pos.getY() <= base.getY() + h) {
					nether.setBlock(pos, air, Block.UPDATE_CLIENTS);
				}
			}
		}
		// the dead on every floor, and what they kept
		for (int dy = 0; dy < h - 2; dy += FLOOR_EVERY) {
			for (int n = 0; n < GUARDS_PER_FLOOR; n++) {
				guard(nether, base.offset(random.nextInt(3) - 1, dy + 1, random.nextInt(3) - 1));
			}
		}
		BlockPos chest = base.offset(0, 1, -(WALL - 2));
		nether.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
		RandomizableContainer.setBlockEntityLootTable(nether, random, chest, BuiltInLootTables.NETHER_BRIDGE);
	}

	private static void guard(ServerLevel nether, BlockPos at) {
		WitherSkeleton guard = EntityTypes.WITHER_SKELETON.create(nether, EntitySpawnReason.STRUCTURE);
		if (guard == null) {
			return;
		}
		guard.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, nether.getRandom().nextFloat() * 360.0F, 0.0F);
		guard.finalizeSpawn(nether, nether.getCurrentDifficultyAt(at), EntitySpawnReason.STRUCTURE, null);
		guard.setPersistenceRequired();
		nether.addFreshEntity(guard);
	}

	// ---- ops -------------------------------------------------------------------------------------------------------------

	private static int foundHere(CommandContext<CommandSourceStack> c) {
		ServerLevel level = c.getSource().getLevel();
		BlockPos here = BlockPos.containing(c.getSource().getPosition());
		if (!Stacked.at(level, here, Level.NETHER)) {
			c.getSource().sendFailure(Component.literal("Dragon roosts are only in the Nether"));
			return 0;
		}
		Entity entity = c.getSource().getEntity();
		BlockPos ahead = entity == null ? here : here.relative(entity.getDirection(), ROOF + 4);
		Roost roost = found(level, ahead, here.getY(), true);
		if (roost == null) {
			c.getSource().sendFailure(Component.literal("No fit ground for a roost ahead of you (needs level, loaded ground and "
				+ (MIN_HEIGHT + ROOF_ROOM) + " blocks of open air over it)"));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("Founded a roost " + roost.height() + " high at " + roost.base().toShortString()), true);
		return 1;
	}

	/** Calls the nearest roost's dragon now, whoever is or isn't on its roof. */
	private static int callNow(CommandContext<CommandSourceStack> c) {
		ServerLevel level = c.getSource().getLevel();
		BlockPos here = BlockPos.containing(c.getSource().getPosition());
		Roost nearest = null;
		for (Roost r : roosts(level)) {
			if (nearest == null || r.base().distSqr(here) < nearest.base().distSqr(here)) {
				nearest = r;
			}
		}
		if (nearest == null || CALLS.containsKey(nearest.id()) || !call(level, nearest, level.getGameTime())) {
			c.getSource().sendFailure(Component.literal("No roost here, or no dragon could come to it"));
			return 0;
		}
		Roost called = nearest;
		c.getSource().sendSuccess(() -> Component.literal("A dragon comes to the roost at " + called.base().toShortString()), true);
		return 1;
	}

	private static int info(CommandContext<CommandSourceStack> c) {
		List<Roost> all = roosts(c.getSource().getLevel());
		StringBuilder out = new StringBuilder(all.size() + " dragon roosts");
		for (Roost r : all) {
			out.append("\n#").append(r.id()).append(" at ").append(r.base().toShortString()).append(", ").append(r.height()).append(" high");
		}
		c.getSource().sendSuccess(() -> Component.literal(out.toString()), false);
		return 1;
	}
}
