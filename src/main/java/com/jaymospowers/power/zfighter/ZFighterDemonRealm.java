package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.biome.v1.NetherBiomes;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.62, Andy's NOEA picks) The demon realm: the dark masters' home, three dark biomes in the vanilla Nether ({@code demon_gloam},
 * {@code demon_ash}, {@code demon_abyss}), added to the Nether's biome source with Fabric's {@link NetherBiomes} ({@link #addBiomes})
 * and laid out so they are uncommon but findable: each sits in a corner of the Nether's temperature and humidity plane that the
 * vanilla biomes leave empty, the Abyss in the farthest and so the rarest. No portals, no rite: you walk in. They stand in for the
 * three layers of the first design, so the deeper (rarer) the biome the harder its army.
 * <ul>
 * <li><b>Demon villages</b> are built by code, one in about two of three cells of {@link #CELL} blocks whose spot lies in a demon
 * biome, the first time a player is within {@link #BUILD_NEAR}: a blackstone yard with a soul campfire, four nether-brick huts and
 * a chest. Each has a <b>dark master</b> (a trader who never leaves) who teaches the Rogue's path (a fighter whose Kingdoms skills
 * lean to Stealth; without Kingdoms, one beaten {@link ZFighterMasters#DARK_DEFEATS} times): sneak and right-click him bare-handed,
 * once a day; a dark lesson that draws blood, as the camps' dark masters', and pays more.</li>
 * <li><b>The army</b> fights anyone standing in a demon biome: soldiers, in squads, that come at a player in the dark every few
 * seconds (wither skeletons and piglin brutes, then blazes, zoglins, captains; the deeper the biome the more, the harder and the
 * bigger). They are the Nether's vanilla fighters under a demon name and tag ({@link #ARMY_TAG}), nothing new to register.</li>
 * </ul>
 */
public final class ZFighterDemonRealm {
	/** The three biomes, shallowest first: the Gloam, the Ash, the Abyss (the index is the "layer" the tuning arrays below use). */
	public static final List<ResourceKey<Biome>> BIOMES = List.of(
		ResourceKey.create(Registries.BIOME, JaymosPowers.id("demon_gloam")),
		ResourceKey.create(Registries.BIOME, JaymosPowers.id("demon_ash")),
		ResourceKey.create(Registries.BIOME, JaymosPowers.id("demon_abyss")));

	// ---- tuning ----
	/**
	 * Where each biome sits in the Nether's climate plane, by layer. The Nether only varies temperature and humidity (its other
	 * climate noises are zero), and the vanilla biomes sit at nether wastes (0, 0), soul sand valley (0, -0.5), crimson forest (0.4, 0),
	 * warped forest (0, 0.5) and basalt deltas (-0.5, 0). A point is picked wherever it is nearest, so these far corners claim a small
	 * share of the Nether each: the Gloam hot and dry, the Ash cold and dry, the Abyss hot and wet (the farthest from everything).
	 */
	static final float[] TEMPERATURE = {0.6F, -0.7F, 0.85F};
	static final float[] HUMIDITY = {-0.65F, -0.7F, 0.85F};
	/** The lowest and highest a floor is looked for (the lava sea is at 32, the roof at 127). */
	static final int FLOOR_MIN = 34;
	static final int FLOOR_MAX = 110;
	/** The headroom a village's spot needs, and how far its yard is propped up below. */
	static final int HEADROOM = 6;
	static final int PROP_DEPTH = 8;
	/** The villages: one cell is this wide, a village stands in this share of cells, and is built when a player comes this near (blocks). */
	static final int CELL = 128;
	static final float VILLAGE_CHANCE = 0.66F;
	static final double BUILD_NEAR = 56.0;
	static final int VILLAGE_RADIUS = 11;
	/** A village's master is put back when a player comes this near (blocks). */
	static final double NPC_NEAR = 48.0;
	/** The dark master's lesson: bonus TP over a good master's, the blood it draws and the hardship it adds are the camp masters' own. */
	static final int LESSON_BONUS_TP = 100;
	static final long DAY = 24000L;
	/** The army: a squad comes every {@code ARMY_EVERY} ticks to a player in a demon biome, if fewer than {@code ARMY_CAP[layer]} are near; squads of up to {@code ARMY_SQUAD[layer]}. */
	static final int ARMY_EVERY = 160;
	static final int[] ARMY_CAP = {6, 9, 12};
	static final int[] ARMY_SQUAD = {2, 3, 4};
	static final double ARMY_NEAR = 64.0;
	static final double ARMY_MIN = 16.0;
	static final double ARMY_MAX = 30.0;
	/** The soldiers' health multiple and Strength level, by layer (0 is none). */
	static final double[] HEALTH_MULT = {1.0, 1.5, 2.2};
	static final int[] STRENGTH = {0, 0, 1};
	/** The share of a squad that are captains (bigger, harder), by layer. */
	static final float[] CAPTAIN_SHARE = {0.1F, 0.2F, 0.35F};
	static final double CAPTAIN_SCALE = 1.35;
	static final String ARMY_TAG = "jaymospowers_demon";
	static final String MASTER_TAG = "jaymospowers_demon_master";

	/** The villages built in the Nether (a centre with y 0 is a spot that had no floor, never retried). Saved. */
	public static final AttachmentType<List<BlockPos>> VILLAGES = AttachmentRegistry.create(JaymosPowers.id("zfighter_demon_villages"),
		builder -> builder.persistent(BlockPos.CODEC.listOf()));
	/** The day of his last dark lesson here. Saved. */
	public static final AttachmentType<Long> LESSON = AttachmentRegistry.create(JaymosPowers.id("zfighter_demon_lesson"),
		builder -> builder.persistent(Codec.LONG));

	/** The layer each player was last told he stood in (so the name shows once on entering). */
	private static final Map<UUID, Integer> TOLD = new HashMap<>();

	private ZFighterDemonRealm() {
	}

	/** The layer (0 Gloam, 1 Ash, 2 Abyss) of the demon biome at {@code pos}, or -1 anywhere else. */
	public static int layerAt(Level level, BlockPos pos) {
		Holder<Biome> biome = level.getBiome(pos);
		for (int i = 0; i < BIOMES.size(); i++) {
			if (biome.is(BIOMES.get(i))) {
				return i;
			}
		}
		return -1;
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterDemonRealm::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> TOLD.clear());
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !entity.entityTags().contains(MASTER_TAG)) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide() && player instanceof ServerPlayer server) {
				teach(server, entity);
			}
			return InteractionResult.SUCCESS;
		});
	}

	private static boolean biomesAdded;

	/**
	 * Puts the three biomes into the Nether's biome source (the vanilla one, by Fabric's biome API: the data files for the
	 * biomes are in the jar). (0.17.63) Called from the server and client entrypoints, which run after every mod's main
	 * entrypoint, never from {@link #init}: once they're added, the Nether preset asks for them in every registry bootstrap,
	 * and TaCZ builds {@code VanillaRegistries.createWorldLookup()} in its main entrypoint, where only vanilla's biomes
	 * exist ("No value registered for key jaymospowers:demon_abyss", the server and client crashed on start). The world's
	 * own registries are loaded later, from the data files, so the biomes still generate.
	 */
	public static void addBiomes() {
		if (biomesAdded) {
			return;
		}
		biomesAdded = true;
		for (int layer = 0; layer < BIOMES.size(); layer++) {
			NetherBiomes.addNetherBiome(BIOMES.get(layer), Climate.parameters(TEMPERATURE[layer], HUMIDITY[layer], 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
		}
	}

	private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
		level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
	}

	/**
	 * The lowest floor above the lava sea at {@code (x, z)} with {@link #HEADROOM} of air over it, or -1 (the chunk is made if it is
	 * not there yet). Returns the y to stand at.
	 */
	private static int floor(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = FLOOR_MIN; y <= FLOOR_MAX; y++) {
			pos.set(x, y - 1, z);
			BlockState ground = level.getBlockState(pos);
			if (ground.isAir() || !ground.getFluidState().isEmpty() || ground.is(Blocks.BEDROCK)) {
				continue;
			}
			boolean clear = true;
			for (int up = 0; up < HEADROOM && clear; up++) {
				pos.set(x, y + up, z);
				clear = level.getBlockState(pos).isAir();
			}
			if (clear) {
				return y;
			}
		}
		return -1;
	}

	// ---------------------------------------------------------------- every tick

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		ServerLevel level = server.getLevel(Level.NETHER);
		if (level == null || level.players().isEmpty()) {
			return;
		}
		if (now % 40 == 0) {
			villages(level);
			for (ServerPlayer player : level.players()) {
				int layer = layerAt(level, player.blockPosition());
				Integer told = TOLD.get(player.getUUID());
				if (layer < 0) {
					TOLD.remove(player.getUUID());
				} else if (told == null || told != layer) {
					TOLD.put(player.getUUID(), layer);
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_demon_layer_" + (layer + 1)).withStyle(ChatFormatting.DARK_RED));
				}
			}
		}
		if (now % ARMY_EVERY == 0) {
			for (ServerPlayer player : level.players()) {
				int layer = layerAt(level, player.blockPosition());
				if (layer >= 0 && player.isAlive() && !player.isCreative() && !player.isSpectator()) {
					army(level, layer, player);
				}
			}
		}
	}

	// ---------------------------------------------------------------- the villages

	/** The village a cell holds: its x and z, or empty (about a third of cells hold none). Fixed by the cell alone. */
	static Optional<int[]> villageAt(int cellX, int cellZ) {
		RandomSource random = RandomSource.create(cellX * 341873128712L + cellZ * 132897987541L + 0x5DEECE66DL);
		if (random.nextFloat() >= VILLAGE_CHANCE) {
			return Optional.empty();
		}
		int margin = VILLAGE_RADIUS + 8;
		return Optional.of(new int[] {cellX * CELL + margin + random.nextInt(CELL - 2 * margin), cellZ * CELL + margin + random.nextInt(CELL - 2 * margin)});
	}

	/**
	 * Whether a village could stand at (x, z): it is in a demon biome, and so are the four points on its edge (so it is not on a
	 * border). Returns the layer of its middle, or -1.
	 */
	private static int villageLayer(ServerLevel level, int x, int z) {
		int layer = layerAt(level, new BlockPos(x, 64, z));
		if (layer < 0) {
			return -1;
		}
		for (int[] edge : new int[][] {{VILLAGE_RADIUS, 0}, {-VILLAGE_RADIUS, 0}, {0, VILLAGE_RADIUS}, {0, -VILLAGE_RADIUS}}) {
			if (layerAt(level, new BlockPos(x + edge[0], 64, z + edge[1])) < 0) {
				return -1;
			}
		}
		return layer;
	}

	/** Builds the villages in the cells round each player who comes near, and puts back their masters. */
	private static void villages(ServerLevel level) {
		List<BlockPos> known = level.getAttachedOrElse(VILLAGES, List.of());
		List<BlockPos> villages = known;
		for (ServerPlayer player : level.players()) {
			int cx = Math.floorDiv(Mth.floor(player.getX()), CELL);
			int cz = Math.floorDiv(Mth.floor(player.getZ()), CELL);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					Optional<int[]> spot = villageAt(cx + dx, cz + dz);
					if (spot.isEmpty()) {
						continue;
					}
					int x = spot.get()[0], z = spot.get()[1];
					if (Math.hypot(player.getX() - x, player.getZ() - z) > BUILD_NEAR || has(villages, x, z)) {
						continue;
					}
					int layer = villageLayer(level, x, z);
					if (layer < 0) {
						continue;
					}
					int y = floor(level, x, z);
					if (villages == known) {
						villages = new ArrayList<>(known);
					}
					if (y < 0) {
						villages.add(new BlockPos(x, 0, z));
						continue;
					}
					build(level, x, y, z, layer);
					villages.add(new BlockPos(x, y, z));
				}
			}
		}
		if (villages != known) {
			level.setAttached(VILLAGES, villages);
		}
		for (BlockPos village : villages) {
			if (village.getY() > 0) {
				ensureMaster(level, village);
			}
		}
	}

	private static boolean has(List<BlockPos> villages, int x, int z) {
		for (BlockPos village : villages) {
			if (village.getX() == x && village.getZ() == z) {
				return true;
			}
		}
		return false;
	}

	private static Vec3 masterAt(BlockPos village) {
		return new Vec3(village.getX() + 2.5, village.getY(), village.getZ() + 0.5);
	}

	private static void ensureMaster(ServerLevel level, BlockPos village) {
		Vec3 at = masterAt(village);
		boolean near = false;
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(at) <= NPC_NEAR * NPC_NEAR) {
				near = true;
				break;
			}
		}
		if (!near || !level.hasChunkAt(village)) {
			return;
		}
		AABB around = new AABB(village).inflate(VILLAGE_RADIUS, 5.0, VILLAGE_RADIUS);
		int layer = Math.max(0, layerAt(level, village));
		if (level.getEntitiesOfClass(LivingEntity.class, around, e -> e.entityTags().contains(MASTER_TAG)).isEmpty()) {
			ZFighterSites.spawnNpc(level, at, MASTER_TAG, "entity.jaymospowers.demon_master_" + (layer + 1), new MerchantOffers());
		}
	}

	/** A yard of blackstone, a soul campfire, four nether-brick huts (doors toward the fire) and a chest in one. */
	private static void build(ServerLevel level, int x, int y, int z, int layer) {
		RandomSource random = level.getRandom();
		for (int dx = -VILLAGE_RADIUS; dx <= VILLAGE_RADIUS; dx++) {
			for (int dz = -VILLAGE_RADIUS; dz <= VILLAGE_RADIUS; dz++) {
				if (dx * dx + dz * dz > VILLAGE_RADIUS * VILLAGE_RADIUS) {
					continue;
				}
				for (int down = 2; down <= PROP_DEPTH; down++) {
					BlockPos under = new BlockPos(x + dx, y - down, z + dz);
					BlockState below = level.getBlockState(under);
					if (below.isAir() || !below.getFluidState().isEmpty()) {
						level.setBlock(under, Blocks.BLACKSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
					} else {
						break;
					}
				}
				set(level, x + dx, y - 1, z + dz, random.nextInt(5) == 0 ? Blocks.GILDED_BLACKSTONE.defaultBlockState()
					: random.nextBoolean() ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
				for (int up = 0; up < 7; up++) {
					set(level, x + dx, y + up, z + dz, Blocks.AIR.defaultBlockState());
				}
			}
		}
		set(level, x, y, z, Blocks.SOUL_CAMPFIRE.defaultBlockState());
		boolean chest = true;
		for (int[] corner : new int[][] {{-7, -7}, {7, -7}, {-7, 7}, {7, 7}}) {
			hut(level, x + corner[0], y, z + corner[1], corner[0] > 0 ? Direction.WEST : Direction.EAST);
			if (chest) {
				chest = false;
				loot(level, new BlockPos(x + corner[0], y, z + corner[1]), layer, random);
			}
		}
		for (int[] post : new int[][] {{-3, 0}, {3, 0}, {0, -3}, {0, 3}}) {
			set(level, x + post[0], y, z + post[1], Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
			set(level, x + post[0], y + 1, z + post[1], Blocks.SOUL_LANTERN.defaultBlockState());
		}
	}

	/** A 5 by 5 hut of nether brick, 3 high, a slab roof, a doorway on the side toward the fire (one of two sides given). */
	private static void hut(ServerLevel level, int cx, int y, int cz, Direction doorX) {
		BlockState brick = Blocks.NETHER_BRICKS.defaultBlockState();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
				for (int up = 0; up < 3; up++) {
					boolean door = dz == 0 && dx == doorX.getStepX() * 2 && up < 2;
					set(level, cx + dx, y + up, cz + dz, edge && !door ? brick : Blocks.AIR.defaultBlockState());
				}
				set(level, cx + dx, y + 3, cz + dz, Blocks.NETHER_BRICK_SLAB.defaultBlockState());
			}
		}
		set(level, cx, y, cz, Blocks.SOUL_LANTERN.defaultBlockState());
	}

	private static void loot(ServerLevel level, BlockPos hut, int layer, RandomSource random) {
		BlockPos at = hut.offset(1, 0, 1);
		set(level, at.getX(), at.getY(), at.getZ(), Blocks.CHEST.defaultBlockState());
		if (level.getBlockEntity(at) instanceof Container chest) {
			chest.setItem(11, new ItemStack(Items.GOLD_INGOT, 4 + random.nextInt(6) + layer * 4));
			chest.setItem(13, new ItemStack(Items.NETHERITE_SCRAP, 1 + layer));
			chest.setItem(15, new ItemStack(Items.WITHER_SKELETON_SKULL, 1));
		}
	}

	// ---------------------------------------------------------------- the dark master

	private static void teach(ServerPlayer player, net.minecraft.world.entity.Entity master) {
		String voice = "message.jaymospowers.zfighter_demon_master";
		if (!Powers.has(player, ZFighterPower.INSTANCE)) {
			player.sendSystemMessage(Component.translatable(voice + "_no_spark", master.getDisplayName()).withStyle(ChatFormatting.DARK_PURPLE));
			return;
		}
		boolean rogue = ZFighterKingdoms.rogue(player) || !ZFighterKingdoms.present() && ZFighterPower.zenkai(player) >= ZFighterMasters.DARK_DEFEATS;
		if (!rogue) {
			player.sendSystemMessage(Component.translatable(voice + "_refuses", master.getDisplayName()).withStyle(ChatFormatting.DARK_PURPLE));
			return;
		}
		if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) {
			player.sendSystemMessage(Component.translatable(voice + "_hint", master.getDisplayName()).withStyle(ChatFormatting.DARK_PURPLE));
			return;
		}
		long day = player.level().getGameTime() / DAY;
		Long last = player.getAttached(LESSON);
		if (last != null && last >= day && !player.isCreative()) {
			player.sendSystemMessage(Component.translatable(voice + "_tomorrow", master.getDisplayName()).withStyle(ChatFormatting.DARK_PURPLE));
			return;
		}
		player.setAttached(LESSON, day);
		Component lesson = ZFighterMasters.lesson(player, true);
		ZFighterTraining.grant(player, ZFighterMasters.LESSON_TP + LESSON_BONUS_TP);
		player.sendSystemMessage(Component.translatable(voice + "_teaches", master.getDisplayName(), lesson, ZFighterMasters.LESSON_TP + LESSON_BONUS_TP)
			.withStyle(ChatFormatting.DARK_PURPLE));
		ServerLevel level = player.level();
		player.hurtServer(level, level.damageSources().magic(), ZFighterMasters.DARK_PRICE);
		ZFighterPower.addHardship(player, ZFighterMasters.DARK_HARDSHIP);
		ZFighterCorruption.darkLesson(player);
		level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.6F, 1.4F);
	}

	// ---------------------------------------------------------------- the army

	/** A squad of the realm's soldiers comes at {@code player} out of the dark, unless enough are near already. */
	private static void army(ServerLevel level, int layer, ServerPlayer player) {
		AABB near = player.getBoundingBox().inflate(ARMY_NEAR);
		if (level.getEntitiesOfClass(Mob.class, near, e -> e.entityTags().contains(ARMY_TAG)).size() >= ARMY_CAP[layer]) {
			return;
		}
		RandomSource random = level.getRandom();
		int count = 1 + random.nextInt(ARMY_SQUAD[layer]);
		double angle = random.nextDouble() * Mth.TWO_PI;
		double distance = ARMY_MIN + random.nextDouble() * (ARMY_MAX - ARMY_MIN);
		for (int i = 0; i < count; i++) {
			int x = Mth.floor(player.getX() + Math.cos(angle) * distance + random.nextInt(5) - 2);
			int z = Mth.floor(player.getZ() + Math.sin(angle) * distance + random.nextInt(5) - 2);
			if (!level.hasChunkAt(new BlockPos(x, Mth.floor(player.getY()), z))) {
				continue;
			}
			int y = groundNear(level, x, Mth.floor(player.getY()), z);
			if (y < 0) {
				continue;
			}
			soldier(level, layer, new Vec3(x + 0.5, y, z + 0.5), player, random.nextFloat() < CAPTAIN_SHARE[layer]);
		}
	}

	/** The nearest standing room with two air blocks over it within 12 of {@code y}, or -1. */
	private static int groundNear(ServerLevel level, int x, int y, int z) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int d = 0; d <= 12; d++) {
			for (int sign = -1; sign <= 1; sign += 2) {
				int at = y + d * sign;
				pos.set(x, at - 1, z);
				BlockState ground = level.getBlockState(pos);
				pos.set(x, at, z);
				boolean air = level.getBlockState(pos).isAir();
				pos.set(x, at + 1, z);
				if (!ground.isAir() && ground.getFluidState().isEmpty() && air && level.getBlockState(pos).isAir()) {
					return at;
				}
			}
		}
		return -1;
	}

	private static void soldier(ServerLevel level, int layer, Vec3 at, ServerPlayer target, boolean captain) {
		RandomSource random = level.getRandom();
		EntityType<? extends Mob> type;
		float roll = random.nextFloat();
		if (layer >= 1 && roll < 0.2F) {
			type = EntityTypes.BLAZE;
		} else if (layer >= 1 && roll < 0.4F) {
			type = EntityTypes.ZOGLIN;
		} else if (roll < 0.7F) {
			type = EntityTypes.WITHER_SKELETON;
		} else {
			type = EntityTypes.PIGLIN_BRUTE;
		}
		Mob mob = type.create(level, EntitySpawnReason.EVENT);
		if (mob == null) {
			return;
		}
		mob.snapTo(at.x, at.y, at.z, random.nextFloat() * 360.0F, 0.0F);
		double health = mob.getMaxHealth() * HEALTH_MULT[layer] * (captain ? 1.6 : 1.0);
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		mob.setHealth((float)health);
		if (captain) {
			mob.getAttribute(Attributes.SCALE).setBaseValue(CAPTAIN_SCALE);
		}
		if (type == EntityTypes.WITHER_SKELETON) {
			mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(layer >= 2 ? Items.NETHERITE_SWORD : layer == 1 ? Items.IRON_SWORD : Items.STONE_SWORD));
			mob.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		} else if (type == EntityTypes.PIGLIN_BRUTE) {
			mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_AXE));
			mob.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		}
		if (STRENGTH[layer] > 0) {
			mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, MobEffectInstance.INFINITE_DURATION, STRENGTH[layer] - 1, false, false));
		}
		mob.setCustomName(Component.translatable(captain ? "entity.jaymospowers.demon_captain" : "entity.jaymospowers.demon_soldier"));
		mob.setCustomNameVisible(captain);
		mob.addTag(ARMY_TAG);
		mob.setTarget(target);
		level.addFreshEntity(mob);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 1.0, at.z, 12, 0.3, 0.6, 0.3, 0.03);
	}
}
