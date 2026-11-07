package com.jaymospowers.costume;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.baubles.Baubles;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * (0.21.41) Andy: "The archer hood should attach to a cloak that fits the player, and allows the hood to be removed and
 * worn in the back, visible to all, and lowering visibility while up". A head piece in {@link #HOODS} (the Archers' archer
 * hood) comes with a cloak: a shoulder mantle and a back panel drawn on the wearer ({@code client.costume.HoodedCloakLayer},
 * our own boxes and paint, docs/Hooded-Cloak-Spec.md). The mask key (' by default, {@link Costume#toggleMask}) lowers the
 * hood onto the back and raises it again ({@link #DOWN}, synced to everyone, so all players see it).
 * <p>While the hood is up the wearer is hooded like a costume hood ({@link SecretIdentity#HOODED}): Kingdoms' mob senses
 * see them at {@code Identities.HOOD_SIGHT} of the usual range, witnesses know them as a hooded figure, and other players
 * see their name tag only up close.
 */
public final class HoodedCloak {
	/** Head pieces that are hoods joined to a cloak. */
	public static final TagKey<Item> HOODS = TagKey.create(Registries.ITEM, JaymosPowers.id("cloak_hoods"));
	/** On a player: their cloak's hood is down, on their back (synced to everyone). Kept while the hood is taken off. */
	public static final AttachmentType<Boolean> DOWN = AttachmentRegistry.create(JaymosPowers.id("cloak_hood_down"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath().syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
	/** The hood can't be flipped again sooner than this (ticks). */
	static final int COOLDOWN = 10;
	/** The rustle of the hood going down and up: pitches. */
	static final float DOWN_PITCH = 0.9F, UP_PITCH = 1.15F;
	/** (server) When each player last flipped their hood (game time). */
	private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

	private HoodedCloak() {
	}

	/** Loads the class (the attachment registers on load). */
	public static void init() {
	}

	/** Whether {@code player} wears a cloak hood on their head (either side): in the vanilla head armour slot, or in the baubles head slot. */
	public static boolean wears(Player player) {
	    if (player.getItemBySlot(EquipmentSlot.HEAD).is(HOODS)) {
	        return true;
	    }
	    List<ItemStack> worn = Baubles.worn(player);
	    return worn != null && worn.size() > Baubles.HEAD_SLOT && worn.get(Baubles.HEAD_SLOT).is(HOODS);
	}

	/** Whether the hood is lowered onto the back (either side; true only while one is worn). */
	public static boolean down(Player player) {
		return wears(player) && Boolean.TRUE.equals(player.getAttached(DOWN));
	}

	/** Whether {@code player} has a cloak hood up (either side). */
	public static boolean up(Player player) {
		return wears(player) && !Boolean.TRUE.equals(player.getAttached(DOWN));
	}

	/** The identity a hooded cloak's wearer goes by ({@link SecretIdentity#HOODED}): their own, one per player. */
	static String key(Player player) {
		String canon = "cloak_hood|" + player.getUUID();
		long h = 0xcbf29ce484222325L;
		for (int i = 0; i < canon.length(); i++) {
			h ^= canon.charAt(i);
			h *= 0x100000001b3L;
		}
		return String.format(java.util.Locale.ROOT, "%016x", h);
	}

	/** The mask key, for a cloak hood (server): down onto the back, or back up over the head. */
	public static void toggle(ServerPlayer player) {
		long now = player.level().getGameTime();
		Long last = LAST.get(player.getUUID());
		if (last != null && now - last >= 0 && now - last < COOLDOWN) {
			return;
		}
		LAST.put(player.getUUID(), now);
		boolean lower = !Boolean.TRUE.equals(player.getAttached(DOWN));
		if (lower) {
			player.setAttached(DOWN, true);
		} else {
			player.removeAttached(DOWN);
		}
		player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.6F,
			lower ? DOWN_PITCH : UP_PITCH);
		player.sendOverlayMessage(Component.translatable(lower ? "message.jaymospowers.cloak_hood.down" : "message.jaymospowers.cloak_hood.up"));
		SecretIdentity.update(player);
	}
}
