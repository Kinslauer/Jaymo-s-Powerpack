package com.jaymospowers.armory;

import com.jaymospowers.gear.arrow.mixin.ArrowPierceInvoker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The heavy crossbow: a steel-stiff prod no arm can span, wound back with a crank. A vanilla crossbow underneath (it
 * loads a real arrow from the pack by holding use, holds it, and looses it at the next use), but the winding takes
 * {@link #WIND} times as long, clicking round the ratchet as it goes, and what it looses flies {@link #POWER} times as
 * fast (so hits that much harder, and drops less), spreads {@link #SPREAD} as much, and goes on through the first body
 * it strikes. Quick Charge still shortens the winding.
 */
public class HeavyCrossbowItem extends CrossbowItem {
	/** How many times a crossbow's loading time the winding takes (a crossbow's is 25 ticks). */
	static final float WIND = 3.0F;
	/** Its bolt's speed over a crossbow's, and how much of a crossbow's spread is left. */
	static final float POWER = 1.6F, SPREAD = 0.4F;
	/** Extra bodies its bolt goes through. */
	static final byte PIERCE = 1;
	/** The ratchet clicks this often while winding (ticks). */
	static final int CLICK = 5;
	static final int DURABILITY = 520;

	public HeavyCrossbowItem(Properties properties) {
		super(properties);
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return Math.round(getChargeDuration(stack, user) * WIND) + 3;
	}

	/** Loading is judged on the time held, counted at the crank's pace (the vanilla judge uses its own, unscaled, charge time). */
	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remainingUseTicks) {
		int base = getUseDuration(stack, user);
		int held = (int) ((base - remainingUseTicks) / WIND);
		return super.releaseUsing(stack, level, user, base - held);
	}

	/** The crank: a click every {@link #CLICK} ticks, rising, and the pawl dropping home when it's spanned. */
	@Override
	public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remainingUseTicks) {
		if (!(level instanceof ServerLevel) || isCharged(stack)) {
			return;
		}
		int wound = Math.round(getChargeDuration(stack, user) * WIND);
		int held = getUseDuration(stack, user) - remainingUseTicks;
		if (held == wound) {
			level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
		} else if (held < wound && held % CLICK == 0) {
			level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.CHAIN_STEP, SoundSource.PLAYERS, 0.5F,
				0.9F + 0.6F * held / wound);
		}
	}

	@Override
	protected void shootProjectile(LivingEntity shooter, Projectile projectile, int index, float power, float uncertainty, float angle,
		@Nullable LivingEntity target) {
		if (projectile instanceof AbstractArrow bolt && bolt.getPierceLevel() < PIERCE) {
			((ArrowPierceInvoker) bolt).jaymospowers$setPierceLevel(PIERCE);
		}
		super.shootProjectile(shooter, projectile, index, power * POWER, uncertainty * SPREAD, angle, target);
	}
}
