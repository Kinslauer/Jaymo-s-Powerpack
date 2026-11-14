package com.jaymoskingdoms.reaped;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.KitchenCompat;
import com.jaymoskingdoms.Reaper;
import com.jaymoskingdoms.ZombieHordes;
import com.jaymoskingdoms.mixin.MobAccessor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The reaped (Andy, 2026-11-14: "i like undead illagers, waging war for the reaper"): dead illagers the Reaper
 * ({@link Reaper}) took and sent back to fight for it. The idea of undead pillagers, vindicators and evokers is Immersive
 * Pillagers' (GPL-3.0); the code, the looks (kingdoms/tools/gen_reaped.py) and what they do here are our own.
 * <p>
 * Three of them, each the living illager it was with the life gone out of it ({@link ReapedPillager},
 * {@link ReapedVindicator}, {@link ReapedEvoker}): undead (smite, healing and harm turned round, the Shroud), a little
 * tougher and slower, and no illager's friend. They make war on everything living, and first on their own kin:
 * <ul>
 * <li>They go for living illagers on sight, and living illagers for them.</li>
 * <li><b>They reap.</b> A living illager one of them kills gets up {@link #RISE_TICKS} later as the same kind of reaped
 * (its corpse, if Jaymo's Kitchen laid one, taken up into it) and falls in with the host that killed it.</li>
 * <li>They leave alone whoever carries the Reaper, unless that keeper struck them; and every kill one of them makes
 * within {@link #TOLL_RANGE} blocks of a keeper pays the keeper's Reaper {@link #TOLL} of its Toll.</li>
 * </ul>
 * They come as a host: a kind of horde ({@link ZombieHordes}), which marches on the nearest settlement or camp.
 */
public interface Reaped {
	/** Health and speed: the pillager, the vindicator, the evoker. */
	double PILLAGER_HEALTH = 30.0, PILLAGER_SPEED = 0.3, VINDICATOR_HEALTH = 32.0, VINDICATOR_SPEED = 0.31, EVOKER_HEALTH = 30.0, EVOKER_SPEED = 0.42;
	/** How low their voices are. */
	float VOICE_PITCH = 0.55F;
	/** A living illager they kill gets up this long after (ticks). */
	int RISE_TICKS = 60;
	/** A kill within this many blocks of a Reaper's keeper pays it this much Toll. */
	double TOLL_RANGE = 24.0;
	int TOLL = 1;

	/** Set in {@link #register()}. */
	EntityType<?>[] TYPES = new EntityType<?>[3];
	Item[] EGGS = new Item[3];
	/** The living illagers they've killed, waiting to get up. */
	List<Rising> RISING = new ArrayList<>();

	record Rising(ServerLevel level, Vec3 at, float yaw, EntityType<? extends Mob> as, Mob killer, long when) {
	}

	@SuppressWarnings("unchecked")
	static EntityType<ReapedPillager> pillager() {
		return (EntityType<ReapedPillager>) TYPES[0];
	}

	@SuppressWarnings("unchecked")
	static EntityType<ReapedVindicator> vindicator() {
		return (EntityType<ReapedVindicator>) TYPES[1];
	}

	@SuppressWarnings("unchecked")
	static EntityType<ReapedEvoker> evoker() {
		return (EntityType<ReapedEvoker>) TYPES[2];
	}

	static void register() {
		TYPES[0] = type("reaped_pillager", EntityType.Builder.<ReapedPillager>of(ReapedPillager::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(8));
		TYPES[1] = type("reaped_vindicator", EntityType.Builder.<ReapedVindicator>of(ReapedVindicator::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(8));
		TYPES[2] = type("reaped_evoker", EntityType.Builder.<ReapedEvoker>of(ReapedEvoker::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(8));
		FabricDefaultAttributeRegistry.register(pillager(), Pillager.createAttributes().add(Attributes.MAX_HEALTH, PILLAGER_HEALTH).add(Attributes.MOVEMENT_SPEED, PILLAGER_SPEED));
		FabricDefaultAttributeRegistry.register(vindicator(), Vindicator.createAttributes().add(Attributes.MAX_HEALTH, VINDICATOR_HEALTH).add(Attributes.MOVEMENT_SPEED, VINDICATOR_SPEED));
		FabricDefaultAttributeRegistry.register(evoker(), Evoker.createAttributes().add(Attributes.MAX_HEALTH, EVOKER_HEALTH).add(Attributes.MOVEMENT_SPEED, EVOKER_SPEED));
		String[] names = {"reaped_pillager", "reaped_vindicator", "reaped_evoker"};
		for (int i = 0; i < names.length; i++) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(names[i] + "_spawn_egg"));
			EGGS[i] = Registry.register(BuiltInRegistries.ITEM, key, new SpawnEggItem(new Item.Properties().spawnEgg(TYPES[i]).setId(key)));
		}
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> {
			output.insertAfter(Items.PILLAGER_SPAWN_EGG, EGGS[0]);
			output.insertAfter(Items.VINDICATOR_SPAWN_EGG, EGGS[1]);
			output.insertAfter(Items.EVOKER_SPAWN_EGG, EGGS[2]);
		});
		// the living fight back (an illager shrugs off a blow from anything it takes for a raider, so they need telling)
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractIllager illager && !(entity instanceof Reaped)) {
				((MobAccessor) illager).jaymoskingdoms$targets().addGoal(2,
					new NearestAttackableTargetGoal<>(illager, AbstractIllager.class, 10, true, false, (target, l) -> target instanceof Reaped));
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(Reaped::died);
		ServerTickEvents.END_SERVER_TICK.register(Reaped::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> RISING.clear());
	}

	private static <T extends Entity> EntityType<T> type(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	/** The reaped's own side: each other, and nobody else (never the living illagers a dead illager's class would take for friends). */
	static boolean ally(Entity self, Entity other) {
		return other instanceof Reaped || self.getTeam() != null && self.isAlliedTo(other.getTeam());
	}

	/** They leave the Reaper's keepers be, unless one struck them. */
	static boolean spares(Mob self, LivingEntity target) {
		return target instanceof Player && Reaper.has(target) && self.getLastHurtByMob() != target;
	}

	/** The living illagers they hunt. */
	static boolean prey(LivingEntity target) {
		return target instanceof AbstractIllager && !(target instanceof Reaped);
	}

	/** What a dead {@code illager} gets up as, or null (a ravager stays down). */
	static @Nullable EntityType<? extends Mob> risesAs(LivingEntity illager) {
		if (illager instanceof Reaped || illager.is(net.minecraft.tags.EntityTypeTags.UNDEAD)) {
			return null;
		}
		return illager instanceof Pillager ? pillager() : illager instanceof Vindicator ? vindicator() : illager instanceof SpellcasterIllager ? evoker() : null;
	}

	private static void died(LivingEntity victim, DamageSource source) {
		if (!(source.getEntity() instanceof Mob killer) || !(killer instanceof Reaped) || !(victim.level() instanceof ServerLevel level)) {
			return;
		}
		// the Reaper is paid for what its host kills near its keeper
		for (ServerPlayer player : level.players()) {
			if (Reaper.has(player) && player.distanceToSqr(victim) <= TOLL_RANGE * TOLL_RANGE) {
				Reaper.feed(player, TOLL);
			}
		}
		EntityType<? extends Mob> as = risesAs(victim);
		if (as != null) {
			RISING.add(new Rising(level, victim.position(), victim.getYRot(), as, killer, level.getGameTime() + RISE_TICKS));
		}
	}

	private static void tick(MinecraftServer server) {
		if (RISING.isEmpty()) {
			return;
		}
		for (Iterator<Rising> it = RISING.iterator(); it.hasNext();) {
			Rising r = it.next();
			ServerLevel level = r.level();
			long left = r.when() - level.getGameTime();
			if (left > 0) {
				if (left % 10 == 0) {
					level.sendParticles(Fx.miasma(Reaper.SMOKE, 1.2F), r.at().x, r.at().y + 0.3, r.at().z, 6, 0.3, 0.15, 0.3, 0.01);
				}
				continue;
			}
			it.remove();
			BlockPos pos = BlockPos.containing(r.at());
			if (!level.isPositionEntityTicking(pos)) {
				continue;
			}
			Mob mob = r.as().create(level, EntitySpawnReason.CONVERSION);
			if (mob == null) {
				continue;
			}
			mob.snapTo(r.at().x, r.at().y, r.at().z, r.yaw(), 0.0F);
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.CONVERSION, null);
			// the body it left gets up with it
			Entity corpse = KitchenCompat.freeCorpseNear(level, r.at(), 2.5);
			if (corpse != null) {
				corpse.discard();
			}
			level.addFreshEntity(mob);
			if (r.killer().isAlive()) {
				ZombieHordes.enlist(r.killer(), mob);
			}
			Fx.burst(level, r.at().x, r.at().y + 1.0, r.at().z, Reaper.RED, 1.0F, 10, 0.3, 0.5, 0.3);
			level.playSound(null, r.at().x, r.at().y, r.at().z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.0F, 0.6F);
		}
	}
}
