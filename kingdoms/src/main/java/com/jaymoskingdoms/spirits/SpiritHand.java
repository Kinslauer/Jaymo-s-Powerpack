package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Mana;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.279) A spirit's floating hand, after Mage Hand (CurseForge, SkylorBeck; GPL-3.0, ideas only, our own code and look).
 * Andy: "the hands shouldnt stay behind. just make the hand follow the wispy ki glow texture, and only use it for the far
 * reach, and catch." A hand of the aura alone, no arm, drawn in the ki glow's wisps (the client's {@code SpiritHandRenderer}):
 * <ul>
 * <li><b>Far hand</b> ({@link #reach}): it flies from its keeper to the chest, lever, door or item in sight, open, and only on
 * getting there does the thing happen (the chest opens, the item is taken); it closes on it and flies home, carrying the
 * item along with it, and fades at its keeper's side.</li>
 * <li><b>Catch</b> ({@link #guard}, {@link #snatch}): while the catch lasts an open hand hovers ahead of its keeper; each shot it
 * snatches, a hand closes on it where it was caught and is gone.</li>
 * </ul>
 * Short lived and never saved. Only the attuned see it.
 */
public final class SpiritHand extends Entity {
	/** How fast it flies out and home (blocks a tick), and how near counts as there. */
	static final double OUT_SPEED = 2.0, HOME_SPEED = 1.4, THERE = 0.5;
	/** Ticks it holds on what it reached before it turns home, and the longest it may live. */
	static final int GRIP_TICKS = 5, MAX_LIFE = 80;
	/** A snatching hand: ticks it holds the shot before it is gone. */
	static final int SNATCH_TICKS = 8;
	/** The guarding hand: how far ahead of its keeper's eyes it hovers (blocks), and how far below them. */
	static final double GUARD_AHEAD = 1.3, GUARD_DOWN = 0.35;

	public static EntityType<SpiritHand> TYPE;

	/** Open (reaching, guarding) or closed (gripping, carrying). */
	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(SpiritHand.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> CLOSED = SynchedEntityData.defineId(SpiritHand.class, EntityDataSerializers.BOOLEAN);
	/** How faded it is, 0 whole to 1 gone (it fades at the end of its life). */
	private static final EntityDataAccessor<Float> FADE = SynchedEntityData.defineId(SpiritHand.class, EntityDataSerializers.FLOAT);

	private enum Phase { OUT, GRIP, HOME, GUARD, SNATCH }

	private @Nullable ServerPlayer keeper;
	private Phase phase = Phase.OUT;
	private Vec3 goal = Vec3.ZERO;
	/** What it carries home (an item it reached), followed rather than flown to. */
	private @Nullable Entity carried;
	/** What it does on getting there. */
	private @Nullable Runnable arrive;
	private int phaseTicks, until;

	public SpiritHand(EntityType<? extends SpiritHand> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	/** Registers the entity type (from {@link HandMoves#register}). */
	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("spirit_hand"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<SpiritHand>of(SpiritHand::new, MobCategory.MISC)
			.noLootTable()
			.noSave()
			.fireImmune()
			.sized(0.4F, 0.4F)
			.clientTrackingRange(8)
			.updateInterval(1)
			.build(key));
	}

	/** Where a hand leaves its keeper from: just ahead of their chest. */
	private static Vec3 from(ServerPlayer keeper) {
		return keeper.getEyePosition().add(keeper.getLookAngle().scale(0.6)).add(0.0, -0.4, 0.0);
	}

	private static SpiritHand make(ServerPlayer keeper, SpiritState state, Vec3 at, Phase phase) {
		ServerLevel level = (ServerLevel)keeper.level();
		SpiritHand hand = new SpiritHand(TYPE, level);
		hand.keeper = keeper;
		hand.phase = phase;
		hand.entityData.set(KIND, state.kind());
		hand.setPos(at.x, at.y, at.z);
		hand.face(keeper.getLookAngle());
		level.addFreshEntity(hand);
		return hand;
	}

	/**
	 * Far hand: a hand flies from {@code keeper} to {@code to}, and {@code arrive} happens when it gets there; it then flies
	 * home, carrying {@code carry} if that is set by then ({@link #carry}).
	 */
	static SpiritHand reach(ServerPlayer keeper, SpiritState state, Vec3 to, @Nullable Runnable arrive) {
		SpiritHand hand = make(keeper, state, from(keeper), Phase.OUT);
		hand.goal = to;
		hand.arrive = arrive;
		return hand;
	}

	/** What happens when it gets there (set after {@link #reach}, so it may name the hand). */
	void onArrive(Runnable then) {
		this.arrive = then;
	}

	/** The far hand carries {@code item} home with it (it follows the item as the item is pulled in). */
	void carry(Entity item) {
		this.carried = item;
	}

	/** Catch: an open hand hovers ahead of {@code keeper} for {@code ticks}. */
	static SpiritHand guard(ServerPlayer keeper, SpiritState state, int ticks) {
		SpiritHand hand = make(keeper, state, from(keeper), Phase.GUARD);
		hand.until = ticks;
		return hand;
	}

	/** Catch: a hand closes on a shot snatched at {@code at}, and is gone. */
	static void snatch(ServerPlayer keeper, SpiritState state, Vec3 at) {
		SpiritHand hand = make(keeper, state, at, Phase.SNATCH);
		hand.entityData.set(CLOSED, true);
	}

	public int kind() {
		return this.entityData.get(KIND);
	}

	public boolean closed() {
		return this.entityData.get(CLOSED);
	}

	public float fade() {
		return this.entityData.get(FADE);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(KIND, 0);
		builder.define(CLOSED, false);
		builder.define(FADE, 0.0F);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer keeper = this.keeper;
		if (keeper == null || !keeper.isAlive() || keeper.level() != level || this.tickCount > MAX_LIFE) {
			this.discard();
			return;
		}
		this.phaseTicks++;
		switch (this.phase) {
			case OUT -> {
				if (this.fly(this.goal, OUT_SPEED)) {
					Mana.sendSeen(level, Mana.MOTE, this.getX(), this.getY(), this.getZ(), 4, 0.1, 0.1, 0.1, 0.02);
					this.entityData.set(CLOSED, true);
					this.next(Phase.GRIP);
					Runnable then = this.arrive;
					this.arrive = null;
					if (then != null) {
						then.run();
					}
				}
			}
			case GRIP -> {
				if (this.phaseTicks >= GRIP_TICKS) {
					this.next(Phase.HOME);
				}
			}
			case HOME -> {
				Entity item = this.carried;
				if (item != null && item.isAlive()) {
					// with the item it carries, at the item's pace
					Vec3 at = item.position().add(0.0, item.getBbHeight() * 0.5 + 0.1, 0.0);
					this.face(at.subtract(this.position()));
					this.setPos(at.x, at.y, at.z);
				} else {
					this.carried = null;
					Vec3 home = from(keeper);
					if (this.fly(home, HOME_SPEED) || this.position().distanceToSqr(keeper.getEyePosition()) < 1.2) {
						this.vanish(level);
					}
				}
			}
			case GUARD -> {
				Vec3 eye = keeper.getEyePosition();
				Vec3 at = eye.add(keeper.getLookAngle().scale(GUARD_AHEAD)).add(0.0, -GUARD_DOWN, 0.0);
				this.setPos(at.x, at.y, at.z);
				this.face(keeper.getLookAngle());
				if (this.phaseTicks >= this.until) {
					this.vanish(level);
				}
			}
			case SNATCH -> {
				this.entityData.set(FADE, Mth.clamp(this.phaseTicks / (float)SNATCH_TICKS, 0.0F, 1.0F));
				if (this.phaseTicks >= SNATCH_TICKS) {
					this.discard();
				}
			}
		}
	}

	/** One step toward {@code to} at {@code speed}: whether it is there. */
	private boolean fly(Vec3 to, double speed) {
		Vec3 way = to.subtract(this.position());
		double d = way.length();
		if (d > 1.0E-4) {
			this.face(way);
		}
		Vec3 next = d <= speed ? to : this.position().add(way.scale(speed / d));
		this.setPos(next.x, next.y, next.z);
		return d <= Math.max(speed, THERE);
	}

	/** Points the hand along {@code dir}. */
	private void face(Vec3 dir) {
		if (dir.lengthSqr() < 1.0E-6) {
			return;
		}
		this.setYRot((float)(Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG));
		this.setXRot((float)(-Mth.atan2(dir.y, dir.horizontalDistance()) * Mth.RAD_TO_DEG));
	}

	private void next(Phase phase) {
		this.phase = phase;
		this.phaseTicks = 0;
	}

	/** Back at its keeper: a few motes and it is gone. */
	private void vanish(ServerLevel level) {
		Mana.sendSeen(level, Mana.MOTE, this.getX(), this.getY(), this.getZ(), 6, 0.1, 0.1, 0.1, 0.02);
		this.discard();
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 64.0 * 64.0;
	}
}
