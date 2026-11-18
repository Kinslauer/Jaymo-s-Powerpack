package com.jaymospowers.cloudbottle;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.aether.AercloudBlock;
import com.jaymospowers.aether.cloudsea.CloudSea;
import com.jaymospowers.compat.Stacked;
import com.jaymospowers.power.zfighter.ZFighterHighSky;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * Bottled cloud (Clouds and Bits, world bits). An empty glass bottle used on a cloud (our Cloudbank, or any of the Aether's
 * aerclouds, never a puff of bottled cloud) or used in the air in the Cloud Sea or the high sky of a stacked world gives a
 * Bottle of Cloud ({@link BottleOfCloudItem}), which lays a short-lived puff ({@link CloudPuffBlock}) or catches a faller. The
 * cloud is not used up by bottling. Art: tools/gen_stone_ores.py (the bottle, the puff), lang: lang/parts/cloud_bottle.json.
 */
public final class CloudBottle {
	public static Block PUFF;
	public static Item BOTTLE_OF_CLOUD;

	private CloudBottle() {
	}

	public static void init() {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("cloud_puff"));
		PUFF = Registry.register(BuiltInRegistries.BLOCK, blockKey, new CloudPuffBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW)
			.strength(0.1F).sound(SoundType.WOOL).noOcclusion().noLootTable()
			.isValidSpawn(Blocks::never).isRedstoneConductor(Blocks::never).isSuffocating(Blocks::never).setId(blockKey)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("bottle_of_cloud"));
		BOTTLE_OF_CLOUD = Registry.register(BuiltInRegistries.ITEM, itemKey, new BottleOfCloudItem(new Item.Properties().setId(itemKey).stacksTo(16)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.accept(BOTTLE_OF_CLOUD));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (player.isSpectator() || !stack.is(Items.GLASS_BOTTLE)) {
				return InteractionResult.PASS;
			}
			Block block = level.getBlockState(hit.getBlockPos()).getBlock();
			return block instanceof AercloudBlock && !(block instanceof CloudPuffBlock) ? fill(level, player, hand, stack) : InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			return !player.isSpectator() && stack.is(Items.GLASS_BOTTLE) && inSky(player) ? fill(level, player, hand, stack) : InteractionResult.PASS;
		});
	}

	/** Whether the player is in the Cloud Sea or the high sky (bands of a stacked world; the realm is the level's own otherwise). */
	static boolean inSky(Player player) {
		return Stacked.in(player, CloudSea.REALM) || Stacked.in(player, ZFighterHighSky.HIGH_SKY);
	}

	/** Whether a puff block can go here: open air or something that gives way, and no fluid. */
	static boolean open(Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return level.isInWorldBounds(pos) && (state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty());
	}

	/** One empty bottle becomes a Bottle of Cloud. */
	private static InteractionResult fill(Level level, Player player, InteractionHand hand, ItemStack stack) {
		if (level instanceof ServerLevel server) {
			player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(BOTTLE_OF_CLOUD)));
			server.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1.0F, 1.3F);
			server.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getEyeY() - 0.3, player.getZ(), 5, 0.3, 0.2, 0.3, 0.01);
		}
		return InteractionResult.SUCCESS;
	}
}
