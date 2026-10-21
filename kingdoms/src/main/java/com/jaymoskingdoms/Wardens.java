package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * A ruler's own sworn protectors among the players (0.5.1). A king (or lord) with fewer than {@link #GUARDS_LEAST}
 * guards about dares not leave his hall ({@link Royals}), and says so to whoever he gives an audience. He may also,
 * once a day for each player, offer them a place: the more desperate he is (fewer guards, fewer people) and the better
 * they stand with him, the likelier. What he offers depends on their reputation: a <b>royal guardsman</b> (welcome),
 * a <b>vassal</b> sworn as a knight ({@link #VASSAL_REP}), or a <b>lord</b> of his realm ({@link #LORD_REP}). Accepting
 * ({@code /kingdom serve <name>}) swears them to the realm at that rank and makes them his <b>warden</b>: he goes out on
 * his rounds with them at his side even short of guards, and if he falls while they're in the world (in a war above
 * all), they lose all their good name with his realm and every realm allied with it, and their charge ends. Wardens are
 * kept on the Overworld, by faction id.
 */
public final class Wardens {
	/** Under this many guards he keeps to his hall (a warden near him makes up for them). */
	static final int GUARDS_LEAST = 3;
	/** A warden this close lets him go out. */
	static final double WARDEN_NEAR = 24.0;
	/** The reputation an offer needs: a guardsman's, a vassal's, a lord's. */
	static final int GUARD_REP = Reputation.WELCOME, VASSAL_REP = 200, LORD_REP = 500;
	/** The chance of an offer: a base, more per guard short of the least, more for a small town, more for a good name (at 1000). */
	static final float CHANCE_BASE = 0.10F, CHANCE_PER_GUARD_SHORT = 0.20F, CHANCE_SMALL_TOWN = 0.15F, CHANCE_FOR_REPUTATION = 0.25F;
	/** A town of fewer villagers than this is small. */
	static final int SMALL_TOWN = 10;
	/** How long an offer stands (ticks). */
	static final long OFFER_TICKS = 2400L;

	/** What he may offer, least first. */
	enum Post {
		GUARDSMAN(Rank.SOLDIER), VASSAL(Rank.KNIGHT), LORD(Rank.LORD);

		final Rank rank;

		Post(Rank rank) {
			this.rank = rank;
		}

		String key() {
			return "warden.jaymoskingdoms." + this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** Faction id → its wardens. */
	static final AttachmentType<Map<String, List<UUID>>> WARDENS = AttachmentRegistry.create(JaymosKingdoms.id("wardens"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, UUIDUtil.CODEC.listOf())));

	private record Offer(int faction, Post post, long until) {
	}

	/** Player → the offer standing for them. */
	private static final Map<UUID, Offer> OFFERS = new HashMap<>();
	/** Player and faction → the day he last considered them. */
	private static final Map<String, Long> CONSIDERED = new HashMap<>();

	private Wardens() {
	}

	static void clear() {
		OFFERS.clear();
		CONSIDERED.clear();
	}

	static List<UUID> of(MinecraftServer server, int faction) {
		return Factions.level(server).getAttachedOrElse(WARDENS, Map.<String, List<UUID>>of()).getOrDefault(Integer.toString(faction), List.of());
	}

	private static void put(MinecraftServer server, int faction, List<UUID> wardens) {
		Map<String, List<UUID>> map = new HashMap<>(Factions.level(server).getAttachedOrElse(WARDENS, Map.<String, List<UUID>>of()));
		if (wardens.isEmpty()) {
			map.remove(Integer.toString(faction));
		} else {
			map.put(Integer.toString(faction), List.copyOf(wardens));
		}
		Factions.level(server).setAttached(WARDENS, Map.copyOf(map));
	}

	/** The faction's guards about its seat (folk, and (0.7.138) its MCA guards). */
	static int guards(ServerLevel level, Faction faction) {
		return Rulers.guards(level, faction, new AABB(faction.seat()).inflate(Royals.ESCORT_CALL));
	}

	/** One of his wardens is in the world and near him. */
	static boolean wardenNear(ServerLevel level, Faction faction, LivingEntity ruler) {
		for (UUID id : of(level.getServer(), faction.id())) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p != null && p.level() == level && p.distanceTo(ruler) <= WARDEN_NEAR) {
				return true;
			}
		}
		return false;
	}

	/** Too few guards to go out, and no warden at his side. */
	static boolean confined(ServerLevel level, Faction faction, LivingEntity ruler) {
		return guards(level, faction) < GUARDS_LEAST && !wardenNear(level, faction, ruler);
	}

	/**
	 * At an audience with a ruler short of guards: he says he dares not go out and, at most once a day for each
	 * player (and never to one sworn elsewhere or one he counts unwelcome), he may offer them a place.
	 */
	static void audience(ServerLevel level, ServerPlayer player, Faction faction, LivingEntity ruler) {
		MinecraftServer server = level.getServer();
		int guards = guards(level, faction);
		if (!Rulers.is(ruler) || !faction.type().isRealm() || guards >= GUARDS_LEAST) {
			return;
		}
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.warden.confined", ruler.getDisplayName(), guards)
			.withStyle(ChatFormatting.YELLOW));
		if (of(server, faction.id()).contains(player.getUUID())) {
			return;
		}
		Faction sworn = Factions.of(server, player.getUUID());
		int rep = Reputation.get(player, faction.id());
		long day = Production.day(server);
		String key = player.getUUID() + "|" + faction.id();
		if ((sworn != null && sworn.id() != faction.id()) || rep < GUARD_REP || CONSIDERED.getOrDefault(key, -1L) == day) {
			return;
		}
		CONSIDERED.put(key, day);
		float chance = CHANCE_BASE + CHANCE_PER_GUARD_SHORT * (GUARDS_LEAST - guards)
			+ (faction.holdings().population() < SMALL_TOWN ? CHANCE_SMALL_TOWN : 0.0F) + CHANCE_FOR_REPUTATION * Math.max(0, rep) / Reputation.MAX;
		if (level.getRandom().nextFloat() >= chance) {
			return;
		}
		Post post = rep >= LORD_REP ? Post.LORD : rep >= VASSAL_REP ? Post.VASSAL : Post.GUARDSMAN;
		Faction.Member member = faction.member(player.getUUID());
		if (member != null && member.standing() >= post.rank.standing && post != Post.GUARDSMAN) {
			post = Post.GUARDSMAN;
		}
		OFFERS.put(player.getUUID(), new Offer(faction.id(), post, level.getGameTime() + OFFER_TICKS));
		String command = "/kingdom serve " + faction.name();
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.warden.offer", ruler.getDisplayName(), Component.translatable(post.key()))
			.withStyle(ChatFormatting.GOLD));
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.warden.accept").withStyle(style -> style.withColor(ChatFormatting.GREEN)
			.withUnderlined(true).withClickEvent(new ClickEvent.RunCommand(command)).withHoverEvent(new HoverEvent.ShowText(Component.literal(command)))));
	}

	/** {@code /kingdom serve <name>}: takes up the offer standing. Returns a failure message's key, or null on success. */
	static @Nullable String serve(ServerPlayer player, Faction faction) {
		MinecraftServer server = player.level().getServer();
		Offer offer = OFFERS.get(player.getUUID());
		if (offer == null || offer.faction() != faction.id() || offer.until() < Factions.level(server).getGameTime()) {
			return "message.jaymoskingdoms.warden.no_offer";
		}
		Faction sworn = Factions.of(server, player.getUUID());
		if (sworn != null && sworn.id() != faction.id()) {
			return "message.jaymoskingdoms.already_sworn";
		}
		OFFERS.remove(player.getUUID());
		Faction.Member member = faction.member(player.getUUID());
		int standing = Math.max(member == null ? 0 : member.standing(), offer.post().rank.standing);
		Factions.update(server, faction.id(), f -> f.withMember(player.getUUID(), new Faction.Member(Rank.forStanding(standing).ordinal(), standing)));
		List<UUID> wardens = new ArrayList<>(of(server, faction.id()));
		if (!wardens.contains(player.getUUID())) {
			wardens.add(player.getUUID());
		}
		put(server, faction.id(), wardens);
		KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.warden.sworn", player.getDisplayName(), faction.name(),
			Component.translatable(offer.post().key())).withStyle(ChatFormatting.GOLD));
		return null;
	}

	/** A warden who leaves the realm is a warden no more. */
	static void dismiss(MinecraftServer server, int faction, UUID player) {
		List<UUID> wardens = new ArrayList<>(of(server, faction));
		if (wardens.remove(player)) {
			put(server, faction, wardens);
		}
	}

	/**
	 * Its ruler fell: every warden in the world loses all their good name with the realm and every realm allied with it
	 * (what's above nothing goes; a bad name stays bad). Their charge ends either way.
	 */
	static void rulerFell(ServerLevel level, Faction faction) {
		MinecraftServer server = level.getServer();
		List<UUID> wardens = of(server, faction.id());
		if (wardens.isEmpty()) {
			return;
		}
		for (UUID id : wardens) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null) {
				continue;
			}
			Reputation.wipe(p, faction);
			for (Faction other : Factions.all(server)) {
				if (other.id() != faction.id() && !other.fallen() && Relations.between(server, faction.id(), other.id()) == Relation.ALLIED) {
					Reputation.wipe(p, other);
				}
			}
			p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.warden.failed", faction.name()).withStyle(ChatFormatting.DARK_RED));
		}
		put(server, faction.id(), List.of());
	}
}
