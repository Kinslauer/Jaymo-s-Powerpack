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
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.jaymoskingdoms.wild.SpiderMinds;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.155) Zombie awareness, after Zombie Awareness (Corosauce, ARR; its ideas, our own code). Andy: "player zombies
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
 * <p>(Medieval picks, Hearing) The sounds are no longer the zombies' alone: every noise goes through {@link Hearing}, so
 * every monster hears it with its own ears, through the walls, under the weather's cover. Bells ring far ({@link #BELL}),
 * a thrown thing is heard where it lands, and the sprinting player's footsteps are {@link Footsteps}' now (by the ground,
 * for everyone). Blood is smelled by every hunter in {@link #SMELLS_BLOOD} (the zombies, spiders, wolves, werewolves), and
 * a player bleeds below half their health ({@link #BLEED_HEALTH}). A horde's call spreads them round the spot.
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
	/** A bell rung (blocks); a thrown thing landing (blocks, unless {@link Hearing#decoy} says how far its item carries). */
	static final double BELL = 40.0, LANDED = 8.0;
	/** The hunters that follow blood: {@code jaymoskingdoms:smells_blood}. */
	public static final TagKey<EntityType<?>> SMELLS_BLOOD = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("smells_blood"));
	/** Hooves at a gallop are heard every this many ticks. */
	static final int STEP_TICKS = 10;
	/** A sneaking player's own noise carries this share as far; Quiet Step rank 1 and 2 these instead. */
	static final double SNEAK_NOISE = 0.5;
	static final double[] QUIET_STEP_NOISE = {0.35, 0.2};

	/** Light: seen this far (blocks), Shadowed's ranks this much less each; how often it is looked for (ticks). */
	static final double LIGHT_RANGE = 40.0, SHADOWED_LIGHT_PER_RANK = 0.2;
	static final int LIGHT_TICKS = 20;
	/** Light: shows only where the sky's light is below this; block light at the eyes this bright, or a held light this bright, gives you away. */
	static final int DARK_BELOW = 8, BLOCK_LIGHT_MIN = 12, HELD_LIGHT_MIN = 10;
	/** How loud a light counts as (a near loud noise beats it). */
	static final double LIGHT_LOUDNESS = 6.0;

	/** Blood: hits under this much spill none; at or below this share of health, a drip every {@link #DRIP_TICKS} or so. */
	static final float BLEED_MIN = 1.0F, BLEED_HEALTH = 0.5F;
	static final int DRIP_TICKS = 60;
	/** Blood: how long it smells (ticks; Slip the Net divides by {@link Senses#SLIP_THE_NET_DIVISOR}), how far, how often zombies sniff (ticks). */
	static final int SCENT_TICKS = 1200, SNIFF_TICKS = 40;
	static final double SCENT_RANGE = 24.0;
	/** Blood: how loud it counts as fresh (fading to a third as it dries); at most this many spots per level. */
	static final double SCENT_LOUDNESS = 7.0;
	static final int MAX_SCENTS = 96;
	/** Blood: the colour of the drops as it's spilled. */
	static final int BLOOD_COLOUR = 0x6E0A0A;
	/**
	 * Blood: the size of the stain it leaves on the ground (Powers' lasting stains, {@link PowersCompat#trailDrop}), for a
	 * drip and for a wound. It's laid once and lies still; the scent itself is not shown again.
	 */
	static final float DRIP_STAIN = 0.2F, WOUND_STAIN = 0.35F;

	/** The horde: how far a groan carries (blocks), how loud it counts as, and how often a chasing zombie calls again (ticks). */
	static final double HORDE_RANGE = 16.0, HORDE_LOUDNESS = 9.0;
	/** The horde closes on the player: its ring round them is no wider than this (blocks). */
	static final double HORDE_RING = 1.5;
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

	/** A noise of {@code reach} at {@code at}: everyone who hears it hears it through the walls ({@link Hearing}). */
	private static void sound(ServerLevel level, Vec3 at, double reach, @Nullable UUID who, Hearing.Kind kind) {
		Hearing.noise(level, at, reach, who, kind);
	}

	/** How far {@code player}'s own noise carries, as a share: less sneaking, and less with Quiet Step. */
	static double noise(ServerPlayer player) {
		return Rangers.quiet(player) * baseNoise(player);
	}

	private static double baseNoise(ServerPlayer player) {
		if (!player.isCrouching()) {
			return Pace.slow(player) ? Pace.NOISE : 1.0;
		}
		int quiet = Skills.rank(player, Skills.Perk.QUIET_STEP);
		return quiet <= 0 ? SNEAK_NOISE : QUIET_STEP_NOISE[Math.min(quiet, QUIET_STEP_NOISE.length) - 1];
	}

	// ---- sound -------------------------------------------------------------------------------------------------------

	/** A game event happened at {@code at}: if it is one that makes noise, the aware mobs that hear it go to look. */
	public static void heard(ServerLevel level, Holder<GameEvent> event, Vec3 at, GameEvent.@Nullable Context context) {
		Double reach = SOUNDS.get(event);
		Entity source = context == null ? null : context.sourceEntity();
		Hearing.Kind kind = event == GameEvent.EXPLODE || event == GameEvent.LIGHTNING_STRIKE ? Hearing.Kind.BLAST : Hearing.Kind.ACTION;
		if (event == GameEvent.BLOCK_CHANGE && level.getBlockState(BlockPos.containing(at)).getBlock() instanceof BellBlock) {
			// a bell rung
			reach = BELL;
			kind = Hearing.Kind.BLAST;
		} else if (event == GameEvent.PROJECTILE_LAND) {
			// a gun's ball strikes in Hearing; a thrown thing is as loud as what it is
			if (source == null) {
				return;
			}
			if (Hearing.gun(source)) {
				// a gun's ball strikes: heard there (Hearing), if it's far from the muzzle
				Hearing.struck(source, at);
				return;
			}
			reach = source instanceof ItemSupplier thrown ? Hearing.decoy(thrown.getItem()) : LANDED;
			kind = Hearing.Kind.DECOY;
		}
		if (reach == null) {
			return;
		}
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
			// (how quietly you move doesn't quieten a blast, a bell, or a thing landing over there)
			if (kind == Hearing.Kind.ACTION) {
				r *= noise(player);
			}
		}
		sound(level, at, r, who, kind);
	}

	// ---- the tick: footsteps, light, blood ---------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long tick = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (silent(player) || !(player.level() instanceof ServerLevel level)) {
				continue;
			}
			// (a sprinting player's footsteps are Footsteps' now, by the ground underfoot, and heard by everyone)
			if (tick % STEP_TICKS == 0 && Pace.ridingHard(player)) {
				// hooves at a gallop carry as far as a sprinting player's feet
				sound(level, player.position(), Pace.HOOVES, player.getUUID(), Hearing.Kind.STEP);
			}
			if ((tick + player.getId()) % LIGHT_TICKS == 0) {
				light(level, player);
			}
			if ((tick + player.getId()) % 20 == 0 && player.getHealth() <= player.getMaxHealth() * BLEED_HEALTH) {
				long now = level.getGameTime();
				if (NEXT_DRIP.getOrDefault(player.getUUID(), 0L) <= now) {
					NEXT_DRIP.put(player.getUUID(), now + DRIP_TICKS / 2 + player.getRandom().nextInt(DRIP_TICKS));
					spill(level, player, 2, DRIP_STAIN);
				}
			}
		}
		if (tick % SNIFF_TICKS == 0) {
			for (ServerLevel level : server.getAllLevels()) {
				Deque<Scent> scents = SCENTS.get(level);
				if (scents == null || scents.isEmpty()) {
					continue;
				}
				long now = level.getGameTime();
				scents.removeIf(s -> s.dries <= now);
				smell(level, scents, now);
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

	/** The brightest light {@code player} holds in either hand (0 to 15), for the stealth meter. */
	static int heldLight(ServerPlayer player) {
		int main = player.getMainHandItem().isEmpty() ? 0 : Block.byItem(player.getMainHandItem().getItem()).defaultBlockState().getLightEmission();
		int off = player.getOffhandItem().isEmpty() ? 0 : Block.byItem(player.getOffhandItem().getItem()).defaultBlockState().getLightEmission();
		return Math.max(main, off);
	}

	/** {@code player} is hurt enough to leave a blood trail. */
	static boolean bleeding(ServerPlayer player) {
		return !silent(player) && player.getHealth() <= player.getMaxHealth() * BLEED_HEALTH;
	}

	/** {@code player} was hurt for {@code taken}: unless by fire, water, cold, hunger, magic or the like, they bleed. */
	static void bled(ServerPlayer player, DamageSource source, float taken) {
		if (taken < BLEED_MIN || silent(player) || !(player.level() instanceof ServerLevel level) || source.is(DamageTypeTags.IS_FIRE)
			|| source.is(DamageTypeTags.IS_DROWNING) || source.is(DamageTypeTags.IS_FREEZING) || source.is(DamageTypes.STARVE)
			|| source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.WITHER)
			|| source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.GENERIC_KILL)) {
			return;
		}
		spill(level, player, 4 + (int) Math.min(8.0F, taken), WOUND_STAIN);
	}

	/**
	 * Blood on the ground under {@code player}, for zombies to smell: {@code drops} of it falling at once, and a stain this
	 * big where it lands that stays put (Andy: blood that has landed stays still; it used to be shown again every second).
	 */
	private static void spill(ServerLevel level, ServerPlayer player, int drops, float stain) {
		long now = level.getGameTime();
		int lasts = SCENT_TICKS / (Skills.rank(player, Skills.Perk.SLIP_THE_NET) > 0 ? Senses.SLIP_THE_NET_DIVISOR : 1);
		Deque<Scent> scents = SCENTS.computeIfAbsent(level, l -> new ArrayDeque<>());
		Vec3 at = player.position();
		scents.addLast(new Scent(at, player.getUUID(), now, now + lasts));
		while (scents.size() > MAX_SCENTS) {
			scents.removeFirst();
		}
		level.sendParticles(PowersCompat.blood(BLOOD_COLOUR, 1.4F), at.x, at.y + 0.6, at.z, drops, 0.25, 0.3, 0.25, 0.0);
		PowersCompat.trailDrop(level, player, stain);
	}

	/**
	 * Each idle hunter near blood ({@link #SMELLS_BLOOD}: the zombies, spiders, wolves, werewolves) goes to the freshest spot
	 * it smells that is fresher than the last it followed and not underfoot. A tame wolf doesn't.
	 */
	private static void smell(ServerLevel level, Deque<Scent> scents, long now) {
		Map<Mob, Boolean> done = new IdentityHashMap<>();
		for (Iterator<Scent> it = scents.descendingIterator(); it.hasNext();) {
			Scent s = it.next();
			double fresh = (double) (s.dries - now) / Math.max(1L, s.dries - s.spilled);
			double range = SCENT_RANGE * (0.4 + 0.6 * fresh);
			for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(s.at, s.at).inflate(range), m -> smells(m) && m.distanceToSqr(s.at) < range * range)) {
				if (done.containsKey(mob) || FOLLOWED.getOrDefault(mob, Long.MIN_VALUE) >= s.spilled || mob.distanceToSqr(s.at) < Senses.ARRIVED * Senses.ARRIVED) {
					continue;
				}
				done.put(mob, Boolean.TRUE);
				boolean followed = mob instanceof Spider spider ? SpiderMinds.heard(spider, s.at)
					: Senses.lure(mob, s.at, s.who, SCENT_LOUDNESS * (1.0 + 2.0 * fresh) / 3.0);
				if (followed) {
					FOLLOWED.put(mob, s.spilled);
				}
			}
		}
	}

	/** {@code mob} hunts by the smell of blood, and is free to. */
	private static boolean smells(Mob mob) {
		return mob.isAlive() && mob.getTarget() == null && mob.getType().builtInRegistryHolder().is(SMELLS_BLOOD)
			&& !(mob instanceof TamableAnimal tame && tame.isTame());
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
		List<Mob> horde = near(level, mob.position(), HORDE_RANGE);
		horde.remove(mob);
		// (Hearing) they close in round where the player is, on ground they can stand on, instead of all making for the one block
		double base = Math.atan2(mob.getZ() - at.z, mob.getX() - at.x);
		for (int i = 0; i < horde.size(); i++) {
			Mob other = horde.get(i);
			if (Senses.lure(other, Hearing.ringSpot(level, at, base, i + 1, horde.size() + 1, HORDE_RING), player.getUUID(), HORDE_LOUDNESS)) {
				Hearing.hold(other, player.getUUID(), now);
			}
		}
	}
}
