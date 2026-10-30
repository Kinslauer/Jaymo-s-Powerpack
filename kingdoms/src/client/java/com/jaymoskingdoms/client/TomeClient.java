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
import net.minecraft.world.InteractionHand;
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
 * <p>
 * (0.7.391) A Mage's or Sorcerer's off-hand book is held on right click (its use; {@code Tomes.Tome#useOff}), sneaking to
 * cast its page; each hand's book keeps its own state ({@link Book}).
 */
final class TomeClient {
	/** The frames {@code tome_pose} counts (see {@code elemental_tomes.py}): closed, half open, open, then the leaf's five angles. */
	static final int CLOSED = 0, HALF = 1, OPEN = 2, FLIP = 3, FLIP_FRAMES = 5;
	/** How long (ms) the cover takes to swing from closed to half and from half to open (and back), a leaf takes to turn, and the book stays open after the last thing done. */
	static final long COVER_MS = 90, FLIP_MS = 260, LINGER_MS = 900;
	/** While charging, a leaf turns every this long (ms): slowly for a tap's charge, quickly for a full one. */
	static final long FLUTTER_SLOW_MS = 560, FLUTTER_FAST_MS = 240;

	/** (0.7.391) What each of your books is doing: the main hand's (left click) and the off hand's (right click). */
	private static final class Book {
		final boolean off;
		boolean down, wasKey;
		/** When (ms) the charge began, and when the book last shot, had a page stored, and cast one. */
		long downSince, lastShot, lastStore, lastCast;
		/** Whether the book is open, and since when (ms). */
		boolean open;
		long changedAt;
		/** What the hand held last tick, to tell a page stored from a book just drawn. */
		@Nullable Item lastItem;
		int lastSlot;
		@Nullable String lastPage;
		boolean wasCooling;

		Book(boolean off) {
			this.off = off;
		}

		ItemStack stack(Player player) {
			return off ? player.getOffhandItem() : player.getMainHandItem();
		}
	}

	private static final Book MAIN = new Book(false), OFF = new Book(true);

	private TomeClient() {
	}

	/** Whether you're charging a bolt now from the book in that hand. */
	static boolean charging(boolean off) {
		return (off ? OFF : MAIN).down;
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
		boolean free = minecraft.gui.screen() == null && minecraft.mouseHandler.isMouseGrabbed();
		boolean ready = player != null && !player.isSpectator() && !player.isUsingItem();
		long now = Util.getMillis();
		// the main hand's book takes left click
		work(MAIN, ready && holdsAttack(minecraft), minecraft.options.keyAttack.isDown() && free, now);
		// a Mage's or Sorcerer's off-hand book is held on right click (the server times it as the item's use)
		boolean using = player != null && player.isUsingItem() && player.getUsedItemHand() == InteractionHand.OFF_HAND
			&& Tomes.isTome(player.getOffhandItem());
		if (using && !OFF.down) {
			OFF.downSince = now;
		} else if (!using && OFF.down) {
			OFF.lastShot = now;
		}
		OFF.down = using;
		follow(MAIN, player, now);
		follow(OFF, player, now);
	}

	/** The main hand's book's button: pressed starts a charge, let go fires it. */
	private static void work(Book book, boolean tome, boolean key, long now) {
		if (!ClientPlayNetworking.canSend(Tomes.Attack.TYPE)) {
			book.down = false;
			book.wasKey = key;
			return;
		}
		if (book.down) {
			if (!tome || !key) {
				// let go: fire; the book changed or a screen opened: drop the charge
				boolean fire = tome && Minecraft.getInstance().gui.screen() == null;
				ClientPlayNetworking.send(new Tomes.Attack(fire ? 0 : 2));
				book.down = false;
				if (fire) {
					book.lastShot = now;
				}
			}
		} else if (tome && key && !book.wasKey) {
			// only a fresh press starts one (not a button still down from before the book was drawn)
			ClientPlayNetworking.send(new Tomes.Attack(1));
			book.down = true;
			book.downSince = now;
		}
		book.wasKey = key;
	}

	/** Notes what the book in that hand is doing: a page stored, a page cast, and whether it should be open. */
	private static void follow(Book book, @Nullable LocalPlayer player, long now) {
		boolean held = player != null && Tomes.isTome(book.stack(player));
		if (held) {
			ItemStack stack = book.stack(player);
			String page = Tomes.page(stack);
			int slot = book.off ? -1 : player.getInventory().getSelectedSlot();
			boolean cooling = player.getCooldowns().isOnCooldown(stack);
			// only the same book as last tick counts: a book just drawn isn't a page stored or a spell cast
			if (stack.getItem() == book.lastItem && slot == book.lastSlot) {
				if (!Objects.equals(page, book.lastPage)) {
					book.lastStore = now;
				}
				if (cooling && !book.wasCooling) {
					book.lastCast = now;
				}
			}
			book.lastItem = stack.getItem();
			book.lastSlot = slot;
			book.lastPage = page;
			book.wasCooling = cooling;
		} else {
			book.lastItem = null;
		}
		boolean wanted = held && (book.down || TomeFloat.afloat(player, book.off)
			|| now - Math.max(book.lastShot, Math.max(book.lastStore, book.lastCast)) < LINGER_MS);
		if (wanted != book.open) {
			book.open = wanted;
			book.changedAt = now;
		}
	}

	/** The frame {@code tome_pose} gives the book {@code stack} that {@code owner} holds (see {@link TomePose}). */
	static float pose(ItemStack stack, @Nullable ItemOwner owner) {
		LivingEntity living = owner == null ? null : owner.asLivingEntity();
		if (living == null) {
			return CLOSED;
		}
		boolean off = stack == living.getOffhandItem() && stack != living.getMainHandItem();
		Minecraft minecraft = Minecraft.getInstance();
		if (living == minecraft.player && Tomes.isTome(stack)) {
			return mine(off ? OFF : MAIN, minecraft.player, Util.getMillis());
		}
		// anyone else's: open, a leaf turning, for as long as the hand swings; afloat, open, a leaf turning as it rises or locks
		if (living.isSwinging()) {
			return flip(living.getSwingAnimation(0.0F));
		}
		if (living instanceof Player player && TomeFloat.afloat(player, off)) {
			float turned = TomeFloat.turning(player, off, Util.getMillis());
			return turned >= 0.0F ? flip(turned) : OPEN;
		}
		return CLOSED;
	}

	/** The frame of your own book: the cover, then the leaf (a turn for each thing done, a flutter while charging). */
	private static float mine(Book book, LocalPlayer player, long now) {
		boolean shut = now - book.changedAt >= COVER_MS;
		if (!book.open) {
			return shut ? CLOSED : HALF;
		}
		if (!shut) {
			return HALF;
		}
		long since = Math.min(now - book.lastShot, Math.min(now - book.lastStore, now - book.lastCast));
		if (since < FLIP_MS) {
			return flip(since / (float) FLIP_MS);
		}
		float turned = TomeFloat.turning(player, book.off, now);
		if (turned >= 0.0F) {
			return flip(turned);
		}
		if (book.down) {
			float charge = Tomes.charge((int) ((now - book.downSince) / 50L));
			long period = Math.round(FLUTTER_SLOW_MS + (FLUTTER_FAST_MS - FLUTTER_SLOW_MS) * charge);
			long into = (now - book.downSince) % period;
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
