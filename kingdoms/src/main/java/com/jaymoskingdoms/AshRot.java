package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Rot, the Ash Whisperer's decay (Andy's Awakening pick, 2026-11-17: "Ruin's decay powers go to the Ash Whisperer's followers; the
 * Unseeing's ritual-keepers fight with decay, blood and death magic"). Our own take on the idea, nothing of the Awakening's.
 * <p>
 * <b>Decay</b> is a count of stacks on a living thing, with no cap. Every stack hurts it {@link #ROT_DAMAGE} each second (a
 * withering, credited to whoever put the last stack on), and all of them drop {@link #ROT_FADE} ticks after the last new one.
 * Anyone may lay it ({@link #rot}): the Ash's sworn, the Cordon (its hunters), Jaymo's Powers' Unseeing ritual-keepers.
 * <p>
 * The Ash Whisperer's sworn learn it tier by tier, on the Rot key (End, rebindable):
 * <ul>
 * <li>tier 1: their blows put a stack of Decay on what they strike;</li>
 * <li>tier 2: <b>Grip</b> (tap at a foe): it is pinned where it stands {@link #GRIP_TICKS} ticks and stacked while it is held;
 * <b>Spread</b> (sneak + tap): a strip of ground ahead rots for a while, stacking whatever stands on it;</li>
 * <li>tier 3: <b>Rush</b> (sprint + tap at a foe): you take it and drive it ahead of you until it meets a wall;
 * <b>Breach</b> (tap at a block, no foe in sight): a tunnel rots through the wall, and closes again after a while;</li>
 * <li>tier 4: <b>Collapse</b> (hold the key): the ground where you look caves in to a great crater; everything in it is stacked
 * deep. The earth closes over it again in time.</li>
 * </ul>
 * Holes rot only through plain blocks (no chests or other block entities, nothing unbreakable, no water or lava), and every one
 * of them grows back in the order it went, never over anyone standing in the way.
 */
public final class AshRot {
	// ---- tuning ----
	/** Harm per stack per second, the beat, and how long after the last new stack they all drop (ticks). */
	static final float ROT_DAMAGE = 0.5F;
	static final int ROT_BEAT = 20, ROT_FADE = 160;
	/** Stacks a sworn blow lays (tier 1). */
	static final int BLOW_STACKS = 1;
	/** The ash-grey green of rot, and the darker heart of a deep rot. */
	static final int COLOUR = 0x6E6B45, DEEP = 0x3A3824;
	/** The tier each art comes at. */
	static final int TIER_BLOWS = 1, TIER_GRIP = 2, TIER_RUSH = 3, TIER_COLLAPSE = 4;
	/** Grip: reach, ticks held, stacks laid at once, a stack every so many ticks while held, cooldown. */
	static final double GRIP_REACH = 16.0;
	static final int GRIP_TICKS = 50, GRIP_STACKS = 3, GRIP_EVERY = 10, GRIP_COOLDOWN = 200;
	/** Spread: length and half-width of the strip (blocks), ticks it lasts, cooldown. */
	static final double SPREAD_LENGTH = 12.0, SPREAD_HALF = 1.5;
	static final int SPREAD_TICKS = 120, SPREAD_COOLDOWN = 300;
	/** Rush: reach to take a foe, most ticks driven, speed (blocks a tick), the wall's blow and stacks, cooldown. */
	static final double RUSH_REACH = 10.0, RUSH_SPEED = 1.1;
	static final int RUSH_TICKS = 14, RUSH_STACKS = 4, RUSH_COOLDOWN = 240;
	static final float RUSH_DAMAGE = 6.0F;
	/** Breach: reach to the wall, depth and half-width of the tunnel, ticks till it closes, cooldown. */
	static final double BREACH_REACH = 8.0;
	static final int BREACH_DEPTH = 6, BREACH_HALF = 1, BREACH_CLOSE = 400, BREACH_COOLDOWN = 300;
	/** Collapse: the hold (ticks), reach, the crater's radius and depth, the stacks, ticks till it closes, cooldown. */
	static final int COLLAPSE_HOLD = 40, COLLAPSE_RADIUS = 9, COLLAPSE_DEPTH = 6, COLLAPSE_STACKS = 6, COLLAPSE_CLOSE = 1200, COLLAPSE_COOLDOWN = 20 * 60 * 5;
	static final double COLLAPSE_REACH = 32.0;
	/** Blocks grown back a tick at most, so a closing crater is not one great lurch. */
	static final int REGROW_PER_TICK = 40;

	private static final int GRIP = 0, SPREAD = 1, RUSH = 2, BREACH = 3, COLLAPSE = 4;

	/** The Rot key went down or up. */
	public record Key(boolean down) implements CustomPacketPayload {
		public static final Type<Key> TYPE = new Type<>(JaymosKingdoms.id("ash_rot_key"));
		public static final StreamCodec<ByteBuf, Key> CODEC = ByteBufCodecs.BOOL.map(Key::new, Key::down);

		@Override
		public Type<Key> type() {
			return TYPE;
		}
	}

	private static final class Rot {
		int stacks;
		long last;
		@Nullable UUID by;
	}

	private record Grip(LivingEntity target, Vec3 at, long until) {
	}

	private record Strip(ResourceKey<Level> dim, Vec3 from, Vec3 dir, long until, @Nullable UUID by) {
	}

	private record Drive(ServerPlayer player, LivingEntity target, Vec3 dir, long until) {
	}

	/** A hole rotted in the world, closing in order once its time comes. */
	private static final class Scar {
		final ResourceKey<Level> dim;
		final List<BlockPos> pos = new ArrayList<>();
		final List<BlockState> states = new ArrayList<>();
		final long closeAt;
		int next;

		Scar(ResourceKey<Level> dim, long closeAt) {
			this.dim = dim;
			this.closeAt = closeAt;
		}
	}

	private static final Map<LivingEntity, Rot> ROTS = new WeakHashMap<>();
	private static final List<Grip> GRIPS = new ArrayList<>();
	private static final List<Strip> STRIPS = new ArrayList<>();
	private static final List<Drive> DRIVES = new ArrayList<>();
	private static final List<Scar> SCARS = new ArrayList<>();

	/** A hole as its level keeps it: where it was eaten and what stood there. */
	record Hole(List<Long> pos, List<BlockState> states) {
		static final Codec<Hole> CODEC = RecordCodecBuilder.create(i -> i.group(Codec.LONG.listOf().fieldOf("pos").forGetter(Hole::pos),
			BlockState.CODEC.listOf().fieldOf("states").forGetter(Hole::states)).apply(i, Hole::new));
	}

	/**
	 * The holes open in a level, saved with it. A normal stop closes every hole and leaves none here, so any found when the server
	 * starts were left by a crash, and are closed then.
	 */
	static final AttachmentType<List<Hole>> HOLES = AttachmentRegistry.create(JaymosKingdoms.id("ash_rot_holes"),
		builder -> builder.persistent(Hole.CODEC.listOf()));
	private static final Map<UUID, long[]> READY = new HashMap<>();
	private static final Map<UUID, Long> HELD_FROM = new HashMap<>();

	private AshRot() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Key.TYPE, Key.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Key.TYPE, (payload, context) -> key(context.player(), payload.down()));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0F && source.getEntity() instanceof ServerPlayer p && source.isDirect() && Patrons.blessed(p, Patron.ASH_WHISPERER, TIER_BLOWS)) {
				rot(entity, p, BLOW_STACKS);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(AshRot::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			READY.remove(handler.player.getUUID());
			HELD_FROM.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			// holes a crash left open close now
			for (ServerLevel level : server.getAllLevels()) {
				List<Hole> left = level.removeAttached(HOLES);
				if (left == null) {
					continue;
				}
				for (Hole hole : left) {
					for (int i = Math.min(hole.pos().size(), hole.states().size()) - 1; i >= 0; i--) {
						BlockPos pos = BlockPos.of(hole.pos().get(i));
						if (level.getBlockState(pos).canBeReplaced()) {
							level.setBlock(pos, hole.states().get(i), Block.UPDATE_ALL);
						}
					}
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			// every hole closes before the world is saved
			for (Scar scar : SCARS) {
				ServerLevel level = server.getLevel(scar.dim);
				if (level != null) {
					while (scar.next < scar.pos.size()) {
						regrow(level, scar, true);
					}
				}
			}
			SCARS.clear();
			for (ServerLevel level : server.getAllLevels()) {
				level.removeAttached(HOLES);
			}
			ROTS.clear();
			GRIPS.clear();
			STRIPS.clear();
			DRIVES.clear();
			READY.clear();
			HELD_FROM.clear();
		});
	}

	// ---- the stacks ----------------------------------------------------------------------------------------------------------

	/** Lays {@code stacks} of Decay on {@code target}, by {@code by} (may be null). Public: the Cordon and Jaymo's Powers call it. */
	public static void rot(LivingEntity target, @Nullable LivingEntity by, int stacks) {
		if (target.level().isClientSide() || !target.isAlive() || stacks <= 0 || target == by) {
			return;
		}
		Rot rot = ROTS.computeIfAbsent(target, t -> new Rot());
		rot.stacks += stacks;
		rot.last = target.level().getGameTime();
		if (by != null) {
			rot.by = by.getUUID();
		}
		if (target.level() instanceof ServerLevel level) {
			level.sendParticles(Fx.miasma(COLOUR, 1.0F + Math.min(2.0F, rot.stacks * 0.1F)), target.getX(), target.getY(0.5), target.getZ(),
				3 + Math.min(12, stacks * 2), target.getBbWidth() * 0.4, target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.01);
		}
	}

	/** The Decay on {@code target} now (0 without). */
	public static int stacks(LivingEntity target) {
		Rot rot = ROTS.get(target);
		return rot == null ? 0 : rot.stacks;
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		Iterator<Map.Entry<LivingEntity, Rot>> it = ROTS.entrySet().iterator();
		List<Runnable> hurts = new ArrayList<>();
		while (it.hasNext()) {
			Map.Entry<LivingEntity, Rot> e = it.next();
			LivingEntity target = e.getKey();
			Rot rot = e.getValue();
			if (!target.isAlive() || target.isRemoved() || now - rot.last >= ROT_FADE) {
				it.remove();
				continue;
			}
			// a steady beat of its own (not timed from the last stack, which a Grip keeps renewing)
			if ((now + target.getId()) % ROT_BEAT == 0 && target.level() instanceof ServerLevel level) {
				Entity by = rot.by == null ? null : level.getEntity(rot.by);
				float harm = rot.stacks * ROT_DAMAGE;
				hurts.add(() -> {
					DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.WITHER), null, by);
					target.setInvulnerableTime(0);
					target.hurtServer(level, source, harm);
				});
				level.sendParticles(Fx.miasma(rot.stacks >= 10 ? DEEP : COLOUR, 1.2F), target.getX(), target.getY(0.6), target.getZ(),
					Math.min(20, 2 + rot.stacks), target.getBbWidth() * 0.4, target.getBbHeight() * 0.35, target.getBbWidth() * 0.4, 0.005);
			}
		}
		// (hurt after walking the map: a death in the middle of it would change the map under the walk)
		hurts.forEach(Runnable::run);
		tickGrips(now);
		tickStrips(server, now);
		tickDrives(now);
		tickScars(server, now);
		for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
			Long from = HELD_FROM.get(sp.getUUID());
			if (from != null && now - from == COLLAPSE_HOLD && Patrons.blessed(sp, Patron.ASH_WHISPERER, TIER_COLLAPSE)) {
				// the hold is full: a low groan tells them the ground is ready to go
				sp.level().playSound(null, sp.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.4F, 0.4F);
			}
		}
	}

	// ---- the key -------------------------------------------------------------------------------------------------------------

	private static void key(ServerPlayer player, boolean down) {
		if (!player.isAlive() || player.isSpectator()) {
			return;
		}
		if (!Patrons.blessed(player, Patron.ASH_WHISPERER, TIER_GRIP)) {
			if (down) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.ash_rot.unknown").withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		long now = player.level().getGameTime();
		if (down) {
			HELD_FROM.put(player.getUUID(), now);
			return;
		}
		Long from = HELD_FROM.remove(player.getUUID());
		if (from == null) {
			return;
		}
		boolean deep = Patrons.blessed(player, Patron.ASH_WHISPERER, TIER_RUSH);
		if (now - from >= COLLAPSE_HOLD && Patrons.blessed(player, Patron.ASH_WHISPERER, TIER_COLLAPSE)) {
			use(player, COLLAPSE, COLLAPSE_COOLDOWN, () -> collapse(player));
		} else if (player.isSprinting() && deep) {
			use(player, RUSH, RUSH_COOLDOWN, () -> rush(player));
		} else if (player.isShiftKeyDown()) {
			use(player, SPREAD, SPREAD_COOLDOWN, () -> spread(player));
		} else if (aimed(player, GRIP_REACH) != null || !deep) {
			use(player, GRIP, GRIP_COOLDOWN, () -> grip(player));
		} else {
			use(player, BREACH, BREACH_COOLDOWN, () -> breach(player));
		}
	}

	private static void use(ServerPlayer player, int art, int cooldown, java.util.function.BooleanSupplier act) {
		long now = player.level().getGameTime();
		long[] ready = READY.computeIfAbsent(player.getUUID(), k -> new long[5]);
		if (ready[art] > now && !player.isCreative()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.ash_rot.wait", (ready[art] - now + 19) / 20).withStyle(ChatFormatting.GRAY));
			return;
		}
		if (act.getAsBoolean()) {
			ready[art] = now + cooldown;
		}
	}

	private static @Nullable LivingEntity aimed(ServerPlayer player, double reach) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(reach));
		LivingEntity best = null;
		double near = Double.MAX_VALUE;
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().expandTowards(player.getLookAngle().scale(reach)).inflate(1.0),
			e -> e != player && e.isAlive() && !e.isSpectator() && !player.isAlliedTo(e))) {
			var hit = e.getBoundingBox().inflate(0.3).clip(eye, end);
			if (hit.isPresent() && hit.get().distanceToSqr(eye) < near && player.hasLineOfSight(e)) {
				near = hit.get().distanceToSqr(eye);
				best = e;
			}
		}
		return best;
	}

	// ---- Grip -------------------------------------------------------------------------------------------------------------------

	private static boolean grip(ServerPlayer player) {
		LivingEntity target = aimed(player, GRIP_REACH);
		if (target == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.ash_rot.no_foe").withStyle(ChatFormatting.GRAY));
			return false;
		}
		ServerLevel level = player.level();
		GRIPS.add(new Grip(target, target.position(), level.getGameTime() + GRIP_TICKS));
		rot(target, player, GRIP_STACKS);
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, GRIP_TICKS, 6, false, false, true));
		Fx.beam(level, player.getEyePosition().add(0.0, -0.3, 0.0), target.position().add(0.0, target.getBbHeight() * 0.5, 0.0), 0.12F, COLOUR, 10);
		Fx.pool(level, target.position().add(0.0, 0.05, 0.0), Math.max(1.0F, target.getBbWidth()), DEEP, GRIP_TICKS);
		level.playSound(null, target.blockPosition(), SoundEvents.SCULK_VEIN_PLACE, SoundSource.PLAYERS, 1.2F, 0.5F);
		return true;
	}

	private static void tickGrips(long now) {
		GRIPS.removeIf(g -> now >= g.until() || !g.target().isAlive());
		for (Grip g : GRIPS) {
			LivingEntity t = g.target();
			Vec3 v = t.getDeltaMovement();
			t.setDeltaMovement(0.0, Math.min(0.0, v.y), 0.0);
			if (t.position().distanceToSqr(g.at()) > 0.25 && !(t instanceof Player)) {
				t.teleportTo(g.at().x, t.getY(), g.at().z);
			}
			t.syncVelocity = true;
			if (t instanceof Mob mob) {
				mob.getNavigation().stop();
			}
			if ((g.until() - now) % GRIP_EVERY == 0) {
				rot(t, null, 1);
			}
		}
	}

	// ---- Spread -----------------------------------------------------------------------------------------------------------------

	private static boolean spread(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 dir = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		if (dir.lengthSqr() < 1.0E-4) {
			dir = Vec3.directionFromRotation(0.0F, player.getYRot());
		}
		dir = dir.normalize();
		Vec3 from = player.position();
		STRIPS.add(new Strip(level.dimension(), from, dir, level.getGameTime() + SPREAD_TICKS, player.getUUID()));
		for (double d = 1.0; d <= SPREAD_LENGTH; d += 2.0) {
			Vec3 at = from.add(dir.scale(d));
			Fx.pool(level, new Vec3(at.x, groundY(level, at) + 0.05, at.z), (float)SPREAD_HALF + 0.4F, COLOUR, SPREAD_TICKS);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 1.2F, 0.6F);
		return true;
	}

	private static double groundY(ServerLevel level, Vec3 at) {
		BlockPos pos = BlockPos.containing(at.x, at.y + 1.0, at.z);
		for (int i = 0; i < 6 && level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty(); i++) {
			pos = pos.below();
		}
		return pos.getY();
	}

	private static void tickStrips(MinecraftServer server, long now) {
		STRIPS.removeIf(s -> now >= s.until());
		if (now % ROT_BEAT != 0) {
			return;
		}
		for (Strip s : STRIPS) {
			ServerLevel level = server.getLevel(s.dim());
			if (level == null) {
				continue;
			}
			Vec3 end = s.from().add(s.dir().scale(SPREAD_LENGTH));
			AABB box = new AABB(s.from(), end).inflate(SPREAD_HALF, 2.0, SPREAD_HALF);
			Entity by = s.by() == null ? null : level.getEntity(s.by());
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != by && (e.onGround() || e.getY() - groundY(level, e.position()) < 0.6))) {
				Vec3 rel = e.position().subtract(s.from());
				double along = rel.dot(s.dir());
				double side = rel.subtract(s.dir().scale(along)).multiply(1.0, 0.0, 1.0).length();
				if (along >= 0.0 && along <= SPREAD_LENGTH && side <= SPREAD_HALF + e.getBbWidth() * 0.5) {
					rot(e, by instanceof LivingEntity l ? l : null, 1);
				}
			}
		}
	}

	// ---- Rush -------------------------------------------------------------------------------------------------------------------

	private static boolean rush(ServerPlayer player) {
		LivingEntity target = aimed(player, RUSH_REACH);
		if (target == null || target.getBbWidth() > 2.5 || target.getBbHeight() > 4.0) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.ash_rot.no_foe").withStyle(ChatFormatting.GRAY));
			return false;
		}
		Vec3 dir = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		dir = dir.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, player.getYRot()) : dir.normalize();
		DRIVES.add(new Drive(player, target, dir, player.level().getGameTime() + RUSH_TICKS));
		player.level().playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.6F, 1.4F);
		return true;
	}

	private static void tickDrives(long now) {
		Iterator<Drive> it = DRIVES.iterator();
		while (it.hasNext()) {
			Drive d = it.next();
			ServerPlayer p = d.player();
			LivingEntity t = d.target();
			if (!p.isAlive() || !t.isAlive() || p.level() != t.level()) {
				it.remove();
				continue;
			}
			ServerLevel level = p.level();
			Vec3 step = d.dir().scale(RUSH_SPEED);
			// the foe held just ahead of you, driven on
			Vec3 front = p.position().add(d.dir().scale(1.2 + t.getBbWidth() * 0.5));
			AABB next = t.getBoundingBox().move(front.x - t.getX() + step.x, 0.0, front.z - t.getZ() + step.z);
			boolean wall = !level.noCollision(t, next);
			if (wall || now >= d.until()) {
				it.remove();
				if (wall) {
					t.hurtServer(level, level.damageSources().playerAttack(p), RUSH_DAMAGE);
					rot(t, p, RUSH_STACKS);
					Fx.blast(level, t.position().add(0.0, t.getBbHeight() * 0.5, 0.0), 1.6F, DEEP);
					level.playSound(null, t.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.7F, 0.5F);
				}
				p.setDeltaMovement(Vec3.ZERO);
				p.syncVelocity = true;
				continue;
			}
			p.setDeltaMovement(step.x, Math.min(0.0, p.getDeltaMovement().y), step.z);
			p.syncVelocity = true;
			t.teleportTo(front.x + step.x, t.getY(), front.z + step.z);
			t.setDeltaMovement(step);
			t.syncVelocity = true;
			t.fallDistance = 0.0;
			level.sendParticles(Fx.miasma(COLOUR, 1.4F), t.getX(), t.getY(0.4), t.getZ(), 4, 0.3, 0.3, 0.3, 0.01);
		}
	}

	// ---- Breach and Collapse --------------------------------------------------------------------------------------------------

	/** Whether rot can eat this block (and so grow it back). */
	static boolean eats(ServerLevel level, BlockPos pos, BlockState state) {
		return !state.isAir() && !state.hasBlockEntity() && state.getFluidState().isEmpty() && state.getDestroySpeed(level, pos) >= 0.0F
			&& !state.is(Blocks.BEDROCK) && level.getWorldBorder().isWithinBounds(pos);
	}

	private static void eat(ServerLevel level, Scar scar, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!eats(level, pos, state)) {
			return;
		}
		scar.pos.add(pos.immutable());
		scar.states.add(state);
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
	}

	private static boolean breach(ServerPlayer player) {
		ServerLevel level = player.level();
		HitResult hit = player.pick(BREACH_REACH, 1.0F, false);
		if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.ash_rot.no_wall").withStyle(ChatFormatting.GRAY));
			return false;
		}
		Direction in = bhr.getDirection().getOpposite();
		Direction a = in.getAxis() == Direction.Axis.Y ? Direction.EAST : Direction.UP;
		Direction b = in.getAxis() == Direction.Axis.Y ? Direction.SOUTH : in.getClockWise();
		Scar scar = new Scar(level.dimension(), level.getGameTime() + BREACH_CLOSE);
		for (int depth = 0; depth < BREACH_DEPTH; depth++) {
			BlockPos core = bhr.getBlockPos().relative(in, depth);
			for (int i = -BREACH_HALF; i <= BREACH_HALF; i++) {
				for (int j = -BREACH_HALF; j <= BREACH_HALF; j++) {
					eat(level, scar, core.relative(a, i).relative(b, j));
				}
			}
		}
		if (scar.pos.isEmpty()) {
			return false;
		}
		SCARS.add(scar);
		keep(level);
		Vec3 mouth = Vec3.atCenterOf(bhr.getBlockPos());
		Fx.blast(level, mouth, 1.8F, COLOUR);
		level.sendParticles(Fx.miasma(COLOUR, 2.0F), mouth.x, mouth.y, mouth.z, 30, 1.0, 1.0, 1.0, 0.02);
		level.playSound(null, bhr.getBlockPos(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.BLOCKS, 1.5F, 0.4F);
		level.playSound(null, bhr.getBlockPos(), SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 1.5F, 0.5F);
		return true;
	}

	private static boolean collapse(ServerPlayer player) {
		ServerLevel level = player.level();
		HitResult hit = player.pick(COLLAPSE_REACH, 1.0F, false);
		BlockPos centre = hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK ? bhr.getBlockPos() : player.blockPosition().below();
		Scar scar = new Scar(level.dimension(), level.getGameTime() + COLLAPSE_CLOSE);
		int r = COLLAPSE_RADIUS;
		// a bowl: widest at the top, down to the depth at its heart; everything above it drops in
		for (int dy = 2; dy >= -COLLAPSE_DEPTH; dy--) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					double flat = Math.sqrt(dx * dx + dz * dz) / r;
					if (flat > 1.0) {
						continue;
					}
					double floor = -COLLAPSE_DEPTH * (1.0 - flat * flat);
					if (dy >= floor) {
						eat(level, scar, centre.offset(dx, dy, dz));
					}
				}
			}
		}
		if (scar.pos.isEmpty()) {
			return false;
		}
		SCARS.add(scar);
		keep(level);
		Vec3 at = Vec3.atCenterOf(centre);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r, COLLAPSE_DEPTH + 4, r), e -> e != player && e.isAlive())) {
			rot(e, player, COLLAPSE_STACKS);
		}
		Fx.ring(level, at.add(0.0, 1.0, 0.0), r, COLOUR);
		Fx.blast(level, at, 4.0F, DEEP);
		level.sendParticles(Fx.miasma(COLOUR, 3.0F), at.x, at.y, at.z, 80, r * 0.5, 2.0, r * 0.5, 0.03);
		level.playSound(null, centre, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2.0F, 0.4F);
		level.playSound(null, centre, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.BLOCKS, 1.5F, 0.3F);
		return true;
	}

	private static void tickScars(MinecraftServer server, long now) {
		Iterator<Scar> it = SCARS.iterator();
		while (it.hasNext()) {
			Scar scar = it.next();
			if (now < scar.closeAt) {
				continue;
			}
			ServerLevel level = server.getLevel(scar.dim);
			if (level == null) {
				it.remove();
				continue;
			}
			// grows back from the bottom up, deepest first (the order it was eaten is top-down per column, so walk it back)
			for (int i = 0; i < REGROW_PER_TICK && scar.next < scar.pos.size(); i++) {
				if (!regrow(level, scar, false)) {
					break;
				}
			}
			if (scar.next >= scar.pos.size()) {
				it.remove();
				keep(level);
			}
		}
	}

	/** Writes {@code level}'s open holes to it, so a crash doesn't leave them open for good. */
	private static void keep(ServerLevel level) {
		List<Hole> open = new ArrayList<>();
		for (Scar scar : SCARS) {
			if (scar.dim == level.dimension()) {
				open.add(new Hole(scar.pos.stream().map(BlockPos::asLong).toList(), List.copyOf(scar.states)));
			}
		}
		if (open.isEmpty()) {
			level.removeAttached(HOLES);
		} else {
			level.setAttached(HOLES, open);
		}
	}

	/** Grows back the next block of the scar (last eaten first), unless someone stands there (then it waits a tick, unless forced). */
	private static boolean regrow(ServerLevel level, Scar scar, boolean force) {
		int i = scar.pos.size() - 1 - scar.next;
		BlockPos pos = scar.pos.get(i);
		if (!force && !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos)).isEmpty()) {
			return false;
		}
		// water, lava or grass that came into the hole gives way; a block someone put there stays
		if (level.getBlockState(pos).canBeReplaced()) {
			level.setBlock(pos, scar.states.get(i), Block.UPDATE_ALL);
		}
		scar.next++;
		return true;
	}
}
