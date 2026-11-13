package com.jaymospowers.eden;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.aether.FloraPlantBlock;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Limphium, tended: a sapling (from its leaves, four to a sapling) that grows into the tall plant when the light is good
 * and the air above is free, and a leaf held in the hand that lets a faller down slowly, as the plant's broad leaves
 * would. Whoever plants it gets more of it; nobody is hurt by leaving it be.
 */
public final class EdenLimphium {
	/** Chance a random tick grows the sapling, and the light it needs. */
	public static final float GROW_CHANCE = 0.2F;
	public static final int GROW_LIGHT = 9;
	/** How often (ticks) the held leaf is looked for, and how long the slow fall it gives lasts. */
	public static final int LEAF_CHECK = 5, LEAF_FALL_TICKS = 20;

	public static Block SAPLING;

	private EdenLimphium() {
	}

	/** A limphium sapling: a small plant that grows into the tall one. */
	static final class SaplingBlock extends FloraPlantBlock {
		SaplingBlock(BlockBehaviour.Properties properties) {
			super(Block.box(3, 0, 3, 13, 12, 13), false, properties);
		}

		@Override
		protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
			if (random.nextFloat() < GROW_CHANCE && level.getMaxLocalRawBrightness(pos.above()) >= GROW_LIGHT && level.isEmptyBlock(pos.above())) {
				DoublePlantBlock.placeAt(level, Eden.LIMPHIUM.defaultBlockState(), pos, Block.UPDATE_ALL);
			}
		}

		@Override
		protected boolean isRandomlyTicking(BlockState state) {
			return true;
		}
	}

	public static void init() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("limphium_sapling"));
		SAPLING = Registry.register(BuiltInRegistries.BLOCK, key, new SaplingBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT)
			.noCollision().instabreak().sound(SoundType.GRASS).pushReaction(PushReaction.POPPED).randomTicks().setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("limphium_sapling"));
		Item item = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(SAPLING, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> output.accept(item));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % LEAF_CHECK != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (!player.onGround() && player.getDeltaMovement().y < 0.0 && !player.isInWater() && holdsLeaf(player)) {
					player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, LEAF_FALL_TICKS, 0, true, false, false));
				}
			}
		});
	}

	private static boolean holdsLeaf(ServerPlayer player) {
		return isLeaf(player.getMainHandItem()) || isLeaf(player.getOffhandItem());
	}

	private static boolean isLeaf(ItemStack stack) {
		return stack.is(Eden.LIMPHIUM_LEAF) || stack.is(Eden.DRIED_LIMPHIUM_LEAF);
	}
}
