package com.jaymoskingdoms;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.20) Tongues. The land is split into language regions by its climate ({@link Climate}): every settlement speaks
 * the tongue of the land its seat stands in, and you grew up speaking the tongue of the land where the world's spawn is.
 * With folk whose tongue you don't speak you can only do simple things: trade, give gifts, hug, play with children,
 * beckon them to follow or stay, pay a toll, hand in a finished errand. Anything that needs words (chatting, jokes,
 * stories, rumours, flirting, hiring, quests, their questions, a court wizard's respec, explaining your tail) is out,
 * and whatever they say comes out as words you don't know ({@link #garble}, over their heads and on their screens).
 *
 * <p>You learn a tongue by living among it: time spent near its speakers ({@link #IMMERSION_TICKS}) and each trade with
 * one of them add to it, and at {@link #FLUENT} you speak it. You keep learning after that, and the locals comment on
 * your accent as it improves, until at {@link #NATIVE} you sound born to it. Your own family (MCA spouse, children, parents) always
 * understands you, and you speak the tongue of any realm you're sworn to.
 */
public final class Languages {
	/** The tongues. Saved by name: rename none. */
	public enum Tongue {
		/** The temperate heartlands: plains, forests, meadows. */
		COMMON("an", "el", "dor", "wyn", "ber", "ith", "al", "mar", "en", "los", "ta", "ren", "gal", "fen"),
		/** Taiga and snow. */
		NORTHERN("skal", "hjor", "vik", "dun", "gr", "ulf", "ey", "rak", "thor", "ka", "sig", "vor", "hr", "ja"),
		/** Desert, badlands and savanna. */
		SOUTHERN("al", "ra", "sha", "qir", "zan", "mu", "har", "bek", "dah", "im", "sul", "fa", "az", "ib"),
		/** Jungle and swamp. */
		WILDWOOD("ko", "tla", "ua", "xi", "pa", "teo", "mak", "nu", "ich", "hu", "za", "ama", "ok", "il"),
		/** The cherry groves. */
		BLOSSOM("ka", "shi", "ro", "mei", "ten", "yu", "sa", "hon", "ri", "ko", "na", "jin", "ha", "mo");

		final String[] syllables;

		Tongue(String... syllables) {
			this.syllables = syllables;
		}

		public String id() {
			return this.name().toLowerCase(Locale.ROOT);
		}

		public Component title() {
			return Component.translatable("language.jaymoskingdoms." + this.id());
		}

		static @Nullable Tongue parse(String id) {
			for (Tongue t : values()) {
				if (t.id().equals(id)) {
					return t;
				}
			}
			return null;
		}

		/** The tongue spoken in {@code climate}. */
		static Tongue of(Climate climate) {
			return switch (climate) {
				case TAIGA, SNOWY -> NORTHERN;
				case DESERT, BADLANDS, SAVANNA -> SOUTHERN;
				case JUNGLE, SWAMP -> WILDWOOD;
				case CHERRY -> BLOSSOM;
				default -> COMMON;
			};
		}
	}

	/**
	 * MCA's buttons that need no words: trading, a gift, beckoning them to follow or stay or move, riding, picking up a
	 * little one, and (for your own hires and family) their pack and armour. Everything else is asked aloud.
	 */
	public static final Set<String> SIMPLE_COMMANDS = Set.of("trade", "gift", "follow", "stay", "move", "ridehorse", "pick_up", "inventory", "armor",
		"slap");
	/** MCA's talk menu: what can be done without words (a hug, a kiss, rock-paper-scissors), and the greetings that lead to it. */
	static final Set<String> SIMPLE_ANSWERS = Set.of("hug", "kiss", "rock_paper_scissor");
	static final Set<String> SIMPLE_QUESTIONS = Set.of("root", "first", "greet", "hug", "kiss", "rock_paper_scissor");

	/** Learning: what speaking a tongue takes; what each stretch among its speakers and each trade with one adds. */
	public static final int FLUENT = 100;
	/**
	 * (Accent) Learning goes on past fluent: at {@link #NATIVE} you sound born to it. The locals remark on your accent from
	 * {@link #ACCENT_FROM} points (a stumbling try at their words) until then, by tier: halting, broken, thick, slight.
	 */
	public static final int NATIVE = 200, ACCENT_FROM = 50;
	static final int[] ACCENT_TIERS = {50, 75, 100, 150};
	static final String[] ACCENTS = {"halting", "broken", "thick", "slight"};
	/** Lines per accent tier (lang {@code language.jaymoskingdoms.accent.<tier>.<n>}), and for sounding native. */
	static final int ACCENT_LINES = 4, NATIVE_LINES = 3;
	/** A remark on your accent: the chance each immersion minute, and the least time between two (ticks). */
	static final float ACCENT_CHANCE = 0.3F;
	static final int ACCENT_GAP = 20 * 60 * 5;
	static final int IMMERSION_TICKS = 20 * 60, IMMERSION_POINTS = 1, TRADE_POINTS = 2;
	/** How near a speaker must be for the time to count (blocks). */
	static final double IMMERSION_RANGE = 24.0;
	/** You're told how far along you are at every this many points. */
	static final int PROGRESS_STEP = 25;
	/** How often (ticks) you're reminded that you don't speak a tongue, when it gets in your way. */
	static final int REMIND_TICKS = 20 * 20;
	/** How long a speaker's tongue is remembered before it's looked up again (ticks: they may move house). */
	static final int SPEAKER_CACHE_TICKS = 20 * 30;
	/** Gibberish: how many words (at least, plus up to), and syllables a word (at least, plus up to). */
	static final int WORDS = 3, WORDS_SPREAD = 6, SYLLABLES = 1, SYLLABLES_SPREAD = 3;

	/** The tongue a player grew up with ({@link Tongue#id}); set the first time it's needed. */
	static final AttachmentType<String> HOME = AttachmentRegistry.create(JaymosKingdoms.id("home_tongue"),
		builder -> builder.persistent(Codec.STRING).copyOnDeath());
	/** How far along a player is in each other tongue (tongue id → points). */
	static final AttachmentType<Map<String, Integer>> LEARNED = AttachmentRegistry.create(JaymosKingdoms.id("tongues"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());

	/** Faction id → its tongue (a seat doesn't move). */
	private static final Map<Integer, Tongue> FACTIONS = new HashMap<>();
	/** Speaker → {tongue ordinal (-1: none), until (server tick)}. */
	private static final Map<Entity, int[]> SPEAKERS = new WeakHashMap<>();
	/** Player → when they were last reminded (server tick). */
	private static final Map<UUID, Integer> REMINDED = new HashMap<>();
	/** Player → when their accent was last remarked on (server tick). */
	private static final Map<UUID, Integer> REMARKED = new HashMap<>();

	private Languages() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Languages::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> home(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			REMINDED.remove(handler.player.getUUID());
			REMARKED.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FACTIONS.clear();
			SPEAKERS.clear();
			REMINDED.clear();
			REMARKED.clear();
		});
	}

	// ---- who speaks what ---------------------------------------------------------------------------------------------

	/** The tongue {@code player} grew up with: the land's round the world's spawn, the first time they're asked. */
	public static Tongue home(ServerPlayer player) {
		Tongue t = Tongue.parse(player.getAttachedOrElse(HOME, ""));
		if (t == null) {
			ServerLevel level = player.level().getServer().overworld();
			t = Tongue.of(Climate.at(level, level.getRespawnData().pos()));
			player.setAttached(HOME, t.id());
		}
		return t;
	}

	/** {@code f}'s tongue: the land's round its seat. */
	static Tongue of(MinecraftServer server, Faction f) {
		return FACTIONS.computeIfAbsent(f.id(), id -> Tongue.of(Climate.at(Factions.level(server), f.seat())));
	}

	/**
	 * The tongue {@code speaker} talks in: a guard's or a king's, his realm's; a villager's, their settlement's (or, with
	 * none, the land they stand on). Null for anyone who isn't townsfolk.
	 */
	public static @Nullable Tongue of(Entity speaker) {
		if (!(speaker.level() instanceof ServerLevel level) || !(speaker instanceof Villager || speaker instanceof Folk)) {
			return null;
		}
		int now = level.getServer().getTickCount();
		int[] known = SPEAKERS.get(speaker);
		if (known != null && now < known[1]) {
			return known[0] < 0 ? null : Tongue.values()[known[0]];
		}
		Tongue t = look(level, speaker);
		SPEAKERS.put(speaker, new int[] {t == null ? -1 : t.ordinal(), now + SPEAKER_CACHE_TICKS});
		return t;
	}

	private static @Nullable Tongue look(ServerLevel level, Entity speaker) {
		MinecraftServer server = level.getServer();
		Faction f;
		if (speaker instanceof Folk folk) {
			f = Factions.get(server, folk.factionId());
		} else {
			f = Factions.around(server, level, speaker.blockPosition(), Villages.VILLAGE_RADIUS);
			if (f == null || f.fallen()) {
				f = Factions.at(server, level, speaker.blockPosition());
			}
		}
		if (f != null && !f.fallen()) {
			return of(server, f);
		}
		return level.dimension() == net.minecraft.world.level.Level.OVERWORLD ? Tongue.of(Climate.at(level, speaker.blockPosition())) : null;
	}

	/** Points {@code player} has in {@code t}: {@link #NATIVE} in their own, at least {@link #FLUENT} in their sworn realm's. */
	public static int points(ServerPlayer player, Tongue t) {
		if (t == home(player)) {
			return NATIVE;
		}
		int learned = Math.min(NATIVE, player.getAttachedOrElse(LEARNED, Map.<String, Integer>of()).getOrDefault(t.id(), 0));
		return sworn(player, t) ? Math.max(FLUENT, learned) : learned;
	}

	/** How much of {@code t} {@code player} knows, as a percentage of speaking it (never over 100). */
	static int percent(ServerPlayer player, Tongue t) {
		return Math.min(FLUENT, points(player, t)) * 100 / FLUENT;
	}

	/** Whether {@code player} speaks {@code t}. */
	public static boolean speaks(ServerPlayer player, Tongue t) {
		return player.isCreative() || player.isSpectator() || points(player, t) >= FLUENT;
	}

	/** Sworn to a realm that speaks {@code t}: you live among them. */
	private static boolean sworn(ServerPlayer player, Tongue t) {
		MinecraftServer server = player.level().getServer();
		Faction mine = Factions.of(server, player.getUUID());
		return mine != null && of(server, mine) == t;
	}

	/** Whether {@code player} and {@code speaker} understand each other (always, with anyone who isn't townsfolk). */
	public static boolean understands(ServerPlayer player, Entity speaker) {
		Tongue t = of(speaker);
		return t == null || speaks(player, t) || (speaker instanceof Villager v && McaCompat.family(v, player));
	}

	/** (0.7.61) MCA voice lines that are words (hello, yes, no, a cheer, an angry shout) → the plain grunt heard instead without the tongue. */
	static final java.util.Map<String, net.minecraft.sounds.SoundEvent> GRUNTS = java.util.Map.of(
		"greet", net.minecraft.sounds.SoundEvents.VILLAGER_AMBIENT, "yes", net.minecraft.sounds.SoundEvents.VILLAGER_YES,
		"no", net.minecraft.sounds.SoundEvents.VILLAGER_NO, "celebrate", net.minecraft.sounds.SoundEvents.VILLAGER_CELEBRATE,
		"angry", net.minecraft.sounds.SoundEvents.VILLAGER_NO);

	/**
	 * (0.7.61) ({@code EntityVoiceMixin}) A villager speaks a voice line: each player near hears the words only if they
	 * understand the speaker ({@link #understands}); everyone else hears a grunt. True if it was played here.
	 */
	public static boolean voice(Entity speaker, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
		if (!(speaker instanceof Villager) || !(speaker.level() instanceof ServerLevel level) || speaker.isSilent()) {
			return false;
		}
		net.minecraft.resources.Identifier key = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getKey(sound);
		if (key == null || !key.getNamespace().equals("mca") || !key.getPath().startsWith("villager.")) {
			return false;
		}
		net.minecraft.sounds.SoundEvent grunt = GRUNTS.get(key.getPath().substring(key.getPath().lastIndexOf('.') + 1));
		if (grunt == null || of(speaker) == null) {
			return false;
		}
		double range = sound.getRange(volume);
		long seed = level.getRandom().nextLong();
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(speaker) > range * range) {
				continue;
			}
			net.minecraft.sounds.SoundEvent heard = understands(p, speaker) ? sound : grunt;
			p.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
				net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(heard), speaker.getSoundSource(),
				speaker.getX(), speaker.getY(), speaker.getZ(), volume, pitch, seed));
		}
		return true;
	}

	/** {@link #understands}, and if not, a reminder over the hotbar (now and then) that only simple things will do. */
	public static boolean check(ServerPlayer player, Entity speaker) {
		if (understands(player, speaker)) {
			return true;
		}
		remind(player, speaker, true);
		return false;
	}

	private static void remind(ServerPlayer player, Entity speaker, boolean force) {
		Tongue t = of(speaker);
		if (t == null) {
			return;
		}
		int now = player.level().getServer().getTickCount();
		Integer last = REMINDED.get(player.getUUID());
		if (!force && last != null && now - last < REMIND_TICKS) {
			return;
		}
		REMINDED.put(player.getUUID(), now);
		player.sendOverlayMessage(Component.translatable("language.jaymoskingdoms.foreign", t.title(), percent(player, t))
			.withStyle(ChatFormatting.GRAY));
	}

	/** Whether answering {@code answer} to MCA's {@code question} needs no words. */
	public static boolean simpleDialogue(@Nullable String question, @Nullable String answer) {
		if (question == null) {
			return true;
		}
		if ("main".equals(question)) {
			return answer != null && SIMPLE_ANSWERS.contains(answer);
		}
		int dot = question.indexOf('.');
		String head = dot < 0 ? question : question.substring(0, dot);
		return SIMPLE_QUESTIONS.contains(question) || (!"first".equals(head) && SIMPLE_QUESTIONS.contains(head));
	}

	// ---- what a foreigner hears ------------------------------------------------------------------------------------

	/**
	 * {@code words}, as {@code player} hears them from {@code speaker}: unchanged if they understand each other, else
	 * gibberish in the speaker's tongue (the same line always comes out the same).
	 */
	public static Component heard(ServerPlayer player, Entity speaker, Component words) {
		if (understands(player, speaker)) {
			return words;
		}
		remind(player, speaker, false);
		return garble(of(speaker), speaker.getUUID().hashCode() * 31L + words.toString().hashCode());
	}

	/** A line of gibberish in {@code t}. */
	static Component garble(@Nullable Tongue t, long seed) {
		Tongue tongue = t == null ? Tongue.COMMON : t;
		RandomSource random = RandomSource.create(seed);
		StringBuilder line = new StringBuilder();
		int words = WORDS + random.nextInt(WORDS_SPREAD);
		for (int w = 0; w < words; w++) {
			StringBuilder word = new StringBuilder();
			int n = SYLLABLES + random.nextInt(SYLLABLES_SPREAD);
			for (int s = 0; s < n; s++) {
				word.append(tongue.syllables[random.nextInt(tongue.syllables.length)]);
			}
			if (w == 0) {
				word.setCharAt(0, Character.toUpperCase(word.charAt(0)));
			}
			line.append(w == 0 ? "" : random.nextInt(6) == 0 ? ", " : " ").append(word);
		}
		line.append(random.nextInt(4) == 0 ? "?" : random.nextInt(3) == 0 ? "!" : ".");
		return Component.literal(line.toString()).withStyle(ChatFormatting.ITALIC);
	}

	/** {@code speaker} answers a foreigner who tried something that takes words: a shrug, and words they don't know. */
	public static void shrug(ServerPlayer player, Entity speaker) {
		if (!(speaker.level() instanceof ServerLevel level)) {
			return;
		}
		Speech.say(level, speaker, Component.translatable("language.jaymoskingdoms.shrug"), Speech.WHITE, 12.0);
		Tongue t = of(speaker);
		if (t != null) {
			REMINDED.put(player.getUUID(), level.getServer().getTickCount());
			player.sendOverlayMessage(Component.translatable("language.jaymoskingdoms.shrugged", speaker.getDisplayName(), t.title(), percent(player, t))
				.withStyle(ChatFormatting.GRAY));
		}
		if (speaker instanceof LivingEntity) {
			level.playSound(null, speaker.getX(), speaker.getY(), speaker.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8F, 1.0F);
		}
	}

	// ---- learning ------------------------------------------------------------------------------------------------------

	/** {@code player} learns {@code points} of {@code t} (told at each step, and when they speak it). */
	public static int learn(ServerPlayer player, Tongue t, int points) {
		int before = points(player, t);
		if (before >= NATIVE || points <= 0) {
			return before;
		}
		int after = Math.min(NATIVE, before + points);
		Map<String, Integer> learned = new HashMap<>(player.getAttachedOrElse(LEARNED, Map.of()));
		learned.put(t.id(), after);
		player.setAttached(LEARNED, learned);
		if (after >= NATIVE) {
			player.sendSystemMessage(Component.translatable("language.jaymoskingdoms.native", t.title()).withStyle(ChatFormatting.GOLD));
		} else if (after >= FLUENT && before < FLUENT) {
			player.sendSystemMessage(Component.translatable("language.jaymoskingdoms.fluent", t.title()).withStyle(ChatFormatting.GOLD));
			player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.2F);
		} else if (after < FLUENT && after / PROGRESS_STEP > before / PROGRESS_STEP) {
			player.sendOverlayMessage(Component.translatable("language.jaymoskingdoms.progress", t.title(), after).withStyle(ChatFormatting.AQUA));
		}
		return after;
	}

	/** (From the trade mixin) a trade with {@code villager}: a few more words of their tongue. */
	public static void traded(Villager villager) {
		if (villager.getTradingPlayer() instanceof ServerPlayer player && !player.isCreative()) {
			Tongue t = of(villager);
			if (t != null && t != home(player)) {
				learn(player, t, TRADE_POINTS);
			}
		}
	}

	/** Every so long, each player among speakers of a tongue they don't know picks up a little of it. */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % IMMERSION_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || player.isCreative() || !(player.level() instanceof ServerLevel level)) {
				continue;
			}
			List<Mob> near = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(IMMERSION_RANGE),
				e -> (e instanceof Villager || e instanceof Folk) && e.isAlive() && !e.isBaby());
			near.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(player)));
			for (Mob m : near) {
				Tongue t = of(m);
				if (t != null && points(player, t) < NATIVE) {
					int before = points(player, t);
					int after = learn(player, t, IMMERSION_POINTS);
					remark(level, player, m, before, after);
					break;
				}
			}
		}
	}

	/**
	 * Now and then, {@code speaker} (one of the folk whose tongue {@code player} is learning) remarks on their accent, in
	 * words simple enough to follow; and once, when they first sound native, says so.
	 */
	private static void remark(ServerLevel level, ServerPlayer player, Mob speaker, int before, int after) {
		if (speaker.distanceTo(player) > Approaches.SPEECH_RANGE || !speaker.hasLineOfSight(player) || speaker.isSleeping() || speaker.getTarget() != null) {
			return;
		}
		int now = level.getServer().getTickCount();
		Component line;
		if (after >= NATIVE && before < NATIVE) {
			line = Component.translatable("language.jaymoskingdoms.accent.native." + level.getRandom().nextInt(NATIVE_LINES), player.getName());
		} else {
			Integer last = REMARKED.get(player.getUUID());
			if (after < ACCENT_FROM || after >= NATIVE || (last != null && now - last < ACCENT_GAP) || level.getRandom().nextFloat() > ACCENT_CHANCE) {
				return;
			}
			int tier = 0;
			while (tier + 1 < ACCENT_TIERS.length && after >= ACCENT_TIERS[tier + 1]) {
				tier++;
			}
			line = Component.translatable("language.jaymoskingdoms.accent." + ACCENTS[tier] + "." + level.getRandom().nextInt(ACCENT_LINES), player.getName());
		}
		REMARKED.put(player.getUUID(), now);
		// (said slowly and simply, to you: you follow it even before you speak the tongue)
		Speech.sayTo(level, speaker, player, line, Speech.WHITE);
		speaker.getLookControl().setLookAt(player, 30.0F, 30.0F);
		level.playSound(null, speaker.getX(), speaker.getY(), speaker.getZ(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 0.8F, 1.0F);
	}

	// ---- /kingdom language -------------------------------------------------------------------------------------------

	static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("language").executes(Languages::show)
			// (ops) teach a player a tongue, or take it back, by points
			.then(Commands.literal("set").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("player", EntityArgument.player())
					.then(Commands.argument("tongue", StringArgumentType.word()).suggests((c, b) -> {
						for (Tongue t : Tongue.values()) {
							b.suggest(t.id());
						}
						return b.buildFuture();
					}).then(Commands.argument("points", IntegerArgumentType.integer(0, NATIVE)).executes(Languages::set)))));
	}

	private static int show(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Tongue home = home(player);
		c.getSource().sendSuccess(() -> Component.translatable("language.jaymoskingdoms.command.home", home.title()).withStyle(ChatFormatting.GOLD), false);
		for (Tongue t : Tongue.values()) {
			if (t != home) {
				int p = points(player, t);
				String key = p >= NATIVE ? "native" : p >= FLUENT ? "fluent" : "learning";
				c.getSource().sendSuccess(() -> Component.translatable("language.jaymoskingdoms.command." + key, t.title(), p)
					.withStyle(p >= FLUENT ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
			}
		}
		return 1;
	}

	private static int set(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(c, "player");
		Tongue t = Tongue.parse(StringArgumentType.getString(c, "tongue").toLowerCase(Locale.ROOT));
		if (t == null) {
			c.getSource().sendFailure(Component.translatable("language.jaymoskingdoms.command.unknown"));
			return 0;
		}
		int points = IntegerArgumentType.getInteger(c, "points");
		Map<String, Integer> learned = new HashMap<>(player.getAttachedOrElse(LEARNED, Map.of()));
		learned.put(t.id(), points);
		player.setAttached(LEARNED, learned);
		c.getSource().sendSuccess(() -> Component.translatable("language.jaymoskingdoms.command.set", player.getDisplayName(), t.title(), points), true);
		return 1;
	}
}
