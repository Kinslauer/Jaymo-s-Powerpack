package com.jaymoskingdoms;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.264, Andy's NOEA picks) Parties: up to {@link #MAX_SIZE} players who fight and quest together. Andy: "parties
 * should be what are started with handshakes", so a party is the handshake {@link Groups group}, one scoreboard team:
 * shaking someone's hand brings them into your party (started, with you leading, if you have none), and
 * {@code /party invite} (good for {@link #INVITE_TICKS}) does the same from afar. Everything a group gave stays (known
 * names, its colour, shoulder to shoulder, {@code /group name}); on top of it the party
 * <ul>
 * <li>has a leader, who invites, kicks and turns player-versus-player on or off ({@code /party pvp on|off}); the lead
 * passes on when they leave;</li>
 * <li>never hurts its own while PvP is off (the default): blows, arrows, owned beasts and powers all arrive as damage
 * with a player behind it, and are cancelled (with PvP on they land at a group's quarter);</li>
 * <li>shares a slay quest's kills with members within {@link #SHARE_RANGE} blocks ({@link Quests#shareSlain});</li>
 * <li>shows each member's health, hunger and dimension at any distance, about once a second, in a compact list under
 * the top-left of the screen ({@code client/PartyClient}).</li>
 * </ul>
 * Members are the team's (so a party survives relogging and restarts with the scoreboard); who leads and the PvP
 * setting are kept on the Overworld by team name. Only teams this mod made are parties: an op's own team stays a plain
 * group. Jaymo's Powers asks who is in one by reflection: {@link #members} and {@link #allied}.
 */
public final class Parties {
	/** Most players in one party. */
	public static final int MAX_SIZE = 6;
	/** An invite lapses after this long (ticks: 60 s). */
	static final int INVITE_TICKS = 1200;
	/** How often (ticks) each party's stats are sent to its members. */
	static final int SYNC_TICKS = 20;
	/** A member this close (blocks), in the same dimension, shares in a quest kill. */
	public static final double SHARE_RANGE = 64.0;

	/** A party's leader (by username, as the team keeps its members) and whether its members may fight. */
	record Meta(String leader, boolean pvp) {
		static final Codec<Meta> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("leader").forGetter(Meta::leader),
				Codec.BOOL.optionalFieldOf("pvp", false).forGetter(Meta::pvp))
			.apply(i, Meta::new));
	}

	/** Each party's leader and PvP, by its team's name, on the Overworld. */
	static final AttachmentType<Map<String, Meta>> META = AttachmentRegistry.create(JaymosKingdoms.id("party_meta"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Meta.CODEC)));

	/** An invite as made: who from, and the tick it lapses. */
	private record Invite(UUID from, int until) {
	}

	/** Invited player → their invite (one at a time; a new one replaces it). */
	private static final Map<UUID, Invite> INVITES = new HashMap<>();
	/** Players who were last sent a party list (so one who has left is sent an empty one, once). */
	private static final Set<UUID> LISTED = new HashSet<>();

	// ---- the packet ----------------------------------------------------------------------------------------------------

	/** One member as the HUD shows them. {@code dimension}: its id; the position is the block they stand at. */
	public record Entry(UUID id, Component name, boolean leader, float health, float maxHealth, int food, String dimension, int x, int y, int z) {
		static void write(RegistryFriendlyByteBuf buf, Entry e) {
			buf.writeUUID(e.id);
			ComponentSerialization.STREAM_CODEC.encode(buf, e.name);
			buf.writeBoolean(e.leader);
			buf.writeFloat(e.health);
			buf.writeFloat(e.maxHealth);
			buf.writeVarInt(e.food);
			buf.writeUtf(e.dimension);
			buf.writeInt(e.x);
			buf.writeInt(e.y);
			buf.writeInt(e.z);
		}

		static Entry read(RegistryFriendlyByteBuf buf) {
			return new Entry(buf.readUUID(), ComponentSerialization.STREAM_CODEC.decode(buf), buf.readBoolean(), buf.readFloat(), buf.readFloat(),
				buf.readVarInt(), buf.readUtf(), buf.readInt(), buf.readInt(), buf.readInt());
		}
	}

	/** Server → player: their party's members, themself among them (empty once they have none). */
	public record Sync(List<Entry> members) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(JaymosKingdoms.id("party_sync"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.members.size());
				for (Entry e : p.members) {
					Entry.write(buf, e);
				}
			},
			buf -> {
				int n = buf.readVarInt();
				List<Entry> list = new ArrayList<>();
				for (int i = 0; i < n; i++) {
					list.add(Entry.read(buf));
				}
				return new Sync(list);
			});

		@Override
		public Type<Sync> type() {
			return TYPE;
		}
	}

	private Parties() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(Parties::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !spared(entity, source));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			LISTED.remove(handler.player.getUUID());
			INVITES.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			INVITES.clear();
			LISTED.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("party")
			.executes(c -> list(c.getSource().getPlayerOrException()))
			.then(Commands.literal("list").executes(c -> list(c.getSource().getPlayerOrException())))
			.then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player())
				.executes(c -> invite(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "player")))))
			.then(Commands.literal("accept").executes(c -> accept(c.getSource().getPlayerOrException())))
			.then(Commands.literal("decline").executes(c -> decline(c.getSource().getPlayerOrException())))
			.then(Commands.literal("leave").executes(c -> leave(c.getSource().getPlayerOrException())))
			.then(Commands.literal("kick").then(Commands.argument("player", StringArgumentType.word())
				.suggests((c, builder) -> {
					ServerPlayer me = c.getSource().getPlayer();
					PlayerTeam team = me == null ? null : team(me);
					return SharedSuggestionProvider.suggest(team == null ? List.<String>of() : team.getPlayers(), builder);
				})
				.executes(c -> kick(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "player")))))
			.then(Commands.literal("pvp")
				.then(Commands.literal("on").executes(c -> pvp(c.getSource().getPlayerOrException(), true)))
				.then(Commands.literal("off").executes(c -> pvp(c.getSource().getPlayerOrException(), false))))));
	}

	// ---- the public side (read by Jaymo's Powers) -------------------------------------------------------------------

	/** The online members of {@code player}'s party, {@code player} first; just {@code player} if they have no party. */
	public static List<ServerPlayer> members(ServerPlayer player) {
		List<ServerPlayer> out = new ArrayList<>();
		out.add(player);
		PlayerTeam team = team(player);
		if (team != null) {
			for (ServerPlayer p : online(player.level().getServer(), team)) {
				if (p != player) {
					out.add(p);
				}
			}
		}
		return out;
	}

	/** {@code a} and {@code b} are the same player or in the same party (whatever its PvP setting). */
	public static boolean allied(ServerPlayer a, ServerPlayer b) {
		if (a == b || a.getUUID().equals(b.getUUID())) {
			return true;
		}
		PlayerTeam team = team(a);
		return team != null && team == b.getTeam();
	}

	/** The members of {@code player}'s party other than them, online, in their dimension and within {@code range} blocks. */
	static List<ServerPlayer> near(ServerPlayer player, double range) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : members(player)) {
			if (p != player && p.level() == player.level() && p.isAlive() && p.distanceToSqr(player) <= range * range) {
				out.add(p);
			}
		}
		return out;
	}

	// ---- the party: a group's team --------------------------------------------------------------------------------------

	/** {@code player}'s party: their team, if it's one of ours. */
	static @Nullable PlayerTeam team(ServerPlayer player) {
		PlayerTeam team = player.getTeam();
		return Groups.ours(team) ? team : null;
	}

	/** {@code team}'s members who are on. */
	private static List<ServerPlayer> online(MinecraftServer server, PlayerTeam team) {
		List<ServerPlayer> out = new ArrayList<>();
		for (String name : team.getPlayers()) {
			ServerPlayer p = server.getPlayerList().getPlayerByName(name);
			if (p != null) {
				out.add(p);
			}
		}
		return out;
	}

	private static Map<String, Meta> metas(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(META, Map.of());
	}

	private static void putMeta(MinecraftServer server, String team, @Nullable Meta meta) {
		Map<String, Meta> all = new HashMap<>(metas(server));
		if (meta == null) {
			all.remove(team);
		} else {
			all.put(team, meta);
		}
		Factions.level(server).setAttached(META, Map.copyOf(all));
	}

	/**
	 * {@code team}'s leader and PvP. A party with no leader kept, or whose leader has left it (a group from before parties,
	 * or one the lead walked out of), is led by a member who's on, and the rest are told.
	 */
	private static Meta meta(MinecraftServer server, PlayerTeam team) {
		Meta meta = metas(server).get(team.getName());
		if (meta != null && team.getPlayers().contains(meta.leader)) {
			return meta;
		}
		List<ServerPlayer> on = online(server, team);
		if (on.isEmpty()) {
			return meta != null ? meta : new Meta("", false);
		}
		Meta next = new Meta(on.get(0).getScoreboardName(), meta != null && meta.pvp);
		putMeta(server, team.getName(), next);
		if (meta != null) {
			tellAll(server, team, Component.translatable("message.jaymoskingdoms.party.new_leader", name(on.get(0))).withStyle(ChatFormatting.GOLD));
		}
		return next;
	}

	private static boolean leads(ServerPlayer player, PlayerTeam team) {
		return meta(player.level().getServer(), team).leader.equals(player.getScoreboardName());
	}

	/** What {@code player} is called to their party: their character's name, else their username. */
	private static Component name(ServerPlayer player) {
		Component shown = CharacterNames.of(player);
		return shown != null ? shown : Component.literal(player.getGameProfile().name());
	}

	/** Tells every online member of {@code team}. */
	private static void tellAll(MinecraftServer server, PlayerTeam team, Component message) {
		for (ServerPlayer p : online(server, team)) {
			p.sendSystemMessage(message);
		}
	}

	private static int fail(ServerPlayer player, String key, Object... args) {
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.party." + key, args).withStyle(ChatFormatting.RED));
		return 0;
	}

	/**
	 * {@code taker} joins {@code host}'s party (leaving their own; one started, led by {@code host}, if they have none), by
	 * handshake or invite. What came of it, to tell them both: the group's line, or that the party is full.
	 */
	private static Component join(ServerPlayer host, ServerPlayer taker) {
		MinecraftServer server = host.level().getServer();
		PlayerTeam mine = team(host), old = team(taker);
		if (mine != null && mine != old && mine.getPlayers().size() >= MAX_SIZE) {
			return Component.translatable("message.jaymoskingdoms.party.full", MAX_SIZE);
		}
		Component said = Groups.shaken(host, taker);
		PlayerTeam team = team(host);
		if (team == null || team == old) {
			return said;
		}
		if (!metas(server).containsKey(team.getName())) {
			putMeta(server, team.getName(), new Meta(host.getScoreboardName(), false));
		}
		tellAll(server, team, Component.translatable("message.jaymoskingdoms.party.joined", name(taker)).withStyle(ChatFormatting.GREEN));
		sync(server, team);
		if (old != null) {
			left(server, old, taker);
		}
		return said;
	}

	/** Handshakes: {@code taker} took {@code offerer}'s hand. Andy: "The initiating player's group is what is joined, everytime". */
	static Component shaken(ServerPlayer offerer, ServerPlayer taker) {
		return join(offerer, taker);
	}

	/**
	 * {@code gone} has just come off {@code team} (left, was put out or joined another): the rest are told, and a party
	 * down to one is no more (its last member let go of it too).
	 */
	private static void left(MinecraftServer server, PlayerTeam team, ServerPlayer gone) {
		send(gone, List.of());
		LISTED.remove(gone.getUUID());
		Scoreboard scoreboard = server.getScoreboard();
		if (scoreboard.getPlayerTeam(team.getName()) == null) {
			putMeta(server, team.getName(), null);
			return;
		}
		if (team.getPlayers().size() < 2) {
			for (ServerPlayer p : online(server, team)) {
				p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.party.disbanded").withStyle(ChatFormatting.YELLOW));
				send(p, List.of());
				LISTED.remove(p.getUUID());
			}
			scoreboard.removePlayerTeam(team);
			putMeta(server, team.getName(), null);
			return;
		}
		tellAll(server, team, Component.translatable("message.jaymoskingdoms.party.member_left", name(gone)).withStyle(ChatFormatting.YELLOW));
		meta(server, team);
		sync(server, team);
	}

	/** Takes {@code who} (a username) off {@code team}; whoever's on is told after. */
	private static void takeOff(MinecraftServer server, PlayerTeam team, String who) {
		server.getScoreboard().removePlayerFromTeam(who, team);
		ServerPlayer p = server.getPlayerList().getPlayerByName(who);
		if (p != null) {
			left(server, team, p);
		} else if (team.getPlayers().size() < 2) {
			for (ServerPlayer other : online(server, team)) {
				other.sendSystemMessage(Component.translatable("message.jaymoskingdoms.party.disbanded").withStyle(ChatFormatting.YELLOW));
				send(other, List.of());
				LISTED.remove(other.getUUID());
			}
			server.getScoreboard().removePlayerTeam(team);
			putMeta(server, team.getName(), null);
		} else {
			tellAll(server, team, Component.translatable("message.jaymoskingdoms.party.member_left", who).withStyle(ChatFormatting.YELLOW));
			meta(server, team);
			sync(server, team);
		}
	}

	// ---- commands ---------------------------------------------------------------------------------------------------------

	private static int invite(ServerPlayer leader, ServerPlayer target) {
		MinecraftServer server = leader.level().getServer();
		if (target == leader) {
			return fail(leader, "self");
		}
		PlayerTeam mine = team(leader);
		if (mine != null && !leads(leader, mine)) {
			return fail(leader, "not_leader");
		}
		if (mine != null && mine == target.getTeam()) {
			return fail(leader, "has_party", name(target));
		}
		if (mine != null && mine.getPlayers().size() >= MAX_SIZE) {
			return fail(leader, "full", MAX_SIZE);
		}
		INVITES.put(target.getUUID(), new Invite(leader.getUUID(), server.getTickCount() + INVITE_TICKS));
		leader.sendSystemMessage(Component.translatable("message.jaymoskingdoms.party.invited", name(target)).withStyle(ChatFormatting.GREEN));
		Component accept = Component.translatable("message.jaymoskingdoms.party.accept_button").withStyle(style -> style.withColor(ChatFormatting.GREEN)
			.withBold(true).withClickEvent(new ClickEvent.RunCommand("/party accept"))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal("/party accept"))));
		Component decline = Component.translatable("message.jaymoskingdoms.party.decline_button").withStyle(style -> style.withColor(ChatFormatting.RED)
			.withBold(true).withClickEvent(new ClickEvent.RunCommand("/party decline"))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal("/party decline"))));
		target.sendSystemMessage(Component.translatable("message.jaymoskingdoms.party.invite", name(leader), INVITE_TICKS / 20, accept, decline)
			.withStyle(ChatFormatting.GOLD));
		return 1;
	}

	/** {@code player}'s invite, if there is a live one (an old or missing one is dropped and they're told). */
	private static @Nullable Invite pending(MinecraftServer server, ServerPlayer player) {
		Invite invite = INVITES.remove(player.getUUID());
		if (invite == null) {
			fail(player, "no_invite");
			return null;
		}
		if (server.getTickCount() > invite.until) {
			fail(player, "expired");
			return null;
		}
		return invite;
	}

	/** {@code /party accept}: like taking the inviter's hand (so it moves you out of a party you're in). */
	private static int accept(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Invite invite = pending(server, player);
		if (invite == null) {
			return 0;
		}
		ServerPlayer from = server.getPlayerList().getPlayer(invite.from);
		if (from == null) {
			return fail(player, "inviter_gone");
		}
		PlayerTeam party = team(from);
		if (party != null && (!leads(from, party) || party.getPlayers().size() >= MAX_SIZE)) {
			return fail(player, "cannot_join");
		}
		Component said = join(from, player);
		player.sendSystemMessage(said.copy().withStyle(ChatFormatting.GREEN));
		return 1;
	}

	private static int decline(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Invite invite = pending(server, player);
		if (invite == null) {
			return 0;
		}
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.party.declined").withStyle(ChatFormatting.GRAY));
		ServerPlayer from = server.getPlayerList().getPlayer(invite.from);
		if (from != null) {
			from.sendSystemMessage(Component.translatable("message.jaymoskingdoms.party.declined_by", name(player)).withStyle(ChatFormatting.GRAY));
		}
		return 1;
	}

	/** {@code /party leave} (and {@code /group leave}). */
	static int leave(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		PlayerTeam team = team(player);
		if (team == null) {
			return fail(player, "none");
		}
		server.getScoreboard().removePlayerFromTeam(player.getScoreboardName(), team);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.party.left").withStyle(ChatFormatting.YELLOW));
		left(server, team, player);
		return 1;
	}

	private static int kick(ServerPlayer leader, String who) {
		MinecraftServer server = leader.level().getServer();
		PlayerTeam team = team(leader);
		if (team == null) {
			return fail(leader, "none");
		}
		if (!leads(leader, team)) {
			return fail(leader, "not_leader");
		}
		String target = null;
		for (String name : team.getPlayers()) {
			if (name.equalsIgnoreCase(who)) {
				target = name;
			}
		}
		if (target == null || target.equals(leader.getScoreboardName())) {
			return fail(leader, "kick_none", who);
		}
		ServerPlayer p = server.getPlayerList().getPlayerByName(target);
		if (p != null) {
			p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.party.kicked").withStyle(ChatFormatting.RED));
		}
		takeOff(server, team, target);
		return 1;
	}

	private static int pvp(ServerPlayer leader, boolean on) {
		MinecraftServer server = leader.level().getServer();
		PlayerTeam team = team(leader);
		if (team == null) {
			return fail(leader, "none");
		}
		if (!leads(leader, team)) {
			return fail(leader, "not_leader");
		}
		putMeta(server, team.getName(), new Meta(leader.getScoreboardName(), on));
		tellAll(server, team, Component.translatable(on ? "message.jaymoskingdoms.party.pvp_on" : "message.jaymoskingdoms.party.pvp_off")
			.withStyle(on ? ChatFormatting.RED : ChatFormatting.GREEN));
		return 1;
	}

	/** {@code /party}: the party's name, its members, who leads, and how each is. */
	private static int list(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		PlayerTeam team = team(player);
		if (team == null) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.party.none").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		Meta meta = meta(server, team);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.party.header", team.getDisplayName(), team.getPlayers().size(), MAX_SIZE,
			Component.translatable(meta.pvp ? "message.jaymoskingdoms.party.pvp.on" : "message.jaymoskingdoms.party.pvp.off")).withStyle(ChatFormatting.GOLD));
		for (String member : team.getPlayers()) {
			ServerPlayer p = server.getPlayerList().getPlayerByName(member);
			Component line = p == null
				? Component.translatable("message.jaymoskingdoms.party.member.away", member)
				: Component.translatable("message.jaymoskingdoms.party.member", name(p), Math.round(p.getHealth()), Math.round(p.getMaxHealth()));
			player.sendSystemMessage(Component.literal(meta.leader.equals(member) ? "* " : "- ").append(line)
				.withStyle(p == null ? ChatFormatting.GRAY : ChatFormatting.WHITE));
		}
		return team.getPlayers().size();
	}

	// ---- friendly fire -----------------------------------------------------------------------------------------------------

	/** A blow from one member of a party on another (an arrow, a beast of theirs or a power too) while PvP is off in it. */
	private static boolean spared(LivingEntity victim, DamageSource source) {
		if (!(victim instanceof ServerPlayer hurt)) {
			return false;
		}
		ServerPlayer by = playerBehind(source.getEntity());
		if (by == null || by == hurt) {
			return false;
		}
		PlayerTeam team = team(hurt);
		return team != null && team == by.getTeam() && !meta(hurt.level().getServer(), team).pvp;
	}

	/** The player behind {@code attacker}: themself, or whoever owns it (a tamed beast, a summon), if any. */
	private static @Nullable ServerPlayer playerBehind(@Nullable Entity attacker) {
		if (attacker instanceof ServerPlayer player) {
			return player;
		}
		if (attacker instanceof OwnableEntity owned && owned.getOwner() instanceof ServerPlayer owner) {
			return owner;
		}
		return null;
	}

	// ---- stats ---------------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (!INVITES.isEmpty()) {
			INVITES.values().removeIf(i -> server.getTickCount() > i.until + INVITE_TICKS);
		}
		if (server.getTickCount() % SYNC_TICKS != 0) {
			return;
		}
		Scoreboard scoreboard = server.getScoreboard();
		for (PlayerTeam team : List.copyOf(scoreboard.getPlayerTeams())) {
			if (Groups.ours(team) && !team.getPlayers().isEmpty()) {
				sync(server, team);
			}
		}
		// (a party whose team went, by /team or a group's last member leaving, lets go of its leader and PvP)
		Map<String, Meta> metas = metas(server);
		if (!metas.isEmpty() && metas.keySet().stream().anyMatch(name -> scoreboard.getPlayerTeam(name) == null)) {
			Map<String, Meta> kept = new HashMap<>(metas);
			kept.keySet().removeIf(name -> scoreboard.getPlayerTeam(name) == null);
			Factions.level(server).setAttached(META, Map.copyOf(kept));
		}
		// (anyone who was listed and no longer has a party was sent an empty list at once; this catches the rest)
		LISTED.removeIf(id -> {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null && team(p) == null) {
				send(p, List.of());
			}
			return p == null || team(p) == null;
		});
	}

	/** Sends each online member of {@code team} everyone's stats (their own included, so the HUD can skip them). */
	private static void sync(MinecraftServer server, PlayerTeam team) {
		List<ServerPlayer> online = online(server, team);
		if (online.isEmpty()) {
			return;
		}
		String leader = meta(server, team).leader;
		List<Entry> entries = new ArrayList<>();
		for (ServerPlayer p : online) {
			entries.add(new Entry(p.getUUID(), name(p), leader.equals(p.getScoreboardName()), p.getHealth(), p.getMaxHealth(), p.getFoodData().getFoodLevel(),
				p.level().dimension().identifier().toString(), p.getBlockX(), p.getBlockY(), p.getBlockZ()));
		}
		for (ServerPlayer p : online) {
			send(p, entries);
			LISTED.add(p.getUUID());
		}
	}

	private static void send(ServerPlayer player, List<Entry> entries) {
		if (ServerPlayNetworking.canSend(player, Sync.TYPE)) {
			ServerPlayNetworking.send(player, new Sync(entries));
		}
	}
}
