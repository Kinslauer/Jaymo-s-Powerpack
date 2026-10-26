package com.jaymospowers.client.power.resonance;

import com.jaymospowers.power.resonance.ResonanceHush;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Hush on every client: the domes are synced on their casters ({@link ResonanceHush#DOME}). A sound inside a dome, or
 * any sound at all for a listener inside one, never starts (music and menu clicks still do). The caster keeps their
 * hearing unless they're Shrieker (corrupted), whose Hush drains their own hearing too: their screen goes muffled. Also
 * tells the core's key handling when the local player's power keys are dead (inside someone else's dome).
 */
public final class ResonanceHushClient {
	/** (0.17.9) Everyone holding a dome just now (players, and gifted villagers' domes too), looked for once a tick. */
	private static final List<Entity> CASTERS = new ArrayList<>();
	private static long castersAt = Long.MIN_VALUE;
	private static @Nullable ClientLevel castersIn;

	private ResonanceHushClient() {
	}

	private static List<Entity> casters(ClientLevel level) {
		long now = level.getGameTime();
		if (now != castersAt || level != castersIn) {
			castersAt = now;
			castersIn = level;
			CASTERS.clear();
			for (Entity e : level.entitiesForRendering()) {
				if (e.hasAttached(ResonanceHush.DOME)) {
					CASTERS.add(e);
				}
			}
		}
		return CASTERS;
	}

	/** Whether the local player stands in someone else's dome (their power keys do nothing). */
	public static boolean silenced(Player self) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return false;
		}
		long now = level.getGameTime();
		for (Entity caster : casters(level)) {
			ResonanceHush.Dome dome = caster.getAttached(ResonanceHush.DOME);
			if (caster != self && dome != null && now < dome.until() && dome.contains(self.position())) {
				return true;
			}
		}
		return false;
	}

	/** 0 when the local player hears normally, 1 inside a silence that takes their hearing, 2 when it's their own (Shrieker). */
	static int deafness() {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		LocalPlayer self = minecraft.player;
		if (level == null || self == null) {
			return 0;
		}
		long now = level.getGameTime();
		Vec3 ear = self.getEyePosition();
		int deaf = 0;
		for (Entity caster : casters(level)) {
			ResonanceHush.Dome dome = caster.getAttached(ResonanceHush.DOME);
			if (dome == null || now >= dome.until()) {
				continue;
			}
			if (caster == self) {
				if (dome.corrupted()) {
					deaf = 2;
				}
			} else if (dome.contains(ear)) {
				deaf = Math.max(deaf, 1);
			}
		}
		return deaf;
	}

	/** Whether {@code sound} is swallowed by a silence (called as it starts). */
	public static boolean muted(SoundInstance sound) {
		SoundSource source = sound.getSource();
		if (source == SoundSource.MUSIC || source == SoundSource.UI) {
			return false;
		}
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		LocalPlayer self = minecraft.player;
		if (level == null || self == null) {
			return false;
		}
		long now = level.getGameTime();
		Vec3 ear = self.getEyePosition();
		Vec3 at = sound.isRelative() ? ear : new Vec3(sound.getX(), sound.getY(), sound.getZ());
		for (Entity caster : casters(level)) {
			ResonanceHush.Dome dome = caster.getAttached(ResonanceHush.DOME);
			if (dome == null || now >= dome.until()) {
				continue;
			}
			if (caster == self) {
				if (dome.corrupted()) {
					return true; // Shrieker's Hush takes their own hearing, everywhere
				}
				continue; // your own silence leaves you your ears
			}
			if (dome.contains(ear) || dome.contains(at)) {
				return true;
			}
		}
		return false;
	}

	/** The silence on screen: a soft grey closing in from the edges (darker, and "muffled", for Shrieker). */
	static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		int deaf = deafness();
		if (deaf == 0) {
			return;
		}
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int edge = Math.max(24, height / 6);
		int color = deaf == 2 ? 0x2A1030 : 0x404850;
		int alpha = deaf == 2 ? 0xB0 : 0x80;
		int solid = alpha << 24 | color;
		int clear = color;
		graphics.fillGradient(0, 0, width, edge, solid, clear);
		graphics.fillGradient(0, height - edge, width, height, clear, solid);
		// the sides: stepped bands, fading inward
		int steps = 8;
		for (int i = 0; i < steps; i++) {
			int a = (int)(alpha * (1.0F - i / (float)steps) * 0.8F);
			int w = Mth.ceil(edge / (float)steps);
			graphics.fill(i * w, 0, i * w + w, height, a << 24 | color);
			graphics.fill(width - i * w - w, 0, width - i * w, height, a << 24 | color);
		}
		if (deaf == 2) {
			graphics.centeredText(Minecraft.getInstance().font, Component.translatable("hud.jaymospowers.resonance.muffled"), width / 2, edge / 2,
				0x90C8A0D8);
		}
	}
}
