package com.jaymoskingdoms;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.minecraft.world.scores.TeamColor;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.184) Andy: "add a group system for players ... and scoreboard team integration allowing for reduced team damage
 * and other benefits." Two players who shake hands ({@link Handshakes}) end up in one group, and a group is a plain
 * scoreboard team, so {@code /team} and {@code /teammsg} work on it and a team an op made counts as a group too. Being
 * in one with someone:
 * <ul>
 * <li>you always know each other's names ({@link Introductions#knows}), in the group's colour;</li>
 * <li>your blows on each other land at a quarter ({@link #FRIENDLY_DAMAGE}), arrows and powers included;</li>
 * <li>shoulder to shoulder: each groupmate near you takes a little off what anyone else does to you
 * ({@link #SIDE_GUARD}, up to {@link #SIDE_MAX} of them);</li>
 * <li>you see each other while invisible, and don't shove each other about.</li>
 * </ul>
 * {@code /group} names who's in yours, {@code /group leave} leaves it, {@code /group name <name>} renames it. Whoever
 * takes an offered hand joins the offerer's group, leaving their own. (0.7.264) Andy: "parties should be what are started
 * with handshakes": a group of ours is a {@link Parties party}, which adds a leader, invites, no friendly fire and the
 * party list; the handshake goes through {@link Parties#shaken}, and {@code /group leave} is {@code /party leave}.
 */
public final class Groups {
	/** A team this mod made for a group (an op's own teams are groups too, but are theirs to break up). */
	static final String PREFIX = "jk_group_";
	/** What a groupmate's blow does to you, as a share of what it would. */
	static final float FRIENDLY_DAMAGE = 0.25F;
	/** Shoulder to shoulder: each groupmate this near (blocks) takes this share off a blow from anyone else, up to this many. */
	static final double SIDE_RANGE = 8.0;
	static final float SIDE_GUARD = 0.05F;
	static final int SIDE_MAX = 3;
	/** The longest a group's name may be. */
	static final int NAME_MAX = 32;
	/** The colours a new group's names are given. */
	private static final TeamColor[] COLOURS = {TeamColor.AQUA, TeamColor.GREEN, TeamColor.GOLD, TeamColor.LIGHT_PURPLE, TeamColor.YELLOW,
		TeamColor.BLUE, TeamColor.RED, TeamColor.DARK_AQUA, TeamColor.DARK_GREEN};

	private Groups() {
	}

	static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("group")
			.executes(c -> list(c.getSource().getPlayerOrException()))
			.then(Commands.literal("leave").executes(c -> Parties.leave(c.getSource().getPlayerOrException())))
			.then(Commands.literal("name").then(Commands.argument("name", StringArgumentType.greedyString())
				.executes(c -> rename(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "name")))))));
	}

	/** {@code a} and {@code b} are in the same group (a scoreboard team, ours or an op's). Either side. */
	public static boolean together(Player a, Entity b) {
		PlayerTeam team = a.getTeam();
		return team != null && b instanceof Player && team == b.getTeam();
	}

	/**
	 * {@code offerer} and {@code taker} have just shaken hands. Andy: "The initiating player's group is what is joined,
	 * everytime": {@code taker} leaves any group of theirs for {@code offerer}'s, which is started (named for them) if they
	 * have none. What came of it, to tell them both.
	 */
	static Component shaken(ServerPlayer offerer, ServerPlayer taker) {
		Scoreboard scoreboard = offerer.level().getServer().getScoreboard();
		PlayerTeam mine = offerer.getTeam(), theirs = taker.getTeam();
		if (mine != null && mine == theirs) {
			return Component.translatable("message.jaymoskingdoms.group.already", mine.getDisplayName());
		}
		PlayerTeam team = mine != null ? mine : create(scoreboard, offerer);
		scoreboard.addPlayerToTeam(offerer.getScoreboardName(), team);
		// (moves them off their old team; one of ours left empty goes)
		scoreboard.addPlayerToTeam(taker.getScoreboardName(), team);
		if (ours(theirs) && theirs.getPlayers().isEmpty()) {
			scoreboard.removePlayerTeam(theirs);
		}
		return Component.translatable("message.jaymoskingdoms.group.joined", team.getDisplayName());
	}

	/** A new group, set up for its perks, named for {@code founder}. */
	private static PlayerTeam create(Scoreboard scoreboard, ServerPlayer founder) {
		String name;
		do {
			name = PREFIX + Integer.toHexString(founder.getRandom().nextInt(0x1000000));
		} while (scoreboard.getPlayerTeam(name) != null);
		PlayerTeam team = scoreboard.addPlayerTeam(name);
		team.setDisplayName(Component.translatable("group.jaymoskingdoms.default_name", Identities.trueName(founder)));
		team.setColor(Optional.of(COLOURS[founder.getRandom().nextInt(COLOURS.length)]));
		// (blows between them are cut here, not cancelled)
		team.setAllowFriendlyFire(true);
		team.setSeeFriendlyInvisibles(true);
		team.setCollisionRule(Team.CollisionRule.PUSH_OTHER_TEAMS);
		return team;
	}

	/** What a blow from {@code source} does to {@code victim}: a groupmate's cut down, anyone else's eased by groupmates nearby. */
	public static float dealt(LivingEntity victim, DamageSource source, float amount) {
		if (!(victim instanceof Player hurt) || hurt.getTeam() == null) {
			return amount;
		}
		Entity by = source.getEntity();
		if (by == null || by == hurt) {
			return amount;
		}
		if (together(hurt, by)) {
			return amount * FRIENDLY_DAMAGE;
		}
		int near = 0;
		for (Player other : hurt.level().players()) {
			if (other != hurt && other.isAlive() && !other.isSpectator() && together(hurt, other)
				&& other.distanceToSqr(hurt) <= SIDE_RANGE * SIDE_RANGE && ++near >= SIDE_MAX) {
				break;
			}
		}
		return amount * (1.0F - SIDE_GUARD * near);
	}

	/** {@code /group}: your group's name and who's in it (by the names you know them by; those away only counted). */
	private static int list(ServerPlayer player) {
		PlayerTeam team = player.getTeam();
		if (team == null) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.group.none").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		MinecraftServer server = player.level().getServer();
		List<Component> here = new ArrayList<>();
		int away = 0;
		for (String member : team.getPlayers()) {
			ServerPlayer p = server.getPlayerList().getPlayerByName(member);
			if (p == null) {
				away++;
			} else {
				here.add(PlayerTeam.formatNameForTeam(team, Identities.trueName(p)));
			}
		}
		Component line = Component.translatable("message.jaymoskingdoms.group.members", team.getDisplayName(),
			ComponentUtils.formatList(here, Component.literal(", ")));
		if (away > 0) {
			line = line.copy().append(" ").append(Component.translatable("message.jaymoskingdoms.group.away", away));
		}
		player.sendSystemMessage(line.copy().withStyle(ChatFormatting.GRAY));
		return here.size();
	}

	/** {@code /group name <name>}: any member renames one of ours. */
	private static int rename(ServerPlayer player, String name) {
		PlayerTeam team = player.getTeam();
		name = name.strip();
		if (team == null || !ours(team) || name.isEmpty()) {
			player.sendSystemMessage(Component.translatable(team == null ? "message.jaymoskingdoms.group.none"
				: "message.jaymoskingdoms.group.kept").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		if (name.length() > NAME_MAX) {
			name = name.substring(0, NAME_MAX);
		}
		team.setDisplayName(Component.literal(name));
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.group.renamed", team.getDisplayName()).withStyle(ChatFormatting.GRAY));
		return 1;
	}

	/** One of this mod's teams: a party ({@link Parties}). */
	static boolean ours(@Nullable PlayerTeam team) {
		return team != null && team.getName().startsWith(PREFIX);
	}
}
