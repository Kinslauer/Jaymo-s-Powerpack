package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The Arm screen's one move (WebArmScreen): swap an inventory slot with one of the arm's slots, or ({@code inv} = -1)
 * send an arm slot's stack to the first free inventory slot. Checked here: the arm is unlocked and the player is up.
 */
public final class WebArmSwap {
	/** Client to server: swap inventory slot {@code inv} (0-35, hotbar first) with arm slot {@code arm}; {@code inv} -1 empties the arm slot into the inventory. */
	public record Swap(int inv, int arm) implements CustomPacketPayload {
		public static final Type<Swap> TYPE = new Type<>(JaymosPowers.id("web_symbiote_arm_swap"));
		public static final StreamCodec<ByteBuf, Swap> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Swap::inv,
			ByteBufCodecs.VAR_INT, Swap::arm,
			Swap::new);

		@Override
		public Type<Swap> type() {
			return TYPE;
		}
	}

	private WebArmSwap() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Swap.TYPE, Swap.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Swap.TYPE, (payload, ctx) -> swap(ctx.player(), payload.inv(), payload.arm()));
	}

	private static void swap(ServerPlayer player, int inv, int slot) {
		if (!player.isAlive() || player.isSpectator() || !WebSymbioteBody.armUnlocked(player) || WebSymbioteBond.dormant(player) || WebSymbiote.driving(player)
			|| slot < 0 || slot >= WebSymbioteBody.ARM_SLOTS || inv < -1 || inv >= Inventory.INVENTORY_SIZE) {
			return;
		}
		Inventory inventory = player.getInventory();
		WebSymbioteBody.Arm arm = WebSymbioteBody.arm(player);
		ItemStack held = arm.get(slot).copy();
		if (inv < 0) {
			int free = inventory.getFreeSlot();
			if (held.isEmpty() || free < 0 || free >= Inventory.INVENTORY_SIZE) {
				return;
			}
			inventory.setItem(free, held);
			arm = arm.with(slot, ItemStack.EMPTY);
		} else {
			ItemStack mine = inventory.getItem(inv).copy();
			inventory.setItem(inv, held);
			arm = arm.with(slot, mine);
		}
		if (arm.isEmpty() && arm.selected() == 0 && !arm.using()) {
			player.removeAttached(WebSymbioteBody.ARM);
		} else {
			player.setAttached(WebSymbioteBody.ARM, arm);
		}
		inventory.setChanged();
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.5F, 1.4F);
	}
}
