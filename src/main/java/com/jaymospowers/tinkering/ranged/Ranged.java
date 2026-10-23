package com.jaymospowers.tinkering.ranged;

import com.jaymospowers.armory.ThrownWeapon;
import com.jaymospowers.tinkering.TinkerTool;
import com.jaymospowers.tinkering.ToolStats;
import com.jaymospowers.tinkering.ToolType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * (0.15.x) What the ranged Tinkering tools share: throwing (the javelin held back like a trident, throwing axes and shuriken
 * flicked a stack at a time, all as the Armory's {@link ThrownWeapon}, which lies where it lands for its thrower to pick up),
 * and how a bow's and an arrow's materials change a shot.
 */
public final class Ranged {
	/** How long a javelin can be held drawn back (ticks). */
	public static final int HOLD_DURATION = 72000;
	/** What a thrown blade without stats of its own does. */
	static final float FALLBACK_DAMAGE = 2.0F;

	private Ranged() {
	}

	/** Right mouse with a javelin (draw it back), a throwing axe or a shuriken (throw one now). */
	public static InteractionResult use(Level level, Player player, InteractionHand hand, ToolType type) {
		ItemStack stack = player.getItemInHand(hand);
		if (type.style == ToolType.Style.JAVELIN) {
			player.startUsingItem(hand);
			return InteractionResult.CONSUME;
		}
		player.getCooldowns().addCooldown(stack, type.ranged.cooldown());
		if (level instanceof ServerLevel serverLevel) {
			throwFrom(serverLevel, player, stack.consumeAndReturn(1, player), type);
		}
		player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
		return InteractionResult.SUCCESS;
	}

	/** Letting go of a drawn javelin: thrown once it was held long enough. Returns whether it was. */
	public static boolean releaseJavelin(ItemStack stack, Level level, LivingEntity user, int held) {
		ToolType type = ((TinkerTool) stack.getItem()).type();
		if (!(user instanceof Player player) || held < type.ranged.cooldown() || ToolStats.isBroken(stack)) {
			return false;
		}
		if (level instanceof ServerLevel serverLevel) {
			stack.hurtAndBreak(1, player, user.getUsedItemHand());
			throwFrom(serverLevel, player, stack.consumeAndReturn(1, player), type);
		}
		player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
		return true;
	}

	private static void throwFrom(ServerLevel level, Player player, ItemStack thrown, ToolType type) {
		ToolStats.Ranged stats = ToolStats.ranged(type, ToolStats.materials(thrown));
		float speed = stats.throwSpeed() * stats.velocity();
		ThrownWeapon weapon = Projectile.spawnProjectileFromRotation(
			(l, owner, stack) -> new ThrownWeapon(l, owner, stack), level, thrown, player, 0.0F, speed, stats.spread());
		if (player.hasInfiniteMaterials()) {
			weapon.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
		}
		level.playSound(null, weapon, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.8F, type == ToolType.SHURIKEN ? 1.5F : 1.2F);
	}

	/** What a thrown Tinkering tool does when it hits (a ThrownWeapon asks). */
	public static float thrownDamage(ItemStack stack) {
		if (stack.getItem() instanceof TinkerTool tool) {
			return ToolStats.ranged(tool.type(), ToolStats.materials(stack)).damage();
		}
		return FALLBACK_DAMAGE;
	}

	/** Whether a thrown Tinkering tool tumbles end over end (throwing axes and shuriken; a javelin flies point first). */
	public static boolean spins(ItemStack stack) {
		return stack.getItem() instanceof TinkerTool tool && tool.type().style == ToolType.Style.THROWN;
	}

	/** The Tinkering arrow a loosed projectile is, or null (a vanilla arrow, a firework). */
	private static ToolStats.Ranged arrow(Projectile projectile) {
		if (projectile instanceof AbstractArrow arrow) {
			ItemStack ammo = arrow.getPickupItemStackOrigin();
			if (ammo.getItem() instanceof TinkerTool tool && tool.type().style == ToolType.Style.ARROW) {
				return ToolStats.ranged(tool.type(), ToolStats.materials(ammo));
			}
		}
		return null;
	}

	/** The power a bow's shot leaves with: the bow's velocity, and the arrow's own. */
	public static float power(ItemStack weapon, Projectile projectile, float power) {
		ToolStats.Ranged bow = ToolStats.ranged(((TinkerTool) weapon.getItem()).type(), ToolStats.materials(weapon));
		ToolStats.Ranged arrow = arrow(projectile);
		return power * bow.velocity() * (arrow == null ? 1.0F : arrow.velocity());
	}

	/** The spread of a bow's shot: the bow's accuracy, and the arrow's (feathers). */
	public static float uncertainty(ItemStack weapon, Projectile projectile, float uncertainty) {
		ToolStats.Ranged bow = ToolStats.ranged(((TinkerTool) weapon.getItem()).type(), ToolStats.materials(weapon));
		ToolStats.Ranged arrow = arrow(projectile);
		return uncertainty * bow.spread() * (arrow == null ? 1.0F : arrow.spread());
	}

	/** The stack of this bow or crossbow the shooter is drawing or holding (empty if none). */
	public static ItemStack heldWeapon(LivingEntity shooter, Item item) {
		for (ItemStack stack : new ItemStack[] {shooter.getUseItem(), shooter.getMainHandItem(), shooter.getOffhandItem()}) {
			if (stack.is(item)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	/** A bow's draw speed multiplier (1 is a plain bow's). */
	public static float drawSpeed(ItemStack weapon) {
		return ToolStats.ranged(((TinkerTool) weapon.getItem()).type(), ToolStats.materials(weapon)).drawSpeed();
	}
}
