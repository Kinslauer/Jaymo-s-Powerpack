package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.xenoform.XenoMinds;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * A Mirage wearing a face ({@code MirageFace}) is drawn as what it copied. A player's face is their skin and name plate on the
 * Mirage's own body; a creature's is drawn by that creature's own renderer, fed by a stand-in the client keeps for each wearer (never
 * added to the world) that is moved to the wearer's place every frame. From the dispatcher's extract, last
 * ({@code MirageFaceDispatcherMixin}); {@link XenoformClient#extract} leaves the alien body off a wearer so a player's skin shows.
 */
public final class MirageFaceClient {
	private record Proxy(EntityType<?> type, LivingEntity entity) {
	}

	private static final Map<Integer, Proxy> PROXIES = new HashMap<>();
	private static boolean broken;

	private MirageFaceClient() {
	}

	/** The face {@code entity} wears, or null. */
	public static @Nullable String face(Entity entity) {
		return entity instanceof Player player ? player.getAttached(XenoMinds.FACE) : null;
	}

	/** {@code state}, or the state of the face a wearing Mirage shows in its place. */
	public static EntityRenderState swap(Entity entity, EntityRenderState state, float partialTicks, EntityRenderDispatcher dispatcher) {
		String face = face(entity);
		if (broken || face == null || face.isEmpty() || !(entity instanceof Player player)) {
			return state;
		}
		try {
			if (face.startsWith("p:")) {
				skin(face, state);
				return state;
			}
			if (face.startsWith("e:")) {
				return creature(player, face.substring(2), state, partialTicks, dispatcher);
			}
		} catch (RuntimeException e) {
			JaymosPowers.LOGGER.debug("A Mirage's face can't be drawn", e);
			broken = true;
		}
		return state;
	}

	/** A player's skin and name over the wearer's body. */
	private static void skin(String face, EntityRenderState state) {
		if (!(state instanceof AvatarRenderState avatar)) {
			return;
		}
		String[] parts = face.split(":", 3);
		if (parts.length < 3) {
			return;
		}
		UUID id;
		try {
			id = UUID.fromString(parts[1]);
		} catch (IllegalArgumentException e) {
			return;
		}
		var connection = Minecraft.getInstance().getConnection();
		PlayerInfo info = connection == null ? null : connection.getPlayerInfo(id);
		if (info != null) {
			avatar.skin = info.getSkin();
		}
		if (avatar.nameTag != null) {
			avatar.nameTag = Component.literal(parts[2]);
			avatar.scoreText = null;
		}
	}

	/** The creature's own state, in the wearer's place. */
	private static EntityRenderState creature(Player player, String typeId, EntityRenderState state, float partialTicks, EntityRenderDispatcher dispatcher) {
		var level = Minecraft.getInstance().level;
		Identifier id = Identifier.tryParse(typeId);
		if (level == null || id == null) {
			return state;
		}
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
		if (type == null || type == net.minecraft.world.entity.EntityTypes.PLAYER) {
			return state;
		}
		Proxy proxy = PROXIES.get(player.getId());
		if (proxy == null || proxy.type != type || proxy.entity.level() != level) {
			Entity made = type.create(level, EntitySpawnReason.LOAD);
			if (!(made instanceof LivingEntity living)) {
				return state;
			}
			proxy = new Proxy(type, living);
			PROXIES.put(player.getId(), proxy);
		}
		LivingEntity stand = proxy.entity;
		stand.setPos(player.getX(), player.getY(), player.getZ());
		stand.xo = player.xo;
		stand.yo = player.yo;
		stand.zo = player.zo;
		stand.setYRot(player.getYRot());
		stand.setXRot(player.getXRot());
		stand.yRotO = player.yRotO;
		stand.xRotO = player.xRotO;
		stand.yBodyRot = player.yBodyRot;
		stand.yBodyRotO = player.yBodyRotO;
		stand.yHeadRot = player.yHeadRot;
		stand.yHeadRotO = player.yHeadRotO;
		stand.tickCount = player.tickCount;
		stand.walkAnimation.setSpeed(player.walkAnimation.speed());
		stand.setInvisible(player.isInvisible());
		stand.setOnGround(player.onGround());
		EntityRenderState made = dispatcher.extractEntity(stand, partialTicks);
		made.x = state.x;
		made.y = state.y;
		made.z = state.z;
		made.lightCoords = state.lightCoords;
		made.distanceToCameraSq = state.distanceToCameraSq;
		made.isInvisible = state.isInvisible;
		made.outlineColor = state.outlineColor;
		made.nameTag = null;
		made.scoreText = null;
		return made;
	}

	/** A client left the world: the stand-ins go. */
	public static void clear() {
		PROXIES.clear();
	}
}
