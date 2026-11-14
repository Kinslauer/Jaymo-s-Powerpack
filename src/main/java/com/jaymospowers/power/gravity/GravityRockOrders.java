package com.jaymospowers.power.gravity;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.combat.Guard;
import com.jaymospowers.core.LockOn;
import com.jaymospowers.core.Powers;
import com.jaymospowers.ground.FirstTools;
import com.jaymospowers.power.arsenal.ArsenalStorm;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "anything the user can do with swords should be doable with the rocks as well. Except flying." The orders an
 * Arsenal user gives the Attendant Blades, for a Gravity user's harnessed rocks ({@link GravityRocks}; the ball and the
 * orbit are its formations, and it already answers each hit and clips what comes close). With the rocks out, holding G
 * while B is held (a tap still changes the formation) opens a wheel ({@link Pick}):
 * <ul>
 * <li><b>Harry</b>: the rocks are set on what their holder looks at, and keep going at it (the ball's lump or the
 * orbit's volley, again and again) until it falls, gets out of reach or they are called off (Harry again).</li>
 * <li><b>Steer</b>: for up to {@link #STEER_TICKS} the rocks leave their holder and fly where they look, battering what
 * they pass through. Steer again calls them back.</li>
 * <li><b>Stone storm</b>: for {@link #STORM_TICKS} rocks whip out over the shoulder one after another, loop through
 * what their holder aims at and come back (the Blade Storm's loops, {@link ArsenalStorm#place}). The holder is slowed.</li>
 * <li><b>Closing ring</b>: the rocks ring what their holder looks at, hold it where it stands for
 * {@link #RING_TICKS}, then all crash in.</li>
 * </ul>
 * And <b>ward</b>, with no order: a guard raised, and a rock meets each blow or shot from someone (no more often than
 * {@link #WARD_GAP}); that rock is spent. There is no riding: Gravity has the earthboard.
 *
 * <p>An order under way is one synced attachment ({@link #ORDER}); both sides place the rocks by the same formula
 * ({@link #place}), drawn by {@code GravityRockOrdersRenderer} (the ball or ring on the holder is hidden meanwhile).
 */
public final class GravityRockOrders {
	// tuning (ticks)
	/** G held this long (with B) opens the wheel; let go sooner and the formation changes. */
	public static final int HOLD_TICKS = 8;
	public static final int STEER_TICKS = 100;
	public static final double STEER_RANGE = 24.0;
	/** A pass through something while steered: this per rock, each thing no more often than {@link #STEER_HIT_GAP}. */
	static final float STEER_PER_ROCK = 0.1F;
	static final int STEER_HIT_GAP = 10;
	public static final int STORM_TICKS = 80;
	static final int STORM_COOLDOWN = 300;
	/** One rock every this many ticks, each loop {@link ArsenalStorm#LOOP_TICKS} long. */
	public static final float STORM_GAP = 3.0F;
	static final float STORM_PER_ROCK = 0.05F;
	static final int STORM_HIT_GAP = 8;
	static final int STORM_SLOWNESS = 1;
	public static final int RING_TICKS = 60;
	public static final int RING_OUT_TICKS = 8, RING_CLOSE_TICKS = 6;
	public static final double RING_RADIUS = 1.7;
	static final double RING_RANGE = 24.0;
	static final float RING_PER_ROCK = 0.3F;
	static final int RING_COOLDOWN = 400;
	static final double HARRY_RANGE = 10.0;
	static final int WARD_GAP = 30;
	/** How many rocks are drawn (and fly) in an order, whatever the pack holds. */
	public static final int DRAWN = 12;

	public static final int HARRY = 0, STEER = 1, STORM = 2, RING = 3, PICKS = 4;

	/** An order under way: which ({@link #STEER}, {@link #STORM}, {@link #RING}), from when to when, at what (entity id, -1 none), and a seed for the storm's loops. */
	public record Order(int kind, long start, long end, int target, int seed) {
		public static final StreamCodec<ByteBuf, Order> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Order::kind,
			ByteBufCodecs.VAR_LONG, Order::start,
			ByteBufCodecs.VAR_LONG, Order::end,
			ByteBufCodecs.VAR_INT, Order::target,
			ByteBufCodecs.VAR_INT, Order::seed,
			Order::new);

		/** The same storm an Arsenal user's would be, for its loops. */
		public ArsenalStorm.Storm storm() {
			return new ArsenalStorm.Storm(start, end, STORM_GAP, seed, List.of());
		}
	}

	public static final AttachmentType<Order> ORDER = AttachmentRegistry.create(JaymosPowers.id("gravity_rock_order"),
		builder -> builder.syncWith(Order.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** Client → server: a pick on the rocks' wheel. */
	public record Pick(int choice) implements CustomPacketPayload {
		public static final Type<Pick> TYPE = new Type<>(JaymosPowers.id("gravity_rock_order"));
		public static final StreamCodec<ByteBuf, Pick> CODEC = ByteBufCodecs.VAR_INT.map(Pick::new, Pick::choice);

		@Override
		public Type<Pick> type() {
			return TYPE;
		}
	}

	/** When G went down with B held and the rocks out ({@code -1}: held long enough, the wheel has it). */
	private static final Map<UUID, Long> PRESSED = new HashMap<>();
	/** What each holder's rocks are set on. */
	private static final Map<UUID, UUID> HARRIED = new HashMap<>();
	private static final Map<UUID, Long> STORM_READY = new HashMap<>();
	private static final Map<UUID, Long> RING_READY = new HashMap<>();
	private static final Map<UUID, Long> WARD_READY = new HashMap<>();
	/** When each thing (by entity id) was last hit by this holder's steered or storming rocks. */
	private static final Map<UUID, Map<Integer, Long>> HIT = new HashMap<>();

	private GravityRockOrders() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Pick.TYPE, Pick.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Pick.TYPE, (payload, ctx) -> {
			ServerPlayer player = ctx.player();
			if (Powers.has(player, GravityPower.INSTANCE) && player.isAlive() && !player.isSpectator()) {
				pick(player, payload.choice());
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer player && ward(player, source)));
	}

	/** Whether an order has the rocks away from their holder (their own follow-ups wait). */
	public static boolean busy(Player player) {
		return player.getAttached(ORDER) != null;
	}

	/**
	 * G went down or up while B is held. With no rocks out it gathers them at once, as ever; with rocks out the press
	 * waits: let go early and the formation changes, held and the client opens the wheel.
	 */
	static void key(ServerPlayer player, boolean down) {
		UUID id = player.getUUID();
		if (down) {
			if (player.getAttached(GravityRocks.ROCKS) == null) {
				GravityRocks.cycle(player);
			} else {
				PRESSED.put(id, player.level().getGameTime());
			}
			return;
		}
		Long pressed = PRESSED.remove(id);
		// a little under the client's count, so the key coming up as the wheel opens is never taken for a tap
		if (pressed != null && player.level().getGameTime() - pressed < HOLD_TICKS - 3) {
			end(player);
			GravityRocks.cycle(player);
		}
	}

	private static void pick(ServerPlayer player, int choice) {
		GravityRocks.Rocks rocks = player.getAttached(GravityRocks.ROCKS);
		if (rocks == null) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		UUID id = player.getUUID();
		Order order = player.getAttached(ORDER);
		switch (choice) {
			case HARRY -> {
				LivingEntity target = looked(player, HARRY_RANGE);
				if (target == null || target.getUUID().equals(HARRIED.get(id))) {
					boolean was = HARRIED.remove(id) != null;
					say(player, was ? "called_off" : "no_target", ChatFormatting.GRAY);
					return;
				}
				HARRIED.put(id, target.getUUID());
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.gravity_rocks.harry", target.getDisplayName()).withStyle(ChatFormatting.GOLD));
			}
			case STEER -> {
				if (order != null) {
					if (order.kind() == STEER) {
						end(player);
					}
					return;
				}
				begin(player, new Order(STEER, now, now + STEER_TICKS, -1, 0));
			}
			case STORM -> {
				if (order != null || waiting(player, STORM_READY, now)) {
					return;
				}
				STORM_READY.put(id, now + STORM_TICKS + STORM_COOLDOWN);
				begin(player, new Order(STORM, now, now + STORM_TICKS, -1, player.getRandom().nextInt()));
			}
			case RING -> {
				if (order != null || waiting(player, RING_READY, now)) {
					return;
				}
				LivingEntity target = looked(player, RING_RANGE);
				if (target == null) {
					say(player, "no_target", ChatFormatting.GRAY);
					return;
				}
				RING_READY.put(id, now + RING_COOLDOWN);
				begin(player, new Order(RING, now, now + RING_TICKS, target.getId(), 0));
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.gravity_rocks.ring", target.getDisplayName()).withStyle(ChatFormatting.GOLD));
			}
			default -> {
			}
		}
	}

	private static boolean waiting(ServerPlayer player, Map<UUID, Long> ready, long now) {
		long at = ready.getOrDefault(player.getUUID(), 0L);
		if (now >= at) {
			return false;
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.gravity_rocks.recharging", String.format("%.1f", (at - now) / 20.0))
			.withStyle(ChatFormatting.GRAY));
		return true;
	}

	private static void say(ServerPlayer player, String key, ChatFormatting style) {
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.gravity_rocks." + key).withStyle(style));
	}

	private static void begin(ServerPlayer player, Order order) {
		player.setAttached(ORDER, order);
		HIT.remove(player.getUUID());
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.8F, 0.6F);
	}

	/** The order is over: the rocks are back with their holder. */
	static void end(ServerPlayer player) {
		if (player.getAttached(ORDER) != null) {
			player.removeAttached(ORDER);
			HIT.remove(player.getUUID());
		}
	}

	/** Every tick for a holder with the rocks out (from {@link GravityRocks}): the order under way, and the harrying. */
	static void tick(ServerPlayer player, GravityRocks.Rocks rocks) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		UUID id = player.getUUID();
		Order order = player.getAttached(ORDER);
		if (order == null) {
			harry(player, rocks, now);
			return;
		}
		if (order.kind() == RING) {
			ring(player, rocks, order, now);
			return;
		}
		if (now >= order.end() + (order.kind() == STORM ? ArsenalStorm.LOOP_TICKS : 0)) {
			end(player);
			return;
		}
		Map<Integer, Long> last = HIT.computeIfAbsent(id, key -> new HashMap<>());
		int n = Math.min(rocks.count(), DRAWN);
		if (order.kind() == STEER) {
			float damage = STEER_PER_ROCK * rocks.count();
			Vec3 at = steerPoint(player, 1.0F);
			for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(at, 2.4, 2.4, 2.4), e -> fair(player, e))) {
				strike(player, level, rocks, living, damage, last, now, STEER_HIT_GAP);
			}
			return;
		}
		// the storm
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, STORM_SLOWNESS, true, false, false));
		ArsenalStorm.Storm storm = order.storm();
		float damage = STORM_PER_ROCK * rocks.count();
		int count = storm.count(now);
		for (int i = Math.max(0, count - 1 - (int) (ArsenalStorm.LOOP_TICKS / STORM_GAP) - 1); i < count; i++) {
			double along = storm.along(i, now);
			if (along < 0.0 || along > 1.0) {
				continue;
			}
			Vec3 at = ArsenalStorm.place(player, storm, i, along, 1.0F);
			Vec3 was = ArsenalStorm.place(player, storm, i, Math.max(0.0, storm.along(i, now - 1.0)), 1.0F);
			for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(was, at).inflate(1.2), e -> fair(player, e))) {
				strike(player, level, rocks, living, damage, last, now, STORM_HIT_GAP);
			}
			if (storm.along(i, now - 1.0) < 0.0) {
				level.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_HIT, SoundSource.PLAYERS, 0.5F, 0.6F + level.getRandom().nextFloat() * 0.4F);
			}
		}
	}

	private static void strike(ServerPlayer player, ServerLevel level, GravityRocks.Rocks rocks, LivingEntity living, float damage, Map<Integer, Long> last,
		long now, int gap) {
		if (now - last.getOrDefault(living.getId(), -1000L) < gap) {
			return;
		}
		last.put(living.getId(), now);
		GravityRocks.hit(player, level, living, damage, true);
		Vec3 at = living.getBoundingBox().getCenter();
		grit(level, rocks, at, 4);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_HIT, SoundSource.PLAYERS, 0.8F, 0.9F + level.getRandom().nextFloat() * 0.3F);
	}

	/** The rocks keep going at what they are set on: a new follow-up whenever the last is home. */
	private static void harry(ServerPlayer player, GravityRocks.Rocks rocks, long now) {
		UUID id = player.getUUID();
		UUID harried = HARRIED.get(id);
		if (harried == null) {
			return;
		}
		if (!(player.level().getEntity(harried) instanceof LivingEntity prey) || !fair(player, prey) || player.distanceTo(prey) > HARRY_RANGE * 2.0) {
			HARRIED.remove(id);
			return;
		}
		if (now < rocks.busyUntil() + 4 || player.distanceTo(prey) > HARRY_RANGE) {
			return;
		}
		int shots = rocks.form() == GravityRocks.BALL ? 1 : Math.min(rocks.count(), GravityRocks.VOLLEY);
		player.setAttached(GravityRocks.ROCKS, new GravityRocks.Rocks(rocks.form(), rocks.count(), rocks.item(), prey.getId(), now, shots));
	}

	private static void ring(ServerPlayer player, GravityRocks.Rocks rocks, Order order, long now) {
		ServerLevel level = player.level();
		LivingEntity target = level.getEntity(order.target()) instanceof LivingEntity living && fair(player, living)
			&& living.distanceToSqr(player) < RING_RANGE * RING_RANGE * 2.25 ? living : null;
		if (target == null) {
			end(player);
			return;
		}
		Vec3 center = target.getBoundingBox().getCenter();
		if (now < order.end()) {
			if (now - order.start() >= RING_OUT_TICKS) {
				Vec3 v = target.getDeltaMovement();
				target.setDeltaMovement(0.0, Math.min(0.0, v.y), 0.0);
				target.needsSync = true;
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 6, true, false, false));
				if (target instanceof Mob mob) {
					mob.getNavigation().stop();
				}
				if ((now - order.start()) % 10 == 0) {
					level.playSound(null, center.x, center.y, center.z, SoundEvents.STONE_STEP, SoundSource.PLAYERS, 0.8F, 0.6F);
				}
			}
			return;
		}
		GravityRocks.hit(player, level, target, RING_PER_ROCK * rocks.count(), false);
		grit(level, rocks, center, 20 + rocks.count());
		level.playSound(null, center.x, center.y, center.z, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1.0F, 0.8F);
		end(player);
	}

	/** Ward: a guard raised, rocks out, and what someone sent at the holder meets a rock instead. That rock is spent. */
	private static boolean ward(ServerPlayer player, DamageSource source) {
		GravityRocks.Rocks rocks = player.getAttached(GravityRocks.ROCKS);
		if (rocks == null || source.getEntity() == null || source.getEntity() == player || !Guard.raised(player)) {
			return false;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now < WARD_READY.getOrDefault(player.getUUID(), 0L)) {
			return false;
		}
		WARD_READY.put(player.getUUID(), now + WARD_GAP);
		if (!player.getAbilities().instabuild) {
			var inventory = player.getInventory();
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				ItemStack stack = inventory.getItem(i);
				if (FirstTools.rock(stack)) {
					stack.shrink(1);
					break;
				}
			}
		}
		Entity from = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
		Vec3 toward = from.position().subtract(player.position());
		Vec3 at = player.getEyePosition().add(toward.lengthSqr() < 1.0E-4 ? player.getLookAngle() : toward.normalize().scale(0.9));
		grit(level, rocks, at, 14);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.0F, 1.2F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.7F, 0.8F);
		return true;
	}

	private static void grit(ServerLevel level, GravityRocks.Rocks rocks, Vec3 at, int count) {
		ItemStack stone = rocks.stack();
		if (!stone.isEmpty()) {
			level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stone.getItem()), at.x, at.y, at.z, count, 0.3, 0.3, 0.3, 0.08);
		}
	}

	/** Something the rocks may be sent at: alive, here, not their holder, not on their side, not a player they can't harm. */
	private static boolean fair(ServerPlayer player, LivingEntity entity) {
		return entity != player && entity.isAlive() && !entity.isSpectator() && entity.level() == player.level() && !entity.isAlliedTo(player)
			&& !(entity instanceof net.minecraft.world.entity.OwnableEntity owned && owned.getOwner() == player)
			&& !(entity instanceof Player other && (other.isCreative() || !player.canHarmPlayer(other)));
	}

	/** What the holder is looking at within {@code range} (their lock-on target first). */
	private static @Nullable LivingEntity looked(ServerPlayer player, double range) {
		LivingEntity locked = LockOn.target(player);
		if (locked != null && fair(player, locked)) {
			return locked;
		}
		return GravityPower.entityInSight(player, range) instanceof LivingEntity living && fair(player, living) ? living : null;
	}

	/** Where steered rocks fly to: the first block along the look, or the end of {@link #STEER_RANGE} (both sides). */
	public static Vec3 steerPoint(Player player, float partialTick) {
		Vec3 eye = player.getEyePosition(partialTick);
		Vec3 look = player.getViewVector(partialTick);
		Vec3 end = eye.add(look.scale(STEER_RANGE));
		BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation().subtract(look.scale(0.4));
	}

	/**
	 * Where rock {@code i} of {@code n} is in a steer or a closing ring at {@code now} (both sides), flown out from over
	 * its holder's shoulder. Null when the ring has nothing to ring any more. (A storm's rocks are on
	 * {@link ArsenalStorm#place}.)
	 */
	public static @Nullable Vec3 place(Player player, Order order, int i, int n, double now, float partialTick) {
		Vec3 home = player.getPosition(partialTick).add(0.0, player.getBbHeight() * 1.1, 0.0);
		double angle = (now - order.start()) * 0.3 + i * Mth.TWO_PI / n;
		double out = Mth.clamp((now - order.start()) / RING_OUT_TICKS, 0.0, 1.0);
		out = out * out * (3.0 - 2.0 * out);
		if (order.kind() == RING) {
			Entity target = player.level().getEntity(order.target());
			if (target == null) {
				return null;
			}
			Vec3 center = target.getPosition(partialTick).add(0.0, target.getBbHeight() * 0.5, 0.0);
			double close = Mth.clamp((now - (order.end() - RING_CLOSE_TICKS)) / RING_CLOSE_TICKS, 0.0, 1.0);
			double r = Mth.lerp(close * close, Math.max(RING_RADIUS, target.getBbWidth() * 0.5 + 1.0), 0.2);
			return home.lerp(center.add(Math.cos(angle) * r, Math.sin(angle * 0.5 + i) * 0.4 * (1.0 - close), Math.sin(angle) * r), out);
		}
		// a tumbling knot of rocks round the spot looked at
		Vec3 there = steerPoint(player, partialTick).add(Math.cos(angle) * 0.7, Math.sin(angle * 1.3 + i) * 0.5, Math.sin(angle) * 0.7);
		return home.lerp(there, out);
	}

	static void forget(ServerPlayer player) {
		UUID id = player.getUUID();
		PRESSED.remove(id);
		HARRIED.remove(id);
		HIT.remove(id);
		end(player);
	}
}
