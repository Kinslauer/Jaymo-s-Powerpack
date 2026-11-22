package com.jaymoskingdoms.client.disasters;

import com.jaymoskingdoms.Soundscape;
import com.jaymoskingdoms.Soundscape.Layer;
import com.jaymoskingdoms.sailing.WindPush;
import com.jaymoskingdoms.sailing.Winds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The wind you hear: a low rush that follows its strength, a thin whistle in a hard wind, quiet in shelter and indoors.
 * A {@link Soundscape} layer, kept low under the ambient mod's own weather.
 */
public final class WindSounds {
	/** The rush starts at this wind and is full at this one. */
	static final float FROM = 0.62F, FULL = 1.5F;
	/** What shelter (a wall upwind) and being shut in leave of the sound. */
	static final float SHELTERED = 0.4F, ENCLOSED = 0.2F;
	/** The whistle comes in from this wind, and how often it joins the rush. */
	static final float WHISTLE_FROM = 1.0F, WHISTLE_CHANCE = 0.5F;
	/** Gaps (ticks) between breaths of it. */
	static final int MIN = 50, MAX = 110;
	static final Layer RUSH = Layer.of(0.25F, 0.4F, 0.45F, 0.6F, SoundEvents.WOOL_STEP);
	static final Layer WHISTLE = Layer.of(0.1F, 0.18F, 1.7F, 1.95F, SoundEvents.FIRE_EXTINGUISH).chance(WHISTLE_CHANCE);

	private WindSounds() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(WindSounds::tick);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || minecraft.isPaused() || minecraft.level.dimension() != Level.OVERWORLD) {
			return;
		}
		Level level = minecraft.level;
		float strength = Winds.clientStrength(level, player.getX(), player.getZ());
		float v = Mth.clamp((strength - FROM) / (FULL - FROM), 0.0F, 1.0F);
		if (v <= 0.05F || !Soundscape.due(player, "wind", MIN, MAX)) {
			return;
		}
		Vec3 head = player.getEyePosition();
		boolean enclosed = Soundscape.enclosed(level, head);
		if (enclosed) {
			v *= ENCLOSED;
		} else if (WindPush.sheltered(player, player.getEyeHeight())) {
			v *= SHELTERED;
		}
		if (strength >= WHISTLE_FROM && !enclosed) {
			Soundscape.playLocal(level, head.x, head.y, head.z, SoundSource.AMBIENT, RUSH.louder(v), WHISTLE.louder(v));
		} else {
			Soundscape.playLocal(level, head.x, head.y, head.z, SoundSource.AMBIENT, RUSH.louder(v));
		}
	}
}
