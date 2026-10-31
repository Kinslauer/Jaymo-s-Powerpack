package com.jaymoskingdoms.client.equestrian;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.mixin.equestrian.AbstractContainerScreenAccessor;
import com.jaymoskingdoms.equestrian.Bond;
import com.jaymoskingdoms.equestrian.Bonding;
import com.jaymoskingdoms.equestrian.Breed;
import com.jaymoskingdoms.equestrian.Equestrian;
import com.jaymoskingdoms.equestrian.Equine;
import com.jaymoskingdoms.equestrian.Gait;
import com.jaymoskingdoms.equestrian.Herd;
import com.jaymoskingdoms.equestrian.Herding;
import com.jaymoskingdoms.equestrian.Mounted;
import com.jaymoskingdoms.equestrian.Tackroom;
import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.equestrian.Stride;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractMountInventoryScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.34) Riding, client side: the scroll wheel shifts gait, sprint bursts at the gallop, and the HUD (bottom right,
 * right of the hotbar, above the powers' chips, in Wind's style) shows the breed, the gait and a stamina bar with the
 * mark where a blown horse gets its wind back. A tamed horse's inventory screen shows its breed and stats.
 * (0.5.44) Bond and tack: the whistle (N) and trick (J; hold back for the kick) keys, hunger and thirst bars over the
 * stamina bar (and under the crosshair when you look at your horse), the tack screen and its button on the horse's own
 * screen, and the stats only for a horse that trusts you enough.
 * (0.7.305, ideas from Icy's Better Horses) Hold the whistle key for the command wheel ({@link HorseWheelScreen}); double-tap
 * forward in the saddle to ride on without holding it (back stops); the HUD shows riding on and the charge; a Rider
 * holding a saddle sees a wild horse's breed, sex and stats under the crosshair; the handbook.
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

	private static final int TAG_RANGE = 8;
	/** (0.7.305) Hold the whistle this many ticks for the wheel (shorter: a whistle); two taps of forward this close together ride on. */
	private static final int WHEEL_HOLD = 6, DOUBLE_TAP = 7;
	private static final int CHARGE_COLOR = 0xFF9A4A, CRUISE_COLOR = 0xBFF4EA;
	private static final int MARE_COLOR = 0xFF9AC8, STALLION_COLOR = 0x8AC8FF;
	/** (0.7.444) A coat's name in the readouts: plain, and a rare (or moonlit) one. */
	static final int COAT_COLOR = 0xC8B8A0, RARE_COAT_COLOR = 0xFFD966;
	/** How long the whistle's been held (-1: it opened the wheel; wait for it to come up). */
	private static int whistleHeld;
	/** Riding on without holding forward; the tick forward was last pressed; whether it was down last tick. */
	private static boolean cruising;
	private static int lastForward = -100;
	private static boolean forwardWas;
	private static KeyMapping whistleKey;
	private static KeyMapping trickKey;

	/** (Implemented by {@code AbstractMountInventoryScreenMixin}) the animal a mount screen is for. */
	public interface MountScreen {
		LivingEntity jaymoskingdoms$mount();
	}

	private EquestrianClient() {
	}

	public static void register() {
		EntityRenderers.register(Equestrian.WAR_ELEPHANT, WarElephantRenderer::new);
		// 0.7.305: our own horse, a build per breed, for every horse, skeleton horse and zombie horse (0.5.44: Mo' Creatures')
		BreedHorseModel.register();
		TackLayer.register();
		EntityRenderers.register(EntityTypes.HORSE, BreedHorseRenderer::new);
		EntityRenderers.register(EntityTypes.SKELETON_HORSE, BreedHorseRenderer::new);
		EntityRenderers.register(EntityTypes.ZOMBIE_HORSE, BreedHorseRenderer::new);
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
		// (0.7.305) riding on, the roster's answer, the handbook
		Mounted.cruise = player -> cruising && player == Minecraft.getInstance().player;
		ClientPlayNetworking.registerGlobalReceiver(Herding.Roster.TYPE, (payload, context) -> RosterScreen.receive(payload));
		Tackroom.openHandbook = () -> Minecraft.getInstance().gui.setScreen(new HandbookScreen());
		MythicMountsClient.register();
		ClientPlayNetworking.registerGlobalReceiver(Equestrian.Shied.TYPE, (payload, context) -> {
			if (payload.horse() == riding) {
				gait = Gait.WALK;
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, JaymosKingdoms.id("equestrian"), EquestrianClient::hud);
		// 0.5.44: bond and tack
		KeyMapping.Category category = KeyMapping.Category.register(JaymosKingdoms.id("equestrian"));
		whistleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.whistle", InputConstants.KEY_N, category));
		trickKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.trick", InputConstants.KEY_J, category));
		// (0.7.110) free rein: the camera free, the keys turn the horse toward it
		FreeReinClient.register(category);
		ClientPlayNetworking.registerGlobalReceiver(Bonding.RiderGait.TYPE, (payload, context) -> {
			if (payload.horse() == riding) {
				gait = Gait.byId(payload.gait());
			}
		});
		MenuScreens.register(Bonding.TACK_MENU, TackScreen::new);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosKingdoms.id("horse_tag"), EquestrianClient::tag);
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (screen instanceof AbstractMountInventoryScreen<?> mount && screen instanceof MountScreen holder
				&& holder.jaymoskingdoms$mount() instanceof AbstractHorse horse && Equestrian.covered(horse) && horse.isTamed() && !horse.isBaby()
				&& !(Equestrian.peek(horse) instanceof Equine pe && pe.breed().ass())) {
				int x = (width - imageWidth(mount)) / 2 + imageWidth(mount) - 42;
				int y = (height - imageHeight(mount)) / 2 + 3;
				Screens.getWidgets(screen).add(Button.builder(Component.translatable("button.jaymoskingdoms.tack"),
					b -> ClientPlayNetworking.send(new Bonding.OpenTack(horse.getId()))).bounds(x, y, 38, 12).build());
			}
		});
	}

	/** The horse {@code player} rides and steers, if it's one of the upgraded ones. */
	static @Nullable AbstractHorse ridden(@Nullable Player player) {
		return player != null && player.getVehicle() instanceof AbstractHorse horse && Equestrian.covered(horse)
			&& horse.getControllingPassenger() == player && horse.getAttached(Equestrian.EQUINE) != null ? horse : null;
	}

	private static void tick(Minecraft minecraft) {
		// (0.7.305) a tap whistles; held, the command wheel opens
		boolean whistling = whistleKey.isDown();
		while (whistleKey.consumeClick()) {
			if (whistleHeld == 0) {
				whistleHeld = 1;
			}
		}
		if (whistling && whistleHeld > 0) {
			if (++whistleHeld > WHEEL_HOLD && minecraft.player != null && minecraft.gui.screen() == null) {
				whistleHeld = -1;
				minecraft.gui.setScreen(new HorseWheelScreen(whistleKey, commanded(minecraft.player)));
			}
		} else if (!whistling) {
			if (whistleHeld > 0 && minecraft.player != null) {
				ClientPlayNetworking.send(new Bonding.Whistle());
			}
			whistleHeld = 0;
		}
		while (trickKey.consumeClick()) {
			if (ridden(minecraft.player) != null) {
				ClientPlayNetworking.send(new Bonding.Trick(minecraft.options.keyDown.isDown()));
			}
		}
		AbstractHorse horse = ridden(minecraft.player);
		if (horse == null) {
			riding = -1;
			sprint = false;
			cruising = false;
			return;
		}
		cruise(minecraft);
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

	/** (0.7.305) Double-tap forward to ride on; back (or rearing to a stop) ends it. */
	private static void cruise(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		boolean forward = minecraft.options.keyUp.isDown() && minecraft.gui.screen() == null;
		if (forward && !forwardWas) {
			if (player.tickCount - lastForward <= DOUBLE_TAP) {
				toggleCruise();
				lastForward = -100;
			} else {
				lastForward = player.tickCount;
			}
		}
		forwardWas = forward;
		if (cruising && minecraft.options.keyDown.isDown()) {
			toggleCruise();
		}
	}

	/** Riding on, or not (from the double tap, the back key and the wheel). */
	static void toggleCruise() {
		LocalPlayer player = Minecraft.getInstance().player;
		cruising = !cruising && ridden(player) != null;
		if (player != null && ridden(player) != null) {
			player.sendOverlayMessage(Component.translatable(cruising ? "message.jaymoskingdoms.ride_on" : "message.jaymoskingdoms.ride_off"));
		}
	}

	static boolean cruising() {
		return cruising;
	}

	/** The horse the command wheel orders: the tamed one under the crosshair, close enough, else the one you ride. */
	static @Nullable AbstractHorse commanded(Player player) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.crosshairPickEntity instanceof AbstractHorse horse && Equestrian.covered(horse) && horse.isTamed()
			&& horse.distanceTo(player) <= Herding.ORDER_RANGE) {
			return horse;
		}
		return player.getVehicle() instanceof AbstractHorse horse && Equestrian.covered(horse) && horse.isTamed() ? horse : null;
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
		double capacity = Bonding.capacity(horse, e);
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
			String key = e.breed().elephant() ? "hud.jaymoskingdoms.charge" : "hud.jaymoskingdoms.sprint";
			label.append(Component.translatable(key).withColor(pulse(GAIT_COLORS[4], 0xFFFFFF, time * 0.8F)));
		} else {
			label.append(Component.translatable(shown.key()).withColor(GAIT_COLORS[shown.ordinal()]));
		}
		if (cruising) {
			label.append(Component.literal("  ")).append(Component.translatable("hud.jaymoskingdoms.riding_on").withColor(CRUISE_COLOR));
		}
		graphics.text(font, label, x0 + BAR_WIDTH - font.width(label), y - 11, 0xE0FFFFFF);
		// (0.7.305) the charge, a thin bar under the stamina; it glows once it's enough to run things down
		float charge = Mounted.charge(horse);
		if (charge > 0.0F) {
			int cc = charge >= Mounted.CHARGE_READY ? pulse(CHARGE_COLOR, 0xFFFFFF, time * 0.6F) : 0x806040;
			graphics.fill(x0 - 1, y + 4, x0 + BAR_WIDTH + 1, y + 6, 0x90000000);
			graphics.fill(x0, y + 4, x0 + Mth.ceil(BAR_WIDTH * charge), y + 5, 0xFF000000 | cc);
		}
		// the gait ladder: five pips, lit to the current one
		for (Gait g : Gait.values()) {
			int px = x0 + g.ordinal() * 4;
			boolean reach = g.ordinal() <= e.breed().topGait.ordinal();
			int pip = g.ordinal() <= shown.ordinal() ? 0xFF000000 | GAIT_COLORS[shown.ordinal()] : reach ? 0xFF404040 : 0x60202020;
			graphics.fill(px, y - 4, px + 3, y - 2, pip);
		}
		// (0.5.44) its hunger and thirst, two short bars over the label
		Bond b = horse.getAttached(Bonding.BOND);
		// (its owner isn't synced to clients; a horse that knows you is a horse of yours or a friend's)
		if (b != null && horse.isTamed() && b.knows(player.getUUID()) && !Equestrian.undead(horse)) {
			needs(graphics, b, x0, y - 15, BAR_WIDTH, time);
		}
	}

	/** Hunger and thirst side by side, {@code width} across; each flashes when it runs low. */
	private static void needs(GuiGraphicsExtractor graphics, Bond b, int x, int y, int width, float time) {
		int half = (width - 2) / 2;
		bar(graphics, x, y, half, b.hunger() / Bond.FULL, b.hunger() < Bonding.HUNGRY ? pulse(TackScreen.HUNGER_COLOR, 0xFFFFFF, time * 0.4F) : TackScreen.HUNGER_COLOR);
		bar(graphics, x + half + 2, y, half, b.thirst() / Bond.FULL, b.thirst() < Bonding.THIRSTY ? pulse(TackScreen.THIRST_COLOR, 0xFFFFFF, time * 0.4F) : TackScreen.THIRST_COLOR);
	}

	private static void bar(GuiGraphicsExtractor graphics, int x, int y, int width, float share, int color) {
		graphics.fill(x - 1, y - 1, x + width + 1, y + 3, 0x90000000);
		graphics.fill(x, y, x + Mth.ceil(width * Mth.clamp(share, 0.0F, 1.0F)), y + 2, 0xFF000000 | color);
	}

	/** Under the crosshair, when you look at a horse of yours from close by on foot: its name, your trust, its hunger and thirst. */
	private static void tag(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || player.isPassenger() || minecraft.gui.screen() != null
			|| !(minecraft.crosshairPickEntity instanceof AbstractHorse horse) || !Equestrian.covered(horse) || horse.distanceTo(player) > TAG_RANGE) {
			return;
		}
		if (!horse.isTamed()) {
			wild(graphics, player, horse);
			return;
		}
		Bond b = horse.getAttached(Bonding.BOND);
		if (b == null || !b.knows(player.getUUID())) {
			return;
		}
		Font font = minecraft.font;
		float time = player.tickCount + delta.getGameTimeDeltaPartialTick(false);
		int cx = graphics.guiWidth() / 2;
		int y = graphics.guiHeight() / 2 + 10;
		float trust = b.trustOf(player.getUUID());
		Component name = horse.hasCustomName() ? horse.getCustomName() : Component.translatable(
			horse.getAttached(Equestrian.EQUINE) == null ? "entity.minecraft.horse" : horse.getAttached(Equestrian.EQUINE).breed().key());
		MutableComponent label = name.copy().withColor(0xE0E0E0).append(sex(horse)).append(Component.literal("  "))
			.append(Component.translatable("tack.jaymoskingdoms.trust", Math.round(trust)).withColor(TackScreen.trustColor(trust)));
		graphics.text(font, label, cx - font.width(label) / 2, y, 0xFFFFFFFF);
		if (!Equestrian.undead(horse)) {
			needs(graphics, b, cx - BAR_WIDTH / 2, y + 11, BAR_WIDTH, time);
		}
	}

	/** (0.7.305) A Rider with a saddle in hand reads a wild horse at a glance: its breed and sex over its five stats. */
	private static void wild(GuiGraphicsExtractor graphics, Player player, AbstractHorse horse) {
		Equine e = horse.getAttached(Equestrian.EQUINE);
		if (e == null || !Classes.is(player, Classes.PlayerClass.RIDER) || !(saddle(player.getMainHandItem()) || saddle(player.getOffhandItem()))) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		int cx = graphics.guiWidth() / 2;
		int y = graphics.guiHeight() / 2 + 10;
		MutableComponent label = Component.translatable(e.breed().key()).withColor(0xE0E0E0).append(sex(horse));
		if (!e.breed().ass()) {
			label.append(Component.literal(" · ").withColor(0x909090)).append(coat(e));
		}
		graphics.text(font, label, cx - font.width(label) / 2, y, 0xFFFFFFFF);
		float[] values = {e.speed(), e.stamina(), e.jump(), e.courage(), e.temper()};
		int x = cx - 45;
		graphics.fill(x - 2, y + 10, x + 92, y + 11 + STATS.length * 9 + 1, 0x90000000);
		for (int i = 0; i < STATS.length; i++) {
			int row = y + 12 + i * 9;
			graphics.text(font, Component.translatable("stat.jaymoskingdoms.horse." + STATS[i]), x + 2, row, com.jaymoskingdoms.client.Ui.TEXT, false);
			int bx = x + 46;
			int bw = 40;
			graphics.fill(bx, row + 3, bx + bw, row + 6, com.jaymoskingdoms.client.Ui.BAR_BACK);
			graphics.fill(bx, row + 3, bx + Mth.ceil(bw * values[i]), row + 6, 0xFF000000 | STAT_COLORS[i]);
		}
	}

	private static boolean saddle(ItemStack stack) {
		return stack.is(Items.SADDLE) || stack.is(Tackroom.WAR_SADDLE) || stack.is(Tackroom.RACING_SADDLE);
	}

	/** (0.7.444, Havens patch 8) Its coat by name ("Bay with Blaze"), in gold when it's its breed's rare one or moonlit. */
	static Component coat(Equine e) {
		return Component.translatable("coat.jaymoskingdoms." + e.coatName()).withColor(e.rareCoat() ? RARE_COAT_COLOR : COAT_COLOR);
	}

	/** Its sex as a coloured symbol after its name (nothing until it's known). */
	static Component sex(AbstractHorse horse) {
		Herd h = horse.getAttached(Herding.HERD);
		if (h == null || h.sex() == Herd.UNKNOWN) {
			return Component.empty();
		}
		return Component.literal(" " + Herding.symbol(h.sex())).withColor(h.mare() ? MARE_COLOR : STALLION_COLOR);
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
		// (0.7.444) its coat after its breed, sliding when it doesn't fit the box
		MutableComponent head = Component.translatable(e.breed().key()).withColor(com.jaymoskingdoms.client.Ui.GOLD).append(sex(horse));
		if (!e.breed().ass()) {
			head.append(Component.literal(" · ").withColor(com.jaymoskingdoms.client.Ui.DIM)).append(coat(e));
		}
		com.jaymoskingdoms.client.Ui.line(graphics, font, head, x + 2, y + 1, 86, com.jaymoskingdoms.client.Ui.GOLD);
		// (0.5.44) a horse shows you what it's made of only once it trusts you
		Player viewer = Minecraft.getInstance().player;
		Bond b = horse.getAttached(Bonding.BOND);
		if (viewer != null && (b == null || b.trustOf(viewer.getUUID()) < Bonding.STATS_TRUST)) {
			graphics.textWithWordWrap(font, Component.translatable("tack.jaymoskingdoms.wary", (int)Bonding.STATS_TRUST), x + 2, y + 13, 86, com.jaymoskingdoms.client.Ui.DIM, false);
			return;
		}
		float[] values = {e.speed(), e.stamina(), e.jump(), e.courage(), e.temper()};
		for (int i = 0; i < STATS.length; i++) {
			int row = y + 11 + i * 9;
			graphics.text(font, Component.translatable("stat.jaymoskingdoms.horse." + STATS[i]), x + 2, row, com.jaymoskingdoms.client.Ui.TEXT, false);
			int bx = x + 46;
			int bw = 40;
			graphics.fill(bx, row + 3, bx + bw, row + 6, com.jaymoskingdoms.client.Ui.BAR_BACK);
			graphics.fill(bx, row + 3, bx + Mth.ceil(bw * values[i]), row + 6, 0xFF000000 | STAT_COLORS[i]);
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
