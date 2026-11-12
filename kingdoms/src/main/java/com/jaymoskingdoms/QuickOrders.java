package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Four quick orders on keys, no menu (docs/Pawn-Plan.md row E1; after Dragon's Dogma's Go, Come, Wait and Help, a
 * commercial game: ideas only). They go to every villager of yours within {@link Followers#ALL_RANGE} who'd take an order
 * from you ({@link Followers#takesOrders}, in a tongue they understand), and mean one thing in a fight and another out
 * of one:
 * <ul>
 * <li>{@link #GO}: in a fight, spread out and each take a foe by their own leanings; out of one, the first of them
 * scouts a little way ahead where you look ({@link #SCOUT_AHEAD}) and comes back. Anyone standing or holding a spot
 * follows again.</li>
 * <li>{@link #COME}: follow, and for {@link #CLOSE_TICKS} fight only what's near you ({@link Comrades#CLOSE}).</li>
 * <li>{@link #WAIT}: stop and stay here ({@link Followers#STAND}, until another order), and stop attacking (a blow taken
 * is still answered).</li>
 * <li>{@link #HELP}: get the fallen up, you first ({@link FieldMedics#send}); with nobody down, the nearest comes and
 * sees to your wounds ({@link #PATCH_GAP} between two from one companion).</li>
 * </ul>
 * One of them answers (a {@code order.*} line). The orders screen ({@link Followers}) keeps the long orders. The keys
 * are on the client ({@code QuickOrderKeys}); band leaders give the same orders to their bands ({@link BandLeaders}).
 */
public final class QuickOrders {
	public static final int GO = 0, COME = 1, WAIT = 2, HELP = 3;
	static final String[] NAMES = {"go", "come", "wait", "help"};
	/** How long a told-to-come companion keeps close, and a told-to-wait one holds back in a fight (ticks). */
	static final int CLOSE_TICKS = 600, WAIT_TICKS = 200;
	/** A scout goes this far ahead (blocks), and is back after this long at most (ticks). */
	static final double SCOUT_AHEAD = 12.0;
	static final int SCOUT_TICKS = 200;
	/** A fight is on when a mob this near you (blocks) is after you. */
	static final double FIGHT_NEAR = 16.0;
	/** Seeing to your wounds: how near, how long (ticks); and how long before the same one does it again (ticks). */
	static final double PATCH_REACH = 2.0;
	static final int PATCH_TICKS = 40, PATCH_GAP = 1800;
	/** Powers' bandage tag is taken off again after this long if nothing of yours was bleeding to use it (ticks). */
	static final int UNTAG_AFTER = 60;
	static final float WALK = 0.7F;

	/** What a companion is doing on a quick order just now: {@link #COME}, {@link #WAIT}, a scout ({@link #GO}), a patch ({@link #HELP}). */
	public record Stance(int kind, UUID by, long until, @Nullable Vec3 point) {
	}

	/** Client → server: a quick order, {@link #GO} to {@link #HELP}. */
	public record Give(int kind) implements CustomPacketPayload {
		public static final Type<Give> TYPE = new Type<>(JaymosKingdoms.id("quick_order"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Give> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Give::kind, Give::new);

		@Override
		public Type<Give> type() {
			return TYPE;
		}
	}

	private static final Map<UUID, Stance> STANCES = new HashMap<>();
	private static final Map<UUID, Villager> HOLDERS = new HashMap<>();
	private static final Map<UUID, Long> PATCHED = new HashMap<>();
	private static final Map<ServerPlayer, Long> UNTAG = new HashMap<>();
	private static final Map<UUID, Integer> PATCHING = new HashMap<>();

	private QuickOrders() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Give.TYPE, Give.CODEC);
		if (!McaCompat.LOADED) {
			return;
		}
		ServerPlayNetworking.registerGlobalReceiver(Give.TYPE, (payload, context) -> give(context.player(), payload.kind()));
		ServerTickEvents.END_SERVER_TICK.register(QuickOrders::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			STANCES.clear();
			HOLDERS.clear();
			PATCHED.clear();
			UNTAG.clear();
			PATCHING.clear();
		});
	}

	/** {@code m}'s quick-order stance, if it has one still running. */
	static @Nullable Stance stance(Mob m) {
		Stance s = STANCES.get(m.getUUID());
		return s != null && m.level().getGameTime() < s.until() ? s : null;
	}

	private static void give(ServerPlayer player, int kind) {
		if (kind < GO || kind > HELP || !player.isAlive() || player.isSpectator()) {
			return;
		}
		ServerLevel level = player.level();
		List<Villager> yours = new ArrayList<>();
		for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(Followers.ALL_RANGE),
			v -> !v.isBaby() && !v.isSleeping() && !Downed.isDowned(v) && Followers.takesOrders(v, player))) {
			if (!Languages.understands(player, v)) {
				Languages.shrug(player, v);
				continue;
			}
			yours.add(v);
		}
		if (yours.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.followers.none").withStyle(ChatFormatting.GRAY));
			return;
		}
		yours.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
		boolean fight = yours.stream().anyMatch(v -> Comrades.target(v) != null)
			|| !level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(FIGHT_NEAR), m -> Comrades.target(m) == player).isEmpty();
		long now = level.getGameTime();
		Villager first = yours.getFirst();
		switch (kind) {
			case GO -> {
				for (Villager v : yours) {
					STANCES.remove(v.getUUID());
					Followers.Order order = v.getAttached(Followers.ORDER);
					if (order != null && order.kind() != Followers.FIGHT) {
						Followers.take(v, player, Followers.FOLLOW);
					}
					if (fight) {
						Comrades.think(level, v, true);
					}
				}
				if (!fight) {
					scout(level, first, player, now);
				}
			}
			case COME -> {
				for (Villager v : yours) {
					Followers.take(v, player, Followers.FOLLOW);
					STANCES.put(v.getUUID(), new Stance(COME, player.getUUID(), now + CLOSE_TICKS, null));
				}
			}
			case WAIT -> {
				for (Villager v : yours) {
					Followers.take(v, player, Followers.STAND);
					Comrades.stand(v);
					STANCES.put(v.getUUID(), new Stance(WAIT, player.getUUID(), now + WAIT_TICKS, null));
				}
			}
			default -> help(level, player, yours, now);
		}
		Callouts.say(first, "order." + NAMES[kind], player, true);
	}

	/** {@code v} goes a little way ahead of {@code player}, where they look, and comes back. */
	private static void scout(ServerLevel level, Villager v, ServerPlayer player, long now) {
		Vec3 look = player.getViewVector(1.0F);
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		if (flat.lengthSqr() < 1.0E-4) {
			return;
		}
		Vec3 ahead = player.position().add(flat.normalize().scale(SCOUT_AHEAD));
		BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(ahead));
		Vec3 point = Math.abs(top.getY() - player.getY()) <= 6.0 ? Vec3.atBottomCenterOf(top) : ahead;
		McaBridge.moveState(v, 0, player);
		STANCES.put(v.getUUID(), new Stance(GO, player.getUUID(), now + SCOUT_TICKS, point));
		HOLDERS.put(v.getUUID(), v);
		v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(point, WALK, 1));
	}

	private static void help(ServerLevel level, ServerPlayer player, List<Villager> yours, long now) {
		List<Villager> free = new ArrayList<>(yours);
		List<LivingEntity> down = new ArrayList<>();
		if (Downed.isDowned(player)) {
			down.add(player);
		}
		String side = Comrades.side(player);
		for (LivingEntity body : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(Followers.ALL_RANGE),
			b -> b != player && b.isAlive() && Downed.isDowned(b) && Comrades.side(b).equals(side))) {
			down.add(body);
		}
		boolean sent = false;
		for (LivingEntity body : down) {
			if (FieldMedics.claimed(body) || Downed.tended(body)) {
				continue;
			}
			Villager nearest = null;
			for (Villager v : free) {
				if (nearest == null || v.distanceToSqr(body) < nearest.distanceToSqr(body)) {
					nearest = v;
				}
			}
			if (nearest != null && FieldMedics.send(nearest, body)) {
				free.remove(nearest);
				sent = true;
			}
		}
		if (sent || Downed.isDowned(player)) {
			return;
		}
		for (Villager v : free) {
			Long last = PATCHED.get(v.getUUID());
			if (last == null || now - last >= PATCH_GAP) {
				STANCES.put(v.getUUID(), new Stance(HELP, player.getUUID(), now + SCOUT_TICKS, null));
				HOLDERS.put(v.getUUID(), v);
				PATCHING.put(v.getUUID(), 0);
				return;
			}
		}
	}

	private static void tick(MinecraftServer server) {
		long time = server.overworld().getGameTime();
		if (!UNTAG.isEmpty()) {
			UNTAG.entrySet().removeIf(e -> {
				if (time >= e.getValue()) {
					e.getKey().removeTag(Downed.BANDAGED_TAG);
					return true;
				}
				return false;
			});
		}
		if (server.getTickCount() % 5 != 0) {
			return;
		}
		STANCES.entrySet().removeIf(e -> time >= e.getValue().until() && !HOLDERS.containsKey(e.getKey()));
		for (var it = HOLDERS.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			Villager v = e.getValue();
			Stance s = STANCES.get(e.getKey());
			ServerPlayer player = s == null ? null : server.getPlayerList().getPlayer(s.by());
			if (s == null || player == null || !v.isAlive() || v.isRemoved() || Downed.isDowned(v) || v.level() != player.level()) {
				end(v, s, player);
				it.remove();
				continue;
			}
			boolean done = s.kind() == GO ? scouting(v, s, time) : patching(v, player, time);
			if (done) {
				end(v, s, player);
				it.remove();
			}
		}
	}

	/** A scout's walk: true once there (or out of time). */
	private static boolean scouting(Villager v, Stance s, long time) {
		return time >= s.until() || s.point() == null || v.position().distanceToSqr(s.point()) < 4.0 || Comrades.target(v) != null;
	}

	/** Seeing to the player's wounds: true once done (or out of time). */
	private static boolean patching(Villager v, ServerPlayer player, long time) {
		Stance s = STANCES.get(v.getUUID());
		if (s == null || time >= s.until() || Comrades.target(v) != null) {
			return true;
		}
		if (v.distanceToSqr(player) > PATCH_REACH * PATCH_REACH) {
			v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(player, WALK, 1));
			PATCHING.put(v.getUUID(), 0);
			return false;
		}
		v.getLookControl().setLookAt(player);
		int ticks = PATCHING.merge(v.getUUID(), 5, Integer::sum);
		if (ticks < PATCH_TICKS) {
			return false;
		}
		v.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		player.addTag(Downed.BANDAGED_TAG);
		UNTAG.put(player, time + UNTAG_AFTER);
		PATCHED.put(v.getUUID(), time);
		return true;
	}

	private static void end(Villager v, @Nullable Stance s, @Nullable ServerPlayer player) {
		STANCES.remove(v.getUUID());
		PATCHING.remove(v.getUUID());
		if (s != null && s.kind() == GO && player != null && v.isAlive()) {
			// back to following
			McaBridge.moveState(v, 2, player);
		}
	}
}
