package com.jaymoskingdoms.client.films;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.films.FilmActor;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.jspecify.annotations.Nullable;

/**
 * The armour a film's actor wears. Andy: "They should all be wearing real armor too". An actor's body is jointed (an
 * elbow, a knee, a waist), so the game's own armour models, which are stiff from shoulder to wrist, can't go over it.
 * Instead what the actor really has on (its head, chest, legs and feet slots) is drawn onto its skin's outer layer from
 * that armour's own textures, as this game has them loaded (so a resource pack's steel is the film's steel): the helmet
 * on the hat, the chestplate on the jacket and sleeves, the leggings on the jacket and trousers, the boots on the
 * trousers, each face of each part copied to where the skin has it (the left limbs mirrored from the right, as armour
 * is). Armour that isn't drawn the plain way (no equipment asset, or a texture that isn't 64 by 32) leaves the skin as
 * it was painted. One texture is made per skin and set of armour, the first time it is drawn.
 *
 * <p>The line's armour has been fought in (Andy: "their armor is too shiny, it should be at max dirtiness and bloody
 * grime"): on a skin of the {@link #FOUGHT_IN} kind what is worn is dulled, caked with mud (thicker toward the foot of
 * each piece) and splashed with old blood, each man's his own way.
 */
final class FilmGear {
	static final EquipmentSlot[] SLOTS = {EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
	/** Less solid than this (0-255) and a pixel of the armour is left out. */
	static final int SOLID = 24;
	/** Skins whose armour is drawn filthy, and what leather is the colour of when nobody has dyed it. */
	static final String FOUGHT_IN = "soldier_";
	static final int LEATHER = 0xA06540;
	/**
	 * The dirt: how much of the armour's own light is left, how much of a pixel the mud takes at the least and at the most,
	 * the mud's two colours and dried blood's, how many splashes of blood a man has on him (and up to how many more) and
	 * how wide the widest is (pixels).
	 */
	static final float DULL = 0.6F, MUD_LEAST = 0.3F, MUD_MOST = 0.85F, BLOOD_DEEP = 0.82F;
	static final int MUD = 0x43362A, MUD_DARK = 0x221B15, BLOOD = 0x4F0D0B, SPLASHES = 7, SPLASHES_MORE = 5, SPLASH_WIDE = 3;

	private static final Map<String, Identifier> MADE = new HashMap<>();
	static @Nullable EquipmentAssetManager assets;

	private FilmGear() {
	}

	/** A cast skin that is a player's own: {@code PLAYER + <their uuid>}. */
	static final String PLAYER = "@player:";

	/** The skin of the player with id {@code uuid} in this level, or the default skin for that id if they're not here. */
	private static Identifier playerSkin(String uuid) {
		Minecraft minecraft = Minecraft.getInstance();
		java.util.UUID id;
		try {
			id = java.util.UUID.fromString(uuid);
		} catch (IllegalArgumentException e) {
			return net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture();
		}
		if (minecraft.level != null) {
			for (net.minecraft.client.player.AbstractClientPlayer player : minecraft.level.players()) {
				if (player.getUUID().equals(id)) {
					return player.getSkin().body().texturePath();
				}
			}
		}
		return net.minecraft.client.resources.DefaultPlayerSkin.get(id).body().texturePath();
	}

	/** What {@code actor} wears, as one word ("" for nothing): the same for the same armour on the same shape of arm. */
	static String key(FilmActor actor) {
		StringBuilder key = new StringBuilder();
		boolean any = false;
		for (EquipmentSlot slot : SLOTS) {
			ItemStack worn = actor.getItemBySlot(slot);
			any |= !worn.isEmpty();
			key.append(worn.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(worn.getItem()).toString()).append(',');
		}
		return any ? key.append(actor.body == FilmActor.SLIM ? "slim" : "wide").toString() : "";
	}

	/** {@code skin} with the armour of {@code key} on it. */
	static Identifier texture(String skin, String key) {
		if (skin.startsWith(PLAYER)) {
			// a real player cast as themselves (the Second Defence's morning): their own skin, as their game has it
			return playerSkin(skin.substring(PLAYER.length()));
		}
		Identifier plain = JaymosKingdoms.id("textures/entity/film/" + skin + ".png");
		return key.isEmpty() ? plain : MADE.computeIfAbsent(skin + "|" + key, k -> make(plain, key, MADE.size()));
	}

	private static Identifier make(Identifier plain, String key, int number) {
		NativeImage img = read(plain);
		if (img == null || img.getWidth() != 64 || img.getHeight() != 64 || assets == null) {
			if (img != null) {
				img.close();
			}
			return plain;
		}
		String[] ids = key.split(",", -1);
		boolean[] worn = new boolean[64 * 64];
		int arm = ids[SLOTS.length].equals("slim") ? 3 : 4;
		for (int i = 0; i < SLOTS.length; i++) {
			Identifier id = ids[i].isEmpty() ? null : Identifier.tryParse(ids[i]);
			Equippable equippable = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).map(item -> new ItemStack(item).get(DataComponents.EQUIPPABLE)).orElse(null);
			if (equippable == null || equippable.assetId().isEmpty()) {
				continue;
			}
			EquipmentClientInfo.LayerType type = SLOTS[i] == EquipmentSlot.LEGS ? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS : EquipmentClientInfo.LayerType.HUMANOID;
			for (EquipmentClientInfo.Layer layer : assets.get(equippable.assetId().get()).getLayers(type)) {
				NativeImage armour = read(layer.getTextureLocation(type));
				if (armour == null) {
					continue;
				}
				if (armour.getWidth() == 64 && armour.getHeight() == 32) {
					// (leather is drawn in the colour it has undyed)
					int tint = layer.dyeable().map(dye -> dye.colorWhenUndyed().orElse(LEATHER)).orElse(-1);
					wear(img, armour, SLOTS[i], arm, tint, worn);
				}
				armour.close();
			}
		}
		if (plain.getPath().contains("/" + FOUGHT_IN)) {
			foul(img, worn, (plain.getPath() + key).hashCode());
		}
		Identifier made = JaymosKingdoms.id("film_gear/" + number);
		Minecraft.getInstance().getTextureManager().register(made, new DynamicTexture(made::toString, img));
		return made;
	}

	/** Mud and blood over every pixel of armour ({@code worn}), laid out by {@code seed}. */
	private static void foul(NativeImage img, boolean[] worn, int seed) {
		java.util.Random rnd = new java.util.Random(seed);
		// the mud lies in soft patches: a coarse grid of how thick it is, read between its points
		float[] thick = new float[17 * 17];
		for (int i = 0; i < thick.length; i++) {
			thick[i] = rnd.nextFloat();
		}
		float[] blood = new float[64 * 64];
		for (int i = 0, many = SPLASHES + rnd.nextInt(SPLASHES_MORE + 1); i < many; i++) {
			int cx = rnd.nextInt(64), cy = rnd.nextInt(64);
			float wide = 1.0F + rnd.nextFloat() * (SPLASH_WIDE - 1.0F);
			for (int y = Math.max(0, cy - SPLASH_WIDE); y <= Math.min(63, cy + SPLASH_WIDE + 2); y++) {
				for (int x = Math.max(0, cx - SPLASH_WIDE); x <= Math.min(63, cx + SPLASH_WIDE); x++) {
					// (it ran a little before it dried)
					float dy = y > cy ? (y - cy) * 0.6F : y - cy, far = (float)Math.sqrt((x - cx) * (x - cx) + dy * dy) / wide + rnd.nextFloat() * 0.35F;
					blood[y * 64 + x] = Math.max(blood[y * 64 + x], far < 1.0F ? 1.0F - far * 0.4F : 0.0F);
				}
			}
		}
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				if (!worn[y * 64 + x]) {
					continue;
				}
				int gx = x / 4, gy = y / 4;
				float u = (x % 4) / 4.0F, v = (y % 4) / 4.0F;
				float mud = (thick[gy * 17 + gx] * (1.0F - u) + thick[gy * 17 + gx + 1] * u) * (1.0F - v)
					+ (thick[(gy + 1) * 17 + gx] * (1.0F - u) + thick[(gy + 1) * 17 + gx + 1] * u) * v;
				// (more of it lower down each piece, where the field got at it; a skin's rows of parts are 16 high)
				mud = Math.min(1.0F, mud * 0.75F + ((y % 16) / 16.0F) * 0.45F + (rnd.nextFloat() - 0.5F) * 0.2F);
				float share = MUD_LEAST + (MUD_MOST - MUD_LEAST) * Math.max(0.0F, mud);
				int pixel = img.getPixel(x, y), earth = ARGB.srgbLerp(Math.max(0.0F, mud), MUD, MUD_DARK);
				int dull = ARGB.color(255, (int)(ARGB.red(pixel) * DULL), (int)(ARGB.green(pixel) * DULL), (int)(ARGB.blue(pixel) * DULL));
				int out = ARGB.srgbLerp(share, dull, ARGB.opaque(earth));
				if (blood[y * 64 + x] > 0.0F) {
					out = ARGB.srgbLerp(BLOOD_DEEP * blood[y * 64 + x], out, ARGB.opaque(BLOOD));
				}
				img.setPixel(x, y, ARGB.opaque(out));
			}
		}
	}

	private static void wear(NativeImage skin, NativeImage armour, EquipmentSlot slot, int arm, int tint, boolean[] worn) {
		switch (slot) {
			case HEAD -> {
				box(armour, 0, 0, 8, 8, 8, skin, 32, 0, 8, false, tint, worn);
				box(armour, 32, 0, 8, 8, 8, skin, 32, 0, 8, false, tint, worn);
			}
			case CHEST -> {
				box(armour, 16, 16, 8, 12, 4, skin, 16, 32, 8, false, tint, worn);
				box(armour, 40, 16, 4, 12, 4, skin, 40, 32, arm, false, tint, worn);
				box(armour, 40, 16, 4, 12, 4, skin, 48, 48, arm, true, tint, worn);
			}
			case LEGS -> {
				box(armour, 16, 16, 8, 12, 4, skin, 16, 32, 8, false, tint, worn);
				box(armour, 0, 16, 4, 12, 4, skin, 0, 32, 4, false, tint, worn);
				box(armour, 0, 16, 4, 12, 4, skin, 0, 48, 4, true, tint, worn);
			}
			default -> {
				box(armour, 0, 16, 4, 12, 4, skin, 0, 32, 4, false, tint, worn);
				box(armour, 0, 16, 4, 12, 4, skin, 0, 48, 4, true, tint, worn);
			}
		}
	}

	/**
	 * Copies a box of the armour's ({@code w} wide, {@code h} high, {@code d} deep, laid out at {@code sx, sy} the way the
	 * game lays out a body part) onto the skin's at {@code dx, dy}, which is {@code dw} wide (a slim arm is narrower);
	 * {@code mirror} for a left limb, which armour draws as the right one turned over.
	 */
	private static void box(NativeImage src, int sx, int sy, int w, int h, int d, NativeImage dst, int dx, int dy, int dw, boolean mirror,
		int tint, boolean[] worn) {
		// top, bottom
		face(src, sx + d, sy, w, d, dst, dx + d, dy, dw, mirror, tint, worn);
		face(src, sx + d + w, sy, w, d, dst, dx + d + dw, dy, dw, mirror, tint, worn);
		// the outer side, the front, the inner side, the back
		face(src, mirror ? sx + d + w : sx, sy + d, d, h, dst, dx, dy + d, d, mirror, tint, worn);
		face(src, sx + d, sy + d, w, h, dst, dx + d, dy + d, dw, mirror, tint, worn);
		face(src, mirror ? sx : sx + d + w, sy + d, d, h, dst, dx + d + dw, dy + d, d, mirror, tint, worn);
		face(src, sx + d + w + d, sy + d, w, h, dst, dx + d + dw + d, dy + d, dw, mirror, tint, worn);
	}

	private static void face(NativeImage src, int sx, int sy, int sw, int sh, NativeImage dst, int dx, int dy, int dw, boolean flip, int tint,
		boolean[] worn) {
		for (int y = 0; y < sh; y++) {
			for (int x = 0; x < dw; x++) {
				int from = Math.min(sw - 1, x), pixel = src.getPixel(flip ? sx + sw - 1 - from : sx + from, sy + y);
				if (ARGB.alpha(pixel) >= SOLID) {
					dst.setPixel(dx + x, dy + y, ARGB.opaque(tint == -1 ? pixel : ARGB.multiply(pixel, ARGB.opaque(tint))));
					worn[(dy + y) * 64 + dx + x] = true;
				}
			}
		}
	}

	private static @Nullable NativeImage read(Identifier texture) {
		Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(texture);
		if (resource.isEmpty()) {
			return null;
		}
		try (InputStream in = resource.get().open()) {
			return NativeImage.read(in);
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}
}
