package com.jaymospowers.cloudbottle;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.aether.AercloudBlock;
import com.jaymospowers.aether.AetherBlocks;
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
 * cloud is not used up by bottling. Clouds drop nothing when broken unless mined with Silk Touch (they are soft like leaves,
 * {@link AercloudBlock}), so bottling is the other way cloud is gathered: a blue, golden or purple aercloud fills a bottle
 * of its own colour (what their gliders are made from, as well as the Silk Touched block), every other cloud and the open
 * sky a plain Bottle of Cloud (what the cold glider, the cloud skiff, the updraft vent and cloudwool are made from, as well
 * as a Silk Touched cold aercloud). Every bottle lays the same puff, and crafting hands the glass back. Art:
 * tools/gen_stone_ores.py (the bottles, the puff), lang: lang/parts/cloud_bottle.json.
 */
public final class CloudBottle {
	public static Block PUFF;
	public static Item BOTTLE_OF_CLOUD, BOTTLE_OF_BLUE_CLOUD, BOTTLE_OF_GOLDEN_CLOUD, BOTTLE_OF_PURPLE_CLOUD;

	private CloudBottle() {
	}

	public static void init() {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("cloud_puff"));
		PUFF = Registry.register(BuiltInRegistries.BLOCK, blockKey, new CloudPuffBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW)
			.strength(0.1F).sound(SoundType.WOOL).noOcclusion().noLootTable()
			.isValidSpawn(Blocks::never).isRedstoneConductor(Blocks::never).isSuffocating(Blocks::never).setId(blockKey)));
		BOTTLE_OF_CLOUD = bottle("bottle_of_cloud");
		BOTTLE_OF_BLUE_CLOUD = bottle("bottle_of_blue_cloud");
		BOTTLE_OF_GOLDEN_CLOUD = bottle("bottle_of_golden_cloud");
		BOTTLE_OF_PURPLE_CLOUD = bottle("bottle_of_purple_cloud");
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.accept(BOTTLE_OF_CLOUD);
			output.accept(BOTTLE_OF_BLUE_CLOUD);
			output.accept(BOTTLE_OF_GOLDEN_CLOUD);
			output.accept(BOTTLE_OF_PURPLE_CLOUD);
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (player.isSpectator() || !stack.is(Items.GLASS_BOTTLE)) {
				return InteractionResult.PASS;
			}
			Block block = level.getBlockState(hit.getBlockPos()).getBlock();
			return block instanceof AercloudBlock && !(block instanceof CloudPuffBlock) ? fill(level, player, hand, stack, bottleOf(block)) : InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			return !player.isSpectator() && stack.is(Items.GLASS_BOTTLE) && inSky(player) ? fill(level, player, hand, stack, BOTTLE_OF_CLOUD) : InteractionResult.PASS;
		});
	}

	/** A bottle of cloud that hands its glass back when it is crafted with. */
	private static Item bottle(String id) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new BottleOfCloudItem(new Item.Properties().setId(key).stacksTo(16)
			.craftRemainder(Items.GLASS_BOTTLE)));
	}

	/** What a cloud fills a bottle with: the blue, golden and purple aerclouds their own colour, the rest plain cloud. */
	static Item bottleOf(Block cloud) {
		if (cloud == AetherBlocks.BLUE_AERCLOUD) {
			return BOTTLE_OF_BLUE_CLOUD;
		}
		if (cloud == AetherBlocks.GOLDEN_AERCLOUD) {
			return BOTTLE_OF_GOLDEN_CLOUD;
		}
		if (cloud == AetherBlocks.PURPLE_AERCLOUD) {
			return BOTTLE_OF_PURPLE_CLOUD;
		}
		return BOTTLE_OF_CLOUD;
	}

	/** Whether the player is in the Cloud Sea or the high sky (bands of a stacked world; the realm is the level's own otherwise). */
	static boolean inSky(Player player) {
		return Stacked.in(player, CloudSea.REALM) || Stacked.in(player, ZFighterHighSky.HIGH_SKY);
	}

	/** Whether a puff block can go here: open air only, so nothing (grass, snow, flowers) is lost when the puff thins away. */
	static boolean open(Level level, BlockPos pos) {
		return level.isInWorldBounds(pos) && level.getBlockState(pos).isAir();
	}

	/** One empty bottle becomes a bottle of cloud. */
	private static InteractionResult fill(Level level, Player player, InteractionHand hand, ItemStack stack, Item filled) {
		if (level instanceof ServerLevel server) {
			player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(filled)));
			server.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1.0F, 1.3F);
			server.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getEyeY() - 0.3, player.getZ(), 5, 0.3, 0.2, 0.3, 0.01);
		}
		return InteractionResult.SUCCESS;
	}
}
