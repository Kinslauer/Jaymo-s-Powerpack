package com.jaymoskingdoms;

import com.jaymoskingdoms.tinker.Mimic;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Class trees patch 8, the extras of the fighting and the working classes (docs/ClassTrees-Plan.md).
 *
 * <p><b>Warlord.</b> Drilled (a formation forms up twice as fast, and a soldier hit from afar keeps his place), Shield Wall
 * (blocking beside another raised shield, both stop shot from the front outright), Far Command (orders carry 48 blocks).
 * <b>Brute.</b> Thick Skull (a blow to the head knocks them out half as often) and Scar Tissue (wounds close a third
 * sooner), both asked by Jaymo's Powers ({@link PowersCompat#shareExtras}). <b>Marksman.</b> Dry Powder (rain, standing
 * water and a soaking don't stop the shot), the second crossroads Sharpshooter (knelt and still two seconds, the next shot
 * flies dead along the eye and a quarter further) or Gunslinger (for a second after a gun is drawn, everything that stands
 * for aiming is twice as quick). <b>Rider.</b> Farrier and Sure Seat are worked in {@code equestrian/}, their numbers here.
 * <b>Tinkerer.</b> The second crossroads Clockmaker (gadgets' waits a quarter shorter) or Scrapper (a gadget that wears
 * out gives back half its parts).
 */
public final class WarExtras {
	// ---- Warlord ----
	/** Drilled: how many times as fast a formation's soldiers go to their places. */
	static final float DRILLED_PACE = 2.0F;
	/** Shield Wall: how near the other shield must be (blocks). */
	static final double WALL_REACH = 2.5;
	/** Far Command: how far orders carry (blocks; they carried {@link Followers#ALL_RANGE}). */
	static final double FAR_RANGE = 48.0;
	// ---- Brute ----
	/** Thick Skull: what the chance a blow to the head knocks them out is multiplied by. Scar Tissue: what the time a wound takes to close is. */
	static final float SKULL = 0.5F, SCAR_CLOSES = 2.0F / 3.0F;
	// ---- Marksman ----
	/** Sharpshooter: how long knelt and still (ticks), how much further the shot carries, and how far a step is. */
	static final int SHARP_TICKS = 40;
	static final float SHARP_RANGE = 1.25F;
	static final double SHARP_MOVED = 0.02;
	/** Gunslinger: how long after the draw (ticks), and what the waits of aiming are multiplied by. */
	static final int DRAW_TICKS = 20;
	static final float DRAW_HASTE = 0.5F;
	// ---- Rider ----
	/** Farrier: what feeding their own horse heals on top (health). */
	public static final float FARRIER_HEAL = 4.0F;
	// ---- Tinkerer ----
	/** Clockmaker: what a gadget's wait is multiplied by. Scrapper: the share of its parts a worn-out gadget gives back. */
	static final float CLOCK_WAIT = 0.75F, SCRAP_SHARE = 0.5F;

	public static final String DRILLED = "wl_drilled", WALL = "wl_wall", FAR = "wl_far", SKULL_KEY = "br_skull", SCAR = "br_scar", POWDER = "mm_powder",
		SHARP = "mm_sharp", SLINGER = "mm_slinger", FARRIER = "rid_farrier", SEAT = "rid_seat", CLOCK = "tink_clock", SCRAP = "tink_scrap";

	/** Sharpshooter: where each Marksman stood a tick ago, since when they have been still, and the tick their settled shot left. */
	private static final Map<UUID, Vec3> STOOD = new HashMap<>();
	private static final Map<UUID, Long> STILL_SINCE = new HashMap<>();
	private static final Map<UUID, Long> SHOT_AT = new HashMap<>();
	/** Gunslinger: the slot each player had a gun in a tick ago (-1: no gun in hand), and when it came to hand. */
	private static final Map<UUID, Integer> GUN_SLOT = new HashMap<>();
	private static final Map<UUID, Long> DRAWN = new HashMap<>();

	private WarExtras() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(WarExtras::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			STOOD.clear();
			STILL_SINCE.clear();
			SHOT_AT.clear();
			GUN_SLOT.clear();
			DRAWN.clear();
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !walled(entity, source));
	}

	// ---- Warlord --------------------------------------------------------------------------------------------------------------

	/** Drilled: how many times as fast {@code player}'s formation forms up (1: not drilled). */
	static float drilled(Player player) {
		return 1.0F + (DRILLED_PACE - 1.0F) * Math.min(1.0F, ClassTrees.power(player, DRILLED));
	}

	/** Far Command: what the reach of {@code player}'s orders is multiplied by. */
	static double far(Player player) {
		return 1.0 + (FAR_RANGE / Followers.ALL_RANGE - 1.0) * Math.min(1.0F, ClassTrees.power(player, FAR));
	}

	/** Shield Wall: {@code victim} blocks beside another raised shield, one of them a Shield Wall Warlord's, and the shot comes from the front. */
	private static boolean walled(LivingEntity victim, DamageSource source) {
		if (!source.is(DamageTypeTags.IS_PROJECTILE) || !raised(victim) || !(victim.level() instanceof ServerLevel level)) {
			return false;
		}
		Vec3 from = source.getSourcePosition();
		if (from == null || from.subtract(victim.position()).multiply(1.0, 0.0, 1.0).dot(victim.getLookAngle().multiply(1.0, 0.0, 1.0)) <= 0.0) {
			return false;
		}
		float wall = wallOf(victim);
		boolean beside = false;
		for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, victim.getBoundingBox().inflate(WALL_REACH), e -> e != victim && e.isAlive() && raised(e))) {
			beside = true;
			wall = Math.max(wall, wallOf(other));
		}
		if (!beside || wall <= 0.0F || level.getRandom().nextFloat() >= Math.min(1.0F, wall)) {
			return false;
		}
		level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 0.8F);
		return true;
	}

	private static boolean raised(LivingEntity e) {
		return e.isBlocking() || e instanceof Mob mob && MobShields.guarding(mob);
	}

	private static float wallOf(LivingEntity e) {
		return e instanceof Player player ? ClassTrees.power(player, WALL) : 0.0F;
	}

	// ---- Brute (Jaymo's Powers asks) ------------------------------------------------------------------------------------------

	/** Thick Skull: what the chance a blow to {@code victim}'s head knocks them out cold is multiplied by. */
	static double headKnock(LivingEntity victim) {
		return victim instanceof Player player ? 1.0 - (1.0 - SKULL) * Math.min(1.0F, ClassTrees.power(player, SKULL_KEY)) : 1.0;
	}

	/** Scar Tissue: what the time {@code body}'s wounds take to close by themselves is multiplied by. */
	static double closing(LivingEntity body) {
		return body instanceof Player player ? 1.0 - (1.0 - SCAR_CLOSES) * Math.min(1.0F, ClassTrees.power(player, SCAR)) : 1.0;
	}

	// ---- Marksman -------------------------------------------------------------------------------------------------------------

	/** Dry Powder: {@code shooter}'s powder takes whatever the wet (not under water, where no match burns). */
	static boolean dryPowder(LivingEntity shooter) {
		return shooter instanceof Player player && ClassTrees.has(player, POWDER) > 0 && !shooter.isUnderWater();
	}

	private static boolean settled(ServerPlayer shooter, long now) {
		Long shot = SHOT_AT.get(shooter.getUUID());
		if (shot != null && shot == now) {
			return true;
		}
		Long since = STILL_SINCE.get(shooter.getUUID());
		return since != null && now - since >= SHARP_TICKS;
	}

	/**
	 * Sharpshooter: a shot leaving {@code shooter}'s gun at {@code v}. Knelt and still long enough, it flies dead along the
	 * eye and a quarter faster, so further (every ball of the one shot; then they must settle again).
	 */
	static Vec3 sharpshot(ServerPlayer shooter, Vec3 v) {
		float sharp = ClassTrees.power(shooter, SHARP);
		long now = shooter.level().getGameTime();
		if (sharp <= 0.0F || !settled(shooter, now)) {
			return v;
		}
		SHOT_AT.put(shooter.getUUID(), now);
		return shooter.getLookAngle().scale(v.length() * (1.0F + (SHARP_RANGE - 1.0F) * Math.min(1.0F, sharp)));
	}

	/** Gunslinger: what a wait of aiming is multiplied by for {@code user} now (a moment after the draw, half). */
	public static float draw(LivingEntity user) {
		if (!(user instanceof ServerPlayer player)) {
			return 1.0F;
		}
		Long drawn = DRAWN.get(player.getUUID());
		if (drawn == null || player.level().getGameTime() - drawn > DRAW_TICKS) {
			return 1.0F;
		}
		return 1.0F - (1.0F - DRAW_HASTE) * Math.min(1.0F, ClassTrees.power(player, SLINGER));
	}

	// ---- Tinkerer -------------------------------------------------------------------------------------------------------------

	/** Clockmaker: a gadget's wait of {@code ticks}, as {@code player} waits it. */
	public static int wait(Player player, int ticks) {
		return Math.max(1, Math.round(ticks * (1.0F - (1.0F - CLOCK_WAIT) * Math.min(1.0F, ClassTrees.power(player, CLOCK)))));
	}

	/** A gadget of {@code player}'s wears by one; a Scrapper's that wears out gives back half of what it was built from ({@code cost}). */
	public static void wear(ServerPlayer player, ItemStack gadget, InteractionHand hand, List<Mimic.Part> cost) {
		gadget.hurtAndBreak(1, player, hand);
		float scrap = ClassTrees.power(player, SCRAP);
		if (!gadget.isEmpty() || scrap <= 0.0F) {
			return;
		}
		boolean any = false;
		for (Mimic.Part part : cost) {
			int back = Math.round(part.count() * SCRAP_SHARE * Math.min(1.0F, scrap));
			if (back > 0) {
				any = true;
				ItemStack parts = new ItemStack(part.item(), back);
				if (!player.getInventory().add(parts) && !parts.isEmpty()) {
					player.drop(parts, false, net.minecraft.util.Prediction.SERVER_ONLY);
				}
			}
		}
		if (any) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 0.8F);
		}
	}

	// ---- every tick -----------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			long now = player.level().getGameTime();
			boolean main = Workshop.gun(player.getMainHandItem());
			boolean armed = main || Workshop.gun(player.getOffhandItem());
			// Gunslinger: the gun comes to hand
			int slot = main ? player.getInventory().getSelectedSlot() : armed ? 40 : -1;
			Integer was = GUN_SLOT.put(id, slot);
			if (slot >= 0 && (was == null || was != slot)) {
				DRAWN.put(id, now);
			}
			// Sharpshooter: knelt (or crouched) and still, gun in hand
			Vec3 at = player.position();
			Vec3 stood = STOOD.put(id, at);
			Long shot = SHOT_AT.get(id);
			boolean fired = shot != null && shot < now;
			if (fired) {
				SHOT_AT.remove(id);
			}
			if (!armed || fired || stood == null || stood.distanceToSqr(at) > SHARP_MOVED * SHARP_MOVED || !(player.isShiftKeyDown() || KneelFirst.kneeling(player))
				|| ClassTrees.has(player, SHARP) <= 0) {
				STILL_SINCE.put(id, now);
			} else if (now - STILL_SINCE.getOrDefault(id, now) == SHARP_TICKS) {
				// (settled: a breath let out, heard by them alone)
				player.connection.send(new ClientboundSoundPacket(Holder.direct(SoundEvents.SPYGLASS_USE), SoundSource.PLAYERS, player.getX(), player.getEyeY(),
					player.getZ(), 0.7F, 1.4F, player.getRandom().nextLong()));
			}
		}
	}
}
