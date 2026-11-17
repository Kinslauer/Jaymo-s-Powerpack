package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The Warlock's tree (class trees, docs/ClassTrees-Plan.md). {@link Warlocks} keeps the starting move (one pact slot, its
 * casts growing harder with the Warlock's level) and asks here for the rest:
 * <ul>
 * <li><b>Pact Slots</b> (3): a slot more, each rank.</li>
 * <li><b>Patient Creditor</b> (3): debts fall due two days later.</li>
 * <li><b>Eldritch Bolt</b> (3): the class key with the seal in hand and nothing owed throws a weak bolt from it, no slot spent; it
 * grows with the Warlock's level.</li>
 * <li><b>In Kind</b> (5): the key with the patron's own offerings in hand pays any debt with them.</li>
 * <li><b>Renege</b> (8): sneak and the key while owing swaps what's asked for another kind, once a debt.</li>
 * <li><b>Pact of the Blade</b>, <b>of the Chain</b> or <b>of the Tome</b> (10): the seal in your off hand makes what's in the
 * other your pact weapon; or a familiar flies with you, showing what lurks and stinging what you fight; or any scroll's
 * spell is yours to learn.</li>
 * <li><b>Arcanum</b> (20): once a day, a cast made sneaking is a great one: twice as strong, no slot, no debt.</li>
 * </ul>
 */
public final class PactArts {
	/** Patient Creditor: days added to a debt. */
	static final int CREDITOR_DAYS = 2;
	/** Eldritch Bolt: its harm, what each Warlock level adds, its reach and wait (ticks). */
	static final float BOLT_BASE = 3.0F, BOLT_PER_LEVEL = 0.15F;
	static final double BOLT_REACH = 24.0;
	static final int BOLT_WAIT = 20;
	/** In Kind: how many of the patron's offerings pay a debt. */
	static final int KIND_COUNT = 4;
	/** Pact of the Blade: blows with the pact weapon this share harder. */
	static final float BLADE_HIT = 0.3F;
	/** Pact of the Chain: how far the familiar keeps, what it shows and how far, its sting and how often (ticks). */
	static final double CHAIN_KEEP = 6.0, CHAIN_SCOUT = 16.0;
	static final float CHAIN_STING = 2.0F;
	static final int CHAIN_EVERY = 40, CHAIN_GLOW = 60;
	/** Arcanum: how much stronger, and its pool (once a day, back with a night's sleep). */
	static final float ARCANUM_POWER = 2.0F;
	static final String ARCANUM_POOL = "arcanum";

	public static final String SLOTS = "wlk_slots", CREDITOR = "wlk_creditor", BOLT = "wlk_bolt", KIND = "wlk_kind", RENEGE = "wlk_renege",
		BLADE = "wlk_blade", CHAIN = "wlk_chain", TOME = "wlk_tome", ARCANUM = "wlk_arcanum";

	private static final Map<UUID, Long> BOLTED = new HashMap<>();
	private static final Map<UUID, Bat> FAMILIARS = new HashMap<>();
	public static final String FAMILIAR_TAG = "jaymoskingdoms_familiar";

	private PactArts() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(PactArts::tick);
		// a familiar left behind (a crash, an unload) is gone
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Bat bat && bat.entityTags().contains(FAMILIAR_TAG) && !FAMILIARS.containsValue(bat)) {
				bat.discard();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			FAMILIARS.values().forEach(Bat::discard);
			FAMILIARS.clear();
			BOLTED.clear();
		});
	}

	static boolean has(Player player, String key) {
		return Classes.is(player, PlayerClass.WARLOCK) && ClassTrees.has(player, key) > 0;
	}

	/** Pact Slots: the slots it adds. */
	static int slots(Player player) {
		return Classes.is(player, PlayerClass.WARLOCK) ? Math.round(ClassTrees.power(player, SLOTS)) : 0;
	}

	/** Patient Creditor: the days a debt is due in. */
	static int days(Player player, int days) {
		return has(player, CREDITOR) ? days + CREDITOR_DAYS : days;
	}

	/** Pact of the Tome: any scroll's spell is theirs to learn. */
	static boolean tome(Player player) {
		return has(player, TOME);
	}

	private static boolean sealed(Player player) {
		return player.getMainHandItem().is(Warlocks.SEAL) || player.getOffhandItem().is(Warlocks.SEAL);
	}

	// ---- the key --------------------------------------------------------------------------------------------------------------

	/** The key with nothing owed: Eldritch Bolt. False when it isn't theirs (or the seal isn't in hand). */
	static boolean bolt(ServerPlayer warlock) {
		if (!has(warlock, BOLT) || !sealed(warlock)) {
			return false;
		}
		ServerLevel level = warlock.level();
		long now = level.getGameTime();
		if (BOLTED.getOrDefault(warlock.getUUID(), 0L) > now) {
			return true;
		}
		BOLTED.put(warlock.getUUID(), now + BOLT_WAIT);
		Vec3 eye = warlock.getEyePosition().add(0.0, -0.2, 0.0);
		LivingEntity target = Arcana.sighted(warlock);
		Vec3 end = target != null && target.distanceTo(warlock) <= BOLT_REACH ? target.getBoundingBox().getCenter()
			: eye.add(warlock.getLookAngle().scale(BOLT_REACH));
		Fx.beam(level, eye, end, 0.06F, 0x8A4FD0, 6);
		if (target != null && target.distanceTo(warlock) <= BOLT_REACH) {
			float harm = BOLT_BASE + BOLT_PER_LEVEL * ClassTrees.level(warlock, PlayerClass.WARLOCK);
			target.hurtServer(level, warlock.damageSources().indirectMagic(warlock, warlock), harm);
			Fx.blast(level, end, 0.6F, 0x8A4FD0);
		}
		level.playSound(null, warlock.getX(), warlock.getY(), warlock.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.6F, 1.6F);
		return true;
	}

	/** The key while owing, with the patron's offerings in hand: In Kind. False when it isn't that. */
	static boolean inKind(ServerPlayer warlock) {
		Patron patron = Patrons.of(warlock);
		ItemStack held = warlock.getMainHandItem();
		if (!has(warlock, KIND) || patron == null || !held.is(patron.offerings) || held.getCount() < KIND_COUNT) {
			return false;
		}
		held.shrink(KIND_COUNT);
		return true;
	}

	/** Sneak and the key while owing: Renege. Returns the debt's new kind, or null when it isn't that. */
	static String renege(ServerPlayer warlock, Warlocks.Pact pact, String[] kinds) {
		if (!has(warlock, RENEGE) || pact.reneged()) {
			return null;
		}
		int at = java.util.Arrays.asList(kinds).indexOf(pact.kind());
		return kinds[Math.floorMod(at + 1, kinds.length)];
	}

	// ---- casting --------------------------------------------------------------------------------------------------------------

	/** Arcanum: this cast, made sneaking, is the day's great one (not yet spent). */
	static boolean arcanum(ServerPlayer warlock) {
		return warlock.isShiftKeyDown() && has(warlock, ARCANUM) && Rests.left(warlock, ARCANUM_POOL, 1) > 0;
	}

	/** The Arcanum is cast: spent till a night's sleep. */
	static void arcanumCast(ServerPlayer warlock, ServerLevel level) {
		Rests.spend(warlock, ARCANUM_POOL, 1, 1);
		Fx.sigil(level, warlock.position().add(0.0, 0.05, 0.0), 2.0F, 0x8A4FD0, 30);
		level.playSound(null, warlock.getX(), warlock.getY(), warlock.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.4F, 1.6F);
		warlock.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.warlock.arcanum"));
	}

	// ---- the blows ------------------------------------------------------------------------------------------------------------

	/** Pact of the Blade: the pact weapon's blows land harder. */
	static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (source.getEntity() instanceof ServerPlayer warlock && source.getDirectEntity() == warlock && warlock != target && has(warlock, BLADE)
			&& warlock.getOffhandItem().is(Warlocks.SEAL) && !warlock.getMainHandItem().isEmpty()) {
			warlock.level().sendParticles(ParticleTypes.WITCH, target.getX(), target.getY(0.6), target.getZ(), 6, 0.2, 0.3, 0.2, 0.02);
			return amount * (1.0F + BLADE_HIT);
		}
		return amount;
	}

	// ---- Pact of the Chain ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 10 != 0) {
			return;
		}
		long now = server.overworld().getGameTime();
		FAMILIARS.entrySet().removeIf(e -> {
			ServerPlayer warlock = server.getPlayerList().getPlayer(e.getKey());
			Bat bat = e.getValue();
			if (warlock != null && bat.isAlive() && bat.level() == warlock.level() && has(warlock, CHAIN) && sealed(warlock) && warlock.isAlive()) {
				return false;
			}
			bat.discard();
			return true;
		});
		for (ServerPlayer warlock : server.getPlayerList().getPlayers()) {
			if (!has(warlock, CHAIN) || !sealed(warlock) || !warlock.isAlive() || warlock.isSpectator()) {
				continue;
			}
			Bat bat = FAMILIARS.get(warlock.getUUID());
			ServerLevel level = warlock.level();
			if (bat == null) {
				bat = EntityTypes.BAT.create(level, EntitySpawnReason.EVENT);
				if (bat == null) {
					continue;
				}
				bat.addTag(FAMILIAR_TAG);
				bat.setPermanentlyInvulnerable(true);
				bat.setNoAi(true);
				bat.setNoGravity(true);
				bat.setPos(warlock.getX(), warlock.getEyeY() + 0.5, warlock.getZ());
				FAMILIARS.put(warlock.getUUID(), bat);
				level.addFreshEntity(bat);
				Fx.burst(level, bat.getX(), bat.getY(), bat.getZ(), 0x8A4FD0, 0.6F, 10, 0.2, 0.2, 0.2);
			}
			familiar(warlock, level, bat, now);
		}
	}

	/** The familiar keeps by its Warlock, shows what lurks near, and stings what they fight. */
	private static void familiar(ServerPlayer warlock, ServerLevel level, Bat bat, long now) {
		LivingEntity foe = warlock.getLastHurtMob() != null && warlock.getLastHurtMob().isAlive() && warlock.tickCount - warlock.getLastHurtMobTimestamp() < 200
			? warlock.getLastHurtMob() : warlock.getLastHurtByMob();
		Vec3 home = warlock.position().add(0.0, warlock.getBbHeight() + 0.8, 0.0);
		if (foe != null && foe.isAlive() && foe.level() == level && foe.distanceTo(warlock) < CHAIN_SCOUT) {
			// to the foe, and a sting
			Vec3 to = foe.getEyePosition().subtract(bat.position());
			bat.setDeltaMovement(to.scale(Math.min(1.0, 0.6 / Math.max(0.1, to.length()))));
			if (now % CHAIN_EVERY < 10 && bat.distanceTo(foe) < 2.5) {
				foe.hurtServer(level, warlock.damageSources().indirectMagic(bat, warlock), CHAIN_STING);
				level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.BEE_STING, SoundSource.PLAYERS, 0.8F, 0.8F);
			}
		} else if (bat.position().distanceTo(home) > CHAIN_KEEP) {
			bat.teleportTo(home.x, home.y, home.z);
		} else {
			Vec3 to = home.subtract(bat.position());
			bat.setDeltaMovement(to.scale(0.2));
		}
		if (now % CHAIN_GLOW < 10) {
			// it scouts: what lurks near shows
			for (Mob m : level.getEntitiesOfClass(Mob.class, bat.getBoundingBox().inflate(CHAIN_SCOUT), m -> m.isAlive() && m instanceof Enemy)) {
				m.addEffect(new MobEffectInstance(MobEffects.GLOWING, CHAIN_GLOW + 10, 0, false, false, false));
			}
		}
	}
}
