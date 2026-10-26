package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
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
 * Your character level is every pillar's level added up ({@link Skills}); every {@link #LEVELS_PER_POINT} of it is an
 * attribute point, spent on the K screen's Attributes tab. Six attributes, each to {@link #MAX}:
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
 */
public final class Stats {
	/** Character levels per attribute point (all three pillars at their top: 55 levels, 27 points). */
	public static final int LEVELS_PER_POINT = 2;
	/** An attribute's highest value, free points from heritage and class included. */
	public static final int MAX = 10;
	/** Might: attack damage per point (a share). */
	static final double MIGHT = 0.025;
	/** Agility: movement speed per point (a share). */
	static final double AGILITY = 0.01;
	/** Finesse: attack speed per point (a share). */
	static final double FINESSE = 0.025;
	/** Endurance: this share off hunger's drain per point. */
	static final float ENDURANCE = 0.025F;
	/** Grit: armour points, and knockback resistance, per point. */
	static final double GRIT_ARMOR = 0.5, GRIT_KNOCKBACK = 0.02;
	/** Wits: this share more skill experience, and this much luck, per point. */
	static final float WITS_XP = 0.03F;
	static final double WITS_LUCK = 0.1;

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

	/** Player → server: put a point into {@code stat} (its ordinal). */
	public record Raise(int stat) implements CustomPacketPayload {
		public static final Type<Raise> TYPE = new Type<>(JaymosKingdoms.id("stat_raise"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Raise> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.stat), buf -> new Raise(buf.readVarInt()));

		@Override
		public Type<Raise> type() {
			return TYPE;
		}
	}

	private Stats() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Raise.TYPE, Raise.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Raise.TYPE, (payload, context) -> raise(context.player(), payload.stat()));
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

	/** Every attribute point earned: from your character level, your heritage's free ones, a Warrior-born's from the brink, and (0.7.216) a foundling's. */
	public static int earned(Player player, Skills.Progress p) {
		Heritage.Kind kind = Heritage.of(player);
		return characterLevel(p) / LEVELS_PER_POINT + (kind == null ? 0 : kind.freePoints) + WarriorZenkai.points(player) + LifeStory.freePoints(player);
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
		return cls != null && favoured(cls) == stat ? 1 : 0;
	}

	/** Free points in {@code stat} from {@code player}'s heritage. */
	public static int fromHeritage(Player player, Stat stat) {
		Heritage.Kind kind = Heritage.of(player);
		return kind == null ? 0 : kind.bonus(stat);
	}

	/** (0.7.216) Free points in {@code stat} from {@code player}'s life story ({@link LifeStory}). */
	public static int fromStory(Player player, Stat stat) {
		return LifeStory.points(player, stat);
	}

	/** {@code stat}'s value for {@code player}: spent, heritage, class and (0.7.216) story, at most {@link #MAX}. */
	public static int value(Player player, Skills.Progress p, Stat stat) {
		return Math.min(MAX, spent(p, stat) + fromHeritage(player, stat) + fromClass(player, stat) + fromStory(player, stat));
	}

	public static int value(Player player, Stat stat) {
		return value(player, Skills.of(player), stat);
	}

	/** Why another point can't go into {@code stat} (a translation key), or null if it can. */
	public static @Nullable String blocked(Player player, Skills.Progress p, Stat stat) {
		if (stat == Stat.ARCANA && !Mana.attuned(player)) {
			return "gui.jaymoskingdoms.stats.blocked.unattuned";
		}
		if (spent(p, stat) + fromHeritage(player, stat) + fromClass(player, stat) + fromStory(player, stat) >= MAX) {
			return "gui.jaymoskingdoms.stats.blocked.max";
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

	private static void raise(ServerPlayer player, int ordinal) {
		Stat[] all = Stat.values();
		if (ordinal < 0 || ordinal >= all.length) {
			return;
		}
		Stat stat = all[ordinal];
		Skills.Progress p = Skills.of(player);
		if (blocked(player, p, stat) != null) {
			return;
		}
		Map<String, Integer> ranks = new HashMap<>(p.ranks());
		ranks.merge(stat.rankKey(), 1, Integer::sum);
		player.setAttached(Skills.PROGRESS, new Skills.Progress(p.xp(), Map.copyOf(ranks)));
		attributes(player);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.5F, 1.1F);
	}
}
