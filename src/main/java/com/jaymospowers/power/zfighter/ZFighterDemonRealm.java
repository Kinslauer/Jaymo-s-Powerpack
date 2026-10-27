package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
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
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.62, Andy's NOEA picks) The demon realm: the dark masters' home, three dark layers one under the other, each its own
 * dimension ({@code demon_gloam}, {@code demon_ash}, {@code demon_abyss}: a nether-shaped cavern of rock over a lava sea, one
 * dim biome each; three dimensions were simpler than three height bands in one).
 * <ul>
 * <li><b>The rite</b> is how you go down from any other world: stand a Crying Obsidian block in the middle of four lit soul
 * campfires (one on each side), and right-click it bare-handed with a Wither Skeleton Skull in your off hand ({@link #riteReady});
 * the skull is spent and the gate takes you to the first layer. It remembers where you came from.</li>
 * <li><b>The altars</b> inside: each layer's arrival platform has an up altar (Crying Obsidian on Gilded Blackstone) that lifts
 * you to the layer above, or home from the first, and every layer but the last has a down altar (Crying Obsidian on Ancient
 * Debris) some {@link #DOWN_X} blocks east that drops you to the next. Right-click bare-handed.</li>
 * <li><b>Demon villages</b> are built by code, one in about two of three cells of {@link #CELL} blocks, the first time a player is
 * within {@link #BUILD_NEAR}: a blackstone yard with a soul campfire, four nether-brick huts and a chest. Each has a
 * <b>dark master</b> (a trader who never leaves) who teaches the Rogue's path (a fighter whose Kingdoms skills lean to
 * Stealth; without Kingdoms, one beaten {@link ZFighterMasters#DARK_DEFEATS} times): sneak and right-click him bare-handed, once a
 * day; a dark lesson that draws blood, as the camps' dark masters', and pays more.</li>
 * <li><b>The army</b> fights anyone in a layer: soldiers, in squads, that come at a player in the dark every few seconds
 * (wither skeletons and piglin brutes, then blazes, zoglins, captains; the deeper the more, the harder and the bigger). They
 * are the realm's vanilla fighters under a demon name and tag ({@link #ARMY_TAG}), nothing new to register.</li>
 * </ul>
 */
public final class ZFighterDemonRealm {
	public static final ResourceKey<Level>[] LAYERS = layers();

	@SuppressWarnings("unchecked")
	private static ResourceKey<Level>[] layers() {
		return new ResourceKey[] {ResourceKey.create(Registries.DIMENSION, JaymosPowers.id("demon_gloam")),
			ResourceKey.create(Registries.DIMENSION, JaymosPowers.id("demon_ash")),
			ResourceKey.create(Registries.DIMENSION, JaymosPowers.id("demon_abyss"))};
	}

	// ---- tuning ----
	/** The rite: the campfires stand this far from the altar block (the skull is spent). */
	static final int RITE_REACH = 1;
	/** Where the down altar stands from the arrival platform (blocks east). */
	static final int DOWN_X = 40;
	/** The platform round an altar (radius), and the headroom cleared above it. */
	static final int PLATFORM_RADIUS = 4;
	static final int HEADROOM = 6;
	/** The lowest and highest a floor is looked for (the lava sea is at 32, the roof at 127). */
	static final int FLOOR_MIN = 34;
	static final int FLOOR_MAX = 110;
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
	/** The army: a squad comes every {@code ARMY_EVERY} ticks to a player in a layer, if fewer than {@code ARMY_CAP[layer]} are near; squads of up to {@code ARMY_SQUAD[layer]}. */
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

	/** Where an arrival platform's altars are, on a layer's level. Saved. */
	public record Gates(BlockPos up, Optional<BlockPos> down) {
		public static final Codec<Gates> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("up").forGetter(Gates::up),
			BlockPos.CODEC.optionalFieldOf("down").forGetter(Gates::down)
		).apply(i, Gates::new));
	}

	public static final AttachmentType<Gates> GATES = AttachmentRegistry.create(JaymosPowers.id("zfighter_demon_gates"),
		builder -> builder.persistent(Gates.CODEC));
	/** The villages built on a layer (a centre with y 0 is a cell that had no floor, never retried). Saved. */
	public static final AttachmentType<List<BlockPos>> VILLAGES = AttachmentRegistry.create(JaymosPowers.id("zfighter_demon_villages"),
		builder -> builder.persistent(BlockPos.CODEC.listOf()));
	/** Where a player went down from, to be lifted back there. Saved. */
	public static final AttachmentType<ZFighterAfterlife.Home> ORIGIN = AttachmentRegistry.create(JaymosPowers.id("zfighter_demon_origin"),
		builder -> builder.persistent(ZFighterAfterlife.Home.CODEC));
	/** The day of his last dark lesson here. Saved. */
	public static final AttachmentType<Long> LESSON = AttachmentRegistry.create(JaymosPowers.id("zfighter_demon_lesson"),
		builder -> builder.persistent(Codec.LONG));

	private ZFighterDemonRealm() {
	}

	public static int layerOf(ResourceKey<Level> dimension) {
		for (int i = 0; i < LAYERS.length; i++) {
			if (LAYERS[i].equals(dimension)) {
				return i;
			}
		}
		return -1;
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterDemonRealm::tick);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty() || !level.getBlockState(hit.getBlockPos()).is(Blocks.CRYING_OBSIDIAN)) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			int layer = layerOf(level.dimension());
			Block under = level.getBlockState(pos.below()).getBlock();
			boolean altar = layer >= 0 && (under == Blocks.GILDED_BLACKSTONE || under == Blocks.ANCIENT_DEBRIS);
			boolean rite = layer < 0 && level.dimension() != ZFighterAfterlife.OTHER_WORLD && riteReady(level, pos, player);
			if (!altar && !rite) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide() && player instanceof ServerPlayer server) {
				if (altar) {
					altar(server, layer, under == Blocks.GILDED_BLACKSTONE);
				} else {
					rite(server);
				}
			}
			return InteractionResult.SUCCESS;
		});
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

	// ---------------------------------------------------------------- the rite and the altars

	/** A Crying Obsidian block with a lit soul campfire on each of its four sides, and a Wither Skeleton Skull in the off hand. */
	static boolean riteReady(Level level, BlockPos pos, net.minecraft.world.entity.player.Player player) {
		if (!player.getOffhandItem().is(Items.WITHER_SKELETON_SKULL)) {
			return false;
		}
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockState state = level.getBlockState(pos.relative(side, RITE_REACH));
			if (!state.is(Blocks.SOUL_CAMPFIRE) || !state.getValue(CampfireBlock.LIT)) {
				return false;
			}
		}
		return true;
	}

	private static void rite(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		ServerLevel gloam = server.getLevel(LAYERS[0]);
		if (gloam == null) {
			return;
		}
		if (!player.isCreative()) {
			player.getOffhandItem().shrink(1);
		}
		player.setAttached(ORIGIN, new ZFighterAfterlife.Home(player.level().dimension(), player.blockPosition()));
		ServerLevel from = player.level();
		from.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.5, 0.9, 0.5, 0.06);
		from.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.6F, 1.6F);
		descend(player, gloam, 0, true);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_demon_rite").withStyle(ChatFormatting.DARK_RED));
	}

	/** An altar: the up altar lifts him a layer (home from the first), the down altar drops him one. */
	private static void altar(ServerPlayer player, int layer, boolean up) {
		MinecraftServer server = player.level().getServer();
		if (up && layer == 0) {
			home(player);
			return;
		}
		int to = layer + (up ? -1 : 1);
		if (to < 0 || to >= LAYERS.length) {
			return;
		}
		ServerLevel level = server.getLevel(LAYERS[to]);
		if (level != null) {
			descend(player, level, to, !up);
		}
	}

	private static void home(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		ZFighterAfterlife.Home origin = player.getAttached(ORIGIN);
		ServerLevel to = origin == null ? null : server.getLevel(origin.dimension());
		BlockPos at;
		if (to == null || layerOf(to.dimension()) >= 0) {
			to = server.overworld();
			at = to.getRespawnData().pos();
		} else {
			at = origin.pos();
		}
		to.getChunk(at);
		player.removeAttached(ORIGIN);
		player.teleportTo(to, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), player.getYRot(), 0.0F, true);
		to.playSound(null, at, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.2F);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_demon_up").withStyle(ChatFormatting.GRAY));
	}

	/** To a layer's up altar's platform (arriving from above) or to its down altar's (arriving from below). */
	private static void descend(ServerPlayer player, ServerLevel level, int layer, boolean fromAbove) {
		Gates gates = gates(level, layer);
		BlockPos stand = (fromAbove ? gates.up() : gates.down().orElse(gates.up())).above().relative(Direction.SOUTH, 2);
		player.teleportTo(level, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, Set.of(), player.getYRot(), 0.0F, true);
		level.playSound(null, stand, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.6F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_demon_layer_" + (layer + 1)).withStyle(ChatFormatting.DARK_RED));
	}

	/** A layer's platform and altars, built the first time they are needed. */
	private static Gates gates(ServerLevel level, int layer) {
		Gates gates = level.getAttached(GATES);
		if (gates != null && level.getBlockState(gates.up()).is(Blocks.CRYING_OBSIDIAN)) {
			return gates;
		}
		BlockPos up = platform(level, 0, 0, Blocks.GILDED_BLACKSTONE);
		Optional<BlockPos> down = Optional.empty();
		if (layer < LAYERS.length - 1) {
			down = Optional.of(platform(level, DOWN_X, 0, Blocks.ANCIENT_DEBRIS));
		}
		gates = new Gates(up, down);
		level.setAttached(GATES, gates);
		return gates;
	}

	/** A blackstone platform with a Crying Obsidian altar on {@code base} in its middle, soul lanterns round it, and air above. Returns the altar. */
	private static BlockPos platform(ServerLevel level, int x, int z, Block base) {
		int y = floor(level, x, z);
		if (y < 0) {
			y = 64;
		}
		for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++) {
			for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++) {
				if (dx * dx + dz * dz > PLATFORM_RADIUS * PLATFORM_RADIUS + 1) {
					continue;
				}
				set(level, x + dx, y - 1, z + dz, (dx + dz) % 2 == 0 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState());
				for (int up = 0; up < HEADROOM; up++) {
					set(level, x + dx, y + up, z + dz, Blocks.AIR.defaultBlockState());
				}
			}
		}
		set(level, x, y - 1, z, base.defaultBlockState());
		set(level, x, y, z, Blocks.CRYING_OBSIDIAN.defaultBlockState());
		for (int[] corner : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
			set(level, x + corner[0], y, z + corner[1], Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
			set(level, x + corner[0], y + 1, z + corner[1], Blocks.SOUL_LANTERN.defaultBlockState());
		}
		return new BlockPos(x, y, z);
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
		if (now % 40 == 0) {
			for (int layer = 0; layer < LAYERS.length; layer++) {
				ServerLevel level = server.getLevel(LAYERS[layer]);
				if (level != null && !level.players().isEmpty()) {
					villages(level, layer);
				}
			}
		}
		if (now % ARMY_EVERY == 0) {
			for (int layer = 0; layer < LAYERS.length; layer++) {
				ServerLevel level = server.getLevel(LAYERS[layer]);
				if (level != null) {
					for (ServerPlayer player : level.players()) {
						if (player.isAlive() && !player.isCreative() && !player.isSpectator()) {
							army(level, layer, player);
						}
					}
				}
			}
		}
	}

	// ---------------------------------------------------------------- the villages

	/** The village a cell holds: its x and z, or empty (about a third of cells hold none). Fixed by the cell alone. */
	static Optional<int[]> villageAt(int layer, int cellX, int cellZ) {
		RandomSource random = RandomSource.create(cellX * 341873128712L + cellZ * 132897987541L + layer * 7919L + 0x5DEECE66DL);
		if (random.nextFloat() >= VILLAGE_CHANCE) {
			return Optional.empty();
		}
		int margin = VILLAGE_RADIUS + 8;
		return Optional.of(new int[] {cellX * CELL + margin + random.nextInt(CELL - 2 * margin), cellZ * CELL + margin + random.nextInt(CELL - 2 * margin)});
	}

	/** Builds the villages in the cells round each player who comes near, and puts back their masters. */
	private static void villages(ServerLevel level, int layer) {
		List<BlockPos> known = level.getAttachedOrElse(VILLAGES, List.of());
		List<BlockPos> villages = known;
		for (ServerPlayer player : level.players()) {
			int cx = Math.floorDiv(Mth.floor(player.getX()), CELL);
			int cz = Math.floorDiv(Mth.floor(player.getZ()), CELL);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					Optional<int[]> spot = villageAt(layer, cx + dx, cz + dz);
					if (spot.isEmpty()) {
						continue;
					}
					int x = spot.get()[0], z = spot.get()[1];
					if (Math.hypot(player.getX() - x, player.getZ() - z) > BUILD_NEAR || has(villages, x, z)) {
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
				ensureMaster(level, village, layer);
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

	private static void ensureMaster(ServerLevel level, BlockPos village, int layer) {
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
