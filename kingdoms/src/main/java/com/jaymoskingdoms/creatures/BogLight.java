package com.jaymoskingdoms.creatures;

import com.jaymoskingdoms.Fear;
import com.jaymoskingdoms.Fx;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A light in the bog: a small cold flame that bobs ahead of a traveller at night and draws them on, always just out of reach,
 * toward something that will hurt them (quicksand, deep water, a drop: {@link BogLights}). It feeds on fear ({@link Fear}): a
 * frightened body near it (and it frightens the ones it has trapped) swells its glow; fed full, it lashes out in a cold
 * shock at the frightened. Struck, it goes out; it is gone by dawn.
 */
public class BogLight extends Beast {
	static final double HEALTH = 8.0, SPEED = 0.14;
	/** It keeps this far (blocks) ahead of the one it leads: nearer than {@code near} it slips on, further than {@code far} it waits. */
	static final double NEAR = 5.0, FAR = 12.0;
	/** Hover this high (blocks) over the ground, and bob this much. */
	static final double HOVER = 1.6, BOB = 0.25;
	/** Fed a point a second by each frightened body within this far (blocks); full at this; a shock then does this much. */
	static final double FEEDS_ON = 18.0;
	static final int FULL = 10, SHOCK_EVERY = 20 * 6;
	static final float SHOCK = 3.0F;
	/** A trapped body (in quicksand) is frightened for this long (ticks) each time. */
	static final int TERROR = 100;

	private @Nullable UUID leads;
	private @Nullable BlockPos goal;
	private int fed;

	public BogLight(EntityType<? extends BogLight> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.xpReward = 6;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return createMonsterAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED).add(Attributes.FOLLOW_RANGE, 32.0)
			.add(Attributes.ATTACK_DAMAGE, 1.0);
	}

	void leads(ServerPlayer player, BlockPos goal) {
		this.leads = player.getUUID();
		this.goal = goal;
	}

	@Override
	protected void registerGoals() {
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		this.setNoGravity(true);
		if (level.isBrightOutside() || this.tickCount > 20 * 60 * 6 || this.leads == null && this.tickCount > 40) {
			this.fade(level);
			return;
		}
		LivingEntity leader = this.leads != null && level.getEntity(this.leads) instanceof LivingEntity l && l.isAlive() ? l : null;
		if (leader == null || this.goal == null) {
			this.fade(level);
			return;
		}
		Vec3 here = this.position();
		Vec3 goalAt = Vec3.atCenterOf(this.goal).add(0.0, HOVER, 0.0);
		double toLeader = here.distanceTo(leader.position());
		Vec3 want = Vec3.ZERO;
		if (here.distanceTo(goalAt) > 1.5 && toLeader < FAR) {
			want = goalAt.subtract(here).normalize().scale(toLeader < NEAR ? SPEED * 1.6 : SPEED);
		}
		double bob = Math.sin(this.tickCount * 0.15) * BOB * 0.1;
		this.setDeltaMovement(this.getDeltaMovement().scale(0.8).add(want.x, want.y + bob, want.z));
		// feeding on fear
		if (this.tickCount % 20 == 0) {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(FEEDS_ON), x -> Fear.frightened(x) && !(x instanceof BogLight))) {
				this.fed = Math.min(FULL, this.fed + 1);
			}
			if (this.fed > 0 && this.tickCount % 100 == 0) {
				this.fed--;
			}
			this.setFullness(this.fed / (float) FULL);
		}
		// the one it leads is stuck in quicksand: the light dims, and the dark is frightening
		if (this.tickCount % 40 == 0 && level.getBlockState(leader.blockPosition()).is(Creatures.QUICKSAND)) {
			Fear.expose(leader, this, TERROR);
			level.playSound(null, leader.getX(), leader.getY(), leader.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 0.8F, 0.5F);
		}
		if (this.fed >= FULL && this.tickCount % SHOCK_EVERY == 0) {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(10.0), x -> Fear.frightened(x) && !(x instanceof BogLight))) {
				e.hurtServer(level, level.damageSources().lightningBolt(), SHOCK);
				Fx.beam(level, here, e.position().add(0, e.getBbHeight() * 0.5, 0), 0.05F, 0x9FE8FF, 6);
				this.fed -= 3;
				break;
			}
		}
		if (this.tickCount % 2 == 0) {
			int colour = this.fed >= FULL ? 0xB8F4FF : 0x7FD6E8;
			level.sendParticles(Fx.wisp(colour, 0.6F + this.fed * 0.07F), this.getX(), this.getY(0.5), this.getZ(), 1, 0.03, 0.03, 0.03, 0.0);
		}
	}

	private void fade(ServerLevel level) {
		level.sendParticles(Fx.mote(0x7FD6E8, 0.6F), this.getX(), this.getY(0.5), this.getZ(), 6, 0.2, 0.2, 0.2, 0.01);
		this.discard();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && this.isAlive()) {
			level.sendParticles(Fx.spark(0x7FD6E8, 0.6F), this.getX(), this.getY(0.5), this.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
		}
		return hurt;
	}

	@Override
	public boolean shouldDropExperience() {
		return true;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
	}
}
