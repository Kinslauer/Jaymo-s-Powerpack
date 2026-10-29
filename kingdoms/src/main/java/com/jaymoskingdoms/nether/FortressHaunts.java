package com.jaymoskingdoms.nether;

import com.jaymoskingdoms.Armory;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.McaCompat;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (Kingdoms 0.7.351) The fortress haunts: villagers who went into the Nether's fortresses and never came out, their dead
 * bodies possessed and doomed to walk the halls. Andy: "kintsugi should not be craftable, instead found on mca zombies
 * in the nether that were possessed and doomed to walk the fortress halls."
 * <ul>
 * <li>While a player is in a fortress, now and then a haunt rises out of the bricks somewhere in its halls, out of
 * arm's reach ({@link #SITE_MIN}..{@link #SITE_MAX}), up to {@link #MOST} near them. An MCA zombie villager (a plain
 * zombie villager without MCA), marked {@link #POSSESSED}.</li>
 * <li>Each wears {@link #WORN_MIN}..{@link #WORN_MAX} pieces of the {@link Armory#KINTSUGI_PIECES Kintsugi Armour}, the
 * only place it is found, and drops what it wears when it falls.</li>
 * <li>Possessed, they shrug off fire and lava, give off souls, are tougher than a common zombie, and can't be cured: a
 * golden apple only sets the soul inside them screaming ({@link #use}).</li>
 * </ul>
 */
public final class FortressHaunts {
	// ---- tuning ----
	/** How often each player in a fortress may see a haunt rise (ticks), and the chance it does. */
	static final int RISE_EVERY = 20 * 30;
	static final float RISE_CHANCE = 0.35F;
	/** No more rise while this many haunts are within {@link #CROWD_RANGE} of the player. */
	static final int MOST = 3;
	static final double CROWD_RANGE = 48.0;
	/** A haunt rises this far from the player (blocks, flat), within this of their height. */
	static final int SITE_MIN = 12, SITE_MAX = 28, SITE_RISE = 4, SITE_TRIES = 12;
	/** Kintsugi pieces a haunt wears. */
	static final int WORN_MIN = 1, WORN_MAX = 2;
	/** A haunt's health (a zombie's is 20). */
	static final double HEALTH = 34.0;
	/** Souls drift off a haunt this often (ticks). */
	static final int SOULS_EVERY = 12;

	/** On every haunt (saved). */
	public static final AttachmentType<Boolean> POSSESSED = AttachmentRegistry.create(JaymosKingdoms.id("fortress_haunt"),
		builder -> builder.persistent(Codec.BOOL));

	/** Haunts loaded now. */
	private static final Set<Mob> LOADED = new HashSet<>();

	private FortressHaunts() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && haunt(mob)) {
				LOADED.add(mob);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> LOADED.remove(entity));
		ServerTickEvents.END_SERVER_TICK.register(FortressHaunts::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LOADED.clear());
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> entity instanceof Mob mob ? use(mob, player.getItemInHand(hand)) : InteractionResult.PASS);
	}

	/** One of the fortress haunts. */
	public static boolean haunt(Mob mob) {
		return mob.hasAttached(POSSESSED);
	}

	/** {@code pos} is in a piece of a Nether fortress. */
	static boolean inFortress(ServerLevel level, BlockPos pos) {
		return level.structureManager().getStructureWithPieceAt(pos, holder -> holder.is(BuiltinStructures.FORTRESS)).isValid();
	}

	/** No cure for the possessed: the golden apple is refused, and the soul in them screams. */
	private static InteractionResult use(Mob mob, ItemStack held) {
		if (!haunt(mob) || !held.is(Items.GOLDEN_APPLE)) {
			return InteractionResult.PASS;
		}
		if (mob.level() instanceof ServerLevel level) {
			level.playSound(null, mob.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.5F, 0.6F);
			level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getEyeY(), mob.getZ(), 12, 0.3, 0.3, 0.3, 0.04);
		}
		return InteractionResult.FAIL;
	}

	private static void tick(MinecraftServer server) {
		ServerLevel nether = server.getLevel(Level.NETHER);
		if (nether == null) {
			return;
		}
		long now = nether.getGameTime();
		if (now % SOULS_EVERY == 0) {
			for (Mob mob : List.copyOf(LOADED)) {
				if (mob.isAlive() && mob.level() == nether) {
					nether.sendParticles(nether.getRandom().nextBoolean() ? ParticleTypes.SOUL : ParticleTypes.SOUL_FIRE_FLAME,
						mob.getX(), mob.getY() + mob.getBbHeight() * 0.6, mob.getZ(), 1, 0.25, 0.4, 0.25, 0.01);
				}
			}
		}
		if (now % RISE_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : nether.players()) {
			if (player.isSpectator() || nether.getRandom().nextFloat() >= RISE_CHANCE || !inFortress(nether, player.blockPosition())) {
				continue;
			}
			AABB around = player.getBoundingBox().inflate(CROWD_RANGE);
			if (nether.getEntitiesOfClass(Mob.class, around, m -> m.isAlive() && haunt(m)).size() < MOST) {
				BlockPos at = site(nether, player.blockPosition());
				if (at != null) {
					rise(nether, at);
				}
			}
		}
	}

	/** A spot in the same fortress's halls near {@code around}: brick underfoot, room to stand, out of reach. */
	private static @Nullable BlockPos site(ServerLevel level, BlockPos around) {
		RandomSource random = level.getRandom();
		for (int i = 0; i < SITE_TRIES; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int dist = SITE_MIN + random.nextInt(SITE_MAX - SITE_MIN + 1);
			BlockPos column = around.offset((int) Math.round(Math.cos(angle) * dist), 0, (int) Math.round(Math.sin(angle) * dist));
			for (int dy = SITE_RISE; dy >= -SITE_RISE; dy--) {
				BlockPos pos = column.above(dy);
				if (level.getBlockState(pos.below()).is(net.minecraft.world.level.block.Blocks.NETHER_BRICKS)
					&& level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty()
					&& level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
					&& inFortress(level, pos)) {
					return pos;
				}
			}
		}
		return null;
	}

	/** A haunt rises at {@code at}, in its Kintsugi. */
	static @Nullable Mob rise(ServerLevel level, BlockPos at) {
		Vec3 feet = Vec3.atBottomCenterOf(at);
		ZombieVillager haunt = McaCompat.LOADED ? McaCompat.spawnZombie(level, feet) : null;
		if (haunt == null) {
			haunt = EntityTypes.ZOMBIE_VILLAGER.create(level, EntitySpawnReason.EVENT);
			if (haunt == null) {
				return null;
			}
			haunt.snapTo(feet.x, feet.y, feet.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
			haunt.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
			level.addFreshEntity(haunt);
		}
		haunt.setBaby(false);
		if (haunt.getVehicle() != null) {
			haunt.stopRiding();
		}
		haunt.setAttached(POSSESSED, true);
		LOADED.add(haunt);
		var health = haunt.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(HEALTH);
			haunt.setHealth((float) HEALTH);
		}
		haunt.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
		wear(haunt, level.getRandom());
		level.sendParticles(ParticleTypes.SOUL, feet.x, feet.y + 0.2, feet.z, 20, 0.4, 0.1, 0.4, 0.03);
		level.playSound(null, at, SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.0F, 0.5F);
		return haunt;
	}

	/** Dresses {@code haunt} in a few Kintsugi pieces it always drops (and nothing else on the slots it wears). */
	private static void wear(Mob haunt, RandomSource random) {
		List<Item> pieces = new ArrayList<>(Armory.KINTSUGI_PIECES.values());
		int count = WORN_MIN + random.nextInt(WORN_MAX - WORN_MIN + 1);
		for (int i = 0; i < count && !pieces.isEmpty(); i++) {
			Item piece = pieces.remove(random.nextInt(pieces.size()));
			ItemStack stack = new ItemStack(piece);
			EquipmentSlot slot = haunt.getEquipmentSlotForItem(stack);
			haunt.setItemSlot(slot, stack);
			haunt.setGuaranteedDrop(slot);
		}
	}
}
