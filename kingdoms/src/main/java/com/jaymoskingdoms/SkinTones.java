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
 * (0.5.38) Andy: villagers in hot, dry lands should tend darker-skinned, those in the snow lighter. MCA already leaned its
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
