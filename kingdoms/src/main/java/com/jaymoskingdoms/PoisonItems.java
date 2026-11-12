package com.jaymoskingdoms;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.phys.Vec3;

/**
 * The poison items ({@link Poisons}): the vials, the antidote and the thrown gas bottle.
 */
final class PoisonItems {
	/** How hard a gas bottle is thrown, how wild, and the wait before the next (ticks). */
	static final float POWER = 0.9F, SPREAD = 1.0F;
	static final int THROW_COOLDOWN = 20;
	/** The antidote's drinking time (ticks). */
	static final int DRINK_TICKS = 32;

	private PoisonItems() {
	}

	/**
	 * A vial of one poison. Hold it in a hand and the thing to poison in the other, and use it: a weapon is coated (three
	 * blows), food or a drink is dosed (one of the stack), a glass bottle becomes a gas bottle. Used on a door, it smears the
	 * handle. Nothing else happens: a vial is never drunk.
	 */
	static final class Vial extends Item {
		final Poisons.Kind kind;

		Vial(Properties properties, Poisons.Kind kind) {
			super(properties);
			this.kind = kind;
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			if (!(context.getLevel() instanceof ServerLevel level) || !(context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof DoorBlock)
				|| context.getPlayer() == null) {
				return InteractionResult.PASS;
			}
			if (Poisons.smear(level, context.getClickedPos(), this.kind, context.getPlayer())) {
				spend(level, context.getPlayer(), context.getItemInHand(), Vec3.atCenterOf(context.getClickedPos()), this.kind, true);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
			ItemStack other = player.getItemInHand(otherHand);
			ItemStack vial = player.getItemInHand(hand);
			if (other.isEmpty()) {
				return InteractionResult.PASS;
			}
			boolean blade = Nets.blade(other) || other.has(DataComponents.WEAPON) && other.isDamageableItem();
			boolean dish = !blade && (other.has(DataComponents.FOOD) || other.has(DataComponents.CONSUMABLE)) && !(other.getItem() instanceof Vial)
				&& !(other.getItem() instanceof Antidote) && Poisons.KIND != null && other.get(Poisons.KIND) == null;
			boolean bottle = other.is(Items.GLASS_BOTTLE);
			if (!blade && !dish && !bottle) {
				return InteractionResult.PASS;
			}
			if (!(level instanceof ServerLevel server)) {
				return InteractionResult.SUCCESS;
			}
			if (blade) {
				other.set(Poisons.KIND, this.kind.name().toLowerCase(java.util.Locale.ROOT));
				other.set(Poisons.COAT, Poisons.COAT_HITS);
			} else if (dish) {
				ItemStack dosed = other.split(1);
				dosed.set(Poisons.KIND, this.kind.name().toLowerCase(java.util.Locale.ROOT));
				if (other.isEmpty()) {
					player.setItemInHand(otherHand, dosed);
				} else {
					player.getInventory().placeItemBackInInventory(dosed, net.minecraft.util.Prediction.SERVER_ONLY);
				}
			} else {
				ItemStack gas = new ItemStack(Poisons.GAS_BOTTLE);
				gas.set(Poisons.KIND, this.kind.name().toLowerCase(java.util.Locale.ROOT));
				other.shrink(1);
				player.getInventory().placeItemBackInInventory(gas, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			spend(server, player, vial, player.position().add(0.0, 1.0, 0.0), this.kind, !bottle);
			return InteractionResult.SUCCESS;
		}

		/** One vial is used up; the empty bottle comes back (unless it went into the gas). */
		private static void spend(ServerLevel level, Player player, ItemStack vial, Vec3 at, Poisons.Kind kind, boolean bottleBack) {
			vial.consume(1, player);
			if (bottleBack && !player.hasInfiniteMaterials()) {
				player.getInventory().placeItemBackInInventory(new ItemStack(Items.GLASS_BOTTLE), net.minecraft.util.Prediction.SERVER_ONLY);
			}
			level.playSound(null, at.x, at.y, at.z, SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.7F, 1.1F);
			Fx.burst(level, at.x, at.y, at.z, kind.color, 0.5F, 4, 0.15, 0.15, 0.15);
		}
	}

	/** The antidote: drunk, it cures every poison and the vanilla ones with it. */
	static final class Antidote extends Item {
		Antidote(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			player.startUsingItem(hand);
			return InteractionResult.CONSUME;
		}

		@Override
		public int getUseDuration(ItemStack stack, LivingEntity user) {
			return DRINK_TICKS;
		}

		@Override
		public ItemUseAnimation getUseAnimation(ItemStack stack) {
			return ItemUseAnimation.DRINK;
		}

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
			if (!level.isClientSide()) {
				Poisons.cure(user);
				level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.8F, 1.0F);
			}
			if (user instanceof Player player && !player.hasInfiniteMaterials()) {
				stack.shrink(1);
				if (stack.isEmpty()) {
					return new ItemStack(Items.GLASS_BOTTLE);
				}
				player.getInventory().placeItemBackInInventory(new ItemStack(Items.GLASS_BOTTLE), net.minecraft.util.Prediction.SERVER_ONLY);
			}
			return stack;
		}
	}

	/** A bottle of gas: use it to throw it; it breaks into a cloud of its poison ({@link GasBottle}). */
	static final class GasBottleItem extends Item {
		GasBottleItem(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack held = player.getItemInHand(hand);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS, 0.6F, 0.8F);
			if (level instanceof ServerLevel server) {
				Projectile.spawnProjectileFromRotation(GasBottle::new, server, held, player, -10.0F, POWER, SPREAD);
			}
			player.getCooldowns().addCooldown(held, THROW_COOLDOWN);
			held.consume(1, player);
			return InteractionResult.SUCCESS;
		}
	}
}
