package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * The three disciplines of defence (Andy, 2026-11-22; the vault's {@code defence-disciplines}): what Jaymo's Powers'
 * passive blocks ({@code combat.PassiveBlocks}) ask by reflection. Every character's passive blocks come from
 * <b>disciplines</b>, not from what's in their hand: {@link #ARMS} (the reflex block, a weapon or shield snapping up),
 * {@link #KI} (the ki ward) and {@link #ARCANA} (the ward, a turning of magic). Andy: "A soldier wouldnt be able to
 * concentrate on a ward just because he found a staff, and likewise a mage wouldnt have the dexterity to raise a guard
 * instinctively. They should still be able to train any of these skills, just they have that specific inclination."
 * <ul>
 * <li><b>Inclination</b>, by class ({@link #INCLINED}): it starts open and trains at full speed. A Z Fighter (the power)
 * is inclined to Ki too, whatever their class. A classless character leans to Arms.</li>
 * <li><b>Leans</b>, by heritage ({@link #leans}): a lean that matches the class starts at level {@link #KIN_START}; one
 * that differs is open with no teacher and trains at {@link #LEAN_SPEED}. Multi-lean heritages have every one open.</li>
 * <li><b>Anything else</b> is locked until a teacher opens it ({@link #teach}), then trains at {@link #TAUGHT_SPEED}.</li>
 * <li><b>Levels</b> 0 to {@link #MAX_LEVEL}, earned by blocks caught in that discipline ({@link #trained}); the blocks they
 * give, {@link #INCLINED_BLOCKS} and {@link #TRAINED_BLOCKS}, all together at most {@link #MAX_BLOCKS}.</li>
 * </ul>
 * And the skills the passive blocks ask about: Keen Timing and Iron Guard (Strength), Snatch (Strength, the Thief's tree;
 * every Monk has it).
 */
public final class Disciplines {
	public static final int ARMS = 0, KI = 1, ARCANA = 2, KINDS = 3;
	public static final int MAX_LEVEL = 10, MAX_BLOCKS = 5;
	/** A lean that matches the class starts here. */
	static final int KIN_START = 2;
	/** Training speed: a lean that differs from the class, and a discipline a teacher opened. */
	static final float LEAN_SPEED = 0.5F, TAUGHT_SPEED = 0.25F;
	/** Experience to the next level from level {@code l}: {@code XP_BASE + XP_STEP * l} blocks caught at full speed. */
	static final int XP_BASE = 12, XP_STEP = 6;
	/** Blocks by level (index: 0, 3, 5, 8, 10 and up): inclined, and trained. */
	static final int[] LEVEL_STEPS = {0, 3, 5, 8, 10};
	static final int[] INCLINED_BLOCKS = {1, 1, 2, 2, 3}, TRAINED_BLOCKS = {0, 1, 1, 2, 2};
	/** A ward's mana; a monk's ki ward takes one ki; anyone else with Ki and no ki of their own pays a half-feather. */
	static final float WARD_MANA = 2.0F;
	static final int OTHER_KI_FEATHERS = 1;

	/** The skill keys (Skills.Perk, and the Thief's tree node). */
	static final String KEEN = "keen_timing", IRON = "iron_guard", SNATCH = "snatch";
	/** The Thief's tree's Snatch (its node key). */
	public static final String SNATCH_NODE = "snatch";

	/** Classes and the discipline each is inclined to (the rest, Arms). */
	static final Set<PlayerClass> KI_CLASSES = EnumSet.of(PlayerClass.MONK);
	static final Set<PlayerClass> ARCANA_CLASSES = EnumSet.of(PlayerClass.MAGE, PlayerClass.SORCERER, PlayerClass.WARLOCK, PlayerClass.CLERIC,
		PlayerClass.DRUID);

	/** Levels, experience toward the next level, and which disciplines a teacher opened (a bit each). */
	public record Data(List<Integer> levels, List<Float> xp, int taught) {
		static final Data NONE = new Data(List.of(0, 0, 0), List.of(0.0F, 0.0F, 0.0F), 0);
		public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.listOf().fieldOf("levels").forGetter(Data::levels),
			Codec.FLOAT.listOf().fieldOf("xp").forGetter(Data::xp),
			Codec.INT.optionalFieldOf("taught", 0).forGetter(Data::taught)).apply(i, Data::new));

		int level(int d) {
			return d < this.levels.size() ? this.levels.get(d) : 0;
		}

		float xp(int d) {
			return d < this.xp.size() ? this.xp.get(d) : 0.0F;
		}
	}

	public static final AttachmentType<Data> DATA = AttachmentRegistry.create(JaymosKingdoms.id("disciplines"),
		builder -> builder.persistent(Data.CODEC).copyOnDeath());

	private Disciplines() {
	}

	static Data of(Player player) {
		Data data = player.getAttached(DATA);
		return data == null ? Data.NONE : data;
	}

	// ---- inclination and leans -----------------------------------------------------------------------------------

	/** The disciplines {@code player}'s class (and a Z Fighter's ki) incline them to. */
	static Set<Integer> inclined(Player player) {
		Set<Integer> out = new java.util.TreeSet<>();
		for (PlayerClass cls : Classes.both(player)) {
			out.add(KI_CLASSES.contains(cls) ? KI : ARCANA_CLASSES.contains(cls) ? ARCANA : ARMS);
		}
		if ("zfighter".equals(PowersCompat.power(player))) {
			out.add(KI);
		}
		if (out.isEmpty()) {
			// no class yet: anyone can raise what's in their hand
			out.add(ARMS);
		}
		return out;
	}

	/** The disciplines {@code player}'s heritage takes to (Andy's table, drafts 6 and 7). */
	static Set<Integer> leans(Player player) {
		return leans(Heritage.of(player), HeritageVariants.of(player));
	}

	/** The disciplines heritage {@code kind} (in look {@code variant}) takes to: a player's, or an MCA villager's. */
	static Set<Integer> leans(Heritage.@Nullable Kind kind, HeritageVariants.@Nullable Variant variant) {
		if (kind == null) {
			return Set.of();
		}
		return switch (kind) {
			case HEARTLANDER, STONEBORN, DEEPBORN, NIGHTBORN -> Set.of(ARMS);
			case STORMBORN, BEASTFOLK, RUBBERFOLK -> Set.of(KI);
			case SYLVAN, TIDEBORN, DRAGONBLOOD, DAWNBORN, ENDBORN, DIVINE -> Set.of(ARCANA);
			case WARRIORBORN, VERDANT, FROSTBORN, EMBERBORN -> Set.of(ARMS, KI);
			case BIOBUILT -> Set.of(ARMS, KI, ARCANA);
			case EARTHLING -> variant == HeritageVariants.Variant.INKMARKED ? Set.of(ARCANA) : Set.of();
			case HOLLOW -> variant == HeritageVariants.Variant.ZOMBIE || variant == HeritageVariants.Variant.SKELETON ? Set.of(ARMS) : Set.of(ARCANA);
			default -> Set.of();
		};
	}

	/** Whether {@code player} can use discipline {@code d} at all: inclined, leaning, or taught. */
	static boolean open(Player player, int d) {
		return inclined(player).contains(d) || leans(player).contains(d) || (of(player).taught() & 1 << d) != 0;
	}

	/** How fast {@code d} trains for {@code player}: full when inclined, half on a lean, a quarter when taught; 0 locked. */
	static float speed(Player player, int d) {
		if (inclined(player).contains(d)) {
			return 1.0F;
		}
		if (leans(player).contains(d)) {
			return LEAN_SPEED;
		}
		return (of(player).taught() & 1 << d) != 0 ? TAUGHT_SPEED : 0.0F;
	}

	/** {@code player}'s level in {@code d} (a lean matching the class starts at {@link #KIN_START}). */
	public static int level(Player player, int d) {
		int level = of(player).level(d);
		if (inclined(player).contains(d) && leans(player).contains(d)) {
			level = Math.max(level, KIN_START);
		}
		return Math.min(MAX_LEVEL, level);
	}

	private static int blocksAt(int level, boolean inclined) {
		int step = 0;
		for (int i = 0; i < LEVEL_STEPS.length; i++) {
			if (level >= LEVEL_STEPS[i]) {
				step = i;
			}
		}
		return (inclined ? INCLINED_BLOCKS : TRAINED_BLOCKS)[step];
	}

	// ---- what Jaymo's Powers asks (com.jaymospowers.combat.PassiveBlocks, by reflection) --------------------------

	/** {@code player}'s block slots in catching order: the inclination's first, at most {@link #MAX_BLOCKS}. */
	public static int[] blocks(ServerPlayer player) {
		Set<Integer> inclined = inclined(player);
		List<Integer> slots = new ArrayList<>();
		for (int pass = 0; pass < 2; pass++) {
			for (int d = 0; d < KINDS; d++) {
				boolean mine = inclined.contains(d);
				if (mine != (pass == 0) || !open(player, d)) {
					continue;
				}
				int n = blocksAt(level(player, d), mine);
				for (int i = 0; i < n && slots.size() < MAX_BLOCKS; i++) {
					slots.add(d);
				}
			}
		}
		int[] out = new int[slots.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = slots.get(i);
		}
		return out;
	}

	/** Takes what a ki ward or a ward costs; false if {@code player} can't pay. Arms is free. */
	public static boolean spend(ServerPlayer player, int d) {
		if (player.isCreative() || d == ARMS) {
			return true;
		}
		if (d == ARCANA) {
			return Mana.canPay(player, WARD_MANA) && Mana.spend(player, WARD_MANA);
		}
		int ki = Monks.ki(player);
		if (ki > 0) {
			return Rests.spend(player, Rests.KI, 1, ki);
		}
		return PowersCompat.spendFeathers(player, OTHER_KI_FEATHERS);
	}

	/** Whether {@code player}'s bare fists are Valor-coated (Armament): they block at the full share. */
	public static boolean valorFists(ServerPlayer player) {
		return Valor.armed(player);
	}

	/** Keen Timing's rank (Strength): the parry windows widen back to the old ones at 3. */
	public static int keenTiming(ServerPlayer player) {
		return Skills.rank(player, KEEN);
	}

	/** Snatch: a Strength skill, the Thief's tree's, or any Monk's from the start. */
	public static boolean snatch(ServerPlayer player) {
		return Classes.is(player, PlayerClass.MONK) || Skills.rank(player, SNATCH) > 0 || ClassTrees.rank(player, SNATCH_NODE) > 0;
	}

	/** Iron Guard (Strength): a held guard costs less. */
	public static boolean ironGuard(ServerPlayer player) {
		return Skills.rank(player, IRON) > 0;
	}

	/** A block caught in discipline {@code d}: experience toward its next level, by how fast it trains. */
	public static void trained(ServerPlayer player, int d) {
		float speed = speed(player, d);
		if (speed <= 0.0F || d < 0 || d >= KINDS) {
			return;
		}
		Data data = of(player);
		List<Integer> levels = new ArrayList<>(List.of(data.level(0), data.level(1), data.level(2)));
		List<Float> xp = new ArrayList<>(List.of(data.xp(0), data.xp(1), data.xp(2)));
		int level = levels.get(d);
		if (level >= MAX_LEVEL) {
			return;
		}
		float next = xp.get(d) + speed;
		if (next >= XP_BASE + XP_STEP * level) {
			next = 0.0F;
			levels.set(d, level + 1);
			player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jaymoskingdoms.discipline_level",
				net.minecraft.network.chat.Component.translatable("discipline.jaymoskingdoms." + KEYS[d]), level + 1)
				.withStyle(net.minecraft.ChatFormatting.GOLD));
		}
		xp.set(d, next);
		player.setAttached(DATA, new Data(List.copyOf(levels), List.copyOf(xp), data.taught()));
	}

	public static final String[] KEYS = {"arms", "ki", "arcana"};

	/** A teacher opens discipline {@code d} for {@code player} (the drill yard's master-at-arms, a monk, a court wizard). */
	static void teach(ServerPlayer player, int d) {
		Data data = of(player);
		player.setAttached(DATA, new Data(data.levels(), data.xp(), data.taught() | 1 << d));
	}

	/** Whether {@code d} is locked for {@code player} (needs a teacher). */
	static boolean locked(@Nullable Player player, int d) {
		return player != null && !open(player, d);
	}
}
