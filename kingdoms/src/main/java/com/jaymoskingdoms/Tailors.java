package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Items;

/**
 * (0.7.53) Andy: "only possible at a tailor villager, and for varying price". A shepherd (the villager at the loom) is
 * the tailor: MCA's Change Clothes button on them names their fee and opens MCA's editor on its clothing page only
 * (the client's {@code WardrobeScreen}); the fee is paid in emeralds (bundles count) when an outfit is picked. Each
 * tailor has their own rate, friends pay less, people they dislike more, and family half.
 */
public final class Tailors {
	/** The cheapest tailor's rate, and how much dearer the dearest is (by the tailor's UUID). */
	static final int BASE_FEE = 3, FEE_SPREAD = 5;
	/** One emerald off per this many hearts with the tailor (on per this many below zero), and the most off / on. */
	static final int HEARTS_PER_EMERALD = 25, MOST_OFF = 3, MOST_ON = 2;
	/** How long a fitting stays open for its pick to be paid, in ticks (5 minutes). */
	static final int FITTING_TICKS = 20 * 60 * 5;

	/** A fitting: the tailor, the fee, when it was opened (game time). */
	private record Fitting(UUID tailor, int fee, long opened) {
	}

	private static final Map<UUID, Fitting> FITTINGS = new HashMap<>();

	/** Server → client: open the wardrobe for {@code fee} emeralds. */
	public record Open(int fee) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(JaymosKingdoms.id("wardrobe_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Open::fee, Open::new);

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	/** Client → server: an outfit was picked in the wardrobe; pay the tailor. */
	public record Picked() implements CustomPacketPayload {
		public static final Type<Picked> TYPE = new Type<>(JaymosKingdoms.id("wardrobe_picked"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Picked> CODEC = StreamCodec.unit(new Picked());

		@Override
		public Type<Picked> type() {
			return TYPE;
		}
	}

	private Tailors() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Picked.TYPE, Picked.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Picked.TYPE, (payload, context) -> pay(context.player()));
	}

	/** Whether {@code villager} tailors: a grown shepherd. */
	public static boolean is(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().profession().is(VillagerProfession.SHEPHERD);
	}

	/** What {@code tailor} charges {@code player} for an outfit. */
	static int fee(Villager tailor, ServerPlayer player) {
		int fee = BASE_FEE + Math.floorMod(tailor.getUUID().hashCode(), FEE_SPREAD);
		int hearts = McaBridge.heartsOf(tailor, player);
		fee -= Mth.clamp(hearts / HEARTS_PER_EMERALD, -MOST_ON, MOST_OFF);
		if (McaBridge.family(tailor, player)) {
			fee /= 2;
		}
		return Math.max(1, fee);
	}

	/** MCA's Change Clothes button on {@code villager}: a tailor names their fee and opens the wardrobe. */
	public static void ask(ServerPlayer player, Villager villager) {
		ServerLevel level = (ServerLevel)villager.level();
		if (!is(villager)) {
			Speech.tell(level, villager, player, Component.translatable("tailor.jaymoskingdoms.not"), Speech.WHITE);
			return;
		}
		int fee = fee(villager, player);
		if (!player.isCreative() && Purse.count(player, Items.EMERALD) < fee) {
			Speech.tell(level, villager, player, Component.translatable("tailor.jaymoskingdoms.cant_pay", fee), Speech.WHITE);
			return;
		}
		if (!ServerPlayNetworking.canSend(player, Open.TYPE)) {
			return;
		}
		FITTINGS.put(player.getUUID(), new Fitting(villager.getUUID(), fee, level.getGameTime()));
		Speech.tell(level, villager, player, Component.translatable("tailor.jaymoskingdoms.fee", fee), Speech.WHITE);
		ServerPlayNetworking.send(player, new Open(fee));
	}

	/** An outfit was picked: the fee goes to the tailor's till. */
	private static void pay(ServerPlayer player) {
		Fitting fitting = FITTINGS.remove(player.getUUID());
		ServerLevel level = player.level();
		if (fitting == null || level.getGameTime() - fitting.opened() > FITTING_TICKS) {
			return;
		}
		if (!player.isCreative()) {
			Purse.take(player, Items.EMERALD, Math.min(fitting.fee(), Purse.count(player, Items.EMERALD)));
		}
		Entity tailor = level.getEntity(fitting.tailor());
		if (tailor != null) {
			Speech.tell(level, tailor, player, Component.translatable("tailor.jaymoskingdoms.done"), Speech.WHITE);
		}
	}
}
