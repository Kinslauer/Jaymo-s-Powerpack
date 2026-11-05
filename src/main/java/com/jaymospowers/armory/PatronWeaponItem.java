package com.jaymospowers.armory;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** (0.20.18) A patron's weapon ({@link PatronWeapons}): an on-hit ability, or one used with the right mouse button. */
public class PatronWeaponItem extends Item {
	private final PatronWeapons.Kind kind;

	public PatronWeaponItem(Item.Properties properties, PatronWeapons.Kind kind) {
		super(properties);
		this.kind = kind;
	}

	public PatronWeapons.Kind kind() {
		return this.kind;
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		super.postHurtEnemy(stack, target, attacker);
		if (!(attacker.level() instanceof ServerLevel level) || !target.isAlive() && this.kind != PatronWeapons.Kind.GRAVELIGHT) {
			return;
		}
		float s = PatronWeapons.sworn(attacker, this.kind);
		if (s < 0.0F) {
			return;
		}
		boolean sworn = s > 0.0F;
		switch (this.kind) {
			case HEARTHGUARD -> PatronWeaponHits.kindled(level, attacker, sworn);
			case ANVIL_HAMMER -> PatronWeaponHits.sunder(level, target, sworn);
			case HUNTSMANS_SPEAR -> PatronWeaponHits.mark(level, target, attacker, sworn);
			case GRAVELIGHT -> PatronWeaponHits.soulFlame(level, target, attacker, sworn);
			case PLAIN_WORD -> PatronWeaponHits.unmask(level, target, sworn);
			default -> {
				// the others are used, not struck with
			}
		}
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		boolean active = this.kind == PatronWeapons.Kind.TIDECALLER || this.kind == PatronWeapons.Kind.SUNBRAND || this.kind == PatronWeapons.Kind.ASHFANG;
		float s = PatronWeapons.sworn(player, this.kind);
		if (!active || hand != InteractionHand.MAIN_HAND || s < 0.0F || player.getCooldowns().isOnCooldown(stack) || player.isPassenger()) {
			return InteractionResult.PASS;
		}
		boolean sworn = s > 0.0F;
		switch (this.kind) {
			case TIDECALLER -> {
				if (level instanceof ServerLevel server) {
					PatronWeaponHits.undertow(server, player, sworn);
				}
				player.getCooldowns().addCooldown(stack, sworn ? PatronWeapons.UNDERTOW_COOLDOWN_SWORN : PatronWeapons.UNDERTOW_COOLDOWN);
			}
			case SUNBRAND -> {
				if (!(level instanceof ServerLevel server)) {
					return InteractionResult.PASS;
				}
				if (!PatronWeaponHits.judge(server, player, sworn)) {
					return InteractionResult.PASS;
				}
				player.getCooldowns().addCooldown(stack, sworn ? PatronWeapons.SUN_COOLDOWN_SWORN : PatronWeapons.SUN_COOLDOWN);
			}
			default -> {
				// Smokestep: the leap on both sides so the client doesn't wait
				Vec3 look = player.getLookAngle();
				Vec3 flat = new Vec3(look.x, 0.0, look.z);
				flat = flat.lengthSqr() > 1.0E-4 ? flat.normalize() : Vec3.directionFromRotation(0.0F, player.getYRot());
				player.setDeltaMovement(-flat.x * PatronWeapons.SMOKE_BACK, PatronWeapons.SMOKE_UP, -flat.z * PatronWeapons.SMOKE_BACK);
				if (level instanceof ServerLevel server) {
					player.syncVelocity = true;
					PatronWeaponHits.smokestep(server, player, sworn);
				}
				player.getCooldowns().addCooldown(stack, sworn ? PatronWeapons.SMOKE_COOLDOWN_SWORN : PatronWeapons.SMOKE_COOLDOWN);
			}
		}
		if (level instanceof ServerLevel) {
			player.swing(hand, stack.getAttackAnimation(), true);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F, 1.4F);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		String base = "item.jaymospowers.patron_weapon." + this.kind.id;
		builder.accept(Component.translatable(base).withStyle(ChatFormatting.GRAY));
		Player viewer = PatronWeapons.VIEWER.get();
		if (viewer != null && PatronWeapons.sworn(viewer, this.kind) < 0.0F) {
			builder.accept(Component.translatable("item.jaymospowers.patron_weapon.cold").withStyle(ChatFormatting.RED));
		} else {
			builder.accept(Component.translatable(base + ".sworn").withStyle(ChatFormatting.DARK_AQUA));
		}
	}
}
