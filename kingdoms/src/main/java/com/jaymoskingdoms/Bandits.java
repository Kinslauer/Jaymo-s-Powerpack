package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.125) Bandit masks and bandanas. Andy: "bandit masks and bandanas that hide your identity the same way a costume
 * does but npcs call you a bandit/pirate depending on location and powers can be traced back to a bandit or pirate if
 * villagers put it together."
 * <ul>
 * <li>Either one on the head hides the face: to {@link Identities} the wearer is an identity of their own,
 * {@link #KEY}{@code <their uuid>} (every bandit mask a player wears is the same bandit), with its own reputation,
 * crimes, wanted time and bounties, given away by the same things that give a costume away (putting it on or off in
 * view, a guard who makes you lift it, and powers seen used both masked and bare-faced: {@link Identities#powers}).</li>
 * <li>What they call you depends on where you are: a <b>pirate</b> in or on the water, in a boat or on a ship, or on a
 * sea, a beach or a river; a <b>bandit</b> anywhere else ({@link #pirate}). Jaymo's Powers shows the same name over the
 * head, in chat and on the tab list (its {@code BanditMask}), with the same rule.</li>
 * <li>Townsfolk who see a masked bandit walk into their settlement cry out ({@link #CRY_GAP} apart per settlement).</li>
 * </ul>
 */
public final class Bandits {
	/** The identity key's prefix (then the wearer's UUID). Jaymo's Powers tags masked bandits with the same key. */
	public static final String KEY = "bandit_";
	/** How often players are looked at (ticks); how far townsfolk see a bandit, and how often (per player and settlement) they cry out. */
	static final int EVERY = 20;
	static final double CRY_RANGE = 14.0;
	static final long CRY_GAP = 20L * 120;
	/** Lines a crier has, per name. */
	static final int CRIES = 5;

	static Item MASK, BANDANA;

	/** Player/faction → when its people last cried out at them. */
	private static final Map<String, Long> CRIED = new HashMap<>();

	private Bandits() {
	}

	static void register() {
		MASK = item("bandit_mask");
		BANDANA = item("bandana");
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.insertAfter(Items.LEATHER_BOOTS, MASK);
			output.insertAfter(MASK, BANDANA);
		});
		ServerTickEvents.END_SERVER_TICK.register(Bandits::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CRIED.clear());
	}

	private static Item item(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		Equippable worn = Equippable.builder(EquipmentSlot.HEAD).setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER)
			.setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosKingdoms.id(name))).build();
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1).component(DataComponents.EQUIPPABLE, worn)));
	}

	// ---- who is a bandit -----------------------------------------------------------------------------------------------

	/** {@code e} has a bandit mask or bandana on. */
	public static boolean wears(Entity e) {
		return MASK != null && e instanceof LivingEntity living
			&& (living.getItemBySlot(EquipmentSlot.HEAD).is(MASK) || living.getItemBySlot(EquipmentSlot.HEAD).is(BANDANA));
	}

	/** The bandit identity {@code e} goes by in a mask (whether or not they wear one now). */
	public static String key(Entity e) {
		return KEY + e.getStringUUID().replace("-", "").toLowerCase(Locale.ROOT);
	}

	/** {@code key} is a bandit's identity (not a costume's). */
	public static boolean is(@Nullable String key) {
		return key != null && key.startsWith(KEY);
	}

	/** Here, folk would call a masked {@code e} a pirate: in or on the water, in a boat or aboard a ship, by the sea or a river. */
	public static boolean pirate(Entity e) {
		if (e.isInWater() || e.getVehicle() != null && !(e.getVehicle() instanceof LivingEntity)) {
			return true;
		}
		BlockPos pos = e.blockPosition();
		var biome = e.level().getBiome(pos);
		return biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_BEACH) || biome.is(BiomeTags.IS_RIVER);
	}

	/** What a masked bandit is called where {@code e} is: "a bandit" or "a pirate". */
	public static Component alias(Entity e) {
		return Component.translatable(pirate(e) ? "identity.jaymoskingdoms.pirate" : "identity.jaymoskingdoms.bandit");
	}

	// ---- the cry ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!(player.level() instanceof ServerLevel level) || player.isSpectator() || player.isCreative()
				|| !is(Identities.maskKey(player))) {
				continue;
			}
			Faction f = Theft.lawAt(level, player.blockPosition());
			if (f == null || f.type() == FactionType.BARBARIAN || !Identities.hidden(player, f.id())) {
				continue;
			}
			String at = player.getStringUUID() + "/" + f.id();
			long now = level.getGameTime();
			Long last = CRIED.get(at);
			if (last != null && now - last < CRY_GAP) {
				continue;
			}
			Villager crier = null;
			for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(CRY_RANGE),
				v -> v.isAlive() && !v.isBaby() && !Downed.isDowned(v) && v.hasLineOfSight(player))) {
				crier = v;
				break;
			}
			if (crier == null) {
				continue;
			}
			CRIED.put(at, now);
			String kind = pirate(player) ? "pirate" : "bandit";
			Speech.say(level, crier, Component.translatable("talk.jaymoskingdoms.bandit.cry." + kind + "." + level.getRandom().nextInt(CRIES)),
				Speech.YELLOW, CRY_RANGE + 8.0);
		}
	}
}
