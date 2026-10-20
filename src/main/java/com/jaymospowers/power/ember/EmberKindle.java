package com.jaymospowers.power.ember;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Flames;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.smoke.SmokeField;
import com.jaymospowers.power.smoke.SmokePower;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.14.0) <b>Kindle</b>, the Emberborn's own fire (Jaymo's Kingdoms heritage; anyone of that blood, power or not).
 * Andy: "expand on emberborn, with a left click hold ability, and synergies merging smoke power into fire somehow".
 * Hold left mouse, bare-handed and not at a block, and flame gushes from the palm in a short cone ({@link #START_REACH}
 * blocks, reaching {@link #FULL_REACH} over the first second). What it touches is set alight ({@link Flames}) and
 * takes a little fire damage every half second; it lights campfires, candles and TNT and melts snow and ice it meets,
 * but never sets a block on fire, so nobody's village burns down by accident. It costs hunger while it runs.
 * <p>
 * Holding builds <b>heat</b> (0 to full in {@link #HEAT_TICKS}; the client's bar over the crosshair), which cools once
 * you let go. At full heat the flame burns white-hot (more damage), and letting go then throws the gathered heat as a
 * fireball ({@link EmberFireball}). Chilled (the heritage's cold weakness) the cone is half as long and heat builds
 * half as fast; in water or rain the flame only hisses steam.
 * <p>
 * With the Smoke power too, their own body is a heat source for their smoke ({@code SmokeHeat}), Kindle leaves sooty
 * smoke behind it, and the flame (or a fireball) meeting their own thick smoke sets off a flashover through the whole
 * cloud ({@link EmberFlashover}).
 * <p>
 * The client asks through {@code RaceHoldClient} (a Frost-born's and a Verdant's left holds live there too): with a
 * power other than Smoke, Kindle is sneak + left hold. It sends {@link Kindle}{@code (true)} when the stream starts and
 * every few ticks while held, and {@code (false)} when let go; without word for {@link #TIMEOUT} ticks it stops.
 */
public final class EmberKindle {
	// ---- tuning ----
	/** The cone's reach (blocks) as it starts and once it's grown, over {@link #GROW_TICKS}. */
	public static final double START_REACH = 3.0, FULL_REACH = 5.0;
	public static final int GROW_TICKS = 20;
	/** The cone's half-width at the palm and how much wider it gets a block out (blocks). */
	static final double MOUTH = 0.25, SPREAD = 0.22;
	/** Ticks from cold to full heat, and from full heat back to cold once let go. */
	public static final int HEAT_TICKS = 60, COOL_TICKS = 40;
	/** Chilled: the cone is this share as long and heat builds this share as fast. */
	static final double CHILL_REACH = 0.5;
	static final float CHILL_HEAT = 0.5F;
	/** Biome temperature at or under which an Emberborn is chilled (Kingdoms' {@code Heritage.COLD}). */
	static final float COLD = 0.2F;
	/** Damage to what it catches every {@link #HIT_EVERY} ticks, and white-hot; how long they burn (seconds). */
	static final float DAMAGE = 1.0F, WHITE_DAMAGE = 2.0F;
	static final int HIT_EVERY = 10, BURN_SECONDS = 3, WHITE_BURN_SECONDS = 5;
	/** How often (ticks) it lights or melts the block it plays on. */
	static final int BLOCK_EVERY = 5;
	/** Hunger a tick, and at or below this food it gutters out. */
	static final float EXHAUSTION = 0.03F;
	static final int MIN_FOOD = 6;
	/** The client's word lasts this long (ticks): no "still held" in that time and it stops. */
	static final int TIMEOUT = 40;
	/** With the Smoke power: sooty smoke left behind every {@link #SMOKE_EVERY} ticks (before the power's own boost), and its least soot. */
	static final float SMOKE = 0.15F, SMOKE_SOOT = 0.7F;
	static final int SMOKE_EVERY = 4;
	/** The flame sets off a flashover in the user's own smoke at least this thick, and no nearer than this to the palm. */
	static final float FLASH_LEAST = 1.0F;
	static final double FLASH_NEAR = 1.5;
	/** How often (ticks) the heritage is read off the Kingdoms tag. */
	static final int READ_EVERY = 20;
	/** Jaymo's Kingdoms' tag for the Emberborn heritage. */
	public static final String TAG = "jaymoskingdoms_heritage_emberborn";

	/** Of Emberborn blood (from Kingdoms' tag): the client needs it to hand the left hold to Kindle. Not saved. */
	public static final AttachmentType<Boolean> EMBER = AttachmentRegistry.create(JaymosPowers.id("emberborn"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** Kindle's state, seen by all: the flame running ({@code on}: the arm held out) and the heat (0-1; the bar, white-hot). */
	public record State(boolean on, float heat) {
		public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, State::on, ByteBufCodecs.FLOAT, State::heat, State::new);
	}

	public static final AttachmentType<State> KINDLE = AttachmentRegistry.create(JaymosPowers.id("ember_kindle"),
		builder -> builder.syncWith(State.CODEC, AttachmentSyncPredicate.all()));

	/** Client → server: Kindle held ({@code on}: started or still held), or let go. */
	public record Kindle(boolean on) implements CustomPacketPayload {
		public static final Type<Kindle> TYPE = new Type<>(JaymosPowers.id("ember_kindle"));
		public static final StreamCodec<ByteBuf, Kindle> CODEC = ByteBufCodecs.BOOL.map(Kindle::new, Kindle::on);

		@Override
		public Type<Kindle> type() {
			return TYPE;
		}
	}

	/** One Emberborn's flame on the server. */
	private static final class Live {
		boolean on;
		/** When the stream started and when the client last said it's held (game time). */
		long since, heard;
		float heat;
		/** The heat last synced, so it's only sent when it moves visibly. */
		float sent = -1.0F;
		boolean sentOn;
	}

	private static final Map<UUID, Live> LIVE = new HashMap<>();

	private EmberKindle() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Kindle.TYPE, Kindle.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Kindle.TYPE, (payload, context) -> hold(context.player(), payload.on()));
		ServerTickEvents.END_SERVER_TICK.register(EmberKindle::serverTick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LIVE.clear();
			EmberFireball.clear();
			EmberFlashover.clear();
		});
	}

	/** Whether {@code player} is of Emberborn blood (either side: synced from Kingdoms' tag). */
	public static boolean ember(Player player) {
		return Boolean.TRUE.equals(player.getAttached(EMBER));
	}

	/** Whether {@code player}'s flame is running (either side). */
	public static boolean kindling(Player player) {
		State state = player.getAttached(KINDLE);
		return state != null && state.on();
	}

	/** {@code player}'s heat, 0-1 (either side). */
	public static float heat(Player player) {
		State state = player.getAttached(KINDLE);
		return state == null ? 0.0F : state.heat();
	}

	/**
	 * Chilled, the heritage's cold weakness. Powers can't call Jaymo's Kingdoms, so this reads the cold the way Kingdoms'
	 * {@code Heritage.chilled} does: a cold land (biome temperature at or under {@link #COLD}) or freezing.
	 */
	static boolean chilled(ServerPlayer player) {
		return player.getTicksFrozen() > 0 || player.level().getBiome(player.blockPosition()).value().getBaseTemperature() <= COLD;
	}

	private static void hold(ServerPlayer player, boolean on) {
		Live live = LIVE.computeIfAbsent(player.getUUID(), k -> new Live());
		long now = player.level().getGameTime();
		if (!on) {
			if (live.on) {
				stop(player, live, true);
			}
			return;
		}
		live.heard = now;
		if (!live.on && usable(player)) {
			live.on = true;
			live.since = now;
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 0.7F);
		}
	}

	/** Emberborn, alive, bare-handed, fed enough. */
	private static boolean usable(ServerPlayer player) {
		return ember(player) && player.isAlive() && !player.isSpectator() && player.getMainHandItem().isEmpty()
			&& (player.getAbilities().instabuild || player.getFoodData().getFoodLevel() > MIN_FOOD);
	}

	/** The flame goes out; let go at full heat ({@code release}), the heat is thrown as a fireball. */
	private static void stop(ServerPlayer player, Live live, boolean release) {
		live.on = false;
		if (release && live.heat >= 1.0F && player.isAlive() && EmberFireball.throwFrom(player, palm(player))) {
			live.heat = 0.0F;
		}
	}

	private static void serverTick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.tickCount % READ_EVERY == 0) {
				boolean ember = player.entityTags().contains(TAG);
				if (ember != ember(player)) {
					if (ember) {
						player.setAttached(EMBER, true);
					} else {
						player.removeAttached(EMBER);
					}
				}
			}
			Live live = LIVE.get(player.getUUID());
			if (live != null) {
				tick(player, live);
			}
		}
		// the players who left
		LIVE.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		EmberFireball.tick(server);
		EmberFlashover.tick();
	}

	private static void tick(ServerPlayer player, Live live) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (live.on && (!usable(player) || now - live.heard > TIMEOUT || now < live.heard)) {
			stop(player, live, false);
		}
		boolean chilled = chilled(player);
		boolean wet = player.isInWaterOrRain();
		if (live.on) {
			if (!wet) {
				live.heat = Math.min(1.0F, live.heat + (chilled ? CHILL_HEAT : 1.0F) / HEAT_TICKS);
			}
			flame(player, live, level, now, chilled, wet);
		} else {
			live.heat = Math.max(0.0F, live.heat - 1.0F / COOL_TICKS);
		}
		sync(player, live);
	}

	/** Sends the state on when it changes visibly (heat in twentieths, rounded down: white-hot only when it is). */
	private static void sync(ServerPlayer player, Live live) {
		float shown = (float)Math.floor(live.heat * 20.0F) / 20.0F;
		if (shown == live.sent && live.on == live.sentOn) {
			return;
		}
		live.sent = shown;
		live.sentOn = live.on;
		if (!live.on && shown <= 0.0F) {
			player.removeAttached(KINDLE);
		} else {
			player.setAttached(KINDLE, new State(live.on, shown));
		}
	}

	/** Where the flame leaves the right palm: out in front, a little right and below the eyes. */
	static Vec3 palm(ServerPlayer player) {
		double yaw = Math.toRadians(player.getYRot());
		Vec3 right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
		return player.getEyePosition().add(player.getViewVector(1.0F).scale(0.6)).add(right.scale(0.35)).add(0.0, -0.3, 0.0);
	}

	/** One tick of the stream: its look, what it burns, the blocks it lights or melts, the smoke it leaves. */
	private static void flame(ServerPlayer player, Live live, ServerLevel level, long now, boolean chilled, boolean wet) {
		if (!player.getAbilities().instabuild) {
			player.causeFoodExhaustion(EXHAUSTION);
		}
		Vec3 from = palm(player);
		Vec3 look = player.getViewVector(1.0F);
		double grown = Math.min(1.0, (now - live.since) / (double)GROW_TICKS);
		double reach = (START_REACH + (FULL_REACH - START_REACH) * grown) * (chilled ? CHILL_REACH : 1.0);
		if (wet) {
			// in water or the rain the flame only hisses steam
			level.sendParticles(ParticleTypes.CLOUD, from.x, from.y, from.z, 2, 0.08, 0.08, 0.08, 0.02);
			if ((now - live.since) % 10 == 0) {
				level.playSound(null, from.x, from.y, from.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.6F);
			}
			return;
		}
		// a wall stops it short
		Vec3 end = from.add(look.scale(reach));
		HitResult wall = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (wall.getType() != HitResult.Type.MISS) {
			reach = wall.getLocation().distanceTo(from);
		}
		boolean white = live.heat >= 1.0F;
		particles(level, from, look, reach, white);
		if ((now - live.since) % 20 == 0) {
			level.playSound(null, from.x, from.y, from.z, SoundEvents.BLAZE_BURN, SoundSource.PLAYERS, 0.9F, white ? 1.3F : 0.8F);
			level.playSound(null, from.x, from.y, from.z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.2F, 0.6F);
		}
		if ((now - live.since) % HIT_EVERY == 0) {
			burn(player, level, from, look, reach, white);
		}
		if ((now - live.since) % BLOCK_EVERY == 0) {
			HitResult touched = level.clip(new ClipContext(from, from.add(look.scale(reach + 0.3)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
			if (touched instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
				touch(level, hit.getBlockPos(), player);
			}
		}
		if (Powers.has(player, SmokePower.INSTANCE)) {
			// smoke merged into fire: the flame leaves sooty smoke rising off it, and lights their own thick smoke
			if ((now - live.since) % SMOKE_EVERY == 0) {
				Vec3 behind = from.add(look.scale(Math.min(1.0, reach * 0.3))).add(0.0, 0.6, 0.0);
				SmokeField.puffSooty(level, behind, SMOKE, player, SMOKE_SOOT);
			}
			for (double t = FLASH_NEAR; t <= reach; t += 0.5) {
				BlockPos pos = BlockPos.containing(from.add(look.scale(t)));
				if (SmokeField.ownedDensity(level, pos.asLong(), player.getId()) >= FLASH_LEAST) {
					EmberFlashover.ignite(level, player, pos);
					break;
				}
			}
		}
	}

	private static void particles(ServerLevel level, Vec3 from, Vec3 look, double reach, boolean white) {
		var random = level.getRandom();
		// the gush from the palm, carried along the look
		for (int i = 0; i < 3; i++) {
			double speed = 0.25 + random.nextDouble() * 0.2;
			level.sendParticles(ParticleTypes.FLAME, from.x, from.y, from.z, 0, look.x + random.nextGaussian() * 0.12,
				look.y + random.nextGaussian() * 0.12, look.z + random.nextGaussian() * 0.12, speed);
		}
		// the body of the cone, widening
		int along = (int)Math.ceil(reach * 1.6);
		for (int i = 0; i < along; i++) {
			double t = random.nextDouble() * reach;
			double width = MOUTH + SPREAD * t;
			Vec3 p = from.add(look.scale(t));
			level.sendParticles(white && random.nextInt(3) == 0 ? ParticleTypes.END_ROD : ParticleTypes.FLAME, p.x, p.y, p.z, 1,
				width * 0.5, width * 0.5, width * 0.5, 0.01);
		}
		if (random.nextInt(3) == 0) {
			Vec3 p = from.add(look.scale(random.nextDouble() * reach));
			level.sendParticles(ParticleTypes.LAVA, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.0);
		}
		level.sendParticles(ParticleTypes.SMALL_FLAME, from.x, from.y, from.z, 1, 0.04, 0.04, 0.04, 0.01);
	}

	/** Everything living (and burnable) in the cone, in sight of the palm, is set alight and singed. */
	private static void burn(ServerPlayer player, ServerLevel level, Vec3 from, Vec3 look, double reach, boolean white) {
		Vec3 end = from.add(look.scale(reach));
		AABB box = new AABB(from, end).inflate(MOUTH + SPREAD * reach + 0.5);
		for (Entity entity : level.getEntities(player, box, e -> !e.isSpectator() && e.isAlive())) {
			Vec3 at = entity.getBoundingBox().getCenter();
			Vec3 off = at.subtract(from);
			double t = off.dot(look);
			if (t < 0.0 || t > reach + entity.getBbWidth() * 0.5) {
				continue;
			}
			double side = off.subtract(look.scale(t)).length();
			if (side > MOUTH + SPREAD * t + entity.getBbWidth() * 0.5) {
				continue;
			}
			if (level.clip(new ClipContext(from, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) {
				continue;
			}
			Flames.touch(level, entity, white ? WHITE_BURN_SECONDS : BURN_SECONDS, player);
			if (entity instanceof LivingEntity living) {
				living.hurtServer(level, level.damageSources().source(DamageTypes.IN_FIRE, player), white ? WHITE_DAMAGE : DAMAGE);
			}
		}
	}

	/**
	 * Fire meets the block at {@code pos} (Kindle or a fireball): campfires, candles and candle cakes are lit, TNT is
	 * primed, snow melts away and ice to water (snow lying on it too). Nothing is ever set on fire.
	 */
	static void touch(ServerLevel level, BlockPos pos, ServerPlayer by) {
		BlockState state = level.getBlockState(pos);
		if (lightable(state)) {
			level.setBlockAndUpdate(pos, state.setValue(BlockStateProperties.LIT, true));
			level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.4F + 0.8F);
			return;
		}
		if (state.is(Blocks.TNT)) {
			level.removeBlock(pos, false);
			PrimedTnt tnt = new PrimedTnt(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, by);
			level.addFreshEntity(tnt);
			level.playSound(null, pos, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
			return;
		}
		if (!melt(level, pos)) {
			melt(level, pos.above());
		}
	}

	/** An unlit campfire, candle or candle cake, not under water. */
	private static boolean lightable(BlockState state) {
		if (!state.hasProperty(BlockStateProperties.LIT) || state.getValue(BlockStateProperties.LIT)) {
			return false;
		}
		if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
			return false;
		}
		String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		return path.endsWith("campfire") || path.endsWith("candle") || path.endsWith("candle_cake");
	}

	/** Snow at {@code pos} melts away, ice turns to water. True if it did. */
	private static boolean melt(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) {
			level.removeBlock(pos, false);
		} else if (state.is(Blocks.ICE) || state.is(Blocks.FROSTED_ICE)) {
			level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
		} else {
			return false;
		}
		level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 4, 0.25, 0.2, 0.25, 0.02);
		level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.4F);
		return true;
	}
}
