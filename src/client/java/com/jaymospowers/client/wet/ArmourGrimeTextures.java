package com.jaymospowers.client.wet;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.blood.BloodParticles;
import com.jaymospowers.core.blood.Bloodied;
import com.jaymospowers.core.wet.ArmourGrime;
import com.jaymospowers.core.wet.Rust;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.19.74) Andy: "the dirty armor should be splotchier and reflect the grime in the png, so you can see it cleaned if you
 * wash it manually". Dirty armour ({@link ArmourGrime}) is drawn with a copy of its own texture that has grime painted
 * into it in splotches, and its inventory icon gets the same splotches on top. Every wash (a scrub at a cauldron, a wet
 * sponge, a dunk, rain, wading) takes splotches away, edges first, until the piece is clean.
 * <p>
 * Where the grime goes comes from our own masks in {@code textures/grime/} ({@code tools/grime_textures.py}): alpha is how
 * early a pixel gets dirty (the bottoms of the legs, boots and chest hem first), grey is how dark the grime is there. Dust
 * and mud have their own masks and colours and blend by how muddy the piece is. The armour's own picture is read from
 * whatever resource pack is drawing it, so the grime always sits on the armour's pixels and nowhere else. Its dark
 * outline pixels are left clean so the shape still reads.
 * <p>
 * Each kind of armour wears its grime its own way ({@link Material}): leather and cloth soak it up as dark stains, iron and
 * chain are caked over, gold sheds it from its polish and keeps it in its engraving, diamond packs it into its facets'
 * cracks, and netherite takes a pale film of dust. A body's own clothes and skin ({@link ArmourGrime#BODY}) are painted
 * over like a coat of blood ({@link #body}), from their own masks, as cloth.
 * <p>
 * Dust takes the colour of the ground it came off ({@link ArmourGrime.Grime#tint}). Grime shows early: a lightly dirty
 * piece already has splotches on every part, and a filthy one is caked nearly all over, so a wash has a lot to take away.
 * <p>
 * The colours suit the normal textures. With the optional Excalibur pack switched on, the grime takes Excalibur's grade
 * (darker, less saturated; {@code tools/excalibur_style/grade.py} on the default colours).
 * <p>
 * (0.20.41) Blood ({@link Bloodied}) is painted the same way over the grime, from its own masks in {@code textures/blood/}
 * ({@code tools/blood_textures.py}): armour, any item's icon and a held weapon ({@link #icon}, the client's
 * {@code BloodItemOverlay}), and a bloodied body's skin ({@link #skin}). It darkens to a dried brown as it ages.
 */
public final class ArmourGrimeTextures {
	/** Dust and mud colours for the normal textures, and graded for Excalibur. */
	static final int DUST = 0xB59E78, MUD = 0x5C4630;
	static final int EXCALIBUR_DUST = 0x94836B, EXCALIBUR_MUD = 0x41382E;
	/** How many steps of dirtiness, and of muddiness, get their own picture. */
	static final int STAGES = 10, MUD_STEPS = 3;
	/** How quickly the cover grows with dirtiness: below 1, a little dirt already shows a lot (cover = level to this power). */
	static final float COVER_CURVE = 0.6F;
	/** How far a stain soaked into cloth goes from the cloth's own (grime-dyed) colour toward the grime's colour. */
	static final float SOAK_TINT = 0.45F;
	/** Pixels darker than this (0..255 luminance) are outlines and stay clean. */
	static final int OUTLINE = 40;
	/** Painted pictures kept before all are thrown away and painted again as needed. */
	static final int MOST_KEPT = 600;

	private static final Map<String, Identifier> MADE = new HashMap<>();
	private static final Set<Identifier> UNREADABLE = new HashSet<>();
	private static @Nullable Mask armourDust, armourMud, itemDust, itemMud, bodyDust, bodyMud;
	/** (0.20.41) The blood masks: armour sheet, item icon, body skin. */
	private static @Nullable Mask armourBlood, itemBlood, bodyBlood;
	/** (0.20.41) Blood's steps of wetness (fresh to dried), how much of the mask's order soaked blood reaches, and how strong it is. */
	static final int DRY_STEPS = 2;
	static final float MOST_BLOOD = 0.85F, BLOOD_EDGE = 0.6F, BLOOD_HEART = 0.95F;
	/** (0.20.41) Dried blood is this dark a share of the fresh colour, and this much browner. */
	static final float DRIED = 0.5F;
	static final int DRIED_TOWARD = 0x3B1E12;
	/** Rotten blood turns toward this brown-green, this far at its worst (Bloodied.rot, in {@link #DRY_STEPS} steps). */
	static final int ROTTEN_TOWARD = 0x34301A;
	static final float ROTTEN_SHARE = 0.55F;
	/** Rust: its light and dark orange-browns, how much of the metal it reaches at its worst, how strong it is at a
	 * pit's edge and heart, how much sooner the creases rust, and how much the speckle breaks it up. */
	static final int RUST_LIGHT = 0xA65A2A, RUST_DARK = 0x5A2A14;
	static final float RUST_COVER = 0.95F, RUST_EDGE = 0.5F, RUST_HEART = 0.9F, RUST_CREVICE = 0.35F, RUST_PIT = 0.5F;
	private static boolean masksRead;
	private static boolean excalibur;
	private static int made;

	private ArmourGrimeTextures() {
	}

	public static void init() {
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(JaymosPowers.id("armour_grime"),
			(ResourceManagerReloadListener) manager -> reset());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> MADE.clear());
	}

	/** Throws every painted picture away (the resource packs changed, or too many were kept). */
	static void reset() {
		Minecraft minecraft = Minecraft.getInstance();
		for (Identifier id : MADE.values()) {
			minecraft.getTextureManager().release(id);
		}
		MADE.clear();
		UNREADABLE.clear();
		masksRead = false;
		armourDust = armourMud = itemDust = itemMud = bodyDust = bodyMud = null;
		armourBlood = itemBlood = bodyBlood = null;
		excalibur = false;
		for (String id : minecraft.getResourcePackRepository().getSelectedIds()) {
			if (id.toLowerCase(Locale.ROOT).contains("excalibur")) {
				excalibur = true;
				break;
			}
		}
	}

	/** The worn texture {@code base} with {@code grime} painted in, or {@code base} when it's clean or can't be read. */
	public static Identifier armour(Identifier base, ArmourGrime.@Nullable Grime grime) {
		return armour(base, grime, null);
	}

	/** (0.20.41) The worn texture {@code base} with {@code grime} and {@code blood} painted in, or {@code base} when it's clean. */
	public static Identifier armour(Identifier base, ArmourGrime.@Nullable Grime grime, Bloodied.@Nullable Blood blood) {
		return armour(base, grime, blood, 0);
	}

	/** The same with {@code rust} ({@link Rust}) pitted into the metal under the blood and grime. */
	public static Identifier armour(Identifier base, ArmourGrime.@Nullable Grime grime, Bloodied.@Nullable Blood blood, int rust) {
		boolean dirty = grime != null && grime.amount() > 0;
		Smear smear = smear(blood);
		int rusted = rustStage(rust);
		if (!dirty && smear == null && rusted == 0) {
			return base;
		}
		readMasks();
		if (armourDust == null || armourMud == null || armourBlood == null) {
			return base;
		}
		Identifier painted = paint(base, dirty ? grime : null, armourDust, armourMud, smear, armourBlood, false, false, false, rusted);
		return painted == null ? base : painted;
	}

	/** How rusty a piece looks, a stage {@code 0..}{@link #STAGES} (none below {@link Rust#SPOTTED}). */
	static int rustStage(int rust) {
		return rust < Rust.SPOTTED ? 0 : Mth.clamp(Mth.ceil(rust / (float) Rust.MAX * STAGES), 1, STAGES);
	}

	/** Just the grime splotches to lay over {@code stack}'s inventory icon, or null for none. */
	public static @Nullable Identifier icon(ItemStack stack) {
		ArmourGrime.Grime grime = ArmourGrime.grime(stack);
		boolean dirty = grime != null && grime.amount() > 0;
		// (0.20.41) and blood, on any item
		Smear smear = com.jaymospowers.client.blood.BloodSettings.off() ? null : smear(Bloodied.blood(stack));
		int rusted = rustStage(Rust.rust(stack));
		if (!dirty && smear == null && rusted == 0) {
			return null;
		}
		Identifier model = stack.get(DataComponents.ITEM_MODEL);
		if (model == null) {
			return null;
		}
		readMasks();
		if (itemDust == null || itemMud == null || itemBlood == null) {
			return null;
		}
		return paint(model.withPath("textures/item/" + model.getPath() + ".png"), dirty ? grime : null, itemDust, itemMud, smear, itemBlood, true,
			false, false, rusted);
	}

	/**
	 * Just the blood to lay over one texture of a modelled (3D) item ({@code BloodItemOverlay}): splotches where the
	 * texture {@code sprite} (a sprite name, like {@code minecraft:block/oak_log}) has pixels. Null for none.
	 */
	public static @Nullable Identifier sprite(Identifier sprite, Bloodied.@Nullable Blood blood) {
		Smear smear = smear(blood);
		if (smear == null) {
			return null;
		}
		readMasks();
		if (itemBlood == null) {
			return null;
		}
		return paint(sprite.withPath("textures/" + sprite.getPath() + ".png"), null, null, null, smear, itemBlood, true, false);
	}

	/**
	 * (0.20.41) Just the blood to lay over a body drawn from the skin {@code base} ({@code BloodCoatLayer}): splotches where
	 * the skin has pixels, or, for a skin that can't be read (a player's, downloaded), where the mask says, as if the
	 * skin were whole. Null for none.
	 */
	public static @Nullable Identifier skin(Identifier base, Bloodied.@Nullable Coat coat) {
		return skin(base, coat, false);
	}

	/**
	 * (0.20.66) The same, for a body drawn in a skin with an outer layer ({@code layered}: a humanoid's hat, jacket,
	 * sleeves and trousers): the blood is painted on the outer layer too, wherever that layer has pixels, so it sits on top
	 * of the clothes and hair instead of showing only through their gaps (Andy: it "seemed to be under the skin").
	 */
	public static @Nullable Identifier skin(Identifier base, Bloodied.@Nullable Coat coat, boolean layered) {
		if (coat == null || coat.amount() <= 0) {
			return null;
		}
		readMasks();
		if (bodyBlood == null) {
			return null;
		}
		// a coat dries and rots as blood on gear does (a coat from before it kept its age stays fresh)
		Smear smear = coat.laid() > 0 ? smear(new Bloodied.Blood(coat.amount(), coat.color(), coat.laid()))
			: new Smear(Mth.clamp(Mth.ceil(coat.level() * STAGES), 1, STAGES), coat.color() & 0xFFFFFF);
		if (smear == null) {
			return null;
		}
		Identifier painted = paint(base, null, null, null, smear, bodyBlood, true, false, layered);
		return painted != null ? painted : paint(JaymosPowers.id("blood/whole_skin"), null, null, null, smear, bodyBlood, true, true);
	}

	/**
	 * (0.20.55) The same for a corpse's blood ({@link Bloodied#CORPSE_BLOOD}): it stays, and darkens as it dries, as blood
	 * on gear does.
	 */
	public static @Nullable Identifier skin(Identifier base, Bloodied.@Nullable Blood blood) {
		return skin(base, blood, false);
	}

	/** (0.20.66) A corpse's blood over a layered skin ({@link #skin(Identifier, Bloodied.Coat, boolean)}). */
	public static @Nullable Identifier skin(Identifier base, Bloodied.@Nullable Blood blood, boolean layered) {
		Smear smear = smear(blood);
		if (smear == null) {
			return null;
		}
		readMasks();
		if (bodyBlood == null) {
			return null;
		}
		Identifier painted = paint(base, null, null, null, smear, bodyBlood, true, false, layered);
		return painted != null ? painted : paint(JaymosPowers.id("blood/whole_skin"), null, null, null, smear, bodyBlood, true, true);
	}

	/**
	 * Just the grime to lay over a body drawn from the skin {@code base}: splotches on its clothes and skin, legs first,
	 * soaked in as stains; over the outer layer too when {@code layered} (as {@link #skin} does blood). Null for none.
	 */
	public static @Nullable Identifier body(Identifier base, ArmourGrime.@Nullable Grime grime, boolean layered) {
		if (grime == null || grime.amount() <= 0) {
			return null;
		}
		readMasks();
		if (bodyDust == null || bodyMud == null) {
			return null;
		}
		Identifier painted = paint(base, grime, bodyDust, bodyMud, null, null, true, false, layered);
		return painted != null ? painted : paint(JaymosPowers.id("blood/whole_skin"), grime, bodyDust, bodyMud, null, null, true, true);
	}

	/**
	 * How a kind of armour wears its grime, from its texture's name: how much of the mask filthy armour reaches, how strong
	 * a splotch is at its edge and heart, how much sooner its dark creases get dirty than its bright faces ({@code crevice}),
	 * whether it soaks grime in as a stain (darkening its own colour) instead of being caked over, and how pale a film of
	 * dust it takes ({@code film}, for dark armour).
	 */
	enum Material {
		METAL(0.9F, 0.55F, 0.9F, 0.3F, false, 0.0F),
		CLOTH(0.95F, 0.5F, 0.85F, 0.0F, true, 0.0F),
		GOLD(0.75F, 0.45F, 0.8F, 0.6F, false, 0.0F),
		GEM(0.8F, 0.5F, 0.85F, 1.0F, false, 0.0F),
		DARK(0.9F, 0.4F, 0.7F, 0.2F, false, 0.45F),
		SHELL(0.85F, 0.5F, 0.85F, 0.5F, false, 0.0F);

		/** The pale dust dark armour's film goes toward. */
		static final int FILM = 0xD2C6B2;

		final float cover, edge, heart, crevice, film;
		final boolean soak;

		Material(float cover, float edge, float heart, float crevice, boolean soak, float film) {
			this.cover = cover;
			this.edge = edge;
			this.heart = heart;
			this.crevice = crevice;
			this.soak = soak;
			this.film = film;
		}

		static Material of(Identifier texture) {
			String path = texture.getPath().toLowerCase(Locale.ROOT);
			if (has(path, "leather", "cloth", "wool", "robe", "hide", "fur", "linen", "silk", "padded", "gambeson")) {
				return CLOTH;
			}
			if (has(path, "netherite", "obsidian", "blackened", "onyx")) {
				return DARK;
			}
			if (has(path, "gold", "gilded", "brass")) {
				return GOLD;
			}
			if (has(path, "diamond", "emerald", "amethyst", "crystal", "gem", "quartz")) {
				return GEM;
			}
			if (has(path, "turtle", "scute", "shell", "carapace", "bone")) {
				return SHELL;
			}
			return METAL;
		}

		private static boolean has(String path, String... words) {
			for (String word : words) {
				if (path.contains(word)) {
					return true;
				}
			}
			return false;
		}
	}

	/** (0.20.41) Blood to paint: how soaked (a stage, 1 to {@link #STAGES}) and its colour as it looks now (drying darker). */
	record Smear(int stage, int colour) {
	}

	private static @Nullable Smear smear(Bloodied.@Nullable Blood blood) {
		if (blood == null || blood.amount() <= 0) {
			return null;
		}
		var level = Minecraft.getInstance().level;
		float dry = level == null ? 0.0F : Bloodied.dryness(blood.laid(), level.getGameTime());
		dry = Math.round(dry * DRY_STEPS) / (float) DRY_STEPS;
		int fresh = blood.color() & 0xFFFFFF;
		if (fresh == BloodParticles.MAGMA) {
			// magma on a blade cools as it does on the ground
			fresh = BloodParticles.MAGMA_CRUST;
		}
		int darker = lerpRgb(0.5F, scale(fresh, DRIED), DRIED_TOWARD);
		float rot = level == null ? 0.0F : Math.round(Bloodied.rot(blood.laid(), level.getGameTime()) * DRY_STEPS) / (float) DRY_STEPS;
		int now = lerpRgb(dry, fresh, darker);
		if (rot > 0.0F) {
			now = lerpRgb(rot * ROTTEN_SHARE, now, ROTTEN_TOWARD);
		}
		return new Smear(Mth.clamp(Mth.ceil(blood.level() * STAGES), 1, STAGES), now);
	}

	private static int scale(int rgb, float k) {
		return Math.round((rgb >> 16 & 0xFF) * k) << 16 | Math.round((rgb >> 8 & 0xFF) * k) << 8 | Math.round((rgb & 0xFF) * k);
	}

	// ---- painting -------------------------------------------------------------------------------------------------------

	private static @Nullable Identifier paint(Identifier base, ArmourGrime.@Nullable Grime grime, @Nullable Mask dust, @Nullable Mask mud,
		@Nullable Smear smear, @Nullable Mask bloodMask, boolean overlay, boolean whole) {
		return paint(base, grime, dust, mud, smear, bloodMask, overlay, whole, false);
	}

	/**
	 * {@code layered} (0.20.66): {@code base} is a humanoid skin (64 by 64 in the player's layout, at any size) whose
	 * outer layer is drawn over its body, so each outer pixel the skin has takes the blood of the body pixel under it.
	 */
	private static @Nullable Identifier paint(Identifier base, ArmourGrime.@Nullable Grime grime, @Nullable Mask dust, @Nullable Mask mud,
		@Nullable Smear smear, @Nullable Mask bloodMask, boolean overlay, boolean whole, boolean layered) {
		return paint(base, grime, dust, mud, smear, bloodMask, overlay, whole, layered, 0);
	}

	/** {@code rust}: a rust stage ({@link #rustStage}, 0 for none), pitted into the metal first, under blood and grime. */
	private static @Nullable Identifier paint(Identifier base, ArmourGrime.@Nullable Grime grime, @Nullable Mask dust, @Nullable Mask mud,
		@Nullable Smear smear, @Nullable Mask bloodMask, boolean overlay, boolean whole, boolean layered, int rust) {
		if (UNREADABLE.contains(base)) {
			return null;
		}
		int stage = grime == null ? 0 : Mth.clamp(Mth.ceil(grime.level() * STAGES), 1, STAGES);
		int mudStep = grime == null ? 0 : Math.round(grime.muddiness() * MUD_STEPS);
		// rounded to steps, so a slowly changing colour doesn't paint a new picture every time
		int tint = grime == null || grime.tint() == 0 ? 0 : grime.tint() & 0xF0F0F0 | 0x080808;
		boolean onBody = dust != null && dust == bodyDust;
		Material material = onBody ? Material.CLOTH : Material.of(base);
		String key = base + "|" + stage + "|" + mudStep + "|" + tint + (onBody ? "|s" : "") + (smear == null ? "" : "|b" + smear.stage() + ":" + smear.colour()) + (overlay ? "|o" : "") + (layered ? "|l" : "") + (rust > 0 ? "|r" + rust : "");
		Identifier done = MADE.get(key);
		if (done != null) {
			return done;
		}
		NativeImage image;
		Mask wholeMask = bloodMask != null ? bloodMask : dust;
		if (whole && wholeMask != null) {
			// (0.20.41) a skin we can't read: paint over a whole, plain one the mask's size
			image = new NativeImage(wholeMask.width(), wholeMask.height(), false);
			for (int y = 0; y < wholeMask.height(); y++) {
				for (int x = 0; x < wholeMask.width(); x++) {
					image.setPixel(x, y, 0xFFFFFFFF);
				}
			}
		} else {
			image = read(base);
		}
		if (image == null) {
			UNREADABLE.add(base);
			return null;
		}
		if (MADE.size() >= MOST_KEPT) {
			reset();
		}
		float level = (float) Math.pow(stage / (float) STAGES, COVER_CURVE);
		float muddy = mudStep / (float) MUD_STEPS;
		int colour = lerpRgb(muddy, dust(tint), excalibur ? EXCALIBUR_MUD : MUD);
		float threshold = 1.0F - material.cover * level;
		float bloodThreshold = smear == null ? 2.0F : 1.0F - MOST_BLOOD * smear.stage() / (float) STAGES;
		float rustThreshold = rust == 0 ? 2.0F : 1.0F - RUST_COVER * (float) Math.pow(rust / (float) STAGES, COVER_CURVE);
		int w = image.getWidth(), h = image.getHeight();
		// (0.20.66) only a square skin has the outer layer's layout (an old 64 by 32 one is made square before it's drawn)
		boolean outer = layered && !whole && w == h && w >= 64;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int argb = image.getPixel(x, y);
				int a = argb >>> 24;
				int r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
				float lum = r * 0.3F + g * 0.59F + b * 0.11F;
				int out = overlay ? 0 : argb;
				if (rust > 0 && mud != null && a != 0 && lum >= OUTLINE) {
					// rust first, on the metal itself: where water sits (the mud's order: lower edges first) and in the dark
					// creases, pitted by a speckle of its own
					int mm = mud.at(x, y, w, h);
					float pit = pit(x, y);
					float order = (mm >>> 24) / 255.0F + RUST_CREVICE * (0.5F - lum / 255.0F) + (pit - 0.5F) * RUST_PIT;
					if ((mm >>> 24) > 0 && order > rustThreshold) {
						float strength = RUST_EDGE + (RUST_HEART - RUST_EDGE) * Math.min(1.0F, (order - rustThreshold) * 4.0F);
						int c = lerpRgb(pit, RUST_LIGHT, RUST_DARK);
						float k = 0.65F + 0.35F * lum / 255.0F;
						int rr = Math.round((c >> 16 & 0xFF) * k), rg = Math.round((c >> 8 & 0xFF) * k), rb = Math.round((c & 0xFF) * k);
						if (overlay) {
							out = Math.round(strength * a) << 24 | rr << 16 | rg << 8 | rb;
						} else {
							r = mix(r, rr, strength);
							g = mix(g, rg, strength);
							b = mix(b, rb, strength);
							out = a << 24 | r << 16 | g << 8 | b;
						}
					}
				}
				if (smear != null && bloodMask != null && a != 0 && (whole || lum >= OUTLINE || bloodMask == bodyBlood)) {
					// (0.20.41) blood first, so grime over it reads as grime and the blood's own pixels below it as blood
					int bm = outer ? bloodMask.at(under(x, y, w), w, h) : bloodMask.at(x, y, w, h);
					float order = (bm >>> 24) / 255.0F;
					if (order > 0.0F && order > bloodThreshold) {
						float shade = (bm & 0xFF) / 255.0F;
						float strength = BLOOD_EDGE + (BLOOD_HEART - BLOOD_EDGE) * Math.min(1.0F, (order - bloodThreshold) * 4.0F);
						float k = shade * (0.75F + 0.25F * lum / 255.0F);
						int c = smear.colour();
						int br = Math.round((c >> 16 & 0xFF) * k), bg = Math.round((c >> 8 & 0xFF) * k), bb = Math.round((c & 0xFF) * k);
						if (overlay) {
							out = Math.round(strength * a) << 24 | br << 16 | bg << 8 | bb;
						} else {
							r = mix(r, br, strength);
							g = mix(g, bg, strength);
							b = mix(b, bb, strength);
							out = a << 24 | r << 16 | g << 8 | b;
						}
					}
				}
				if (grime != null && dust != null && mud != null && a != 0 && (onBody || lum >= OUTLINE)) {
					int dm = outer ? dust.at(under(x, y, w), w, h) : dust.at(x, y, w, h);
					int mm = outer ? mud.at(under(x, y, w), w, h) : mud.at(x, y, w, h);
					float order = ((1.0F - muddy) * (dm >>> 24) + muddy * (mm >>> 24)) / 255.0F;
					if (order > 0.0F) {
						// dark creases fill first, bright faces last
						order += material.crevice * (0.5F - lum / 255.0F) * 0.6F;
					}
					if (order > 0.0F && order > threshold) {
						float shade = ((1.0F - muddy) * (dm & 0xFF) + muddy * (mm & 0xFF)) / 255.0F;
						float strength = material.edge + (material.heart - material.edge) * Math.min(1.0F, (order - threshold) * 4.0F);
						int gr, gg, gb;
						if (material.soak) {
							// soaked in: the cloth's own colour dyed by the grime, part way to the grime's own (dust dulls dark
							// cloth paler, mud stains light cloth darker), the weave still showing through
							float k = 0.55F + 0.45F * shade;
							gr = mix(Math.min(255, Math.round(r * (colour >> 16 & 0xFF) / 210.0F * k)), Math.round((colour >> 16 & 0xFF) * k), SOAK_TINT);
							gg = mix(Math.min(255, Math.round(g * (colour >> 8 & 0xFF) / 210.0F * k)), Math.round((colour >> 8 & 0xFF) * k), SOAK_TINT);
							gb = mix(Math.min(255, Math.round(b * (colour & 0xFF) / 210.0F * k)), Math.round((colour & 0xFF) * k), SOAK_TINT);
						} else {
							// caked on: the grime keeps a little of the armour's own light and shadow
							int c = material.film > 0.0F ? lerpRgb(material.film, colour, Material.FILM) : colour;
							float k = shade * (0.7F + 0.3F * lum / 255.0F);
							gr = Math.round((c >> 16 & 0xFF) * k);
							gg = Math.round((c >> 8 & 0xFF) * k);
							gb = Math.round((c & 0xFF) * k);
						}
						if (overlay) {
							int over = Math.round(strength * a);
							if ((out >>> 24) < over) {
								out = over << 24 | gr << 16 | gg << 8 | gb;
							}
						} else {
							out = a << 24 | mix(r, gr, strength) << 16 | mix(g, gg, strength) << 8 | mix(b, gb, strength);
						}
					}
				}
				image.setPixel(x, y, out);
			}
		}
		Identifier id = JaymosPowers.id("grime/" + made++);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
		MADE.put(key, id);
		return id;
	}

	/**
	 * (0.20.66) The body pixel under the skin pixel ({@code x}, {@code y}) of a square skin {@code w} across, packed as
	 * {@code y * w + x}: an outer-layer pixel's own body pixel (the hat over the head, the jacket over the body, each sleeve
	 * and trouser leg over its limb), any other pixel itself.
	 */
	static int under(int x, int y, int w) {
		int u = w / 64;
		int px = x / u, py = y / u;
		int dx = 0, dy = 0;
		if (py < 16 && px >= 32) {
			// hat over head
			dx = -32;
		} else if (py >= 32 && py < 48 && px < 56) {
			// right trouser leg, jacket, right sleeve over right leg, body, right arm
			dy = -16;
		} else if (py >= 48 && px < 16) {
			// left trouser leg over left leg
			dx = 16;
		} else if (py >= 48 && px >= 48) {
			// left sleeve over left arm
			dx = -16;
		}
		return (y + dy * u) * w + x + dx * u;
	}

	/** A fixed speckle (0..1) for pixel ({@code x}, {@code y}): where rust pits first and how dark it is there. */
	static float pit(int x, int y) {
		int n = x * 374761393 + y * 668265263;
		n = (n ^ (n >>> 13)) * 1274126177;
		return ((n ^ (n >>> 16)) & 0xFFFF) / 65535.0F;
	}

	/** The dust colour for a grime's {@code tint} (0: plain tan), graded like the plain tan when Excalibur is on. */
	private static int dust(int tint) {
		if (tint == 0) {
			return excalibur ? EXCALIBUR_DUST : DUST;
		}
		if (!excalibur) {
			return tint;
		}
		int r = (tint >> 16 & 0xFF) * (EXCALIBUR_DUST >> 16 & 0xFF) / (DUST >> 16 & 0xFF);
		int g = (tint >> 8 & 0xFF) * (EXCALIBUR_DUST >> 8 & 0xFF) / (DUST >> 8 & 0xFF);
		int b = (tint & 0xFF) * (EXCALIBUR_DUST & 0xFF) / (DUST & 0xFF);
		return Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b);
	}

	private static int mix(int from, int to, float t) {
		return Mth.clamp(Math.round(from + (to - from) * t), 0, 255);
	}

	private static int lerpRgb(float t, int from, int to) {
		return mix(from >> 16 & 0xFF, to >> 16 & 0xFF, t) << 16 | mix(from >> 8 & 0xFF, to >> 8 & 0xFF, t) << 8 | mix(from & 0xFF, to & 0xFF, t);
	}

	private static void readMasks() {
		if (masksRead) {
			return;
		}
		masksRead = true;
		armourDust = Mask.read(JaymosPowers.id("textures/grime/armour_dust.png"));
		armourMud = Mask.read(JaymosPowers.id("textures/grime/armour_mud.png"));
		itemDust = Mask.read(JaymosPowers.id("textures/grime/item_dust.png"));
		itemMud = Mask.read(JaymosPowers.id("textures/grime/item_mud.png"));
		bodyDust = Mask.read(JaymosPowers.id("textures/grime/body_dust.png"));
		bodyMud = Mask.read(JaymosPowers.id("textures/grime/body_mud.png"));
		armourBlood = Mask.read(JaymosPowers.id("textures/blood/armour_blood.png"));
		itemBlood = Mask.read(JaymosPowers.id("textures/blood/item_blood.png"));
		bodyBlood = Mask.read(JaymosPowers.id("textures/blood/body_blood.png"));
	}

	/** A fresh copy of {@code texture}'s pixels (a resource, or a downloaded skin), or null if it can't be read; the caller closes it. */
	public static @Nullable NativeImage copy(Identifier texture) {
		return read(texture);
	}

	private static @Nullable NativeImage read(Identifier texture) {
		Resource resource = Minecraft.getInstance().getResourceManager().getResource(texture).orElse(null);
		if (resource == null) {
			return downloaded(texture);
		}
		try (InputStream in = resource.open()) {
			return NativeImage.read(in);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * (0.20.66) A copy of a texture made in the game, not a resource: a player's downloaded skin (the skin downloader keeps
	 * it as a {@link DynamicTexture}, already squared to the 64 by 64 layout). Null for anything else.
	 */
	private static @Nullable NativeImage downloaded(Identifier texture) {
		try {
			if (Minecraft.getInstance().getTextureManager().getTexture(texture) instanceof DynamicTexture dynamic) {
				NativeImage pixels = dynamic.getPixels();
				if (pixels != null) {
					NativeImage copy = new NativeImage(pixels.getWidth(), pixels.getHeight(), false);
					copy.copyFrom(pixels);
					return copy;
				}
			}
		} catch (RuntimeException e) {
			// unreadable: painted as a whole plain skin instead
		}
		return null;
	}

	/** A grime mask's pixels (ARGB: alpha = order, blue = shade), sampled at the same spot of any size of texture. */
	record Mask(int width, int height, int[] pixels) {
		static @Nullable Mask read(Identifier id) {
			NativeImage image = ArmourGrimeTextures.read(id);
			if (image == null) {
				return null;
			}
			try (image) {
				int w = image.getWidth(), h = image.getHeight();
				int[] pixels = new int[w * h];
				for (int y = 0; y < h; y++) {
					for (int x = 0; x < w; x++) {
						pixels[y * w + x] = image.getPixel(x, y);
					}
				}
				return new Mask(w, h, pixels);
			}
		}

		/** (0.20.66) At a packed pixel ({@code y * w + x}). */
		int at(int packed, int w, int h) {
			return this.at(packed % w, packed / w, w, h);
		}

		int at(int x, int y, int w, int h) {
			return this.pixels[(y * this.height / h) * this.width + x * this.width / w];
		}
	}
}
