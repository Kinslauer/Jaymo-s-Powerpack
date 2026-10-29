package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ApeRage;
import com.jaymospowers.power.zfighter.ZFighterLineage;
import com.jaymospowers.power.zfighter.ZFighterPower;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.190) Andy: Primal Super's hair "keeps your villager's selected hair color", and Super Primal Super's red "should
 * recolor the user's default hair because that still shows". Both read MCA's villager visuals on the render state (by name,
 * as {@link ZFighterFusionLook} does, since Powers doesn't compile against MCA):
 * <ul>
 * <li>Primal Super: the Super 2 spikes ({@link ZFighterHair}) take the colour MCA draws his own hair in (its dye, or the
 * colour its genes give);</li>
 * <li>Super Primal Super: MCA's own hair layer is dyed the form's red, so it no longer shows through in his old colour.</li>
 * </ul>
 * Runs after {@link ZFighterFusionLook}, so a fused body's blended hair is the one read and dyed.
 */
public final class ZFighterPrimalHair {
	/** Primal Super's spikes when his villager's hair can't be read (a plain skin, no MCA): a dark brown. */
	static final int FALLBACK = 0x3A2A1E;
	/** MCA's "undyed" hair dye. */
	private static final int UNDYED = 0xFF000000;
	/** MCA's albino hair: the genes are scaled down this far. */
	private static final float ALBINO = 0.1F;

	private static final String MCA = "net.conczin.mca.";
	private static final boolean MCA_LOADED = FabricLoader.getInstance().isModLoaded("mca");
	private static boolean mcaBroken;
	private static @Nullable Method getData, setData, dataModel, dataVisuals, hairDye, albinism, eumelanin, pheomelanin, paletteColor;
	private static @Nullable Object hairPalette;
	private static @Nullable Constructor<?> newData, newVisuals;
	private static RecordComponent @Nullable [] components;

	private ZFighterPrimalHair() {
	}

	/** From the avatar renderer's extractRenderState, after {@link ZFighterHair} and {@link ZFighterFusionLook}. */
	static void extract(Avatar entity, AvatarRenderState state) {
		if (!(entity instanceof Player player) || ZFighterLineage.ape(player)) {
			return;
		}
		Integer form = player.getAttached(ZFighterPower.FORM);
		if (form == null || form != ApeRage.PRIMAL_SUPER && form != ApeRage.SUPER_4) {
			return;
		}
		ZFighterHair.Hair hair = state.getData(ZFighterHair.KEY);
		Object data = null;
		if (MCA_LOADED && !mcaBroken) {
			try {
				data = data(state);
				if (data != null && form == ApeRage.SUPER_4) {
					dye(state, data, hair != null ? hair.color() : ZFighterLineage.color(ZFighterLineage.WARRIOR, ApeRage.SUPER_4));
				}
			} catch (ReflectiveOperationException | RuntimeException e) {
				mcaBroken = true;
				data = null;
				JaymosPowers.LOGGER.warn("Couldn't read MCA's hair for the primal forms; Primal Super's hair stays dark brown", e);
			}
		}
		if (form == ApeRage.PRIMAL_SUPER && hair != null) {
			int color = FALLBACK;
			if (data != null) {
				try {
					color = color(dataVisuals.invoke(data));
				} catch (ReflectiveOperationException | RuntimeException e) {
					mcaBroken = true;
					JaymosPowers.LOGGER.warn("Couldn't read MCA's hair colour for Primal Super", e);
				}
			}
			state.setData(ZFighterHair.KEY, new ZFighterHair.Hair(hair.form(), color & 0xFFFFFF, hair.style()));
		}
	}

	/** MCA's render data on the state (null on a body MCA doesn't draw). */
	private static @Nullable Object data(AvatarRenderState state) throws ReflectiveOperationException {
		if (getData == null) {
			ClassLoader loader = ZFighterPrimalHair.class.getClassLoader();
			Class<?> holder = Class.forName(MCA + "client.render.VillagerStateHolder", false, loader);
			Class<?> data = Class.forName(MCA + "client.render.VillagerRenderData", false, loader);
			Class<?> visuals = Class.forName(MCA + "client.render.VillagerVisuals", false, loader);
			Class<?> model = Class.forName(MCA + "entity.VillagerLike$PlayerModel", false, loader);
			Class<?> palette = Class.forName(MCA + "client.resources.ColorPalette", false, loader);
			setData = holder.getMethod("mca$setVillagerRenderData", data);
			dataModel = data.getMethod("playerModel");
			dataVisuals = data.getMethod("visuals");
			newData = data.getConstructor(model, visuals);
			hairDye = visuals.getMethod("hairDye");
			albinism = visuals.getMethod("albinism");
			eumelanin = visuals.getMethod("eumelaninGene");
			pheomelanin = visuals.getMethod("pheomelaninGene");
			hairPalette = palette.getField("HAIR").get(null);
			paletteColor = palette.getMethod("getColor", float.class, float.class, float.class);
			components = visuals.getRecordComponents();
			Class<?>[] types = new Class<?>[components.length];
			for (int i = 0; i < components.length; i++) {
				types[i] = components[i].getType();
			}
			newVisuals = visuals.getDeclaredConstructor(types);
			getData = holder.getMethod("mca$getVillagerRenderData");
		}
		Object data = getData.invoke(state);
		return data == null || dataVisuals.invoke(data) == null ? null : data;
	}

	/** The colour MCA draws this hair in (as its hair layer does, leaving out the rainbow). */
	private static int color(Object visuals) throws ReflectiveOperationException {
		int dye = (Integer)hairDye.invoke(visuals);
		if (dye != UNDYED) {
			return dye;
		}
		float scale = (Boolean)albinism.invoke(visuals) ? ALBINO : 1.0F;
		return (Integer)paletteColor.invoke(hairPalette, (Float)eumelanin.invoke(visuals) * scale, (Float)pheomelanin.invoke(visuals) * scale, 0.0F);
	}

	/** Swaps MCA's visuals on the state for the same with the hair dyed {@code rgb} (and no rainbow). */
	private static void dye(AvatarRenderState state, Object data, int rgb) throws ReflectiveOperationException {
		Object visuals = dataVisuals.invoke(data);
		Object[] values = new Object[components.length];
		for (int i = 0; i < components.length; i++) {
			String name = components[i].getName();
			values[i] = name.equals("hairDye") ? (Object)(0xFF000000 | rgb) : name.equals("rainbowHair") ? (Object)Boolean.FALSE
				: components[i].getAccessor().invoke(visuals);
		}
		setData.invoke(state, newData.newInstance(dataModel.invoke(data), newVisuals.newInstance(values)));
	}
}
