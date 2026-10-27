package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.Arcana;
import com.jaymoskingdoms.BossBars;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Lockpicking;
import com.jaymoskingdoms.Mana;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.241) The Quiet One. Andy: "five personalities, one of those being seemingly non responsive at all". It never speaks:
 * every line of {@link SpiritVoice} is dropped for it but the dots of its sickness ({@code sick.1} to {@code sick.5}, a
 * growing run of full stops) and, once, at bond {@value Spirits#MAX_BOND}, a single word ({@code word.1}, kept by
 * {@link #SPOKEN} so it is never said again; Andy: "Yes" to speaking once at max bond). It is a different size each time it is
 * conjured ({@link #rollScale}, kept in {@link SpiritState#scale()}), smooth and featureless, and it repeats what you do.
 * <ul>
 * <li><b>Echo strike</b> (level 1): while on, every melee blow you land is repeated {@value #ECHO_DELAY} ticks later by the
 * arms for half as hard, for {@link #ECHO_COST} mana each. <b>Mirror step</b> (3): a dodge three blocks to the side or back,
 * leaving a glowing double for two seconds that the mobs hunting you turn on. <b>Stillness</b> (6): for three seconds every
 * mob near is slowed to a crawl and cannot attack, and arrows hang in the air. <b>Silent end</b> (8): one blow kills a foe
 * in reach that is under {@code 30%} health (never a boss), once a day. <b>Sense</b> (2): for ten seconds the hidden show
 * to you as motes, with locked chests, traps and ores near.</li>
 * <li>On its own (bond 20 and up), almost never: one fight in {@value #FINISH_ODDS} it finishes the fight (a silent end on a
 * weak foe within six blocks), and one in {@value #STILL_ODDS} it holds the world still. No advice, no remarks.</li>
 * </ul>
 */
public final class QuietOne extends Spirit {
	/** The size it is conjured at each time is a random scale (times 100) from this to that. */
	static final int SCALE_MIN = 50, SCALE_MAX = 300;
	/** Echo strike: mana per echo, ticks after the blow, share of the blow's damage, and how far off the foe may be by then. */
	static final float ECHO_COST = 6.0F, ECHO_SHARE = 0.5F;
	static final int ECHO_DELAY = 10;
	static final double ECHO_LOSE = 8.0;
	/** Mirror step: blocks stepped, ticks the double lasts, how far it draws mobs (blocks), and how often it re-aims them (ticks). */
	static final double MIRROR_STEP = 3.0, MIRROR_DRAW = 16.0;
	static final int MIRROR_TICKS = 40, MIRROR_AIM_EVERY = 2;
	/** Stillness: ticks it lasts, blocks it reaches, the slowness amplifier (5 is slowness VI), and the odds per fight (1 in). */
	static final int STILL_TICKS = 60, STILL_SLOW = 5, STILL_ODDS = 60;
	static final double STILL_RANGE = 8.0;
	/** Silent end: the health share a foe must be under, the reach by key and on its own (blocks), and the odds per fight (1 in). */
	static final float SILENT_BELOW = 0.3F;
	static final double SILENT_REACH = 4.0, SILENT_AUTO_REACH = 6.0;
	static final int FINISH_ODDS = 40;
	/** On its own: what the silent end and the stillness cost it, and the least gap between its own (ticks). */
	static final float SILENT_AUTO_COST = 20.0F, STILL_AUTO_COST = 15.0F;
	static final int SILENT_AUTO_EVERY = 24000, STILL_AUTO_EVERY = 1200;
	/** A fight is over when no foe has been near for this many ticks; a foe this near (blocks) is a fight. */
	static final int FIGHT_GAP = 200;
	static final double FIGHT_RANGE = 8.0;
	/** Sense: ticks it lasts, how far it feels mobs, locked chests and traps, and ores (blocks); ticks between pulses. */
	static final int SENSE_TICKS = 200, SENSE_EVERY = 10;
	static final double SENSE_MOBS = 24.0, SENSE_CHESTS = 16.0;
	static final int SENSE_ORES = 8;
	/** The tag on a mirror step's double. */
	static final String MIRROR_TAG = "jaymoskingdoms_mirror";

	/** Set once the Quiet One has spoken its one word: never again. */
	public static final AttachmentType<Boolean> SPOKEN = AttachmentRegistry.create(JaymosKingdoms.id("spirit_spoken"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());

	/** Ways to find rune traps near a player (blocks); Thessaly's adds hers. Sense rings each of them. */
	public static final List<Function<ServerPlayer, List<BlockPos>>> TRAP_FINDERS = new ArrayList<>();

	/** A blow to be echoed: whose, on whom, when (game time) and how hard. */
	private record Echo(UUID player, UUID victim, long due, float damage) {
	}

	/** A mirror step's double: the stand, when it goes, and the mobs it drew. */
	private record Mirror(ArmorStand stand, long until, List<Mob> drawn) {
	}

	/** A stillness: when it ends, and the arrows it holds. */
	private record Still(long until, Set<UUID> held) {
	}

	/** A fight under way: when a foe was last near, and what the spirit has decided to do in it. */
	private static final class Fight {
		long seen;
		boolean finishes, stills;

		Fight(long seen, boolean finishes, boolean stills) {
			this.seen = seen;
			this.finishes = finishes;
			this.stills = stills;
		}
	}

	/** Players whose echo strike is on. */
	private static final Set<UUID> ECHOING = new HashSet<>();
	/** Echoes waiting to land. */
	private static final List<Echo> ECHOES = new ArrayList<>();
	/** Mirror doubles, stillnesses and senses under way, by player. */
	private static final Map<UUID, Mirror> MIRRORS = new HashMap<>();
	private static final Map<UUID, Still> STILLS = new HashMap<>();
	private static final Map<UUID, Long> SENSES = new HashMap<>();
	/** Fights under way. */
	private static final Map<UUID, Fight> FIGHTS = new HashMap<>();
	/** Set while an echo lands, so it is not echoed in turn. */
	private static boolean echoing;
	/** {@code Lockpicking.locked}, which is not public (by reflection); null if it can't be reached. */

	/** What a small move does when it is cast. */
	@FunctionalInterface
	interface Cast {
		boolean cast(ServerPlayer player, SpiritState state);
	}

	/** One of its moves: its numbers and its cast. */
	private record Def(String id, float cost, int cooldown, int level, int pose, int intent, Cast cast) implements Move {
		@Override
		public boolean cast(ServerPlayer player, SpiritState state, int charge) {
			return cast.cast(player, state);
		}
	}

	/** Its own moves, after the shared hands. */
	public static final List<Move> MOVES = new ArrayList<>();

	static {
		MOVES.add(new Def("echo", ECHO_COST, 20, 1, Poses.ECHO, Mana.HARM, QuietOne::echoStrike));
		MOVES.add(new Def("sense", 8.0F, 200, 2, Poses.SENSE, Mana.WILL, QuietOne::sense));
		MOVES.add(new Def("mirror", 10.0F, 120, 3, Poses.MIRROR, Mana.WARD, QuietOne::mirrorStep));
		MOVES.add(new Def("still", 30.0F, 1200, 6, Poses.STILL, Mana.WARD, QuietOne::stillness));
		MOVES.add(new Def("silent", 40.0F, 24000, 8, Poses.SILENT, Mana.HARM, QuietOne::silentEnd));
	}

	QuietOne() {
		super(Spirits.QUIET, "quiet", 0xFFC8C8DC, 1.0F);
	}

	static void register() {
		for (Move move : MOVES) {
			Moves.register(move);
		}
		JaymosKingdoms.LOGGER.debug("quiet one's attachment: {}", SPOKEN);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(QuietOne::struck);
		ServerTickEvents.END_SERVER_TICK.register(QuietOne::tickAll);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			// (a double left in the world by a crash: it was never meant to be kept)
			if (entity instanceof ArmorStand && entity.entityTags().contains(MIRROR_TAG) && !tracked(entity)) {
				level.getServer().execute(entity::discard);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Mirror mirror : MIRRORS.values()) {
				mirror.stand().discard();
			}
			endAllStills(server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ECHOING.clear();
			ECHOES.clear();
			MIRRORS.clear();
			STILLS.clear();
			SENSES.clear();
			FIGHTS.clear();
			echoing = false;
		});
	}

	/** A player left: forget what is kept for them (and take their double away). */
	static void dropState(UUID id) {
		ECHOING.remove(id);
		ECHOES.removeIf(e -> e.player().equals(id));
		Mirror mirror = MIRRORS.remove(id);
		if (mirror != null) {
			mirror.stand().discard();
		}
		STILLS.remove(id);
		SENSES.remove(id);
		FIGHTS.remove(id);
	}

	@Override
	public List<Move> moves() {
		return MOVES;
	}

	// ---- size and silence -------------------------------------------------------------------------------------------

	/** The size (times 100) it is conjured at this time: anywhere from {@value #SCALE_MIN} to {@value #SCALE_MAX}. */
	static int rollScale(ServerPlayer player) {
		return SCALE_MIN + player.getRandom().nextInt(SCALE_MAX - SCALE_MIN + 1);
	}

	/** Whether the Quiet One may say {@code event} to {@code player}: its sick dots, and its one word at full bond, once. */
	static boolean mayWhisper(ServerPlayer player, String event) {
		if (event.startsWith("sick.")) {
			return true;
		}
		if (!event.equals("word")) {
			return false;
		}
		SpiritState state = Spirits.state(player);
		return state != null && state.kind() == Spirits.QUIET && state.bond() >= Spirits.MAX_BOND && !player.getAttachedOrElse(SPOKEN, false);
	}

	/** The word has been said. */
	static void spoke(ServerPlayer player) {
		player.setAttached(SPOKEN, true);
	}

	// ---- on its own -------------------------------------------------------------------------------------------------

	/**
	 * Every ten ticks while it's out. At full bond the word is said (once). From bond 20, rarely: when a fight starts it
	 * decides, one in {@value #FINISH_ODDS} to finish it (a silent end on a foe under 30% within six blocks) and one in
	 * {@value #STILL_ODDS} to hold the world still.
	 */
	@Override
	public void tick(ServerPlayer player, SpiritState state, @Nullable LivingEntity threat, long now) {
		UUID id = player.getUUID();
		if (state.bond() >= Spirits.MAX_BOND && !player.getAttachedOrElse(SPOKEN, false)) {
			SpiritVoice.sayNow(player, Spirits.QUIET, "word");
		}
		Fight fight = FIGHTS.get(id);
		if (threat == null || threat.distanceTo(player) > FIGHT_RANGE) {
			return;
		}
		if (fight == null || now - fight.seen > FIGHT_GAP) {
			fight = new Fight(now, player.getRandom().nextInt(FINISH_ODDS) == 0, player.getRandom().nextInt(STILL_ODDS) == 0);
			FIGHTS.put(id, fight);
		}
		fight.seen = now;
		if (fight.finishes && cooled(player, "silent", now)) {
			LivingEntity weak = weakFoe(player, SILENT_AUTO_REACH, false);
			if (weak != null && Spirits.act(player, Spirits.QUIET, Spirits.FLASH_ACT, SILENT_AUTO_COST)) {
				cool(player, "silent", now, SILENT_AUTO_EVERY);
				fight.finishes = false;
				finish(player, weak);
			}
		}
		if (fight.stills && cooled(player, "still", now) && !STILLS.containsKey(id)
			&& Spirits.act(player, Spirits.QUIET, Spirits.FLASH_WARD, STILL_AUTO_COST)) {
			cool(player, "still", now, STILL_AUTO_EVERY);
			fight.stills = false;
			holdStill(player);
		}
	}

	// ---- echo strike ------------------------------------------------------------------------------------------------

	private static boolean echoStrike(ServerPlayer player, SpiritState state) {
		if (ECHOING.remove(player.getUUID())) {
			Spirits.toggle(player, SpiritState.ECHO, false);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.echo.off").withStyle(ChatFormatting.GRAY));
			return false;
		}
		ECHOING.add(player.getUUID());
		Spirits.toggle(player, SpiritState.ECHO, true);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.echo.on").withStyle(ChatFormatting.WHITE));
		return true;
	}

	/** A blow landed: if it was a keeper's melee blow with the echo on, the arms will repeat it. */
	private static void struck(LivingEntity victim, DamageSource source, float base, float taken, boolean blocked) {
		if (echoing || taken <= 0.0F || blocked || !(source.getEntity() instanceof ServerPlayer player) || victim == player
			|| !source.is(DamageTypes.PLAYER_ATTACK) || !ECHOING.contains(player.getUUID()) || !Spirits.out(player)) {
			return;
		}
		ECHOES.add(new Echo(player.getUUID(), victim.getUUID(), player.level().getGameTime() + ECHO_DELAY, taken * ECHO_SHARE));
	}

	private static void tickEchoes(MinecraftServer server) {
		if (ECHOES.isEmpty()) {
			return;
		}
		for (Iterator<Echo> it = ECHOES.iterator(); it.hasNext();) {
			Echo echo = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(echo.player());
			if (player == null || !player.isAlive() || !Spirits.out(player)) {
				it.remove();
				continue;
			}
			if (player.level().getGameTime() < echo.due()) {
				continue;
			}
			it.remove();
			SpiritState state = Spirits.state(player);
			if (state == null || !(player.level() instanceof ServerLevel level) || !(level.getEntity(echo.victim()) instanceof LivingEntity victim)
				|| !victim.isAlive() || victim.distanceTo(player) > ECHO_LOSE || !SpiritFoes.foe(player, victim, true)
				|| !Mana.pay(player, ECHO_COST, Mana.HARM)) {
				continue;
			}
			echoing = true;
			try {
				SpiritFoes.strike(level, player, victim, echo.damage() * Spirits.damageScale(state));
			} finally {
				echoing = false;
			}
			Spirits.show(player, Poses.ECHO);
			Vec3 at = victim.getBoundingBox().getCenter();
			level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.2);
			level.sendParticles(ParticleTypes.WHITE_ASH, at.x, at.y, at.z, 8, 0.3, 0.3, 0.3, 0.02);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.7F, 0.5F);
		}
	}

	// ---- mirror step ------------------------------------------------------------------------------------------------

	private static boolean mirrorStep(ServerPlayer player, SpiritState state) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		Vec3 look = player.getLookAngle();
		Vec3 fwd = new Vec3(look.x, 0.0, look.z);
		fwd = fwd.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : fwd.normalize();
		net.minecraft.world.entity.player.Input input = player.getLastClientInput();
		Vec3 dir = input.left() && !input.right() ? new Vec3(fwd.z, 0.0, -fwd.x)
			: input.right() && !input.left() ? new Vec3(-fwd.z, 0.0, fwd.x) : fwd.scale(-1.0);
		Vec3 from = player.position();
		Vec3 to = null;
		for (double d = MIRROR_STEP; d >= 1.0 && to == null; d -= 0.5) {
			for (int dy : new int[] {0, 1, -1}) {
				if (landing(level, player, from.x + dir.x * d, from.y + dy, from.z + dir.z * d)) {
					to = new Vec3(from.x + dir.x * d, from.y + dy, from.z + dir.z * d);
					break;
				}
			}
		}
		if (to == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.mirror.none").withStyle(ChatFormatting.GRAY));
			return false;
		}
		Mirror old = MIRRORS.remove(player.getUUID());
		if (old != null) {
			endMirror(player, old);
		}
		ArmorStand stand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.TRIGGERED);
		if (stand == null) {
			return false;
		}
		stand.addTag(MIRROR_TAG);
		stand.snapTo(from.x, from.y, from.z, player.getYRot(), 0.0F);
		stand.setNoGravity(true);
		stand.setPermanentlyInvulnerable(true);
		stand.setShowArms(true);
		stand.setGlowingTag(true);
		level.addFreshEntity(stand);
		MIRRORS.put(player.getUUID(), new Mirror(stand, level.getGameTime() + MIRROR_TICKS, new ArrayList<>()));
		aimMobs(player, MIRRORS.get(player.getUUID()));
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1.0, from.z, 24, 0.3, 0.6, 0.3, 0.05);
		Mana.sendSeen(level, Mana.MOTE, from.x, from.y + 1.0, from.z, 18, 0.3, 0.6, 0.3, 0.04);
		player.teleportTo(to.x, to.y, to.z);
		level.sendParticles(ParticleTypes.WHITE_ASH, to.x, to.y + 1.0, to.z, 12, 0.3, 0.6, 0.3, 0.02);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5F, 1.6F);
		return true;
	}

	/** Whether {@code player} could stand at this spot: room for them, no lava or fire, something solid underfoot. */
	private static boolean landing(ServerLevel level, ServerPlayer player, double x, double y, double z) {
		Vec3 pos = player.position();
		AABB box = player.getBoundingBox().move(x - pos.x, y - pos.y, z - pos.z);
		if (!level.noCollision(player, box)) {
			return false;
		}
		BlockPos feet = BlockPos.containing(x, y, z), below = feet.below();
		for (BlockPos at : new BlockPos[] {feet, feet.above()}) {
			if (level.getBlockState(at).is(BlockTags.FIRE) || level.getFluidState(at).is(FluidTags.LAVA)) {
				return false;
			}
		}
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && !level.getFluidState(below).is(FluidTags.LAVA);
	}

	/** The mobs hunting {@code player} near the double turn on it. */
	private static void aimMobs(ServerPlayer player, Mirror mirror) {
		ArmorStand stand = mirror.stand();
		for (Mob mob : stand.level().getEntitiesOfClass(Mob.class, stand.getBoundingBox().inflate(MIRROR_DRAW),
			m -> m.isAlive() && (m.getTarget() == player || mirror.drawn().contains(m)))) {
			mob.setTarget(stand);
			if (!mirror.drawn().contains(mob)) {
				mirror.drawn().add(mob);
			}
		}
	}

	/** The double is gone: it goes, and the mobs it drew look for the player again. */
	private static void endMirror(@Nullable ServerPlayer player, Mirror mirror) {
		ArmorStand stand = mirror.stand();
		if (stand.level() instanceof ServerLevel level && stand.isAlive()) {
			level.sendParticles(ParticleTypes.WHITE_ASH, stand.getX(), stand.getY() + 1.0, stand.getZ(), 14, 0.3, 0.6, 0.3, 0.02);
		}
		stand.discard();
		for (Mob mob : mirror.drawn()) {
			if (mob.isAlive() && mob.getTarget() == stand) {
				mob.setTarget(player != null && player.isAlive() ? player : null);
			}
		}
	}

	private static boolean tracked(Entity entity) {
		for (Mirror mirror : MIRRORS.values()) {
			if (mirror.stand() == entity) {
				return true;
			}
		}
		return false;
	}

	// ---- stillness --------------------------------------------------------------------------------------------------

	private static boolean stillness(ServerPlayer player, SpiritState state) {
		holdStill(player);
		return true;
	}

	/** The world holds its breath round {@code player} for {@value #STILL_TICKS} ticks. */
	private static void holdStill(ServerPlayer player) {
		ServerLevel level = (ServerLevel)player.level();
		Still old = STILLS.put(player.getUUID(), new Still(level.getGameTime() + STILL_TICKS, new HashSet<>()));
		if (old != null) {
			release(level, old);
		}
		player.setAttached(Spirits.FLASH, level.getGameTime() * 8L + Spirits.FLASH_WARD);
		level.sendParticles(ParticleTypes.WHITE_ASH, player.getX(), player.getY() + 1.0, player.getZ(), 60, STILL_RANGE * 0.4, 1.0, STILL_RANGE * 0.4, 0.01);
		Mana.sendSeen(level, Mana.MOTE, player.getX(), player.getY() + 1.0, player.getZ(), 40, STILL_RANGE * 0.4, 1.0, STILL_RANGE * 0.4, 0.01);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.5F, 0.5F);
	}

	private static void tickStill(ServerPlayer owner, Still still, long now) {
		ServerLevel level = (ServerLevel)owner.level();
		AABB box = owner.getBoundingBox().inflate(STILL_RANGE);
		double range2 = STILL_RANGE * STILL_RANGE;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && m.distanceToSqr(owner) <= range2 && !HandMoves.ally(owner, m))) {
			mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 5, STILL_SLOW, true, false, false));
			mob.setTarget(null);
			mob.getNavigation().stop();
		}
		for (Projectile arrow : level.getEntitiesOfClass(Projectile.class, box, p -> p.isAlive() && p.distanceToSqr(owner) <= range2 && p.getOwner() != owner)) {
			arrow.setDeltaMovement(Vec3.ZERO);
			arrow.setNoGravity(true);
			arrow.syncVelocity = true;
			still.held().add(arrow.getUUID());
		}
		if (now % 12 == 0) {
			level.sendParticles(ParticleTypes.WHITE_ASH, owner.getX(), owner.getY() + 1.0, owner.getZ(), 6, STILL_RANGE * 0.4, 1.0, STILL_RANGE * 0.4, 0.0);
		}
		holdPose(owner, Poses.STILL, now);
	}

	/** The arrows held fall (or fly) again. */
	private static void release(ServerLevel level, Still still) {
		for (UUID id : still.held()) {
			if (level.getEntity(id) instanceof Projectile arrow) {
				arrow.setNoGravity(false);
			}
		}
	}

	private static void endAllStills(MinecraftServer server) {
		for (Map.Entry<UUID, Still> entry : STILLS.entrySet()) {
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			if (owner != null && owner.level() instanceof ServerLevel level) {
				release(level, entry.getValue());
			}
		}
	}

	// ---- silent end -------------------------------------------------------------------------------------------------

	private static boolean silentEnd(ServerPlayer player, SpiritState state) {
		LivingEntity weak = weakFoe(player, SILENT_REACH, true);
		if (weak == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.silent.none").withStyle(ChatFormatting.GRAY));
			return false;
		}
		finish(player, weak);
		return true;
	}

	/** The foe in sight (or, on its own, the nearest) within {@code reach} that is under {@link #SILENT_BELOW} health and no boss. */
	private static @Nullable LivingEntity weakFoe(ServerPlayer player, double reach, boolean sight) {
		List<LivingEntity> candidates = new ArrayList<>();
		LivingEntity seen = sight ? Arcana.sighted(player) : null;
		if (seen != null) {
			candidates.add(seen);
		}
		candidates.addAll(SpiritFoes.cone(player, reach, sight ? 25.0 : 180.0, false));
		for (LivingEntity e : candidates) {
			if (e.isAlive() && e.distanceTo(player) - e.getBbWidth() * 0.5 <= reach && SpiritFoes.foe(player, e, sight)
				&& e.getHealth() < e.getMaxHealth() * SILENT_BELOW && !boss(e)) {
				return e;
			}
		}
		return null;
	}

	/** Whether {@code e} is a boss: the dragon, the Wither, and the lords and giants the boss bars are for. */
	static boolean boss(Entity e) {
		return e instanceof EnderDragon || e instanceof WitherBoss || BossBars.boss(e);
	}

	/** One blow, and the foe is dead; the arms go still. */
	private static void finish(ServerPlayer player, LivingEntity foe) {
		ServerLevel level = (ServerLevel)player.level();
		Vec3 at = foe.getBoundingBox().getCenter();
		level.sendParticles(ParticleTypes.SCULK_SOUL, at.x, at.y, at.z, 24, 0.3, 0.5, 0.3, 0.04);
		level.sendParticles(ParticleTypes.WHITE_ASH, at.x, at.y, at.z, 30, 0.4, 0.6, 0.4, 0.05);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.8F, 1.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.8F, 0.4F);
		SpiritFoes.strike(level, player, foe, Math.max(1.0E4F, foe.getMaxHealth() * 100.0F));
		if (foe.isAlive()) {
			foe.kill(level);
		}
		Spirits.show(player, Poses.SILENT);
	}

	// ---- sense ------------------------------------------------------------------------------------------------------

	private static boolean sense(ServerPlayer player, SpiritState state) {
		SENSES.put(player.getUUID(), player.level().getGameTime() + SENSE_TICKS);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.spirit.sense.on").withStyle(ChatFormatting.WHITE));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 0.8F, 0.7F);
		pulse(player);
		return true;
	}

	/** One pulse of the senses: motes, seen only by {@code player}. */
	private static void pulse(ServerPlayer player) {
		ServerLevel level = (ServerLevel)player.level();
		// hidden mobs: out of sight behind something, or unseen
		double reach2 = SENSE_MOBS * SENSE_MOBS;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SENSE_MOBS),
			m -> m != player && m.isAlive() && !(m instanceof ArmorStand) && m.distanceToSqr(player) <= reach2 && (m.isInvisible() || !player.hasLineOfSight(m)))) {
			Vec3 c = e.getBoundingBox().getCenter();
			mote(level, player, 0xFF7070, c.x, c.y, c.z, 3, e.getBbWidth() * 0.3, e.getBbHeight() * 0.3);
		}
		// rune traps
		for (Function<ServerPlayer, List<BlockPos>> finder : TRAP_FINDERS) {
			for (BlockPos pos : finder.apply(player)) {
				if (pos.distSqr(player.blockPosition()) <= SENSE_CHESTS * SENSE_CHESTS) {
					ring(level, player, 0xC090FF, pos);
				}
			}
		}
		// locked chests
		int r = (int)Math.ceil(SENSE_CHESTS);
		BlockPos here = player.blockPosition();
		for (int cx = (here.getX() - r) >> 4; cx <= (here.getX() + r) >> 4; cx++) {
			for (int cz = (here.getZ() - r) >> 4; cz <= (here.getZ() + r) >> 4; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
					BlockPos pos = be.getBlockPos();
					if ((be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) && pos.distSqr(here) <= SENSE_CHESTS * SENSE_CHESTS
						&& locked(level, pos)) {
						ring(level, player, 0xFFD060, pos);
					}
				}
			}
		}
		// ores
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dx = -SENSE_ORES; dx <= SENSE_ORES; dx++) {
			for (int dy = -SENSE_ORES; dy <= SENSE_ORES; dy++) {
				for (int dz = -SENSE_ORES; dz <= SENSE_ORES; dz++) {
					at.set(here.getX() + dx, here.getY() + dy, here.getZ() + dz);
					if (dx * dx + dy * dy + dz * dz <= SENSE_ORES * SENSE_ORES && sensed(level.getBlockState(at))) {
						mote(level, player, 0x60FFC0, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 1, 0.1, 0.1);
					}
				}
			}
		}
	}

	/** Iron, gold, diamond, emerald, amethyst and copper. */
	private static boolean sensed(BlockState state) {
		if (state.isAir()) {
			return false;
		}
		Block block = state.getBlock();
		return state.is(BlockTags.IRON_ORES) || state.is(BlockTags.GOLD_ORES) || state.is(BlockTags.COPPER_ORES)
			|| block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE
			|| block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE
			|| block == Blocks.AMETHYST_CLUSTER || block == Blocks.BUDDING_AMETHYST || block == Blocks.AMETHYST_BLOCK;
	}

	/** Whether the container at {@code pos} is a locked loot chest. */
	private static boolean locked(ServerLevel level, BlockPos pos) {
		return Lockpicking.lockLevel(level, pos) > 0;
	}

	private static void mote(ServerLevel level, ServerPlayer player, int colour, double x, double y, double z, int count, double spreadXZ, double spreadY) {
		ParticleOptions dust = new DustParticleOptions(colour, 1.0F);
		level.sendParticles(player, dust, false, false, x, y, z, count, spreadXZ, spreadY, spreadXZ, 0.0);
	}

	/** A ring of motes round the block at {@code pos}. */
	private static void ring(ServerLevel level, ServerPlayer player, int colour, BlockPos pos) {
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4.0;
			mote(level, player, colour, pos.getX() + 0.5 + Math.cos(a) * 0.8, pos.getY() + 0.5, pos.getZ() + 0.5 + Math.sin(a) * 0.8, 1, 0.02, 0.02);
		}
	}

	// ---- every tick -------------------------------------------------------------------------------------------------

	private static void tickAll(MinecraftServer server) {
		tickEchoes(server);
		if (ECHOING.isEmpty() && MIRRORS.isEmpty() && STILLS.isEmpty() && SENSES.isEmpty()) {
			return;
		}
		for (Iterator<UUID> it = ECHOING.iterator(); it.hasNext();) {
			ServerPlayer owner = server.getPlayerList().getPlayer(it.next());
			if (owner == null || !owner.isAlive() || !Spirits.out(owner)) {
				it.remove();
				if (owner != null) {
					Spirits.toggle(owner, SpiritState.ECHO, false);
				}
			}
		}
		for (Iterator<Map.Entry<UUID, Mirror>> it = MIRRORS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Mirror> entry = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			Mirror mirror = entry.getValue();
			if (owner == null || !owner.isAlive() || mirror.stand().isRemoved() || owner.level() != mirror.stand().level()
				|| owner.level().getGameTime() >= mirror.until()) {
				endMirror(owner, mirror);
				it.remove();
			} else if (owner.level().getGameTime() % MIRROR_AIM_EVERY == 0) {
				aimMobs(owner, mirror);
			}
		}
		for (Iterator<Map.Entry<UUID, Still>> it = STILLS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Still> entry = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			if (owner == null || !owner.isAlive()) {
				it.remove();
				continue;
			}
			long now = owner.level().getGameTime();
			if (now >= entry.getValue().until()) {
				release((ServerLevel)owner.level(), entry.getValue());
				it.remove();
			} else {
				tickStill(owner, entry.getValue(), now);
			}
		}
		for (Iterator<Map.Entry<UUID, Long>> it = SENSES.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Long> entry = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			if (owner == null || !owner.isAlive() || !Spirits.out(owner) || owner.level().getGameTime() >= entry.getValue()) {
				it.remove();
				continue;
			}
			long now = owner.level().getGameTime();
			if (now % SENSE_EVERY == 0) {
				pulse(owner);
			}
			holdPose(owner, Poses.SENSE, now);
		}
	}

	/** Keeps the arms in {@code pose} while a long move is under way (the client lets a pose go after 16 ticks). */
	private static void holdPose(ServerPlayer owner, int pose, long now) {
		if (now % 12 != 0) {
			return;
		}
		SpiritState state = Spirits.state(owner);
		if (state != null && (state.pose() == Poses.IDLE || state.pose() == pose)) {
			Spirits.show(owner, pose);
		}
	}
}
