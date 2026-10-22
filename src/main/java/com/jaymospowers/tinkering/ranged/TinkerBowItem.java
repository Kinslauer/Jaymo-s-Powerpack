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
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.x) The Tinkering longbow: a vanilla bow underneath (it draws, finds arrows, loses an arrow and wears a point as one
 * does), but its limbs set how fast it draws, how fast its arrows leave and how truly, and its materials its wear. It shoots
 * any arrow; a Tinkering arrow ({@link TinkerArrowItem}) adds its own velocity and accuracy.
 */
public class TinkerBowItem extends BowItem implements TinkerTool {
	private final ToolType type;

	public TinkerBowItem(Item.Properties properties, ToolType type) {
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

	/** A broken bow can't be drawn until it's mended. */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		return ToolStats.isBroken(player.getItemInHand(hand)) ? InteractionResult.FAIL : super.use(level, player, hand);
	}

	/** Draw speed: the time it was held counts for more (or less), as a quicker (or slower) draw. */
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
