package com.jaymospowers.power.darktide;

import com.jaymospowers.JaymosPowers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The shade: a Darktide user who dies at night comes back as their own shadow instead (once a night). The shade can't
 * stand the sun: in sunlight (a world with a sun, bright out, eyes under open sky, not under water) it smoulders for
 * {@link #SUN_GRACE_TICKS} and burns away. The user dies for real, every item they carry turns to nothing and a little
 * {@link #ASH shadow ash} is all that drops. Hidden through the day (a roof, a cave, deep water, the rain, the Nether
 * or the End) it lasts until the next nightfall, when the user is flesh again. A shade that dies again just dies. Saved
 * in {@link DarktideState.Save#shadeUntil()}, so logging out doesn't dodge the sun.
 */
final class DarktideShade {
	/** Health the shade comes back with. */
	static final float REBIRTH_HEALTH = 10.0F;
	/** The screen goes dark this long as the shadow takes over (ticks). */
	static final int REBIRTH_DARKNESS_TICKS = 60;
	/** A shade is warned this long before dawn (ticks, 30 s). */
	static final long DAWN_WARNING_TICKS = 600L;
	/** How long a shade can stand in sunlight before it burns away (ticks). */
	static final int SUN_GRACE_TICKS = 20;
	/** Shadow ash left where a shade burned away. */
	static final int ASH_DROPPED = 3;
	/** The black dust a shade sheds as it moves: colour and size, and how many specks every {@link #DUST_EVERY} ticks. */
	static final DustParticleOptions DUST = new DustParticleOptions(0x0B0B0F, 1.3F);
	static final int DUST_EVERY = 2, DUST_COUNT = 3;
	/** The sun burning a shade away: {@code jaymospowers:darktide_dawn}, through everything. */
	static final ResourceKey<DamageType> DAWN = ResourceKey.create(Registries.DAMAGE_TYPE, JaymosPowers.id("darktide_dawn"));

	/** {@code jaymospowers:shadow_ash}: what's left of a shade the sun caught. */
	static Item ASH;

	private DarktideShade() {
	}

	static void registerItems() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("shadow_ash"));
		ASH = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
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
		if (DarktideState.save(player).shadeUntil() > 0L) {
			// a shadow leaves no body behind (Jaymo's Kitchen would lay one down)
			player.addTag(DarktideTide.NO_CORPSE_TAG);
			return true;
		}
		if (!DarktideFlow.night(player.level()) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
				|| player.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
			return true;
		}
		rise(player);
		return false;
	}

	/** A real death ends the shade, so the respawned player (the save is copied on death) starts whole. */
	static void afterDeath(LivingEntity entity, DamageSource source) {
		if (entity instanceof ServerPlayer player && DarktideState.save(player).shadeUntil() > 0L) {
			setShade(player, 0L);
		}
	}

	static void rise(ServerPlayer player) {
		player.setHealth(REBIRTH_HEALTH);
		player.removeAllEffects();
		player.clearFire();
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, REBIRTH_DARKNESS_TICKS, 0, false, false, true));
		setShade(player, DarktidePower.nextNightfall(player.level().getOverworldClockTime()));
		DarktideLive live = DarktidePower.live(player);
		live.flow = DarktideFlow.MAX;
		live.dawnWarned = false;
		live.sunTicks = 0;
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.4, 0.8, 0.4, 0.05);
		level.sendParticles(DUST, player.getX(), player.getY() + 1.0, player.getZ(), 80, 0.5, 0.9, 0.5, 0.0);
		level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.3, 0.6, 0.3, 0.02);
		whisper(player, "shade_rises", SoundEvents.WARDEN_EMERGE, ChatFormatting.DARK_AQUA);
	}

	/** Makes {@code player} a shade until the overworld clock reaches {@code until}, or ends it ({@code 0}). */
	static void setShade(ServerPlayer player, long until) {
		player.setAttached(DarktideState.SAVE, DarktideState.save(player).withShadeUntil(until));
		setDrawnDark(player, until > 0L);
	}

	private static void setDrawnDark(ServerPlayer player, boolean on) {
		if (on) {
			player.setAttached(DarktideState.SHADE, true);
		} else {
			player.removeAttached(DarktideState.SHADE);
		}
	}

	/** In sunlight: a world with a sun, bright out (not night, not rain), eyes under open sky and not under water. */
	static boolean sunlit(ServerPlayer player) {
		ServerLevel level = player.level();
		return !level.dimensionType().hasFixedTime() && level.isBrightOutside() && level.canSeeSky(BlockPos.containing(player.getEyePosition()))
			&& !player.isUnderWater();
	}

	static void tick(ServerPlayer player, DarktideLive live) {
		if (player.isAlive()) {
			// (only a dead shade's body needs it)
			player.removeTag(DarktideTide.NO_CORPSE_TAG);
		}
		long until = DarktideState.save(player).shadeUntil();
		if (until > 0L != shade(player)) {
			// joining as a shade: the drawn-dark flag isn't saved, only the save is
			setDrawnDark(player, until > 0L);
		}
		if (until <= 0L) {
			return;
		}
		long clock = player.level().getOverworldClockTime();
		if (until - clock > DarktidePower.DAY_TICKS) {
			// the clock was turned back: the shade still lasts only until the coming nightfall
			until = DarktidePower.nextNightfall(clock);
			setShade(player, until);
		}
		if (clock >= until) {
			setShade(player, 0L);
			live.sunTicks = 0;
			whisper(player, "shade_survives", SoundEvents.CONDUIT_ACTIVATE, ChatFormatting.AQUA);
			return;
		}
		long time = Math.floorMod(clock, DarktidePower.DAY_TICKS);
		boolean night = time >= DarktideFlow.NIGHT_START && time < DarktideFlow.NIGHT_END;
		if (night && !live.dawnWarned && time >= DarktideFlow.NIGHT_END - DAWN_WARNING_TICKS) {
			live.dawnWarned = true;
			whisper(player, "shade_dawn_near", SoundEvents.CONDUIT_AMBIENT_SHORT, ChatFormatting.DARK_AQUA);
		} else if (!night) {
			live.dawnWarned = false;
		}
		ServerLevel level = player.level();
		if (player.tickCount % DUST_EVERY == 0) {
			// black dust off the whole body, drifting down; now and then a wisp of ash
			level.sendParticles(DUST, player.getX(), player.getY() + 0.9, player.getZ(), DUST_COUNT, 0.3, 0.55, 0.3, 0.0);
			if (player.tickCount % 10 == 0) {
				level.sendParticles(ParticleTypes.ASH, player.getX(), player.getY() + 1.0, player.getZ(), 4, 0.35, 0.6, 0.35, 0.0);
			}
		}
		if (!sunlit(player) || player.isCreative()) {
			live.sunTicks = 0;
			return;
		}
		if (live.sunTicks++ == 0) {
			whisper(player, "shade_sunlit", SoundEvents.FIRE_AMBIENT, ChatFormatting.GOLD);
		}
		level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.3, 0.6, 0.3, 0.02);
		if (live.sunTicks >= SUN_GRACE_TICKS) {
			live.sunTicks = 0;
			burn(player);
		}
	}

	/** The sun caught the shade: everything it carries goes with it, a little ash is left, and the user dies for real. */
	static void burn(ServerPlayer player) {
		ServerLevel level = player.level();
		setShade(player, 0L);
		// Tide Arms hands the real items back first, so they go too
		DarktideTide.revertArms(player, false);
		player.getInventory().clearContent();
		player.inventoryMenu.getCraftSlots().clearContent();
		player.containerMenu.setCarried(ItemStack.EMPTY);
		player.containerMenu.broadcastChanges();
		ItemEntity ash = new ItemEntity(level, player.getX(), player.getY() + 0.2, player.getZ(), new ItemStack(ASH, ASH_DROPPED));
		ash.setDefaultPickUpDelay();
		level.addFreshEntity(ash);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.4, 0.8, 0.4, 0.03);
		level.sendParticles(ParticleTypes.ASH, player.getX(), player.getY() + 1.0, player.getZ(), 80, 0.5, 1.0, 0.5, 0.0);
		level.sendParticles(ParticleTypes.WHITE_ASH, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 1.0, 0.5, 0.0);
		level.sendParticles(DUST, player.getX(), player.getY() + 1.0, player.getZ(), 120, 0.6, 1.0, 0.6, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 0.6F);
		// nothing but ash: no body for Jaymo's Kitchen to lay down
		player.addTag(DarktideTide.NO_CORPSE_TAG);
		player.hurtServer(level, new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAWN)), Float.MAX_VALUE);
	}

	private static void whisper(ServerPlayer player, String key, SoundEvent sound, ChatFormatting colour) {
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.darktide." + key).withStyle(colour, ChatFormatting.ITALIC));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 0.8F, 0.6F);
	}
}
