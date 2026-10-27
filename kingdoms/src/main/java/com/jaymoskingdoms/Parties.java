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
import net.minecraft.core.UUIDUtil;
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
import org.jspecify.annotations.Nullable;

/**
 * (0.7.264, Andy's NOEA picks) Parties: up to {@link #MAX_SIZE} players who fight and quest together. Whoever invites
 * first leads (and leadership passes on when they leave); the leader invites ({@code /party invite}, good for
 * {@link #INVITE_TICKS}), kicks and turns player-versus-player on or off ({@code /party pvp on|off}). Different from a
 * handshake {@link Groups group}, which only softens blows: a party
 * <ul>
 * <li>never hurts its own while PvP is off (the default): blows, arrows, owned beasts and powers all arrive as damage
 * with a player behind it, and are cancelled;</li>
 * <li>shares a slay quest's kills with members within {@link #SHARE_RANGE} blocks ({@link Quests#shareSlain});</li>
 * <li>shows each member's health, hunger and dimension at any distance, about once a second, in a compact list under
 * the top-left of the screen ({@code client/PartyClient}).</li>
 * </ul>
 * Parties are kept on the Overworld, so they survive relogging and restarts. Jaymo's Powers asks who is in one by
 * reflection: {@link #members} and {@link #allied}.
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

	/** One member as kept: who, and the username last seen (so an absent member can still be named and kicked). */
	record Member(UUID id, String name) {
		static final Codec<Member> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("id").forGetter(Member::id),
				Codec.STRING.fieldOf("name").forGetter(Member::name))
			.apply(i, Member::new));
	}

	/** A party: its leader, its members in the order they joined (the leader among them) and whether they may fight. */
	record Party(UUID leader, List<Member> members, boolean pvp) {
		static final Codec<Party> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("leader").forGetter(Party::leader),
				Member.CODEC.listOf().fieldOf("members").forGetter(Party::members),
				Codec.BOOL.optionalFieldOf("pvp", false).forGetter(Party::pvp))
			.apply(i, Party::new));

		boolean has(UUID id) {
			for (Member m : members) {
				if (m.id.equals(id)) {
					return true;
				}
			}
			return false;
		}
	}

	/** Every party, on the Overworld. */
	static final AttachmentType<List<Party>> PARTIES = AttachmentRegistry.create(JaymosKingdoms.id("parties"),
		builder -> builder.persistent(Party.CODEC.listOf()));

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
					Party party = me == null ? null : of(c.getSource().getServer(), me.getUUID());
					return SharedSuggestionProvider.suggest(party == null ? List.<String>of() : party.members.stream().map(Member::name).toList(), builder);
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
		MinecraftServer server = player.level().getServer();
		Party party = server == null ? null : of(server, player.getUUID());
		if (party != null) {
			for (Member m : party.members) {
				ServerPlayer p = m.id.equals(player.getUUID()) ? null : server.getPlayerList().getPlayer(m.id);
				if (p != null) {
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
		MinecraftServer server = a.level().getServer();
		Party party = server == null ? null : of(server, a.getUUID());
		return party != null && party.has(b.getUUID());
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

	// ---- storage ---------------------------------------------------------------------------------------------------------

	private static List<Party> all(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(PARTIES, List.of());
	}

	private static void save(MinecraftServer server, List<Party> parties) {
		Factions.level(server).setAttached(PARTIES, List.copyOf(parties));
	}

	static @Nullable Party of(MinecraftServer server, UUID player) {
		for (Party party : all(server)) {
			if (party.has(player)) {
				return party;
			}
		}
		return null;
	}

	/** Replaces {@code old} (if there is one) with {@code next}, or removes it if {@code next} is null. */
	private static void swap(MinecraftServer server, @Nullable Party old, @Nullable Party next) {
		List<Party> parties = new ArrayList<>(all(server));
		int at = old == null ? -1 : parties.indexOf(old);
		if (at < 0) {
			if (next != null) {
				parties.add(next);
			}
		} else if (next == null) {
			parties.remove(at);
		} else {
			parties.set(at, next);
		}
		save(server, parties);
	}

	/** {@code party} without {@code gone}: leadership to the next in line, and none at all if only one is left. */
	private static @Nullable Party without(Party party, UUID gone) {
		List<Member> rest = new ArrayList<>(party.members);
		rest.removeIf(m -> m.id.equals(gone));
		if (rest.size() < 2) {
			return null;
		}
		return new Party(party.leader.equals(gone) ? rest.get(0).id : party.leader, List.copyOf(rest), party.pvp);
	}

	/** What {@code player} is called to their party: their character's name, else their username. */
	private static Component name(ServerPlayer player) {
		Component shown = CharacterNames.of(player);
		return shown != null ? shown : Component.literal(player.getGameProfile().name());
	}

	/** {@code m}'s username (the live one if they are on). */
	private static String username(MinecraftServer server, Member m) {
		ServerPlayer p = server.getPlayerList().getPlayer(m.id);
		return p == null ? m.name : p.getGameProfile().name();
	}

	/** Tells every online member of {@code party}. */
	private static void tellAll(MinecraftServer server, Party party, Component message) {
		for (Member m : party.members) {
			ServerPlayer p = server.getPlayerList().getPlayer(m.id);
			if (p != null) {
				p.sendSystemMessage(message);
			}
		}
	}

	private static int fail(ServerPlayer player, String key, Object... args) {
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.party." + key, args).withStyle(ChatFormatting.RED));
		return 0;
	}

	// ---- commands ---------------------------------------------------------------------------------------------------------

	private static int invite(ServerPlayer leader, ServerPlayer target) {
		MinecraftServer server = leader.level().getServer();
		if (target == leader) {
			return fail(leader, "self");
		}
		Party mine = of(server, leader.getUUID());
		if (mine != null && !mine.leader.equals(leader.getUUID())) {
			return fail(leader, "not_leader");
		}
		if (mine != null && mine.members.size() >= MAX_SIZE) {
			return fail(leader, "full", MAX_SIZE);
		}
		if (of(server, target.getUUID()) != null) {
			return fail(leader, "has_party", name(target));
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
		if (of(server, player.getUUID()) != null) {
			return fail(player, "you_have_party");
		}
		Party party = of(server, from.getUUID());
		if (party != null && (!party.leader.equals(from.getUUID()) || party.members.size() >= MAX_SIZE)) {
			return fail(player, "cannot_join");
		}
		Member joined = new Member(player.getUUID(), player.getGameProfile().name());
		Party next;
		if (party == null) {
			// (whoever invited first leads)
			next = new Party(from.getUUID(), List.of(new Member(from.getUUID(), from.getGameProfile().name()), joined), false);
		} else {
			List<Member> list = new ArrayList<>(party.members);
			list.add(joined);
			next = new Party(party.leader, List.copyOf(list), party.pvp);
		}
		swap(server, party, next);
		tellAll(server, next, Component.translatable("message.jaymoskingdoms.party.joined", name(player)).withStyle(ChatFormatting.GREEN));
		sync(server, next);
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

	private static int leave(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Party party = of(server, player.getUUID());
		if (party == null) {
			return fail(player, "none");
		}
		remove(server, party, player.getUUID(), player.getGameProfile().name());
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.party.left").withStyle(ChatFormatting.YELLOW));
		return 1;
	}

	/** Takes {@code gone} out of {@code party} and tells the rest (and hands over the lead, or disbands a party of one). */
	private static void remove(MinecraftServer server, Party party, UUID gone, String goneName) {
		Party next = without(party, gone);
		swap(server, party, next);
		ServerPlayer p = server.getPlayerList().getPlayer(gone);
		if (p != null) {
			send(p, List.of());
			LISTED.remove(gone);
		}
		if (next == null) {
			for (Member m : party.members) {
				ServerPlayer other = m.id.equals(gone) ? null : server.getPlayerList().getPlayer(m.id);
				if (other != null) {
					other.sendSystemMessage(Component.translatable("message.jaymoskingdoms.party.disbanded").withStyle(ChatFormatting.YELLOW));
					send(other, List.of());
					LISTED.remove(m.id);
				}
			}
			return;
		}
		tellAll(server, next, Component.translatable("message.jaymoskingdoms.party.member_left", goneName).withStyle(ChatFormatting.YELLOW));
		if (!next.leader.equals(party.leader)) {
			ServerPlayer lead = server.getPlayerList().getPlayer(next.leader);
			Component who = lead != null ? name(lead) : Component.literal(next.members.get(0).name);
			tellAll(server, next, Component.translatable("message.jaymoskingdoms.party.new_leader", who).withStyle(ChatFormatting.GOLD));
		}
		sync(server, next);
	}

	private static int kick(ServerPlayer leader, String who) {
		MinecraftServer server = leader.level().getServer();
		Party party = of(server, leader.getUUID());
		if (party == null) {
			return fail(leader, "none");
		}
		if (!party.leader.equals(leader.getUUID())) {
			return fail(leader, "not_leader");
		}
		Member target = null;
		for (Member m : party.members) {
			if (username(server, m).equalsIgnoreCase(who)) {
				target = m;
			}
		}
		if (target == null || target.id.equals(leader.getUUID())) {
			return fail(leader, "kick_none", who);
		}
		ServerPlayer p = server.getPlayerList().getPlayer(target.id);
		if (p != null) {
			p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.party.kicked").withStyle(ChatFormatting.RED));
		}
		remove(server, party, target.id, username(server, target));
		return 1;
	}

	private static int pvp(ServerPlayer leader, boolean on) {
		MinecraftServer server = leader.level().getServer();
		Party party = of(server, leader.getUUID());
		if (party == null) {
			return fail(leader, "none");
		}
		if (!party.leader.equals(leader.getUUID())) {
			return fail(leader, "not_leader");
		}
		Party next = new Party(party.leader, party.members, on);
		swap(server, party, next);
		tellAll(server, next, Component.translatable(on ? "message.jaymoskingdoms.party.pvp_on" : "message.jaymoskingdoms.party.pvp_off")
			.withStyle(on ? ChatFormatting.RED : ChatFormatting.GREEN));
		return 1;
	}

	/** {@code /party}: the members, who leads, and how each is. */
	private static int list(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Party party = of(server, player.getUUID());
		if (party == null) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.party.none").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.party.header", party.members.size(), MAX_SIZE,
			Component.translatable(party.pvp ? "message.jaymoskingdoms.party.pvp.on" : "message.jaymoskingdoms.party.pvp.off")).withStyle(ChatFormatting.GOLD));
		for (Member m : party.members) {
			ServerPlayer p = server.getPlayerList().getPlayer(m.id);
			Component line = p == null
				? Component.translatable("message.jaymoskingdoms.party.member.away", m.name)
				: Component.translatable("message.jaymoskingdoms.party.member", name(p), Math.round(p.getHealth()), Math.round(p.getMaxHealth()));
			player.sendSystemMessage(Component.literal(party.leader.equals(m.id) ? "* " : "- ").append(line)
				.withStyle(p == null ? ChatFormatting.GRAY : ChatFormatting.WHITE));
		}
		return party.members.size();
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
		MinecraftServer server = hurt.level().getServer();
		Party party = server == null ? null : of(server, hurt.getUUID());
		return party != null && !party.pvp && party.has(by.getUUID());
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
		List<Party> kept = new ArrayList<>();
		boolean renamed = false;
		for (Party party : all(server)) {
			// (a member who changed their username is kept under the new one)
			List<Member> names = new ArrayList<>(party.members.size());
			boolean changed = false;
			for (Member m : party.members) {
				ServerPlayer p = server.getPlayerList().getPlayer(m.id);
				if (p != null && !p.getGameProfile().name().equals(m.name)) {
					names.add(new Member(m.id, p.getGameProfile().name()));
					changed = true;
				} else {
					names.add(m);
				}
			}
			Party now = changed ? new Party(party.leader, List.copyOf(names), party.pvp) : party;
			renamed |= changed;
			kept.add(now);
			sync(server, now);
		}
		if (renamed) {
			save(server, kept);
		}
		// (anyone who was listed and no longer has a party was sent an empty list at once; this catches the rest)
		LISTED.removeIf(id -> {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null && of(server, id) == null) {
				send(p, List.of());
			}
			return p == null || of(server, id) == null;
		});
	}

	/** Sends each online member of {@code party} everyone's stats (their own included, so the HUD can skip them). */
	private static void sync(MinecraftServer server, Party party) {
		List<Entry> entries = new ArrayList<>();
		List<ServerPlayer> online = new ArrayList<>();
		for (Member m : party.members) {
			ServerPlayer p = server.getPlayerList().getPlayer(m.id);
			if (p == null) {
				continue;
			}
			online.add(p);
			entries.add(new Entry(p.getUUID(), name(p), party.leader.equals(m.id), p.getHealth(), p.getMaxHealth(), p.getFoodData().getFoodLevel(),
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
