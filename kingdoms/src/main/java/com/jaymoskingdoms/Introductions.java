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
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
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
 * (0.7.187) Andy: "Jade should no longer show player or villager names, health or occupation until you hit them or find
 * out through speaking with them". Jade's panel on a stranger keeps only what you can see ({@code client/compat/
 * JadeIntroductions}): no name, no trade, and no health until you've struck them ({@link #STRUCK}). Being introduced
 * shows all three.
 * <ul>
 * <li>A villager: "Ask their name" in MCA's menu ({@link #ANSWER}). One who bears you too much ill will won't say. A
 * ruler gives their title and realm, and their nametag carries the title from then on ({@link #TITLE}), so a king is
 * easy to pick out once met. An audience with a ruler introduces them too, and your own MCA family is always known.</li>
 * <li>(0.7.204) A villager: "Offer a handshake" in MCA's menu ({@link #HANDSHAKE}), which needs no shared tongue.
 * Not everyone takes it ({@link #takesHand}); one who does shakes on it ({@link Handshakes#offerTo}) and you learn
 * their name. A ruler won't: they're met at an audience.</li>
 * <li>(0.7.184) A player: shake hands ({@link Handshakes}: both use on each other), which groups you too. Or
 * {@code /introduce}, looking at them. They learn your name, and are offered to introduce themselves
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
	/** (0.7.397) Lines an outlaw has for keeping their name to themselves. */
	static final int OUTLAW_LINES = 3;
	/** (0.7.504) Lines a wary villager has for keeping their name from you until you've proven yourself ({@link #wary}). */
	public static final int WARY_LINES = 5;
	/**
	 * (0.7.504) Proving yourself to one who minds your blood ({@link Prejudice#minds}): MCA hearts needed per degree they
	 * mind, never more than {@link Strangers#TRUSTED_HEARTS}; and the hearts that win over one whose people think ill of you.
	 */
	static final int PROVEN_HEARTS_PER_DEGREE = 8, PROVEN_HEARTS_REP = 20;

	/**
	 * (0.7.204) Taking an offered hand ({@link Handshakes#offerTo}): the odds before anything else, what each heart and
	 * each point of mood adds, what a warm or sour temper adds per step, and the least and most they can be.
	 */
	static final float HAND_TAKEN = 0.5F, HAND_PER_HEART = 0.01F, HAND_PER_MOOD = 0.015F, HAND_PER_TEMPER = 0.12F,
		HAND_LEAST = 0.1F, HAND_MOST = 0.95F;

	/** (0.7.204) "Offer a handshake" (MCA's {@code main} menu, needs no words): shown only to one you don't know. */
	public static final String HANDSHAKE = "jaymoskingdoms_handshake";

	/**
	 * (0.7.230) MCA's interact screen's Introduce button for one you don't know (sent as a {@code main} answer, not in the
	 * talk menu): their name asked if you share a tongue, else a handshake offered.
	 */
	public static final String INTRODUCE = "jaymoskingdoms_introduce";

	/** "Ask their name" (added to MCA's {@code main} menu by our dialogue data): shown only to one you don't know. */
	public static final String ANSWER = "jaymoskingdoms_name";

	/** On a player: the villagers and players whose names they know. Saved, kept through death, synced to them alone. */
	static final AttachmentType<Set<UUID>> KNOWN = AttachmentRegistry.create(JaymosKingdoms.id("known_names"),
		builder -> builder.persistent(UUIDUtil.CODEC_SET).copyOnDeath()
			.syncWith(ByteBufCodecs.collection(HashSet::new, UUIDUtil.STREAM_CODEC), AttachmentSyncPredicate.targetOnly()));

	/** On a player: the strangers they've struck, whose health Jade shows them. Kept through death, not saved, synced to them alone. */
	static final AttachmentType<Set<UUID>> STRUCK = AttachmentRegistry.create(JaymosKingdoms.id("struck"),
		builder -> builder.copyOnDeath()
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
		JaymosKingdoms.LOGGER.debug("introduction attachments: {} {} {}", KNOWN, STRUCK, TITLE);
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
			Commands.literal("introduce").executes(c -> introduce(c.getSource().getPlayerOrException()))));
		ServerTickEvents.END_SERVER_TICK.register(Introductions::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (source.getEntity() instanceof ServerPlayer player) {
				strike(player, entity);
			}
		});
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
		if (Groups.together(viewer, e)) {
			// (0.7.184) your group is always known (Groups; an op's team too)
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

	/** This client's player may see {@code e}'s health: they know them, or have struck them. */
	public static boolean healthKnownHere(Entity e) {
		if (knownHere(e)) {
			return true;
		}
		Player me = viewer.get();
		Set<UUID> struck = me == null ? null : me.getAttached(STRUCK);
		return struck != null && struck.contains(e.getUUID());
	}

	/** {@code player} struck {@code e}: a stranger's health is theirs to see from then (Jade). */
	static void strike(ServerPlayer player, Entity e) {
		if (knows(player, e)) {
			return;
		}
		Set<UUID> struck = player.getAttached(STRUCK);
		if (struck != null && struck.contains(e.getUUID())) {
			return;
		}
		// a new set, so it's synced
		Set<UUID> next = struck == null ? new HashSet<>() : new HashSet<>(struck);
		next.add(e.getUUID());
		player.setAttached(STRUCK, next);
	}

	/**
	 * (0.7.474) What {@code e}, a villager whose name you haven't asked, goes by: a goblin or an orc as one (Andy: "they
	 * shouldnt be referred to as villagers if they are orc or goblin"), anyone else what MCA calls them.
	 */
	public static Component what(Entity e) {
		Heritage.Kind brood = Greenskins.of(e);
		if (brood == Heritage.Kind.ORC) {
			return Component.translatable("identity.jaymoskingdoms.orc_name");
		}
		if (brood == Heritage.Kind.GOBLIN) {
			return Component.translatable("identity.jaymoskingdoms.goblin_name");
		}
		return e.getType().getDescription();
	}

	/** What an unintroduced player is called. */
	public static Component stranger() {
		return Component.translatable("identity.jaymoskingdoms.stranger");
	}

	/** {@code player} now knows {@code e}'s name. True if they didn't before. */
	static boolean learn(ServerPlayer player, Entity e) {
		return learn(player, e.getUUID());
	}

	/** (0.7.473) {@code player} now knows the name of whoever has the id {@code id} (a dead player whose face they've seen). */
	static boolean learn(ServerPlayer player, UUID id) {
		Set<UUID> known = player.getAttached(KNOWN);
		if (known != null && known.contains(id)) {
			return false;
		}
		// a new set, so it's synced
		Set<UUID> next = known == null ? new HashSet<>() : new HashSet<>(known);
		next.add(id);
		player.setAttached(KNOWN, next);
		return true;
	}

	/**
	 * (0.7.504) Andy: "someone who has any distaste toward you would not introduce themselves, i was just on a beastfolk,
	 * and the guy said remember this city isnt for you, but still shook my hand, they should be as apprehensive as the old
	 * orcs were until you prove yourself". {@code villager} keeps you at arm's length: no name, no hand, no coming over to
	 * say hello. Anyone who minds your blood ({@link Prejudice#minds}: a haven's folk minding a human, a townsman minding a
	 * Tideborn...), whose people think ill of you (their realm's reputation below welcome), or who remembers you coldly.
	 * You've proven yourself once they remember you warmly ({@link Recollections#WARM}: a rescue, a kindness), or once
	 * you've won enough of MCA's hearts (gifts and talk): {@link #PROVEN_HEARTS_PER_DEGREE} for each degree they mind.
	 */
	public static boolean wary(ServerPlayer player, Villager villager) {
		int lean = Recollections.leaning(villager, player);
		if (lean >= Recollections.WARM) {
			return false;
		}
		if (lean <= Recollections.COLD) {
			return true;
		}
		int hearts = McaCompat.heartsOf(villager, player);
		float minds = Prejudice.minds(villager, player);
		if (minds > 0.0F && hearts < Math.min(Strangers.TRUSTED_HEARTS, Math.round(minds * PROVEN_HEARTS_PER_DEGREE))) {
			return true;
		}
		Faction f = Approaches.home(player.level(), villager);
		return f != null && Reputation.get(player, f.id()) < Reputation.WELCOME && hearts < PROVEN_HEARTS_REP;
	}

	/**
	 * (0.7.504) What a {@linkplain #wary wary} villager says in place of their name (a greenskin in its own dialect).
	 * (0.7.597) Andy: "this villager talked to me then acted this way when i introduced myself, the interactions should be
	 * consistent". The same line to the same player all day, so a step back from your hand, their menu opening and
	 * asking their name don't each draw a different cold word (and the bubbles showing it at once show it once).
	 */
	public static Component waryLine(Villager villager, ServerPlayer player) {
		long day = Deeds.today(player.level().getServer());
		int variant = Math.floorMod(java.util.Objects.hash(villager.getUUID(), player.getUUID(), day), WARY_LINES);
		return Dialects.trShared(villager, "dialogue.chat.jaymoskingdoms.name_wary." + variant);
	}

	/** {@code player} asked {@code villager}'s name (MCA's menu): what they answer, and {@code player} knows it from then. */
	public static Component askName(ServerPlayer player, Villager villager) {
		if (Recollections.refuses(villager, player)) {
			// (0.7.490) an orc or goblin with a grudge refuses in its own dialect
			return Dialects.trShared(villager, "dialogue.chat.jaymoskingdoms.name_refused");
		}
		if (!Outlaws.is(villager) && !ZFighterFolk.is(villager) && wary(player, villager)) {
			// (0.7.504) one who minds you keeps their name until you've proven yourself
			return waryLine(villager, player);
		}
		if (Outlaws.is(villager)) {
			// (0.7.397) a looter, a bandit or a camp's raider keeps their name to themselves
			// (0.7.490) an orc or goblin refuses in its own dialect
			return Dialects.trShared(villager, "dialogue.chat.jaymoskingdoms.name_outlaw." + villager.getRandom().nextInt(OUTLAW_LINES));
		}
		String kind = ZFighterFolk.is(villager) ? ZFighterFolk.kind(villager) : null;
		if (kind != null) {
			// (0.7.405) Andy: "the judge shouldnt say this" ("Oh! I'm The Judge. And you are?"): one of Powers' Z Fighter
			// characters answers as who they are
			learn(player, villager);
			return Component.translatable("dialogue.chat.jaymoskingdoms.name_zfighter." + kind, Component.literal(villager.getName().getString()));
		}
		boolean fresh = learn(player, villager);
		Component name = Component.literal(villager.getName().getString());
		Faction f = Rulers.is(villager) ? Factions.get(player.level().getServer(), Rulers.faction(villager)) : null;
		if (f != null) {
			Component title = Rulers.title(player.level().getServer(), f, Rulers.role(villager), McaCompat.female(villager));
			// (0.7.490) an orc king (or a goblin one) names himself in his own dialect
			return Dialects.trShared(villager, "dialogue.chat.jaymoskingdoms.name_ruler." + villager.getRandom().nextInt(RULER_LINES), title, name, f.name());
		}
		// (0.7.254) Andy: "if a guard i havent met before's first interaction is a shove or searching me, the introduction
		// might be awkward": one whose first dealing with you was like that says so (SharedHistory)
		Component met = fresh ? SharedHistory.introduction(player, villager, name) : null;
		if (met != null) {
			return met;
		}
		// (0.7.521) a human living in a ChoiceTheorem village's haven says, in a word, why they're there (Havens#ctov)
		String reason = Havens.humanReason(villager);
		if (reason != null) {
			return Component.translatable("dialogue.chat.jaymoskingdoms.name_haven_human." + reason, name);
		}
		// (0.7.490) a greenskin gives its name in its own dialect
		return Dialects.trShared(villager, "dialogue.chat.jaymoskingdoms.name." + villager.getRandom().nextInt(LINES), name);
	}

	/**
	 * (0.7.204) Andy: "allow handshaking with villagers too, not everyone will accept one as an introduction. but it will
	 * help if you dont speak the language to get started". Whether {@code villager} takes {@code player}'s hand: never
	 * with a grudge, likelier the more hearts, the better their mood and the warmer their temper.
	 */
	static boolean takesHand(ServerPlayer player, Villager villager) {
		if (Recollections.refuses(villager, player) || Outlaws.is(villager) || wary(player, villager)) {
			// ((0.7.504) nor one who minds you, till you've proven yourself)
			return false;
		}
		float chance = HAND_TAKEN + McaCompat.heartsOf(villager, player) * HAND_PER_HEART + McaCompat.mood(villager) * HAND_PER_MOOD
			+ McaCompat.temper(villager) * HAND_PER_TEMPER;
		return villager.getRandom().nextFloat() < Math.clamp(chance, HAND_LEAST, HAND_MOST);
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
		Commandments.act(player, "introduce");
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
