package com.jaymospowers.client.hud;

import com.jaymospowers.client.power.zfighter.ZFighterClientConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Andy (2026-11-26): "how do you load a cannon? there should be a popup with a tutorial". The first time the crosshair
 * finds one of Jaymo's Kingdoms' guns a player works by hand, or one is in their hand, a {@link TipCard} says how to load
 * and fire it: the Ship's Cannon (gunpowder, then shot, then flint and steel or a fire charge; the helm's broadside; a
 * store behind its breech and a redstone pulse) and the Field Mortar (a Mortar Shell, then use again). Each is shown once
 * per player, flagged {@code gun_tip.<player uuid>.<part>} in {@code config/jaymospowers-client.properties}, and waits
 * for any card already up, as {@link BodyTips}' do. Kingdoms' things are known by their ids, so it needn't be loaded.
 */
public final class GunTips {
	/** How often (ticks) it looks; ticks into a session before any card. */
	static final int EVERY = 10, SETTLE_TICKS = 200;
	/** Jaymo's Kingdoms' ship gun (block and item) and field mortar (entity and item). */
	private static final String SHIP_CANNON = "jaymoskingdoms:ship_cannon", FIELD_MORTAR = "jaymoskingdoms:field_mortar";

	private GunTips() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(GunTips::tick);
	}

	private static String flag(LocalPlayer player, String part) {
		return "gun_tip." + player.getUUID() + "." + part;
	}

	private static boolean shown(LocalPlayer player, String part) {
		return "true".equals(ZFighterClientConfig.get(flag(player, part), "false"));
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || player.tickCount < SETTLE_TICKS || player.tickCount % EVERY != 0 || TipCard.busy()
			|| minecraft.gui.screen() != null || player.isSpectator()) {
			return;
		}
		String looked = "";
		HitResult hit = minecraft.hitResult;
		if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
			looked = BuiltInRegistries.BLOCK.getKey(minecraft.level.getBlockState(block.getBlockPos()).getBlock()).toString();
		} else if (hit instanceof EntityHitResult entity) {
			looked = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getEntity().getType()).toString();
		}
		String held = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString();
		if (!shown(player, "ship_cannon") && (SHIP_CANNON.equals(looked) || SHIP_CANNON.equals(held))) {
			offer(player, "ship_cannon", key("key.use"), key("key.attack"));
		} else if (!shown(player, "field_mortar") && (FIELD_MORTAR.equals(looked) || FIELD_MORTAR.equals(held))) {
			offer(player, "field_mortar", key("key.use"));
		}
	}

	private static Component key(String name) {
		return Component.keybind(name);
	}

	private static void offer(LocalPlayer player, String part, Object... args) {
		ZFighterClientConfig.set(flag(player, part), "true");
		TipCard.show(Component.translatable("gun_tip.jaymospowers." + part + ".title"), Component.translatable("gun_tip.jaymospowers." + part, args));
	}
}
