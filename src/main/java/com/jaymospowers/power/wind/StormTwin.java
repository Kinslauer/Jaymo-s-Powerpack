package com.jaymospowers.power.wind;

import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Two storm weapons brought together: with a storm weapon in each hand (one marked by the storm, see {@link StormRecall},
 * or a hammer or mace), the twin key clashes them and sends one great bolt along the look.
 */
final class StormTwin {
	private static final String TABLE = "wind_storm";
	private static final int SLOT = 4;
	static final int COOLDOWN = 260;
	static final double RANGE = 40.0, WIDTH = 1.6;
	static final float DAMAGE = 16.0F, END_BOLT = 6.0F;
	static final float EXHAUSTION = 4.0F;
	static final int MIN_FOOD = 6;

	private StormTwin() {
	}

	/** Whether this is a storm weapon: marked by the storm or a hammer or mace. */
	private static boolean stormWeapon(ItemStack stack) {
		return !stack.isEmpty() && (StormRecall.marked(stack) || StormHammer.hammer(stack));
	}

	static void press(ServerPlayer player) {
		if (!stormWeapon(player.getMainHandItem()) || !stormWeapon(player.getOffhandItem())) {
			StormNet.say(player, "twin_hands");
			return;
		}
		if (!PickKit.ready(TABLE, player, SLOT)) {
			StormNet.say(player, "cooling");
			return;
		}
		if (!player.isCreative() && player.getFoodData().getFoodLevel() < MIN_FOOD) {
			StormNet.say(player, "hungry");
			return;
		}
		PickKit.spend(TABLE, player, SLOT, COOLDOWN);
		player.causeFoodExhaustion(EXHAUSTION);
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 start = eye.add(look.scale(0.9)).add(0.0, -0.3, 0.0);
		Vec3 end = eye.add(look.scale(RANGE));
		var hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 stop = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
		// the clash, then the beam
		MagicFx.blast(level, start, 1.6F, StormDraw.BOLT);
		MagicFx.beam(level, start, stop, 0.55F, StormDraw.GLOW, 14);
		MagicFx.beam(level, start, stop, 0.2F, 0xFFFFFF, 14);
		DamageSource source = player.damageSources().source(DamageTypes.LIGHTNING_BOLT, player);
		Vec3 line = stop.subtract(start);
		double length = Math.max(1.0E-4, line.lengthSqr());
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(start, stop).inflate(WIDTH + 1.0), c -> PickKit.foe(player, c))) {
			Vec3 p = e.getBoundingBox().getCenter().subtract(start);
			double t = Math.max(0.0, Math.min(1.0, p.dot(line) / length));
			if (p.subtract(line.scale(t)).length() <= WIDTH) {
				e.hurtServer(level, source, DAMAGE * WindBolts.sky(level));
				WindModes.chargeCreeper(e);
				WindBolts.zap(level, start.add(line.scale(t)), e.getBoundingBox().getCenter());
			}
		}
		WindBolts.strike(player, stop, END_BOLT, 3.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.6F, 1.0F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8F, 1.6F);
		Shakes.around(level, player.position(), 24.0, 1.2F);
	}
}
