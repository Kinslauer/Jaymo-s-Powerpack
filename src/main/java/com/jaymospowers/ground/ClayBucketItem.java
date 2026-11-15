package com.jaymospowers.ground;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.cow.AbstractCow;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * The fired clay bucket ({@link StoneAge}), empty or full of water: it scoops water and milks a cow or goat like a bucket,
 * and every time it's emptied (poured, drunk from, used in the grid) it wears a little, until it cracks
 * ({@link StoneAge#CLAY_BUCKET_USES}). Dipped in lava it shatters.
 */
public class ClayBucketItem extends BucketItem {
	public ClayBucketItem(net.minecraft.world.level.material.Fluid content, Properties properties) {
		super(content, properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);
		BlockHitResult hit = getPlayerPOVHitResult(level, player, content == Fluids.EMPTY ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		Direction side = hit.getDirection();
		if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos.relative(side), side, held)) {
			return InteractionResult.FAIL;
		}
		BlockState state = level.getBlockState(pos);
		if (content == Fluids.EMPTY) {
			FluidState fluid = level.getFluidState(pos);
			if (fluid.is(FluidTags.LAVA)) {
				shatter(level, player, pos);
				if (!player.hasInfiniteMaterials()) {
					held.shrink(1);
				}
				return InteractionResult.SUCCESS;
			}
			if (!fluid.is(FluidTags.WATER) || !fluid.isSource() || !(state.getBlock() instanceof BucketPickup pickup)
				|| pickup.pickupBlock(player, level, pos, state).isEmpty()) {
				return InteractionResult.PASS;
			}
			player.awardStat(Stats.ITEM_USED.get(this));
			pickup.getPickupSound().ifPresent(sound -> player.playSound(sound, 1.0F, 1.0F));
			level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
			return InteractionResult.SUCCESS.heldItemTransformedTo(
				ItemUtils.createFilledResult(held, player, held.transmuteCopy(StoneAge.CLAY_WATER_BUCKET, 1)));
		}
		BlockPos place = state.getBlock() instanceof LiquidBlockContainer ? pos : pos.relative(side);
		if (!emptyContents(player, level, place, hit)) {
			return InteractionResult.FAIL;
		}
		player.awardStat(Stats.ITEM_USED.get(this));
		return InteractionResult.SUCCESS.heldItemTransformedTo(player.hasInfiniteMaterials() ? held : emptied(held, level, player));
	}

	/** Milks a cow or goat into it, like a bucket. */
	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
		if (content != Fluids.EMPTY || target.isBaby() || !(target instanceof AbstractCow || target instanceof Goat)) {
			return InteractionResult.PASS;
		}
		player.playSound(target instanceof Goat ? SoundEvents.GOAT_MILK : SoundEvents.COW_MILK, 1.0F, 1.0F);
		player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, stack.transmuteCopy(StoneAge.CLAY_MILK_BUCKET, 1)));
		return InteractionResult.SUCCESS;
	}

	/** Poured into a recipe: it comes back empty and worn (or cracked away). */
	@Override
	public @Nullable ItemStackTemplate getCraftingRemainder(ItemStack stack) {
		if (content == Fluids.EMPTY) {
			return null;
		}
		ItemStack empty = worn(stack);
		return empty.isEmpty() ? null : ItemStackTemplate.fromNonEmptyStack(empty);
	}

	/** This bucket emptied: the empty clay bucket one use more worn, or nothing if that cracks it (with the sound). */
	static ItemStack emptied(ItemStack full, @Nullable Level level, @Nullable LivingEntity user) {
		ItemStack empty = worn(full);
		if (empty.isEmpty() && level != null && user != null) {
			level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.DECORATED_POT_BREAK, SoundSource.PLAYERS, 0.8F, 1.2F);
		}
		return empty;
	}

	private static ItemStack worn(ItemStack full) {
		ItemStack empty = full.transmuteCopy(StoneAge.CLAY_BUCKET, 1);
		empty.setDamageValue(empty.getDamageValue() + 1);
		return empty.getDamageValue() >= empty.getMaxDamage() ? ItemStack.EMPTY : empty;
	}

	private static void shatter(Level level, Player player, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.DECORATED_POT_SHATTER, SoundSource.PLAYERS, 1.0F, 1.0F);
		level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.4F);
		if (level instanceof ServerLevel server) {
			server.sendParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.2, 0.1, 0.2, 0.0);
		}
	}
}
