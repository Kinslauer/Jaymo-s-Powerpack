package com.jaymospowers.tinkering.ranged;

import com.jaymospowers.tinkering.TinkerTool;
import com.jaymospowers.tinkering.ToolStats;
import com.jaymospowers.tinkering.ToolType;
import com.jaymospowers.tinkering.TraitEffects;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.x) The Tinkering crossbow: a vanilla crossbow underneath (loaded by holding right mouse, fired by the next click,
 * holds its bolt), with its limbs setting the loading speed, the bolt's velocity and accuracy. Quick Charge still works on top.
 */
public class TinkerCrossbowItem extends CrossbowItem implements TinkerTool {
	private final ToolType type;

	public TinkerCrossbowItem(Item.Properties properties, ToolType type) {
		super(properties);
		this.type = type;
	}

	@Override
	public ToolType type() {
		return type;
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		TinkerTool.tick(stack, level);
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity mob, LivingEntity attacker) {
		TraitEffects.afterHit(stack, mob, attacker);
	}

	/** A broken crossbow can't be loaded or fired until it's mended. */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		return ToolStats.isBroken(player.getItemInHand(hand)) ? InteractionResult.FAIL : super.use(level, player, hand);
	}

	/** How long loading takes: the vanilla time over the limbs' draw speed. */
	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return Math.round(getChargeDuration(stack, user) / Ranged.drawSpeed(stack)) + 3;
	}

	/** Loading is judged on the time held, counted at the draw speed (the vanilla judge uses its own, unscaled, charge time). */
	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remainingUseTicks) {
		int base = getUseDuration(stack, user);
		int held = Math.round((base - remainingUseTicks) * Ranged.drawSpeed(stack));
		return super.releaseUsing(stack, level, user, base - held);
	}

	@Override
	protected void shootProjectile(LivingEntity shooter, Projectile projectile, int index, float power, float uncertainty, float angle,
		@Nullable LivingEntity target) {
		ItemStack weapon = Ranged.heldWeapon(shooter, this);
		if (weapon.isEmpty()) {
			super.shootProjectile(shooter, projectile, index, power, uncertainty, angle, target);
			return;
		}
		super.shootProjectile(shooter, projectile, index, Ranged.power(weapon, projectile, power),
			Ranged.uncertainty(weapon, projectile, uncertainty), angle, target);
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
		TooltipFlag flag) {
		TinkerTool.tooltip(stack, type, builder);
	}
}
