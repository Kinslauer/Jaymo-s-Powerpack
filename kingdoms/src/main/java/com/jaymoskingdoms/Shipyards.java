package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Crew;
import com.jaymoskingdoms.sailing.Fleet;
import com.jaymoskingdoms.sailing.Ship;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The yards (Andy, 2026-11-15: "I'd like to see a shipwright job and mca villager that you can see assembling them at a
 * kingdom's dock, if we can get one large enough for whaling that would give a great way to unlock that feature/start").
 * <p>
 * A realm's ships are its own shipwright's work ({@link Navies}' harbour keeps him on its pier). When it wants a
 * warship or a whaler, he lays her down on the water by the pier end ({@link Fleet.Slip}): her keel first, then up
 * through her hull and decks, her masts and rigging as they come, her helm last. He walks the work with his axe and puts
 * each block in by hand, a few to a beat, in daylight and at peace, so the whole harbour watches her grow over the days.
 * Done, he gives the sea a fish over her bow and she's launched with her crew aboard, the realm's own.
 * <ul>
 * <li>A realm goes whaling ({@link Whalers}) only once its yard has launched a whaler; one lost (sunk, or never home)
 * is laid down again. A life story's whaling start still finds its whaler afloat.</li>
 * <li>A lost warship is built again here, once it's been long enough ({@link Navies#REBUILD_TICKS}).</li>
 * <li>One ship on the slip at a time. Progress is saved with the harbour's realm; a block someone took out is put back.</li>
 * <li>Anyone who brings him timber ("Talk about work", {@link ShipTalk}) hurries the work ({@link #HELP_EXTRA} more blocks a
 * beat for each block's worth) and is remembered for it by the realm. Leaving him alone costs nobody anything.</li>
 * </ul>
 */
public final class Shipyards {
	/** What's on the slip. */
	static final String WARSHIP = "warship", WHALER = "whaler";
	/** A beat: this many ticks between his blocks, and up to this many more; this many blocks a beat (more for timber brought). */
	static final int BEAT_TICKS = 24, BEAT_JITTER = 12, BLOCKS_A_BEAT = 3, HELP_EXTRA = 3;
	/** Blocks already in skipped in one go. */
	static final int SKIP_PER_TICK = 32;
	/** He works this near the block going in (across, up or down); from the pier end this far, while there's nowhere nearer to stand. */
	static final double REACH = 5.0, UP_REACH = 7.0, PIER_REACH = 40.0;
	/** Where he stands: found this near the block, no further than this from it. */
	static final int STAND_RADIUS = 4;
	static final double STAND_NEAR = 7.0;
	/** Walking: this fast, a new path this often; no nearer for this long and he's moved there in a puff of smoke. */
	static final double WALK_SPEED = 0.7;
	static final int REPATH_TICKS = 20, STUCK_TICKS = 200;
	/** Hurt this recently, he's left to run. */
	static final int FLEE_TICKS = 100;
	/** Timber brought: a plank's worth of help, a log's; the most taken at once (blocks' worth); the realm's thanks, one for this many. */
	static final int PLANK = 1, LOG = 4, TIMBER_MOST = 128, TIMBER_PER_REP = 16, REP_MOST = 6;
	/** Launch tried this often once she's all in (ticks). */
	static final int LAUNCH_TRY = 100;
	/** Out of every player's reach the work goes on unseen at his daylight pace, reckoned this often (ticks). */
	static final int UNSEEN_TICKS = 400;

	/** A realm's slip: what's on it, her rig, where she lies and which way, how many blocks are in, timber brought, and when she was begun. */
	record Yard(String purpose, String rig, BlockPos origin, Direction bow, int placed, int help, long begun) {
		static final Codec<Yard> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("purpose").forGetter(Yard::purpose),
				Codec.STRING.fieldOf("rig").forGetter(Yard::rig),
				BlockPos.CODEC.fieldOf("origin").forGetter(Yard::origin),
				Direction.CODEC.fieldOf("bow").forGetter(Yard::bow),
				Codec.INT.optionalFieldOf("placed", 0).forGetter(Yard::placed),
				Codec.INT.optionalFieldOf("help", 0).forGetter(Yard::help),
				Codec.LONG.optionalFieldOf("begun", 0L).forGetter(Yard::begun))
			.apply(i, Yard::new));

		Yard at(int placed, int help) {
			return new Yard(this.purpose, this.rig, this.origin, this.bow, placed, help, this.begun);
		}

		Fleet.Rig kind() {
			return Fleet.Rig.valueOf(this.rig.toUpperCase(java.util.Locale.ROOT));
		}
	}

	/** Every realm's slip (realm id → it), kept on the Overworld. */
	static final AttachmentType<Map<String, Yard>> YARDS = AttachmentRegistry.create(JaymosKingdoms.id("shipyards"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Yard.CODEC)));
	/** Realm id → whether it has a whaler of its own (false: she was lost); kept on the Overworld. */
	static final AttachmentType<Map<String, Boolean>> WHALERS = AttachmentRegistry.create(JaymosKingdoms.id("whalers_built"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.BOOL)));

	/** What a shipwright is doing (not saved). */
	private static final class Job {
		@Nullable BlockPos goal;
		double best = Double.MAX_VALUE;
		long improvedAt, repathAt, actAt;
	}

	private static final Map<UUID, Job> JOBS = new HashMap<>();
	/** Realms whose slip has been brought up to date in the world since it was last out of reach. */
	private static final java.util.Set<Integer> SHOWN = new java.util.HashSet<>();

	private Shipyards() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			JOBS.clear();
			SHOWN.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(Shipyards::unseen);
	}

	/**
	 * The yards no player is near: the work goes on there unseen (by day, at peace), so a realm far from everyone still
	 * gets its ship built; what was done is put in the world when someone comes ({@link #catchUp}). She's launched only
	 * with someone there to see it.
	 */
	private static void unseen(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		if (level.getGameTime() % UNSEEN_TICKS != 0 || Homes.bedtime(level)) {
			return;
		}
		Map<String, Yard> all = level.getAttachedOrElse(YARDS, Map.of());
		for (Map.Entry<String, Yard> e : all.entrySet()) {
			int realm = Integer.parseInt(e.getKey());
			Yard y = e.getValue();
			Faction f = Factions.get(server, realm);
			if (f == null || level.isPositionEntityTicking(y.origin())) {
				continue;
			}
			SHOWN.remove(realm);
			if (f.fallen() || !Economy.atPeace(server, f) || Navies.harbour(server, realm) == null) {
				continue;
			}
			int size = slip(level, f, y).size();
			int beats = UNSEEN_TICKS / (BEAT_TICKS + BEAT_JITTER / 2);
			int placed = Math.min(size, y.placed() + beats * (BLOCKS_A_BEAT + (y.help() > 0 ? HELP_EXTRA : 0)));
			if (placed != y.placed()) {
				put(server, realm, y.at(placed, Math.max(0, y.help() - (placed - y.placed()))));
			}
		}
	}

	/** Whatever was done on {@code y} unseen put in the world, quietly, the first time she's in reach again. */
	private static void catchUp(ServerLevel level, int realm, Yard y, Fleet.Slip slip) {
		if (!SHOWN.add(realm)) {
			return;
		}
		for (int i = 0; i < Math.min(y.placed(), slip.size()); i++) {
			if (!slip.laid(level, i)) {
				slip.place(level, i);
			}
		}
	}

	static @Nullable Yard yard(MinecraftServer server, int realm) {
		return Factions.level(server).getAttachedOrElse(YARDS, Map.of()).get(Integer.toString(realm));
	}

	private static void put(MinecraftServer server, int realm, @Nullable Yard yard) {
		ServerLevel level = Factions.level(server);
		Map<String, Yard> all = new HashMap<>(level.getAttachedOrElse(YARDS, Map.of()));
		if (yard == null) {
			all.remove(Integer.toString(realm));
		} else {
			all.put(Integer.toString(realm), yard);
		}
		level.setAttached(YARDS, Map.copyOf(all));
	}

	// ---- whalers of its own ------------------------------------------------------------------------------------------

	/**
	 * Whether {@code realm} has a whaler of its own to go whaling in. A realm that whaled before there were yards (it has
	 * a voyage on record) counts as having one.
	 */
	static boolean hasWhaler(MinecraftServer server, int realm) {
		ServerLevel level = Factions.level(server);
		Boolean built = level.getAttachedOrElse(WHALERS, Map.of()).get(Integer.toString(realm));
		if (built != null) {
			return built;
		}
		return level.getAttachedOrElse(Whalers.LAST, Map.of()).containsKey(Integer.toString(realm)) || Whalers.voyage(server, realm) != null;
	}

	/** {@code realm} has a whaler afloat (launched from its yard, or fitted out for a story), or (false) lost her. */
	static void whaler(MinecraftServer server, int realm, boolean has) {
		ServerLevel level = Factions.level(server);
		Map<String, Boolean> all = new HashMap<>(level.getAttachedOrElse(WHALERS, Map.of()));
		all.put(Integer.toString(realm), has);
		level.setAttached(WHALERS, Map.copyOf(all));
	}

	// ---- laying her down ---------------------------------------------------------------------------------------------

	/**
	 * {@code f}'s shipwright lays a {@code purpose} ship down at harbour {@code h} (her rig {@code rig}), if the slip is
	 * free and there's room by the pier. True if she's on the slip (now or already).
	 */
	static boolean lay(ServerLevel level, Faction f, Navies.Harbour h, String purpose, Fleet.Rig rig) {
		MinecraftServer server = level.getServer();
		Yard y = yard(server, f.id());
		if (y != null) {
			return y.purpose().equals(purpose);
		}
		if (!level.isPositionEntityTicking(h.mooring())) {
			return false;
		}
		Direction bow = bow(h);
		RandomSource random = level.getRandom();
		for (int tries = 0; tries < 4; tries++) {
			BlockPos water = tries == 0 ? h.mooring() : Fleet.openSea(level, h.mooring(), 24, 4, random);
			Fleet.Slip slip = water == null ? null : Fleet.slip(level, rig, look(f, purpose), water, bow);
			if (slip != null) {
				put(server, f.id(), new Yard(purpose, rig.id(), slip.origin, bow, 0, 0, level.getGameTime()));
				Raids.tell(level, h.shore(), Component.translatable("message.jaymoskingdoms.shipyard.laid." + purpose, f.name())
					.withStyle(ChatFormatting.AQUA));
				JaymosKingdoms.LOGGER.info("{}'s shipwright lays down a {} ({}) at {}", f.name(), purpose, rig.id(), slip.origin);
				return true;
			}
		}
		return false;
	}

	/** Her bow along the shore, as the harbour's ships lie ({@link Navies#launch}). */
	private static Direction bow(Navies.Harbour h) {
		return Direction.getApproximateNearest(h.mooring().getX() - h.shore().getX(), 0, h.mooring().getZ() - h.shore().getZ()).getClockWise();
	}

	/** What a {@code purpose} ship of {@code f}'s is built of. */
	static Fleet.Look look(Faction f, String purpose) {
		return WHALER.equals(purpose) ? Whalers.look(f) : Navies.warshipLook(f);
	}

	private static Fleet.Slip slip(ServerLevel level, Faction f, Yard y) {
		return Fleet.slipAt(level, y.kind(), look(f, y.purpose()), y.origin(), y.bow());
	}

	/** How far along {@code realm}'s ship on the slip is (0-100), or -1 with none. */
	static int progress(ServerLevel level, Faction f) {
		Yard y = yard(level.getServer(), f.id());
		if (y == null) {
			return -1;
		}
		int size = Math.max(1, slip(level, f, y).size());
		return Math.min(100, y.placed() * 100 / size);
	}

	/** {@code mob} is a harbour's shipwright, the one its realm's harbour knows (server side). */
	static boolean isWright(LivingEntity mob) {
		return mob instanceof Worker w && w.role() == Pirates.SHIPWRIGHT && mob.level() instanceof ServerLevel level
			&& Navies.isWright(level.getServer(), w.factionId(), mob);
	}

	/** Timber brought for {@code f}'s slip: {@code worth} blocks' worth of help. False with nothing on the slip. */
	static boolean help(MinecraftServer server, Faction f, int worth) {
		Yard y = yard(server, f.id());
		if (y == null) {
			return false;
		}
		put(server, f.id(), y.at(y.placed(), y.help() + worth));
		return true;
	}

	// ---- at the work (the shipwright's step, from Workers) -----------------------------------------------------------

	/**
	 * A harbour's shipwright at his slip: true while he's at the work (his brain held), false to let him be a villager
	 * (nothing on the slip, night, a fight on, hurt, frightened, talking).
	 */
	static boolean step(ServerLevel level, Worker w) {
		PathfinderMob mob = w.mob();
		if (mob.isSleeping() || mob.isBaby() || Homes.bedtime(level) || McaCompat.busy(mob) || Downed.helping(mob) || Downed.isDowned(mob)
			|| Frights.afraid(mob.getUUID()) || mob.getLastHurtByMob() != null && mob.tickCount - mob.getLastHurtByMobTimestamp() < FLEE_TICKS) {
			return false;
		}
		MinecraftServer server = level.getServer();
		Faction f = Factions.get(server, w.factionId());
		if (f == null || f.fallen() || !Economy.atPeace(server, f) || !Navies.isWright(server, f.id(), mob)) {
			return false;
		}
		Navies.Harbour h = Navies.harbour(server, f.id());
		Yard y = yard(server, f.id());
		if (h == null || y == null || !level.isPositionEntityTicking(y.origin())) {
			return false;
		}
		Fleet.Slip slip = slip(level, f, y);
		catchUp(level, f.id(), y, slip);
		long now = level.getGameTime();
		// what's in already (by his hand, or put back after) is passed over
		int i = y.placed();
		for (int skipped = 0; i < slip.size() && skipped < SKIP_PER_TICK && slip.laid(level, i); skipped++) {
			i++;
		}
		if (i != y.placed()) {
			put(server, f.id(), y = y.at(i, y.help()));
		}
		Job job = JOBS.computeIfAbsent(mob.getUUID(), u -> new Job());
		if (i >= slip.size()) {
			// she's all in: launched as soon as she'll go
			if (now % LAUNCH_TRY == 0) {
				launch(level, f, h, y, slip, mob);
			}
			return true;
		}
		BlockPos target = slip.at(i);
		BlockPos stand = stand(level, target, h);
		boolean fromPier = stand.equals(pierStand(level, h));
		if (!approach(level, mob, job, stand, target, fromPier ? PIER_REACH : REACH, now)) {
			return true;
		}
		mob.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
		if (now < job.actAt) {
			return true;
		}
		job.actAt = now + BEAT_TICKS + level.getRandom().nextInt(BEAT_JITTER + 1);
		int help = y.help();
		int blocks = BLOCKS_A_BEAT + (help > 0 ? HELP_EXTRA : 0);
		int laid = 0;
		while (laid < blocks && i < slip.size()) {
			if (!slip.laid(level, i)) {
				if (!clearOf(level, slip.at(i), slip.state(i))) {
					// a player stands where it goes: he waits for them
					break;
				}
				slip.place(level, i);
				effects(level, slip.at(i), slip.state(i));
				laid++;
			}
			i++;
		}
		if (laid > 0) {
			mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		}
		put(server, f.id(), y.at(i, help > 0 ? Math.max(0, help - laid) : 0));
		return true;
	}

	/** Where he stands to put {@code target} in: on her (or the shore) near it, else the pier's end. */
	private static BlockPos stand(ServerLevel level, BlockPos target, Navies.Harbour h) {
		BlockPos s = Construction.standable(level, target.above(), STAND_RADIUS);
		if (footing(level, s) && s.distSqr(target) <= STAND_NEAR * STAND_NEAR) {
			return s;
		}
		return pierStand(level, h);
	}

	private static BlockPos pierStand(ServerLevel level, Navies.Harbour h) {
		return Construction.standable(level, h.pier().above(), 3);
	}

	/** Solid underfoot and dry (the surface over open water isn't). */
	private static boolean footing(ServerLevel level, BlockPos at) {
		BlockPos below = at.below();
		return level.getFluidState(at).isEmpty() && !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
	}

	/** Walks him to {@code stand}; true once he's within {@code reach} of {@code target}. Stuck too long, he's moved there in a puff of smoke. */
	private static boolean approach(ServerLevel level, PathfinderMob mob, Job job, BlockPos stand, BlockPos target, double reach, long now) {
		double tx = target.getX() + 0.5 - mob.getX(), tz = target.getZ() + 0.5 - mob.getZ();
		if (tx * tx + tz * tz <= reach * reach && Math.abs(target.getY() - mob.getY()) <= UP_REACH && footing(level, mob.blockPosition())) {
			mob.getNavigation().stop();
			return true;
		}
		if (!stand.equals(job.goal)) {
			job.goal = stand;
			job.best = Double.MAX_VALUE;
			job.improvedAt = now;
			job.repathAt = 0;
		}
		double d = mob.position().distanceTo(Vec3.atBottomCenterOf(stand));
		if (d < job.best - 0.5) {
			job.best = d;
			job.improvedAt = now;
		}
		if (now - job.improvedAt > STUCK_TICKS) {
			level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 1.0, mob.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
			mob.getNavigation().stop();
			mob.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
			level.sendParticles(ParticleTypes.POOF, stand.getX() + 0.5, stand.getY() + 1.0, stand.getZ() + 0.5, 12, 0.3, 0.5, 0.3, 0.02);
			job.best = Double.MAX_VALUE;
			job.improvedAt = now;
			return false;
		}
		if (now >= job.repathAt || mob.getNavigation().isDone()) {
			job.repathAt = now + REPATH_TICKS;
			BlockPos leg = Construction.leg(level, mob, stand);
			mob.getNavigation().moveTo(leg.getX() + 0.5, leg.getY(), leg.getZ() + 0.5, WALK_SPEED);
		}
		return false;
	}

	/** Nobody's in the way of a solid block going in at {@code at}: a player waits it out, anyone else is moved off it. */
	private static boolean clearOf(ServerLevel level, BlockPos at, BlockState state) {
		if (state.getCollisionShape(level, at).isEmpty()) {
			return true;
		}
		for (LivingEntity in : level.getEntitiesOfClass(LivingEntity.class, new AABB(at), LivingEntity::isAlive)) {
			if (in instanceof Player) {
				return false;
			}
			BlockPos out = Construction.standable(level, at.above(), 3);
			Attention.carry(() -> in.teleportTo(out.getX() + 0.5, out.getY(), out.getZ() + 0.5));
		}
		return true;
	}

	/** The knock of a block going in, and a few chips of it. */
	private static void effects(ServerLevel level, BlockPos at, BlockState state) {
		var sound = state.getSoundType();
		level.playSound(null, at, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
		if (!state.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 6, 0.25,
				0.25, 0.25, 0.05);
		}
	}

	// ---- the launch --------------------------------------------------------------------------------------------------

	/** She's all in: something given back to the sea over her bow, then away with her crew, the realm's own. */
	private static void launch(ServerLevel level, Faction f, Navies.Harbour h, Yard y, Fleet.Slip slip, PathfinderMob wright) {
		MinecraftServer server = level.getServer();
		Ship ship = switch (y.purpose()) {
			// (one whaler to a realm at a time: one fitted out for a life story, say, sails first and she waits)
			case WHALER -> Whalers.voyage(server, f.id()) != null ? null : Whalers.fitOut(level, f, h, slip);
			default -> Navies.commission(level, f, h, slip, y.kind());
		};
		if (ship == null) {
			if (!slip.laid(level, 0)) {
				// she wouldn't go and was taken up again: begun afresh
				put(server, f.id(), y.at(0, y.help()));
			}
			return;
		}
		put(server, f.id(), null);
		JOBS.remove(wright.getUUID());
		offering(level, wright, ship);
		Raids.tell(level, h.shore(), Component.translatable("message.jaymoskingdoms.shipyard.launched." + y.purpose(),
			ship.crew != null ? ship.crew.name : "", f.name()).withStyle(ChatFormatting.AQUA));
		JaymosKingdoms.LOGGER.info("{}'s shipwright launched a {} ({} blocks, {} ticks on the slip)", f.name(), y.purpose(), slip.size(),
			level.getGameTime() - y.begun());
	}

	/** (Shipwrights keep the Deep Mother: nothing of theirs goes afloat till something's gone back to her.) A fish over the side. */
	private static void offering(ServerLevel level, PathfinderMob wright, Entity ship) {
		Vec3 from = wright.getEyePosition();
		Vec3 to = ship.position();
		Vec3 toss = to.subtract(from).normalize().scale(0.45).add(0.0, 0.3, 0.0);
		ItemEntity fish = new ItemEntity(level, from.x, from.y, from.z, new ItemStack(Items.COD), toss.x, toss.y, toss.z);
		fish.makeFakeItem();
		level.addFreshEntity(fish);
		wright.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, wright.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 1.2F, 0.7F);
	}

	// ---- for the crews' launch ---------------------------------------------------------------------------------------

	/** The ship on {@code slip}, all in, cast off with {@code crew} and her company mustered: the ship, or null if she wouldn't go. */
	static @Nullable Ship launch(ServerLevel level, Faction f, Fleet.Slip slip, Crew crew, Corsairs.Complement complement) {
		BlockPos helm = slip.join(level);
		if (helm == null) {
			return null;
		}
		Ship ship = Fleet.launchLaid(level, helm, slip.where(), crew);
		if (ship != null) {
			Corsairs.man(level, ship, crew, f, complement);
		}
		return ship;
	}
}
