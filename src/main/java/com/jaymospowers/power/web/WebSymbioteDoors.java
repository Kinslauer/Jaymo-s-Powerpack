package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Doors: the big bodies (the big suit and the red slender body, {@link WebSymbiote#bulked}) are taller than a 1x2 gap, so
 * they congeal through it and form whole on the other side. Any time the body is driven at a gap it only just can't pass
 * (walked by you or by a takeover), it narrows: its size is cut by {@link #CUT} (a transient modifier on the scale
 * attribute, so the hitbox really does fit a one-wide, two-high opening), eased in over {@link #RAMP} and held while it's
 * in the gap, and it reforms, again eased, only once the full size has room. The share (0-1) is synced to everyone for
 * the client's squeeze (the suit narrows and stretches through: {@code WebSuitGrowth}).
 * <p>
 * (Takeover walks tearing wooden doors off and biting through leaves, and iron doors stopping them, are in
 * {@code WebTakeoverPath}.)
 */
public final class WebSymbioteDoors {
	/** How much of its size it gives up when squeezed (0.32 takes the big body, 1.5 times a player, down to 1.02: under two blocks). */
	public static final float CUT = 0.32F;
	/** Share gained a tick going in, lost a tick coming out (about three ticks in, six out). */
	static final float RAMP = 0.34F, RELEASE = 0.17F;
	/** It looks this far ahead of itself (blocks) for a gap it only fits through squeezed. */
	static final double PROBE = 0.45;
	/** It's going this fast (blocks a tick) or faster, or driven, for the direction to count. */
	static final double MIN_SPEED = 0.04;
	/** The squeeze's share, synced to everyone. */
	public static final AttachmentType<Float> SQUEEZE = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_squeeze"),
		builder -> builder.syncWith(net.minecraft.network.codec.ByteBufCodecs.FLOAT, AttachmentSyncPredicate.all()));
	private static final Identifier SQUEEZE_ID = JaymosPowers.id("web_symbiote_squeeze");
	private static final Map<UUID, Vec3> LAST = new HashMap<>();

	private WebSymbioteDoors() {
	}

	static void register() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST.remove(handler.player.getUUID()));
	}

	/** How narrowed it is now (0-1), either side. */
	public static float share(net.minecraft.world.entity.player.Player player) {
		Float share = player.getAttached(SQUEEZE);
		return share == null ? 0.0F : share;
	}

	/** Every tick for a bonded host. */
	static void tick(ServerPlayer player) {
		UUID id = player.getUUID();
		Vec3 pos = player.position();
		Vec3 last = LAST.put(id, pos);
		float share = share(player);
		if (!WebSymbiote.bulked(player) || !player.isAlive() || player.isSpectator() || player.isPassenger()) {
			if (share > 0.0F) {
				clear(player);
			}
			return;
		}
		ServerLevel level = player.level();
		float current = 1.0F - CUT * share;
		EntityDimensions dims = player.getDimensions(player.getPose());
		Vec3 heading = heading(player, last, pos);
		boolean target = false;
		if (heading != null) {
			// it can't go on at its full size but could squeezed: a gap
			Vec3 ahead = pos.add(heading.scale(PROBE));
			AABB full = dims.scale(1.0F / current).makeBoundingBox(ahead);
			AABB narrow = dims.scale((1.0F - CUT) / current).makeBoundingBox(ahead);
			target = !level.noCollision(player, full) && level.noCollision(player, narrow);
		}
		if (!target && share > 0.0F) {
			// held while it's in the gap: the full size has no room here
			target = !level.noCollision(player, dims.scale(1.0F / current).makeBoundingBox(pos));
		}
		float next = target ? Math.min(1.0F, share + RAMP) : Math.max(0.0F, share - RELEASE);
		if (next < share) {
			// reforming: only as far as there's room for
			float bigger = (1.0F - CUT * next) / current;
			if (!level.noCollision(player, dims.scale(bigger).makeBoundingBox(pos))) {
				next = share;
			}
		}
		if (next != share) {
			set(player, next);
		}
	}

	/** Where it's pushing: the way it's moving, or, driven or holding forward, where it looks. */
	private static @Nullable Vec3 heading(ServerPlayer player, @Nullable Vec3 last, Vec3 pos) {
		if (last != null) {
			Vec3 moved = pos.subtract(last).multiply(1.0, 0.0, 1.0);
			if (moved.lengthSqr() > MIN_SPEED * MIN_SPEED) {
				return moved.normalize();
			}
		}
		if (player.getLastClientInput().forward() || WebSymbiote.driving(player)) {
			Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
			if (look.lengthSqr() > 1.0E-4) {
				return look.normalize();
			}
		}
		return null;
	}

	private static void set(ServerPlayer player, float share) {
		if (share <= 0.001F) {
			clear(player);
			return;
		}
		AttributeInstance scale = player.getAttribute(Attributes.SCALE);
		if (scale != null) {
			scale.addOrUpdateTransientModifier(new AttributeModifier(SQUEEZE_ID, -CUT * share, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		player.setAttached(SQUEEZE, share);
	}

	/** Whole again (also when the body comes down). */
	static void clear(ServerPlayer player) {
		AttributeInstance scale = player.getAttribute(Attributes.SCALE);
		if (scale != null && scale.hasModifier(SQUEEZE_ID)) {
			scale.removeModifier(SQUEEZE_ID);
		}
		if (player.getAttached(SQUEEZE) != null) {
			player.removeAttached(SQUEEZE);
		}
	}
}
