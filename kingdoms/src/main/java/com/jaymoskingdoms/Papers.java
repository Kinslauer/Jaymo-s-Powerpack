package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Your papers: who you are on paper, in one card (heritage, class, the realm you are sworn to and your rank and standing
 * in it, the title renown has earned you and your level). You read your own from the Character page ("Show
 * my papers"); sneak-using on another player with an empty hand shows yours to them, and their screen opens on it. The
 * server builds every card from what it holds, so a card can't say what isn't so.
 */
public final class Papers {
	/** How near the one you show them must be (blocks), and the ticks before you may show the same player again. */
	static final double SHOW_RANGE = 4.0;
	static final long SHOW_GAP = 100L;
	/** The most a name or realm is cut to on the wire. */
	static final int TEXT_MAX = 64;

	/**
	 * A card as the screen reads it: {@code owner} whose it is; {@code mine} whether it is your own; {@code heritage},
	 * {@code cls} and {@code second} the keys (empty if none); {@code realm} the realm's name ("" unsworn), its {@code color},
	 * your {@code rank} (a lang key) and {@code standing}; {@code renown} the title code ({@link Renown#TITLE}, 0 none)
	 * and {@code level} the character level.
	 */
	public record Card(String owner, boolean mine, String heritage, String cls, String second, String realm, int color, String rank, int standing, int renown,
		int level) implements CustomPacketPayload {
		public static final Type<Card> TYPE = new Type<>(JaymosKingdoms.id("papers_card"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Card> CODEC = CustomPacketPayload.codec(Card::write, Card::read);

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeUtf(cut(owner), TEXT_MAX);
			buf.writeBoolean(mine);
			buf.writeUtf(heritage);
			buf.writeUtf(cls);
			buf.writeUtf(second);
			buf.writeUtf(cut(realm), TEXT_MAX);
			buf.writeInt(color);
			buf.writeUtf(rank);
			buf.writeVarInt(standing);
			buf.writeVarInt(renown);
			buf.writeVarInt(level);
		}

		private static String cut(String s) {
			return s.length() > TEXT_MAX ? s.substring(0, TEXT_MAX) : s;
		}

		private static Card read(RegistryFriendlyByteBuf buf) {
			return new Card(buf.readUtf(TEXT_MAX), buf.readBoolean(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(TEXT_MAX), buf.readInt(), buf.readUtf(),
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
		}

		@Override
		public Type<Card> type() {
			return TYPE;
		}
	}

	/** From a player: send their papers to the player with entity id {@code target}, or to themselves for {@code -1}. */
	public record Show(int target) implements CustomPacketPayload {
		public static final Type<Show> TYPE = new Type<>(JaymosKingdoms.id("papers_show"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Show> CODEC = CustomPacketPayload.codec(Show::write, Show::read);

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeVarInt(target + 1);
		}

		private static Show read(RegistryFriendlyByteBuf buf) {
			return new Show(buf.readVarInt() - 1);
		}

		@Override
		public Type<Show> type() {
			return TYPE;
		}
	}

	/** When each player last showed their papers to each other player (keyed by both). */
	private static final Map<String, Long> SHOWN = new HashMap<>();

	private Papers() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Card.TYPE, Card.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Show.TYPE, Show.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Show.TYPE, (payload, context) -> show(context.player(), payload.target()));
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> SHOWN.clear());
	}

	private static void show(ServerPlayer owner, int target) {
		if (target < 0) {
			send(owner, owner, true);
			return;
		}
		Entity found = owner.level().getEntity(target);
		if (!(found instanceof ServerPlayer to) || to == owner || to.distanceToSqr(owner) > SHOW_RANGE * SHOW_RANGE || !owner.getMainHandItem().isEmpty()) {
			return;
		}
		long now = owner.level().getGameTime();
		String key = owner.getUUID() + ">" + to.getUUID();
		Long last = SHOWN.get(key);
		if (last != null && now - last < SHOW_GAP) {
			return;
		}
		SHOWN.put(key, now);
		if (send(owner, to, false)) {
			owner.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.papers.shown", to.getDisplayName()).withStyle(ChatFormatting.GOLD));
		}
	}

	/** Sends {@code owner}'s card to {@code to}; false if their client can't take it. */
	private static boolean send(ServerPlayer owner, ServerPlayer to, boolean mine) {
		if (!ServerPlayNetworking.canSend(to, Card.TYPE)) {
			return false;
		}
		ServerPlayNetworking.send(to, card(owner, mine));
		return true;
	}

	private static Card card(ServerPlayer player, boolean mine) {
		MinecraftServer server = player.level().getServer();
		Heritage.Kind heritage = Heritage.of(player);
		Classes.PlayerClass cls = Classes.classOf(player), second = Classes.second(player);
		Faction f = Factions.of(server, player.getUUID());
		String realm = "", rank = "";
		int color = 0, standing = 0;
		if (f != null) {
			Faction.Member me = f.members().get(player.getUUID());
			standing = me == null ? 0 : me.standing();
			Rank r = Rank.forStanding(standing);
			if (me != null && me.rank() > r.ordinal() && me.rank() < Rank.values().length) {
				r = Rank.values()[me.rank()];
			}
			realm = f.name();
			rank = r.key();
			color = f.styleColor(server).getTextureDiffuseColor() & 0xFFFFFF;
		}
		return new Card(player.getGameProfile().name(), mine, heritage == null ? "" : heritage.key(), cls == null ? "" : cls.key(), second == null ? "" : second.key(),
			realm, color, rank, standing, player.getAttachedOrElse(Renown.TITLE, 0),
			Stats.characterLevel(Skills.of(player)));
	}
}
