package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Limbo. Andy: "the ghost should also be how the player character appears while in limbo, so you can kind of haunt your
 * friends." A player lying left for dead ({@link Mortality#outOfBody}: the black has lifted, they haven't chosen to come
 * to) slips out of their body as a {@link LimboGhost}: their view leaves the body and drifts where they look and steer
 * (the client moves it, {@code client/wounds/LimboClient}, and says where it is: {@link Drift}), never further than
 * {@link #LEASH} from the body, which stays where it fell to be carried, brought round or come back to (sneak, as
 * before). Everyone else sees the ghost. It can't touch, be touched or be seen by mobs. Where each ghost is can be asked
 * from any thread ({@link #ghostAt}): Kingdoms' voice chat plugin speaks and hears from there.
 */
public final class Limbo {
	/** How far from its body a ghost can drift (blocks), and how far over the body it rises. */
	public static final double LEASH = 48.0, RISE = 1.0;
	/** Its light, for the motes it leaves coming and going. */
	static final int COLOR = 0x8ACEFF;

	public static EntityType<LimboGhost> GHOST;

	/** Set on a player who is out of their body, and told to them alone: their client takes the view out with it. */
	public static final AttachmentType<Boolean> OUT = AttachmentRegistry.create(JaymosPowers.id("limbo_out"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL.cast(), AttachmentSyncPredicate.targetOnly()));

	/** Client → server, every tick out of the body: where the ghost has drifted to and how it looks. */
	public record Drift(double x, double y, double z, float yRot, float xRot) implements CustomPacketPayload {
		public static final Type<Drift> TYPE = new Type<>(JaymosPowers.id("limbo_drift"));
		public static final StreamCodec<ByteBuf, Drift> CODEC = StreamCodec.composite(ByteBufCodecs.DOUBLE, Drift::x,
			ByteBufCodecs.DOUBLE, Drift::y, ByteBufCodecs.DOUBLE, Drift::z, ByteBufCodecs.FLOAT, Drift::yRot,
			ByteBufCodecs.FLOAT, Drift::xRot, Drift::new);

		@Override
		public Type<Drift> type() {
			return TYPE;
		}
	}

	/** Each player's ghost, while they're out. */
	private static final Map<UUID, LimboGhost> GHOSTS = new HashMap<>();
	/** Where each ghost is, for other threads (voice chat's). */
	private static final Map<UUID, Vec3> PLACES = new ConcurrentHashMap<>();

	private Limbo() {
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("limbo_ghost"));
		GHOST = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.<LimboGhost>of(LimboGhost::new, MobCategory.MISC)
				.sized(0.6F, 1.8F)
				.eyeHeight(1.62F)
				.clientTrackingRange(8)
				.noLootTable()
				.noSummon()
				.build(key));
		FabricDefaultAttributeRegistry.register(GHOST, LimboGhost.createAttributes());
		PayloadTypeRegistry.serverboundPlay().register(Drift.TYPE, Drift.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Drift.TYPE, (payload, context) -> drift(context.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(Limbo::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			GHOSTS.clear();
			PLACES.clear();
		});
	}

	/** Where {@code player}'s ghost is, if they're out of their body. Any thread. */
	public static @Nullable Vec3 ghostAt(UUID player) {
		return PLACES.get(player);
	}

	/** Whether {@code player} is out of their body as a ghost. Any thread. */
	public static boolean out(UUID player) {
		return PLACES.containsKey(player);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			LimboGhost ghost = GHOSTS.get(id);
			boolean out = Mortality.outOfBody(player);
			if (ghost != null && (!out || ghost.isRemoved() || ghost.level() != player.level())) {
				back(player, ghost);
				ghost = null;
			}
			if (ghost == null) {
				if (out) {
					rise(player);
				} else if (player.hasAttached(OUT)) {
					player.removeAttached(OUT);
				}
				continue;
			}
			// (a carried body takes its ghost along at the leash's end)
			Vec3 place = leashed(player, ghost.position());
			if (!place.equals(ghost.position())) {
				ghost.setPos(place);
			}
			PLACES.put(id, place);
		}
		// the ghosts of those who've left
		for (Iterator<Map.Entry<UUID, LimboGhost>> it = GHOSTS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, LimboGhost> entry = it.next();
			if (server.getPlayerList().getPlayer(entry.getKey()) == null) {
				entry.getValue().discard();
				PLACES.remove(entry.getKey());
				it.remove();
			}
		}
	}

	private static void rise(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		LimboGhost ghost = GHOST.create(level, EntitySpawnReason.EVENT);
		if (ghost == null) {
			return;
		}
		ghost.snapTo(player.getX(), player.getY() + RISE, player.getZ(), player.getYRot(), 0.0F);
		ghost.owner(player);
		level.addFreshEntity(ghost);
		GHOSTS.put(player.getUUID(), ghost);
		PLACES.put(player.getUUID(), ghost.position());
		player.setAttached(OUT, true);
		motes(level, ghost);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.limbo.out").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	private static void back(ServerPlayer player, LimboGhost ghost) {
		if (!ghost.isRemoved() && ghost.level() instanceof ServerLevel level) {
			motes(level, ghost);
		}
		ghost.discard();
		GHOSTS.remove(player.getUUID());
		PLACES.remove(player.getUUID());
		player.removeAttached(OUT);
	}

	private static void motes(ServerLevel level, LimboGhost ghost) {
		level.sendParticles(MagicFx.mote(COLOR, 1.0F), ghost.getX(), ghost.getY() + 1.0, ghost.getZ(), 14, 0.25, 0.6, 0.25, 0.01);
	}

	/** {@code player}'s client says where their ghost has drifted to: taken, inside the leash and loaded ground. */
	private static void drift(ServerPlayer player, Drift to) {
		LimboGhost ghost = GHOSTS.get(player.getUUID());
		if (ghost == null || ghost.isRemoved() || !Double.isFinite(to.x) || !Double.isFinite(to.y) || !Double.isFinite(to.z)
			|| !Float.isFinite(to.yRot) || !Float.isFinite(to.xRot)) {
			return;
		}
		Vec3 place = leashed(player, new Vec3(to.x, to.y, to.z));
		if (!ghost.level().isLoaded(BlockPos.containing(place))) {
			return;
		}
		float yRot = Mth.wrapDegrees(to.yRot), xRot = Mth.clamp(to.xRot, -90.0F, 90.0F);
		ghost.setPos(place);
		ghost.setYRot(yRot);
		ghost.setXRot(xRot);
		ghost.setYHeadRot(yRot);
		ghost.setYBodyRot(yRot);
	}

	/** {@code place}, pulled back to the end of the leash from {@code body} if it's past it. Both sides use this. */
	public static Vec3 leashed(net.minecraft.world.entity.Entity body, Vec3 place) {
		Vec3 from = body.position().add(0.0, RISE, 0.0), off = place.subtract(from);
		return off.lengthSqr() <= LEASH * LEASH ? place : from.add(off.normalize().scale(LEASH));
	}
}
