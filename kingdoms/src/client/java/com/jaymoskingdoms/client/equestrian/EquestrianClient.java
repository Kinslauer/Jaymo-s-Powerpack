package com.jaymoskingdoms.client.equestrian;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.mixin.equestrian.AbstractContainerScreenAccessor;
import com.jaymoskingdoms.equestrian.Breed;
import com.jaymoskingdoms.equestrian.Equestrian;
import com.jaymoskingdoms.equestrian.Equine;
import com.jaymoskingdoms.equestrian.Gait;
import com.jaymoskingdoms.equestrian.Stride;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.33) Riding, client side: the scroll wheel shifts gait, sprint bursts at the gallop, and the HUD (bottom right,
 * right of the hotbar, above the powers' chips, in Wind's style) shows the breed, the gait and a stamina bar with the
 * mark where a blown horse gets its wind back. A tamed horse's inventory screen shows its breed and stats.
 */
public final class EquestrianClient {
	private static final int BAR_WIDTH = 64;
	/** The powers' chip row is this tall (its readouts stack above it); the HUD sits over it. */
	private static final int CHIP = 20;
	private static final int[] GAIT_COLORS = {0xA0A0A0, 0xBFF4EA, 0x7FD4FF, 0xFFD45A, 0xFF9A4A};
	private static final int STAMINA_COLOR = 0x8FE07A, LOW_COLOR = 0xFFC04A, BLOWN_COLOR = 0xE04040, SPRINT_COLOR = 0xFFFFFF;
	private static final String[] STATS = {"speed", "stamina", "jump", "courage", "temper"};
	private static final int[] STAT_COLORS = {0x7FD4FF, 0x8FE07A, 0xBFF4EA, 0xFFD45A, 0xE06A4A};

	/** The horse this client rides (entity id), the gait its rider picked, and what the server was last told. */
	private static int riding = -1;
	private static Gait gait = Gait.TROT;
	private static boolean sprint;
	private static @Nullable Gait sentGait;
	private static boolean sentSprint;

	private EquestrianClient() {
	}

	public static void register() {
		EntityRenderers.register(Equestrian.WAR_ELEPHANT, WarElephantRenderer::new);
		Equestrian.localRider = new Equestrian.LocalRider() {
			@Override
			public boolean controls(Player controller) {
				return controller == Minecraft.getInstance().player;
			}

			@Override
			public Gait gait(AbstractHorse horse) {
				return horse.getId() == riding ? gait : Equestrian.stride(horse).gaitOf();
			}

			@Override
			public boolean sprint(AbstractHorse horse) {
				return horse.getId() == riding && sprint;
			}
		};
		ClientTickEvents.END_CLIENT_TICK.register(EquestrianClient::tick);
		ClientPlayNetworking.registerGlobalReceiver(Equestrian.Shied.TYPE, (payload, context) -> {
			if (payload.horse() == riding) {
				gait = Gait.WALK;
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, JaymosKingdoms.id("equestrian"), EquestrianClient::hud);
	}

	/** The horse {@code player} rides and steers, if it's one of the upgraded ones. */
	static @Nullable AbstractHorse ridden(@Nullable Player player) {
		return player != null && player.getVehicle() instanceof AbstractHorse horse && Equestrian.covered(horse)
			&& horse.getControllingPassenger() == player && horse.getAttached(Equestrian.EQUINE) != null ? horse : null;
	}

	private static void tick(Minecraft minecraft) {
		AbstractHorse horse = ridden(minecraft.player);
		if (horse == null) {
			riding = -1;
			sprint = false;
			return;
		}
		if (horse.getId() != riding) {
			riding = horse.getId();
			gait = Equestrian.stride(horse).gaitOf();
			sentGait = null;
		}
		Equine e = horse.getAttached(Equestrian.EQUINE);
		Stride s = Equestrian.stride(horse);
		// a blown horse drops to a trot of its own accord
		gait = Equestrian.allowed(e, s, gait);
		sprint = minecraft.options.keySprint.isDown() && gait == Gait.GALLOP && !s.blown() && minecraft.gui.screen() == null;
		if (gait != sentGait || sprint != sentSprint) {
			sentGait = gait;
			sentSprint = sprint;
			ClientPlayNetworking.send(new Equestrian.SetGait(gait.ordinal(), sprint));
		}
	}

	/** (From {@code MouseHandlerMixin}) the wheel turned: in the saddle it shifts gait (up: faster). True if it did. */
	public static boolean scroll(int wheel) {
		AbstractHorse horse = ridden(Minecraft.getInstance().player);
		if (horse == null || wheel == 0) {
			return false;
		}
		Equine e = horse.getAttached(Equestrian.EQUINE);
		gait = Equestrian.allowed(e, Equestrian.stride(horse), Gait.byId(gait.ordinal() + Integer.signum(wheel)));
		return true;
	}

	// ---- HUD ---------------------------------------------------------------------------------------------------------

	private static void hud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		AbstractHorse horse = ridden(player);
		if (horse == null) {
			return;
		}
		Equine e = horse.getAttached(Equestrian.EQUINE);
		Stride s = Equestrian.stride(horse);
		Font font = minecraft.font;
		float time = player.tickCount + delta.getGameTimeDeltaPartialTick(false);
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int cx = width / 2;
		int x0 = cx + 91 + 30;
		int y = height - CHIP - 1 - 6;
		if (x0 + BAR_WIDTH > width - 2) {
			// a narrow screen: above the right half of the hotbar instead
			x0 = cx + 91 - BAR_WIDTH;
			y = height - 22 - 30 - CHIP - 6;
		}
		double capacity = e.capacity();
		float share = (float)Mth.clamp((s.stamina() < 0.0F ? capacity : s.stamina()) / capacity, 0.0, 1.0);
		int color = s.blown() ? pulse(BLOWN_COLOR, 0xFFFFFF, time * 0.5F) : sprint ? pulse(STAMINA_COLOR, SPRINT_COLOR, time * 0.8F)
			: share < Equestrian.WIND_BACK ? LOW_COLOR : STAMINA_COLOR;
		graphics.fill(x0 - 1, y - 1, x0 + BAR_WIDTH + 1, y + 3, 0x90000000);
		graphics.fill(x0, y, x0 + Mth.ceil(BAR_WIDTH * share), y + 2, 0xFF000000 | color);
		int mark = x0 + (int)Math.round(BAR_WIDTH * Equestrian.WIND_BACK);
		graphics.fill(mark, y - 1, mark + 1, y + 3, 0xFF606060);
		Gait shown = Equestrian.allowed(e, s, gait);
		MutableComponent label = Component.translatable(e.breed().key()).withColor(0xB0B0B0)
			.append(Component.literal("  "));
		if (s.blown()) {
			label.append(Component.translatable("hud.jaymoskingdoms.blown").withColor(BLOWN_COLOR));
		} else if (sprint) {
			String key = e.breed() == Breed.WAR_ELEPHANT ? "hud.jaymoskingdoms.charge" : "hud.jaymoskingdoms.sprint";
			label.append(Component.translatable(key).withColor(pulse(GAIT_COLORS[4], 0xFFFFFF, time * 0.8F)));
		} else {
			label.append(Component.translatable(shown.key()).withColor(GAIT_COLORS[shown.ordinal()]));
		}
		graphics.text(font, label, x0 + BAR_WIDTH - font.width(label), y - 11, 0xE0FFFFFF);
		// the gait ladder: five pips, lit to the current one
		for (Gait g : Gait.values()) {
			int px = x0 + g.ordinal() * 4;
			boolean reach = g.ordinal() <= e.breed().topGait.ordinal();
			int pip = g.ordinal() <= shown.ordinal() ? 0xFF000000 | GAIT_COLORS[shown.ordinal()] : reach ? 0xFF404040 : 0x60202020;
			graphics.fill(px, y - 4, px + 3, y - 2, pip);
		}
	}

	// ---- the inventory screen -----------------------------------------------------------------------------------------

	public static int imageWidth(AbstractContainerScreen<?> screen) {
		return ((AbstractContainerScreenAccessor)screen).jaymoskingdoms$imageWidth();
	}

	public static int imageHeight(AbstractContainerScreen<?> screen) {
		return ((AbstractContainerScreenAccessor)screen).jaymoskingdoms$imageHeight();
	}

	/** (From {@code AbstractMountInventoryScreenMixin}) a tamed horse's breed and five stats as bars, in the 90x54 box at {@code x}, {@code y}. */
	public static void stats(GuiGraphicsExtractor graphics, LivingEntity mount, int x, int y) {
		if (!(mount instanceof AbstractHorse horse) || !horse.isTamed()) {
			return;
		}
		Equine e = horse.getAttached(Equestrian.EQUINE);
		if (e == null) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		graphics.text(font, Component.translatable(e.breed().key()), x + 2, y + 1, 0xFF404040, false);
		float[] values = {e.speed(), e.stamina(), e.jump(), e.courage(), e.temper()};
		for (int i = 0; i < STATS.length; i++) {
			int row = y + 11 + i * 9;
			graphics.text(font, Component.translatable("stat.jaymoskingdoms.horse." + STATS[i]), x + 2, row, 0xFF404040, false);
			int bx = x + 46;
			int bw = 40;
			graphics.fill(bx, row + 2, bx + bw, row + 6, 0xFF8B8B8B);
			graphics.fill(bx, row + 2, bx + Mth.ceil(bw * values[i]), row + 6, 0xFF000000 | STAT_COLORS[i]);
		}
	}

	/** Blend between two 0xRRGGBB colours on a sine wave. */
	private static int pulse(int a, int b, float phase) {
		float t = 0.5F + 0.5F * Mth.sin(phase);
		int r = (int)Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF);
		int g = (int)Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF);
		int bl = (int)Mth.lerp(t, a & 0xFF, b & 0xFF);
		return r << 16 | g << 8 | bl;
	}
}
