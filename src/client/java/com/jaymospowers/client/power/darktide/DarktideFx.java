package com.jaymospowers.client.power.darktide;

import com.jaymospowers.power.darktide.DarktideMove;
import com.jaymospowers.power.darktide.DarktideNet;
import com.jaymospowers.power.darktide.DarktideState;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Darktide's client-only effects:
 * <ul>
 * <li>the riposte's <b>freeze frame</b>: both bodies' Darktide animations hold for {@link #FREEZE_TICKS} (only the drawn
 * poses; the server never pauses, so it's fair on the SMP), and a white <b>flash</b> on the screens of the two involved;</li>
 * <li>a Riptide flurry's hit flashes, a parry's glint;</li>
 * <li>Gedan's field: a faint blue, watery, ethereal disc of particles spreading to 10 blocks around anyone in the stance,
 * and a faint blue tint on the screen of anyone standing inside one.</li>
 * </ul>
 */
public final class DarktideFx {
	public static final int FREEZE_TICKS = 3;
	static final int FLASH_TICKS = 6, HIT_FLASH_TICKS = 2, PARRY_FLASH_TICKS = 3;
	static final int FIELD_COLOR = 0x5FA8E8;

	/** entity id → the game time its freeze began. */
	private static final Map<Integer, Long> FROZEN = new HashMap<>();
	private static int flash;
	private static int flashLength = 1;
	private static int flashColor = 0xFFFFFF;
	private static float fieldTint;

	private DarktideFx() {
	}

	static void receive(DarktideNet.Fx fx) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer self = minecraft.player;
		if (self == null || minecraft.level == null) {
			return;
		}
		boolean involved = fx.entity() == self.getId() || fx.target() == self.getId();
		long now = minecraft.level.getGameTime();
		switch (fx.kind()) {
			case DarktideNet.Fx.RIPOSTE -> {
				FROZEN.put(fx.entity(), now);
				if (fx.target() >= 0) {
					FROZEN.put(fx.target(), now);
				}
				if (involved) {
					flash(FLASH_TICKS, 0xFFFFFF);
				}
			}
			case DarktideNet.Fx.HIT -> {
				if (involved) {
					flash(HIT_FLASH_TICKS, 0xCFE8FF);
				}
			}
			default -> {
				if (fx.entity() == self.getId()) {
					flash(PARRY_FLASH_TICKS, 0xBFE6FF);
				}
			}
		}
	}

	private static void flash(int ticks, int color) {
		flash = ticks;
		flashLength = ticks;
		flashColor = color;
	}

	/**
	 * The time a Darktide animation on {@code entity} is drawn at: the game time, except that a riposte's freeze holds it
	 * still for {@link #FREEZE_TICKS} and the rest of the move then plays on from there.
	 */
	static float animTime(Entity entity, float partialTicks) {
		float now = entity.level().getGameTime() + partialTicks;
		Long frozen = FROZEN.get(entity.getId());
		if (frozen == null || now < frozen) {
			return now;
		}
		if (now < frozen + FREEZE_TICKS) {
			return frozen;
		}
		return now - FREEZE_TICKS;
	}

	static void tick(Minecraft minecraft) {
		if (flash > 0) {
			flash--;
		}
		ClientLevel level = minecraft.level;
		LocalPlayer self = minecraft.player;
		if (level == null || self == null || minecraft.isPaused()) {
			fieldTint = Math.max(0.0F, fieldTint - 0.1F);
			return;
		}
		long now = level.getGameTime();
		for (Iterator<Map.Entry<Integer, Long>> it = FROZEN.entrySet().iterator(); it.hasNext(); ) {
			if (now - it.next().getValue() > 60) {
				it.remove();
			}
		}
		boolean inField = false;
		RandomSource random = level.getRandom();
		for (Player player : level.players()) {
			DarktideState.Move move = DarktideMove.current(player);
			if (move == null || move.kind() != DarktideMove.STANCE || player.distanceToSqr(self) > 64.0 * 64.0) {
				continue;
			}
			double r = 10.0 * Math.min(1.0, (now - move.start()) / 100.0);
			Vec3 c = player.position();
			// the rim, and a few motes drifting inside: a disc of still, dark-blue water
			int rim = Math.max(4, (int) (r * 3.0));
			for (int i = 0; i < rim; i++) {
				double a = random.nextDouble() * Math.PI * 2.0;
				level.addParticle(new DustParticleOptions(FIELD_COLOR, 0.9F), c.x + Math.cos(a) * r, c.y + 0.08, c.z + Math.sin(a) * r, 0.0, 0.01, 0.0);
			}
			for (int i = 0; i < 5; i++) {
				double a = random.nextDouble() * Math.PI * 2.0;
				double d = Math.sqrt(random.nextDouble()) * r;
				level.addParticle(i % 2 == 0 ? ParticleTypes.BUBBLE_POP : ParticleTypes.DRIPPING_WATER, c.x + Math.cos(a) * d, c.y + 0.1 + random.nextDouble() * 0.6,
					c.z + Math.sin(a) * d, 0.0, 0.02, 0.0);
			}
			if (self.distanceTo(player) <= r) {
				inField = true;
			}
		}
		fieldTint = Mth.clamp(fieldTint + (inField ? 0.05F : -0.05F), 0.0F, 1.0F);
	}

	/** The flash and the field's tint, over everything (any player, any power: a riposte's victim sees it too). */
	static void renderOverlay(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		if (fieldTint > 0.0F) {
			int alpha = (int) (fieldTint * 0x28);
			graphics.fill(0, 0, width, height, alpha << 24 | FIELD_COLOR);
		}
		if (flash > 0) {
			float t = (flash - delta.getGameTimeDeltaPartialTick(false)) / flashLength;
			int alpha = (int) (Mth.clamp(t, 0.0F, 1.0F) * 0xB0);
			graphics.fill(0, 0, width, height, alpha << 24 | flashColor);
		}
	}
}
