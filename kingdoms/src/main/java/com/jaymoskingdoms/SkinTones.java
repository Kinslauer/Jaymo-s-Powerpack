package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.44) Andy: villagers in hot, dry lands should tend darker-skinned, those in the snow lighter. MCA already leaned its
 * villagers' melanin on the biome's temperature, with one in five an "immigrant" whose skin ignored the land.
 * {@code mixin/mca/McaGeneticsMixin} re-rolls the melanin of every newly spawned MCA villager here, from where their
 * family comes from:
 * <ul>
 * <li><b>Natives</b> (most): a firm lean on the land's heat with a natural spread.</li>
 * <li><b>Newcomers</b> ({@link #NEWCOMER_CHANCE}): from a neighbouring climate, up to {@link #NEWCOMER_SHIFT} of biome
 * temperature away.</li>
 * <li><b>Immigrants</b> ({@link #IMMIGRANT_CHANCE}): from a far land, at least {@link #IMMIGRANT_MIN_SHIFT} away (a
 * desert family in a snow town, or the other way about).</li>
 * </ul>
 * Newcomers and immigrants remember where they're from ({@link #ORIGIN}) and sometimes bring it up when chatted to
 * ({@code mixin/mca/McaDialogueMixin}). Children still inherit from their parents (MCA doesn't randomize them), and
 * villagers already in the world keep their skin. Our own folk (guards, lords, kings, builders) wear climate faces
 * already ({@link Climate#skinGroup}).
 */
public final class SkinTones {
	/** Melanin at a biome temperature of 0 (the snow), and how much each degree of heat adds (the desert is 2). */
	static final float BASE = 0.06F, PER_HEAT = 0.42F;
	/** Biome temperatures are read within these bounds (frozen peaks are -0.7, deserts, savannas and badlands 2). */
	static final float MIN_HEAT = -0.5F, MAX_HEAT = 2.0F;
	/** Natural spread: melanin varies this much either side of the land's lean. */
	static final float SPREAD = 0.15F;
	/** The chance a villager's family came from a neighbouring climate, and how far that can be (in biome temperature). */
	static final float NEWCOMER_CHANCE = 0.15F, NEWCOMER_SHIFT = 0.9F;
	/** The chance a villager's family came from a far land (MCA's own immigrant chance is 0.2), and how far at least. */
	static final float IMMIGRANT_CHANCE = 0.2F, IMMIGRANT_MIN_SHIFT = 1.0F;
	/** Highlanders are a little paler: melanin lost per 128 blocks above sea level (MCA's own is 0.2). */
	static final float PER_ALTITUDE = 0.15F;
	/** The chance a newcomer or an immigrant talks about home when chatted to (instead of MCA's own small talk). */
	public static final float ORIGIN_TALK_CHANCE = 0.3F;

	/**
	 * (0.7.236) Andy: "the sliders should only allow the color ranges that the heritage allows". Each heritage's
	 * melanin, least to most (0 pale .. 1 dark), that MCA's skin picker may set on a player of that blood. My picks
	 * from each blood's land: Andy can change them here.
	 */
	static final float[] HEARTLANDER_SKIN = {0.0F, 1.0F}, STONEBORN_SKIN = {0.15F, 0.6F}, TIDEBORN_SKIN = {0.3F, 0.85F},
		SYLVAN_SKIN = {0.1F, 0.55F}, VERDANT_SKIN = {0.0F, 1.0F}, EMBERBORN_SKIN = {0.5F, 1.0F}, FROSTBORN_SKIN = {0.0F, 0.3F},
		NIGHTBORN_SKIN = {0.0F, 0.35F}, WARRIORBORN_SKIN = {0.2F, 0.9F}, EARTHLING_SKIN = {0.0F, 1.0F}, STORMBORN_SKIN = {0.15F, 0.6F},
		DAWNBORN_SKIN = {0.55F, 1.0F}, ENDBORN_SKIN = {0.0F, 0.3F}, DRAGONBLOOD_SKIN = {0.3F, 0.9F}, DEEPBORN_SKIN = {0.0F, 0.25F},
		// (0.7.262) the divine are pale, the made are anyone's shade (the plating takes over), rubber starts pale to take its pink
		DIVINE_SKIN = {0.0F, 0.35F}, BIOBUILT_SKIN = {0.0F, 1.0F}, RUBBERFOLK_SKIN = {0.0F, 0.4F},
		// (0.7.292) a goblin's or an orc's green is its own (Greenskins.skin): MCA's melanin isn't drawn on them
		BROOD_SKIN = {0.0F, 1.0F};

	/** (0.7.236) {@code kind}'s melanin range, least and most. */
	public static float[] skinRange(Heritage.Kind kind) {
		return switch (kind) {
			case HEARTLANDER -> HEARTLANDER_SKIN;
			case STONEBORN -> STONEBORN_SKIN;
			case TIDEBORN -> TIDEBORN_SKIN;
			case SYLVAN -> SYLVAN_SKIN;
			case VERDANT -> VERDANT_SKIN;
			case EMBERBORN -> EMBERBORN_SKIN;
			case FROSTBORN -> FROSTBORN_SKIN;
			case NIGHTBORN -> NIGHTBORN_SKIN;
			case WARRIORBORN -> WARRIORBORN_SKIN;
			case EARTHLING -> EARTHLING_SKIN;
			case STORMBORN -> STORMBORN_SKIN;
			case DAWNBORN -> DAWNBORN_SKIN;
			case ENDBORN -> ENDBORN_SKIN;
			case DRAGONBLOOD -> DRAGONBLOOD_SKIN;
			case DEEPBORN -> DEEPBORN_SKIN;
			case DIVINE -> DIVINE_SKIN;
			case BIOBUILT -> BIOBUILT_SKIN;
			case RUBBERFOLK -> RUBBERFOLK_SKIN;
			case GOBLIN, ORC -> BROOD_SKIN;
		};
	}

	/** (0.7.236) {@code melanin} brought into {@code kind}'s range (no heritage: as it is). */
	public static float clampSkin(Heritage.@Nullable Kind kind, float melanin) {
		if (kind == null) {
			return melanin;
		}
		float[] range = skinRange(kind);
		return Mth.clamp(melanin, range[0], range[1]);
	}

	/** (0.7.236) How far inside its heritage's range a villager born outside it lands, at most (a share of the range). */
	static final float BORN_INSIDE = 0.25F;

	/**
	 * (0.7.236) Andy: villagers too ("yes"). A villager's {@code melanin}, born of the land, brought into its heritage's
	 * range: as it was if it's inside, else a little way in from the nearer edge (so they don't all sit on the edge).
	 */
	public static float bornSkin(Heritage.@Nullable Kind kind, float melanin, RandomSource random) {
		if (kind == null) {
			return melanin;
		}
		float[] range = skinRange(kind);
		float inside = random.nextFloat() * BORN_INSIDE * (range[1] - range[0]);
		return melanin < range[0] ? range[0] + inside : melanin > range[1] ? range[1] - inside : melanin;
	}

	/** Where a villager's family is from, as {@code "newcomer.<land>"} or {@code "immigrant.<land>"} (none: a native). */
	public static final AttachmentType<String> ORIGIN = AttachmentRegistry.create(JaymosKingdoms.id("origin"),
		builder -> builder.persistent(Codec.STRING));

	private SkinTones() {
	}

	/** A melanin gene (0 pale .. 1 dark) for {@code villager}, born at its position; remembers where its family is from. */
	public static float melanin(Entity villager, RandomSource random) {
		Level level = villager.level();
		BlockPos pos = villager.blockPosition();
		float home = Mth.clamp(level.getBiome(pos).value().getBaseTemperature(), MIN_HEAT, MAX_HEAT);
		float heat = home;
		String kind = null;
		float roll = random.nextFloat();
		if (roll < IMMIGRANT_CHANCE) {
			for (int i = 0; i < 8 && Math.abs(heat - home) < IMMIGRANT_MIN_SHIFT; i++) {
				heat = MIN_HEAT + random.nextFloat() * (MAX_HEAT - MIN_HEAT);
			}
			kind = Math.abs(heat - home) >= IMMIGRANT_MIN_SHIFT ? "immigrant" : "newcomer";
		} else if (roll < IMMIGRANT_CHANCE + NEWCOMER_CHANCE) {
			heat = Mth.clamp(home + (random.nextFloat() * 2 - 1) * NEWCOMER_SHIFT, MIN_HEAT, MAX_HEAT);
			kind = "newcomer";
		}
		String land = land(heat);
		if (kind != null && !land.equals(land(home))) {
			villager.setAttached(ORIGIN, kind + "." + land);
		} else {
			villager.removeAttached(ORIGIN);
		}
		float altitude = (pos.getY() - level.getSeaLevel()) / 128.0F;
		float melanin = BASE + heat * PER_HEAT + (random.nextFloat() * 2 - 1) * SPREAD - altitude * PER_ALTITUDE;
		return Mth.clamp(melanin, 0, 1);
	}

	/** The kind of land a biome temperature reads as. */
	static String land(float heat) {
		if (heat >= 1.5F) {
			return "desert";
		} else if (heat >= 0.9F) {
			return "jungle";
		} else if (heat >= 0.5F) {
			return "plains";
		} else if (heat >= 0.15F) {
			return "taiga";
		}
		return "snow";
	}

	/** What {@code villager} says about home, or null for a native. */
	public static @Nullable String originTalk(Entity villager) {
		String origin = villager.getAttached(ORIGIN);
		if (origin == null || origin.indexOf('.') < 0) {
			return null;
		}
		return origin.substring(0, origin.indexOf('.'));
	}

	/** The land {@code villager}'s family came from, in words. */
	public static Component originLand(Entity villager) {
		String origin = villager.getAttached(ORIGIN);
		return Component.translatable("origin.jaymoskingdoms." + (origin == null ? "plains" : origin.substring(origin.indexOf('.') + 1)));
	}
}
