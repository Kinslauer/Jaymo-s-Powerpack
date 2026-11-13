package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Coinage;
import com.jaymoskingdoms.money.Money;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.illager.Illusioner;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Talking with illagers, once you know enough of their tongue ({@link IllagerTongue#TALK_FROM}): MCA's way of talking
 * with villagers, made for them (Andy, 2026-11-13). Sneak and use on one (or use with an empty hand) and a talk card opens.
 * <ul>
 * <li><b>Who they are.</b> Each illager has a name (theirs to give, {@link #askName}: then it shows over their head to
 * you alone, as a villager's does), a nature ({@link #NATURES}), a mood that moves with how they're treated, and hearts
 * for each player (how they regard you). The card's head shows their name (or what they are), how they regard you and
 * their mood.</li>
 * <li><b>Learning the tongue doesn't make them friendly.</b> An illager is hostile until something changes that. Speak to
 * a hostile one and it holds its hand while it hears you out ({@link #HEAR_TICKS}); then it is a parley: <b>calm</b> them
 * (a chance, by your Presence, their nature, mood and regard), <b>pay them off</b> (a price by who they are and how many
 * are with them), <b>bluff</b> (only with a good command of the tongue, {@link IllagerTongue#BLUFF_FROM}), or walk away.
 * Won, they and their own near them hold off you for a while (a truce); lost, they come for you and won't hear you again
 * for a time. Strike any of them and every truce near is off. Those who come to regard you highly
 * ({@link #TRUSTED}) leave you be on their own, until you strike one of theirs.</li>
 * <li><b>At peace</b> (in a truce, or trusted), the card offers: their name, a chat (once a day), what they want (each
 * wants something by nature; bring it for their thanks, {@link #WANTS}), a gift of what you hold, trade (a few goods by
 * who they are, and iron bought), word of their hold (who leads, captives, where another hold lies), Talk Them Round
 * (persuade, bluff, threaten), and goodbye.</li>
 * <li><b>One attitude a conversation.</b> How they take you is worked out when the card opens and holds for that talk
 * (Andy's rule): only what you do in it moves it.</li>
 * </ul>
 * Their lines come through {@link Speech} like anyone's, so you see them in their tongue as far as you know it.
 */
public final class IllagerTalk {
	/** Their natures (lang {@code illager_talk.jaymoskingdoms.nature.<n>}). */
	static final List<String> NATURES = List.of("brutal", "greedy", "proud", "cunning", "zealous", "weary", "superstitious", "craven");
	/** What each nature wants (item ids). */
	static final Map<String, Item> WANTS = Map.of("brutal", Items.IRON_AXE, "greedy", Items.GOLD_INGOT, "proud", Items.DIAMOND,
		"cunning", Items.SPYGLASS, "zealous", Items.AMETHYST_SHARD, "weary", Items.BREAD, "superstitious", Items.RABBIT_FOOT, "craven", Items.SHIELD);
	/** Hearts run from -100 to 100; mood from -15 to 15. */
	static final int HEARTS_MIN = -100, HEARTS_MAX = 100, MOOD_MIN = -15, MOOD_MAX = 15;
	/** Regard steps: below these they hate you, are hostile, wary, civil; from {@link #TRUSTED} they leave you be. */
	static final int HATED = -40, COLD = -10, CIVIL = 10, TRUSTED = 40;
	/** How long a hostile one holds its hand while hearing you out (ticks), and how far its own count (blocks). */
	static final int HEAR_TICKS = 20 * 25;
	static final double BAND_RANGE = 24.0;
	/** Truces (ticks): calmed, paid off, bluffed, let walk away. */
	static final long CALM_TRUCE = 20 * 60 * 5, PAID_TRUCE = 24000L, BLUFF_TRUCE = 20 * 60 * 3, WALK_TRUCE = 20 * 8;
	/** After a lost parley, or a blow, they won't hear you for this long (ticks). */
	static final long REFUSE_TICKS = 20 * 120;
	/** The chances: calm, bluff, persuade, threaten; and what leans them. */
	static final float CALM_BASE = 0.35F, BLUFF_BASE = 0.3F, PERSUADE_BASE = 0.4F, THREATEN_BASE = 0.25F;
	static final float MOOD_LEAN = 0.012F, HEARTS_LEAN = 0.003F, TONGUE_LEAN = 0.002F, LIED_LEAN = -0.25F, MIN_CHANCE = 0.05F, MAX_CHANCE = 0.9F;
	/** A pay-off: by who they are (emeralds' worth), and one more for each of their own near (at most). */
	static final int PAY_PILLAGER = 3, PAY_VINDICATOR = 4, PAY_EVOKER = 8, PAY_WARLORD = 24, PAY_PER_BAND = 1, PAY_BAND_MOST = 10;
	/** Hearts: a good chat, a bad one, a want brought, a gift they like, any gift, a coin, a calm won, a parley lost, a blow, a threat. */
	static final int CHAT_HEARTS = 3, CHAT_LOST = -1, WANT_HEARTS = 15, LIKED_GIFT = 4, GIFT = 1, COIN_GIFT = 2, CALM_HEARTS = 4, LOST_HEARTS = -4,
		STRUCK_HEARTS = -10, STRUCK_BAND = -3, THREATEN_HEARTS = -10, PERSUADE_HEARTS = 5;
	/** A want brought: what they hand you (emeralds' worth), and how long until they want something again (days). */
	static final int WANT_REWARD = 3, WANT_DAYS = 3;
	/** What a threatened one hands over (emeralds' worth, at least plus up to) and how long it runs (ticks). */
	static final int PURSE_MIN = 2, PURSE_SPREAD = 4, FRIGHT_TICKS = 400;
	/** Prices: those who trust you sell for less, those who don't for more; the greedy for more; a persuaded one for less today. */
	static final float TRUSTED_PRICE = 0.8F, COLD_PRICE = 1.3F, GREEDY_PRICE = 1.2F, PERSUADED_PRICE = 0.85F;
	/**
	 * Disarmed and spared (Andy, 2026-11-13: "disarmed illagers that dont attack you will speak, and will improve standing if
	 * you spare them after downing"). An illager seen armed whose hands are now empty is disarmed ({@link #ARMED_TAG}); unless
	 * brutal or proud ({@link #FIGHTS_ON}), it yields: it attacks no one, calls out to a player near ({@link #YIELD_GAP} between
	 * calls), and talks at peace. One downed by players and left to get back up is spared: each who struck it gains
	 * {@link #SPARED_HEARTS} with it (and {@link #SPARED_BAND} with its own near), and it keeps a truce with them.
	 */
	public static final String ARMED_TAG = "jaymoskingdoms.illager_armed";
	static final java.util.Set<String> FIGHTS_ON = java.util.Set.of("brutal", "proud");
	static final int YIELD_GAP = 20 * 45, SPARED_HEARTS = 25, SPARED_BAND = 5;
	static final double YIELD_RANGE = 10.0, WATCH_RANGE = 32.0;
	/** How long the players who struck an illager are remembered for its sparing (ticks since the last blow). */
	static final int DOWNERS_TICKS = 20 * 60 * 5;
	/** Their colour on the card's head. */
	static final int HEAD_COLOR = 0xB8C4A0;
	/** A conversation lapses this long after it opened (ticks). */
	static final int TALK_TICKS = 20 * 90;
	/** Their names: a start and an end. */
	static final String[] NAME_START = {"Vor", "Grek", "Mal", "Thask", "Ozr", "Kel", "Durn", "Hask", "Zev", "Brak", "Orl", "Sev", "Varn", "Ruk",
		"Ezk", "Mord", "Ulv", "Garth", "Isk", "Rov"};
	static final String[] NAME_END = {"ik", "ath", "osk", "un", "eth", "ar", "ix", "ulm", "esh", "or", "ak", "ev", "ugh", "ard"};
	/** Lines per kind of line (lang {@code illager_talk.jaymoskingdoms.line.<kind>.<n>}). */
	static final int LINES = 3;

	/** One illager's self: name, nature, mood, what it wants and since when, and per player (UUID string): hearts, truce till, last chat day, lied. */
	record Self(String name, String nature, int mood, String want, long wantDay, Map<String, Integer> hearts, Map<String, Long> truce,
		Map<String, Long> chatted, List<String> lied) {
		static final Codec<Self> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("name").forGetter(Self::name),
				Codec.STRING.fieldOf("nature").forGetter(Self::nature),
				Codec.INT.optionalFieldOf("mood", 0).forGetter(Self::mood),
				Codec.STRING.optionalFieldOf("want", "").forGetter(Self::want),
				Codec.LONG.optionalFieldOf("want_day", 0L).forGetter(Self::wantDay),
				Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("hearts", Map.of()).forGetter(Self::hearts),
				Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("truce", Map.of()).forGetter(Self::truce),
				Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("chatted", Map.of()).forGetter(Self::chatted),
				Codec.STRING.listOf().optionalFieldOf("lied", List.of()).forGetter(Self::lied))
			.apply(i, Self::new));

		int heartsOf(UUID player) {
			return this.hearts.getOrDefault(player.toString(), 0);
		}

		Self withHearts(UUID player, int delta) {
			Map<String, Integer> h = new HashMap<>(this.hearts);
			h.put(player.toString(), Math.max(HEARTS_MIN, Math.min(HEARTS_MAX, heartsOf(player) + delta)));
			return new Self(this.name, this.nature, this.mood, this.want, this.wantDay, Map.copyOf(h), this.truce, this.chatted, this.lied);
		}

		Self withMood(int delta) {
			return new Self(this.name, this.nature, Math.max(MOOD_MIN, Math.min(MOOD_MAX, this.mood + delta)), this.want, this.wantDay, this.hearts,
				this.truce, this.chatted, this.lied);
		}

		Self withTruce(UUID player, long until) {
			Map<String, Long> t = new HashMap<>(this.truce);
			if (until <= 0L) {
				t.remove(player.toString());
			} else {
				t.put(player.toString(), until);
			}
			return new Self(this.name, this.nature, this.mood, this.want, this.wantDay, this.hearts, Map.copyOf(t), this.chatted, this.lied);
		}

		Self withChatted(UUID player, long day) {
			Map<String, Long> c = new HashMap<>(this.chatted);
			c.put(player.toString(), day);
			return new Self(this.name, this.nature, this.mood, this.want, this.wantDay, this.hearts, this.truce, Map.copyOf(c), this.lied);
		}

		Self withWant(String want, long day) {
			return new Self(this.name, this.nature, this.mood, want, day, this.hearts, this.truce, this.chatted, this.lied);
		}

		Self withLied(UUID player) {
			List<String> l = new ArrayList<>(this.lied);
			if (!l.contains(player.toString())) {
				l.add(player.toString());
			}
			return new Self(this.name, this.nature, this.mood, this.want, this.wantDay, this.hearts, this.truce, this.chatted, List.copyOf(l));
		}
	}

	/** On an illager: its self, made the first time anyone deals with it. Saved. */
	static final AttachmentType<Self> SELF = AttachmentRegistry.create(JaymosKingdoms.id("illager_self"), builder -> builder.persistent(Self.CODEC));
	/** On an illager: it has told someone its name (so names are kept from strangers, {@link Introductions#named}). Saved, synced to all. */
	public static final AttachmentType<Boolean> NAMED = AttachmentRegistry.create(JaymosKingdoms.id("illager_named"),
		builder -> builder.persistent(Codec.BOOL).syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** How an illager takes you in a conversation. */
	enum Attitude {
		HOSTILE, WARY, CIVIL, FRIENDLY;

		String key() {
			return this.name().toLowerCase(Locale.ROOT);
		}
	}

	/** A conversation: whom with, how they take you, when it opened (server tick). */
	private record Talk(int illager, Attitude attitude, int opened) {
	}

	/** A hostile one hearing a player out: which player, until when (server tick). */
	private record Hearing(UUID player, int until) {
	}

	private static final Map<UUID, Talk> TALKS = new HashMap<>();
	private static final Map<Entity, Hearing> HEARING = new java.util.WeakHashMap<>();
	/** "illager|player" → when they'll hear the player again (game time). */
	private static final Map<String, Long> REFUSING = new HashMap<>();
	/** "illager|player" → the day a persuade was won (cheaper trade that day), and the day each move was tried. */
	private static final Map<String, Long> PERSUADED = new HashMap<>(), TRIED = new HashMap<>();
	/** "f:camp|player" → until when a camp's beasts keep its truce with a player (game time). */
	private static final Map<String, Long> SIDE_TRUCE = new HashMap<>();
	/** An illager players have struck: who, when last, and whether it has been down since. */
	private static final class Downers {
		final java.util.Set<UUID> players = new java.util.HashSet<>();
		int last;
		boolean down;
	}

	private static final Map<AbstractIllager, Downers> DOWNERS = new java.util.WeakHashMap<>();
	/** "illager|player" → when a yielding illager last called out to that player (server tick). */
	private static final Map<String, Integer> CALLED = new HashMap<>();

	private IllagerTalk() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("illager talk attachments: {} {}", SELF, NAMED);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || world.isClientSide() || !(player instanceof ServerPlayer sp) || sp.isSpectator()
				|| !IllagerTongue.speaker(entity) || !(entity instanceof AbstractIllager illager)
				|| !(sp.isShiftKeyDown() || sp.getMainHandItem().isEmpty()) || Downed.isDowned(illager) || Captives.bound(illager)) {
				return InteractionResult.PASS;
			}
			open(sp, illager);
			return InteractionResult.SUCCESS;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> struck(victim, source));
		ServerTickEvents.END_SERVER_TICK.register(IllagerTalk::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TALKS.clear();
			HEARING.clear();
			REFUSING.clear();
			PERSUADED.clear();
			TRIED.clear();
			SIDE_TRUCE.clear();
			DOWNERS.clear();
			CALLED.clear();
		});
	}

	// ---- who they are ------------------------------------------------------------------------------------------------

	/** {@code illager}'s self, made now if it has none. */
	static Self self(AbstractIllager illager) {
		Self s = illager.getAttached(SELF);
		if (s == null) {
			RandomSource random = illager.getRandom();
			String name = NAME_START[random.nextInt(NAME_START.length)] + NAME_END[random.nextInt(NAME_END.length)];
			String nature = NATURES.get(random.nextInt(NATURES.size()));
			s = new Self(name, nature, random.nextInt(11) - 5, "", 0L, Map.of(), Map.of(), Map.of(), List.of());
			illager.setAttached(SELF, s);
		}
		return s;
	}

	private static Self put(AbstractIllager illager, Self s) {
		illager.setAttached(SELF, s);
		return s;
	}

	/** (Introductions) whether {@code e} is an illager who has a name that's kept from strangers. */
	public static boolean named(Entity e) {
		return e instanceof AbstractIllager && e.getAttachedOrElse(NAMED, false);
	}

	/** What {@code wants} is, wanted now (a new want is picked once the last was met and {@link #WANT_DAYS} have passed). */
	private static Item want(AbstractIllager illager, Self s, long day) {
		Item item = s.want().isEmpty() ? null : BuiltInRegistries.ITEM.getValue(Identifier.parse(s.want()));
		if (item == null || item == Items.AIR) {
			if (day < s.wantDay()) {
				return Items.AIR;
			}
			item = WANTS.getOrDefault(s.nature(), Items.BREAD);
			put(illager, s.withWant(BuiltInRegistries.ITEM.getKey(item).toString(), day));
		}
		return item;
	}

	static Attitude attitude(AbstractIllager illager, Self s, ServerPlayer player) {
		int hearts = s.heartsOf(player.getUUID());
		if (!atPeace(illager, s, player)) {
			return Attitude.HOSTILE;
		}
		return hearts >= TRUSTED ? Attitude.FRIENDLY : hearts >= CIVIL ? Attitude.CIVIL : Attitude.WARY;
	}

	/** Whether {@code illager} leaves {@code player} be: a truce runs, or it trusts them (and no blow of theirs is fresh). */
	static boolean atPeace(AbstractIllager illager, Self s, ServerPlayer player) {
		long now = illager.level().getGameTime();
		Long until = s.truce().get(player.getUUID().toString());
		if (until != null && until > now) {
			return true;
		}
		if (yields(illager, s)) {
			return true;
		}
		Long refusing = REFUSING.get(illager.getUUID() + "|" + player.getUUID());
		return s.heartsOf(player.getUUID()) >= TRUSTED && (refusing == null || refusing <= now);
	}

	/** Seen armed, and its hands empty now. */
	public static boolean disarmed(AbstractIllager illager) {
		return illager.entityTags().contains(ARMED_TAG) && illager.getMainHandItem().isEmpty() && illager.getOffhandItem().isEmpty();
	}

	/** Disarmed and not one to fight on bare-handed: it attacks no one, and talks at peace. */
	static boolean yields(AbstractIllager illager, Self s) {
		return disarmed(illager) && !FIGHTS_ON.contains(s.nature());
	}

	/** (ThrallTargetMixin) an illager hearing {@code target} out, or at peace with them, doesn't go for them. */
	public static boolean spares(LivingEntity attacker, LivingEntity target) {
		if (!(target instanceof ServerPlayer player)) {
			return false;
		}
		if (attacker instanceof net.minecraft.world.entity.monster.Vex vex && vex.getOwner() instanceof AbstractIllager owner) {
			// (an evoker's vexes keep its word)
			attacker = owner;
		} else if (!(attacker instanceof AbstractIllager)) {
			// (a camp's ravager keeps its camp's truce)
			if (!(attacker instanceof net.minecraft.world.entity.monster.Ravager) || SIDE_TRUCE.isEmpty()) {
				return false;
			}
			String side = Comrades.side(attacker);
			Long until = side.startsWith("f:") ? SIDE_TRUCE.get(side + "|" + player.getUUID()) : null;
			return until != null && until > attacker.level().getGameTime();
		}
		AbstractIllager illager = (AbstractIllager)attacker;
		Hearing h = HEARING.get(illager);
		if (h != null && h.player().equals(player.getUUID()) && h.until() > player.level().getServer().getTickCount()) {
			return true;
		}
		Self s = illager.getAttached(SELF);
		if (s == null && disarmed(illager)) {
			s = self(illager);
		}
		return s != null && atPeace(illager, s, player);
	}

	/** The card's head: their name if you know it (else what they are), their nature, how they take you, their mood. */
	private static Component head(AbstractIllager illager, Self s, ServerPlayer player, Attitude attitude) {
		Component who = Introductions.knows(player, illager) && named(illager) ? Component.literal(s.name()) : illager.getType().getDescription();
		return Component.translatable("illager_talk.jaymoskingdoms.head", who, Component.translatable("illager_talk.jaymoskingdoms.nature." + s.nature()),
			Component.translatable("illager_talk.jaymoskingdoms.attitude." + attitude.key()),
			Component.translatable("illager_talk.jaymoskingdoms.mood." + moodStep(s.mood()))).withColor(HEAD_COLOR);
	}

	/** Mood in five steps: 0 spiteful .. 4 pleased. */
	static int moodStep(int mood) {
		return mood <= -9 ? 0 : mood <= -3 ? 1 : mood < 3 ? 2 : mood < 9 ? 3 : 4;
	}

	/** A line of theirs (lang {@code illager_talk.jaymoskingdoms.line.<kind>.<n>}). */
	private static Component line(String kind, RandomSource random, Object... args) {
		return Component.translatable("illager_talk.jaymoskingdoms.line." + kind + "." + random.nextInt(LINES), args);
	}

	/** {@code illager} says {@code words} to {@code player} (in their tongue, as far as each listener knows it). */
	private static void say(ServerLevel level, AbstractIllager illager, ServerPlayer player, Component words, int color) {
		Speech.tell(level, illager, player, words, color);
		illager.getLookControl().setLookAt(player, 30.0F, 30.0F);
	}

	// ---- the talk card ------------------------------------------------------------------------------------------------

	/** {@code player} speaks to {@code illager}. */
	static void open(ServerPlayer player, AbstractIllager illager) {
		if (!(illager.level() instanceof ServerLevel level)) {
			return;
		}
		int known = IllagerTongue.points(player);
		RandomSource random = level.getRandom();
		if (known < IllagerTongue.TALK_FROM) {
			// (they answer in their tongue, and you can't make yourself understood)
			say(level, illager, player, line("unheard", random), IllagerTongue.COLOR);
			player.sendOverlayMessage(Component.translatable("illager_talk.jaymoskingdoms.too_little", IllagerTongue.describe(known)).withStyle(ChatFormatting.GRAY));
			return;
		}
		Self s = self(illager);
		long now = level.getGameTime();
		Long refusing = REFUSING.get(illager.getUUID() + "|" + player.getUUID());
		Attitude attitude = attitude(illager, s, player);
		if (attitude == Attitude.HOSTILE && refusing != null && refusing > now) {
			say(level, illager, player, line("refuse", random), Speech.RED);
			return;
		}
		int tick = level.getServer().getTickCount();
		TALKS.put(player.getUUID(), new Talk(illager.getId(), attitude, tick));
		if (attitude == Attitude.HOSTILE) {
			HEARING.put(illager, new Hearing(player.getUUID(), tick + HEAR_TICKS));
			if (illager.getTarget() == player) {
				illager.setTarget(null);
			}
			illager.getNavigation().stop();
			parley(player, illager, s);
		} else {
			main(player, illager, s, attitude);
		}
	}

	/** The conversation {@code player} has open with {@code illager}, or null when it lapsed or is with someone else. */
	private static @Nullable Talk talk(ServerPlayer player, AbstractIllager illager) {
		Talk t = TALKS.get(player.getUUID());
		if (t == null || t.illager() != illager.getId() || player.level().getServer().getTickCount() - t.opened() > TALK_TICKS || !illager.isAlive()
			|| Downed.isDowned(illager)) {
			return null;
		}
		return t;
	}

	/** The parley with a hostile one: calm them, pay them off, bluff, or walk away. */
	private static void parley(ServerPlayer player, AbstractIllager illager, Self s) {
		ServerLevel level = (ServerLevel)illager.level();
		RandomSource random = level.getRandom();
		int price = price(illager, s);
		long milli = Money.worth(price);
		boolean canBluff = IllagerTongue.points(player) >= IllagerTongue.BLUFF_FROM;
		List<Menus.Choice> choices = new ArrayList<>();
		choices.add(Menus.Choice.of(Component.translatable("illager_talk.jaymoskingdoms.parley.calm"), p -> calm(p, illager)));
		choices.add(Menus.Choice.of(Component.translatable("illager_talk.jaymoskingdoms.parley.pay", Money.text(player, milli)), Money.canPay(player, milli),
			p -> pay(p, illager, price)));
		choices.add(Menus.Choice.of(Component.translatable(canBluff ? "illager_talk.jaymoskingdoms.parley.bluff" : "illager_talk.jaymoskingdoms.parley.bluff_cant"),
			canBluff, p -> bluff(p, illager)));
		choices.add(Menus.Choice.of(Component.translatable("illager_talk.jaymoskingdoms.parley.walk"), p -> walk(p, illager)));
		Menus.ask(player, illager, head(illager, s, player, Attitude.HOSTILE), IllagerTongue.heard(player, illager, line("parley." + s.nature(), random)), choices);
		level.playSound(null, illager.getX(), illager.getY(), illager.getZ(), SoundEvents.VINDICATOR_AMBIENT, SoundSource.HOSTILE, 0.8F, 0.9F);
	}

	/** The card at peace. */
	private static void main(ServerPlayer player, AbstractIllager illager, Self s, Attitude attitude) {
		ServerLevel level = (ServerLevel)illager.level();
		RandomSource random = level.getRandom();
		long day = level.getGameTime() / 24000L;
		List<Menus.Choice> choices = new ArrayList<>();
		if (!(Introductions.knows(player, illager) && named(illager))) {
			choices.add(Menus.Choice.of(Component.translatable("illager_talk.jaymoskingdoms.ask_name"), p -> askName(p, illager)));
		}
		Long chatted = s.chatted().get(player.getUUID().toString());
		choices.add(Menus.Choice.of(Component.translatable("illager_talk.jaymoskingdoms.chat"), chatted == null || chatted < day, p -> chat(p, illager)));
		choices.add(Menus.Choice.of(Component.translatable("illager_talk.jaymoskingdoms.ask_want"), p -> askWant(p, illager)));
		ItemStack held = player.getMainHandItem();
		choices.add(Menus.Choice.of(held.isEmpty() ? Component.translatable("illager_talk.jaymoskingdoms.give_none")
			: Component.translatable("illager_talk.jaymoskingdoms.give", held.getHoverName()), !held.isEmpty(), p -> give(p, illager)));
		choices.add(Menus.Choice.of(Component.translatable("illager_talk.jaymoskingdoms.trade"), p -> trade(p, illager)));
		choices.add(Menus.Choice.of(Component.translatable("illager_talk.jaymoskingdoms.hold"), p -> hold(p, illager)));
		choices.add(Menus.Choice.of(Component.translatable("illager_talk.jaymoskingdoms.round"), p -> round(p, illager)));
		choices.add(Menus.Choice.of(Component.translatable("illager_talk.jaymoskingdoms.leave"), p -> leave(p, illager)));
		Menus.ask(player, illager, head(illager, s, player, attitude), IllagerTongue.heard(player, illager, line("greet." + attitude.key(), random)), choices);
	}

	/** Back to the card at peace, in the same conversation (same attitude). */
	private static void back(ServerPlayer player, AbstractIllager illager) {
		Talk t = talk(player, illager);
		if (t != null && t.attitude() != Attitude.HOSTILE && atPeace(illager, self(illager), player)) {
			main(player, illager, self(illager), t.attitude());
		}
	}

	// ---- the parley -----------------------------------------------------------------------------------------------------

	/** The price of letting you be: by who they are and how many of their own are near; the greedy ask less, the proud more. */
	static int price(AbstractIllager illager, Self s) {
		Illagers.Tag tag = Illagers.tag(illager);
		int base = tag != null && tag.role() == Illagers.WARLORD ? PAY_WARLORD
			: illager instanceof Evoker || illager instanceof Illusioner ? PAY_EVOKER : illager instanceof Pillager ? PAY_PILLAGER : PAY_VINDICATOR;
		int band = Math.min(PAY_BAND_MOST, band(illager).size() - 1);
		float f = s.nature().equals("greedy") ? 0.75F : s.nature().equals("proud") ? 1.5F : 1.0F;
		if (s.mood() <= -9) {
			f *= 1.25F;
		}
		return Math.max(1, Math.round((base + band * PAY_PER_BAND) * f));
	}

	/** {@code illager} and its own near it (its camp, or the illagers about it if it has none). */
	static List<AbstractIllager> band(AbstractIllager illager) {
		String side = Comrades.side(illager);
		return illager.level().getEntitiesOfClass(AbstractIllager.class, illager.getBoundingBox().inflate(BAND_RANGE),
			e -> IllagerTongue.speaker(e) && Comrades.side(e).equals(side));
	}

	/** A truce with {@code player} for {@code illager}'s band, till {@code until}. */
	private static void truce(AbstractIllager illager, ServerPlayer player, long until) {
		String side = Comrades.side(illager);
		if (side.startsWith("f:")) {
			SIDE_TRUCE.put(side + "|" + player.getUUID(), until);
		}
		for (AbstractIllager e : band(illager)) {
			put(e, self(e).withTruce(player.getUUID(), until));
			if (e.getTarget() == player) {
				e.setTarget(null);
			}
		}
		HEARING.remove(illager);
	}

	/** The parley lost: they come for you, and won't hear you for a while. */
	private static void broken(ServerLevel level, AbstractIllager illager, ServerPlayer player) {
		HEARING.remove(illager);
		TALKS.remove(player.getUUID());
		REFUSING.put(illager.getUUID() + "|" + player.getUUID(), level.getGameTime() + REFUSE_TICKS);
		if (!player.isCreative()) {
			illager.setTarget(player);
		}
		level.playSound(null, illager.getX(), illager.getY(), illager.getZ(), SoundEvents.VINDICATOR_CELEBRATE, SoundSource.HOSTILE, 1.0F, 0.8F);
	}

	/** The nature's lean on a move (calm, bluff, persuade, threaten). */
	static float nature(String nature, String move) {
		return switch (nature) {
			case "weary" -> move.equals("calm") || move.equals("persuade") ? 0.2F : 0.0F;
			case "craven" -> move.equals("threaten") ? 0.25F : move.equals("bluff") ? 0.15F : 0.1F;
			case "brutal" -> move.equals("calm") || move.equals("persuade") ? -0.15F : move.equals("threaten") ? -0.2F : 0.0F;
			case "zealous" -> move.equals("bluff") ? -0.05F : -0.1F;
			case "proud" -> move.equals("threaten") ? -0.15F : -0.05F;
			case "cunning" -> move.equals("bluff") ? -0.2F : 0.0F;
			case "superstitious" -> move.equals("bluff") ? 0.15F : 0.0F;
			case "greedy" -> move.equals("persuade") ? -0.1F : 0.0F;
			default -> 0.0F;
		};
	}

	/** The chance {@code player}'s {@code move} on {@code illager} works now. */
	static float chance(ServerPlayer player, AbstractIllager illager, Self s, String move, float base) {
		float c = base + Presence.talk(player) + nature(s.nature(), move) + s.mood() * MOOD_LEAN + s.heartsOf(player.getUUID()) * HEARTS_LEAN
			* (move.equals("threaten") ? -0.3F : 1.0F) + (IllagerTongue.points(player) - IllagerTongue.TALK_FROM) * TONGUE_LEAN;
		if (move.equals("bluff")) {
			c += FeatsTrade.actorLie(player);
			if (s.lied().contains(player.getUUID().toString())) {
				c += LIED_LEAN;
			}
		}
		return Math.max(MIN_CHANCE, Math.min(MAX_CHANCE, c));
	}

	private static boolean hearing(ServerPlayer player, AbstractIllager illager) {
		Hearing h = HEARING.get(illager);
		return h != null && h.player().equals(player.getUUID()) && talk(player, illager) != null;
	}

	private static void calm(ServerPlayer player, AbstractIllager illager) {
		if (!hearing(player, illager) || !(illager.level() instanceof ServerLevel level)) {
			return;
		}
		Self s = self(illager);
		RandomSource random = level.getRandom();
		if (random.nextFloat() < chance(player, illager, s, "calm", CALM_BASE)) {
			put(illager, s.withHearts(player.getUUID(), CALM_HEARTS).withMood(1));
			truce(illager, player, level.getGameTime() + CALM_TRUCE);
			say(level, illager, player, line("calmed", random), Speech.GOLD);
			Sparks.act(player, Sparks.Act.PERSUADED, false);
			Skills.grant(player, Skills.Pillar.STEALTH, 1);
		} else {
			put(illager, s.withHearts(player.getUUID(), LOST_HEARTS).withMood(-1));
			say(level, illager, player, line("not_calmed", random), Speech.RED);
			broken(level, illager, player);
		}
	}

	private static void pay(ServerPlayer player, AbstractIllager illager, int price) {
		if (!hearing(player, illager) || !(illager.level() instanceof ServerLevel level)) {
			return;
		}
		long milli = Money.worth(price);
		if (!Money.charge(player, milli)) {
			say(level, illager, player, line("short", level.getRandom()), Speech.RED);
			broken(level, illager, player);
			return;
		}
		put(illager, self(illager).withMood(2));
		truce(illager, player, level.getGameTime() + PAID_TRUCE);
		say(level, illager, player, line("paid", level.getRandom()), Speech.GOLD);
		level.playSound(null, illager.getX(), illager.getY(), illager.getZ(), SoundEvents.VILLAGER_YES, SoundSource.HOSTILE, 0.6F, 0.7F);
		Sparks.act(player, Sparks.Act.PAID_TOLL, false);
	}

	private static void bluff(ServerPlayer player, AbstractIllager illager) {
		if (!hearing(player, illager) || !(illager.level() instanceof ServerLevel level) || IllagerTongue.points(player) < IllagerTongue.BLUFF_FROM) {
			return;
		}
		Self s = self(illager);
		RandomSource random = level.getRandom();
		boolean won = random.nextFloat() < chance(player, illager, s, "bluff", BLUFF_BASE);
		Sparks.act(player, Sparks.Act.LIED, !won);
		put(illager, s.withLied(player.getUUID()));
		if (won) {
			truce(illager, player, level.getGameTime() + BLUFF_TRUCE);
			say(level, illager, player, line("bluffed", random), Speech.GOLD);
			Skills.grant(player, Skills.Pillar.STEALTH, 1);
		} else {
			put(illager, self(illager).withHearts(player.getUUID(), LOST_HEARTS * 2).withMood(-2));
			say(level, illager, player, line("bluff_seen", random), Speech.RED);
			broken(level, illager, player);
		}
	}

	private static void walk(ServerPlayer player, AbstractIllager illager) {
		if (!hearing(player, illager) || !(illager.level() instanceof ServerLevel level)) {
			return;
		}
		Self s = self(illager);
		HEARING.remove(illager);
		TALKS.remove(player.getUUID());
		if (s.mood() >= 0) {
			// (a word and a head start: go, before they think better of it)
			put(illager, s.withTruce(player.getUUID(), level.getGameTime() + WALK_TRUCE));
			say(level, illager, player, line("go", level.getRandom()), Speech.WHITE);
		} else {
			say(level, illager, player, line("no_go", level.getRandom()), Speech.RED);
		}
	}

	// ---- at peace -------------------------------------------------------------------------------------------------------

	private static boolean peaceful(ServerPlayer player, AbstractIllager illager) {
		Talk t = talk(player, illager);
		return t != null && t.attitude() != Attitude.HOSTILE && atPeace(illager, self(illager), player);
	}

	private static void askName(ServerPlayer player, AbstractIllager illager) {
		if (!peaceful(player, illager) || !(illager.level() instanceof ServerLevel level)) {
			return;
		}
		Self s = self(illager);
		RandomSource random = level.getRandom();
		if (s.heartsOf(player.getUUID()) < 0 || s.mood() <= -9) {
			say(level, illager, player, line("name_no", random), Speech.WHITE);
		} else {
			if (illager.getCustomName() == null) {
				illager.setCustomName(Component.literal(s.name()));
			}
			illager.setAttached(NAMED, true);
			illager.setPersistenceRequired();
			Introductions.learn(player, illager);
			say(level, illager, player, line("name", random, s.name()), Speech.WHITE);
		}
		back(player, illager);
	}

	private static void chat(ServerPlayer player, AbstractIllager illager) {
		if (!peaceful(player, illager) || !(illager.level() instanceof ServerLevel level)) {
			return;
		}
		Self s = self(illager);
		long day = level.getGameTime() / 24000L;
		Long chatted = s.chatted().get(player.getUUID().toString());
		if (chatted != null && chatted >= day) {
			return;
		}
		RandomSource random = level.getRandom();
		float c = chance(player, illager, s, "persuade", 0.55F);
		boolean good = random.nextFloat() < c;
		s = s.withChatted(player.getUUID(), day).withHearts(player.getUUID(), good ? CHAT_HEARTS : CHAT_LOST).withMood(good ? 1 : 0);
		put(illager, s);
		say(level, illager, player, line("chat." + (good ? s.nature() : "sour"), random), Speech.WHITE);
		back(player, illager);
	}

	private static void askWant(ServerPlayer player, AbstractIllager illager) {
		if (!peaceful(player, illager) || !(illager.level() instanceof ServerLevel level)) {
			return;
		}
		Self s = self(illager);
		Item want = want(illager, s, level.getGameTime() / 24000L);
		RandomSource random = level.getRandom();
		say(level, illager, player, want == Items.AIR ? line("want_none", random) : line("want", random, new ItemStack(want).getHoverName()), Speech.WHITE);
		back(player, illager);
	}

	private static void give(ServerPlayer player, AbstractIllager illager) {
		if (!peaceful(player, illager) || !(illager.level() instanceof ServerLevel level)) {
			return;
		}
		ItemStack held = player.getMainHandItem();
		if (held.isEmpty()) {
			return;
		}
		Self s = self(illager);
		long day = level.getGameTime() / 24000L;
		RandomSource random = level.getRandom();
		Item want = want(illager, s, day);
		s = self(illager);
		Item gift = held.getItem();
		if (want != Items.AIR && gift == want) {
			s = s.withHearts(player.getUUID(), WANT_HEARTS).withMood(3).withWant("", day + WANT_DAYS);
			say(level, illager, player, line("want_met", random), Speech.GOLD);
			Money.pay(player, Money.worth(WANT_REWARD));
			if (IllagerTongue.LEDGER != null && !player.getInventory().add(new ItemStack(IllagerTongue.LEDGER))) {
				player.drop(new ItemStack(IllagerTongue.LEDGER), false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			Sparks.act(player, Sparks.Act.GIFT, false);
		} else if (Coinage.isMoney(held)) {
			s = s.withHearts(player.getUUID(), COIN_GIFT).withMood(1);
			say(level, illager, player, line("coin", random), Speech.WHITE);
		} else if (liked(s.nature(), held)) {
			s = s.withHearts(player.getUUID(), LIKED_GIFT).withMood(1);
			say(level, illager, player, line("liked", random), Speech.WHITE);
		} else {
			s = s.withHearts(player.getUUID(), GIFT);
			say(level, illager, player, line("gift", random), Speech.WHITE);
		}
		put(illager, s);
		if (!player.hasInfiniteMaterials()) {
			held.shrink(1);
		}
		level.playSound(null, illager.getX(), illager.getY(), illager.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 1.0F);
		back(player, illager);
	}

	/** What each nature is glad of, beyond its want. */
	static boolean liked(String nature, ItemStack stack) {
		return switch (nature) {
			case "brutal" -> stack.is(Items.IRON_INGOT) || stack.is(Items.IRON_SWORD);
			case "greedy" -> stack.is(Items.EMERALD) || stack.is(Items.GOLD_NUGGET);
			case "proud" -> stack.is(whiteBanner()) || stack.is(Items.EMERALD);
			case "cunning" -> stack.is(Items.MAP) || stack.is(Items.COMPASS);
			case "zealous" -> stack.is(Items.LAPIS_LAZULI) || stack.is(Items.BOOK);
			case "weary" -> stack.has(net.minecraft.core.component.DataComponents.FOOD);
			case "superstitious" -> stack.is(Items.GOAT_HORN) || stack.is(Items.BONE);
			case "craven" -> stack.is(Items.POTION) || stack.is(Items.TOTEM_OF_UNDYING);
			default -> false;
		};
	}

	/** A white banner (coloured blocks have no Items field in 26.3). */
	static Item whiteBanner() {
		return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("white_banner"));
	}

	/** A good of theirs: what, how many, its price (emeralds' worth); a negative price is what they pay you for it. */
	record Offer(Item item, int count, int price) {
	}

	static List<Offer> offers(AbstractIllager illager) {
		Illagers.Tag tag = Illagers.tag(illager);
		List<Offer> list = new ArrayList<>();
		if (tag != null && tag.role() == Illagers.WARLORD) {
			list.add(new Offer(Items.IRON_AXE, 1, 6));
			list.add(new Offer(whiteBanner(), 1, 8));
		} else if (illager instanceof Evoker || illager instanceof Illusioner) {
			list.add(new Offer(Items.LAPIS_LAZULI, 6, 3));
			list.add(new Offer(Items.EXPERIENCE_BOTTLE, 3, 6));
			if (IllagerTongue.LEDGER != null) {
				list.add(new Offer(IllagerTongue.LEDGER, 1, 10));
			}
		} else if (illager instanceof Pillager) {
			list.add(new Offer(Items.ARROW, 16, 2));
			list.add(new Offer(Items.CROSSBOW, 1, 6));
			list.add(new Offer(Items.FIREWORK_ROCKET, 3, 4));
		} else {
			list.add(new Offer(Items.IRON_AXE, 1, 6));
			list.add(new Offer(Items.LEATHER, 4, 2));
			list.add(new Offer(Items.COOKED_MUTTON, 4, 2));
		}
		// (they all buy iron)
		list.add(new Offer(Items.IRON_INGOT, 4, -2));
		return list;
	}

	/** What {@code offer} costs {@code player} from {@code illager} now. */
	static int cost(ServerPlayer player, AbstractIllager illager, Self s, Offer offer) {
		if (offer.price() < 0) {
			return offer.price();
		}
		int hearts = s.heartsOf(player.getUUID());
		float f = hearts >= TRUSTED ? TRUSTED_PRICE : hearts < 0 ? COLD_PRICE : 1.0F;
		if (s.nature().equals("greedy")) {
			f *= GREEDY_PRICE;
		}
		Long persuaded = PERSUADED.get(illager.getUUID() + "|" + player.getUUID());
		if (persuaded != null && persuaded == illager.level().getGameTime() / 24000L) {
			f *= PERSUADED_PRICE;
		}
		return Math.max(1, Math.round(offer.price() * f));
	}

	private static void trade(ServerPlayer player, AbstractIllager illager) {
		if (!peaceful(player, illager)) {
			return;
		}
		Self s = self(illager);
		List<Menus.Choice> choices = new ArrayList<>();
		for (Offer o : offers(illager)) {
			int price = cost(player, illager, s, o);
			Component goods = Component.translatable("illager_talk.jaymoskingdoms.goods", o.count(), new ItemStack(o.item()).getHoverName());
			if (price < 0) {
				boolean has = count(player.getInventory(), st -> st.is(o.item())) >= o.count();
				choices.add(Menus.Choice.of(Component.translatable("illager_talk.jaymoskingdoms.sell", goods, Money.text(player, Money.worth(-price))), has,
					p -> sell(p, illager, o)));
			} else {
				long milli = Money.worth(price);
				choices.add(Menus.Choice.of(Component.translatable("illager_talk.jaymoskingdoms.buy", goods, Money.text(player, milli)), Money.canPay(player, milli),
					p -> buy(p, illager, o)));
			}
		}
		choices.add(Menus.Choice.of(Component.translatable("illager_talk.jaymoskingdoms.back"), p -> back(p, illager)));
		Talk t = talk(player, illager);
		Menus.ask(player, illager, head(illager, s, player, t == null ? Attitude.WARY : t.attitude()),
			IllagerTongue.heard(player, illager, line("trade", illager.getRandom())), choices);
	}

	private static void buy(ServerPlayer player, AbstractIllager illager, Offer o) {
		if (!peaceful(player, illager) || !(illager.level() instanceof ServerLevel level)) {
			return;
		}
		long milli = Money.worth(cost(player, illager, self(illager), o));
		if (!Money.charge(player, milli)) {
			say(level, illager, player, line("short", level.getRandom()), Speech.WHITE);
			return;
		}
		ItemStack goods = new ItemStack(o.item(), o.count());
		if (!player.getInventory().add(goods)) {
			player.drop(goods, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		say(level, illager, player, line("sold", level.getRandom()), Speech.WHITE);
		level.playSound(null, illager.getX(), illager.getY(), illager.getZ(), SoundEvents.VILLAGER_TRADE, SoundSource.HOSTILE, 0.6F, 0.7F);
		trade(player, illager);
	}

	private static void sell(ServerPlayer player, AbstractIllager illager, Offer o) {
		if (!peaceful(player, illager) || !(illager.level() instanceof ServerLevel level)) {
			return;
		}
		if (count(player.getInventory(), st -> st.is(o.item())) < o.count()) {
			return;
		}
		take(player.getInventory(), st -> st.is(o.item()), o.count());
		Money.pay(player, Money.worth(-o.price()));
		say(level, illager, player, line("bought", level.getRandom()), Speech.WHITE);
		trade(player, illager);
	}

	static int count(Inventory inv, Predicate<ItemStack> what) {
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			if (!st.isEmpty() && what.test(st)) {
				n += st.getCount();
			}
		}
		return n;
	}

	static void take(Inventory inv, Predicate<ItemStack> what, int n) {
		for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
			ItemStack st = inv.getItem(i);
			if (!st.isEmpty() && what.test(st)) {
				int t = Math.min(n, st.getCount());
				st.shrink(t);
				n -= t;
			}
		}
		inv.setChanged();
	}

	/** Word of their hold: who leads them, the captives kept, and where another hold of theirs lies. */
	private static void hold(ServerPlayer player, AbstractIllager illager) {
		if (!peaceful(player, illager) || !(illager.level() instanceof ServerLevel level)) {
			return;
		}
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		Illagers.Tag tag = Illagers.tag(illager);
		Faction camp = tag == null ? null : Factions.get(server, tag.camp());
		if (camp == null || camp.fallen()) {
			say(level, illager, player, line("hold.none", random), Speech.WHITE);
		} else if (tag.role() == Illagers.WARLORD) {
			say(level, illager, player, line("hold.mine", random), Speech.WHITE);
		} else {
			Entity leader = camp.leader().map(level::getEntity).orElse(null);
			Component who = leader instanceof AbstractIllager l && l.isAlive() && Introductions.knows(player, l) && named(l)
				? Component.literal(self(l).name()) : Component.translatable("illager_talk.jaymoskingdoms.the_warlord");
			say(level, illager, player, leader != null && leader.isAlive() ? line("hold.leader", random, who) : line("hold.leaderless", random), Speech.WHITE);
			int captives = level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(camp.seat()).inflate(48.0), Captives::bound).size();
			if (captives > 0) {
				say(level, illager, player, line("hold.captives", random, captives), Speech.WHITE);
			}
		}
		BlockPos other = otherHold(server, illager.blockPosition(), tag == null ? -1 : tag.camp());
		if (other != null) {
			say(level, illager, player, line("hold.other", random, Directions.heading(illager.position(), Vec3.atCenterOf(other)),
				Directions.distance(Math.sqrt(other.distSqr(illager.blockPosition())))), Speech.WHITE);
		}
		back(player, illager);
	}

	/** The nearest other illager hold: a camp of theirs, or an Illager Structures place they hold. */
	static @Nullable BlockPos otherHold(MinecraftServer server, BlockPos from, int own) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (Faction f : Factions.all(server, FactionType.BARBARIAN)) {
			if (f.id() == own || f.fallen() || !IllagerCamps.KIND.equals(Camps.kind(server, f.id()))) {
				continue;
			}
			double d = f.seat().distSqr(from);
			if (d < bestD) {
				bestD = d;
				best = f.seat();
			}
		}
		return best;
	}

	// ---- Talk Them Round -------------------------------------------------------------------------------------------------

	private static void round(ServerPlayer player, AbstractIllager illager) {
		if (!peaceful(player, illager) || !(illager.level() instanceof ServerLevel level)) {
			return;
		}
		long day = level.getGameTime() / 24000L;
		Self s = self(illager);
		boolean canBluff = IllagerTongue.points(player) >= IllagerTongue.BLUFF_FROM;
		List<Menus.Choice> choices = new ArrayList<>();
		for (String move : new String[] {"persuade", "bluff", "threaten"}) {
			boolean fresh = TRIED.getOrDefault(illager.getUUID() + "|" + player.getUUID() + "|" + move, -1L) < day;
			boolean able = fresh && (!move.equals("bluff") || canBluff);
			choices.add(Menus.Choice.of(Component.translatable("illager_talk.jaymoskingdoms.round." + move + (move.equals("bluff") && !canBluff ? "_cant" : "")),
				able, p -> move(p, illager, move)));
		}
		choices.add(Menus.Choice.of(Component.translatable("illager_talk.jaymoskingdoms.back"), p -> back(p, illager)));
		Talk t = talk(player, illager);
		Menus.ask(player, illager, head(illager, s, player, t == null ? Attitude.WARY : t.attitude()),
			IllagerTongue.heard(player, illager, line("round", level.getRandom())), choices);
	}

	private static void move(ServerPlayer player, AbstractIllager illager, String move) {
		if (!peaceful(player, illager) || !(illager.level() instanceof ServerLevel level)) {
			return;
		}
		long day = level.getGameTime() / 24000L;
		String key = illager.getUUID() + "|" + player.getUUID();
		if (TRIED.getOrDefault(key + "|" + move, -1L) >= day) {
			return;
		}
		TRIED.put(key + "|" + move, day);
		Self s = self(illager);
		RandomSource random = level.getRandom();
		float base = move.equals("persuade") ? PERSUADE_BASE : move.equals("bluff") ? BLUFF_BASE : THREATEN_BASE;
		boolean won = random.nextFloat() < chance(player, illager, s, move, base);
		say(level, illager, player, line("round." + move + (won ? ".won" : ".lost"), random), won ? Speech.GOLD : Speech.RED);
		switch (move) {
			case "persuade" -> {
				put(illager, s.withHearts(player.getUUID(), won ? PERSUADE_HEARTS : LOST_HEARTS));
				if (won) {
					PERSUADED.put(key, day);
					Sparks.act(player, Sparks.Act.PERSUADED, false);
				}
				back(player, illager);
			}
			case "bluff" -> {
				put(illager, s.withLied(player.getUUID()).withHearts(player.getUUID(), won ? 0 : LOST_HEARTS * 2));
				Sparks.act(player, Sparks.Act.LIED, !won);
				if (won && IllagerTongue.SCRAWL != null && !player.getInventory().add(new ItemStack(IllagerTongue.SCRAWL))) {
					// (they let slip their orders: a page of them)
					player.drop(new ItemStack(IllagerTongue.SCRAWL), false, net.minecraft.util.Prediction.SERVER_ONLY);
				}
				back(player, illager);
			}
			default -> {
				put(illager, s.withHearts(player.getUUID(), THREATEN_HEARTS).withMood(-3));
				Sparks.act(player, Sparks.Act.THREATENED, true);
				if (won) {
					Money.pay(player, Money.worth(PURSE_MIN + random.nextInt(PURSE_SPREAD + 1)));
					Fear.frighten(illager, player, FRIGHT_TICKS);
					TALKS.remove(player.getUUID());
				} else {
					put(illager, self(illager).withTruce(player.getUUID(), 0L));
					broken(level, illager, player);
				}
			}
		}
	}

	private static void leave(ServerPlayer player, AbstractIllager illager) {
		if (illager.level() instanceof ServerLevel level && peaceful(player, illager)) {
			say(level, illager, player, line("bye", level.getRandom()), Speech.WHITE);
		}
		TALKS.remove(player.getUUID());
	}

	// ---- blows --------------------------------------------------------------------------------------------------------

	/** A player struck an illager: every truce of theirs with that illager's band is off, and they hold it against them. */
	private static void struck(LivingEntity victim, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer player) || !IllagerTongue.speaker(victim) || !(victim instanceof AbstractIllager illager)
			|| !(illager.level() instanceof ServerLevel level)) {
			return;
		}
		Downers d = DOWNERS.computeIfAbsent(illager, k -> new Downers());
		d.players.add(player.getUUID());
		d.last = level.getServer().getTickCount();
		long until = level.getGameTime() + REFUSE_TICKS;
		SIDE_TRUCE.remove(Comrades.side(illager) + "|" + player.getUUID());
		for (AbstractIllager e : band(illager)) {
			Self s = e.getAttached(SELF);
			if (e == illager) {
				put(e, self(e).withTruce(player.getUUID(), 0L).withHearts(player.getUUID(), STRUCK_HEARTS).withMood(-3));
			} else if (s != null) {
				put(e, s.withTruce(player.getUUID(), 0L).withHearts(player.getUUID(), STRUCK_BAND));
			}
			REFUSING.put(e.getUUID() + "|" + player.getUUID(), until);
			Hearing h = HEARING.get(e);
			if (h != null && h.player().equals(player.getUUID())) {
				HEARING.remove(e);
			}
		}
		Talk t = TALKS.get(player.getUUID());
		if (t != null && t.illager() == illager.getId()) {
			TALKS.remove(player.getUUID());
		}
	}

	/** Illagers players struck: one that went down and is back up, alive, was spared by them. */
	private static void spared(MinecraftServer server, int now) {
		for (var it = DOWNERS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<AbstractIllager, Downers> e = it.next();
			AbstractIllager illager = e.getKey();
			Downers d = e.getValue();
			if (!illager.isAlive() || illager.isRemoved() || !(illager.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			if (Downed.isDowned(illager)) {
				d.down = true;
				continue;
			}
			if (!d.down) {
				if (now - d.last > DOWNERS_TICKS) {
					it.remove();
				}
				continue;
			}
			it.remove();
			long truce = level.getGameTime() + CALM_TRUCE;
			ServerPlayer nearest = null;
			for (UUID id : d.players) {
				for (AbstractIllager other : band(illager)) {
					Self o = self(other);
					if (other == illager) {
						o = o.withHearts(id, SPARED_HEARTS).withMood(2).withTruce(id, truce);
					} else {
						o = o.withHearts(id, SPARED_BAND);
					}
					put(other, o);
					REFUSING.remove(other.getUUID() + "|" + id);
				}
				ServerPlayer p = server.getPlayerList().getPlayer(id);
				if (p != null && p.level() == level && (nearest == null || p.distanceToSqr(illager) < nearest.distanceToSqr(illager))) {
					nearest = p;
				}
				if (p != null && illager.getTarget() == p) {
					illager.setTarget(null);
				}
			}
			if (nearest != null && nearest.distanceTo(illager) < WATCH_RANGE) {
				say(level, illager, nearest, line("spared", level.getRandom()), Speech.GOLD);
			}
		}
	}

	/** Illagers near players: marked armed while they hold a weapon; a yielding one calls out to a player near. */
	private static void yielding(MinecraftServer server, int now) {
		CALLED.values().removeIf(t -> now - t > YIELD_GAP * 4);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
				continue;
			}
			for (AbstractIllager illager : level.getEntitiesOfClass(AbstractIllager.class, player.getBoundingBox().inflate(WATCH_RANGE), IllagerTongue::speaker)) {
				if (!illager.getMainHandItem().isEmpty()) {
					illager.addTag(ARMED_TAG);
					continue;
				}
				if (!disarmed(illager) || Downed.isDowned(illager) || Captives.bound(illager) || illager.distanceTo(player) > YIELD_RANGE
					|| !illager.hasLineOfSight(player)) {
					continue;
				}
				Self s = self(illager);
				if (!yields(illager, s)) {
					continue;
				}
				if (illager.getTarget() instanceof ServerPlayer) {
					illager.setTarget(null);
				}
				String key = illager.getUUID() + "|" + player.getUUID();
				Integer last = CALLED.get(key);
				if (last != null && now - last < YIELD_GAP) {
					continue;
				}
				CALLED.put(key, now);
				say(level, illager, player, line("yield." + s.nature(), level.getRandom()), Speech.WHITE);
			}
		}
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		int now = server.getTickCount();
		for (var it = HEARING.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Entity, Hearing> e = it.next();
			if (!e.getKey().isAlive()) {
				it.remove();
			} else if (e.getValue().until() <= now) {
				// (kept waiting with no answer: they lose patience, and won't hear you again for a while)
				it.remove();
				ServerPlayer p = server.getPlayerList().getPlayer(e.getValue().player());
				if (p != null && e.getKey() instanceof AbstractIllager illager && illager.level() instanceof ServerLevel level) {
					REFUSING.put(illager.getUUID() + "|" + p.getUUID(), level.getGameTime() + REFUSE_TICKS);
					TALKS.remove(p.getUUID());
					if (!p.isCreative() && p.level() == level) {
						illager.setTarget(p);
					}
				}
			}
		}
		TALKS.values().removeIf(t -> now - t.opened() > TALK_TICKS);
		spared(server, now);
		yielding(server, now);
		// (one hearing you out keeps still and looks at you)
		for (Map.Entry<Entity, Hearing> e : HEARING.entrySet()) {
			if (e.getKey() instanceof AbstractIllager illager && server.getPlayerList().getPlayer(e.getValue().player()) instanceof ServerPlayer p) {
				illager.getNavigation().stop();
				illager.getLookControl().setLookAt(p, 30.0F, 30.0F);
			}
		}
	}
}
