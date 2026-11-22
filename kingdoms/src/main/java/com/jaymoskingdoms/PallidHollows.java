package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The Pallid, the Hollow's first look, made deathless. Andy (2026-11-19): "pallids should now be like [a deathless ranger],
 * he doesnt die under regular means no longer needs to eat or drink, and doesnt bleed. npcs who see him again and again will
 * comment and react based on their temperament".
 * <ul>
 * <li><b>Death won't keep them</b> (Jaymo's Powers' {@code Mortality.DEATHLESS}): a Pallid player's death is never rolled and
 * never final. They lie in a long grey black where they fell and get up there again, with no new injury, and no sure wake is
 * used up. They still go down like anyone (the downed rules are the same for them). Only what ends a mid-turn death ends a
 * Pallid: losing their head, the void, lava, an execution by a player or a boss, or the Nether's last chance. That is the
 * true death, and the Reincarnate after it, as for anyone.</li>
 * <li><b>No hunger or thirst</b>: their food bar and (with Jaymo's Kitchen) their water stay full ({@link #FED}). They may
 * still eat for the taste ({@code mixin/heritage/PlayerDivineEatMixin}), since Kitchen's meals are what mends a body.</li>
 * <li><b>No blood</b>: a Pallid body, player or villager, bleeds nothing ({@link PowersCompat#heritageBlood}).</li>
 * <li><b>Folk who keep seeing them</b>: each of a settlement's people counts the days they've seen a Pallid, and the times
 * they saw them die ({@link #WITNESS_RANGE}) and get up again ({@link #SEEN}, kept on the villager). Seen on
 * {@link #AGAIN_DAYS} days or more, or once they've watched them die or come back, they say something of it over their
 * head, in the way of their temperament ({@link Temperaments}): the kindhearted are glad, the timid run, the spiteful
 * jeer, the greedy wonder what it's worth. Seeing it {@link #MANY} times or more, they have other words for it. The
 * warm warm a little to them and the sour sour ({@link #hearts}), and a return they watched goes into what they
 * remember ({@link Recollections.Kind#SAW_DEATHLESS}). A full mask hides what they are, as it hides a Hollow's
 * turning ({@link HollowSighting}).</li>
 * </ul>
 */
public final class PallidHollows {
	/** How often their food and water are filled and the folk round them looked at (ticks). */
	static final int FED = 20;
	/** How far (blocks) folk see a Pallid die or come back, and notice one walking by; how far a line carries. */
	static final double WITNESS_RANGE = 24.0, NOTICE_RANGE = 12.0, SPEECH_RANGE = 16.0;
	/** At most this many speak up about one Pallid at once. */
	static final int AT_ONCE = 3;
	/** Days seen before one remarks on seeing them yet again; deaths or returns seen before they speak of it as old news. */
	static final int AGAIN_DAYS = 3, MANY = 3;
	/** The chance, on the first sighting of a day past {@link #AGAIN_DAYS}, that they remark on it. */
	static final float AGAIN_CHANCE = 0.5F;
	/** The least time between two remarks from one villager about one Pallid (ticks), a return they watched aside. */
	static final long SAY_GAP = 20L * 60 * 5;
	/** Lines for each temperament and each thing seen ({@code pallid.jaymoskingdoms.<temperament>.<again|back|backs>.<n>}), and a child's. */
	static final int LINES = 3, CHILD_LINES = 2;
	/** The grey of the black while they lie between death and getting up (RGB): the Pallid's turning tint. */
	static final int TINT = HollowBlight.PALLID_TINT;

	/**
	 * What one villager has seen of one Pallid: the days they've seen them on, the last of those days, the deaths and returns
	 * they watched, whether one is still unspoken of, and the game time they last said anything of it.
	 */
	record Seen(int days, long lastDay, int deaths, int returns, boolean owed, long said) {
		static final Seen NONE = new Seen(0, -1L, 0, 0, false, Long.MIN_VALUE / 2);
		static final Codec<Seen> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("days", 0).forGetter(Seen::days),
			Codec.LONG.optionalFieldOf("lastDay", -1L).forGetter(Seen::lastDay),
			Codec.INT.optionalFieldOf("deaths", 0).forGetter(Seen::deaths),
			Codec.INT.optionalFieldOf("returns", 0).forGetter(Seen::returns),
			Codec.BOOL.optionalFieldOf("owed", false).forGetter(Seen::owed),
			Codec.LONG.optionalFieldOf("said", Long.MIN_VALUE / 2).forGetter(Seen::said)).apply(i, Seen::new));
	}

	/** Each villager's sightings of each Pallid player (their uuid as text). Saved on the villager. */
	public static final AttachmentType<Map<String, Seen>> SEEN = AttachmentRegistry.create(JaymosKingdoms.id("pallid_seen"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Seen.CODEC)));

	private PallidHollows() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(PallidHollows::tick);
		PowersCompat.tellDeathless(PallidHollows::deathless, TINT, "message.jaymoskingdoms.pallid.fading", "message.jaymoskingdoms.pallid.woke");
	}

	/** Whether {@code entity} is a Pallid: one of the Hollow, of the first look. */
	public static boolean is(Entity entity) {
		return VillagerHeritage.of(entity) == Heritage.Kind.HOLLOW && HeritageVariants.of(entity) == null;
	}

	// ---- death -----------------------------------------------------------------------------------------------------------

	/**
	 * {@code player} is dying: if they're Pallid, those round them see it, and what's done as they get up again is handed
	 * back (null: they die as anyone does).
	 */
	static @Nullable Runnable deathless(ServerPlayer player) {
		if (!is(player)) {
			return null;
		}
		if (!Prejudice.masked(player)) {
			for (LivingEntity by : watching(player, WITNESS_RANGE)) {
				change(by, player, s -> new Seen(s.days(), s.lastDay(), s.deaths() + 1, s.returns(), true, s.said()));
			}
		}
		JaymosKingdoms.LOGGER.info("{} died Pallid: the grave won't keep them", player.getScoreboardName());
		return () -> back(player);
	}

	/** {@code player} gets up again where they fell: those who watch it remember it, and the first few say so. */
	private static void back(ServerPlayer player) {
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY(0.4), player.getZ(), 8, 0.3, 0.2, 0.3, 0.02);
		if (Prejudice.masked(player)) {
			return;
		}
		int spoke = 0;
		for (LivingEntity by : watching(player, WITNESS_RANGE)) {
			Seen s = change(by, player, was -> new Seen(was.days(), was.lastDay(), was.deaths(), was.returns() + 1, true, was.said()));
			Recollections.remember(by, player, Recollections.Kind.SAW_DEATHLESS, Component.empty());
			if (spoke < AT_ONCE) {
				spoke++;
				speak(level, by, player, s);
			}
		}
	}

	// ---- every second ----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % FED != 0) {
			return;
		}
		long today = Deeds.today(server);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!p.isAlive() || !is(p)) {
				continue;
			}
			// no need to eat or drink: the food bar (and Kitchen's water, KitchenCompat) stay full
			p.getFoodData().setFoodLevel(20);
			if (p.isSpectator() || p.isCreative() || Prejudice.masked(p)) {
				continue;
			}
			ServerLevel level = p.level();
			int spoke = 0;
			for (LivingEntity by : watching(p, NOTICE_RANGE)) {
				Seen was = seen(by, p);
				boolean newDay = was.lastDay() != today;
				Seen s = newDay ? change(by, p, x -> new Seen(x.days() + 1, today, x.deaths(), x.returns(), x.owed(), x.said())) : was;
				if (spoke >= AT_ONCE || level.getGameTime() - s.said() < SAY_GAP) {
					continue;
				}
				if (s.owed() || newDay && s.days() >= AGAIN_DAYS && level.getRandom().nextFloat() < AGAIN_CHANCE) {
					spoke++;
					speak(level, by, p, s);
				}
			}
		}
	}

	/** {@code by} says what they make of seeing Pallid {@code p} again, and does what their temperament does. */
	private static void speak(ServerLevel level, LivingEntity by, ServerPlayer p, Seen s) {
		// (a death or a return they watched and haven't spoken of yet, or simply seeing them again)
		int back = s.owed() ? Math.max(1, Math.max(s.deaths(), s.returns())) : 0;
		String what = back <= 0 ? "again" : back >= MANY ? "backs" : "back";
		Temperaments.Temperament t = Temperaments.of(by);
		String key = by.isBaby() || t == null ? "pallid.jaymoskingdoms.child." + what + "." + level.getRandom().nextInt(CHILD_LINES)
			: "pallid.jaymoskingdoms." + t.key() + "." + what + "." + level.getRandom().nextInt(LINES);
		change(by, p, x -> new Seen(x.days(), x.lastDay(), x.deaths(), x.returns(), false, level.getGameTime()));
		if (by instanceof Mob m) {
			m.getLookControl().setLookAt(p, 30.0F, 30.0F);
		}
		int hearts = back <= 0 || by.isBaby() ? 0 : hearts(t, back >= MANY);
		if (hearts != 0) {
			McaCompat.hearts(by, p, hearts);
		}
		Speech.say(level, by, Component.translatable(key, Recollections.nameTo(p, by), back), hearts < 0 ? Speech.RED : hearts > 0 ? Speech.GOLD
			: Speech.WHITE, SPEECH_RANGE);
		level.playSound(null, by.blockPosition(), hearts < 0 ? SoundEvents.VILLAGER_NO : hearts > 0 ? SoundEvents.VILLAGER_YES
			: SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 0.7F, by.isBaby() ? 1.5F : 1.0F);
		// the timid and the young run from a death walking about, until they've seen it often enough
		if (back > 0 && back < MANY && (by.isBaby() || t == Temperaments.Temperament.TIMID)) {
			HollowSighting.flee(by, p);
		}
	}

	/**
	 * How a return they watched moves {@code t}'s hearts: the kindhearted, the cheerful and the brave warm to one the grave
	 * gave back; the suspicious, the timid and the hotheaded sour; the spiteful most. Seen often, the fear wears off: the
	 * timid stop souring, and the devout, who first took it as wrong, come to take it as a sign.
	 */
	static int hearts(Temperaments.@Nullable Temperament t, boolean often) {
		if (t == null) {
			return 0;
		}
		return switch (t) {
			case KINDHEARTED, CHEERFUL, BRAVE -> 1;
			case DEVOUT -> often ? 1 : -1;
			case TIMID -> often ? 0 : -1;
			case SUSPICIOUS, HOTHEADED -> -1;
			case SPITEFUL -> -2;
			default -> 0;
		};
	}

	// ---- what each one has seen ------------------------------------------------------------------------------------------

	/** The settlement's people who can see {@code player} from within {@code range}, awake. */
	private static List<LivingEntity> watching(ServerPlayer player, double range) {
		return player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
			x -> x.isAlive() && !x.isSleeping() && Recollections.rememberer(x) && x.hasLineOfSight(player));
	}

	private static Seen seen(LivingEntity by, Player p) {
		return by.getAttachedOrElse(SEEN, Map.of()).getOrDefault(p.getStringUUID(), Seen.NONE);
	}

	private static Seen change(LivingEntity by, Player p, java.util.function.UnaryOperator<Seen> how) {
		Map<String, Seen> all = new HashMap<>(by.getAttachedOrElse(SEEN, Map.of()));
		Seen now = how.apply(all.getOrDefault(p.getStringUUID(), Seen.NONE));
		all.put(p.getStringUUID(), now);
		by.setAttached(SEEN, Map.copyOf(all));
		return now;
	}
}
