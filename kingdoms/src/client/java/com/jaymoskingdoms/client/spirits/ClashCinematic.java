package com.jaymoskingdoms.client.spirits;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Mana;
import com.jaymoskingdoms.spirits.SpiritClash;
import com.jaymoskingdoms.spirits.SpiritClash.Clash;
import com.jaymoskingdoms.spirits.SpiritState;
import com.jaymoskingdoms.spirits.Spirits;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Two keepers' spirits locked arm to arm ({@link SpiritClash}) as a short film, in the way of the handshake's
 * ({@code HandshakeCinematic}): while your {@link SpiritClash#CLASH} is set, black bars slide in (12% top and bottom), the
 * HUD clears but for a tug bar showing the lead in the two spirits' colours, the camera swings side-on to the pair, the
 * movement keys go dead, and the screen shakes harder the further the lead swings. When it ends the screen flashes in the
 * winner's colour. An attuned viewer near a clash gets the bars only.
 */
public final class ClashCinematic {
	/** Each bar's height as a share of the screen's; how much of the way they slide a tick. */
	static final float BAR_SHARE = 0.12F, BAR_EASE = 0.25F;
	/** Viewers this near (blocks) to a clash get the bars. */
	static final double VIEW_RANGE = 24.0;
	/** The camera: how far out to the side (blocks), how high over the pair's feet, how far it sways (degrees). */
	static final double CAM_OUT = 3.4, CAM_UP = 1.5, CAM_SWAY = 14.0;
	/** The look-at height over the pair's feet (blocks). */
	static final double LOOK_UP = 1.3;
	/** The camera's shake, in blocks, at no lead and at the full lead. */
	static final double SHAKE_LOW = 0.01, SHAKE_HIGH = 0.09;
	/** The flash at the end: its length (ticks) and its strongest alpha. */
	static final int FLASH_TICKS = 14;
	static final float FLASH_ALPHA = 0.7F;
	/** The tug bar: its width and height, and its lift from the screen's bottom edge. */
	static final int TUG_WIDTH = 220, TUG_HEIGHT = 10, TUG_LIFT = 30;
	/** Kept this far off a wall the camera would otherwise sink into (blocks). */
	static final double WALL_MARGIN = 0.3;
	/** The vanilla HUD hidden while it runs. */
	private static final Identifier[] HIDDEN = {VanillaHudElements.CROSSHAIR, VanillaHudElements.HOTBAR, VanillaHudElements.ARMOR_BAR,
		VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH,
		VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.MOB_EFFECTS};

	/** Whether the film runs for you (you are a side of it); the other side's id. */
	private static boolean running;
	private static int other = -1;
	private static @Nullable CameraType viewBefore;
	private static float side = 1.0F, bars, barsO;
	private static long since;
	private static Vec3 otherAt = Vec3.ZERO;
	/** Your lead over them, -100 to 100. */
	private static int lead;
	/** The finished clash's flash: its end time, and the winner's colour. */
	private static long flashAt = -1L;
	private static int flashColor = 0xFFFFFF;

	private ClashCinematic() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (!minecraft.isPaused()) {
				tick(minecraft);
			}
		});
		for (Identifier id : HIDDEN) {
			HudElementRegistry.replaceElement(id, old -> (graphics, delta) -> {
				if (!running) {
					old.extractRenderState(graphics, delta);
				}
			});
		}
		HudElementRegistry.addLast(JaymosKingdoms.id("clash_cinematic"), (HudElement)ClashCinematic::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			stop(client);
			bars = 0.0F;
			barsO = 0.0F;
			flashAt = -1L;
		});
	}

	/** Whether the movement keys are dead: you are locked in a clash that is still on. */
	public static boolean locked() {
		return running;
	}

	private static boolean live(Entity entity) {
		Clash clash = entity.getAttached(SpiritClash.CLASH);
		return clash != null && !clash.over();
	}

	private static void tick(Minecraft minecraft) {
		barsO = bars;
		LocalPlayer self = minecraft.player;
		Clash mine = self == null || minecraft.level == null ? null : self.getAttached(SpiritClash.CLASH);
		Clash now = mine != null && !mine.over() ? mine : null;
		boolean viewer = false;
		if (self != null && now != null) {
			if (!running) {
				running = true;
				viewBefore = minecraft.options.getCameraType();
				side = self.getRandom().nextBoolean() ? 1.0F : -1.0F;
			}
			boolean first = now.a() == self.getId();
			other = first ? now.b() : now.a();
			since = now.start();
			lead = first ? now.lead() : -now.lead();
		} else {
			if (running) {
				stop(minecraft);
			}
			if (self != null && mine != null && mine.over() && mine.end() != flashAt) {
				// (the flash, in the winner's colour: white for a draw)
				flashAt = mine.end();
				Entity won = mine.winner() == 0 ? null : self.level().getEntity(mine.winner() == 1 ? mine.a() : mine.b());
				flashColor = colourOf(won);
			}
			if (self != null && minecraft.level != null && Mana.attuned(self)) {
				for (Player p : minecraft.level.players()) {
					if (p != self && live(p) && p.distanceToSqr(self) < VIEW_RANGE * VIEW_RANGE) {
						viewer = true;
						break;
					}
				}
			}
		}
		if (running && self != null) {
			if (minecraft.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
				minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			}
			Entity them = self.level().getEntity(other);
			if (them != null) {
				otherAt = them.position();
				Vec3 to = them.getEyePosition().subtract(self.getEyePosition());
				float yaw = (float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
				self.setYRot(yaw);
				self.setYHeadRot(yaw);
				self.setXRot(0.0F);
			}
		}
		bars += (((running || viewer) ? 1.0F : 0.0F) - bars) * BAR_EASE;
		if (!running && !viewer && bars < 0.01F) {
			bars = 0.0F;
		}
	}

	private static void stop(Minecraft minecraft) {
		if (running && viewBefore != null) {
			minecraft.options.setCameraType(viewBefore);
		}
		running = false;
		viewBefore = null;
		other = -1;
	}

	/** The colour (RGB) of the spirit {@code who} keeps; white if none. */
	private static int colourOf(@Nullable Entity who) {
		SpiritState state = who == null ? null : Spirits.state(who);
		return state == null ? 0xFFFFFF : Spirits.of(state.kind()).color() & 0xFFFFFF;
	}

	/** Where the camera is and which way it looks this frame ({x, y, z, yaw, pitch}), or null when the film isn't running. */
	public static double @Nullable [] view(Minecraft minecraft, float partialTicks) {
		LocalPlayer self = minecraft.player;
		if (!running || self == null || minecraft.getCameraEntity() != self || minecraft.level == null) {
			return null;
		}
		Entity them = self.level().getEntity(other);
		Vec3 me = self.getPosition(partialTicks);
		Vec3 you = them != null ? them.getPosition(partialTicks) : otherAt;
		Vec3 flat = new Vec3(you.x - me.x, 0.0, you.z - me.z);
		Vec3 axis = flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, self.getYRot()) : flat.normalize();
		Vec3 across = new Vec3(axis.z, 0.0, -axis.x).scale(side);
		Vec3 mid = me.add(you).scale(0.5);
		float t = minecraft.level.getGameTime() - since + partialTicks;
		// a slow swing round the pair, drawing in as the lead goes either way
		double angle = Math.toRadians(Math.sin(t * 0.06) * CAM_SWAY);
		Vec3 out = across.scale(Math.cos(angle)).add(axis.scale(Math.sin(angle)));
		double pull = Math.abs(lead) / 100.0;
		Vec3 look = mid.add(0.0, LOOK_UP, 0.0);
		Vec3 pos = mid.add(out.scale(CAM_OUT - 0.6 * pull)).add(0.0, CAM_UP, 0.0);
		// the shake: harder as the lead swings
		double shake = Mth.lerp(pull, SHAKE_LOW, SHAKE_HIGH);
		pos = pos.add(Math.sin(t * 2.7) * shake, Math.sin(t * 3.9 + 1.0) * shake, Math.cos(t * 3.1) * shake);
		pos = clipped(self, look, pos);
		Vec3 dir = look.subtract(pos);
		dir = dir.lengthSqr() < 1.0E-6 ? axis : dir.normalize();
		double yaw = Math.toDegrees(Math.atan2(-dir.x, dir.z)) + Math.sin(t * 4.3) * shake * 20.0;
		double pitch = Math.toDegrees(-Math.asin(Mth.clamp(dir.y, -1.0, 1.0))) + Math.sin(t * 5.1) * shake * 20.0;
		return new double[] {pos.x, pos.y, pos.z, yaw, pitch};
	}

	/** {@code want}, or short of it where a wall stands between it and what it's looking at. */
	private static Vec3 clipped(LocalPlayer self, Vec3 from, Vec3 want) {
		HitResult hit = self.level().clip(new ClipContext(from, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, self));
		if (hit.getType() == HitResult.Type.MISS) {
			return want;
		}
		Vec3 back = hit.getLocation().subtract(from);
		double length = back.length();
		return length > WALL_MARGIN ? from.add(back.scale((length - WALL_MARGIN) / length)) : from;
	}

	/** The bars, the tug bar and the closing flash. */
	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer self = minecraft.player;
		int width = graphics.guiWidth(), height = graphics.guiHeight();
		float partial = delta.getGameTimeDeltaPartialTick(false);
		float shown = Mth.lerp(partial, barsO, bars);
		if (shown > 0.0F) {
			int bar = Math.round(height * BAR_SHARE * shown);
			graphics.fill(0, 0, width, bar, 0xFF000000);
			graphics.fill(0, height - bar, width, height, 0xFF000000);
		}
		if (self == null || minecraft.level == null) {
			return;
		}
		if (running) {
			tug(graphics, minecraft, self, width, height);
		}
		if (flashAt >= 0L) {
			float age = minecraft.level.getGameTime() + partial - flashAt;
			if (age >= 0.0F && age < FLASH_TICKS) {
				float alpha = FLASH_ALPHA * (1.0F - age / FLASH_TICKS);
				graphics.fill(0, 0, width, height, ARGB.colorFromFloat(alpha, ARGB.red(flashColor) / 255.0F,
					ARGB.green(flashColor) / 255.0F, ARGB.blue(flashColor) / 255.0F));
			}
		}
	}

	/** The tug bar: your side's colour from the left, theirs from the right, the knot where the lead stands. */
	private static void tug(GuiGraphicsExtractor graphics, Minecraft minecraft, LocalPlayer self, int width, int height) {
		Entity them = self.level().getEntity(other);
		int mine = ARGB.opaque(colourOf(self)), theirs = ARGB.opaque(colourOf(them));
		int x0 = (width - TUG_WIDTH) / 2, y0 = height - TUG_LIFT - TUG_HEIGHT;
		int knot = x0 + Math.round(TUG_WIDTH * (0.5F + lead / 200.0F));
		graphics.fill(x0 - 2, y0 - 2, x0 + TUG_WIDTH + 2, y0 + TUG_HEIGHT + 2, 0xFF000000);
		graphics.fill(x0, y0, knot, y0 + TUG_HEIGHT, mine);
		graphics.fill(knot, y0, x0 + TUG_WIDTH, y0 + TUG_HEIGHT, theirs);
		graphics.fill(knot - 2, y0 - 4, knot + 2, y0 + TUG_HEIGHT + 4, 0xFFFFFFFF);
		Component hint = Component.translatable("message.jaymoskingdoms.spirit.clash_tap");
		Font font = minecraft.font;
		graphics.text(font, hint, (width - font.width(hint)) / 2, y0 - 16, 0xFFFFFFFF, true);
	}
}
