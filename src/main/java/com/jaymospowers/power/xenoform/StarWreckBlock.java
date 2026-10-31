package com.jaymospowers.power.xenoform;

import com.jaymospowers.JaymosPowers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.121, Xeno-Band patch 6) A star wreck's core: the broken heart of a ship that came down as a falling star. While a
 * band wearer is within {@link #WAKE_RANGE} it sends out a probe ({@link XenoFinds.StarProbe}) every {@link #EVERY} ticks,
 * up to {@link #MAX_PROBES} at once. Mined (iron pickaxe), it gives a wreck core and a few circuits.
 */
public final class StarWreckBlock extends Block {
	// ---- tuning ----
	public static final double WAKE_RANGE = 32.0, PROBE_COUNT_RANGE = 48.0;
	public static final int EVERY = 100, MAX_PROBES = 3;
	public static final int CIRCUITS_MIN = 2, CIRCUITS_MAX = 4;
	/** Bits of hull strewn round the crater when a wreck lands. */
	public static final int DEBRIS = 8;
	public static final double DEBRIS_SPREAD = 5.0;

	public static Block WRECK;
	public static Item ITEM;

	public StarWreckBlock(Properties properties) {
		super(properties);
	}

	static void init() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("star_wreck"));
		WRECK = Registry.register(BuiltInRegistries.BLOCK, key, new StarWreckBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.METAL)
			.strength(6.0F, 1200.0F)
			.requiresCorrectToolForDrops()
			.lightLevel(state -> 7)
			.sound(SoundType.NETHERITE_BLOCK)
			.pushReaction(PushReaction.IMMOVEABLE)
			.noLootTable()
			.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("star_wreck"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(WRECK, new Item.Properties().setId(itemKey)
			.useBlockDescriptionPrefix().rarity(Rarity.RARE)));
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
		super.onPlace(state, level, pos, old, moved);
		if (!level.isClientSide()) {
			level.scheduleTick(pos, this, EVERY);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		level.scheduleTick(pos, this, EVERY);
		Vec3 centre = Vec3.atCenterOf(pos);
		Player near = null;
		double best = WAKE_RANGE * WAKE_RANGE;
		for (ServerPlayer player : level.players()) {
			double d = player.distanceToSqr(centre);
			if (d < best && XenoformPower.has(player) && !player.isCreative() && !player.isSpectator()) {
				best = d;
				near = player;
			}
		}
		if (near == null) {
			return;
		}
		int out = level.getEntitiesOfClass(XenoFinds.StarProbe.class, new AABB(pos).inflate(PROBE_COUNT_RANGE)).size();
		if (out >= MAX_PROBES) {
			return;
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, centre.x, centre.y + 0.6, centre.z, 12, 0.3, 0.3, 0.3, 0.1);
		XenoFinds.sendProbe(level, centre.add(0.0, 1.5, 0.0), near);
	}

	@Override
	public void playerDestroy(ServerLevel server, ServerPlayer player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
		super.playerDestroy(server, player, pos, state, blockEntity, tool);
		RandomSource random = server.getRandom();
		popResource(server, pos, new ItemStack(XenoFinds.WRECK_CORE));
		popResource(server, pos, new ItemStack(XenoFinds.CIRCUIT, CIRCUITS_MIN + random.nextInt(CIRCUITS_MAX - CIRCUITS_MIN + 1)));
		server.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 0.6F);
	}

	/** A star came down a wreck: the core beside the star, and bits of hull strewn about the crater. */
	static void wreck(ServerLevel level, BlockPos star) {
		RandomSource random = level.getRandom();
		BlockPos core = null;
		for (BlockPos side : new BlockPos[] {star.north(), star.south(), star.east(), star.west(), star.above()}) {
			if (level.getBlockState(side).canBeReplaced() || level.getBlockState(side).isAir()) {
				core = side;
				break;
			}
		}
		level.setBlock(core != null ? core : star.above(), WRECK.defaultBlockState(), Block.UPDATE_ALL);
		BlockState[] hull = {Blocks.CUT_COPPER.waxed().weathered().defaultBlockState(), Blocks.CUT_COPPER.waxed().exposed().defaultBlockState(),
			Blocks.IRON_BARS.defaultBlockState(), Blocks.TINTED_GLASS.defaultBlockState()};
		for (int i = 0; i < DEBRIS; i++) {
			double a = random.nextDouble() * Math.PI * 2.0, d = 1.5 + random.nextDouble() * DEBRIS_SPREAD;
			BlockPos column = star.offset((int)Math.round(Math.cos(a) * d), 4, (int)Math.round(Math.sin(a) * d));
			BlockPos.MutableBlockPos at = column.mutable();
			for (int y = 0; y < 10; y++, at.move(0, -1, 0)) {
				if (level.getBlockState(at.below()).isSolid() && (level.getBlockState(at).isAir() || level.getBlockState(at).canBeReplaced())) {
					level.setBlock(at, hull[random.nextInt(hull.length)], Block.UPDATE_ALL);
					break;
				}
			}
		}
	}
}
