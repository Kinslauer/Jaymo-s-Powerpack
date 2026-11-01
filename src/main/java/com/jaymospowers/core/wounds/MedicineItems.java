package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;

/**
 * Wounds, phase 4 (0.14.4): the medieval kit (Andy, 2026-10-20: "I dont want any modern medicine"), in its own creative
 * tab. Bandages, rags, splints, needle and thread, smelling salts and leeches are used through {@link Treatment} (use:
 * the body screen; sneak + use: the worst part); willow-bark tea and poppy syrup are drunk ({@link Pain}), and poppy can
 * be spooned into someone else (use it on them).
 */
public final class MedicineItems {
	public static Item LINEN_BANDAGE;
	public static Item RAG;
	public static Item SPLINT;
	public static Item NEEDLE_AND_THREAD;
	public static Item SMELLING_SALTS;
	public static Item LEECHES;
	public static Item WILLOW_BARK_TEA;
	public static Item POPPY_SYRUP;
	/** (0.19.20) The herbal remedies, the clean rag, the healer's satchel and the sickbed foods (docs/Healing-Plan.md). */
	public static Item YARROW_POWDER, KNITBONE_POULTICE, BURN_SALVE, GARLIC_POULTICE, CLEAN_RAG, HEALERS_SATCHEL;
	public static Item BONE_BROTH, CHAMOMILE_TEA, POSSET;

	/** (0.14.4) Smelling salts' uses, and needle and thread's. */
	public static final int SALTS_USES = 4, NEEDLE_USES = 8;

	private static final List<Item> ALL = new ArrayList<>();

	private MedicineItems() {
	}

	static void register() {
		LINEN_BANDAGE = item("linen_bandage", p -> new MedicineItem(Treatment.Remedy.BANDAGE, p.stacksTo(16)));
		RAG = item("rag", p -> new MedicineItem(Treatment.Remedy.RAG, p.stacksTo(16)));
		SPLINT = item("splint", p -> new MedicineItem(Treatment.Remedy.SPLINT, p.stacksTo(16)));
		NEEDLE_AND_THREAD = item("needle_and_thread", p -> new MedicineItem(Treatment.Remedy.NEEDLE, p.durability(NEEDLE_USES)));
		SMELLING_SALTS = item("smelling_salts", p -> new MedicineItem(Treatment.Remedy.SALTS, p.durability(SALTS_USES)));
		LEECHES = item("leeches", p -> new MedicineItem(Treatment.Remedy.LEECHES, p.stacksTo(16)));
		WILLOW_BARK_TEA = item("willow_bark_tea", p -> new DrinkItem(false, p.stacksTo(16).usingConvertsTo(Items.BOWL)
			.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK)));
		POPPY_SYRUP = item("poppy_syrup", p -> new DrinkItem(true, p.stacksTo(16).usingConvertsTo(Items.GLASS_BOTTLE)
			.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK)));

		// (0.19.20) the five wild herbs (blocks and items), then what's made of them
		com.jaymospowers.core.wounds.herbs.Herbs.register();
		YARROW_POWDER = item("yarrow_powder", p -> new MedicineItem(Treatment.Remedy.YARROW, p.stacksTo(16)));
		KNITBONE_POULTICE = item("knitbone_poultice", p -> new MedicineItem(Treatment.Remedy.KNITBONE, p.stacksTo(16)));
		BURN_SALVE = item("burn_salve", p -> new MedicineItem(Treatment.Remedy.SALVE, p.stacksTo(16)));
		GARLIC_POULTICE = item("garlic_poultice", p -> new MedicineItem(Treatment.Remedy.GARLIC, p.stacksTo(16)));
		CLEAN_RAG = item("clean_rag", p -> new MedicineItem(Treatment.Remedy.CLEAN_RAG, p.stacksTo(16)));
		HEALERS_SATCHEL = item("healers_satchel", p -> new Satchel.SatchelItem(p.stacksTo(1)));
		BONE_BROTH = item("bone_broth", p -> new SickbedItem(SickbedItem.Kind.BROTH, p.stacksTo(16).usingConvertsTo(Items.BOWL)
			.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK)));
		CHAMOMILE_TEA = item("chamomile_tea", p -> new SickbedItem(SickbedItem.Kind.CHAMOMILE, p.stacksTo(16).usingConvertsTo(Items.BOWL)
			.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK)));
		POSSET = item("posset", p -> new SickbedItem(SickbedItem.Kind.POSSET, p.stacksTo(16).usingConvertsTo(Items.BOWL)
			.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK)));

		ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, JaymosPowers.id("medicine"));
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tab, FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.jaymospowers.medicine"))
			.icon(() -> new ItemStack(LINEN_BANDAGE))
			.build());
		CreativeModeTabEvents.modifyOutputEvent(tab).register(output -> ALL.forEach(output::accept));
	}

	/** An item with its one line of lore (how it's used), from lang {@code tooltip.jaymospowers.<id>}. */
	public static Item item(String id, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		Item.Properties properties = new Item.Properties().setId(key).component(DataComponents.LORE, new ItemLore(List.of(
			Component.translatable("tooltip.jaymospowers." + id).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC))));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties));
		ALL.add(item);
		return item;
	}

	/** Bandage, rag, splint, needle, salts, leeches: used on a body (yours: use in the air) through {@link Treatment}. */
	public static final class MedicineItem extends Item {
		final Treatment.Remedy remedy;

		MedicineItem(Treatment.Remedy remedy, Item.Properties properties) {
			super(properties);
			this.remedy = remedy;
		}

		public Treatment.Remedy remedy() {
			return this.remedy;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			// (0.19.20) a dirty rag in or facing water is washed by holding use (sneak + use still treats)
			if (this.remedy == Treatment.Remedy.RAG && !player.isShiftKeyDown() && RagWashing.canWash(player)) {
				player.startUsingItem(hand);
				return InteractionResult.CONSUME;
			}
			if (player instanceof ServerPlayer server) {
				Treatment.useOnSelf(server, hand, this.remedy);
			}
			return InteractionResult.SUCCESS;
		}

		/** (0.19.20) Only washing rags is a held use. */
		@Override
		public int getUseDuration(ItemStack stack, LivingEntity user) {
			return this.remedy == Treatment.Remedy.RAG ? RagWashing.USE_TICKS : 0;
		}

		@Override
		public ItemUseAnimation getUseAnimation(ItemStack stack) {
			return this.remedy == Treatment.Remedy.RAG ? ItemUseAnimation.BRUSH : ItemUseAnimation.NONE;
		}

		@Override
		public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
			if (this.remedy == Treatment.Remedy.RAG) {
				RagWashing.washTick(level, user, stack);
			}
		}
	}

	/** Willow-bark tea ({@code poppy} false) and poppy syrup: drunk, and they work as they go down. */
	public static final class DrinkItem extends Item {
		final boolean poppy;

		DrinkItem(boolean poppy, Item.Properties properties) {
			super(properties);
			this.poppy = poppy;
		}

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
			ItemStack left = super.finishUsingItem(stack, level, entity);
			if (level instanceof ServerLevel server) {
				if (this.poppy) {
					Pain.poppy(server, entity);
				} else {
					Pain.tea(entity);
				}
			}
			return left;
		}
	}

	/** (0.19.20) Bone broth, chamomile tea and posset: sickbed foods, drunk from a bowl; see {@link Herbal}. */
	public static final class SickbedItem extends Item {
		public enum Kind {
			BROTH, CHAMOMILE, POSSET
		}

		final Kind kind;

		SickbedItem(Kind kind, Item.Properties properties) {
			super(properties);
			this.kind = kind;
		}

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
			ItemStack left = super.finishUsingItem(stack, level, entity);
			if (level instanceof ServerLevel) {
				switch (this.kind) {
					case BROTH -> Herbal.broth(entity);
					case CHAMOMILE -> Herbal.chamomile(entity);
					case POSSET -> Herbal.posset(entity);
				}
			}
			return left;
		}
	}
}
