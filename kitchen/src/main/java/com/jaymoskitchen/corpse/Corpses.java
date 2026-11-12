package com.jaymoskitchen.corpse;

import com.jaymoskitchen.JaymosKitchen;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.jaymoskitchen.ragdoll.RagdollBody;
import com.jaymoskitchen.ragdoll.RagdollRig;
import com.jaymoskitchen.ragdoll.RagdollShapes;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * Corpses: mobs no longer vanish when they die, they leave a body that has to be dealt with (see
 * {@link CorpseKind}). Registers the corpse entity, the carried-corpse item, the butcher block, and the tags.
 */
public final class Corpses {
	/** Mobs that die the vanilla way: bosses, slimes (they split), and so on. */
	public static final TagKey<EntityType<?>> NO_CORPSE = tag("no_corpse");
	public static final TagKey<EntityType<?>> ZOMBIE_CORPSES = tag("zombie_corpses");
	public static final TagKey<EntityType<?>> SKELETON_CORPSES = tag("skeleton_corpses");
	/** Animals too big for one butcher block: they need a 2x2 table, and are heavier to carry. */
	public static final TagKey<EntityType<?>> BIG_CARCASSES = tag("big_carcasses");
	/**
	 * Bodies too heavy to lift by hand (iron golems): no carrying, no pocketing, no butchering. A power can still
	 * take one ({@link CorpseEntity#takeBy}: Jaymo's Powers lets only Gravity do it).
	 */
	public static final TagKey<EntityType<?>> HEAVY_CORPSES = tag("heavy_corpses");
	/**
	 * (0.5.42) Machines and golems (copper golems, clockwork golems, androids, mechs, sentries...): built, not born. They
	 * deconstruct the way an iron golem does: searched once, everything falls out and the rest comes apart and is gone.
	 * They never rot, burn, bleed or get butchered, and scavengers leave them be. Every heavy corpse counts as one too;
	 * a machine that isn't heavy can still be lifted and carried.
	 */
	public static final TagKey<EntityType<?>> MACHINE_CORPSES = tag("machine_corpses");
	/** (0.5.24) What butchers an animal's corpse: any axe, and Jaymo's Powers' primitive and bone knives. */
	public static final TagKey<Item> BUTCHERING_TOOLS = TagKey.create(Registries.ITEM, JaymosKitchen.id("butchering_tools"));
	/**
	 * (0.5.77) Andy: "i need a way to cook and eat this crab with just a fire and a rock, the most primitive barebones
	 * survival with small mobs should be possible". A loose rock (Jaymo's Powers' ground rocks) cracks open any small
	 * animal's body, one you could carry in one hand (a crab, a chicken, a rabbit, a frog...): a rough job, never on a
	 * butcher block's terms. See {@code CorpseEntity#crack}.
	 */
	public static final TagKey<Item> CRACKING_ROCKS = TagKey.create(Registries.ITEM, JaymosKitchen.id("cracking_rocks"));
	/**
	 * Fish, vanilla and modded (Alex's Mobs' flying fish, catfish...), on top of anything built on vanilla's fish class:
	 * looted in one click and gone (see {@link CorpseKind#FISH}).
	 */
	public static final TagKey<EntityType<?>> FISH_CORPSES = tag("fish_corpses");
	/**
	 * (0.5.66) Small critters, looted in one click and gone like a fish (crickets, mice, snails, butterflies...). Any
	 * creature whose kind is under {@link #SMALL_CRITTER_SIZE} both ways counts without being listed (see
	 * {@link #isSmallCritter}).
	 */
	public static final TagKey<EntityType<?>> SMALL_CORPSES = tag("small_corpses");
	/**
	 * (0.5.66) A kind narrower and shorter than this (in blocks) is a small critter. Just under a rabbit (0.4 by 0.5), so
	 * rabbits, chickens and frogs still leave a body to butcher.
	 */
	public static final float SMALL_CRITTER_SIZE = 0.5F;
	/** (0.5.5) Villagers, whose bodies zombies go for first and whose graves (MCA's) a zombie's meal empties ({@link Scavengers}). */
	public static final TagKey<EntityType<?>> VILLAGERS = tag("villagers");

	/** A corpse's look is the mob's saved data as text; anything longer than this is cut down to the basics. */
	public static final int MAX_LOOK_LENGTH = 30000;
	/** Saved data that's large and has nothing to do with how the mob looks. */
	private static final Set<String> HEAVY_KEYS = Set.of("Brain", "brain", "Offers", "offers", "Gossips", "gossips",
		"attributes", "Attributes", "active_effects", "ActiveEffects", "Passengers", "Leash", "leash");

	public static EntityType<CorpseEntity> CORPSE;
	public static DataComponentType<CorpseData> CORPSE_DATA;
	public static Item CORPSE_ITEM;
	/** The oak butcher block (the original one); every wood has its own, all in {@link #BUTCHER_BLOCKS}. */
	public static Block BUTCHER_BLOCK;
	/** Woods with a butcher block, in creative-tab order. Oak's is plain "butcher_block". */
	public static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove",
		"cherry", "pale_oak", "crimson", "warped");
	public static final TagKey<Block> BUTCHER_BLOCKS = TagKey.create(Registries.BLOCK, JaymosKitchen.id("butcher_blocks"));

	private Corpses() {
	}

	private static TagKey<EntityType<?>> tag(String name) {
		return TagKey.create(Registries.ENTITY_TYPE, JaymosKitchen.id(name));
	}

	public static void register() {
		ResourceKey<EntityType<?>> corpseKey = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKitchen.id("corpse"));
		CORPSE = Registry.register(BuiltInRegistries.ENTITY_TYPE, corpseKey,
			EntityType.Builder.<CorpseEntity>of(CorpseEntity::new, MobCategory.MISC)
				.sized(1.0F, 0.4F)
				.clientTrackingRange(8)
				.updateInterval(1)
				.noSummon()
				.build(corpseKey));
		// (0.5.1) someone coming into range sees a resting body's points at once, not at its next resend
		EntityTrackingEvents.START_TRACKING.register((tracked, player) -> {
			if (tracked instanceof CorpseEntity corpse) {
				corpse.sendRagdollTo(player);
			}
		});

		CORPSE_DATA = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKitchen.id("corpse"),
			DataComponentType.<CorpseData>builder()
				.persistent(CorpseData.CODEC)
				.networkSynchronized(CorpseData.STREAM_CODEC)
				.build());

		ResourceKey<Item> corpseItemKey = ResourceKey.create(Registries.ITEM, JaymosKitchen.id("corpse"));
		CORPSE_ITEM = Registry.register(BuiltInRegistries.ITEM, corpseItemKey,
			new CorpseItem(new Item.Properties().stacksTo(1).setId(corpseItemKey)));

		List<Item> butcherBlocks = new ArrayList<>();
		for (String wood : WOODS) {
			String id = wood.equals("oak") ? "butcher_block" : wood + "_butcher_block";
			boolean nether = wood.equals("crimson") || wood.equals("warped");
			ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id(id));
			BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
				.mapColor(MapColor.WOOD)
				.instrument(NoteBlockInstrument.BASS)
				.strength(2.5F)
				.sound(nether ? SoundType.STEM : SoundType.WOOD)
				.setId(blockKey);
			Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, new Block(nether ? properties : properties.ignitedByLava()));
			if (wood.equals("oak")) {
				BUTCHER_BLOCK = block;
			}
			ResourceKey<Item> blockItemKey = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
			butcherBlocks.add(Registry.register(BuiltInRegistries.ITEM, blockItemKey,
				new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(blockItemKey))));
		}

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			ItemStack after = new ItemStack(Items.CRAFTING_TABLE);
			// (stacks are made here, not at registration: items can't be stacked before components are bound)
			for (Item item : butcherBlocks) {
				ItemStack stack = new ItemStack(item);
				output.insertAfter(after, stack);
				after = stack;
			}
		});
	}

	/** Any wood's butcher block. */
	public static boolean isButcherBlock(BlockState state) {
		return state.is(BUTCHER_BLOCKS);
	}

	/**
	 * An entity carrying this (scoreboard) tag leaves no corpse, player or mob: how other mods mark bodies that should
	 * vanish (Jaymo's Powers' Darktide shades and risen shadows).
	 */
	public static final String NO_CORPSE_TAG = "jaymoskitchen.no_corpse";
	/**
	 * (0.5.61) Places other mods say no body is left in (Powers' Other World, where the Judge stands: Andy, "player corpses
	 * dont spawn in the otherworld"). Whoever dies there, player or mob, leaves no corpse (a player's hotbar drops with the
	 * rest), and a body already lying there never gets up.
	 */
	private static final java.util.List<java.util.function.Predicate<net.minecraft.world.entity.Entity>> NO_BODIES =
		new java.util.concurrent.CopyOnWriteArrayList<>();

	/** For other mods (by reflection): where {@code entity} is, no corpse is left and none rises. */
	public static void addNoCorpseZone(java.util.function.Predicate<net.minecraft.world.entity.Entity> zone) {
		NO_BODIES.add(zone);
	}

	/** Whether {@code entity} stands where no body is left ({@link #addNoCorpseZone}). */
	public static boolean inNoCorpseZone(net.minecraft.world.entity.Entity entity) {
		for (java.util.function.Predicate<net.minecraft.world.entity.Entity> zone : NO_BODIES) {
			if (zone.test(entity)) {
				return true;
			}
		}
		return false;
	}

	/** (0.4.37) Prefix of the tags marking parts a body has lost ({@code head}, {@code right_arm}, ...; Jaymo's Powers sets them). */
	public static final String MISSING_TAG = "jaymoskitchen.missing.";
	/**
	 * (0.5.23) Prefix of the tag Jaymo's Kingdoms puts on a body an Archer's killing shot nailed to a wall, followed by the
	 * way it faces out from the wall in whole degrees: its corpse hangs there ({@link CorpseEntity#pin}).
	 */
	public static final String PINNED_TAG = "jaymoskitchen.pinned.";

	/**
	 * Prefix of the tag another mod puts on a body that dies already lying on the ground (Jaymo's Powers: someone downed
	 * and finished where they lay), followed by {@code u} or {@code d} (face up or down), a dot, and the way its head
	 * points in whole degrees: its corpse starts lying just so ({@link #deathBody}) instead of standing and toppling.
	 */
	public static final String LYING_TAG = "jaymoskitchen.lying.";

	/** The body {@code mob}'s {@link #LYING_TAG} asks for (the tag used up), or null if it carries none or it can't be read. */
	private static @org.jspecify.annotations.Nullable RagdollBody lyingFrom(LivingEntity mob, RagdollRig rig) {
		for (String tag : java.util.List.copyOf(mob.entityTags())) {
			if (tag.startsWith(LYING_TAG)) {
				mob.removeTag(tag);
				String rest = tag.substring(LYING_TAG.length());
				try {
					float yaw = Float.parseFloat(rest.substring(2));
					return RagdollBody.lying(rig, mob.getX(), mob.getY(), mob.getZ(), yaw, rest.charAt(0) == 'u', mob.getRandom().nextLong());
				} catch (RuntimeException ignored) {
					return null;
				}
			}
		}
		return null;
	}

	/** (0.5.23) Pins {@code corpse} as {@code body}'s {@link #PINNED_TAG} says, if it carries one (which is then used up). */
	static void pinFrom(LivingEntity body, CorpseEntity corpse) {
		for (String tag : java.util.List.copyOf(body.entityTags())) {
			if (tag.startsWith(PINNED_TAG)) {
				body.removeTag(tag);
				try {
					corpse.pin(Float.parseFloat(tag.substring(PINNED_TAG.length())));
				} catch (NumberFormatException ignored) {
				}
				return;
			}
		}
	}
	private static final String[] PARTS = {"head", "right_arm", "left_arm", "right_leg", "left_leg"};

	/** The parts {@code entity} has lost, from its tags, as {@link CorpseEntity#missing()} bits. */
	public static int missingParts(LivingEntity entity) {
		int missing = 0;
		for (int i = 0; i < PARTS.length; i++) {
			if (entity.entityTags().contains(MISSING_TAG + PARTS[i])) {
				missing |= 1 << i;
			}
		}
		return missing;
	}

	/** (0.5.42) A robot or golem ({@link #MACHINE_CORPSES}, heavy corpses included). */
	public static boolean isMachine(@org.jspecify.annotations.Nullable EntityType<?> type) {
		return type != null && (type.builtInRegistryHolder().is(MACHINE_CORPSES) || type.builtInRegistryHolder().is(HEAVY_CORPSES));
	}

	/**
	 * (0.5.66) A small critter's body: {@link #SMALL_CORPSES}, or any wild kind (not a monster, a machine, a zombie or a
	 * skeleton) under {@link #SMALL_CRITTER_SIZE} both ways. It goes by the kind's size, so a baby or a shrunk mob of a
	 * bigger kind still leaves a body.
	 */
	public static boolean isSmallCritter(@org.jspecify.annotations.Nullable EntityType<?> type) {
		if (type == null) {
			return false;
		}
		var holder = type.builtInRegistryHolder();
		if (holder.is(SMALL_CORPSES)) {
			return true;
		}
		if (type.getCategory() == MobCategory.MONSTER || type.getCategory() == MobCategory.MISC || isMachine(type)
			|| holder.is(ZOMBIE_CORPSES) || holder.is(SKELETON_CORPSES) || holder.is(BIG_CARCASSES)) {
			return false;
		}
		var size = type.getDimensions();
		return size.width() < SMALL_CRITTER_SIZE && size.height() < SMALL_CRITTER_SIZE;
	}

	/** Whether this entity leaves a corpse when it dies (players, listed mobs and tagged ones don't). */
	public static boolean leavesCorpse(LivingEntity entity) {
		return entity instanceof Mob && !(entity instanceof Player) && !entity.is(NO_CORPSE) && !entity.entityTags().contains(NO_CORPSE_TAG)
			&& entity.level() instanceof ServerLevel;
	}

	/**
	 * (0.5.65) For other mods (by reflection, only plain Java and Minecraft types): told (the one who died, its new body)
	 * just before the body is added to the world, so they can carry onto it what the living body had. Jaymo's Powers: the
	 * blood it was covered in (Andy: "the corpses should stay bloodied").
	 */
	public static final java.util.List<java.util.function.BiConsumer<LivingEntity, net.minecraft.world.entity.Entity>> ON_BODY =
		new java.util.concurrent.CopyOnWriteArrayList<>();

	/** Runs the {@link #ON_BODY} hooks; one failing never stops the body appearing. */
	static void tellBody(LivingEntity dead, CorpseEntity corpse) {
		for (java.util.function.BiConsumer<LivingEntity, net.minecraft.world.entity.Entity> hook : ON_BODY) {
			try {
				hook.accept(dead, corpse);
			} catch (RuntimeException e) {
				// another mod's hook
			}
		}
	}

	/** Replaces a mob that has finished dying with its corpse, holding the loot it would have dropped. */
	public static void spawnCorpse(LivingEntity mob, List<ItemStack> loot) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}
		CorpseEntity corpse = new CorpseEntity(CORPSE, level);
		corpse.snapTo(mob.getX(), mob.getY(), mob.getZ(), mob.yBodyRot, 0.0F);
		CorpseKind kind = CorpseKind.of(mob);
		List<ItemStack> kept = new ArrayList<>();
		for (ItemStack stack : loot) {
			// a skeleton's bones come out by hand, one at a time, instead
			if (!stack.isEmpty() && !(kind == CorpseKind.SKELETON && stack.is(Items.BONE))) {
				kept.add(stack);
			}
		}
		corpse.setUp(look(mob), kind, kept, level.getRandom().nextInt(3) + 1);
		corpse.setMissing(missingParts(mob));
		// (0.5.23) the arrows it died with stay in it
		corpse.setArrows(com.jaymoskitchen.archery.StuckArrows.of(mob));
		corpse.setArrowItems(com.jaymoskitchen.archery.StuckArrows.items(mob));
		LivingEntity killer = mob.getKillCredit();
		// a pet's kill is its owner's
		corpse.setKiller(killer, killer instanceof Player
			|| killer instanceof net.minecraft.world.entity.TamableAnimal tame && tame.isTame());
		if (mob.isOnFire() && corpse.burns()) {
			// died burning (a zombie in the sun): keeps burning on the ground
			corpse.setRemainingFireTicks(mob.getRemainingFireTicks());
		}
		corpse.markJustDied();
		corpse.startRagdoll(deathBody(mob));
		pinFrom(mob, corpse);
		tellBody(mob, corpse);
		level.addFreshEntity(corpse);
	}

	/** (0.5.1) A topple this hard (blocks a tick) at the head, and how much of it the rest of the body takes. */
	private static final double TOPPLE_MIN = 0.08, TOPPLE_MAX = 0.14;
	private static final float TOPPLE_SHARE = 0.3F;
	/** (0.5.74) The share of a dying mob's speed (its killing knockback in it) its body keeps, and the most it rises (blocks a tick). */
	private static final double DEATH_CARRY = 0.6, DEATH_LIFT = 0.05;

	/**
	 * (0.5.1) The ragdoll a dying mob (or player) starts as: standing as it stood, moving as it was moving, then toppled
	 * with a push at the head along its death knockback (any way if it had none). One lying or swimming starts lying, and so
	 * does one marked as lying where it died ({@link #LYING_TAG}).
	 */
	public static RagdollBody deathBody(LivingEntity mob) {
		net.minecraft.world.entity.EntityDimensions size = mob.getDimensions(Pose.STANDING);
		RagdollRig rig = RagdollShapes.rig(mob.getType(), size.width(), size.height());
		net.minecraft.util.RandomSource random = mob.getRandom();
		Pose pose = mob.getPose();
		boolean down = pose == Pose.SWIMMING || pose == Pose.SLEEPING || pose == Pose.FALL_FLYING;
		Vec3 v = mob.getDeltaMovement();
		// Andy: downed enemies "popped up after, instead of being beaten into the ground": one that died lying stays lying
		RagdollBody body = lyingFrom(mob, rig);
		if (body != null) {
			down = true;
		} else if (down) {
			body = RagdollBody.lying(rig, mob.getX(), mob.getY(), mob.getZ(), mob.yBodyRot, random.nextBoolean(), random.nextLong());
		} else {
			body = RagdollBody.standing(rig, mob.getX(), mob.getY(), mob.getZ(), mob.yBodyRot);
		}
		// (0.5.74) Andy: "you should just fall where you stand or a bit further if knocked back": the killing blow's
		// knockback carries the body only partway, and its hop (vanilla lifts anyone it knocks back) is left out
		body.setSpeed(v.x * DEATH_CARRY, Math.min(v.y, DEATH_LIFT), v.z * DEATH_CARRY);
		if (!down) {
			double dx = v.x, dz = v.z;
			double length = Math.sqrt(dx * dx + dz * dz);
			if (length < 0.01) {
				double angle = random.nextDouble() * Math.PI * 2.0;
				dx = Math.cos(angle);
				dz = Math.sin(angle);
				length = 1.0;
			}
			double push = TOPPLE_MIN + random.nextDouble() * (TOPPLE_MAX - TOPPLE_MIN);
			int head = switch (rig.shape) {
				case HUMANOID -> RagdollRig.HEAD;
				case QUADRUPED -> RagdollRig.SNOUT;
				case BOX, CHAIN -> RagdollRig.TOP_RIGHT;
			};
			body.hit(body.x[head], body.y[head], body.z[head], dx / length * push, 0.0, dz / length * push, TOPPLE_SHARE);
		}
		return body;
	}

	/** The mob's saved data, trimmed, with its type id: enough for clients to draw it and for a zombie to rise. */
	public static String look(LivingEntity mob) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, mob.registryAccess());
		mob.saveWithoutId(output);
		CompoundTag tag = output.buildResult();
		// (0.4.50) a mob's size is part of its look (Kingdoms' small spiders, grown apes): its scale stays
		ListTag scale = new ListTag();
		for (Tag attribute : tag.getListOrEmpty("attributes")) {
			if (attribute instanceof CompoundTag entry && entry.getStringOr("id", "").equals("minecraft:scale")) {
				scale.add(entry);
			}
		}
		for (String key : HEAVY_KEYS) {
			tag.remove(key);
		}
		if (!scale.isEmpty()) {
			tag.put("attributes", scale);
		}
		tag.putString("id", EntityType.getKey(mob.getType()).toString());
		String look = tag.toString();
		if (look.length() > MAX_LOOK_LENGTH) {
			CompoundTag minimal = new CompoundTag();
			minimal.putString("id", EntityType.getKey(mob.getType()).toString());
			// (0.5.5) and who it was, so its grave can still be found
			minimal.store("UUID", net.minecraft.core.UUIDUtil.CODEC, mob.getUUID());
			look = minimal.toString();
		}
		return look;
	}
}
