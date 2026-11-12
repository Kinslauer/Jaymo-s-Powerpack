package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Carry;
import com.jaymospowers.core.wounds.Downed;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.lang.reflect.Method;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Escort jobs, a fourth kind of side job ({@link ZFighterSideQuests#ESCORT}). Andy (2026-11-12) asked for escort jobs
 * ("propose some"); these are the four proposed:
 * <ul>
 * <li><b>The courier</b> ({@link #COURIER}): walk a courier to the next town. Partway there the Ash Rival's lackeys jump
 * you both ({@link #LACKEYS}).</li>
 * <li><b>The vigil</b> ({@link #VIGIL}): take a master's pupil to the nearest Aether monolith (with Jaymo's Kingdoms; else
 * the Tower) and keep them alive through the night while they climb and meditate at its foot; the night throws its
 * monsters at them. Done at dawn.</li>
 * <li><b>The caravan</b> ({@link #CARAVAN}): see a trader and her pack mule to the next town, past a Giant asleep beside
 * the road. Sprint near it, strike it or let it be struck, and it wakes.</li>
 * <li><b>The fallen</b> ({@link #FALLEN}): a fighter lies downed out in the wild; carry them back to the master who sent
 * you (Powers' carrying), and the master brings them round.</li>
 * </ul>
 * The ward is an MCA villager (Kingdoms' {@code ZFighterFolk}, the {@link #WARD_TAG} part: Powers walks them), so the jobs
 * need Jaymo's Kingdoms and MCA; without them none is offered. A ward lost (dead, or out of sight for
 * {@link #LOST_TICKS}) or a job not done in {@link #DAYS} days fails it. One escort at a time.
 */
public final class ZFighterEscorts {
	public static final int COURIER = 0, VIGIL = 1, CARAVAN = 2, FALLEN = 3;
	public static final int VARIANTS = 4;

	// ---- tuning ----
	/** Towns that count as the next one: at least this far, at most that (blocks). */
	static final double TOWN_MIN = 150.0, TOWN_MAX = 1500.0;
	/** A monolith (or the Tower) for the vigil, within this (blocks). */
	static final int MONOLITH_RANGE = 1200;
	/** The ward keeps within this of you (blocks), and is "falling behind" past that. */
	static final double FOLLOW = 3.0, BEHIND = 40.0;
	static final double WALK_SPEED = 0.75;
	/** Done when the ward is this near the end (blocks); the fallen, this near the master. */
	static final double ARRIVED = 20.0, HANDED_OVER = 6.0;
	/** The lackeys jump you this far into the walk (share of the way), how many, and their health. */
	static final float AMBUSH_AT = 0.45F;
	static final int LACKEYS = 2;
	static final double LACKEY_HEALTH = 30.0;
	/** The vigil: monsters come in waves this far apart (ticks), so many a wave, this far out (blocks). */
	static final int WAVE_TICKS = 500, WAVE_SIZE = 3;
	static final int WAVE_RANGE = 14;
	/** The sleeping Giant lies this far off the middle of the road (blocks), and wakes to a sprint within that. */
	static final int GIANT_OFF = 28;
	static final double GIANT_WAKES = 22.0;
	/** The fallen fighter lies this far out (blocks). */
	static final int FALLEN_MIN = 80, FALLEN_MAX = 160;
	/** Ticks out of sight before a ward is lost, and game days to do the job in. */
	static final int LOST_TICKS = 1200, DAYS = 3;
	static final int CHECK_TICKS = 10;

	public static final String WARD_TAG = "jaymospowers_escort_ward", GIANT_TAG = "jaymospowers_escort_giant", LACKEY_TAG = "jaymospowers_escort_lackey";
	static final String[] WARD_NAMES = {"entity.jaymospowers.escort.courier", "entity.jaymospowers.escort.pupil", "entity.jaymospowers.escort.trader",
		"entity.jaymospowers.escort.fallen"};

	/**
	 * The escort under way: which, the ward, where it ends, when it began, how far it was at the start (blocks), what has
	 * happened ({@link #AMBUSHED}, {@link #VIGIL_ON}, {@link #GIANT_LAID}), the sleeping Giant (if any), the mule (if any).
	 */
	public record Escort(int variant, UUID ward, BlockPos dest, long started, int span, int stage, java.util.Optional<UUID> giant,
		java.util.Optional<UUID> mule) {
		public static final Codec<Escort> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("variant").forGetter(Escort::variant),
			UUIDUtil.CODEC.fieldOf("ward").forGetter(Escort::ward),
			BlockPos.CODEC.fieldOf("dest").forGetter(Escort::dest),
			Codec.LONG.fieldOf("started").forGetter(Escort::started),
			Codec.INT.optionalFieldOf("span", 0).forGetter(Escort::span),
			Codec.INT.optionalFieldOf("stage", 0).forGetter(Escort::stage),
			UUIDUtil.CODEC.optionalFieldOf("giant").forGetter(Escort::giant),
			UUIDUtil.CODEC.optionalFieldOf("mule").forGetter(Escort::mule)).apply(i, Escort::new));

		Escort with(int stage) {
			return new Escort(variant, ward, dest, started, span, stage, giant, mule);
		}
	}

	static final int AMBUSHED = 1, VIGIL_ON = 2, GIANT_LAID = 4;

	public static final AttachmentType<Escort> ESCORT = AttachmentRegistry.create(JaymosPowers.id("zfighter_escort"),
		builder -> builder.persistent(Escort.CODEC).copyOnDeath());

	private static final java.util.Map<UUID, Integer> UNSEEN = new java.util.HashMap<>();
	private static final java.util.Map<UUID, Long> NEXT_WAVE = new java.util.HashMap<>();

	private static boolean lookedForMonoliths;
	private static @Nullable Method monolithFoot;

	private ZFighterEscorts() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterEscorts::tick);
		// a sleeping Giant struck (by anyone) wakes
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof Mob giant && giant.entityTags().contains(GIANT_TAG) && giant.isNoAi()) {
				wake(giant, source.getEntity() instanceof LivingEntity by ? by : null);
			}
		});
	}

	/** Whether escort jobs can be had here (the wards are MCA villagers, through Jaymo's Kingdoms). */
	static boolean available(ServerPlayer player) {
		return ZFighterNpcs.villagers() && !player.hasAttached(ESCORT);
	}

	/**
	 * Sets escort {@code variant} going from {@code giver}: the ward is put beside you (or, for the fallen, out in the
	 * wild). False if there's nowhere to take them (no town far enough, no monolith or Tower).
	 */
	static boolean begin(ServerPlayer player, Entity giver, int variant) {
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		BlockPos dest;
		Vec3 wardAt = player.position().add(random.nextDouble() * 2.0 - 1.0, 0.0, random.nextDouble() * 2.0 - 1.0);
		switch (variant) {
			case COURIER, CARAVAN -> dest = town(player);
			case VIGIL -> dest = monolith(level, player.blockPosition());
			default -> {
				dest = giver.blockPosition();
				double angle = random.nextDouble() * Mth.TWO_PI;
				int far = FALLEN_MIN + random.nextInt(FALLEN_MAX - FALLEN_MIN + 1);
				BlockPos column = BlockPos.containing(dest.getX() + Math.cos(angle) * far, 0, dest.getZ() + Math.sin(angle) * far);
				wardAt = Vec3.atBottomCenterOf(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column));
			}
		}
		if (dest == null) {
			return false;
		}
		boolean female = variant == CARAVAN || random.nextBoolean();
		LivingEntity ward = ZFighterNpcs.villager(level, wardAt, player.getYRot(), WARD_TAG, WARD_NAMES[variant], female);
		if (ward == null) {
			return false;
		}
		if (ward instanceof Mob mob) {
			mob.setPersistenceRequired();
		}
		java.util.Optional<UUID> mule = java.util.Optional.empty();
		if (variant == CARAVAN) {
			var pack = EntityTypes.MULE.create(level, EntitySpawnReason.EVENT);
			if (pack != null) {
				pack.snapTo(wardAt.x + 1.5, wardAt.y, wardAt.z, 0.0F, 0.0F);
				pack.setTamed(true);
				pack.setChest(true);
				pack.setPersistenceRequired();
				pack.addTag(WARD_TAG);
				level.addFreshEntity(pack);
				pack.setLeashedTo(ward, true);
				mule = java.util.Optional.of(pack.getUUID());
			}
		}
		if (variant == FALLEN) {
			// laid out where they fell: a long while out cold, so they don't wander off before you come
			Downed.knockOut(level, ward, 24000 * DAYS, false, "message.jaymospowers.downed.knocked_out");
			player.sendSystemMessage(Component.translatable("message.jaymospowers.escort.fallen_at", giver.getDisplayName(),
				BlockPos.containing(wardAt).getX(), BlockPos.containing(wardAt).getZ()).withStyle(ChatFormatting.AQUA));
		} else {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.escort.where." + variant, dest.getX(), dest.getZ())
				.withStyle(ChatFormatting.AQUA));
		}
		int span = (int)Math.sqrt(dest.distSqr(BlockPos.containing(wardAt)));
		player.setAttached(ESCORT, new Escort(variant, ward.getUUID(), dest, level.getGameTime(), span, 0, java.util.Optional.empty(), mule));
		return true;
	}

	/** The nearest town (a realm or village of Jaymo's Kingdoms) at a walk's distance, or null. */
	private static @Nullable BlockPos town(ServerPlayer player) {
		if (!ZFighterKingdoms.present()) {
			return null;
		}
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (ZFighterKingdoms.Settlement s : ZFighterKingdoms.settlements(player.level().getServer())) {
			double d = Math.sqrt(s.seat().distSqr(player.blockPosition()));
			if (d >= TOWN_MIN && d <= TOWN_MAX && d < bestD) {
				bestD = d;
				best = s.seat();
			}
		}
		return best;
	}

	/** The nearest Aether monolith's foot (Jaymo's Kingdoms' {@code Monoliths.nearestFoot}), or else the Tower, or null. */
	private static @Nullable BlockPos monolith(ServerLevel level, BlockPos from) {
		if (!lookedForMonoliths) {
			lookedForMonoliths = true;
			if (FabricLoader.getInstance().isModLoaded("jaymoskingdoms")) {
				try {
					monolithFoot = Class.forName("com.jaymoskingdoms.remnants.Monoliths").getMethod("nearestFoot", ServerLevel.class, BlockPos.class);
				} catch (ReflectiveOperationException | LinkageError e) {
					monolithFoot = null;
				}
			}
		}
		if (monolithFoot != null) {
			try {
				BlockPos foot = (BlockPos)monolithFoot.invoke(null, level, from);
				if (foot != null && foot.distSqr(from) <= (double)MONOLITH_RANGE * MONOLITH_RANGE) {
					return foot;
				}
			} catch (ReflectiveOperationException | RuntimeException e) {
				// fall back on the Tower
			}
		}
		ZFighterSites.Data sites = ZFighterSites.data(level.getServer());
		return sites != null && sites.tower().isPresent() && sites.tower().get().distSqr(from) <= (double)MONOLITH_RANGE * MONOLITH_RANGE
			? sites.tower().get() : null;
	}

	// ---------------------------------------------------------------- the walk

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Escort e = player.getAttached(ESCORT);
			if (e != null) {
				step(player, e);
			}
		}
	}

	private static void step(ServerPlayer player, Escort e) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (now - e.started() > 24000L * DAYS) {
			end(player, e, false, "message.jaymospowers.escort.too_long");
			return;
		}
		Entity found = level.getEntity(e.ward());
		if (!(found instanceof LivingEntity ward) || !ward.isAlive()) {
			if (found != null) {
				end(player, e, false, "message.jaymospowers.escort.ward_died");
				return;
			}
			int unseen = UNSEEN.merge(player.getUUID(), CHECK_TICKS, Integer::sum);
			if (unseen >= LOST_TICKS) {
				end(player, e, false, "message.jaymospowers.escort.lost");
			}
			return;
		}
		UNSEEN.remove(player.getUUID());
		double toDest = Math.sqrt(ward.blockPosition().distSqr(e.dest()));
		switch (e.variant()) {
			case COURIER -> {
				follow(ward, player);
				if ((e.stage() & AMBUSHED) == 0 && e.span() > 0 && toDest <= e.span() * (1.0F - AMBUSH_AT)) {
					ambush(level, player, ward);
					player.setAttached(ESCORT, e = e.with(e.stage() | AMBUSHED));
				}
				if (toDest <= ARRIVED) {
					end(player, e, true, "message.jaymospowers.escort.done.0");
				}
			}
			case VIGIL -> vigil(level, player, ward, e, toDest, now);
			case CARAVAN -> {
				follow(ward, player);
				caravan(level, player, ward, e);
				if (toDest <= ARRIVED) {
					end(player, e, true, "message.jaymospowers.escort.done.2");
				}
			}
			default -> {
				// carried home: they're handed over once they lie (or are held) by the master
				net.minecraft.world.entity.player.Player carrier = Carry.carrierOf(ward);
				if (toDest <= HANDED_OVER && (carrier == null || carrier == player)) {
					Downed.rouse(level, ward, 0.5F);
					end(player, e, true, "message.jaymospowers.escort.done.3");
				}
			}
		}
	}

	private static void follow(LivingEntity ward, ServerPlayer player) {
		if (!(ward instanceof PathfinderMob mob) || Downed.isDowned(ward) || Carry.isCarried(ward)) {
			return;
		}
		double d = mob.distanceTo(player);
		if (d > BEHIND) {
			mob.getNavigation().stop();
			if (player.tickCount % 200 < CHECK_TICKS) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.escort.behind", ward.getDisplayName()).withStyle(ChatFormatting.GOLD));
			}
		} else if (d > FOLLOW) {
			mob.getNavigation().moveTo(player, WALK_SPEED + (d > FOLLOW * 4 ? 0.3 : 0.0));
		} else {
			mob.getNavigation().stop();
			mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
		}
	}

	/** The Ash Rival's lackeys come out of hiding behind you both. */
	private static void ambush(ServerLevel level, ServerPlayer player, LivingEntity ward) {
		if (ZFighterRaids.ROGUE == null) {
			return;
		}
		Vec3 back = player.getLookAngle().multiply(-1.0, 0.0, -1.0).normalize().scale(10.0);
		for (int i = 0; i < LACKEYS; i++) {
			ZFighterRogue lackey = ZFighterRaids.ROGUE.create(level, EntitySpawnReason.EVENT);
			if (lackey == null) {
				continue;
			}
			Vec3 at = ward.position().add(back).add((i - 0.5) * 4.0, 0.0, 0.0);
			BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
			lackey.snapTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, player.getYRot(), 0.0F);
			lackey.setCustomName(Component.translatable("entity.jaymospowers.escort.lackey"));
			lackey.addTag(LACKEY_TAG);
			var health = lackey.getAttribute(Attributes.MAX_HEALTH);
			if (health != null) {
				health.setBaseValue(LACKEY_HEALTH);
			}
			lackey.setHealth((float)LACKEY_HEALTH);
			level.addFreshEntity(lackey);
			lackey.setTarget(i == 0 ? player : ward);
		}
		player.sendSystemMessage(Component.translatable("message.jaymospowers.escort.ambush").withStyle(ChatFormatting.RED));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.8F, 1.4F);
	}

	/** The pupil's vigil at the monolith: walk them there, then from dusk to dawn keep the night off them. */
	private static void vigil(ServerLevel level, ServerPlayer player, LivingEntity ward, Escort e, double toDest, long now) {
		boolean on = (e.stage() & VIGIL_ON) != 0;
		if (!on) {
			if (toDest > ARRIVED * 0.6) {
				follow(ward, player);
				return;
			}
			if (ward instanceof PathfinderMob mob) {
				mob.getNavigation().moveTo(e.dest().getX() + 0.5, e.dest().getY(), e.dest().getZ() + 0.5, WALK_SPEED);
			}
			if (!level.isDarkOutside()) {
				if (player.tickCount % 400 < CHECK_TICKS) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.escort.vigil_wait", ward.getDisplayName()).withStyle(ChatFormatting.AQUA));
				}
				return;
			}
			player.setAttached(ESCORT, e.with(e.stage() | VIGIL_ON));
			player.sendSystemMessage(Component.translatable("message.jaymospowers.escort.vigil_start", ward.getDisplayName()).withStyle(ChatFormatting.AQUA));
			NEXT_WAVE.put(player.getUUID(), now);
			return;
		}
		if (ward instanceof PathfinderMob mob) {
			// at the stone, meditating
			mob.getNavigation().stop();
		}
		if (level.isBrightOutside()) {
			end(player, e, toDest <= ARRIVED, toDest <= ARRIVED ? "message.jaymospowers.escort.done.1" : "message.jaymospowers.escort.vigil_left");
			return;
		}
		if (now >= NEXT_WAVE.getOrDefault(player.getUUID(), now)) {
			NEXT_WAVE.put(player.getUUID(), now + WAVE_TICKS);
			RandomSource random = level.getRandom();
			for (int i = 0; i < WAVE_SIZE; i++) {
				double angle = random.nextDouble() * Mth.TWO_PI;
				BlockPos column = BlockPos.containing(ward.getX() + Math.cos(angle) * WAVE_RANGE, 0, ward.getZ() + Math.sin(angle) * WAVE_RANGE);
				BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
				EntityType<? extends Mob> type = switch (random.nextInt(3)) {
					case 0 -> EntityTypes.SKELETON;
					case 1 -> EntityTypes.SPIDER;
					default -> EntityTypes.ZOMBIE;
				};
				Mob monster = type.spawn(level, ground, EntitySpawnReason.EVENT);
				if (monster != null) {
					monster.setTarget(ward);
				}
			}
		}
	}

	/** The caravan's Giant: laid asleep beside the middle of the road once the caravan nears it; wakes to a sprint near. */
	private static void caravan(ServerLevel level, ServerPlayer player, LivingEntity ward, Escort e) {
		if ((e.stage() & GIANT_LAID) == 0) {
			if (e.span() <= 0 || ward.blockPosition().distSqr(e.dest()) > Math.pow(e.span() * 0.5 + 60.0, 2)) {
				return;
			}
			Mob giant = sleepingGiant(level, ward, e);
			player.setAttached(ESCORT, new Escort(e.variant(), e.ward(), e.dest(), e.started(), e.span(), e.stage() | GIANT_LAID,
				giant == null ? java.util.Optional.empty() : java.util.Optional.of(giant.getUUID()), e.mule()));
			if (giant != null) {
				player.sendSystemMessage(Component.translatable("message.jaymospowers.escort.giant", ward.getDisplayName()).withStyle(ChatFormatting.GOLD));
			}
			return;
		}
		Entity found = e.giant().map(level::getEntity).orElse(null);
		if (found instanceof Mob giant && giant.isNoAi() && giant.isAlive()) {
			if (player.isSprinting() && player.distanceTo(giant) <= GIANT_WAKES) {
				wake(giant, player);
			} else if (level.getGameTime() % 40 == 0) {
				// snoring
				level.playSound(null, giant.getX(), giant.getEyeY(), giant.getZ(), SoundEvents.RAVAGER_AMBIENT, SoundSource.HOSTILE, 1.5F, 0.4F);
			}
		}
	}

	private static @Nullable Mob sleepingGiant(ServerLevel level, LivingEntity ward, Escort e) {
		boolean cold = level.getBiome(ward.blockPosition()).value().getBaseTemperature() < 0.2F;
		Identifier id = Identifier.fromNamespaceAndPath("jaymoskingdoms", cold ? "arctic_giant" : "highfield_giant");
		var type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
		if (type == null) {
			return null;
		}
		Entity made = type.create(level, EntitySpawnReason.EVENT);
		if (!(made instanceof Mob giant)) {
			return null;
		}
		Vec3 road = Vec3.atBottomCenterOf(e.dest()).subtract(ward.position()).multiply(1.0, 0.0, 1.0).normalize();
		Vec3 side = new Vec3(-road.z, 0.0, road.x).scale(level.getRandom().nextBoolean() ? GIANT_OFF : -GIANT_OFF);
		Vec3 at = ward.position().add(road.scale(Math.min(60.0, Math.sqrt(ward.blockPosition().distSqr(e.dest())) * 0.5))).add(side);
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
		giant.snapTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		giant.setNoAi(true);
		giant.setPersistenceRequired();
		giant.addTag(GIANT_TAG);
		level.addFreshEntity(giant);
		return giant;
	}

	private static void wake(Mob giant, @Nullable LivingEntity by) {
		giant.setNoAi(false);
		if (by != null) {
			giant.setTarget(by);
		}
		giant.level().playSound(null, giant.getX(), giant.getEyeY(), giant.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 3.0F, 0.5F);
	}

	/** The escort ends ({@code done}: well), the ward and the mule go home, a Giant still asleep slips away. */
	private static void end(ServerPlayer player, Escort e, boolean done, String message) {
		ServerLevel level = player.level();
		player.removeAttached(ESCORT);
		UNSEEN.remove(player.getUUID());
		NEXT_WAVE.remove(player.getUUID());
		player.sendSystemMessage(Component.translatable(message).withStyle(done ? ChatFormatting.GOLD : ChatFormatting.GRAY));
		Entity ward = level.getEntity(e.ward());
		e.giant().map(level::getEntity).ifPresent(g -> {
			if (g instanceof Mob giant && giant.isNoAi()) {
				giant.discard();
			}
		});
		e.mule().map(level::getEntity).ifPresent(Entity::discard);
		if (ward != null && ward.isAlive()) {
			// their thanks, and off they go about their business
			ward.discard();
		}
		ZFighterSideQuests.escortEnded(player, done);
	}

	/** Forgets an escort (the job was given up on the Story screen): its ward and the rest go too. */
	static void abandon(ServerPlayer player) {
		Escort e = player.getAttached(ESCORT);
		if (e != null) {
			ServerLevel level = player.level();
			player.removeAttached(ESCORT);
			for (UUID id : new UUID[] {e.ward(), e.giant().orElse(null), e.mule().orElse(null)}) {
				Entity gone = id == null ? null : level.getEntity(id);
				if (gone instanceof Mob mob && (!mob.entityTags().contains(GIANT_TAG) || mob.isNoAi()) || gone != null && !(gone instanceof Mob)) {
					gone.discard();
				}
			}
		}
	}
}
