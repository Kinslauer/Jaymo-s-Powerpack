package com.jaymoskitchen.corpse;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.KitchenBlood;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Parts from poisoned beasts (Hunter's Road, patch 7, "T-B3"; Toxony's idea, written clean-room): a creature that dies with a
 * poison on it, cut with a <b>scalpel</b>, gives one part, by what it was: a venom fang (wolf), green spit (llama), a double
 * poison sac (squid), brittle poison bones (bogged) and a poison jelly (slime). Like a monster's organ
 * ({@link com.jaymoskitchen.butchery.MonsterOrgans}) the part is <i>carved, never dropped</i>: nothing here touches a loot table.
 * Which beast gives which part is read from {@code data/<namespace>/poisoned_parts/*.json}
 * ({@code {"entity", "part", "count"}}). A body that died poisoned is marked in its look ({@link #POISONED_KEY}; a bogged is
 * always poisoned, for its own bones are); the scalpel takes the part once and the mark goes. A baby has none.
 */
public final class PoisonedParts extends SimpleJsonResourceReloadListener<PoisonedParts.Entry> {
	public record Entry(Identifier entity, Identifier part, int count) {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("entity").forGetter(Entry::entity),
			Identifier.CODEC.fieldOf("part").forGetter(Entry::part),
			Codec.INT.optionalFieldOf("count", 1).forGetter(Entry::count)).apply(i, Entry::new));
	}

	/** The mark in a body's look that it died poisoned and still has its part. */
	public static final String POISONED_KEY = "jaymoskitchen_poisoned";
	/** Uses in a scalpel's blade. */
	private static final int SCALPEL_USES = 96;

	public static Item SCALPEL, VENOM_FANG, GREEN_SPIT, POISON_SAC, BRITTLE_BONE, POISON_JELLY;

	private static volatile Map<Identifier, Entry> table = Map.of();

	private PoisonedParts() {
		super(Entry.CODEC, FileToIdConverter.json("poisoned_parts"));
	}

	public static void register() {
		SCALPEL = item("scalpel", new Item.Properties().durability(SCALPEL_USES));
		VENOM_FANG = item("venom_fang", new Item.Properties());
		GREEN_SPIT = item("green_spit", new Item.Properties());
		POISON_SAC = item("poison_sac", new Item.Properties());
		BRITTLE_BONE = item("brittle_bone", new Item.Properties());
		POISON_JELLY = item("poison_jelly", new Item.Properties());
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(JaymosKitchen.id("poisoned_parts"), new PoisonedParts());
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(new ItemStack(SCALPEL)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			for (Item part : List.of(VENOM_FANG, GREEN_SPIT, POISON_SAC, BRITTLE_BONE, POISON_JELLY)) {
				output.accept(new ItemStack(part));
			}
		});
	}

	private static Item item(String id, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties.setId(key)));
	}

	@Override
	protected void apply(Map<Identifier, Entry> loaded, ResourceManager manager, ProfilerFiller profiler) {
		Map<Identifier, Entry> next = new HashMap<>();
		for (Entry entry : loaded.values()) {
			if (BuiltInRegistries.ENTITY_TYPE.containsKey(entry.entity()) && BuiltInRegistries.ITEM.containsKey(entry.part())) {
				next.put(entry.entity(), entry);
			}
		}
		table = Map.copyOf(next);
	}

	/** Whether {@code mob} dies poisoned: a poison on it, or a bogged (undead, so it takes none, its bones are poison already). */
	public static boolean diedPoisoned(LivingEntity mob) {
		return mob.hasEffect(MobEffects.POISON) || mob.getType() == EntityTypes.BOGGED;
	}

	/** The entry for a body of {@code type}, or null if no part is carved from it. */
	static @Nullable Entry entryOf(@Nullable EntityType<?> type) {
		return type == null || table.isEmpty() ? null : table.get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
	}

	/**
	 * A scalpel used on {@code corpse}: if it died poisoned and a part is carved from its kind, the part comes out beside it, the
	 * blade wears, and the mark goes. Null if this isn't a scalpel or the body has nothing for it (the click is then used as before).
	 */
	static @Nullable InteractionResult use(CorpseEntity corpse, ServerLevel level, Player player, ItemStack held) {
		if (!held.is(SCALPEL) || corpse.isPlayerCorpse() || !corpse.poisonedBody() || !corpse.grown()) {
			return null;
		}
		Entry entry = entryOf(corpse.sourceType());
		Item part = entry == null ? null : BuiltInRegistries.ITEM.getOptional(entry.part()).orElse(null);
		if (entry == null || part == null) {
			return null;
		}
		corpse.partTaken();
		Vec3 at = corpse.position().add(0.0, 0.3, 0.0);
		for (int i = 0; i < Math.max(1, entry.count()); i++) {
			corpse.spawnAtLocation(level, new ItemStack(part), 0.3F);
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.NEUTRAL, 0.8F, 1.2F);
		KitchenBlood.send(level, corpse, at.x, at.y, at.z, 5, 0.25, 0.05, 0.25, 0.08);
		held.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
		return InteractionResult.SUCCESS_SERVER;
	}
}
