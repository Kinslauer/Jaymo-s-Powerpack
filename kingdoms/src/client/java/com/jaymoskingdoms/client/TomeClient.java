package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Tomes;
import java.util.Objects;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.util.Util;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The spell tomes' left click ({@link Tomes}). Vanilla sends nothing to the server for a click on air, so while a tome is in the
 * main hand this watches the attack key and sends its press and release; the server times the hold and flies the bolt.
 * While a tome is held the attack key neither swings nor digs (see {@code TomeAttackMixin}).
 * <p>
 * It also drives the book's animation, all on the client: the closed book opens when you start to charge, shoot, store a page
 * or cast one, a leaf of its pages turns with each (and every so often while you charge, quicker as the charge grows), and it
 * closes again a moment after. The item definitions dispatch on {@link TomePose} ({@code jaymoskingdoms:tome_pose}), which asks
 * {@link #pose}. A page stored is seen as the tome's page component changing, a cast as its cooldown starting. Other players'
 * books open and turn a leaf for as long as their hand swings (the server swings the hand for a shot or a cast).
 * <p>
 * (0.7.390) A book afloat ({@link TomeFloat}) stays open the whole time it floats, and turns a leaf as it rises and at each
 * new lock, yours and everyone else's.
 */
final class TomeClient {
	/** The frames {@code tome_pose} counts (see {@code elemental_tomes.py}): closed, half open, open, then the leaf's five angles. */
	static final int CLOSED = 0, HALF = 1, OPEN = 2, FLIP = 3, FLIP_FRAMES = 5;
	/** How long (ms) the cover takes to swing from closed to half and from half to open (and back), a leaf takes to turn, and the book stays open after the last thing done. */
	static final long COVER_MS = 90, FLIP_MS = 260, LINGER_MS = 900;
	/** While charging, a leaf turns every this long (ms): slowly for a tap's charge, quickly for a full one. */
	static final long FLUTTER_SLOW_MS = 560, FLUTTER_FAST_MS = 240;

	private static boolean down, wasKey;
	/** When (ms) the charge began, and when the book last shot, had a page stored, and cast one. */
	private static long downSince, lastShot, lastStore, lastCast;
	/** Whether the book is open, and since when (ms). */
	private static boolean open;
	private static long changedAt;
	/** What the main hand held last tick, to tell a page stored from a book just drawn. */
	private static @Nullable Item lastItem;
	private static int lastSlot;
	private static @Nullable String lastPage;
	private static boolean wasCooling;

	private TomeClient() {
	}

	/** Whether you're charging a bolt now. */
	static boolean charging() {
		return down;
	}

	/** Whether the attack key belongs to the tome in the hand now. */
	static boolean holdsAttack(Minecraft minecraft) {
		return minecraft.player != null && Tomes.isTome(minecraft.player.getMainHandItem());
	}

	static void register() {
		RangeSelectItemModelProperties.ID_MAPPER.put(JaymosKingdoms.id("tome_pose"), TomePose.CODEC);
		ClientTickEvents.END_CLIENT_TICK.register(TomeClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		boolean tome = player != null && holdsAttack(minecraft) && !player.isSpectator() && !player.isUsingItem();
		boolean key = minecraft.options.keyAttack.isDown() && minecraft.gui.screen() == null && minecraft.mouseHandler.isMouseGrabbed();
		long now = Util.getMillis();
		if (!ClientPlayNetworking.canSend(Tomes.Attack.TYPE)) {
			down = false;
			wasKey = key;
			follow(player, now);
			return;
		}
		if (down) {
			if (!tome || !key) {
				// let go: fire; the book changed or a screen opened: drop the charge
				boolean fire = tome && minecraft.gui.screen() == null;
				ClientPlayNetworking.send(new Tomes.Attack(fire ? 0 : 2));
				down = false;
				if (fire) {
					lastShot = now;
				}
			}
		} else if (tome && key && !wasKey) {
			// only a fresh press starts one (not a button still down from before the book was drawn)
			ClientPlayNetworking.send(new Tomes.Attack(1));
			down = true;
			downSince = now;
		}
		wasKey = key;
		follow(player, now);
	}

	/** Notes what the book in the hand is doing: a page stored, a page cast, and whether it should be open. */
	private static void follow(@Nullable LocalPlayer player, long now) {
		boolean held = player != null && Tomes.isTome(player.getMainHandItem());
		if (held) {
			ItemStack stack = player.getMainHandItem();
			String page = Tomes.page(stack);
			int slot = player.getInventory().getSelectedSlot();
			boolean cooling = player.getCooldowns().isOnCooldown(stack);
			// only the same book as last tick counts: a book just drawn isn't a page stored or a spell cast
			if (stack.getItem() == lastItem && slot == lastSlot) {
				if (!Objects.equals(page, lastPage)) {
					lastStore = now;
				}
				if (cooling && !wasCooling) {
					lastCast = now;
				}
			}
			lastItem = stack.getItem();
			lastSlot = slot;
			lastPage = page;
			wasCooling = cooling;
		} else {
			lastItem = null;
		}
		boolean wanted = held && (down || TomeFloat.afloat(player) || now - Math.max(lastShot, Math.max(lastStore, lastCast)) < LINGER_MS);
		if (wanted != open) {
			open = wanted;
			changedAt = now;
		}
	}

	/** The frame {@code tome_pose} gives the book {@code owner} holds (see {@link TomePose}). */
	static float pose(@Nullable ItemOwner owner) {
		LivingEntity living = owner == null ? null : owner.asLivingEntity();
		if (living == null) {
			return CLOSED;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (living == minecraft.player && Tomes.isTome(minecraft.player.getMainHandItem())) {
			return mine(Util.getMillis());
		}
		// anyone else's: open, a leaf turning, for as long as the hand swings; afloat, open, a leaf turning as it rises or locks
		if (living.isSwinging()) {
			return flip(living.getSwingAnimation(0.0F));
		}
		if (living instanceof Player player && TomeFloat.afloat(player)) {
			float turned = TomeFloat.turning(player, Util.getMillis());
			return turned >= 0.0F ? flip(turned) : OPEN;
		}
		return CLOSED;
	}

	/** The frame of your own book: the cover, then the leaf (a turn for each thing done, a flutter while charging). */
	private static float mine(long now) {
		boolean shut = now - changedAt >= COVER_MS;
		if (!open) {
			return shut ? CLOSED : HALF;
		}
		if (!shut) {
			return HALF;
		}
		long since = Math.min(now - lastShot, Math.min(now - lastStore, now - lastCast));
		if (since < FLIP_MS) {
			return flip(since / (float) FLIP_MS);
		}
		LocalPlayer player = Minecraft.getInstance().player;
		float turned = player == null ? -1.0F : TomeFloat.turning(player, now);
		if (turned >= 0.0F) {
			return flip(turned);
		}
		if (down) {
			float charge = Tomes.charge((int) ((now - downSince) / 50L));
			long period = Math.round(FLUTTER_SLOW_MS + (FLUTTER_FAST_MS - FLUTTER_SLOW_MS) * charge);
			long into = (now - downSince) % period;
			if (into < FLIP_MS) {
				return flip(into / (float) FLIP_MS);
			}
		}
		return OPEN;
	}

	/** The leaf's frame {@code progress} (0 to 1) of the way through its turn. */
	private static float flip(float progress) {
		return FLIP + Math.clamp((int) (progress * FLIP_FRAMES), 0, FLIP_FRAMES - 1);
	}
}
