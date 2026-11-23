package com.jaymoskitchen.corpse;

import com.jaymoskitchen.JaymosKitchen;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.properties.Property;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import com.jaymoskitchen.ragdoll.LivingRagdolls;
import com.jaymoskitchen.ragdoll.RagdollBody;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;

/**
 * Player corpses. When a player dies, their body falls in a heap where they died with everything they carried on it
 * (Andy: "they should keep the loot on their corpse like npcs do, now accessed with a right click. the only time loot
 * should burst like the video is when the body is gibbed"): nothing drops. Right-click it to open its loot screen
 * ({@link CorpseLootMenu}) and take what you want. Blown apart, it all bursts out; burned away, it's left lying in the
 * ash; left long enough, it gets up as a {@link ZombiePlayer} carrying it (dropped when that's killed).
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
		// what a dying player carried waits on them until their body appears on the first tick of dying
		// (LivingEntityMixin); respawning or leaving the game before that tick leaves the body then, so none of it is lost
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> leftBehind(oldPlayer));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> leftBehind(handler.player));
	}

	/** A player gone before their body appeared: it appears now, with what they carried. */
	private static void leftBehind(ServerPlayer player) {
		if (((Holder) player).jaymoskitchen$hotbar().stream().anyMatch(stack -> !stack.isEmpty())) {
			spawn(player);
		}
	}

	/** What a player carried when they died (inventory, armour, off hand: see {@code CorpseEntity#hotbar}), set aside for their body. */
	public interface Holder {
		void jaymoskitchen$setHotbar(List<ItemStack> hotbar, int selected);

		List<ItemStack> jaymoskitchen$hotbar();

		int jaymoskitchen$selected();

		/** For each piece handed over after the inventory ({@link #keepWith}), the parts it's worn on. */
		List<Integer> jaymoskitchen$parts();
	}

	/**
	 * For other mods (by name: Kingdoms' harness and hidden blades, Powers' baubles and web shooters): a player who has
	 * just died and leaves a body keeps {@code stack} on it with the rest of what they carried, worn on {@code parts}
	 * ({@code CorpseEntity.PART_} bits; 0 for the torso), so it's torn off with any of those parts the body lacks. False
	 * (keep it yourself) if no body will hold it: kept inventory, no body left, or no room.
	 */
	public static boolean keepWith(ServerPlayer player, ItemStack stack, int parts) {
		Holder holder = (Holder) player;
		List<ItemStack> held = holder.jaymoskitchen$hotbar();
		if (held.size() < CorpseEntity.INVENTORY_SLOTS || held.size() >= CorpseEntity.PLAYER_SLOTS) {
			return false;
		}
		held.add(stack.copy());
		holder.jaymoskitchen$parts().add(parts);
		return true;
	}

	/** The player has started to die: their body appears, with everything they carried. */
	public static void spawn(ServerPlayer player) {
		ServerLevel level = player.level();
		CorpseEntity corpse = new CorpseEntity(Corpses.CORPSE, level);
		corpse.snapTo(player.getX(), player.getY(), player.getZ(), player.yBodyRot, 0.0F);
		Holder holder = (Holder) player;
		corpse.setUpPlayer(player.getUUID(), player.getGameProfile().name(), holder.jaymoskitchen$hotbar(), holder.jaymoskitchen$selected(),
			holder.jaymoskitchen$parts());
		corpse.setSlim(slim(player));
		// (0.5.37) the look they died in (a Bio-built's form), kept for good
		corpse.keepLookTags(player);
		// what was worn on a head or limb they died without (a blast takes every one) comes off (CorpseEntity#tearOff); the
		// rest stays on the torso
		corpse.setMissing(Corpses.missingParts(player));
		holder.jaymoskitchen$setHotbar(List.of(), 0);
		corpse.markJustDied();
		corpse.setOrigin(player);
		// (0.5.1) a player who died limp leaves exactly that body; otherwise they crumple like any mob
		RagdollBody limp = LivingRagdolls.lastBody(player);
		corpse.startRagdoll(limp != null ? limp : Corpses.deathBody(player));
		Corpses.pinFrom(player, corpse);
		Corpses.tellBody(player, corpse);
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

	/** Left too long: the body gets up as a zombie wearing their face, with what was left on it. */
	static ZombiePlayer reanimate(CorpseEntity corpse, ServerLevel level, List<ItemStack> hotbar, int selected) {
		ZombiePlayer zombie = new ZombiePlayer(ZOMBIE_PLAYER, level);
		zombie.snapTo(corpse.getX(), corpse.getY(), corpse.getZ(), corpse.getYRot(), 0.0F);
		corpse.owner().ifPresent(owner -> zombie.setOwner(owner, corpse.ownerName()));
		zombie.setSlim(corpse.slim());
		List<ItemStack> rest = new ArrayList<>(hotbar);
		ItemStack held = selected >= 0 && selected < rest.size() ? rest.set(selected, ItemStack.EMPTY) : ItemStack.EMPTY;
		zombie.arm(held, rest);
		level.addFreshEntity(zombie);
		// (0.5.84) it rises from where it lay, not standing at once
		Reanimation.start(level, zombie);
		level.playSound(null, corpse.getX(), corpse.getY(), corpse.getZ(), SoundEvents.ZOMBIE_INFECT, SoundSource.HOSTILE, 1.0F,
			0.8F * (corpse.slim() ? SLIM_PITCH : 1.0F));
		return zombie;
	}
}
