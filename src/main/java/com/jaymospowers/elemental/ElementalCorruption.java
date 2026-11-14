package com.jaymospowers.elemental;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.compat.KingdomsCompat;
import com.jaymospowers.core.Carry;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.wounds.Mortality;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (Jung) <b>Elemental Corruption</b>: the gods of the elements answer a corrupted Wind, Smoke, Gravity or Cryokinesis
 * user who murders the innocent in their name. Each killing of a villager who is not a barbarian (vanilla's, MCA's or a
 * Kingdoms settlement's; never a camp's raider, chief or crew), while their power is corrupted, is counted
 * ({@link #KILLS}): the first and the second bring a warning; the third, the gods' wrath.
 * <ul>
 * <li><b>The reckoning</b>, once: the element turns on the land round the killer ({@link Wrath#erupt}).</li>
 * <li><b>Wrath</b>, {@link #WRATH_TICKS} (five minutes): the power runs at {@link #POWER} times (every blow they deal
 * does double), but {@link #RECOIL} of what each of their blows deals comes back on them; the corrupted element pours
 * off them ({@link Wrath#aura}) and burns them for {@link #BURN} every {@link #BURN_EVERY} ticks; every creature near
 * them but a boss or an orc runs from them; and the element works on the world round them ({@link Wrath#tick}).</li>
 * <li><b>Reclaimed</b>: when the wrath ends everything it raised disperses ({@link Wrath#disperse}) and the power is
 * taken for {@link #RECLAIM_TICKS} (three days): its keys do nothing and its passive tick stops ({@link Powers#silenced},
 * {@link Powers#paused}). Purified meanwhile (its own cure, or a Purifying Draught drunk in penance: {@link #penance}),
 * it comes back at once, clean; otherwise it comes back after the three days, still corrupted.</li>
 * </ul>
 * Each element's part is its own power's ({@link #register}): WindWrath, SmokeWrath, GravityWrath, CryoWrath.
 */
public final class ElementalCorruption {
	// ---- tuning ----
	/** Killings before the gods take the power back. */
	public static final int KILLS = 3;
	/**
	 * (Andy: "Remove the chat, add a cacophony of thunder and lightning over the user's general location") The gods
	 * speak in storms, never in chat: after each killing, bolts (the look of lightning: no fire, no harm) fall round the
	 * killer under a roll of thunder for this many ticks, the first warning, the second and the third (the reckoning).
	 */
	public static final int[] OUTCRY_TICKS = {60, 100, 160};
	/** A bolt every this many ticks of it (on average), landing this near to this far (blocks) round them, with this many thunderclaps besides. */
	public static final int OUTCRY_BOLT_EVERY = 4, OUTCRY_THUNDER_EVERY = 3;
	public static final double OUTCRY_NEAR = 6.0, OUTCRY_FAR = 28.0;
	/** The wrath lasts this long (ticks): five minutes. */
	public static final int WRATH_TICKS = 20 * 60 * 5;
	/** The power taken away after it, this long (ticks): three in-game days. */
	public static final int RECLAIM_TICKS = 24000 * 3;
	/** In wrath, every blow they deal is this many times as hard, and this share of what it dealt comes back on them. */
	public static final float POWER = 2.0F, RECOIL = 0.25F;
	/** The element burns its holder: this much (half a heart) every this many ticks (five seconds). */
	public static final float BURN = 1.0F;
	public static final int BURN_EVERY = 100;
	/** Creatures this near (blocks) run from them, looked at every this many ticks. */
	public static final double FLEE_RADIUS = 16.0;
	public static final int FLEE_EVERY = 20, FLEE_FROM = 16, FLEE_HEIGHT = 7;
	public static final double FLEE_SPEED = 1.35;
	/** The corrupted element pours off them every this many ticks. */
	public static final int AURA_EVERY = 4;

	/** Innocents killed while corrupted, toward {@link #KILLS}. Saved, kept through death. */
	public static final AttachmentType<Integer> KILLED = AttachmentRegistry.create(JaymosPowers.id("elemental_killed"),
		builder -> builder.persistent(com.mojang.serialization.Codec.INT).copyOnDeath());
	/** When (game time) the wrath began. Saved, kept through death. */
	public static final AttachmentType<Long> WRATH = AttachmentRegistry.create(JaymosPowers.id("elemental_wrath"),
		builder -> builder.persistent(com.mojang.serialization.Codec.LONG).copyOnDeath());
	/** When (game time) the reclaimed power comes back. Saved, kept through death. */
	public static final AttachmentType<Long> RECLAIMED = AttachmentRegistry.create(JaymosPowers.id("elemental_reclaimed"),
		builder -> builder.persistent(com.mojang.serialization.Codec.LONG).copyOnDeath());

	/** What an element's gods do: one for each elemental power. */
	public interface Wrath {
		/** The third killing: the element turns on the land round {@code player}, once. */
		void erupt(ServerPlayer player, ServerLevel level);

		/** Every tick of the wrath, {@code age} ticks in: what the element does to the world round them. */
		void tick(ServerPlayer player, ServerLevel level, long age);

		/** Every {@link #AURA_EVERY} ticks of the wrath: the corrupted element pouring off them. */
		void aura(ServerPlayer player, ServerLevel level);

		/** The wrath is over: what it raised goes. */
		default void disperse(ServerPlayer player, ServerLevel level) {
		}
	}

	private static final Map<Power, Wrath> WRATHS = new LinkedHashMap<>();
	/** Players under the gods' outcry just now → its ticks left (not saved: a storm, not a state). */
	private static final Map<java.util.UUID, Integer> OUTCRIES = new java.util.HashMap<>();
	/** True while an element's own wrath is working on the world ({@link Wrath#tick}): what it does there is the gods', not doubled and never recoiling. */
	private static boolean working;

	private ElementalCorruption() {
	}

	/** An elemental power's part in it (from that power's own init). */
	public static void register(Power power, Wrath wrath) {
		WRATHS.put(power, wrath);
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DEATH.register(ElementalCorruption::died);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> recoil(entity, source, damageTaken));
		ServerTickEvents.END_SERVER_TICK.register(ElementalCorruption::tick);
		Powers.addSilencer(ElementalCorruption::reclaimed);
		Powers.addPauser(ElementalCorruption::reclaimed);
	}

	/** {@code player}'s element's wrath, or null if their power isn't elemental. */
	private static @Nullable Wrath wrath(Player player) {
		Power power = Powers.of(player);
		return power == null ? null : WRATHS.get(power);
	}

	/** Whether the gods have {@code player}'s power for now. */
	public static boolean reclaimed(ServerPlayer player) {
		Long until = player.getAttached(RECLAIMED);
		return until != null && player.level().getGameTime() < until;
	}

	/** Whether {@code entity} is a player in the wrath right now. */
	public static boolean inWrath(@Nullable Object entity) {
		return entity instanceof ServerPlayer player && player.hasAttached(WRATH);
	}

	/**
	 * (LivingEntity mixin) A blow about to land: one dealt by a player in the wrath is {@link #POWER} times as hard
	 * (never one they deal themselves).
	 */
	public static float empower(LivingEntity target, DamageSource source, float amount) {
		return !working && source.getEntity() != target && inWrath(source.getEntity()) ? amount * POWER : amount;
	}

	/** A blow landed: {@link #RECOIL} of what a player in the wrath dealt comes back on them. */
	private static void recoil(LivingEntity target, DamageSource source, float dealt) {
		if (working || dealt <= 0.0F || !(source.getEntity() instanceof ServerPlayer player) || player == target || !inWrath(player)) {
			return;
		}
		// (a source with no attacker, so it never comes round again)
		player.hurtServer(player.level(), player.damageSources().magic(), dealt * RECOIL);
	}

	/** A Purifying Draught drunk while the gods have the power: penance, and the corruption is cleansed. */
	public static void penance(ServerPlayer player) {
		if ((reclaimed(player) || inWrath(player)) && Fate.isCorrupted(player) && Fate.purify(player)) {
			// (no chat: somewhere, something old accepts it)
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, 0.5F);
		}
	}

	/** A killing: an innocent (a villager who is not a barbarian) by a corrupted elemental user counts toward the wrath. */
	private static void died(LivingEntity victim, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer killer) || !(killer.level() instanceof ServerLevel level) || wrath(killer) == null
			|| !Fate.isCorrupted(killer) || inWrath(killer) || reclaimed(killer) || !innocent(victim)) {
			return;
		}
		int killed = killer.getAttachedOrElse(KILLED, 0) + 1;
		if (killed < KILLS) {
			killer.setAttached(KILLED, killed);
			// the warning: thunder and lightning over them, longer the second time
			outcry(killer, OUTCRY_TICKS[killed - 1]);
			return;
		}
		killer.removeAttached(KILLED);
		begin(killer, level);
	}

	/** A villager of any people but a barbarian camp's. */
	public static boolean innocent(LivingEntity victim) {
		return !(victim instanceof Player) && Carry.isVillager(victim) && !KingdomsCompat.barbarian(victim);
	}

	/** The gods' outcry over {@code player} for {@code ticks}: a cacophony of thunder and lightning round them. */
	public static void outcry(ServerPlayer player, int ticks) {
		OUTCRIES.merge(player.getUUID(), ticks, Math::max);
		player.level().playSound(null, player.getX(), player.getY() + 12.0, player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 6.0F, 0.5F);
	}

	/** One tick of an outcry: now and then a bolt (the look of one) round them, and thunder rolling from all sides. */
	private static void outcryTick(ServerPlayer player, ServerLevel level) {
		Integer left = OUTCRIES.get(player.getUUID());
		if (left == null) {
			return;
		}
		if (left <= 1) {
			OUTCRIES.remove(player.getUUID());
		} else {
			OUTCRIES.put(player.getUUID(), left - 1);
		}
		net.minecraft.util.RandomSource random = level.getRandom();
		if (random.nextInt(OUTCRY_BOLT_EVERY) == 0) {
			double angle = random.nextDouble() * net.minecraft.util.Mth.TWO_PI;
			double far = net.minecraft.util.Mth.lerp(random.nextDouble(), OUTCRY_NEAR, OUTCRY_FAR);
			int x = net.minecraft.util.Mth.floor(player.getX() + Math.cos(angle) * far), z = net.minecraft.util.Mth.floor(player.getZ() + Math.sin(angle) * far);
			int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
			net.minecraft.world.entity.LightningBolt bolt = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(level,
				net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
			if (bolt != null) {
				bolt.snapTo(x + 0.5, y, z + 0.5);
				// (the look of it: the gods are shouting, not striking yet)
				bolt.setVisualOnly(true);
				level.addFreshEntity(bolt);
			}
		}
		if (random.nextInt(OUTCRY_THUNDER_EVERY) == 0) {
			double angle = random.nextDouble() * net.minecraft.util.Mth.TWO_PI;
			level.playSound(null, player.getX() + Math.cos(angle) * 16.0, player.getY() + 10.0 + random.nextDouble() * 10.0,
				player.getZ() + Math.sin(angle) * 16.0, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 4.0F + random.nextFloat() * 4.0F,
				0.5F + random.nextFloat() * 0.5F);
		}
	}

	/** The third killing: the reckoning, then the wrath. Also from the test command. */
	public static void begin(ServerPlayer player, ServerLevel level) {
		Wrath wrath = wrath(player);
		if (wrath == null) {
			return;
		}
		player.setAttached(WRATH, level.getGameTime());
		// the gods' loudest outcry, over the reckoning
		outcry(player, OUTCRY_TICKS[KILLS - 1]);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.5F, 0.6F);
		wrath.erupt(player, level);
	}

	/** Ends the wrath now: everything disperses and the gods keep the power for {@link #RECLAIM_TICKS}. Also from the test command. */
	public static void end(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level) || !player.hasAttached(WRATH)) {
			return;
		}
		player.removeAttached(WRATH);
		Wrath wrath = wrath(player);
		if (wrath != null) {
			wrath.disperse(player, level);
		}
		player.setAttached(RECLAIMED, level.getGameTime() + RECLAIM_TICKS);
		Powers.releaseKeys(player);
		// (no chat: one last roll of thunder as the gods take it)
		level.playSound(null, player.getX(), player.getY() + 12.0, player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 3.0F, 0.4F);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!(player.level() instanceof ServerLevel level)) {
				continue;
			}
			long now = level.getGameTime();
			Long until = player.getAttached(RECLAIMED);
			// (Jung: "it comes back immediately if you purify") purified by any means, the gods give it back at once
			if (until != null && (now >= until || !Fate.isCorrupted(player))) {
				player.removeAttached(RECLAIMED);
				// (no chat: the power stirring again is heard, a far roll if it is still corrupted, a chime if clean)
				level.playSound(null, player.getX(), player.getY(), player.getZ(), Fate.isCorrupted(player) ? SoundEvents.LIGHTNING_BOLT_THUNDER
					: SoundEvents.AMETHYST_BLOCK_CHIME, Fate.isCorrupted(player) ? SoundSource.WEATHER : SoundSource.PLAYERS, 1.0F, 0.6F);
			}
			if (player.isAlive()) {
				outcryTick(player, level);
			}
			Long since = player.getAttached(WRATH);
			if (since == null) {
				continue;
			}
			Wrath wrath = wrath(player);
			long age = now - since;
			if (wrath == null || age >= WRATH_TICKS || age < 0) {
				// over (or the power changed, or time went back)
				end(player);
				continue;
			}
			if (!player.isAlive() || player.isSpectator()) {
				continue;
			}
			if (age % AURA_EVERY == 0) {
				wrath.aura(player, level);
			}
			if (age > 0 && age % BURN_EVERY == 0) {
				player.hurtServer(level, player.damageSources().magic(), BURN);
			}
			if (age % FLEE_EVERY == 0) {
				scatter(player, level);
			}
			// (the element's own work, Smoke's choke say, is the gods' doing: as strong as written, and nothing comes back)
			working = true;
			try {
				wrath.tick(player, level, age);
			} finally {
				working = false;
			}
		}
	}

	/** Every creature near them runs, but a boss or an orc, which stand. */
	private static void scatter(ServerPlayer player, ServerLevel level) {
		Vec3 from = player.position();
		for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(from, from).inflate(FLEE_RADIUS), Mob::isAlive)) {
			if (Mortality.boss(mob) || KingdomsCompat.boss(mob) || KingdomsCompat.orc(mob)) {
				continue;
			}
			if (mob.getTarget() == player) {
				mob.setTarget(null);
			}
			if (mob instanceof PathfinderMob runner) {
				Vec3 away = DefaultRandomPos.getPosAway(runner, FLEE_FROM, FLEE_HEIGHT, from);
				if (away != null) {
					runner.getNavigation().moveTo(away.x, away.y, away.z, FLEE_SPEED);
				}
			}
		}
	}

	/**
	 * The creatures near {@code player} an element works on: every living thing but the holder, other players too (Jung:
	 * "it can effect other players as well"), though not one in creative or spectating.
	 */
	public static java.util.List<LivingEntity> near(ServerPlayer player, double radius) {
		Vec3 at = player.position();
		return player.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof Player other && other.isCreative())
				&& e.distanceToSqr(player) <= radius * radius);
	}

	/**
	 * {@code /power wrath <targets> start|end|lift|clear}, for testing: the reckoning and the wrath now (as if the third
	 * killing), the wrath over now (the power taken), the power given back now, everything forgotten (the count too).
	 */
	public static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> command() {
		return net.minecraft.commands.Commands.literal("wrath")
			.then(net.minecraft.commands.Commands.argument("targets", net.minecraft.commands.arguments.EntityArgument.players())
				.then(net.minecraft.commands.Commands.literal("start").executes(c -> each(c, player -> {
					if (wrath(player) == null || !(player.level() instanceof ServerLevel level)) {
						return false;
					}
					player.removeAttached(RECLAIMED);
					begin(player, level);
					return true;
				})))
				.then(net.minecraft.commands.Commands.literal("end").executes(c -> each(c, player -> {
					boolean was = player.hasAttached(WRATH);
					end(player);
					return was;
				})))
				.then(net.minecraft.commands.Commands.literal("lift").executes(c -> each(c, player -> {
					boolean was = player.hasAttached(RECLAIMED);
					player.removeAttached(RECLAIMED);
					return was;
				})))
				.then(net.minecraft.commands.Commands.literal("clear").executes(c -> each(c, player -> {
					if (player.hasAttached(WRATH)) {
						end(player);
					}
					player.removeAttached(RECLAIMED);
					player.removeAttached(KILLED);
					return true;
				}))));
	}

	private static int each(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c,
		java.util.function.Predicate<ServerPlayer> act) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		int done = 0;
		for (ServerPlayer player : net.minecraft.commands.arguments.EntityArgument.getPlayers(c, "targets")) {
			if (act.test(player)) {
				done++;
			}
		}
		int count = done;
		c.getSource().sendSuccess(() -> Component.translatable("commands.jaymospowers.wrath", count), true);
		return done;
	}
}
