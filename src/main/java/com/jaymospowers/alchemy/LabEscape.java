package com.jaymospowers.alchemy;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.WildRoom;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.83) Life story road "Escaped the alchemist's lab" (Kingdoms calls {@link #start} through {@code PowersCompat.labEscape};
 * the Kingdoms side gates it to the Bio-built, Rubber-folk, Emberborn, Stoneborn and Sylvan). A hermit alchemist's small
 * stone lab is raised on open ground in the wild ({@link WildRoom}): cauldron, brewing stand, shelves, a crucible, a chest of
 * odds and ends. You wake in it with one <b>infusion</b> in you ({@link Mark}): a gift and a price that go together, kept
 * for good ({@link #MARK}) till a Potion of Cleansing or healing magic washes it out ({@link #cure}). Two of the alchemist's
 * clockwork golems (Kingdoms' own, or iron golems without it) stir a few seconds after you wake and hunt you
 * ({@link #HUNT}) as far as you run, for {@link #HUNT_TICKS}. The messages stay vague: you find out what was done to you.
 */
public final class LabEscape {
	// ---- tuning ----
	/** The lab: its size, how far from the player it is raised (blocks), the doorway's place and width. */
	static final int W = 11, D = 9, H = 4, DOOR_X = 5, DOOR_W = 2;
	static final int SITE_MIN = 60, SITE_MAX = 160;
	/** The golems: how many, how long they sleep after you wake (ticks), how long they hunt (ticks), how far a tick looks for them (blocks). */
	static final int GOLEMS = 2, WAKE_DELAY = 140;
	static final long HUNT_TICKS = 12000L;
	/** The hunting golems' follow range (blocks): they chase as far as that. */
	static final double FOLLOW_RANGE = 64.0;
	/** The golems are looked after this often (ticks), whoever is near. */
	static final int GOLEM_EVERY = 40;
	/** The marks work this often (ticks), and the effects they refresh last this long (ticks). */
	static final int EVERY = 20, PULSE = 70;
	/** The night-eyed: the sun weakens them (amplifier). The ox-strong: the hunger they burn each pulse. */
	static final int SUN_WEAKNESS = 0;
	static final float HUNGER_DRAIN = 0.35F;
	/** Glass bones: how many hit points the frailty takes off (2 per heart). */
	static final double FRAGILE_HEALTH = -8.0;
	/** Water-shy: the scald from rain or a swim, per pulse. */
	static final float SCALD = 1.0F;

	static final String GOLEM_TAG = "jaymospowers_lab_golem";
	/** On each golem: the game time it wakes and the game time it is gone (the hunt's end), so it needs no player near to do either. */
	static final String WAKE_TAG = "jaymospowers_lab_wake_", END_TAG = "jaymospowers_lab_end_";
	private static final Identifier GOLEM_ID = Identifier.fromNamespaceAndPath("jaymoskingdoms", "clockwork_golem");
	private static final Identifier FRAGILE_ID = JaymosPowers.id("lab_mark_fragile");

	/** A gift and its price. */
	public enum Mark {
		NIGHT_EYES("night_eyes"), OX_STRENGTH("ox_strength"), QUICKSILVER("quicksilver"), IRON_SKIN("iron_skin"), HEARTFIRE("heartfire");

		public final String key;

		Mark(String key) {
			this.key = key;
		}
	}

	/** On an escapee: their mark's number plus one. Saved, kept through death; gone when cured. */
	public static final AttachmentType<Integer> MARK = AttachmentRegistry.create(JaymosPowers.id("lab_mark"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());
	/** An escapee's hunt: the game time the golems give up, and which golems they are. */
	public record Hunt(long until, List<UUID> golems) {
		public static final Codec<Hunt> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("until").forGetter(Hunt::until),
			UUIDUtil.CODEC.listOf().fieldOf("golems").forGetter(Hunt::golems)).apply(i, Hunt::new));
	}

	/** On an escapee: their hunt. Saved, kept through death; gone when it ends. */
	public static final AttachmentType<Hunt> HUNT = AttachmentRegistry.create(JaymosPowers.id("lab_hunt"),
		builder -> builder.persistent(Hunt.CODEC).copyOnDeath());

	private LabEscape() {
	}

	/** Registered from {@code Alchemy.init}. */
	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(LabEscape::tick);
	}

	/** Which mark {@code player} carries, or null. */
	public static @Nullable Mark markOf(net.minecraft.world.entity.player.Player player) {
		Integer m = player.getAttached(MARK);
		return m == null || m < 1 || m > Mark.values().length ? null : Mark.values()[m - 1];
	}

	// ---------------------------------------------------------------- the start

	/**
	 * Jaymo's Kingdoms (by reflection): builds the lab on open ground a walk from {@code player}, wakes them in it with one random
	 * mark, and sets the golems to stir. False if no ground could be found (Kingdoms falls back to the wild).
	 */
	public static boolean start(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		ServerLevel level = player.level().dimension() == Level.OVERWORLD ? player.level() : server.overworld();
		RandomSource random = player.getRandom();
		try {
			BlockPos origin = WildRoom.find(level, player.level() == level ? player.blockPosition() : level.getRespawnData().pos(), SITE_MIN, SITE_MAX, W, D, H, random);
			if (origin == null) {
				return false;
			}
			build(level, origin, random);
			player.teleportTo(level, origin.getX() + 5.5, origin.getY(), origin.getZ() + 5.5, java.util.Set.of(), 180.0F, 0.0F, true);
			player.resetFallDistance();
			player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
			Mark mark = Mark.values()[random.nextInt(Mark.values().length)];
			player.setAttached(MARK, mark.ordinal() + 1);
			player.setAttached(HUNT, new Hunt(level.getGameTime() + HUNT_TICKS, spawnGolems(level, origin)));
			player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, true, false, false));
			player.sendSystemMessage(Component.translatable("message.jaymospowers.lab_escape.wake").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			player.sendSystemMessage(Component.translatable("message.jaymospowers.lab_mark." + mark.key).withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
			return true;
		} catch (RuntimeException e) {
			JaymosPowers.LOGGER.warn("Couldn't build the alchemist's lab for {}", player.getGameProfile().name(), e);
			return false;
		}
	}

	private static void build(ServerLevel level, BlockPos o, RandomSource random) {
		WildRoom.Style style = new WildRoom.Style(Blocks.STONE_BRICKS.defaultBlockState(),
			random.nextBoolean() ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
			Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.DARK_OAK_PLANKS.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState());
		WildRoom.shell(level, o, W, D, H, style, DOOR_X, DOOR_W, random);
		// barred windows in the side walls
		for (int z : new int[] {3, 5}) {
			WildRoom.set(level, o, 0, 1, z, Blocks.IRON_BARS.defaultBlockState());
			WildRoom.set(level, o, W - 1, 1, z, Blocks.IRON_BARS.defaultBlockState());
		}
		// the alchemist's end (north): shelves, a lectern, lanterns
		for (int x = 4; x <= 8; x++) {
			WildRoom.set(level, o, x, 0, 1, Blocks.BOOKSHELF.defaultBlockState());
			WildRoom.set(level, o, x, 1, 1, Blocks.BOOKSHELF.defaultBlockState());
		}
		BlockState lantern = Blocks.LANTERN.defaultBlockState();
		WildRoom.set(level, o, 4, 2, 1, lantern);
		WildRoom.set(level, o, 8, 2, 1, lantern);
		WildRoom.set(level, o, 2, 0, 1, Blocks.LECTERN.defaultBlockState());
		WildRoom.set(level, o, 1, 0, 1, Blocks.CRAFTING_TABLE.defaultBlockState());
		// brewing corner (west)
		WildRoom.set(level, o, 1, 0, 3, Blocks.CAULDRON.defaultBlockState());
		WildRoom.set(level, o, 1, 0, 4, Blocks.BREWING_STAND.defaultBlockState());
		WildRoom.set(level, o, 1, 0, 5, Blocks.CAULDRON.defaultBlockState());
		// the crucible, if the alchemy has made it, else a third cauldron (east)
		Block crucible = com.jaymospowers.alchemy.crucible.Crucibles.CRUCIBLE;
		WildRoom.set(level, o, 9, 0, 3, crucible != null ? crucible.defaultBlockState() : Blocks.CAULDRON.defaultBlockState());
		WildRoom.set(level, o, 9, 0, 5, Blocks.BARREL.defaultBlockState());
		// a chest of odds and ends: honey is on the shelf for someone who knows what for
		BlockPos chestAt = o.offset(9, 0, 7);
		level.setBlock(chestAt, Blocks.CHEST.defaultBlockState(), Block.UPDATE_CLIENTS);
		if (level.getBlockEntity(chestAt) instanceof ChestBlockEntity chest) {
			chest.setItem(0, new ItemStack(Items.GLASS_BOTTLE, 6));
			chest.setItem(1, new ItemStack(Items.HONEYCOMB, 3));
			chest.setItem(2, new ItemStack(Items.NETHER_WART, 2));
			chest.setItem(3, new ItemStack(Items.BREAD, 2));
		}
		// the slab you woke on, with a lamp
		WildRoom.set(level, o, 5, 0, 4, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE, SlabType.BOTTOM));
		WildRoom.set(level, o, 4, 0, 4, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
		WildRoom.set(level, o, 6, 0, 4, lantern);
		WildRoom.set(level, o, 5, 3, 6, lantern.setValue(LanternBlock.HANGING, true));
		level.playSound(null, o.getX() + 5, o.getY(), o.getZ() + 4, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0F, 0.6F);
	}

	/** The alchemist's golems, asleep (no AI) in the north corners: {@link #golems} wakes them on their own timer. Their ids. */
	private static List<UUID> spawnGolems(ServerLevel level, BlockPos o) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(GOLEM_ID).orElse(EntityTypes.IRON_GOLEM);
		long now = level.getServer().overworld().getGameTime();
		List<UUID> ids = new ArrayList<>();
		for (int i = 0; i < GOLEMS; i++) {
			Entity entity = type.create(level, EntitySpawnReason.EVENT);
			if (!(entity instanceof Mob golem)) {
				continue;
			}
			golem.snapTo(o.getX() + (i == 0 ? 2.5 : 8.5), o.getY(), o.getZ() + 6.5, 180.0F, 0.0F);
			golem.addTag(GOLEM_TAG);
			golem.addTag(WAKE_TAG + (now + WAKE_DELAY));
			golem.addTag(END_TAG + (now + HUNT_TICKS));
			golem.setPersistenceRequired();
			golem.setNoAi(true);
			level.addFreshEntity(golem);
			ids.add(golem.getUUID());
		}
		return ids;
	}

	// ---------------------------------------------------------------- the hunt and the marks

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (now % GOLEM_EVERY == 0) {
			golems(server, now);
		}
		if (now % EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive() || player.isSpectator()) {
				continue;
			}
			Mark mark = markOf(player);
			if (mark != null) {
				pulse(player, mark);
			}
			Hunt hunt = player.getAttached(HUNT);
			if (hunt != null) {
				hunt(player, hunt, now);
			}
		}
	}

	/**
	 * Every loaded lab golem, whoever is near (or online): on its wake time it gets up, with a long follow range; at the hunt's
	 * end it is removed, never left asleep or standing about for good. (One in an unloaded chunk is dealt with when it loads.)
	 */
	private static void golems(MinecraftServer server, long now) {
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity entity : level.getAllEntities()) {
				if (!(entity instanceof Mob golem) || !entity.entityTags().contains(GOLEM_TAG)) {
					continue;
				}
				long end = tagTime(golem, END_TAG);
				if (end < 0L || now >= end) {
					golem.discard();
					continue;
				}
				if (golem.isNoAi() && now >= tagTime(golem, WAKE_TAG)) {
					golem.setNoAi(false);
					AttributeInstance follow = golem.getAttribute(Attributes.FOLLOW_RANGE);
					if (follow != null) {
						follow.setBaseValue(FOLLOW_RANGE);
					}
				}
			}
		}
	}

	private static long tagTime(Entity entity, String prefix) {
		for (String tag : entity.entityTags()) {
			if (tag.startsWith(prefix)) {
				try {
					return Long.parseLong(tag.substring(prefix.length()));
				} catch (NumberFormatException e) {
					return -1L;
				}
			}
		}
		return -1L;
	}

	/** An awake golem of this hunt in the player's world is set on them; at the end the hunt is forgotten (the golems go on their own). */
	private static void hunt(ServerPlayer player, Hunt hunt, long now) {
		if (now >= hunt.until()) {
			player.removeAttached(HUNT);
			return;
		}
		for (UUID id : hunt.golems()) {
			if (player.level().getEntity(id) instanceof Mob golem && !golem.isNoAi() && golem.getTarget() != player) {
				golem.setTarget(player);
			}
		}
	}

	private static void pulse(ServerPlayer player, Mark mark) {
		ServerLevel level = player.level();
		AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
		switch (mark) {
			case NIGHT_EYES -> {
				buff(player, MobEffects.NIGHT_VISION, 0, PULSE + 260);
				if (level.isBrightOutside() && !level.isRaining() && level.canSeeSky(player.blockPosition())) {
					buff(player, MobEffects.WEAKNESS, SUN_WEAKNESS, PULSE);
				}
			}
			case OX_STRENGTH -> {
				buff(player, MobEffects.STRENGTH, 0, PULSE);
				player.getFoodData().addExhaustion(HUNGER_DRAIN);
			}
			case QUICKSILVER -> {
				buff(player, MobEffects.SPEED, 0, PULSE);
				if (health != null && !health.hasModifier(FRAGILE_ID)) {
					health.addOrUpdateTransientModifier(new AttributeModifier(FRAGILE_ID, FRAGILE_HEALTH, AttributeModifier.Operation.ADD_VALUE));
				}
			}
			case IRON_SKIN -> {
				buff(player, MobEffects.RESISTANCE, 0, PULSE);
				buff(player, MobEffects.SLOWNESS, 0, PULSE);
			}
			case HEARTFIRE -> {
				buff(player, MobEffects.FIRE_RESISTANCE, 0, PULSE);
				if (player.isInWaterOrRain()) {
					player.hurtServer(level, level.damageSources().magic(), SCALD);
				}
			}
		}
		if (mark != Mark.QUICKSILVER && health != null) {
			health.removeModifier(FRAGILE_ID);
		}
	}

	/** An effect with no icon, particles or flourish: it's in the blood. */
	private static void buff(ServerPlayer player, Holder<MobEffect> effect, int amplifier, int ticks) {
		player.addEffect(new MobEffectInstance(effect, ticks, amplifier, true, false, false));
	}

	// ---------------------------------------------------------------- the cure

	/**
	 * A Potion of Cleansing or healing magic (called from {@code Cleansing} and {@code Mending.easeFever}): the mark is washed
	 * out. True if there was one.
	 */
	public static boolean cure(ServerPlayer player) {
		if (!player.hasAttached(MARK)) {
			return false;
		}
		player.removeAttached(MARK);
		AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.removeModifier(FRAGILE_ID);
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.lab_mark.cured").withStyle(ChatFormatting.GOLD));
		return true;
	}

	/** {@link #cure(ServerPlayer)} for whatever healing magic was cast on (only players carry a mark). */
	public static boolean cure(LivingEntity entity) {
		return entity instanceof ServerPlayer player && cure(player);
	}
}
