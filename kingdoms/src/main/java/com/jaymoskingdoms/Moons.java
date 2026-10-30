package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * (0.6.6) Lunar events: now and then the night's moon is no ordinary one. Our own take on the idea behind Lunar and
 * Enhanced Celestials (nothing of theirs is used). At dusk the Overworld rolls for the night ({@link #roll}); the moon
 * lasts till dawn, everyone in the Overworld is told it's rising, and the sky, the moon and the night light take its
 * colour on every client ({@link #addLayers}, through the game's own environment attributes, and the moon's tint in
 * {@code MoonSkyMixin}).
 *
 * <ul>
 * <li>{@link Moon#BLOOD Blood Moon} (any night): zombies and skeletons claw up out of the ground round every player
 * ({@link #rise}), monsters come out stronger; no one can
 * sleep it off; barbarian camps raid twice as readily.</li>
 * <li>{@link Moon#SUPER_BLOOD Super Blood Moon} (full moons only, rare): a huge, deep red moon; the same, twice over.</li>
 * <li>{@link Moon#HARVEST Harvest Moon} (full moons only): a big golden moon; crops, berries and saplings round every
 * player grow all night, and at dawn every loaded settlement's fields have grown a stage.</li>
 * <li>{@link Moon#BLUE Blue Moon} (full moons only): everyone outside has Luck, and monsters slain by a player give
 * extra experience and sometimes a gem.</li>
 * </ul>
 *
 * The roll is saved, so a restart mid-night keeps the moon. No events in a world's first {@link #QUIET_NIGHTS} nights.
 * Operators: {@code /kingdom moon <blood|super_blood|harvest|blue|clear>} sets tonight's moon (it rises at once if it's
 * night).
 */
public final class Moons {
	/** A night runs from this time of day till {@link #NIGHT_END}. */
	static final long NIGHT_START = 12500L, NIGHT_END = 23500L;
	/** No lunar events in the world's first nights. */
	static final long QUIET_NIGHTS = 4;
	/** Chances a night is a Blood Moon (any night); on a full moon instead, the chances of each full-moon event. */
	static final float BLOOD_CHANCE = 0.05F;
	static final float FULL_SUPER_BLOOD = 0.08F, FULL_HARVEST = 0.30F, FULL_BLUE = 0.22F;

	/** Blood moons: how often each player draws a monster (ticks), the most monsters round them, where they appear. */
	static final int BLOOD_SPAWN_EVERY = 40, BLOOD_NEAR_CAP = 22, SUPER_NEAR_CAP = 36;
	static final int BLOOD_SPAWN_MIN = 22, BLOOD_SPAWN_MAX = 40;
	/** Monsters out under a blood moon: Strength, Speed (amplifiers), and how long it lasts at most (ticks). */
	static final int BLOOD_STRENGTH = 0, SUPER_STRENGTH = 1, BLOOD_SPEED = 0, BLOOD_BUFF_TICKS = 12000;
	/** Barbarian camps' raid chance under a blood moon (times). */
	static final float BLOOD_RAID_FACTOR = 2.0F;

	/** Harvest moon: plants tried round each player every second, and how far out. */
	static final int HARVEST_TRIES = 24, HARVEST_RANGE = 40;
	/** Blue moon: Luck's level; extra experience per monster; the chance of a gem as well. */
	static final int BLUE_LUCK = 1, BLUE_XP = 6;
	static final float BLUE_GEM_CHANCE = 0.15F;

	/** Visuals: how much of the night sky and fog takes the moon's colour (at full strength). */
	static final float SKY_BLEND = 0.55F, FOG_BLEND = 0.45F;
	/** How long the colours take to come and go on a client (ticks). */
	public static final int FADE_TICKS = 200;

	/** The night's moon. Colours: the moon's tint, the night sky's, and what the night light is multiplied by. */
	public enum Moon {
		NONE(ChatFormatting.WHITE, 0xFFFFFF, 0x000000, 0xFFFFFF, 1.0F),
		BLOOD(ChatFormatting.RED, 0xFF4033, 0x5A0808, 0xFF9A8C, 1.15F),
		SUPER_BLOOD(ChatFormatting.DARK_RED, 0xFF1F19, 0x730505, 0xFF7366, 1.7F),
		HARVEST(ChatFormatting.GOLD, 0xFFB84D, 0x40290A, 0xFFDA99, 1.35F),
		BLUE(ChatFormatting.AQUA, 0x73A6FF, 0x0A1A4D, 0xB3CCFF, 1.1F);

		public final ChatFormatting style;
		public final Vector3fc moon, sky, light;
		/** The moon's size (times). */
		public final float scale;

		Moon(ChatFormatting style, int moon, int sky, int light, float scale) {
			this.style = style;
			this.moon = ARGB.vector3fFromRGB24(moon);
			this.sky = ARGB.vector3fFromRGB24(sky);
			this.light = ARGB.vector3fFromRGB24(light);
			this.scale = scale;
		}

		public String id() {
			return this.name().toLowerCase(Locale.ROOT);
		}

		static Moon parse(String id) {
			for (Moon m : values()) {
				if (m.id().equals(id)) {
					return m;
				}
			}
			return NONE;
		}

		boolean blood() {
			return this == BLOOD || this == SUPER_BLOOD;
		}
	}

	/** The Overworld's roll: the night it was for (see {@link #nightOf}) and what came up. */
	record Roll(long night, String moon) {
		static final Codec<Roll> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("night").forGetter(Roll::night),
			Codec.STRING.fieldOf("moon").forGetter(Roll::moon)).apply(i, Roll::new));
	}

	static final AttachmentType<Roll> ROLL = AttachmentRegistry.create(JaymosKingdoms.id("moon"), builder -> builder.persistent(Roll.CODEC));

	/** Server → player: tonight's moon over the Overworld (NONE by day). */
	public record Sync(int moon) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(JaymosKingdoms.id("moon"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.moon), buf -> new Sync(buf.readVarInt()));

		@Override
		public Type<Sync> type() {
			return TYPE;
		}
	}

	/** The moon now over the server's Overworld (NONE by day), as last sent. */
	private static Moon shown = Moon.NONE;

	/** The client's side: the moon it was told of, the last one it showed (for fading out), and how strongly (0-1). */
	public static volatile Moon clientMoon = Moon.NONE;
	public static volatile Moon clientShown = Moon.NONE;
	public static volatile float clientFade;

	private Moons() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(Moons::tick);
		ServerTickEvents.END_SERVER_TICK.register(Moons::riseTick);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			RISING.values().forEach(Moons::free);
			RISING.clear();
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ServerPlayNetworking.send(handler.player, new Sync(shown.ordinal())));
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> shown = Moon.NONE);
		ServerEntityEvents.ENTITY_LOAD.register(Moons::loaded);
		ServerLivingEntityEvents.AFTER_DEATH.register(Moons::died);
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
			var moon = Commands.literal("moon").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
			for (Moon m : Moon.values()) {
				moon.then(Commands.literal(m == Moon.NONE ? "clear" : m.id()).executes(c -> force(c.getSource().getServer(), m)));
			}
			dispatcher.register(Commands.literal("kingdom").then(moon));
		});
	}

	// ---- the roll -------------------------------------------------------------------------------------------------------

	/** The night {@code clock} falls in (counted from the world's first), or -1 by day. */
	static long nightOf(long clock) {
		long t = Math.floorMod(clock, 24000L);
		return t >= NIGHT_START && t < NIGHT_END ? Math.floorDiv(clock, 24000L) : -1;
	}

	/** The moon over the server's Overworld now (NONE by day). */
	public static Moon now(MinecraftServer server) {
		return shown;
	}

	static boolean blood(MinecraftServer server) {
		return shown.blood();
	}

	/** (Raids) A camp's raid chance, as tonight's moon takes it. */
	static float raidChance(MinecraftServer server, float chance) {
		return shown.blood() ? chance * BLOOD_RAID_FACTOR : chance;
	}

	private static Moon current(ServerLevel level) {
		long night = nightOf(level.getOverworldClockTime());
		if (night < 0) {
			return Moon.NONE;
		}
		Roll roll = level.getAttached(ROLL);
		if (roll == null || roll.night() != night) {
			roll = new Roll(night, roll(level, night).id());
			level.setAttached(ROLL, roll);
		}
		return Moon.parse(roll.moon());
	}

	/** What rises on {@code night}. */
	private static Moon roll(ServerLevel level, long night) {
		if (night < QUIET_NIGHTS) {
			return Moon.NONE;
		}
		RandomSource random = level.getRandom();
		MoonPhase phase = level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, BlockPos.ZERO);
		if (phase == MoonPhase.FULL_MOON) {
			float r = random.nextFloat();
			if ((r -= FULL_SUPER_BLOOD) < 0) {
				return Moon.SUPER_BLOOD;
			}
			if ((r -= FULL_HARVEST) < 0) {
				return Moon.HARVEST;
			}
			if ((r -= FULL_BLUE) < 0) {
				return Moon.BLUE;
			}
			return Moon.NONE;
		}
		return random.nextFloat() < BLOOD_CHANCE ? Moon.BLOOD : Moon.NONE;
	}

	/** {@code /kingdom moon ...}: tonight's moon set (and shown at once if it's night). */
	private static int force(MinecraftServer server, Moon moon) {
		ServerLevel level = server.overworld();
		long clock = level.getOverworldClockTime();
		long night = nightOf(clock);
		level.setAttached(ROLL, new Roll(night >= 0 ? night : Math.floorDiv(clock, 24000L), moon.id()));
		return 1;
	}

	// ---- every tick -----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		Moon moon = current(level);
		if (moon != shown) {
			change(level, shown, moon);
		}
		if (moon == Moon.NONE) {
			return;
		}
		long now = level.getGameTime();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			if (moon.blood() && (now + player.getId()) % BLOOD_SPAWN_EVERY == 0 && !player.isCreative()) {
				bloodSpawn(level, player, moon);
			}
			if (moon == Moon.HARVEST && (now + player.getId()) % 20 == 0) {
				harvest(level, player);
			}
			if (moon == Moon.BLUE && (now + player.getId()) % 100 == 0 && level.canSeeSky(player.blockPosition())) {
				player.addEffect(new MobEffectInstance(MobEffects.LUCK, 400, BLUE_LUCK - 1, true, false, true));
			}
		}
	}

	/** The moon over the Overworld changed: everyone's told, and a harvest moon's dawn grows the settlements' fields. */
	private static void change(ServerLevel level, Moon was, Moon moon) {
		shown = moon;
		// (0.7.419) a road deed: a blood moon that has come and gone
		if (was.blood() && !moon.blood()) {
			Deeds.done(level.getServer(), "moon.blood");
		}
		for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(player, new Sync(moon.ordinal()));
		}
		if (moon != Moon.NONE) {
			Component rises = Component.translatable("message.jaymoskingdoms.moon." + moon.id()).withStyle(moon.style);
			for (ServerPlayer player : level.players()) {
				player.sendSystemMessage(rises);
				player.sendOverlayMessage(Component.translatable("moon.jaymoskingdoms." + moon.id()).withStyle(moon.style));
				if (moon.blood()) {
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 1.0F, 0.5F);
				} else {
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 1.5F, 0.6F);
				}
			}
		} else if (was == Moon.HARVEST) {
			int grown = 0;
			for (Faction f : Factions.all(level.getServer())) {
				if (!f.fallen() && level.isPositionEntityTicking(f.seat())) {
					grown += Nights.grow(level, f.seat(), 1);
				}
			}
			JaymosKingdoms.LOGGER.debug("the harvest moon grew {} crops in the settlements", grown);
		}
	}

	// ---- blood moons ----------------------------------------------------------------------------------------------------

	/** (0.6.6) Under a blood moon the dead come up out of the ground: zombies and skeletons only. */
	private static final List<EntityType<? extends Mob>> BLOOD_MOBS = List.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON);

	/** A monster drawn out of the dark round {@code player}, if there's room and a dark place for it. */
	private static void bloodSpawn(ServerLevel level, ServerPlayer player, Moon moon) {
		int cap = moon == Moon.SUPER_BLOOD ? SUPER_NEAR_CAP : BLOOD_NEAR_CAP;
		AABB near = player.getBoundingBox().inflate(48.0);
		if (level.getEntitiesOfClass(Monster.class, near, e -> true).size() >= cap) {
			return;
		}
		RandomSource random = level.getRandom();
		int tries = moon == Moon.SUPER_BLOOD ? 2 : 1;
		for (int i = 0; i < tries; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int dist = Mth.nextInt(random, BLOOD_SPAWN_MIN, BLOOD_SPAWN_MAX);
			BlockPos column = BlockPos.containing(player.getX() + Math.cos(angle) * dist, player.getY(), player.getZ() + Math.sin(angle) * dist);
			if (!level.isPositionEntityTicking(column)) {
				continue;
			}
			EntityType<? extends Mob> type = BLOOD_MOBS.get(random.nextInt(BLOOD_MOBS.size()));
			BlockPos at = level.getHeightmapPos(SpawnPlacements.getHeightmapType(type), column);
			if (Math.abs(at.getY() - player.getY()) > 24 || level.getBrightness(LightLayer.BLOCK, at) > 0
				|| !SpawnPlacements.isSpawnPositionOk(type, level, at) || !SpawnPlacements.checkSpawnRules(type, level, EntitySpawnReason.NATURAL, at, random)) {
				continue;
			}
			Mob mob = type.create(level, EntitySpawnReason.NATURAL);
			if (mob == null) {
				continue;
			}
			mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			if (!level.noCollision(mob)) {
				mob.discard();
				continue;
			}
			if (!sturdyGround(level, at)) {
				mob.discard();
				continue;
			}
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.NATURAL, null);
			if (!mob.isPassenger()) {
				rise(level, mob);
			}
			level.addFreshEntityWithPassengers(mob);
		}
	}

	/** A monster appearing under a blood moon comes out stronger (till dawn, about). */
	private static void loaded(Entity entity, ServerLevel level) {
		if (!(entity instanceof Monster monster) || entity.tickCount > 0 || level != level.getServer().overworld() || !shown.blood()) {
			return;
		}
		long t = Math.floorMod(level.getOverworldClockTime(), 24000L);
		int ticks = (int)Math.min(BLOOD_BUFF_TICKS, Math.max(200, NIGHT_END - t + 600));
		monster.addEffect(new MobEffectInstance(MobEffects.STRENGTH, ticks, shown == Moon.SUPER_BLOOD ? SUPER_STRENGTH : BLOOD_STRENGTH, true, false));
		monster.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, BLOOD_SPEED, true, false));
		// the game's own zombies and skeletons climb out of the ground as well
		if ((entity.getType() == EntityTypes.ZOMBIE || entity.getType() == EntityTypes.SKELETON) && !RISING.containsKey(entity.getUUID())
			&& !entity.isPassenger() && !entity.isVehicle() && sturdyGround(level, entity.blockPosition())) {
			rise(level, (Mob)entity);
		}
	}

	// ---- (0.6.6) the dead rising ----------------------------------------------------------------------------------------

	/** How deep they start (blocks) and how long they take to climb out (ticks). */
	static final double RISE_DEPTH = 2.0;
	static final int RISE_TICKS = 50;

	/** One climbing out: where it ends up standing, the ground it breaks through, how far along it is. */
	private static final class Rising {
		final Mob mob;
		final double x, top, z;
		final BlockState ground;
		int ticks;

		Rising(Mob mob, BlockState ground) {
			this.mob = mob;
			this.x = mob.getX();
			this.top = mob.getY();
			this.z = mob.getZ();
			this.ground = ground;
		}
	}

	private static final java.util.Map<java.util.UUID, Rising> RISING = new java.util.LinkedHashMap<>();

	private static boolean sturdyGround(ServerLevel level, BlockPos feet) {
		BlockPos below = feet.below();
		return level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP);
	}

	/** {@code mob} starts below the ground under it, frozen, and climbs up through it ({@link #riseTick}). */
	private static void rise(ServerLevel level, Mob mob) {
		Rising r = new Rising(mob, level.getBlockState(mob.blockPosition().below()));
		RISING.put(mob.getUUID(), r);
		// (0.7.88) the risen dead leave Nightborn vampires be (NightbornBlood.spares)
		mob.addTag(NightbornBlood.RISEN_TAG);
		mob.setNoAi(true);
		mob.setNoGravity(true);
		mob.noPhysics = true;
		mob.setPos(r.x, r.top - RISE_DEPTH, r.z);
		level.playSound(null, r.x, r.top, r.z, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 1.0F, 0.6F);
	}

	private static void riseTick(MinecraftServer server) {
		if (RISING.isEmpty()) {
			return;
		}
		for (var it = RISING.values().iterator(); it.hasNext();) {
			Rising r = it.next();
			if (r.mob.isRemoved() || !(r.mob.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			r.ticks++;
			float t = Math.min(1.0F, r.ticks / (float)RISE_TICKS);
			// quick at first, slow as the shoulders come free
			double eased = 1.0 - (1.0 - t) * (1.0 - t);
			r.mob.setPos(r.x, r.top - RISE_DEPTH * (1.0 - eased), r.z);
			r.mob.setDeltaMovement(Vec3.ZERO);
			if (r.ticks % 2 == 0) {
				level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, r.ground),
					r.x, r.top + 0.1, r.z, 6, 0.3, 0.05, 0.3, 0.1);
			}
			if (r.ticks % 12 == 0) {
				level.playSound(null, r.x, r.top, r.z, r.ticks < RISE_TICKS / 2 ? SoundEvents.GRAVEL_BREAK : SoundEvents.ROOTED_DIRT_BREAK,
					SoundSource.HOSTILE, 0.8F, 0.7F + level.getRandom().nextFloat() * 0.2F);
			}
			if (r.ticks >= RISE_TICKS) {
				it.remove();
				free(r);
				level.playSound(null, r.x, r.top, r.z, r.mob.getType() == EntityTypes.SKELETON ? SoundEvents.SKELETON_AMBIENT : SoundEvents.ZOMBIE_AMBIENT,
					SoundSource.HOSTILE, 1.0F, 0.8F);
			}
		}
	}

	/** Out of the ground (or the server's stopping: never saved half-buried and frozen). */
	private static void free(Rising r) {
		r.mob.setPos(r.x, r.top, r.z);
		r.mob.setNoAi(false);
		r.mob.setNoGravity(false);
		r.mob.noPhysics = false;
	}

	// ---- harvest moons --------------------------------------------------------------------------------------------------

	/** Plants round {@code player} grow as if the night were days. */
	private static void harvest(ServerLevel level, ServerPlayer player) {
		RandomSource random = level.getRandom();
		for (int i = 0; i < HARVEST_TRIES; i++) {
			BlockPos column = player.blockPosition().offset(random.nextInt(HARVEST_RANGE * 2 + 1) - HARVEST_RANGE, 0,
				random.nextInt(HARVEST_RANGE * 2 + 1) - HARVEST_RANGE);
			if (!level.isPositionEntityTicking(column)) {
				continue;
			}
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			for (BlockPos pos : new BlockPos[] {top, top.below()}) {
				BlockState state = level.getBlockState(pos);
				if (state.getBlock() instanceof CropBlock || state.getBlock() instanceof StemBlock || state.getBlock() instanceof SweetBerryBushBlock
					|| state.getBlock() instanceof SaplingBlock || state.getBlock() instanceof CocoaBlock) {
					state.randomTick(level, pos, random);
					break;
				}
			}
		}
	}

	// ---- blue moons -----------------------------------------------------------------------------------------------------

	/** A monster slain by a player under a blue moon gives more. */
	private static void died(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof Monster) || !(entity.level() instanceof ServerLevel level) || level != level.getServer().overworld()
			|| shown != Moon.BLUE || !(source.getEntity() instanceof ServerPlayer)) {
			return;
		}
		ExperienceOrb.award(level, entity.position(), BLUE_XP);
		RandomSource random = level.getRandom();
		if (random.nextFloat() < BLUE_GEM_CHANCE) {
			entity.spawnAtLocation(level, switch (random.nextInt(4)) {
				case 0 -> Items.EMERALD;
				case 1 -> Items.AMETHYST_SHARD;
				case 2 -> Items.DIAMOND;
				default -> Items.LAPIS_LAZULI;
			});
		}
	}

	// ---- what the world looks like (both sides) -------------------------------------------------------------------------

	/** The moon this side of {@code level} sees, and how strongly. */
	private static Moon moonFor(Level level) {
		return level.isClientSide() ? clientShown : shown;
	}

	private static float fadeFor(Level level) {
		return level.isClientSide() ? clientFade : shown == Moon.NONE ? 0.0F : 1.0F;
	}

	private static final BedRule NO_SLEEP = new BedRule(BedRule.Rule.NEVER, BedRule.Rule.ALWAYS, false, false,
		Optional.of(Component.translatable("message.jaymoskingdoms.moon.no_sleep")));

	/**
	 * (EnvironmentAttributeSystemBuilderMixin) The Overworld's sky, fog and night light take the moon's colour; a blood
	 * moon keeps everyone awake.
	 */
	public static void addLayers(EnvironmentAttributeSystem.Builder builder, Level level) {
		if (level.dimension() != Level.OVERWORLD) {
			return;
		}
		builder.addTimeBasedLayer(EnvironmentAttributes.SKY_COLOR, (sky, tick) -> {
			float fade = fadeFor(level);
			return fade <= 0.0F ? sky : ARGB.srgbLerp(SKY_BLEND * fade, sky, moonFor(level).sky);
		});
		builder.addTimeBasedLayer(EnvironmentAttributes.FOG_COLOR, (fog, tick) -> {
			float fade = fadeFor(level);
			return fade <= 0.0F ? fog : ARGB.srgbLerp(FOG_BLEND * fade, fog, moonFor(level).sky);
		});
		builder.addTimeBasedLayer(EnvironmentAttributes.SKY_LIGHT_COLOR, (light, tick) -> {
			float fade = fadeFor(level);
			if (fade <= 0.0F) {
				return light;
			}
			Vector3fc tint = moonFor(level).light;
			return ARGB.multiply(light, new Vector3f(Mth.lerp(fade, 1.0F, tint.x()), Mth.lerp(fade, 1.0F, tint.y()), Mth.lerp(fade, 1.0F, tint.z())));
		});
		builder.addTimeBasedLayer(EnvironmentAttributes.BED_RULE, (rule, tick) -> moonFor(level).blood() ? NO_SLEEP : rule);
	}

	/** (MoonSkyMixin) The moon's colour and size on the client. */
	public static Vec3 clientMoonTint() {
		float fade = clientFade;
		Vector3fc tint = clientShown.moon;
		return new Vec3(Mth.lerp(fade, 1.0F, tint.x()), Mth.lerp(fade, 1.0F, tint.y()), Mth.lerp(fade, 1.0F, tint.z()));
	}

	public static float clientMoonScale() {
		return Mth.lerp(clientFade, 1.0F, clientShown.scale);
	}
}
