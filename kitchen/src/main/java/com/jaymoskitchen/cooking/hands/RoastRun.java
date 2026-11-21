package com.jaymoskitchen.cooking.hands;

import com.jaymoskitchen.cooking.Minigames;
import com.jaymoskitchen.cooking.Quality;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Roasting on the spit over the campfire: four sides, only the one facing down cooks (its neighbours a little); right-click
 * the spit with an empty hand to turn it a quarter, sneak and right-click to take it off. The same game and numbers as
 * the screen's (see {@link Minigames}); anyone at the spit can turn it, and it is judged once.
 */
final class RoastRun extends Run {
	private final float[] done = new float[Minigames.SIDES];
	private int down;
	private float heat = 1.0F;
	private int heatTimer = 40;
	private int turnAnim = -1;

	RoastRun(Table table, Pick pick, int now) {
		super(table, pick, now);
	}

	@Override
	boolean use(ServerPlayer player, ItemStack held, boolean sneaking, int now) {
		if (!held.isEmpty()) {
			return false;
		}
		if (sneaking) {
			serve();
		} else if (turnAnim < 0) {
			turnAnim = 0;
			table.playBlock(SoundEvents.WOOD_STEP, 0.6F, 0.8F);
		}
		return true;
	}

	@Override
	void tick(int now) {
		if (--heatTimer <= 0) {
			heat = Mth.lerp(random.nextFloat(), Minigames.ROAST_HEAT_MIN, Minigames.ROAST_HEAT_MAX);
			heatTimer = Minigames.HEAT_GAP_MIN + random.nextInt(Minigames.HEAT_GAP_MAX - Minigames.HEAT_GAP_MIN);
		}
		float perTick = heat / dish.grillTicks();
		done[down] += perTick;
		done[(down + 1) % Minigames.SIDES] += perTick * Minigames.ROAST_SIDE_SPILL;
		done[(down + Minigames.SIDES - 1) % Minigames.SIDES] += perTick * Minigames.ROAST_SIDE_SPILL;
		if (turnAnim >= 0 && ++turnAnim >= Minigames.TURN_TICKS) {
			turnAnim = -1;
			down = (down + 1) % Minigames.SIDES;
		}
		if (now % 4 == 0) {
			table.level.sendParticles(done[down] > Minigames.BURNT_AT ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE,
				table.pos.getX() + 0.5, table.pos.getY() + 0.9, table.pos.getZ() + 0.5, 1, 0.1, 0.05, 0.1, 0.01);
		}
		if (now % 25 == 0) {
			table.playBlock(SoundEvents.CAMPFIRE_CRACKLE, 0.5F, 1.0F);
		}
		for (float side : done) {
			if (side >= Minigames.ROAST_END) {
				serve();
				return;
			}
		}
	}

	private void serve() {
		end(Minigames.judgeRoast(done));
	}

	/** Walking away leaves it on the fire. */
	@Override
	void leave() {
		end(Quality.CHARRED);
	}

	/** ints: the side facing down, ticks into the turn (-1 none); floats: the heat, then each side's doneness. */
	@Override
	void fill(List<Integer> ints, List<Float> floats) {
		ints.add(down);
		ints.add(turnAnim);
		floats.add(heat);
		for (float side : done) {
			floats.add(side);
		}
	}
}
