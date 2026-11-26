package com.jaymospowers.client.armory;

import com.jaymospowers.armory.BodyWeapons;
import com.jaymospowers.armory.WeaponWheel;
import com.jaymospowers.baubles.Baubles;
import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.client.UiStyle;
import com.jaymospowers.compat.ToolBeltCompat;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The weapon wheel (Weapon Wheel's idea, CC0, by henalne; the server side is {@link WeaponWheel}). The 0 key, held: a
 * ring of eight kinds, clockwise from the top (blades, axes, long arms, pistols, bare fists, the off hand's shields and
 * arrows, tools, the worn tool belt), each showing one of what the body carries of that kind: the hotbar's weapons and tools, the back slot's
 * item and the belt's tools. Point at a kind and let go (or click) to bring it to hand; Q and E, or the mouse wheel,
 * step through a kind with more than one. A quick tap leaves the wheel open until a click, the key again, or Escape. In
 * the middle, the pick's name, where it's carried and how worn it is. It doesn't pause, and the world stays in view.
 * <p>The key is 0: every key the left hand reaches is already a power's, a Kingdoms key or vanilla's (the mod's own Tab is
 * the player list), and 0 sits at the end of the hotbar's number row.
 */
public final class WeaponWheelScreen extends Screen {
	private static final int RADIUS = 70;
	private static final int TILE = 28;
	private static final int DEAD_ZONE = 26;
	private static final int ACCENT = UiStyle.ACCENT;
	private static final int INFO_W = 220;
	private static final int INFO_H = 30;
	/** A press shorter than this (ticks) is a tap: the wheel stays open until a click. */
	private static final int HOLD_TICKS = 5;
	/** Below this share of durability left the number shows red, below the second yellow. */
	private static final float WORN_RED = 0.2F, WORN_YELLOW = 0.5F;

	/** The ring's kinds, clockwise from the top. */
	private enum Slice {
		BLADE(BodyWeapons.Kind.BLADE), AXE(BodyWeapons.Kind.AXE), LONG(BodyWeapons.Kind.LONG), PISTOL(BodyWeapons.Kind.PISTOL),
		FISTS(null), SHIELD(BodyWeapons.Kind.SHIELD), TOOL(BodyWeapons.Kind.TOOL), BELT(null);

		final BodyWeapons.@Nullable Kind kind;

		Slice(BodyWeapons.@Nullable Kind kind) {
			this.kind = kind;
		}

		Component label() {
			return Component.translatable("screen.jaymospowers.weapon_wheel." + name().toLowerCase(java.util.Locale.ROOT));
		}
	}

	private static final int SLICES = Slice.values().length;
	private static final int SLICE_DEGREES = 360 / SLICES;

	/** One thing on the wheel: where it is ({@link WeaponWheel#HOTBAR} etc.), which slot, and a copy of it. */
	private record Entry(int source, int index, ItemStack stack) {
	}

	private static KeyMapping key;

	private final List<List<Entry>> entries = new ArrayList<>();
	private final int[] variant = new int[SLICES];
	private int selected = -1;
	private int heldTicks;
	private boolean tapped;
	private boolean chosen;

	private WeaponWheelScreen() {
		super(Component.translatable("screen.jaymospowers.weapon_wheel"));
	}

	/** Called once from {@code JaymosPowersClient}: the wheel's key, 0. */
	public static void register() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.weapon_wheel", InputConstants.KEY_0, JaymosPowersClient.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			boolean pressed = false;
			while (key.consumeClick()) {
				pressed = true;
			}
			LocalPlayer player = minecraft.player;
			if (pressed && player != null && minecraft.gui.screen() == null && !player.isSpectator() && player.isAlive()
				&& ClientPlayNetworking.canSend(WeaponWheel.Pick.TYPE)) {
				minecraft.gui.setScreen(new WeaponWheelScreen());
			}
		});
	}

	@Override
	protected void init() {
		entries.clear();
		for (int i = 0; i < SLICES; i++) {
			entries.add(new ArrayList<>());
		}
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			add(WeaponWheel.HOTBAR, slot, inventory.getItem(slot));
		}
		add(WeaponWheel.BACK, 0, Baubles.back(player));
		List<ItemStack> tools = ToolBeltCompat.tools(player);
		for (int slot = 0; slot < tools.size(); slot++) {
			if (!tools.get(slot).isEmpty()) {
				entries.get(Slice.BELT.ordinal()).add(new Entry(WeaponWheel.BELT, slot, tools.get(slot)));
			}
		}
		entries.get(Slice.FISTS.ordinal()).add(new Entry(WeaponWheel.FISTS, 0, ItemStack.EMPTY));
		// each kind starts on what's in hand, if it's that kind
		int held = inventory.getSelectedSlot();
		for (int s = 0; s < SLICES; s++) {
			List<Entry> list = entries.get(s);
			for (int i = 0; i < list.size(); i++) {
				if (list.get(i).source() == WeaponWheel.HOTBAR && list.get(i).index() == held) {
					variant[s] = i;
				}
			}
		}
	}

	private void add(int source, int index, ItemStack stack) {
		BodyWeapons.Kind kind = stack.is(net.minecraft.tags.ItemTags.ARROWS) ? BodyWeapons.Kind.SHIELD : BodyWeapons.kind(stack);
		if (kind == null) {
			return;
		}
		for (Slice slice : Slice.values()) {
			if (slice.kind == kind) {
				entries.get(slice.ordinal()).add(new Entry(source, index, stack.copy()));
			}
		}
	}

	private @Nullable Entry current(int slice) {
		List<Entry> list = slice < 0 || slice >= entries.size() ? List.of() : entries.get(slice);
		return list.isEmpty() ? null : list.get(Math.floorMod(variant[slice], list.size()));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		// no blur: the world stays in view
	}

	@Override
	public void tick() {
		LocalPlayer player = minecraft.player;
		if (player == null || !player.isAlive() || player.isSpectator()) {
			onClose();
			return;
		}
		if (tapped) {
			return;
		}
		// polled, in case the release went elsewhere: a hold picks on letting go, a tap stays open
		InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(key);
		boolean down = bound.getType() == InputConstants.Type.KEYBOARD && InputConstants.isKeyDown(bound.getValue());
		if (down) {
			heldTicks++;
		} else {
			released();
		}
	}

	private void released() {
		if (heldTicks >= HOLD_TICKS) {
			choose();
		} else {
			tapped = true;
		}
	}

	@Override
	public boolean keyPressed(final KeyEvent event) {
		if (key.matches(event)) {
			if (tapped) {
				choose();
			}
			return true;
		}
		if (event.key() == InputConstants.KEY_E || event.key() == InputConstants.KEY_Q) {
			step(event.key() == InputConstants.KEY_E ? 1 : -1);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(final KeyEvent event) {
		if (key.matches(event)) {
			if (!tapped) {
				released();
			}
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean mouseScrolled(final double x, final double y, final double scrollX, final double scrollY) {
		if (scrollY != 0.0) {
			step(scrollY < 0.0 ? 1 : -1);
		}
		return true;
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		choose();
		return true;
	}

	/** Steps through the pointed-at kind's things. */
	private void step(int by) {
		if (selected < 0 || entries.get(selected).size() < 2) {
			return;
		}
		variant[selected] = Math.floorMod(variant[selected] + by, entries.get(selected).size());
		LocalPlayer player = minecraft.player;
		if (player != null) {
			player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS,
				0.15F, 1.9F, false);
		}
	}

	private void choose() {
		if (chosen) {
			return;
		}
		chosen = true;
		Entry entry = current(selected);
		if (entry != null && !inHand(entry)) {
			ClientPlayNetworking.send(new WeaponWheel.Pick(entry.source(), entry.index()));
		}
		onClose();
	}

	/** Whether {@code entry} is already where the pick would put it. */
	private boolean inHand(Entry entry) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return true;
		}
		if (entry.source() == WeaponWheel.FISTS) {
			return player.getMainHandItem().isEmpty();
		}
		return entry.source() == WeaponWheel.HOTBAR && entry.index() == player.getInventory().getSelectedSlot()
			&& !WeaponWheel.offHand(entry.stack());
	}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		LocalPlayer player = minecraft.player;
		if (player == null || entries.isEmpty()) {
			return;
		}
		int cx = width / 2;
		int cy = height / 2;
		int dx = mouseX - cx;
		int dy = mouseY - cy;
		int before = selected;
		if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) {
			selected = -1;
		} else {
			double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90.0;
			selected = Math.floorMod((int)Math.round(angle / SLICE_DEGREES), SLICES);
		}
		if (selected != before && selected >= 0) {
			player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS,
				0.15F, 1.6F + selected * 0.04F, false);
		}

		int outer = RADIUS + TILE / 2 + 6;
		UiStyle.wheel(graphics, cx, cy, RADIUS, TILE);
		if (selected >= 0) {
			UiStyle.wheelPointer(graphics, cx, cy, RADIUS, TILE, Math.atan2(dy, dx), ACCENT);
		}
		for (int i = 0; i < SLICES; i++) {
			double t = Math.toRadians(i * SLICE_DEGREES - 90.0);
			int x = cx + (int)Math.round(Math.cos(t) * RADIUS) - TILE / 2;
			int y = cy + (int)Math.round(Math.sin(t) * RADIUS) - TILE / 2;
			Entry entry = current(i);
			boolean picked = i == selected;
			boolean held = entry != null && inHand(entry);
			UiStyle.wheelTile(graphics, x, y, TILE, i, picked, picked ? ACCENT : held ? 0xA0000000 | (ACCENT & 0xFFFFFF) : entry == null ? UiStyle.ROW_ALT : 0x60FFFFFF);
			if (entry == null || entry.stack().isEmpty()) {
				if (i == Slice.FISTS.ordinal() || picked) {
					UiStyle.lineCentred(graphics, font, Slice.values()[i].label(), x + TILE / 2, y + TILE / 2 - 4, TILE - 2, entry == null ? UiStyle.HINT : UiStyle.DIM);
				}
			} else {
				graphics.item(entry.stack(), x + (TILE - 16) / 2, y + (TILE - 16) / 2);
				graphics.itemDecorations(font, entry.stack(), x + (TILE - 16) / 2, y + (TILE - 16) / 2);
				int count = entries.get(i).size();
				if (count > 1) {
					String more = Integer.toString(count);
					graphics.text(font, more, x + TILE - font.width(more) - 1, y + 1, UiStyle.DIM);
				}
			}
			if (held) {
				graphics.fill(x + 2, y + TILE - 3, x + TILE - 2, y + TILE - 1, ACCENT);
			}
		}

		// the middle: the pick's name, Q/E through its kind, and how worn it is
		Entry entry = current(selected);
		ItemStack stack = entry == null ? ItemStack.EMPTY : entry.stack();
		int room = 2 * UiStyle.wheelInner(RADIUS, TILE) - 8;
		if (!stack.isEmpty()) {
			UiStyle.lineCentred(graphics, font, stack.getHoverName(), cx, cy - 14, room, UiStyle.TEXT);
			int count = entries.get(selected).size();
			if (count > 1) {
				UiStyle.lineCentred(graphics, font, Component.translatable("screen.jaymospowers.weapon_wheel.step",
					Math.floorMod(variant[selected], count) + 1, count), cx, cy - 3, room, UiStyle.DIM);
			}
			if (stack.isDamageableItem()) {
				float left = 1.0F - stack.getDamageValue() / (float)Math.max(1, stack.getMaxDamage());
				int color = left < WORN_RED ? UiStyle.BAD : left < WORN_YELLOW ? UiStyle.GOLD : UiStyle.GOOD;
				UiStyle.lineCentred(graphics, font, Component.literal(Math.round(left * 100.0F) + "%"), cx, cy + 8, room, color);
			} else if (stack.getCount() > 1) {
				UiStyle.lineCentred(graphics, font, Component.literal("x" + stack.getCount()), cx, cy + 8, room, UiStyle.TEXT);
			}
		} else if (selected >= 0) {
			UiStyle.lineCentred(graphics, font, Slice.values()[selected].label(), cx, cy - 4, room, entry == null ? UiStyle.HINT : UiStyle.TEXT);
		}

		// under the ring: what the pick is and where it comes from
		int ix = cx - INFO_W / 2;
		int iy = Math.min(cy + outer + 4, height - 4 - INFO_H);
		UiStyle.panel(graphics, ix, iy, INFO_W, INFO_H, ACCENT);
		Component name = selected < 0 ? title : stack.isEmpty() ? Slice.values()[selected].label() : stack.getHoverName();
		UiStyle.line(graphics, font, name, ix + 8, iy + 6, INFO_W - 16, ACCENT);
		Component status;
		if (selected < 0) {
			status = Component.translatable("screen.jaymospowers.weapon_wheel.hint");
		} else if (entry == null) {
			status = Component.translatable(selected == Slice.BELT.ordinal() && !ToolBeltCompat.wears(player)
				? "screen.jaymospowers.weapon_wheel.no_belt" : "screen.jaymospowers.weapon_wheel.none");
		} else if (inHand(entry)) {
			status = Component.translatable("screen.jaymospowers.weapon_wheel.in_hand");
		} else {
			status = switch (entry.source()) {
				case WeaponWheel.BACK -> Component.translatable(WeaponWheel.offHand(stack)
					? "screen.jaymospowers.weapon_wheel.from_back_off_hand" : "screen.jaymospowers.weapon_wheel.from_back");
				case WeaponWheel.BELT -> Component.translatable("screen.jaymospowers.weapon_wheel.from_belt");
				case WeaponWheel.FISTS -> Component.translatable("screen.jaymospowers.weapon_wheel.put_away");
				default -> Component.translatable(WeaponWheel.offHand(stack)
					? "screen.jaymospowers.weapon_wheel.off_hand" : "screen.jaymospowers.weapon_wheel.from_hotbar", entry.index() + 1);
			};
		}
		UiStyle.line(graphics, font, status, ix + 8, iy + 17, INFO_W - 16, selected >= 0 ? UiStyle.DIM : UiStyle.HINT);
	}
}
