package com.jaymoskingdoms;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The leech's kit's items ({@link LeechKit}). */
final class LeechKitItems {
	private LeechKitItems() {
	}

	/**
	 * A thorn filled with a draught. Hold use for {@link LeechKit#STICK_TICKS} to stick it in yourself (a dark one can't be: it
	 * only goes into another body or a corpse, by using it on them, {@link LeechKit}); the empty thorn comes back.
	 */
	static final class Thorn extends Item {
		final LeechKit.Kind kind;

		Thorn(Properties properties, LeechKit.Kind kind) {
			super(properties);
			this.kind = kind;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (this.kind.dark) {
				return InteractionResult.PASS;
			}
			player.startUsingItem(hand);
			return InteractionResult.CONSUME;
		}

		@Override
		public int getUseDuration(ItemStack stack, LivingEntity user) {
			return LeechKit.STICK_TICKS;
		}

		@Override
		public ItemUseAnimation getUseAnimation(ItemStack stack) {
			return ItemUseAnimation.BLOCK;
		}

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
			if (level instanceof ServerLevel server && user instanceof Player player) {
				if (LeechKit.stick(this.kind, player, player, server)) {
					LeechKit.spend(player, stack);
				} else if (player instanceof ServerPlayer sp) {
					sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.thorn.nothing"));
				}
			}
			return stack;
		}
	}

	/**
	 * A tube of bamboo that blows a sleep dart at what you look at ({@link LeechKit#BLOW_RANGE} blocks): a dart is a knockout
	 * draught ({@link LeechKit#KNOCK_TICKS}) in the body it hits. It uses a dart from your pack, and a shot is blown at once.
	 */
	static final class Blowgun extends Item {
		Blowgun(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack gun = player.getItemInHand(hand);
			ItemStack dart = dartOf(player);
			if (dart.isEmpty()) {
				if (player instanceof ServerPlayer sp) {
					sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.thorn.no_dart"));
				}
				return InteractionResult.FAIL;
			}
			if (!(level instanceof ServerLevel server)) {
				return InteractionResult.SUCCESS;
			}
			Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(0.6));
			HitResult hit = ProjectileUtil.getHitResultOnViewVector(player, e -> e instanceof LivingEntity l && l.isAlive() && !l.isSpectator() && e != player,
				LeechKit.BLOW_RANGE);
			Vec3 to = hit.getLocation();
			Fx.stroke(server, from, to, 0.04F, LeechKit.Kind.KNOCKOUT.color);
			server.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.4F, 2.0F);
			if (hit instanceof EntityHitResult entity && entity.getEntity() instanceof LivingEntity target) {
				LeechKit.knockOut(target, LeechKit.KNOCK_TICKS);
				Fx.burst(server, to.x, to.y, to.z, LeechKit.Kind.KNOCKOUT.color, 0.5F, 4, 0.2, 0.2, 0.2);
			}
			if (!player.hasInfiniteMaterials()) {
				dart.shrink(1);
				gun.hurtAndBreak(1, player, hand);
			}
			player.getCooldowns().addCooldown(gun, LeechKit.BLOW_COOLDOWN);
			return InteractionResult.SUCCESS;
		}

		/** The first sleep dart in hand or pack (empty if none). */
		private static ItemStack dartOf(Player player) {
			for (InteractionHand h : InteractionHand.values()) {
				if (player.getItemInHand(h).is(LeechKit.SLEEP_DART)) {
					return player.getItemInHand(h);
				}
			}
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				ItemStack s = player.getInventory().getItem(i);
				if (s.is(LeechKit.SLEEP_DART)) {
					return s;
				}
			}
			return ItemStack.EMPTY;
		}
	}
}
