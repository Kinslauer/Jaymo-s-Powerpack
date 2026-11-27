package com.jaymoskingdoms;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Written starts (Andy, 2026-11-25: "is there a way i could curate a start like an adventure map? placing items and gear
 * specifically how i want? fostering a linear progression?"): a start someone built by hand, as an adventure map is, and
 * the life story's road {@link LifeStory.Start#WRITTEN} that wakes a newcomer in it. How to make one is in
 * {@code docs/WrittenStarts.md}.
 *
 * <p><b>What a start is.</b> One JSON file, {@code data/<ns>/written_starts/<id>.json}: the saved build it raises (a
 * structure-block save, {@code data/<ns>/structure/...nbt}, with everything its maker put in its chests, barrels, stands and
 * frames), which layer of it is the ground, how often it's drawn against the others, and the tale ({@link Tales}, its
 * {@code "start": {"by": "written"}}) that leads the player through it step by step.
 *
 * <p><b>The marks in the build.</b> Structure blocks in Data mode, named in their box: {@code spawn} (where they wake,
 * {@code spawn:north} to face north as the build was saved), {@code place:<key>} (a spot the tale's places
 * {@code "at": "written"} can name) and {@code gate:<key>} (what a tale step's {@code "opens"} opens). One gate mark: the
 * doors, trapdoors and fence gates touching it swing open and the iron bars touching it (and bars joined to those) are
 * taken away. Two or more marks of one gate: everything in the box between them opens, or is taken away if it can't. Every
 * structure block in the build is gone once it stands.
 *
 * <p><b>Where it stands.</b> Each player who takes the road gets a build of their own: out in the wild, from their own
 * setting-out point ({@link LifeStory}), on dry, natural, level ground, {@link #TOWN_KEEP} from any settlement's seat (a
 * way-hut is for those caught between towns). A party mate who takes the road after them wakes beside them in theirs.
 * The land is made behind the loading screen first, as every start's is. Once raised it stays: a new life raises another.
 *
 * <p><b>Shared starts.</b> (Andy, 2026-11-25: "Both, chosen per start") A start whose file says {@code "shared": true} is
 * raised once for the whole world, and every newcomer who draws it wakes in that same build, with the tale of their own:
 * at {@code "at": [x, z]} (on the ground there) or {@code [x, y, z]} (its ground layer at y), unturned, as its maker
 * placed it; with no {@code "at"}, wherever the first newcomer's start search set it down. What the first ones took from
 * its chests is gone for those after, and its opened gates stay open.
 */
public final class WrittenStarts {
	/** How far the ground under a build may rise and fall (blocks). */
	static final int FLAT = 2;
	/** How far from the spot the start search chose a level site is looked for, and the step between the sites looked at. */
	static final int SITE_REACH = 24, SITE_STEP = 6;
	/** No build stands this near a settlement's seat. */
	static final int TOWN_KEEP = 96;
	/** How far earth is filled down under a build's bottom layer where the land fell away. */
	static final int FILL_DOWN = 8;
	/** The most blocks one gate mark takes in (bars joined to bars). */
	static final int GATE_MOST = 64;

	/** A written start as read from its file. */
	record Def(String id, Identifier structure, int ground, int weight, String tale, boolean bare, boolean shared, @Nullable BlockPos at, boolean atGround) {
	}

	private static Map<String, Def> all = Map.of();

	/** Where a player's written start stands: which one, its spot, its marks (key to spot) and its gates (key to the blocks they open). */
	record Placed(String start, long place, Map<String, Long> marks, Map<String, List<Long>> gates, float yaw) {
		static final Codec<Placed> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("start").forGetter(Placed::start),
				Codec.LONG.fieldOf("place").forGetter(Placed::place),
				Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("marks", Map.of()).forGetter(Placed::marks),
				Codec.unboundedMap(Codec.STRING, Codec.LONG.listOf()).optionalFieldOf("gates", Map.of()).forGetter(Placed::gates),
				Codec.FLOAT.optionalFieldOf("yaw", 0.0F).forGetter(Placed::yaw))
			.apply(i, Placed::new));
	}

	/** On the overworld: the shared starts raised so far (start id to where it stands). */
	static final AttachmentType<Map<String, Placed>> SHARED = AttachmentRegistry.create(JaymosKingdoms.id("written_starts_shared"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Placed.CODEC)));

	/** On a player: the written start they woke in this life (kept through a death; a new life's replaces it). */
	static final AttachmentType<Placed> PLACED = AttachmentRegistry.create(JaymosKingdoms.id("written_start"),
		builder -> builder.persistent(Placed.CODEC).copyOnDeath());

	/** What {@link #raise} set up: where to stand, which way to look, and the start's place. */
	record Raised(Vec3 feet, float yaw, BlockPos place) {
	}

	private WrittenStarts() {
	}

	static void register() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(JaymosKingdoms.id("written_starts"),
			new SimpleJsonResourceReloadListener<>(ExtraCodecs.JSON, FileToIdConverter.json("written_starts")) {
				@Override
				protected void apply(Map<Identifier, JsonElement> loaded, ResourceManager manager, ProfilerFiller profiler) {
					Map<String, Def> read = new LinkedHashMap<>();
					loaded.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
						Def def = read(e.getKey(), e.getValue());
						if (def != null) {
							read.put(def.id(), def);
						}
					});
					all = read;
				}
			});
	}

	private static @Nullable Def read(Identifier file, JsonElement json) {
		String id = file.getNamespace().equals(JaymosKingdoms.MOD_ID) ? file.getPath() : file.toString();
		if (!json.isJsonObject()) {
			JaymosKingdoms.LOGGER.warn("Written start {} is not a JSON object; left out", id);
			return null;
		}
		JsonObject o = json.getAsJsonObject();
		Identifier structure = Identifier.tryParse(GsonHelper.getAsString(o, "structure", ""));
		if (structure == null) {
			JaymosKingdoms.LOGGER.warn("Written start {} names no structure; left out", id);
			return null;
		}
		BlockPos at = null;
		boolean atGround = false;
		if (o.has("at")) {
			com.google.gson.JsonArray a = GsonHelper.getAsJsonArray(o, "at");
			if (a.size() == 2) {
				at = new BlockPos(a.get(0).getAsInt(), 0, a.get(1).getAsInt());
				atGround = true;
			} else if (a.size() == 3) {
				at = new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
			} else {
				JaymosKingdoms.LOGGER.warn("Written start {}: 'at' must be [x, z] or [x, y, z]; it's ignored", id);
			}
		}
		return new Def(id, structure, Math.max(0, GsonHelper.getAsInt(o, "ground", 0)), Math.max(1, GsonHelper.getAsInt(o, "weight", 1)),
			GsonHelper.getAsString(o, "tale", ""), GsonHelper.getAsBoolean(o, "bare", false), GsonHelper.getAsBoolean(o, "shared", false), at, atGround);
	}

	/** Whether any written start is loaded (with none, the road is shut: {@link RoadLocks#shutKeys}). */
	static boolean any() {
		return !all.isEmpty();
	}

	/** A written start by weight, or null with none. */
	static @Nullable Def pick(RandomSource random) {
		int total = 0;
		for (Def d : all.values()) {
			total += d.weight();
		}
		if (total <= 0) {
			return null;
		}
		int lot = random.nextInt(total);
		for (Def d : all.values()) {
			lot -= d.weight();
			if (lot < 0) {
				return d;
			}
		}
		return null;
	}

	/** Whether {@code player}'s written start says to wake with nothing but what its build holds and its tale hands over. */
	static boolean bare(ServerPlayer player) {
		Placed placed = player.getAttached(PLACED);
		Def def = placed == null ? null : all.get(placed.start());
		return def != null && def.bare();
	}

	/**
	 * Raises a written start for {@code player} near {@code near} (a spot the start search chose; null: none found) and notes
	 * it on them; the spot to wake at, or null: no start is loaded, its build is missing, no level site is near, or the land
	 * isn't made yet (then {@code wanted} asks for it, and the start is tried again once it's made). A shared start already
	 * raised isn't raised again: they wake in it.
	 */
	static @Nullable Raised raise(ServerLevel level, ServerPlayer player, @Nullable BlockPos near, RandomSource random, LifeStory.@Nullable Wanted wanted) {
		Def def = pick(random);
		if (def == null) {
			return null;
		}
		Map<String, Placed> shared = level.getAttachedOrElse(SHARED, Map.of());
		Placed standing = def.shared() ? shared.get(def.id()) : null;
		if (standing != null) {
			player.setAttached(PLACED, standing);
			Long spawn = standing.marks().get("spawn");
			BlockPos place = BlockPos.of(standing.place());
			Vec3 feet = spawn != null ? Vec3.atBottomCenterOf(BlockPos.of(spawn)) : Vec3.atBottomCenterOf(level.getHeightmapPos(
				net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, place));
			return new Raised(feet, standing.yaw(), place);
		}
		Optional<StructureTemplate> found = level.getServer().getStructureTemplateManager().get(def.structure());
		if (found.isEmpty() || found.get().getSize().getX() == 0) {
			JaymosKingdoms.LOGGER.warn("Written start {}: no build {}", def.id(), def.structure());
			return null;
		}
		StructureTemplate template = found.get();
		// (one put where its maker said stands as they saved it; the rest are turned any way)
		Rotation rotation = def.at() != null ? Rotation.NONE : Rotation.getRandom(random);
		BlockPos pivot = new BlockPos(template.getSize().getX() / 2, 0, template.getSize().getZ() / 2);
		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setRotationPivot(pivot).setIgnoreEntities(false);
		BlockPos origin = def.at() != null ? fixed(level, template, settings, pivot, def, wanted)
			: near == null ? null : site(level, template, settings, pivot, def.ground(), near, wanted);
		if (origin == null) {
			return null;
		}
		template.placeInWorld(level, origin, origin, settings, random, Block.UPDATE_ALL);
		BoundingBox box = template.getBoundingBox(settings, origin);
		fillUnder(level, box);
		// the marks: read, then taken out of the world
		Map<String, Long> marks = new HashMap<>();
		Map<String, List<BlockPos>> gateMarks = new LinkedHashMap<>();
		Vec3 feet = null;
		float yaw = random.nextFloat() * 360.0F;
		for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(origin, settings, Blocks.STRUCTURE_BLOCK)) {
			level.setBlock(info.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			if (info.nbt() == null || !info.nbt().getStringOr("mode", "").equalsIgnoreCase("DATA")) {
				continue;
			}
			String mark = info.nbt().getStringOr("metadata", "").trim();
			if (mark.equals("spawn") || mark.startsWith("spawn:")) {
				feet = Vec3.atBottomCenterOf(info.pos());
				Direction facing = mark.length() > 6 ? Direction.byName(mark.substring(6)) : null;
				if (facing != null && facing.getAxis() != Direction.Axis.Y) {
					yaw = rotation.rotate(facing).toYRot();
				}
				marks.put("spawn", info.pos().asLong());
			} else if (mark.startsWith("place:") && mark.length() > 6) {
				marks.put(mark.substring(6), info.pos().asLong());
			} else if (mark.startsWith("gate:") && mark.length() > 5) {
				gateMarks.computeIfAbsent(mark.substring(5), k -> new ArrayList<>()).add(info.pos());
			}
		}
		Map<String, List<Long>> gates = new HashMap<>();
		for (Map.Entry<String, List<BlockPos>> e : gateMarks.entrySet()) {
			gates.put(e.getKey(), gate(level, e.getValue()));
		}
		BlockPos place = box.getCenter();
		if (feet == null) {
			// (a build with no spawn mark: its middle, on its ground layer)
			feet = Vec3.atBottomCenterOf(new BlockPos(place.getX(), origin.getY() + def.ground() + 1, place.getZ()));
			JaymosKingdoms.LOGGER.warn("Written start {} has no spawn mark; waking at its middle", def.id());
		}
		// (where they woke, for those who wake in a shared start after them)
		marks.put("spawn", BlockPos.containing(feet).asLong());
		Placed placed = new Placed(def.id(), place.asLong(), Map.copyOf(marks), Map.copyOf(gates), yaw);
		player.setAttached(PLACED, placed);
		if (def.shared()) {
			Map<String, Placed> now = new HashMap<>(shared);
			now.put(def.id(), placed);
			level.setAttached(SHARED, Map.copyOf(now));
		}
		JaymosKingdoms.LOGGER.info("Written start {} raised{} for {} at {}", def.id(), def.shared() ? " (shared)" : "", player.getName().getString(), place);
		return new Raised(feet, yaw, place);
	}

	/**
	 * Where a start with an {@code "at"} stands: its middle on that column, its ground layer at the given height or on the
	 * ground's top there; null while its land isn't made (asked of {@code wanted}).
	 */
	private static @Nullable BlockPos fixed(ServerLevel level, StructureTemplate template, StructurePlaceSettings settings, BlockPos pivot, Def def,
		LifeStory.@Nullable Wanted wanted) {
		BlockPos centre = def.at();
		BlockPos at = new BlockPos(centre.getX() - pivot.getX(), 0, centre.getZ() - pivot.getZ());
		BoundingBox box = template.getBoundingBox(settings, at);
		if (!Loaded.made(level, box.minX() - 1, box.minZ() - 1, box.maxX() + 1, box.maxZ() + 1)) {
			if (wanted != null) {
				wanted.want(box.minX(), box.minZ());
				wanted.want(box.maxX(), box.minZ());
				wanted.want(box.minX(), box.maxZ());
				wanted.want(box.maxX(), box.maxZ());
			}
			return null;
		}
		int y = centre.getY();
		if (def.atGround()) {
			y = WaysideInns.groundY(level, centre.getX(), centre.getZ());
			if (y == Integer.MIN_VALUE) {
				// (built on, or wet: the top of whatever is there)
				y = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre).getY() - 1;
			}
		}
		return new BlockPos(at.getX(), y - def.ground(), at.getZ());
	}

	/**
	 * Where {@code template} can stand, near {@code near}: its origin, the ground layer on the ground's top; or null. A site
	 * whose land isn't made is asked of {@code wanted} and passed over for now.
	 */
	private static @Nullable BlockPos site(ServerLevel level, StructureTemplate template, StructurePlaceSettings settings, BlockPos pivot, int ground,
		BlockPos near, LifeStory.@Nullable Wanted wanted) {
		MinecraftServer server = level.getServer();
		List<BlockPos> seats = new ArrayList<>();
		for (Faction f : Factions.all(server)) {
			if (f.seat() != null && !f.fallen()) {
				seats.add(f.seat());
			}
		}
		for (int r = 0; r <= SITE_REACH; r += SITE_STEP) {
			for (int dx = -r; dx <= r; dx += SITE_STEP) {
				for (int dz = -r; dz <= r; dz += SITE_STEP) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					BlockPos centre = near.offset(dx, 0, dz);
					BlockPos at = new BlockPos(centre.getX() - pivot.getX(), 0, centre.getZ() - pivot.getZ());
					BoundingBox box = template.getBoundingBox(settings, at);
					if (!Loaded.made(level, box.minX() - 1, box.minZ() - 1, box.maxX() + 1, box.maxZ() + 1)) {
						if (wanted != null) {
							wanted.want(box.minX(), box.minZ());
							wanted.want(box.maxX(), box.minZ());
							wanted.want(box.minX(), box.maxZ());
							wanted.want(box.maxX(), box.maxZ());
						}
						continue;
					}
					boolean nearTown = false;
					for (BlockPos seat : seats) {
						if (Math.hypot(seat.getX() - centre.getX(), seat.getZ() - centre.getZ()) < TOWN_KEEP) {
							nearTown = true;
						}
					}
					if (nearTown) {
						continue;
					}
					int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, count = 0;
					long sum = 0;
					for (int x = box.minX(); x <= box.maxX() && lo != Integer.MIN_VALUE; x++) {
						for (int z = box.minZ(); z <= box.maxZ(); z++) {
							int y = WaysideInns.groundY(level, x, z);
							if (y == Integer.MIN_VALUE) {
								lo = Integer.MIN_VALUE;
								break;
							}
							lo = Math.min(lo, y);
							hi = Math.max(hi, y);
							sum += y;
							count++;
						}
					}
					if (lo == Integer.MIN_VALUE || count == 0 || hi - lo > FLAT) {
						continue;
					}
					int y = (int)Math.round(sum / (double)count);
					BlockPos origin = new BlockPos(at.getX(), y - ground, at.getZ());
					if (!level.isInsideBuildHeight(origin) || !level.isInsideBuildHeight(origin.above(template.getSize().getY()))) {
						continue;
					}
					return origin;
				}
			}
		}
		return null;
	}

	/** Earth (stone under stone) under the build's bottom layer, down to the ground, where the land fell away. */
	private static void fillUnder(ServerLevel level, BoundingBox box) {
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				BlockPos bottom = new BlockPos(x, box.minY(), z);
				if (level.getBlockState(bottom).isAir()) {
					continue;
				}
				BlockState fill = level.getBlockState(bottom).is(BlockTags.BASE_STONE_OVERWORLD) ? Blocks.STONE.defaultBlockState() : Blocks.DIRT.defaultBlockState();
				for (int i = 1; i <= FILL_DOWN; i++) {
					BlockPos at = bottom.below(i);
					BlockState s = level.getBlockState(at);
					if (!(s.isAir() || s.canBeReplaced() || !level.getFluidState(at).isEmpty())) {
						break;
					}
					level.setBlock(at, fill, Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	/** The blocks a gate's marks take in ({@link WrittenStarts}'s rules), as they stand now the build is up. */
	private static List<Long> gate(ServerLevel level, List<BlockPos> marks) {
		Set<Long> out = new HashSet<>();
		if (marks.size() >= 2) {
			int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
			for (BlockPos m : marks) {
				minX = Math.min(minX, m.getX());
				minY = Math.min(minY, m.getY());
				minZ = Math.min(minZ, m.getZ());
				maxX = Math.max(maxX, m.getX());
				maxY = Math.max(maxY, m.getY());
				maxZ = Math.max(maxZ, m.getZ());
			}
			for (BlockPos p : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
				if (!level.getBlockState(p).isAir()) {
					out.add(p.asLong());
				}
			}
		} else if (marks.size() == 1) {
			BlockPos mark = marks.get(0);
			ArrayDeque<BlockPos> bars = new ArrayDeque<>();
			for (Direction d : Direction.values()) {
				BlockPos p = mark.relative(d);
				BlockState s = level.getBlockState(p);
				if (s.hasProperty(BlockStateProperties.OPEN)) {
					out.add(p.asLong());
					// (both halves of a door)
					if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
						out.add((s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
							? p.above() : p.below()).asLong());
					}
				} else if (s.getBlock() instanceof IronBarsBlock) {
					bars.add(p.immutable());
				}
			}
			while (!bars.isEmpty() && out.size() < GATE_MOST) {
				BlockPos p = bars.poll();
				if (!out.add(p.asLong())) {
					continue;
				}
				for (Direction d : Direction.values()) {
					BlockPos q = p.relative(d);
					if (!out.contains(q.asLong()) && level.getBlockState(q).getBlock() instanceof IronBarsBlock) {
						bars.add(q.immutable());
					}
				}
			}
		}
		return List.copyOf(out);
	}

	/** (Start Together) {@code player} wakes in {@code mate}'s written start: theirs is noted as this player's too. */
	static void share(ServerPlayer player, @Nullable ServerPlayer mate) {
		Placed theirs = mate == null ? null : mate.getAttached(PLACED);
		if (theirs != null) {
			player.setAttached(PLACED, theirs);
		}
	}

	/** {@code player}'s written start's tale is begun ({@link Tales#written}), if it names one. */
	static void begin(ServerPlayer player) {
		Placed placed = player.getAttached(PLACED);
		Def def = placed == null ? null : all.get(placed.start());
		if (def == null || def.tale().isEmpty()) {
			return;
		}
		if (!Tales.written(player, def.tale(), BlockPos.of(placed.place()), placed.marks())) {
			JaymosKingdoms.LOGGER.warn("Written start {}: its tale {} couldn't be begun (no such tale, not by \"written\", or already going or told)",
				def.id(), def.tale());
		}
	}

	/** The gates {@code keys} of {@code player}'s written start open (a tale step's {@code "opens"}, once it's done). */
	static void open(ServerPlayer player, List<String> keys) {
		Placed placed = player.getAttached(PLACED);
		if (placed == null || keys.isEmpty() || !(player.level() instanceof ServerLevel level) || level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
			return;
		}
		for (String key : keys) {
			List<Long> blocks = placed.gates().get(key);
			if (blocks == null) {
				JaymosKingdoms.LOGGER.warn("Written start {}: no gate {}", placed.start(), key);
				continue;
			}
			boolean sounded = false;
			for (long l : blocks) {
				BlockPos p = BlockPos.of(l);
				if (!level.isLoaded(p)) {
					continue;
				}
				BlockState s = level.getBlockState(p);
				if (s.hasProperty(BlockStateProperties.OPEN)) {
					if (!s.getValue(BlockStateProperties.OPEN)) {
						level.setBlock(p, s.setValue(BlockStateProperties.OPEN, true), Block.UPDATE_ALL);
						if (!sounded) {
							level.playSound(null, p, s.is(BlockTags.WOODEN_DOORS) || s.is(BlockTags.WOODEN_TRAPDOORS) || s.is(BlockTags.FENCE_GATES)
								? SoundEvents.WOODEN_DOOR_OPEN : SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0F, 1.0F);
							sounded = true;
						}
					}
				} else if (!s.isAir()) {
					level.destroyBlock(p, false);
				}
			}
		}
	}
}
