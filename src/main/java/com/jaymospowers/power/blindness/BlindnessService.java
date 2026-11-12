package com.jaymospowers.power.blindness;

import com.jaymospowers.core.BladeBlood;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-11): "you should be able to stumble on a service of the members performing this on one of their
 * followers." Now and then, out in the wild at night, a survival player comes on the Unseeing ({@link Unseeing}) at
 * their service: a ring of {@link #MEMBERS} blindfolded members round one of their followers, four candles lit at the
 * corners. They chant; then one of the two rites (Andy: "they do the blindfold ritual, but they also do the eye
 * gouging"): the eldest binds the follower's eyes with a blindfold, or carves them out (blood, a cry) and binds the
 * sockets with linen. The follower, now one of them, thanks them. A watcher close by is left a blindfold "for when you
 * are ready". Then the Unseeing go about their nonsense round the spot. The candles stay where they burned.
 * <p>At most once every {@link #GAP_DAYS} days for each player ({@link #LAST}, not saved), and only when no one is near
 * a village. Anyone can break it up: one of them killed ends the service where it stands. A service the server stopped
 * in the middle of is let go as it loads back ({@link #release}).
 */
public final class BlindnessService {
	/** How often (ticks) each player is checked, and the odds a check at night out in the wild starts a service. */
	static final int CHECK_EVERY = 200;
	static final float CHANCE = 0.004F;
	/** In-game days between one player's services. */
	static final int GAP_DAYS = 3;
	/** How far off it is set up (blocks), how many members stand round the follower and how far out. */
	static final int NEAR = 22, FAR = 32, MEMBERS = 3;
	static final double RING = 2.0;
	/** How far from their spot the Unseeing roam after. */
	static final int HOME_RANGE = 12;
	/** Who hears it (blocks), who counts as watching when it ends (and gets the blindfold). */
	static final double HEAR = 40.0, WATCH = 20.0;
	/** The service's clock (ticks): a chant line every CHANT ticks, the cut, the binding, the end, and when the frozen members walk off. */
	static final int CHANT = 120, CHANTS = 3, CUT_AT = CHANT * (CHANTS + 1), BIND_AT = CUT_AT + 80, END_AT = BIND_AT + 100;
	/** Tags on the service's people (the follower also gets {@link #FOLLOWER_TAG}). */
	/** The eldest's hush over a service: how far it reaches (blocks), how often its ring shows (ticks), its colour. */
	static final double HUSH_RADIUS = 10.0;
	static final int HUSH_SHOW_EVERY = 60, HUSH_COLOR = 0x8C8C9A;
	static final String TAG = "jaymospowers_unseeing_service", FOLLOWER_TAG = "jaymospowers_unseeing_follower";

	private static final List<Service> SERVICES = new ArrayList<>();
	private static final Map<UUID, Long> LAST = new HashMap<>();
	/** True while a service is being set up (its people load before it's listed). */
	private static boolean starting;

	private BlindnessService() {
	}

	private static final class Service {
		final ServerLevel level;
		final BlockPos centre;
		final List<Unseeing> members;
		final Unseeing follower;
		final boolean gouging;
		int age;

		Service(ServerLevel level, BlockPos centre, List<Unseeing> members, Unseeing follower, boolean gouging) {
			this.gouging = gouging;
			this.level = level;
			this.centre = centre;
			this.members = members;
			this.follower = follower;
		}

		Unseeing elder() {
			return this.members.get(0);
		}
	}

	static void init() {
		com.jaymospowers.core.Powers.addSilencer(BlindnessService::hushed);
		ServerTickEvents.END_SERVER_TICK.register(BlindnessService::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (!starting && entity instanceof Unseeing villager && villager.entityTags().contains(TAG)
				&& SERVICES.stream().noneMatch(s -> s.members.contains(villager) || s.follower == villager)) {
				release(villager);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Service service : SERVICES) {
				service.members.forEach(BlindnessService::release);
				release(service.follower);
			}
			SERVICES.clear();
			LAST.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Service> it = SERVICES.iterator(); it.hasNext(); ) {
			Service service = it.next();
			if (!step(service)) {
				service.members.forEach(BlindnessService::release);
				release(service.follower);
				it.remove();
			}
		}
		if (server.getTickCount() % CHECK_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.level();
			if (player.isCreative() || player.isSpectator() || level.dimension() != Level.OVERWORLD || !level.isDarkOutside()
				|| level.isCloseToVillage(player.blockPosition(), 4) || level.getRandom().nextFloat() >= CHANCE) {
				continue;
			}
			long day = level.getOverworldClockTime() / 24000L;
			Long last = LAST.get(player.getUUID());
			if (last != null && day - last < GAP_DAYS) {
				continue;
			}
			if (start(level, player)) {
				LAST.put(player.getUUID(), day);
			}
		}
	}

	/** Sets up a service {@link #NEAR}-{@link #FAR} blocks from {@code player}, on open ground. False if there's nowhere. */
	static boolean start(ServerLevel level, ServerPlayer player) {
		starting = true;
		try {
			return setUp(level, player);
		} finally {
			starting = false;
		}
	}

	private static boolean setUp(ServerLevel level, ServerPlayer player) {
		RandomSource random = level.getRandom();
		for (int tries = 0; tries < 8; tries++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = NEAR + random.nextDouble() * (FAR - NEAR);
			int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
			int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
			BlockPos centre = ground(level, x, z);
			if (centre == null) {
				continue;
			}
			List<BlockPos> spots = new ArrayList<>();
			for (int i = 0; i < MEMBERS; i++) {
				double a = Math.PI * 2.0 * i / MEMBERS;
				BlockPos spot = ground(level, Mth.floor(centre.getX() + 0.5 + Math.cos(a) * RING), Mth.floor(centre.getZ() + 0.5 + Math.sin(a) * RING));
				if (spot == null || Math.abs(spot.getY() - centre.getY()) > 1) {
					spots = null;
					break;
				}
				spots.add(spot);
			}
			if (spots == null) {
				continue;
			}
			Unseeing follower = person(level, centre, centre, Unseeing.Look.FOLLOWER);
			if (follower == null) {
				return false;
			}
			follower.addTag(FOLLOWER_TAG);
			List<Unseeing> members = new ArrayList<>();
			for (BlockPos spot : spots) {
				Unseeing member = person(level, spot, centre, Unseeing.Look.BLINDFOLD);
				if (member != null) {
					member.setElder(members.isEmpty());
					members.add(member);
				}
			}
			if (members.isEmpty()) {
				follower.discard();
				return false;
			}
			for (int[] corner : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
				candle(level, centre.offset(corner[0], 0, corner[1]));
			}
			SERVICES.add(new Service(level, centre, members, follower, random.nextBoolean()));
			return true;
		}
		return false;
	}

	/** One tick of a service; false when it's over (finished, or broken up). */
	/** Whether {@code player} stands on a service's hushed ground: the eldest keeps it silent, and no power answers there. */
	public static boolean hushed(ServerPlayer player) {
		for (Service service : SERVICES) {
			if (service.level == player.level() && service.elder().isAlive()
				&& player.distanceToSqr(Vec3.atCenterOf(service.centre)) <= HUSH_RADIUS * HUSH_RADIUS) {
				return true;
			}
		}
		return false;
	}

	private static boolean step(Service service) {
		if (!service.follower.isAlive() || service.members.stream().anyMatch(m -> !m.isAlive())) {
			if (service.age < CUT_AT && service.follower.isAlive()) {
				say(service, service.follower, "message.jaymospowers.blindness.service.scattered");
			}
			return false;
		}
		int age = service.age++;
		if (age % HUSH_SHOW_EVERY == 0) {
			// the hush, shown: a grey ring running out over the ground to its edge
			com.jaymospowers.core.fx.MagicFx.ring(service.level, Vec3.atBottomCenterOf(service.centre).add(0.0, 0.1, 0.0), (float) HUSH_RADIUS, HUSH_COLOR);
		}
		ServerLevel level = service.level;
		Unseeing elder = service.elder();
		if (age > 0 && age % CHANT == 0 && age / CHANT <= CHANTS) {
			say(service, service.members.get((age / CHANT - 1) % service.members.size()), "message.jaymospowers.blindness.service.chant." + age / CHANT);
			level.playSound(null, service.centre, SoundEvents.AMBIENT_CAVE.value(), SoundSource.NEUTRAL, 0.6F, 0.6F);
		}
		if (age == CUT_AT) {
			say(service, elder, service.gouging ? "message.jaymospowers.blindness.service.cut" : "message.jaymospowers.blindness.service.cover");
			if (service.gouging) {
				level.playSound(null, service.follower.getX(), service.follower.getY(), service.follower.getZ(), SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 1.0F, 0.6F);
				level.playSound(null, service.follower.getX(), service.follower.getY(), service.follower.getZ(), SoundEvents.VILLAGER_HURT, SoundSource.NEUTRAL, 1.0F, 0.7F);
			}
		}
		if (service.gouging && age >= CUT_AT && age < BIND_AT && age % 10 == 0) {
			BladeBlood.drip(level, service.follower, 0.85, true);
		}
		if (age == BIND_AT) {
			service.follower.setLook(service.gouging ? Unseeing.Look.GOUGED : Unseeing.Look.BLINDFOLD);
			say(service, elder, service.gouging ? "message.jaymospowers.blindness.service.bind" : "message.jaymospowers.blindness.service.bound");
			level.playSound(null, service.centre, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.NEUTRAL, 1.0F, 0.8F);
		}
		if (age == BIND_AT + 50) {
			say(service, service.follower, service.gouging ? "message.jaymospowers.blindness.service.thanks" : "message.jaymospowers.blindness.service.thanks_bound");
		}
		if (age == END_AT) {
			ServerPlayer watcher = level.getNearestPlayer(service.centre.getX() + 0.5, service.centre.getY(), service.centre.getZ() + 0.5, WATCH, false)
				instanceof ServerPlayer p ? p : null;
			if (watcher != null && !watcher.isSpectator()) {
				say(service, elder, "message.jaymospowers.blindness.service.gift");
				ItemEntity gift = new ItemEntity(level, elder.getX(), elder.getY() + 0.5, elder.getZ(), new ItemStack(BlindnessDevotion.BLINDFOLD));
				Vec3 toward = watcher.position().subtract(elder.position()).normalize().scale(0.25);
				gift.setDeltaMovement(toward.x, 0.2, toward.z);
				level.addFreshEntity(gift);
			}
			return false;
		}
		return true;
	}

	/** A line from {@code speaker} to everyone within {@link #HEAR} blocks. */
	private static void say(Service service, Unseeing speaker, String key) {
		Component line = Component.translatable("message.jaymospowers.blindness.service.says", speaker.getDisplayName(), Component.translatable(key))
			.withStyle(ChatFormatting.GRAY);
		for (ServerPlayer player : service.level.getEntitiesOfClass(ServerPlayer.class, new AABB(service.centre).inflate(HEAR))) {
			player.sendSystemMessage(line);
		}
	}

	/** A still one of the Unseeing standing at {@code at}, facing {@code centre}, with {@code look}. */
	static @Nullable Unseeing person(ServerLevel level, BlockPos at, BlockPos centre, Unseeing.Look look) {
		Unseeing one = place(level, at, centre, look);
		if (one != null) {
			one.setNoAi(true);
			one.addTag(TAG);
		}
		return one;
	}

	/** One of the Unseeing at {@code at}, facing {@code centre}, with {@code look}, kept near {@code centre}. */
	static @Nullable Unseeing place(ServerLevel level, BlockPos at, BlockPos centre, Unseeing.Look look) {
		Unseeing one = Unseeing.TYPE.create(level, EntitySpawnReason.EVENT);
		if (one == null) {
			return null;
		}
		double dx = centre.getX() - at.getX(), dz = centre.getZ() - at.getZ();
		float yaw = dx == 0 && dz == 0 ? level.getRandom().nextFloat() * 360.0F : (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		one.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
		one.setYHeadRot(yaw);
		one.setYBodyRot(yaw);
		one.setLook(look);
		one.setHomeTo(centre, HOME_RANGE);
		level.addFreshEntity(one);
		return one;
	}

	/** Lets a service's person go about their own business round the spot. */
	private static void release(Unseeing one) {
		one.setNoAi(false);
		one.removeTag(TAG);
		one.removeTag(FOLLOWER_TAG);
	}

	/** Open ground at x, z: the top block is solid, the two over it are clear and it isn't water. Returns the standing spot. */
	static @Nullable BlockPos ground(ServerLevel level, int x, int z) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos at = new BlockPos(x, y, z);
		BlockState below = level.getBlockState(at.below());
		if (!below.isFaceSturdy(level, at.below(), net.minecraft.core.Direction.UP) || !below.getFluidState().isEmpty()
			|| !level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir()) {
			return null;
		}
		return at;
	}

	/** A lit candle on the ground near {@code near}, if there's room. */
	static void candle(ServerLevel level, BlockPos near) {
		BlockPos at = ground(level, near.getX(), near.getZ());
		if (at != null && Math.abs(at.getY() - near.getY()) <= 2) {
			level.setBlock(at, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true), 3);
		}
	}
}
