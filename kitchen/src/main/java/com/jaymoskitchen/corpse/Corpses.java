package com.jaymoskitchen.corpse;

import com.jaymoskitchen.JaymosKitchen;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.jaymoskitchen.ragdoll.RagdollBody;
import com.jaymoskitchen.ragdoll.RagdollRig;
import com.jaymoskitchen.ragdoll.RagdollShapes;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
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
	 * Fish, vanilla and modded (Alex's Mobs' flying fish, catfish...), on top of anything built on vanilla's fish class:
	 * looted in one click and gone (see {@link CorpseKind#FISH}).
	 */
	public static final TagKey<EntityType<?>> FISH_CORPSES = tag("fish_corpses");

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
	/** (0.4.37) Prefix of the tags marking parts a body has lost ({@code head}, {@code right_arm}, ...; Jaymo's Powers sets them). */
	public static final String MISSING_TAG = "jaymoskitchen.missing.";
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

	/** Whether this entity leaves a corpse when it dies (players, listed mobs and tagged ones don't). */
	public static boolean leavesCorpse(LivingEntity entity) {
		return entity instanceof Mob && !(entity instanceof Player) && !entity.is(NO_CORPSE) && !entity.entityTags().contains(NO_CORPSE_TAG)
			&& entity.level() instanceof ServerLevel;
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
		level.addFreshEntity(corpse);
	}

	/** (0.5.0) A topple this hard (blocks a tick) at the head, and how much of it the rest of the body takes. */
	private static final double TOPPLE_MIN = 0.08, TOPPLE_MAX = 0.14;
	private static final float TOPPLE_SHARE = 0.3F;

	/**
	 * (0.5.0) The ragdoll a dying mob (or player) starts as: standing as it stood, moving as it was moving, then toppled
	 * with a push at the head along its death knockback (any way if it had none). One lying or swimming starts lying.
	 */
	public static RagdollBody deathBody(LivingEntity mob) {
		net.minecraft.world.entity.EntityDimensions size = mob.getDimensions(Pose.STANDING);
		RagdollRig rig = RagdollShapes.rig(mob.getType(), size.width(), size.height());
		net.minecraft.util.RandomSource random = mob.getRandom();
		Pose pose = mob.getPose();
		boolean down = pose == Pose.SWIMMING || pose == Pose.SLEEPING || pose == Pose.FALL_FLYING;
		Vec3 v = mob.getDeltaMovement();
		RagdollBody body;
		if (down) {
			body = RagdollBody.lying(rig, mob.getX(), mob.getY(), mob.getZ(), mob.yBodyRot, random.nextBoolean(), random.nextLong());
		} else {
			body = RagdollBody.standing(rig, mob.getX(), mob.getY(), mob.getZ(), mob.yBodyRot);
		}
		body.setSpeed(v.x, v.y, v.z);
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
				case BOX -> RagdollRig.TOP_RIGHT;
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
			look = minimal.toString();
		}
		return look;
	}
}
