package com.jaymoskingdoms.barrow;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.beasts.Beasts;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Laughing bones: a skeleton with the Skeleton Lord's laughter settled in it, standing in an old barrow whose knights are
 * gone. Knock it down and its skull rolls away laughing ({@link RunningSkull}); left alone a while, the skull grows the
 * rest of itself back. It ends when the skull is smashed, or when the blow that fells the body is gold (gold burns the
 * laughter out, as it does the curse). The body drops nothing: what it carries falls with the skull.
 */
public class LaughingBones extends Skeleton {
	static final int LAUGH_COLOR = 0xB48CFF;

	public LaughingBones(EntityType<? extends LaughingBones> type, Level level) {
		super(type, level);
	}

	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return false;
	}

	@Override
	protected int getBaseExperienceReward(ServerLevel level) {
		return 0;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (!(this.level() instanceof ServerLevel level) || Beasts.RUNNING_SKULL == null) {
			return;
		}
		Vec3 head = this.getEyePosition();
		if (gold(source)) {
			Fx.burst(level, head.x, head.y, head.z, 0xFFD24A, 0.8F, 12, 0.2, 0.2, 0.2);
			level.playSound(null, head.x, head.y, head.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.8F, 1.2F);
			RunningSkull.spill(level, head);
			return;
		}
		RunningSkull skull = Beasts.RUNNING_SKULL.create(level, EntitySpawnReason.TRIGGERED);
		if (skull == null) {
			return;
		}
		skull.snapTo(head.x, head.y, head.z, this.getYRot(), 0.0F);
		// knocked off, away from whoever struck it
		Vec3 from = source.getSourcePosition();
		Vec3 away = from == null ? Vec3.ZERO : head.subtract(from).multiply(1.0, 0.0, 1.0);
		if (away.lengthSqr() > 1.0E-4) {
			away = away.normalize().scale(0.45);
		}
		skull.setDeltaMovement(away.x, 0.35, away.z);
		skull.setPersistenceRequired();
		if (this.hasHome()) {
			skull.setHomeTo(this.getHomePosition(), this.getHomeRadius());
		}
		level.addFreshEntity(skull);
		level.playSound(null, head.x, head.y, head.z, SoundEvents.WITCH_CELEBRATE, SoundSource.HOSTILE, 0.9F, 1.7F);
	}

	/** The blow was struck with a golden weapon or tool. */
	private static boolean gold(DamageSource source) {
		return source.getDirectEntity() instanceof LivingEntity striker && source.getEntity() == striker
			&& striker.getMainHandItem().is(ItemTags.PIGLIN_LOVED) && striker.getMainHandItem().isDamageableItem();
	}
}
