package com.jaymoskingdoms.beasts;

import com.jaymoskingdoms.Fx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A short-range flamethrower fuelled by bombardier glands ({@link LabyrinthCraft#BEETLE_GLAND}). Hold use to spray a
 * cone of boiling fire {@link #RANGE} blocks long; it burns one gland from your pack every {@link #BURN_TICKS} ticks and
 * dies away when you have none. It sets creatures alight but not blocks.
 */
public class GlandFlamer extends Item {
	/** The cone: how far (blocks), how wide (the least dot product with the way you face), what it does (damage a burst, seconds alight). */
	static final double RANGE = 5.0, WIDTH = 0.8;
	static final float DAMAGE = 2.0F;
	static final int ALIGHT = 4;
	/** A burst every {@link #EVERY} ticks; a gland lasts {@link #BURN_TICKS}. */
	static final int EVERY = 4, BURN_TICKS = 40;
	static final int FLAME = 0xFF8A20;

	public GlandFlamer(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!hasGland(player) && !player.isCreative()) {
			return InteractionResult.FAIL;
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.BOW;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public void onUseTick(Level level, LivingEntity user, ItemStack stack, int ticksRemaining) {
		if (!(level instanceof ServerLevel server) || !(user instanceof Player player)) {
			return;
		}
		int run = this.getUseDuration(stack, user) - ticksRemaining;
		if (run % BURN_TICKS == 0 && !player.isCreative() && !burnGland(player)) {
			player.releaseUsingItem();
			level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.4F);
			return;
		}
		if (run % EVERY != 0) {
			return;
		}
		Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(0.6)).add(0.0, -0.25, 0.0);
		Vec3 look = player.getLookAngle();
		Fx.wave(server, from, look, RANGE, 1.4F, FLAME, EVERY + 4);
		level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.5F, 1.5F);
		for (LivingEntity victim : server.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RANGE), e -> e != player && e.isAlive())) {
			Vec3 to = victim.getBoundingBox().getCenter().subtract(from);
			double distance = to.length();
			if (distance > RANGE || distance < 0.01 || to.scale(1.0 / distance).dot(look) < WIDTH || !player.hasLineOfSight(victim)) {
				continue;
			}
			victim.hurtServer(server, server.damageSources().playerAttack(player), DAMAGE);
			victim.setRemainingFireTicks(ALIGHT * 20);
		}
	}

	private static boolean hasGland(Player player) {
		return player.getInventory().contains(stack -> stack.is(LabyrinthCraft.BEETLE_GLAND));
	}

	/** Burns one gland from {@code player}'s pack; false if there is none. */
	private static boolean burnGland(Player player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(LabyrinthCraft.BEETLE_GLAND)) {
				stack.shrink(1);
				return true;
			}
		}
		return false;
	}
}
