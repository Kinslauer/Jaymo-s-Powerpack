package com.jaymoskitchen.vintner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * What a treading tub or an apple press holds: up to {@link #LOAD} of one fruit, and the juice made of it, up to
 * {@link #MAX_BOTTLES} bottles. Each {@link #PER_BOTTLE} fruit make a bottle after {@link #STEPS} treads (the tub: land in
 * it from a jump) or turns (the press: use it with an empty hand). Tuning: the constants below.
 */
public class JuiceEntity extends BlockEntity {
	public static final int LOAD = 9, PER_BOTTLE = 3, MAX_BOTTLES = 3, STEPS = 4;

	private int fruit;
	private int bottles;
	private int work;
	private @Nullable Juice juice;

	public JuiceEntity(BlockPos pos, BlockState state) {
		super(Vintner.JUICE_ENTITY, pos, state);
	}

	private boolean tub() {
		return getBlockState().getBlock() instanceof JuiceBlock block && block.tub;
	}

	public InteractionResult use(Player player, InteractionHand hand, ItemStack held) {
		if (!(level instanceof ServerLevel)) {
			return InteractionResult.SUCCESS;
		}
		Juice fruitOf = Juice.ofFruit(held, tub());
		if (fruitOf != null) {
			if ((juice != null && juice != fruitOf && (fruit > 0 || bottles > 0)) || fruit >= LOAD) {
				return InteractionResult.SUCCESS_SERVER;
			}
			int take = Math.min(held.getCount(), LOAD - fruit);
			if (!player.getAbilities().instabuild) {
				held.shrink(take);
			}
			fruit += take;
			juice = fruitOf;
			play(SoundEvents.ITEM_PICKUP, 0.8F);
			markUpdated();
			return InteractionResult.SUCCESS_SERVER;
		}
		if (held.is(Items.GLASS_BOTTLE) && bottles > 0 && juice != null) {
			player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(juice.bottle())));
			bottles--;
			play(SoundEvents.BOTTLE_FILL, 1.0F);
			markUpdated();
			return InteractionResult.SUCCESS_SERVER;
		}
		if (held.isEmpty() && !tub()) {
			if (turn()) {
				return InteractionResult.SUCCESS_SERVER;
			}
		}
		return held.isEmpty() ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
	}

	/** A landing in the tub: one tread. */
	void tread(Player player) {
		if (!(level instanceof ServerLevel server) || fruit < PER_BOTTLE || bottles >= MAX_BOTTLES) {
			return;
		}
		server.sendParticles(ParticleTypes.SPLASH, worldPosition.getX() + 0.5, worldPosition.getY() + 0.6, worldPosition.getZ() + 0.5,
			6, 0.3, 0.05, 0.3, 0.1);
		play(SoundEvents.SLIME_SQUISH, 0.7F + server.getRandom().nextFloat() * 0.3F);
		step();
	}

	/** A turn of the press's screw; false if it has nothing to press. */
	private boolean turn() {
		if (!(level instanceof ServerLevel server) || fruit < PER_BOTTLE || bottles >= MAX_BOTTLES) {
			return false;
		}
		play(SoundEvents.HONEY_BLOCK_STEP, 0.6F);
		server.sendParticles(ParticleTypes.SPLASH, worldPosition.getX() + 0.5, worldPosition.getY() + 0.3, worldPosition.getZ() + 0.5,
			3, 0.2, 0.05, 0.2, 0.05);
		step();
		return true;
	}

	private void step() {
		work++;
		if (work >= STEPS) {
			work = 0;
			fruit -= PER_BOTTLE;
			bottles++;
			play(SoundEvents.BOTTLE_FILL, 0.6F);
		}
		markUpdated();
	}

	/** Broken: what it held spills. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level instanceof ServerLevel server && juice != null) {
			if (bottles > 0) {
				Block.popResource(server, pos, new ItemStack(juice.bottle(), bottles));
			}
			if (fruit > 0) {
				// back to the first fruit it can be (a mix of grape kinds comes out as the plain kind)
				ItemStack back = switch (juice) {
					case GRAPE -> new ItemStack(Vintner.GRAPES, fruit);
					case APPLE -> new ItemStack(Items.APPLE, fruit);
					case BLACK -> new ItemStack(Vintner.CRIMSON_GRAPES, fruit);
				};
				Block.popResource(server, pos, back);
			}
		}
	}

	private void play(SoundEvent sound, float pitch) {
		if (level != null) {
			level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, 1.0F, pitch);
		}
	}

	private void markUpdated() {
		setChanged();
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		fruit = input.getIntOr("Fruit", 0);
		bottles = input.getIntOr("Bottles", 0);
		work = input.getIntOr("Work", 0);
		juice = Juice.byName(input.getStringOr("Juice", ""));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("Fruit", fruit);
		output.putInt("Bottles", bottles);
		output.putInt("Work", work);
		output.putString("Juice", juice == null ? "" : juice.name());
	}
}
