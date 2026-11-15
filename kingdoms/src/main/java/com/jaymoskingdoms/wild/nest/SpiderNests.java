package com.jaymoskingdoms.wild.nest;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Loaded;
import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.beasts.Beasts;
import com.mojang.serialization.Codec;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;

/**
 * (medieval picks, patch 2: spider nests; rows de-netcaster, de-spiny, de-nests, de-eggs, de-broodqueen) The spider nests:
 * their blocks (dark spider stone, nest silk, shed skins, egg sacs, the brood queen's egg), the silk glands netcasters leave,
 * the brood queen, and the nest's life.
 * <ul>
 * <li><b>Where:</b> the silk deeps, a cave biome of ours ({@link SilkDeeps}); the queen's chamber at a nest's deepest and the
 * watcher's hut above it ({@link NestStructures}).</li>
 * <li><b>What it eats</b> (feedback loop): the nest's silk and cobwebs snare the cave's bats and critters
 * ({@link #NEST_PREY}) that blunder into them, and whatever its spiders kill there feeds it too. A fed nest spreads
 * ({@link #fed}): its silk creeps over the rock near by and a new egg sac is laid, until it holds {@link #EGG_CAP} round
 * there; so a nest grows while its caves have bats, and stops when it has eaten them.</li>
 * <li><b>Fire:</b> its silk, skins and egg sacs burn; a nest that burned ({@link #scar}) stays dead: no egg grows back
 * there, nothing spreads, no queen comes back.</li>
 * </ul>
 * Lore: the silk deeps (Lore.md): a brood queen grown huge on the cave's life.
 */
public final class SpiderNests {
	// ---- tuning ----
	/** How often (ticks) the nest round each player in it looks for snared critters, and how far round them (blocks). */
	static final int CATCH_EVERY = 40;
	static final double CATCH_RANGE = 24.0;
	/** A feeding lays an egg sac within FEED_REACH (blocks) unless EGG_CAP are there already, and spreads silk over up to SPREAD
	 * blocks of rock. */
	static final int FEED_REACH = 6, EGG_CAP = 6, SPREAD = 3, FEED_TRIES = 24;
	/** A laid egg sac's first look (ticks). */
	static final int LAID_LOOK = 200;

	/** What a nest's silk snares and eats: the cave's bats and small critters ({@code jaymoskingdoms:nest_prey}). */
	public static final TagKey<EntityType<?>> NEST_PREY = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("nest_prey"));
	/** On a chunk: a nest here burned out, and stays dead. Saved. */
	static final AttachmentType<Boolean> SCARRED = AttachmentRegistry.create(JaymosKingdoms.id("nest_scarred"),
		builder -> builder.persistent(Codec.BOOL));

	public static Block SPIDER_STONE, NEST_SILK, SHED_SKIN, EGG_SAC, BROOD_EGG;
	public static Item SILK_GLAND;
	public static BlockEntityType<BroodEggBlock.Entity> BROOD_EGG_ENTITY;
	public static EntityType<BroodQueen> BROOD_QUEEN;

	private SpiderNests() {
	}

	public static void register() {
		JaymosKingdoms.LOGGER.debug("spider nest attachment: {}", SCARRED);
		SPIDER_STONE = block("spider_stone", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops()
			.strength(2.5F, 6.0F).sound(SoundType.DEEPSLATE));
		NEST_SILK = block("nest_silk", p -> new NestBlock(p, false), BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).strength(0.6F)
			.sound(SoundType.WOOL).ignitedByLava());
		SHED_SKIN = block("shed_skin", p -> new NestBlock(p, true), BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).noCollision()
			.instabreak().sound(SoundType.WOOL).ignitedByLava().pushReaction(PushReaction.POPPED).noOcclusion());
		EGG_SAC = block("egg_sac", EggSacBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(0.3F)
			.sound(SoundType.FROGSPAWN).ignitedByLava().pushReaction(PushReaction.POPPED).noOcclusion().randomTicks());
		BROOD_EGG = block("brood_egg", BroodEggBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(-1.0F, 3600000.0F)
			.sound(SoundType.FROGSPAWN).noOcclusion().noLootTable().pushReaction(PushReaction.IMMOVEABLE).lightLevel(s -> 3));
		BROOD_EGG_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKingdoms.id("brood_egg"),
			new BlockEntityType<>(BroodEggBlock.Entity::new, Set.of(BROOD_EGG)));
		ResourceKey<Item> gland = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("silk_gland"));
		SILK_GLAND = Registry.register(BuiltInRegistries.ITEM, gland, new Item(new Item.Properties().setId(gland)));
		BROOD_QUEEN = Beasts.type("brood_queen", EntityType.Builder.of(BroodQueen::new, MobCategory.MONSTER).sized(2.6F, 1.4F).clientTrackingRange(10),
			BroodQueen.createAttributes());

		FlammableBlockRegistry flames = FlammableBlockRegistry.getDefaultInstance();
		flames.add(NEST_SILK, 30, 60);
		flames.add(SHED_SKIN, 60, 100);
		flames.add(EGG_SAC, 60, 100);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> {
			output.accept(SPIDER_STONE);
			output.accept(NEST_SILK);
			output.accept(SHED_SKIN);
			output.accept(EGG_SAC);
			output.accept(BROOD_EGG);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.accept(SILK_GLAND));

		SilkDeeps.register();
		NestStructures.register();
		// whatever the nest's spiders (or their queen) kill in it feeds it
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.level() instanceof ServerLevel level && !(entity instanceof Spider) && !(entity instanceof BroodQueen)
				&& (source.getEntity() instanceof Spider || source.getEntity() instanceof BroodQueen)) {
				fed(level, entity.blockPosition(), entity.getRandom());
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(SpiderNests::tick);
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> make, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, make.apply(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	/** Whether {@code pos} is in a spider nest (the silk deeps). Only for a loaded spot. */
	public static boolean inNest(ServerLevel level, BlockPos pos) {
		return level.getBiome(pos).is(SilkDeeps.SILK_DEEPS);
	}

	// ---- what the nest eats ----

	/** Bats and critters near players in a nest, snared in its silk, are wrapped and eaten. */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CATCH_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.level();
			if (player.isSpectator() || !inNest(level, player.blockPosition())) {
				continue;
			}
			for (LivingEntity prey : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(CATCH_RANGE),
					e -> e.isAlive() && e.getType().builtInRegistryHolder().is(NEST_PREY))) {
				if (snared(level, prey) && inNest(level, prey.blockPosition())) {
					eat(level, prey);
				}
			}
		}
	}

	/** Whether {@code body} is touching the nest's silk: a cobweb, nest silk or an egg sac. */
	private static boolean snared(ServerLevel level, LivingEntity body) {
		AABB box = body.getBoundingBox().inflate(0.15);
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			BlockState s = level.getBlockState(p);
			if (s.is(Blocks.COBWEB) || s.is(NEST_SILK) || s.is(EGG_SAC)) {
				return true;
			}
		}
		return false;
	}

	private static void eat(ServerLevel level, LivingEntity prey) {
		PowersCompat.web(prey, 100, null, 3);
		level.playSound(null, prey.blockPosition(), SoundEvents.COBWEB_HIT, SoundSource.HOSTILE, 0.8F, 1.3F);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, prey.getX(), prey.getY(0.5), prey.getZ(), 10, 0.2, 0.2, 0.2, 0.02);
		BlockPos at = prey.blockPosition();
		prey.hurtServer(level, level.damageSources().generic(), Float.MAX_VALUE);
		if (!prey.isAlive()) {
			fed(level, at, prey.getRandom());
		}
	}

	/**
	 * The nest at {@code at} has eaten: its silk creeps over a few blocks of the rock near by, and an egg sac is laid, unless
	 * it already holds {@link #EGG_CAP} round there. Nothing outside a nest, or in a burned-out one.
	 */
	static void fed(ServerLevel level, BlockPos at, RandomSource random) {
		if (!Loaded.made(level, at) || !inNest(level, at) || scarred(level, at)) {
			return;
		}
		int eggs = 0;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-FEED_REACH, -FEED_REACH, -FEED_REACH), at.offset(FEED_REACH, FEED_REACH, FEED_REACH))) {
			if (level.getBlockState(p).is(EGG_SAC)) {
				eggs++;
			}
		}
		boolean laid = eggs >= EGG_CAP;
		int spread = 0;
		for (int i = 0; i < FEED_TRIES && (!laid || spread < SPREAD); i++) {
			BlockPos p = at.offset(random.nextInt(FEED_REACH * 2 + 1) - FEED_REACH, random.nextInt(FEED_REACH * 2 + 1) - FEED_REACH,
				random.nextInt(FEED_REACH * 2 + 1) - FEED_REACH);
			if (!Loaded.made(level, p)) {
				continue;
			}
			BlockState s = level.getBlockState(p);
			if (!laid && s.isAir()) {
				boolean roof = level.getBlockState(p.above()).isFaceSturdy(level, p.above(), Direction.DOWN);
				boolean floor = level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP);
				if (roof || floor) {
					EggSacBlock.lay(level, p, roof, LAID_LOOK);
					laid = true;
				}
			} else if (spread < SPREAD && (s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(SPIDER_STONE)) && open(level, p)) {
				level.setBlockAndUpdate(p, NEST_SILK.defaultBlockState());
				spread++;
			}
		}
	}

	private static boolean open(ServerLevel level, BlockPos p) {
		for (Direction d : Direction.values()) {
			if (level.getBlockState(p.relative(d)).isAir()) {
				return true;
			}
		}
		return false;
	}

	// ---- fire ----

	/** (From a nest block's removal) If fire took it, or an Emberborn's touch (Jaymo's Powers burns webs so), its nest is burned out. */
	static void burned(ServerLevel level, BlockPos pos) {
		boolean fire = level.getBlockState(pos).is(BlockTags.FIRE) || emberAt(level, pos);
		for (Direction d : Direction.values()) {
			fire |= level.getBlockState(pos.relative(d)).is(BlockTags.FIRE);
		}
		if (fire) {
			scar(level, pos);
		}
	}

	/** An Emberborn touching the block at {@code pos} (they carry Kingdoms' heritage tag, which Powers reads). */
	private static boolean emberAt(ServerLevel level, BlockPos pos) {
		String ember = com.jaymoskingdoms.Heritage.TAG + com.jaymoskingdoms.Heritage.Kind.EMBERBORN.key();
		return !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(0.2), e -> e.entityTags().contains(ember)).isEmpty();
	}

	/**
	 * The nest at {@code pos} burned out: its chunk stays dead (no egg grows back, nothing spreads, no queen returns); and in a
	 * brood queen's lair, the whole lair ({@link BroodEggBlock#lairOf}): her egg never grows back and no queen comes again.
	 */
	public static void scar(ServerLevel level, BlockPos pos) {
		BroodEggBlock.Entity lair = BroodEggBlock.lairOf(level, pos);
		if (lair != null) {
			lair.burn(level);
		}
		if (inNest(level, pos) && !scarred(level, pos)) {
			level.getChunkAt(pos).setAttached(SCARRED, true);
			level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 0.5F);
		}
	}

	public static boolean scarred(ServerLevel level, BlockPos pos) {
		return Boolean.TRUE.equals(level.getChunkAt(pos).getAttached(SCARRED));
	}

	/** Damage from the queen's moves: her own blow. */
	static DamageSource struck(ServerLevel level, LivingEntity by) {
		return level.damageSources().mobAttack(by);
	}
}
