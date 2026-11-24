package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Wayside inns (village mods patch 6, row rc_inn; Recurrent Complex's idea, our own design): halfway along a King's Road,
 * a day's ride from each end, stands an inn ({@code jaymoskingdoms:smalls/wayside_inn}, drawn by
 * {@code tools/small_designs/inn.py}): a two-storey timber house with a hearth, a counter, tables and three rooms, a
 * stable beside it, a name sign by its door and a bounty board in its yard. Its keeper, an innkeeper, greets whoever comes
 * in. It is the Hearthkeeper's custom made a house: a hearth open to any traveller (Lore.md).
 *
 * <p><b>Built unseen.</b> Every {@link #LOOK_TICKS} each road at least {@link #INN_MIN_LENGTH} long that has no inn yet is
 * looked over, one at a time: near its middle (then further along either way), on either side, a spot {@link #SIDE}
 * blocks off the road, where the chunks are made, the road is paved there, no player is within {@link #HIDE}, the ground is
 * natural, dry and level to within {@link #FLAT}, and no road comes near. The template is turned so its door faces the road,
 * its footing filled to the ground, and a path laid out to the road. A road with no such spot after {@link #MOST_TRIES}
 * looks is given up on. What was built, per road, is saved on the Overworld ({@link #INNS}).
 *
 * <p><b>The board.</b> A {@link BountyBoard} stands in the yard, and is told to the villagers ({@link Bounties#boardUp});
 * the innkeeper is the villager that pins notices to it. {@link #owner} gives the court of its road's nearer end, for
 * {@code Bounties.courtAt} (far from every settlement, a board has no court of its own).
 *
 * <p><b>The keeper.</b> An MCA villager (or the game's, without MCA) in the cook's trade, never despawning and kept to the
 * inn. If they die, a new one comes {@link #REPLACE_DAYS} days after the loss is seen.
 */
final class WaysideInns {
	/** The template, and where its spots are in it (the same numbers {@code inn.py} prints). */
	static final Identifier TEMPLATE = JaymosKingdoms.id("smalls/wayside_inn");
	static final BlockPos PIVOT = new BlockPos(6, 0, 7);
	static final int WIDTH = 14, DEPTH = 16;
	/** The sign post's top (a standing sign), the board's bottom middle block (it faces the way the door does), the keeper's spot, the path's first block. */
	static final BlockPos SIGN = new BlockPos(7, 2, 2), BOARD = new BlockPos(12, 1, 2), KEEPER = new BlockPos(6, 1, 8), PATH = new BlockPos(4, 0, 0);

	/** Roads shorter than this (blocks: route points times {@link Roads#STEP}) get no inn. */
	static final int INN_MIN_LENGTH = 320;
	/** How often (ticks) the roads are looked over; how many route points apart, and how many, are tried around the middle. */
	static final int LOOK_TICKS = 1200, POINT_GAP = 3, TRY_POINTS = 9;
	/** Blocks between the road's line and the inn's front edge. */
	static final int SIDE = 8;
	/** No player within this many blocks of the spot while it's built (it goes up unseen). */
	static final double HIDE = 72.0;
	/** The ground under the whole template differs by at most this many blocks. */
	static final int FLAT = 3;
	/** Every road's line stays at least this far from the template's box; a settlement's seat at least {@link #TOWN_KEEP}. */
	static final double CLEAR = 4.0, TOWN_KEEP = 110.0;
	/** Looks at a road that found no spot, before it's given up on. */
	static final int MOST_TRIES = 6;
	/** Filled under the footing, at most (blocks). */
	static final int FILL_MOST = 6;

	/** The keeper's tag, how far from the inn one is looked for, how far from the spot they keep to, and the trade. */
	static final String TAG = "jaymoskingdoms_innkeeper";
	static final int KEEPER_BOX = 40, HOME = 16;
	/** How often (ticks) the keepers are looked over; days a lost keeper is gone before a new one comes. */
	static final int KEEP_TICKS = 200, REPLACE_DAYS = 3;
	/** How often (ticks) a player's coming in is looked for; how near to the keeper's spot (blocks) counts as in; greetings the keeper has; a day (ticks). */
	static final int GREET_TICKS = 40, GREET_LINES = 4;
	static final double GREET_RANGE = 7.0;
	static final long DAY = 24000L;
	/** Words each half of an inn's name is picked from ({@code sign.jaymoskingdoms.inn.first.<n>} and {@code .second.<n>}). */
	static final int WORDS = 8;

	/** "The keeper is there": the value of {@link Inn#gone} when they were last seen. */
	static final long PRESENT = -1L;

	/**
	 * What's been done on a road: where the keeper's spot is ({@link Roads#NONE} while there's no inn), how many looks found
	 * no spot, and when the keeper was first found missing ({@link #PRESENT}: not missing).
	 */
	record Inn(long at, int tries, long gone) {
		static final Codec<Inn> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.optionalFieldOf("at", Roads.NONE).forGetter(Inn::at),
				Codec.INT.optionalFieldOf("tries", 0).forGetter(Inn::tries),
				Codec.LONG.optionalFieldOf("gone", PRESENT).forGetter(Inn::gone))
			.apply(i, Inn::new));
	}

	/** On the Overworld: each road's key ({@link Roads.Road#key}) to what's been done on it. Must be class-loaded at init. */
	static final AttachmentType<Map<String, Inn>> INNS = AttachmentRegistry.create(JaymosKingdoms.id("wayside_inns"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Inn.CODEC)));

	/** Who was greeted when (player id and road key to game time); not saved, so a restart greets again. */
	private static final Map<String, Long> GREETED = new HashMap<>();

	/** A spot found: where the template's centre goes, the way its door faces (toward the road), and how it's turned. */
	private record Site(StructureTemplate template, BlockPos centre, Direction front, Rotation rotation, StructurePlaceSettings settings, int y0, BoundingBox box) {
		BlockPos origin() {
			return new BlockPos(this.centre.getX() - PIVOT.getX(), this.y0, this.centre.getZ() - PIVOT.getZ());
		}

		/** Where the template's {@code local} block is in the world. */
		BlockPos world(BlockPos local) {
			return StructureTemplate.calculateRelativePosition(this.settings, local).offset(this.origin());
		}
	}

	private WaysideInns() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ServerLevel level = Factions.level(server);
			long now = level.getGameTime();
			if (now % LOOK_TICKS == LOOK_TICKS / 2) {
				look(server, level, now);
			}
			if (now % KEEP_TICKS == 7) {
				keep(level, now);
			}
			if (now % GREET_TICKS == 3) {
				greet(level, now);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> GREETED.clear());
	}

	private static void put(ServerLevel level, String key, Inn inn) {
		Map<String, Inn> next = new HashMap<>(level.getAttachedOrElse(INNS, Map.of()));
		next.put(key, inn);
		level.setAttached(INNS, Map.copyOf(next));
	}

	/**
	 * The faction whose road runs by the inn nearest {@code pos} (within {@code reach}), the nearer end's, or null. The
	 * board's court: {@code Bounties.courtAt} turns to it where no settlement is near.
	 */
	static @Nullable Faction owner(MinecraftServer server, BlockPos pos, double reach) {
		for (Map.Entry<String, Inn> e : Factions.level(server).getAttachedOrElse(INNS, Map.<String, Inn>of()).entrySet()) {
			if (e.getValue().at() == Roads.NONE || BlockPos.of(e.getValue().at()).distSqr(pos) > reach * reach) {
				continue;
			}
			for (Roads.Road road : Roads.network(server).roads()) {
				if (road.key().equals(e.getKey())) {
					Faction a = Factions.get(server, road.a()), b = Factions.get(server, road.b());
					if (a == null || b == null) {
						return a != null ? a : b;
					}
					return a.seat().distSqr(pos) <= b.seat().distSqr(pos) ? a : b;
				}
			}
		}
		return null;
	}

	// ---- finding a spot and building --------------------------------------------------------------------------------------

	/** Looks over the roads without an inn, one at a time; the first that has a spot gets its inn. */
	private static void look(MinecraftServer server, ServerLevel level, long now) {
		Map<String, Inn> inns = level.getAttachedOrElse(INNS, Map.<String, Inn>of());
		for (Roads.Road road : Roads.network(server).roads()) {
			Inn had = inns.getOrDefault(road.key(), new Inn(Roads.NONE, 0, PRESENT));
			if (had.at() != Roads.NONE || had.tries() >= MOST_TRIES || road.points().size() * Roads.STEP < INN_MIN_LENGTH) {
				continue;
			}
			boolean[] waiting = {false};
			Site site = find(server, level, road, had.tries(), waiting);
			if (site != null) {
				put(level, road.key(), build(level, road, site, now));
				return;
			}
			if (!waiting[0]) {
				// (looked over where it could be, and nowhere would do: one look fewer to go)
				put(level, road.key(), new Inn(Roads.NONE, had.tries() + 1, PRESENT));
				return;
			}
		}
	}

	/**
	 * A spot for an inn on {@code road}, or null. {@code waiting[0]} is set if something wasn't ready (chunks not made, the
	 * road not paved there, a player near), so a null isn't yet "nowhere".
	 */
	private static @Nullable Site find(MinecraftServer server, ServerLevel level, Roads.Road road, int tries, boolean[] waiting) {
		Optional<StructureTemplate> template = server.getStructureTemplateManager().get(TEMPLATE);
		if (template.isEmpty()) {
			waiting[0] = true;
			return null;
		}
		List<Long> pts = road.points();
		int n = pts.size(), mid = n / 2;
		RandomSource random = RandomSource.create(road.key().hashCode() * 31L + tries);
		boolean firstSide = random.nextBoolean();
		List<BlockPos> seats = Roads.towns(server).stream().map(Faction::seat).toList();
		for (int k = 0; k < TRY_POINTS; k++) {
			int i = mid + (k + 1) / 2 * POINT_GAP * (k % 2 == 1 ? 1 : -1);
			if (i < 1 || i >= n - 1) {
				continue;
			}
			long p = pts.get(i);
			int px = BlockPos.getX(p), pz = BlockPos.getZ(p);
			if (!Loaded.made(level, px, pz) || !road.laid().contains(ChunkPos.pack(px >> 4, pz >> 4))) {
				waiting[0] = true;
				continue;
			}
			for (int s = 0; s < 2; s++) {
				Site site = site(level, template.get(), road, i, (s == 0) == firstSide ? 1 : -1, seats, waiting);
				if (site != null) {
					return site;
				}
			}
		}
		return null;
	}

	/** The spot {@link #SIDE} blocks off the road at route point {@code i}, on {@code side} of it (1 or -1), or null. */
	private static @Nullable Site site(ServerLevel level, StructureTemplate template, Roads.Road road, int i, int side, List<BlockPos> seats, boolean[] waiting) {
		List<Long> pts = road.points();
		long p = pts.get(i), before = pts.get(i - 1), after = pts.get(i + 1);
		double dx = BlockPos.getX(after) - BlockPos.getX(before), dz = BlockPos.getZ(after) - BlockPos.getZ(before);
		double len = Math.max(1.0E-3, Math.hypot(dx, dz));
		// away from the road, to the inn's side; the door looks back at it
		Direction away = Direction.getApproximateNearest((float)(-dz / len * side), 0.0F, (float)(dx / len * side));
		if (away.getAxis() == Direction.Axis.Y) {
			return null;
		}
		Direction front = away.getOpposite();
		Rotation rotation = null;
		for (Rotation r : Rotation.values()) {
			if (r.rotate(Direction.NORTH) == front) {
				rotation = r;
			}
		}
		if (rotation == null) {
			return null;
		}
		int reach = SIDE + PIVOT.getZ();
		BlockPos centre = new BlockPos(BlockPos.getX(p) + away.getStepX() * reach, 0, BlockPos.getZ(p) + away.getStepZ() * reach);
		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setRotationPivot(PIVOT).setIgnoreEntities(true);
		BlockPos at = new BlockPos(centre.getX() - PIVOT.getX(), 0, centre.getZ() - PIVOT.getZ());
		BoundingBox box = template.getBoundingBox(settings, at);
		if (!Loaded.made(level, box.minX() - 1, box.minZ() - 1, box.maxX() + 1, box.maxZ() + 1)) {
			waiting[0] = true;
			return null;
		}
		for (ServerPlayer player : level.players()) {
			double px = player.getX() - centre.getX(), pz = player.getZ() - centre.getZ();
			if (px * px + pz * pz < HIDE * HIDE) {
				waiting[0] = true;
				return null;
			}
		}
		for (BlockPos seat : seats) {
			if (Math.hypot(seat.getX() - centre.getX(), seat.getZ() - centre.getZ()) < TOWN_KEEP) {
				return null;
			}
		}
		// level, dry and natural ground under all of it
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, count = 0;
		long sum = 0;
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				int ground = groundY(level, x, z);
				if (ground == Integer.MIN_VALUE) {
					return null;
				}
				lo = Math.min(lo, ground);
				hi = Math.max(hi, ground);
				sum += ground;
				count++;
			}
		}
		if (hi - lo > FLAT) {
			return null;
		}
		// no road (this one or another) near the box
		MinecraftServer server = level.getServer();
		for (Roads.Road other : Roads.network(server).roads()) {
			Roads.Line line = Roads.line(other);
			for (int x = box.minX(); x <= box.maxX(); x += 2) {
				for (int z = box.minZ(); z <= box.maxZ(); z += 2) {
					boolean edge = x <= box.minX() + 1 || x >= box.maxX() - 1 || z <= box.minZ() + 1 || z >= box.maxZ() - 1;
					if (edge && line.near(x, z, CLEAR) && line.project(x + 0.5, z + 0.5)[1] < CLEAR) {
						return null;
					}
				}
			}
		}
		return new Site(template, centre, front, rotation, settings, (int)Math.round(sum / (double)count), box);
	}

	/** Ground nobody built: what an inn may stand on (the same as a road may be laid on, {@code Roads.natural}). */
	private static boolean natural(BlockState s) {
		return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.SNOW)
			|| s.is(BlockTags.TERRACOTTA) || s.is(BlockTags.MUD) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.SANDSTONE)
			|| s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.ICE) || s.is(Blocks.PACKED_ICE) || s.is(Castle.NATURAL_GROUND);
	}

	/**
	 * The height of the natural, dry ground top at {@code x, z} (trunks and plants over it don't count: the template digs
	 * them out), or {@link Integer#MIN_VALUE} if it is built, wet or not there. The column's chunk must be made.
	 */
	private static int groundY(ServerLevel level, int x, int z) {
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z)).below();
		for (int i = 0; i < 12; i++) {
			BlockState s = level.getBlockState(top);
			if (!(s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.isAir() || (s.canBeReplaced() && level.getFluidState(top).isEmpty()))) {
				break;
			}
			top = top.below();
		}
		if (!natural(level.getBlockState(top)) || !level.getFluidState(top).isEmpty() || !level.getFluidState(top.above()).isEmpty()) {
			return Integer.MIN_VALUE;
		}
		return top.getY();
	}

	/** Puts the inn up at {@code site}: the template, the footing filled down, the sign, the board, the path and the keeper. */
	private static Inn build(ServerLevel level, Roads.Road road, Site site, long now) {
		BlockPos origin = site.origin();
		site.template().placeInWorld(level, origin, origin, site.settings(), level.getRandom(), Block.UPDATE_ALL);
		fillUnder(level, site);
		sign(level, road, site);
		BlockPos anchor = site.world(BOARD);
		level.setBlock(anchor, BountyBoard.partState(Bounties.BOARD, site.front(), 1, 0), Block.UPDATE_ALL);
		BountyBoard.build(level, anchor, Bounties.BOARD, site.front());
		Bounties.boardUp(level, anchor);
		path(level, road, site);
		BlockPos at = site.world(KEEPER);
		boolean kept = spawnKeeper(level, at);
		JaymosKingdoms.LOGGER.debug("wayside inn for road {} at {}", road.key(), at);
		// (no keeper made: one comes after the usual wait)
		return new Inn(at.asLong(), 0, kept ? PRESENT : now - REPLACE_DAYS * DAY);
	}

	/** Under every placed footing block, earth (stone under stone) down to the ground, where the land fell away. */
	private static void fillUnder(ServerLevel level, Site site) {
		for (int x = 0; x < WIDTH; x++) {
			for (int z = 0; z < DEPTH; z++) {
				BlockPos at = site.world(new BlockPos(x, 0, z));
				BlockState footing = level.getBlockState(at);
				if (footing.isAir()) {
					continue;
				}
				BlockState fill = footing.is(Blocks.COBBLESTONE) ? footing : Blocks.DIRT.defaultBlockState();
				for (int d = 1; d <= FILL_MOST; d++) {
					BlockPos under = at.below(d);
					BlockState s = level.getBlockState(under);
					if (!(s.isAir() || s.canBeReplaced())) {
						break;
					}
					level.setBlock(under, fill, Block.UPDATE_ALL);
				}
			}
		}
	}

	/** The name sign on its post by the door, facing the road: the inn's name on the front, a few words on the back. */
	private static void sign(ServerLevel level, Roads.Road road, Site site) {
		BlockPos at = site.world(SIGN);
		level.setBlock(at, Blocks.SPRUCE_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, RotationSegment.convertToSegment(site.front().toYRot())),
			Block.UPDATE_ALL);
		if (level.getBlockEntity(at) instanceof SignBlockEntity sign) {
			RandomSource random = RandomSource.create(road.key().hashCode());
			Component first = Component.translatable("sign.jaymoskingdoms.inn.first." + random.nextInt(WORDS));
			Component second = Component.translatable("sign.jaymoskingdoms.inn.second." + random.nextInt(WORDS));
			List<Component> front = List.of(Component.empty(), Component.translatable("sign.jaymoskingdoms.inn.the", first), second, Component.empty());
			List<Component> back = List.of(Component.empty(), Component.translatable("sign.jaymoskingdoms.inn.back.0"),
				Component.translatable("sign.jaymoskingdoms.inn.back.1"), Component.empty());
			sign.setText(new SignText(front, front, DyeColor.BLACK, false), SignTextSlot.FRONT);
			sign.setText(new SignText(back, back, DyeColor.BLACK, false), SignTextSlot.BACK);
			sign.setWaxed(true);
			sign.setChanged();
			BlockState state = level.getBlockState(at);
			level.sendBlockUpdated(at, state, state, Block.UPDATE_ALL);
		}
	}

	/** A dirt path from the yard out toward the road, over earth only, stopping at the road's edge or the first chunk not made. */
	private static void path(ServerLevel level, Roads.Road road, Site site) {
		Roads.Line line = Roads.line(road);
		BlockPos start = site.world(PATH);
		for (int step = 1; step <= SIDE; step++) {
			BlockPos col = start.relative(site.front(), step);
			if (!Loaded.made(level, col) || line.project(col.getX() + 0.5, col.getZ() + 0.5)[1] <= Roads.HALF + 1) {
				return;
			}
			int ground = groundY(level, col.getX(), col.getZ());
			if (ground == Integer.MIN_VALUE) {
				return;
			}
			BlockPos top = new BlockPos(col.getX(), ground, col.getZ());
			BlockState above = level.getBlockState(top.above());
			if (level.getBlockState(top).is(BlockTags.DIRT) && (above.isAir() || above.canBeReplaced())) {
				level.setBlock(top.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				level.setBlock(top, Blocks.DIRT_PATH.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
	}

	// ---- the keeper -------------------------------------------------------------------------------------------------------

	/** An innkeeper at {@code at}, kept to the inn; false if none could be made. */
	private static boolean spawnKeeper(ServerLevel level, BlockPos at) {
		Villager keeper = McaCompat.visitor(level, Vec3.atBottomCenterOf(at), Professions.COOK, false);
		if (keeper == null) {
			return false;
		}
		keeper.addTag(TAG);
		keeper.setPersistenceRequired();
		// (a little experience, so the game doesn't take the trade off them for want of a job site)
		keeper.setVillagerXp(Math.max(1, keeper.getVillagerXp()));
		keeper.setHomeTo(at, HOME);
		return true;
	}

	private static List<Villager> keepers(ServerLevel level, BlockPos at) {
		return level.getEntitiesOfClass(Villager.class, new AABB(at).inflate(KEEPER_BOX), v -> v.isAlive() && v.entityTags().contains(TAG));
	}

	/** Each inn that's loaded has its keeper: a keeper missing for {@link #REPLACE_DAYS} days is replaced. */
	private static void keep(ServerLevel level, long now) {
		for (Map.Entry<String, Inn> e : level.getAttachedOrElse(INNS, Map.<String, Inn>of()).entrySet()) {
			Inn inn = e.getValue();
			if (inn.at() == Roads.NONE) {
				continue;
			}
			BlockPos at = BlockPos.of(inn.at());
			if (!Loaded.made(level, at) || !level.isPositionEntityTicking(at)) {
				continue;
			}
			if (!keepers(level, at).isEmpty()) {
				if (inn.gone() != PRESENT) {
					put(level, e.getKey(), new Inn(inn.at(), inn.tries(), PRESENT));
				}
			} else if (inn.gone() == PRESENT) {
				put(level, e.getKey(), new Inn(inn.at(), inn.tries(), now));
			} else if (now - inn.gone() >= REPLACE_DAYS * DAY && spawnKeeper(level, at)) {
				put(level, e.getKey(), new Inn(inn.at(), inn.tries(), PRESENT));
			}
		}
	}

	/** A player who comes in is greeted by the keeper, once a day. */
	private static void greet(ServerLevel level, long now) {
		Map<String, Inn> inns = level.getAttachedOrElse(INNS, Map.<String, Inn>of());
		if (inns.isEmpty()) {
			return;
		}
		for (Map.Entry<String, Inn> e : inns.entrySet()) {
			if (e.getValue().at() == Roads.NONE) {
				continue;
			}
			BlockPos at = BlockPos.of(e.getValue().at());
			Vec3 spot = Vec3.atCenterOf(at);
			for (ServerPlayer player : level.players()) {
				String who = player.getStringUUID() + "@" + e.getKey();
				Long last = GREETED.get(who);
				if (player.isSpectator() || player.distanceToSqr(spot) > GREET_RANGE * GREET_RANGE || last != null && now - last < DAY) {
					continue;
				}
				Villager nearest = null;
				for (Villager keeper : keepers(level, at)) {
					if (!keeper.isSleeping() && (nearest == null || keeper.distanceToSqr(player) < nearest.distanceToSqr(player))) {
						nearest = keeper;
					}
				}
				if (nearest != null) {
					GREETED.put(who, now);
					Speech.tell(level, nearest, player, Component.translatable("speech.jaymoskingdoms.inn.greet." + level.getRandom().nextInt(GREET_LINES)), Speech.WHITE);
				}
			}
		}
	}
}
