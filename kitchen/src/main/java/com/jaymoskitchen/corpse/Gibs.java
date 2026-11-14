package com.jaymoskitchen.corpse;

import com.jaymoskitchen.JaymosKitchen;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import com.jaymoskitchen.KitchenBlood;
import com.jaymoskitchen.butchery.Butchery;
import com.jaymoskitchen.butchery.OffalPileBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.21) Andy: "creepers should not leave corpses when they explode, and other explosions should blast other corpses
 * apart into a mess." A body caught in a blast of at least {@link #SHRED_DAMAGE}, or a creature killed outright by one,
 * is blown apart: {@link #GIBS_MIN}-{@link #GIBS_MAX} chunks of it ({@link Gib}, pieces of its own model in its own skin)
 * fly out away from the blast, tumble, lie about and sink away, its blood bursts (Jaymo's Powers', see
 * {@link #addOnBurst}), and whatever it carried drops loose. A creeper never leaves a body to a blast: one that goes off
 * bursts into a few chunks of itself, and one killed by another's blast is blown apart whatever the blast's strength.
 * <p>
 * Andy: "the tiger ate me and left nothing, not even tatters, so explosions should gib with the limbs like they do,
 * but drop the offal piles as entrails and any other gore pieces". With the chunks goes what was inside the body
 * ({@link Insides}): organs, ropes of gut, a slab of ribs, a length of spine and shards of bone ({@link Gib#piece}),
 * scraps of what it wore, and its entrails landing as gut piles ({@link OffalPileBlock}), which scavengers smell
 * and rot away as a butcher's do. A body eaten where it lay ({@link Scavengers}) leaves the same picked over
 * ({@link #eaten}): ribs, spine, bone, a gut pile and the tatters of its clothes, lying for
 * {@link #REMAINS_LIFE} ticks. Bones have no soft parts, machines, creepers and things with no blood have none of it,
 * and neither have spiders or anything narrower than {@link #GORE_MIN_WIDTH}.
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

	/** Insides thrown from a man-sized body blown apart (a bigger or smaller one more or fewer). */
	static final int ORGANS_MIN = 2, ORGANS_MAX = 3, GUT_ROPES = 2, SHARDS_MIN = 2, SHARDS_MAX = 4, TATTERS_MIN = 1, TATTERS_MAX = 3;
	/** Pieces of entrails a man-sized body's blast leaves in gut piles, and the most any body leaves. */
	static final int ENTRAILS = 2, ENTRAILS_CAP = 6;
	/** How far from the body its entrails land (blocks), and how far down they fall looking for ground. */
	static final double ENTRAILS_REACH = 1.6;
	static final int ENTRAILS_DROP = 8;
	/** A body narrower than this (blocks) has no insides worth throwing. */
	static final double GORE_MIN_WIDTH = 0.35;
	/** What a predator leaves of a body lies this long (ticks; 5 minutes, a player's 10), and tumbles out this gently. */
	static final int REMAINS_LIFE = 6000, PLAYER_REMAINS_LIFE = 12000;
	static final double REMAINS_SPEED = 0.3;
	/** Creatures with blood but nothing inside to throw: spiders, slimes and the like (data/jaymoskitchen/tags/entity_type/no_innards.json). */
	public static final TagKey<EntityType<?>> NO_INNARDS = TagKey.create(Registries.ENTITY_TYPE, JaymosKitchen.id("no_innards"));

	/** (0.5.67) Tags Jaymo's Powers puts on a body dying of a blast: blown apart, or left whole (by how close it was). */
	public static final String GIB_TAG = "jaymoskitchen.gib", NO_GIB_TAG = "jaymoskitchen.no_gib";

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
		if (mob instanceof Creeper) {
			return true;
		}
		// (0.5.67) Jaymo's Powers rolls it by how close the blast was (its BlastGore): its word goes; without it, the blast's damage
		if (mob.entityTags().contains(GIB_TAG)) {
			return true;
		}
		if (mob.entityTags().contains(NO_GIB_TAG)) {
			return false;
		}
		return damage >= SHRED_DAMAGE;
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
			source == null ? null : source.getSourcePosition(), source, damage, all,
			Insides.of(mob, CorpseKind.of(mob), mob.getType(), mob instanceof Player, false, Corpses.look(mob),
				mob instanceof Player ? mob.getStringUUID() : ""));
	}

	/** A creeper just went off: a few chunks of it are thrown out (it dropped nothing, as ever, and leaves no corpse). */
	public static void creeperWentOff(Creeper creeper) {
		if (!(creeper.level() instanceof ServerLevel level)) {
			return;
		}
		int count = 1 + level.getRandom().nextInt(CREEPER_GIBS);
		burst(level, creeper, Corpses.look(creeper), creeper.getScale(), creeper.getBbWidth(), count, null,
			creeper.damageSources().explosion(creeper, creeper), 0.0F, List.of(), Insides.NONE);
	}

	/** A corpse caught in a blast of {@code damage} is blown apart; true if it was (the body is gone). */
	static boolean blowApartCorpse(CorpseEntity corpse, ServerLevel level, DamageSource source, float damage) {
		if (damage < SHRED_DAMAGE) {
			return false;
		}
		EntityType<?> type = corpse.sourceType();
		double width = type == null ? corpse.getBbWidth() : type.getWidth() * corpse.bodyScale();
		burst(level, corpse, corpse.look(), corpse.bodyScale(), width, count(level.getRandom(), width), source.getSourcePosition(),
			source, damage, corpse.takeLoot(), Insides.of(corpse));
		corpse.discard();
		return true;
	}

	/**
	 * A predator has torn {@code corpse} open and eaten it ({@link Scavengers}): what it leaves lies where the body was,
	 * picked over. Ribs, spine and a few bones, a rope of gut and a gut pile it didn't want, and the tatters of what the
	 * body wore.
	 */
	static void eaten(CorpseEntity corpse, ServerLevel level) {
		Insides insides = Insides.of(corpse);
		EntityType<?> type = corpse.sourceType();
		double width = type == null ? corpse.getBbWidth() : type.getWidth() * corpse.bodyScale();
		if (insides == Insides.NONE || width < GORE_MIN_WIDTH) {
			return;
		}
		RandomSource random = level.getRandom();
		int life = corpse.isPlayerCorpse() ? PLAYER_REMAINS_LIFE : REMAINS_LIFE;
		Vec3 at = corpse.position().add(0.0, 0.2, 0.0);
		float size = goreSize(width);
		if (insides.bones()) {
			throwPiece(level, at, Vec3.ZERO, width, Gib.RIBS, insides, size, life, REMAINS_SPEED);
			throwPiece(level, at, Vec3.ZERO, width, Gib.SPINE, insides, size, life, REMAINS_SPEED);
			for (int i = scaled(1 + random.nextInt(2), width); i > 0; i--) {
				throwPiece(level, at, Vec3.ZERO, width, Gib.BONE, insides, size, life, REMAINS_SPEED);
			}
		}
		if (insides.soft()) {
			throwPiece(level, at, Vec3.ZERO, width, Gib.GUTS, insides, size, life, REMAINS_SPEED);
			entrails(level, at, Vec3.ZERO, 1, 0.5);
		}
		if (insides.clothes()) {
			for (int i = 2 + random.nextInt(2); i > 0; i--) {
				throwPiece(level, at, Vec3.ZERO, width, Gib.TATTERS, insides, size, life, REMAINS_SPEED);
			}
		}
	}

	/**
	 * What a body has in it to throw: {@code blood} its colour (RGB) if it has soft insides (organs, gut), or
	 * {@link KitchenBlood#NONE}; whether it has bones; whether it wore clothes, and then its look and (a player's) id, so
	 * the scraps are cut from its own clothes.
	 */
	private record Insides(int blood, boolean bones, boolean clothes, String look, String owner) {
		static final Insides NONE = new Insides(KitchenBlood.NONE, false, false, "", "");

		boolean soft() {
			return blood != KitchenBlood.NONE;
		}

		static Insides of(CorpseEntity corpse) {
			return of(corpse, corpse.kind(), corpse.sourceType(), corpse.isPlayerCorpse(), corpse.gutted(), corpse.look(), corpse.ownerId());
		}

		static Insides of(Entity body, CorpseKind kind, @Nullable EntityType<?> type, boolean player, boolean gutted, String look,
			String owner) {
			if (kind == CorpseKind.SKELETON) {
				return new Insides(KitchenBlood.NONE, true, false, "", "");
			}
			// a fish or a small critter is too little to come apart into more than its chunks
			if (kind == CorpseKind.FISH || Corpses.isMachine(type) || type == EntityTypes.CREEPER
				|| type != null && (type.builtInRegistryHolder().is(NO_INNARDS) || type.builtInRegistryHolder().is(SpiderCrush.CRUSHABLE))) {
				return NONE;
			}
			int blood = KitchenBlood.of(body);
			if (blood == KitchenBlood.NONE) {
				return NONE;
			}
			boolean clothes = player || kind == CorpseKind.ZOMBIE || type != null && (type.builtInRegistryHolder().is(Corpses.VILLAGERS)
				|| type.builtInRegistryHolder().is(EntityTypeTags.ILLAGER) || type == EntityTypes.WITCH);
			// a carcass gutted on the hook has its bones and nothing soft
			return new Insides(gutted ? KitchenBlood.NONE : blood, true, clothes, look, owner);
		}
	}

	/** How big a body this wide has its insides drawn (1 = a man's). */
	private static float goreSize(double width) {
		return (float) Mth.clamp(width / 0.6, 0.4, 2.5);
	}

	/** {@code count} for a man-sized body, more or fewer for a body this wide (at least one). */
	private static int scaled(int count, double width) {
		return Math.max(1, Math.round(count * (float) Mth.clamp(width / 0.6, 0.6, 2.0)));
	}

	/** The insides of a body blown apart: thrown with its chunks, its entrails landing in gut piles around it. */
	private static void gore(ServerLevel level, Vec3 at, Vec3 away, double width, Insides insides) {
		if (insides == Insides.NONE || width < GORE_MIN_WIDTH) {
			return;
		}
		RandomSource random = level.getRandom();
		float size = goreSize(width);
		if (insides.soft()) {
			for (int i = scaled(ORGANS_MIN + random.nextInt(ORGANS_MAX - ORGANS_MIN + 1), width); i > 0; i--) {
				throwPiece(level, at, away, width, Gib.ORGAN, insides, size, life(random), 1.0);
			}
			for (int i = scaled(GUT_ROPES, width); i > 0; i--) {
				throwPiece(level, at, away, width, Gib.GUTS, insides, size, life(random), 1.0);
			}
			entrails(level, at, away, Math.min(ENTRAILS_CAP, scaled(ENTRAILS, width)), ENTRAILS_REACH);
		}
		if (insides.bones()) {
			throwPiece(level, at, away, width, Gib.RIBS, insides, size, life(random), 1.0);
			throwPiece(level, at, away, width, Gib.SPINE, insides, size, life(random), 1.0);
			for (int i = scaled(SHARDS_MIN + random.nextInt(SHARDS_MAX - SHARDS_MIN + 1), width); i > 0; i--) {
				throwPiece(level, at, away, width, Gib.BONE, insides, size, life(random), 1.0);
			}
		}
		if (insides.clothes()) {
			for (int i = TATTERS_MIN + random.nextInt(TATTERS_MAX - TATTERS_MIN + 1); i > 0; i--) {
				throwPiece(level, at, away, width, Gib.TATTERS, insides, size, life(random), 1.0);
			}
		}
	}

	private static int life(RandomSource random) {
		return LIFE_MIN + random.nextInt(LIFE_MAX - LIFE_MIN + 1);
	}

	/** One of a body's insides thrown out from {@code at}, mostly along {@code away}, {@code speed} times as hard as a chunk. */
	private static void throwPiece(ServerLevel level, Vec3 at, Vec3 away, double width, int piece, Insides insides, float size, int life,
		double speed) {
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Math.PI * 2.0;
		double cos = Math.cos(angle), sin = Math.sin(angle), reach = width * 0.35;
		Gib gib = new Gib(GIB, level);
		gib.snapTo(at.x + cos * reach, at.y, at.z + sin * reach, random.nextFloat() * 360.0F, 0.0F);
		double out = GIB_SPEED * speed * (0.4 + random.nextDouble() * 0.6);
		gib.setDeltaMovement((away.x * 0.7 + cos * 0.6) * out, GIB_HOP * speed * (0.6 + random.nextDouble() * 0.6), (away.z * 0.7 + sin * 0.6) * out);
		// a scrap of cloth knows whose it was, to be cut from their clothes
		gib.setUp(piece == Gib.TATTERS ? insides.look() : "", size, life);
		if (piece == Gib.TATTERS) {
			gib.setOwner(insides.owner());
		}
		gib.setPiece(piece, insides.soft() ? insides.blood() : KitchenBlood.RED);
		level.addFreshEntity(gib);
	}

	/**
	 * {@code pieces} of entrails land around {@code at} (within {@code reach}, further along {@code away}) as gut piles.
	 * A tick late: a blast kills before it breaks its blocks, and a pile set down now would go with the ground under it.
	 */
	private static void entrails(ServerLevel level, Vec3 at, Vec3 away, int pieces, double reach) {
		RandomSource random = level.getRandom();
		List<BlockPos> spots = new ArrayList<>();
		for (int i = 0; i < pieces; i++) {
			spots.add(BlockPos.containing(at.x + away.x * reach + (random.nextDouble() - 0.5) * 2.0 * reach, at.y + 1.0,
				at.z + away.z * reach + (random.nextDouble() - 0.5) * 2.0 * reach));
		}
		level.getServer().schedule(new TickTask(level.getServer().getTickCount() + 1, () -> {
			for (BlockPos spot : spots) {
				layEntrails(level, spot);
			}
		}));
	}

	/** One piece of entrails falls from {@code from} onto the first ground under it: a new gut pile, or onto one lying there. */
	private static void layEntrails(ServerLevel level, BlockPos from) {
		BlockState pile = Butchery.OFFAL_PILE.defaultBlockState();
		BlockPos.MutableBlockPos pos = from.mutable();
		for (int fallen = 0; fallen <= ENTRAILS_DROP && level.isLoaded(pos); fallen++, pos.move(Direction.DOWN)) {
			BlockState there = level.getBlockState(pos);
			if (there.is(Butchery.OFFAL_PILE)) {
				int pieces = there.getValue(OffalPileBlock.PIECES);
				if (pieces < OffalPileBlock.MAX) {
					level.setBlock(pos, there.setValue(OffalPileBlock.PIECES, pieces + 1), Block.UPDATE_ALL);
				}
				return;
			}
			if (!there.isAir()) {
				return;
			}
			if (pile.canSurvive(level, pos)) {
				level.setBlock(pos, pile, Block.UPDATE_ALL);
				return;
			}
		}
	}

	/** How many chunks a body this wide breaks into. */
	private static int count(RandomSource random, double width) {
		int count = GIBS_MIN + random.nextInt(GIBS_MAX - GIBS_MIN + 1);
		return Mth.clamp(Math.round(count * (float) Mth.clamp(width / 0.6, 0.6, 2.0)), 2, GIBS_CAP);
	}

	/** Throws {@code count} chunks of {@code body} out from it, away from {@code from} (the blast) if known, and bursts its blood. */
	private static void burst(ServerLevel level, Entity body, String look, float size, double width, int count, @Nullable Vec3 from,
		@Nullable DamageSource source, float damage, List<ItemStack> loot, Insides insides) {
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
		gore(level, at.add(0.0, body.getBbHeight() * 0.4, 0.0), away, width, insides);
		for (ItemStack stack : loot) {
			if (!stack.isEmpty()) {
				ItemEntity item = new ItemEntity(level, at.x, at.y + 0.3, at.z, stack);
				item.setDefaultPickUpDelay();
				level.addFreshEntity(item);
			}
		}
		EntityType<?> kind = body instanceof CorpseEntity corpse ? corpse.sourceType() : body.getType();
		if (Corpses.isMachine(kind)) {
			// (0.5.42) a robot or golem doesn't bleed: its plating and parts scatter with a clang
			BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, CorpseEntity.debris(kind));
			level.sendParticles(debris, at.x, at.y + 0.4, at.z, 40, width * 0.4, 0.3, width * 0.4, 0.3);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_GOLEM_DEATH, SoundSource.HOSTILE, 1.0F, 0.8F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 1.0F, 0.6F);
			return;
		}
		if (ON_BURST.isEmpty() || source == null) {
			// no Jaymo's Powers to bleed it: a spray of red
			KitchenBlood.send(level, body, at.x, at.y + 0.4, at.z, 40, width * 0.4, 0.3, width * 0.4, 0.3);
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
