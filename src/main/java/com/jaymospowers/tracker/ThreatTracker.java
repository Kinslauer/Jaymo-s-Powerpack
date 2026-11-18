package com.jaymospowers.tracker;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.compat.KingdomsCompat;
import com.jaymospowers.core.mobs.BlightMobs;
import com.jaymospowers.relics.Relics;
import java.util.Optional;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.LodestoneTracker;
import org.jspecify.annotations.Nullable;

/**
 * (Awakening pick) The threat tracker, sold by the Keeper on the Lookout ({@code ZFighterSites#keeperTalk}): a brass case
 * whose needle turns to the nearest elite or boss within {@link #RANGE} blocks, and spins when there is none. Elites are
 * blighted creatures ({@link BlightMobs}) and Kingdoms' marked monsters with a bar; bosses are {@link Relics#boss}. The needle
 * is the vanilla compass's lodestone needle (the item's lodestone tracker, set here, untracked so no lodestone is needed),
 * drawn on our own case. Held, it says what it points at and how far, on the action bar, never in chat.
 */
public final class ThreatTracker extends Item {
	/** How far it feels (blocks), and how often it looks again (ticks). */
	static final double RANGE = 96.0;
	static final int LOOK_EVERY = 20;
	/** What the Keeper asks for it, in silver. */
	public static final int PRICE = 30;

	public static Item TRACKER;

	ThreatTracker(Properties properties) {
		super(properties);
	}

	public static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("threat_tracker"));
		TRACKER = Registry.register(BuiltInRegistries.ITEM, key, new ThreatTracker(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.UNCOMMON)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.insertAfter(Items.RECOVERY_COMPASS, TRACKER));
	}

	/** An elite or a boss: what the needle turns to. */
	static boolean threat(LivingEntity entity) {
		return entity.isAlive() && !(entity instanceof net.minecraft.world.entity.player.Player)
			&& (BlightMobs.isBlighted(entity) || Relics.boss(entity)
				|| Boolean.TRUE.equals(KingdomsCompat.call("MarkedMonsters", "barred", new Class<?>[] {Entity.class}, false, entity)));
	}

	static @Nullable LivingEntity nearest(ServerLevel level, Entity from) {
		LivingEntity best = null;
		double bestSq = RANGE * RANGE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(RANGE), ThreatTracker::threat)) {
			double d = e.distanceToSqr(from);
			if (d < bestSq) {
				bestSq = d;
				best = e;
			}
		}
		return best;
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		if (owner.tickCount % LOOK_EVERY != 0) {
			return;
		}
		LivingEntity threat = nearest(level, owner);
		if (threat == null) {
			stack.remove(DataComponents.LODESTONE_TRACKER);
		} else {
			stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(level.dimension(), threat.blockPosition())), false));
		}
		if (owner instanceof ServerPlayer player && (player.getMainHandItem() == stack || player.getOffhandItem() == stack)) {
			player.sendOverlayMessage(threat == null
				? Component.translatable("message.jaymospowers.threat_tracker.none", (int)RANGE).withStyle(ChatFormatting.GRAY)
				: Component.translatable("message.jaymospowers.threat_tracker.near", threat.getDisplayName(), (int)Math.round(threat.distanceTo(player)))
					.withStyle(ChatFormatting.GOLD));
		}
	}
}
