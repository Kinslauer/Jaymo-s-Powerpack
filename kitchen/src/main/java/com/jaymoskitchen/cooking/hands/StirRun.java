package com.jaymoskitchen.cooking.hands;

import com.jaymoskitchen.cooking.Minigames;
import com.jaymoskitchen.cooking.Quality;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Stirring the pot: point the spoon (the crosshair) round the pot's middle, in the world. Too slow and it sticks and
 * scorches, too fast and it slops over; now and then it boils up and wants stirring faster. The same game and numbers
 * as the screen's (see {@link Minigames}). Everyone stirring adds to the same pot: their turns add up.
 */
final class StirRun extends Run {
	/** The most one cook's pointer can turn in a tick (radians): beyond that it's not a hand. */
	private static final float MAX_STEP = 1.2F;
	/** Ticks of getting going before slow stirring counts as sticking. */
	private static final int GRACE = 20;

	private final Vec3 centre;
	private final Map<UUID, Float> lastAngle = new HashMap<>();
	/** Turn this tick, from every cook. */
	private float pending;
	private float omega;
	private int ticks;
	private int stirred;
	private int stuck;
	private int spilled;
	private int boilTicks;
	private int nextBoil;
	private float spoonAngle;

	StirRun(Table table, Pick pick, int now) {
		super(table, pick, now);
		this.centre = Geometry.potCenter(table.level, table.pos);
		this.nextBoil = Minigames.BOIL_GAP_MIN + random.nextInt(Minigames.BOIL_GAP_MAX - Minigames.BOIL_GAP_MIN);
	}

	private boolean boiling() {
		return boilTicks > 0;
	}

	@Override
	boolean use(ServerPlayer player, ItemStack held, boolean sneaking, int now) {
		return CookTools.Tool.SPOON.holds(held);
	}

	@Override
	void input(ServerPlayer player, int what, float value, int now) {
		if (what != HandsOn.IN_ANGLE) {
			return;
		}
		if (!CookTools.Tool.SPOON.holds(player.getMainHandItem()) || Float.isNaN(value) || Float.isInfinite(value)) {
			lastAngle.remove(player.getUUID());
			return;
		}
		Float before = lastAngle.put(player.getUUID(), value);
		if (before != null) {
			pending += Math.min(Math.abs(Geometry.turn(before, value)), MAX_STEP);
		}
		spoonAngle = value;
		touch(player, now);
	}

	@Override
	void tick(int now) {
		ticks++;
		omega = omega * 0.7F + pending * 0.3F;
		pending = 0.0F;
		if (boilTicks > 0) {
			boilTicks--;
		} else if (--nextBoil <= 0) {
			boilTicks = Minigames.BOIL_TICKS;
			nextBoil = Minigames.BOIL_GAP_MIN + random.nextInt(Minigames.BOIL_GAP_MAX - Minigames.BOIL_GAP_MIN);
		}
		float min = boiling() ? Minigames.BOIL_MIN : Minigames.STIR_MIN;
		float max = boiling() ? Minigames.BOIL_MAX : Minigames.STIR_MAX;
		if (omega >= min && omega <= max) {
			stirred++;
		} else if (omega < min) {
			// a moment's grace to get going
			if (ticks > GRACE) {
				stuck++;
			}
		} else {
			spilled++;
		}
		if (ticks % 4 == 0) {
			table.level.sendParticles(boiling() ? ParticleTypes.BUBBLE_POP : ParticleTypes.SMALL_FLAME, centre.x + (random.nextDouble() - 0.5) * 0.8,
				centre.y, centre.z + (random.nextDouble() - 0.5) * 0.8, boiling() ? 3 : 1, 0.05, 0.02, 0.05, 0.01);
		}
		if (omega > max && ticks % 6 == 0) {
			table.level.sendParticles(ParticleTypes.SPLASH, centre.x + Math.cos(spoonAngle) * 0.9, centre.y, centre.z + Math.sin(spoonAngle) * 0.9, 4,
				0.1, 0.05, 0.1, 0.1);
		}
		if (omega >= min && ticks % 10 == 0) {
			table.playBlock(SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.4F, 1.0F);
		}
		if (stirred >= Minigames.STIR_TICKS) {
			end(Minigames.judgeStir(stuck, spilled, true));
		} else if (stuck >= Minigames.STUCK_CHARRED || ticks >= Minigames.STIR_LIMIT) {
			end(Minigames.judgeStir(stuck, spilled, false));
		}
	}

	/** Walking away leaves the pot on the fire. */
	@Override
	void leave() {
		end(Quality.CHARRED);
	}

	/** ints: ticks stirred, stuck, spilled, boiling (1 or 0), ticks; floats: how fast it's being stirred, the spoon's angle. */
	@Override
	void fill(List<Integer> ints, List<Float> floats) {
		ints.add(stirred);
		ints.add(stuck);
		ints.add(spilled);
		ints.add(boiling() ? 1 : 0);
		ints.add(ticks);
		floats.add(omega);
		floats.add(Mth.wrapDegrees(spoonAngle * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD);
	}
}
