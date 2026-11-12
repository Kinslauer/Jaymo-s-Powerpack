package com.jaymospowers.gear.belt;

import com.jaymospowers.baubles.Baubles;
import com.jaymospowers.gear.GearPicks;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/** The worn belt's two keys, on the server: cycle the picked throwable, and throw it. */
public final class Belt {
	private static final Map<UUID, Long> LAST_THROW = new HashMap<>();

	private Belt() {
	}

	public static void init() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_THROW.remove(handler.player.getUUID()));
	}

	/** The belt of throwables worn in the belt slot (the live stack), or empty. */
	public static ItemStack worn(ServerPlayer player) {
		ItemStack belt = Baubles.beltSlot(player);
		return belt.getItem() instanceof ThrowerBeltItem ? belt : ItemStack.EMPTY;
	}

	/** Puts a changed belt back in its slot as a copy, so the wearer's client is told (the attachment syncs when the list differs). */
	private static void save(ServerPlayer player, ItemStack belt) {
		List<ItemStack> worn = Baubles.worn(player);
		worn.set(Baubles.BELT_SLOT, belt.copy());
		Baubles.setWorn(player, worn);
	}

	public static void cycle(ServerPlayer player) {
		ItemStack belt = worn(player).copy();
		if (belt.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.belt.none").withStyle(ChatFormatting.GRAY));
			return;
		}
		BeltContents contents = ThrowerBeltItem.contents(belt);
		if (contents.stacks().size() > 1) {
			belt.set(GearPicks.BELT, contents.next());
			save(player, belt);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4F, 1.5F);
		}
		say(player, belt);
	}

	private static void say(ServerPlayer player, ItemStack belt) {
		BeltContents contents = ThrowerBeltItem.contents(belt);
		ItemStackTemplate picked = contents.selected();
		if (picked == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.belt.empty").withStyle(ChatFormatting.GRAY));
			return;
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.belt.picked", picked.count(), picked.create().getHoverName(),
			contents.pick() + 1, contents.stacks().size()).withStyle(ChatFormatting.GOLD));
	}

	public static void throwPicked(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long last = LAST_THROW.get(player.getUUID());
		if (last != null && now - last < Throwables.THROW_COOLDOWN || player.isSpectator()) {
			return;
		}
		ItemStack belt = worn(player).copy();
		BeltContents contents = ThrowerBeltItem.contents(belt);
		ItemStackTemplate picked = contents.selected();
		if (picked == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.belt.empty").withStyle(ChatFormatting.GRAY));
			return;
		}
		LAST_THROW.put(player.getUUID(), now);
		ItemStack one = picked.create().copyWithCount(1);
		if (!player.hasInfiniteMaterials()) {
			belt.set(GearPicks.BELT, contents.withoutOne());
			save(player, belt);
		}
		Throwables.lob(level, player, one);
		say(player, belt);
	}
}
