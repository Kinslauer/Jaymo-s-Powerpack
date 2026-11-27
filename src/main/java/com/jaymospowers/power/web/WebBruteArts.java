package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.BlockCluster;
import com.jaymospowers.core.Crushing;
import com.jaymospowers.core.Debris;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.UltimateFilm;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.power.gravity.GravityShockwave;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ListIterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Big-suit attacks with server-owned timing, aim and debris. */
public final class WebBruteArts {
	public static final int CLAP_WINDUP = 28, NOVA_WINDUP = 40, POUND_TICKS = 100;
	private static final int CRACK_DELAY = 10, STRIP_LENGTH = 12;
	private static final double TRACK_RANGE = 22.0, NOVA_RADIUS = 13.0;

	public enum Art {
		CLAP("clap", WebPose.BRUTE_CLAP, 65, false, false),
		RUBBLE("rubble", WebPose.BRUTE_FLIP, 75, true, false),
		POUND("pound", WebPose.BRUTE_POUND, 160, true, true),
		RAMPAGE("rampage", WebPose.BRUTE_RAMPAGE, 100, true, false),
		CRACK("crack", WebPose.BRUTE_STOMP, 90, true, false),
		NOVA("nova", WebPose.BRUTE_NOVA, 240, true, true),
		UPHEAVE("upheave", WebPose.BRUTE_FLIP, 100, true, false);

		public final String key;
		final int pose, cooldown;
		final boolean ground, full;

		Art(String key, int pose, int cooldown, boolean ground, boolean full) {
			this.key = key;
			this.pose = pose;
			this.cooldown = cooldown;
			this.ground = ground;
			this.full = full;
		}
	}

	/** Only the choice of move crosses the wire. */
	public record Cast(int art) implements CustomPacketPayload {
		public static final Type<Cast> TYPE = new Type<>(JaymosPowers.id("web_brute_art"));
		public static final StreamCodec<ByteBuf, Cast> CODEC = ByteBufCodecs.VAR_INT.map(Cast::new, Cast::art);

		@Override
		public Type<Cast> type() {
			return TYPE;
		}
	}

	private record Stone(BlockCluster body, Vec3 velocity, Art art, UUID owner, ServerLevel level) {
	}

	private static final class Run {
		final Art art;
		final ServerLevel level;
		final long start;
		final Vec3 origin;
		final Set<Integer> hit = new HashSet<>();
		final List<Stone> stones = new ArrayList<>();
		final List<BlockPos> cracks = new ArrayList<>();
		Vec3 head, direction;
		double clapRadius;
		long ticked = Long.MIN_VALUE;
		LivingEntity quarry;
		int steps;

		Run(ServerPlayer player, Art art) {
			this.art = art;
			level = player.level();
			start = level.getGameTime();
			origin = player.position();
			head = origin;
			direction = heading(player);
			quarry = aim(player, TRACK_RANGE);
		}
	}

	private static final Art[] ARTS = Art.values();
	private static final Map<UUID, Run> RUNS = new HashMap<>();
	private static final Map<UUID, Long> READY = new HashMap<>();
	private static final List<Stone> FLYING = new ArrayList<>();

	private WebBruteArts() {
	}

	static void register() {
		WebBruteMovement.register();
		PayloadTypeRegistry.serverboundPlay().register(Cast.TYPE, Cast.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Cast.TYPE, (payload, ctx) -> {
			int choice = payload.art();
			if (choice >= 0 && choice < ARTS.length) {
				start(ctx.player(), ARTS[choice]);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tick(player);
				WebBruteMovement.tick(player);
			}
			tickFlying(server);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
		net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL
			.register((player, origin, destination) -> forget(player));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				forget(player);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				forget(player);
			}
			RUNS.clear();
			READY.clear();
			WebBruteMovement.clear();
		});
	}

	static boolean able(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && !Downed.isDowned(player)
			&& Powers.has(player, WebPower.INSTANCE) && !Powers.silenced(player)
			&& WebSymbiote.bulked(player) && !WebSymbiote.driving(player);
	}

	static boolean holdsFury(ServerPlayer player) {
		Run run = RUNS.get(player.getUUID());
		return run != null && run.art.full && run.level == player.level();
	}

	static boolean busy(ServerPlayer player) {
		return RUNS.containsKey(player.getUUID());
	}

	private static void start(ServerPlayer player, Art art) {
		long now = player.level().getGameTime();
		if (!able(player) || busy(player) || now < READY.getOrDefault(player.getUUID(), 0L)
			|| art.ground && !player.onGround() || player.isPassenger() || player.isInWater()
			|| player.getAbilities().flying || WebHeldGrab.holding(player) || player.getAttached(WebBrute.MOVE) != null
			|| player.getAttached(WebSlam.STANCE) != null || player.getAttached(WebPower.CHARGING) != null
			|| WebBruteMovement.special(player) || WebPower.swingingNow(player) || art.full && WebFury.fury(player) < WebFury.MAX) {
			return;
		}
		Run run = new Run(player, art);
		RUNS.put(player.getUUID(), run);
		READY.put(player.getUUID(), now + art.cooldown);
		pose(player, art.pose, 1);
		if (art == Art.NOVA) {
			UltimateFilm.play(player, UltimateFilm.BRUTE_NOVA, NOVA_WINDUP + 5, 0.0);
		}
		run.level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_AGITATED,
			SoundSource.PLAYERS, 1.0F, 0.7F);
	}

	static void forget(ServerPlayer player) {
		finish(player);
		// stones still in the air for this player stop when they leave
		var it = FLYING.iterator();
		while (it.hasNext()) {
			Stone stone = it.next();
			if (stone.owner() != null && stone.owner().equals(player.getUUID())) {
				stone.body().discard();
				it.remove();
			}
		}
		WebBruteMovement.forget(player);
		WebSymbiote.cancelRhythm(player);
		READY.remove(player.getUUID());
	}

	private static void finish(ServerPlayer player) {
		Run run = RUNS.remove(player.getUUID());
		if (run != null) {
			for (Stone stone : run.stones) {
				stone.body().discard();
			}
			WebPose pose = player.getAttached(WebPower.POSE);
			if (pose != null && pose.kind() == run.art.pose && pose.start() == run.start) {
				player.removeAttached(WebPower.POSE);
			}
		}
	}

	private static void tick(ServerPlayer player) {
		Run run = RUNS.get(player.getUUID());
		if (run == null) {
			return;
		}
		if (!able(player) || run.level != player.level() || player.isPassenger() || player.isInWater()) {
			finish(player);
			return;
		}
		long now = run.level.getGameTime();
		if (run.ticked == now) {
			return;
		}
		run.ticked = now;
		int age = (int)(now - run.start);
		boolean keep = switch (run.art) {
			case CLAP -> clap(player, run, age);
			case RUBBLE, CRACK, UPHEAVE -> strip(player, run, age);
			case POUND -> pound(player, run, age);
			case RAMPAGE -> rampage(player, run, age);
			case NOVA -> nova(player, run, age);
		};
		if (!keep) {
			// the move's own ticks are done: hand control back, let its stones finish on their own list
			FLYING.addAll(run.stones);
			run.stones.clear();
			finish(player);
			return;
		}
		tickStones(player, run.stones);
	}

	private static boolean clap(ServerPlayer player, Run run, int age) {
		if (age == CLAP_WINDUP) {
			run.head = player.position();
			run.direction = heading(player);
			run.clapRadius = 3.0 + 2.0 * WebFury.fury(player) / WebFury.MAX;
			impact(player, run.head, 1.7F);
			if (!player.onGround() && player.getLookAngle().y < -0.55) {
				Vec3 motion = player.getDeltaMovement();
				velocity(player, new Vec3(motion.x * 0.6, 1.05, motion.z * 0.6));
			}
		}
		int spread = age - CLAP_WINDUP;
		if (spread >= 0 && spread < 8) {
			double radius = run.clapRadius * (spread + 1) / 8.0;
			for (LivingEntity foe : foes(player, run.head, radius * 2, 3.0)) {
				Vec3 offset = foe.position().subtract(run.head);
				Vec3 out = offset.horizontal().normalize();
				float force = out.dot(run.direction) > 0.65 ? 2 : 1;
				if (offset.horizontalDistance() <= radius * force && clear(run.level, player, run.head.add(0, 0.8, 0), foe.getEyePosition())
					&& run.hit.add(foe.getId())) {
					hit(player, foe, 5.0F * force, out.scale(0.65 * force).add(0, 0.23 * force, 0));
				}
			}
			if (spread % 2 == 1) {
				ring(run.level, run.head, radius);
				arc(run.level, run.head, run.direction, radius * 2);
			}
		}
		return age < WebPose.ticks(WebPose.BRUTE_CLAP);
	}

	private static boolean strip(ServerPlayer player, Run run, int age) {
		if (age < 10) {
			return anchor(player, run);
		}
		if (age == 10) {
			impact(player, run.origin, 0.95F);
			if (run.art == Art.RUBBLE) {
				WebSlam.breakGround(player, run.origin.add(run.direction.scale(1.25)), 1.25);
			}
		}
		if (run.art == Art.CRACK) {
			if (age < 10 + STRIP_LENGTH) {
				advance(player, run, 1.05, true, true);
			}
			if (age >= 10 + CRACK_DELAY) {
				int index = (age - 10 - CRACK_DELAY) * 3;
				for (int i = index; i < Math.min(index + 3, run.cracks.size()); i++) {
					erupt(player, run, run.cracks.get(i), 0.68, 7.0F, true);
				}
			}
			return age < 10 + CRACK_DELAY + STRIP_LENGTH;
		}
		int length = run.art == Art.UPHEAVE ? 15 : STRIP_LENGTH;
		if (age < 10 + length && age % 2 == 0) {
			advance(player, run, 1.8, false, run.art == Art.RUBBLE);
			if (run.art == Art.UPHEAVE) {
				WebSlam.breakGround(player, run.head, 1.0);
			}
		}
		return age < 10 + length;
	}

	private static void advance(ServerPlayer player, Run run, double stride, boolean cracks, boolean steer) {
		Vec3 dir = steer ? steer(player, run, 0.18) : run.direction;
		Vec3 next = run.head.add(dir.scale(stride));
		Vec3 from = run.head.add(0, 0.55, 0);
		Vec3 to = next.add(0, 0.55, 0);
		BlockPos middle = clear(run.level, player, from, to) ? ground(run.level, next) : null;
		if (middle == null || Math.abs(middle.getY() + 1 - run.head.y) > 1.5) {
			if (cracks) {
				// Keep one entry per lane per tick so every mark has the same delay.
				for (int lane = -1; lane <= 1; lane++) {
					run.cracks.add(null);
				}
			}
			return;
		}
		run.head = new Vec3(next.x, middle.getY() + 1, next.z);
		run.steps++;
		Vec3 cross = new Vec3(-dir.z, 0, dir.x);
		for (int lane = -1; lane <= 1; lane++) {
			double spread = cracks ? 0.55 + 0.12 * run.steps : 0.8;
			Vec3 laneAt = run.head.add(cross.scale(lane * spread));
			BlockPos ground = ground(run.level, laneAt);
			if (ground != null && Math.abs(ground.getY() + 1 - run.head.y) > 1.5) {
				ground = null;
			}
			if (cracks) {
				run.cracks.add(ground);
				if (ground != null) {
					WebCracks.crack(run.level, ground, 5);
				}
			} else if (ground != null) {
				erupt(player, run, ground, run.art == Art.UPHEAVE ? 0.88 : 0.46, 7.0F, run.art == Art.UPHEAVE);
			}
		}
		if (run.art == Art.RUBBLE) {
			AABB front = AABB.ofSize(run.head.add(0, 0.8, 0), 4.0, 2.3, 4.0);
			for (AbstractArrow arrow : run.level.getEntitiesOfClass(AbstractArrow.class, front,
				e -> e.getDeltaMovement().lengthSqr() > 0.01 && e.getOwner() != player
					&& !(e instanceof ThrownTrident))) {
				arrow.discard();
			}
		}
	}

	private static void erupt(ServerPlayer player, Run run, @Nullable BlockPos ground, double lift, float damage, boolean slab) {
		if (ground == null) {
			return;
		}
		Vec3 at = Vec3.atBottomCenterOf(ground.above());
		Debris.puff(run.level, ground, run.level.getBlockState(ground), 1.0F);
		Vec3 toss = run.direction.scale(slab ? 0.23 : 0.48);
		stone(run, player, ground, at, toss.add(0, lift, 0), slab);
		for (LivingEntity foe : foes(player, at, 1.45, 2.0)) {
			if (run.hit.add(foe.getId())) {
				hit(player, foe, damage, toss.add(0, lift, 0));
			}
		}
	}

	private static boolean pound(ServerPlayer player, Run run, int age) {
		if (age >= POUND_TICKS) {
			return false;
		}
		if (!anchor(player, run) || WebFury.fury(player) < WebFury.MAX) {
			return false;
		}
		if (age % 20 == 17) {
			boolean last = age == 97;
			impact(player, run.origin, last ? 2.0F : 1.1F);
			ring(run.level, run.origin, 5.5);
			if (age == 17) {
				WebSlam.breakGround(player, run.origin, 1.3);
			}
			for (LivingEntity foe : foes(player, run.origin, 5.5, 2.5)) {
				Vec3 inward = run.origin.subtract(foe.position()).horizontal().normalize();
				hit(player, foe, last ? 9.0F : 4.0F, inward.scale(last ? 0.0 : 0.40).add(0, last ? 1.0 : 0.32, 0));
			}
			int spawned = 0;
			for (LivingEntity foe : foes(player, run.origin, 8.0, 4.0)) {
				if (spawned >= 4) {
					break;
				}
				BlockPos ground = ground(run.level, Vec3.atBottomCenterOf(foe.blockPosition()));
				if (ground != null) {
					stone(run, player, ground, Vec3.atBottomCenterOf(ground.above()), new Vec3(0, 0.68, 0), true);
					spawned++;
				}
			}
		}
		return true;
	}

	private static boolean rampage(ServerPlayer player, Run run, int age) {
		if (age < 9) {
			return anchor(player, run);
		}
		if (age >= WebPose.ticks(WebPose.BRUTE_RAMPAGE) || player.horizontalCollision || !player.onGround()) {
			return false;
		}
		run.quarry = aim(player, TRACK_RANGE);
		Vec3 dir = steer(player, run, 0.22);
		Vec3 motion = player.getDeltaMovement();
		velocity(player, dir.scale(0.86).add(0, motion.y, 0));
		Vec3 at = player.position();
		for (LivingEntity foe : foes(player, at.add(dir.scale(0.7)), 2.0, 1.75)) {
			if (player.hasLineOfSight(foe) && run.hit.add(foe.getId())) {
				Vec3 side = new Vec3(-dir.z, 0, dir.x);
				double sign = foe.position().subtract(at).dot(side) >= 0 ? 1 : -1;
				hit(player, foe, 9.0F, side.scale(sign * 0.48).add(dir.scale(0.24)).add(0, 0.28, 0));
			}
		}
		if (age % 5 == 0) {
			dust(run.level, at, 7, 0.55);
			Shakes.heavy(player, at, 10, 0.35F);
		}
		return true;
	}

	private static boolean nova(ServerPlayer player, Run run, int age) {
		if (age < NOVA_WINDUP) {
			if (!anchor(player, run) || WebFury.fury(player) < WebFury.MAX) {
				return false;
			}
			if (age % 5 == 0) {
				ring(run.level, run.origin, 2 + age * 0.12);
				Shakes.heavy(player, run.origin, 10, 0.25F + 0.013F * age);
			}
			return true;
		}
		if (!WebFury.spend(player, WebFury.MAX)) {
			return false;
		}
		Vec3 at = run.origin;
		for (LivingEntity foe : foes(player, at, NOVA_RADIUS, 6.0)) {
			if (player.hasLineOfSight(foe)) {
				Vec3 out = foe.position().subtract(at);
				float strength = (float)(1.0 - 0.65 * Math.min(1.0, out.length() / NOVA_RADIUS));
				hit(player, foe, 23.0F * strength, out.horizontal().normalize().scale(2.3 * strength).add(0, 0.9 * strength, 0));
			}
		}
		WebSlam.breakGround(player, at, 1.8);
		ring(run.level, at, NOVA_RADIUS);
		impact(player, at, 3.0F);
		WebFury.spend(player, WebFury.fury(player));
		WebSymbiote.exhaustBulk(player);
		return false;
	}

	private static boolean anchor(ServerPlayer player, Run run) {
		if (!player.onGround() || player.position().distanceToSqr(run.origin) > 4.0) {
			return false;
		}
		Vec3 motion = player.getDeltaMovement();
		velocity(player, new Vec3(0, motion.y, 0));
		return true;
	}

	private static void stone(Run run, ServerPlayer player, BlockPos ground, Vec3 at, Vec3 motion, boolean slab) {
		var material = run.level.getBlockState(ground);
		if (material.hasBlockEntity() || material.isAir() || !material.getFluidState().isEmpty()) {
			return;
		}
		BlockCluster body = BlockCluster.made(run.level, slab ? List.of(material, material, material) : List.of(material), at);
		body.setAttached(BlockCluster.SCALE, slab ? 0.62F : 0.48F);
		body.refreshBox();
		body.script(true, false);
		body.linger(45, true);
		run.stones.add(new Stone(body, motion, run.art, player.getUUID(), run.level));
	}

	private static @Nullable Stone tickStone(ServerPlayer player, Stone stone) {
		BlockCluster body = stone.body();
		if (body.isRemoved()) {
			return null;
		}
		if (!(body.level() instanceof ServerLevel level)) {
			body.discard();
			return null;
		}
		// the owner changed dimension: the stone is in the old world, drop it
		if (player.level() != level) {
			body.discard();
			return null;
		}
		Vec3 to = body.position().add(stone.velocity());
		if (!level.hasChunkAt(BlockPos.containing(to))
			|| !level.noCollision(body, AABB.ofSize(to.add(0, 0.35, 0), 0.38, 0.6, 0.38))) {
			if (stone.art() == Art.POUND && stone.velocity().y < 0) {
				for (LivingEntity foe : foes(player, body.position(), 1.5, 2.0)) {
					hit(player, foe, 5.0F, new Vec3(0, 0.25, 0));
				}
			}
			body.discard();
			return null;
		}
		body.setPos(to.x, to.y, to.z);
		return new Stone(body, stone.velocity().add(0, -0.085, 0), stone.art(), stone.owner(), stone.level());
	}
	private static void tickStones(ServerPlayer player, List<Stone> stones) {
		ListIterator<Stone> iterator = stones.listIterator();
		while (iterator.hasNext()) {
			Stone ticked = tickStone(player, iterator.next());
			if (ticked == null) {
				iterator.remove();
			} else {
				iterator.set(ticked);
			}
		}
	}

	private static boolean clear(ServerLevel level, Entity actor, Vec3 from, Vec3 to) {
		return level.hasChunkAt(BlockPos.containing(to))
			&& level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor)).getType()
				== HitResult.Type.MISS;
	}

	private static void tickFlying(MinecraftServer server) {
		java.util.ListIterator<Stone> it = FLYING.listIterator();
		while (it.hasNext()) {
			Stone stone = it.next();
			ServerPlayer owner = stone.owner() == null ? null : server.getPlayerList().getPlayer(stone.owner());
			if (owner == null) {
				stone.body().discard();
				it.remove();
				continue;
			}
			Stone ticked = tickStone(owner, stone);
			if (ticked == null) {
				it.remove();
			} else {
				it.set(ticked);
			}
		}
	}

	static @Nullable BlockPos ground(ServerLevel level, Vec3 at) {
		BlockPos pos = BlockPos.containing(at);
		return level.hasChunkAt(pos) ? GravityShockwave.ground(level, at.x, at.y, at.z) : null;
	}

	static Vec3 heading(ServerPlayer player) {
		double yaw = Math.toRadians(player.getYRot());
		return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
	}

	static @Nullable LivingEntity aim(ServerPlayer player, double range) {
		Vec3 eye = player.getEyePosition(), end = eye.add(player.getLookAngle().scale(range));
		double closest = range * range;
		LivingEntity result = null;
		for (LivingEntity foe : foes(player, eye, range, range)) {
			var clip = foe.getBoundingBox().inflate(0.45).clip(eye, end);
			if (clip.isPresent() && eye.distanceToSqr(clip.get()) < closest && player.hasLineOfSight(foe)) {
				result = foe;
				closest = eye.distanceToSqr(clip.get());
			}
		}
		return result;
	}

	private static Vec3 steer(ServerPlayer player, Run run, double turn) {
		LivingEntity target = run.quarry;
		Vec3 wish = target != null && target.isAlive() && target.level() == run.level
			&& target.distanceToSqr(player) <= TRACK_RANGE * TRACK_RANGE
			? target.position().subtract(run.head).horizontal().normalize() : heading(player);
		run.direction = run.direction.lerp(wish, turn).normalize();
		if (run.art == Art.RAMPAGE) {
			run.head = player.position();
		}
		return run.direction;
	}

	static List<LivingEntity> foes(ServerPlayer player, Vec3 at, double radius, double height) {
		return player.level().getEntitiesOfClass(LivingEntity.class, AABB.ofSize(at, radius * 2, height * 2, radius * 2),
			e -> e != player && e.isAlive() && !e.isSpectator() && !player.isAlliedTo(e)
				&& !(e instanceof Player other && !player.canHarmPlayer(other))
				&& !(e instanceof TamableAnimal tame && tame.isOwnedBy(player))
				&& e.position().subtract(at).horizontalDistanceSqr() <= radius * radius);
	}

	static void hit(ServerPlayer player, LivingEntity foe, float damage, Vec3 shove) {
		if (foe.hurtServer(player.level(), player.damageSources().playerAttack(player), damage)) {
			Crushing.mark(foe);
			shove(foe, foe.getDeltaMovement().add(shove));
		}
	}

	/** Moves a foe (the move's hit still lands): anything too big or a boss to haul is left where it stands. */
	static void shove(Entity foe, Vec3 motion) {
		if (WebHaul.haulable(foe)) {
			velocity(foe, motion);
		}
	}

	static void velocity(Entity entity, Vec3 motion) {
		entity.setDeltaMovement(motion);
		entity.needsSync = true;
		entity.syncVelocity = true;
		if (entity instanceof ServerPlayer player) {
			ServerPlayNetworking.send(player, new WebNet.Nudge(motion));
		}
	}

	static void pose(ServerPlayer player, int kind, int side) {
		player.setAttached(WebPower.POSE, new WebPose(kind, player.level().getGameTime(), side));
	}

	static void dust(ServerLevel level, Vec3 at, int count, double spread) {
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.2, at.z, count, spread, 0.13, spread, 0.015);
	}

	static void ring(ServerLevel level, Vec3 at, double radius) {
		for (int i = 0; i < 24; i++) {
			double angle = i * Math.PI / 12.0;
			dust(level, at.add(radius * Math.cos(angle), 0, radius * Math.sin(angle)), 1, 0.12);
		}
	}

	private static void arc(ServerLevel level, Vec3 at, Vec3 forward, double radius) {
		for (int i = -5; i <= 5; i++) {
			dust(level, at.add(forward.yRot(i * 0.16F).scale(radius)), 2, 0.16);
		}
	}

	static void impact(ServerPlayer player, Vec3 at, float shake) {
		player.level().playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.25F, 0.75F);
		Shakes.heavy(player, at, 18.0, shake);
	}

	public static float damageScale(LivingEntity entity) {
		if (!(entity instanceof ServerPlayer player) || !able(player)) {
			return 1.0F;
		}
		Run run = RUNS.get(player.getUUID());
		return run != null && run.art == Art.RAMPAGE && run.level == player.level() ? 0.28F : WebBruteMovement.brace(player);
	}
}
