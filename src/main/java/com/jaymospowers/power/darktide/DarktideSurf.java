package com.jaymospowers.power.darktide;

import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Surf and Geyser (Andy's Awakening pick: "Darktide's water half").
 * <ul>
 * <li><b>Surf</b>, sprint + G: a crest of dark water rises under you and carries you where you look, over land at
 * {@link #LAND_SPEED} and over water or in the rain at {@link #WATER_SPEED} blocks a tick. Whoever stands in its way is knocked
 * aside. Sprint + G again (or Sneak) steps off. Over dry land it is fed from water carried ({@link DarktideVessels}).</li>
 * <li><b>Geyser</b>, sprint + R: water erupts under the foe you look at (within {@link #GEYSER_REACH}) and throws it up; looking
 * at no one, it erupts under you and you ride it up, landing soft.</li>
 * </ul>
 * Both are {@link DarktideDark#WET_BONUS} stronger in the rain or standing in water.
 */
final class DarktideSurf {
	// ---- tuning ----
	private static final String TABLE = "darktide_surf";
	private static final int GEYSER = 0;
	/** The crest: speeds (blocks a tick), how high it holds you over the ground or water, how fast it settles there. */
	static final double LAND_SPEED = 0.45, WATER_SPEED = 0.75, CREST = 0.4, SETTLE = 0.3;
	/** Over dry land: water spent every {@link #WATER_EVERY} ticks; Flow a second. */
	static final float LAND_WATER = 5.0F, FLOW_PER_SECOND = 1.0F;
	static final int WATER_EVERY = 20;
	/** What it knocks aside: how near, harm, push. */
	static final double BUMP_REACH = 1.6, BUMP_PUSH = 0.8;
	static final float BUMP_HARM = 2.0F;
	/** The geyser: reach, harm, how hard it throws a foe and you up, its water and Flow, the cooldown. */
	static final double GEYSER_REACH = 20.0, GEYSER_FOE_UP = 1.2, GEYSER_SELF_UP = 1.5;
	static final float GEYSER_HARM = 4.0F, GEYSER_WATER = 25.0F, GEYSER_FLOW = 15.0F;
	static final int GEYSER_COOLDOWN = 160, GEYSER_HEIGHT = 6;

	private static final Set<UUID> SURFING = new HashSet<>();
	/** Riding a geyser up: no fall harm until they land. */
	private static final Set<UUID> RIDING = new HashSet<>();
	private static final Map<UUID, Long> BUMPED = new HashMap<>();

	private DarktideSurf() {
	}

	static boolean surfing(ServerPlayer player) {
		return SURFING.contains(player.getUUID());
	}

	static void forget(ServerPlayer player) {
		SURFING.remove(player.getUUID());
		RIDING.remove(player.getUUID());
	}

	// ---------------------------------------------------------------- surf

	static void toggle(ServerPlayer player) {
		if (SURFING.remove(player.getUUID())) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.8F, 0.8F);
			return;
		}
		if (!DarktideVessels.draw(player, LAND_WATER, DarktideVessels.waterNear(player))) {
			return;
		}
		SURFING.add(player.getUUID());
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.0F, 0.8F);
	}

	static void tick(ServerPlayer player, DarktideLive live) {
		if (RIDING.contains(player.getUUID())) {
			player.resetFallDistance();
			if (player.onGround() || player.isInWater()) {
				RIDING.remove(player.getUUID());
			}
		}
		if (!SURFING.contains(player.getUUID())) {
			return;
		}
		if (!player.isAlive() || player.isShiftKeyDown() || player.isPassenger() || player.getAbilities().flying) {
			SURFING.remove(player.getUUID());
			return;
		}
		ServerLevel level = player.level();
		boolean overWater = level.getFluidState(player.blockPosition()).is(FluidTags.WATER) || level.getFluidState(player.blockPosition().below()).is(FluidTags.WATER);
		boolean wet = overWater || player.isInWaterOrRain();
		if (!wet && player.tickCount % WATER_EVERY == 0) {
			if (!DarktideVessels.draw(player, LAND_WATER, DarktideVessels.waterNear(player))) {
				SURFING.remove(player.getUUID());
				return;
			}
		}
		if (player.tickCount % 20 == 0 && !player.getAbilities().instabuild) {
			live.flow = Math.max(0.0F, live.flow - FLOW_PER_SECOND);
			if (live.flow <= 0.0F) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.darktide.no_flow").withStyle(ChatFormatting.DARK_AQUA));
				SURFING.remove(player.getUUID());
				return;
			}
		}
		double speed = (wet ? WATER_SPEED : LAND_SPEED) * (player.isInWaterOrRain() ? DarktideDark.WET_BONUS : 1.0F);
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		look = look.lengthSqr() < 1.0E-4 ? Vec3.ZERO : look.normalize();
		double ground = ground(level, player);
		double lift = Mth.clamp((ground + CREST - player.getY()) * SETTLE, -0.3, 0.4) + 0.08;
		player.setDeltaMovement(look.x * speed, lift, look.z * speed);
		player.syncVelocity = true;
		player.resetFallDistance();
		player.clearFire();
		Vec3 feet = player.position();
		if (player.tickCount % 2 == 0) {
			MagicFx.wave(level, feet.subtract(look.scale(0.6)), look, 1.6, 1.4F, DarktideLooks.WATER, 6);
			level.sendParticles(MagicFx.mote(DarktideLooks.PALE, 0.8F), feet.x, feet.y + 0.2, feet.z, 3, 0.4, 0.1, 0.4, 0.05);
		}
		if (player.tickCount % 10 == 0) {
			level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.BOAT_PADDLE_WATER, SoundSource.PLAYERS, 0.6F, 0.7F);
		}
		// the crest knocks aside whoever stands in its way
		long now = level.getGameTime();
		Vec3 front = feet.add(look.scale(1.0)).add(0.0, 0.8, 0.0);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(front, front).inflate(BUMP_REACH), e -> PickKit.foe(player, e))) {
			if (now - BUMPED.getOrDefault(e.getUUID(), -100L) < 20) {
				continue;
			}
			BUMPED.put(e.getUUID(), now);
			Vec3 side = e.position().subtract(feet).multiply(1.0, 0.0, 1.0);
			side = side.lengthSqr() < 1.0E-4 ? look : side.normalize();
			e.hurtServer(level, player.damageSources().playerAttack(player), BUMP_HARM);
			e.setDeltaMovement(e.getDeltaMovement().add(side.x * BUMP_PUSH, 0.35, side.z * BUMP_PUSH));
			e.syncVelocity = true;
		}
		if (BUMPED.size() > 64) {
			BUMPED.values().removeIf(t -> now - t > 40);
		}
	}

	/** The top of the ground or water under the player (within a few blocks), or below them if there is none near. */
	private static double ground(ServerLevel level, ServerPlayer player) {
		BlockPos pos = BlockPos.containing(player.getX(), player.getY() + 0.5, player.getZ());
		for (int i = 0; i < 5; i++) {
			BlockPos at = pos.below(i);
			if (!level.getFluidState(at).isEmpty()) {
				return at.getY() + level.getFluidState(at).getHeight(level, at);
			}
			if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
				return at.getY() + level.getBlockState(at).getCollisionShape(level, at).max(net.minecraft.core.Direction.Axis.Y);
			}
		}
		return player.getY() - 2.0;
	}

	// ---------------------------------------------------------------- geyser

	static void geyser(ServerPlayer player) {
		if (!PickKit.ready(TABLE, player, GEYSER)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.darktide.cooling").withStyle(ChatFormatting.GRAY));
			return;
		}
		DarktideLive live = DarktidePower.live(player);
		if (live.flow + 1.0E-3F < GEYSER_FLOW && !player.getAbilities().instabuild) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.darktide.no_flow").withStyle(ChatFormatting.DARK_AQUA));
			return;
		}
		if (!DarktideVessels.draw(player, GEYSER_WATER, DarktideVessels.waterNear(player))) {
			return;
		}
		if (!player.getAbilities().instabuild) {
			live.flow = Math.max(0.0F, live.flow - GEYSER_FLOW);
		}
		PickKit.spend(TABLE, player, GEYSER, GEYSER_COOLDOWN);
		ServerLevel level = player.level();
		float wet = player.isInWaterOrRain() ? DarktideDark.WET_BONUS : 1.0F;
		LivingEntity foe = PickKit.aimed(player, DarktideFlow.reach(player, GEYSER_REACH));
		LivingEntity on = foe != null ? foe : player;
		Vec3 base = on.position();
		if (foe != null) {
			foe.hurtServer(level, player.damageSources().playerAttack(player), GEYSER_HARM * wet);
			foe.setDeltaMovement(foe.getDeltaMovement().x, GEYSER_FOE_UP * wet, foe.getDeltaMovement().z);
			foe.syncVelocity = true;
			foe.clearFire();
		} else {
			player.setDeltaMovement(player.getDeltaMovement().x, GEYSER_SELF_UP * wet, player.getDeltaMovement().z);
			player.syncVelocity = true;
			player.resetFallDistance();
			RIDING.add(player.getUUID());
		}
		MagicFx.beam(level, base, base.add(0.0, GEYSER_HEIGHT * wet, 0.0), 0.7F, DarktideLooks.DROP, 14);
		MagicFx.ring(level, base.add(0.0, 0.1, 0.0), 1.6F, DarktideLooks.WATER);
		DarktideLooks.spray(level, base, 1.8F);
		level.playSound(null, base.x, base.y, base.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.4F, 0.6F);
		level.playSound(null, base.x, base.y, base.z, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.PLAYERS, 1.2F, 0.8F);
	}
}
