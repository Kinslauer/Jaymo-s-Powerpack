package com.jaymospowers.client;

import com.jaymospowers.core.Crawl;
import com.jaymospowers.core.Stance;
import com.jaymospowers.core.wounds.Downed;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.57) The stance wheel ({@link Stance}): hold Sneak and scroll down to go lower (sneak, low crouch, prone) or up to
 * rise (back up to standing), one stance a notch. While Sneak is held the wheel is the stance's, never the hotbar's, so
 * the two never fight. The stance picked stays once Sneak is let go:
 * <ul>
 * <li>a held sneak or low crouch keeps you crouched without the key (the Sneak bit is sent for you, {@link #shift});</li>
 * <li>prone is the crawl ({@link CrawlTaps}), and gets up the crawl's ways too (Jump, the Crawl key) as well as by the wheel;</li>
 * <li>Jump, or a tap of Sneak without scrolling, stands you up from a held sneak or low crouch.</li>
 * </ul>
 * Rising waits for room overhead: under a low ceiling the wheel stops at the highest stance that fits. Scrolling up with
 * Sneak still held stands you straight up (Sneak is ignored until you let it go). The stance shows in the corner,
 * Tarkov-style ({@link StanceHud}).
 */
public final class StanceScroll {
	/** The stance held for you (a {@link Stance} constant), or null: none, Sneak works as it always has. */
	private static @Nullable Integer held;
	/** The wheel turned so far toward the next notch (a trackpad scrolls in small bits). */
	private static double wheel;
	private static boolean shiftWas;
	/** The wheel turned during this press of Sneak (so letting go isn't a tap). */
	private static boolean scrolledThisPress;
	private static boolean jumpWas;
	/** (0.18.80) Ticks the low crouch has stood still, and whether it has settled onto the knee (then you stay put). */
	private static int stillTicks;
	private static boolean knelt;
	private static double lastX, lastZ;

	private StanceScroll() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(StanceScroll::tick);
	}

	/** From {@code StanceScrollMixin}: the wheel turned. True if the stance took it (the hotbar keeps still). */
	public static boolean scroll(double amount) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (amount == 0.0 || player == null || minecraft.gui.screen() != null || !minecraft.options.keyShift.isDown()
			|| JaymosPowersClient.modeKey().isDown()) {
			return false;
		}
		// (0.18.85) mid-slide the wheel says whether it ends in prone
		if (com.jaymospowers.client.freerun.FreerunClient.slideScroll(amount)) {
			scrolledThisPress = true;
			return true;
		}
		if (!able(player)) {
			return false;
		}
		scrolledThisPress = true;
		if (Math.signum(wheel) != Math.signum(amount)) {
			wheel = 0.0;
		}
		wheel += amount;
		if (Math.abs(wheel) < 1.0) {
			return true;
		}
		// scroll down goes lower, like turning the wheel toward you
		int step = wheel > 0.0 ? 1 : -1;
		wheel -= step;
		int from = current(player);
		int to = Mth.clamp(from + step, Stance.PRONE, Stance.STAND);
		if (from == Stance.PRONE && step < 0) {
			// (0.19.1) Andy: "sliding into prone and scrolling down doesnt lock you into the prone toggle like it should".
			// Already down from a slide with Sneak held, a turn down locks it: letting go of Sneak no longer gets you up
			CrawlTaps.lockProne();
			held = Stance.PRONE;
		}
		if (to != from) {
			go(player, from, to);
		}
		return true;
	}

	/**
	 * From {@code StanceInputMixin}: what the Sneak bit sent this tick should be, or null to leave it to the key. A held
	 * sneak or low crouch holds it down; standing by the wheel with Sneak still down lets it up.
	 */
	public static @Nullable Boolean shift() {
		if (held == null) {
			return null;
		}
		return switch (held) {
			case Stance.LOW, Stance.SNEAK -> true;
			case Stance.STAND -> false;
			default -> null;
		};
	}

	/** (0.18.85) Whether the wheel turned during the current (or just-released) press of Sneak. */
	static boolean scrolledThisPress() {
		return scrolledThisPress;
	}

	/** (0.18.85) A slide held down into prone by the wheel: prone is now the held stance. */
	public static void holdProne() {
		held = Stance.PRONE;
	}

	/** The stance the local player is in now. Sneak held with no stance held counts as a sneak. */
	private static int current(LocalPlayer player) {
		if (CrawlTaps.prone()) {
			return Stance.PRONE;
		}
		if (Stance.localLow == player.getId()) {
			return Stance.LOW;
		}
		if (held != null) {
			return held;
		}
		return Minecraft.getInstance().options.keyShift.isDown() ? Stance.SNEAK : Stance.STAND;
	}

	private static void go(LocalPlayer player, int from, int to) {
		if (from == Stance.PRONE) {
			// off the belly only with room for where you're getting up to
			if (!room(player, to == Stance.LOW ? Stance.LOW_HEIGHT : Stance.SNEAK_HEIGHT)) {
				return;
			}
			CrawlTaps.getUp();
		} else if (from == Stance.LOW && to > Stance.LOW && !room(player, Stance.SNEAK_HEIGHT)) {
			return;
		}
		if (to == Stance.PRONE) {
			CrawlTaps.goProne(player, false);
			if (!CrawlTaps.prone()) {
				return;
			}
		}
		setLow(player, to == Stance.LOW);
		held = to;
	}

	/** The stance the local player shows (for {@link StanceHud}): prone, low crouch, crouched, or on their feet. */
	static int shown(LocalPlayer player) {
		if (Crawl.crawling(player) || player.getPose() == Pose.SWIMMING && !player.isInWater()) {
			return Stance.PRONE;
		}
		if (player.isCrouching()) {
			return Stance.low(player) ? Stance.LOW : Stance.SNEAK;
		}
		return Stance.STAND;
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			held = null;
			Stance.localLow = -1;
			wheel = 0.0;
			return;
		}
		boolean shift = minecraft.options.keyShift.isDown();
		boolean jump = minecraft.options.keyJump.isDown();
		boolean free = minecraft.gui.screen() == null;
		if (shift && !shiftWas) {
			scrolledThisPress = false;
		}
		if (CrawlTaps.prone()) {
			// prone by the Crawl key or a slide counts too: the wheel rises from there
			if (held != null) {
				held = Stance.PRONE;
			}
			setLow(player, false);
		} else if (held != null && held == Stance.PRONE) {
			// got up the crawl's own way
			held = null;
		} else if (held != null && held != Stance.STAND && !able(player)) {
			// in water, mounted, flying, downed: the held stance drops
			release(player);
		} else if (held != null && held != Stance.STAND && free
			&& (jump && !jumpWas || !shift && shiftWas && !scrolledThisPress)) {
			// Jump, or a tap of Sneak, stands you up (from the low crouch only with room to)
			if (held != Stance.LOW || room(player, Stance.SNEAK_HEIGHT)) {
				release(player);
			}
		}
		if (held != null && held == Stance.STAND && !shift) {
			held = null;
		}
		tickKneel(player);
		shiftWas = shift;
		jumpWas = jump;
	}

	/**
	 * (0.18.80) Andy: "crouching should keep our low stance with arms out, stopping stabilizes into a kneel, and you can
	 * still walk but you have to change back to a sneak or stand after kneeling". Walking in the low crouch is free; stood
	 * still {@link com.jaymospowers.core.Kneel#KNEEL_AFTER} ticks you settle onto the knee and stay there ({@link #knelt})
	 * until the wheel takes you up to a sneak or standing (or Jump / a Sneak tap stands you).
	 */
	private static void tickKneel(LocalPlayer player) {
		boolean low = held != null && held == Stance.LOW && Stance.localLow == player.getId() && player.onGround();
		double dx = player.getX() - lastX, dz = player.getZ() - lastZ;
		lastX = player.getX();
		lastZ = player.getZ();
		if (!low) {
			stillTicks = 0;
			knelt = false;
			return;
		}
		if (knelt) {
			return;
		}
		boolean still = dx * dx + dz * dz < com.jaymospowers.core.Kneel.STILL * com.jaymospowers.core.Kneel.STILL;
		stillTicks = still ? stillTicks + 1 : 0;
		knelt = stillTicks >= com.jaymospowers.core.Kneel.KNEEL_AFTER;
	}

	/** (0.18.80) From {@code StanceInputMixin}: on the knee in the low crouch, the walking keys do nothing. */
	public static boolean kneelHeld() {
		return knelt;
	}

	/** Whether the stances can be changed now: on foot, not swimming, flying, riding, downed or hanging from a ledge. */
	private static boolean able(LocalPlayer player) {
		return Crawl.canCrawl(player) && !Downed.isDowned(player) && !com.jaymospowers.core.wounds.Breaks.crawlOnly(player)
			&& !LedgeClient.hanging();
	}

	private static void release(LocalPlayer player) {
		held = null;
		setLow(player, false);
	}

	private static void setLow(LocalPlayer player, boolean low) {
		if (low == (Stance.localLow == player.getId())) {
			return;
		}
		Stance.localLow = low ? player.getId() : -1;
		ClientPlayNetworking.send(new Stance.Low(low));
		player.refreshDimensions();
	}

	/** Whether there's room overhead for a crouch {@code height} blocks tall (before scale) right where the player is. */
	private static boolean room(LocalPlayer player, float height) {
		float scale = player.getScale();
		EntityDimensions box = EntityDimensions.scalable(player.getDimensions(Pose.STANDING).width(), height * scale);
		return player.level().noCollision(player, box.makeBoundingBox(player.position()).deflate(1.0E-7));
	}
}
