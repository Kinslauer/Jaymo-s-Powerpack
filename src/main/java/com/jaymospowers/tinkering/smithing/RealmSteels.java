package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.Tinkering;
import com.jaymospowers.tinkering.kiln.Kiln;
import com.jaymospowers.tinkering.smeltery.Molten;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

/**
 * Medieval picks (me-alloys): the realm steels, each its people's. At the Alloyer three of steel to one of lead run into
 * the Stoneborn's <b>deep steel</b>, three of steel to one of silver into the forest folk's <b>bright steel</b>, and bronze
 * with ash into the orcs' crude <b>ash bronze</b> ({@code Melting.ALLOYS}). They cast into ingots, heat like any ingot and
 * forge everything steel does ({@link #forgesAs}), but only a smith who knows the metal can work it on the anvil
 * ({@link #knows}, which Jaymo's Kingdoms hands over: one of that people, or sworn to a haven of theirs). What is forged
 * of them keeps its metal ({@link #REALM_STEEL}, carried head to tool): deep steel is stubborn to work and heavy, its
 * blows shove harder and its armour stands firmer; bright steel bites the undead as silver does; ash bronze is kind to
 * work but crude, never better than Expertly Forged. Lead is a new ore deep under the mountains (worldgen/feature/ore_lead.json).
 * Art, data and lang: tools/gen_medieval_smithing.py.
 */
public final class RealmSteels {
	// ---- tuning ----
	/** Deep steel: the extra shove each weapon blow gives, and the knockback resistance each armour piece adds. */
	public static final double DEEP_SHOVE = 0.3, DEEP_STEADY = 0.05;
	/** Bright steel: the extra damage each weapon blow does to the undead. */
	public static final float BRIGHT_UNDEAD_DAMAGE = 2.0F;
	/** The silver-white sparks of its bite. */
	static final int BRIGHT_SPARK = 0xE6EEF6;
	/** Ash bronze: the best grade its crude metal takes. */
	public static final Grade ASH_CAP = Grade.EXPERT;

	/** The realm steel a forging blank, head, weapon or armour piece is of (a {@link Molten} id), when it is one. */
	public static DataComponentType<String> REALM_STEEL;
	public static Block LEAD_ORE, DEEPSLATE_LEAD_ORE;
	public static Item RAW_LEAD, LEAD_INGOT, LEAD_NUGGET;

	/** Whether this smith knows how to work this realm steel (its {@link Molten} id). Jaymo's Kingdoms sets it (by reflection); alone, everyone does. */
	public static BiPredicate<ServerPlayer, String> knows = (smith, metal) -> true;

	static final Identifier STEADY_ID = JaymosPowers.id("deep_steel_steady");
	private static boolean biting;

	private RealmSteels() {
	}

	public static boolean realm(@Nullable Molten metal) {
		return metal == Molten.DEEP_STEEL || metal == Molten.BRIGHT_STEEL || metal == Molten.ASH_BRONZE;
	}

	/** The metal whose forgings this one makes: a realm steel makes steel's. */
	public static @Nullable Molten forgesAs(@Nullable Molten metal) {
		return realm(metal) ? Molten.STEEL : metal;
	}

	/** The realm steel on the anvil (a Hot Ingot of it, or a blank started from one), or null. */
	public static @Nullable Molten of(ItemStack work) {
		Molten metal = work.is(Smithing.HOT_INGOT) ? HotMetal.metal(work) : Molten.byId(work.getOrDefault(REALM_STEEL, ""));
		return realm(metal) ? metal : null;
	}

	/** The metal a forging on the anvil is really of, for how hard it works. */
	static Molten working(ItemStack work, Molten metal) {
		Molten realm = of(work);
		return realm == null ? metal : realm;
	}

	/** From {@link Smithing#register}: after the smeltery's ingots. */
	static void register() {
		REALM_STEEL = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("realm_steel"),
			DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
		LEAD_ORE = block("lead_ore", BlockBehaviour.Properties.of().mapColor(MapColor.STONE)
			.instrument(NoteBlockInstrument.BASEDRUM).strength(3.0F, 3.0F).requiresCorrectToolForDrops());
		DEEPSLATE_LEAD_ORE = block("deepslate_lead_ore", BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE)
			.instrument(NoteBlockInstrument.BASEDRUM).strength(4.5F, 3.0F).sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops());
		RAW_LEAD = Smithing.item("raw_lead", Item::new, Function.identity());
		LEAD_INGOT = Smithing.item("lead_ingot", Item::new, Function.identity());
		LEAD_NUGGET = Smithing.item("lead_nugget", Item::new, Function.identity());
		Tinkering.ALLOY_INGOTS.put(Molten.LEAD, LEAD_INGOT);
		for (Molten steel : List.of(Molten.DEEP_STEEL, Molten.BRIGHT_STEEL, Molten.ASH_BRONZE)) {
			Tinkering.ALLOY_INGOTS.put(steel, Smithing.item(steel.id + "_ingot", Item::new, Function.identity()));
		}
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_MOUNTAIN), GenerationStep.Decoration.UNDERGROUND_ORES,
			ResourceKey.create(Registries.PLACED_FEATURE, JaymosPowers.id("ore_lead")));

		Forging.CHECKS.add((smith, anvil, option) -> {
			Molten metal = of(anvil.work());
			return metal != null && !smith.isCreative() && !knows.test(smith, metal.id)
				? Component.translatable("message.jaymospowers.smithing.realm_steel", metal.displayName()) : null;
		});
		Forging.CAPS.add((smith, anvil, option) -> of(anvil.work()) == Molten.ASH_BRONZE ? ASH_CAP : null);
		Forging.FINISHES.add((smith, anvil, option, made) -> {
			Molten metal = of(anvil.work());
			if (metal != null) {
				made.set(REALM_STEEL, metal.id);
				// (the armour's steadiness is baked into its numbers)
				Grades.bake(made);
			}
		});
		Grades.CARRIERS.add((head, made) -> {
			String metal = head.get(REALM_STEEL);
			if (metal != null) {
				made.set(REALM_STEEL, metal);
			}
		});
		Grades.ATTRIBUTE_PASSES.add((stack, attributes) -> {
			if (!Molten.DEEP_STEEL.id.equals(stack.get(REALM_STEEL))) {
				return attributes;
			}
			EquipmentSlotGroup armour = null;
			for (ItemAttributeModifiers.Entry entry : attributes.modifiers()) {
				if (entry.attribute().equals(Attributes.ARMOR)) {
					armour = entry.slot();
				}
			}
			return armour == null ? attributes : attributes.withModifierAdded(Attributes.KNOCKBACK_RESISTANCE,
				new AttributeModifier(STEADY_ID, DEEP_STEADY, AttributeModifier.Operation.ADD_VALUE), armour);
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (biting || damageTaken <= 0.0F || blocked || !(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker
				|| !(entity.level() instanceof ServerLevel level)) {
				return;
			}
			String metal = attacker.getMainHandItem().get(REALM_STEEL);
			if (Molten.DEEP_STEEL.id.equals(metal)) {
				entity.knockback(DEEP_SHOVE, attacker.getX() - entity.getX(), attacker.getZ() - entity.getZ(), source, damageTaken);
			} else if (Molten.BRIGHT_STEEL.id.equals(metal) && entity.is(EntityTypeTags.UNDEAD) && entity.isAlive()) {
				biting = true;
				try {
					entity.setInvulnerableTime(0);
					entity.hurtServer(level, level.damageSources().magic(), BRIGHT_UNDEAD_DAMAGE);
				} finally {
					biting = false;
				}
				level.sendParticles(com.jaymospowers.core.fx.MagicFx.spark(BRIGHT_SPARK, 0.6F), entity.getX(), entity.getY(0.6), entity.getZ(), 8,
					0.25, 0.3, 0.25, 0.05);
				level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.6F);
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(Kiln.tab()).register(output -> {
			output.accept(LEAD_ORE);
			output.accept(DEEPSLATE_LEAD_ORE);
			output.accept(RAW_LEAD);
			output.accept(LEAD_NUGGET);
		});
	}

	private static Block block(String id, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, new Block(properties.setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}
}
