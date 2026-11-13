package com.jaymoskingdoms;

import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Magic screen's two packets. The player's choice from the screen goes up ({@link Pick}): a cantrip, a ritual to begin, or
 * the counterspell key. A page of plain lines comes down ({@link Page}): what an identify finds, shown on a screen and not in
 * chat.
 */
public final class MagicMenu {
	/** The longest whisper a player can send. */
	public static final int WHISPER_MAX = 80;

	/** Player to server: a choice from the Magic screen (a cantrip, or a ritual to begin; {@code text} is a whisper's words). */
	public record Pick(String id, String text) implements CustomPacketPayload {
		public static final Type<Pick> TYPE = new Type<>(JaymosKingdoms.id("magic_pick"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Pick> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeUtf(p.id, 48);
				buf.writeUtf(p.text, WHISPER_MAX);
			}, buf -> new Pick(buf.readUtf(48), buf.readUtf(WHISPER_MAX)));

		@Override
		public Type<Pick> type() {
			return TYPE;
		}
	}

	/** Server to player: open the Magic screen (the cantrips and rituals they know, listed by id). */
	public record Open(List<String> cantrips, List<String> rituals, List<String> choices) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(JaymosKingdoms.id("magic_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Open::cantrips, ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Open::rituals,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Open::choices, Open::new);

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	/** Server to player: a page to read. */
	public record Page(Component title, List<Component> lines) implements CustomPacketPayload {
		public static final Type<Page> TYPE = new Type<>(JaymosKingdoms.id("magic_page"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Page> CODEC = StreamCodec.composite(
			ComponentSerialization.STREAM_CODEC, Page::title, ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()), Page::lines, Page::new);

		@Override
		public Type<Page> type() {
			return TYPE;
		}
	}

	private MagicMenu() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Pick.TYPE, Pick.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Page.TYPE, Page.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Pick.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> pick(player, payload));
		});
	}

	/** The Magic key: answers a cast in sight if they know how, else opens the screen. */
	public static void key(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level) || !Cantrips.caster(player)) {
			return;
		}
		if (Counterspell.key(player, level)) {
			return;
		}
		ServerPlayNetworking.send(player, new Open(Cantrips.IDS, Rituals.known(player), Choices.open(player)));
	}

	/** Shows {@code player} a page of lines. */
	public static void show(ServerPlayer player, Component title, List<Component> lines) {
		if (ServerPlayNetworking.canSend(player, Page.TYPE)) {
			ServerPlayNetworking.send(player, new Page(title, lines));
		}
	}

	private static void pick(ServerPlayer player, Pick pick) {
		if (!(player.level() instanceof ServerLevel level) || !Cantrips.caster(player)) {
			return;
		}
		if (pick.id().equals("key")) {
			key(player);
		} else if (Cantrips.IDS.contains(pick.id())) {
			Cantrips.cast(pick.id(), level, player, pick.text());
		} else if (pick.id().startsWith("choose.")) {
			String key = pick.id().substring("choose.".length());
			if (key.equals("send")) {
				Familiars.send(player);
			} else if (Choices.ALL.stream().anyMatch(c -> c.key().equals(key) && NewSpells.knows(player, c.spell()))) {
				Choices.cycle(player, key);
				key(player);
			}
		} else if (pick.id().equals("scry_stop")) {
			Scrying.stop(player);
		} else if ((NewSpells.RITUALS.contains(pick.id()) || pick.id().equals(NewSpells.CALL_FAMILIAR)) && NewSpells.knows(player, pick.id())) {
			Rituals.begin(player, pick.id());
		}
	}
}
