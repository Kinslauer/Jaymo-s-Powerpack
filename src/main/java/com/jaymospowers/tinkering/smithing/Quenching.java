package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.tinkering.smeltery.Molten;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (Smithing) Quenching. A freshly hammered head is hot ({@link Smithing#HEATED_AT}) and the Tool Station won't take it:
 * dip it. Use (right-click) a hot item on a water cauldron (it loses a level) or on a water source block and it
 * hisses and goes cold; a Hot Ingot becomes a plain ingot. (0.18.55) A far world's ice-glass or a Cinder Ember lying in the
 * cauldron gives the piece a Cold or Hot Edge ({@link Edges}). Our own twist: a graded head left to cool in air instead
 * ({@link HotMetal#COOLERS}) comes out a grade lower.
 */
public final class Quenching {
	private Quenching() {
	}

	static void register() {
		Grades.STATION_REFUSALS.add(stack -> stack.has(Smithing.HEATED_AT) ? Component.translatable("message.jaymospowers.smithing.quench_first") : null);
		HotMetal.COOLERS.add(Quenching::airCooled);
		UseBlockCallback.EVENT.register(Quenching::onBlock);
		UseItemCallback.EVENT.register(Quenching::onItem);
	}

	/** A graded head that went cold in the open air: one grade lower. */
	private static @Nullable ItemStack airCooled(ItemStack stack, ServerLevel level) {
		Grade grade = Grades.of(stack);
		if (grade == null) {
			return null;
		}
		ItemStack out = stack.copy();
		out.remove(Smithing.HEATED_AT);
		Grades.set(out, grade.lower());
		return out;
	}

	/** This hot stack dipped: a Hot Ingot back to its ingot, anything else without its heat and grade untouched. */
	public static ItemStack quenched(ItemStack stack) {
		if (stack.is(Smithing.HOT_INGOT)) {
			Molten molten = HotMetal.metal(stack);
			if (molten != null) {
				ItemStack ingot = HotMetal.ingot(molten, stack.getCount());
				if (!ingot.isEmpty()) {
					return ingot;
				}
			}
		}
		ItemStack out = stack.copy();
		out.remove(Smithing.HEATED_AT);
		return out;
	}

	private static InteractionResult onBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		ItemStack stack = player.getItemInHand(hand);
		if (!stack.has(Smithing.HEATED_AT)) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		boolean cauldron = state.is(Blocks.WATER_CAULDRON);
		if (!cauldron && !isWaterSource(state)) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server) {
			// (0.18.55) a far world's ice-glass or a Cinder Ember lying in the cauldron gives the piece an edge
			dip(server, player, hand, stack, pos, cauldron ? Edges.take(server, pos, stack) : null);
			if (cauldron) {
				LayeredCauldronBlock.lowerFillLevel(state, level, pos);
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** Use in the air while looking at a water source (a click on water has no block to use). */
	private static InteractionResult onItem(Player player, Level level, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!stack.has(Smithing.HEATED_AT)) {
			return InteractionResult.PASS;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(player.blockInteractionRange()));
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));
		if (hit.getType() != HitResult.Type.BLOCK || !isWaterSource(level.getBlockState(hit.getBlockPos()))) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server) {
			dip(server, player, hand, stack, hit.getBlockPos(), null);
		}
		return InteractionResult.SUCCESS;
	}

	private static boolean isWaterSource(BlockState state) {
		return state.getFluidState().is(FluidTags.WATER) && state.getFluidState().isSource();
	}

	private static void dip(ServerLevel level, Player player, InteractionHand hand, ItemStack stack, BlockPos pos, @Nullable String edge) {
		player.setItemInHand(hand, Edges.mark(quenched(stack), edge));
		level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8F, 1.0F + level.getRandom().nextFloat() * 0.2F);
		level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 12, 0.25, 0.1, 0.25, 0.05);
	}
}
