package com.jaymoskingdoms;

import com.jaymoskingdoms.creatures.OldRules;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Ride your thrall by day (Andy's own row, Night's Blood patch 5). From your coffin by day, sneak-use it to lie down (the day's rest,
 * {@link BedRest}) and, with a thrall villager of yours to hand ({@link #ridable}), look out through its eyes: the camera is
 * theirs ({@link Seeing}, as a familiar's, {@link Familiars}), and the body does as you will ({@link Drive}): walk, sprint, jump,
 * turn to face where it goes; use its hands (what the villager's reach touches, a door, a lever, a trader or a talker, as the
 * range checks measure from the thrall, {@code RideRangeMixin}); carry and fetch: what it passes over it picks up ({@link #PACK}),
 * hands over when it is back by the coffin, and drops where you tell it. Folk see only the villager.
 * <p>
 * Your own body lies in the coffin and can be staked ({@link OldRules#stakeAsleep}). A rider is not counted among those who lie
 * down to sleep the day away ({@link CoffinRest}), so the ride never skips the day. The ride ends, and you wake:
 * <ul>
 * <li>when you choose (the wake key), or at nightfall, or when you leave the coffin or die;</li>
 * <li>when the thrall is badly hurt ({@link #WAKE_HEALTH} of its health or {@link #BIG_BLOW} in one blow), dies, or falls unsound ({@link Servants#unsound});</li>
 * <li>when the thrall stands in a temple or under holy dread ({@link #THROW_DREAD}): thrown out, sick and weak, with a shake of the view.</li>
 * </ul>
 * The thrall's thinking is switched off while ridden ({@code NoAi}, marked {@link #RIDDEN} so a crash never leaves it so), and it
 * keeps within {@link #LEASH} of the coffin, as far as the client's view of the world reaches. Its hands are wholly yours: you fight,
 * dig, build, open chests and use what you hold from where it stands (reach from its eyes, {@code RideRangeMixin}), and what you
 * shoot or throw leaves from its eyes.
 */
public final class ThrallRide {
	/** How far (blocks) from the coffin the thrall may walk. */
	static final double LEASH = 80.0;
	/** The thrall wakes you under this share of its health, and at a single blow of this much. */
	static final float WAKE_HEALTH = 0.5F, BIG_BLOW = 6.0F;
	/** Holy dread (0-1) that throws the rider out. */
	static final float THROW_DREAD = 0.25F;
	/** Blocks per tick: a walk, a sprint, a sneak. */
	static final double WALK = 0.2, SPRINT = 0.28, SNEAK = 0.07;
	/** Fall and rise: gravity, drag and the jump's lift (blocks per tick). */
	static final double GRAVITY = 0.08, DRAG = 0.98, JUMP = 0.42, SWIM = 0.1;
	/** Degrees the body turns per tick toward where it walks; how far the head may turn from the body. */
	static final float TURN = 25.0F, HEAD_FROM_BODY = 60.0F;
	/** How far (blocks) the thrall picks things up; how near the coffin it hands them over. */
	static final double PICK_UP = 1.2, HAND_OVER = 3.0;
	/** How long (ticks) a sneak-use waits for the player to lie down before it is forgotten. */
	static final int PENDING_TICKS = 60;
	/** Weakness and slowness (ticks), and the sickness of the shake, a thrown-out rider carries. */
	static final int THROWN_WEAK = 600, THROWN_SLOW = 200, THROWN_SHAKE = 100;

	/** The keys the client holds, as bits. */
	public static final int FORWARD = 1, BACK = 2, LEFT = 4, RIGHT = 8, JUMPING = 16, SNEAKING = 32, SPRINTING = 64, DROP = 128;
	/** What a {@link Drive} asks besides moving: nothing, wake, or ride the next thrall. */
	public static final int NONE = 0, WAKE = 1, NEXT = 2;

	/** On a thrall being ridden: kept, so a crash or a restart gives it its mind back. */
	static final AttachmentType<Boolean> RIDDEN = AttachmentRegistry.create(JaymosKingdoms.id("thrall_ridden"), b -> b.persistent(Codec.BOOL));

	/** Client → server: where the rider looks, the keys held, and wake or next. */
	public record Drive(float yaw, float pitch, int keys, int act) implements CustomPacketPayload {
		public static final Type<Drive> TYPE = new Type<>(JaymosKingdoms.id("thrall_drive"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Drive> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, Drive::yaw, ByteBufCodecs.FLOAT, Drive::pitch, ByteBufCodecs.VAR_INT, Drive::keys, ByteBufCodecs.VAR_INT, Drive::act, Drive::new);

		@Override
		public Type<Drive> type() {
			return TYPE;
		}
	}

	/** Server → client: the entity whose eyes the camera is, or -1 for your own. */
	public record Seeing(int entity) implements CustomPacketPayload {
		public static final Type<Seeing> TYPE = new Type<>(JaymosKingdoms.id("thrall_seeing"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Seeing> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Seeing::entity, Seeing::new);

		@Override
		public Type<Seeing> type() {
			return TYPE;
		}
	}

	private static final class Ride {
		UUID thrall;
		float yaw, pitch, hurtAt;
		int keys;

		Ride(UUID thrall, float yaw, float pitch, float health) {
			this.thrall = thrall;
			this.yaw = yaw;
			this.pitch = pitch;
			this.hurtAt = health;
		}
	}

	private static final Map<UUID, Ride> RIDES = new HashMap<>();
	/** Players who sneak-used a coffin and are on their way to lying in it: when. */
	private static final Map<UUID, Long> PENDING = new HashMap<>();

	private ThrallRide() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Drive.TYPE, Drive.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Seeing.TYPE, Seeing.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Drive.TYPE, (payload, context) -> drive(context.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(ThrallRide::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			end(handler.player, false);
			PENDING.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			RIDES.clear();
			PENDING.clear();
		});
		// a thrall that comes back from a crash while marked ridden gets its mind back
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob m && m.hasAttached(RIDDEN) && RIDES.values().stream().noneMatch(r -> r.thrall.equals(m.getUUID()))) {
				release(m);
			}
		});
		// sneak-using the coffin by day with a thrall about: the ride begins when you are lying in it, and the page doesn't open
		Identifier early = JaymosKingdoms.id("thrall_ride_early");
		UseBlockCallback.EVENT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
		UseBlockCallback.EVENT.register(early, (player, level, hand, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown() && player instanceof ServerPlayer sp && level instanceof ServerLevel server
				&& !riding(sp) && wants(sp, server, hit.getBlockPos())) {
				PENDING.put(sp.getUUID(), server.getGameTime());
			}
			return InteractionResult.PASS;
		});
		// the thrall's hands are yours (Andy: "they should play completely as if you were they"): reach is measured from its eyes
		// (RideRangeMixin), and what you shoot or throw leaves from its eyes along its look, not from the coffin
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Projectile shot && shot.tickCount == 0 && shot.getOwner() instanceof ServerPlayer owner && riding(owner)
				&& thrall(owner) instanceof Villager v) {
				double speed = shot.getDeltaMovement().length();
				Vec3 eye = v.getEyePosition();
				shot.setPos(eye.x, eye.y - 0.1, eye.z);
				shot.setDeltaMovement(v.getLookAngle().scale(speed));
				shot.setYRot(v.getYHeadRot());
				shot.setXRot(v.getXRot());
			}
		});
	}

	// ---- asking -------------------------------------------------------------------------------------------------------

	/** Whether {@code player} is riding a thrall now (server side). */
	public static boolean riding(@Nullable Player player) {
		return player != null && !player.level().isClientSide() && RIDES.containsKey(player.getUUID());
	}

	/** Whether {@code player} has just sneak-used a coffin to ride from: the Blood page leaves the click alone. */
	public static boolean willRide(ServerPlayer player) {
		return PENDING.containsKey(player.getUUID());
	}

	/** The villager {@code player} is riding now, or null. */
	public static @Nullable Villager thrall(Player player) {
		Ride r = player.level().isClientSide() ? null : RIDES.get(player.getUUID());
		if (r != null && player.level() instanceof ServerLevel level && level.getEntity(r.thrall) instanceof Villager v && v.isAlive()) {
			return v;
		}
		return null;
	}

	/** Where the rider's eyes are for reach: the thrall's, if it is ridden by {@code player}; else null. */
	public static @Nullable Vec3 eyes(Player player) {
		Ride r = player.level().isClientSide() ? null : RIDES.get(player.getUUID());
		if (r != null && player.level() instanceof ServerLevel level && level.getEntity(r.thrall) instanceof Villager v && v.isAlive()) {
			return v.getEyePosition();
		}
		return null;
	}

	private static boolean wants(ServerPlayer sp, ServerLevel level, BlockPos bed) {
		return !Vampires.night(level.getOverworldClockTime()) && Vampires.isVampire(sp) && !VampirePlayers.dhampir(sp) && OldRules.coffin(level, bed)
			&& !ridable(sp, level, Vec3.atCenterOf(bed)).isEmpty() && sp.getMainHandItem().isEmpty();
	}

	/** Thrall villagers of {@code player}'s near {@code at} that can be ridden now, the nearest first. */
	static List<Villager> ridable(ServerPlayer player, ServerLevel level, Vec3 at) {
		MinecraftServer server = level.getServer();
		List<Villager> out = new ArrayList<>();
		for (Mob m : Servants.thrallsNear(player, at, LEASH)) {
			if (m instanceof Villager v && !v.isBaby() && !v.hasAttached(RIDDEN) && !Servants.unsound(v) && v.getHealth() > v.getMaxHealth() * WAKE_HEALTH
				&& !HolyDread.inTemple(server, v) && HolyDread.dreadAt(server, v) < THROW_DREAD) {
				out.add(v);
			}
		}
		out.sort(Comparator.comparingDouble(v -> v.position().distanceToSqr(at)));
		return out;
	}

	// ---- beginning and ending -----------------------------------------------------------------------------------------

	private static void begin(ServerPlayer sp, ServerLevel level, Villager thrall) {
		thrall.setAttached(RIDDEN, true);
		thrall.setNoAi(true);
		thrall.getNavigation().stop();
		thrall.setDeltaMovement(Vec3.ZERO);
		if (thrall.isSleeping()) {
			thrall.stopSleeping();
		}
		Ride r = new Ride(thrall.getUUID(), thrall.getYHeadRot(), thrall.getXRot(), thrall.getHealth());
		RIDES.put(sp.getUUID(), r);
		ServerPlayNetworking.send(sp, new Seeing(thrall.getId()));
		level.playSound(null, sp.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.6F, 0.5F);
	}

	private static void release(Mob thrall) {
		thrall.removeAttached(RIDDEN);
		thrall.setNoAi(false);
		thrall.setDeltaMovement(Vec3.ZERO);
	}

	/** {@code thrall} is freed: if it was ridden, the ride ends. */
	static void freed(Mob thrall) {
		for (Map.Entry<UUID, Ride> e : new ArrayList<>(RIDES.entrySet())) {
			if (e.getValue().thrall.equals(thrall.getUUID()) && thrall.level().getServer() != null) {
				ServerPlayer p = thrall.level().getServer().getPlayerList().getPlayer(e.getKey());
				if (p != null) {
					end(p, true);
				}
			}
		}
		if (thrall.hasAttached(RIDDEN)) {
			release(thrall);
		}
	}

	/** {@code player}'s ride ends (if they had one); with {@code wake} they get up from the coffin. */
	static void end(ServerPlayer player, boolean wake) {
		PENDING.remove(player.getUUID());
		Ride r = RIDES.remove(player.getUUID());
		if (r == null) {
			return;
		}
		if (player.level() instanceof ServerLevel level && level.getEntity(r.thrall) instanceof Mob m) {
			release(m);
		}
		if (ServerPlayNetworking.canSend(player, Seeing.TYPE)) {
			ServerPlayNetworking.send(player, new Seeing(-1));
		}
		if (wake && player.isSleeping() && player.isAlive()) {
			player.stopSleepInBed(true, true);
		}
	}

	private static void thrownOut(ServerPlayer sp, ServerLevel level) {
		end(sp, true);
		sp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, THROWN_WEAK, 1));
		sp.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, THROWN_SLOW, 1));
		sp.addEffect(new MobEffectInstance(MobEffects.NAUSEA, THROWN_SHAKE, 0, false, false, false));
		level.sendParticles(ParticleTypes.SMOKE, sp.getX(), sp.getY(0.6), sp.getZ(), 20, 0.3, 0.4, 0.3, 0.02);
		level.playSound(null, sp.blockPosition(), SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 0.6F, 0.7F);
	}

	// ---- the rider's will -----------------------------------------------------------------------------------------------

	private static void drive(ServerPlayer player, Drive d) {
		Ride r = RIDES.get(player.getUUID());
		if (r == null || !Float.isFinite(d.yaw()) || !Float.isFinite(d.pitch())) {
			return;
		}
		r.yaw = Mth.wrapDegrees(d.yaw());
		r.pitch = Mth.clamp(d.pitch(), -90.0F, 90.0F);
		r.keys = d.keys();
		if (d.act() == WAKE) {
			end(player, true);
		} else if (d.act() == NEXT && player.level() instanceof ServerLevel level) {
			next(player, level, r);
		}
	}

	/** Rides the next thrall of theirs in turn. */
	private static void next(ServerPlayer sp, ServerLevel level, Ride r) {
		BlockPos bed = sp.getSleepingPos().orElse(sp.blockPosition());
		List<Villager> all = ridable(sp, level, Vec3.atCenterOf(bed));
		Entity now = level.getEntity(r.thrall);
		if (all.isEmpty() || now == null) {
			return;
		}
		all.sort(Comparator.comparing(v -> v.getUUID().toString()));
		int at = 0;
		for (int i = 0; i < all.size(); i++) {
			if (all.get(i).getUUID().compareTo(now.getUUID()) > 0) {
				at = i;
				break;
			}
		}
		Villager to = all.get(at);
		if (level.getEntity(r.thrall) instanceof Mob m) {
			release(m);
		}
		RIDES.remove(sp.getUUID());
		begin(sp, level, to);
	}

	// ---- every tick -----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (java.util.Iterator<Map.Entry<UUID, Long>> it = PENDING.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Long> e = it.next();
			ServerPlayer sp = server.getPlayerList().getPlayer(e.getKey());
			if (sp == null || now - e.getValue() > PENDING_TICKS) {
				it.remove();
			} else if (CoffinRest.inCoffin(sp) && sp.level() instanceof ServerLevel level) {
				it.remove();
				BlockPos bed = sp.getSleepingPos().orElse(sp.blockPosition());
				List<Villager> all = ridable(sp, level, Vec3.atCenterOf(bed));
				if (!all.isEmpty() && !Vampires.night(level.getOverworldClockTime())) {
					begin(sp, level, all.getFirst());
				}
			}
		}
		if (RIDES.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Ride> e : new ArrayList<>(RIDES.entrySet())) {
			ServerPlayer sp = server.getPlayerList().getPlayer(e.getKey());
			if (sp == null || !(sp.level() instanceof ServerLevel level)) {
				RIDES.remove(e.getKey());
				continue;
			}
			step(server, level, sp, e.getValue());
		}
	}

	private static void step(MinecraftServer server, ServerLevel level, ServerPlayer sp, Ride r) {
		if (!sp.isAlive() || !CoffinRest.inCoffin(sp)) {
			end(sp, false);
			return;
		}
		if (!(level.getEntity(r.thrall) instanceof Villager t) || !t.isAlive() || !Servants.ownedBy(t, sp)) {
			end(sp, true);
			return;
		}
		if (Vampires.night(level.getOverworldClockTime()) || Servants.unsound(t)) {
			end(sp, true);
			return;
		}
		if (t.getHealth() <= t.getMaxHealth() * WAKE_HEALTH || t.getHealth() < r.hurtAt - BIG_BLOW) {
			end(sp, true);
			return;
		}
		r.hurtAt = t.getHealth();
		if (HolyDread.inTemple(server, t) || HolyDread.dreadAt(server, t) >= THROW_DREAD) {
			thrownOut(sp, level);
			return;
		}
		BlockPos bed = sp.getSleepingPos().orElse(sp.blockPosition());
		move(t, r, Vec3.atCenterOf(bed));
		pick(level, t);
		if ((r.keys & DROP) != 0) {
			dropPack(t, level);
			r.keys &= ~DROP;
		}
		if (t.distanceToSqr(Vec3.atCenterOf(bed)) <= HAND_OVER * HAND_OVER) {
			handOver(sp, t);
		}
	}

	/** One tick of the body: walk where the keys say, face where it goes, look where the rider looks. */
	private static void move(Villager t, Ride r, Vec3 coffin) {
		int fwd = ((r.keys & FORWARD) != 0 ? 1 : 0) - ((r.keys & BACK) != 0 ? 1 : 0);
		int left = ((r.keys & LEFT) != 0 ? 1 : 0) - ((r.keys & RIGHT) != 0 ? 1 : 0);
		double speed = (r.keys & SNEAKING) != 0 ? SNEAK : (r.keys & SPRINTING) != 0 ? SPRINT : WALK;
		double sin = Math.sin(r.yaw * Mth.DEG_TO_RAD), cos = Math.cos(r.yaw * Mth.DEG_TO_RAD);
		double ix = left * cos - fwd * sin, iz = fwd * cos + left * sin;
		double len = Math.sqrt(ix * ix + iz * iz);
		double dx = 0.0, dz = 0.0;
		if (len > 1.0E-4) {
			dx = ix / len * speed;
			dz = iz / len * speed;
			// no further than the leash
			double nx = t.getX() + dx - coffin.x, nz = t.getZ() + dz - coffin.z;
			if (nx * nx + nz * nz > LEASH * LEASH) {
				dx = 0.0;
				dz = 0.0;
			}
		}
		double vy = t.getDeltaMovement().y;
		boolean jump = (r.keys & JUMPING) != 0;
		if (t.isInWater() || t.isInLava()) {
			vy = jump ? SWIM : Math.max(vy * 0.8 - 0.01, -SWIM);
		} else if (t.onGround()) {
			vy = jump ? JUMP : -0.0784;
		} else {
			vy = (vy - GRAVITY) * DRAG;
		}
		t.move(MoverType.SELF, new Vec3(dx, vy, dz));
		if (t.verticalCollision) {
			vy = 0.0;
		}
		t.setDeltaMovement(0.0, vy, 0.0);
		// the body faces where it goes; the head looks where the rider looks, within reach of the body
		float body = t.yBodyRot;
		if (dx != 0.0 || dz != 0.0) {
			float heading = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
			body += Mth.clamp(Mth.wrapDegrees(heading - body), -TURN, TURN);
		}
		float off = Mth.wrapDegrees(r.yaw - body);
		if (Math.abs(off) > HEAD_FROM_BODY) {
			body += off - Math.signum(off) * HEAD_FROM_BODY;
		}
		t.setYBodyRot(body);
		t.setYRot(body);
		t.setYHeadRot(r.yaw);
		t.setXRot(r.pitch);
	}

	/** What the thrall passes over goes into its pack, as far as it holds. */
	private static void pick(ServerLevel level, Villager t) {
		List<ItemStack> pack = new ArrayList<>(t.getAttachedOrElse(Servants.PACK, List.of()));
		boolean changed = false;
		for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, t.getBoundingBox().inflate(PICK_UP, 0.5, PICK_UP), e -> e.isAlive() && !e.hasPickUpDelay())) {
			ItemStack in = ie.getItem().copy();
			for (ItemStack have : pack) {
				if (!in.isEmpty() && ItemStack.isSameItemSameComponents(have, in) && have.getCount() < have.getMaxStackSize()) {
					int take = Math.min(in.getCount(), have.getMaxStackSize() - have.getCount());
					have.grow(take);
					in.shrink(take);
				}
			}
			if (!in.isEmpty() && pack.size() < Servants.PACK_SLOTS) {
				pack.add(in.copy());
				in.setCount(0);
			}
			if (in.isEmpty()) {
				ie.discard();
				changed = true;
			} else if (in.getCount() != ie.getItem().getCount()) {
				ie.setItem(in);
				changed = true;
			}
		}
		if (changed) {
			t.setAttached(Servants.PACK, pack);
			level.playSound(null, t.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.3F, 1.2F);
		}
	}

	/** The pack goes into the rider's hands. */
	private static void handOver(ServerPlayer sp, Villager t) {
		List<ItemStack> pack = t.getAttachedOrElse(Servants.PACK, List.of());
		if (pack.isEmpty()) {
			return;
		}
		for (ItemStack s : pack) {
			if (!s.isEmpty() && !sp.getInventory().add(s.copy())) {
				sp.spawnAtLocation(sp.level(), s.copy());
			}
		}
		t.removeAttached(Servants.PACK);
	}

	/** The pack falls at the thrall's feet. */
	private static void dropPack(Villager t, ServerLevel level) {
		for (ItemStack s : t.getAttachedOrElse(Servants.PACK, List.of())) {
			if (!s.isEmpty()) {
				level.addFreshEntity(new ItemEntity(level, t.getX(), t.getY(0.5), t.getZ(), s.copy()));
			}
		}
		t.removeAttached(Servants.PACK);
	}
}
