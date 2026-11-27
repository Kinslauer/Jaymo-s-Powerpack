package com.jaymospowers.freerun;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.GroundCracks;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.jump.PowerJump;
import com.jaymospowers.power.web.WebPower;
import com.jaymospowers.stamina.Feathers;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Transient, server-authored player clips and the shared second jump. */
public final class CoreMotion {
	private static final double LIGHT_FALL = 1.75, MEDIUM_FALL = 4.0, HEAVY_FALL = 8.0, HERO_FALL = 14.0;
	private static final int JUMP_COST = 4;
	private static final double JUMP_LIFT = 0.35, LEAP_LIFT = 0.45, LEAP_SPEED = 0.48;

	public record Clip(Identifier animation, long until) {
		private static final StreamCodec<ByteBuf, Clip> CODEC = StreamCodec.composite(
			Identifier.STREAM_CODEC, Clip::animation, ByteBufCodecs.VAR_LONG, Clip::until, Clip::new);
	}

	public static final AttachmentType<Clip> CLIP = AttachmentRegistry.create(JaymosPowers.id("core_motion"),
		b -> b.syncWith(Clip.CODEC, AttachmentSyncPredicate.all()));
	private static final AttachmentType<Boolean> JUMPED = AttachmentRegistry.create(JaymosPowers.id("core_jumped"));

	public record Jump() implements CustomPacketPayload {
		public static final Type<Jump> TYPE = new Type<>(JaymosPowers.id("core_jump"));
		private static final StreamCodec<ByteBuf, Jump> CODEC = StreamCodec.unit(new Jump());

		@Override
		public Type<Jump> type() {
			return TYPE;
		}
	}

	private CoreMotion() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Jump.TYPE, Jump.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Jump.TYPE, (payload, ctx) -> jump(ctx.player()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.onGround()) {
					player.removeAttached(JUMPED);
				}
			}
		});
	}

	public static void play(ServerPlayer player, String animation, int ticks) {
		player.setAttached(CLIP, new Clip(JaymosPowers.id(animation), player.level().getGameTime() + ticks));
	}

	public static boolean canJump(Player player) {
		// Powers with their own second jump (PowerJump) keep that one instead of stacking a third.
		return Powers.of(player) != null && PowerJump.kindOf(player) == PowerJump.NONE && !player.onGround() && !player.getAbilities().flying
			&& !player.isFallFlying() && !player.isInWater() && !player.isInLava() && !player.onClimbable()
			&& !player.isUsingItem() && !Slide.sliding(player) && Freerun.canMove(player)
			&& !WebPower.ragdolled(player);
	}

	private static void jump(ServerPlayer player) {
		if (player.hasAttached(JUMPED) || !canJump(player) || !Feathers.spend(player, JUMP_COST)) {
			return;
		}
		player.setAttached(JUMPED, true);
		player.resetFallDistance();
		Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
		AABB feet = new AABB(player.getX() - 0.24, player.getY() - 0.4, player.getZ() - 0.24,
			player.getX() + 0.24, player.getY() + 0.2, player.getZ() + 0.24);
		AABB lip = feet.move(forward.scale(0.8));
		boolean vault = player.getLastClientInput().forward() && !player.level().noCollision(player, lip)
			&& player.level().noCollision(player, player.getBoundingBox().move(forward).move(0, 0.65, 0));
		Vec3 push = vault ? forward.scale(LEAP_SPEED) : player.getKnownMovement();
		Vec3 motion = new Vec3(push.x, vault ? LEAP_LIFT : JUMP_LIFT, push.z);
		player.setDeltaMovement(motion);
		player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), motion));
		play(player, vault ? "animation.player.catleap" : "animation.player.double_jump", vault ? 15 : 11);
	}

	public static void landed(ServerPlayer player, double fall) {
		if (fall < LIGHT_FALL || Powers.of(player) == null || !Freerun.canMove(player)
			|| player.isInWater() || player.isFallFlying() || WebPower.ragdolled(player)) {
			return;
		}
		int tier = fall >= HERO_FALL ? 4 : fall >= HEAVY_FALL ? 3 : fall >= MEDIUM_FALL ? 2 : 1;
		play(player, "animation.player.landingposev" + tier, 12);
		if (tier == 4) {
			GroundCracks.crack(player.level(), player.getOnPos(), player.getRandom(), 3);
			LandingRoll.dust(player.level(), player);
		}
	}
}
