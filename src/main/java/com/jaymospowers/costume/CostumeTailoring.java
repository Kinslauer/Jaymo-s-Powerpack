package com.jaymospowers.costume;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.KingdomsMoney;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.11.78) A costume sewn by a tailor (Andy: "village tailors ... can make a costume for you for emeralds, so you don't
 * need the materials"). Jaymo's Kingdoms names the price (it knows the tailor's rate and your hearts with them) and runs
 * {@code /costume tailor <player> <price> [<tailor>]} as the server; that opens the designer in its paid mode
 * ({@link Open}) and keeps an order here. Sewing it ({@link Sew}) is checked against the order: still open
 * ({@link #ORDER_TICKS}), the tailor still alive and near ({@link #TAILOR_REACH}), the design one you may sew
 * ({@link CostumeSets}), and the money you carry ((0.20.0) coins, purses and bundles, through {@link KingdomsMoney}, which
 * falls back to emeralds one for one without Jaymo's Kingdoms; free in creative). One costume per order. The price is in
 * emeralds' worth, which is paid in the coin of the land.
 */
public final class CostumeTailoring {
	/** How long an order stays open (ticks; 5 minutes). */
	static final int ORDER_TICKS = 20 * 60 * 5;
	/** How near the tailor must still be when it's sewn (blocks). */
	static final double TAILOR_REACH = 8.0;
	/** The most an order can cost (the command's bound). */
	static final int MAX_PRICE = 576;

	/** An order: its price, the tailor (null: none to stay near), when it was opened (the overworld's game time). */
	private record Order(int price, @Nullable UUID tailor, long opened) {
	}

	private static final Map<UUID, Order> ORDERS = new ConcurrentHashMap<>();

	/**
	 * Server → client: open the designer for a tailor to sew, at {@code price} emeralds' worth (paid in coin). (0.20.0) The cost line is
	 * made here, as the client can't count a purse or know the coin of the land: {@code cost} is the price in that coin ("20 Realm Silver"),
	 * {@code coin} one of it to draw beside, and {@code affordable} whether {@link KingdomsMoney#charge} would take it right now.
	 */
	public record Open(int price, Component cost, ItemStack coin, boolean affordable) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(JaymosPowers.id("costume_tailor_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Open::price,
			ComponentSerialization.TRUSTED_STREAM_CODEC, Open::cost, ItemStack.STREAM_CODEC, Open::coin, ByteBufCodecs.BOOL, Open::affordable, Open::new);

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	/** Client → server: have the tailor sew this design. */
	public record Sew(CostumeDesign design) implements CustomPacketPayload {
		public static final Type<Sew> TYPE = new Type<>(JaymosPowers.id("costume_tailor_sew"));
		public static final StreamCodec<ByteBuf, Sew> CODEC = CostumeDesign.STREAM_CODEC.map(Sew::new, Sew::design);

		@Override
		public Type<Sew> type() {
			return TYPE;
		}
	}

	private CostumeTailoring() {
	}

	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Sew.TYPE, Sew.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Sew.TYPE, (payload, ctx) -> sew(ctx.player(), payload.design()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ORDERS.remove(handler.player.getUUID()));
		// (merged into Costume's own /costume by Brigadier)
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(Commands.literal("costume")
			.then(Commands.literal("tailor").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("player", EntityArgument.player())
					.then(Commands.argument("price", IntegerArgumentType.integer(0, MAX_PRICE))
						.executes(c -> order(c, null))
						.then(Commands.argument("tailor", EntityArgument.entity())
							.executes(c -> order(c, EntityArgument.getEntity(c, "tailor")))))))));
	}

	private static int order(CommandContext<CommandSourceStack> c, @Nullable Entity tailor) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(c, "player");
		int price = IntegerArgumentType.getInteger(c, "price");
		if (!ServerPlayNetworking.canSend(player, Open.TYPE)) {
			c.getSource().sendFailure(Component.translatable("message.jaymospowers.costume.tailor.cannot_open"));
			return 0;
		}
		ORDERS.put(player.getUUID(), new Order(price, tailor != null ? tailor.getUUID() : null, player.level().getGameTime()));
		// (0.20.0) the price in the coin of the land, with the answer to "can they pay it" from the same rule the charge uses
		long milli = KingdomsMoney.worth(price);
		ServerPlayNetworking.send(player, new Open(price, KingdomsMoney.text(player, milli), KingdomsMoney.icon(player), KingdomsMoney.canPay(player, milli)));
		return 1;
	}

	private static void sew(ServerPlayer player, CostumeDesign raw) {
		Order order = ORDERS.get(player.getUUID());
		if (order == null || player.level().getGameTime() - order.opened() > ORDER_TICKS) {
			ORDERS.remove(player.getUUID());
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.costume.tailor.expired"));
			return;
		}
		if (order.tailor() != null) {
			Entity tailor = player.level().getEntity(order.tailor());
			if (tailor == null || !tailor.isAlive() || player.distanceToSqr(tailor) > TAILOR_REACH * TAILOR_REACH) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.costume.tailor.too_far"));
				return;
			}
		}
		CostumeDesign design = raw.sanitized();
		if (!CostumeSets.allowed(player, design)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.costume.not_your_set"));
			return;
		}
		long price = KingdomsMoney.worth(order.price());
		if (!KingdomsMoney.charge(player, price)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.costume.tailor.cant_pay", KingdomsMoney.text(player, price)));
			return;
		}
		ORDERS.remove(player.getUUID());
		player.getInventory().placeItemBackInInventory(CostumeItem.of(design), Prediction.SERVER_ONLY);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_LOOM_TAKE_RESULT, SoundSource.PLAYERS, 1.0F, 1.0F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.costume.tailor.sewn", CostumeItem.label(design), KingdomsMoney.text(player, price)));
	}
}
