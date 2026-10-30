package com.jaymoskingdoms.forms;

import com.jaymoskingdoms.Casters;
import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.CourtWizards;
import com.jaymoskingdoms.Downed;
import com.jaymoskingdoms.Faction;
import com.jaymoskingdoms.Factions;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Post;
import com.jaymoskingdoms.Sorcery;
import com.jaymoskingdoms.Speech;
import com.jaymoskingdoms.Worker;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.384) Wizard trials (Weapon Forms, patch 4). A court wizard ({@link CourtWizards#is}) or a realm's necromancer (a caster
 * villager of {@link Casters}) sets a trial when asked ({@link #ANSWER}, MCA's main menu, the way {@link GuardDrills} does),
 * rotating three with the day:
 * <ul>
 * <li><b>The Shade</b> ({@link Kind#SHADE}): a practice foe, stronger than a drill's, hunts you. Beat it yourself.</li>
 * <li><b>The Ward</b> ({@link Kind#WARD}): stand inside a ring of light for {@link #WARD_TICKS} while weak foes come at you in
 * turn; the clock only runs while you are inside. Any class may do it.</li>
 * <li><b>The Marked One</b> ({@link Kind#MARKED}): {@link #MARKED_FOES} foes stand about; one glows, and only that one fights
 * back. Strike any other and the trial is lost.</li>
 * </ul>
 * The practice foes are the drills' ({@link GuardDrills#FOE_TAG}: weak, helmeted against the sun, never dropping anything,
 * swept up on load, counted as beaten only by their {@link GuardDrills#FELL_TAG} and only if you struck them
 * ({@link #HIT_TAG}), so one only unloaded, or felled by a golem or a bolt, never counts). The wizard fights none of them
 * ({@link #practiceFoe}). A passed trial practises the two forms you know least by {@link #PRACTICE}, adds
 * {@link #STANDING} to your standing in the school the land teaches ({@link Schools#localSchool}), and gives a Mage or a Sorcerer
 * {@link #CASTER_XP} experience besides. One trial per in-game day per player, from any wizard (a trial lost doesn't spend it).
 * Hints are the wizard's bubbles and your Tidings, never chat or the screen.
 */
public final class WizardTrials {
	// ---- tuning ----
	static final long DAY_TICKS = 24000L;
	/** How far (blocks) you may go from the wizard before the trial is called off. */
	static final double LEAVE = 32.0;
	/** The share of health you must have to begin. */
	static final float START_HEALTH = 0.7F;
	/** The longest each trial lasts (ticks). */
	static final int SHADE_TICKS = 2400, WARD_MAX_TICKS = 2400, MARKED_TICKS = 2400;
	/** How far (blocks) from the spot the practice foes appear. */
	static final double FOE_MIN = 6.0, FOE_MAX = 9.0;
	/** The Shade: its health and blow. */
	static final double SHADE_HEALTH = 24.0, SHADE_DAMAGE = 3.0;
	/** The Ward: ticks to hold inside the ring, its radius (blocks), how often a foe comes (ticks) and how many at once; their health and blow. */
	static final int WARD_TICKS = 600, WARD_EVERY = 90, WARD_AT_ONCE = 3;
	static final double RING_RADIUS = 3.5, WARD_HEALTH = 8.0, WARD_DAMAGE = 2.0;
	/** The Marked One: how many foes stand about, and the marked one's health and blow. */
	static final int MARKED_FOES = 4;
	static final double MARKED_HEALTH = 14.0, MARKED_DAMAGE = 2.5;
	/** What a passed trial is worth: practice to each of the two forms known least, standing in the local school, a caster's experience. */
	static final int PRACTICE = 8, PRACTICE_FORMS = 2, STANDING = 6, CASTER_XP = 40;
	/** Put on a practice foe the player has struck (a foe felled by someone else never counts). */
	static final String HIT_TAG = "jaymoskingdoms_trial_hit";
	/** The MCA {@code main} menu answer (our dialogue data): "Ask for a trial". */
	public static final String ANSWER = "jaymoskingdoms_trial";

	/** The three trials (offered in this order, rotating with the day). */
	public enum Kind {
		SHADE, WARD, MARKED;

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

	/** A player's trials: the one under way (kind key and wizard; empty kind: none) and the day number each wizard (UUID) last set one that was passed. */
	public record State(String kind, String wizard, Map<String, Long> done) {
		public static final State NONE = new State("", "", Map.of());
		public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.optionalFieldOf("kind", "").forGetter(State::kind),
				Codec.STRING.optionalFieldOf("wizard", "").forGetter(State::wizard),
				Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("done", Map.of()).forGetter(State::done))
			.apply(i, State::new));

		State idle() {
			return new State("", "", this.done);
		}

		State under(Kind k, UUID wizard) {
			return new State(k.key(), wizard.toString(), this.done);
		}

		State passed(UUID wizard, long day) {
			Map<String, Long> next = new HashMap<>(this.done);
			next.put(wizard.toString(), day);
			return new State("", "", next);
		}
	}

	/** On a player: their trials. Saved, kept through death. */
	public static final AttachmentType<State> TRIALS = AttachmentRegistry.create(JaymosKingdoms.id("wizard_trials"),
		builder -> builder.persistent(State.CODEC).copyOnDeath());

	/** A trial under way (in memory only: a log-out ends it). */
	private static final class Run {
		final Kind kind;
		final UUID wizard;
		final long until;
		final List<Zombie> foes = new ArrayList<>();
		@Nullable Zombie marked;
		/** The ward's ring, and the ticks held inside it. */
		Vec3 ring = Vec3.ZERO;
		int held;
		long nextFoe;
		/** The Shade or the Marked One gone without being struck down by the player (an unloaded chunk): the trial no longer counts. */
		boolean lost;

		Run(Kind kind, UUID wizard, long until) {
			this.kind = kind;
			this.wizard = wizard;
			this.until = until;
		}
	}

	private static final Map<UUID, Run> RUNS = new HashMap<>();

	private WizardTrials() {
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			// (a trial doesn't carry over a log-in; the day isn't spent)
			if (!stateOf(handler.getPlayer()).kind().isEmpty()) {
				handler.getPlayer().setAttached(TRIALS, stateOf(handler.getPlayer()).idle());
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> drop(handler.getPlayer().getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Run run : new ArrayList<>(RUNS.values())) {
				sweep(run);
			}
			RUNS.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(WizardTrials::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> allow(entity, source, amount));
	}

	// ---- who and when ------------------------------------------------------------------------------------------------

	/** Whether {@code who} is a practice foe of a drill or a trial: no wizard casts at it. */
	public static boolean practiceFoe(Entity who) {
		return who.entityTags().contains(GuardDrills.FOE_TAG);
	}

	/** A trial-setter: a court wizard, or a necromancer in a realm's service (a camp's casters set none). */
	public static boolean isSetter(LivingEntity who) {
		if (who.level().isClientSide() || !who.isAlive() || Downed.isDowned(who)) {
			return false;
		}
		if (CourtWizards.is(who)) {
			return true;
		}
		if (who instanceof Worker w && w.role() == Sorcery.NECROMANCER && who.level().getServer() != null) {
			Faction f = Factions.get(who.level().getServer(), w.factionId());
			return f != null && f.type().isRealm() && !f.fallen();
		}
		return false;
	}

	/** Whether "Ask for a trial" shows on {@code who}. */
	public static boolean canOffer(ServerPlayer player, LivingEntity who) {
		return isSetter(who);
	}

	private static long today(ServerLevel level) {
		return level.getGameTime() / DAY_TICKS;
	}

	private static State stateOf(Player player) {
		return player.getAttachedOrElse(TRIALS, State.NONE);
	}

	// ---- the offer ---------------------------------------------------------------------------------------------------

	/** "Ask for a trial" ({@link #ANSWER}): the wizard sets today's trial, or says why not. */
	public static void offer(ServerPlayer player, LivingEntity who) {
		if (!(who instanceof PathfinderMob wizard) || !isSetter(who) || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		Run mine = RUNS.get(player.getUUID());
		if (mine != null) {
			say(level, wizard, player, mine.wizard.equals(wizard.getUUID()) ? "trial.jaymoskingdoms.under_way" : "trial.jaymoskingdoms.elsewhere");
			return;
		}
		for (Run run : RUNS.values()) {
			if (run.wizard.equals(wizard.getUUID())) {
				say(level, wizard, player, "trial.jaymoskingdoms.busy");
				return;
			}
		}
		// one trial a day for a player, whichever wizard set it
		if (stateOf(player).done().values().stream().anyMatch(day -> day >= today(level))) {
			say(level, wizard, player, "trial.jaymoskingdoms.tomorrow");
			return;
		}
		if (wizard.getTarget() != null || player.getHealth() < player.getMaxHealth() * START_HEALTH || Downed.isDowned(player) || player.isCreative()) {
			say(level, wizard, player, "trial.jaymoskingdoms.not_now");
			return;
		}
		Kind[] all = Kind.values();
		int first = Math.floorMod((int)(wizard.getUUID().getLeastSignificantBits() ^ today(level)), all.length);
		for (int i = 0; i < all.length; i++) {
			if (begin(level, wizard, player, all[(first + i) % all.length])) {
				return;
			}
		}
		say(level, wizard, player, "trial.jaymoskingdoms.no_room");
	}

	private static boolean begin(ServerLevel level, PathfinderMob wizard, ServerPlayer player, Kind kind) {
		long now = level.getGameTime();
		Run run = new Run(kind, wizard.getUUID(), now + switch (kind) {
			case SHADE -> SHADE_TICKS;
			case WARD -> WARD_MAX_TICKS;
			case MARKED -> MARKED_TICKS;
		});
		switch (kind) {
			case SHADE -> {
				Zombie shade = foe(level, wizard.position(), player, SHADE_HEALTH, SHADE_DAMAGE, true);
				if (shade == null) {
					return false;
				}
				run.foes.add(shade);
			}
			case WARD -> {
				run.ring = new Vec3(Mth.floor(player.getX()) + 0.5, player.getY(), Mth.floor(player.getZ()) + 0.5);
				run.nextFoe = now + 60;
			}
			case MARKED -> {
				for (int i = 0; i < MARKED_FOES; i++) {
					// (only the marked one fights: the rest stand as they were put)
					Zombie z = foe(level, wizard.position(), player, i == 0 ? MARKED_HEALTH : WARD_HEALTH, MARKED_DAMAGE, i == 0);
					if (z != null) {
						run.foes.add(z);
					} else if (i == 0) {
						// (the first is the one that hunts you: without it there's no trial)
						break;
					}
				}
				if (run.foes.size() < 3 || !run.foes.get(0).isAlive()) {
					sweep(run);
					return false;
				}
				// (the marked one is the first spawned; it's the one that comes for you)
				run.marked = run.foes.get(0);
				run.marked.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int)(MARKED_TICKS + 40), 0, true, false));
			}
		}
		RUNS.put(player.getUUID(), run);
		player.setAttached(TRIALS, stateOf(player).under(kind, wizard.getUUID()));
		say(level, wizard, player, "trial.jaymoskingdoms." + kind.key() + ".start");
		return true;
	}

	/**
	 * A practice foe on a free spot {@link #FOE_MIN}-{@link #FOE_MAX} blocks from {@code around}, going for {@code player} if
	 * {@code hunts} (else it stands still), or null. Tagged with the drills' tags, so the same sweep and death rules apply.
	 */
	private static @Nullable Zombie foe(ServerLevel level, Vec3 around, ServerPlayer player, double health, double damage, boolean hunts) {
		for (int tries = 0; tries < 12; tries++) {
			double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
			double dist = FOE_MIN + level.getRandom().nextDouble() * (FOE_MAX - FOE_MIN);
			int x = Mth.floor(around.x + Math.cos(angle) * dist), z = Mth.floor(around.z + Math.sin(angle) * dist);
			BlockPos at = null;
			for (int y = Mth.floor(around.y) + 3; y >= Mth.floor(around.y) - 4; y--) {
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
			z0.addTag(GuardDrills.FOE_TAG);
			z0.setBaby(false);
			z0.setCanPickUpLoot(false);
			z0.setPersistenceRequired();
			z0.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
			z0.setDropChance(EquipmentSlot.HEAD, 0.0F);
			set(z0, Attributes.MAX_HEALTH, health);
			set(z0, Attributes.ATTACK_DAMAGE, damage);
			set(z0, Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0);
			z0.setHealth((float)health);
			if (hunts) {
				z0.setTarget(player);
			} else {
				z0.setNoAi(true);
			}
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

	// ---- the trials --------------------------------------------------------------------------------------------------

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
			Entity found = level.getEntity(run.wizard);
			if (!(found instanceof PathfinderMob wizard) || !wizard.isAlive() || Downed.isDowned(wizard) || wizard.level() != level
				|| !player.isAlive() || Downed.isDowned(player) || wizard.distanceTo(player) > LEAVE) {
				abandon(level, player, run, found instanceof PathfinderMob w ? w : null, "trial.jaymoskingdoms.called_off");
				continue;
			}
			long now = level.getGameTime();
			if (now >= run.until) {
				abandon(level, player, run, wizard, "trial.jaymoskingdoms." + run.kind.key() + ".lost");
				continue;
			}
			switch (run.kind) {
				case SHADE -> tickShade(level, player, wizard, run);
				case WARD -> tickWard(level, player, wizard, run, now);
				case MARKED -> tickMarked(level, player, wizard, run);
			}
		}
	}

	/** Takes foes that are gone off {@code run}'s list: true if one struck down by {@code player} is among them (beaten), else a lost one marks the trial lost. */
	private static boolean reap(Run run, boolean counts) {
		boolean beaten = false;
		for (Zombie foe : new ArrayList<>(run.foes)) {
			if (foe.isRemoved() || !foe.isAlive()) {
				run.foes.remove(foe);
				if (foe.entityTags().contains(GuardDrills.FELL_TAG) && foe.entityTags().contains(HIT_TAG)) {
					beaten = true;
				} else if (counts) {
					run.lost = true;
				}
			}
		}
		return beaten;
	}

	private static void tickShade(ServerLevel level, ServerPlayer player, PathfinderMob wizard, Run run) {
		boolean beaten = reap(run, true);
		if (run.foes.isEmpty()) {
			if (beaten && !run.lost) {
				complete(level, player, wizard, run);
			} else {
				abandon(level, player, run, wizard, "trial.jaymoskingdoms.shade.lost");
			}
		}
	}

	private static void tickWard(ServerLevel level, ServerPlayer player, PathfinderMob wizard, Run run, long now) {
		// the foes that fall or go are only cleared away: this trial is about holding the ring
		reap(run, false);
		if (run.held >= WARD_TICKS) {
			complete(level, player, wizard, run);
			return;
		}
		double dx = player.getX() - run.ring.x, dz = player.getZ() - run.ring.z;
		boolean inside = dx * dx + dz * dz <= RING_RADIUS * RING_RADIUS && Math.abs(player.getY() - run.ring.y) <= 2.5;
		if (inside) {
			run.held++;
		}
		if (player.tickCount % 10 == 0) {
			for (int i = 0; i < 20; i++) {
				double a = i * Math.PI * 2.0 / 20;
				level.sendParticles(player, inside ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK, true, false,
					run.ring.x + Math.cos(a) * RING_RADIUS, run.ring.y + 0.15, run.ring.z + Math.sin(a) * RING_RADIUS, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		if (now >= run.nextFoe && run.foes.size() < WARD_AT_ONCE) {
			run.nextFoe = now + WARD_EVERY;
			Zombie z = foe(level, run.ring, player, WARD_HEALTH, WARD_DAMAGE, true);
			if (z != null) {
				run.foes.add(z);
			}
		}
	}

	private static void tickMarked(ServerLevel level, ServerPlayer player, PathfinderMob wizard, Run run) {
		Zombie marked = run.marked;
		boolean beaten = reap(run, false);
		if (marked != null && (marked.isRemoved() || !marked.isAlive())) {
			if (beaten && marked.entityTags().contains(GuardDrills.FELL_TAG) && marked.entityTags().contains(HIT_TAG)) {
				complete(level, player, wizard, run);
			} else {
				abandon(level, player, run, wizard, "trial.jaymoskingdoms.marked.lost");
			}
			return;
		}
		if (marked != null && player.tickCount % 10 == 0) {
			// (a mark over its head that only you see, besides its glow)
			level.sendParticles(player, ParticleTypes.END_ROD, true, false, marked.getX(), marked.getY() + marked.getBbHeight() + 0.6, marked.getZ(), 2,
				0.1, 0.1, 0.1, 0.0);
		}
	}

	// ---- damage ------------------------------------------------------------------------------------------------------

	/** Blows in a trial: your blow on a practice foe is noted; in the Marked One, a blow on a foe that isn't marked loses it. */
	private static boolean allow(LivingEntity entity, DamageSource source, float amount) {
		if (RUNS.isEmpty() || amount <= 0.0F || !(entity instanceof Zombie foe) || !practiceFoe(foe) || !(source.getEntity() instanceof ServerPlayer attacker)) {
			return true;
		}
		Run run = RUNS.get(attacker.getUUID());
		if (run == null || !run.foes.contains(foe) || !(attacker.level() instanceof ServerLevel level)) {
			return true;
		}
		if (run.kind == Kind.MARKED && foe != run.marked) {
			abandon(level, attacker, run, level.getEntity(run.wizard) instanceof PathfinderMob w ? w : null, "trial.jaymoskingdoms.marked.wrong");
			return false;
		}
		foe.addTag(HIT_TAG);
		return true;
	}

	// ---- endings -----------------------------------------------------------------------------------------------------

	/** The trial is passed: the day is spent and the reward is yours. */
	private static void complete(ServerLevel level, ServerPlayer player, PathfinderMob wizard, Run run) {
		RUNS.remove(player.getUUID());
		sweep(run);
		player.setAttached(TRIALS, stateOf(player).passed(run.wizard, today(level)));
		say(level, wizard, player, "trial.jaymoskingdoms." + run.kind.key() + ".done");
		reward(level, player, wizard);
	}

	/** The trial ends unfinished: no day spent, the foes are swept, the wizard says {@code line}. */
	private static void abandon(ServerLevel level, ServerPlayer player, Run run, @Nullable PathfinderMob wizard, String line) {
		RUNS.remove(player.getUUID());
		sweep(run);
		player.setAttached(TRIALS, stateOf(player).idle());
		if (wizard != null && wizard.isAlive()) {
			say(level, wizard, player, line);
		}
	}

	/** Clears what's left of a trial's practice foes. */
	private static void sweep(Run run) {
		for (Zombie foe : run.foes) {
			if (!foe.isRemoved()) {
				foe.discard();
			}
		}
		run.foes.clear();
		run.marked = null;
	}

	/** Practice for the two forms known least, standing in the local school, and a caster's extra. Told in the Tidings. */
	private static void reward(ServerLevel level, ServerPlayer player, PathfinderMob wizard) {
		List<String> known = new ArrayList<>(Forms.known(player));
		known.sort(Comparator.comparingInt(id -> Forms.rank(player, id)));
		for (int i = 0; i < Math.min(PRACTICE_FORMS, known.size()); i++) {
			Forms.practice(player, known.get(i), PRACTICE);
		}
		Schools.School local = Schools.localSchool(level, wizard.blockPosition());
		if (local != null) {
			Schools.addStanding(player, local, STANDING);
		}
		boolean caster = Classes.is(player, Classes.PlayerClass.MAGE) || Classes.is(player, Classes.PlayerClass.SORCERER);
		if (caster) {
			player.giveExperiencePoints(CASTER_XP);
		}
		Post.tidings(player.getUUID(), Component.translatable("tidings.jaymoskingdoms.trial." + (caster ? "caster" : "passed"), wizard.getDisplayName()));
	}

	/** The wizard says one of two variants of {@code key} (speech bubble, not chat). */
	private static void say(ServerLevel level, PathfinderMob wizard, ServerPlayer player, String key) {
		Speech.tell(level, wizard, player, Component.translatable(key + "." + level.getRandom().nextInt(2), player.getDisplayName()), Speech.WHITE);
	}
}
