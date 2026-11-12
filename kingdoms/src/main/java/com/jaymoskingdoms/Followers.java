package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One orders screen for the folk who take orders from you (Andy's pick from Star Wars: Galaxy in a Cube, ideas only: its
 * soldiers all open one small screen of six orders). Ours is for MCA villagers you are involved with ({@link Strangers}:
 * family, hired help, a friend of {@link Strangers#TRUSTED_HEARTS} hearts): look at one and press the Orders key for
 * theirs, or look at nobody for everyone of yours within {@link #ALL_RANGE} blocks. Strangers still refuse, a Truth
 * villager still won't follow where their commandments forbid, and a villager whose tongue you don't share only
 * shrugs. MCA's own Follow and Stay buttons still work; this is the same and more in one place.
 * <ul>
 * <li>{@link #FOLLOW}: MCA's follow.
 * <li>{@link #HOLD}: keep this spot. They meet any monster that comes within {@link #HOLD_GUARD} blocks, then walk back.
 * <li>{@link #STAND}: stand still (MCA's stay) and start nothing.
 * <li>{@link #PATROL}: walk a round of {@link #PATROL_RADIUS} blocks about this spot, meeting monsters on the way.
 * <li>{@link #CLEAR}: hunt every monster within {@link #CLEAR_RADIUS} blocks of this spot; when none has been found for
 * {@link #CLEAR_DONE} ticks they say so and hold the spot.
 * <li>{@link #FIGHT}: follow, and go for whatever you strike or whatever strikes you.
 * <li>{@link #FREE}: as you were (MCA's "go about your day").
 * </ul>
 * Only those who can fight do: MCA gives a guard, an archer or a hired blade the wits for it; a child never is sent at
 * anything. The order is saved on the villager; needs MCA.
 */
public final class Followers {
	public static final int FOLLOW = 0, HOLD = 1, STAND = 2, PATROL = 3, CLEAR = 4, FIGHT = 5, FREE = 6;
	/** With nobody under the crosshair, everyone of yours this near takes the order; one looked at may be this far. */
	public static final double ALL_RANGE = 24.0, ONE_RANGE = 12.0;
	static final double HOLD_GUARD = 8.0, HOLD_LEASH = 2.5, PATROL_RADIUS = 10.0, PATROL_GUARD = 10.0, CLEAR_RADIUS = 24.0, FIGHT_RANGE = 24.0;
	/** A patrol has this many points on its round and moves on this often (ticks); a hunt with nothing found ends after this long. */
	static final int PATROL_POINTS = 6, PATROL_EVERY = 120, CLEAR_DONE = 200;
	/** Orders are looked at this often (ticks); a blow of yours counts for this long. */
	static final int EVERY = 10, FIGHT_MEMORY = 100;
	static final float WALK = 0.6F;

	/** A standing order on a villager: what, whose, and the spot it was given at. */
	public record Order(int kind, UUID by, long at) {
		static final Codec<Order> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("kind").forGetter(Order::kind),
			UUIDUtil.CODEC.fieldOf("by").forGetter(Order::by),
			Codec.LONG.fieldOf("at").forGetter(Order::at)).apply(i, Order::new));
	}

	/** On an ordered villager: saved. */
	public static final AttachmentType<Order> ORDER = AttachmentRegistry.create(JaymosKingdoms.id("follower_order"),
		builder -> builder.persistent(Order.CODEC));

	/** Client → server: {@code kind} for the villager {@code target} (an entity id), or for all of yours near when below zero. */
	public record Give(int target, int kind) implements CustomPacketPayload {
		public static final Type<Give> TYPE = new Type<>(JaymosKingdoms.id("follower_order"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Give> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Give::target, ByteBufCodecs.VAR_INT, Give::kind, Give::new);

		@Override
		public Type<Give> type() {
			return TYPE;
		}
	}

	/** Every loaded villager under an order, and what its patrol or hunt has come to (server memory). */
	private static final Map<UUID, Villager> ORDERED = new HashMap<>();
	private static final Map<UUID, int[]> PROGRESS = new HashMap<>();

	private Followers() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("follower attachment: {}", ORDER);
		PayloadTypeRegistry.serverboundPlay().register(Give.TYPE, Give.CODEC);
		if (!McaCompat.LOADED) {
			return;
		}
		ServerPlayNetworking.registerGlobalReceiver(Give.TYPE, (payload, context) -> give(context.player(), payload.target(), payload.kind()));
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Villager v && v.hasAttached(ORDER)) {
				ORDERED.put(v.getUUID(), v);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Villager) {
				ORDERED.remove(entity.getUUID());
				PROGRESS.remove(entity.getUUID());
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ORDERED.clear();
			PROGRESS.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(Followers::tick);
	}

	/** Whether {@code v} takes orders from {@code player} at all (MCA's ties, or enough hearts). */
	public static boolean takesOrders(Entity v, ServerPlayer player) {
		return McaCompat.LOADED && v instanceof Villager villager && villager.isAlive() && (McaBridge.involved(villager, player) || Loans.borrowedBy(villager, player) || RivalParties.hiredBy(villager, player));
	}

	private static void give(ServerPlayer player, int target, int kind) {
		if (kind < FOLLOW || kind > FREE || !player.isAlive() || player.isSpectator()) {
			return;
		}
		ServerLevel level = player.level();
		List<Villager> asked = new ArrayList<>();
		if (target >= 0) {
			if (level.getEntity(target) instanceof Villager v && v.distanceToSqr(player) <= ONE_RANGE * ONE_RANGE) {
				if (!takesOrders(v, player)) {
					player.sendOverlayMessage(Component.translatable("mca.jaymoskingdoms.stranger", Strangers.TRUSTED_HEARTS).withStyle(ChatFormatting.GRAY));
					return;
				}
				asked.add(v);
			}
		} else {
			asked.addAll(level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(ALL_RANGE), v -> takesOrders(v, player)));
		}
		int taken = 0;
		for (Villager v : asked) {
			if ((kind == FOLLOW || kind == FIGHT) && Commandments.refusesCommand(v, player, "follow")) {
				continue;
			}
			if (!Languages.understands(player, v)) {
				Languages.shrug(player, v);
				continue;
			}
			// turned on you, or far gone with the Murmur (docs/Pawn-Plan.md H1, H2)
			if (Possession.held(v) || Murmur.ignores(v, player)) {
				continue;
			}
			take(v, player, kind);
			taken++;
		}
		if (taken == 0 && asked.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.followers.none").withStyle(ChatFormatting.GRAY));
		} else if (taken > 0) {
			player.sendOverlayMessage(Component.translatable(taken == 1 ? "message.jaymoskingdoms.followers.taken_one" : "message.jaymoskingdoms.followers.taken",
				asked.size() == 1 ? asked.getFirst().getDisplayName() : Component.literal(Integer.toString(taken)),
				Component.translatable("gui.jaymoskingdoms.followers.order." + kind)).withStyle(ChatFormatting.GOLD));
		}
	}

	/** {@code v} takes the order, at the spot they stand on. */
	static void take(Villager v, ServerPlayer player, int kind) {
		Attention.heed(v);
		PROGRESS.remove(v.getUUID());
		if (kind == FOLLOW || kind == FREE) {
			v.removeAttached(ORDER);
			ORDERED.remove(v.getUUID());
			McaBridge.moveState(v, kind == FOLLOW ? 2 : 0, player);
			return;
		}
		v.setAttached(ORDER, new Order(kind, player.getUUID(), v.blockPosition().asLong()));
		ORDERED.put(v.getUUID(), v);
		McaBridge.moveState(v, kind == FIGHT ? 2 : kind == STAND ? 1 : 0, player);
	}

	private static void tick(MinecraftServer server) {
		if (ORDERED.isEmpty() || server.getTickCount() % EVERY != 0) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Villager>> it = ORDERED.entrySet().iterator(); it.hasNext();) {
			Villager v = it.next().getValue();
			Order order = v.getAttached(ORDER);
			if (order == null || !v.isAlive() || v.isRemoved() || !(v.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			if (v.isSleeping()) {
				continue;
			}
			step(level, v, order);
		}
	}

	private static void step(ServerLevel level, Villager v, Order order) {
		Vec3 spot = Vec3.atBottomCenterOf(BlockPos.of(order.at()));
		boolean fighting = foe(v) != null;
		int[] progress = PROGRESS.computeIfAbsent(v.getUUID(), id -> new int[2]);
		switch (order.kind()) {
			case HOLD -> {
				if (!fighting && !sendAt(level, v, spot, HOLD_GUARD) && v.position().distanceToSqr(spot) > HOLD_LEASH * HOLD_LEASH) {
					walk(v, spot);
				}
			}
			case PATROL -> {
				if (fighting || sendAt(level, v, v.position(), PATROL_GUARD)) {
					return;
				}
				progress[1] += EVERY;
				double angle = progress[0] * (Math.PI * 2.0 / PATROL_POINTS);
				Vec3 point = spot.add(Math.cos(angle) * PATROL_RADIUS, 0.0, Math.sin(angle) * PATROL_RADIUS);
				if (progress[1] >= PATROL_EVERY || v.position().distanceToSqr(point) < 9.0) {
					progress[0] = (progress[0] + 1) % PATROL_POINTS;
					progress[1] = 0;
				}
				// on the open ground where there is some near their own height; under a roof or in a cave, at the spot's height
				BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(point));
				walk(v, Math.abs(top.getY() - spot.y) <= 5.0 ? Vec3.atBottomCenterOf(top) : point);
			}
			case CLEAR -> {
				if (fighting || sendAt(level, v, spot, CLEAR_RADIUS)) {
					progress[1] = 0;
					return;
				}
				progress[1] += EVERY;
				if (progress[1] >= CLEAR_DONE) {
					if (level.getPlayerByUUID(order.by()) instanceof ServerPlayer by && by.distanceToSqr(v) < 64.0 * 64.0) {
						by.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.followers.cleared", v.getDisplayName()).withStyle(ChatFormatting.GOLD));
					}
					v.setAttached(ORDER, new Order(HOLD, order.by(), order.at()));
					PROGRESS.remove(v.getUUID());
				}
			}
			case FIGHT -> {
				if (fighting || !(level.getPlayerByUUID(order.by()) instanceof ServerPlayer by) || v.isBaby()) {
					return;
				}
				LivingEntity struck = by.getLastHurtMob();
				LivingEntity foe = struck != null && by.tickCount - by.getLastHurtMobTimestamp() <= FIGHT_MEMORY ? struck : by.getLastHurtByMob();
				if (foe != null && foe.isAlive() && foe != v && foe.distanceToSqr(v) <= FIGHT_RANGE * FIGHT_RANGE && !takesOrders(foe, by)) {
					v.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, foe);
				}
			}
			default -> {
			}
		}
	}

	/** Whoever {@code v}'s brain is set on, or null (a brain with no such memory has nobody). */
	private static @Nullable LivingEntity foe(Villager v) {
		if (!v.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
			return null;
		}
		LivingEntity foe = v.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
		return foe != null && foe.isAlive() ? foe : null;
	}

	/** {@code v} is set on the nearest monster within {@code radius} of {@code around} that they can see; false if there is none. */
	private static boolean sendAt(ServerLevel level, Villager v, Vec3 around, double radius) {
		if (v.isBaby()) {
			return false;
		}
		LivingEntity nearest = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, net.minecraft.world.phys.AABB.ofSize(around, radius * 2.0, radius, radius * 2.0),
			e -> e instanceof Enemy && e.isAlive() && !(e instanceof Villager))) {
			double d = e.distanceToSqr(v);
			if (d < best && (d < 36.0 || v.hasLineOfSight(e))) {
				best = d;
				nearest = e;
			}
		}
		if (nearest == null) {
			return false;
		}
		v.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, nearest);
		return true;
	}

	private static void walk(Villager v, Vec3 to) {
		v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(to, WALK, 1));
	}
}
