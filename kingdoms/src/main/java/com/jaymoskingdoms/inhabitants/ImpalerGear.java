package com.jaymoskingdoms.inhabitants;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;

/**
 * What an {@link Impaler} leaves behind, and what it does to you, after Inhabitants (JeremySeq / Team Synapse, MIT; the
 * item art and sounds are theirs: kingdoms/art/impaler-CREDITS.md). Andy (2026-11-13), asked whether the spikes should drop
 * for concussive arrows and a hand drill as in Inhabitants: "sure".
 *
 * <ul>
 * <li>The <b>Impaler spike</b>: a dead Impaler's arm gives up the spikes it hadn't thrown.
 * <li><b>Concussion</b>: a spike to the head (thrown by an Impaler, or on a concussive arrow) rings the skull. You move slower,
 * your eyes lag behind your head, the world goes muffled and your ears ring.
 * <li>The <b>concussive arrow</b>: a spike, a stick and a feather make four.
 * <li>The <b>spike drill</b>: three spikes turned by copper and redstone. It bites faster the longer it runs, and heats as it
 * goes; too hot and it burns the hand that holds it. A snowball dropped on it in the inventory cools it.
 * </ul>
 */
public final class ImpalerGear {
	/** Concussion: this much slower (a share of your speed), for this long from a thrown spike and from an arrow (ticks). */
	static final double CONCUSSED_SLOWER = -0.3;
	public static final int SPIKE_CONCUSSION = 100, ARROW_CONCUSSION = 120;
	/** The concussion's colour in the effect list: bruise grey-purple. */
	static final int CONCUSSION_COLOUR = 0x7A6A86;

	public static Item IMPALER_SPIKE, CONCUSSIVE_ARROW, SPIKE_DRILL;
	public static Holder<MobEffect> CONCUSSION;
	public static EntityType<ConcussiveArrow> CONCUSSIVE_ARROW_ENTITY;

	public static SoundEvent IMPALER_ATTACK, IMPALER_DEATH, IMPALER_HURT, IMPALER_IDLE, IMPALER_SCREAM, IMPALER_SPIKES, CONCUSSION_HIT,
		CONCUSSION_BUZZ, DRILL_START, DRILL_DIG, DRILL_STOP;

	private ImpalerGear() {
	}

	static void register() {
		IMPALER_ATTACK = sound("impaler.attack");
		IMPALER_DEATH = sound("impaler.death");
		IMPALER_HURT = sound("impaler.hurt");
		IMPALER_IDLE = sound("impaler.idle");
		IMPALER_SCREAM = sound("impaler.scream");
		IMPALER_SPIKES = sound("impaler.spikes");
		CONCUSSION_HIT = sound("impaler.concussion");
		CONCUSSION_BUZZ = sound("effect.concussion.buzz");
		DRILL_START = sound("item.spike_drill.start");
		DRILL_DIG = sound("item.spike_drill.dig");
		DRILL_STOP = sound("item.spike_drill.stop");

		CONCUSSION = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, JaymosKingdoms.id("concussion"),
			new Concussion().addAttributeModifier(Attributes.MOVEMENT_SPEED, JaymosKingdoms.id("concussion"), CONCUSSED_SLOWER,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

		IMPALER_SPIKE = item("impaler_spike", Item::new, new Item.Properties());
		CONCUSSIVE_ARROW = item("concussive_arrow", ConcussiveArrowItem::new, new Item.Properties());
		SPIKE_DRILL = item("spike_drill", SpikeDrill::new, new Item.Properties().pickaxe(ToolMaterial.IRON, 1.0F, -2.8F)
			.component(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA, SpikeDrill.COLD));

		ResourceKey<EntityType<?>> arrow = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("concussive_arrow"));
		CONCUSSIVE_ARROW_ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE, arrow,
			EntityType.Builder.<ConcussiveArrow>of(ConcussiveArrow::new, MobCategory.MISC).sized(0.5F, 0.5F).eyeHeight(0.13F)
				.clientTrackingRange(4).updateInterval(20).build(arrow));

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.insertAfter(Items.BONE, IMPALER_SPIKE));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.insertAfter(Items.SPECTRAL_ARROW, CONCUSSIVE_ARROW));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.insertAfter(Items.IRON_PICKAXE, SPIKE_DRILL));
	}

	/** A spike to the head: concussed for {@code ticks}, with the crack of it. */
	public static void concuss(LivingEntity hit, int ticks) {
		if (CONCUSSION == null) {
			return;
		}
		hit.addEffect(new MobEffectInstance(CONCUSSION, ticks));
		hit.level().playSound(null, hit.getX(), hit.getEyeY(), hit.getZ(), CONCUSSION_HIT, hit.getSoundSource(), 1.0F, 1.0F);
	}

	private static Item item(String name, Function<Item.Properties, Item> make, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, make.apply(properties.setId(key)));
	}

	private static SoundEvent sound(String name) {
		Identifier id = JaymosKingdoms.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/** Concussion: its slowness is an attribute; the lagging eyes, muffled ears and ringing are drawn on the client. */
	static final class Concussion extends MobEffect {
		Concussion() {
			super(MobEffectCategory.HARMFUL, CONCUSSION_COLOUR);
		}
	}
}
