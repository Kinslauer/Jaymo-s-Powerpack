package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * GT New Horizons picks, patch 6 (Special Mobs' idea, none of its code). Andy: "the MCA can remember their past lives, and get
 * these abilities, not the long dead though." An MCA villager who turns (MCA's zombie villager keeps their name, face and
 * trade) still knows the work of their hands, and uses it on the living (Lore.md: the MCA zombies are the recently dead):
 * <ul>
 * <li><b>Fishers</b> cast their old rod at you and reel you in toward them.</li>
 * <li><b>Hunters, guards and fletchers</b> (and MCA's archers) loose arrows from their old bow, from a quiver that runs out
 * ({@link MobAmmo}: no free shots; spent arrows lie where they land, and they pick them up again). Out of arrows, they come
 * at you with their hands like any of the dead.</li>
 * <li><b>Farmers</b> throw their hoe at you, once ({@link CaptainWays#throwBlade}: it lands as the hoe it was).</li>
 * </ul>
 * Only those who turned from an MCA villager: plain zombies, the old dead that rise out of the ground, and every skeleton get
 * nothing new. Without MCA nothing here runs.
 */
public final class TurnedVillagers {
	// ================================================================================================================
	// Tuning
	/** A fisher's cast: from this range to this (blocks), at most this often (ticks, plus up to half again); the reel's pull. */
	static final double ROD_MIN = 3.0, ROD_MAX = 10.0, ROD_PULL = 0.8, ROD_LIFT = 0.3;
	static final int ROD_EVERY = 100, ROD_REEL = 12;
	/** A bowman shoots from this range to this (blocks), drawing this long (ticks), at most this often (ticks). */
	static final double BOW_MIN = 5.0, BOW_MAX = 16.0;
	static final int BOW_DRAW = 20, BOW_EVERY = 50;
	static final float BOW_POWER = 1.0F, BOW_SPREAD = 10.0F;
	/** A farmer throws their hoe from this range to this (blocks), this hard. */
	static final double HOE_MIN = 4.0, HOE_MAX = 10.0;
	static final float HOE_DAMAGE = 4.0F;
	// ================================================================================================================

	/** What their hands remember. */
	enum Trade {
		ROD, BOW, HOE, HOE_THROWN;

		String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		static @Nullable Trade parse(String key) {
			for (Trade t : values()) {
				if (t.key().equals(key)) {
					return t;
				}
			}
			return null;
		}
	}

	/** On a turned MCA villager: their old trade ({@link Trade#key}). Saved. */
	public static final AttachmentType<String> TRADE = AttachmentRegistry.create(JaymosKingdoms.id("turned_trade"),
		builder -> builder.persistent(Codec.STRING));

	/** The turned given their trade's goal (goals aren't saved; a reload gives them again). */
	private static final Set<ZombieVillager> GIVEN = Collections.newSetFromMap(new WeakHashMap<>());

	private TurnedVillagers() {
	}

	static void register() {
		if (!McaCompat.LOADED) {
			return;
		}
		ServerLivingEntityEvents.MOB_CONVERSION.register((previous, converted, params) -> {
			if (previous instanceof Villager villager && McaCompat.isMca(villager) && converted instanceof ZombieVillager zombie) {
				Trade trade = trade(villager.getVillagerData().profession().unwrapKey().map(k -> k.identifier().getPath()).orElse(""));
				if (trade != null) {
					zombie.setAttached(TRADE, trade.key());
					equip(zombie, trade);
					arm(zombie);
				}
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof ZombieVillager zombie && zombie.hasAttached(TRADE)) {
				arm(zombie);
			}
		});
	}

	/** {@code e} is a turned villager who remembers their trade. */
	static boolean turned(Entity e) {
		return e instanceof ZombieVillager && e.hasAttached(TRADE);
	}

	private static @Nullable Trade trade(String job) {
		if (job.equals("fisherman") || job.endsWith("_whaler")) {
			return Trade.ROD;
		}
		if (job.equals("fletcher") || job.equals("guard") || job.equals("archer") || job.contains("hunter")) {
			return Trade.BOW;
		}
		if (job.equals("farmer") || job.endsWith("_farmer")) {
			return Trade.HOE;
		}
		return null;
	}

	/** What they were carrying when they turned, back in their hand if it isn't already. */
	private static void equip(ZombieVillager zombie, Trade trade) {
		ItemStack hand = zombie.getMainHandItem();
		ItemStack tool = switch (trade) {
			case ROD -> hand.is(Items.FISHING_ROD) ? ItemStack.EMPTY : new ItemStack(Items.FISHING_ROD);
			case BOW -> hand.is(Items.BOW) ? ItemStack.EMPTY : new ItemStack(Items.BOW);
			case HOE -> hand.is(net.minecraft.tags.ItemTags.HOES) ? ItemStack.EMPTY : new ItemStack(Items.STONE_HOE);
			case HOE_THROWN -> ItemStack.EMPTY;
		};
		if (!tool.isEmpty()) {
			// what they held instead goes to the other hand (or falls, if that's full)
			if (!hand.isEmpty() && zombie.getOffhandItem().isEmpty()) {
				zombie.setItemSlot(EquipmentSlot.OFFHAND, hand);
			} else if (!hand.isEmpty() && zombie.level() instanceof ServerLevel level) {
				zombie.spawnAtLocation(level, hand);
			}
			zombie.setItemSlot(EquipmentSlot.MAINHAND, tool);
		}
	}

	private static void arm(ZombieVillager zombie) {
		if (GIVEN.add(zombie)) {
			((com.jaymoskingdoms.mixin.MobAccessor) zombie).jaymoskingdoms$goals().addGoal(1, new TradeGoal(zombie));
		}
	}

	private static @Nullable Trade of(ZombieVillager zombie) {
		String key = zombie.getAttached(TRADE);
		return key == null ? null : Trade.parse(key);
	}

	/** Their trade's trick, when their foe is at the right range: holds them still while it lasts. */
	private static final class TradeGoal extends Goal {
		private final ZombieVillager zombie;
		private int ticks;
		private long readyAt;
		private @Nullable LivingEntity target;

		TradeGoal(ZombieVillager zombie) {
			this.zombie = zombie;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		private long now() {
			return this.zombie.level().getGameTime();
		}

		@Override
		public boolean canUse() {
			LivingEntity target = this.zombie.getTarget();
			Trade trade = of(this.zombie);
			if (trade == null || trade == Trade.HOE_THROWN || target == null || !target.isAlive() || this.now() < this.readyAt
				|| !this.zombie.hasLineOfSight(target)) {
				return false;
			}
			double d = this.zombie.distanceTo(target);
			boolean ok = switch (trade) {
				case ROD -> d >= ROD_MIN && d <= ROD_MAX && this.zombie.getMainHandItem().is(Items.FISHING_ROD);
				case BOW -> d >= BOW_MIN && d <= BOW_MAX && this.zombie.getMainHandItem().is(Items.BOW) && MobAmmo.has(this.zombie, MobAmmo.Kind.ARROWS);
				case HOE -> d >= HOE_MIN && d <= HOE_MAX && this.zombie.getMainHandItem().is(net.minecraft.tags.ItemTags.HOES);
				case HOE_THROWN -> false;
			};
			if (ok) {
				this.target = target;
			}
			return ok;
		}

		@Override
		public boolean canContinueToUse() {
			Trade trade = of(this.zombie);
			LivingEntity target = this.target;
			int lasts = trade == Trade.BOW ? BOW_DRAW : trade == Trade.ROD ? ROD_REEL : 1;
			return target != null && target.isAlive() && this.ticks <= lasts;
		}

		@Override
		public void start() {
			this.ticks = 0;
			this.zombie.getNavigation().stop();
			if (of(this.zombie) == Trade.ROD && this.zombie.level() instanceof ServerLevel level && this.target != null) {
				line(level, this.target);
				level.playSound(null, this.zombie.getX(), this.zombie.getY(), this.zombie.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.HOSTILE, 0.6F, 0.6F);
			}
		}

		@Override
		public void stop() {
			this.target = null;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = this.target;
			Trade trade = of(this.zombie);
			if (target == null || trade == null || !(this.zombie.level() instanceof ServerLevel level)) {
				return;
			}
			this.zombie.getLookControl().setLookAt(target, 30.0F, 30.0F);
			this.ticks++;
			switch (trade) {
				case ROD -> {
					if (this.ticks == ROD_REEL) {
						this.reel(level, target);
						this.readyAt = this.now() + ROD_EVERY + this.zombie.getRandom().nextInt(ROD_EVERY / 2);
					}
				}
				case BOW -> {
					if (this.ticks == BOW_DRAW) {
						this.shoot(level, target);
						this.readyAt = this.now() + BOW_EVERY;
					}
				}
				case HOE -> {
					// once: the hoe leaves the hand and lies where it lands
					ItemStack hoe = this.zombie.getMainHandItem().copyWithCount(1);
					CaptainWays.throwBlade(level, this.zombie, target, hoe, HOE_DAMAGE, 0.0F, false);
					MobAmmo.lost(this.zombie, hoe.getItem());
					this.zombie.setAttached(TRADE, Trade.HOE_THROWN.key());
					level.playSound(null, this.zombie.getX(), this.zombie.getY(), this.zombie.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.HOSTILE, 0.7F, 0.7F);
				}
				case HOE_THROWN -> {
				}
			}
		}

		/** The fishing line, from the rod's tip to the one it's hooked in. */
		private void line(ServerLevel level, LivingEntity target) {
			Vec3 from = this.zombie.getEyePosition(), to = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
			ItemParticleOption silk = new ItemParticleOption(ParticleTypes.ITEM, Items.STRING);
			int steps = Math.max(2, (int) (from.distanceTo(to) * 2.0));
			for (int i = 0; i <= steps; i++) {
				Vec3 p = from.lerp(to, i / (double) steps);
				level.sendParticles(silk, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}

		/** Reeled in: pulled toward them, off the ground a little, as a rod pulls whatever it hooks. */
		private void reel(ServerLevel level, LivingEntity target) {
			if (this.zombie.distanceTo(target) > ROD_MAX + 2.0 || !this.zombie.hasLineOfSight(target)) {
				return;
			}
			this.line(level, target);
			Vec3 toward = this.zombie.position().subtract(target.position()).multiply(1.0, 0.0, 1.0).normalize().scale(ROD_PULL);
			target.setDeltaMovement(target.getDeltaMovement().add(toward.x, ROD_LIFT, toward.z));
			target.needsSync = true;
			level.playSound(null, this.zombie.getX(), this.zombie.getY(), this.zombie.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.HOSTILE, 0.8F, 0.7F);
			this.zombie.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		}

		/** One arrow from the quiver, from their old bow; it can be picked up where it lands. */
		private void shoot(ServerLevel level, LivingEntity target) {
			if (!this.zombie.hasLineOfSight(target) || !MobAmmo.spend(this.zombie, MobAmmo.Kind.ARROWS)) {
				return;
			}
			ItemStack bow = this.zombie.getMainHandItem();
			ItemStack shot = new ItemStack(Items.ARROW);
			AbstractArrow arrow = ProjectileUtil.getMobArrow(this.zombie, shot, BOW_POWER, bow);
			MobAmmo.canBePickedUp(arrow);
			double dx = target.getX() - this.zombie.getX(), dz = target.getZ() - this.zombie.getZ();
			double dy = target.getY(0.3333) - arrow.getY(), flat = Math.sqrt(dx * dx + dz * dz);
			Projectile.spawnProjectileUsingShoot(arrow, level, shot, dx, dy + flat * 0.2, dz, 1.6F, BOW_SPREAD);
			level.playSound(null, this.zombie.getX(), this.zombie.getY(), this.zombie.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.HOSTILE, 1.0F,
				1.0F / (this.zombie.getRandom().nextFloat() * 0.4F + 0.8F));
		}
	}
}
