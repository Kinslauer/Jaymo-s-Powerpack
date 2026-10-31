package com.jaymospowers.client.pets;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * (0.18.115, Motion patch 7) A few hidden name-tag looks, like vanilla's own: no screen text says what they are, only
 * villagers' old tales hint at them (Kingdoms' rarepet hints). Drawn only, so a creature's size for hitting it is its own.
 * <ul>
 * <li><b>Pip</b>: stays a whelp, drawn at {@link #PIP}.</li>
 * <li><b>Goliath</b>: drawn at {@link #GOLIATH}.</li>
 * <li><b>Midas</b>: gilded ({@link #MIDAS}).</li>
 * <li><b>Soot</b>: black as the chimney ({@link #SOOT}).</li>
 * <li><b>Wisp</b>: pale as fog ({@link #WISP}).</li>
 * </ul>
 */
public final class NameLooks {
	/** Sizes drawn. */
	static final float PIP = 0.5F, GOLIATH = 1.6F;
	/** Tints (RGB, multiplied in). */
	static final int MIDAS = 0xFFD54A, SOOT = 0x2A2A30, WISP = 0xC8DCF0;

	enum Look { PIP, GOLIATH, MIDAS, SOOT, WISP }

	static final RenderStateDataKey<Look> KEY = RenderStateDataKey.create(() -> "jaymospowers:name_look");

	private NameLooks() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		state.setData(KEY, look(entity));
	}

	private static Look look(LivingEntity entity) {
		if (entity instanceof Player) {
			return null;
		}
		Component name = entity.getCustomName();
		if (name == null) {
			return null;
		}
		return switch (name.getString().strip().toLowerCase(Locale.ROOT)) {
			case "pip" -> Look.PIP;
			case "goliath" -> Look.GOLIATH;
			case "midas" -> Look.MIDAS;
			case "soot" -> Look.SOOT;
			case "wisp" -> Look.WISP;
			default -> null;
		};
	}

	/** Its size, as the model is scaled. */
	public static void scale(LivingEntityRenderState state, PoseStack pose) {
		Look look = state.getData(KEY);
		if (look == Look.PIP) {
			pose.scale(PIP, PIP, PIP);
		} else if (look == Look.GOLIATH) {
			pose.scale(GOLIATH, GOLIATH, GOLIATH);
		}
	}

	/** Its tint over the model's own. */
	public static int tint(LivingEntityRenderState state, int color) {
		Look look = state.getData(KEY);
		int over = look == Look.MIDAS ? MIDAS : look == Look.SOOT ? SOOT : look == Look.WISP ? WISP : -1;
		if (over == -1) {
			return color;
		}
		int r = ((color >> 16) & 0xFF) * ((over >> 16) & 0xFF) / 255;
		int g = ((color >> 8) & 0xFF) * ((over >> 8) & 0xFF) / 255;
		int b = (color & 0xFF) * (over & 0xFF) / 255;
		return color & 0xFF000000 | r << 16 | g << 8 | b;
	}
}
