package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/**
 * Hidden armour, for every player: small eye buttons beside the armour slots in the inventory hide that piece. A hidden
 * piece still protects (it stays worn); it just isn't drawn, for anyone. The choice is kept on the player (through
 * death) and synced to everyone, so every client draws you the same way.
 */
public final class HiddenArmor {
	/** On a player: bit {@code 1 << slot.getIndex()} set for each armour slot they hide (synced to everyone). */
	public static final AttachmentType<Integer> HIDDEN = AttachmentRegistry.create(JaymosPowers.id("hidden_armor"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/** The slots that can be hidden, top to bottom as the inventory shows them. */
	public static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** Client → server: show or hide the piece in this armour slot (an index into {@link #SLOTS}). */
	public record Toggle(int slot) implements CustomPacketPayload {
		public static final Type<Toggle> TYPE = new Type<>(JaymosPowers.id("hidden_armor_toggle"));
		public static final StreamCodec<ByteBuf, Toggle> CODEC = ByteBufCodecs.VAR_INT.map(Toggle::new, Toggle::slot);

		@Override
		public Type<Toggle> type() {
			return TYPE;
		}
	}

	private HiddenArmor() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Toggle.TYPE, Toggle.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Toggle.TYPE, (payload, ctx) -> toggle(ctx.player(), payload.slot()));
	}

	private static void toggle(ServerPlayer player, int index) {
		if (index < 0 || index >= SLOTS.length) {
			return;
		}
		int mask = player.getAttachedOrElse(HIDDEN, 0) ^ bit(SLOTS[index]);
		if (mask == 0) {
			player.removeAttached(HIDDEN);
		} else {
			player.setAttached(HIDDEN, mask);
		}
	}

	private static int bit(EquipmentSlot slot) {
		return 1 << slot.getIndex();
	}

	/** Whether this player hides the piece in {@code slot} (on either side). */
	public static boolean hidden(Player player, EquipmentSlot slot) {
		Integer mask = player.getAttached(HIDDEN);
		return mask != null && (mask & bit(slot)) != 0;
	}
}
