package com.jaymospowers.tinkering.ranged;

import com.jaymospowers.tinkering.TinkerTool;
import com.jaymospowers.tinkering.ToolStats;
import com.jaymospowers.tinkering.ToolType;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.x) A Tinkering arrow (head, shaft, fletching), built several at a time at the Tool Station. Any bow, crossbow or
 * dispenser shoots it as a plain arrow that hits for what its head gives ({@link ToolStats#ranged}); the Tinkering bows
 * also take its shaft's velocity and its fletching's accuracy ({@link Ranged#power}).
 */
public class TinkerArrowItem extends ArrowItem implements TinkerTool {
	private final ToolType type;

	public TinkerArrowItem(Item.Properties properties, ToolType type) {
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
	public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter, @Nullable ItemStack weapon) {
		return withDamage(super.createArrow(level, ammo, shooter, weapon), ammo);
	}

	@Override
	public Projectile asProjectile(Level level, Position position, ItemStack stack, Direction direction) {
		Projectile projectile = super.asProjectile(level, position, stack, direction);
		return projectile instanceof AbstractArrow arrow ? withDamage(arrow, stack) : projectile;
	}

	private AbstractArrow withDamage(AbstractArrow arrow, ItemStack ammo) {
		arrow.setBaseDamage(ToolStats.ranged(type, ToolStats.materials(ammo)).damage());
		return arrow;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
		TooltipFlag flag) {
		TinkerTool.tooltip(stack, type, builder);
	}
}
