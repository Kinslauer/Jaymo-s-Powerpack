package com.jaymoskingdoms;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The horn of breaking: sound it and the rock ahead shakes loose in a tunnel three blocks across and ten deep (not past sixty
 * blocks in all). It breaks no locked thing, no chest or furnace or any block with a body of its own, nothing harder than
 * stone, and nothing in another realm's land unless the one who blows it rules there.
 */
public final class BreakingHorn {
	/** How far the tunnel runs (blocks), the most blocks it takes, the hardest it breaks, and the wait (ticks). */
	static final int DEPTH = 10, MOST = 60, COOLDOWN = 400;
	static final float HARDEST = 5.0F;
	public static Item ITEM;

	private BreakingHorn() {
	}

	static void register() {
		ITEM = Gadgets.item("breaking_horn", Rarity.RARE, Horn::new);
	}

	private static boolean mayBreak(ServerLevel level, ServerPlayer player, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || state.getDestroySpeed(level, pos) < 0 || state.getDestroySpeed(level, pos) > HARDEST || state.hasBlockEntity()
			|| !state.getFluidState().isEmpty()) {
			return false;
		}
		Faction there = Factions.around(level.getServer(), level, pos, 0);
		return there == null || Crowns.rules(level.getServer(), there, player.getUUID());
	}

	static final class Horn extends Item {
		Horn(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.SUCCESS;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (!ItemBonds.works(sp, stack)) {
				return InteractionResult.PASS;
			}
			Direction dir = player.getDirection();
			BlockPos start = player.blockPosition().relative(dir);
			Direction side = dir.getClockWise();
			int broken = 0;
			for (int d = 0; d < DEPTH && broken < MOST; d++) {
				for (int w = -1; w <= 1 && broken < MOST; w++) {
					for (int h = 0; h <= 2 && broken < MOST; h++) {
						BlockPos pos = start.relative(dir, d).relative(side, w).above(h);
						if (mayBreak(server, sp, pos)) {
							server.destroyBlock(pos, true, player);
							broken++;
						}
					}
				}
			}
			Vec3 mouth = player.position().add(0, 1.5, 0).add(Vec3.atLowerCornerOf(dir.getUnitVec3i()).scale(1.2));
			Fx.ring(server, mouth, 1.5F, 0xC8B89A);
			Fx.beam(server, mouth, mouth.add(Vec3.atLowerCornerOf(dir.getUnitVec3i()).scale(DEPTH)), 0.5F, 0xC8B89A, 8);
			server.playSound(null, player.blockPosition(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(4).value(), SoundSource.PLAYERS, 2.0F, 0.7F);
			player.getCooldowns().addCooldown(stack, COOLDOWN);
			return InteractionResult.SUCCESS;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("tooltip.jaymoskingdoms.breaking_horn").withStyle(ChatFormatting.GRAY));
		}
	}
}
