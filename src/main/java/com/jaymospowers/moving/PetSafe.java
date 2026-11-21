package com.jaymospowers.moving;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Don't hit your own pets: your melee, and the sweep of a weapon that hits the ones beside the target, pass over a
 * tame animal (a dog, cat, parrot, a horse tamed by you) that is yours. Sneak and the blow lands.
 */
public final class PetSafe {
	private PetSafe() {
	}

	public static void init() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> spared(player, entity) ? InteractionResult.FAIL : InteractionResult.PASS);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(source.is(DamageTypes.PLAYER_ATTACK) && source.getEntity() instanceof Player player && spared(player, entity)));
	}

	/** {@code target} is a tame animal of {@code player}'s and they aren't sneaking. */
	public static boolean spared(Player player, Entity target) {
		return !player.isShiftKeyDown() && target instanceof OwnableEntity pet && pet.getOwner() == player;
	}
}
