package com.jaymospowers.power.darktide;

import com.jaymospowers.JaymosPowers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;

/**
 * The shade: a Darktide user who dies at night comes back as their own shadow instead (once a night). The shade lives
 * until dawn on the overworld's clock; if it's under open sky when the sun comes up it burns away and the user dies for
 * real, and if it's sheltered (a roof, a cave, deep water, the Nether or the End) the user is flesh again. A shade that
 * dies again before dawn just dies. Saved in {@link DarktideState.Save#shade()}, so logging out doesn't dodge the dawn.
 */
final class DarktideShade {
	/** Health the shade comes back with. */
	static final float REBIRTH_HEALTH = 10.0F;
	/** The screen goes dark this long as the shadow takes over (ticks). */
	static final int REBIRTH_DARKNESS_TICKS = 60;
	/** A shade is warned this long before dawn (ticks, 30 s). */
	static final long DAWN_WARNING_TICKS = 600L;
	/** The dawn burning a shade away under open sky: {@code jaymospowers:darktide_dawn}, through everything. */
	static final ResourceKey<DamageType> DAWN = ResourceKey.create(Registries.DAMAGE_TYPE, JaymosPowers.id("darktide_dawn"));

	private DarktideShade() {
	}

	/** Whether {@code entity} is walking as their shadow. Both sides (synced). */
	static boolean shade(LivingEntity entity) {
		return entity.getAttached(DarktideState.SHADE) != null;
	}

	/** A Darktide user's death at night becomes the shade's rising (the shade's own death is final). */
	static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || !DarktidePower.has(player) || player.isSpectator()) {
			return true;
		}
		// already a shade, day, a dimension without night, /kill or the void: dies for real; a held totem goes first
		if (DarktideState.save(player).shade() || !DarktideFlow.night(player.level()) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
				|| player.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
			return true;
		}
		rise(player);
		return false;
	}

	/** A real death ends the shade, so the respawned player (the save is copied on death) starts whole. */
	static void afterDeath(LivingEntity entity, DamageSource source) {
		if (entity instanceof ServerPlayer player && DarktideState.save(player).shade()) {
			setShade(player, false);
		}
	}

	static void rise(ServerPlayer player) {
		player.setHealth(REBIRTH_HEALTH);
		player.removeAllEffects();
		player.clearFire();
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, REBIRTH_DARKNESS_TICKS, 0, false, false, true));
		setShade(player, true);
		DarktideLive live = DarktidePower.live(player);
		live.flow = DarktideFlow.MAX;
		live.dawnWarned = false;
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.4, 0.8, 0.4, 0.05);
		level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.3, 0.6, 0.3, 0.02);
		whisper(player, "shade_rises", SoundEvents.WARDEN_EMERGE, ChatFormatting.DARK_AQUA);
	}

	static void setShade(ServerPlayer player, boolean on) {
		player.setAttached(DarktideState.SAVE, DarktideState.save(player).withShade(on));
		if (on) {
			player.setAttached(DarktideState.SHADE, true);
		} else {
			player.removeAttached(DarktideState.SHADE);
		}
	}

	/** Night on the overworld's clock (the shade keeps the overworld's hours wherever it goes). */
	static boolean overworldNight(long clock) {
		long time = Math.floorMod(clock, DarktidePower.DAY_TICKS);
		return time >= DarktideFlow.NIGHT_START && time < DarktideFlow.NIGHT_END;
	}

	/** Under open sky in a world with a sun, and not under water. */
	static boolean outdoors(ServerPlayer player) {
		ServerLevel level = player.level();
		return !level.dimensionType().hasFixedTime() && level.canSeeSky(BlockPos.containing(player.getEyePosition())) && !player.isUnderWater();
	}

	static void tick(ServerPlayer player, DarktideLive live) {
		boolean shade = DarktideState.save(player).shade();
		if (shade != shade(player)) {
			// joining as a shade: the drawn-dark flag isn't saved, only the save is
			setShade(player, shade);
		}
		if (!shade) {
			return;
		}
		long clock = player.level().getOverworldClockTime();
		if (overworldNight(clock)) {
			long time = Math.floorMod(clock, DarktidePower.DAY_TICKS);
			if (!live.dawnWarned && time >= DarktideFlow.NIGHT_END - DAWN_WARNING_TICKS) {
				live.dawnWarned = true;
				whisper(player, "shade_dawn_near", SoundEvents.CONDUIT_AMBIENT_SHORT, ChatFormatting.DARK_AQUA);
			}
			if (player.tickCount % 10 == 0) {
				player.level().sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.25, 0.5, 0.25, 0.0);
			}
			return;
		}
		live.dawnWarned = false;
		setShade(player, false);
		if (outdoors(player) && !player.isCreative()) {
			ServerLevel level = player.level();
			level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.4, 0.8, 0.4, 0.03);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 0.6F);
			player.hurtServer(level, new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAWN)), Float.MAX_VALUE);
		} else {
			whisper(player, "shade_survives", SoundEvents.CONDUIT_ACTIVATE, ChatFormatting.AQUA);
		}
	}

	private static void whisper(ServerPlayer player, String key, SoundEvent sound, ChatFormatting colour) {
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.darktide." + key).withStyle(colour, ChatFormatting.ITALIC));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 0.8F, 0.6F);
	}
}
