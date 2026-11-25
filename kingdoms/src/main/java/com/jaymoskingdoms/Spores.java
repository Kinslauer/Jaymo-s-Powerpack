package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The Circle of Spores (the Druid's third circle, beside Moon and Land): in a shape the Druid is wrapped in a haze of spores
 * that rots what stands in it; mushrooms burst from the bodies they pass (Kitchen's corpses, found through
 * {@link KitchenCompat}; without Kitchen, from what dies near them), and now and then the dead, stitched with fungus, rise to
 * fight for them for a little while.
 */
public final class Spores {
	public static final String KEY = "dru_spores";
	/** The haze: how far (blocks) it reaches, and how long (ticks) the rot and the sluggishness it gives last. */
	static final double HAZE_RANGE = 4.0;
	static final int ROT_TICKS = 60;
	/** Bodies: how near a Druid must pass (blocks), how many mushrooms burst from one, and the chance the dead rise. */
	static final double CORPSE_RANGE = 6.0;
	static final int MUSHROOMS = 3;
	static final float RISE_CHANCE = 0.3F;
	/** The risen: how many a Druid may have, how long (ticks) they stay, how far they see a foe (blocks). */
	static final int MAX_RISEN = 3, RISEN_TICKS = 20 * 20;
	static final double RISEN_SIGHT = 12.0;
	static final String RISEN_TAG = "jaymoskingdoms_spore_dead", SPORED_TAG = "jaymoskingdoms_spored";
	static final int FUNGUS = 0x9BB05A;

	private record Risen(Mob dead, UUID owner, long until) {
	}

	private static final Map<Integer, Risen> RISEN = new HashMap<>();

	private Spores() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Spores::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> RISEN.clear());
		// without Kitchen there is no corpse to burst: what dies near them does
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!KitchenCompat.corpsesExist() && !(entity instanceof Player) && entity.level() instanceof ServerLevel level && !RISEN.containsKey(entity.getId())) {
				for (ServerPlayer druid : level.players()) {
					if (spored(druid) && druid.distanceTo(entity) < CORPSE_RANGE) {
						burst(druid, level, entity.position().x, entity.position().y, entity.position().z);
						break;
					}
				}
			}
		});
		// the risen leave nothing behind, and don't wait round for a Druid who has gone
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity.entityTags().contains(RISEN_TAG)) {
				RISEN.remove(entity.getId());
				if (entity.level() instanceof ServerLevel level) {
					level.sendParticles(Fx.wisp(FUNGUS, 1.4F), entity.getX(), entity.getY(0.5), entity.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
				}
				entity.discard();
				return false;
			}
			return true;
		});
	}

	/** Whether {@code player} is a Druid of the Circle of Spores in a shape: where the circle's power flows. */
	static boolean spored(Player player) {
		return Classes.is(player, PlayerClass.DRUID) && WildShapes.has(player, KEY) && Druids.shaped(player);
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (!RISEN.isEmpty()) {
			rule(server, now);
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!spored(player) || (now + player.getId()) % 20 != 0 || !(player.level() instanceof ServerLevel level)) {
				continue;
			}
			haze(player, level);
			if (KitchenCompat.corpsesExist()) {
				for (Entity corpse : level.getEntities((Entity) null, player.getBoundingBox().inflate(CORPSE_RANGE), KitchenCompat::isFreeCorpse)) {
					if (!corpse.entityTags().contains(SPORED_TAG) && !KitchenCompat.isPlayerCorpse(corpse)) {
						corpse.addTag(SPORED_TAG);
						burst(player, level, corpse.getX(), corpse.getY(), corpse.getZ());
					}
				}
			}
		}
	}

	/** The haze: what stands in it rots (the wither) and drags. The Druid's own, the tamed and the risen are spared. */
	private static void haze(ServerPlayer druid, ServerLevel level) {
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, druid.getBoundingBox().inflate(HAZE_RANGE),
			x -> x.isAlive() && x != druid && !(x instanceof Player) && !Druids.standIn(x) && !x.entityTags().contains(RISEN_TAG)
				&& !(x instanceof OwnableEntity o && o.getOwnerReference() != null))) {
			e.addEffect(new MobEffectInstance(MobEffects.WITHER, ROT_TICKS, 0));
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ROT_TICKS, 0));
		}
		level.sendParticles(Fx.miasma(FUNGUS, 1.6F), druid.getX(), druid.getY(0.3), druid.getZ(), 16, HAZE_RANGE * 0.5, 0.3, HAZE_RANGE * 0.5, 0.0);
		level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, druid.getX(), druid.getY(0.5), druid.getZ(), 10, HAZE_RANGE * 0.5, 0.5, HAZE_RANGE * 0.5, 0.0);
	}

	/** Mushrooms burst from the body at ({@code x}, {@code y}, {@code z}); sometimes the dead get up. */
	private static void burst(ServerPlayer druid, ServerLevel level, double x, double y, double z) {
		BlockPos at = BlockPos.containing(x, y, z);
		for (int i = 0; i < MUSHROOMS; i++) {
			BlockPos p = at.offset(level.getRandom().nextInt(3) - 1, 0, level.getRandom().nextInt(3) - 1);
			BlockState mushroom = (level.getRandom().nextBoolean() ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).defaultBlockState();
			if (level.getBlockState(p).isAir() && mushroom.canSurvive(level, p)) {
				level.setBlock(p, mushroom, Block.UPDATE_ALL);
			} else {
				Block.popResource(level, p, new ItemStack(Items.RED_MUSHROOM));
			}
		}
		level.sendParticles(Fx.wisp(FUNGUS, 1.5F), x, y + 0.4, z, 14, 0.4, 0.3, 0.4, 0.03);
		level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, x, y + 0.4, z, 14, 0.5, 0.3, 0.5, 0.02);
		level.playSound(null, at, SoundEvents.FUNGUS_BREAK, SoundSource.PLAYERS, 1.0F, 0.8F);
		long mine = RISEN.values().stream().filter(r -> r.owner.equals(druid.getUUID())).count();
		if (mine < MAX_RISEN && level.getRandom().nextFloat() < RISE_CHANCE && EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT) instanceof net.minecraft.world.entity.monster.zombie.Zombie dead) {
			dead.setPos(x, y, z);
			dead.setBaby(false);
			dead.setPersistenceRequired();
			dead.setCanPickUpLoot(false);
			dead.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.RED_MUSHROOM_BLOCK));
			dead.addTag(RISEN_TAG);
			dead.addTag(BeastForms.TEMP_TAG);
			level.addFreshEntity(dead);
			RISEN.put(dead.getId(), new Risen(dead, druid.getUUID(), level.getGameTime() + RISEN_TICKS));
		}
	}

	/** The risen fight what their Druid fights (or what is near and hostile), never a player, and go back to the ground when their time is up. */
	private static void rule(MinecraftServer server, long now) {
		RISEN.values().removeIf(r -> {
			ServerPlayer owner = server.getPlayerList().getPlayer(r.owner);
			if (r.dead.isRemoved() || owner == null || !owner.isAlive() || now >= r.until || r.dead.level() != owner.level()) {
				if (!r.dead.isRemoved() && r.dead.level() instanceof ServerLevel level) {
					level.sendParticles(Fx.wisp(FUNGUS, 1.4F), r.dead.getX(), r.dead.getY(0.5), r.dead.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
					r.dead.discard();
				}
				return true;
			}
			LivingEntity foe = owner.getLastHurtMob() != null && owner.getLastHurtMob().isAlive() && owner.tickCount - owner.getLastHurtMobTimestamp() < 200
				? owner.getLastHurtMob() : owner.getLastHurtByMob();
			if (foe == null || !foe.isAlive() || foe == r.dead || foe.distanceTo(r.dead) > RISEN_SIGHT) {
				foe = r.dead.level().getEntitiesOfClass(LivingEntity.class, r.dead.getBoundingBox().inflate(RISEN_SIGHT),
					x -> x instanceof Enemy && x.isAlive() && !x.entityTags().contains(RISEN_TAG) && x != r.dead).stream().findFirst().orElse(null);
			}
			r.dead.setTarget(foe);
			return false;
		});
	}
}
