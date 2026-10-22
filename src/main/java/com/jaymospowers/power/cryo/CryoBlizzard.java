package com.jaymospowers.power.cryo;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.smoke.SmokeField;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.14.0) C, Blizzard (the capstone: every ability mastered): a {@link #DURATION_TICKS} (20 s) whiteout round you,
 * {@link #RADIUS} blocks every way, moving with you. Snow drives through it and lies on the ground (snow layers, only
 * into air on a full top face); everything in it but you slowly freezes and is slowed; smoke in it turns to freezing
 * fog; and the white hides you: creatures hunting you lose you unless they're right on you, and others inside see
 * nothing but snow (the client whites out their view, {@code CryoBlizzardClient}, from {@link #STORM}).
 */
public final class CryoBlizzard {
	public static final double RADIUS = 16.0;
	public static final int DURATION_TICKS = 400;
	public static final int COOLDOWN = 2400;
	/** Every how many ticks it bites: freezing added and Slowness I this long. */
	static final int BITE_EVERY = 10;
	static final int FREEZE = 20;
	static final int FREEZE_OVER = 60;
	static final int SLOW_TICKS = 30;
	/** Closer than this (blocks), a hunter still finds you in the white. */
	static final double FOUND = 5.0;
	/** Snow: tries a tick to lay a layer somewhere in it. */
	static final int SNOW_TRIES = 3;
	/** Hunger a tick while it rages. */
	static final float EXHAUSTION = 0.01F;

	/** The game time a player's Blizzard began: synced to everyone (the whiteout on screen). */
	public static final AttachmentType<Long> STORM = AttachmentRegistry.create(JaymosPowers.id("cryo_blizzard"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	private CryoBlizzard() {
	}

	/** Raging round this player now (both sides). */
	public static boolean raging(Player player) {
		return player.getAttached(STORM) != null;
	}

	static void start(ServerPlayer player) {
		if (raging(player)) {
			return;
		}
		if (!CryoPower.ready(player, CryoState.BLIZZARD)) {
			long left = CryoPower.state(player).left(CryoState.BLIZZARD, player.level().getGameTime());
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.cryo_blizzard_wait", (left + 19) / 20).withStyle(ChatFormatting.GRAY));
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		player.setAttached(STORM, now);
		CryoPower.set(player, CryoPower.state(player).withBlizzard(now));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 1.5F, 0.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 1.5F, 0.5F);
		com.jaymospowers.core.Shakes.around(level, player.position(), RADIUS, 0.4F);
	}

	/** Over; with {@code cooldown}, the cooldown starts. */
	static void end(ServerPlayer player, boolean cooldown) {
		if (player.getAttached(STORM) == null) {
			return;
		}
		player.removeAttached(STORM);
		CryoPower.set(player, CryoPower.state(player).withBlizzard(-1L));
		if (cooldown) {
			CryoPower.cooldown(player, CryoState.BLIZZARD, COOLDOWN);
		}
	}

	static void tick(ServerPlayer player) {
		Long from = player.getAttached(STORM);
		if (from == null) {
			return;
		}
		ServerLevel level = player.level();
		long age = level.getGameTime() - from;
		if (!player.isAlive() || player.isSpectator() || age >= DURATION_TICKS) {
			end(player, true);
			return;
		}
		player.causeFoodExhaustion(EXHAUSTION);
		Vec3 center = player.position();
		RandomSource random = level.getRandom();
		float strength = CryoPower.strength(player);
		// the snow drives through, for everyone
		for (int i = 0; i < 6; i++) {
			double a = random.nextDouble() * Math.PI * 2.0;
			double r = Math.sqrt(random.nextDouble()) * RADIUS;
			double x = center.x + Math.cos(a) * r;
			double z = center.z + Math.sin(a) * r;
			double y = center.y + random.nextDouble() * 6.0 - 1.0;
			level.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, 6, 1.5, 1.0, 1.5, 0.15);
			level.sendParticles(ParticleTypes.WHITE_ASH, x, y, z, 4, 2.0, 1.0, 2.0, 0.05);
		}
		if (age % 30 == 0) {
			level.playSound(null, center.x, center.y, center.z, SoundEvents.BREEZE_JUMP, SoundSource.WEATHER, 0.8F, 0.3F + random.nextFloat() * 0.2F);
		}
		// snow lies
		BlockState snow = Blocks.SNOW.defaultBlockState();
		for (int i = 0; i < SNOW_TRIES; i++) {
			int x = (int)Math.floor(center.x + (random.nextDouble() * 2.0 - 1.0) * RADIUS);
			int z = (int)Math.floor(center.z + (random.nextDouble() * 2.0 - 1.0) * RADIUS);
			BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
			if (Vec3.atCenterOf(pos).distanceToSqr(center) > RADIUS * RADIUS || !level.isLoaded(pos) || !level.getBlockState(pos).isAir()) {
				continue;
			}
			BlockPos floor = pos.below();
			if (level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP) && snow.canSurvive(level, pos)) {
				level.setBlockAndUpdate(pos, snow);
			}
		}
		if (age % BITE_EVERY != 0) {
			return;
		}
		SmokeField.freeze(level, center, RADIUS);
		AABB box = new AABB(center, center).inflate(RADIUS);
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box,
			e -> e != player && e.isAlive() && !e.isSpectator() && e.position().distanceToSqr(center) <= RADIUS * RADIUS)) {
			if (living instanceof Player other && other.isCreative()) {
				continue;
			}
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, 0), player);
			if (living.canFreeze()) {
				living.setTicksFrozen(Math.min(living.getTicksRequiredToFreeze() + FREEZE_OVER, living.getTicksFrozen() + Math.round(FREEZE * strength)));
			}
			if (living.isOnFire()) {
				living.clearFire();
			}
		}
		// the white hides you
		for (Mob mob : level.getEntitiesOfClass(Mob.class, box.inflate(8.0), m -> m.getTarget() == player)) {
			if (mob.distanceToSqr(player) > FOUND * FOUND) {
				mob.setTarget(null);
				mob.getNavigation().stop();
			}
		}
	}

	static void clear() {
		// (the STORM attachment isn't saved: nothing else to forget)
	}
}
