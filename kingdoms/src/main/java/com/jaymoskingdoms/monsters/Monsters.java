package com.jaymoskingdoms.monsters;

import com.jaymoskingdoms.Factions;
import com.jaymoskingdoms.JaymosKingdoms;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * (0.5.56) The monsters of legend, after Fangs 'n Claws' list: the werewolf, the ogres (green, cave, fire), the wraiths
 * (and the flame wraith), the silver skeleton (after DrZhark's Mo' Creatures, GPL-3, see {@code licenses/mocreatures};
 * this package and its client side are under that licence too), and Kingdoms' own mud golem and chest mimic. They come
 * as wilderness encounters ({@code Encounters}), the fire ogre and the flame wraith also in the Nether, and the mimic
 * from loot chests out in the wild. The silver sword (from silver skeletons) bites deep into werewolves and the undead.
 */
public final class Monsters {
	/** How often a loot chest out in the wild (away from every settlement) is a mimic. */
	public static final float MIMIC_CHANCE = 0.06F;
	/** No mimic this close to a settlement (blocks). */
	static final double MIMIC_CLEARANCE = 256.0;
	/** Nether spawn weights (a zombified piglin is 100) and how many come. */
	static final int FIRE_OGRE_WEIGHT = 2, FLAME_WRAITH_WEIGHT = 4;
	/**
	 * The silver sword: like iron, a little keener and more enchantable, and what it adds against the undead. (0.5.57)
	 * Forged from, and mended with, silver ingots (Jaymo's Tinkering's, by the {@code c:ingots/silver} tag).
	 */
	static final ToolMaterial SILVER = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, 320, 6.0F, 2.0F, 20,
		TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ingots/silver")));
	static final float SILVER_UNDEAD_BONUS = 4.0F;

	public static EntityType<Werewolf> WEREWOLF;
	public static EntityType<Ogre> OGRE, CAVE_OGRE, FIRE_OGRE;
	public static EntityType<Wraith> WRAITH, FLAME_WRAITH;
	public static EntityType<SilverSkeleton> SILVER_SKELETON;
	public static EntityType<MudGolem> MUD_GOLEM;
	public static EntityType<Mimic> MIMIC;
	public static Item SILVER_SWORD;

	private Monsters() {
	}

	/** The monsters of legend, the ones folk gossip about and send you after. */
	public static List<EntityType<?>> legends() {
		return List.of(WEREWOLF, OGRE, CAVE_OGRE, FIRE_OGRE, WRAITH, FLAME_WRAITH, SILVER_SKELETON, MUD_GOLEM);
	}

	public static boolean legend(EntityType<?> type) {
		return legends().contains(type);
	}

	public static void register() {
		// (0.7.0) updated every tick: a werewolf player's beast follows them step for step
		WEREWOLF = type("werewolf", EntityType.Builder.of(Werewolf::new, MobCategory.MONSTER).sized(0.8F, 1.9F).clientTrackingRange(10).updateInterval(1));
		OGRE = type("ogre", EntityType.Builder.<Ogre>of((t, l) -> new Ogre(t, l, Ogre.GREEN), MobCategory.MONSTER).sized(1.6F, 3.4F).clientTrackingRange(10));
		CAVE_OGRE = type("cave_ogre", EntityType.Builder.<Ogre>of((t, l) -> new Ogre(t, l, Ogre.CAVE), MobCategory.MONSTER).sized(1.6F, 3.4F)
			.clientTrackingRange(10));
		FIRE_OGRE = type("fire_ogre", EntityType.Builder.<Ogre>of((t, l) -> new Ogre(t, l, Ogre.FIRE), MobCategory.MONSTER).sized(1.6F, 3.4F)
			.fireImmune().clientTrackingRange(10));
		WRAITH = type("wraith", EntityType.Builder.<Wraith>of((t, l) -> new Wraith(t, l, false), MobCategory.MONSTER).sized(0.7F, 1.9F)
			.clientTrackingRange(8));
		FLAME_WRAITH = type("flame_wraith", EntityType.Builder.<Wraith>of((t, l) -> new Wraith(t, l, true), MobCategory.MONSTER).sized(0.7F, 1.9F)
			.fireImmune().clientTrackingRange(8));
		SILVER_SKELETON = type("silver_skeleton", EntityType.Builder.of(SilverSkeleton::new, MobCategory.MONSTER).sized(0.8F, 2.0F).clientTrackingRange(8));
		MUD_GOLEM = type("mud_golem", EntityType.Builder.of(MudGolem::new, MobCategory.MONSTER).sized(1.3F, 2.1F).clientTrackingRange(10));
		MIMIC = type("mimic", EntityType.Builder.of(Mimic::new, MobCategory.MONSTER).sized(0.9F, 0.9F).clientTrackingRange(8));
		FabricDefaultAttributeRegistry.register(WEREWOLF, Werewolf.createAttributes());
		FabricDefaultAttributeRegistry.register(OGRE, Ogre.createAttributes(Ogre.GREEN));
		FabricDefaultAttributeRegistry.register(CAVE_OGRE, Ogre.createAttributes(Ogre.CAVE));
		FabricDefaultAttributeRegistry.register(FIRE_OGRE, Ogre.createAttributes(Ogre.FIRE));
		FabricDefaultAttributeRegistry.register(WRAITH, Wraith.createAttributes(false));
		FabricDefaultAttributeRegistry.register(FLAME_WRAITH, Wraith.createAttributes(true));
		FabricDefaultAttributeRegistry.register(SILVER_SKELETON, SilverSkeleton.createAttributes());
		FabricDefaultAttributeRegistry.register(MUD_GOLEM, MudGolem.createAttributes());
		FabricDefaultAttributeRegistry.register(MIMIC, Mimic.createAttributes());

		// the Nether's own: fire ogres and flame wraiths among its monsters
		SpawnPlacements.register(FIRE_OGRE, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules);
		SpawnPlacements.register(FLAME_WRAITH, SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules);
		BiomeModifications.addSpawn(BiomeSelectors.foundInTheNether(), MobCategory.MONSTER, FIRE_OGRE, FIRE_OGRE_WEIGHT, 1, 1);
		BiomeModifications.addSpawn(BiomeSelectors.foundInTheNether(), MobCategory.MONSTER, FLAME_WRAITH, FLAME_WRAITH_WEIGHT, 1, 1);

		ResourceKey<Item> sword = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("silver_sword"));
		SILVER_SWORD = Registry.register(BuiltInRegistries.ITEM, sword,
			new SilverSword(new Item.Properties().setId(sword).sword(SILVER, 3.0F, -2.4F).rarity(Rarity.UNCOMMON)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.insertAfter(Items.IRON_SWORD, SILVER_SWORD));

		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(level instanceof ServerLevel server) || player.isSpectator() || !(level.getBlockEntity(hit.getBlockPos()) instanceof ChestBlockEntity chest)
				|| chest.getLootTable() == null || level.getRandom().nextFloat() >= MIMIC_CHANCE
				|| Factions.around(server.getServer(), server, hit.getBlockPos(), MIMIC_CLEARANCE) != null) {
				return InteractionResult.PASS;
			}
			return mimic(server, chest, hit.getBlockPos(), player) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
	}

	/** The loot chest at {@code pos} was a mimic all along: it takes the chest's place and its loot, and wakes. */
	private static boolean mimic(ServerLevel level, ChestBlockEntity chest, BlockPos pos, net.minecraft.world.entity.player.Player player) {
		Mimic mimic = MIMIC.create(level, EntitySpawnReason.EVENT);
		if (mimic == null) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		float yaw = state.hasProperty(ChestBlock.FACING) ? state.getValue(ChestBlock.FACING).toYRot() : 0.0F;
		// Roll the loot ourselves: a Lootr chest is a ChestBlockEntity whose unpackLootTable does nothing (each player's
		// share lives elsewhere), so unpacking it left the mimic empty-handed and the chest gone for everyone.
		List<ItemStack> loot = new ArrayList<>();
		for (int i = 0; i < chest.getContainerSize(); i++) {
			loot.add(chest.getItem(i).copy());
		}
		ResourceKey<LootTable> table = chest.getLootTable();
		if (table != null) {
			SimpleContainer rolled = new SimpleContainer(chest.getContainerSize());
			LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
				.withLuck(player.getLuck()).withParameter(LootContextParams.THIS_ENTITY, player).create(LootContextParamSets.CHEST);
			level.getServer().reloadableRegistries().getLootTable(table).fill(rolled, params, chest.getLootTableSeed());
			loot.addAll(rolled.getItems());
			chest.setLootTable(null);
		}
		chest.clearContent();
		level.removeBlock(pos, false);
		mimic.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
		mimic.yBodyRot = mimic.yHeadRot = yaw;
		mimic.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
		mimic.setPersistenceRequired();
		mimic.hold(loot);
		level.addFreshEntity(mimic);
		mimic.wake(player);
		return true;
	}

	private static <T extends Entity> EntityType<T> type(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	/** The silver sword: an iron-like blade that bites deep into werewolves ({@link Werewolf}) and the undead. */
	static final class SilverSword extends Item {
		SilverSword(Item.Properties properties) {
			super(properties);
		}

		@Override
		public float getAttackDamageBonus(Entity victim, float damage, DamageSource source) {
			return victim.is(EntityTypeTags.UNDEAD) ? SILVER_UNDEAD_BONUS : 0.0F;
		}
	}
}
