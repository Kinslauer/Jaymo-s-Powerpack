package com.jaymospowers.client.rig;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.rig.Rig;
import com.jaymospowers.rig.RigForms;
import com.jaymospowers.rig.RigHelm;
import com.jaymospowers.rig.RigNet;
import com.jaymospowers.rig.RigOcto;
import com.jaymospowers.rig.RigPicks;
import com.jaymospowers.rig.RigScan;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Locale;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * (0.11.110) The Rig's radial menu (Andy: "make j menu radial"): hold Sneak + J, point the mouse toward an entry, let go
 * (or click). An active (Hover, Palm Blast, Launcher, Unibeam, Scan, Suit) becomes what a tap of J uses, and is used
 * now; the switches (Faceplate, Night Vision, the flight helpers...) just flip. Like the symbiote's menu, it doesn't pause.
 * <p>
 * Two rings (Andy: "the shift J menu is way too cluttered"; it had grown to 43 tiles on one ring, 52 with the Octo
 * Core). The inner ring holds the groups ({@link #GROUPS}): Flight, Blasts, Guns, Arm Forms, Back Rig, Helm, Suit and
 * Drones, and the Octo Arms while an Octo Core is worn. Point at a group and its entries fan out on the outer ring
 * around it; carry on out to one and let go. Every entry is still there, each in one group. A group's tile has a lit rim
 * when it holds what J uses now, and a lit strip when one of its switches is on.
 */
final class RigMenuScreen extends Screen {
	/** Ring radius, tile size, and the dead zone in the middle (no pick). */
	private static final int RADIUS = 96;
	/** The ring and tile sizes in use: the full ones, shrunk when the screen is too short to hold them. */
	private int radius = RADIUS, tile = TILE;
	private static final int TILE = 26;
	private static final int DEAD_ZONE = 24;
	private static final int ACCENT = 0xFF5FE6FF;
	/** The panel under the ring that names the pick and says what's stopping it. */
	private static final int INFO_W = 200;
	private static final int INFO_H = 30;

	/** The entries, clockwise from the top. */
	private static final int[] ACTIONS = {RigNet.HOVER, RigNet.PALM, RigNet.LAUNCHER, RigNet.UNIBEAM, RigNet.SCAN, RigNet.CALL, RigNet.SENTRY,
		RigNet.FACEPLATE, RigNet.NIGHT, RigPicks.DRONES, RigPicks.MARK, RigPicks.TABLE, RigPicks.DECOY, RigPicks.FIELD, RigPicks.BANK, RigClient.JET_START,
		RigPicks.DASH, RigPicks.BARRAGE, RigClient.SLOW_BEAT, RigPicks.FAILSAFE, RigPicks.AF_BLADE, RigPicks.AF_MACE, RigPicks.AF_PILE,
		RigPicks.AF_DRILL, RigPicks.AF_LANCE, RigPicks.AF_LASH, RigPicks.AF_MIMIC,
		// the back rig (RigBack)
		com.jaymospowers.rig.RigBack.WINGSLASH, com.jaymospowers.rig.RigBack.SPEARS, com.jaymospowers.rig.RigBack.PINCER,
		com.jaymospowers.rig.RigBack.GRAPPLE, com.jaymospowers.rig.RigBack.HORNS,
		// the guns (RigGuns)
		RigNet.PALM_CHARGE, RigNet.GATLING, RigNet.RAILGUN, RigNet.SONIC, RigNet.TWIN, RigNet.SIEGE,
		// flight actives
		RigPicks.PULLUP, RigPicks.HYPER, RigPicks.BANK_TURN, RigPicks.METEOR, RigPicks.SMOOTH,
		// the Octo Core's arms (RigOcto): shown only while one is worn
		RigOcto.ARMS, RigOcto.GRAB, RigOcto.SKEWER, RigOcto.PINWHEEL, RigOcto.VAULT, RigOcto.ANCHOR, RigOcto.WATCH, RigOcto.WALK, RigOcto.COMBO};
	private static final String[] NAMES = {"hover", "palm", "launcher", "unibeam", "scan", "call", "sentry", "faceplate", "night", "drones", "mark", "table", "decoy", "field", "bank", "jet_start", "dash", "barrage",
		"slow_beat", "failsafe", "blade", "mace", "pile", "drill", "lance", "lash", "mimic", "wingslash", "spears", "pincer", "grapple", "horns",
		"palm_charge", "gatling", "railgun", "sonic", "twin", "siege", "pullup", "hyper", "bank_turn", "meteor", "smooth",
		"octo_arms", "octo_grab", "octo_skewer", "octo_pinwheel", "octo_vault", "octo_anchor", "octo_watch", "octo_walk", "octo_combo"};
	private static final int[] LEVEL = {1, 1, 1, Rig.UNIBEAM_LEVEL, Rig.SCAN_LEVEL, Rig.CALL_LEVEL, Rig.CALL_LEVEL, 1, 1, 1, 1, 1, 1, 1, 1, Rig.FLIGHT_LEVEL, 1, Rig.MAX_LEVEL, Rig.UNIBEAM_LEVEL, 1,
		RigForms.BLADE_LEVEL, RigForms.MACE_LEVEL, RigForms.PILE_LEVEL, RigForms.DRILL_LEVEL, RigForms.LANCE_LEVEL, RigForms.LASH_LEVEL, RigForms.MIMIC_LEVEL,
		3, 2, 3, 2, 4,
		Rig.LOCK_LEVEL, Rig.SCAN_LEVEL, Rig.UNIBEAM_LEVEL, Rig.UNIBEAM_LEVEL, Rig.MAX_LEVEL, Rig.MAX_LEVEL,
		Rig.FLIGHT_LEVEL, Rig.FLIGHT_LEVEL, Rig.FLIGHT_LEVEL, Rig.FLIGHT_LEVEL, Rig.FLIGHT_LEVEL,
		RigOcto.ARMS_LEVEL, RigOcto.GRAB_LEVEL, RigOcto.SKEWER_LEVEL, RigOcto.PINWHEEL_LEVEL, RigOcto.VAULT_LEVEL, RigOcto.ANCHOR_LEVEL, RigOcto.WATCH_LEVEL,
		RigOcto.WALK_LEVEL, RigOcto.COMBO_LEVEL};
	/** Cooldowns' full lengths (ticks), for the shading; 0 for none. */
	private static final int[] COOLDOWN = {0, RigHud.PALM_COOLDOWN, RigHud.LAUNCHER_COOLDOWN, RigHud.UNIBEAM_COOLDOWN, RigScan.SCAN_COOLDOWN, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
		RigForms.BLADE_COOLDOWN, RigForms.MACE_COOLDOWN, RigForms.PILE_COOLDOWN, RigForms.DRILL_COOLDOWN, RigForms.LANCE_COOLDOWN, RigForms.LASH_COOLDOWN, RigForms.MIMIC_COOLDOWN,
		0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 0, 0, 0, 0, 0, 0};
	/**
	 * The groups on the inner ring, clockwise from the top: a lang key (gui.jaymospowers.rig_menu.group.*), the icon, and
	 * its entries by {@link #NAMES}. Every entry is in exactly one group (checked when the class loads).
	 */
	private record Group(String key, Item icon, int[] entries) {
	}

	private static final Group[] GROUPS = {
		groupOf("flight", Items.ELYTRA, "hover", "jet_start", "dash", "pullup", "hyper", "bank_turn", "meteor", "smooth"),
		groupOf("blasts", Items.FIREWORK_ROCKET, "palm", "launcher", "barrage", "unibeam", "slow_beat"),
		groupOf("guns", Items.CROSSBOW, "palm_charge", "gatling", "railgun", "sonic", "twin", "siege"),
		groupOf("forms", Items.IRON_SWORD, "blade", "mace", "pile", "drill", "lance", "lash", "mimic"),
		groupOf("back", Items.PHANTOM_MEMBRANE, "wingslash", "spears", "pincer", "grapple", "horns"),
		groupOf("helm", Items.SPYGLASS, "faceplate", "night", "scan", "mark", "table", "bank"),
		groupOf("suit", Items.ARMOR_STAND, "call", "sentry", "failsafe", "drones", "decoy", "field"),
		groupOf("octo", Items.PISTON, "octo_arms", "octo_grab", "octo_skewer", "octo_pinwheel", "octo_vault", "octo_anchor", "octo_watch", "octo_walk", "octo_combo")};

	static {
		int[] seen = new int[NAMES.length];
		for (Group g : GROUPS) {
			for (int i : g.entries()) {
				seen[i]++;
			}
		}
		for (int i = 0; i < seen.length; i++) {
			if (seen[i] != 1) {
				throw new IllegalStateException("Rig menu entry " + NAMES[i] + " is in " + seen[i] + " groups");
			}
		}
	}

	private static Group groupOf(String key, Item icon, String... names) {
		int[] entries = new int[names.length];
		for (int k = 0; k < names.length; k++) {
			entries[k] = java.util.Arrays.asList(NAMES).indexOf(names[k]);
			if (entries[k] < 0) {
				throw new IllegalStateException("No Rig menu entry " + names[k]);
			}
		}
		return new Group(key, icon, entries);
	}

	/** The groups on the inner ring now (indices into {@link #GROUPS}): the Octo Arms only while an Octo Core is worn. */
	private int[] shown = new int[0];
	private double slice = 360.0;
	/** The inner ring's radius: the groups' tiles. ({@link #radius} is the outer ring's: the entries'.) */
	private int inner = RADIUS / 2;

	/** The group pointed at (index into {@link #GROUPS}), or -1; and the entry (index into {@link #ACTIONS}), or -1. */
	private int group = -1;
	private int selected = -1;
	private boolean chosen;

	RigMenuScreen() {
		super(Component.translatable("screen.jaymospowers.rig_menu"));
		LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
		boolean octo = player != null && RigOcto.wears(player);
		int[] list = new int[GROUPS.length];
		int n = 0;
		for (int g = 0; g < GROUPS.length; g++) {
			if (octo || !"octo".equals(GROUPS[g].key())) {
				list[n++] = g;
			}
		}
		this.shown = java.util.Arrays.copyOf(list, n);
		this.slice = 360.0 / Math.max(1, n);
	}

	/** The inner-ring slot of this group (its place clockwise from the top), or -1 if it isn't shown. */
	private int slot(int g) {
		for (int k = 0; k < shown.length; k++) {
			if (shown[k] == g) {
				return k;
			}
		}
		return -1;
	}

	/** A group's angle (radians, 0 = right, clockwise), from its slot on the inner ring. */
	private double groupAngle(int g) {
		return Math.toRadians(slot(g) * slice - 90.0);
	}

	/** The angle between the outer ring's entries (radians): a tile and a small gap along the ring. */
	private double step() {
		return (tile + 4) / (double)radius;
	}

	/** The angle of the {@code k}th of a group's {@code n} entries: fanned out on the outer ring, centred on the group. */
	private double entryAngle(int g, int k, int n) {
		return groupAngle(g) + (k - (n - 1) / 2.0) * step();
	}

	/** The group whose slice holds this angle (radians, as {@link Math#atan2}). */
	private int groupAt(double angle) {
		if (shown.length == 0) {
			return -1;
		}
		double degrees = Math.toDegrees(angle) + 90.0;
		return shown[Math.floorMod((int)Math.round(degrees / slice), shown.length)];
	}

	/** The shortest turn between two angles (radians), 0 to pi. */
	private static double apart(double a, double b) {
		double d = Math.abs(Math.IEEEremainder(a - b, Math.PI * 2.0));
		return Math.min(d, Math.PI * 2.0 - d);
	}

	private static Item icon(int i) {
		return switch (i) {
			case 0 -> Items.FEATHER;
			case 1 -> Items.GLOWSTONE_DUST;
			case 2 -> Items.FIREWORK_ROCKET;
			case 3 -> Items.BEACON;
			case 4 -> Items.SPYGLASS;
			case 5 -> Items.ARMOR_STAND;
			case 6 -> Items.SHIELD;
			case 7 -> Rig.HELM != null ? Rig.HELM : Items.IRON_HELMET;
			case 9 -> Items.COPPER_INGOT;
			case 10 -> Items.COMPASS;
			case 11 -> Items.CLOCK;
			case 12 -> Items.PLAYER_HEAD;
			case 13 -> Items.GOLD_INGOT;
			case 14 -> Items.ARROW;
			case 15 -> Items.ELYTRA;
			case 16 -> Items.BLAZE_POWDER;
			case 17 -> Items.FIRE_CHARGE;
			case 18 -> Items.CLOCK;
			case 19 -> Items.TOTEM_OF_UNDYING;
			case 20 -> Items.IRON_SWORD;
			case 21 -> Items.IRON_AXE;
			case 22 -> Items.IRON_PICKAXE;
			case 23 -> Items.BLAZE_ROD;
			case 24 -> Items.TRIDENT;
			case 25 -> Items.FISHING_ROD;
			case 26 -> Items.BOOK;
			case 27 -> Items.PHANTOM_MEMBRANE;
			case 28 -> Items.TRIDENT;
			case 29 -> Items.LEAD;
			case 30 -> Items.FISHING_ROD;
			case 31 -> Items.BLAZE_ROD;
			case 32 -> Items.REDSTONE;
			case 33 -> Items.IRON_INGOT;
			case 34 -> Items.DIAMOND;
			case 35 -> Items.NOTE_BLOCK;
			case 36 -> Items.NETHER_STAR;
			case 37 -> Items.OBSIDIAN;
			case 38 -> Items.REDSTONE;
			case 39 -> Items.NETHER_STAR;
			case 40 -> Items.FEATHER;
			case 41 -> Items.FIRE_CHARGE;
			case 42 -> Items.CLOCK;
			// the Octo Core's arms
			case 43 -> Items.PISTON;
			case 44 -> Items.STICKY_PISTON;
			case 45 -> Items.POINTED_DRIPSTONE;
			case 46 -> Items.BREEZE_ROD;
			case 47 -> Items.RABBIT_FOOT;
			case 48 -> Items.TRIPWIRE_HOOK;
			case 49 -> Items.OBSERVER;
			case 50 -> Items.SCAFFOLDING;
			case 51 -> Items.BLAZE_POWDER;
			default -> Items.ENDER_EYE;
		};
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
		// let go of the key: use what's picked (polled, in case the release went elsewhere)
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(RigClient.rigKey());
		if (key.getType() == InputConstants.Type.KEYBOARD && !InputConstants.isKeyDown(key.getValue())) {
			choose();
		}
		LocalPlayer player = minecraft.player;
		if (player == null || !player.isAlive() || !Rig.wearsAny(player)) {
			onClose();
		}
	}

	@Override
	public boolean keyReleased(final KeyEvent event) {
		if (RigClient.rigKey().matches(event)) {
			choose();
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
		if (selected < 0 && group >= 0) {
			// a click on a group just keeps it open: its entries are already fanned out
			return true;
		}
		choose();
		return true;
	}

	private void choose() {
		if (chosen) {
			return;
		}
		chosen = true;
		LocalPlayer player = minecraft.player;
		if (selected >= 0 && player != null && blocker(player, selected) == null) {
			RigClient.pick(ACTIONS[selected]);
		}
		onClose();
	}

	/** Why this entry can't be used now, or null if it can. */
		private static Component blocker(LocalPlayer player, int i) {
			int level = Rig.level(player);
			if (level < LEVEL[i]) {
				return Component.translatable("gui.jaymospowers.rig_menu.level", LEVEL[i]);
			}
			int action = ACTIONS[i];
			boolean helm = Rig.wears(player, Rig.Piece.HELM);
			boolean core = !Rig.core(player).isEmpty();
			if (RigOcto.is(action) && !RigOcto.wears(player)) {
				return Component.translatable("gui.jaymospowers.rig_menu.no_octo");
			}
			if (RigOcto.is(action) && action != RigOcto.ARMS && !RigOcto.out(player)) {
				return Component.translatable("gui.jaymospowers.rig_menu.arms_in");
			}
			if (action == RigNet.FACEPLATE && !helm) {
				return Component.translatable("gui.jaymospowers.rig_menu.no_helm");
			}
			if ((action == RigNet.NIGHT || action == RigNet.SCAN) && (!helm || !core)) {
				return Component.translatable("gui.jaymospowers.rig_menu.no_helm_core");
			}
			if ((action == RigNet.CALL || action == RigNet.SENTRY || action == RigPicks.DRONES || action == RigPicks.DECOY || action == RigPicks.FIELD) && !core) {
				return Component.translatable("gui.jaymospowers.rig_menu.no_core");
			}
			if ((action == RigNet.HOVER || action == RigNet.PALM || action == RigNet.LAUNCHER || action == RigNet.UNIBEAM || action == RigPicks.DASH
				|| action == RigPicks.BARRAGE || com.jaymospowers.rig.RigBack.is(action) || action == RigPicks.PULLUP || action == RigPicks.HYPER || action == RigPicks.BANK_TURN
				|| action == RigPicks.METEOR || action == RigPicks.SMOOTH) && !Rig.fullSet(player)) {
				return Component.translatable("gui.jaymospowers.rig_menu.incomplete");
			}
			return null;
		}

	/** Ticks left on this entry's cooldown. */
	private static int left(int i) {
		RigNet.Status status = RigClient.status;
		return switch (ACTIONS[i]) {
			case RigNet.PALM -> status.palmCooldown();
			case RigNet.LAUNCHER -> status.launcherCooldown();
			case RigNet.UNIBEAM -> status.unibeamCooldown();
			case RigNet.SCAN -> status.scanCooldown();
			default -> 0;
		};
	}

	/** Whether this entry is switched on (hovering, the faceplate up, night vision on). */
		private static boolean on(LocalPlayer player, int i) {
			return switch (ACTIONS[i]) {
				case RigNet.HOVER -> RigClient.status.hovering();
				case RigNet.FACEPLATE -> RigHelm.faceUp(player);
				case RigNet.NIGHT -> RigHelm.night(player);
				case RigClient.JET_START -> RigFlightFeel.jetStart();
				case RigClient.SLOW_BEAT -> RigImpact.slowBeat();
				case RigPicks.FAILSAFE -> com.jaymospowers.rig.RigCall.failsafeOn(player);
				case RigPicks.PULLUP -> RigClient.status.pullup();
				case RigPicks.HYPER -> RigClient.status.hyper();
				case RigPicks.BANK_TURN -> RigClient.status.banking();
				case RigPicks.SMOOTH -> RigClient.status.smoothOthers();
				case RigOcto.ARMS -> RigOcto.out(player);
				case RigOcto.WATCH -> RigOcto.watching(player);
				case RigOcto.WALK -> RigOcto.walking(player);
				default -> false;
			};
		}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		int room = height - INFO_H - 8;
		tile = Math.max(16, Math.min(TILE, (int)(Math.min(RADIUS, room / 2 - 19) * 0.4) - 1));
		radius = Math.max(DEAD_ZONE + 2 * tile + 8, Math.min(RADIUS, room / 2 - tile / 2 - 6));
		inner = Math.max(DEAD_ZONE + tile / 2 + 4, radius / 2);
		int cx = width / 2;
		int cy = height / 2;
		int dx = mouseX - cx;
		int dy = mouseY - cy;
		double angle = Math.atan2(dy, dx);
		int dist2 = dx * dx + dy * dy;
		int split = (inner + radius) / 2;
		int beforeGroup = group, before = selected;
		if (dist2 < DEAD_ZONE * DEAD_ZONE) {
			group = -1;
			selected = -1;
		} else if (dist2 < split * split) {
			// on the inner ring: the group under the pointer, no entry yet
			group = groupAt(angle);
			selected = -1;
		} else {
			// out past it: the group stays, unless the pointer has swung well clear of its fan (or came straight out)
			if (group >= 0) {
				int n = GROUPS[group].entries().length;
				if (apart(angle, groupAngle(group)) > (n / 2.0 + 1.0) * step()) {
					group = groupAt(angle);
				}
			} else {
				group = groupAt(angle);
			}
			selected = -1;
			if (group >= 0) {
				int[] entries = GROUPS[group].entries();
				double best = Double.MAX_VALUE;
				for (int k = 0; k < entries.length; k++) {
					double d = apart(angle, entryAngle(group, k, entries.length));
					if (d < best) {
						best = d;
						selected = entries[k];
					}
				}
			}
		}
		if ((selected != before && selected >= 0) || (group != beforeGroup && group >= 0)) {
			player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS,
				0.15F, selected >= 0 ? 1.6F + selected * 0.01F : 1.2F + group * 0.05F, false);
		}

		// the dark disc out to the entries, a darker hub under the groups, and the pointer
		int outer = radius + tile / 2 + 6;
		UiStyle.wheel(graphics, cx, cy, radius, tile);
		UiStyle.wheel(graphics, cx, cy, inner, tile);
		if (selected >= 0) {
			UiStyle.wheelPointer(graphics, cx, cy, radius, tile, angle, ACCENT);
		} else if (group >= 0) {
			UiStyle.wheelPointer(graphics, cx, cy, inner, tile, angle, ACCENT);
		}

		// the groups
		for (int k = 0; k < shown.length; k++) {
			int g = shown[k];
			double t = Math.toRadians(k * slice - 90.0);
			int x = cx + (int)Math.round(Math.cos(t) * inner) - tile / 2;
			int y = cy + (int)Math.round(Math.sin(t) * inner) - tile / 2;
			boolean open = g == group;
			boolean holdsJ = false, anyOn = false, anyReady = false;
			for (int i : GROUPS[g].entries()) {
				holdsJ |= ACTIONS[i] == RigClient.selected;
				anyOn |= on(player, i);
				anyReady |= blocker(player, i) == null;
			}
			UiStyle.wheelTile(graphics, x, y, tile, k, open, open ? ACCENT : holdsJ ? 0xA05FE6FF : anyReady ? 0x60FFFFFF : UiStyle.ROW_ALT);
			graphics.item(new ItemStack(GROUPS[g].icon()), x + (tile - 16) / 2, y + (tile - 16) / 2);
			if (!anyReady) {
				graphics.fill(x + 1, y + 1, x + tile - 1, y + tile - 1, 0x90000000);
			}
			if (anyOn) {
				graphics.fill(x + 2, y + tile - 3, x + tile - 2, y + tile - 1, ACCENT);
			}
		}

		// the open group's entries, fanned out on the outer ring around it
		if (group >= 0) {
			int[] entries = GROUPS[group].entries();
			for (int k = 0; k < entries.length; k++) {
				int i = entries[k];
				double t = entryAngle(group, k, entries.length);
				int x = cx + (int)Math.round(Math.cos(t) * radius) - tile / 2;
				int y = cy + (int)Math.round(Math.sin(t) * radius) - tile / 2;
				entryTile(graphics, player, i, k, x, y);
			}
		}

		// the middle: the group's name; under the rings, the entry (or group) and what it does or what's stopping it
		if (group >= 0) {
			UiStyle.lineCentred(graphics, font, Component.translatable("gui.jaymospowers.rig_menu.group." + GROUPS[group].key()), cx, cy - 4,
				2 * (inner - tile / 2) - 8, UiStyle.TEXT);
		}
		Component name = selected >= 0 ? Component.translatable("gui.jaymospowers.rig_menu." + NAMES[selected])
			: group >= 0 ? Component.translatable("gui.jaymospowers.rig_menu.group." + GROUPS[group].key()) : null;
		int ix = cx - INFO_W / 2;
		int iy = Math.min(cy + outer + 4, height - 4 - INFO_H);
		UiStyle.panel(graphics, ix, iy, INFO_W, INFO_H, ACCENT);
		UiStyle.line(graphics, font, name != null ? name : title, ix + 8, iy + 6, INFO_W - 16, ACCENT);
		UiStyle.line(graphics, font, status(player), ix + 8, iy + 17, INFO_W - 16, name != null ? UiStyle.DIM : UiStyle.HINT);
	}

	/** One entry's tile: its icon, cooldown shading, the Mind level it waits for, and a lit strip when it's on. */
	private void entryTile(GuiGraphicsExtractor graphics, LocalPlayer player, int i, int k, int x, int y) {
		int left = left(i);
		boolean blocked = blocker(player, i) != null;
		boolean picked = i == selected;
		boolean jKey = ACTIONS[i] == RigClient.selected;
		UiStyle.wheelTile(graphics, x, y, tile, k, picked, picked ? ACCENT : jKey ? 0xA05FE6FF : left <= 0 && !blocked ? 0x60FFFFFF : UiStyle.ROW_ALT);
		graphics.item(new ItemStack(icon(i)), x + (tile - 16) / 2, y + (tile - 16) / 2);
		if (left > 0 && COOLDOWN[i] > 0) {
			int shade = (int)Math.ceil((tile - 2) * Math.min(1.0, left / (double)COOLDOWN[i]));
			graphics.fill(x + 1, y + 1 + (tile - 2) - shade, x + tile - 1, y + tile - 1, 0xB0000000);
			String seconds = left >= 200 ? Integer.toString((left + 19) / 20) : String.format(Locale.ROOT, "%.1f", left / 20.0);
			graphics.text(font, seconds, x + tile / 2 - font.width(seconds) / 2, y + tile / 2 - 4, 0xFFFFFFFF);
		}
		if (Rig.level(player) < LEVEL[i]) {
			// not yet: nearly black, with the Mind level it waits for
			graphics.fill(x + 1, y + 1, x + tile - 1, y + tile - 1, 0xD8000000);
			String need = "Lv" + LEVEL[i];
			graphics.text(font, need, x + tile / 2 - font.width(need) / 2, y + tile / 2 - 4, 0xFF70A0B0);
		} else if (blocked) {
			graphics.fill(x + 1, y + 1, x + tile - 1, y + tile - 1, 0x90000000);
		}
		if (on(player, i)) {
			graphics.fill(x + 2, y + tile - 3, x + tile - 2, y + tile - 1, ACCENT);
		}
	}

	private Component status(LocalPlayer player) {
			if (selected < 0) {
				return Component.translatable(group >= 0 ? "gui.jaymospowers.rig_menu.group_hint" : "gui.jaymospowers.rig_menu.hint");
			}
			Component blocker = blocker(player, selected);
			if (blocker != null) {
				return blocker;
			}
			int left = left(selected);
			if (left > 0) {
				return Component.translatable("gui.jaymospowers.rig_menu.cooling", String.format(Locale.ROOT, "%.1f", left / 20.0));
			}
			if (ACTIONS[selected] == RigNet.FACEPLATE || ACTIONS[selected] == RigNet.NIGHT || ACTIONS[selected] == RigNet.HOVER || ACTIONS[selected] == RigClient.JET_START
				|| ACTIONS[selected] == RigClient.SLOW_BEAT || ACTIONS[selected] == RigPicks.FAILSAFE || ACTIONS[selected] == RigPicks.PULLUP || ACTIONS[selected] == RigPicks.HYPER
				|| ACTIONS[selected] == RigPicks.BANK_TURN || ACTIONS[selected] == RigPicks.SMOOTH || ACTIONS[selected] == RigOcto.ARMS
				|| ACTIONS[selected] == RigOcto.WATCH || ACTIONS[selected] == RigOcto.WALK) {
				return Component.translatable(on(player, selected) ? "gui.jaymospowers.rig_menu.on" : "gui.jaymospowers.rig_menu.off");
			}
			return Component.translatable("gui.jaymospowers.rig_menu.ready");
		}
}
