package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.449) Andy: "move some of these options to the interact menu" (Ask the way plus the action options). What used to
 * be talk-menu answers that do something rather than say something are buttons on MCA's Interact page now, each shown
 * only when it applies: seek an audience (a ruler, or a realm's guard who walks you there, {@link Audiences}), a
 * pedlar's wares and tribute to a tribe ({@link Pedlars}, {@link Tribes}), and a sworn captain sent spying or raiding
 * ({@link SwornCaptains}). Ask the Way ({@link Directions}) has its own page.
 * <p>Which apply is worked out here as you right-click a villager and sent ahead of MCA's screen ({@link Offered}); the
 * client hides the rest ({@code McaActionButtonsMixin}). A button pressed comes back as MCA command {@code act.<action>}
 * ({@code McaCommandMixin}), checked again here before it's done.
 */
public final class InteractActions {
	/** MCA's command prefix, and the actions in the order their buttons stand. */
	public static final String COMMAND = "act.";
	public static final String AUDIENCE = "audience", WARES = "wares", TRIBUTE = "tribute", SPY = "spy", RAID = "raid";
	public static final List<String> ALL = List.of(AUDIENCE, WARES, TRIBUTE, SPY, RAID);

	/** Which of {@link #ALL} a villager ({@code entityId}) offers the player now. */
	public record Offered(int entityId, List<String> actions) implements CustomPacketPayload {
		public static final Type<Offered> TYPE = new Type<>(JaymosKingdoms.id("interact_actions"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Offered> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Offered::entityId,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Offered::actions,
			Offered::new);

		@Override
		public Type<Offered> type() {
			return TYPE;
		}
	}

	private InteractActions() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Offered.TYPE, Offered.CODEC);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && hand == InteractionHand.MAIN_HAND && McaCompat.LOADED && entity instanceof Villager v
				&& player instanceof ServerPlayer sp && ServerPlayNetworking.canSend(sp, Offered.TYPE)) {
				ServerPlayNetworking.send(sp, new Offered(v.getId(), offered(sp, v)));
			}
			return InteractionResult.PASS;
		});
	}

	/** What {@code v} offers {@code player} on the Interact page. */
	static List<String> offered(ServerPlayer player, Villager v) {
		List<String> out = new ArrayList<>();
		for (String a : ALL) {
			if (offers(player, v, a)) {
				out.add(a);
			}
		}
		return out;
	}

	static boolean offers(ServerPlayer player, LivingEntity v, String action) {
		return switch (action) {
			case AUDIENCE -> Rulers.is(v) || Audiences.canAsk(v);
			case WARES -> Pedlars.pedlar(v);
			case TRIBUTE -> Tribes.takesTribute(player, v);
			case SPY, RAID -> SwornCaptains.takesOrders(player, v);
			default -> false;
		};
	}

	/**
	 * {@code player} pressed {@code action} on {@code v}'s Interact page: done if it still applies. What they say goes back
	 * (for MCA's chat), or null if they've nothing to say (or it no longer applies).
	 */
	public static @Nullable Component act(ServerPlayer player, PathfinderMob v, String action) {
		if (!offers(player, v, action)) {
			return null;
		}
		switch (action) {
			case AUDIENCE -> {
				if (Rulers.is(v)) {
					Rulers.audience(player, v);
				} else {
					Audiences.request(player, v);
				}
				return null;
			}
			case WARES -> {
				Pedlars.trade(player, v);
				return null;
			}
			case TRIBUTE -> {
				return Tribes.tribute(player, v);
			}
			case SPY -> {
				return SwornCaptains.order(player, v, SwornCaptains.SPY_ANSWER);
			}
			case RAID -> {
				return SwornCaptains.order(player, v, SwornCaptains.RAID_ANSWER);
			}
			default -> {
				return null;
			}
		}
	}
}
