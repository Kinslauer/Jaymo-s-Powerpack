package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Powers;
import java.util.List;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;

/**
 * Pocket Arsenal's items: the <b>Dimensional Key</b>, found in bastion chests. In the hands of a master of the rift
 * (every ability mastered) it turns, and the arsenal ascends into the Armory. For anyone else the lock just rattles.
 */
public final class ArsenalItems {
	/** Chance of a key in a bastion treasure chest / any other bastion chest. */
	public static final float KEY_CHANCE_TREASURE = 0.25F;
	public static final float KEY_CHANCE_OTHER = 0.05F;
	/** After a rattle, the key can't be tried again for this long (ticks). */
	public static final int RATTLE_COOLDOWN = 20;

	public static Item DIMENSIONAL_KEY;

	private ArsenalItems() {
	}

	static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("arsenal_dimensional_key"));
		DIMENSIONAL_KEY = Registry.register(BuiltInRegistries.ITEM, key, new DimensionalKey(new Item.Properties().setId(key)
			.stacksTo(1)
			.rarity(Rarity.EPIC)
			.fireResistant()
			.component(DataComponents.LORE, new ItemLore(List.of(
				Component.translatable("item.jaymospowers.arsenal_dimensional_key.lore_1").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC),
				Component.translatable("item.jaymospowers.arsenal_dimensional_key.lore_2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))))));

		LootTableEvents.MODIFY.register((table, builder, source, registries) -> {
			if (!source.isBuiltin()) {
				return;
			}
			float chance;
			if (table.equals(BuiltInLootTables.BASTION_TREASURE)) {
				chance = KEY_CHANCE_TREASURE;
			} else if (table.equals(BuiltInLootTables.BASTION_OTHER)) {
				chance = KEY_CHANCE_OTHER;
			} else {
				return;
			}
			builder.withPool(LootPool.lootPool()
				.when(LootItemRandomChanceCondition.randomChance(chance))
				.add(LootItem.lootTableItem(DIMENSIONAL_KEY)));
		});
	}

	/** The key itself: it turns only for an arsenal user who has mastered everything and not yet ascended. */
	public static class DimensionalKey extends Item {
		public DimensionalKey(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
				return InteractionResult.SUCCESS;
			}
			if (Powers.has(serverPlayer, ArsenalPower.INSTANCE) && Fate.canAscend(serverPlayer)) {
				// it turns
				serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.IRON_DOOR_OPEN, SoundSource.PLAYERS, 1.0F, 0.5F);
				serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.7F, 1.2F);
				serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 120, 0.4, 0.8, 0.4, 0.15);
				stack.consume(1, player);
				Fate.ascend(serverPlayer);
				return InteractionResult.SUCCESS;
			}
			// locked: a rattle, nothing more
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHEST_LOCKED, SoundSource.PLAYERS, 0.9F, 0.7F + player.getRandom().nextFloat() * 0.2F);
			player.getCooldowns().addCooldown(stack, RATTLE_COOLDOWN);
			return InteractionResult.SUCCESS;
		}
	}
}
