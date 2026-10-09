package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Relics: weapons lying about the world (in dungeon, temple, mineshaft, bastion, end-city and ancient-city chests) that
 * were pulled out of the pocket dimension once and never went back. Anyone can swing one. An Arsenal user who picks one
 * up takes it into their pocket dimension for good: from then on it's the first thing Reach brings out, ahead of the
 * random pull. Dropped, sent back, or timed out, it slips home again. It's only ever lost by wearing it out
 * (durability), or by losing the power.
 */
public final class ArsenalRelics {
	/** Chance a chest of each kind holds a relic. */
	static final float CHANCE_COMMON = 0.08F;
	static final float CHANCE_RARE = 0.25F;
	/** How many relics a pocket dimension can keep. */
	public static final int MAX_STORED = 5;

	private static final Set<ResourceKey<LootTable>> COMMON = Set.of(BuiltInLootTables.SIMPLE_DUNGEON, BuiltInLootTables.ABANDONED_MINESHAFT,
		BuiltInLootTables.DESERT_PYRAMID, BuiltInLootTables.JUNGLE_TEMPLE, BuiltInLootTables.PILLAGER_OUTPOST, BuiltInLootTables.SHIPWRECK_TREASURE,
		BuiltInLootTables.RUINED_PORTAL, BuiltInLootTables.WOODLAND_MANSION);
	private static final Set<ResourceKey<LootTable>> RARE = Set.of(BuiltInLootTables.BASTION_OTHER, BuiltInLootTables.END_CITY_TREASURE,
		BuiltInLootTables.ANCIENT_CITY, BuiltInLootTables.STRONGHOLD_CORRIDOR);

	/** The {@code jaymospowers:arsenal_relic} component: this weapon is a relic. */
	public static DataComponentType<Boolean> RELIC;

	/** A player's pocket dimension: the relics it keeps. Saved and kept through death. */
	public static final AttachmentType<List<ItemStack>> STORED = AttachmentRegistry.create(JaymosPowers.id("arsenal_relics"),
		builder -> builder.persistent(ItemStack.CODEC.listOf()).copyOnDeath());

	/** Relics on their way home to an owner who's offline: kept on the Overworld until they're back. */
	private record Return(UUID owner, ItemStack stack) {
		static final Codec<Return> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("owner").forGetter(Return::owner),
				ItemStack.CODEC.fieldOf("stack").forGetter(Return::stack))
			.apply(i, Return::new));
	}

	private static final AttachmentType<List<Return>> RETURNS = AttachmentRegistry.create(JaymosPowers.id("arsenal_relic_returns"),
		builder -> builder.persistent(Return.CODEC.listOf()));

	private ArsenalRelics() {
	}

	static void init() {
		RELIC = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("arsenal_relic"),
			DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());
		JaymosPowers.LOGGER.debug("arsenal relic attachments: {} {}", STORED, RETURNS);
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			ResourceKey<LootTable> key = table.unwrapKey().orElse(null);
			if (key == null) {
				return;
			}
			float chance = RARE.contains(key) ? CHANCE_RARE : COMMON.contains(key) ? CHANCE_COMMON : 0.0F;
			if (chance > 0.0F && context.getRandom().nextFloat() < chance) {
				drops.add(make(context.getLevel(), context.getRandom()));
			}
		});
	}

	public static boolean is(ItemStack stack) {
		return !stack.isEmpty() && Boolean.TRUE.equals(stack.get(RELIC));
	}

	/** A fresh relic: one weapon from the pocket dimension's pool, still shimmering. */
	static ItemStack make(ServerLevel level, RandomSource random) {
		ItemStack stack = ItemStack.EMPTY;
		for (int tries = 0; tries < 6 && (stack.isEmpty() || !stack.isDamageableItem()); tries++) {
			List<ItemStack> rolled = ArsenalPool.roll(level, random);
			stack = rolled.isEmpty() ? ItemStack.EMPTY : rolled.getFirst();
		}
		stack = stack.copyWithCount(1);
		stack.set(RELIC, true);
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			Component.translatable("item.jaymospowers.arsenal_relic.lore").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC))));
		return stack;
	}

	static List<ItemStack> stored(ServerPlayer player) {
		List<ItemStack> stored = player.getAttached(STORED);
		return stored == null ? List.of() : stored;
	}

	/**
	 * The relic Reach brings out first, if the pocket dimension keeps one (it's taken out of storage; it comes back
	 * through {@link #reclaim}). Empty if there's none.
	 */
	static ItemStack takeFirst(ServerPlayer player) {
		List<ItemStack> stored = stored(player);
		if (stored.isEmpty()) {
			return ItemStack.EMPTY;
		}
		List<ItemStack> rest = new ArrayList<>(stored);
		ItemStack first = rest.removeFirst();
		set(player, rest);
		return first;
	}

	/**
	 * A summoned stack is going home (dissolved, timed out, dropped, sent back): a relic goes back into its owner's
	 * pocket dimension, worn as it was. A worn-out one is gone for good.
	 */
	public static void reclaim(MinecraftServer server, ItemStack stack) {
		Summoned summoned = Summoned.of(stack);
		if (!is(stack) || summoned == null) {
			return;
		}
		ItemStack relic = stack.copyWithCount(1);
		relic.remove(Summoned.TYPE);
		if (relic.isDamageableItem() && relic.getDamageValue() >= relic.getMaxDamage()) {
			return;
		}
		ServerPlayer owner = server.getPlayerList().getPlayer(summoned.owner());
		if (owner != null) {
			store(owner, relic);
			return;
		}
		ServerLevel overworld = server.overworld();
		List<Return> returns = new ArrayList<>(overworld.getAttachedOrElse(RETURNS, List.of()));
		returns.add(new Return(summoned.owner(), relic));
		overworld.setAttached(RETURNS, returns);
	}

	private static void store(ServerPlayer player, ItemStack relic) {
		List<ItemStack> stored = new ArrayList<>(stored(player));
		if (stored.size() < MAX_STORED) {
			stored.add(relic);
			set(player, stored);
		} else {
			// no room: it's left at their feet as an ordinary (if strange) weapon
			player.spawnAtLocation(player.level(), relic);
		}
	}

	private static void set(ServerPlayer player, List<ItemStack> stored) {
		if (stored.isEmpty()) {
			player.removeAttached(STORED);
		} else {
			player.setAttached(STORED, List.copyOf(stored));
		}
	}

	/** Once a second, per Arsenal user: relics they're carrying slip into the pocket dimension; strays come home. */
	static void tick(ServerPlayer player) {
		if (player.tickCount % 20 != 0 || !Powers.has(player, ArsenalPower.INSTANCE)) {
			return;
		}
		ServerLevel overworld = player.level().getServer().overworld();
		List<Return> returns = overworld.getAttached(RETURNS);
		if (returns != null && returns.stream().anyMatch(r -> r.owner().equals(player.getUUID()))) {
			List<Return> rest = new ArrayList<>();
			for (Return r : returns) {
				if (r.owner().equals(player.getUUID())) {
					store(player, r.stack());
				} else {
					rest.add(r);
				}
			}
			if (rest.isEmpty()) {
				overworld.removeAttached(RETURNS);
			} else {
				overworld.setAttached(RETURNS, rest);
			}
		}
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (is(stack) && !Summoned.is(stack) && stored(player).size() < MAX_STORED) {
				inventory.setItem(slot, ItemStack.EMPTY);
				store(player, stack.copyWithCount(1));
				if (stack.getCount() > 1) {
					inventory.setItem(slot, stack.copyWithCount(stack.getCount() - 1));
				}
				ServerLevel level = player.level();
				Summoned.dissolveEffects(level, player.position().add(0.0, 1.0, 0.0));
				level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.3, 0.5, 0.3, 0.05);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.7F);
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.relic_kept", stack.getHoverName())
					.withStyle(ChatFormatting.LIGHT_PURPLE));
			}
		}
	}
}
