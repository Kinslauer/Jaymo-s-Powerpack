package com.jaymoskingdoms;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.225) Players on the throne. A realm's ruler stays a villager on its throne (its {@link Faction#leader}), but a
 * player may wear its crown: the sovereign. While one does, the villager rules in their name as <b>steward</b>, so the
 * realm runs itself when they're away; a steward who falls is replaced by the next day, and the realm doesn't fall with
 * them. A sovereign is always sworn to their realm at lord rank (treasury, war and peace, festivals, plots), and no
 * crime in it is a crime. One crown to a player. Four roads to one:
 * <ul>
 * <li><b>Usurping.</b> A lord of the realm who kills its ruler in their own hall (near the throne) takes the crown; its
 * allies think the worse of them. A player sovereign falls to a usurper the same way.</li>
 * <li><b>Inheriting.</b> A ruler's player spouse takes the crown when the ruler dies (in their bed, to an assassin, or
 * slain), unless the spouse did it or the realm is ruined.</li>
 * <li><b>Conquest.</b> A lord who plants the banner on an enemy seat, or kills its ruler, in a war may claim its crown
 * for a day ({@code /kingdom crown claim <name>}): it's freed from vassalage and allied with their old realm.</li>
 * <li><b>Founding.</b> {@code /kingdom crown found [name]} pays {@link #FOUND_COST} emeralds into a new kingdom's treasury
 * at your feet, away from every other realm.</li>
 * </ul>
 * Sovereigns are kept on the Overworld, by faction id.
 */
public final class Crowns {
	/** Emeralds to found a realm (they seed its treasury). */
	static final int FOUND_COST = 256;
	/** A ruler slain this close to their throne fell in their own hall. */
	static final double USURP_RANGE = 16.0;
	/** What a usurper's name is worth with the realm's allies. */
	static final int REP_USURP_ALLIES = -200;
	/** How long a conqueror has to claim a crown (ticks). */
	static final long CLAIM_TICKS = 24000L;

	/** Faction id → its sovereign. */
	static final AttachmentType<Map<String, UUID>> CROWNS = AttachmentRegistry.create(JaymosKingdoms.id("crowns"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, UUIDUtil.CODEC)));

	private record Claim(UUID player, int side, long until) {
	}

	/** Faction id → the conqueror who may claim it. */
	private static final Map<Integer, Claim> CLAIMS = new HashMap<>();

	private Crowns() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player && player.level() instanceof ServerLevel) {
				sovereignDied(player, source);
			}
		});
	}

	static void clear() {
		CLAIMS.clear();
	}

	// ---- who wears what ------------------------------------------------------------------------------------------------

	/** The player who wears {@code f}'s crown, if any (a realm still standing, they still sworn to it). */
	public static @Nullable UUID sovereign(MinecraftServer server, Faction f) {
		if (!f.type().isRealm() || f.fallen()) {
			return null;
		}
		UUID id = Factions.level(server).getAttachedOrElse(CROWNS, Map.of()).get(Integer.toString(f.id()));
		return id != null && f.members().containsKey(id) ? id : null;
	}

	/** {@code player} wears {@code f}'s crown. */
	public static boolean rules(MinecraftServer server, @Nullable Faction f, UUID player) {
		return f != null && player.equals(sovereign(server, f));
	}

	/** The realm whose crown {@code player} wears, if any. */
	public static @Nullable Faction crownOf(MinecraftServer server, UUID player) {
		Faction f = Factions.of(server, player);
		return rules(server, f, player) ? f : null;
	}

	private static void put(MinecraftServer server, int faction, @Nullable UUID player) {
		ServerLevel level = Factions.level(server);
		Map<String, UUID> crowns = new HashMap<>(level.getAttachedOrElse(CROWNS, Map.of()));
		if (player == null) {
			crowns.remove(Integer.toString(faction));
		} else {
			crowns.put(Integer.toString(faction), player);
		}
		level.setAttached(CROWNS, Map.copyOf(crowns));
	}

	/** {@code player}'s name for the news: their character's, if they're about; else {@code otherwise}. */
	private static Component name(MinecraftServer server, UUID player, String otherwise) {
		ServerPlayer p = server.getPlayerList().getPlayer(player);
		return p != null ? p.getDisplayName() : Component.translatable(otherwise);
	}

	/**
	 * {@code player} takes {@code f}'s crown: sworn to it at lord rank (leaving any other realm, without the sting), its
	 * wardens' charge and its law's quarrel with them ended. The villager on the throne (or the next, by tomorrow) is
	 * their steward.
	 */
	static void crown(MinecraftServer server, Faction f, UUID player) {
		Faction old = Factions.of(server, player);
		if (old != null && old.id() != f.id()) {
			if (rules(server, old, player)) {
				put(server, old.id(), null);
			}
			Factions.update(server, old.id(), x -> x.withMember(player, null));
			Wardens.dismiss(server, old.id(), player);
		}
		Faction.Member was = f.member(player);
		int standing = Math.max(was == null ? 0 : was.standing(), Rank.LORD.standing);
		Factions.update(server, f.id(), x -> x.withMember(player, new Faction.Member(Rank.LORD.ordinal(), standing)));
		Wardens.dismiss(server, f.id(), player);
		put(server, f.id(), player);
		CLAIMS.remove(f.id());
		ServerPlayer p = server.getPlayerList().getPlayer(player);
		if (p != null) {
			Theft.clearWanted(p, f.id());
			int rep = Reputation.get(p, f.id());
			if (rep < Reputation.WELCOME) {
				Reputation.change(p, f, Reputation.WELCOME - rep);
			}
			if (p.level() instanceof ServerLevel level) {
				level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.4, 0.8, 0.4, 0.3);
				level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
			}
		}
		JaymosKingdoms.LOGGER.info("{} crowned player {}", f.name(), player);
	}

	/** {@code f}'s sovereign gives up the crown: their steward rules in their own right. */
	static void abdicate(MinecraftServer server, Faction f) {
		UUID was = sovereign(server, f);
		if (was == null) {
			return;
		}
		put(server, f.id(), null);
		KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.crown.abdicated", name(server, was, "message.jaymoskingdoms.crown.someone"),
			f.name()).withStyle(ChatFormatting.YELLOW));
	}

	/** Where {@code f}'s ruler sits: its keep's throne, else its seat. */
	static BlockPos throne(MinecraftServer server, Faction f) {
		Castle.Keep keep = Factions.keep(server, f.id());
		return keep != null ? keep.throne() : f.seat();
	}

	// ---- when a ruler dies ---------------------------------------------------------------------------------------------

	/**
	 * {@code f}'s villager ruler died while a player wears its crown: only their steward fell. A new steward by tomorrow;
	 * the realm stands. True if so (and nothing else follows).
	 */
	static boolean stewardFell(ServerLevel level, Faction f) {
		MinecraftServer server = level.getServer();
		UUID sovereign = sovereign(server, f);
		if (sovereign == null) {
			return false;
		}
		Factions.update(server, f.id(), x -> x.withSuccessionAt(level.getGameTime() + KingdomEvents.SUCCESSION_TICKS));
		KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.crown.steward_fell", f.name(),
			name(server, sovereign, "message.jaymoskingdoms.crown.its_sovereign")).withStyle(ChatFormatting.YELLOW));
		return true;
	}

	/**
	 * {@code killer} slew {@code f}'s ruler near the throne, a lord of the realm: they take the crown. True if so. The
	 * realm's allies think the worse of them.
	 */
	static boolean usurped(ServerLevel level, Faction f, LivingEntity ruler, @Nullable ServerPlayer killer) {
		MinecraftServer server = level.getServer();
		if (killer == null || !Wars.isLord(f, killer.getUUID()) || !ruler.blockPosition().closerThan(throne(server, f), USURP_RANGE)) {
			return false;
		}
		crown(server, f, killer.getUUID());
		for (Faction other : Factions.all(server)) {
			if (other.id() != f.id() && other.type().isRealm() && !other.fallen() && Relations.between(other, f) == Relation.ALLIED) {
				Reputation.change(killer, other, REP_USURP_ALLIES);
			}
		}
		Factions.update(server, f.id(), x -> x.withSuccessionAt(level.getGameTime() + KingdomEvents.SUCCESSION_TICKS));
		KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.crown.usurped", killer.getDisplayName(), f.name())
			.withStyle(ChatFormatting.DARK_RED));
		killer.sendSystemMessage(Component.translatable("message.jaymoskingdoms.crown.yours", f.name()).withStyle(ChatFormatting.GOLD));
		return true;
	}

	/**
	 * {@code f}'s ruler died and a player was their spouse (not their killer, and wearing no other crown): the crown
	 * is theirs. True if so; a villager steward by tomorrow.
	 */
	static boolean inherited(ServerLevel level, Faction f, LivingEntity ruler, @Nullable ServerPlayer killer) {
		MinecraftServer server = level.getServer();
		if (!(ruler instanceof Villager villager)) {
			return false;
		}
		Set<UUID> candidates = new LinkedHashSet<>(f.members().keySet());
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			candidates.add(p.getUUID());
		}
		for (UUID id : candidates) {
			if ((killer != null && killer.getUUID().equals(id)) || McaCompat.kinOf(villager, id) != McaCompat.PARTNER) {
				continue;
			}
			Faction theirs = Factions.of(server, id);
			if (theirs != null && theirs.id() != f.id() && rules(server, theirs, id)) {
				continue;
			}
			crown(server, f, id);
			Factions.update(server, f.id(), x -> x.withSuccessionAt(level.getGameTime() + KingdomEvents.SUCCESSION_TICKS));
			KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.crown.inherited", name(server, id, "message.jaymoskingdoms.crown.spouse"),
				f.name()).withStyle(ChatFormatting.GOLD));
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) {
				p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.crown.yours", f.name()).withStyle(ChatFormatting.GOLD));
			}
			return true;
		}
		return false;
	}

	/** A sovereign died: slain by a lord of their realm in their own hall, the crown goes to them; in a war, the war decides. */
	private static void sovereignDied(ServerPlayer player, DamageSource source) {
		MinecraftServer server = player.level().getServer();
		Faction f = crownOf(server, player.getUUID());
		if (f == null) {
			return;
		}
		ServerLevel level = Factions.level(server);
		ServerPlayer killer = source.getEntity() instanceof ServerPlayer p && p != player ? p : null;
		if (player.level() == level && killer != null && Wars.isLord(f, killer.getUUID())
			&& player.blockPosition().closerThan(throne(server, f), USURP_RANGE)) {
			crown(server, f, killer.getUUID());
			KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.crown.usurped", killer.getDisplayName(), f.name())
				.withStyle(ChatFormatting.DARK_RED));
			killer.sendSystemMessage(Component.translatable("message.jaymoskingdoms.crown.yours", f.name()).withStyle(ChatFormatting.GOLD));
			return;
		}
		if (killer != null && Wars.rulerFell(level, f, source)) {
			KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.crown.fell_in_war", player.getDisplayName(), f.name(),
				killer.getDisplayName()).withStyle(ChatFormatting.RED));
			offerConquest(server, f, killer);
		}
	}

	// ---- conquest ------------------------------------------------------------------------------------------------------

	/** {@code by} took {@code target}'s seat or slew its ruler in a war: a lord of the other side may claim its crown for a day. */
	static void offerConquest(MinecraftServer server, Faction target, @Nullable ServerPlayer by) {
		if (by == null || !target.type().isRealm()) {
			return;
		}
		Faction theirs = Factions.of(server, by.getUUID());
		if (theirs == null || theirs.id() == target.id() || !Wars.isLord(theirs, by.getUUID()) || crownOf(server, by.getUUID()) != null) {
			return;
		}
		CLAIMS.put(target.id(), new Claim(by.getUUID(), theirs.id(), Factions.level(server).getGameTime() + CLAIM_TICKS));
		String command = "/kingdom crown claim " + target.name();
		by.sendSystemMessage(Component.translatable("message.jaymoskingdoms.crown.may_claim", target.name()).withStyle(ChatFormatting.GOLD));
		by.sendSystemMessage(Component.translatable("message.jaymoskingdoms.crown.claim_it").withStyle(style -> style.withColor(ChatFormatting.GREEN)
			.withUnderlined(true).withClickEvent(new ClickEvent.RunCommand(command)).withHoverEvent(new HoverEvent.ShowText(Component.literal(command)))));
	}

	/** A failure message's key, or null once {@code player} wears {@code target}'s crown. */
	static @Nullable String claim(ServerPlayer player, Faction target) {
		MinecraftServer server = player.level().getServer();
		Claim c = CLAIMS.get(target.id());
		if (c == null || !c.player().equals(player.getUUID()) || Factions.level(server).getGameTime() > c.until() || target.fallen()) {
			return "message.jaymoskingdoms.crown.no_claim";
		}
		if (crownOf(server, player.getUUID()) != null) {
			return "message.jaymoskingdoms.crown.one_crown";
		}
		if (target.overlord() >= 0) {
			// (freed: a kingdom of its own again)
			Factions.update(server, target.id(), f -> f.withType(f.type() == FactionType.FIEFDOM ? FactionType.KINGDOM : f.type()).withOverlord(-1));
		}
		Faction now = Factions.get(server, target.id());
		crown(server, now == null ? target : now, player.getUUID());
		Relations.set(server, target.id(), c.side(), Relation.ALLIED);
		KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.crown.conquered", player.getDisplayName(), target.name())
			.withStyle(ChatFormatting.GOLD));
		return null;
	}

	// ---- founding ------------------------------------------------------------------------------------------------------

	/** A failure message (key and arguments), or null once {@code player} has founded a realm at their feet. */
	static @Nullable Component found(ServerPlayer player, @Nullable String name) {
		MinecraftServer server = player.level().getServer();
		ServerLevel level = Factions.level(server);
		if (player.level() != level) {
			return Component.translatable("message.jaymoskingdoms.crown.overworld");
		}
		if (crownOf(server, player.getUUID()) != null) {
			return Component.translatable("message.jaymoskingdoms.crown.one_crown");
		}
		if (name != null && Factions.byName(server, name) != null) {
			return Component.translatable("message.jaymoskingdoms.crown.name_taken", name);
		}
		BlockPos at = player.blockPosition();
		Faction near = Factions.realmNear(server, at, KingdomEvents.CAPITAL_SPACING);
		if (near == null) {
			near = Factions.near(server, at, KingdomEvents.TOWN_RADIUS);
		}
		if (near != null) {
			return Component.translatable("message.jaymoskingdoms.crown.too_near", near.name(), KingdomEvents.CAPITAL_SPACING);
		}
		int have = player.getInventory().countItem(Items.EMERALD);
		if (have < FOUND_COST) {
			return Component.translatable("message.jaymoskingdoms.crown.found_cost", FOUND_COST, have);
		}
		int take = FOUND_COST;
		for (int slot = 0; slot < player.getInventory().getContainerSize() && take > 0; slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(Items.EMERALD)) {
				int n = Math.min(take, stack.getCount());
				stack.shrink(n);
				take -= n;
			}
		}
		Faction k = KingdomEvents.found(level, at, null);
		if (name != null) {
			Factions.update(server, k.id(), f -> f.withName(name));
		}
		Faction founded = Factions.update(server, k.id(), f -> f.withTreasury(f.treasury() + FOUND_COST));
		crown(server, founded == null ? k : founded, player.getUUID());
		Faction done = Factions.get(server, k.id());
		KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.crown.founded", player.getDisplayName(),
			done == null ? k.name() : done.name()).withStyle(ChatFormatting.GOLD));
		return null;
	}

	// ---- commands ------------------------------------------------------------------------------------------------------

	/** {@code /kingdom crown}: what you rule; {@code abdicate}, {@code claim <name>}, {@code found [name]}. */
	static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("crown").executes(Crowns::status)
			.then(Commands.literal("abdicate").executes(Crowns::abdicateCommand))
			.then(Commands.literal("claim").then(Commands.argument("name", StringArgumentType.word()).executes(Crowns::claimCommand)))
			.then(Commands.literal("found").executes(c -> foundCommand(c, null))
				.then(Commands.argument("name", StringArgumentType.word()).executes(c -> foundCommand(c, StringArgumentType.getString(c, "name")))));
	}

	private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		MinecraftServer server = c.getSource().getServer();
		Faction f = crownOf(server, player.getUUID());
		if (f != null) {
			c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.crown.you_rule", f.name()).withStyle(ChatFormatting.GOLD), false);
			return 1;
		}
		long now = Factions.level(server).getGameTime();
		for (Map.Entry<Integer, Claim> e : CLAIMS.entrySet()) {
			Faction target = Factions.get(server, e.getKey());
			if (target != null && e.getValue().player().equals(player.getUUID()) && now <= e.getValue().until()) {
				c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.crown.may_claim", target.name()).withStyle(ChatFormatting.GOLD), false);
			}
		}
		c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.crown.roads", FOUND_COST).withStyle(ChatFormatting.GRAY), false);
		return 0;
	}

	private static int abdicateCommand(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Faction f = crownOf(c.getSource().getServer(), player.getUUID());
		if (f == null) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.crown.none"));
			return 0;
		}
		abdicate(c.getSource().getServer(), f);
		return 1;
	}

	private static int claimCommand(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Faction target = Factions.byName(c.getSource().getServer(), StringArgumentType.getString(c, "name"));
		if (target == null) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.no_such"));
			return 0;
		}
		String refused = claim(player, target);
		if (refused != null) {
			c.getSource().sendFailure(Component.translatable(refused));
			return 0;
		}
		return 1;
	}

	private static int foundCommand(CommandContext<CommandSourceStack> c, @Nullable String name) throws CommandSyntaxException {
		Component refused = found(c.getSource().getPlayerOrException(), name);
		if (refused != null) {
			c.getSource().sendFailure(refused);
			return 0;
		}
		return 1;
	}
}
