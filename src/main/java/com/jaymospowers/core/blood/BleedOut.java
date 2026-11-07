package com.jaymospowers.core.blood;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.BladeBlood;
import com.jaymospowers.core.wounds.Downed;
import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.21.28) Andy: "blood should pool under the downed and corpses until they bleed out, that way blood sorcerers and
 * vampires have finite resources". Every body holds so much blood ({@link #full}: a man's {@link #MAN}, more for the
 * bigger, less for the small), in stain size, the same measure blood magic draws in ({@link BloodTrail#draw}).
 * <ul>
 * <li>The <b>downed</b> bleed it into a pool under them ({@link BloodTrail#pour}), a little every second, so that by the
 * time they bleed out ({@link Downed#BLEED_OUT_TICKS}) {@link #DOWNED_SHARE} of it is on the ground. It's their own blood,
 * so hunters can track it to them. Back on their feet, they make it back over {@link #REFILL_TICKS}.</li>
 * <li>A <b>corpse</b> (Jaymo's Kitchen's) of anything that died bleeding (downed, cut, or bloodied) takes over whatever
 * blood the body still had and pours it out over {@link #CORPSE_TICKS}, the pool spreading wider as it goes, until it's
 * empty. A body that's carried, dragged (that leaves its smear) or hung on a hook (its own drip) holds it for later.</li>
 * </ul>
 * A pool full to {@link BloodTrail#MAX_SCALE} spills over beside it, so the whole of the blood lies there to be drawn up:
 * no more and no less. Once it's drawn up (or rained away), that body has none left to give.
 */
public final class BleedOut {
	/** The blood a man holds (stain size: a full pool is {@link BloodTrail#MAX_SCALE}); and the least and most any body holds. */
	static final float MAN = 3.0F, LEAST = 0.3F, MOST = 12.0F;
	/** A man's body (width times height), the measure for the rest. */
	static final float MAN_BULK = 0.6F * 1.8F;
	/** How often bodies bleed (ticks), and how near a player they must be (blocks: the stains go no further). */
	static final int EVERY = 20;
	static final double RANGE = 64.0;
	/** How much of their blood the downed have bled by the time they bleed out. */
	static final float DOWNED_SHARE = 0.6F;
	/** How long a corpse takes to pour out all a man's blood (ticks); and someone back up takes to make it all back. */
	static final int CORPSE_TICKS = 1800, REFILL_TICKS = 20 * 60 * 10;
	/** How far from the body's middle a pool can spread, at most (blocks, plus half its width). */
	static final double SPREAD = 0.9;
	/** Spots tried each time when the pool under it is full. */
	static final int TRIES = 4;

	/**
	 * The blood a living body has left (stain size), once it's begun to lose it; gone when it's whole again. Saved, so a
	 * downed body doesn't refill by a restart.
	 */
	public static final AttachmentType<Float> LEFT = AttachmentRegistry.create(JaymosPowers.id("blood_left"),
		builder -> builder.persistent(Codec.FLOAT));
	/** On a corpse: the blood it has still to pour out. Saved with the body. Gone once it's empty. */
	public static final AttachmentType<Float> CORPSE_LEFT = AttachmentRegistry.create(JaymosPowers.id("corpse_blood_left"),
		builder -> builder.persistent(Codec.FLOAT));

	private BleedOut() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(BleedOut::tick);
		hookCorpses();
	}

	/** Kitchen tells us of each new body; an older Kitchen (no hook) leaves bodies dry. */
	@SuppressWarnings("unchecked")
	private static void hookCorpses() {
		if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("jaymoskitchen")) {
			return;
		}
		try {
			((java.util.List<java.util.function.BiConsumer<LivingEntity, Entity>>) Class.forName("com.jaymoskitchen.corpse.Corpses")
				.getField("ON_BODY").get(null)).add(BleedOut::toCorpse);
		} catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
			JaymosPowers.LOGGER.warn("Jaymo's Kitchen has no body hook (older Kitchen?): corpses won't bleed out", e);
		}
	}

	/** All the blood a body of this size holds. */
	public static float full(float width, float height) {
		return Mth.clamp(MAN * width * height / MAN_BULK, LEAST, MOST);
	}

	public static float full(Entity body) {
		return full(body.getBbWidth(), body.getBbHeight());
	}

	/** The blood {@code body} has left (a living body's, or a corpse's still to pour). */
	public static float left(Entity body) {
		Float corpse = body.getAttached(CORPSE_LEFT);
		if (corpse != null) {
			return corpse;
		}
		Float living = body.getAttached(LEFT);
		return living != null ? living : body instanceof LivingEntity ? full(body) : 0.0F;
	}

	/**
	 * {@code dead} has a body now, {@code corpse}: if it died bleeding (downed, with open cuts, or bloodied by its last
	 * blows) the blood it still had pours out of the corpse.
	 */
	private static void toCorpse(LivingEntity dead, Entity corpse) {
		if (!BladeBlood.bleeds(dead.getType())) {
			return;
		}
		Bloodied.Coat coat = Bloodied.coat(dead);
		boolean bleeding = Downed.isDowned(dead) || dead.hasAttached(LEFT) || com.jaymospowers.core.wounds.Hemomancy.cuts(dead) > 0
			|| coat != null && coat.amount() > 0;
		if (!bleeding) {
			return;
		}
		float left = left(dead);
		if (left > 0.0F) {
			corpse.setAttached(CORPSE_LEFT, left);
		}
	}

	// ---- every second ---------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Set<Entity> seen = new HashSet<>();
			for (ServerPlayer player : level.players()) {
				for (Entity body : level.getEntities((Entity) null, player.getBoundingBox().inflate(RANGE), BleedOut::concerned)) {
					if (seen.add(body)) {
						step(level, body);
					}
				}
			}
		}
	}

	/** A body bleeding out (downed, or a corpse with blood still in it), or one making its blood back. */
	private static boolean concerned(Entity e) {
		if (e.hasAttached(CORPSE_LEFT)) {
			return !e.isRemoved();
		}
		return e instanceof LivingEntity living && living.isAlive() && (Downed.isDowned(living) || living.hasAttached(LEFT));
	}

	private static void step(ServerLevel level, Entity body) {
		Float corpseLeft = body.getAttached(CORPSE_LEFT);
		if (corpseLeft != null) {
			if (!com.jaymospowers.power.web.KitchenCompat.liesStill(body)) {
				return;
			}
			EntityType<?> type = com.jaymospowers.power.web.KitchenCompat.bodyType(body);
			if (type == null || !BladeBlood.bleeds(type)) {
				body.removeAttached(CORPSE_LEFT);
				return;
			}
			// a corpse's box is the body lying down: its blood is what it was standing
			float full = full(type.getWidth(), type.getHeight());
			float each = Math.max(0.02F, full * EVERY / CORPSE_TICKS);
			float left = corpseLeft - bleed(level, body, null, BladeBlood.color(type), Math.min(each, corpseLeft), full - corpseLeft, full);
			if (left <= 0.01F) {
				body.removeAttached(CORPSE_LEFT);
			} else {
				body.setAttached(CORPSE_LEFT, left);
			}
			return;
		}
		LivingEntity living = (LivingEntity) body;
		float full = full(living);
		float left = left(living);
		if (!Downed.isDowned(living)) {
			// back up: the blood comes back
			left += full * EVERY / REFILL_TICKS;
			if (left >= full) {
				living.removeAttached(LEFT);
			} else {
				living.setAttached(LEFT, left);
			}
			return;
		}
		if (!BladeBlood.bleeds(living) || living instanceof Player p && (p.isCreative() || p.isSpectator())) {
			return;
		}
		float each = full * DOWNED_SHARE * EVERY / Downed.BLEED_OUT_TICKS;
		// (0.21.49) resting in a bed only the cuts they aren't holding shut bleed, each at the downed rate of their own blood
		int cuts = living instanceof ServerPlayer resting ? com.jaymospowers.core.wounds.DownedStruggle.bleedingCuts(resting) : -1;
		if (cuts >= 0) {
			each *= cuts;
			if (each <= 0.0F) {
				return;
			}
		}
		if (left <= 0.0F) {
			return;
		}
		left -= bleed(level, living, living.getUUID(), BladeBlood.color(living), Math.min(each, left), full - left, full);
		living.setAttached(LEFT, Math.max(0.0F, left));
	}

	/**
	 * Pours {@code amount} of {@code body}'s blood into the pool under it, spreading wider the more of its {@code full}
	 * it has {@code lost}. Returns how much went in (none if there's no dry ground under it: it keeps it).
	 */
	private static float bleed(ServerLevel level, Entity body, @Nullable UUID by, int color, float amount, float lost, float full) {
		double reach = (body.getBbWidth() * 0.25 + SPREAD) * Mth.clamp(lost / Math.max(full, 0.01F), 0.0F, 1.0F);
		double y = body.getY() + 0.5;
		float in = 0.0F;
		for (int i = 0; i < TRIES && in < amount; i++) {
			double x = body.getX(), z = body.getZ();
			if (i > 0 || reach > 0.05) {
				double turn = level.getRandom().nextDouble() * Math.PI * 2.0;
				double r = (i == 0 ? level.getRandom().nextDouble() * 0.5 : 0.5 + level.getRandom().nextDouble() * 0.5) * Math.max(reach, 0.45);
				x += Math.cos(turn) * r;
				z += Math.sin(turn) * r;
			}
			in += BloodTrail.pour(level, by, color, x, y, z, amount - in);
		}
		return in;
	}
}
