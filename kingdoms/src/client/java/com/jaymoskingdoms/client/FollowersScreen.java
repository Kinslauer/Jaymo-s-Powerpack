package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Followers;
import com.jaymoskingdoms.Formations;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Ranks;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.jaymoskingdoms.client.ui.FlatButton;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The orders screen ({@link Followers}): the Orders key (comma) with a villager of yours under the crosshair opens
 * theirs; with nobody there it speaks to everyone of yours near. Seven buttons, one an order; the server decides who
 * listens. (medieval picks) Beside them, the formation's ({@link Formations}): the four shapes, go where I look, hold,
 * follow, charge, set on my mark, break ranks. A ranked recruit's rank shows by their name ({@link Ranks}).
 */
public final class FollowersScreen extends Screen {
	/** The page is the roster's shape ({@link RosterLayout}): the list left, then the orders and the formation's in two columns. */
	private static final int COL_W = 104, ROW = 20, HEADING = 24, GAP = 6;
	/** The buttons, top to bottom. */
	private static final int[] ORDERS = {Followers.FOLLOW, Followers.FIGHT, Followers.HOLD, Followers.STAND, Followers.PATROL, Followers.CLEAR, Followers.FREE};
	/** The formation's buttons, top to bottom. */
	private static final int[] FORMATION = {Formations.LINE, Formations.WEDGE, Formations.SQUARE, Formations.COLUMN, Formations.PLACE, Formations.HOLD,
		Formations.FOLLOW, Formations.CHARGE, Formations.SET_ON, Formations.BREAK};
	private static @Nullable KeyMapping key;

	private final int target;
	private final Component who;

	private FollowersScreen(int target, Component who) {
		super(Component.translatable("gui.jaymoskingdoms.followers.title"));
		this.target = target;
		this.who = who;
	}

	static void register() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.follower_orders", InputConstants.KEY_COMMA,
			KeyMapping.Category.register(JaymosKingdoms.id("followers"))));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (key.consumeClick()) {
				if (minecraft.player != null && minecraft.gui.screen() == null && ClientPlayNetworking.canSend(Followers.Give.TYPE)) {
					if (!com.jaymoskingdoms.TreeLocks.check(minecraft.player, com.jaymoskingdoms.TreeLocks.Lock.ORDERS)) {
						// (Andy's skill-tree locks) the orders are learned: Command
						continue;
					}
					Villager aimed = aimed(minecraft);
					minecraft.gui.setScreen(aimed == null ? new FollowersScreen(-1, Component.translatable("gui.jaymoskingdoms.followers.all"))
						: new FollowersScreen(aimed.getId(), named(aimed)));
				}
			}
		});
	}

	/** Opens the orders for all your followers (the menu's page for them), if the server can take orders and you have learned them. */
	static void openAll() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null && ClientPlayNetworking.canSend(Followers.Give.TYPE)
			&& com.jaymoskingdoms.TreeLocks.check(minecraft.player, com.jaymoskingdoms.TreeLocks.Lock.ORDERS)) {
			minecraft.gui.setScreen(new FollowersScreen(-1, Component.translatable("gui.jaymoskingdoms.followers.all")));
		}
	}

	/** Opens the orders for {@code villager} (from MCA's menu, which Orders stands in for), if the server can take orders. */
	public static void open(Entity villager) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null && ClientPlayNetworking.canSend(Followers.Give.TYPE)
			&& com.jaymoskingdoms.TreeLocks.check(minecraft.player, com.jaymoskingdoms.TreeLocks.Lock.ORDERS)) {
			minecraft.gui.setScreen(new FollowersScreen(villager.getId(), named(villager)));
		}
	}

	/** {@code e}'s name, with their rank when they have one. */
	private static Component named(Entity e) {
		Integer rank = e.getAttached(Ranks.RANK);
		if (rank == null) {
			return e.getDisplayName();
		}
		String step = new String[] {"militia", "soldier", "knight", "veteran", "leader"}[Math.min(4, Ranks.step(rank))];
		return Component.translatable("gui.jaymoskingdoms.followers.ranked", e.getDisplayName(),
			Component.translatable("gui.jaymoskingdoms.rank." + (Ranks.bow(rank) ? "bow." : "") + step));
	}

	/** The villager under the crosshair within {@link Followers#ONE_RANGE} blocks, or null. */
	private static @Nullable Villager aimed(Minecraft minecraft) {
		Entity camera = minecraft.getCameraEntity();
		if (camera == null) {
			return null;
		}
		double reach = minecraft.player == null ? Followers.ONE_RANGE : com.jaymoskingdoms.Presence.reach(minecraft.player, Followers.ONE_RANGE);
		Vec3 eye = camera.getEyePosition(), end = eye.add(camera.getViewVector(1.0F).scale(reach));
		EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(camera, eye, end,
			camera.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0), e -> e instanceof Villager && e.isAlive(),
			reach * reach);
		return hit != null && hit.getEntity() instanceof Villager v ? v : null;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int height0() {
		return RosterLayout.HEAD + 6 + HEADING + Math.max(ORDERS.length, FORMATION.length) * ROW + 12;
	}

	private int width0() {
		return RosterLayout.PAD * 2 + RosterLayout.LIST_W + RosterLayout.GAP + COL_W * 2 + GAP + 16;
	}

	private int left0() {
		return MenuCompat.centre(this.width, width0());
	}

	private int top0() {
		return Math.max(4 + MenuCompat.STRIP, (this.height - height0()) / 2);
	}

	/** The first column's left edge: orders; the second (formation) sits {@code COL_W + GAP} right of it. */
	private int col0() {
		return RosterLayout.detailX(left0()) + 8;
	}

	private int bodyTop() {
		return RosterLayout.bodyY(top0()) + HEADING + 4;
	}

	@Override
	protected void init() {
		int x = col0(), y = bodyTop();
		for (int i = 0; i < ORDERS.length; i++) {
			int kind = ORDERS[i];
			addRenderableWidget(FlatButton.builder(Component.translatable("gui.jaymoskingdoms.followers.order." + kind), b -> {
				ClientPlayNetworking.send(new Followers.Give(this.target, kind));
				onClose();
			}).bounds(x, y + i * ROW, COL_W, 18)
				.tooltip(Ui.tipOf(Component.translatable("gui.jaymoskingdoms.followers.order." + kind + ".desc"))).build());
		}
		if (!ClientPlayNetworking.canSend(Formations.Give.TYPE)) {
			return;
		}
		int fx = x + COL_W + GAP;
		for (int i = 0; i < FORMATION.length; i++) {
			int kind = FORMATION[i];
			addRenderableWidget(FlatButton.builder(Component.translatable("gui.jaymoskingdoms.formation." + kind), b -> {
				ClientPlayNetworking.send(new Formations.Give(kind, this.target));
				onClose();
			}).bounds(fx, y + i * ROW, COL_W, 18)
				.tooltip(Ui.tipOf(Component.translatable("gui.jaymoskingdoms.formation." + kind + ".desc"))).build());
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = left0(), y = top0(), w = width0();
		RosterLayout.frame(graphics, this.font, x, y, w, height0(), this.title, null);
		RosterLayout.wells(graphics, x, y, w, height0() - RosterLayout.HEAD - 6 - 8);
		// the list: who the orders are for, with how far they are
		int lx = RosterLayout.listX(x), ly = RosterLayout.bodyY(y) + 3;
		RosterLayout.row(graphics, lx, ly, 0, true, Ui.over(mouseX, mouseY, lx, ly, RosterLayout.LIST_W, RosterLayout.ROW - 2));
		Ui.line(graphics, this.font, this.who, lx + 6, ly + 3, RosterLayout.LIST_W - 12, Ui.TEXT);
		Entity them = this.target < 0 || this.minecraft.level == null ? null : this.minecraft.level.getEntity(this.target);
		if (them != null && this.minecraft.player != null) {
			Ui.pill(graphics, this.font, lx + 6, ly + 12, Component.translatable("gui.jaymoskingdoms.followers.away").getString(),
				Math.round(them.distanceTo(this.minecraft.player)) + " m", Ui.GOLD);
		} else if (this.target < 0) {
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.followers.in_earshot"), lx + 6, ly + 13, RosterLayout.LIST_W - 12, Ui.DIM);
		}
		// the orders and the formation, under their headings
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.followers.for", this.who), col0(), RosterLayout.bodyY(y) + 4, COL_W * 2 + GAP, Ui.GOLD);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.followers.orders"), col0(), bodyTop() - 10, COL_W, Ui.DIM);
		if (ClientPlayNetworking.canSend(Formations.Give.TYPE)) {
			Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.formation.title"), col0() + COL_W + GAP, bodyTop() - 10, COL_W, Ui.DIM);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
