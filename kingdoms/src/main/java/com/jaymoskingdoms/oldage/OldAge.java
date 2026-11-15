package com.jaymoskingdoms.oldage;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.beasts.Beasts;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;

/**
 * (medieval picks: Lost peoples) The old age's peoples (Lore: the old age's peoples) and what is left of them: their
 * cities ({@link OldRuins}), their sleeping rulers and guard ({@link OldRuler}, {@link TombGuard}) behind the tomb seals
 * ({@link #TOMB_SEAL}: breaking one wakes the tomb), their temples' altars ({@link OldAltarBlock}, {@link OldTemples}),
 * their descendants ({@link RuinFolk}) and their relics ({@link #relic}: the sands' sun scarab, the plains' marble bust,
 * the north's barrow brooch, Blossom's jade seal), which collectors in rich towns buy ({@code Currencies}). Their chests
 * keep the old money (emeralds); a tomb's now and then its people's arms too (Jaymo's Powers' {@code OldAgeArms}).
 */
public final class OldAge {
	// ---- tuning ----
	/** How far round a broken seal the tomb wakes (blocks). */
	static final double SEAL_WAKES = 28.0;
	/** The chance a tomb chest holds its people's arms, and an old relic of the realms' ({@code Relics}); a house chest its arms. */
	static final float TOMB_ARMS = 0.5F, TOMB_RELIC = 0.2F, HOUSE_ARMS = 0.06F;

	public static Block TOMB_SEAL, ALTAR;
	public static EntityType<OldRuler> RULER;
	public static EntityType<TombGuard> GUARD;
	static final Map<People, Item> RELICS = new EnumMap<>(People.class);

	private OldAge() {
	}

	public static void register() {
		TOMB_SEAL = block("tomb_seal", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).requiresCorrectToolForDrops()
			.strength(4.0F, 9.0F).sound(SoundType.DEEPSLATE_BRICKS).pushReaction(PushReaction.IMMOVEABLE));
		ALTAR = block("old_altar", OldAltarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(-1.0F, 3600000.0F)
			.sound(SoundType.STONE).noLootTable().pushReaction(PushReaction.IMMOVEABLE).randomTicks()
			.lightLevel(s -> s.getValue(OldAltarBlock.LIT) ? 11 : 0));
		relic(People.SUNSTEP, "sun_scarab");
		relic(People.PILLARED, "marble_bust");
		relic(People.MOUND, "barrow_brooch");
		relic(People.COURT, "jade_seal");
		RULER = Beasts.type("old_ruler", EntityType.Builder.of(OldRuler::new, MobCategory.MONSTER).sized(0.75F, 2.45F).clientTrackingRange(10),
			OldRuler.createAttributes());
		GUARD = Beasts.type("tomb_guard", EntityType.Builder.of(TombGuard::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(8),
			TombGuard.createAttributes());
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output -> output.accept(TOMB_SEAL));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(ALTAR));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> RELICS.values().forEach(output::accept));

		OldRuins.register();
		OldTemples.register();
		RuinFolk.register();
		JaymosKingdoms.LOGGER.debug("ruin folk attachments: {} {}", RuinFolk.FOLK, RuinFolk.PEOPLED);
		// a broken seal wakes the tomb behind it
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			if (state.is(TOMB_SEAL) && level instanceof ServerLevel server) {
				AABB round = new AABB(pos).inflate(SEAL_WAKES);
				server.playSound(null, pos, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 0.5F, 1.6F);
				for (OldRuler r : server.getEntitiesOfClass(OldRuler.class, round)) {
					r.wake(server);
				}
				for (TombGuard g : server.getEntitiesOfClass(TombGuard.class, round)) {
					g.wake(server);
				}
			}
		});
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			ResourceKey<net.minecraft.world.level.storage.loot.LootTable> key = table.unwrapKey().orElse(null);
			if (key == null || !key.identifier().getNamespace().equals(JaymosKingdoms.MOD_ID) || !key.identifier().getPath().startsWith("chests/old_age/")) {
				return;
			}
			String path = key.identifier().getPath().substring("chests/old_age/".length());
			int cut = path.indexOf('_');
			if (cut < 0) {
				return;
			}
			People p = People.byName(path.substring(0, cut));
			boolean tomb = path.endsWith("_tomb");
			RandomSource random = context.getRandom();
			if (random.nextFloat() < (tomb ? TOMB_ARMS : HOUSE_ARMS)) {
				ItemStack arms = com.jaymoskingdoms.Spoils.powers(random.nextBoolean() ? p.weapon : p.weapon2);
				if (!arms.isEmpty()) {
					drops.add(arms);
				}
			}
			if (tomb && random.nextFloat() < TOMB_RELIC) {
				ItemStack found = com.jaymoskingdoms.Relics.found(context.getLevel(), random);
				if (!found.isEmpty()) {
					drops.add(found);
				}
			}
		});
	}

	/** {@code p}'s relic. */
	public static Item relic(People p) {
		return RELICS.get(p);
	}

	/** Whether {@code stack} is one of the old age's relics (what a collector buys). */
	public static boolean relic(ItemStack stack) {
		return RELICS.containsValue(stack.getItem());
	}

	public static List<Item> relics() {
		return List.copyOf(RELICS.values());
	}

	private static void relic(People p, String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).rarity(Rarity.UNCOMMON).stacksTo(16)
			.component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.jaymoskingdoms." + name + ".hint").withStyle(ChatFormatting.GRAY))))));
		RELICS.put(p, item);
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> make, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, make.apply(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}
}
