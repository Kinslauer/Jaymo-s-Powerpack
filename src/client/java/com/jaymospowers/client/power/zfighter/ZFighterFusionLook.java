package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ZFighterFusion;
import com.jaymospowers.power.zfighter.ZFighterLineage;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

/**
 * (0.16.16) Andy: "the fusion should combine the two fused character's models". The fused body ({@link ZFighterFusion#LOOK})
 * is drawn as both of them, the same before and after they change places: the first to dance gives the build, the face
 * and the top half of the outfit; the second gives the hair, the legs and the trousers; skin and hair colour come out
 * between the two; one eye is each one's. A tail or antennae either one has, the body has.
 * <ul>
 * <li><b>MCA's villager body</b> (every player in the pack): its visuals on the render state are swapped for a blend of
 * both players' (genes averaged, hair layers mixed, the two outfits sewn into one texture), read from MCA by name, since
 * Powers doesn't compile against it.</li>
 * <li><b>A plain skin</b>: the first's skin, with the second's hair (hat layer) and legs, its skin tones evened between them.</li>
 * </ul>
 */
public final class ZFighterFusionLook {
	/** The second's skin texture, on a fused body drawn with a plain skin. */
	static final RenderStateDataKey<Identifier> KEY = RenderStateDataKey.create(() -> "jaymospowers:zfighter_fusion");
	/** A skin with fewer opaque hat-layer pixels than this has no hair of its own there (the first's stays). */
	static final int HAIR_PIXELS = 24;
	/** How far each skin's tones move toward the pair's middle (1: all the way). */
	static final float TONE_MIX = 1.0F;
	/** The bigger of the two builds, and this much more: two fighters in one. */
	static final float SIZE_BONUS = 1.04F;

	/** Sewn skins and outfits: "<first>|<second>" → the texture, once built. */
	private static final Map<String, Identifier> SEWN = new HashMap<>();
	private static final String MCA = "net.conczin.mca.";
	private static final boolean MCA_LOADED = FabricLoader.getInstance().isModLoaded("mca");
	private static boolean mcaBroken;
	private static @Nullable Class<?> likeClass;
	private static @Nullable Method getData, setData, dataModel, dataVisuals, playerData, capture, eyeColor, libraryTexture;
	private static @Nullable Constructor<?> newData, newVisuals;
	private static RecordComponent @Nullable [] components;

	private ZFighterFusionLook() {
	}

	/** The fused look, if {@code player}'s body is two fused; null otherwise (and never for the Great Ape). */
	static ZFighterFusion.@Nullable Look of(Player player) {
		ZFighterFusion.Look look = player.getAttached(ZFighterFusion.LOOK);
		return look == null || ZFighterLineage.ape(player) ? null : look;
	}

	/** The one watching from inside the fused body {@code player}. */
	static UUID inner(Player player, ZFighterFusion.Look look) {
		return player.getUUID().equals(look.first()) ? look.second() : look.first();
	}

	/** From the avatar renderer's extractRenderState, after MCA has put its visuals on the state. */
	static void extract(Avatar entity, AvatarRenderState state) {
		state.setData(KEY, null);
		ZFighterFusion.Look look = entity instanceof Player player ? of(player) : null;
		if (look == null) {
			return;
		}
		PlayerSkin first = skin(look.first()), second = skin(look.second());
		if (first != null && second != null) {
			// built as the first, whoever steers
			state.skin = first;
			state.setData(KEY, second.body().texturePath());
		}
		if (MCA_LOADED && !mcaBroken) {
			try {
				blendMca(entity, state, look);
			} catch (ReflectiveOperationException | RuntimeException e) {
				mcaBroken = true;
				JaymosPowers.LOGGER.warn("Couldn't blend MCA's looks for a fusion; the fused body looks like whoever steers", e);
			}
		}
	}

	private static @Nullable PlayerSkin skin(UUID id) {
		ClientPacketListener connection = Minecraft.getInstance().getConnection();
		PlayerInfo info = connection == null ? null : connection.getPlayerInfo(id);
		return info == null ? null : info.getSkin();
	}

	/** The fused body's plain skin: {@code texture} (the first's) sewn with the second's; the first's until both can be read. */
	public static Identifier skin(AvatarRenderState state, Identifier texture) {
		Identifier second = state.getData(KEY);
		if (second == null || second.equals(texture)) {
			return texture;
		}
		String key = "skin|" + texture + "|" + second;
		Identifier done = SEWN.get(key);
		if (done != null) {
			return done;
		}
		NativeImage a = pixels(texture), b = pixels(second);
		try {
			if (a == null || b == null || a.getWidth() != 64 || a.getHeight() != 64 || b.getWidth() != 64 || b.getHeight() != 64) {
				return texture;
			}
			NativeImage sewn = new NativeImage(64, 64, true);
			sewn.copyFrom(a);
			boolean[] fromSecond = new boolean[64 * 64];
			// the second's hair, if their skin has any on the hat layer
			if (opaque(b, 32, 0, 64, 16) >= HAIR_PIXELS) {
				copy(b, sewn, fromSecond, 32, 0, 64, 16);
			}
			legs(b, sewn, fromSecond);
			evenTones(sewn, fromSecond, a, b);
			return register(key, JaymosPowers.id("zfighter_fusion_skin/" + Integer.toHexString(key.hashCode())), sewn);
		} finally {
			close(a, b);
		}
	}

	// ---------------------------------------------------------------- MCA

	private static void blendMca(Avatar entity, AvatarRenderState state, ZFighterFusion.Look look) throws ReflectiveOperationException {
		if (getData == null) {
			ClassLoader loader = ZFighterFusionLook.class.getClassLoader();
			Class<?> holder = Class.forName(MCA + "client.render.VillagerStateHolder", false, loader);
			Class<?> data = Class.forName(MCA + "client.render.VillagerRenderData", false, loader);
			Class<?> visuals = Class.forName(MCA + "client.render.VillagerVisuals", false, loader);
			Class<?> like = Class.forName(MCA + "entity.VillagerLike", false, loader);
			likeClass = like;
			Class<?> model = Class.forName(MCA + "entity.VillagerLike$PlayerModel", false, loader);
			setData = holder.getMethod("mca$setVillagerRenderData", data);
			dataModel = data.getMethod("playerModel");
			dataVisuals = data.getMethod("visuals");
			newData = data.getConstructor(model, visuals);
			playerData = Class.forName(MCA + "MCAClient", false, loader).getMethod("getPlayerData", UUID.class);
			capture = visuals.getMethod("capture", like);
			eyeColor = visuals.getMethod("staticEyeColor", boolean.class);
			libraryTexture = Class.forName(MCA + "client.gui.immersive_library.SkinCache", false, loader).getMethod("getTextureIdentifier", int.class);
			components = visuals.getRecordComponents();
			Class<?>[] types = new Class<?>[components.length];
			for (int i = 0; i < components.length; i++) {
				types[i] = components[i].getType();
			}
			newVisuals = visuals.getDeclaredConstructor(types);
			getData = holder.getMethod("mca$getVillagerRenderData");
		}
		Object data = getData.invoke(state);
		Object own = data == null ? null : dataVisuals.invoke(data);
		if (own == null) {
			return;
		}
		UUID innerId = inner((Player)entity, look);
		Object otherLike = ((Optional<?>)playerData.invoke(null, innerId)).orElse(null);
		if (otherLike == null) {
			// (0.17.51) a master's fusion: the one inside is a villager of MCA's in the world, not a player
			otherLike = villager(innerId);
		}
		if (otherLike == null) {
			return;
		}
		Object other = capture.invoke(null, otherLike);
		boolean firstSteers = entity.getUUID().equals(look.first());
		Object a = firstSteers ? own : other, b = firstSteers ? other : own;
		Object[] values = new Object[components.length];
		for (int i = 0; i < components.length; i++) {
			Method get = components[i].getAccessor();
			values[i] = blend(components[i].getName(), get.invoke(own), get.invoke(a), get.invoke(b), a, b);
		}
		setData.invoke(state, newData.newInstance(dataModel.invoke(data), newVisuals.newInstance(values)));
	}

	/** The MCA villager with this id in the world, if there is one near enough to be known. */
	private static @Nullable Object villager(UUID id) {
		if (Minecraft.getInstance().level == null || likeClass == null) {
			return null;
		}
		for (net.minecraft.world.entity.Entity e : Minecraft.getInstance().level.entitiesForRendering()) {
			if (e.getUUID().equals(id) && likeClass.isInstance(e)) {
				return e;
			}
		}
		return null;
	}

	/** One of MCA's visuals for the fused body, by name: {@code mine} is the body's own (its animation), a and b the two. */
	private static Object blend(String name, Object mine, Object a, Object b, Object visualsA, Object visualsB) throws ReflectiveOperationException {
		return switch (name) {
			// how the body moves now: its own
			case "tickCount", "entityId", "sleeping", "deadOrDying", "infectionProgress", "burned", "baby" -> mine;
			case "breastSize" -> ((Float)a + (Float)b) / 2.0F;
			case "rawHorizontalScaleFactor", "rawVerticalScaleFactor" -> Math.max((Float)a, (Float)b) * SIZE_BONUS;
			// skin, hair and eye colours come out between the two
			case "skinGene", "melaninGene", "hemoglobinGene", "eumelaninGene", "pheomelaninGene", "eyeBrightnessGene" -> ((Float)a + (Float)b) / 2.0F;
			case "albinism" -> (Boolean)a && (Boolean)b;
			case "rainbowHair", "rainbowEyes" -> (Boolean)a || (Boolean)b;
			// one eye each
			case "heterochromia" -> true;
			case "eyeDye" -> eyeColor.invoke(visualsA, false);
			case "eyeLeftDye" -> eyeColor.invoke(visualsB, false);
			case "skinDye", "hairDye" -> dye((Integer)a, (Integer)b);
			// the first's crown and back of the hair, the second's fringe and the rest
			case "hairBangs", "hairFront", "hairExtra" -> blank(b) ? a : b;
			case "clothes" -> clothes((String)a, (String)b);
			// the build, the face, the hair's base: the first's
			default -> a;
		};
	}

	/** MCA's "natural" (no dye) is -1. */
	private static int dye(int a, int b) {
		return a == -1 ? b : b == -1 ? a : ARGB.srgbLerp(0.5F, a, b);
	}

	private static boolean blank(Object value) {
		return !(value instanceof String s) || s.isBlank();
	}

	/**
	 * The two outfits as one: the first's top (and hat), the second's legs. MCA's clothing layer draws any texture in the
	 * {@code immersive_library} namespace without checking the resources, which is where the sewn one is registered.
	 */
	private static String clothes(String a, String b) throws ReflectiveOperationException {
		if (blank(a)) {
			return b;
		}
		if (blank(b) || a.equals(b)) {
			return a;
		}
		String key = "clothes|" + a + "|" + b;
		Identifier done = SEWN.get(key);
		if (done != null) {
			return done.toString();
		}
		NativeImage top = pixels(clothesTexture(a)), bottom = pixels(clothesTexture(b));
		try {
			if (top == null || bottom == null || top.getWidth() != bottom.getWidth() || top.getHeight() != bottom.getHeight() || top.getWidth() != 64
				|| top.getHeight() != 64) {
				return a;
			}
			NativeImage sewn = new NativeImage(64, 64, true);
			sewn.copyFrom(top);
			legs(bottom, sewn, null);
			return register(key, Identifier.fromNamespaceAndPath("immersive_library", "jaymospowers_fusion/" + Integer.toHexString(key.hashCode())), sewn)
				.toString();
		} finally {
			close(top, bottom);
		}
	}

	/** Where MCA keeps an outfit: a library one by its number, or a texture of its own. */
	private static @Nullable Identifier clothesTexture(String clothes) throws ReflectiveOperationException {
		String library = "immersive_library:";
		if (clothes.startsWith(library)) {
			try {
				return (Identifier)libraryTexture.invoke(null, Integer.parseInt(clothes.substring(library.length())));
			} catch (NumberFormatException e) {
				// one of ours, or someone else's in that namespace
			}
		}
		return Identifier.tryParse(clothes);
	}

	// ---------------------------------------------------------------- textures

	private static Identifier register(String key, Identifier id, NativeImage image) {
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, image));
		SEWN.put(key, id);
		return id;
	}

	/** Both legs, base and outer layer, from {@code from}. */
	private static void legs(NativeImage from, NativeImage to, boolean @Nullable [] mark) {
		copy(from, to, mark, 0, 16, 16, 32); // right leg
		copy(from, to, mark, 0, 32, 16, 48); // right leg, outer
		copy(from, to, mark, 16, 48, 32, 64); // left leg
		copy(from, to, mark, 0, 48, 16, 64); // left leg, outer
	}

	/** A region of {@code from} onto {@code to} (same layout), marked in {@code mark} (64 wide) if given. */
	private static void copy(NativeImage from, NativeImage to, boolean @Nullable [] mark, int x0, int y0, int x1, int y1) {
		for (int y = y0; y < y1; y++) {
			for (int x = x0; x < x1; x++) {
				to.setPixel(x, y, from.getPixel(x, y));
				if (mark != null) {
					mark[y * 64 + x] = true;
				}
			}
		}
	}

	private static int opaque(NativeImage image, int x0, int y0, int x1, int y1) {
		int count = 0;
		for (int y = y0; y < y1; y++) {
			for (int x = x0; x < x1; x++) {
				count += ARGB.alpha(image.getPixel(x, y)) > 128 ? 1 : 0;
			}
		}
		return count;
	}

	/** The skin tones in {@code sewn} moved toward the middle of the two skins' own (each keeping its shading). */
	private static void evenTones(NativeImage sewn, boolean[] second, NativeImage a, NativeImage b) {
		int[] toneA = tone(a), toneB = tone(b);
		if (toneA == null || toneB == null) {
			return;
		}
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				int argb = sewn.getPixel(x, y);
				if (!ZFighterVerdantLook.skinTone(argb)) {
					continue;
				}
				int[] own = second[y * 64 + x] ? toneB : toneA;
				int r = shift(ARGB.red(argb), own[0], toneA[0], toneB[0]);
				int g = shift(ARGB.green(argb), own[1], toneA[1], toneB[1]);
				int bl = shift(ARGB.blue(argb), own[2], toneA[2], toneB[2]);
				sewn.setPixel(x, y, ARGB.color(ARGB.alpha(argb), r, g, bl));
			}
		}
	}

	private static int shift(int value, int own, int a, int b) {
		float middle = (a + b) / 2.0F;
		float target = Mth.lerp(TONE_MIX, own, middle);
		return Mth.clamp(Math.round(value * target / Math.max(1, own)), 0, 255);
	}

	/** A skin's mean skin tone (rgb), or null if it shows none. */
	private static int @Nullable [] tone(NativeImage image) {
		long r = 0, g = 0, b = 0;
		int n = 0;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int argb = image.getPixel(x, y);
				if (ZFighterVerdantLook.skinTone(argb)) {
					r += ARGB.red(argb);
					g += ARGB.green(argb);
					b += ARGB.blue(argb);
					n++;
				}
			}
		}
		return n == 0 ? null : new int[] {(int)(r / n), (int)(g / n), (int)(b / n)};
	}

	/** A copy of the texture's pixels (to close after): from the resources, or a downloaded skin's own copy. */
	private static @Nullable NativeImage pixels(@Nullable Identifier texture) {
		if (texture == null) {
			return null;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Optional<Resource> resource = minecraft.getResourceManager().getResource(texture);
		if (resource.isPresent()) {
			try (InputStream in = resource.get().open()) {
				return NativeImage.read(in);
			} catch (Exception e) {
				JaymosPowers.LOGGER.warn("Fusion look: couldn't read {}", texture, e);
				return null;
			}
		}
		AbstractTexture loaded = ((com.jaymospowers.client.mixin.TextureManagerAccessor)minecraft.getTextureManager()).jaymospowers$byPath().get(texture);
		NativeImage pixels = loaded instanceof DynamicTexture dynamic ? dynamic.getPixels() : null;
		if (pixels == null) {
			return null;
		}
		NativeImage copy = new NativeImage(pixels.getWidth(), pixels.getHeight(), true);
		copy.copyFrom(pixels);
		return copy;
	}

	private static void close(@Nullable NativeImage a, @Nullable NativeImage b) {
		if (a != null) {
			a.close();
		}
		if (b != null) {
			b.close();
		}
	}
}
