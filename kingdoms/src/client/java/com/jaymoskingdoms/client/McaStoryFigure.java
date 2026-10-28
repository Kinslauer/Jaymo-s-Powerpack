package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.HeritageVariants;
import com.jaymoskingdoms.McaCompat;
import com.jaymoskingdoms.SkinTones;
import com.jaymoskingdoms.Wardrobe;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.286) The life story's figure with MCA loaded ({@link StoryFigure}). Andy: "the player's character doesnt load.
 * only armor": the story comes before MCA's creator, so the player has no MCA body yet, and MCA draws no plain one in its
 * place. So the figure is a stand-in MCA villager that is never put in the world (as MCA's own editor figure is): your
 * body and gender guessed from your id, in clothes from MCA's job wardrobe that fit the scene (none of the banned ones,
 * {@link Wardrobe}), wearing the heritage and look shown. Its skin is tinted for the heritage through {@link EditorFigure}.
 * Only loaded when MCA is.
 */
final class McaStoryFigure {
	/** MCA's hair count per gender (hair/male/0..n-1.png), and the clothes tried for a scene, in order, before these. */
	static final int HAIRS = 20, CLOTHES_VARIANTS = 5;
	static final List<String> FALLBACK_JOBS = List.of("shepherd", "farmer", "mason", "guard", "cleric");

	private static @Nullable VillagerEntityMCA figure;
	private static @Nullable Level figureLevel;
	private static String dressedFor = "";
	/** (MCA's swing is ticked by the world, which this figure isn't in: ticked here.) */
	private static @Nullable Field swingState;
	private static @Nullable Method swingTick;
	private static boolean swingLooked;

	private McaStoryFigure() {
	}

	/** The stand-in for {@code player}, dressed for {@code job}, as heritage {@code kind}'s look {@code look}; null if it can't be made. */
	static @Nullable LivingEntity figure(LocalPlayer player, Heritage.@Nullable Kind kind, int look, String job) {
		ClientLevel level = (ClientLevel)player.level();
		if (figure == null || figureLevel != level) {
			figure = make(player, level);
			figureLevel = level;
			dressedFor = "";
			if (figure == null) {
				return null;
			}
		}
		VillagerEntityMCA v = figure;
		if (!job.equals(dressedFor)) {
			dressedFor = job;
			v.setClothes(clothes(v, job));
		}
		if (kind != null) {
			HeritageVariants.Variant variant = HeritageVariants.at(kind, look);
			v.setAttached(Heritage.HERITAGE, kind.key());
			v.setAttached(HeritageVariants.VARIANT, variant == null ? HeritageVariants.BASE : variant.key());
		} else {
			v.removeAttached(Heritage.HERITAGE);
			v.removeAttached(HeritageVariants.VARIANT);
		}
		Genetics genes = v.getGenetics();
		float melanin = genes.getGene(Genetics.MELANIN), kept = SkinTones.clampSkin(kind, melanin);
		if (kept != melanin) {
			genes.setGene(Genetics.MELANIN, kept);
		}
		EditorFigure.set(v);
		return v;
	}

	/** Lets the figure go (the story closed): it's no longer the editor figure. */
	static void release() {
		if (figure != null && EditorFigure.current() == figure) {
			EditorFigure.set(null);
		}
	}

	/** One world tick for the figure's arm swing and idle sway. */
	static void tick(LivingEntity entity) {
		entity.tickCount++;
		if (!swingLooked) {
			swingLooked = true;
			try {
				swingState = LivingEntity.class.getDeclaredField("swingState");
				swingState.setAccessible(true);
				swingTick = swingState.getType().getMethod("tick");
			} catch (ReflectiveOperationException | RuntimeException e) {
				swingState = null;
			}
		}
		if (swingState != null && swingTick != null) {
			try {
				swingTick.invoke(swingState.get(entity));
			} catch (ReflectiveOperationException | RuntimeException e) {
				swingState = null;
			}
		}
	}

	private static @Nullable VillagerEntityMCA make(LocalPlayer player, ClientLevel level) {
		UUID id = player.getUUID();
		boolean female = (id.getLeastSignificantBits() & 1L) != 0L;
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.fromNamespaceAndPath(McaCompat.MOD, female ? "female_villager" : "male_villager"))
			.orElse(null);
		if (type == null || !(type.create(level, EntitySpawnReason.LOAD) instanceof VillagerEntityMCA v)) {
			return null;
		}
		Genetics genes = v.getGenetics();
		genes.randomize();
		genes.setGender(female ? Gender.FEMALE : Gender.MALE);
		// (an ordinary grown body: the creator after the story decides the rest)
		genes.setGene(Genetics.SIZE, 0.5F);
		genes.setGene(Genetics.WIDTH, 0.5F);
		v.setHair("mca:skins/hair/" + (female ? "female" : "male") + "/" + Math.floorMod(id.hashCode(), HAIRS) + ".png");
		return v;
	}

	/** MCA job clothes for {@code job} that villagers may wear, falling back to plain jobs' clothes. */
	private static String clothes(VillagerEntityMCA v, String job) {
		String gender = v.getGenetics().getGender() == Gender.FEMALE ? "female" : "male";
		java.util.ArrayList<String> jobs = new java.util.ArrayList<>();
		jobs.add(job);
		jobs.addAll(FALLBACK_JOBS);
		for (String j : jobs) {
			for (int i = 0; i < CLOTHES_VARIANTS; i++) {
				String id = "mca:skins/clothing/normal/" + gender + "/" + j + "/" + i + ".png";
				if (!Wardrobe.banned(id)) {
					return id;
				}
			}
		}
		return "mca:skins/clothing/normal/" + gender + "/guard/0.png";
	}
}
