package com.jaymoskitchen.climate;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.net.Payloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

import org.jspecify.annotations.Nullable;

import java.util.Random;

/**
 * The parasite from dirty water: slow, survivable, a reason to boil water rather than a death sentence. It lasts two
 * in-game days. From the start it eats your food ({@link #HUNGER}, like the Hunger effect) and your stomach growls now
 * and then ({@link #GROWL_EVERY}), loud enough for anyone near to hear. Once it's settled in ({@link #CRAMPS_AFTER}) it
 * cramps you too: a point of damage on random ticks (roughly once every 15 seconds), each with a louder growl and a
 * sickly flash at the edges of your screen ({@code ParasiteHud}, sent by {@link Payloads.Cramp}), and a faint queasy
 * tint at the edges for as long as the cramps last. Hungry enough, you may faint (Jaymo's Powers). A drink in
 * {@link #CURES} clears it ({@link Thirst#consumed}).
 */
public final class ParasiteEffect extends MobEffect {
	/** How long the parasite lasts (ticks): two in-game days, so curing it is worth the walk back to a fire. */
	static final int TICKS = 24_000 * 2;

	/** Food exhaustion a tick, plus as much again per amplifier: the Hunger effect's rate. */
	static final float HUNGER = 0.005F;

	/** A stomach growl rolls once in this many ticks on average: about once every 25 seconds. */
	static final int GROWL_EVERY = 20 * 25;

	/** The cramps (and their damage) start this long into the parasite (ticks): a third of the way, about 13 minutes. */
	public static final int CRAMPS_AFTER = TICKS / 3;

	/** Damage ticks roll once in this many ticks on average: about once every 15 seconds. */
	static final int ROLL_EVERY = 20 * 15;

	/** Damage per roll, plus one per amplifier. */
	static final float DAMAGE = 1.0F;

	/** The damage type the parasite's damage is recorded as: data/jaymoskitchen/damage_type/parasite.json. */
	static final ResourceKey<DamageType> PARASITE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, JaymosKitchen.id("parasite"));

	/** Drinks that wash it out: hot chocolate, and Jaymo's Powers' willow bark tea and chamomile tea when it's loaded
	 * (data/jaymoskitchen/tags/item/cures_parasite.json). */
	public static final TagKey<Item> CURES = TagKey.create(Registries.ITEM, JaymosKitchen.id("cures_parasite"));

	public static Holder<MobEffect> PARASITE;

	private static final Random RANDOM = new Random();

	private ParasiteEffect() {
		super(MobEffectCategory.HARMFUL, 0x556B2F);
	}

	static void register() {
		PARASITE = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
				ResourceKey.create(Registries.MOB_EFFECT, JaymosKitchen.id("parasite")),
				new ParasiteEffect());
	}

	@Override
	public boolean shouldApplyEffectTickThisTick(int tickCount, int amplifier) {
		return true;
	}

	@Override
	public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
		if (entity instanceof Player player) {
			player.causeFoodExhaustion(HUNGER * (amplifier + 1));
		}
		if (cramping(entity.getEffect(PARASITE)) && RANDOM.nextInt(ROLL_EVERY) == 0) {
			// slow and survivable: random cramps of damage, but never the killing blow
			float damage = DAMAGE + amplifier;
			growl(level, entity, 1.0F);
			if (entity instanceof ServerPlayer player) {
				ServerPlayNetworking.send(player, new Payloads.Cramp());
			}
			if (entity.getHealth() > damage) {
				DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(PARASITE_DAMAGE));
				entity.hurtServer(level, source, damage);
			}
		} else if (RANDOM.nextInt(GROWL_EVERY) == 0) {
			growl(level, entity, 0.5F);
		}
		return true;
	}

	/** Whether {@code parasite} has settled in far enough to cramp ({@link #CRAMPS_AFTER}). Both sides. */
	public static boolean cramping(@Nullable MobEffectInstance parasite) {
		return parasite != null && (parasite.isInfiniteDuration() || parasite.getDuration() <= TICKS - CRAMPS_AFTER);
	}

	/** A stomach growl at {@code entity}, heard by those near: a low gurgle and a wet slosh. */
	private static void growl(ServerLevel level, LivingEntity entity, float volume) {
		float pitch = 0.5F + RANDOM.nextFloat() * 0.15F;
		level.playSound(null, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(), SoundEvents.PLAYER_BURP,
			SoundSource.PLAYERS, 0.6F * volume, pitch);
		level.playSound(null, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(), SoundEvents.BUCKET_EMPTY,
			SoundSource.PLAYERS, 0.35F * volume, pitch + 0.1F);
	}
}
