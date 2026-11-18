package com.jaymoskitchen.cooking.hands;

import com.jaymoskitchen.cooking.Quality;
import com.jaymoskitchen.cooking.Smoke;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Curing in the smoker: the heat drifts down on its own and gusts knock it about; hold the fan (hold use with it in
 * hand) to raise it. Time in the band cures the meat, running hot scorches it. The same game and numbers as the
 * screen's (see {@link Smoke}); every cook fanning adds to the same fire.
 */
final class SmokeRun extends Run {
	private final Map<UUID, Integer> holding = new HashMap<>();
	private final float cooling;
	private float heat = 0.25F;
	private boolean fanning;
	private float gustVelocity;
	private int gustTicks;
	private int nextGust;
	private int ticks;
	private int cure;
	private int scorch;
	private int cold;

	SmokeRun(Table table, Pick pick, int now) {
		super(table, pick, now);
		this.cooling = Mth.lerp(random.nextFloat(), Smoke.COOLING_MIN, Smoke.COOLING_MAX);
		this.nextGust = Smoke.GUST_GAP_MIN + random.nextInt(Smoke.GUST_GAP_MAX - Smoke.GUST_GAP_MIN);
		table.playBlock(SoundEvents.SMOKER_SMOKE, 1.0F, 1.0F);
	}

	@Override
	boolean use(ServerPlayer player, ItemStack held, boolean sneaking, int now) {
		return CookTools.Tool.FAN.holds(held);
	}

	@Override
	void input(ServerPlayer player, int what, float value, int now) {
		if (what != HandsOn.IN_HOLD) {
			return;
		}
		if (value > 0.5F && CookTools.Tool.FAN.holds(player.getMainHandItem())) {
			holding.put(player.getUUID(), now);
			touch(player, now);
		} else {
			holding.remove(player.getUUID());
		}
	}

	@Override
	void tick(int now) {
		holding.values().removeIf(at -> now - at > HandsOn.HOLD_STALE);
		fanning = !holding.isEmpty();
		ticks++;
		heat -= cooling;
		if (fanning) {
			heat += Smoke.FANNING;
		}
		// wind: every so often a gust flares the coals or blows the heat away for a moment
		if (gustTicks > 0) {
			heat += gustVelocity;
			gustTicks--;
		} else if (--nextGust <= 0) {
			gustVelocity = (random.nextBoolean() ? 1 : -1) * Mth.lerp(random.nextFloat(), Smoke.GUST_MIN, Smoke.GUST_MAX);
			gustTicks = Smoke.GUST_TICKS_MIN + random.nextInt(Smoke.GUST_TICKS_MAX - Smoke.GUST_TICKS_MIN);
			nextGust = Smoke.GUST_GAP_MIN + random.nextInt(Smoke.GUST_GAP_MAX - Smoke.GUST_GAP_MIN);
		}
		heat = Mth.clamp(heat, 0.0F, 1.0F);
		if (heat >= Smoke.BAND_MIN && heat <= Smoke.BAND_MAX) {
			cure++;
		} else if (heat < Smoke.BAND_MIN && cure > 0) {
			cold++;
		}
		if (heat > Smoke.SCORCH) {
			scorch++;
		}
		if (now % 3 == 0) {
			table.level.sendParticles(heat > Smoke.SCORCH ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CAMPFIRE_COSY_SMOKE,
				table.pos.getX() + 0.5, table.pos.getY() + 1.0, table.pos.getZ() + 0.5, 1, 0.1, 0.02, 0.1, 0.01);
		}
		if (cure >= Smoke.CURE_TICKS) {
			end(Smoke.judge(scorch, cold, true));
		} else if (scorch >= Smoke.SCORCH_CHARRED || ticks >= Smoke.TIME_LIMIT) {
			end(Smoke.judge(scorch, cold, false));
		}
	}

	/** Walking away leaves the smoker unattended: it burns. */
	@Override
	void leave() {
		end(Quality.BURNT);
	}

	/** ints: ticks cured, scorched, cold, ticks, fanning (1 or 0); floats: the heat. */
	@Override
	void fill(List<Integer> ints, List<Float> floats) {
		ints.add(cure);
		ints.add(scorch);
		ints.add(cold);
		ints.add(ticks);
		ints.add(fanning ? 1 : 0);
		floats.add(heat);
	}
}
