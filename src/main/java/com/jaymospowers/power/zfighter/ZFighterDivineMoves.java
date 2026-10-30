package com.jaymospowers.power.zfighter;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.zfighter.divine.DivineEntities;
import com.jaymospowers.power.zfighter.divine.EightSignCircle;
import com.jaymospowers.power.zfighter.divine.FlyingSword;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * (0.18.48, Weapon Forms patch 6) The Divine-born's two lineage moves, on the keys a Z Fighter's kit leaves free. Both are
 * sneak taps, so no existing key changes for anyone else:
 * <ul>
 * <li><b>Sneak + G</b>: {@link FlyingSword}. The sword (or spear) in your hand leaves it and fights for itself for five
 * seconds, then comes back. Costs {@link #SWORD_KI} ki; {@link #SWORD_COOLDOWN} ticks before another.</li>
 * <li><b>Sneak + B</b>: {@link EightSignCircle}. A ring of eight signs under your feet seals foes inside from powers and forms.
 * Costs {@link EightSignCircle#KI_COST} ki; {@link EightSignCircle#COOLDOWN} ticks before another.</li>
 * </ul>
 * Only a Divine-born ({@link ZFighterLineage#divine}) answers them; hooked from {@link ZFighterPower#onKey}, ahead of the
 * ordinary G and B (a Divine-born who sneaks and taps G or B gets these in place of the ordinary move).
 */
final class ZFighterDivineMoves {
	/** Ki the Flying Sword costs, and the wait before another (ticks). */
	static final float SWORD_KI = 20.0F;
	static final int SWORD_COOLDOWN = 240;

	private static final Map<UUID, Long> READY_AT = new HashMap<>();

	private ZFighterDivineMoves() {
	}

	/** Called once from {@link ZFighterRaces#init}. */
	static void init() {
		DivineEntities.init();
		EightSignCircle.init();
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			// the blade is never lost to a logout
			FlyingSword.recall(handler.player);
			READY_AT.remove(handler.player.getUUID());
			RING_READY_AT.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			FlyingSword.clear();
			READY_AT.clear();
			RING_READY_AT.clear();
		});
	}

	/** Whether {@code stack} is a blade the Flying Sword can carry. */
	static boolean blade(ItemStack stack) {
		return !stack.isEmpty() && (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.SPEARS));
	}

	/** A sneak press of G or B by a Divine-born: true if it was one of these moves (even refused), so the ordinary move is skipped. */
	static boolean key(ServerPlayer player, ZFighterPower.Live live, PowerKey key) {
		if (!ZFighterLineage.divine(player) || key != PowerKey.ABILITY_2 && key != PowerKey.ABILITY_4) {
			return false;
		}
		if (key == PowerKey.ABILITY_2) {
			sword(player, live);
		} else {
			circle(player, live);
		}
		return true;
	}

	private static boolean wait(ServerPlayer player, String name) {
		long left = READY_AT.getOrDefault(player.getUUID(), 0L) - player.level().getGameTime();
		if (left > 0 && !player.isCreative()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_divine.wait", Component.translatable(name), (left + 19) / 20)
				.withStyle(ChatFormatting.GRAY));
			return true;
		}
		return false;
	}

	private static void sword(ServerPlayer player, ZFighterPower.Live live) {
		if (FlyingSword.out(player)) {
			return;
		}
		ItemStack held = player.getMainHandItem();
		if (!blade(held)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_flying_sword.none").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (wait(player, "technique.jaymospowers.zfighter.flying_sword") || !(player.level() instanceof ServerLevel level)
			|| !ZFighterPower.spend(player, live, SWORD_KI)) {
			return;
		}
		float damage = (float)player.getAttributeValue(Attributes.ATTACK_DAMAGE);
		int slot = player.getInventory().getSelectedSlot();
		ItemStack blade = held.copy();
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		FlyingSword.launch(level, player, blade, slot, damage);
		READY_AT.put(player.getUUID(), level.getGameTime() + SWORD_COOLDOWN);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_flying_sword.out").withStyle(ChatFormatting.AQUA));
	}

	private static void circle(ServerPlayer player, ZFighterPower.Live live) {
		long now = player.level().getGameTime();
		Long ready = RING_READY_AT.get(player.getUUID());
		if (ready != null && ready > now && !player.isCreative()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_divine.wait",
				Component.translatable("technique.jaymospowers.zfighter.eight_sign"), (ready - now + 19) / 20).withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!player.onGround() && !player.isCreative()) {
			// the ring is drawn on the ground
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_eight_sign.ground").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!ZFighterPower.spend(player, live, EightSignCircle.KI_COST)) {
			return;
		}
		EightSignCircle.open(player);
		RING_READY_AT.put(player.getUUID(), now + EightSignCircle.COOLDOWN);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_eight_sign.open").withStyle(ChatFormatting.GOLD));
	}

	/** The ring's own wait (the sword has its own in {@link #READY_AT}). */
	private static final Map<UUID, Long> RING_READY_AT = new HashMap<>();
}
