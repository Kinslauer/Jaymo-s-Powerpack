package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.jspecify.annotations.Nullable;

/**
 * Tissou's Zombie Pack (by Tissou, CurseForge 312822; free to use with credit, so the pack downloads it by id and we never
 * ship it: build.gradle's packResourcePacks) belongs to the future alone (Andy: "All can go into the future"). Through
 * Entity Texture Features its skins take the place of the game's own zombie, husk and drowned textures, and it repaints
 * the drowned, the zombie villagers and the villager's base outright. So none of it reaches the present:
 * <ul>
 * <li>the present's zombies and husks wear our own skins ({@link OldDeadSkins}), never the game's paths;</li>
 * <li>while the pack is on, a grown drowned is drawn with the game's own built-in drowned texture under our name
 * ({@link #DROWNED}: ETF finds no variants for it), and every path the pack repaints outright ({@link #MIRRORED}) is read
 * from the game's own built-in textures instead ({@link #mirror}, from mixin.deadlooks.TissouTextureManagerMixin);</li>
 * <li>only a share of the future's grown zombies are drawn with the game's zombie texture, for the pack to dress
 * ({@link OldDeadSkins#TISSOU_SHARE}), and only with ETF loaded; the rest keep Jaymo's Powers' Warrior-born skins.</li>
 * </ul>
 * Its zombie sounds (its sounds.json) are not held back: a sound has no place to tell the present's dead from the
 * future's.
 */
public final class TissouPack {
	/** The pack's file names start with this (its id once switched on: "file/" and the file name). */
	static final String PACK_PREFIX = "file/TZP_";
	static final boolean ETF = FabricLoader.getInstance().isModLoaded("entity_texture_features");
	/** The game's paths the pack repaints outright (besides the zombie's own, which the future's share is drawn with). */
	static final Set<String> MIRRORED = Set.of("textures/entity/zombie/drowned.png", "textures/entity/zombie/drowned_outer_layer.png",
		"textures/entity/zombie/husk.png", "textures/entity/villager/villager.png");
	static final String MIRRORED_FOLDER = "textures/entity/zombie_villager/";
	/** Our name for a game texture read from the game's own files ({@code textures/entity/mirror/<its path past textures/entity/>}). */
	static final String MIRROR = "textures/entity/mirror/";
	public static final Identifier DROWNED = JaymosKingdoms.id(MIRROR + "zombie/drowned.png");

	private static volatile boolean on;
	private static final Map<Identifier, AbstractTexture> MADE = new HashMap<>();

	private TissouPack() {
	}

	static void register() {
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(JaymosKingdoms.id("tissou_pack"),
			(ResourceManagerReloadListener) manager -> reload());
	}

	/** Whether the pack is switched on (checked at each resource reload, which every change of packs brings). */
	public static boolean on() {
		return on;
	}

	/** Whether it dresses the future's share: switched on, with ETF to draw its skins. */
	static boolean dresses() {
		return on && ETF;
	}

	private static void reload() {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.execute(() -> {
			for (Identifier id : MADE.keySet()) {
				minecraft.getTextureManager().release(id);
			}
			MADE.clear();
			on = minecraft.getResourcePackRepository().getSelectedIds().stream().anyMatch(id -> id.startsWith(PACK_PREFIX));
		});
	}

	/**
	 * (TextureManager.getTexture) While the pack is on: for a path it repaints, or one of our mirror names, the game's own
	 * texture read from its built-in files; otherwise null (the texture as it is).
	 */
	public static @Nullable AbstractTexture mirror(TextureManager manager, Identifier id) {
		if (!on) {
			return null;
		}
		Identifier ours;
		Identifier game;
		if (id.getNamespace().equals(JaymosKingdoms.MOD_ID) && id.getPath().startsWith(MIRROR)) {
			ours = id;
			game = Identifier.withDefaultNamespace("textures/entity/" + id.getPath().substring(MIRROR.length()));
		} else if (id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)
			&& (MIRRORED.contains(id.getPath()) || id.getPath().startsWith(MIRRORED_FOLDER))) {
			game = id;
			ours = JaymosKingdoms.id(MIRROR + id.getPath().substring("textures/entity/".length()));
		} else {
			return null;
		}
		AbstractTexture made = MADE.get(ours);
		if (made != null || MADE.containsKey(ours)) {
			return made;
		}
		NativeImage image = read(game);
		made = image == null ? null : new DynamicTexture(ours::toString, image);
		if (made != null) {
			manager.register(ours, made);
		}
		MADE.put(ours, made);
		return made;
	}

	/** A texture from the game's own built-in files, whatever packs are on. */
	private static @Nullable NativeImage read(Identifier texture) {
		IoSupplier<InputStream> resource = Minecraft.getInstance().getVanillaPackResources().fullResources()
			.getResource(PackType.CLIENT_RESOURCES, texture);
		if (resource == null) {
			return null;
		}
		try (InputStream in = resource.get()) {
			return NativeImage.read(in);
		} catch (Exception e) {
			JaymosKingdoms.LOGGER.warn("Couldn't read the game's own {}", texture, e);
			return null;
		}
	}
}
