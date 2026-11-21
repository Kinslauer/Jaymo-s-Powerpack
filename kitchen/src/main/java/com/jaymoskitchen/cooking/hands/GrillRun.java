package com.jaymoskitchen.cooking.hands;

import com.jaymoskitchen.cooking.Grill;
import com.jaymoskitchen.cooking.Quality;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Grilling on the campfire (or a pan on a hearth or hob) and baking in the oven. On the grill the side facing the fire
 * cooks; flip it with the tongs, and sneak and use the tongs to serve it. The fire's heat is new after every flip, and
 * a soul fire runs hotter. In the oven there is one side, the heat surges and dips by itself, and the peel pulls the
 * dish out. The same game and numbers as the screen's (see {@link Grill}); anyone with the tool can work it.
 */
final class GrillRun extends Run {
	private final boolean oven;
	private final float baseHeat;
	private final float[] done = new float[2];
	private int down;
	private float heat;
	private int flippedAt;
	private int nextSurge;
	private int flipAnim = -1;

	GrillRun(Table table, Pick pick, int now) {
		super(table, pick, now);
		this.oven = pick.kind() == Pick.Kind.OVEN;
		this.baseHeat = table.level.getBlockState(table.pos).is(Blocks.SOUL_CAMPFIRE) ? Grill.SOUL_HEAT : 1.0F;
		this.flippedAt = now;
		this.heat = rollHeat();
		this.nextSurge = now + 30 + random.nextInt(50);
		table.playBlock(oven ? SoundEvents.FURNACE_FIRE_CRACKLE : SoundEvents.CAMPFIRE_CRACKLE, 1.0F, 1.0F);
	}

	private float rollHeat() {
		float swing = oven ? 0.7F + random.nextFloat() * 0.7F : 0.8F + random.nextFloat() * 0.5F;
		return baseHeat * swing;
	}

	private float perTick() {
		return heat / dish.grillTicks();
	}

	@Override
	boolean use(ServerPlayer player, ItemStack held, boolean sneaking, int now) {
		if (oven) {
			if (CookTools.Tool.PEEL.holds(held)) {
				serve();
				return true;
			}
			return false;
		}
		if (!CookTools.Tool.TONGS.holds(held)) {
			return false;
		}
		if (sneaking) {
			serve();
		} else if (now - flippedAt >= Grill.FLIP_TICKS) {
			flippedAt = now;
			down ^= 1;
			flipAnim = 0;
			// the coals shift: the new side gets a different heat
			heat = rollHeat();
			table.playBlock(SoundEvents.FIRE_EXTINGUISH, 0.25F, 1.8F);
		}
		return true;
	}

	@Override
	void tick(int now) {
		done[down] += perTick();
		if (flipAnim >= 0 && ++flipAnim > Grill.FLIP_TICKS) {
			flipAnim = -1;
		}
		if (oven && now >= nextSurge) {
			heat = rollHeat();
			nextSurge = now + 30 + random.nextInt(50);
		}
		if (now % 5 == 0) {
			table.level.sendParticles(done[down] > Grill.BURNT ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE,
				table.pos.getX() + 0.5, table.pos.getY() + 1.0, table.pos.getZ() + 0.5, 1, 0.12, 0.03, 0.12, 0.01);
		}
		if (done[down] >= Grill.ASH) {
			// burnt to nothing
			serve();
		}
	}

	private void serve() {
		end(oven ? Quality.judgeOven(done[0]) : Quality.judgeGrill(done[0], done[1]));
	}

	/** Walking away takes it off as it is, the way closing the screen did. */
	@Override
	void leave() {
		serve();
	}

	/** ints: the side down, ticks into the flip (-1 none), oven (1 or 0); floats: the heat per tick, the heat, then each side's doneness. */
	@Override
	void fill(List<Integer> ints, List<Float> floats) {
		ints.add(down);
		ints.add(flipAnim);
		ints.add(oven ? 1 : 0);
		floats.add(perTick());
		floats.add(heat);
		floats.add(done[0]);
		floats.add(done[1]);
	}
}
