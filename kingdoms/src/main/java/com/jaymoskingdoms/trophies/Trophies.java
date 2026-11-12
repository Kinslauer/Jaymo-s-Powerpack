package com.jaymoskingdoms.trophies;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.films.BossIntros;
import com.jaymoskingdoms.monsters.Monsters;
import java.util.Set;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.nbt.CompoundTag;
import org.jspecify.annotations.Nullable;

/**
 * The trophy stand and what is set on it. A great foe (whatever has an entrance, {@link BossIntros#kind}, and the monsters
 * of legend, {@link Monsters#legends}) leaves one {@link #FOE_TROPHY} when a player fells it: a carved mask the stand shows
 * upright and facing out. The stand ({@link TrophyStandBlock}, in wood and in stone) holds one item of any kind (a great
 * foe's head, a relic, a trophy) and names a foe's trophy on its plate only to someone who has learned the foe's name.
 */
public final class Trophies {
	/** The key marking a trophy's foe, and who felled it, in the stack's custom data. */
	public static final String FOE_KEY = "foe", SLAYER_KEY = "slayer";
	/** A trophy set on a stand draws a remark from a villager within this many blocks. */
	public static final int REMARK_RANGE = 8;
	/** How many different remarks there are (lang keys {@code speech.jaymoskingdoms.trophy.0..}). */
	static final int REMARK_LINES = 4;

	public static Block WOOD_STAND, STONE_STAND;
	public static Item FOE_TROPHY;
	public static BlockEntityType<TrophyStandEntity> STAND_ENTITY;

	private Trophies() {
	}

	public static void register() {
		WOOD_STAND = block("trophy_stand_wood", BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD)
			.ignitedByLava().noOcclusion());
		STONE_STAND = block("trophy_stand_stone", BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0F, 6.0F).sound(SoundType.STONE)
			.requiresCorrectToolForDrops().noOcclusion());
		STAND_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKingdoms.id("trophy_stand"),
			new BlockEntityType<>(TrophyStandEntity::new, Set.of(WOOD_STAND, STONE_STAND)));
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("great_foe_trophy"));
		FOE_TROPHY = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.RARE)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.accept(WOOD_STAND);
			output.accept(STONE_STAND);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.insertAfter(Items.NETHER_STAR, FOE_TROPHY));
		ServerLivingEntityEvents.AFTER_DEATH.register(Trophies::fell);
	}

	private static Block block(String name, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, new TrophyStandBlock(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}

	/**
	 * The great-foe kind {@code entity} is for a trophy, as {@link BossIntros#key} names it (so the journal's "great foes I
	 * have seen" and a trophy's plate agree), or null: a monster of legend is its entity type; a crowned villager is no foe.
	 */
	public static @Nullable String foeKind(Entity entity) {
		String kind = BossIntros.kind(entity);
		if (kind != null) {
			return kind.endsWith("/leader") ? null : kind;
		}
		return entity instanceof LivingEntity && Monsters.legend(entity.getType()) ? BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString() : null;
	}

	/** One trophy of the fallen great foe, to the player who felled it. */
	private static void fell(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level) || !(source.getEntity() instanceof Player slayer)) {
			return;
		}
		String kind = foeKind(entity);
		if (kind == null) {
			return;
		}
		ItemEntity drop = new ItemEntity(level, entity.getX(), entity.getY() + 0.5, entity.getZ(), trophy(kind, slayer));
		drop.setDefaultPickUpDelay();
		level.addFreshEntity(drop);
	}

	/** A trophy of {@code kind}, felled by {@code slayer} (null: nobody to know its name by). */
	public static ItemStack trophy(String kind, @Nullable Player slayer) {
		ItemStack stack = new ItemStack(FOE_TROPHY);
		CompoundTag tag = new CompoundTag();
		tag.putString(FOE_KEY, kind);
		if (slayer != null) {
			tag.putString(SLAYER_KEY, slayer.getUUID().toString());
		}
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_NAME, Component.translatable("item.jaymoskingdoms.great_foe_trophy"));
		return stack;
	}

	/** The foe a trophy stack is of (its kind key), or null for any other item. */
	public static @Nullable String foeOf(ItemStack stack) {
		if (!stack.is(FOE_TROPHY)) {
			return null;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? null : data.copyTag().getString(FOE_KEY).orElse(null);
	}

	/** Who felled the foe a trophy is of (the text of their id), or null. */
	public static @Nullable String slayerOf(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? null : data.copyTag().getString(SLAYER_KEY).orElse(null);
	}

	/** The foe's name for its plate: its entity type's name, or the kind key tidied ("ogre_king/..." becomes "Ogre King"). */
	public static Component foeName(String kind) {
		String base = kind.indexOf('/') < 0 ? kind : kind.substring(0, kind.indexOf('/'));
		Identifier id = Identifier.tryParse(base);
		if (id != null) {
			var type = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
			if (type.isPresent()) {
				return ((EntityType<?>) type.get()).getDescription();
			}
			StringBuilder out = new StringBuilder();
			for (String word : id.getPath().split("_")) {
				if (!word.isEmpty()) {
					out.append(out.length() == 0 ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
				}
			}
			return Component.literal(out.toString());
		}
		return Component.literal(kind);
	}

	/**
	 * Whether {@code player} has learned the name of the foe {@code kind} is: they have watched it make its entrance
	 * ({@link BossIntros#seen}, the journal's "great foes I have seen"), or they felled the very foe the trophy is of.
	 */
	public static boolean knowsName(Player player, String kind, ItemStack stack) {
		if (player.getUUID().toString().equals(slayerOf(stack))) {
			return true;
		}
		String base = kind.indexOf('/') < 0 ? kind : kind.substring(0, kind.indexOf('/'));
		for (String seen : BossIntros.seen(player)) {
			if (seen.equals(kind) || (seen.indexOf('/') < 0 ? seen : seen.substring(0, seen.indexOf('/'))).equals(base)) {
				return true;
			}
		}
		return false;
	}
}
