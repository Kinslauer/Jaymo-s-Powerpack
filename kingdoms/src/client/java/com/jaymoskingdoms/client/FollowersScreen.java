package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Followers;
import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
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
 * listens.
 */
public final class FollowersScreen extends Screen {
	private static final int W = 176, ROW = 20, TOP = 24;
	/** The buttons, top to bottom. */
	private static final int[] ORDERS = {Followers.FOLLOW, Followers.FIGHT, Followers.HOLD, Followers.STAND, Followers.PATROL, Followers.CLEAR, Followers.FREE};
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
					Villager aimed = aimed(minecraft);
					minecraft.gui.setScreen(aimed == null ? new FollowersScreen(-1, Component.translatable("gui.jaymoskingdoms.followers.all"))
						: new FollowersScreen(aimed.getId(), aimed.getDisplayName()));
				}
			}
		});
	}

	/** Opens the orders for {@code villager} (from MCA's menu, which Orders stands in for), if the server can take orders. */
	public static void open(Entity villager) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null && ClientPlayNetworking.canSend(Followers.Give.TYPE)) {
			minecraft.gui.setScreen(new FollowersScreen(villager.getId(), villager.getDisplayName()));
		}
	}

	/** The villager under the crosshair within {@link Followers#ONE_RANGE} blocks, or null. */
	private static @Nullable Villager aimed(Minecraft minecraft) {
		Entity camera = minecraft.getCameraEntity();
		if (camera == null) {
			return null;
		}
		Vec3 eye = camera.getEyePosition(), end = eye.add(camera.getViewVector(1.0F).scale(Followers.ONE_RANGE));
		EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(camera, eye, end,
			camera.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0), e -> e instanceof Villager && e.isAlive(),
			Followers.ONE_RANGE * Followers.ONE_RANGE);
		return hit != null && hit.getEntity() instanceof Villager v ? v : null;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int height0() {
		return TOP + ORDERS.length * ROW + 8;
	}

	@Override
	protected void init() {
		int x = (this.width - W) / 2, y = (this.height - height0()) / 2 + TOP;
		for (int i = 0; i < ORDERS.length; i++) {
			int kind = ORDERS[i];
			addRenderableWidget(Button.builder(Component.translatable("gui.jaymoskingdoms.followers.order." + kind), b -> {
				ClientPlayNetworking.send(new Followers.Give(this.target, kind));
				onClose();
			}).bounds(x + 8, y + i * ROW, W - 16, 18)
				.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.jaymoskingdoms.followers.order." + kind + ".desc"))).build());
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = (this.width - W) / 2, y = (this.height - height0()) / 2;
		Ui.panel(graphics, x, y, W, height0(), Ui.GOLD);
		Ui.line(graphics, this.font, Component.translatable("gui.jaymoskingdoms.followers.for", this.who), x + 10, y + 8, W - 20, Ui.TEXT);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
