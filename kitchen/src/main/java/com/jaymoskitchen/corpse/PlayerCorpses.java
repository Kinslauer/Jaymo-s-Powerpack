package com.jaymoskitchen.corpse;

import com.jaymoskitchen.JaymosKitchen;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.properties.Property;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Player corpses. When a player dies, everything but their hotbar drops as usual; the hotbar stays with their body,
 * which falls in a heap where they died. Right-click it to take the hotbar back: into the same slots when they're
 * free, anywhere in the inventory otherwise (whatever doesn't fit stays on the body). Burn it, or after a while it
 * gets up as a {@link ZombiePlayer}.
 */
public final class PlayerCorpses {
	public static EntityType<ZombiePlayer> ZOMBIE_PLAYER;
	/** How much higher a slim-skinned player's body and zombie sound. */
	public static final float SLIM_PITCH = 1.35F;

	private PlayerCorpses() {
	}

	public static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKitchen.id("zombie_player"));
		ZOMBIE_PLAYER = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.<ZombiePlayer>of(ZombiePlayer::new, MobCategory.MONSTER)
				.sized(0.6F, 1.95F)
				.eyeHeight(1.74F)
				.clientTrackingRange(8)
				.build(key));
		FabricDefaultAttributeRegistry.register(ZOMBIE_PLAYER, ZombiePlayer.createZombiePlayerAttributes());
	}

	/** What a player had on their hotbar when they died, set aside before the rest of the inventory drops. */
	public interface Holder {
		void jaymoskitchen$setHotbar(List<ItemStack> hotbar, int selected);

		List<ItemStack> jaymoskitchen$hotbar();

		int jaymoskitchen$selected();
	}

	/** The player's death animation has ended: their body stays, with their hotbar. */
	public static void spawn(ServerPlayer player) {
		ServerLevel level = player.level();
		CorpseEntity corpse = new CorpseEntity(Corpses.CORPSE, level);
		corpse.snapTo(player.getX(), player.getY(), player.getZ(), player.yBodyRot, 0.0F);
		Holder holder = (Holder) player;
		corpse.setUpPlayer(player.getUUID(), player.getGameProfile().name(), holder.jaymoskitchen$hotbar(), holder.jaymoskitchen$selected());
		corpse.setSlim(slim(player));
		corpse.setMissing(Corpses.missingParts(player));
		holder.jaymoskitchen$setHotbar(List.of(), 0);
		corpse.markJustDied();
		level.addFreshEntity(corpse);
	}

	/**
	 * Whether the player's skin has slim arms: from their profile's skin, or (no skin set) the default skin the game
	 * picks from their id, half of which are slim.
	 */
	static boolean slim(ServerPlayer player) {
		for (Property property : player.getGameProfile().properties().get("textures")) {
			try {
				JsonObject textures = JsonParser.parseString(new String(Base64.getDecoder().decode(property.value()), StandardCharsets.UTF_8))
					.getAsJsonObject().getAsJsonObject("textures");
				JsonObject skin = textures == null ? null : textures.getAsJsonObject("SKIN");
				if (skin != null) {
					JsonObject metadata = skin.getAsJsonObject("metadata");
					return metadata != null && metadata.has("model") && "slim".equals(metadata.get("model").getAsString());
				}
			} catch (RuntimeException e) {
				// unreadable: fall back to the default skin
			}
		}
		// the default skins: the first nine of eighteen are slim
		return Math.floorMod(player.getUUID().hashCode(), 18) < 9;
	}

	/** Right-clicked: hands the hotbar back, slot for slot where it can. */
	static void restore(CorpseEntity corpse, Player player, List<ItemStack> hotbar, int selected) {
		Inventory inventory = player.getInventory();
		boolean moved = false;
		boolean left = false;
		for (int i = 0; i < hotbar.size(); i++) {
			ItemStack stack = hotbar.get(i);
			if (stack.isEmpty()) {
				continue;
			}
			if (i < Inventory.getSelectionSize() && inventory.getItem(i).isEmpty()) {
				inventory.setItem(i, stack);
				hotbar.set(i, ItemStack.EMPTY);
				moved = true;
				continue;
			}
			int before = stack.getCount();
			inventory.add(stack);
			moved |= stack.getCount() != before;
			if (stack.isEmpty()) {
				hotbar.set(i, ItemStack.EMPTY);
			} else {
				left = true;
			}
		}
		if (moved) {
			corpse.jolt(false);
		}
		if (moved && player.getUUID().toString().equals(corpse.ownerId())) {
			inventory.setSelectedSlot(selected);
		}
		player.level().playSound(null, corpse.getX(), corpse.getY(), corpse.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(),
			SoundSource.PLAYERS, 1.0F, 0.8F);
		player.sendOverlayMessage(Component.translatable(left ? "message.jaymoskitchen.corpse.player_no_room"
			: moved ? "message.jaymoskitchen.corpse.player_restored" : "message.jaymoskitchen.corpse.burn_zombie"));
	}

	/** Left too long: the body gets up as a zombie wearing their face, with what was left on it. */
	static void reanimate(CorpseEntity corpse, ServerLevel level, List<ItemStack> hotbar, int selected) {
		ZombiePlayer zombie = new ZombiePlayer(ZOMBIE_PLAYER, level);
		zombie.snapTo(corpse.getX(), corpse.getY(), corpse.getZ(), corpse.getYRot(), 0.0F);
		corpse.owner().ifPresent(owner -> zombie.setOwner(owner, corpse.ownerName()));
		zombie.setSlim(corpse.slim());
		List<ItemStack> rest = new ArrayList<>(hotbar);
		ItemStack held = selected >= 0 && selected < rest.size() ? rest.set(selected, ItemStack.EMPTY) : ItemStack.EMPTY;
		zombie.arm(held, rest);
		level.addFreshEntity(zombie);
		level.playSound(null, corpse.getX(), corpse.getY(), corpse.getZ(), SoundEvents.ZOMBIE_INFECT, SoundSource.HOSTILE, 1.0F,
			0.8F * (corpse.slim() ? SLIM_PITCH : 1.0F));
	}
}
