package com.jaymoskingdoms;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The endless jug: a clay jug that never runs dry. Use it on a cauldron to fill it; on farmland to soak it (and the soil
 * round it); on a fire to put it out; on a campfire to douse it. Use it in the air with an empty bucket in your other hand to
 * fill the bucket. Used in the air bare, it is a drink of clean water: it puts out any flame on you and quenches thirst (or, without Kitchen, settles a hungry belly a little).
 * It sets no water loose in the world: only what a jug can fill, nothing that flows.
 */
public final class EndlessJug {
	/** Farmland round the one used is soaked this far (blocks). */
	static final int SOAK = 1;
	/** A drink's water (Kitchen's thirst), its food points without Kitchen, and the wait after (ticks). */
	static final int DRINK_WATER = 4, DRINK_FOOD = 1, COOLDOWN = 20 * 3;
	public static Item ITEM;

	private EndlessJug() {
	}

	static void register() {
		ITEM = Gadgets.item("endless_jug", Rarity.UNCOMMON, Jug::new);
	}

	static final class Jug extends Item {
		Jug(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			Level level = context.getLevel();
			BlockPos pos = context.getClickedPos();
			BlockState state = level.getBlockState(pos);
			if (!(level instanceof ServerLevel server)) {
				return InteractionResult.SUCCESS;
			}
			if (state.is(Blocks.CAULDRON) || state.is(Blocks.LAVA_CAULDRON) || state.is(Blocks.POWDER_SNOW_CAULDRON)
				|| state.is(Blocks.WATER_CAULDRON) && state.getValue(LayeredCauldronBlock.LEVEL) < 3) {
				server.setBlock(pos, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3), 3);
				pour(server, pos);
				return InteractionResult.SUCCESS;
			}
			if (state.getBlock() instanceof FarmlandBlock) {
				for (BlockPos p : BlockPos.betweenClosed(pos.offset(-SOAK, 0, -SOAK), pos.offset(SOAK, 0, SOAK))) {
					BlockState soil = server.getBlockState(p);
					if (soil.getBlock() instanceof FarmlandBlock) {
						server.setBlock(p, soil.setValue(FarmlandBlock.MOISTURE, 7), 3);
					}
				}
				pour(server, pos);
				return InteractionResult.SUCCESS;
			}
			BlockPos above = pos.relative(context.getClickedFace());
			if (server.getBlockState(above).is(BlockTags.FIRE)) {
				server.removeBlock(above, false);
				server.playSound(null, above, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8F, 1.0F);
				pour(server, above);
				return InteractionResult.SUCCESS;
			}
			if (state.is(BlockTags.CAMPFIRES) && state.hasProperty(net.minecraft.world.level.block.CampfireBlock.LIT)
				&& state.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)) {
				server.setBlock(pos, state.setValue(net.minecraft.world.level.block.CampfireBlock.LIT, false), 3);
				server.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8F, 1.0F);
				pour(server, pos);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(level instanceof ServerLevel server)) {
				return InteractionResult.SUCCESS;
			}
			ItemStack jug = player.getItemInHand(hand);
			ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
			if (other.is(Items.BUCKET)) {
				other.shrink(1);
				if (!player.getInventory().add(new ItemStack(Items.WATER_BUCKET))) {
					player.drop(new ItemStack(Items.WATER_BUCKET), false, net.minecraft.util.Prediction.SERVER_ONLY);
				}
				server.playSound(null, player.blockPosition(), SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 0.9F, 1.0F);
			} else {
				if (player.isOnFire()) {
					player.clearFire();
				}
				// clean water for the thirst (Kitchen); without Kitchen, a little settles the belly
				if (!(player instanceof net.minecraft.server.level.ServerPlayer sp && KitchenCompat.drink(sp, DRINK_WATER))) {
					player.getFoodData().eat(DRINK_FOOD, 0.0F);
				}
				server.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.8F, 1.1F);
			}
			player.getCooldowns().addCooldown(jug, COOLDOWN);
			return InteractionResult.SUCCESS;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("tooltip.jaymoskingdoms.endless_jug").withStyle(ChatFormatting.GRAY));
		}
	}

	private static void pour(ServerLevel level, BlockPos at) {
		level.playSound(null, at, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8F, 1.2F);
		level.sendParticles(ParticleTypes.SPLASH, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 14, 0.3, 0.2, 0.3, 0.1);
	}
}
