package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Lockpicking;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * (0.7.233) The lockpicking games ({@link Lockpicking}), in the Kingdoms screens' look. Both grow their secret from the
 * lock's seed, so a lock is the same lock every time it's tried. Strain builds on the pick with every mistake and eases
 * off slowly; full strain snaps it (a slip forgiven first, with Lockpicking Prowess 3), and the server takes a pick.
 *
 * <ul>
 * <li><b>Pin</b>: a row of pins that must be set in their binding order. Pick a pin (A/D or the mouse) and push it up
 * (hold W or the mouse button): a loose pin springs up and drops back, the binding pin pushes stiff and trembles, and
 * gives a faint tick as it reaches the shear line. Let go there and it sets; push past it and it drops, straining the
 * pick (on the hardest locks a set pin falls too). Rattling loose pins at the top strains the pick a little.</li>
 * <li><b>Rotary</b>: a dial and a run of hidden gates, turned to in alternating directions. Turn (A/D, the arrows or
 * the wheel; Shift slowly) the way the arrow points: the pick trembles the nearer it is to the gate. Set it (W, Space or
 * a click) in the gate and it catches; a miss strains the pick. Turning back undoes the last gate.</li>
 * </ul>
 */
final class LockpickScreen extends Screen {
	static final int W = 240, H = 200, PAD = 10;
	/** The locks' accent: brass. */
	static final int ACCENT = 0xFFC9A24A, BRASS = 0xFFB08D3A, BRASS_DARK = 0xFF6E5420, STEEL = 0xFFA8AEB6, STEEL_DARK = 0xFF5A6068, SET = 0xFF66C266;
	/** Strain eases off this much a tick. */
	static final float STRAIN_EASE = 0.003F;
	/** After a snapped pick, a short pause before the next (ticks); after the lock gives, how long it shows before closing. */
	static final int SNAP_PAUSE = 14, PICKED_SHOW = 16;

	// ---- Pin -----------------------------------------------------------------------------------------------------
	/** Pins: this many, and one more per level, at most {@link #PIN_MAX}. */
	static final int PIN_BASE = 3, PIN_MAX = 7;
	/** A pin's shear line sits this high in its travel, give or take {@link #PIN_LINE_SPREAD}. */
	static final float PIN_LINE = 0.4F, PIN_LINE_SPREAD = 0.35F;
	/** Half the sweet spot round the shear line: this, less this per level (before Stealth's factor), at most {@link #PIN_HALF_MAX}. */
	static final float PIN_HALF = 0.11F, PIN_HALF_PER_LEVEL = 0.012F, PIN_HALF_MAX = 0.2F;
	/** How fast the binding pin and a loose pin rise a tick, and how fast an unset pin drops. */
	static final float PIN_STIFF = 0.03F, PIN_LOOSE = 0.09F, PIN_DROP = 0.15F;
	/** Strain from pushing the binding pin past its line, and a tick of rattling a loose pin at the top. */
	static final float PIN_OVERSET = 0.35F, PIN_RATTLE = 0.01F;
	/** From this level, overset drops the last pin set as well. */
	static final int PIN_HARD_LEVEL = 4;
	/** How far a pin travels on screen (pixels). */
	static final int PIN_TRAVEL = 30;

	// ---- Rotary --------------------------------------------------------------------------------------------------
	/** Gates: this many, and one more every other level. */
	static final int GATE_BASE = 2;
	/** The gate's give either way (degrees): this, less this per level (before Stealth's factor). */
	static final float GATE_TOL = 11.0F, GATE_TOL_PER_LEVEL = 1.5F;
	/** The dial turns this far a tick (degrees), slowly with Shift, and per notch of the wheel. */
	static final float TURN = 4.0F, TURN_SLOW = 1.0F, TURN_WHEEL = 2.0F;
	/** The pick trembles from this far off a gate (degrees). */
	static final float FEEL_RANGE = 45.0F;
	/** Strain from setting a gate in the wrong place. */
	static final float GATE_MISS = 0.3F;
	/** Gates no closer together than this (degrees). */
	static final float GATE_APART = 50.0F;
	/** The dial's radius on screen (pixels). */
	static final int DIAL_R = 42;

	private final Lockpicking.Open lock;
	private final boolean rotary;
	private final Random shake = new Random();
	private final Set<Integer> held = new HashSet<>();
	private boolean mouseDown;
	private int picks, forgive, pause, picked = -1, tick;
	private float strain;
	private boolean sent;

	// pin state
	private int pins, binding, selected;
	private float[] line = new float[0], lift = new float[0];
	private boolean[] set = new boolean[0];
	private int[] order = new int[0];
	private float half;
	private boolean pushing, ticked;

	// rotary state
	private float[] gates = new float[0];
	private int gate;
	private float angle, tol, lastFeel;

	private LockpickScreen(Lockpicking.Open lock) {
		super(Component.translatable(lock.kind() == Lockpicking.Kind.ROTARY.ordinal() ? "gui.jaymoskingdoms.lock.rotary" : "gui.jaymoskingdoms.lock.pin"));
		this.lock = lock;
		this.rotary = lock.kind() == Lockpicking.Kind.ROTARY.ordinal();
		this.picks = lock.picks();
		this.forgive = lock.forgive();
		Random r = new Random(lock.seed());
		if (this.rotary) {
			int n = GATE_BASE + (lock.level() + 1) / 2;
			this.gates = new float[n];
			for (int i = 0; i < n; i++) {
				float a;
				int tries = 0;
				do {
					a = r.nextFloat() * 360.0F;
				} while (i > 0 && Math.abs(Mth.wrapDegrees(a - this.gates[i - 1])) < GATE_APART && ++tries < 20);
				this.gates[i] = a;
			}
			this.tol = Math.max(1.5F, (GATE_TOL - GATE_TOL_PER_LEVEL * lock.level()) * lock.width());
		} else {
			this.pins = Math.min(PIN_MAX, PIN_BASE + lock.level());
			this.line = new float[this.pins];
			this.lift = new float[this.pins];
			this.set = new boolean[this.pins];
			List<Integer> shuffled = new ArrayList<>();
			for (int i = 0; i < this.pins; i++) {
				this.line[i] = PIN_LINE + r.nextFloat() * PIN_LINE_SPREAD;
				shuffled.add(i);
			}
			Collections.shuffle(shuffled, r);
			this.order = shuffled.stream().mapToInt(Integer::intValue).toArray();
			this.half = Math.min(PIN_HALF_MAX, (PIN_HALF - PIN_HALF_PER_LEVEL * lock.level()) * lock.width());
		}
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Lockpicking.Open.TYPE, (payload, context) -> context.client().gui.setScreen(new LockpickScreen(payload)));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		send(Lockpicking.Result.GAVE_UP);
		super.onClose();
	}

	private void send(int outcome) {
		if (outcome != Lockpicking.Result.SNAPPED) {
			if (this.sent) {
				return;
			}
			this.sent = true;
		}
		ClientPlayNetworking.send(new Lockpicking.Result(this.lock.pos(), outcome));
	}

	// ---- input ---------------------------------------------------------------------------------------------------

	@Override
	public boolean keyPressed(KeyEvent event) {
		this.held.add(event.key());
		if (this.picked < 0 && this.pause == 0) {
			int key = event.key();
			if (this.rotary && (key == InputConstants.KEY_W || key == InputConstants.KEY_UP || key == InputConstants.KEY_SPACE)) {
				tryGate();
			} else if (!this.rotary && (key == InputConstants.KEY_A || key == InputConstants.KEY_LEFT)) {
				select(this.selected - 1);
			} else if (!this.rotary && (key == InputConstants.KEY_D || key == InputConstants.KEY_RIGHT)) {
				select(this.selected + 1);
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		this.held.remove(event.key());
		return super.keyReleased(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			this.mouseDown = true;
			if (this.rotary && this.picked < 0 && this.pause == 0) {
				tryGate();
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			this.mouseDown = false;
		}
		return super.mouseReleased(event);
	}

	@Override
	public void mouseMoved(double x, double y) {
		// (the pin under the mouse is the one picked, while no pin is being pushed)
		if (!this.rotary && !this.pushing && this.picked < 0) {
			int left = left() + PAD + 20, span = W - 2 * PAD - 40;
			int i = (int)Math.floor((x - left) / (span / (double)this.pins));
			if (i >= 0 && i < this.pins && y > top() + 30 && y < top() + 150) {
				select(i);
			}
		}
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (this.rotary && this.picked < 0 && this.pause == 0 && scrollY != 0.0) {
			turn((float)-Math.signum(scrollY) * TURN_WHEEL);
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	private boolean down(int... keys) {
		for (int key : keys) {
			if (this.held.contains(key)) {
				return true;
			}
		}
		return false;
	}

	// ---- the game ------------------------------------------------------------------------------------------------

	@Override
	public void tick() {
		this.tick++;
		if (this.picked >= 0) {
			if (++this.picked >= PICKED_SHOW) {
				this.minecraft.gui.setScreen(null);
			}
			return;
		}
		if (this.pause > 0) {
			this.pause--;
			return;
		}
		if (this.rotary) {
			boolean slow = down(InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT);
			float step = slow ? TURN_SLOW : TURN;
			if (down(InputConstants.KEY_D, InputConstants.KEY_RIGHT)) {
				turn(step);
			} else if (down(InputConstants.KEY_A, InputConstants.KEY_LEFT)) {
				turn(-step);
			}
			float feel = feel();
			// (a soft tick, quicker the nearer the gate)
			if (feel > 0.0F && this.tick % Math.max(2, Math.round(8 - 6 * feel)) == 0 && feel != this.lastFeel) {
				play(SoundEvents.NOTE_BLOCK_HAT, 1.4F + 0.6F * feel, 0.15F + 0.25F * feel);
			}
			this.lastFeel = feel;
		} else {
			pinTick();
		}
		this.strain = Math.max(0.0F, this.strain - STRAIN_EASE);
		if (this.strain >= 1.0F) {
			slip();
		}
	}

	/** The pick took too much: a forgiven slip, or it snaps (the server takes it) and the lock starts over. */
	private void slip() {
		if (this.forgive > 0) {
			this.forgive--;
			this.strain = 0.5F;
			play(SoundEvents.CHAIN_HIT, 1.6F, 0.6F);
			return;
		}
		send(Lockpicking.Result.SNAPPED);
		play(SoundEvents.ITEM_BREAK, 1.5F, 0.8F);
		this.picks--;
		this.strain = 0.0F;
		this.forgive = this.lock.forgive();
		this.pause = SNAP_PAUSE;
		if (this.rotary) {
			this.gate = 0;
		} else {
			for (int i = 0; i < this.pins; i++) {
				this.set[i] = false;
				this.lift[i] = 0.0F;
			}
			this.binding = 0;
		}
		if (this.picks <= 0) {
			this.sent = true;
			if (this.minecraft.player != null) {
				this.minecraft.player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.lock.out_of_picks"));
			}
			this.minecraft.gui.setScreen(null);
		} else if (this.minecraft.player != null) {
			this.minecraft.player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.lock.snapped"));
		}
	}

	private void done() {
		this.picked = 0;
		send(Lockpicking.Result.PICKED);
		play(SoundEvents.IRON_TRAPDOOR_OPEN, 1.5F, 0.7F);
	}

	// pin

	private void select(int i) {
		i = Mth.clamp(i, 0, this.pins - 1);
		if (i != this.selected) {
			release();
			this.selected = i;
		}
	}

	private int bindingPin() {
		return this.binding < this.pins ? this.order[this.binding] : -1;
	}

	private void pinTick() {
		boolean push = down(InputConstants.KEY_W, InputConstants.KEY_UP, InputConstants.KEY_SPACE) || this.mouseDown;
		if (this.pushing && !push) {
			release();
		}
		this.pushing = push;
		int s = this.selected;
		for (int i = 0; i < this.pins; i++) {
			if (!this.set[i] && !(push && i == s)) {
				this.lift[i] = Math.max(0.0F, this.lift[i] - PIN_DROP);
			}
		}
		if (!push || this.set[s]) {
			return;
		}
		if (s == bindingPin()) {
			this.lift[s] += PIN_STIFF;
			boolean in = Math.abs(this.lift[s] - this.line[s]) <= this.half;
			if (in && !this.ticked) {
				this.ticked = true;
				play(SoundEvents.NOTE_BLOCK_HAT, 1.9F, 0.35F);
			}
			if (this.lift[s] > this.line[s] + this.half) {
				// overset: it drops, and the pick takes it
				this.lift[s] = 0.0F;
				this.ticked = false;
				this.strain += PIN_OVERSET * this.lock.strain();
				play(SoundEvents.CHAIN_HIT, 1.3F, 0.5F);
				if (this.lock.level() >= PIN_HARD_LEVEL && this.binding > 0) {
					this.binding--;
					int fell = this.order[this.binding];
					this.set[fell] = false;
				}
			}
		} else {
			this.lift[s] = Math.min(1.0F, this.lift[s] + PIN_LOOSE);
			if (this.lift[s] >= 1.0F) {
				this.strain += PIN_RATTLE * this.lock.strain();
			}
		}
	}

	/** The pushed pin let go: the binding pin at its line sets. */
	private void release() {
		int s = this.selected;
		this.ticked = false;
		if (s < 0 || s >= this.pins || this.set[s] || s != bindingPin()) {
			return;
		}
		if (Math.abs(this.lift[s] - this.line[s]) <= this.half) {
			this.set[s] = true;
			this.lift[s] = this.line[s];
			this.binding++;
			play(SoundEvents.TRIPWIRE_CLICK_ON, 1.6F, 0.8F);
			if (this.binding >= this.pins) {
				done();
			}
		}
	}

	// rotary

	/** Which way the dial must turn for the gate it's on: clockwise for the first, then back and forth. */
	private int direction() {
		return this.gate % 2 == 0 ? 1 : -1;
	}

	private void turn(float by) {
		this.angle = (this.angle + by + 360.0F) % 360.0F;
		if (Math.signum(by) != direction() && this.gate > 0) {
			// turned back: the last gate slips
			this.gate--;
			play(SoundEvents.LEVER_CLICK, 0.8F, 0.4F);
		}
	}

	/** How near the dial is to the gate, 0 (out of feel) to 1 (in it). */
	private float feel() {
		if (this.gate >= this.gates.length) {
			return 0.0F;
		}
		float off = Math.abs(Mth.wrapDegrees(this.angle - this.gates[this.gate]));
		if (off <= this.tol) {
			return 1.0F;
		}
		float f = 1.0F - (off - this.tol) / FEEL_RANGE;
		return f <= 0.0F ? 0.0F : f * f;
	}

	private void tryGate() {
		if (this.gate >= this.gates.length) {
			return;
		}
		if (Math.abs(Mth.wrapDegrees(this.angle - this.gates[this.gate])) <= this.tol) {
			this.gate++;
			play(SoundEvents.TRIPWIRE_CLICK_ON, 1.2F + 0.15F * this.gate, 0.8F);
			if (this.gate >= this.gates.length) {
				done();
			}
		} else {
			this.strain += GATE_MISS * this.lock.strain();
			play(SoundEvents.CHAIN_HIT, 1.3F, 0.5F);
		}
	}

	private void play(SoundEvent sound, float pitch, float volume) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
	}

	private void play(Holder<SoundEvent> sound, float pitch, float volume) {
		play(sound.value(), pitch, volume);
	}

	// ---- drawing -------------------------------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left(), y = top();
		Ui.panel(graphics, x, y, W, H, ACCENT);
		Ui.line(graphics, this.font, this.title, x + PAD, y + 8, W / 2, ACCENT);
		Ui.lineRight(graphics, this.font, Component.translatable("gui.jaymoskingdoms.lock.level",
			Component.translatable("message.jaymoskingdoms.lock.level." + this.lock.level())), x + W - PAD, y + 8, W / 2 - PAD, Ui.DIM);
		Ui.rule(graphics, x + 6, x + W - 6, y + 21);
		if (this.rotary) {
			drawDial(graphics, x + W / 2, y + 30 + DIAL_R + 8);
		} else {
			drawPins(graphics, x, y);
		}
		// strain, picks, help
		int by = y + H - 50;
		graphics.text(this.font, Component.translatable("gui.jaymoskingdoms.lock.strain"), x + PAD, by, Ui.DIM);
		int color = this.strain > 0.7F ? Ui.BAD : this.strain > 0.4F ? 0xFFE0A040 : ACCENT;
		Ui.bar(graphics, x + PAD + 40, by + 2, 90, 4, this.strain, color);
		Ui.lineRight(graphics, this.font, Component.translatable("gui.jaymoskingdoms.lock.picks", this.picks), x + W - PAD, by, 80, Ui.TEXT);
		if (this.forgive > 0) {
			graphics.text(this.font, Component.translatable("gui.jaymoskingdoms.lock.forgive"), x + PAD, by + 10, Ui.GOOD);
		}
		graphics.textWithWordWrap(this.font, Component.translatable(this.rotary ? "gui.jaymoskingdoms.lock.rotary.help" : "gui.jaymoskingdoms.lock.pin.help"),
			x + PAD, by + 20, W - 2 * PAD, Ui.HINT);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	private void drawPins(GuiGraphicsExtractor graphics, int x, int y) {
		int bodyX = x + PAD + 10, bodyY = y + 28, bodyW = W - 2 * PAD - 20, bodyH = 104;
		// the housing, and the plug at its foot
		graphics.fill(bodyX, bodyY, bodyX + bodyW, bodyY + bodyH, BRASS_DARK);
		graphics.fill(bodyX + 2, bodyY + 2, bodyX + bodyW - 2, bodyY + bodyH - 2, BRASS);
		int plugY = bodyY + bodyH - 30;
		graphics.fill(bodyX + 2, plugY, bodyX + bodyW - 2, bodyY + bodyH - 2, 0xFFC7A250);
		graphics.fill(bodyX + 2, plugY, bodyX + bodyW - 2, plugY + 1, BRASS_DARK);
		int span = bodyW - 20, slot = span / this.pins;
		int bind = bindingPin();
		boolean turning = this.picked >= 0;
		for (int i = 0; i < this.pins; i++) {
			int cx = bodyX + 10 + slot * i + slot / 2;
			int top = bodyY + 6, bottom = plugY + 14;
			// the channel
			graphics.fill(cx - 4, top, cx + 4, bottom, 0xFF2A2010);
			if (this.lock.feel() && i == bind && i == this.selected) {
				graphics.fill(cx - 5, top, cx - 4, bottom, 0x80FFE080);
				graphics.fill(cx + 4, top, cx + 5, bottom, 0x80FFE080);
			}
			int jitter = 0;
			if (this.pushing && i == this.selected && i == bind) {
				jitter = this.shake.nextInt(3) - 1;
			}
			int rise = Math.round(this.lift[i] * PIN_TRAVEL);
			// the key pin (brass, from the plug) and the driver pin (steel) over it, the spring above
			int keyBottom = bottom - 2 - rise, keyTop = keyBottom - 14;
			int driverTop = keyTop - 16 - (this.set[i] ? 2 : 0);
			graphics.fill(cx - 3 + jitter, keyTop, cx + 3 + jitter, keyBottom, this.set[i] ? SET : 0xFFE0C068);
			graphics.fill(cx - 3 + jitter, driverTop, cx + 3 + jitter, keyTop - (this.set[i] ? 2 : 0), STEEL);
			for (int sy = top; sy < driverTop - 1; sy += 2) {
				graphics.fill(cx - 2 + ((sy / 2) % 2), sy, cx + 1 + ((sy / 2) % 2), sy + 1, STEEL_DARK);
			}
			if (turning) {
				graphics.fill(cx - 4, plugY - 1, cx + 4, plugY, SET);
			}
		}
		// the pick, from the left under the picked pin, its tip raised while pushing
		int px = bodyX + 10 + slot * this.selected + slot / 2;
		int pickY = plugY + 18 - (this.pushing ? 2 : 0);
		graphics.fill(bodyX - 6, plugY + 20, px - 3, plugY + 22, STEEL);
		graphics.fill(px - 3, pickY, px + 3, pickY + 2, STEEL);
		graphics.fill(px + 1, pickY - 2, px + 3, pickY, STEEL);
		// the tension wrench, bottom left
		graphics.fill(bodyX - 8, plugY + 24, bodyX + 10, plugY + 26, STEEL_DARK);
		graphics.fill(bodyX - 8, plugY + 18, bodyX - 6, plugY + 26, STEEL_DARK);
	}

	private void drawDial(GuiGraphicsExtractor graphics, int cx, int cy) {
		// the face: brass rings, ticks round the edge
		for (int r = DIAL_R; r >= 0; r -= 2) {
			int color = r > DIAL_R - 4 ? BRASS_DARK : r > DIAL_R - 8 ? BRASS : r > 10 ? 0xFFC7A250 : BRASS_DARK;
			int half = (int)Math.round(r * Math.sqrt(2) / 2);
			graphics.fill(cx - half, cy - r, cx + half, cy + r, color);
			graphics.fill(cx - r, cy - half, cx + r, cy + half, color);
		}
		for (int t = 0; t < 360; t += 15) {
			double rad = Math.toRadians(t - this.angle);
			int r0 = DIAL_R - 7, r1 = DIAL_R - (t % 45 == 0 ? 2 : 4);
			for (int r = r0; r <= r1; r++) {
				int px = cx + (int)Math.round(Math.sin(rad) * r), py = cy - (int)Math.round(Math.cos(rad) * r);
				graphics.fill(px, py, px + 1, py + 1, BRASS_DARK);
			}
		}
		// the gates already caught, as notches on the rim
		for (int i = 0; i < this.gate && i < this.gates.length; i++) {
			double rad = Math.toRadians(this.gates[i] - this.angle);
			int px = cx + (int)Math.round(Math.sin(rad) * (DIAL_R + 3)), py = cy - (int)Math.round(Math.cos(rad) * (DIAL_R + 3));
			graphics.fill(px - 1, py - 1, px + 2, py + 2, SET);
		}
		// the pick, set in at the top, trembling the nearer the gate
		float feel = this.picked >= 0 ? 0.0F : feel();
		int j = feel > 0.0F ? Math.round((this.shake.nextFloat() * 2.0F - 1.0F) * 2.0F * feel) : 0;
		int pickColor = this.lock.feel() && feel >= 1.0F ? SET : STEEL;
		graphics.fill(cx - 1 + j, cy - DIAL_R - 10, cx + 1 + j, cy - DIAL_R + 6, pickColor);
		graphics.fill(cx - 3 + j, cy - DIAL_R - 12, cx + 3 + j, cy - DIAL_R - 10, pickColor);
		// the hub and its arrow: which way to turn now
		graphics.fill(cx - 4, cy - 4, cx + 4, cy + 4, STEEL_DARK);
		if (this.picked < 0) {
			Component turn = Component.translatable(direction() > 0 ? "gui.jaymoskingdoms.lock.turn.right" : "gui.jaymoskingdoms.lock.turn.left");
			graphics.centeredText(this.font, turn, cx, cy + DIAL_R + 6, Ui.TEXT);
		}
		graphics.centeredText(this.font, this.gate + " / " + this.gates.length, cx, cy - 4, this.picked >= 0 ? SET : Ui.TEXT);
	}

	private int left() {
		return (this.width - W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - H) / 2);
	}
}
