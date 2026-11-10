package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.57) Attributes, PlayerEx's idea remade inside the skill tree (Andy: "i want to see how the attributes integrate").
 * Your character level is every pillar's level added up ({@link Skills}); each level of it earns
 * {@link #POINTS_PER_LEVEL} attribute points, spent on the K screen's Attributes tab. Six attributes, each to {@link #MAX}:
 *
 * <ul>
 * <li><b>Might:</b> every blow hits harder.</li>
 * <li><b>Agility:</b> quicker on your feet.</li>
 * <li><b>Finesse:</b> quicker swings.</li>
 * <li><b>Endurance:</b> hunger drains slower.</li>
 * <li><b>Grit:</b> armour and a firmer stance.</li>
 * <li><b>Wits:</b> every pillar learns faster, and a little luck.</li>
 * <li><b>Arcana</b> (0.7.212): more mana, and it comes back faster. Only for the attuned ({@link Mana}): the row shows
 * once you are, and only they can raise it.</li>
 * </ul>
 *
 * <p>Health is none of them: hearts are Hearty Meals' to give. Your heritage ({@link Heritage}) and your class
 * ({@link Classes}, its {@link #favoured} attribute) each give a point or two free. The points live in the skill
 * tree's ranks (keys {@code stat_<name>}), so a respec (court wizard, bottle o' enchanting) gives them back with the perks.
 *
 * <p>(0.7.540, Andy: "character level points should scale way further, making stats go from 0/100 ... giving users a
 * reason to world tier up") Every attribute now runs 0 to {@link #MAX} (100). One old point is {@link #STEP} new ones:
 * each level earns {@link #POINTS_PER_LEVEL}, a heritage, class or story point gives {@link #STEP}, and every effect per
 * point is a tenth of the old one, times {@link #TOP_SHARE} so a full 100 is half again what the old 10 gave. How high an
 * attribute may go is your world tier's ({@link WorldTiers}): {@link #TIER_CAP}, the highest tier you have opened (or are
 * in). Each opened tier also adds {@link #TIER_POINTS}. Saved points from before are scaled by {@link #migrate} once.
 *
 * <p>Tiv: "they should build might for everyone ... its only good if you keep at it". Might can also be trained: Jaymo's
 * Powers hands over Might for shadowboxing in its training weights ({@link #trainMight}, by reflection), a little every
 * second, kept in {@link #TRAINED_MIGHT}. Each whole point counts on top of the rest, up to the tier's cap like any
 * other; it was never spent, so a respec leaves it.
 */
public final class Stats {
	/** (0.7.540) New points per old point: what a heritage, class or story point is worth now, and what old saves are scaled by. */
	public static final int STEP = 10;
	/** (0.7.540) Attribute points per character level (all three pillars at their top: 75 levels, 375 points). */
	public static final int POINTS_PER_LEVEL = 5;
	/** An attribute's highest value, free points from heritage and class included. */
	public static final int MAX = 100;
	/**
	 * (0.7.540) Per world tier, Hearth to Legend: how high an attribute may go. Hearth's 40 is about where a dedicated
	 * player's best attribute sat before; each tier after opens room for 15 to 20 more.
	 */
	public static final int[] TIER_CAP = {40, 55, 70, 85, 100};
	/** (0.7.540) Per world tier: the attribute points it adds once opened (in all, not each). */
	public static final int[] TIER_POINTS = {0, 10, 25, 45, 70};
	/** (0.7.540) What a full 100 gives against the old full 10. */
	static final double TOP_SHARE = 1.5;
	/** Might: attack damage per point (a share; 100: +37.5%). */
	static final double MIGHT = 0.025 / STEP * TOP_SHARE;
	/** Agility: movement speed per point (a share; 100: +15%). */
	static final double AGILITY = 0.01 / STEP * TOP_SHARE;
	/** Finesse: attack speed per point (a share; 100: +37.5%). */
	static final double FINESSE = 0.025 / STEP * TOP_SHARE;
	/** Endurance: this share off hunger's drain per point (100: 37.5% less). */
	static final float ENDURANCE = (float)(0.025 / STEP * TOP_SHARE);
	/** Grit: armour points, and knockback resistance, per point (100: +7.5 armour, +30%). */
	static final double GRIT_ARMOR = 0.5 / STEP * TOP_SHARE, GRIT_KNOCKBACK = 0.02 / STEP * TOP_SHARE;
	/** Wits: this share more skill experience, and this much luck, per point (100: +45%, +1.5 luck). */
	static final float WITS_XP = (float)(0.03 / STEP * TOP_SHARE);
	static final double WITS_LUCK = 0.1 / STEP * TOP_SHARE;
	/** (0.7.540) The rank key that marks a player's points as already on the 0-100 scale. */
	static final String SCALED_KEY = "stat_scale";
	/** (0.7.540) Points a shift-click puts in at once. */
	public static final int SHIFT_STEP = 10;
	/** The sparks' colour when a trained point of Might lands. */
	static final int MIGHT_COLOR = 0xC0503A;

	/**
	 * Might trained in Jaymo's Powers' training weights ({@link #trainMight}): the whole points count, the fraction is the
	 * next one on its way. Saved, kept through death, synced to its player (the K screen shows it).
	 */
	public static final AttachmentType<Double> TRAINED_MIGHT = AttachmentRegistry.create(JaymosKingdoms.id("trained_might"),
		builder -> builder.persistent(Codec.DOUBLE).copyOnDeath().syncWith(ByteBufCodecs.DOUBLE, AttachmentSyncPredicate.targetOnly()));

	public enum Stat {
		MIGHT, AGILITY, FINESSE, ENDURANCE, GRIT, WITS, ARCANA;

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** Its key in the skill tree's ranks. */
		public String rankKey() {
			return "stat_" + key();
		}
	}

	/** Player → server: put {@code count} points (at most {@link #SHIFT_STEP}) into {@code stat} (its ordinal). */
	public record Raise(int stat, int count) implements CustomPacketPayload {
		public static final Type<Raise> TYPE = new Type<>(JaymosKingdoms.id("stat_raise"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Raise> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.stat);
				buf.writeVarInt(p.count);
			}, buf -> new Raise(buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<Raise> type() {
			return TYPE;
		}
	}

	private Stats() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Raise.TYPE, Raise.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Raise.TYPE, (payload, context) -> raise(context.player(), payload.stat(), payload.count()));
	}

	// ---- reading -----------------------------------------------------------------------------------------------

	/** Your character level: every pillar's level added up. */
	public static int characterLevel(Skills.Progress p) {
		int n = 0;
		for (Skills.Pillar pillar : Skills.Pillar.values()) {
			n += p.level(pillar);
		}
		return n;
	}

	/** Points spent on {@code stat}. */
	public static int spent(Skills.Progress p, Stat stat) {
		return p.ranks().getOrDefault(stat.rankKey(), 0);
	}

	/** Points spent on every attribute. */
	public static int spent(Skills.Progress p) {
		int n = 0;
		for (Stat stat : Stat.values()) {
			n += spent(p, stat);
		}
		return n;
	}

	/**
	 * Every attribute point earned: from your character level, your heritage's free ones, a Warrior-born's from the brink,
	 * (0.7.216) a foundling's, and (0.7.540) the world tiers you've opened.
	 */
	public static int earned(Player player, Skills.Progress p) {
		Heritage.Kind kind = Heritage.of(player);
		int free = (kind == null ? 0 : kind.freePoints) + WarriorZenkai.points(player) + LifeStory.freePoints(player);
		return characterLevel(p) * POINTS_PER_LEVEL + free * STEP + TIER_POINTS[tier(player)];
	}

	/** (0.7.540) The world tier that sets {@code player}'s caps (its ordinal): the highest they've opened, or the one they're in if higher. */
	public static int tier(Player player) {
		int t = Math.max(WorldTiers.of(player).ordinal(), WorldTiers.progress(player).open());
		return Math.clamp(t, 0, TIER_CAP.length - 1);
	}

	/** (0.7.540) How high any attribute of {@code player}'s may go now. */
	public static int cap(Player player) {
		return TIER_CAP[tier(player)];
	}

	/** Points left to spend. */
	public static int points(Player player, Skills.Progress p) {
		return earned(player, p) - spent(p);
	}

	/** The attribute {@code cls} gives a point in free. */
	public static Stat favoured(Classes.PlayerClass cls) {
		return switch (cls) {
			case THIEF, MARKSMAN -> Stat.FINESSE;
			case WARLORD -> Stat.MIGHT;
			case MAGE, SORCERER -> Stat.WITS;
			case ARCHER, RIDER -> Stat.AGILITY;
			case SURVIVALIST -> Stat.ENDURANCE;
			case BRUTE -> Stat.GRIT;
		};
	}

	/** Free points in {@code stat} from {@code player}'s class. */
	public static int fromClass(Player player, Stat stat) {
		Classes.PlayerClass cls = Classes.classOf(player);
		return cls != null && favoured(cls) == stat ? STEP : 0;
	}

	/** Free points in {@code stat} from {@code player}'s heritage. */
	public static int fromHeritage(Player player, Stat stat) {
		Heritage.Kind kind = Heritage.of(player);
		return kind == null ? 0 : kind.bonus(stat) * STEP;
	}

	/** (0.7.216) Free points in {@code stat} from {@code player}'s life story ({@link LifeStory}). */
	public static int fromStory(Player player, Stat stat) {
		return LifeStory.points(player, stat) * STEP;
	}

	/** Might trained in the weights so far, the fraction included (0 for none). */
	public static double trained(Player player) {
		return player.getAttachedOrElse(TRAINED_MIGHT, 0.0);
	}

	/** Free points in {@code stat} from training in the weights: whole points of Might ({@link #trainMight}), none in anything else. */
	public static int fromTraining(Player player, Stat stat) {
		return stat == Stat.MIGHT ? (int)Math.floor(trained(player)) : 0;
	}

	/** {@code stat}'s points before the cap: spent, heritage, class, (0.7.216) story and training. */
	public static int total(Player player, Skills.Progress p, Stat stat) {
		return spent(p, stat) + fromHeritage(player, stat) + fromClass(player, stat) + fromStory(player, stat) + fromTraining(player, stat);
	}

	/** {@code stat}'s value for {@code player}: {@link #total}, at most (0.7.540) their tier's {@link #cap}. */
	public static int value(Player player, Skills.Progress p, Stat stat) {
		return Math.min(cap(player), total(player, p, stat));
	}

	public static int value(Player player, Stat stat) {
		return value(player, Skills.of(player), stat);
	}

	/** Why another point can't go into {@code stat} (a translation key), or null if it can. */
	public static @Nullable String blocked(Player player, Skills.Progress p, Stat stat) {
		if (stat == Stat.ARCANA && !Mana.attuned(player)) {
			return "gui.jaymoskingdoms.stats.blocked.unattuned";
		}
		if (total(player, p, stat) >= cap(player)) {
			return cap(player) >= MAX ? "gui.jaymoskingdoms.stats.blocked.max" : "gui.jaymoskingdoms.stats.blocked.tier";
		}
		if (points(player, p) <= 0) {
			return "gui.jaymoskingdoms.stats.blocked.points";
		}
		return null;
	}

	/** (0.7.212) Whether {@code stat}'s row shows for {@code player}: all of them, Arcana only once they're attuned. */
	public static boolean shown(Player player, Stat stat) {
		return stat != Stat.ARCANA || Mana.attuned(player);
	}

	/** What {@code value} points of {@code stat} do (the K screen's line for it). */
	public static Component now(Stat stat, int value) {
		return switch (stat) {
			case MIGHT -> Component.translatable("stat.jaymoskingdoms.might.now", pct(MIGHT * value));
			case AGILITY -> Component.translatable("stat.jaymoskingdoms.agility.now", pct(AGILITY * value));
			case FINESSE -> Component.translatable("stat.jaymoskingdoms.finesse.now", pct(FINESSE * value));
			case ENDURANCE -> Component.translatable("stat.jaymoskingdoms.endurance.now", pct(ENDURANCE * value));
			case GRIT -> Component.translatable("stat.jaymoskingdoms.grit.now", num(GRIT_ARMOR * value), pct(GRIT_KNOCKBACK * value));
			case WITS -> Component.translatable("stat.jaymoskingdoms.wits.now", pct(WITS_XP * value), num(WITS_LUCK * value));
			case ARCANA -> Component.translatable("stat.jaymoskingdoms.arcana.now", num(Mana.NATURAL_PER_POINT * value), num(Mana.FOUND_PER_POINT * value),
				num(Mana.REGEN_PER_POINT * value));
		};
	}

	private static String pct(double share) {
		return num(share * 100.0);
	}

	private static String num(double n) {
		double r = Math.round(n * 10.0) / 10.0;
		return r == Math.rint(r) ? Long.toString(Math.round(r)) : Double.toString(r);
	}

	// ---- effects -----------------------------------------------------------------------------------------------

	/** The factor on {@code player}'s hunger drain (Endurance). */
	static float exhaustion(ServerPlayer player) {
		return 1.0F - ENDURANCE * value(player, Stat.ENDURANCE);
	}

	/** The factor on skill experience {@code player} earns (Wits). */
	static float xpFactor(ServerPlayer player) {
		return 1.0F + WITS_XP * value(player, Stat.WITS);
	}

	/** Puts every attribute on {@code player} as it stands now. */
	static void attributes(ServerPlayer player) {
		Skills.Progress p = Skills.of(player);
		Skills.modifier(player, Attributes.ATTACK_DAMAGE, "stat_might", MIGHT * value(player, p, Stat.MIGHT), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		Skills.modifier(player, Attributes.MOVEMENT_SPEED, "stat_agility", AGILITY * value(player, p, Stat.AGILITY), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		Skills.modifier(player, Attributes.ATTACK_SPEED, "stat_finesse", FINESSE * value(player, p, Stat.FINESSE), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		int grit = value(player, p, Stat.GRIT);
		Skills.modifier(player, Attributes.ARMOR, "stat_grit", GRIT_ARMOR * grit, AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.KNOCKBACK_RESISTANCE, "stat_grit", GRIT_KNOCKBACK * grit, AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.LUCK, "stat_wits", WITS_LUCK * value(player, p, Stat.WITS), AttributeModifier.Operation.ADD_VALUE);
	}

	/**
	 * {@code points} of Might trained (a fraction of one, mostly): called every second of shadowboxing in training weights by
	 * Jaymo's Powers ({@code core.Training}, by reflection), which sets the pace. Nothing once Might is at the world tier's
	 * cap. A whole point landing goes on at once, with the small fanfare.
	 */
	public static void trainMight(ServerPlayer player, double points) {
		if (points <= 0.0 || total(player, Skills.of(player), Stat.MIGHT) >= cap(player)) {
			return;
		}
		double before = trained(player), after = before + points;
		player.setAttached(TRAINED_MIGHT, after);
		if ((int)Math.floor(after) > (int)Math.floor(before)) {
			attributes(player);
			Fanfare.fanfare(player, Component.translatable("message.jaymoskingdoms.stats.trained_might"),
				Component.translatable("message.jaymoskingdoms.stats.trained_might.value", value(player, Stat.MIGHT), MAX), null, MIGHT_COLOR, Fanfare.MINOR);
		}
	}

	/** If {@code player} has spent more points than they've earned (their heritage changed), the points come back. */
	static void settle(ServerPlayer player) {
		Skills.Progress p = Skills.of(player);
		if (points(player, p) >= 0) {
			return;
		}
		Map<String, Integer> ranks = new HashMap<>(p.ranks());
		for (Stat stat : Stat.values()) {
			ranks.remove(stat.rankKey());
		}
		player.setAttached(Skills.PROGRESS, new Skills.Progress(p.xp(), Map.copyOf(ranks)));
	}

	/**
	 * (0.7.540) Scales {@code player}'s spent points from the old 0-10 to the 0-100 scale, once: a save without the
	 * {@link #SCALED_KEY} mark has each {@code stat_} rank times {@link #STEP}. Every raise sets the mark, so new points are
	 * never scaled; a respec drops the mark with the points, which is harmless (nothing is left to scale).
	 */
	static void migrate(ServerPlayer player) {
		Skills.Progress p = Skills.of(player);
		if (p.ranks().containsKey(SCALED_KEY) || spent(p) == 0) {
			return;
		}
		Map<String, Integer> ranks = new HashMap<>(p.ranks());
		for (Stat stat : Stat.values()) {
			ranks.computeIfPresent(stat.rankKey(), (k, v) -> v * STEP);
		}
		ranks.put(SCALED_KEY, 1);
		player.setAttached(Skills.PROGRESS, new Skills.Progress(p.xp(), Map.copyOf(ranks)));
	}

	private static void raise(ServerPlayer player, int ordinal, int count) {
		Stat[] all = Stat.values();
		if (ordinal < 0 || ordinal >= all.length) {
			return;
		}
		Stat stat = all[ordinal];
		migrate(player);
		Skills.Progress p = Skills.of(player);
		if (blocked(player, p, stat) != null) {
			return;
		}
		// as many as asked (a shift-click: up to SHIFT_STEP), no more than the cap and the points left allow
		int n = Math.min(Math.clamp(count, 1, SHIFT_STEP), Math.min(cap(player) - total(player, p, stat), points(player, p)));
		Map<String, Integer> ranks = new HashMap<>(p.ranks());
		ranks.merge(stat.rankKey(), n, Integer::sum);
		ranks.put(SCALED_KEY, 1);
		player.setAttached(Skills.PROGRESS, new Skills.Progress(p.xp(), Map.copyOf(ranks)));
		attributes(player);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.5F, 1.1F);
	}
}
