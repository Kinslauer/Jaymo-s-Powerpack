package com.jaymoskitchen.climate;

import com.jaymoskitchen.JaymosKitchen;
import com.mojang.serialization.Codec;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * How wet a body is: Kitchen's own meter ({@link #METER}, 0 dry to {@link #MAX} wet through), after Legendary Survival
 * Overhaul's (ideas and numbers only). Every half second ({@link #STEP}) the rain adds {@link #RAIN}, standing in water
 * adds {@link #WATER} times how deep you stand in it, and otherwise you dry {@link #DRY}. Body heat reads the chill from
 * it ({@link BodyHeat}); sneak-using a filled water bottle or waterskin pours it over you ({@link #douse}), which also puts
 * out fire.
 * <p>
 * Jaymo's Powers' Wet and Soaked effects ({@code core/wet/Wet}), read by their ids so Kitchen never needs Powers, keep
 * the meter at least {@link #POWERS_WET} or {@link #MAX} while they last, so whatever wets you there (its spells, a
 * downpour) chills you here too.
 */
public final class Wetness {
	public static final int MAX = 100;
	/** Ticks between steps of the meter. */
	static final int STEP = 10;
	/** A step's change: out in the rain, standing in water (times how deep, feet to over the head), drying off. */
	static final int RAIN = 7, WATER = 10, DRY = 3;
	/** At or over this you're soaked through (clothes keep half their warmth). */
	public static final int SOAKED_AT = 80;
	/** Powers' Wet keeps the meter at least here; its Soaked keeps it full. */
	static final int POWERS_WET = 40;
	/** Ticks of Powers' wetness a douse gives (its full Soaked is three minutes). */
	static final int DOUSE_POWERS_TICKS = 20 * 120;

	/** The meter (absent = dry). Synced to you for the HUD. */
	public static final AttachmentType<Integer> METER = AttachmentRegistry.create(JaymosKitchen.id("wetness"),
		builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	static final Identifier WET = Identifier.fromNamespaceAndPath("jaymospowers", "wet"),
		SOAKED = Identifier.fromNamespaceAndPath("jaymospowers", "soaked");
	private static @Nullable Holder<MobEffect> wet, soaked;
	private static @Nullable Method powersSoak;
	private static boolean looked;

	private Wetness() {
	}

	private static void look() {
		if (!looked) {
			looked = true;
			wet = BuiltInRegistries.MOB_EFFECT.get(WET).orElse(null);
			soaked = BuiltInRegistries.MOB_EFFECT.get(SOAKED).orElse(null);
			try {
				powersSoak = Class.forName("com.jaymospowers.core.wet.Wet").getMethod("soak", LivingEntity.class, int.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				powersSoak = null;
			}
		}
	}

	/** How wet {@code player} is, 0 to {@link #MAX}. Both sides. */
	public static int meter(Player player) {
		return player.getAttachedOrElse(METER, 0);
	}

	static void set(ServerPlayer player, int wetness) {
		wetness = Mth.clamp(wetness, 0, MAX);
		if (wetness == 0) {
			player.removeAttached(METER);
		} else if (wetness != meter(player)) {
			player.setAttached(METER, wetness);
		}
	}

	/** Every {@link #STEP} ticks. */
	static void step(ServerPlayer player) {
		int wetness = meter(player);
		if (player.isInWater()) {
			// how deep: feet in a puddle to over the head
			double deep = Mth.clamp(player.getFluidHeight(FluidTags.WATER) / player.getBbHeight(), 0.1, 1.0);
			wetness += Math.max(1, (int) Math.round(WATER * deep));
		} else if (player.isInWaterOrRain()) {
			wetness += RAIN;
		} else {
			wetness -= DRY;
		}
		look();
		if (soaked != null && player.hasEffect(soaked)) {
			wetness = MAX;
		} else if (wet != null && player.hasEffect(wet)) {
			wetness = Math.max(wetness, POWERS_WET);
		}
		set(player, wetness);
	}

	/** How wet, as a share (0 to 1). */
	public static float share(Player player) {
		return meter(player) / (float) MAX;
	}

	/** Wet at all (the meter, or Powers' Wet or Soaked). */
	public static boolean wet(LivingEntity body) {
		look();
		return body instanceof Player player && meter(player) > 0 || wet != null && body.hasEffect(wet) || soaked(body);
	}

	/** Wet through: the meter at {@link #SOAKED_AT} or more, or Powers' Soaked. */
	public static boolean soaked(LivingEntity body) {
		look();
		return body instanceof Player player && meter(player) >= SOAKED_AT || soaked != null && body.hasEffect(soaked);
	}

	/** Water poured over yourself: wet through, and any fire on you goes out. Powers' Soaked comes with it. */
	static void douse(ServerPlayer player) {
		set(player, MAX);
		look();
		if (powersSoak != null) {
			try {
				powersSoak.invoke(null, player, DOUSE_POWERS_TICKS);
			} catch (ReflectiveOperationException e) {
				powersSoak = null;
			}
		}
		ServerLevel level = player.level();
		if (player.isOnFire()) {
			player.clearFire();
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7F, 1.2F);
			level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.8F, 1.0F);
		level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getEyeY() + 0.2, player.getZ(), 30, 0.3, 0.2, 0.3, 0.1);
		level.sendParticles(ParticleTypes.FALLING_WATER, player.getX(), player.getEyeY(), player.getZ(), 12, 0.3, 0.6, 0.3, 0.0);
	}
}
