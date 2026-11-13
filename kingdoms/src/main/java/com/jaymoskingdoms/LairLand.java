package com.jaymoskingdoms;

import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/**
 * The land around a lair goes wrong (fifth edition's regional effects, Andy's pick D4). Within {@link GreatLairs.Kind#chunks}
 * chunks of a great lair ({@link GreatLairs}: a wild dragon's den, the minotaur's labyrinth, a necromancer's crypt) you see
 * it before you are told, and all of it lifts the day its master falls:
 * <ul>
 * <li><b>Foul springs</b>: green scum and sour bubbles rise off the water, and a drink of it (a sip, a bottle, a cup) turns
 * the stomach: nausea and a little hurt, never enough to kill.</li>
 * <li><b>Scarce animals</b>: grazing animals and fowl rarely spawn ({@link #SCARCE} of tries fail). Herds a keeper already holds are left alone.</li>
 * <li><b>Quiet birds</b>: parrots and fowl (the tag {@link #BIRDS}) make no sound.</li>
 * <li><b>Bad dreams</b>: a sleeper hears things, and wakes weak, dizzy and not rested.</li>
 * <li><b>Withering crops</b>: crops will not ripen past half; the ones that were, slowly wilt back.</li>
 * </ul>
 */
public final class LairLand {
	/** The share of animal spawn tries in a lair's land that fail. */
	static final float SCARCE = 0.85F;
	/** The foul water's sickness: nausea (ticks) and hurt (half hearts); never below this health. */
	static final int SICK_TICKS = 20 * 10;
	static final float SICK_HURT = 1.0F, SICK_LEAST = 4.0F;
	/** Foul springs: how far from a player the scum is looked for and how many spots a second. */
	static final int SCUM_REACH = 14, SCUM_TRIES = 8;
	/** Withering: how often (ticks), how many spots tried round each player, how far; crops past this share of ripe wilt back one stage. */
	static final int WITHER_EVERY = 40, WITHER_TRIES = 30, WITHER_REACH = 24;
	static final float RIPE_CAP = 0.5F;
	/** Bad dreams: how often a sleeper hears something (ticks), and what they wake to (ticks). */
	static final int DREAM_EVERY = 20 * 6, WEAK_TICKS = 20 * 60, DIZZY_TICKS = 20 * 8, DARK_TICKS = 20 * 5;

	/** Birds that go quiet. */
	public static final TagKey<net.minecraft.world.entity.EntityType<?>> BIRDS = TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE,
		JaymosKingdoms.id("songbirds"));

	private LairLand() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(LairLand::tick);
		EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer player && player.level() instanceof ServerLevel level && GreatLairs.at(level, pos) != null) {
				wakeBadly(level, player);
			}
		});
		// Kitchen's drinks (a sip, a bottle, a cup) are heard by its own list
		if (FabricLoader.getInstance().isModLoaded("jaymoskitchen")) {
			try {
				Object list = Class.forName("com.jaymoskitchen.climate.Thirst").getField("DRANK").get(null);
				@SuppressWarnings("unchecked")
				List<java.util.function.Consumer<ServerPlayer>> drank = (List<java.util.function.Consumer<ServerPlayer>>)list;
				drank.add(LairLand::drank);
			} catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
				JaymosKingdoms.LOGGER.warn("Couldn't hear Jaymo's Kitchen's drinks (older Kitchen?)", e);
			}
		}
	}

	/** Whether {@code pos} is in the land of a lair that still stands. */
	public static boolean inLand(ServerLevel level, BlockPos pos) {
		return GreatLairs.at(level, pos) != null;
	}

	/** Whether an animal spawn try at {@code pos} fails: the land is bare. */
	public static boolean scarce(ServerLevel level, BlockPos pos) {
		return level.getRandom().nextFloat() < SCARCE && inLand(level, pos);
	}

	/** Whether {@code mob} is a songbird in a lair's land: it makes no sound. */
	public static boolean quiet(Mob mob) {
		return mob.level() instanceof ServerLevel level && mob.is(BIRDS) && inLand(level, mob.blockPosition());
	}

	/** A drink of vanilla water bottle (when Kitchen isn't there to tell of its own). */
	public static void sipped(net.minecraft.world.entity.LivingEntity who, ItemStack stack) {
		if (who instanceof ServerPlayer player && !FabricLoader.getInstance().isModLoaded("jaymoskitchen") && stack.is(Items.POTION)) {
			var potion = stack.get(DataComponents.POTION_CONTENTS);
			if (potion != null && potion.is(Potions.WATER)) {
				drank(player);
			}
		}
	}

	/** {@code player} drank water: in a lair's land it is foul. */
	public static void drank(ServerPlayer player) {
		ServerLevel level = player.level();
		if (!inLand(level, player.blockPosition()) || player.isCreative() || player.isSpectator()) {
			return;
		}
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, SICK_TICKS, 0, false, true, false));
		if (player.getHealth() > SICK_LEAST) {
			player.hurtServer(level, level.damageSources().magic(), SICK_HURT);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 0.5F);
		level.sendParticles(Fx.miasma(0x6B8A2A, 0.9F), player.getX(), player.getEyeY() - 0.2, player.getZ(), 8, 0.2, 0.15, 0.2, 0.01);
	}

	private static void tick(MinecraftServer server) {
		long t = server.getTickCount();
		if (t % 20 != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (GreatLairs.standing(level).isEmpty()) {
				continue;
			}
			for (ServerPlayer player : level.players()) {
				if (!player.isAlive() || player.isSpectator() || !inLand(level, player.blockPosition())) {
					continue;
				}
				scum(level, player);
				if (t % WITHER_EVERY == 0) {
					wither(level, player);
				}
				if (player.isSleeping() && t % DREAM_EVERY == 0) {
					dream(level, player);
				}
			}
		}
	}

	/** Green scum and sour bubbles on the open water near the player. */
	private static void scum(ServerLevel level, ServerPlayer player) {
		BlockPos mid = player.blockPosition();
		for (int i = 0; i < SCUM_TRIES; i++) {
			BlockPos p = mid.offset(level.getRandom().nextInt(SCUM_REACH * 2 + 1) - SCUM_REACH, level.getRandom().nextInt(9) - 4,
				level.getRandom().nextInt(SCUM_REACH * 2 + 1) - SCUM_REACH);
			if (level.getFluidState(p).isSource() && level.getFluidState(p).getType() == Fluids.WATER && level.getBlockState(p.above()).isAir()) {
				level.sendParticles(Fx.miasma(0x6B8A2A, 0.8F), p.getX() + 0.5, p.getY() + 1.05, p.getZ() + 0.5, 2, 0.3, 0.05, 0.3, 0.005);
				if (level.getRandom().nextInt(4) == 0) {
					level.sendParticles(ParticleTypes.BUBBLE_POP, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, 3, 0.3, 0.05, 0.3, 0.0);
				}
			}
		}
	}

	/** Crops near the player will not ripen past {@link #RIPE_CAP}: the ripe wilt back a stage. */
	private static void wither(ServerLevel level, ServerPlayer player) {
		BlockPos mid = player.blockPosition();
		for (int i = 0; i < WITHER_TRIES; i++) {
			BlockPos p = mid.offset(level.getRandom().nextInt(WITHER_REACH * 2 + 1) - WITHER_REACH, level.getRandom().nextInt(9) - 4,
				level.getRandom().nextInt(WITHER_REACH * 2 + 1) - WITHER_REACH);
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof CropBlock crop && crop.getAge(s) > Math.round(crop.getMaxAge() * RIPE_CAP) && inLand(level, p)) {
				level.setBlock(p, crop.getStateForAge(crop.getAge(s) - 1), 2);
				level.sendParticles(ParticleTypes.ASH, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.01);
			}
		}
	}

	/** A sleeper hears things: whispers, a slow heartbeat, something at the door. */
	private static void dream(ServerLevel level, ServerPlayer player) {
		var sound = switch (level.getRandom().nextInt(3)) {
			case 0 -> SoundEvents.WARDEN_HEARTBEAT;
			case 1 -> SoundEvents.AMBIENT_CAVE.value();
			default -> SoundEvents.SOUL_ESCAPE.value();
		};
		level.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.AMBIENT, 1.0F, 0.6F + level.getRandom().nextFloat() * 0.3F);
	}

	/** Up from a night in a lair's land: weak and dizzy, the dark still in the eyes. */
	private static void wakeBadly(ServerLevel level, ServerPlayer player) {
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAK_TICKS, 0, true, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, DIZZY_TICKS, 0, true, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARK_TICKS, 0, true, false, false));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, 0.7F);
	}
}
