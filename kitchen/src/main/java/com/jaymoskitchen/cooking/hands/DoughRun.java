package com.jaymoskitchen.cooking.hands;

import com.jaymoskitchen.ModItems;
import com.jaymoskitchen.cooking.Prep;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Mixing dough on a worktop, by hand: hold right-click to pour the water, let go inside the band; the same for the oil;
 * then knead, right-click and left-click in turn. The same game and numbers as the screen's (see {@link Prep}), shared:
 * whoever holds use pours, and any hand can knead.
 */
final class DoughRun extends Run {
	/** How long a pour's verdict shows before moving on (ticks). */
	private static final int VERDICT_TICKS = 20;
	private static final int WATER = 0, OIL = 1, KNEAD = 2;

	private final float waterShift;
	private final float oilShift;
	private final Map<UUID, Integer> holding = new HashMap<>();
	private int phase = WATER;
	private float water;
	private float oil;
	private boolean pouring;
	private float pourRate;
	private int verdictTicks = -1;
	private int verdictBand;
	private int kneads;
	private int kneadTicks;
	/** -1 = last knead was left, 1 = right, 0 = none yet. */
	private int lastKnead;
	private boolean spilled;

	DoughRun(Table table, Pick pick, int now) {
		super(table, pick, now);
		this.waterShift = (random.nextFloat() * 2.0F - 1.0F) * Prep.WATER_SHIFT;
		this.oilShift = -Prep.OIL_SHIFT_DOWN + random.nextFloat() * (Prep.OIL_SHIFT_DOWN + Prep.OIL_SHIFT_UP);
	}

	private boolean pourable() {
		return (phase == WATER || phase == OIL) && verdictTicks < 0;
	}

	@Override
	void input(ServerPlayer player, int what, float value, int now) {
		if (what != HandsOn.IN_HOLD) {
			return;
		}
		if (value > 0.5F) {
			holding.put(player.getUUID(), now);
			touch(player, now);
		} else {
			holding.remove(player.getUUID());
		}
	}

	private boolean held(int now) {
		holding.values().removeIf(at -> now - at > HandsOn.HOLD_STALE);
		return !holding.isEmpty();
	}

	@Override
	boolean use(ServerPlayer player, ItemStack held, boolean sneaking, int now) {
		if (phase == KNEAD) {
			knead(1);
		}
		return held.isEmpty();
	}

	@Override
	boolean attack(ServerPlayer player, ItemStack held, int now) {
		if (phase == KNEAD) {
			knead(-1);
		}
		return held.isEmpty();
	}

	private void knead(int direction) {
		if (direction != lastKnead) {
			kneads++;
			lastKnead = direction;
			table.playBlock(SoundEvents.SLIME_BLOCK_STEP, 0.6F, 0.8F + kneads * 0.02F);
			Vec3 top = Geometry.topCenter(table.level, table.pos);
			table.level.sendParticles(ParticleTypes.CLOUD, top.x, top.y, top.z, 2, 0.15, 0.02, 0.15, 0.01);
			if (kneads >= Prep.KNEADS) {
				finish(true);
			}
		}
	}

	@Override
	void tick(int now) {
		boolean down = held(now);
		if (down && !pouring && pourable()) {
			// every pour (and every re-grip) flows at a slightly different speed
			float base = phase == WATER ? Prep.WATER_RATE : Prep.OIL_RATE;
			pourRate = base * (1.0F + random.nextFloat() * 0.4F);
			pouring = true;
		} else if (!down && pouring) {
			endPour();
		}
		if (pouring && pourable()) {
			pourRate += Prep.POUR_ACCEL;
			if (phase == WATER) {
				water += pourRate;
				if (water >= 1.0F) {
					water = 1.0F;
					spilled = true;
					endPour();
				}
			} else {
				oil += pourRate;
				if (oil >= 1.0F) {
					oil = 1.0F;
					spilled = true;
					endPour();
				}
			}
			if (now % 3 == 0) {
				Vec3 top = Geometry.topCenter(table.level, table.pos);
				table.level.sendParticles(phase == WATER ? ParticleTypes.FALLING_WATER : ParticleTypes.FALLING_HONEY, top.x, top.y + 0.5, top.z, 2, 0.04, 0.0, 0.04, 0.0);
			}
		}
		if (verdictTicks > 0 && --verdictTicks == 0) {
			verdictTicks = -1;
			if (verdictBand == 0) {
				// a bad pour ruins the mix; no point kneading it
				finish(false);
			} else if (phase == WATER) {
				phase = OIL;
			} else {
				phase = KNEAD;
			}
		}
		if (phase == KNEAD && ++kneadTicks > Prep.KNEAD_TICKS) {
			finish(false);
		}
	}

	private void endPour() {
		pouring = false;
		float level = phase == WATER ? water : oil;
		if (level <= 0.0F) {
			return;
		}
		verdictBand = phase == WATER ? Prep.waterBand(water, waterShift) : Prep.oilBand(oil, oilShift);
		verdictTicks = VERDICT_TICKS;
		table.playBlock(verdictBand == 0 ? SoundEvents.SLIME_SQUISH : SoundEvents.BOTTLE_FILL, 0.6F, 1.0F);
	}

	private void finish(boolean kneaded) {
		Prep.Result result = Prep.score(water, oil, kneaded, waterShift, oilShift);
		endWith(result.ordinal(), () -> {
			if (result.amount() > 0) {
				table.handOver(this, new ItemStack(ModItems.DOUGH, result.amount()));
			}
			table.playBlock(result == Prep.Result.RUINED ? SoundEvents.SLIME_SQUISH : SoundEvents.PLAYER_LEVELUP, result == Prep.Result.PERFECT ? 1.0F : 0.6F,
				1.0F);
		});
	}

	/** Walking away mid-mix wastes the ingredients. */
	@Override
	void leave() {
		endWith(Prep.Result.RUINED.ordinal(), () -> {
		});
	}

	/** ints: step (0 water, 1 oil, 2 knead), knead count, the pour's verdict band (-1 none), spilled; floats: water, oil, band shifts. */
	@Override
	void fill(List<Integer> ints, List<Float> floats) {
		ints.add(phase);
		ints.add(kneads);
		ints.add(verdictTicks > 0 ? verdictBand : -1);
		ints.add(spilled ? 1 : 0);
		ints.add(pouring ? 1 : 0);
		floats.add(water);
		floats.add(oil);
		floats.add(waterShift);
		floats.add(oilShift);
		floats.add(pourRate);
	}
}
