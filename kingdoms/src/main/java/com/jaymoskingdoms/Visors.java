package com.jaymoskingdoms;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Raising and lowering a helm's visor ({@link HeraldryArmour}): the visor key flips it. Lowered, the wearer sees the
 * world through the sight (the client's dark slit) and a blow aimed at the head is likelier to glance off the plate;
 * raised, the view is whole and the face is open, so a head blow glances no more than off a bare head. A frogmouth's
 * face doesn't lift at all; a kettle hat and a morion have none to lift. The state rides on the helm itself
 * ({@link HeraldryArmour#VISOR_UP}), so everyone sees it up or down.
 * <p>
 * The glance is Jaymo's Powers' ({@code LockOnRegions}); {@link #glance} is handed to it by {@link PowersCompat#shareHarness}.
 */
public final class Visors {
	/** How much likelier a head blow is to glance off a face shut behind plate (a visor down, a frogmouth) than off an open one. */
	static final double SHUT_GLANCE = 1.6;
	/** The clank of a visor going up, and down. */
	static final float UP_PITCH = 1.5F, DOWN_PITCH = 1.1F;

	/** Player → server: flip my visor. */
	public record Flip() implements CustomPacketPayload {
		public static final Type<Flip> TYPE = new Type<>(JaymosKingdoms.id("visor_flip"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Flip> CODEC = StreamCodec.unit(new Flip());

		@Override
		public Type<Flip> type() {
			return TYPE;
		}
	}

	private Visors() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Flip.TYPE, Flip.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Flip.TYPE, (payload, context) -> context.server().execute(() -> {
			if (context.player().isAlive() && !context.player().isSpectator()) {
				flip(context.player());
			}
		}));
	}

	/** {@code player} raises a lowered visor or lowers a raised one; nothing happens in a helm without one. */
	static void flip(ServerPlayer player) {
		ItemStack helm = player.getItemBySlot(EquipmentSlot.HEAD);
		HeraldryArmour.Piece piece = HeraldryArmour.of(helm);
		if (piece == null || piece.face() != HeraldryArmour.Face.VISORED) {
			return;
		}
		boolean up = !helm.getOrDefault(HeraldryArmour.VISOR_UP, false);
		set(helm, up);
		player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.7F,
			up ? UP_PITCH : DOWN_PITCH);
	}

	/** Raises or lowers the visor of {@code helm} (a visored helm). */
	static void set(ItemStack helm, boolean up) {
		if (up) {
			helm.set(HeraldryArmour.VISOR_UP, true);
		} else {
			helm.remove(HeraldryArmour.VISOR_UP);
		}
	}

	/** What a head blow's glance chance on {@code victim} is multiplied by: more behind a shut face of plate. */
	public static double glance(LivingEntity victim) {
		return HeraldryArmour.face(victim) == HeraldryArmour.Face.OPEN ? 1.0 : SHUT_GLANCE;
	}
}
