package com.jaymoskingdoms;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.153) Zombie awareness, after Zombie Awareness (Corosauce, ARR; its ideas, our own code). Andy: "player zombies
 * should work like this, and maybe work it in to help with our stealth". The mobs in {@link #AWARE} (the zombies: zombies,
 * husks, drowned, zombie villagers, and Jaymo's Kitchen's reanimated player corpses) go and look ({@link Senses#lure}, the same search as for a player they lost) when they
 * <ul>
 * <li><b>hear</b> something ({@link #heard}, from {@code ServerLevelGameEventMixin}): mining and placing blocks, explosions,
 * bows and crossbows, eating and drinking, doors, gates, trapdoors, buttons, levers, plates, tripwires, chests and barrels,
 * note blocks and jukeboxes, pistons, fuses, lightning, and a sprinting player's footsteps. Each has its own reach
 * ({@link #SOUNDS}); a mob goes for the loudest it hears, so a piston or TNT draws them off. A sneaking player's own noise
 * reaches {@link #SNEAK_NOISE} as far, and Quiet Step's ranks less ({@link #QUIET_STEP_NOISE}).</li>
 * <li><b>see a light</b> ({@link #light}): a player holding a torch (or anything that gives light), burning, or standing by
 * bright block light in the dark is seen from {@link #LIGHT_RANGE} blocks by a mob with a line to them. Shadowed's ranks
 * shorten it ({@link #SHADOWED_LIGHT_PER_RANK}).</li>
 * <li><b>smell blood</b> ({@link #bled}, {@link #smell}): a hurt player spills blood (red on the ground), and below
 * {@link #BLEED_HEALTH} of their health drips it now and then. Zombies within {@link #SCENT_RANGE} follow the freshest
 * trail. Blood lasts {@link #SCENT_TICKS}; Slip the Net halves it.</li>
 * <li><b>call the horde</b> ({@link #called}): a zombie that goes after a player groans, and the idle zombies within
 * {@link #HORDE_RANGE} come to where they are, again every {@link #HORDE_TICKS} of the chase.</li>
 * </ul>
 * Creative and spectator players make no noise, light or blood. The mod's optional wandering hordes are not made.
 */
public final class Awareness {
	// ---- tuning ----
	/** The mobs that are aware: a tag, so a pack can add skeletons, creepers, witches (the mod's other defaults) or modded ones. */
	public static final TagKey<EntityType<?>> AWARE = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("aware"));

	/** How far (blocks) each sound carries; a mob hears it as loud as what is left of that at its distance. */
	static final Map<Holder<GameEvent>, Double> SOUNDS = new IdentityHashMap<>();
	static {
		SOUNDS.put(GameEvent.EXPLODE, 48.0);
		SOUNDS.put(GameEvent.LIGHTNING_STRIKE, 40.0);
		SOUNDS.put(GameEvent.JUKEBOX_PLAY, 32.0);
		SOUNDS.put(GameEvent.NOTE_BLOCK_PLAY, 24.0);
		SOUNDS.put(GameEvent.INSTRUMENT_PLAY, 32.0);
		SOUNDS.put(GameEvent.PROJECTILE_SHOOT, 16.0);
		SOUNDS.put(GameEvent.PRIME_FUSE, 16.0);
		SOUNDS.put(GameEvent.BLOCK_DESTROY, 14.0);
		SOUNDS.put(GameEvent.BLOCK_PLACE, 10.0);
		SOUNDS.put(GameEvent.BLOCK_OPEN, 12.0);
		SOUNDS.put(GameEvent.BLOCK_CLOSE, 12.0);
		// buttons, levers, plates, tripwires, pistons
		SOUNDS.put(GameEvent.BLOCK_ACTIVATE, 14.0);
		SOUNDS.put(GameEvent.BLOCK_DEACTIVATE, 14.0);
		SOUNDS.put(GameEvent.BLOCK_ATTACH, 8.0);
		SOUNDS.put(GameEvent.BLOCK_DETACH, 8.0);
		SOUNDS.put(GameEvent.CONTAINER_OPEN, 10.0);
		SOUNDS.put(GameEvent.CONTAINER_CLOSE, 10.0);
		SOUNDS.put(GameEvent.EAT, 8.0);
		SOUNDS.put(GameEvent.DRINK, 8.0);
	}
	/** A sprinting player's footsteps carry this far, checked every {@link #STEP_TICKS}. */
	static final double SPRINT_NOISE = 12.0;
	static final int STEP_TICKS = 10;
	/** A sneaking player's own noise carries this share as far; Quiet Step rank 1 and 2 these instead. */
	static final double SNEAK_NOISE = 0.5;
	static final double[] QUIET_STEP_NOISE = {0.35, 0.2};
	/** Too faint to turn a mob (what is left of the reach at its distance, in blocks). */
	static final double FAINTEST = 1.0;

	/** Light: seen this far (blocks), Shadowed's ranks this much less each; how often it is looked for (ticks). */
	static final double LIGHT_RANGE = 40.0, SHADOWED_LIGHT_PER_RANK = 0.2;
	static final int LIGHT_TICKS = 20;
	/** Light: shows only where the sky's light is below this; block light at the eyes this bright, or a held light this bright, gives you away. */
	static final int DARK_BELOW = 8, BLOCK_LIGHT_MIN = 12, HELD_LIGHT_MIN = 10;
	/** How loud a light counts as (a near loud noise beats it). */
	static final double LIGHT_LOUDNESS = 6.0;

	/** Blood: hits under this much spill none; at or below this share of health, a drip every {@link #DRIP_TICKS} or so. */
	static final float BLEED_MIN = 1.0F, BLEED_HEALTH = 0.3F;
	static final int DRIP_TICKS = 60;
	/** Blood: how long it smells (ticks; Slip the Net divides by {@link Senses#SLIP_THE_NET_DIVISOR}), how far, how often zombies sniff (ticks). */
	static final int SCENT_TICKS = 1200, SNIFF_TICKS = 40;
	static final double SCENT_RANGE = 24.0;
	/** Blood: how loud it counts as fresh (fading to a third as it dries); at most this many spots per level. */
	static final double SCENT_LOUDNESS = 7.0;
	static final int MAX_SCENTS = 96;
	/** Blood: the colour on the ground, and how often it is shown (ticks). */
	static final int BLOOD_COLOUR = 0x6E0A0A, BLOOD_SHOW_TICKS = 20;

	/** The horde: how far a groan carries (blocks), how loud it counts as, and how often a chasing zombie calls again (ticks). */
	static final double HORDE_RANGE = 16.0, HORDE_LOUDNESS = 9.0;
	static final int HORDE_TICKS = 300;

	/** A spot of blood: where, whose, when it was spilled, and when it dries. */
	record Scent(Vec3 at, UUID who, long spilled, long dries) {
	}

	/** Each level's blood, newest last. */
	private static final Map<ServerLevel, Deque<Scent>> SCENTS = new WeakHashMap<>();
	/** Mob → when the blood it last followed was spilled (it only follows fresher, so it goes along the trail). */
	private static final Map<Mob, Long> FOLLOWED = new WeakHashMap<>();
	/** Mob → game time of its last horde call. */
	private static final Map<Mob, Long> CALLED = new WeakHashMap<>();
	/** Player → game time of their next drip. */
	private static final Map<UUID, Long> NEXT_DRIP = new HashMap<>();

	private Awareness() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Awareness::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && !blocked) {
				bled(player, source, taken);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SCENTS.clear();
			FOLLOWED.clear();
			CALLED.clear();
			NEXT_DRIP.clear();
		});
	}

	static boolean aware(Mob mob) {
		return mob.isAlive() && mob.getTarget() == null && mob.getType().builtInRegistryHolder().is(AWARE);
	}

	private static boolean silent(ServerPlayer player) {
		return player.isCreative() || player.isSpectator() || !player.isAlive();
	}

	/** The aware mobs within {@code range} of {@code at}. */
	private static List<Mob> near(ServerLevel level, Vec3 at, double range) {
		return level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(range), m -> aware(m) && m.distanceToSqr(at) < range * range);
	}

	/** Lures the aware mobs within {@code reach} of {@code at}, each as loud as what is left of the reach where it stands. */
	private static void sound(ServerLevel level, Vec3 at, double reach, @Nullable UUID who) {
		for (Mob mob : near(level, at, reach)) {
			double loudness = reach - mob.position().distanceTo(at);
			if (loudness >= FAINTEST) {
				Senses.lure(mob, at, who, loudness);
			}
		}
	}

	/** How far {@code player}'s own noise carries, as a share: less sneaking, and less with Quiet Step. */
	static double noise(ServerPlayer player) {
		if (!player.isCrouching()) {
			return 1.0;
		}
		int quiet = Skills.rank(player, Skills.Perk.QUIET_STEP);
		return quiet <= 0 ? SNEAK_NOISE : QUIET_STEP_NOISE[Math.min(quiet, QUIET_STEP_NOISE.length) - 1];
	}

	// ---- sound -------------------------------------------------------------------------------------------------------

	/** A game event happened at {@code at}: if it is one that makes noise, the aware mobs that hear it go to look. */
	public static void heard(ServerLevel level, Holder<GameEvent> event, Vec3 at, GameEvent.@Nullable Context context) {
		Double reach = SOUNDS.get(event);
		if (reach == null) {
			return;
		}
		Entity source = context == null ? null : context.sourceEntity();
		if (source instanceof Projectile projectile && projectile.getOwner() != null) {
			source = projectile.getOwner();
		}
		UUID who = null;
		double r = reach;
		if (source instanceof ServerPlayer player) {
			if (player.isCreative() || player.isSpectator()) {
				return;
			}
			who = player.getUUID();
			if (event != GameEvent.EXPLODE) {
				r *= noise(player);
			}
		}
		sound(level, at, r, who);
	}

	// ---- the tick: footsteps, light, blood ---------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long tick = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (silent(player) || !(player.level() instanceof ServerLevel level)) {
				continue;
			}
			if (tick % STEP_TICKS == 0 && player.isSprinting() && player.onGround()) {
				sound(level, player.position(), SPRINT_NOISE * noise(player), player.getUUID());
			}
			if ((tick + player.getId()) % LIGHT_TICKS == 0) {
				light(level, player);
			}
			if ((tick + player.getId()) % 20 == 0 && player.getHealth() <= player.getMaxHealth() * BLEED_HEALTH) {
				long now = level.getGameTime();
				if (NEXT_DRIP.getOrDefault(player.getUUID(), 0L) <= now) {
					NEXT_DRIP.put(player.getUUID(), now + DRIP_TICKS / 2 + player.getRandom().nextInt(DRIP_TICKS));
					spill(level, player, 2);
				}
			}
		}
		if (tick % BLOOD_SHOW_TICKS == 0 || tick % SNIFF_TICKS == 0) {
			for (ServerLevel level : server.getAllLevels()) {
				Deque<Scent> scents = SCENTS.get(level);
				if (scents == null || scents.isEmpty()) {
					continue;
				}
				long now = level.getGameTime();
				scents.removeIf(s -> s.dries <= now);
				if (tick % BLOOD_SHOW_TICKS == 0) {
					DustParticleOptions blood = new DustParticleOptions(BLOOD_COLOUR, 1.2F);
					for (Scent s : scents) {
						level.sendParticles(blood, s.at.x, s.at.y + 0.05, s.at.z, 2, 0.2, 0.0, 0.2, 0.0);
					}
				}
				if (tick % SNIFF_TICKS == 0) {
					smell(level, scents, now);
				}
			}
		}
	}

	/**
	 * {@code player} shows a light in the dark: one held (in either hand), their own fire, or bright block light about
	 * them. The aware mobs with a line to them within {@link #LIGHT_RANGE} (less with Shadowed) come to look.
	 */
	private static void light(ServerLevel level, ServerPlayer player) {
		BlockPos eyes = BlockPos.containing(player.getEyePosition());
		if (level.getBrightness(LightLayer.SKY, eyes) - level.getSkyDarken() >= DARK_BELOW) {
			return;
		}
		boolean shown = player.isOnFire() || held(player.getItemInHand(InteractionHand.MAIN_HAND)) || held(player.getItemInHand(InteractionHand.OFF_HAND))
			|| level.getBrightness(LightLayer.BLOCK, eyes) >= BLOCK_LIGHT_MIN;
		if (!shown) {
			return;
		}
		double range = LIGHT_RANGE * Math.max(0.2, 1.0 - SHADOWED_LIGHT_PER_RANK * Skills.rank(player, Skills.Perk.SHADOWED));
		Vec3 at = player.position();
		for (Mob mob : near(level, at, range)) {
			if (!Senses.searchingFor(mob, player) && mob.hasLineOfSight(player)) {
				Senses.lure(mob, at, player.getUUID(), LIGHT_LOUDNESS);
			}
		}
	}

	private static boolean held(ItemStack stack) {
		return !stack.isEmpty() && Block.byItem(stack.getItem()).defaultBlockState().getLightEmission() >= HELD_LIGHT_MIN;
	}

	/** {@code player} was hurt for {@code taken}: unless by fire, water, cold, hunger, magic or the like, they bleed. */
	static void bled(ServerPlayer player, DamageSource source, float taken) {
		if (taken < BLEED_MIN || silent(player) || !(player.level() instanceof ServerLevel level) || source.is(DamageTypeTags.IS_FIRE)
			|| source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_FREEZING) || source.is(DamageTypes.STARVE)
			|| source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.WITHER)
			|| source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.GENERIC_KILL)) {
			return;
		}
		spill(level, player, 4 + (int) Math.min(8.0F, taken));
	}

	/** Blood on the ground under {@code player} ({@code drops} of it shown at once), for zombies to smell. */
	private static void spill(ServerLevel level, ServerPlayer player, int drops) {
		long now = level.getGameTime();
		int lasts = SCENT_TICKS / (Skills.rank(player, Skills.Perk.SLIP_THE_NET) > 0 ? Senses.SLIP_THE_NET_DIVISOR : 1);
		Deque<Scent> scents = SCENTS.computeIfAbsent(level, l -> new ArrayDeque<>());
		Vec3 at = player.position();
		scents.addLast(new Scent(at, player.getUUID(), now, now + lasts));
		while (scents.size() > MAX_SCENTS) {
			scents.removeFirst();
		}
		level.sendParticles(new DustParticleOptions(BLOOD_COLOUR, 1.4F), at.x, at.y + 0.6, at.z, drops, 0.25, 0.3, 0.25, 0.0);
	}

	/** Each idle zombie near blood goes to the freshest spot it smells that is fresher than the last it followed and not underfoot. */
	private static void smell(ServerLevel level, Deque<Scent> scents, long now) {
		Map<Mob, Boolean> done = new IdentityHashMap<>();
		for (Iterator<Scent> it = scents.descendingIterator(); it.hasNext();) {
			Scent s = it.next();
			double fresh = (double) (s.dries - now) / Math.max(1L, s.dries - s.spilled);
			double range = SCENT_RANGE * (0.4 + 0.6 * fresh);
			for (Mob mob : near(level, s.at, range)) {
				if (done.containsKey(mob) || FOLLOWED.getOrDefault(mob, Long.MIN_VALUE) >= s.spilled || mob.distanceToSqr(s.at) < Senses.ARRIVED * Senses.ARRIVED) {
					continue;
				}
				done.put(mob, Boolean.TRUE);
				if (Senses.lure(mob, s.at, s.who, SCENT_LOUDNESS * (1.0 + 2.0 * fresh) / 3.0)) {
					FOLLOWED.put(mob, s.spilled);
				}
			}
		}
	}

	// ---- the horde ---------------------------------------------------------------------------------------------------

	/** {@code mob} is after {@code player}: if it is aware, it groans, and the idle aware ones near come to where they are. */
	static void called(Mob mob, ServerPlayer player) {
		if (!(mob.level() instanceof ServerLevel level) || !mob.getType().builtInRegistryHolder().is(AWARE)) {
			return;
		}
		long now = level.getGameTime();
		Long last = CALLED.get(mob);
		if (last != null && now - last < HORDE_TICKS) {
			return;
		}
		CALLED.put(mob, now);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 1.5F, 0.8F);
		Vec3 at = player.position();
		for (Mob other : near(level, mob.position(), HORDE_RANGE)) {
			if (other != mob) {
				Senses.lure(other, at, player.getUUID(), HORDE_LOUDNESS);
			}
		}
	}
}
