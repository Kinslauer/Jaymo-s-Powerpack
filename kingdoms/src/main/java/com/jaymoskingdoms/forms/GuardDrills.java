package com.jaymoskingdoms.forms;

import com.jaymoskingdoms.CourtWizards;
import com.jaymoskingdoms.Aging;
import com.jaymoskingdoms.Downed;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Post;
import com.jaymoskingdoms.Speech;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.366) Veteran guard drills (Weapon Forms, patch 1). A <b>veteran</b> is one of a realm's guards (its folk, or an MCA
 * villager in its service or in the job: {@link Downed#isGuard}) who has been grown {@link #VETERAN_DAYS} days or more
 * (the same service {@code WeaponForms.experienced} counts). Asked for a drill on MCA's main menu ({@link #ANSWER}, the way
 * {@code Captives} offers "Speak of the rescue"), a veteran sets one of three drills, rotating with the day:
 * <ul>
 * <li><b>Hold the Gate</b> ({@link Kind#GATE}): {@link #WAVE} practice zombies (weak, helmeted against the sun, never
 * dropping anything) come at you; the veteran stands by you. Defeat them all.</li>
 * <li><b>Ten Parries</b> ({@link Kind#PARRIES}): the veteran throws blows at you; raise a shield and turn {@link #PARRIES}
 * of them. A blow you fail to turn stings a little but never takes you under {@link #PARRY_SAFE} health.</li>
 * <li><b>Spar to the First Fall</b> ({@link Kind#SPAR}): the veteran spars you. Whoever would drop under
 * {@link #SPAR_FLOOR} of their health first falls: that blow is stopped, the spar ends and the veteran stands down. Neither
 * of you is ever killed.</li>
 * </ul>
 * Finishing one teaches a starter form if you know none ({@link Forms#STARTER_FORMS}, the first you don't know), else
 * practices the least-ranked form you know by {@link #PRACTICE}. One drill per veteran per in-game day per player (a drill
 * lost or abandoned doesn't spend the day). The veteran speaks in speech bubbles and the outcome comes in your Tidings, never
 * in chat. Town errands are bring-this notices ({@code TownNotices}) and can't carry a fight, so the offer is a talk-menu
 * answer instead.
 */
public final class GuardDrills {
	// ---- tuning ----
	/** Days grown before a guard is a veteran (as {@code WeaponForms.VETERAN_DAYS}). */
	static final int VETERAN_DAYS = 12;
	static final long DAY_TICKS = 24000L;
	/** How far (blocks) you may go from the veteran before the drill is called off. */
	static final double LEAVE = 28.0;
	/** The longest each drill lasts (ticks), and the share of health you must have to begin. */
	static final int GATE_TICKS = 3600, PARRY_TICKS = 3600, SPAR_TICKS = 1800;
	static final float START_HEALTH = 0.6F;
	/** Hold the Gate: foes in the wave, how far (blocks) from the veteran they appear, their health and their blow. */
	static final int WAVE = 5;
	static final double WAVE_MIN = 5.0, WAVE_MAX = 8.0;
	static final double FOE_HEALTH = 10.0, FOE_DAMAGE = 2.0;
	/** Ten Parries: blows to turn, how often the veteran strikes (ticks), the sting of one you miss, and the health it never takes you under. */
	static final int PARRIES = 10, PARRY_EVERY = 45;
	static final float PARRY_HARM = 2.0F, PARRY_SAFE = 8.0F;
	/** Spar: the share of most health whoever drops under it first has fallen, how often the veteran strikes (ticks), its harm. */
	static final float SPAR_FLOOR = 0.4F, SPAR_HARM = 3.0F;
	static final int SPAR_EVERY = 25;
	/** How close (blocks) the veteran must be to land a blow. */
	static final double REACH = 2.6;
	/** What a finished drill is worth to a form you know (practice). */
	static final int PRACTICE = 10;
	/** Scoreboard tag on the practice foes (so a stray one left by a crash is swept up on load). */
	static final String FOE_TAG = "jaymoskingdoms_drill_foe";
	/** The MCA {@code main} menu answer (our dialogue data): "Ask for a drill". */
	public static final String ANSWER = "jaymoskingdoms_drill";

	/** The three drills (offered in this order, rotating with the day). */
	public enum Kind {
		GATE, PARRIES, SPAR;

		String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		static @Nullable Kind of(String key) {
			for (Kind k : values()) {
				if (k.key().equals(key)) {
					return k;
				}
			}
			return null;
		}
	}

	/**
	 * A player's drills: the one under way (kind key, veteran, progress; empty kind: none) and the day number each veteran
	 * (UUID) last set them one that was finished.
	 */
	public record State(String kind, String guard, int progress, Map<String, Long> done) {
		public static final State NONE = new State("", "", 0, Map.of());
		public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.optionalFieldOf("kind", "").forGetter(State::kind),
				Codec.STRING.optionalFieldOf("guard", "").forGetter(State::guard),
				Codec.INT.optionalFieldOf("progress", 0).forGetter(State::progress),
				Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("done", Map.of()).forGetter(State::done))
			.apply(i, State::new));

		State idle() {
			return new State("", "", 0, done);
		}

		State under(Kind k, UUID guard, int progress) {
			return new State(k.key(), guard.toString(), progress, done);
		}

		State finished(UUID guard, long day) {
			Map<String, Long> next = new HashMap<>(done);
			next.put(guard.toString(), day);
			return new State("", "", 0, next);
		}
	}

	/** On a player: their drills. Saved, kept through death. */
	public static final AttachmentType<State> DRILLS = AttachmentRegistry.create(JaymosKingdoms.id("guard_drills"),
		builder -> builder.persistent(State.CODEC).copyOnDeath());

	/** A drill under way (in memory; the parries count is also kept in {@link State}). */
	private static final class Run {
		final Kind kind;
		final UUID guard;
		final long until;
		final List<Zombie> foes = new ArrayList<>();
		int defeated, parries;
		long nextBlow, lastParry = -1L;

		Run(Kind kind, UUID guard, long until) {
			this.kind = kind;
			this.guard = guard;
			this.until = until;
		}
	}

	private static final Map<UUID, Run> RUNS = new HashMap<>();

	private GuardDrills() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			// a practice foe left standing by a crash or a save in mid-drill
			if (entity instanceof Zombie && entity.entityTags().contains(FOE_TAG)) {
				entity.discard();
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> resume(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> drop(handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Run run : new ArrayList<>(RUNS.values())) {
				sweep(run);
			}
			RUNS.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(GuardDrills::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> allow(entity, source, amount));
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> !foeFell(entity));
	}

	// ---- who and when ------------------------------------------------------------------------------------------------

	/** A veteran: a realm's guard grown {@link #VETERAN_DAYS} days or more. */
	public static boolean isVeteran(LivingEntity who) {
		if (!(who instanceof PathfinderMob mob) || !(who instanceof Aging aging) || !Downed.isGuard(who) || !who.isAlive() || Downed.isDowned(who)) {
			return false;
		}
		if (who.level().isClientSide()) {
			return false;
		}
		long since = aging.grownSince();
		return since != CourtWizards.UNKNOWN && since != CourtWizards.CHILD
			&& (mob.level().getGameTime() - since) / DAY_TICKS >= VETERAN_DAYS;
	}

	private static long today(ServerLevel level) {
		return level.getGameTime() / DAY_TICKS;
	}

	private static State stateOf(Player player) {
		return player.getAttachedOrElse(DRILLS, State.NONE);
	}

	/** Whether "Ask for a drill" shows on {@code who}: a veteran who has no drill for {@code player} to finish today. */
	public static boolean canOffer(ServerPlayer player, LivingEntity who) {
		return isVeteran(who);
	}

	/** Whether a blow between {@code a} and {@code b} is a drill's spar (no crime). */
	public static boolean sparring(net.minecraft.world.entity.Entity a, net.minecraft.world.entity.Entity b) {
		if (RUNS.isEmpty()) {
			return false;
		}
		for (Map.Entry<UUID, Run> e : RUNS.entrySet()) {
			Run run = e.getValue();
			if (run.kind == Kind.SPAR && (a.getUUID().equals(e.getKey()) && b.getUUID().equals(run.guard)
				|| b.getUUID().equals(e.getKey()) && a.getUUID().equals(run.guard))) {
				return true;
			}
		}
		return false;
	}

	// ---- the offer ---------------------------------------------------------------------------------------------------

	/** "Ask for a drill" ({@link #ANSWER}): the veteran sets today's drill, or says why not. */
	public static void offer(ServerPlayer player, LivingEntity who) {
		if (!(who instanceof PathfinderMob guard) || !isVeteran(who) || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		Run mine = RUNS.get(player.getUUID());
		if (mine != null) {
			say(level, guard, player, mine.guard.equals(guard.getUUID()) ? "drill.jaymoskingdoms.under_way" : "drill.jaymoskingdoms.elsewhere");
			return;
		}
		for (Run run : RUNS.values()) {
			if (run.guard.equals(guard.getUUID())) {
				say(level, guard, player, "drill.jaymoskingdoms.busy");
				return;
			}
		}
		State state = stateOf(player);
		Long last = state.done().get(guard.getUUID().toString());
		if (last != null && last >= today(level)) {
			say(level, guard, player, "drill.jaymoskingdoms.tomorrow");
			return;
		}
		if (guard.getTarget() != null || player.getHealth() < player.getMaxHealth() * START_HEALTH || Downed.isDowned(player)) {
			say(level, guard, player, "drill.jaymoskingdoms.not_now");
			return;
		}
		Kind[] all = Kind.values();
		int first = Math.floorMod((int) (guard.getUUID().getLeastSignificantBits() ^ today(level)), all.length);
		boolean noShield = false;
		for (int i = 0; i < all.length; i++) {
			Kind kind = all[(first + i) % all.length];
			if (kind == Kind.PARRIES && !hasShield(player)) {
				noShield = true;
				continue;
			}
			if (begin(level, guard, player, kind)) {
				return;
			}
		}
		say(level, guard, player, noShield ? "drill.jaymoskingdoms.shield" : "drill.jaymoskingdoms.no_room");
	}

	private static boolean hasShield(Player player) {
		return player.getMainHandItem().is(Items.SHIELD) || player.getOffhandItem().is(Items.SHIELD);
	}

	private static boolean begin(ServerLevel level, PathfinderMob guard, ServerPlayer player, Kind kind) {
		long now = level.getGameTime();
		Run run = new Run(kind, guard.getUUID(), now + switch (kind) {
			case GATE -> GATE_TICKS;
			case PARRIES -> PARRY_TICKS;
			case SPAR -> SPAR_TICKS;
		});
		run.nextBlow = now + 60;
		if (kind == Kind.GATE) {
			for (int i = 0; i < WAVE; i++) {
				Zombie foe = foe(level, guard, player);
				if (foe != null) {
					run.foes.add(foe);
				}
			}
			if (run.foes.size() < 2) {
				sweep(run);
				return false;
			}
		}
		RUNS.put(player.getUUID(), run);
		player.setAttached(DRILLS, stateOf(player).under(kind, guard.getUUID(), 0));
		say(level, guard, player, "drill.jaymoskingdoms." + kind.key() + ".start");
		return true;
	}

	/** A practice foe on a free spot {@link #WAVE_MIN}-{@link #WAVE_MAX} blocks from {@code guard}, going for {@code player}, or null. */
	private static @Nullable Zombie foe(ServerLevel level, PathfinderMob guard, ServerPlayer player) {
		for (int tries = 0; tries < 12; tries++) {
			double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
			double dist = WAVE_MIN + level.getRandom().nextDouble() * (WAVE_MAX - WAVE_MIN);
			int x = Mth.floor(guard.getX() + Math.cos(angle) * dist), z = Mth.floor(guard.getZ() + Math.sin(angle) * dist);
			BlockPos at = null;
			for (int y = guard.blockPosition().getY() + 3; y >= guard.blockPosition().getY() - 4; y--) {
				BlockPos p = new BlockPos(x, y, z);
				if (level.getBlockState(p.below()).isSolid() && level.getBlockState(p).getCollisionShape(level, p).isEmpty()
					&& level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty() && level.getFluidState(p).isEmpty()) {
					at = p;
					break;
				}
			}
			if (at == null) {
				continue;
			}
			Zombie z0 = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (z0 == null) {
				return null;
			}
			z0.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
			z0.addTag(FOE_TAG);
			z0.setBaby(false);
			z0.setCanPickUpLoot(false);
			z0.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
			z0.setDropChance(EquipmentSlot.HEAD, 0.0F);
			set(z0, Attributes.MAX_HEALTH, FOE_HEALTH);
			set(z0, Attributes.ATTACK_DAMAGE, FOE_DAMAGE);
			set(z0, Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0);
			z0.setHealth((float) FOE_HEALTH);
			z0.setTarget(player);
			level.addFreshEntity(z0);
			return z0;
		}
		return null;
	}

	private static void set(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
		AttributeInstance a = mob.getAttribute(attribute);
		if (a != null) {
			a.setBaseValue(value);
		}
	}

	// ---- the drills --------------------------------------------------------------------------------------------------

	private static void resume(ServerPlayer player) {
		State s = stateOf(player);
		Kind kind = Kind.of(s.kind());
		if (kind == null) {
			return;
		}
		// only the parry count carries over a log-out; the others start over, and the day isn't spent
		if (kind == Kind.PARRIES && player.level() instanceof ServerLevel level) {
			UUID guard = parse(s.guard());
			if (guard != null && level.getEntity(guard) != null && hasShield(player)) {
				Run run = new Run(kind, guard, level.getGameTime() + PARRY_TICKS);
				run.parries = s.progress();
				run.nextBlow = level.getGameTime() + 100;
				RUNS.put(player.getUUID(), run);
				return;
			}
		}
		player.setAttached(DRILLS, s.idle());
	}

	private static @Nullable UUID parse(String s) {
		try {
			return UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static void drop(UUID player) {
		Run run = RUNS.remove(player);
		if (run != null) {
			sweep(run);
		}
	}

	private static void tick(MinecraftServer server) {
		if (RUNS.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Run> e : new ArrayList<>(RUNS.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Run run = e.getValue();
			if (player == null) {
				drop(e.getKey());
				continue;
			}
			ServerLevel level = player.level();
			if (!(level.getEntity(run.guard) instanceof PathfinderMob guard) || !guard.isAlive() || Downed.isDowned(guard)
				|| guard.level() != level || !player.isAlive() || Downed.isDowned(player) || guard.distanceTo(player) > LEAVE) {
				abandon(level, player, run, level.getEntity(run.guard) instanceof PathfinderMob g ? g : null, "drill.jaymoskingdoms.called_off");
				continue;
			}
			long now = level.getGameTime();
			if (now >= run.until) {
				abandon(level, player, run, guard, run.kind == Kind.SPAR ? "drill.jaymoskingdoms.spar.draw" : "drill.jaymoskingdoms." + run.kind.key() + ".lost");
				continue;
			}
			switch (run.kind) {
				case GATE -> tickGate(level, player, guard, run);
				case PARRIES -> tickParries(level, player, guard, run, now);
				case SPAR -> tickSpar(level, player, guard, run, now);
			}
		}
	}

	private static void tickGate(ServerLevel level, ServerPlayer player, PathfinderMob guard, Run run) {
		for (Zombie foe : new ArrayList<>(run.foes)) {
			if (foe.isRemoved() || !foe.isAlive()) {
				run.foes.remove(foe);
				run.defeated++;
			}
		}
		if (run.foes.isEmpty()) {
			complete(level, player, guard, run);
			return;
		}
		if (guard.tickCount % 20 == 0) {
			// the veteran holds firm, and the practice foes keep to you
			guard.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 40, 2, true, false));
			Zombie nearest = null;
			for (Zombie foe : run.foes) {
				if (foe.getTarget() != player) {
					foe.setTarget(player);
				}
				if (nearest == null || foe.distanceToSqr(guard) < nearest.distanceToSqr(guard)) {
					nearest = foe;
				}
			}
			guard.setTarget(nearest);
		}
	}

	private static void tickParries(ServerLevel level, ServerPlayer player, PathfinderMob guard, Run run, long now) {
		if (!hasShield(player)) {
			abandon(level, player, run, guard, "drill.jaymoskingdoms.parries.lost");
			return;
		}
		if (run.parries >= PARRIES) {
			complete(level, player, guard, run);
			return;
		}
		guard.getLookControl().setLookAt(player);
		if (guard.distanceTo(player) > REACH) {
			guard.getNavigation().moveTo(player, 1.0);
			return;
		}
		guard.getNavigation().stop();
		if (now >= run.nextBlow) {
			run.nextBlow = now + PARRY_EVERY;
			if (player.isBlocking() || player.getHealth() > PARRY_SAFE) {
				strike(level, guard, player, PARRY_HARM);
			}
		}
	}

	private static void tickSpar(ServerLevel level, ServerPlayer player, PathfinderMob guard, Run run, long now) {
		guard.getLookControl().setLookAt(player);
		if (guard.distanceTo(player) > REACH) {
			guard.getNavigation().moveTo(player, 1.1);
			return;
		}
		guard.getNavigation().stop();
		if (now >= run.nextBlow) {
			run.nextBlow = now + SPAR_EVERY;
			strike(level, guard, player, SPAR_HARM);
		}
	}

	private static void strike(ServerLevel level, PathfinderMob guard, ServerPlayer player, float harm) {
		guard.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		player.hurtServer(level, level.damageSources().mobAttack(guard), harm);
	}

	// ---- damage ------------------------------------------------------------------------------------------------------

	/** Blows in a drill: a turned blow counts as a parry, and a spar's last blow is stopped (the spar is over). */
	private static boolean allow(LivingEntity entity, DamageSource source, float amount) {
		if (RUNS.isEmpty() || amount <= 0.0F) {
			return true;
		}
		if (entity instanceof ServerPlayer player) {
			Run run = RUNS.get(player.getUUID());
			if (run == null || source.getEntity() == null || !source.getEntity().getUUID().equals(run.guard) || !(player.level() instanceof ServerLevel level)) {
				return true;
			}
			if (run.kind == Kind.PARRIES && player.isBlocking() && !source.is(DamageTypeTags.BYPASSES_SHIELD) && run.lastParry != level.getGameTime()) {
				run.lastParry = level.getGameTime();
				run.parries++;
				player.setAttached(DRILLS, stateOf(player).under(Kind.PARRIES, run.guard, run.parries));
				level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getEyeY() - 0.3, player.getZ(), 6, 0.3, 0.2, 0.3, 0.2);
			} else if (run.kind == Kind.SPAR && player.getHealth() - amount <= player.getMaxHealth() * SPAR_FLOOR) {
				if (level.getEntity(run.guard) instanceof PathfinderMob guard) {
					abandon(level, player, run, guard, "drill.jaymoskingdoms.spar.lost");
				}
				return false;
			}
			return true;
		}
		if (source.getEntity() instanceof ServerPlayer attacker && entity instanceof PathfinderMob guard) {
			Run run = RUNS.get(attacker.getUUID());
			if (run != null && run.kind == Kind.SPAR && run.guard.equals(guard.getUUID()) && attacker.level() instanceof ServerLevel level
				&& guard.getHealth() - amount <= guard.getMaxHealth() * SPAR_FLOOR) {
				complete(level, attacker, guard, run);
				return false;
			}
		}
		return true;
	}

	/** A practice foe struck down: it's counted and gone, with nothing dropped. True if so (the death is not to go through). */
	private static boolean foeFell(LivingEntity entity) {
		if (!(entity instanceof Zombie foe) || !foe.entityTags().contains(FOE_TAG) || !(foe.level() instanceof ServerLevel level)) {
			return false;
		}
		level.sendParticles(ParticleTypes.POOF, foe.getX(), foe.getY() + 1.0, foe.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
		foe.setHealth(1.0F);
		foe.discard();
		return true;
	}

	// ---- endings -----------------------------------------------------------------------------------------------------

	/** The drill is done: the day is spent and the veteran's lesson is yours. */
	private static void complete(ServerLevel level, ServerPlayer player, PathfinderMob guard, Run run) {
		RUNS.remove(player.getUUID());
		stand(guard);
		sweep(run);
		player.setAttached(DRILLS, stateOf(player).finished(run.guard, today(level)));
		say(level, guard, player, "drill.jaymoskingdoms." + run.kind.key() + ".done");
		reward(player, guard);
	}

	/** The drill ends unfinished: no day spent, the veteran stands down, and says {@code line}. */
	private static void abandon(ServerLevel level, ServerPlayer player, Run run, @Nullable PathfinderMob guard, String line) {
		RUNS.remove(player.getUUID());
		sweep(run);
		player.setAttached(DRILLS, stateOf(player).idle());
		if (guard != null) {
			stand(guard);
			if (guard.isAlive()) {
				say(level, guard, player, line);
			}
		}
	}

	/** The veteran stops fighting. */
	private static void stand(PathfinderMob guard) {
		guard.setTarget(null);
		guard.getNavigation().stop();
		guard.removeEffect(MobEffects.RESISTANCE);
	}

	/** Clears what's left of a drill's practice foes. */
	private static void sweep(Run run) {
		for (Zombie foe : run.foes) {
			if (!foe.isRemoved()) {
				foe.discard();
			}
		}
		run.foes.clear();
	}

	/** A starter form if {@code player} knows none, else practice for the form they know least. Told in their Tidings. */
	private static void reward(ServerPlayer player, PathfinderMob guard) {
		List<String> known = Forms.known(player);
		if (known.isEmpty()) {
			for (String id : Forms.STARTER_FORMS) {
				if (!Forms.knows(player, id) && Forms.teach(player, id)) {
					Post.tidings(player.getUUID(), Component.translatable("tidings.jaymoskingdoms.drill.taught", guard.getDisplayName(), formName(id)));
					return;
				}
			}
			return;
		}
		String pick = known.get(0);
		for (String id : known) {
			if (Forms.rank(player, id) < Forms.rank(player, pick)) {
				pick = id;
			}
		}
		Forms.practice(player, pick, PRACTICE);
		Post.tidings(player.getUUID(), Component.translatable("tidings.jaymoskingdoms.drill.practiced", guard.getDisplayName(), formName(pick)));
	}

	private static Component formName(String id) {
		StringBuilder pretty = new StringBuilder();
		for (String part : id.split("_")) {
			if (!part.isEmpty()) {
				pretty.append(pretty.isEmpty() ? "" : " ").append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
			}
		}
		return Component.translatableWithFallback("form.jaymoskingdoms." + id, pretty.toString());
	}

	/** The veteran says one of three variants of {@code key} (speech bubble, not chat). */
	private static void say(ServerLevel level, PathfinderMob guard, ServerPlayer player, String key) {
		Speech.tell(level, guard, player, Component.translatable(key + "." + level.getRandom().nextInt(3), player.getDisplayName()), Speech.GOLD);
	}
}
