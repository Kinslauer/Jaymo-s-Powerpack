package com.jaymoskingdoms.harvest;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Harvest Lich's skeleton horse. Nobody else rides it (it can't be mounted, saddled, bred or led), it is the lich's to
 * steer and it is gone with the lich. It is faster and tougher than the common kind; when it is killed the lich fights on
 * on foot.
 */
public final class LichSteed extends SkeletonHorse {
	/** Its speed and toughness; the common skeleton horse's are 0.2 and 15. */
	static final double SPEED = 0.34, HEALTH = 120.0;

	public LichSteed(EntityType<? extends LichSteed> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return SkeletonHorse.createAttributes().add(Attributes.MAX_HEALTH, HEALTH).add(Attributes.MOVEMENT_SPEED, SPEED)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
	}

	@Override
	protected void randomizeAttributes(net.minecraft.util.RandomSource random) {
		// (its attributes are the lich's to set, not the horse's luck)
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		return InteractionResult.PASS;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
		return null;
	}
}
