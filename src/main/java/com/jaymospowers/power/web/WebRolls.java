package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jspecify.annotations.Nullable;

/**
 * Combat Roll (optional, mod id {@code combat_roll}) for Web: up to {@link #ROLL_CHARGES} roll charges, and every
 * Spider-Sense counter ({@link WebCounter}) spends one; with none left, a dodge opens no counter. The same approach as
 * Darktide's {@code CombatRollCompat}, kept in Web's own package:
 * <ul>
 * <li>Charges: Combat Roll's own attribute {@code combat_roll:count}, looked up at runtime; a modifier makes it
 * {@link #ROLL_CHARGES} while Web is your power (checked every {@link #APPLY_EVERY} ticks), and comes off otherwise.
 * Combat Roll itself refills them one at a time, each on its own recharge (its config and its own recharge
 * attribute, untouched here), so a spent charge comes back on its own.</li>
 * <li>The count lives on the client (Combat Roll's {@code RollManager}): the client reports it ({@link Count}) and the
 * server spends one per counter through {@link Charge}. Until a count arrives (or without Combat Roll), counters cost
 * nothing.</li>
 * </ul>
 */
public final class WebRolls {
	// ===== the knobs ================================================================================================
	/** Combat Roll charges a web slinger holds. */
	public static final int ROLL_CHARGES = 5;
	/** How often (ticks) the charge count is put on or taken off. */
	static final int APPLY_EVERY = 20;
	// ================================================================================================================

	static final Identifier COUNT_ATTRIBUTE = Identifier.fromNamespaceAndPath("combat_roll", "count");
	private static final Identifier MODIFIER_ID = JaymosPowers.id("web_roll_charges");
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("combat_roll");
	private static @Nullable Holder<Attribute> count;
	private static boolean looked;
	/** Roll charges each player's client last said it has (missing: it hasn't said). Server thread only. */
	private static final Map<UUID, Integer> ROLLS = new HashMap<>();

	/** Client → server: how many Combat Roll charges the player has right now (sent whenever it changes). */
	public record Count(int available) implements CustomPacketPayload {
		public static final Type<Count> TYPE = new Type<>(JaymosPowers.id("web_rolls"));
		public static final StreamCodec<ByteBuf, Count> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Count::available, Count::new);

		@Override
		public Type<Count> type() {
			return TYPE;
		}
	}

	/** Server → client: a Combat Roll charge spent by a counter ({@code -1}). */
	public record Charge(int change) implements CustomPacketPayload {
		public static final Type<Charge> TYPE = new Type<>(JaymosPowers.id("web_roll_charge"));
		public static final StreamCodec<ByteBuf, Charge> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Charge::change, Charge::new);

		@Override
		public Type<Charge> type() {
			return TYPE;
		}
	}

	private WebRolls() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Count.TYPE, Count.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Charge.TYPE, Charge.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Count.TYPE, (payload, ctx) -> ROLLS.put(ctx.player().getUUID(), Math.max(0, payload.available())));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ROLLS.remove(handler.player.getUUID()));
		if (!LOADED) {
			return;
		}
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % APPLY_EVERY != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				apply(player, Powers.has(player, WebPower.INSTANCE));
			}
		});
	}

	private static @Nullable Holder<Attribute> count() {
		if (!looked) {
			looked = true;
			Optional<Holder.Reference<Attribute>> holder = BuiltInRegistries.ATTRIBUTE.get(COUNT_ATTRIBUTE);
			count = holder.isPresent() ? holder.get() : null;
		}
		return count;
	}

	/** Web's roll charges on ({@code on}) or off. */
	private static void apply(ServerPlayer player, boolean on) {
		Holder<Attribute> attribute = count();
		AttributeInstance instance = attribute == null ? null : player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		double extra = ROLL_CHARGES - instance.getBaseValue();
		if (on && extra > 0.0) {
			if (!instance.hasModifier(MODIFIER_ID)) {
				instance.addOrUpdateTransientModifier(new AttributeModifier(MODIFIER_ID, extra, AttributeModifier.Operation.ADD_VALUE));
			}
		} else if (instance.hasModifier(MODIFIER_ID)) {
			instance.removeModifier(MODIFIER_ID);
		}
	}

	/** Whether {@code player} has a roll charge for a counter (always, without Combat Roll or before the client says). */
	static boolean has(ServerPlayer player) {
		Integer rolls = ROLLS.get(player.getUUID());
		return !LOADED || rolls == null || rolls > 0;
	}

	/** A counter spends a roll charge. */
	static void spend(ServerPlayer player) {
		Integer rolls = ROLLS.get(player.getUUID());
		if (!LOADED || rolls == null || rolls <= 0) {
			return;
		}
		ROLLS.put(player.getUUID(), rolls - 1);
		if (ServerPlayNetworking.canSend(player, Charge.TYPE)) {
			ServerPlayNetworking.send(player, new Charge(-1));
		}
	}
}
