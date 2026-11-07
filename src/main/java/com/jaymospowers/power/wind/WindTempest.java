package com.jaymospowers.power.wind;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ascension: the Tempest. A breeze killed by a wind that hasn't ascended gives up a Gale Heart {@link #HEART_CHANCE} of
 * the time; the heart ascends a wind that has mastered everything. The ascended ability, Cyclone Lance, is here too.
 */
public final class WindTempest {
	/** The chance a breeze killed by a wind (not yet ascended) gives up its heart. No flight needed, no mastery to get it. */
	public static final float HEART_CHANCE = 0.66F;

	/** Cyclone Lance: reach, hit radius around the line, damage (bypasses armour), knockback along the line, cooldown. */
	static final double LANCE_RANGE = 32.0;
	/** (0.16.24) The lance's film (ticks): side on, along it. */
	static final int LANCE_FILM_TICKS = 24;
	static final double LANCE_RADIUS = 0.6;
	static final float LANCE_DAMAGE = 6.0F;
	/** The lance's damage climbs from {@code LANCE_DAMAGE} at weather 1 (rain) to {@code LANCE_DAMAGE_MAX} at {@code LANCE_WEATHER_TOP} (a thunderstorm). */
	static final float LANCE_DAMAGE_MAX = 12.0F, LANCE_WEATHER_TOP = 1.3F;
	static final double LANCE_KNOCKBACK = 1.6;
	static final double LANCE_LIFT = 0.25;
	public static final int LANCE_COOLDOWN = 120;
	/** In a mode (Z), the lance also stuns what it hits for this long. */
	static final int LANCE_MODE_STUN = 50;
	/** Stormbound: lightning along the lance, at most this many bolts. */
	static final int LANCE_MAX_BOLTS = 3;
	/** Spiral particles per block of lance, per strand. */
	static final int LANCE_PARTICLES_PER_BLOCK = 2;

	/** Breezes killed (counted for the chime's pitch only since 0.7). Saved, kept through death; reset when the power goes. */
	public static final AttachmentType<Integer> BREEZE_KILLS = AttachmentRegistry.create(JaymosPowers.id("wind_breeze_kills"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());

	public static final ResourceKey<DamageType> LANCE_DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, JaymosPowers.id("wind_lance"));

	public static Item GALE_HEART;

	private WindTempest() {
	}

	static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("wind_gale_heart"));
		GALE_HEART = Registry.register(BuiltInRegistries.ITEM, key, new WindGaleHeart(new Item.Properties().setId(key)
			.stacksTo(1)
			.rarity(Rarity.RARE)
			.component(DataComponents.LORE, new ItemLore(List.of(
				Component.translatable("item.jaymospowers.wind_gale_heart.lore1").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
				Component.translatable("item.jaymospowers.wind_gale_heart.lore2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))))));
		ServerLivingEntityEvents.AFTER_DEATH.register(WindTempest::afterDeath);
	}

	public static int breezeKills(ServerPlayer player) {
		Integer kills = player.getAttached(BREEZE_KILLS);
		return kills == null ? 0 : kills;
	}

	static void setBreezeKills(ServerPlayer player, int kills) {
		if (kills <= 0) {
			player.removeAttached(BREEZE_KILLS);
		} else {
			player.setAttached(BREEZE_KILLS, kills);
		}
	}

	private static void afterDeath(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof Breeze breeze) || !(source.getEntity() instanceof ServerPlayer player) || !(breeze.level() instanceof ServerLevel level)) {
			return;
		}
		if (!Powers.has(player, WindPower.INSTANCE) || Fate.isAscended(player)) {
			return;
		}
		int kills = breezeKills(player) + 1;
		setBreezeKills(player, kills);
		// a faint answering chime from the dying breeze, a little higher each time
		float pitch = 0.6F + 0.15F * Math.min(kills, 5);
		level.playSound(null, breeze.getX(), breeze.getY(), breeze.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, pitch);
		if (player.getRandom().nextFloat() < HEART_CHANCE) {
			ItemStack heart = new ItemStack(GALE_HEART);
			breeze.spawnAtLocation(level, heart);
			level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, breeze.getX(), breeze.getY() + 0.5, breeze.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ParticleTypes.END_ROD, breeze.getX(), breeze.getY() + 0.5, breeze.getZ(), 20, 0.3, 0.4, 0.3, 0.05);
			level.playSound(null, breeze.getX(), breeze.getY(), breeze.getZ(), SoundEvents.BREEZE_DEATH, SoundSource.HOSTILE, 1.5F, 0.5F);
			level.playSound(null, breeze.getX(), breeze.getY(), breeze.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5F, 0.5F);
		}
	}

	// ---- Cyclone Lance --------------------------------------------------------------------------------------------

	/** Fires the lance along the look direction. The caller checks the cooldown. */
	static void lance(ServerPlayer player, float weather) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(LANCE_RANGE));
		// stopped by walls
		HitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		final Vec3 tip = end;
		com.jaymospowers.core.UltimateFilm.play(player, com.jaymospowers.core.UltimateFilm.PROFILE, LANCE_FILM_TICKS, tip, null, 0.0);
		// the lance blows out the fire along it (a Stormbound lance's bolts fall after)
		WindDouse.line(level, eye, tip, LANCE_RADIUS + 1.0, player);
		// Stormbound only in the rain: under a clear sky it's the Tempest's own lance
		boolean stormbound = Fate.isCorrupted(player) && WindModes.thunderPower(level) > 0.0F;
		float damage = net.minecraft.util.Mth.clampedMap(weather, 1.0F, LANCE_WEATHER_TOP, LANCE_DAMAGE, LANCE_DAMAGE_MAX);
		DamageSource source = player.damageSources().source(LANCE_DAMAGE_TYPE, player);
		// (Jung's revamp) a bigger spiral shot with a line of wind down its middle, flying out to the tip (WindMoves)
		WindMoves.launchLance(player, eye.add(look.scale(1.0)).add(0.0, -0.2, 0.0), look, Math.max(1.0, eye.distanceTo(tip) - 1.0), damage, source, stormbound,
			WindModes.mode(player) != WindModes.NONE);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 1.2F, 1.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.4F, 0.6F);
	}
}
