package com.jaymoskingdoms;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jspecify.annotations.Nullable;

/**
 * A hold's own plans, taken with it. Andy (2026-11-14): "taking over an area like this from bandits or illagers should
 * allow you to add it to a schematic, so your kingdom can build it".
 * <ul>
 * <li><b>Taken.</b> When a camp's leader falls (any camp: barbarians, illagers, goblins, orcs, pirates, bandits,
 * smugglers, the illager sites and the desert arena; its hold cleared, or a sworn captain taking its seat), the place is
 * drawn as it stands that moment, before anything of it rots: only what its people built, not the ground under it, the
 * plants, water and ore, nor the trees growing through it. Whoever struck the blow gets the plans; anyone else's kill
 * leaves them lying where the leader fell. Nothing is taken from a camp the Reaper's host takes ({@link ReapedHolds}).</li>
 * <li><b>Read.</b> Using the plans copies the place among the player's designs ({@link Designs}) and turns them into a
 * drawn {@link MasonTable} building plan: lay it out and fill it by hand, have a mason's table build it, or offer it to a
 * settlement at one ({@link Commissions}), whose builders put it up even as big as the place stood
 * ({@link Designs#CAPTURED_SIDE}).</li>
 * <li><b>Tiers.</b> The plans keep the tier of the place (an illager site's own, else the tier its taker stood in): a
 * player who hasn't opened that tier can't make them out yet ({@link WorldTiers#open}).</li>
 * </ul>
 * The drawings wait in the world's save ({@code jaymoskingdoms_designs/captured/}) until the plans are read.
 */
public final class SitePlans {
	/** In a design's file: the place it was copied from. */
	static final String FROM = "captured_from";
	/** Where the plans' details sit in their custom data. */
	static final String TAG = "jaymoskingdoms_site_plans";
	/** The folder (inside the designs' own) the drawings wait in. */
	static final String FOLDER = "captured";

	public static Item PLANS;

	private SitePlans() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("captured_plans"));
		PLANS = Registry.register(BuiltInRegistries.ITEM, key, new Plans(new Item.Properties().setId(key).stacksTo(1)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.insertAfter(MasonTable.PLAN, PLANS));
	}

	// ---- taking them ------------------------------------------------------------------------------------------------

	/** Camp {@code campId}'s leader fell: the place is drawn, and its plans go to whoever struck the blow. */
	static void taken(ServerLevel level, LivingEntity chief, int campId, DamageSource source) {
		MinecraftServer server = level.getServer();
		Faction camp = Factions.get(server, campId);
		if (camp == null || camp.fallen() || camp.type() != FactionType.BARBARIAN) {
			return;
		}
		IllagerSites.Site site = IllagerSites.site(server, campId);
		ServerPlayer taker = source.getEntity() instanceof ServerPlayer p ? p : null;
		WorldTiers.Tier tier = WorldTiers.Tier.HEARTH;
		if (site != null && IllagerSites.PLANS.containsKey(site.path())) {
			tier = IllagerSites.PLANS.get(site.path()).tier();
		} else if (taker != null) {
			tier = WorldTiers.of(taker);
		}
		CompoundTag drawn = draw(level, grounds(server, camp, site), site == null ? null : built(level, site));
		if (drawn == null) {
			return;
		}
		drawn.putString(FROM, camp.name());
		Blueprint bp = Blueprint.fromTemplate(Designs.PREFIX + "captured", drawn);
		if (bp == null || !Designs.fits(bp)) {
			JaymosKingdoms.LOGGER.info("The plans of {} are too big to keep", camp.name());
			return;
		}
		UUID id = UUID.randomUUID();
		try {
			Path file = file(server, id);
			Files.createDirectories(file.getParent());
			NbtIo.writeCompressed(drawn, file);
		} catch (IOException e) {
			JaymosKingdoms.LOGGER.warn("The plans of {} couldn't be kept: {}", camp.name(), e.toString());
			return;
		}
		ItemStack stack = new ItemStack(PLANS);
		CompoundTag t = new CompoundTag();
		t.putString("id", id.toString());
		t.putString("base", base(server, camp, site));
		t.putString("place", camp.name());
		t.putInt("tier", tier.ordinal());
		t.putInt("w", bp.width());
		t.putInt("d", bp.depth());
		t.putInt("h", bp.height());
		t.putInt("n", bp.blocks().size());
		CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, root -> root.put(TAG, t));
		if (taker != null) {
			if (!taker.getInventory().add(stack)) {
				taker.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			taker.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.captured_plans.taken", camp.name()).withStyle(ChatFormatting.GOLD));
			level.playSound(null, taker.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.8F);
		} else {
			chief.spawnAtLocation(level, stack);
		}
		JaymosKingdoms.LOGGER.info("The plans of {} were taken ({} x {}, {} high, {} blocks)", camp.name(), bp.width(), bp.depth(), bp.height(),
			bp.blocks().size());
	}

	/** Where a camp's people built: an illager site's own bounds, else as far round its fire as a camp of its kind reaches. */
	static BoundingBox grounds(MinecraftServer server, Faction camp, IllagerSites.@Nullable Site site) {
		if (site != null) {
			return site.box();
		}
		int id = camp.id();
		boolean illagers = Camps.illager(server, id), goblins = Camps.goblin(server, id), orcs = Camps.orc(server, id), pirates = Camps.pirate(server, id);
		Camps.CampData data = Camps.data(server, id);
		BlockPos center = data != null ? data.center() : camp.seat();
		// (as far as a cleared camp rots: Camps#chiefSlain)
		int reach = pirates ? PirateCoves.RADIUS + 1 : orcs ? OrcBuildings.CLEAR_RADIUS : goblins ? GoblinBuildings.CLEAR_RADIUS
			: illagers ? IllagerBuildings.CLEAR_RADIUS : CampBuildings.CLEAR_RADIUS;
		if (orcs || goblins) {
			reach = Math.max(reach, Hordes.radius(server, id) + 2);
		}
		int high = pirates ? PirateCoves.CLEAR_HEIGHT : orcs ? OrcBuildings.CLEAR_HEIGHT : goblins ? GoblinBuildings.CLEAR_HEIGHT
			: illagers ? IllagerBuildings.CLEAR_HEIGHT : CampBuildings.CLEAR_HEIGHT;
		return new BoundingBox(center.getX() - reach, center.getY() - 1, center.getZ() - reach, center.getX() + reach, center.getY() + high,
			center.getZ() + reach);
	}

	/** The design name the plans are kept under: the site's own, else the kind of camp. */
	private static String base(MinecraftServer server, Faction camp, IllagerSites.@Nullable Site site) {
		String base = site != null ? site.path() : switch (Camps.kind(server, camp.id())) {
			case IllagerCamps.KIND -> "illager_camp";
			case GoblinVillages.KIND -> "goblin_village";
			case OrcCamps.KIND -> "orc_camp";
			case PirateCoves.KIND -> "pirate_cove";
			case IllagerSites.BANDIT -> "bandit_camp";
			case IllagerSites.SMUGGLER -> "smuggler_den";
			default -> "barbarian_camp";
		};
		base = base.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_");
		return base.length() > 28 ? base.substring(0, 28) : base;
	}

	/**
	 * Where an illager site's own templates put a block (its foundations too), in the world; null if its start can't be
	 * found (then the land in its bounds is told from what was built as a camp's is).
	 */
	static @Nullable Set<BlockPos> built(ServerLevel level, IllagerSites.Site site) {
		StructureStart start = null;
		for (Map.Entry<String, IllagerSites.Site> e : IllagerSites.all(level.getServer()).entrySet()) {
			if (e.getValue().equals(site)) {
				ChunkPos at = ChunkPos.unpack(Long.parseLong(e.getKey()));
				for (StructureStart s : level.getChunk(at.x(), at.z()).getAllStarts().values()) {
					if (s.isValid() && s.getBoundingBox().equals(site.box())) {
						start = s;
					}
				}
			}
		}
		if (start == null) {
			return null;
		}
		Set<BlockPos> out = new HashSet<>();
		for (StructurePiece piece : start.getPieces()) {
			if (!(piece instanceof PoolElementStructurePiece pool) || !(pool.getElement() instanceof SinglePoolElement single)) {
				continue;
			}
			StructureTemplate template = level.getServer().getStructureTemplateManager().get(single.getTemplateLocation()).orElse(null);
			if (template == null) {
				continue;
			}
			CompoundTag tag = template.save(new CompoundTag());
			ListTag palette = tag.getList("palette").orElseGet(() -> tag.getListOrEmpty("palettes").getListOrEmpty(0));
			ListTag blocks = tag.getListOrEmpty("blocks");
			for (int i = 0; i < blocks.size(); i++) {
				CompoundTag b = blocks.getCompoundOrEmpty(i);
				int state = b.getIntOr("state", -1);
				BlockState block = state < 0 || state >= palette.size() ? Blocks.AIR.defaultBlockState()
					: NbtUtils.readBlockState(BuiltInRegistries.BLOCK, palette.getCompoundOrEmpty(state));
				if (block.isAir() || block.is(Blocks.STRUCTURE_VOID) || block.is(Blocks.JIGSAW)) {
					continue;
				}
				ListTag pos = b.getListOrEmpty("pos");
				BlockPos in = new BlockPos(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0));
				out.add(pool.getPosition().offset(StructureTemplate.transform(in, Mirror.NONE, pool.getRotation(), BlockPos.ZERO)));
			}
		}
		return out.isEmpty() ? null : out;
	}

	/**
	 * The place in {@code box} (no bigger than any design may be, round its middle) as a structure file: only what was
	 * built there ({@code only} that, if given), every container empty. Null if nothing was.
	 */
	static @Nullable CompoundTag draw(ServerLevel level, BoundingBox box, @Nullable Set<BlockPos> only) {
		BlockPos mid = box.getCenter();
		int x0 = Math.max(box.minX(), mid.getX() - Designs.PLAN_SIDE / 2), z0 = Math.max(box.minZ(), mid.getZ() - Designs.PLAN_SIDE / 2);
		int x1 = Math.min(box.maxX(), x0 + Designs.PLAN_SIDE - 1), z1 = Math.min(box.maxZ(), z0 + Designs.PLAN_SIDE - 1);
		int y0 = Math.max(box.minY(), level.getMinY()), y1 = Math.min(Math.min(box.maxY(), y0 + Designs.PLAN_HEIGHT - 1), level.getMaxY());
		if (x1 < x0 || y1 < y0 || z1 < z0) {
			return null;
		}
		BlockPos min = new BlockPos(x0, y0, z0), max = new BlockPos(x1, y1, z1);
		Set<BlockPos> trees = only != null ? Set.of() : trees(level, min, max);
		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, min, new Vec3i(x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1), false, List.of());
		CompoundTag tag = template.save(new CompoundTag());
		ListTag blocks = tag.getListOrEmpty("blocks");
		ListTag kept = new ListTag();
		for (int i = 0; i < blocks.size(); i++) {
			CompoundTag b = blocks.getCompoundOrEmpty(i);
			ListTag pos = b.getListOrEmpty("pos");
			BlockPos at = min.offset(pos.getIntOr(0, 0), pos.getIntOr(1, 0), pos.getIntOr(2, 0));
			if (only != null && !only.contains(at) || trees.contains(at) || natural(level.getBlockState(at))) {
				continue;
			}
			// (only the blocks: chests come empty, as every design's do)
			b.remove("nbt");
			kept.add(b);
		}
		if (kept.isEmpty()) {
			return null;
		}
		tag.put("blocks", kept);
		return tag;
	}

	/** The logs of the trees growing in the box: every log joined (through logs) to leaves nobody placed. */
	private static Set<BlockPos> trees(ServerLevel level, BlockPos min, BlockPos max) {
		BoundingBox in = BoundingBox.fromCorners(min, max);
		Set<BlockPos> trees = new HashSet<>();
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
			BlockState state = level.getBlockState(pos);
			if (state.is(BlockTags.LEAVES) && !state.getValueOrElse(LeavesBlock.PERSISTENT, true)) {
				reach(level, pos, in, trees, open);
			}
		}
		while (!open.isEmpty()) {
			reach(level, open.poll(), in, trees, open);
		}
		return trees;
	}

	/** The logs touching {@code from} (corners too) not yet counted as a tree's: counted, and to be looked round. */
	private static void reach(ServerLevel level, BlockPos from, BoundingBox in, Set<BlockPos> trees, ArrayDeque<BlockPos> open) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos n = from.offset(dx, dy, dz);
					if (in.isInside(n) && !trees.contains(n) && level.getBlockState(n).is(BlockTags.LOGS)) {
						trees.add(n);
						open.add(n);
					}
				}
			}
		}
	}

	/** The land's own: ground, stone, sand, snow and ice, water, plants and ore; leaves nobody placed. */
	static boolean natural(BlockState state) {
		if (state.isAir() || state.getBlock() instanceof LiquidBlock || state.canBeReplaced()) {
			return true;
		}
		if (state.is(BlockTags.LEAVES)) {
			return !state.getValueOrElse(LeavesBlock.PERSISTENT, true);
		}
		return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER)
			|| state.is(BlockTags.SNOW) || state.is(BlockTags.ICE) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
			|| state.is(ConventionalBlockTags.ORES) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.BEDROCK)
			|| state.is(Blocks.CACTUS) || state.is(Blocks.SUGAR_CANE) || state.is(Blocks.BAMBOO) || state.is(Blocks.SWEET_BERRY_BUSH)
			|| state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM) || state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT)
			|| state.is(Blocks.CALCITE) || state.is(Blocks.DRIPSTONE_BLOCK) || state.is(Blocks.POINTED_DRIPSTONE);
	}

	private static Path file(MinecraftServer server, UUID id) {
		return Designs.folder(server).resolve(FOLDER).resolve(id + ".nbt");
	}

	private static @Nullable CompoundTag details(ItemStack stack) {
		CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		CompoundTag t = data.copyTag().getCompoundOrEmpty(TAG);
		return t.getStringOr("id", "").isEmpty() ? null : t;
	}

	private static WorldTiers.Tier tier(CompoundTag t) {
		WorldTiers.Tier[] tiers = WorldTiers.Tier.values();
		return tiers[Math.max(0, Math.min(tiers.length - 1, t.getIntOr("tier", 0)))];
	}

	// ---- reading them -----------------------------------------------------------------------------------------------

	static final class Plans extends Item {
		Plans(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer sp)) {
				return InteractionResult.SUCCESS;
			}
			ItemStack stack = player.getItemInHand(hand);
			CompoundTag t = details(stack);
			if (t == null) {
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.captured_plans.lost").withStyle(ChatFormatting.GRAY));
				return InteractionResult.FAIL;
			}
			WorldTiers.Tier tier = tier(t);
			if (!WorldTiers.open(sp, tier)) {
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.captured_plans.beyond", tier.title().copy().withColor(tier.color))
					.withStyle(ChatFormatting.GRAY));
				return InteractionResult.FAIL;
			}
			MinecraftServer server = sp.level().getServer();
			Path file;
			CompoundTag drawn;
			try {
				file = file(server, UUID.fromString(t.getStringOr("id", "")));
				drawn = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
			} catch (IOException | IllegalArgumentException e) {
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.captured_plans.lost").withStyle(ChatFormatting.GRAY));
				return InteractionResult.FAIL;
			}
			String kept = Designs.keepCaptured(sp, t.getStringOr("base", "captured"), drawn);
			Blueprint bp = kept == null ? null : Designs.blueprint(server, kept);
			if (bp == null) {
				return InteractionResult.FAIL;
			}
			try {
				Files.deleteIfExists(file);
			} catch (IOException e) {
				JaymosKingdoms.LOGGER.warn("Captured plans {} couldn't be cleared away: {}", file, e.toString());
			}
			ItemStack plan = new ItemStack(MasonTable.PLAN);
			new MasonTable.Laid(kept, Rotation.NONE, null, "", bp.width(), bp.depth(), bp.height(), bp.blocks().size(), null).write(plan);
			player.setItemInHand(hand, plan);
			MasonTable.tell(sp, Component.translatable("message.jaymoskingdoms.captured_plans.copied", t.getStringOr("place", "?"), kept, bp.width(), bp.depth(),
				bp.height(), bp.blocks().size()).withStyle(ChatFormatting.GREEN));
			MasonTable.tell(sp, (Designs.forCrews(bp) ? Component.translatable("message.jaymoskingdoms.captured_plans.crews")
				: Component.translatable("message.jaymoskingdoms.design.table_only", Designs.MAX_SIDE, Designs.MAX_HEIGHT, Designs.MAX_BLOCKS))
				.withStyle(ChatFormatting.GRAY));
			level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
			return InteractionResult.SUCCESS;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			CompoundTag t = details(stack);
			if (t == null) {
				builder.accept(Component.translatable("message.jaymoskingdoms.captured_plans.lost").withStyle(ChatFormatting.GRAY));
				return;
			}
			WorldTiers.Tier tier = tier(t);
			builder.accept(Component.translatable("item.jaymoskingdoms.captured_plans.of", t.getStringOr("place", "?")).withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("item.jaymoskingdoms.building_plan.design", t.getStringOr("base", "?"), t.getIntOr("w", 0), t.getIntOr("d", 0),
				t.getIntOr("h", 0), t.getIntOr("n", 0)).withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("item.jaymoskingdoms.captured_plans.tier", tier.title().copy().withColor(tier.color)).withStyle(ChatFormatting.DARK_GRAY));
			builder.accept(Component.translatable("item.jaymoskingdoms.captured_plans.how").withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
