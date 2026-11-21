package com.jaymospowers.bits;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Soul fire burns blue. Whatever sets an entity alight (a burn longer than the one it has) notes whether soul fire or a lit
 * soul campfire was touching it at that moment; the flag is synced to every client, which draws the flames and the
 * first-person fire overlay from the soul fire sprites while it stands. It is only read while the entity burns, and the
 * next ignition (from anything else) sets it again, so a body that walked out of the soul fire keeps its blue flames
 * until they go out.
 */
public final class SoulBurn {
	/** How far the entity's box is shrunk when looking for the fire it touches (blocks), so only blocks it is in count. */
	static final double SHRINK = 0.001;

	/** On an entity, synced to all: its fire was lit by soul fire. */
	public static final AttachmentType<Boolean> BLUE = AttachmentRegistry.create(JaymosPowers.id("soul_burn"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	private SoulBurn() {
	}

	/** Whether {@code entity}'s fire is the blue kind. */
	public static boolean blue(Entity entity) {
		return Boolean.TRUE.equals(entity.getAttached(BLUE));
	}

	/** From {@code Entity#setRemainingFireTicks} on the server when {@code entity} is being lit (or its burn lengthened). */
	public static void ignited(Entity entity) {
		boolean soul = touching(entity);
		if (soul != blue(entity)) {
			if (soul) {
				entity.setAttached(BLUE, true);
			} else {
				entity.removeAttached(BLUE);
			}
		}
	}

	private static boolean touching(Entity entity) {
		AABB box = entity.getBoundingBox().deflate(SHRINK);
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
			BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			BlockState state = entity.level().getBlockState(pos);
			if (state.is(Blocks.SOUL_FIRE) || state.is(Blocks.SOUL_CAMPFIRE) && CampfireBlock.isLitCampfire(state)) {
				return true;
			}
		}
		return false;
	}
}
