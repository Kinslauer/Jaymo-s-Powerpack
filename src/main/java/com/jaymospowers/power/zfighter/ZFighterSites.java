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
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The world of the Z Fighter (all built by hand the first time someone comes near, no structure files):
 * <ul>
 * <li><b>Seven shrines</b>, each with a wish orb in a chest buried under its middle stone ({@link ZFighterWishes}).</li>
 * <li><b>The Tower</b>: a white spire some 500-800 blocks out, climbed by the ladder inside. At the top lives the Hermit
 * (a trader who never leaves: Hermit Beans and their seeds).</li>
 * <li><b>The Lookout</b>: a floating temple above the Tower, reached up the pole from the Tower's roof (or by flight).
 * Its Keeper blesses visitors once a day, and its two doors lead to the <b>Time Chamber</b> (a white void where training
 * pays double and hunger bites twice as hard) and the <b>Verdant World</b> (green skies, Verdant villages).</li>
 * </ul>
 * Where everything is lives on the overworld ({@link #DATA}).
 */
public final class ZFighterSites {
	// ---- the shrines ----
	static final int ORBS = 7;
	static final int SHRINE_MIN = 300;
	static final int SHRINE_MAX = 2000;
	/** A shrine is built when a player comes this near (blocks, flat). */
	static final int BUILD_NEAR = 48;
	// ---- the Tower and the Lookout ----
	static final int TOWER_MIN = 500;
	static final int TOWER_MAX = 800;
	static final int TOWER_NEAR = 96;
	static final int TOWER_RADIUS = 4;
	static final int TOWER_TOP = 196;
	static final int ROOM_RADIUS = 6;
	static final int ROOM_HEIGHT = 5;
	static final int LOOKOUT_Y = 236;
	static final int LOOKOUT_RADIUS = 13;
	// ---- the Time Chamber ----
	public static final ResourceKey<Level> TIME_CHAMBER = ResourceKey.create(Registries.DIMENSION, JaymosPowers.id("time_chamber"));
	public static final ResourceKey<Level> VERDANT_WORLD = ResourceKey.create(Registries.DIMENSION, JaymosPowers.id("verdant_world"));
	/** Training pays this many times over in the Time Chamber, and hunger bites this much harder. */
	public static final float CHAMBER_TRAINING = 2.0F;
	static final float CHAMBER_EXHAUSTION = 0.02F;
	/** The chamber's floor (the flat world's top layer is at y 3). */
	static final int CHAMBER_FLOOR = 4;
	// ---- the Keeper ----
	static final int BLESSING_TICKS = 6000;
	static final int BLESSING_TP = 75;
	static final long DAY = 24000L;

	static final String KEEPER_TAG = "jaymospowers_keeper";
	static final String HERMIT_TAG = "jaymospowers_hermit";

	/** Where everything is (on the overworld): the shrines, which are built, when the orbs may be found again, the Tower. */
	public record Data(List<BlockPos> shrines, int built, long restUntil, Optional<BlockPos> tower, boolean towerBuilt) {
		public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.listOf().fieldOf("shrines").forGetter(Data::shrines),
			Codec.INT.fieldOf("built").forGetter(Data::built),
			Codec.LONG.fieldOf("rest_until").forGetter(Data::restUntil),
			BlockPos.CODEC.optionalFieldOf("tower").forGetter(Data::tower),
			Codec.BOOL.fieldOf("tower_built").forGetter(Data::towerBuilt)
		).apply(i, Data::new));
		static final Data EMPTY = new Data(List.of(), 0, 0L, Optional.empty(), false);

		boolean isBuilt(int orb) {
			return (built & 1 << orb) != 0;
		}
	}

	public static final AttachmentType<Data> DATA = AttachmentRegistry.create(JaymosPowers.id("zfighter_sites"),
		builder -> builder.persistent(Data.CODEC));
	/** When each player was last blessed by the Keeper (game time). Saved. */
	public static final AttachmentType<Long> BLESSED = AttachmentRegistry.create(JaymosPowers.id("zfighter_blessed"),
		builder -> builder.persistent(Codec.LONG));

	public static Block CHAMBER_DOOR;
	public static Block VERDANT_GATE;

	private ZFighterSites() {
	}

	static void init() {
		CHAMBER_DOOR = door("chamber_door", MapColor.SNOW);
		VERDANT_GATE = door("verdant_gate", MapColor.EMERALD);
		ServerTickEvents.END_SERVER_TICK.register(ZFighterSites::tick);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || !entity.entityTags().contains(KEEPER_TAG)) {
				return InteractionResult.PASS;
			}
			bless((ServerPlayer)player);
			return InteractionResult.SUCCESS;
		});
	}

	private static Block door(String name, MapColor color) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, new Door(BlockBehaviour.Properties.of().mapColor(color).strength(-1.0F, 3600000.0F)
			.noLootTable().lightLevel(state -> 12).sound(SoundType.AMETHYST).pushReaction(PushReaction.IMMOVEABLE).setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()
			.rarity(Rarity.EPIC)));
		return block;
	}

	static Data data(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		Data data = overworld.getAttached(DATA);
		if (data == null || data.shrines().size() != ORBS || data.tower().isEmpty()) {
			RandomSource random = overworld.getRandom();
			List<BlockPos> shrines = data != null && data.shrines().size() == ORBS ? data.shrines() : scatter(random);
			BlockPos tower = data != null && data.tower().isPresent() ? data.tower().get() : ring(random, random.nextFloat() * Mth.TWO_PI,
				TOWER_MIN, TOWER_MAX);
			data = new Data(shrines, data == null ? 0 : data.built(), data == null ? 0L : data.restUntil(), Optional.of(tower),
				data != null && data.towerBuilt());
			overworld.setAttached(DATA, data);
		}
		return data;
	}

	static void save(MinecraftServer server, Data data) {
		server.overworld().setAttached(DATA, data);
	}

	/** Seven new shrine spots, spread round the world's middle. */
	static List<BlockPos> scatter(RandomSource random) {
		List<BlockPos> spots = new ArrayList<>();
		float start = random.nextFloat() * Mth.TWO_PI;
		for (int i = 0; i < ORBS; i++) {
			float angle = start + i * Mth.TWO_PI / ORBS + (random.nextFloat() - 0.5F) * 0.6F;
			spots.add(ring(random, angle, SHRINE_MIN, SHRINE_MAX));
		}
		return spots;
	}

	private static BlockPos ring(RandomSource random, float angle, int min, int max) {
		double distance = min + random.nextDouble() * (max - min);
		return new BlockPos(Mth.floor(Math.cos(angle) * distance), 0, Mth.floor(Math.sin(angle) * distance));
	}

	// ---------------------------------------------------------------- every tick

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.level().dimension() == TIME_CHAMBER && !player.isCreative() && !player.isSpectator()) {
				player.causeFoodExhaustion(CHAMBER_EXHAUSTION);
			}
		}
		if (now % 20 != 0) {
			return;
		}
		ServerLevel overworld = server.overworld();
		Data data = data(server);
		Data changed = data;
		for (ServerPlayer player : overworld.players()) {
			if (player.isSpectator()) {
				continue;
			}
			if (now >= data.restUntil()) {
				for (int i = 0; i < ORBS; i++) {
					BlockPos spot = data.shrines().get(i);
					if (!changed.isBuilt(i) && near(player, spot, BUILD_NEAR)) {
						buildShrine(overworld, spot, i);
						changed = new Data(changed.shrines(), changed.built() | 1 << i, changed.restUntil(), changed.tower(), changed.towerBuilt());
					}
				}
			}
			BlockPos tower = data.tower().orElseThrow();
			if (!changed.towerBuilt() && near(player, tower, TOWER_NEAR)) {
				buildTower(overworld, tower);
				changed = new Data(changed.shrines(), changed.built(), changed.restUntil(), changed.tower(), true);
			}
		}
		if (changed != data) {
			save(server, changed);
		}
	}

	private static boolean near(Player player, BlockPos spot, int distance) {
		double dx = player.getX() - spot.getX() - 0.5;
		double dz = player.getZ() - spot.getZ() - 0.5;
		return dx * dx + dz * dz <= (double)distance * distance;
	}

	/** Whether shrine {@code orb} still holds its orb (an unbuilt one does; a far, unloaded one is taken to). */
	static boolean holdsOrb(ServerLevel overworld, Data data, int orb) {
		if (!data.isBuilt(orb)) {
			return true;
		}
		BlockPos chest = chestPos(overworld, data.shrines().get(orb));
		if (chest == null || !overworld.isLoaded(chest)) {
			return true;
		}
		if (!(overworld.getBlockEntity(chest) instanceof Container container)) {
			return false;
		}
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (ZFighterWishes.isOrb(container.getItem(i))) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- building

	private static void set(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, Block.UPDATE_CLIENTS);
	}

	private static int surface(ServerLevel level, int x, int z) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
	}

	/** Where a shrine's chest sits: under the middle stone (the shrine's floor is at the spot's surface, built once). */
	private static BlockPos chestPos(ServerLevel level, BlockPos spot) {
		if (!level.isLoaded(spot)) {
			return null;
		}
		for (int y = level.getMaxY(); y > level.getMinY(); y--) {
			BlockPos at = new BlockPos(spot.getX(), y, spot.getZ());
			if (level.getBlockState(at).is(Blocks.CHISELED_STONE_BRICKS)) {
				return at.below();
			}
		}
		return null;
	}

	/** A little ring of mossy stone round a carved stone; under it, a chest with the orb. */
	private static void buildShrine(ServerLevel level, BlockPos spot, int orb) {
		int x = spot.getX();
		int z = spot.getZ();
		int y = surface(level, x, z);
		RandomSource random = level.getRandom();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				if (dx * dx + dz * dz > 10) {
					continue;
				}
				set(level, new BlockPos(x + dx, y - 1, z + dz), random.nextBoolean() ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
					: Blocks.STONE_BRICKS.defaultBlockState());
				for (int up = 0; up < 4; up++) {
					set(level, new BlockPos(x + dx, y + up, z + dz), Blocks.AIR.defaultBlockState());
				}
			}
		}
		for (int[] corner : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
			int height = 1 + random.nextInt(3);
			for (int up = 0; up < height; up++) {
				set(level, new BlockPos(x + corner[0], y + up, z + corner[1]), up == height - 1 && random.nextBoolean()
					? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
			}
		}
		BlockPos stone = new BlockPos(x, y - 1, z);
		set(level, stone, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
		BlockPos chest = stone.below();
		set(level, chest, Blocks.CHEST.defaultBlockState());
		if (level.getBlockEntity(chest) instanceof Container container) {
			container.setItem(13, new ItemStack(ZFighterWishes.ORB[orb]));
		}
		set(level, new BlockPos(x, y, z), Blocks.SOUL_LANTERN.defaultBlockState());
	}

	private static void buildTower(ServerLevel level, BlockPos spot) {
		int cx = spot.getX();
		int cz = spot.getZ();
		// make sure the ground round it exists
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				level.getChunk((cx >> 4) + dx, (cz >> 4) + dz);
			}
		}
		int ground = Integer.MAX_VALUE;
		for (int dx = -TOWER_RADIUS; dx <= TOWER_RADIUS; dx++) {
			for (int dz = -TOWER_RADIUS; dz <= TOWER_RADIUS; dz++) {
				ground = Math.min(ground, surface(level, cx + dx, cz + dz));
			}
		}
		int base = Math.max(level.getMinY() + 2, ground - 3);
		BlockState wall = Blocks.SMOOTH_QUARTZ.defaultBlockState();
		BlockState stripe = Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
		BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
		// the spire: a hollow round wall with a ladder up its north side, a door to the south at the foot
		for (int y = base; y < TOWER_TOP; y++) {
			for (int dx = -TOWER_RADIUS; dx <= TOWER_RADIUS; dx++) {
				for (int dz = -TOWER_RADIUS; dz <= TOWER_RADIUS; dz++) {
					double d = Math.sqrt(dx * dx + dz * dz);
					if (d > TOWER_RADIUS + 0.5) {
						continue;
					}
					BlockPos at = new BlockPos(cx + dx, y, cz + dz);
					if (y == base) {
						set(level, at, wall);
					} else if (d > TOWER_RADIUS - 0.5) {
						boolean doorway = dx == 0 && dz == TOWER_RADIUS && y <= base + 2;
						boolean window = (dx == 0 || dz == 0) && y % 16 == 0 && !(dx == 0 && dz < 0);
						set(level, at, doorway || window ? Blocks.AIR.defaultBlockState() : y % 24 == 0 ? stripe : wall);
					} else if (dx == 0 && dz == -(TOWER_RADIUS - 1) && y > base) {
						set(level, at, ladder);
					} else {
						set(level, at, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		// the Hermit's room on top: a wider round floor, low walls with windows, a roof; the pole goes on up from its middle
		int floor = TOWER_TOP;
		for (int dx = -ROOM_RADIUS; dx <= ROOM_RADIUS; dx++) {
			for (int dz = -ROOM_RADIUS; dz <= ROOM_RADIUS; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > ROOM_RADIUS + 0.5) {
					continue;
				}
				boolean hatch = dx == 0 && dz == -(TOWER_RADIUS - 1);
				set(level, new BlockPos(cx + dx, floor, cz + dz), hatch ? ladder : Blocks.QUARTZ_BRICKS.defaultBlockState());
				for (int up = 1; up < ROOM_HEIGHT; up++) {
					BlockPos at = new BlockPos(cx + dx, floor + up, cz + dz);
					boolean edge = d > ROOM_RADIUS - 0.5;
					boolean window = edge && up == 2 && (Math.abs(dx) <= 1 || Math.abs(dz) <= 1);
					set(level, at, edge && !window ? wall : Blocks.AIR.defaultBlockState());
				}
				boolean poleHole = dx == 0 && dz == 1;
				set(level, new BlockPos(cx + dx, floor + ROOM_HEIGHT, cz + dz), poleHole ? ladderOn(Direction.SOUTH)
					: d > ROOM_RADIUS - 1.5 ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
			}
		}
		set(level, new BlockPos(cx + 3, floor + 1, cz + 3), Blocks.LANTERN.defaultBlockState());
		set(level, new BlockPos(cx - 3, floor + 1, cz + 3), Blocks.FLOWER_POT.defaultBlockState());
		// the pole: a pillar from the room's floor to the Lookout, a ladder on its south face
		for (int y = floor + 1; y < LOOKOUT_Y; y++) {
			set(level, new BlockPos(cx, y, cz), Blocks.STRIPPED_BIRCH_LOG.defaultBlockState());
			set(level, new BlockPos(cx, y, cz + 1), ladderOn(Direction.SOUTH));
		}
		buildLookout(level, cx, cz);
		spawnNpc(level, new Vec3(cx + 3.5, floor + 1, cz - 2.5), HERMIT_TAG, "entity.jaymospowers.hermit", hermitOffers());
	}

	private static BlockState ladderOn(Direction facing) {
		return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing);
	}

	/** The floating temple: a two-layer disc ringed with gold, a palace of white pillars on its north half and the doors. */
	private static void buildLookout(ServerLevel level, int cx, int cz) {
		int y = LOOKOUT_Y;
		for (int dx = -LOOKOUT_RADIUS; dx <= LOOKOUT_RADIUS; dx++) {
			for (int dz = -LOOKOUT_RADIUS; dz <= LOOKOUT_RADIUS; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > LOOKOUT_RADIUS + 0.5) {
					continue;
				}
				boolean hole = dx == 0 && dz == 1;
				BlockState top = d > LOOKOUT_RADIUS - 0.5 ? Blocks.GOLD_BLOCK.defaultBlockState()
					: (dx + dz) % 4 == 0 ? Blocks.QUARTZ_BRICKS.defaultBlockState() : Blocks.SMOOTH_QUARTZ.defaultBlockState();
				set(level, new BlockPos(cx + dx, y, cz + dz), hole ? ladderOn(Direction.SOUTH) : top);
				// the underside narrows like a bowl
				int depth = (int)Math.round(4.0 * (1.0 - d / (LOOKOUT_RADIUS + 1.0)));
				for (int down = 1; down <= depth; down++) {
					if (!hole && !(dx == 0 && dz == 0)) {
						set(level, new BlockPos(cx + dx, y - down, cz + dz), down == depth ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.CALCITE.defaultBlockState());
					}
				}
				for (int up = 1; up <= 9; up++) {
					set(level, new BlockPos(cx + dx, y + up, cz + dz), Blocks.AIR.defaultBlockState());
				}
			}
		}
		// palms of a sort: two flowering azaleas and grass round them
		for (int[] tree : new int[][] {{-8, 4}, {8, 4}, {-6, 8}, {6, 8}}) {
			set(level, new BlockPos(cx + tree[0], y, cz + tree[1]), Blocks.GRASS_BLOCK.defaultBlockState());
			set(level, new BlockPos(cx + tree[0], y + 1, cz + tree[1]), Blocks.FLOWERING_AZALEA.defaultBlockState());
		}
		// the palace: a raised floor, pillars, a roof, the Keeper inside and the two doors at the back
		int pz = cz - 6;
		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				set(level, new BlockPos(cx + dx, y + 1, pz + dz), Blocks.QUARTZ_BRICKS.defaultBlockState());
				set(level, new BlockPos(cx + dx, y + 7, pz + dz), Math.abs(dx) == 5 || Math.abs(dz) == 4 ? Blocks.GOLD_BLOCK.defaultBlockState()
					: Blocks.SMOOTH_QUARTZ.defaultBlockState());
			}
			set(level, new BlockPos(cx + dx, y + 8, pz), Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState());
		}
		for (int dx = -5; dx <= 5; dx += 2) {
			for (int dz : new int[] {-4, 4}) {
				for (int up = 2; up <= 6; up++) {
					set(level, new BlockPos(cx + dx, y + up, pz + dz), Blocks.QUARTZ_PILLAR.defaultBlockState());
				}
			}
		}
		for (int up = 2; up <= 3; up++) {
			set(level, new BlockPos(cx - 2, y + up, pz - 3), CHAMBER_DOOR.defaultBlockState());
			set(level, new BlockPos(cx + 2, y + up, pz - 3), VERDANT_GATE.defaultBlockState());
		}
		set(level, new BlockPos(cx, y + 2, pz - 3), Blocks.LANTERN.defaultBlockState());
		spawnNpc(level, new Vec3(cx + 0.5, y + 2, pz + 0.5), KEEPER_TAG, "entity.jaymospowers.keeper", new MerchantOffers());
	}

	/** A trader who never leaves, never moves and can't be hurt: the Hermit (who sells) or the Keeper (who blesses). */
	private static void spawnNpc(ServerLevel level, Vec3 at, String tag, String name, MerchantOffers offers) {
		WanderingTrader npc = EntityTypes.WANDERING_TRADER.create(level, EntitySpawnReason.STRUCTURE);
		if (npc == null) {
			return;
		}
		npc.snapTo(at.x, at.y, at.z, 180.0F, 0.0F);
		npc.setNoAi(true);
		npc.setPermanentlyInvulnerable(true);
		npc.setPersistenceRequired();
		npc.setDespawnDelay(0);
		npc.addTag(tag);
		npc.setCustomName(Component.translatable(name));
		npc.setCustomNameVisible(true);
		MerchantOffers own = npc.getOffers();
		own.clear();
		own.addAll(offers);
		level.addFreshEntity(npc);
	}

	private static MerchantOffers hermitOffers() {
		MerchantOffers offers = new MerchantOffers();
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 12), new ItemStack(ZFighterItems.HERMIT_BEAN), 999, 0, 0.0F));
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 6), new ItemStack(ZFighterItems.HERMIT_BEAN_SEEDS, 2), 999, 0, 0.0F));
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 24), new ItemStack(ZFighterWishes.RADAR), 999, 0, 0.0F));
		return offers;
	}

	// ---------------------------------------------------------------- the Keeper

	private static void bless(ServerPlayer player) {
		long now = player.level().getGameTime();
		Long last = player.getAttached(BLESSED);
		if (last != null && now - last < DAY) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_keeper_again").withStyle(ChatFormatting.GRAY));
			return;
		}
		player.setAttached(BLESSED, now);
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 1));
		player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, BLESSING_TICKS, 0));
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, BLESSING_TICKS, 0));
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_keeper").withStyle(ChatFormatting.AQUA));
		if (Powers.has(player, ZFighterPower.INSTANCE)) {
			ZFighterTraining.grant(player, BLESSING_TP);
		}
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.4, 0.8, 0.4, 0.05);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.2F);
	}

	// ---------------------------------------------------------------- the doors

	/** The Lookout's doors (and their twins on the far side): right click to pass through. */
	static final class Door extends Block {
		Door(BlockBehaviour.Properties properties) {
			super(properties);
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (!(player instanceof ServerPlayer server)) {
				return InteractionResult.SUCCESS;
			}
			pass(server, state.is(CHAMBER_DOOR) ? TIME_CHAMBER : VERDANT_WORLD);
			return InteractionResult.SUCCESS;
		}
	}

	/** Through a door: from the overworld to {@code far}; from {@code far}, back to the Lookout. */
	static void pass(ServerPlayer player, ResourceKey<Level> far) {
		MinecraftServer server = player.level().getServer();
		ServerLevel to;
		Vec3 at;
		if (player.level().dimension() == far) {
			to = server.overworld();
			BlockPos tower = data(server).tower().orElseThrow();
			at = new Vec3(tower.getX() + 0.5, LOOKOUT_Y + 2, tower.getZ() - 1.5);
		} else {
			to = server.getLevel(far);
			if (to == null) {
				return;
			}
			at = far == TIME_CHAMBER ? arriveChamber(to) : arriveVerdant(to, player);
		}
		player.teleportTo(to, at.x, at.y, at.z, Set.of(), player.getYRot(), player.getXRot(), true);
		to.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1.0F, far == TIME_CHAMBER ? 1.4F : 0.8F);
		player.sendOverlayMessage(Component.translatable(to.dimension() == TIME_CHAMBER ? "message.jaymospowers.zfighter_chamber"
			: to.dimension() == VERDANT_WORLD ? "message.jaymospowers.zfighter_verdant" : "message.jaymospowers.zfighter_lookout")
			.withStyle(ChatFormatting.AQUA));
	}

	/** The Time Chamber's one building: a white floor with the door home, and a bed. */
	private static Vec3 arriveChamber(ServerLevel chamber) {
		BlockPos door = new BlockPos(0, CHAMBER_FLOOR, 0);
		if (!chamber.getBlockState(door).is(CHAMBER_DOOR)) {
			for (int dx = -4; dx <= 4; dx++) {
				for (int dz = -4; dz <= 4; dz++) {
					set(chamber, new BlockPos(dx, CHAMBER_FLOOR - 1, dz), Blocks.QUARTZ_BRICKS.defaultBlockState());
				}
			}
			for (int up = 0; up < 2; up++) {
				set(chamber, door.above(up), CHAMBER_DOOR.defaultBlockState());
			}
			set(chamber, new BlockPos(-3, CHAMBER_FLOOR, -3), Blocks.LANTERN.defaultBlockState());
			set(chamber, new BlockPos(3, CHAMBER_FLOOR, -3), Blocks.WATER_CAULDRON.defaultBlockState());
		}
		return new Vec3(0.5, CHAMBER_FLOOR, 2.5);
	}

	/** Out in the Verdant World where the Lookout floats over the other world: a little ring of stone with the gate back. */
	private static Vec3 arriveVerdant(ServerLevel verdant, ServerPlayer player) {
		int x = Mth.floor(player.getX());
		int z = Mth.floor(player.getZ());
		verdant.getChunk(x >> 4, z >> 4);
		int y = surface(verdant, x, z);
		BlockPos gate = new BlockPos(x, y, z - 2);
		if (!verdant.getBlockState(gate).is(VERDANT_GATE)) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -4; dz <= 1; dz++) {
					set(verdant, new BlockPos(x + dx, y - 1, z + dz), Blocks.MOSSY_COBBLESTONE.defaultBlockState());
					for (int up = 0; up < 3; up++) {
						set(verdant, new BlockPos(x + dx, y + up, z + dz), Blocks.AIR.defaultBlockState());
					}
				}
			}
			for (int up = 0; up < 2; up++) {
				set(verdant, gate.above(up), VERDANT_GATE.defaultBlockState());
			}
		}
		return new Vec3(x + 0.5, y, z + 0.5);
	}
}
