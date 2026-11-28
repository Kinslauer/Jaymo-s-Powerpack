package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Cordon as people (Andy, 2026-11-28: "Consul members seem to be broken overall, they arent MCA and they move
 * impossibly slowly"). With MCA, each of the Cordon ({@link Cordon}) is an MCA guard of no realm: their own name, face,
 * talk and memory, in their rank's ash robes and the black band of the blindfolded ({@link Uniforms#cordon}), walking at
 * the pace every MCA person walks. Their rank ({@link #RANK}) keeps what it did as {@link CordonFolk} (still the body
 * without MCA):
 * <ul>
 * <li>they go after a heretic ({@link Cordon#heretic}) they see within {@link #HUNT_RANGE}, and fight on the Cordon's own
 * side ({@link #SIDE}), not a town's;</li>
 * <li>a <b>Stalker</b> goes unseen until it is within {@link CordonFolk#REVEAL} of its quarry or is struck, and its blow lays
 * Rot; an <b>Enforcer</b>'s blow binds the struck one's gift; an <b>Overseer</b> rallies the Cordon near once badly hurt
 * and carries the summons; the <b>Arbiter</b>, the judge in its court, has its boss bar, stands a head taller, learns the
 * harm dealt it and overloads ({@link #taken});</li>
 * <li>a patrol that has no player within {@link #LEAVE_RANGE} goes its way (out of the world, as the old body despawned),
 * and a court's own keep to the court.</li>
 * </ul>
 * Each rank keeps its old pace against the others (a Stalker quick, an Enforcer a little slower) as a share of MCA's pace.
 */
public final class CordonMen {
	// ---- tuning ----
	/** How far off they see a heretic to go after (blocks), and how often they look (ticks). */
	static final double HUNT_RANGE = 32.0;
	static final int LOOK_EVERY = 10;
	/** A patrol with no player this near (blocks) leaves the world; a court's own keep within this of the court (blocks). */
	static final double LEAVE_RANGE = 96.0, COURT_KEEP = 14.0;
	/** A rank's pace against MCA's: its old speed over the Sentinel's. */
	static final double SENTINEL_SPEED = 0.35;
	/** The Arbiter stands this much taller than a man. */
	static final double ARBITER_SCALE = 1.25;
	/** What the Cordon fight as one: their side ({@link Comrades#side}). */
	static final String SIDE = "cordon";

	/** On one of the Cordon: their rank's key ({@link Cordon.Rank#key}). Saved. */
	static final AttachmentType<String> RANK = AttachmentRegistry.create(JaymosKingdoms.id("cordon_rank"), b -> b.persistent(Codec.STRING));
	/** On a court's Arbiter and guards: the court (packed position). Saved. */
	static final AttachmentType<Long> COURT = AttachmentRegistry.create(JaymosKingdoms.id("cordon_court"), b -> b.persistent(Codec.LONG));
	/** On an Overseer: it has rallied (once) / its summons is out. On an Arbiter: its court is judged. Saved. */
	static final AttachmentType<Boolean> RALLIED = AttachmentRegistry.create(JaymosKingdoms.id("cordon_rallied"), b -> b.persistent(Codec.BOOL));
	static final AttachmentType<Boolean> FALLEN = AttachmentRegistry.create(JaymosKingdoms.id("cordon_fallen"), b -> b.persistent(Codec.BOOL));
	/** On an Arbiter: the harm it has learned of each kind ({@link CaptainWays.Cause}), then what it has soaked. Saved. */
	static final AttachmentType<List<Float>> LEARNED = AttachmentRegistry.create(JaymosKingdoms.id("cordon_learned"),
		b -> b.persistent(Codec.FLOAT.listOf()));
	/** On an Arbiter: when its stagger ends (game time). Saved. */
	static final AttachmentType<Long> STAGGERED = AttachmentRegistry.create(JaymosKingdoms.id("cordon_staggered"), b -> b.persistent(Codec.LONG));

	private static final Identifier PACE_ID = JaymosKingdoms.id("cordon_pace");

	/** The Cordon's people in the world. */
	private static final Set<Mob> MEN = Collections.newSetFromMap(new WeakHashMap<>());
	/** The Arbiters' bars. */
	private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

	private CordonMen() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob m && entity.hasAttached(RANK)) {
				MEN.add(m);
				Cordon.Rank rank = rank(m);
				if (rank != null) {
					pace(m, rank);
				}
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Mob m && MEN.remove(m)) {
				dropBar(m);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(CordonMen::tick);
		// an Arbiter overloaded can't strike
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) ->
			!(source.getEntity() instanceof Mob m && source.getDirectEntity() == m && rank(m) == Cordon.Rank.ARBITER && staggered(m)));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (victim.level() instanceof ServerLevel level) {
				struck(level, victim, source, taken);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Mob m && entity.level() instanceof ServerLevel level && rank(m) != null) {
				fall(level, m);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MEN.clear();
			BARS.clear();
		});
	}

	/** {@code e}'s Cordon rank, or null if it isn't one of the Cordon as people. */
	static Cordon.@Nullable Rank rank(@Nullable Entity e) {
		String key = e == null ? null : e.getAttached(RANK);
		if (key == null) {
			return null;
		}
		for (Cordon.Rank r : Cordon.Rank.values()) {
			if (r.key().equals(key)) {
				return r;
			}
		}
		return Cordon.Rank.SENTINEL;
	}

	/** {@code e}'s side if it is one of the Cordon ({@link Comrades#side}), else null. */
	static @Nullable String side(Entity e) {
		return e.hasAttached(RANK) ? SIDE : null;
	}

	/** The Arbiter as one of the Cordon's people, on its feet (a boss: {@link BossBars#boss}). */
	static boolean arbiter(Entity e) {
		return rank(e) == Cordon.Rank.ARBITER && !Downed.isDowned(e) && !Boolean.TRUE.equals(e.getAttached(FALLEN));
	}

	/** Outfits for {@code villager} if they are one of the Cordon ({@link Uniforms#outfits}), else null. */
	static @Nullable List<String> outfits(Entity villager) {
		Cordon.Rank r = rank(villager);
		return r == null ? null : Uniforms.cordon(r.key());
	}

	/** One of the Cordon of {@code rank} at {@code at}: an MCA guard of no realm, armed and dressed for the rank, in the world; null if none was made. */
	static @Nullable Villager make(ServerLevel level, Cordon.Rank rank, BlockPos at) {
		Villager v = McaBridge.cordonGuard(level, Vec3.atBottomCenterOf(at));
		if (v == null) {
			return null;
		}
		v.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		v.setAttached(RANK, rank.key());
		// the rank's build: health, blows, armour, steadiness; the Arbiter a head taller
		base(v, Attributes.MAX_HEALTH, rank.health);
		base(v, Attributes.ATTACK_DAMAGE, rank.damage);
		base(v, Attributes.ARMOR, rank.armour);
		base(v, Attributes.FOLLOW_RANGE, 40.0);
		base(v, Attributes.KNOCKBACK_RESISTANCE, rank == Cordon.Rank.ARBITER ? 0.8 : 0.2);
		if (rank == Cordon.Rank.ARBITER) {
			base(v, Attributes.SCALE, ARBITER_SCALE);
		}
		v.setHealth(v.getMaxHealth());
		ItemStack held = switch (rank) {
			case SENTINEL -> new ItemStack(Items.IRON_SPEAR);
			case STALKER, OVERSEER -> new ItemStack(Items.IRON_SWORD);
			case ENFORCER -> new ItemStack(Items.IRON_AXE);
			case ARBITER -> new ItemStack(Items.NETHERITE_SPEAR);
		};
		McaBridge.arm(v, EquipmentSlot.MAINHAND, held);
		v.setDropChance(EquipmentSlot.MAINHAND, 0.05F);
		if (rank == Cordon.Rank.ENFORCER || rank == Cordon.Rank.OVERSEER) {
			McaBridge.arm(v, EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
			v.setDropChance(EquipmentSlot.CHEST, 0.0F);
		}
		pace(v, rank);
		McaBridge.dress(v);
		MEN.add(v);
		return v;
	}

	private static void base(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
		AttributeInstance a = e.getAttribute(attribute);
		if (a != null) {
			a.setBaseValue(value);
		}
	}

	/** The rank's own pace against MCA's walk (kept as each body loads). */
	private static void pace(Mob m, Cordon.Rank rank) {
		AttributeInstance speed = m.getAttribute(Attributes.MOVEMENT_SPEED);
		double share = rank.speed / SENTINEL_SPEED - 1.0;
		if (speed != null && Math.abs(share) > 0.001) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(PACE_ID, share, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	/** {@code m} keeps to the court at {@code court}: the Arbiter and its guards. */
	static void keepCourt(Mob m, long court) {
		m.setAttached(COURT, court);
	}

	// ---- each look ----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (MEN.isEmpty()) {
			return;
		}
		for (Mob m : new ArrayList<>(MEN)) {
			if (m.isRemoved() || !(m.level() instanceof ServerLevel level)) {
				MEN.remove(m);
				dropBar(m);
				continue;
			}
			Cordon.Rank rank = rank(m);
			if (rank == null || !m.isAlive()) {
				continue;
			}
			if (Downed.isDowned(m)) {
				fall(level, m);
				continue;
			}
			LivingEntity target = Comrades.target(m);
			if (rank == Cordon.Rank.STALKER && m.tickCount % 10 == 0) {
				boolean seen = target != null && m.distanceTo(target) <= CordonFolk.REVEAL || m.hurtTime > 0;
				if (seen) {
					m.removeEffect(MobEffects.INVISIBILITY);
				} else {
					m.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, false, false));
				}
			}
			if (rank == Cordon.Rank.ARBITER) {
				arbiter(level, m);
			}
			if ((m.tickCount + m.getId()) % LOOK_EVERY != 0) {
				continue;
			}
			if (target == null) {
				ServerPlayer heretic = heretic(level, m);
				if (heretic != null) {
					Comrades.aim(m, heretic);
					target = heretic;
				}
			}
			Long court = m.getAttached(COURT);
			if (court == null) {
				// a patrol: gone once there's no one near to hunt
				if (level.getNearestPlayer(m, LEAVE_RANGE) == null) {
					MEN.remove(m);
					dropBar(m);
					McaCompat.vanish(m);
				}
			} else if (target == null && m.getNavigation().isDone()) {
				BlockPos home = BlockPos.of(court);
				if (m.distanceToSqr(Vec3.atBottomCenterOf(home)) > COURT_KEEP * COURT_KEEP) {
					m.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.6);
				}
			}
		}
	}

	/** The nearest heretic {@code m} can see, within {@link #HUNT_RANGE}; null for none. */
	private static @Nullable ServerPlayer heretic(ServerLevel level, Mob m) {
		ServerPlayer best = null;
		double bestD = HUNT_RANGE * HUNT_RANGE;
		for (ServerPlayer p : level.players()) {
			double d = p.distanceToSqr(m);
			if (d < bestD && p.isAlive() && !Downed.isDowned(p) && Cordon.heretic(p) && m.hasLineOfSight(p)) {
				best = p;
				bestD = d;
			}
		}
		return best;
	}

	/** The Arbiter's bar, and its stagger. */
	private static void arbiter(ServerLevel level, Mob m) {
		if (staggered(m)) {
			m.getNavigation().stop();
			if (m.tickCount % 10 == 0) {
				level.sendParticles(Fx.spark(Cordon.BINDING, 1.0F), m.getX(), m.getY(0.7), m.getZ(), 4, 0.4, 0.5, 0.4, 0.05);
			}
		}
		if (m.tickCount % 10 == 0) {
			ServerBossEvent bar = BARS.computeIfAbsent(m.getUUID(), id -> new ServerBossEvent(id,
				Component.translatable("entity.jaymoskingdoms.cordon_arbiter"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10));
			bar.setProgress(m.getHealth() / m.getMaxHealth());
			BossBars.show(level, m, bar);
		}
	}

	private static void dropBar(Mob m) {
		ServerBossEvent bar = BARS.remove(m.getUUID());
		if (bar != null) {
			bar.removeAllPlayers();
		}
	}

	/** {@code m} has gone down or died: an Overseer's summons falls from it, an Arbiter's court is judged; once. */
	private static void fall(ServerLevel level, Mob m) {
		dropBar(m);
		if (Boolean.TRUE.equals(m.getAttached(FALLEN))) {
			return;
		}
		m.setAttached(FALLEN, true);
		Cordon.Rank rank = rank(m);
		if (rank == Cordon.Rank.OVERSEER) {
			m.spawnAtLocation(level, new ItemStack(Cordon.SUMMONS));
		}
		Long court = m.getAttached(COURT);
		if (rank == Cordon.Rank.ARBITER && court != null) {
			Cordon.judged(level, court);
		}
	}

	// ---- blows --------------------------------------------------------------------------------------------------------

	/** A blow landed: one of the Cordon's struck someone (its rank's mark), or one of them was struck (a Stalker seen, an Overseer's rally). */
	private static void struck(ServerLevel level, LivingEntity victim, DamageSource source, float taken) {
		if (taken <= 0.0F) {
			return;
		}
		if (source.getEntity() instanceof Mob m && source.getDirectEntity() == m && victim != m) {
			Cordon.Rank rank = rank(m);
			if (rank != null) {
				switch (rank) {
					case STALKER -> AshRot.rot(victim, m, CordonFolk.STALK_ROT);
					case ENFORCER -> bind(level, victim, CordonFolk.SEAL_TICKS);
					case ARBITER -> {
						bind(level, victim, CordonFolk.ARBITER_SEAL_TICKS);
						AshRot.rot(victim, m, 1);
					}
					default -> {
					}
				}
			}
		}
		if (!(victim instanceof Mob self)) {
			return;
		}
		Cordon.Rank mine = rank(self);
		if (mine == Cordon.Rank.STALKER) {
			self.removeEffect(MobEffects.INVISIBILITY);
		}
		if (mine == Cordon.Rank.OVERSEER && !Boolean.TRUE.equals(self.getAttached(RALLIED)) && self.getHealth() <= self.getMaxHealth() * CordonFolk.RALLY_AT) {
			self.setAttached(RALLIED, true);
			Speech.say(level, self, Component.translatable("speech.jaymoskingdoms.cordon.rally"), Speech.RED, 32.0);
			LivingEntity foe = Comrades.target(self);
			for (Mob other : level.getEntitiesOfClass(Mob.class, self.getBoundingBox().inflate(CordonFolk.RALLY_RANGE),
				o -> o.isAlive() && (o.hasAttached(RANK) || o instanceof CordonFolk))) {
				other.addEffect(new MobEffectInstance(MobEffects.STRENGTH, CordonFolk.RALLY_TICKS, 0));
				if (foe != null && Comrades.target(other) == null) {
					Comrades.aim(other, foe);
				}
			}
		}
	}

	/** {@code living}'s gift is bound (powers and forms refused) for {@code ticks}; the bound player is told on the action bar. */
	static void bind(ServerLevel level, LivingEntity living, int ticks) {
		PowersCompat.seal(living, ticks);
		level.sendParticles(Fx.rune(Cordon.BINDING, 0.8F), living.getX(), living.getY(0.6), living.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
		if (living instanceof ServerPlayer player && PowersCompat.power(player) != null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.cordon.bound", ticks / 20));
		}
	}

	// ---- the Arbiter's judgment ---------------------------------------------------------------------------------------

	static boolean staggered(Mob m) {
		return m.level().getGameTime() < m.getAttachedOrElse(STAGGERED, 0L);
	}

	/**
	 * ({@code LivingEntityHurtMixin}) The harm the Arbiter (as one of the Cordon's people) lets through of a blow: each kind
	 * dealt it is taken less and less ({@link CordonFolk#MOST_RESIST} once {@link CordonFolk#LEARN_FULL} of it is dealt);
	 * what it soaks builds to an overload ({@link CordonFolk#OVERLOAD_AT}), and staggered it takes the more. Anyone else's
	 * harm is left as it is.
	 */
	public static float taken(LivingEntity self, DamageSource source, float damage) {
		if (!(self instanceof Mob m) || !(m.level() instanceof ServerLevel level) || rank(m) != Cordon.Rank.ARBITER || damage <= 0.0F) {
			return damage;
		}
		if (staggered(m)) {
			return damage * CordonFolk.STAGGERED_TAKEN;
		}
		CaptainWays.Cause cause = CaptainWays.cause(source);
		if (cause == null) {
			return damage;
		}
		int kinds = CaptainWays.Cause.values().length;
		List<Float> saved = m.getAttachedOrElse(LEARNED, List.of());
		float[] learned = new float[kinds + 1];
		for (int i = 0; i < Math.min(saved.size(), learned.length); i++) {
			learned[i] = saved.get(i);
		}
		int i = cause.ordinal();
		float resist = CordonFolk.MOST_RESIST * Math.min(1.0F, learned[i] / CordonFolk.LEARN_FULL);
		float through = damage * (1.0F - resist);
		boolean knewIt = learned[i] >= CordonFolk.LEARN_FULL;
		learned[i] += damage;
		learned[kinds] += damage - through;
		if (!knewIt && learned[i] >= CordonFolk.LEARN_FULL) {
			Speech.say(level, m, Component.translatable("speech.jaymoskingdoms.cordon.learned." + cause.name().toLowerCase(java.util.Locale.ROOT)),
				Speech.RED, 32.0);
		}
		if (learned[kinds] >= CordonFolk.OVERLOAD_AT) {
			m.removeAttached(LEARNED);
			overload(level, m);
			return through;
		}
		List<Float> next = new ArrayList<>(learned.length);
		for (float f : learned) {
			next.add(f);
		}
		m.setAttached(LEARNED, next);
		return through;
	}

	/** Too much learned and held: it bursts, staggers and forgets. */
	private static void overload(ServerLevel level, Mob m) {
		m.setAttached(STAGGERED, level.getGameTime() + CordonFolk.STAGGER_TICKS);
		m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CordonFolk.STAGGER_TICKS, 5, false, false));
		Vec3 at = m.position().add(0.0, m.getBbHeight() * 0.5, 0.0);
		Fx.blast(level, at, CordonFolk.BURST_RANGE, Cordon.BINDING);
		level.playSound(null, m.getX(), m.getY(), m.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2F, 0.7F);
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(CordonFolk.BURST_RANGE),
			e -> e != m && e.isAlive() && !e.hasAttached(RANK) && !(e instanceof CordonFolk))) {
			near.hurtServer(level, m.damageSources().indirectMagic(m, m), CordonFolk.BURST_HARM);
			Vec3 away = near.position().subtract(m.position()).multiply(1.0, 0.0, 1.0).normalize().scale(1.2);
			near.push(away.x, 0.4, away.z);
			near.syncVelocity = true;
		}
		Speech.say(level, m, Component.translatable("speech.jaymoskingdoms.cordon.overload"), Speech.RED, 32.0);
	}
}
