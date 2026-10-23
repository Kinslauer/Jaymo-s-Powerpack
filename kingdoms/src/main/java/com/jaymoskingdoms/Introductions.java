package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.180) Andy: "nametags should now not appear until you ask that villager/player their name. this way you can
 * introduce yourself, and find out easier which characters are leaders or the king". Each player keeps who they've
 * been told the name of ({@link #KNOWN}, synced to them alone). Until then the name over a villager's or player's head
 * isn't drawn ({@code client/mixin/tweaks/IntroNameTagMixin}), the tab list and Xaero's minimap call them a stranger,
 * and Jade (anything asking the client for their name) gets "Villager" or "Stranger".
 * <ul>
 * <li>A villager: "Ask their name" in MCA's menu ({@link #ANSWER}). One who bears you too much ill will won't say. A
 * ruler gives their title and realm, and their nametag carries the title from then on ({@link #TITLE}), so a king is
 * easy to pick out once met. An audience with a ruler introduces them too, and your own MCA family is always known.</li>
 * <li>A player: {@code /introduce}, looking at them. They learn your name, and are offered to introduce themselves
 * back (a click, the same command). Not behind a full mask: a costume's name is shown to everyone anyway, and telling
 * your own would give the face away.</li>
 * </ul>
 */
public final class Introductions {
	/** How far a player may be to be introduced to (blocks), and how near your gaze must be to them (cosine). */
	static final double REACH = 8.0, GAZE = 0.9;
	/** How far (blocks) someone who introduced themselves may be for {@code /introduce} to answer them instead. */
	static final double ANSWER_REACH = 16.0;
	/** How often (ticks) the MCA family near each player is checked, and how near (blocks). */
	static final int FAMILY_TICKS = 100;
	static final double FAMILY_RANGE = 24.0;
	/** How many ways a villager says their name, and a ruler theirs. */
	static final int LINES = 3, RULER_LINES = 2;

	/** "Ask their name" (added to MCA's {@code main} menu by our dialogue data): shown only to one you don't know. */
	public static final String ANSWER = "jaymoskingdoms_name";

	/** On a player: the villagers and players whose names they know. Saved, kept through death, synced to them alone. */
	static final AttachmentType<Set<UUID>> KNOWN = AttachmentRegistry.create(JaymosKingdoms.id("known_names"),
		builder -> builder.persistent(UUIDUtil.CODEC_SET).copyOnDeath()
			.syncWith(ByteBufCodecs.collection(HashSet::new, UUIDUtil.STREAM_CODEC), AttachmentSyncPredicate.targetOnly()));

	/** On a ruler: their title (King, Queen, Lord, Lady), for their nametag. Synced, not saved ({@link Standing#update}). */
	public static final AttachmentType<Component> TITLE = AttachmentRegistry.create(JaymosKingdoms.id("ruler_title"),
		builder -> builder.syncWith(ComponentSerialization.TRUSTED_STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** The player this client sees with (set by the client; null on a dedicated server). */
	public static Supplier<@Nullable Player> viewer = () -> null;

	/** Player → the last player who introduced themselves to them (who {@code /introduce} answers). */
	private static final Map<UUID, UUID> LAST = new HashMap<>();

	private Introductions() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("introduction attachments: {} {}", KNOWN, TITLE);
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
			Commands.literal("introduce").executes(c -> introduce(c.getSource().getPlayerOrException()))));
		ServerTickEvents.END_SERVER_TICK.register(Introductions::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST.clear());
	}

	/** Whose names are kept from strangers: players, and MCA's villagers. */
	public static boolean named(Entity e) {
		return e instanceof Player || McaCompat.isMca(e);
	}

	/** {@code viewer} knows {@code e}'s name (or it's no name of the kind kept: themselves, a mob). */
	public static boolean knows(@Nullable Player viewer, Entity e) {
		if (viewer == null || viewer == e || !named(e)) {
			return true;
		}
		if (e instanceof Player p && Identities.maskedAs(p) != null) {
			// a full mask: the costume's name is for everyone
			return true;
		}
		Set<UUID> known = viewer.getAttached(KNOWN);
		return known != null && known.contains(e.getUUID());
	}

	/** This client's player knows {@code e}'s name (client side; true where there's no one looking). */
	public static boolean knownHere(Entity e) {
		return knows(viewer.get(), e);
	}

	/** This client's player knows the player with {@code id} (for the tab list, where they may not be near). */
	public static boolean knownHere(UUID id) {
		Player me = viewer.get();
		if (me == null || me.getUUID().equals(id)) {
			return true;
		}
		Set<UUID> known = me.getAttached(KNOWN);
		return known != null && known.contains(id);
	}

	/** What an unintroduced player is called. */
	public static Component stranger() {
		return Component.translatable("identity.jaymoskingdoms.stranger");
	}

	/** {@code player} now knows {@code e}'s name. True if they didn't before. */
	static boolean learn(ServerPlayer player, Entity e) {
		Set<UUID> known = player.getAttached(KNOWN);
		if (known != null && known.contains(e.getUUID())) {
			return false;
		}
		// a new set, so it's synced
		Set<UUID> next = known == null ? new HashSet<>() : new HashSet<>(known);
		next.add(e.getUUID());
		player.setAttached(KNOWN, next);
		return true;
	}

	/** {@code player} asked {@code villager}'s name (MCA's menu): what they answer, and {@code player} knows it from then. */
	public static Component askName(ServerPlayer player, Villager villager) {
		if (Recollections.refuses(villager, player)) {
			return Component.translatable("dialogue.chat.jaymoskingdoms.name_refused");
		}
		learn(player, villager);
		Component name = Component.literal(villager.getName().getString());
		Faction f = Rulers.is(villager) ? Factions.get(player.level().getServer(), Rulers.faction(villager)) : null;
		if (f != null) {
			Component title = Rulers.title(Rulers.role(villager), McaCompat.female(villager));
			return Component.translatable("dialogue.chat.jaymoskingdoms.name_ruler." + villager.getRandom().nextInt(RULER_LINES), title, name, f.name());
		}
		return Component.translatable("dialogue.chat.jaymoskingdoms.name." + villager.getRandom().nextInt(LINES), name);
	}

	/** {@code /introduce}: {@code player} tells the player they're looking at (or the last to introduce themselves) their name. */
	private static int introduce(ServerPlayer player) {
		if (Identities.masked(player)) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.introduce.masked").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		ServerPlayer to = lookedAt(player);
		if (to == null) {
			UUID last = LAST.get(player.getUUID());
			ServerPlayer back = last == null ? null : player.level().getServer().getPlayerList().getPlayer(last);
			if (back != null && back.level() == player.level() && back.distanceToSqr(player) <= ANSWER_REACH * ANSWER_REACH) {
				to = back;
			}
		}
		if (to == null) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.introduce.none").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		LAST.remove(player.getUUID(), to.getUUID());
		boolean fresh = learn(to, player);
		Component shown = Identities.trueName(player);
		Component them = knows(player, to) ? Identities.trueName(to) : stranger();
		player.sendSystemMessage(Component.translatable(fresh ? "message.jaymoskingdoms.introduce.done" : "message.jaymoskingdoms.introduce.again", them)
			.withStyle(ChatFormatting.GRAY));
		if (fresh) {
			LAST.put(to.getUUID(), player.getUUID());
			Component message = Component.translatable("message.jaymoskingdoms.introduce.met", shown).withStyle(ChatFormatting.GRAY);
			if (!knows(player, to)) {
				// they don't know you either: offer to introduce yourself back
				message = message.copy().append(" ").append(Component.translatable("message.jaymoskingdoms.introduce.back")
					.withStyle(style -> style.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent.RunCommand("/introduce"))));
			}
			to.sendSystemMessage(message);
		}
		return 1;
	}

	/** The player {@code player} is looking at, near enough to talk to, or null. */
	private static @Nullable ServerPlayer lookedAt(ServerPlayer player) {
		Vec3 eye = player.getEyePosition(), look = player.getViewVector(1.0F);
		ServerPlayer best = null;
		double bestDot = GAZE;
		for (ServerPlayer other : player.level().players()) {
			if (other == player || other.isSpectator() || other.distanceToSqr(player) > REACH * REACH) {
				continue;
			}
			Vec3 to = other.getBoundingBox().getCenter().subtract(eye).normalize();
			double dot = to.dot(look);
			if (dot > bestDot && player.hasLineOfSight(other)) {
				bestDot = dot;
				best = other;
			}
		}
		return best;
	}

	/** Your own MCA family (spouse, children, parents) you always know. */
	private static void tick(MinecraftServer server) {
		if (!McaCompat.LOADED || server.getTickCount() % FAMILY_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			for (Villager v : player.level().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(FAMILY_RANGE),
				v -> McaCompat.isMca(v) && McaBridge.family(v, player))) {
				learn(player, v);
			}
		}
	}
}
