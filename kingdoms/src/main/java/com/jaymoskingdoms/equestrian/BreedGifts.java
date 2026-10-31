package com.jaymoskingdoms.equestrian;

import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.305) Each breed's gift to a Rider (the class) in its saddle (ideas from Icy's Better Horses' per-breed abilities,
 * our own code): it comes when the horse trusts them {@link #FIRST} and grows at {@link #SECOND}. Anyone else just rides
 * a horse. Tuning numbers live at the top.
 * <ul>
 * <li>Destrier, <b>Shield</b>: takes a blow that would kill its rider (once in a while).</li>
 * <li>Courser, <b>Burst</b>: a faster sprint.</li>
 * <li>Palfrey, <b>Road pace</b>: quicker on roads and paths.</li>
 * <li>Draft, <b>Herder</b>: farm animals about follow it.</li>
 * <li>Steppe pony, <b>Snort</b>: snorts monsters that come close away.</li>
 * <li>Desert horse, <b>Quick start</b>: gets up to speed in a bound.</li>
 * <li>Nightmare, <b>Dark sight</b>: its rider sees in the dark (and then doesn't burn).</li>
 * <li>Bat horse, <b>Soft landing</b>: no hurt from a drop that isn't too long, rider or horse.</li>
 * <li>War elephant, <b>Gentle giant</b>: angry wolves, bees, golems and the like calm down about it.</li>
 * <li>Woolly mammoth, <b>Snow strider</b>: walks on powder snow, and then its rider never freezes.</li>
 * </ul>
 */
public final class BreedGifts {
	/** The trust a gift comes at, and grows at. */
	public static final float FIRST = 40.0F, SECOND = 100.0F;
	/** Destrier: how much of the killing blow the horse takes itself, and how long till it can again (ticks, by tier). */
	public static final float SHIELD_SHARE = 0.6F;
	static final long[] SHIELD_COOLDOWN = {0L, 12000L, 6000L};
	/** Courser: the sprint's extra speed, by tier. */
	public static final double[] BURST = {0.0, 0.08, 0.16};
	/** Palfrey: the extra speed on a road or path, by tier. */
	public static final double[] ROAD = {0.0, 0.08, 0.15};
	/** Desert horse: the extra speed while it's under half its pace, by tier. */
	public static final double[] QUICK_START = {0.0, 0.25, 0.4};
	/** Draft: how far it gathers farm animals, by tier, and how close they come. */
	public static final double[] HERD_RANGE = {0.0, 8.0, 16.0};
	static final double HERD_NEAR = 4.0;
	/** Steppe pony: how close a monster may come before it's snorted away, and how often (ticks), by tier. */
	public static final double[] SNORT_RANGE = {0.0, 3.0, 4.5};
	static final int[] SNORT_TICKS = {0, 160, 100};
	/** Bat horse: falls shorter than this don't hurt, by tier (blocks). */
	public static final double[] SOFT_LANDING = {0.0, 8.0, 15.0};
	/** War elephant: how far angry neutral mobs calm down, by tier. */
	public static final double[] CALM_RANGE = {0.0, 8.0, 16.0};
	/** How often the gifts that act by themselves are seen to (ticks), and how long an effect lasts (refreshed while riding). */
	static final int STEP = 20, EFFECT_TICKS = 260;

	/** Rider → game time the destrier may take a killing blow again; horse id → when it may snort again. */
	private static final Map<UUID, Long> SHIELDED = new HashMap<>();
	private static final Map<Integer, Long> SNORTED = new HashMap<>();

	private BreedGifts() {
	}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(BreedGifts::allowDamage);
		ServerTickEvents.END_LEVEL_TICK.register(BreedGifts::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SHIELDED.clear();
			SNORTED.clear();
		});
	}

	/** How far {@code rider}'s gift from {@code horse} has come: 0 (none: not a Rider, or not trusted enough), 1, or 2. Both sides. */
	public static int tier(AbstractHorse horse, Player rider) {
		if (!Classes.is(rider, PlayerClass.RIDER)) {
			return 0;
		}
		Bond b = horse.getAttached(Bonding.BOND);
		float trust = b == null ? 0.0F : b.trustOf(rider.getUUID());
		return trust >= SECOND ? 2 : trust >= FIRST ? 1 : 0;
	}

	/** The gift's lang key ({@code gift.jaymoskingdoms.<breed>}; {@code .1} and {@code .2} say what each tier does). */
	public static String key(Breed breed) {
		return "gift.jaymoskingdoms." + breed.id();
	}

	private static @Nullable AbstractHorse mount(Player rider) {
		return rider.getVehicle() instanceof AbstractHorse horse && Equestrian.covered(horse) && horse.getControllingPassenger() == rider ? horse : null;
	}

	// ---- speed (both sides: the rider's client moves the horse) --------------------------------------------------------

	/** (From {@link Equestrian#riddenSpeed}) how much faster the gift makes it go now. */
	static double speed(AbstractHorse horse, Equine e, Player rider, boolean sprint, double target) {
		int tier = tier(horse, rider);
		if (tier == 0) {
			return 1.0;
		}
		return switch (e.breed()) {
			case COURSER -> sprint ? 1.0 + BURST[tier] : 1.0;
			case PALFREY -> road(horse.level().getBlockState(horse.getBlockPosBelowThatAffectsMyMovement())) ? 1.0 + ROAD[tier] : 1.0;
			case DESERT_HORSE -> target > 0.0 && horse.getDeltaMovement().horizontalDistance() < target * 0.5 ? 1.0 + QUICK_START[tier] : 1.0;
			// (0.7.444) the donkey keeps its pace under a pack; the mule is surest (and quicker) on bare stone and gravel
			case DONKEY -> horse instanceof net.minecraft.world.entity.animal.equine.AbstractChestedHorse c && c.hasChest() ? 1.0 + ROAD[tier] : 1.0;
			case MULE -> stony(horse.level().getBlockState(horse.getBlockPosBelowThatAffectsMyMovement())) ? 1.0 + ROAD[tier] : 1.0;
			default -> 1.0;
		};
	}

	/** (0.7.444) Bare stone or loose rock: the mule's ground. */
	static boolean stony(BlockState under) {
		return under.is(BlockTags.BASE_STONE_OVERWORLD) || under.is(Blocks.GRAVEL) || under.is(Blocks.COBBLESTONE) || under.is(Blocks.MOSSY_COBBLESTONE)
			|| under.is(Blocks.CALCITE) || under.is(Blocks.DRIPSTONE_BLOCK);
	}

	/** A road: a dirt path, gravel, or a laid stone or brick road. */
	static boolean road(BlockState under) {
		return under.is(Blocks.DIRT_PATH) || under.is(Blocks.GRAVEL) || under.is(BlockTags.STONE_BRICKS) || under.is(Blocks.COBBLESTONE)
			|| under.is(Blocks.MUD_BRICKS) || under.is(Blocks.BRICKS) || under.is(Blocks.SMOOTH_STONE) || under.is(Blocks.POLISHED_ANDESITE);
	}

	/** (From {@code PowderSnowMixin}) a mammoth under a Rider it trusts walks on powder snow. */
	public static boolean snowStrider(Entity e) {
		return e instanceof AbstractHorse horse && Equestrian.peek(horse) instanceof Equine eq && eq.breed() == Breed.WOOLLY_MAMMOTH
			&& horse.getControllingPassenger() instanceof Player rider && tier(horse, rider) >= 1;
	}

	// ---- blows -----------------------------------------------------------------------------------------------------------

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return true;
		}
		// the bat horse's soft landing: for the horse and whoever's on it
		if (source.is(DamageTypeTags.IS_FALL)) {
			AbstractHorse horse = entity instanceof AbstractHorse h ? h : entity.getVehicle() instanceof AbstractHorse h ? h : null;
			if (horse != null && Equestrian.peek(horse) instanceof Equine e && e.breed() == Breed.BAT_HORSE
				&& horse.getControllingPassenger() instanceof Player rider) {
				int tier = tier(horse, rider);
				if (tier > 0 && horse.fallDistance < SOFT_LANDING[tier]) {
					return false;
				}
			}
			return true;
		}
		// the destrier's shield: it takes the blow that would have killed its rider
		if (entity instanceof ServerPlayer rider && amount >= rider.getHealth() + rider.getAbsorptionAmount() && mount(rider) instanceof AbstractHorse horse
			&& Equestrian.peek(horse) instanceof Equine e && e.breed() == Breed.DESTRIER && horse.isAlive()) {
			int tier = tier(horse, rider);
			long now = level.getGameTime();
			Long until = SHIELDED.get(rider.getUUID());
			if (tier == 0 || until != null && until > now) {
				return true;
			}
			SHIELDED.put(rider.getUUID(), now + SHIELD_COOLDOWN[tier]);
			horse.hurtServer(level, source, amount * SHIELD_SHARE);
			horse.standIfPossible();
			level.playSound(null, horse.blockPosition(), SoundEvents.HORSE_ANGRY, SoundSource.NEUTRAL, 1.4F, 0.8F);
			level.sendParticles(ParticleTypes.CRIT, rider.getX(), rider.getY() + 1.0, rider.getZ(), 16, 0.4, 0.5, 0.4, 0.2);
			rider.sendSystemMessage(Component.translatable("message.jaymoskingdoms.gift_shield", Bonding.name(horse)), true);
			return false;
		}
		return true;
	}

	// ---- the gifts that act by themselves ----------------------------------------------------------------------------------

	private static void tick(ServerLevel level) {
		long now = level.getGameTime();
		if (now % STEP != 0) {
			return;
		}
		for (ServerPlayer rider : level.players()) {
			AbstractHorse horse = mount(rider);
			if (horse == null || !(Equestrian.peek(horse) instanceof Equine e)) {
				continue;
			}
			int tier = tier(horse, rider);
			if (tier == 0) {
				continue;
			}
			switch (e.breed()) {
				case DRAFT -> herd(level, horse, rider, tier);
				case STEPPE_PONY -> snort(level, horse, rider, tier, now);
				case NIGHTMARE -> {
					rider.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, EFFECT_TICKS, 0, true, false, true));
					if (tier >= 2) {
						rider.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, EFFECT_TICKS, 0, true, false, true));
					}
				}
				case WAR_ELEPHANT -> calm(level, horse, tier);
				case WOOLLY_MAMMOTH -> {
					if (tier >= 2) {
						rider.setTicksFrozen(0);
						horse.setTicksFrozen(0);
					}
				}
				default -> {
				}
			}
		}
		if (now % 1200 == 0) {
			SHIELDED.values().removeIf(t -> t < now);
			SNORTED.values().removeIf(t -> t < now);
		}
	}

	/** The draft: farm animals about (not pets, not on a lead) come along after it. */
	private static void herd(ServerLevel level, AbstractHorse horse, ServerPlayer rider, int tier) {
		for (Animal animal : level.getEntitiesOfClass(Animal.class, horse.getBoundingBox().inflate(HERD_RANGE[tier]),
			a -> a.isAlive() && !(a instanceof AbstractHorse) && !(a instanceof TamableAnimal) && !a.isLeashed() && !a.isPassenger())) {
			if (animal.distanceTo(horse) > HERD_NEAR + horse.getBbWidth()) {
				animal.getNavigation().moveTo(horse, 1.15);
			}
		}
	}

	/** The steppe pony: a monster that comes too close gets a snort and a shove. */
	private static void snort(ServerLevel level, AbstractHorse horse, ServerPlayer rider, int tier, long now) {
		Long until = SNORTED.get(horse.getId());
		if (until != null && until > now) {
			return;
		}
		boolean snorted = false;
		for (LivingEntity monster : level.getEntitiesOfClass(LivingEntity.class, horse.getBoundingBox().inflate(SNORT_RANGE[tier]),
			t -> t.isAlive() && (t instanceof Enemy || t instanceof Mob m && m.getTarget() == rider))) {
			Vec3 away = monster.position().subtract(horse.position()).multiply(1.0, 0.0, 1.0);
			away = away.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, horse.getYRot()) : away.normalize();
			monster.knockback(1.0 + 0.3 * tier, -away.x, -away.z, level.damageSources().mobAttack(horse), 0.0F);
			snorted = true;
		}
		if (snorted) {
			SNORTED.put(horse.getId(), now + SNORT_TICKS[tier]);
			level.playSound(null, horse.blockPosition(), SoundEvents.HORSE_BREATHE, SoundSource.NEUTRAL, 1.6F, 0.6F);
			level.sendParticles(ParticleTypes.CLOUD, horse.getX(), horse.getEyeY(), horse.getZ(), 8, 0.3, 0.1, 0.3, 0.05);
		}
	}

	/** The war elephant: angry neutral mobs about it calm down. */
	private static void calm(ServerLevel level, AbstractHorse horse, int tier) {
		for (Mob mob : level.getEntitiesOfClass(Mob.class, horse.getBoundingBox().inflate(CALM_RANGE[tier]), m -> m instanceof NeutralMob && m.isAlive())) {
			NeutralMob neutral = (NeutralMob)mob;
			if (mob.getTarget() != null) {
				neutral.stopBeingAngry();
				mob.setTarget(null);
			}
		}
	}
}
