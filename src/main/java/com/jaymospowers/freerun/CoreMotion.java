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
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
	public static final int JUMP_COST = 4;
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
		ServerTickEvents.END_SERVER_TICK.register(CoreMotion::resolveLandings);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			LANDINGS.remove(handler.getPlayer().getUUID());
			OWN_CRACKS.remove(handler.getPlayer().getUUID());
		});
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
		// the step's lift is taken off the fall, not the whole fall
		double lift = (vaultAhead(player) ? LEAP_LIFT : JUMP_LIFT);
		player.fallDistance = Math.max(0.0, player.fallDistance - lift * lift / 0.14);
		Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
		boolean vault = vaultAhead(player);
		Vec3 push = vault ? forward.scale(LEAP_SPEED) : player.getKnownMovement();
		Vec3 motion = new Vec3(push.x, vault ? LEAP_LIFT : JUMP_LIFT, push.z);
		player.setDeltaMovement(motion);
		player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), motion));
		play(player, vault ? "animation.player.catleap" : "animation.player.double_jump", vault ? 15 : 11);
	}

	/** A ledge just ahead that the step can clear: the cat leap instead of the straight double jump. */
	private static boolean vaultAhead(ServerPlayer player) {
		Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
		AABB feet = new AABB(player.getX() - 0.24, player.getY() - 0.4, player.getZ() - 0.24,
			player.getX() + 0.24, player.getY() + 0.2, player.getZ() + 0.24);
		AABB lip = feet.move(forward.scale(0.8));
		return player.getLastClientInput().forward() && !player.level().noCollision(player, lip)
			&& player.level().noCollision(player, player.getBoundingBox().move(forward).move(0, 0.65, 0));
	}

	/** A hard landing waiting to be told what it was: its tick, and whether the hero landing took the dust. */
	private static final class Landing {
		final long at;
		boolean heroDust;

		Landing(long at) {
			this.at = at;
		}
	}

	/** Heavy landings whose cracks and dust are still to come, by player (server memory only). */
	private static final java.util.Map<java.util.UUID, Landing> LANDINGS = new java.util.HashMap<>();
	/** The tick each player's Gravity landing threw its own cracks. */
	private static final java.util.Map<java.util.UUID, Long> OWN_CRACKS = new java.util.HashMap<>();

	public static void landed(ServerPlayer player, double fall) {
		if (fall < LIGHT_FALL || Powers.of(player) == null || !Freerun.canMove(player)
			|| player.isInWater() || player.isFallFlying() || WebPower.ragdolled(player)) {
			return;
		}
		int tier = fall >= HERO_FALL ? 4 : fall >= HEAVY_FALL ? 3 : fall >= MEDIUM_FALL ? 2 : 1;
		play(player, "animation.player.landingposev" + tier, 12);
		if (tier == 4) {
			// the damage rules come after this (LandingRoll): the cracks and dust wait a tick to hear what the landing was
			LANDINGS.put(player.getUUID(), new Landing(player.level().getGameTime()));
		}
	}

	/** Drops the landing clip, if one is playing (another landing, or a roll, draws the body). */
	private static void dropClip(ServerPlayer player) {
		Clip clip = player.getAttached(CLIP);
		if (clip != null && clip.animation().getPath().startsWith("animation.player.landingposev")) {
			player.removeAttached(CLIP);
		}
	}

	/** The hero landing ({@link LandingRoll#heroLanding}) drew this landing and threw its dust: no clip, no second ring. */
	static void heroLanded(ServerPlayer player) {
		dropClip(player);
		Landing landing = LANDINGS.get(player.getUUID());
		if (landing != null) {
			landing.heroDust = true;
		}
	}

	/** A roll or the safety tap took this landing, or the fall will kill: no clip over it, and no cracks or dust. */
	static void landingTaken(ServerPlayer player) {
		dropClip(player);
		LANDINGS.remove(player.getUUID());
	}

	/** Gravity's leap landing threw its own cracks this tick. */
	public static void ownCracks(ServerPlayer player) {
		OWN_CRACKS.put(player.getUUID(), player.level().getGameTime());
	}

	private static void resolveLandings(net.minecraft.server.MinecraftServer server) {
		if (LANDINGS.isEmpty()) {
			return;
		}
		java.util.Iterator<java.util.Map.Entry<java.util.UUID, Landing>> it = LANDINGS.entrySet().iterator();
		while (it.hasNext()) {
			java.util.Map.Entry<java.util.UUID, Landing> entry = it.next();
			Landing landing = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				it.remove();
			} else if (player.level().getGameTime() > landing.at) {
				it.remove();
				Long own = OWN_CRACKS.get(player.getUUID());
				if (player.isAlive() && (own == null || own < landing.at - 1)) {
					GroundCracks.crack(player.level(), player.getOnPos(), player.getRandom(), 3);
				}
				if (player.isAlive() && !landing.heroDust) {
					LandingRoll.dust(player.level(), player);
				}
			}
		}
		if (!OWN_CRACKS.isEmpty()) {
			long now = server.overworld().getGameTime();
			OWN_CRACKS.values().removeIf(at -> now - at > 5);
		}
	}
}
