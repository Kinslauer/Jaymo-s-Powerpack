package com.jaymoskingdoms;

import com.jaymoskingdoms.mixin.jobs.HunterArrowAccessor;
import com.jaymoskingdoms.mixin.jobs.HunterDrawAccessor;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A hunter's bow, for a player who holds the Hunter job ({@link Careers.Job#HUNTER}). Tuning is in the constants.
 * <ul>
 * <li><b>Bullseye</b> (Journeyman): hold a full draw with the sights on a beast for {@link #HOLD_TICKS}; a chime and
 * sparks tell you it is steady, and the shot you loose is a bullseye: it flies true, hits as a critical and goes
 * through {@link #PIERCE} beasts.</li>
 * <li><b>The yield at the cut</b>: an animal killed with the hunter's mark on it, or by a bullseye, leaves a better
 * carcass: when the hunter hangs and cuts it up with Jaymo's Kitchen's butchery, the butcher-table extras come
 * {@link #MARKED_EXTRA} (marked) or {@link #BULLSEYE_EXTRA} (bullseye) more times, a Master one more. The credit is
 * kept on the hunter ({@link #CREDITS}, saved, kept through death), one credit a carcass. Without Kitchen there are
 * no carcasses, and the kill drops the beast's meat and what it is hunted for instead.</li>
 * <li><b>From the saddle</b> (Journeyman): a bow is drawn twice as fast on horseback.</li>
 * </ul>
 */
public final class HunterBow {
	// ---- tuning --------------------------------------------------------------------------------------------------------

	/** Ticks of a bow's full draw. */
	static final int FULL_DRAW = 20;
	/** The sights must hold on one beast this long (ticks) after the full draw; within this cosine of the look; up to this far (blocks). */
	static final int HOLD_TICKS = 12;
	static final double AIM_COS = 0.985, RANGE = 48.0;
	/** A bullseye goes through this many beasts and hits this much harder (damage points). */
	static final byte PIERCE = 3;
	static final double BULLSEYE_DAMAGE = 1.5, ARROW_DAMAGE = 2.0;
	/** The steadiness stays this long (ticks) after the bow is loosed or lowered, to be put on the arrow. */
	static final int GRACE = 4;
	/** Extra times the butcher-table extras come on a hung carcass: after a marked kill, a bullseye kill; a Master adds one. */
	static final int MARKED_EXTRA = 1, BULLSEYE_EXTRA = 2, MASTER_EXTRA = 1;
	/** Credits kept for carcasses of each kind, at most. */
	static final int CREDIT_MOST = 6;
	/** Entity tag on an arrow loosed as a bullseye. */
	public static final String BULLSEYE = "jaymoskingdoms.bullseye";

	// ---- state ---------------------------------------------------------------------------------------------------------

	/** Carcasses owed a better cut: from marked kills and from bullseye kills. */
	public record Credits(int marked, int bullseye) {
		public static final Codec<Credits> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("marked").forGetter(Credits::marked),
			Codec.INT.fieldOf("bullseye").forGetter(Credits::bullseye)).apply(i, Credits::new));
		public static final Credits NONE = new Credits(0, 0);
	}

	/** On a player: the carcasses they are owed better cuts of. Saved, kept through death. */
	public static final AttachmentType<Credits> CREDITS = AttachmentRegistry.create(JaymosKingdoms.id("hunter_cut_credits"),
		builder -> builder.persistent(Credits.CODEC).copyOnDeath());

	/** One archer's sights: the beast, since when, whether it is steady, and the last tick the bow was held. */
	private static final class Aim {
		UUID target;
		long since, seen;
		boolean steady;
	}

	private static final Map<UUID, Aim> AIMS = new HashMap<>();
	/** Set once Kitchen's butchery hooks are in. */
	private static boolean kitchen;

	private HunterBow() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(HunterBow::tick);
		ServerEntityEvents.ENTITY_LOAD.register(HunterBow::loosed);
		ServerLivingEntityEvents.AFTER_DEATH.register(HunterBow::died);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> AIMS.clear());
	}

	/** (from {@code KitchenCompat}) Jaymo's Kitchen is here: a hunter's credits better the cuts of the carcass they hang. */
	static void kitchenHooks() {
		try {
			Class.forName("com.jaymoskitchen.corpse.Hanging").getMethod("addExtraCuts", java.util.function.ToIntFunction.class)
				.invoke(null, (java.util.function.ToIntFunction<net.minecraft.world.entity.player.Player>) HunterBow::owed);
			kitchen = true;
		} catch (ReflectiveOperationException e) {
			JaymosKingdoms.LOGGER.warn("Jaymo's Kitchen is too old for the hunter's better cuts (needs 0.5.57): {}", e.toString());
		}
	}

	/** How many more times the table extras come on the carcass {@code player} is finishing; one credit is spent. */
	private static int owed(net.minecraft.world.entity.player.Player player) {
		Credits c = player.getAttachedOrCreate(CREDITS, () -> Credits.NONE);
		int rank = Careers.heldRank(player, Careers.Job.HUNTER);
		if (rank < 0) {
			return 0;
		}
		int bonus = rank == Careers.MASTER ? MASTER_EXTRA : 0;
		if (c.bullseye() > 0) {
			player.setAttached(CREDITS, new Credits(c.marked(), c.bullseye() - 1));
			return BULLSEYE_EXTRA + bonus;
		}
		if (c.marked() > 0) {
			player.setAttached(CREDITS, new Credits(c.marked() - 1, c.bullseye()));
			return MARKED_EXTRA + bonus;
		}
		return 0;
	}

	// ---- the draw ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isUsingItem() || !player.getUseItem().is(Items.BOW) || Careers.heldRank(player, Careers.Job.HUNTER) < Careers.JOURNEYMAN) {
				continue;
			}
			int drawn = player.getTicksUsingItem();
			if (drawn < FULL_DRAW) {
				if (player.getVehicle() instanceof AbstractHorse) {
					// from the saddle the string comes back twice as fast
					HunterDrawAccessor draw = (HunterDrawAccessor) player;
					draw.jaymoskingdoms$left(draw.jaymoskingdoms$left() - 1);
				}
				continue;
			}
			sights(player, now);
		}
		if (!AIMS.isEmpty()) {
			AIMS.values().removeIf(a -> now - a.seen > GRACE);
		}
	}

	private static void sights(ServerPlayer player, long now) {
		LivingEntity beast = aimed(player);
		Aim aim = AIMS.get(player.getUUID());
		if (beast == null) {
			if (aim != null && !aim.steady) {
				AIMS.remove(player.getUUID());
			} else if (aim != null) {
				aim.seen = now;
			}
			return;
		}
		if (aim == null || !aim.target.equals(beast.getUUID())) {
			aim = new Aim();
			aim.target = beast.getUUID();
			aim.since = now;
			AIMS.put(player.getUUID(), aim);
		}
		aim.seen = now;
		if (!aim.steady && now - aim.since >= HOLD_TICKS) {
			aim.steady = true;
			ServerLevel level = player.level();
			level.sendParticles(player, ParticleTypes.CRIT, true, false, beast.getX(), beast.getEyeY(), beast.getZ(), 10, 0.25, 0.25, 0.25, 0.1);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, 1.6F);
		}
	}

	/** The beast the sights are on: nearest in the line of the look, in the clear. */
	private static @Nullable LivingEntity aimed(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		List<LivingEntity> near = player.level().getEntitiesOfClass(LivingEntity.class,
			player.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(2.0), e -> e != player && e.isAlive() && HunterPack.quarry(e));
		for (LivingEntity e : near) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double d = to.length();
			if (d < RANGE && d > 1.0 && to.normalize().dot(look) > AIM_COS && d < bestD && player.hasLineOfSight(e)) {
				best = e;
				bestD = d;
			}
		}
		return best;
	}

	/** An arrow is loosed by a hunter with steady sights: it is a bullseye. */
	private static void loosed(Entity entity, ServerLevel level) {
		if (!(entity instanceof AbstractArrow arrow) || arrow.tickCount > 0 || !(arrow.getOwner() instanceof ServerPlayer owner)) {
			return;
		}
		Aim aim = AIMS.get(owner.getUUID());
		if (aim == null || !aim.steady || level.getGameTime() - aim.seen > GRACE) {
			return;
		}
		AIMS.remove(owner.getUUID());
		arrow.addTag(BULLSEYE);
		arrow.setCritArrow(true);
		arrow.setBaseDamage(ARROW_DAMAGE + BULLSEYE_DAMAGE);
		((HunterArrowAccessor) arrow).jaymoskingdoms$pierce(PIERCE);
		level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.7F, 0.8F);
	}

	// ---- the kill ------------------------------------------------------------------------------------------------------

	private static void died(LivingEntity entity, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer hunter) || !(entity.level() instanceof ServerLevel level)
			|| !(entity instanceof Animal) || entity instanceof Enemy || entity.isBaby()) {
			return;
		}
		if (Careers.heldRank(hunter, Careers.Job.HUNTER) < 0) {
			return;
		}
		boolean bullseye = source.getDirectEntity() != null && source.getDirectEntity().entityTags().contains(BULLSEYE);
		boolean marked = hunter.getUUID().equals(HunterPack.markOwner(entity));
		if (!bullseye && !marked) {
			return;
		}
		if (kitchen) {
			Credits c = hunter.getAttachedOrCreate(CREDITS, () -> Credits.NONE);
			hunter.setAttached(CREDITS, bullseye ? new Credits(c.marked(), Math.min(CREDIT_MOST, c.bullseye() + 1))
				: new Credits(Math.min(CREDIT_MOST, c.marked() + 1), c.bullseye()));
			return;
		}
		// no butchery in the pack: no carcass to cut, so the kill itself gives more
		Item cut = Careers.cut(entity);
		if (cut != null) {
			entity.spawnAtLocation(level, new ItemStack(cut, 1 + (bullseye ? 1 : 0)));
		}
		Item hunted = huntedFor(entity);
		if (hunted != null) {
			entity.spawnAtLocation(level, new ItemStack(hunted, 1 + (bullseye ? 1 : 0)));
		}
	}

	/** What a beast's kind is hunted for, besides its meat. */
	private static @Nullable Item huntedFor(LivingEntity e) {
		String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
		return switch (id) {
			case "cow", "mooshroom", "horse", "donkey", "mule" -> Items.LEATHER;
			case "chicken" -> Items.FEATHER;
			case "rabbit" -> Items.RABBIT_HIDE;
			case "fox" -> Items.LEATHER;
			default -> null;
		};
	}
}
