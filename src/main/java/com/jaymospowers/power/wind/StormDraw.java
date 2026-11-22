package com.jaymospowers.power.wind;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.Soundscape;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Draw from the sky: a Stormbound Wind user outdoors under rain or thunder holds the draw key and pulls charge down out
 * of the clouds (small bolts arcing to the hands, a building hum). A full charge opens the storm state for
 * {@link #STORM_TICKS}: harder hits, less damage taken, sparks on the body. Thunder fills the charge faster than rain.
 */
final class StormDraw {
	private static final String TABLE = "wind_storm";
	private static final int SLOT = 0;
	/** The gauge's full charge, and the ticks a full draw takes under a thunderstorm (rain is slower, see {@link WindModes#thunderPower}). */
	static final float FULL = 100.0F;
	static final int DRAW_TICKS = 100;
	/** How fast an unheld charge drains away (per tick), after this many ticks of letting go. */
	static final float DRAIN = 0.6F;
	static final int DRAIN_AFTER = 10;
	/** The storm state: how long, how much harder you hit, the cooldown before the next draw, hunger a tick. */
	static final int STORM_TICKS = 400, COOLDOWN = 600;
	static final double HIT_BONUS = 0.5;
	static final int RESISTANCE_AMPLIFIER = 0;
	static final float STORM_EXHAUSTION = 0.02F, DRAW_EXHAUSTION = 0.03F;
	static final int MIN_FOOD = 6;
	static final int BOLT = 0xCFE0FF, GLOW = 0x8FB4FF;

	private static final Identifier HIT_ID = JaymosPowers.id("wind_storm_state");

	private static final class Draw {
		float charge;
		boolean holding;
		long letGoAt;
		long stormUntil;
	}

	private static final Map<UUID, Draw> DRAWS = new HashMap<>();

	private StormDraw() {
	}

	private static Draw of(ServerPlayer player) {
		return DRAWS.computeIfAbsent(player.getUUID(), k -> new Draw());
	}

	static int charge(ServerPlayer player) {
		Draw d = DRAWS.get(player.getUUID());
		return d == null ? 0 : Math.round(d.charge);
	}

	static int stormLeft(ServerPlayer player) {
		Draw d = DRAWS.get(player.getUUID());
		return d == null ? 0 : (int)Math.max(0L, d.stormUntil - player.level().getGameTime());
	}

	/** Whether the storm state is on. */
	static boolean storming(ServerPlayer player) {
		return stormLeft(player) > 0;
	}

	static void hold(ServerPlayer player, boolean down) {
		Draw d = of(player);
		if (!down) {
			if (d.holding) {
				d.holding = false;
				d.letGoAt = player.level().getGameTime();
			}
			return;
		}
		ServerLevel level = player.level();
		if (storming(player)) {
			return;
		}
		if (!PickKit.ready(TABLE, player, SLOT)) {
			StormNet.say(player, "cooling");
			return;
		}
		if (!level.isRaining() || !level.canSeeSky(player.blockPosition())) {
			StormNet.say(player, level.isRaining() ? "draw_roof" : "draw_clear");
			return;
		}
		if (!player.isCreative() && player.getFoodData().getFoodLevel() < MIN_FOOD) {
			StormNet.say(player, "hungry");
			return;
		}
		d.holding = true;
	}

	static void tick(ServerPlayer player) {
		Draw d = DRAWS.get(player.getUUID());
		if (d == null) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (d.stormUntil > 0L) {
			if (now >= d.stormUntil) {
				end(player, d);
			} else {
				stormTick(player, level, d, now);
			}
			return;
		}
		if (d.holding) {
			boolean sky = level.isRaining() && level.canSeeSky(player.blockPosition());
			boolean fed = player.isCreative() || player.getFoodData().getFoodLevel() >= MIN_FOOD;
			if (!sky || !fed) {
				d.holding = false;
				d.letGoAt = now;
				StormNet.say(player, sky ? "hungry" : "draw_roof");
				return;
			}
			d.charge = Math.min(FULL, d.charge + FULL / DRAW_TICKS * WindModes.thunderPower(level));
			player.causeFoodExhaustion(DRAW_EXHAUSTION);
			drawFx(player, level, d);
			if (d.charge >= FULL) {
				begin(player, level, d, now);
			}
		} else if (d.charge > 0.0F && now - d.letGoAt > DRAIN_AFTER) {
			d.charge = Math.max(0.0F, d.charge - DRAIN);
		}
	}

	/** Small bolts from the cloud to each hand, and the hum that rises with the charge. */
	private static void drawFx(ServerPlayer player, ServerLevel level, Draw d) {
		Vec3 look = player.getLookAngle();
		Vec3 side = new Vec3(-look.z, 0.0, look.x);
		side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
		Vec3 base = player.position().add(0.0, 1.0, 0.0).add(look.x * 0.4, 0.0, look.z * 0.4);
		if (player.tickCount % 3 == 0) {
			var random = player.getRandom();
			for (int hand = -1; hand <= 1; hand += 2) {
				Vec3 to = base.add(side.scale(0.4 * hand));
				Vec3 from = to.add((random.nextDouble() - 0.5) * 4.0, 10.0 + random.nextDouble() * 6.0, (random.nextDouble() - 0.5) * 4.0);
				WindBolts.zap(level, from, to);
			}
			level.sendParticles(MagicFx.spark(BOLT, 0.8F), base.x, base.y, base.z, 3, 0.4, 0.4, 0.4, 0.0);
		}
		if (Soundscape.due(player, "storm_draw", 6, 12)) {
			float rise = d.charge / FULL;
			Soundscape.play(level, player.position(), SoundSource.PLAYERS,
				Soundscape.Layer.of(0.4F, 0.7F, 0.6F + rise * 0.8F, 0.8F + rise * 0.9F, SoundEvents.BEACON_AMBIENT),
				Soundscape.Layer.of(0.3F, 0.6F, 0.8F + rise * 0.6F, 1.1F + rise * 0.6F, SoundEvents.LIGHTNING_BOLT_IMPACT).chance(0.25F + rise * 0.5F));
		}
	}

	/** A full draw: the storm state opens. */
	private static void begin(ServerPlayer player, ServerLevel level, Draw d, long now) {
		d.charge = 0.0F;
		d.holding = false;
		d.stormUntil = now + STORM_TICKS;
		AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
		if (damage != null) {
			damage.addOrUpdateTransientModifier(new AttributeModifier(HIT_ID, HIT_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		MagicFx.ring(level, player.position().add(0.0, 0.1, 0.0), 4.0F, BOLT);
		MagicFx.blast(level, player.position().add(0.0, 1.0, 0.0), 2.5F, GLOW);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.4F, 1.1F);
		WindModes.screenFx(player, 24.0, WindNet.ScreenFx.VIGNETTE, 0x2F6BFF, 30);
	}

	private static void stormTick(ServerPlayer player, ServerLevel level, Draw d, long now) {
		if (now % 10 == 0) {
			player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 30, RESISTANCE_AMPLIFIER, true, false, false));
		}
		player.causeFoodExhaustion(STORM_EXHAUSTION);
		if (now % 3 == 0) {
			level.sendParticles(MagicFx.spark(BOLT, 0.7F), player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.35, 0.7, 0.35, 0.0);
		}
		if (now % 40 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.35F, 1.7F);
		}
	}

	private static void end(ServerPlayer player, Draw d) {
		d.stormUntil = 0L;
		unbuff(player);
		PickKit.spend(TABLE, player, SLOT, COOLDOWN);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_DEATH, SoundSource.PLAYERS, 0.6F, 1.2F);
	}

	private static void unbuff(ServerPlayer player) {
		AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
		if (damage != null) {
			damage.removeModifier(HIT_ID);
		}
	}

	static void forget(ServerPlayer player) {
		Draw d = DRAWS.remove(player.getUUID());
		if (d != null && d.stormUntil > 0L) {
			unbuff(player);
		}
	}
}
