package com.jaymospowers.combat;

import com.jaymospowers.armory.ThrownWeapon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;

/**
 * A hurled blade (Andy's pick from Star Wars: Galaxy in a Cube, ideas only): any sword, thrown tumbling end over end as
 * a {@link ThrownWeapon}. It strikes once and sticks where it lands for its thrower to fetch, as a thrown dagger does;
 * it flies back to the hand only for someone whose power can call it. Who that is, and when a blade may be hurled at
 * all, is the caller's to say (Kingdoms' Hurled Blade form, by name).
 */
public final class BladeThrow {
	private BladeThrow() {
	}

	/**
	 * {@code player} hurls {@code blade} (one item, already taken from the hand) the way they look at {@code speed}
	 * (blocks/tick; a dagger leaves the hand at about 1.6) for {@code damage}. {@code comesBack}: it returns to the hand.
	 */
	public static boolean hurl(ServerPlayer player, ItemStack blade, float damage, float speed, boolean comesBack) {
		if (blade.isEmpty() || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		ThrownWeapon thrown = Projectile.spawnProjectileFromRotation((l, owner, stack) -> new ThrownWeapon(l, owner, stack), level, blade, player,
			0.0F, speed, 1.0F);
		thrown.hurled(damage, comesBack);
		if (player.hasInfiniteMaterials()) {
			thrown.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
		}
		level.playSound(null, thrown, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.9F, 0.9F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7F, 0.8F);
		return true;
	}
}
