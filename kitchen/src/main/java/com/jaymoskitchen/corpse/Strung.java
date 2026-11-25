package com.jaymoskitchen.corpse;

import com.jaymoskitchen.ragdoll.RagdollBody;
import com.jaymoskitchen.ragdoll.RagdollRig;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * A body hung up on a lead: use a lead on the body, then on a fence post, and it hangs there (by the wrists, if it has
 * hands, otherwise by the neck), swaying a little, and keeps without rotting. Use the body again to take it down; the
 * lead comes back. Not the butcher's hook ({@link Hanging}), which holds an animal by the feet to be dressed.
 */
public final class Strung {
	/** How far from the post the body hangs (blocks), how high above the post's top the hold is, and how far it sways (blocks, radians a tick). */
	static final double OFFSET = 0.55, ABOVE = 0.7, SWAY = 0.05, SWAY_RATE = 0.06;
	/** How far (blocks) a body may be from the player who ties it to the post. */
	static final double REACH_SQ = 8.0 * 8.0;
	/** How often (ticks) a hung body checks its post is still there. */
	static final int CHECK_EVERY = 10;
	/** Who has a lead tied to which body, waiting for a post. */
	private static final Map<UUID, UUID> TIED = new HashMap<>();

	private Strung() {
	}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			ItemStack held = player.getItemInHand(hand);
			UUID body = TIED.get(player.getUUID());
			if (body == null || !held.is(Items.LEAD) || !level.getBlockState(hit.getBlockPos()).is(BlockTags.FENCES)
				|| !(level instanceof ServerLevel server) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			TIED.remove(player.getUUID());
			if (!(server.getEntity(body) instanceof CorpseEntity corpse) || corpse.isRemoved() || corpse.distanceToSqr(player) > REACH_SQ) {
				return InteractionResult.PASS;
			}
			held.consume(1, player);
			corpse.stringUp(hit.getBlockPos());
			server.playSound(null, hit.getBlockPos(), SoundEvents.LEAD_TIED, SoundSource.BLOCKS, 1.0F, 0.8F);
			return InteractionResult.SUCCESS_SERVER;
		});
	}

	/** Whether {@code corpse} is a body that can be hung up: a loose body small enough to lift. */
	static boolean hangable(CorpseEntity corpse) {
		return !corpse.isFish() && !corpse.isMachine() && !corpse.isHeavy() && !corpse.tooBigToLift() && corpse.carrier() == null && !corpse.onHook();
	}

	/** A lead used on {@code corpse}: it's tied, for the next fence post; or, if it's hung already, taken down. Null if neither. */
	static InteractionResult use(CorpseEntity corpse, ServerLevel level, Player player, ItemStack held) {
		if (corpse.strung() != null) {
			cutDown(corpse, level);
			return InteractionResult.SUCCESS_SERVER;
		}
		if (!held.is(Items.LEAD) || !hangable(corpse)) {
			return null;
		}
		TIED.put(player.getUUID(), corpse.getUUID());
		level.playSound(null, corpse.getX(), corpse.getY(), corpse.getZ(), SoundEvents.LEAD_TIED, SoundSource.NEUTRAL, 0.8F, 1.0F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.strung.tied"));
		return InteractionResult.SUCCESS_SERVER;
	}

	/** Takes {@code corpse} down off its post: it falls as it hung, and the lead drops. */
	static void cutDown(CorpseEntity corpse, ServerLevel level) {
		corpse.unstring();
		level.playSound(null, corpse.getX(), corpse.getY(), corpse.getZ(), SoundEvents.LEAD_UNTIED, SoundSource.NEUTRAL, 1.0F, 1.0F);
		level.addFreshEntity(new ItemEntity(level, corpse.getX(), corpse.getY() + 0.5, corpse.getZ(), new ItemStack(Items.LEAD)));
	}

	/** Server, each tick it hangs: held at its post (taken down if the post is gone), swaying a little. */
	static void tick(CorpseEntity corpse, ServerLevel level, RagdollBody body) {
		BlockPos post = corpse.strung();
		if (post == null) {
			return;
		}
		if (corpse.tickCount % CHECK_EVERY == 0 && !level.getBlockState(post).is(BlockTags.FENCES)) {
			cutDown(corpse, level);
			return;
		}
		double side = corpse.hangSide();
		double sway = Math.sin(corpse.tickCount * SWAY_RATE) * SWAY;
		double x = post.getX() + 0.5 + Math.cos(side) * OFFSET + sway * Math.sin(side);
		double z = post.getZ() + 0.5 + Math.sin(side) * OFFSET - sway * Math.cos(side);
		double y = post.getY() + ABOVE + 1.0;
		if (body.rig.shape == RagdollRig.Shape.HUMANOID) {
			body.grip(0, RagdollRig.R_HAND, x, y, z);
			body.grip(1, RagdollRig.L_HAND, x, y, z);
		} else {
			body.grip(0, neck(body.rig), x, y, z);
		}
	}

	/** The point a body without hands hangs by. */
	private static int neck(RagdollRig rig) {
		return switch (rig.shape) {
			case QUADRUPED -> RagdollRig.SNOUT;
			case BOX -> RagdollRig.TOP_RIGHT;
			case CHAIN -> RagdollRig.CHAIN_FIRST;
			case HUMANOID -> RagdollRig.NECK;
		};
	}

	/** The side of the post (radians) a body at {@code corpse} hangs on: the way it already lies from it. */
	static double sideOf(BlockPos post, Entity corpse) {
		Vec3 away = corpse.position().subtract(post.getX() + 0.5, 0.0, post.getZ() + 0.5);
		return Math.atan2(away.z, away.x);
	}
}
