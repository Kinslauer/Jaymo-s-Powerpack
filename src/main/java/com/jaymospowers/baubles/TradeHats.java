package com.jaymospowers.baubles;

import com.jaymospowers.JaymosPowers;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

/**
 * (Village mods patch 5) Trade hats: the hats the village trades wear, worn in the head bauble slot. Ported from
 * PinkGoosik's Villager Hats (MIT, notice in {@code docs/licenses/Villager-Hats-MIT.txt}): its seven hats, their sizes
 * and heights on the head, their models (cut from the game's own villager profession textures, which the item atlas
 * is told to take in: {@code assets/minecraft/atlases/items.json}) and the zombie villager drop. Its crafting recipes are
 * left out (Andy: no grid recipe): a hat comes off a zombie villager of that trade, or a tailor sews one (Jaymo's
 * Kingdoms, {@code TailorHats}), and a player who holds the matching job and wears its hat is liked a little better
 * ({@code Careers.hatBonus}). Worn, a hat is drawn on the head with its item model ({@code client/baubles/TradeHatLayer}).
 */
public final class TradeHats {
	// ===== the knobs ================================================================================================
	/** How likely a zombie villager of a hat's trade drops that hat, when a player killed it and mobs drop loot. */
	static final float DROP_CHANCE = 0.35F;
	// ================================================================================================================

	/** The hats: the trade that wears one, and (as in Villager Hats) how much bigger than the head and how high it sits. */
	public enum Hat {
		FARMER(VillagerProfession.FARMER, 1.15F, -0.0625F),
		FLETCHER(VillagerProfession.FLETCHER, 1.05F, -0.125F),
		FISHERMAN(VillagerProfession.FISHERMAN, 1.05F, -0.0625F),
		ARMORER(VillagerProfession.ARMORER, 1.15F, -0.0625F),
		SHEPHERD(VillagerProfession.SHEPHERD, 1.05F, -0.125F),
		LIBRARIAN(VillagerProfession.LIBRARIAN, 1.15F, 0.0F),
		BUTCHER(VillagerProfession.BUTCHER, 1.15F, -0.125F);

		public final ResourceKey<VillagerProfession> trade;
		/** The hat's scale over the head, and how far it is lowered (in blocks; negative raises it). */
		public final float size, height;
		public @Nullable Item item;

		Hat(ResourceKey<VillagerProfession> trade, float size, float height) {
			this.trade = trade;
			this.size = size;
			this.height = height;
		}

		/** The item id's path: {@code farmer_hat}. */
		public String id() {
			return this.trade.identifier().getPath() + "_hat";
		}
	}

	private TradeHats() {
	}

	static void init() {
		for (Hat hat : Hat.values()) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(hat.id()));
			hat.item = Baubles.add(Registry.register(BuiltInRegistries.ITEM, key,
				new BaubleItem(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.COMMON), Baubles.Kind.HEAD, List.of())));
		}
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ZombieVillager zombie && zombie.level() instanceof ServerLevel level && source.getEntity() instanceof Player
				&& level.getGameRules().get(GameRules.MOB_DROPS) && level.getRandom().nextFloat() < DROP_CHANCE) {
				Hat hat = of(zombie.getVillagerData().profession().unwrapKey().orElse(null));
				if (hat != null && hat.item != null) {
					zombie.spawnAtLocation(level, new ItemStack(hat.item));
				}
			}
		});
	}

	/** The hat {@code trade} wears, or null for a trade with none. */
	public static @Nullable Hat of(@Nullable ResourceKey<VillagerProfession> trade) {
		for (Hat hat : Hat.values()) {
			if (hat.trade.equals(trade)) {
				return hat;
			}
		}
		return null;
	}

	/** The hat {@code item} is, or null. */
	public static @Nullable Hat of(Item item) {
		for (Hat hat : Hat.values()) {
			if (hat.item == item) {
				return hat;
			}
		}
		return null;
	}
}
