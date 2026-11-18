package com.jaymospowers.rig;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;

/**
 * Evacuate the host: when the Core is near empty and a hit would take the wearer down, the suit throws them clear
 * and slumps down as a wreck they can repair at the Forge.
 */
public final class RigEvac {
	private RigEvac() {
	}

	/** Charge at or below which the suit considers itself near empty. */
	static final int NEAR_EMPTY = 40;
	/** How hard the suit throws the wearer upward (blocks per tick). */
	static final double KICK = 0.6;

	static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player) || !Rig.fullSet(player)) {
				return true;
			}
			ItemStack core = Rig.core(player);
			if (core.isEmpty() || Rig.charge(player) > NEAR_EMPTY) {
				return true;
			}
			if (amount >= player.getHealth()) {
				// the hit would take them down: eject instead
				evac(player);
				return false;
			}
			return true;
		});
	}

	/** The Rig menu's pick: eject the wearer now (manual override of the automatic save). */
	static void use(ServerPlayer player) {
		evac(player);
	}

	private static void evac(ServerPlayer player) {
		player.push(0.0, KICK, 0.0);
		player.syncVelocity = true;
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
			SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0,
			player.getZ(), 0, 0.0, 1.0, 0.0, 1.2);
		RigMind.sayAlways(player, "evac", 1.5F);
		// the suit slumps off as a wreck to repair at the Forge
		for (Rig.Piece piece : Rig.Piece.values()) {
			ItemStack worn = player.getItemBySlot(piece.slot);
			if (piece.item() != null && worn.is(piece.item()) && !worn.isEmpty()) {
				player.setItemSlot(piece.slot, ItemStack.EMPTY);
				player.drop(worn.copy(), false, Prediction.SERVER_ONLY);
			}
		}
		Rig.core(player).set(Rig.CHARGE, 0);
	}
}
