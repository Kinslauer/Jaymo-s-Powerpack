package com.jaymospowers.costume;

import com.jaymospowers.power.smoke.SmokeVapour;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * Costumes on armour stands (Andy's Powerborne picks): sneak and right-click an armour stand with an empty hand and your
 * worn costume and the stand's change places: a stand with nothing on takes yours, a bare player takes the stand's, and two
 * costumes trade. The stand keeps the whole design ({@link Costume#STAND}: cut, colours, emblem, kit and its plates) in its
 * own save, and the client draws it as a body wearing it ({@code GiftedCostumeClient}). A broken stand drops its costume as
 * an item. Plates on a stand's costume give no armour (nothing wears them).
 */
public final class CostumeStand {
	private CostumeStand() {
	}

	static void init() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(entity instanceof ArmorStand stand) || !player.isShiftKeyDown() || player.isSpectator()
				|| !player.getMainHandItem().isEmpty()) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer server && !swap(server, stand)) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.SUCCESS;
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof ArmorStand stand && stand.getRemovalReason() == Entity.RemovalReason.KILLED) {
				CostumeDesign design = stand.getAttached(Costume.STAND);
				if (design != null) {
					stand.spawnAtLocation(level, CostumeItem.of(design));
				}
			}
		});
	}

	/** The player's worn costume and the stand's change places (server); false if neither has one. */
	static boolean swap(ServerPlayer player, ArmorStand stand) {
		CostumeDesign mine = Costume.worn(player);
		CostumeDesign theirs = stand.getAttached(Costume.STAND);
		if (mine == null && theirs == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.costume.stand_none"));
			return false;
		}
		if (SmokeVapour.active(player)) {
			SmokeVapour.refuseArmour(player);
			return false;
		}
		if (theirs != null) {
			player.setAttached(Costume.WORN, theirs.sanitized());
		} else {
			player.removeAttached(Costume.WORN);
		}
		// a costume goes on with its mask on
		player.removeAttached(Costume.UNMASKED);
		if (mine != null) {
			stand.setAttached(Costume.STAND, mine);
		} else {
			stand.removeAttached(Costume.STAND);
		}
		SecretIdentity.update(player);
		player.level().playSound(null, stand.getX(), stand.getY(), stand.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0F, 1.0F);
		CostumeDesign now = theirs != null ? theirs : mine;
		player.sendOverlayMessage(Component.translatable(mine == null ? "message.jaymospowers.costume.stand_took"
			: theirs == null ? "message.jaymospowers.costume.stand_dressed" : "message.jaymospowers.costume.stand_swapped", CostumeItem.label(now)));
		return true;
	}
}
