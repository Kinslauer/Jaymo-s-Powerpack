package com.jaymoskitchen.cooking.hands;

import com.jaymoskitchen.ModItems;
import com.jaymoskitchen.cooking.Prep;
import com.jaymoskitchen.cooking.Rolling;
import com.jaymoskitchen.herb.JointItem;
import com.jaymoskitchen.herb.JointQuality;
import java.util.Arrays;
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
 * Rolling a joint on a worktop, by hand, in three steps, each done holding right-click: grind (point round the
 * worktop's middle, turning the grinder), sprinkle (the herb falls where you point, along the paper), roll (move the
 * view up and down at a steady pace; too fast tears the paper). The same game and numbers as the screen's (see
 * {@link Rolling}); the herb and the roll are shared by whoever's holding.
 */
final class JointRun extends Run {
	/** How long a step's verdict shows before the next starts (ticks). */
	private static final int VERDICT_TICKS = 20;
	/** How many of the screen game's pixels one degree of view movement counts for in the roll. */
	static final float ROLL_PX_PER_DEGREE = 6.0F;
	/** The most one cook's view can move in a tick (degrees) and still be a hand. */
	private static final float MAX_ROLL_DEGREES = 25.0F;
	private static final int GRIND = 0, SPRINKLE = 1, ROLL = 2;

	private final Vec3 centre;
	private final Map<UUID, Integer> holding = new HashMap<>();
	private final Map<UUID, Float> lastAngle = new HashMap<>();
	private final Map<UUID, Float> across = new HashMap<>();
	private int phase = GRIND;
	// grind
	private final int grindTarget;
	private float grindAngle;
	private int grindTicks;
	private boolean ground;
	// sprinkle
	private final float[] segments = new float[Rolling.SEGMENTS];
	private float supply = Rolling.SUPPLY;
	private final float sprinkleRate;
	private float evenness;
	// roll
	private float rollProgress;
	private float tears;
	private final float maxSpeed;
	private float moved;
	private int rollTicks;
	/** The step's verdict (0 none, then see VERDICTS) and how long it still shows. */
	private int verdict;
	private int verdictTicks = -1;

	JointRun(Table table, Pick pick, int now) {
		super(table, pick, now);
		this.centre = Geometry.topCenter(table.level, table.pos);
		this.grindTarget = Rolling.GRIND_TURNS_MIN + random.nextInt(Rolling.GRIND_TURNS_MAX - Rolling.GRIND_TURNS_MIN + 1);
		this.sprinkleRate = Mth.lerp(random.nextFloat(), Rolling.SPRINKLE_RATE_MIN, Rolling.SPRINKLE_RATE_MAX);
		this.maxSpeed = Mth.lerp(random.nextFloat(), Rolling.ROLL_MAX_SPEED_MIN, Rolling.ROLL_MAX_SPEED_MAX);
	}

	@Override
	boolean use(ServerPlayer player, ItemStack held, boolean sneaking, int now) {
		return held.isEmpty();
	}

	@Override
	boolean attack(ServerPlayer player, ItemStack held, int now) {
		return held.isEmpty();
	}

	private boolean holds(UUID id, int now) {
		Integer at = holding.get(id);
		return at != null && now - at <= HandsOn.HOLD_STALE;
	}

	@Override
	void input(ServerPlayer player, int what, float value, int now) {
		UUID id = player.getUUID();
		switch (what) {
			case HandsOn.IN_HOLD -> {
				if (value > 0.5F) {
					holding.put(id, now);
					touch(player, now);
				} else {
					holding.remove(id);
					lastAngle.remove(id);
				}
			}
			case HandsOn.IN_ANGLE -> {
				if (phase != GRIND || verdictTicks >= 0 || !holds(id, now) || Float.isNaN(value) || Float.isInfinite(value)) {
					lastAngle.remove(id);
					return;
				}
				Float before = lastAngle.put(id, value);
				if (before != null) {
					grindAngle += Math.max(-1.2F, Math.min(1.2F, Geometry.turn(before, value)));
				}
			}
			case HandsOn.IN_ACROSS -> across.put(id, Float.isNaN(value) ? Float.NaN : Mth.clamp(value, 0.0F, 1.0F));
			case HandsOn.IN_ROLL -> {
				if (phase == ROLL && verdictTicks < 0 && holds(id, now) && !Float.isNaN(value)) {
					moved += Math.min(Math.abs(value), MAX_ROLL_DEGREES) * ROLL_PX_PER_DEGREE;
				}
			}
			default -> {
			}
		}
	}

	private void verdict(int code, Runnable then) {
		verdict = code;
		verdictTicks = VERDICT_TICKS;
		after = then;
	}

	private Runnable after = () -> {
	};

	@Override
	void tick(int now) {
		if (verdictTicks > 0) {
			if (--verdictTicks == 0) {
				verdictTicks = -1;
				verdict = 0;
				after.run();
			}
			return;
		}
		switch (phase) {
			case GRIND -> tickGrind(now);
			case SPRINKLE -> tickSprinkle(now);
			case ROLL -> tickRoll(now);
			default -> {
			}
		}
	}

	private float turns() {
		return Math.abs(grindAngle) / Mth.TWO_PI;
	}

	private void tickGrind(int now) {
		if (turns() >= grindTarget) {
			ground = true;
			table.playBlock(SoundEvents.GRINDSTONE_USE, 0.6F, 1.3F);
			verdict(1, () -> phase = SPRINKLE);
		} else if (++grindTicks > Rolling.GRIND_TICKS) {
			verdict(2, () -> finish(false));
		} else if (now % 5 == 0 && !lastAngle.isEmpty()) {
			table.playBlock(SoundEvents.GRINDSTONE_USE, 0.25F, 1.5F);
		}
	}

	private void tickSprinkle(int now) {
		if (supply <= 0.0F) {
			return;
		}
		for (Map.Entry<UUID, Float> entry : across.entrySet()) {
			if (!holds(entry.getKey(), now) || Float.isNaN(entry.getValue()) || supply <= 0.0F) {
				continue;
			}
			float amount = Math.min(sprinkleRate, supply);
			supply -= amount;
			// it doesn't fall perfectly straight
			double u = entry.getValue() + (random.nextFloat() - 0.5F) / Rolling.SEGMENTS * 0.7F;
			int segment = (int) Math.floor(u * Rolling.SEGMENTS);
			if (segment >= 0 && segment < Rolling.SEGMENTS) {
				segments[segment] += amount;
			}
			if (now % 3 == 0) {
				table.level.sendParticles(ParticleTypes.COMPOSTER, table.pos.getX() + u, centre.y + 0.1, centre.z, 1, 0.02, 0.02, 0.02, 0.0);
			}
		}
		if (supply <= 0.0F) {
			evenness = Rolling.evenness(segments);
			if (evenness < Rolling.EVEN_ENOUGH) {
				verdict(3, () -> finish(false));
			} else {
				verdict(evenness >= Rolling.PERFECT_EVENNESS ? 4 : 5, () -> phase = ROLL);
			}
		}
	}

	private void tickRoll(int now) {
		float speed = moved;
		moved = 0.0F;
		if (speed > maxSpeed) {
			tears += (speed - maxSpeed) * 0.5F;
			rollProgress += maxSpeed;
			table.playBlock(SoundEvents.BOOK_PAGE_TURN, 0.8F, 0.6F);
		} else if (speed >= Rolling.ROLL_MIN_SPEED) {
			rollProgress += speed;
			if (now % 4 == 0) {
				table.playBlock(SoundEvents.BOOK_PAGE_TURN, 0.3F, 1.4F);
			}
		}
		if (tears >= Rolling.TEAR_LIMIT) {
			verdict(6, () -> finish(false));
		} else if (rollProgress >= Rolling.ROLL_LENGTH) {
			finish(true);
		} else if (++rollTicks > Rolling.ROLL_TICKS) {
			verdict(2, () -> finish(false));
		}
	}

	private void finish(boolean rolled) {
		Prep.Result result = Rolling.score(ground, evenness, tears, rolled);
		endWith(result.ordinal(), () -> {
			if (result.amount() > 0) {
				ItemStack joints = new ItemStack(ModItems.JOINT, result.amount());
				JointItem.stamp(joints, result == Prep.Result.PERFECT ? JointQuality.PERFECT
					: tears >= Rolling.TORN_TEARS ? JointQuality.TORN : JointQuality.CLEAN);
				table.handOver(this, joints);
			}
			table.playBlock(result == Prep.Result.RUINED ? SoundEvents.SLIME_SQUISH : SoundEvents.PLAYER_LEVELUP,
				result == Prep.Result.PERFECT ? 1.0F : 0.6F, 1.0F);
		});
	}

	@Override
	void leave() {
		endWith(Prep.Result.RUINED.ordinal(), () -> {
		});
	}

	/**
	 * ints: step (0 grind, 1 sprinkle, 2 roll), verdict code (0 none, 1 ground, 2 too slow, 3 uneven, 4 even, 5 lumpy, 6 torn);
	 * floats: turns, turns wanted, herb left, evenness, roll progress, tears, the most speed before tearing, then each stretch of paper.
	 */
	@Override
	void fill(List<Integer> ints, List<Float> floats) {
		ints.add(phase);
		ints.add(verdict);
		floats.add(turns());
		floats.add((float) grindTarget);
		floats.add(supply);
		floats.add(evenness);
		floats.add(rollProgress);
		floats.add(tears);
		floats.add(maxSpeed);
		for (float segment : segments) {
			floats.add(segment);
		}
	}

	@Override
	public String toString() {
		return "JointRun" + Arrays.toString(segments);
	}
}
