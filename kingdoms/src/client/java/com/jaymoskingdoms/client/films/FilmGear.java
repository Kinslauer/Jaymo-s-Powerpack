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
 */
final class FilmGear {
	static final EquipmentSlot[] SLOTS = {EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
	/** Less solid than this (0-255) and a pixel of the armour is left out. */
	static final int SOLID = 24;

	private static final Map<String, Identifier> MADE = new HashMap<>();
	static @Nullable EquipmentAssetManager assets;

	private FilmGear() {
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
		int arm = ids[SLOTS.length].equals("slim") ? 3 : 4;
		for (int i = 0; i < SLOTS.length; i++) {
			Identifier id = ids[i].isEmpty() ? null : Identifier.tryParse(ids[i]);
			Equippable worn = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).map(item -> new ItemStack(item).get(DataComponents.EQUIPPABLE)).orElse(null);
			if (worn == null || worn.assetId().isEmpty()) {
				continue;
			}
			EquipmentClientInfo.LayerType type = SLOTS[i] == EquipmentSlot.LEGS ? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS : EquipmentClientInfo.LayerType.HUMANOID;
			for (EquipmentClientInfo.Layer layer : assets.get(worn.assetId().get()).getLayers(type)) {
				NativeImage armour = read(layer.getTextureLocation(type));
				if (armour == null) {
					continue;
				}
				if (armour.getWidth() == 64 && armour.getHeight() == 32) {
					wear(img, armour, SLOTS[i], arm);
				}
				armour.close();
			}
		}
		Identifier made = JaymosKingdoms.id("film_gear/" + number);
		Minecraft.getInstance().getTextureManager().register(made, new DynamicTexture(made::toString, img));
		return made;
	}

	private static void wear(NativeImage skin, NativeImage armour, EquipmentSlot slot, int arm) {
		switch (slot) {
			case HEAD -> {
				box(armour, 0, 0, 8, 8, 8, skin, 32, 0, 8, false);
				box(armour, 32, 0, 8, 8, 8, skin, 32, 0, 8, false);
			}
			case CHEST -> {
				box(armour, 16, 16, 8, 12, 4, skin, 16, 32, 8, false);
				box(armour, 40, 16, 4, 12, 4, skin, 40, 32, arm, false);
				box(armour, 40, 16, 4, 12, 4, skin, 48, 48, arm, true);
			}
			case LEGS -> {
				box(armour, 16, 16, 8, 12, 4, skin, 16, 32, 8, false);
				box(armour, 0, 16, 4, 12, 4, skin, 0, 32, 4, false);
				box(armour, 0, 16, 4, 12, 4, skin, 0, 48, 4, true);
			}
			default -> {
				box(armour, 0, 16, 4, 12, 4, skin, 0, 32, 4, false);
				box(armour, 0, 16, 4, 12, 4, skin, 0, 48, 4, true);
			}
		}
	}

	/**
	 * Copies a box of the armour's ({@code w} wide, {@code h} high, {@code d} deep, laid out at {@code sx, sy} the way the
	 * game lays out a body part) onto the skin's at {@code dx, dy}, which is {@code dw} wide (a slim arm is narrower);
	 * {@code mirror} for a left limb, which armour draws as the right one turned over.
	 */
	private static void box(NativeImage src, int sx, int sy, int w, int h, int d, NativeImage dst, int dx, int dy, int dw, boolean mirror) {
		// top, bottom
		face(src, sx + d, sy, w, d, dst, dx + d, dy, dw, mirror);
		face(src, sx + d + w, sy, w, d, dst, dx + d + dw, dy, dw, mirror);
		// the outer side, the front, the inner side, the back
		face(src, mirror ? sx + d + w : sx, sy + d, d, h, dst, dx, dy + d, d, mirror);
		face(src, sx + d, sy + d, w, h, dst, dx + d, dy + d, dw, mirror);
		face(src, mirror ? sx : sx + d + w, sy + d, d, h, dst, dx + d + dw, dy + d, d, mirror);
		face(src, sx + d + w + d, sy + d, w, h, dst, dx + d + dw + d, dy + d, dw, mirror);
	}

	private static void face(NativeImage src, int sx, int sy, int sw, int sh, NativeImage dst, int dx, int dy, int dw, boolean flip) {
		for (int y = 0; y < sh; y++) {
			for (int x = 0; x < dw; x++) {
				int from = Math.min(sw - 1, x), pixel = src.getPixel(flip ? sx + sw - 1 - from : sx + from, sy + y);
				if (ARGB.alpha(pixel) >= SOLID) {
					dst.setPixel(dx + x, dy + y, ARGB.opaque(pixel));
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
