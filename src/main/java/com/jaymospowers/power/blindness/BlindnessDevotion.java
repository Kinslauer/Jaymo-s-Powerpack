package com.jaymospowers.power.blindness;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Callings;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.Bleeding;
import com.jaymospowers.core.wounds.Scars;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Andy (2026-11-11): "The especially devout opt to don a blindfold or carve out their eyes granting a new type of sight
 * within the blindness power, the ultimate granting them an almost birds eye view."
 * <ul>
 * <li>The <b>blindfold</b> (black wool and string), worn on the head. Anyone without Blindness who wears it can't see.
 * A powerless survival player who keeps it on for {@link #VIGIL_SECONDS} s in a row is called to Blindness: a day off
 * the wait, once a day.</li>
 * <li><b>Carving out the eyes</b>: sneak and use shears on nothing, twice within {@link #CONFIRM_TICKS} (the first
 * use only warns). The eyes are gone for the rest of that life ({@link Scars.Kind#CARVED_EYES}), the head bleeds, and a
 * powerless player awakens to Blindness at once. Only the powerless (whom no other power has called) and those who
 * already hold Blindness can do it.</li>
 * <li>Either way the holder is <b>devout</b> ({@link #devout}): the dark thins and living things near them burn through
 * it (drawn on their own screen), and Void Sight, their ultimate, lifts the view high over them: Sky Sight.</li>
 * </ul>
 */
public final class BlindnessDevotion {
	/** Seconds in a row the blindfold must be worn to call Blindness. */
	public static final int VIGIL_SECONDS = 300;
	/** Ticks the second cut has to follow the first use (the warning). */
	static final int CONFIRM_TICKS = 100;
	/** What carving the eyes costs in health (never the last point), and how long a blindfold's wearer stays blind after each check. */
	static final float CARVE_DAMAGE = 6.0F;
	static final int BLINDFOLD_BLIND_TICKS = 60;
	/** How far a devout holder sees living things through the dark, and how far in Sky Sight (blocks). */
	public static final double SIGHT_RANGE = 16.0, SKY_SIGHT_RANGE = 32.0;

	public static final ResourceKey<EquipmentAsset> ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosPowers.id("blindfold"));
	public static Item BLINDFOLD;

	/** Seconds in a row each powerless player has worn the blindfold, and the day it last called them. */
	private static final Map<UUID, Integer> VIGIL = new HashMap<>();
	private static final Map<UUID, Long> VIGIL_DAY = new HashMap<>();
	/** The tick each player was warned (first use of the shears). */
	private static final Map<UUID, Long> WARNED = new HashMap<>();

	private BlindnessDevotion() {
	}

	static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("blindfold"));
		BLINDFOLD = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1)
			.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD)
				.setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER)
				.setAsset(ASSET)
				.setAllowedEntities(EntityTypes.PLAYER)
				.build())));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(BLINDFOLD));
		ServerTickEvents.END_SERVER_TICK.register(BlindnessDevotion::tick);
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer server) || !player.isShiftKeyDown()
				|| !player.getItemInHand(hand).is(Items.SHEARS)) {
				return InteractionResult.PASS;
			}
			return carve(server, hand) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			VIGIL.remove(handler.player.getUUID());
			WARNED.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			VIGIL.clear();
			VIGIL_DAY.clear();
			WARNED.clear();
		});
	}

	/** Whether {@code entity} has the blindfold on. */
	public static boolean blindfolded(LivingEntity entity) {
		return BLINDFOLD != null && entity.getItemBySlot(EquipmentSlot.HEAD).is(BLINDFOLD);
	}

	/** Whether this player took their blindness on: blindfolded, or the eyes carved out. Both sides (the scars and the head slot are synced). */
	public static boolean devout(Player player) {
		return blindfolded(player) || Scars.has(player, Scars.Kind.CARVED_EYES);
	}

	/** Once a second: the blindfold blinds whoever wears it without the power, and keeps the powerless one's vigil. */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean holder = Powers.has(player, BlindnessPower.INSTANCE);
			if (!blindfolded(player) || holder) {
				VIGIL.remove(player.getUUID());
				continue;
			}
			player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLINDFOLD_BLIND_TICKS, 0, true, false, false));
			if (Powers.of(player) != null || player.isCreative() || player.isSpectator()) {
				continue;
			}
			int seconds = VIGIL.merge(player.getUUID(), 1, Integer::sum);
			if (seconds >= VIGIL_SECONDS) {
				VIGIL.remove(player.getUUID());
				long day = player.level().getOverworldClockTime() / Callings.DAY;
				Long last = VIGIL_DAY.put(player.getUUID(), day);
				if (last == null || last != day) {
					Callings.call(player, "blindness", Callings.DAY);
				}
			}
		}
	}

	/** Sneak + shears on nothing: warns the first time, carves the eyes out the second. True when it did either. */
	private static boolean carve(ServerPlayer player, InteractionHand hand) {
		if (Scars.has(player, Scars.Kind.CARVED_EYES)) {
			return false;
		}
		boolean holder = Powers.has(player, BlindnessPower.INSTANCE);
		boolean called = Powers.of(player) == null && !player.isCreative() && !player.isSpectator()
			&& (Callings.power(player) == null || Callings.power(player) == BlindnessPower.INSTANCE);
		if (!holder && !called) {
			return false;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long warned = WARNED.get(player.getUUID());
		if (warned == null || now - warned > CONFIRM_TICKS) {
			WARNED.put(player.getUUID(), now);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.blindness.carve_warn").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
			return true;
		}
		WARNED.remove(player.getUUID());
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 0.6F);
		player.hurtServer(level, player.damageSources().generic(), Math.min(CARVE_DAMAGE, player.getHealth() - 1.0F));
		Bleeding.open(player, BodyPart.HEAD, false);
		player.getItemInHand(hand).hurtAndBreak(1, player, hand);
		Scars.add(player, Scars.Kind.CARVED_EYES);
		if (!holder) {
			Callings.call(player, "blindness", Integer.MAX_VALUE);
		}
		return true;
	}
}
