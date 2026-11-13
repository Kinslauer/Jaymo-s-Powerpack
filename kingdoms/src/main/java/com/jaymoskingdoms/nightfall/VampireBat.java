package com.jaymoskingdoms.nightfall;

import com.jaymoskingdoms.creatures.BloodBat;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A vampire bat (after Nightfall Plague's): out at night, or called up by a Night Bleeder ({@link NightBleeder}), and they
 * hunt in flocks. Andy: "the vampire bats should work like the blood bats": each one latches on, drinks, and flies off fat
 * and slow, its bites bleed, and it can be pulled off or swatted ({@link BloodBat}). By day the wild ones roost like any
 * bat; the summoned ones hunt whatever the hour and are gone after a while, like vexes.
 */
public class VampireBat extends BloodBat {
	/** A summoned bat is gone after this long (ticks). */
	static final int SUMMONED_LIFE = 20 * 50;

	private int lifeLeft = -1;

	public VampireBat(EntityType<? extends VampireBat> type, Level level) {
		super(type, level);
	}

	/** Called up by a Night Bleeder against {@code target}: awake, hungry, and gone in a while. */
	void summoned(@Nullable LivingEntity target) {
		this.setResting(false);
		this.lifeLeft = SUMMONED_LIFE;
		this.setBelly(0.0F);
		if (target != null) {
			this.preyId = target.getUUID();
		}
	}

	@Override
	protected boolean huntable(LivingEntity e) {
		return super.huntable(e) && !Nightfall.kin(e);
	}

	@Override
	protected boolean mayHunt(ServerLevel level) {
		return this.lifeLeft >= 0 || super.mayHunt(level);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		if (this.lifeLeft > 0 && --this.lifeLeft == 0) {
			level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 10, 0.2, 0.2, 0.2, 0.02);
			this.discard();
			return;
		}
		super.customServerAiStep(level);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("LifeLeft", this.lifeLeft);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.lifeLeft = input.getIntOr("LifeLeft", -1);
	}
}
