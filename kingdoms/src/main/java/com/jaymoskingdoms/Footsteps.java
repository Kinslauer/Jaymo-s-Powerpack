package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Footsteps others hear (Andy's ImmersiveFX pick, "wolves and guards could hear them"; the sounds themselves are Jaymo's
 * Powers' Footfalls, on each client). Every {@link #EVERY} ticks a walking player's feet carry {@link #WALK_REACH}
 * blocks, a running one's {@link #RUN_REACH}, times:
 * <ul>
 * <li>the ground ({@link #ground}): metal and gravel loudest, then wood, snow and stone, mud, and soft grass, sand and
 * carpet the quietest; wet ground (rain on it, or wading) {@link #WET} louder; Feather Falling boots and Swift Sneak
 * leggings each {@link #SOFT_GEAR} as loud ({@link #underfoot});</li>
 * <li>the armour worn ({@link #ARMOUR_PER_POINT} more a point of armour, so plate is heard farther than leather);</li>
 * <li>sneaking (the same share as for every other noise, {@link Awareness#noise}, so Quiet Step's ranks count), and the
 * stance wheel's low crouch ({@link #LOW}) and crawling ({@link #PRONE}) less again;</li>
 * <li>the walls between the feet and the ear, by what they're made of ({@link Hearing}, {@link SoundRay}).</li>
 * </ul>
 * Who hears it, as loud as what's left of the reach where they are:
 * <ul>
 * <li><b>wolves</b> (and anything in {@link #HEARS}): a wild one comes to see (the same search as for a player it lost);
 * a tame one turns its head;</li>
 * <li><b>guards</b> (a realm's, a camp's, an MCA guard): awake, they turn to the sound for {@link #LOOK_TICKS}, so their
 * own eyes take it from there; asleep, loud feet ({@link #WAKE_REACH} or more: running, or plate on gravel) may wake them
 * as a chest's creak does ({@link ChestNoise}), more likely the closer.</li>
 * </ul>
 * Creative and spectator players make none. (Medieval picks, Hearing) Every monster hears them too now, walking or
 * running ({@link Hearing#noise}); the guards and animals here answer what they hear in {@link #heard}, which every other
 * noise reaches as well: a decoy, a shot or a strike draws a guard over, and at night a guard hears a trespasser talk.
 */
public final class Footsteps {
	/** How often footsteps are heard (ticks), and how far a walk and a run carry (blocks, before the rest). */
	static final int EVERY = 10;
	static final double WALK_REACH = 6.0, RUN_REACH = 12.0;
	/** Slower than this (blocks a check) and there are no footsteps. */
	static final double MIN_MOVED = 0.4;
	/** Armour: this much farther per point of armour worn. */
	static final double ARMOUR_PER_POINT = 0.025;
	/** The low crouch and crawling, on top of sneaking's share. */
	static final double LOW = 0.7, PRONE = 0.4;
	/** Wet ground carries this much further; Feather Falling boots and Swift Sneak leggings each leave this share. */
	static final double WET = 1.25, SOFT_GEAR = 0.8;
	/** The "heard walking" floor ({@link #heardFloor}): quiet ground is at most this; ground this loud or more ({@link #ground}: gravel, metal) is loud. */
	static final double QUIET_FLOOR = 0.7, LOUD_FLOOR = 1.3;
	/** Too faint to notice (what's left of the reach, blocks). */
	static final double FAINTEST = 1.0;
	/** Guards: how long they look; the reach that can wake one, and the chance at arm's length. */
	static final int LOOK_TICKS = 40;
	static final double WAKE_REACH = 8.0;
	static final float WAKE_CHANCE = 0.35F;
	/** A decoy, a shot or a strike this loud where a guard stands (blocks of its reach left) draws him over to look; and the speed he comes at. */
	static final double COME_LOUDNESS = 4.0, COME_SPEED = 1.0;

	/** Animals that hear footsteps: wolves and foxes, and any a pack adds. */
	public static final TagKey<EntityType<?>> HEARS = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("hears_footsteps"));

	private record Look(Vec3 at, long until) {
	}

	private static final Map<UUID, Vec3> LAST = new HashMap<>();
	private static final Map<Mob, Look> LOOKING = new WeakHashMap<>();

	private Footsteps() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Footsteps::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LAST.clear();
			LOOKING.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		looks();
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Vec3 now = player.position();
			Vec3 last = LAST.put(player.getUUID(), now);
			if (last == null || player.isCreative() || player.isSpectator() || !player.isAlive() || player.isPassenger() || !player.onGround()
				|| !(player.level() instanceof ServerLevel level)) {
				continue;
			}
			double dx = now.x - last.x, dz = now.z - last.z;
			if (dx * dx + dz * dz < MIN_MOVED * MIN_MOVED) {
				continue;
			}
			double reach = reach(level, player);
			if (reach >= FAINTEST) {
				// (everyone near hears it through the walls: Hearing, back to heard below for guards and animals)
				Hearing.noise(level, now, reach, player.getUUID(), Hearing.Kind.STEP);
			}
		}
	}

	/** How far {@code player}'s feet carry right now (blocks). */
	static double reach(ServerLevel level, ServerPlayer player) {
		double reach = player.isSprinting() ? RUN_REACH : WALK_REACH;
		reach *= underfoot(level, player);
		reach *= 1.0 + ARMOUR_PER_POINT * player.getArmorValue();
		reach *= Awareness.noise(player);
		if (player.getPose() == Pose.SWIMMING && !player.isInWater()) {
			reach *= PRONE;
		} else if (player.isCrouching() && PowersCompat.lowStance(player)) {
			reach *= LOW;
		}
		return reach;
	}

	/**
	 * How much louder {@code player}'s steps are than on dry stone in plain boots: the ground (a carpet or a snow layer
	 * they stand in counts, not the floor under it), wet ground, and Feather Falling boots and Swift Sneak leggings.
	 */
	static double underfoot(ServerLevel level, ServerPlayer player) {
		return ground(standing(level, player)) * wetAndShod(level, player);
	}

	/**
	 * What the 8-block "heard walking" of {@link Senses} is multiplied by: ordinary ground (grass, dirt, sand, stone, wood) is
	 * 1, so a walk on it is heard as far as ever; only quiet ground (wool, carpet, snow, moss) is less, at most
	 * {@link #QUIET_FLOOR}, and loud ground (gravel, metal) more; then wet ground and soft boots and leggings as for steps.
	 */
	static double heardFloor(ServerLevel level, ServerPlayer player) {
		SoundType s = standing(level, player);
		double g = ground(s);
		boolean quiet = s == SoundType.WOOL || s == SoundType.MOSS_CARPET || s == SoundType.MOSS || s == SoundType.SNOW || s == SoundType.POWDER_SNOW;
		double base = quiet ? Math.min(g, QUIET_FLOOR) : g >= LOUD_FLOOR ? g : 1.0;
		return base * wetAndShod(level, player);
	}

	/** The ground's sound where {@code player} stands: a carpet or a snow layer they stand in, else the floor under them. */
	private static SoundType standing(ServerLevel level, ServerPlayer player) {
		SoundType in = level.getBlockState(player.blockPosition()).getSoundType();
		boolean layer = in == SoundType.WOOL || in == SoundType.MOSS_CARPET || in == SoundType.SNOW;
		return layer ? in : level.getBlockState(player.getOnPosLegacy()).getSoundType();
	}

	/** Wet ground louder, Feather Falling boots and Swift Sneak leggings quieter. */
	private static double wetAndShod(ServerLevel level, ServerPlayer player) {
		double f = 1.0;
		if (player.isInWater() || level.isRainingAt(player.blockPosition())) {
			f *= WET;
		}
		if (enchanted(level, player.getItemBySlot(EquipmentSlot.FEET), Enchantments.FEATHER_FALLING)) {
			f *= SOFT_GEAR;
		}
		if (enchanted(level, player.getItemBySlot(EquipmentSlot.LEGS), Enchantments.SWIFT_SNEAK)) {
			f *= SOFT_GEAR;
		}
		return f;
	}

	private static boolean enchanted(ServerLevel level, ItemStack stack, ResourceKey<Enchantment> key) {
		if (stack.isEmpty()) {
			return false;
		}
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key)
			.map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack) > 0).orElse(false);
	}

	/** How loud the ground is underfoot, beside stone. */
	static double ground(SoundType type) {
		if (type == SoundType.METAL || type == SoundType.ANVIL || type == SoundType.CHAIN || type == SoundType.COPPER || type == SoundType.IRON
			|| type == SoundType.NETHERITE_BLOCK) {
			return 1.4;
		}
		if (type == SoundType.GRAVEL || type == SoundType.SUSPICIOUS_GRAVEL) {
			return 1.3;
		}
		if (type == SoundType.WOOD || type == SoundType.BAMBOO_WOOD || type == SoundType.NETHER_WOOD || type == SoundType.CHERRY_WOOD
			|| type == SoundType.LADDER || type == SoundType.SCAFFOLDING) {
			return 1.2;
		}
		if (type == SoundType.SNOW || type == SoundType.POWDER_SNOW) {
			return 1.1;
		}
		if (type == SoundType.MUD || type == SoundType.SLIME_BLOCK || type == SoundType.HONEY_BLOCK) {
			return 0.9;
		}
		if (type == SoundType.GRASS || type == SoundType.MOSS || type == SoundType.MOSS_CARPET || type == SoundType.SAND || type == SoundType.SOUL_SAND
			|| type == SoundType.SOUL_SOIL || type == SoundType.ROOTED_DIRT || type == SoundType.LEAF_LITTER) {
			return 0.7;
		}
		if (type == SoundType.WOOL) {
			return 0.4;
		}
		return 1.0;
	}

	/**
	 * (Hearing) A guard or an animal that hears footsteps ({@code mob}) heard a noise of {@code reach} at {@code at} as loud as
	 * {@code loudness}, {@code player}'s if anyone's: awake, it turns to it (a guard comes over for a decoy, a shot or a
	 * strike); asleep, a loud one may wake a guard. A guard's ears take a voice only at night, and only a trespasser's. A
	 * wild animal also goes to look ({@link Hearing} sends it on to {@link Senses#hear}).
	 */
	static void heard(ServerLevel level, Mob mob, @Nullable ServerPlayer player, Vec3 at, double reach, double loudness, Hearing.Kind kind) {
		boolean guard = guard(mob);
		if (guard && kind == Hearing.Kind.VOICE && (player == null || !Vampires.night(level.getOverworldClockTime()) || !trespasser(level, player))) {
			return;
		}
		if (mob.isSleeping()) {
			if (guard && player != null && reach >= WAKE_REACH && level.getRandom().nextFloat() < WAKE_CHANCE * loudness / reach) {
				ChestNoise.wakeBy(level, mob, player, BlockPos.containing(at));
			}
			return;
		}
		LOOKING.put(mob, new Look(at, level.getGameTime() + LOOK_TICKS));
		if (guard && loudness >= COME_LOUDNESS && (kind == Hearing.Kind.DECOY || kind == Hearing.Kind.SHOT || kind == Hearing.Kind.IMPACT
			|| kind == Hearing.Kind.VOICE)) {
			// something thrown, a shot, a voice in the night: he goes over to see
			mob.getNavigation().moveTo(at.x, at.y, at.z, COME_SPEED);
		}
	}

	/** {@code player} has no business here at night: not welcome in the realm whose land it is. */
	private static boolean trespasser(ServerLevel level, ServerPlayer player) {
		Faction f = Theft.lawAt(level, player.blockPosition());
		return f == null || !Theft.exempt(player, f) && Reputation.get(player, f.id()) < Trespass.FRIEND_REP;
	}

	/** A realm's or camp's guard or archer, or an MCA villager guarding a settlement. */
	static boolean guard(Mob mob) {
		if (mob instanceof Folk folk) {
			return folk.role() == Folk.GUARD || folk.role() == Folk.ARCHER;
		}
		return mob instanceof Worker worker && worker.role() == Folk.GUARD;
	}

	/** Keeps the ones who heard looking toward the sound a while. */
	private static void looks() {
		if (LOOKING.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<Mob, Look>> it = LOOKING.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Mob, Look> e = it.next();
			Mob mob = e.getKey();
			Look look = e.getValue();
			if (mob.isRemoved() || !mob.isAlive() || mob.getTarget() != null || mob.level().getGameTime() >= look.until()) {
				it.remove();
				continue;
			}
			mob.getLookControl().setLookAt(look.at().x, look.at().y + 1.0, look.at().z);
		}
	}
}
