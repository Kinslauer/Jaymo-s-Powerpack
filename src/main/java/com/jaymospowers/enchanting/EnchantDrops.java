package com.jaymospowers.enchanting;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * (BWM port, after its HCEnchanting's arcane scrolls) Some mobs a player kills now and then drop a scroll that teaches
 * one enchantment: a creeper Blast Protection, a spider Bane of Arthropods, and so on. The scroll is an enchanted book
 * holding that one enchantment at level 1, so it's read (right-click) like any book ({@link Enchanting}), and since one
 * book teaches every level, that's all it takes.
 * <p>
 * Which mob teaches what is data: {@code data/<ns>/enchant_drops/*.json}, each
 * {@code {"entities": ["minecraft:spider", "#minecraft:skeletons"], "enchantment": "minecraft:bane_of_arthropods"}}, with
 * an optional {@code "chance"} (else {@link #SCROLL_CHANCE}) and {@code "dimension"} (only there; a mob that matches an
 * entry for its dimension ignores its entries without one, so a Nether skeleton teaches Infinity, not Projectile
 * Protection). Mending is left out on purpose.
 */
public final class EnchantDrops {
	/** The chance a mob drops its scroll, and what each level of Looting on the killer's weapon adds to it. */
	static final double SCROLL_CHANCE = 0.005, LOOTING_BONUS = 0.005;

	/** One entry: these mobs teach this enchantment. */
	record Drop(List<String> entities, Identifier enchantment, Optional<Double> chance, Optional<Identifier> dimension) {
		static final Codec<Drop> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.listOf().fieldOf("entities").forGetter(Drop::entities),
			Identifier.CODEC.fieldOf("enchantment").forGetter(Drop::enchantment),
			Codec.doubleRange(0.0, 1.0).optionalFieldOf("chance").forGetter(Drop::chance),
			Identifier.CODEC.optionalFieldOf("dimension").forGetter(Drop::dimension)).apply(i, Drop::new));

		boolean matches(EntityType<?> type) {
			for (String entity : this.entities) {
				if (entity.startsWith("#")) {
					Identifier tag = Identifier.tryParse(entity.substring(1));
					if (tag != null && type.builtInRegistryHolder().is(TagKey.create(Registries.ENTITY_TYPE, tag))) {
						return true;
					}
				} else if (entity.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString())) {
					return true;
				}
			}
			return false;
		}
	}

	private static List<Drop> drops = List.of();

	private EnchantDrops() {
	}

	public static void init() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(JaymosPowers.id("enchant_drops"),
			new SimpleJsonResourceReloadListener<>(Drop.CODEC, FileToIdConverter.json("enchant_drops")) {
				@Override
				protected void apply(Map<Identifier, Drop> loaded, ResourceManager manager, ProfilerFiller profiler) {
					drops = List.copyOf(loaded.values());
				}
			});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer killer && entity.level() instanceof ServerLevel level && !(entity instanceof ServerPlayer)) {
				roll(level, entity, killer);
			}
		});
	}

	private static void roll(ServerLevel level, LivingEntity mob, ServerPlayer killer) {
		Identifier dimension = level.dimension().identifier();
		List<Drop> matched = new ArrayList<>();
		boolean local = false;
		for (Drop drop : drops) {
			if (drop.dimension().isPresent() && !drop.dimension().get().equals(dimension) || !drop.matches(mob.getType())) {
				continue;
			}
			if (drop.dimension().isPresent() && !local) {
				matched.clear();
				local = true;
			}
			if (local == drop.dimension().isPresent()) {
				matched.add(drop);
			}
		}
		if (matched.isEmpty()) {
			return;
		}
		var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		int looting = EnchantmentHelper.getEnchantmentLevel(enchantments.getOrThrow(Enchantments.LOOTING), killer);
		for (Drop drop : matched) {
			// the chance goes up with Looting (BWM added Looting to its random number, which made it rarer)
			double chance = drop.chance().orElse(SCROLL_CHANCE) + LOOTING_BONUS * looting;
			if (mob.getRandom().nextDouble() >= chance) {
				continue;
			}
			Optional<Holder.Reference<Enchantment>> enchantment = enchantments.get(ResourceKey.create(Registries.ENCHANTMENT, drop.enchantment()));
			if (enchantment.isEmpty()) {
				continue;
			}
			mob.spawnAtLocation(level, scroll(enchantment.get()));
		}
	}

	/** A scroll teaching {@code enchantment}: an enchanted book with it alone, at level 1, named for what it is. */
	public static ItemStack scroll(Holder<Enchantment> enchantment) {
		ItemStack book = EnchantmentHelper.createBook(new EnchantmentInstance(enchantment, 1));
		book.set(DataComponents.ITEM_NAME, Component.translatable("item.jaymospowers.arcane_scroll"));
		book.set(DataComponents.LORE, new ItemLore(List.of(
			Component.translatable("item.jaymospowers.arcane_scroll.hint").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC))));
		return book;
	}
}
