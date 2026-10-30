package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.381) Andy (2026-10-30): "the 'starting over in a village' start should take into account what heritage you are
 * and either put you in a haven of your own people or in an accomodating one otherwise. you should never be
 * discriminated against in that start, it should by far be the easiest and most welcoming".
 * <p>
 * The village start's village takes you in ({@link #HOME}): its people never hold your blood against you
 * ({@link Prejudice#minds}, {@link Prejudice#reputation}), and a purist ruling it counts you as one of his own
 * ({@link Purists#kin}). Which village ({@link #want}):
 * <ul>
 * <li>Tideborn, Stoneborn, Warrior-born, Sylvan: a haven of their own blood.</li>
 * <li>Bio-built: a mixed haven (non-humans don't mind them; no haven is of their blood).</li>
 * <li>Everyone who passes for human (Emberborn among them): a village that isn't a haven.</li>
 * </ul>
 * One already standing that fits is taken first ({@link #standing}). Otherwise a village nobody has founded yet is
 * chosen, and when it's founded round you ({@link #PENDING}, {@link #tick}) it's made what you need
 * ({@link Havens#make}), its people of that blood.
 */
public final class StoryHomes {
	/** How far from spawn a standing village that fits is looked for (blocks). */
	static final double STANDING_RANGE = 2400.0;
	/** A village start's own village: your good name there to begin with (at least this). */
	static final int HOME_REPUTATION = 80;
	/** How often a village still to be founded is looked for round its player (ticks), and how near its seat must be (blocks). */
	static final int PENDING_TICKS = 10;
	static final double PENDING_RANGE = Havens.LAND_RANGE;
	/** A village found inside a settlement's land is looked past this many times, from this far off each time (blocks). */
	static final int FRESH_TRIES = 3;
	static final int FRESH_STEP = 480;

	/** The settlement that took them in (its faction id). Saved, kept through death. */
	static final AttachmentType<Integer> HOME = AttachmentRegistry.create(JaymosKingdoms.id("story_home"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());
	/** The village they woke by, not founded yet (packed position). Saved, kept through death. */
	static final AttachmentType<Long> PENDING = AttachmentRegistry.create(JaymosKingdoms.id("story_home_pending"),
		builder -> builder.persistent(Codec.LONG).copyOnDeath());

	private StoryHomes() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(StoryHomes::tick);
	}

	/** The haven one of {@code kind} wants: their own blood's, {@link Havens#MIXED} for the Bio-built, "" for none (human villages). */
	static String want(Heritage.@Nullable Kind kind) {
		if (kind == Heritage.Kind.BIOBUILT) {
			return Havens.MIXED;
		}
		return Prejudice.degree(kind) > 0 ? kind.key() : Havens.NONE;
	}

	/** {@code f} is what one of {@code kind} wants. */
	private static boolean suits(MinecraftServer server, Faction f, Heritage.@Nullable Kind kind, Player player) {
		String haven = Havens.of(server, f), want = want(kind);
		if (!want.isEmpty()) {
			// (the Bio-built: any haven's non-humans take them)
			return Havens.MIXED.equals(want) ? !haven.isEmpty() : want.equals(haven);
		}
		return haven.isEmpty() && f.type() == FactionType.VILLAGE && !Purists.outsider(Emigrants.ruler(server, f), player);
	}

	/** The nearest standing settlement to {@code from} that suits {@code player}'s blood, or null. */
	static @Nullable Faction standing(MinecraftServer server, ServerPlayer player, Heritage.@Nullable Kind kind, BlockPos from) {
		Faction best = null;
		double bestDist = STANDING_RANGE * STANDING_RANGE;
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.type() == FactionType.BARBARIAN) {
				continue;
			}
			double d = f.seat().distSqr(from);
			if (d < bestDist && suits(server, f, kind, player)) {
				best = f;
				bestDist = d;
			}
		}
		return best;
	}

	/** Whether a village at {@code pos} already belongs to a settlement, by its seat or its land (so it can't be made anew). */
	static boolean taken(ServerLevel level, BlockPos pos) {
		return Factions.around(level.getServer(), level, pos, PENDING_RANGE) != null;
	}

	/** {@code player} woke by the unfounded village at {@code village}: it takes them in once it's founded. */
	static void await(ServerPlayer player, BlockPos village) {
		player.removeAttached(HOME);
		player.setAttached(PENDING, village.asLong());
	}

	/** {@code player} woke in standing {@code f}: it takes them in now (as it is). */
	static void settleIn(ServerPlayer player, Faction f) {
		player.removeAttached(PENDING);
		takeIn(player, f);
	}

	/** {@code player}'s story home (a faction id), or -1. */
	static int home(Entity e) {
		Integer id = e instanceof Player ? e.getAttached(HOME) : null;
		return id == null ? -1 : id;
	}

	/** {@code whom} is a player taken in by the settlement {@code who} belongs to (a folk's own, else the one they live by). */
	static boolean takenIn(LivingEntity who, Entity whom) {
		int id = home(whom);
		if (id < 0 || !(who.level() instanceof ServerLevel level)) {
			return false;
		}
		if (who instanceof Folk folk) {
			return folk.factionId() == id;
		}
		Faction f = Factions.around(level.getServer(), level, who.blockPosition(), Havens.LAND_RANGE);
		return f != null && f.id() == id;
	}

	/** {@code e} is a player taken in by {@code f}, or by a settlement {@code f} rules. */
	static boolean of(@Nullable Faction f, Entity e) {
		int id = home(e);
		if (f == null || id < 0) {
			return false;
		}
		if (id == f.id()) {
			return true;
		}
		if (!(e.level() instanceof ServerLevel level)) {
			return false;
		}
		Faction ruler = Emigrants.ruler(level.getServer(), Factions.get(level.getServer(), id));
		return ruler != null && ruler.id() == f.id();
	}

	/** The /kingdom story reset: no home any more. */
	static void clear(ServerPlayer player) {
		player.removeAttached(HOME);
		player.removeAttached(PENDING);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		if (level.getGameTime() % PENDING_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Long packed = player.getAttached(PENDING);
			if (packed == null) {
				continue;
			}
			Faction f = Factions.near(server, BlockPos.of(packed), PENDING_RANGE);
			if (f == null || f.fallen()) {
				continue;
			}
			// founded since they woke there: it's made what their blood needs
			player.removeAttached(PENDING);
			Havens.make(level, f, want(Heritage.of(player)));
			takeIn(player, Factions.get(server, f.id()));
		}
	}

	private static void takeIn(ServerPlayer player, @Nullable Faction f) {
		if (f == null) {
			return;
		}
		player.setAttached(HOME, f.id());
		int now = Reputation.get(player, f.id());
		if (now < HOME_REPUTATION) {
			Reputation.change(player, f, HOME_REPUTATION - now);
		}
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.story.taken_in", f.name()).withStyle(ChatFormatting.GOLD));
	}
}
