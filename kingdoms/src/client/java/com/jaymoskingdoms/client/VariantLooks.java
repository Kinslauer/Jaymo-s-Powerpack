package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.HeritageVariants;
import com.jaymoskingdoms.HeritageVariants.Variant;
import com.jaymoskingdoms.JaymosKingdoms;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.214) How each heritage variant ({@link HeritageVariants}) looks, over the first look's way of drawing
 * ({@link HeritageLooks}, {@link VillagerLooks}): its own marks (textures from {@code tools/gen_heritage_looks.py},
 * {@code textures/entity/heritage/variant/<key>_skin|_glow|_eyes.png}), its eyes (or its heritage's), and its particle.
 * Its skin tint is MCA's skin layer's ({@code McaVerdantSkinMixin}). Tune the looks here.
 */
public final class VariantLooks {
	/** When a variant's particle drifts off its hand. */
	enum When { ALWAYS, NIGHT, DRY, WET, DARK_PLACE }

	/**
	 * One variant's look: its marks, its eyes' colour and when they glow (colour 0: its heritage's eyes as they are), its
	 * particle (one in {@code every} ticks, null: none) and when, and a Tideborn's fins.
	 */
	record Style(Variant variant, Identifier skin, Identifier glow, int eyeColor, @Nullable Identifier stockEyes, HeritageLooks.@Nullable Eyes eyes,
		@Nullable ParticleOptions particle, int every, When when, @Nullable Identifier fins, @Nullable Identifier finsGlow, Map<Identifier, Identifier> eyesBySkin) {
		String eyesPrefix() {
			return "variant_" + this.variant.key() + "_eyes/";
		}
	}

	private static final Map<Variant, Style> STYLES = new EnumMap<>(Variant.class);

	private static final ParticleOptions OBSIDIAN_GLINT = Fx.wisp(0x9A5CFF, 0.6F), THORN_PETAL = Fx.wisp(0xC8283C, 0.7F),
		GLOWCAP_SPORE = Fx.wisp(0xE8F05A, 0.5F), AURORA_MOTE = Fx.wisp(0x7CF0B4, 0.6F),
		MOON_MOTE = Fx.wisp(0xE6EAFF, 0.5F), LODE_SPARK = Fx.wisp(0xA06CE0, 0.5F),
		GILT_MOTE = Fx.wisp(0xFFD45A, 0.5F), GEODE_GLINT = Fx.wisp(0xB17BEB, 0.6F),
		REEF_MOTE = Fx.wisp(0xFF8A6A, 0.5F), INK_MOTE = Fx.wisp(0x2A2E44, 0.5F),
		// (0.7.490) the fae's pollen and leaf-light, a fox's fire, the elves' blood and the dead's chill
		LEAF_MOTE = Fx.wisp(0x7CB84A, 0.6F), POLLEN_MOTE = Fx.wisp(0xB8FFD8, 0.5F),
		WATER_MOTE = Fx.wisp(0x8CE0F0, 0.5F), FAE_MOTE = Fx.wisp(0xFFA8E8, 0.5F),
		FOX_FIRE = Fx.wisp(0xFF9A3C, 0.6F),
		CHILL_MOTE = Fx.wisp(0x9AF0FF, 0.5F),
		// (0.7.486) a jellyfish's shimmer and a sea slug's bright flecks (the weedfrond's leaf is LEAF_MOTE above)
		JELLY_MOTE = Fx.wisp(0xE0B8FF, 0.5F), FRILL_MOTE = Fx.wisp(0xFF8A30, 0.5F);

	static {
		// variant, eye colour (0: its heritage's), eyes, particle, every, when
		style(Variant.SUNFRECKLED, 0, null, null, 0, When.ALWAYS);
		style(Variant.SKYMARKED, 0xFFCFEFFF, HeritageLooks.Eyes.DAY, ParticleTypes.WHITE_ASH, 40, When.ALWAYS);
		style(Variant.OBSIDIAN, 0xFFC49CFF, HeritageLooks.Eyes.ALWAYS, ParticleTypes.DRIPPING_OBSIDIAN_TEAR, 40, When.ALWAYS);
		style(Variant.MAGMAVEIN, 0xFFFFA040, HeritageLooks.Eyes.ALWAYS, ParticleTypes.DRIPPING_LAVA, 30, When.DRY);
		style(Variant.REEFCROWNED, 0xFFFFB49C, null, REEF_MOTE, 24, When.WET);
		style(Variant.ABYSSAL, 0xFFE6FFFF, HeritageLooks.Eyes.ALWAYS, ParticleTypes.GLOW, 28, When.WET);
		// (0.7.486) the thirteen sea-blooded Tideborn looks (their model parts are SeaParts')
		style(Variant.GREYFIN, 0xFFE8EEF2, null, null, 0, When.WET);
		style(Variant.MANTAWING, 0xFFB8E0FF, null, null, 0, When.WET);
		style(Variant.EIGHTARM, 0xFFFFC060, null, INK_MOTE, 40, When.WET);
		style(Variant.LANTERNJAW, 0xFFF0FFE0, null, ParticleTypes.GLOW, 30, When.ALWAYS);
		style(Variant.DRIFTBELL, 0xFFF0C8FF, null, JELLY_MOTE, 18, When.ALWAYS);
		style(Variant.MORAY, 0xFFFFE060, null, null, 0, When.WET);
		style(Variant.SHELLCLAW, 0xFFFFD040, null, null, 0, When.WET);
		style(Variant.SPINEPUFF, 0xFF60D8FF, null, null, 0, When.WET);
		style(Variant.LIONMANE, 0xFFFF5040, null, REEF_MOTE, 30, When.WET);
		style(Variant.WEEDFROND, 0xFFFFB040, null, LEAF_MOTE, 40, When.WET);
		style(Variant.SWORDBILL, 0xFFC8E8FF, null, null, 0, When.WET);
		style(Variant.FRILLBACK, 0xFFFF9030, null, FRILL_MOTE, 24, When.WET);
		style(Variant.OLDSHELL, 0xFFE0D890, null, null, 0, When.WET);
		style(Variant.THORNROSE, 0xFFFF7088, HeritageLooks.Eyes.ALWAYS, THORN_PETAL, 26, When.ALWAYS);
		style(Variant.BLOSSOMKIN, 0xFFFFD0E4, HeritageLooks.Eyes.ALWAYS, ParticleTypes.CHERRY_LEAVES, 22, When.ALWAYS);
		style(Variant.GLOWCAP, 0xFFE8F57A, HeritageLooks.Eyes.NIGHT, GLOWCAP_SPORE, 20, When.ALWAYS);
		style(Variant.MOSSHIDE, 0, null, ParticleTypes.SPORE_BLOSSOM_AIR, 50, When.ALWAYS);
		style(Variant.SOULFLAME, 0xFF7CF4FF, HeritageLooks.Eyes.ALWAYS, ParticleTypes.SOUL_FIRE_FLAME, 12, When.DRY);
		style(Variant.ASHEN, 0xFFFFD8B0, HeritageLooks.Eyes.NIGHT, ParticleTypes.ASH, 10, When.ALWAYS);
		style(Variant.RIMEGLASS, 0xFFE8F8FF, HeritageLooks.Eyes.ALWAYS, ParticleTypes.SNOWFLAKE, 12, When.ALWAYS);
		style(Variant.AURORA, 0xFF9CFFD0, HeritageLooks.Eyes.NIGHT, AURORA_MOTE, 14, When.NIGHT);
		style(Variant.MOONPALE, 0xFFF4F6FF, HeritageLooks.Eyes.NIGHT, MOON_MOTE, 18, When.NIGHT);
		style(Variant.UMBRAL, 0xFFB8A8FF, HeritageLooks.Eyes.ALWAYS, ParticleTypes.SMOKE, 10, When.ALWAYS);
		style(Variant.BATTLESCARRED, 0, null, null, 0, When.ALWAYS);
		style(Variant.WARPAINTED, 0, null, null, 0, When.ALWAYS);
		style(Variant.IRONKNUCKLE, 0, null, null, 0, When.ALWAYS);
		style(Variant.INKMARKED, 0, null, INK_MOTE, 60, When.ALWAYS);
		style(Variant.LODEBORN, 0xFFC4A8FF, HeritageLooks.Eyes.STORM, LODE_SPARK, 16, When.ALWAYS);
		style(Variant.GALEBORN, 0xFFF0F8FF, HeritageLooks.Eyes.STORM, ParticleTypes.CLOUD, 40, When.ALWAYS);
		style(Variant.TWILIGHT, 0xFFFFE6C8, HeritageLooks.Eyes.ALWAYS, null, 0, When.ALWAYS);
		style(Variant.GILDED, 0xFFFFE08A, HeritageLooks.Eyes.DAY, GILT_MOTE, 24, When.ALWAYS);
		style(Variant.CHORUSGROWN, 0xFFF0B4FF, HeritageLooks.Eyes.ALWAYS, ParticleTypes.REVERSE_PORTAL, 16, When.DRY);
		style(Variant.VOIDPALE, 0xFF2A1440, HeritageLooks.Eyes.ALWAYS, ParticleTypes.PORTAL, 14, When.DRY);
		style(Variant.GOLDSCALE, 0xFFFFD060, HeritageLooks.Eyes.NIGHT, GILT_MOTE, 40, When.ALWAYS);
		style(Variant.RIMEWYRM, 0xFFB8E8FF, HeritageLooks.Eyes.NIGHT, ParticleTypes.SNOWFLAKE, 18, When.ALWAYS);
		style(Variant.HOLLOW, 0xFFF0FFFC, HeritageLooks.Eyes.ALWAYS, ParticleTypes.SCULK_SOUL, 50, When.DARK_PLACE);
		style(Variant.GEODE, 0xFFD6AAFF, HeritageLooks.Eyes.ALWAYS, GEODE_GLINT, 20, When.ALWAYS);
		// (0.7.262) the Divine-born's, the Bio-built's and the Rubber-folk's
		style(Variant.STARLIT, 0xFFCFE0FF, HeritageLooks.Eyes.ALWAYS, ParticleTypes.END_ROD, 40, When.NIGHT);
		style(Variant.SERAPHIC, 0xFFFFF4D0, HeritageLooks.Eyes.ALWAYS, ParticleTypes.WHITE_ASH, 36, When.ALWAYS);
		style(Variant.BRASSBOUND, 0xFFFFB050, HeritageLooks.Eyes.ALWAYS, null, 0, When.ALWAYS);
		style(Variant.OPALINE, 0xFFE8FFF8, HeritageLooks.Eyes.ALWAYS, ParticleTypes.GLOW, 60, When.DARK_PLACE);
		style(Variant.CANDYSTRIPE, 0, null, null, 0, When.ALWAYS);
		style(Variant.BERRYGLOSS, 0, null, null, 0, When.ALWAYS);
		// (0.7.490) the Sylvan's fae and elves
		style(Variant.DRYAD, 0xFFB4E070, HeritageLooks.Eyes.ALWAYS, LEAF_MOTE, 30, When.ALWAYS);
		style(Variant.CHANGELING, 0xFFC8E0A0, HeritageLooks.Eyes.NIGHT, null, 0, When.ALWAYS);
		style(Variant.SPRITE, 0xFFC8FFE0, HeritageLooks.Eyes.ALWAYS, POLLEN_MOTE, 14, When.ALWAYS);
		style(Variant.NYMPH, 0xFF9CF0F0, HeritageLooks.Eyes.ALWAYS, WATER_MOTE, 30, When.ALWAYS);
		style(Variant.FAERIE, 0xFFFFC0F0, HeritageLooks.Eyes.ALWAYS, FAE_MOTE, 16, When.ALWAYS);
		style(Variant.HIGHELF, 0xFFFFE890, HeritageLooks.Eyes.DAY, ParticleTypes.WHITE_ASH, 50, When.ALWAYS);
		style(Variant.MOONELF, 0xFFB0C0FF, HeritageLooks.Eyes.NIGHT, MOON_MOTE, 18, When.NIGHT);
		// (0.7.646) Andy: "no redstone textures": a Blood Elf's motes are Powers' blood flecks
		style(Variant.BLOODELF, 0xFFFF5060, HeritageLooks.Eyes.ALWAYS, com.jaymoskingdoms.PowersCompat.blood(0xC0182A, 0.6F), 30, When.DRY);
		// the Beastfolk's family
		style(Variant.ARACHNID, 0xFFFF3030, HeritageLooks.Eyes.ALWAYS, null, 0, When.ALWAYS);
		style(Variant.AVIAN, 0xFFFFE070, HeritageLooks.Eyes.DAY, ParticleTypes.CLOUD, 70, When.ALWAYS);
		style(Variant.CANINE, 0xFFFFD060, HeritageLooks.Eyes.NIGHT, null, 0, When.ALWAYS);
		style(Variant.FELINE, 0xFFC8FF60, HeritageLooks.Eyes.NIGHT, null, 0, When.ALWAYS);
		style(Variant.KITSUNE, 0xFFFFA040, HeritageLooks.Eyes.ALWAYS, FOX_FIRE, 24, When.NIGHT);
		style(Variant.SERPEN, 0xFFE8F060, HeritageLooks.Eyes.ALWAYS, null, 0, When.ALWAYS);
		// the Hollow dead
		style(Variant.ZOMBIE, 0xFFA8FF70, HeritageLooks.Eyes.NIGHT, null, 0, When.ALWAYS);
		style(Variant.SKELETON, 0xFFF0F4EC, HeritageLooks.Eyes.ALWAYS, null, 0, When.ALWAYS);
		style(Variant.WRAITH, 0xFF9CF0FF, HeritageLooks.Eyes.ALWAYS, CHILL_MOTE, 16, When.ALWAYS);
		style(Variant.REAPER, 0xFFFF4040, HeritageLooks.Eyes.ALWAYS, ParticleTypes.SMOKE, 14, When.ALWAYS);
	}

	private VariantLooks() {
	}

	private static void style(Variant v, int eyeColor, HeritageLooks.@Nullable Eyes eyes, @Nullable ParticleOptions particle, int every, When when) {
		String root = "textures/entity/heritage/variant/" + v.key();
		// (0.7.486) every Tideborn look has its own fins' colours (those SeaParts says keep them)
		boolean fins = v.heritage == com.jaymoskingdoms.Heritage.Kind.TIDEBORN;
		STYLES.put(v, new Style(v, JaymosKingdoms.id(root + "_skin.png"), JaymosKingdoms.id(root + "_glow.png"), eyeColor,
			eyeColor == 0 ? null : JaymosKingdoms.id(root + "_eyes.png"), eyes, particle, every, when,
			fins ? JaymosKingdoms.id(root + "_fins.png") : null, fins ? JaymosKingdoms.id(root + "_fins_glow.png") : null, new HashMap<>()));
	}

	/** {@code v}'s look (null: the first look, drawn as it always was). */
	static @Nullable Style of(@Nullable Variant v) {
		return v == null ? null : STYLES.get(v);
	}

	/** {@code entity}'s variant's look, or null. */
	static @Nullable Style of(LivingEntity entity) {
		return of(HeritageVariants.of(entity));
	}

	/** Whether {@code style}'s particle drifts off {@code entity} now. */
	static boolean particleNow(Style style, LivingEntity entity, RandomSource random) {
		if (style.particle() == null || style.every() <= 0 || random.nextInt(style.every()) != 0) {
			return false;
		}
		return switch (style.when()) {
			case ALWAYS -> true;
			case NIGHT -> entity.level().isDarkOutside();
			case DRY -> !entity.isInWaterOrRain();
			case WET -> entity.isInWaterOrRain();
			case DARK_PLACE -> entity.level().getMaxLocalRawBrightness(entity.blockPosition()) <= 7;
		};
	}

	/** One of {@code style}'s particles off {@code entity}'s hand. */
	static void particle(ClientLevel level, LivingEntity entity, Style style, RandomSource random) {
		ParticleOptions particle = style.particle();
		if (particle == null) {
			return;
		}
		// a hand hangs at the side, a little below the waist
		float yaw = entity.yBodyRot * Mth.DEG_TO_RAD;
		double side = (random.nextBoolean() ? 1.0 : -1.0) * entity.getBbWidth() * 0.6;
		double x = entity.getX() - Mth.cos(yaw) * side, z = entity.getZ() - Mth.sin(yaw) * side;
		double y = entity.getY() + entity.getBbHeight() * 0.4;
		if (particle == ParticleTypes.CHERRY_LEAVES || particle == ParticleTypes.WHITE_ASH || particle == ParticleTypes.CLOUD) {
			// petals, motes and wisps drift off the whole body
			x = entity.getX() + (random.nextDouble() - 0.5) * 1.2;
			y = entity.getY() + random.nextDouble() * entity.getBbHeight();
			z = entity.getZ() + (random.nextDouble() - 0.5) * 1.2;
		}
		double up = particle == ParticleTypes.CLOUD ? 0.01 : particle == ParticleTypes.PORTAL ? -0.2 : 0.02;
		level.addParticle(particle, x, y, z, 0.0, up, 0.0);
	}
}
