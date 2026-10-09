package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The ascended form, <b>Armory</b>: the rift has learned your hand. Sneak + Reach while holding a summoned weapon pins
 * that kind of weapon; from then on every Reach brings it first, plus the usual random pull. Sneak + Reach with an
 * empty hand lets go of the pin.
 */
public final class ArsenalArmory {
	/** The pinned weapon's item id. Saved, kept through death, synced to its owner (HUD). */
	public static final AttachmentType<Identifier> PIN = AttachmentRegistry.create(JaymosPowers.id("arsenal_pin"),
		builder -> builder.persistent(Identifier.CODEC).copyOnDeath().syncWith(Identifier.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	private ArsenalArmory() {
	}

	static void init() {
		// (the attachment registers itself when this class loads)
	}

	/** The pinned weapon, if this player is ascended and has one. */
	public static @Nullable Item pinned(Player player) {
		Identifier id = player.getAttached(PIN);
		if (id == null || !Fate.isAscended(player)) {
			return null;
		}
		Item item = BuiltInRegistries.ITEM.getValue(id);
		return item == null || item == Items.AIR ? null : item;
	}

	/**
	 * Sneak + Reach while ascended: pin or unpin. Returns true when it handled the key (no Reach this time).
	 */
	static boolean tryPin(ServerPlayer player) {
		if (!Fate.isAscended(player) || !player.isShiftKeyDown()) {
			return false;
		}
		ServerLevel level = player.level();
		ItemStack held = player.getMainHandItem();
		if (Summoned.is(held)) {
			player.setAttached(PIN, BuiltInRegistries.ITEM.getKey(held.getItem()));
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.pinned", held.getHoverName())
				.withStyle(ChatFormatting.GOLD));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 0.9F, 0.7F);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.6F, 1.4F);
			level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 30, 0.4, 0.5, 0.4, 0.6);
			return true;
		}
		if (held.isEmpty() && player.getAttached(PIN) != null) {
			player.removeAttached(PIN);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.unpinned").withStyle(ChatFormatting.GRAY));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.6F, 0.8F);
			return true;
		}
		return false;
	}

	/**
	 * What a Reach brings: the pinned weapon when it's the first of the three you can hold ({@code first}: nothing
	 * summoned is out), otherwise the usual random pull.
	 */
	static List<ItemStack> rollFor(ServerPlayer player, ServerLevel level, RandomSource random, boolean first) {
		Item pin = pinned(player);
		if (first && pin != null) {
			return new ArrayList<>(ArsenalPool.stacksFor(level, random, pin));
		}
		return ArsenalPool.roll(level, random);
	}

	static void onLost(ServerPlayer player) {
		player.removeAttached(PIN);
	}
}
