package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The spore-rot: an effect that wears the body down, one point every {@link #BASE_INTERVAL} ticks (halved each level, never
 * under {@link #MIN_INTERVAL}) and never below {@link #FLOOR} health. Milk, a forager's stew, the antifungal tonic and any
 * healing spell cure it. Those who die carrying it rise as shamblers: Jaymo's Kingdoms listens for that by this effect's
 * id, so Powers needs no Kingdoms. Fungal things (Kingdoms' {@code fungal} entity tag) and anyone under a spore ward cannot
 * carry it.
 */
public final class Mycosis extends MobEffect {
	// ---- tuning ----
	/** Ticks between points of damage at level 0, and the fewest at any level. */
	public static final int BASE_INTERVAL = 80, MIN_INTERVAL = 10;
	/** Health it will not take anyone under (one heart). */
	public static final float FLOOR = 2.0F;
	/** A deathcap eaten raw gives the rot this long (ticks), and the tonic's ward lasts this long. */
	public static final int DEATHCAP_TICKS = 400, TONIC_WARD_TICKS = 3600;

	private static final TagKey<EntityType<?>> FUNGAL = TagKey.create(Registries.ENTITY_TYPE,
		Identifier.fromNamespaceAndPath("jaymoskingdoms", "fungal"));

	public static Holder<MobEffect> MYCOSIS;
	public static Holder<MobEffect> SPORE_WARD;

	private final boolean rot;

	private Mycosis(MobEffectCategory category, int colour, boolean rot) {
		super(category, colour);
		this.rot = rot;
	}

	static void register() {
		MYCOSIS = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
			ResourceKey.create(Registries.MOB_EFFECT, JaymosPowers.id("mycosis")), new Mycosis(MobEffectCategory.HARMFUL, 0x7FB069, true));
		SPORE_WARD = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
			ResourceKey.create(Registries.MOB_EFFECT, JaymosPowers.id("spore_ward")), new Mycosis(MobEffectCategory.BENEFICIAL, 0xD9C9A3, false));
	}

	public static boolean isImmune(LivingEntity entity) {
		return entity.is(FUNGAL) || entity.hasEffect(SPORE_WARD);
	}

	/** Washes the rot out of {@code entity}. True if it had it. */
	public static boolean cure(LivingEntity entity) {
		return entity.removeEffect(MYCOSIS);
	}

	/** {@code stack} was eaten or drunk by {@code player} (called from {@link Afflictions#consumed}). */
	static void consumed(ServerPlayer player, ItemStack stack) {
		if (stack.is(Items.MILK_BUCKET) || stack.is(MedicineItems.ANTIFUNGAL_TONIC) || isKitchen(stack, "foragers_stew")
			|| isKitchen(stack, "milk_bottle")) {
			cure(player);
		}
		if (stack.is(MedicineItems.ANTIFUNGAL_TONIC)) {
			player.addEffect(new MobEffectInstance(SPORE_WARD, TONIC_WARD_TICKS, 0, true, false, true));
		} else if (isKitchen(stack, "deathcap") && !isImmune(player)) {
			player.addEffect(new MobEffectInstance(MYCOSIS, DEATHCAP_TICKS));
		}
	}

	private static boolean isKitchen(ItemStack stack, String id) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(Identifier.fromNamespaceAndPath("jaymoskitchen", id));
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
		if (!this.rot) {
			return false;
		}
		int interval = Math.max(MIN_INTERVAL, BASE_INTERVAL >> Math.min(amplifier, 10));
		return duration % interval == 0;
	}

	@Override
	public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
		if (this.rot) {
			if (isImmune(entity)) {
				return false;
			}
			if (entity.getHealth() > FLOOR) {
				entity.hurtServer(level, level.damageSources().magic(), 1.0F);
			}
		}
		return true;
	}
}
