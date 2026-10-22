package com.jaymoskitchen.corpse;

import com.jaymoskitchen.JaymosKitchen;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.21) Andy: "creepers should not leave corpses when they explode, and other explosions should blast other corpses
 * apart into a mess." A body caught in a blast of at least {@link #SHRED_DAMAGE}, or a creature killed outright by one,
 * is blown apart: {@link #GIBS_MIN}-{@link #GIBS_MAX} chunks of it ({@link Gib}, pieces of its own model in its own skin)
 * fly out away from the blast, tumble, lie about and sink away, its blood bursts (Jaymo's Powers', see
 * {@link #addOnBurst}), and whatever it carried drops loose. A creeper never leaves a body to a blast: one that goes off
 * bursts into a few chunks of itself, and one killed by another's blast is blown apart whatever the blast's strength.
 */
public final class Gibs {
	/** A blast this strong (its damage) blows a body apart; a weaker one only flings it. */
	public static final float SHRED_DAMAGE = 12.0F;
	/** Chunks a man-sized body breaks into, at random between these; a bigger or smaller one more or fewer, at most {@link #GIBS_CAP}. */
	static final int GIBS_MIN = 5, GIBS_MAX = 8, GIBS_CAP = 14;
	/** A creeper that goes off leaves this many chunks of itself (at most): most of it went up in the blast. */
	static final int CREEPER_GIBS = 4;
	/** How hard the chunks fly (blocks a tick), away from the blast and up. */
	static final double GIB_SPEED = 0.45, GIB_HOP = 0.35;
	/** How long a chunk lies about (ticks; 20 to 45 seconds). */
	static final int LIFE_MIN = 400, LIFE_MAX = 900;

	public static EntityType<Gib> GIB;

	/** Told of each body blown apart (a corpse, or a creature dying), with the blast and its damage; see {@link #addOnBurst}. */
	private static final List<BiConsumer<Entity, Map.Entry<DamageSource, Float>>> ON_BURST = new ArrayList<>();

	private Gibs() {
	}

	public static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKitchen.id("gib"));
		GIB = Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.<Gib>of(Gib::new, MobCategory.MISC)
				.sized(0.3F, 0.2F)
				.clientTrackingRange(6)
				.updateInterval(2)
				.noSummon()
				.build(key));
	}

	/**
	 * Jaymo's Powers: told of every body blown apart, {@link Entity} being the corpse (Kitchen's body) or the creature
	 * itself as it dies, with the blast and its damage, so its blood bursts. Plain Java types, so it can be reached by
	 * reflection from a mod built against an older Kitchen.
	 */
	public static void addOnBurst(BiConsumer<Entity, Map.Entry<DamageSource, Float>> hook) {
		ON_BURST.add(hook);
	}

	/** Whether {@code mob}, just now dying from a blow of {@code damage}, was blown apart (and leaves no corpse). */
	public static boolean blownApart(LivingEntity mob, float damage) {
		DamageSource last = mob.getLastDamageSource();
		if (last == null || !last.is(DamageTypeTags.IS_EXPLOSION)) {
			return false;
		}
		return mob instanceof Creeper || damage >= SHRED_DAMAGE;
	}

	/** {@code mob} is blown apart as it dies: chunks of it fly, its loot (and gear) drops loose, and it leaves no corpse. */
	public static void blowApart(LivingEntity mob, float damage, List<ItemStack> loot) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}
		List<ItemStack> all = new ArrayList<>(loot);
		for (EquipmentSlot slot : EquipmentSlot.VALUES) {
			ItemStack worn = mob.getItemBySlot(slot);
			if (!worn.isEmpty()) {
				all.add(worn.copy());
				mob.setItemSlot(slot, ItemStack.EMPTY);
			}
		}
		DamageSource source = mob.getLastDamageSource();
		burst(level, mob, Corpses.look(mob), mob.getScale(), mob.getBbWidth(), count(level.getRandom(), mob.getBbWidth()),
			source == null ? null : source.getSourcePosition(), source, damage, all);
	}

	/** A creeper just went off: a few chunks of it are thrown out (it dropped nothing, as ever, and leaves no corpse). */
	public static void creeperWentOff(Creeper creeper) {
		if (!(creeper.level() instanceof ServerLevel level)) {
			return;
		}
		int count = 1 + level.getRandom().nextInt(CREEPER_GIBS);
		burst(level, creeper, Corpses.look(creeper), creeper.getScale(), creeper.getBbWidth(), count, null,
			creeper.damageSources().explosion(creeper, creeper), 0.0F, List.of());
	}

	/** A corpse caught in a blast of {@code damage} is blown apart; true if it was (the body is gone). */
	static boolean blowApartCorpse(CorpseEntity corpse, ServerLevel level, DamageSource source, float damage) {
		if (damage < SHRED_DAMAGE) {
			return false;
		}
		EntityType<?> type = corpse.sourceType();
		double width = type == null ? corpse.getBbWidth() : type.getWidth() * corpse.bodyScale();
		burst(level, corpse, corpse.look(), corpse.bodyScale(), width, count(level.getRandom(), width), source.getSourcePosition(),
			source, damage, corpse.takeLoot());
		corpse.discard();
		return true;
	}

	/** How many chunks a body this wide breaks into. */
	private static int count(RandomSource random, double width) {
		int count = GIBS_MIN + random.nextInt(GIBS_MAX - GIBS_MIN + 1);
		return Mth.clamp(Math.round(count * (float) Mth.clamp(width / 0.6, 0.6, 2.0)), 2, GIBS_CAP);
	}

	/** Throws {@code count} chunks of {@code body} out from it, away from {@code from} (the blast) if known, and bursts its blood. */
	private static void burst(ServerLevel level, Entity body, String look, float size, double width, int count, @Nullable Vec3 from,
		@Nullable DamageSource source, float damage, List<ItemStack> loot) {
		RandomSource random = level.getRandom();
		Vec3 at = body.position();
		Vec3 away = from == null ? Vec3.ZERO : at.subtract(from).multiply(1.0, 0.0, 1.0);
		away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize();
		double reach = width * 0.35;
		for (int i = 0; i < count; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double cos = Math.cos(angle), sin = Math.sin(angle);
			Gib gib = new Gib(GIB, level);
			gib.snapTo(at.x + cos * reach, at.y + body.getBbHeight() * 0.4, at.z + sin * reach, random.nextFloat() * 360.0F, 0.0F);
			// mostly away from the blast, every chunk its own way
			double speed = GIB_SPEED * (0.4 + random.nextDouble() * 0.6);
			double x = away.x * 0.7 + cos * 0.6, z = away.z * 0.7 + sin * 0.6;
			gib.setDeltaMovement(x * speed, GIB_HOP * (0.6 + random.nextDouble() * 0.6), z * speed);
			gib.setUp(look, size, LIFE_MIN + random.nextInt(LIFE_MAX - LIFE_MIN + 1));
			level.addFreshEntity(gib);
		}
		for (ItemStack stack : loot) {
			if (!stack.isEmpty()) {
				ItemEntity item = new ItemEntity(level, at.x, at.y + 0.3, at.z, stack);
				item.setDefaultPickUpDelay();
				level.addFreshEntity(item);
			}
		}
		if (ON_BURST.isEmpty() || source == null) {
			// no Jaymo's Powers to bleed it: a spray of red
			BlockParticleOption blood = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());
			level.sendParticles(blood, at.x, at.y + 0.4, at.z, 40, width * 0.4, 0.3, width * 0.4, 0.3);
		} else {
			Map.Entry<DamageSource, Float> blast = Map.entry(source, damage);
			for (var hook : ON_BURST) {
				try {
					hook.accept(body, blast);
				} catch (RuntimeException e) {
					JaymosKitchen.LOGGER.warn("A blown-apart hook failed: {}", e.toString());
				}
			}
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.0F, 0.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.HONEY_BLOCK_BREAK, SoundSource.HOSTILE, 0.9F, 0.6F);
	}
}
