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

	/** (0.5.67) Tags Jaymo's Powers puts on a body dying of a blast or a blunderbuss shot: blown apart, or left whole (by how close it was). */
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
		boolean blast = last != null && last.is(DamageTypeTags.IS_EXPLOSION);
		// (not a blast: only a point-blank blunderbuss shot Jaymo's Powers rolled to blow the body apart)
		if (!blast && !mob.entityTags().contains(GIB_TAG)) {
			return false;
		}
		if (blast && mob instanceof Creeper) {
			return true;
		}
		if (mob.getType().builtInRegistryHolder().is(com.jaymoskitchen.farm.Familiarity.FARM_ANIMALS)) {
			// (GT New Horizons picks) a farm animal isn't blown apart: it leaves a bruised body (CleanKill#BRUISED)
			return false;
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
	 * What a scavenger leaves of {@code corpse} once it has torn the trunk open (Andy, 2026-11-25: "a realistic amount of
	 * entrails should drop, that npc died and there was one intestine and no bones"): the ribcage, the spine, a man's skull
	 * (the eater gnaws a head, it can't crack it), shards of bone, the organs and gut it didn't finish (a few, not a blast's
	 * worth) and gut piles of intestines and offal on the ground, and the tatters of its clothes. A bigger body leaves more
	 * ({@link #scaled}). The bone items left with them come from {@link #bonesLeft}.
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
			if (corpse.humanoid()) {
				throwPiece(level, at, Vec3.ZERO, width, Gib.SKULL, insides, size, life, REMAINS_SPEED);
			}
			for (int i = scaled(EATEN_SHARDS_MIN + random.nextInt(EATEN_SHARDS_MAX - EATEN_SHARDS_MIN + 1), width); i > 0; i--) {
				throwPiece(level, at, Vec3.ZERO, width, Gib.BONE, insides, size, life, REMAINS_SPEED);
			}
		}
		if (insides.soft()) {
			guts(level, at, width, insides, size, life, random);
		}
		if (insides.clothes()) {
			for (int i = 2 + random.nextInt(2); i > 0; i--) {
				throwPiece(level, at, Vec3.ZERO, width, Gib.TATTERS, insides, size, life, REMAINS_SPEED);
			}
		}
	}

	/**
	 * Andy (2026-11-25): scavengers go for "brains, then guts/entrails, then limbs". {@code corpse} torn open at the belly
	 * and its guts pulled out ({@link CorpseEntity#GUTS}): the organs and ropes of gut the eater didn't finish, and gut
	 * piles on the ground. After this it's gutted, and nothing soft is left in it for {@link #eaten}.
	 */
	static void gutsPulled(CorpseEntity corpse, ServerLevel level) {
		Insides insides = Insides.of(corpse);
		EntityType<?> type = corpse.sourceType();
		double width = type == null ? corpse.getBbWidth() : type.getWidth() * corpse.bodyScale();
		if (!insides.soft() || width < GORE_MIN_WIDTH) {
			return;
		}
		int life = corpse.isPlayerCorpse() ? PLAYER_REMAINS_LIFE : REMAINS_LIFE;
		guts(level, corpse.position().add(0.0, 0.2, 0.0), width, insides, goreSize(width), life, level.getRandom());
	}

	/** The guts an eaten body leaves ({@link #eaten}, {@link #gutsPulled}). */
	private static void guts(ServerLevel level, Vec3 at, double width, Insides insides, float size, int life, RandomSource random) {
		for (int i = scaled(EATEN_ORGANS_MIN + random.nextInt(EATEN_ORGANS_MAX - EATEN_ORGANS_MIN + 1), width); i > 0; i--) {
			throwPiece(level, at, Vec3.ZERO, width, Gib.ORGAN, insides, size, life, REMAINS_SPEED);
		}
		for (int i = scaled(EATEN_GUT_ROPES, width); i > 0; i--) {
			throwPiece(level, at, Vec3.ZERO, width, Gib.GUTS, insides, size, life, REMAINS_SPEED);
		}
		entrails(level, at, Vec3.ZERO, Math.min(ENTRAILS_CAP, scaled(EATEN_ENTRAILS_MIN + random.nextInt(EATEN_ENTRAILS_MAX - EATEN_ENTRAILS_MIN + 1),
			width)), EATEN_ENTRAILS_REACH);
	}

	/**
	 * Left by an eaten body (a man's, more or fewer for a bigger or smaller one): shards of bone thrown with the ribs and
	 * spine; organs and ropes of gut it didn't finish; gut piles on the ground (intestines and offal in turn), and how far
	 * round the body they lie (blocks); bone items (the long bones of its limbs, picked clean).
	 */
	static final int EATEN_SHARDS_MIN = 2, EATEN_SHARDS_MAX = 3, EATEN_ORGANS_MIN = 1, EATEN_ORGANS_MAX = 2, EATEN_GUT_ROPES = 2;
	static final int EATEN_ENTRAILS_MIN = 2, EATEN_ENTRAILS_MAX = 3;
	static final double EATEN_ENTRAILS_REACH = 1.1;
	static final int EATEN_BONES_MIN = 3, EATEN_BONES_MAX = 5;

	/** How many bone items an eaten body leaves ({@link #EATEN_BONES_MIN} to {@link #EATEN_BONES_MAX} for a man, more or fewer by size). */
	static int bonesLeft(CorpseEntity corpse, RandomSource random) {
		Insides insides = Insides.of(corpse);
		EntityType<?> type = corpse.sourceType();
		double width = type == null ? corpse.getBbWidth() : type.getWidth() * corpse.bodyScale();
		if (!insides.bones() || width < GORE_MIN_WIDTH) {
			// (a creeper, a spider, a critter: a bone or two as before; a fish none)
			return corpse.kind() == CorpseKind.FISH ? 0 : 1 + random.nextInt(2);
		}
		return scaled(EATEN_BONES_MIN + random.nextInt(EATEN_BONES_MAX - EATEN_BONES_MIN + 1), width);
	}

	/** Bite chunks: lumps a bite throws (more when a part comes away), how big beside a blast's, how long they lie (ticks), how hard they fly. */
	static final int BITE_LUMPS = 1, PART_LUMPS = 3, BITE_LIFE_MIN = 60, BITE_LIFE_MAX = 120;
	static final float BITE_SIZE = 0.4F;
	static final double BITE_SPEED = 0.55;
	/** One bite in this many tears a scrap off what the body wore (a part coming away always does). */
	static final int BITE_TATTERS_ONE_IN = 4;

	/**
	 * Andy (2026-11-24): "chunks flying everywhere". A bite torn out of {@code corpse} at {@code at} ({@link Scavengers}):
	 * lumps of it fly up and back the way the jaws ripped ({@code away}, flat), a scrap of its clothes now and then, more
	 * when a whole part comes away ({@code partOff}). Small and quickly gone, not a blast's mess.
	 */
	static void bitten(CorpseEntity corpse, ServerLevel level, Vec3 at, Vec3 away, boolean partOff) {
		Insides insides = Insides.of(corpse);
		EntityType<?> type = corpse.sourceType();
		double width = type == null ? corpse.getBbWidth() : type.getWidth() * corpse.bodyScale();
		if (insides == Insides.NONE || width < GORE_MIN_WIDTH) {
			return;
		}
		RandomSource random = level.getRandom();
		float size = goreSize(width) * BITE_SIZE;
		int lumps = partOff ? PART_LUMPS + random.nextInt(2) : BITE_LUMPS + random.nextInt(2);
		for (int i = 0; i < lumps; i++) {
			// flesh where it bleeds, bone where it's only bone
			biteChunk(level, at, away, insides.soft() ? Gib.ORGAN : Gib.BONE, insides, size, random);
		}
		if (partOff && insides.bones()) {
			biteChunk(level, at, away, Gib.BONE, insides, size, random);
		}
		if (insides.clothes() && (partOff || random.nextInt(BITE_TATTERS_ONE_IN) == 0)) {
			biteChunk(level, at, away, Gib.TATTERS, insides, size, random);
		}
	}

	private static void biteChunk(ServerLevel level, Vec3 at, Vec3 away, int piece, Insides insides, float size, RandomSource random) {
		Gib gib = new Gib(GIB, level);
		gib.snapTo(at.x, at.y, at.z, random.nextFloat() * 360.0F, 0.0F);
		double angle = random.nextDouble() * Math.PI * 2.0;
		double out = GIB_SPEED * BITE_SPEED * (0.5 + random.nextDouble() * 0.5);
		gib.setDeltaMovement((away.x * 0.6 + Math.cos(angle) * 0.4) * out, GIB_HOP * BITE_SPEED * (0.8 + random.nextDouble() * 0.6),
			(away.z * 0.6 + Math.sin(angle) * 0.4) * out);
		gib.setUp(piece == Gib.TATTERS ? insides.look() : "", size, BITE_LIFE_MIN + random.nextInt(BITE_LIFE_MAX - BITE_LIFE_MIN + 1));
		if (piece == Gib.TATTERS) {
			gib.setOwner(insides.owner());
		}
		gib.setPiece(piece, insides.soft() ? insides.blood() : KitchenBlood.RED);
		level.addFreshEntity(gib);
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
	private static Gib throwPiece(ServerLevel level, Vec3 at, Vec3 away, double width, int piece, Insides insides, float size, int life,
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
		return gib;
	}

	/** Candy colours a piñata's sweets are, RGB: strawberry, lemon, mint, grape, orange, sky, bubblegum. */
	static final int[] SWEETS = {0xFF5A7A, 0xFFE24D, 0x5BE3A0, 0xA05CE8, 0xFF9A3C, 0x55B8FF, 0xFF8FD8};
	/** Sweets a man-sized body bursts into, at random between these (a bigger or smaller one more or fewer). */
	static final int SWEETS_MIN = 8, SWEETS_MAX = 12;
	/** Shards of ice a man-sized frozen body shatters into, at random between these; how hard they fly. */
	static final int SHARDS_ICE_MIN = 8, SHARDS_ICE_MAX = 12;
	static final double ICE_SPEED = 0.8;
	static final int ICE_COLOUR = 0xCFEBFF;
	/** A body that comes apart into loose bones: this many (a man's), and the bones' speed. */
	static final int LOOSE_BONES_MIN = 3, LOOSE_BONES_MAX = 5;
	/** Pieces a body flattened under a falling block sprays (a man's, more or fewer for a bigger or smaller one), and how gently. */
	static final int FLAT_MIN = 3, FLAT_MAX = 5;
	static final double FLAT_SPEED = 0.5;

	/** One tinted, clean piece ({@code piece}, {@code rgb}) thrown from {@code at}, away from {@code away}. */
	private static Gib throwTinted(ServerLevel level, Vec3 at, Vec3 away, double width, int piece, int rgb, float size, int life, double speed) {
		Gib gib = throwPiece(level, at, away, width, piece, Insides.NONE, size, life, speed);
		gib.setPiece(piece, rgb);
		return gib;
	}

	/** A piñata's body bursts into sweets (candy-coloured pieces) instead of gore. */
	private static void pinata(ServerLevel level, Vec3 at, Vec3 away, double width) {
		RandomSource random = level.getRandom();
		float size = goreSize(width);
		for (int i = scaled(SWEETS_MIN + random.nextInt(SWEETS_MAX - SWEETS_MIN + 1), width); i > 0; i--) {
			throwTinted(level, at, away, width, Gib.CANDY, SWEETS[random.nextInt(SWEETS.length)], size, life(random), 1.0);
		}
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y, at.z, 12, width * 0.4, 0.3, width * 0.4, 0.2);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.HOSTILE, 1.0F, 1.4F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.HOSTILE, 0.6F, 1.6F);
	}

	/** A frozen body shatters: shards of ice fly out of it in all directions, and the frost hangs in the air. */
	static void shatter(ServerLevel level, Vec3 at, double width, double height) {
		RandomSource random = level.getRandom();
		float size = goreSize(width);
		Vec3 middle = at.add(0.0, Math.min(height, 2.0) * 0.4, 0.0);
		for (int i = scaled(SHARDS_ICE_MIN + random.nextInt(SHARDS_ICE_MAX - SHARDS_ICE_MIN + 1), width); i > 0; i--) {
			throwTinted(level, middle, Vec3.ZERO, width, Gib.ICE, ICE_COLOUR, size, life(random), ICE_SPEED);
		}
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()), middle.x, middle.y, middle.z, 30,
			width * 0.4, 0.4, width * 0.4, 0.2);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.0F, 0.8F);
	}

	/** A skeleton's body comes apart: a few loose bones tumble out of it. */
	static void looseBones(ServerLevel level, Vec3 at, double width) {
		RandomSource random = level.getRandom();
		Insides bones = new Insides(KitchenBlood.NONE, true, false, "", "");
		float size = goreSize(width);
		for (int i = scaled(LOOSE_BONES_MIN + random.nextInt(LOOSE_BONES_MAX - LOOSE_BONES_MIN + 1), width); i > 0; i--) {
			throwPiece(level, at, Vec3.ZERO, width, i % 3 == 0 ? Gib.RIBS : i % 3 == 1 ? Gib.BONE : Gib.SPINE, bones, size, life(random), REMAINS_SPEED);
		}
	}

	/**
	 * A falling block or anvil came down on {@code corpse}: flattened pieces of what it was (its chunks and insides, drawn wide
	 * and thin) squirt out along the ground, and its blood spreads.
	 */
	static void crushed(CorpseEntity corpse, ServerLevel level) {
		EntityType<?> type = corpse.sourceType();
		double width = type == null ? corpse.getBbWidth() : type.getWidth() * corpse.bodyScale();
		RandomSource random = level.getRandom();
		Vec3 at = corpse.position().add(0.0, 0.15, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.HOSTILE, 1.0F, 0.5F);
		if (Corpses.isMachine(type) || corpse.kind() == CorpseKind.FISH) {
			return;
		}
		boolean sweets = GoreRules.pinata(level, corpse.blockPosition());
		Insides insides = sweets ? Insides.NONE : Insides.of(corpse);
		float size = goreSize(width);
		int pieces = scaled(FLAT_MIN + random.nextInt(FLAT_MAX - FLAT_MIN + 1), width);
		for (int i = 0; i < pieces; i++) {
			Gib gib;
			if (sweets) {
				gib = throwTinted(level, at, Vec3.ZERO, width, Gib.CANDY, SWEETS[random.nextInt(SWEETS.length)], size, life(random), FLAT_SPEED);
			} else if (insides.soft() && i % 2 == 0) {
				gib = throwPiece(level, at, Vec3.ZERO, width, i % 4 == 0 ? Gib.ORGAN : Gib.GUTS, insides, size, life(random), FLAT_SPEED);
			} else if (insides.bones() && i % 2 == 1) {
				gib = throwPiece(level, at, Vec3.ZERO, width, Gib.BONE, insides, size, life(random), FLAT_SPEED);
			} else {
				// a flattened chunk of its own model
				gib = new Gib(GIB, level);
				gib.snapTo(at.x, at.y, at.z, random.nextFloat() * 360.0F, 0.0F);
				double angle = random.nextDouble() * Math.PI * 2.0;
				double out = GIB_SPEED * FLAT_SPEED * (0.4 + random.nextDouble() * 0.6);
				gib.setDeltaMovement(Math.cos(angle) * out, GIB_HOP * 0.3, Math.sin(angle) * out);
				gib.setUp(corpse.look(), size, life(random));
				level.addFreshEntity(gib);
			}
			gib.setFlat();
		}
		if (!sweets && !ON_BURST.isEmpty() && corpse.level() instanceof ServerLevel) {
			DamageSource source = level.damageSources().fallingBlock(null);
			Map.Entry<DamageSource, Float> blow = Map.entry(source, 4.0F);
			for (var hook : ON_BURST) {
				try {
					hook.accept(corpse, blow);
				} catch (RuntimeException e) {
					JaymosKitchen.LOGGER.warn("A blown-apart hook failed: {}", e.toString());
				}
			}
		} else if (!sweets && insides.soft()) {
			KitchenBlood.send(level, corpse, at.x, at.y, at.z, 24, width * 0.5, 0.1, width * 0.5, 0.2);
		}
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
		// (Andy, 2026-11-19: "intestines and offal should fall from gibs") turn about, so two pieces or more are both
		boolean guts = random.nextBoolean();
		level.getServer().schedule(new TickTask(level.getServer().getTickCount() + 1, () -> {
			boolean rope = guts;
			for (BlockPos spot : spots) {
				layPile(level, spot, rope ? Butchery.INTESTINES_PILE : Butchery.OFFAL_PILE);
				rope = !rope;
			}
		}));
	}

	/**
	 * One piece of {@code kind} (offal, intestines, brain matter: an {@link OffalPileBlock}) falls from {@code from} onto the
	 * first ground under it: a new pile, or onto one of its kind lying there.
	 */
	static boolean layPile(ServerLevel level, BlockPos from, Block kind) {
		BlockState pile = kind.defaultBlockState();
		BlockPos.MutableBlockPos pos = from.mutable();
		for (int fallen = 0; fallen <= ENTRAILS_DROP && level.isLoaded(pos); fallen++, pos.move(Direction.DOWN)) {
			BlockState there = level.getBlockState(pos);
			if (there.is(kind)) {
				int pieces = there.getValue(OffalPileBlock.PIECES);
				if (pieces < OffalPileBlock.MAX) {
					level.setBlock(pos, there.setValue(OffalPileBlock.PIECES, pieces + 1), Block.UPDATE_ALL);
					return true;
				}
				return false;
			}
			if (!there.isAir()) {
				return false;
			}
			if (pile.canSurvive(level, pos)) {
				level.setBlock(pos, pile, Block.UPDATE_ALL);
				return true;
			}
		}
		return false;
	}

	/** A crushed skull's pieces of bone and lumps of brain thrown (for a man's head; a bigger or smaller one more or fewer). */
	static final int SKULL_PIECES_MIN = 2, SKULL_PIECES_MAX = 3, BRAIN_LUMPS_MIN = 2, BRAIN_LUMPS_MAX = 4;
	/** Brain matter a man's crushed head leaves on the ground (pieces), how far round it, and how gently the pieces fly. */
	static final int BRAIN_MATTER_MIN = 1, BRAIN_MATTER_MAX = 2;
	static final double BRAIN_REACH = 0.9, CRUSH_SPEED = 0.45;

	/**
	 * Andy (2026-11-19): "maces and blunt weapons should crush skulls upon a blow that connects on [the head] and kills.
	 * crushing should leave white debris and smaller offal like pebbles called brain matter". Jaymo's Powers calls this
	 * (by reflection) when a blunt killing blow crushes {@code body}'s head at {@code head}: pieces of skull and jaw and a
	 * few lumps of brain fly a little way and lie about as a blast's do, a puff of white bone dust, and the brain matter
	 * lands on the ground under it as small piles ({@link com.jaymoskitchen.butchery.BrainMatterBlock}). A body with no
	 * blood or no innards (a machine, a slime) leaves only the bone; one with no bones nothing. True if anything was left.
	 */
	public static boolean skullCrushed(LivingEntity body, Vec3 head) {
		if (!(body.level() instanceof ServerLevel level)) {
			return false;
		}
		Insides insides = Insides.of(body, CorpseKind.of(body), body.getType(), body instanceof Player, false, "", "");
		if (insides == Insides.NONE && Corpses.isMachine(body.getType())) {
			return false;
		}
		RandomSource random = level.getRandom();
		double width = body.getBbWidth();
		float size = goreSize(width);
		Vec3 away = body.getLastDamageSource() != null && body.getLastDamageSource().getSourcePosition() != null
			? head.subtract(body.getLastDamageSource().getSourcePosition()).multiply(1.0, 0.0, 1.0) : Vec3.ZERO;
		away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize();
		for (int i = scaled(SKULL_PIECES_MIN + random.nextInt(SKULL_PIECES_MAX - SKULL_PIECES_MIN + 1), width); i > 0; i--) {
			throwPiece(level, head, away, width, Gib.SKULL, insides, size, life(random), CRUSH_SPEED);
		}
		// the white debris: dust and chips of bone
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), head.x, head.y, head.z, 40,
			0.2, 0.15, 0.2, 0.25);
		if (!insides.soft()) {
			return true;
		}
		for (int i = scaled(BRAIN_LUMPS_MIN + random.nextInt(BRAIN_LUMPS_MAX - BRAIN_LUMPS_MIN + 1), width); i > 0; i--) {
			throwPiece(level, head, away, width, Gib.BRAIN, insides, size, life(random), CRUSH_SPEED);
		}
		for (int i = scaled(BRAIN_MATTER_MIN + random.nextInt(BRAIN_MATTER_MAX - BRAIN_MATTER_MIN + 1), width); i > 0; i--) {
			layPile(level, BlockPos.containing(head.x + away.x * BRAIN_REACH + (random.nextDouble() - 0.5) * 2.0 * BRAIN_REACH, head.y,
				head.z + away.z * BRAIN_REACH + (random.nextDouble() - 0.5) * 2.0 * BRAIN_REACH), Butchery.BRAIN_MATTER_PILE);
		}
		level.playSound(null, head.x, head.y, head.z, SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.0F, 0.7F);
		return true;
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
		EntityType<?> bodyType = body instanceof CorpseEntity corpse ? corpse.sourceType() : body.getType();
		// a piñata (a rule, or the Candy Cavity): sweets, no chunks, no gore, no blood
		boolean sweets = !Corpses.isMachine(bodyType) && GoreRules.pinata(level, body.blockPosition());
		for (int i = 0; i < (sweets ? 0 : count); i++) {
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
		if (sweets) {
			pinata(level, at.add(0.0, body.getBbHeight() * 0.4, 0.0), away, width);
		} else {
			gore(level, at.add(0.0, body.getBbHeight() * 0.4, 0.0), away, width, insides);
		}
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
		if (sweets) {
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
