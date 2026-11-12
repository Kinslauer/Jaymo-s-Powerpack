package com.jaymospowers.alchemy.picks;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Alchemy;
import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.Research;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.darktide.DarktideStatus;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Holder;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Skin draughts: the skin of the thing touched, as alchemy. One draught base ({@link #SKIN_DRAUGHT}); drunk, it is ready for
 * {@link #READY_TICKS}, and the next block the drinker touches (right-clicks) with a material in {@link Skin} gives them
 * that material's skin for {@link #SKIN_TICKS}: a gain and a cost, every number in the table of {@link Skin} and the
 * constants below. The whole body is drawn over in the touched block's map colour (the client's {@code SkinLayer}).
 * Block matching uses block tags where the game has one.
 */
public final class SkinDraughts {
	// ---- tuning ----
	/** How long the drinker has to touch something (ticks), and how long the skin lasts. */
	public static final int READY_TICKS = 20 * 8, SKIN_TICKS = 20 * 180;
	/** Glass: mobs lose sight of a glass skin beyond this many blocks; blunt hits (falls, blasts, fists, maces) hurt this much more; this share of a melee hit goes back at the attacker. */
	public static final double GLASS_SIGHT = 5.0;
	public static final float GLASS_BLUNT = 1.75F, GLASS_REFLECT = 0.4F;
	/** Ice: the freezing ticks a hit adds, and how much skin a check near fire melts away (ticks). */
	public static final int ICE_FREEZE = 140, ICE_MELT = 60;
	/** Fire damage taken by the skins that burn (wood, wool, sponge), as a multiple. */
	public static final float BURNS = 1.5F;
	/** Water: how hard iron sinks and wood floats (blocks per tick, pushed twice a second), sand's cost per check in water, the damage per second a wet sand skin takes. */
	public static final double SINK = 0.06, FLOAT = 0.05;
	public static final float SAND_WET_DAMAGE = 1.0F;
	/** Sand: the chance a hit comes apart on the skin (and the skin reforms) instead of landing. */
	public static final float SAND_DODGE = 0.25F;
	/** Slime: the bounce a landing gives back, per square root of the fall damage that would have been, and its cap; and the stack of weak hits. */
	public static final double BOUNCE = 0.28, BOUNCE_MAX = 1.1;
	/** Honey: the slowness a melee attacker is stuck with (amplifier, ticks). */
	public static final int STICK_AMPLIFIER = 1, STICK_TICKS = 80;
	/** Sponge: the radius of the water it soaks up, every second. */
	public static final double SOAK_RADIUS = 2.4;
	/** Bell: how long a ringing hit stuns (ticks), how far every hit taken is heard (blocks). */
	public static final int BELL_STUN = 14;
	public static final double BELL_HEARD = 24.0;
	/** How often the scanning effects look about (ticks). */
	private static final int LOOK_EVERY = 10;

	/**
	 * The skins: what touches them, and the gain and cost as attribute changes ({@code armour}, {@code toughness} and
	 * {@code knockback} resistance are added; {@code speed}, {@code attack} and {@code fall} are shares of the base,
	 * -1 meaning none). Extra behaviour (sinking, bouncing, ringing...) is in {@link SkinDraughts}.
	 */
	public enum Skin {
		STONE("stone", state -> state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.STONE_BRICKS) || state.is(Blocks.COBBLESTONE),
			5.0, 1.0, 0.2, -0.12, 0.0, 0.0),
		IRON("iron", state -> state.is(Blocks.IRON_BLOCK) || state.is(Blocks.RAW_IRON_BLOCK) || state.is(BlockTags.ANVIL) || state.is(BlockTags.IRON_ORES)
			|| state.is(Blocks.IRON_BARS) || state.is(Blocks.IRON_DOOR) || state.is(Blocks.IRON_TRAPDOOR) || state.is(BlockTags.CAULDRONS),
			8.0, 2.0, 0.6, -0.22, 0.0, 0.0),
		GLASS("glass", state -> state.getBlock() instanceof TransparentBlock || state.getBlock() instanceof StainedGlassPaneBlock || state.is(Blocks.GLASS_PANE),
			0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
		ICE("ice", state -> state.is(BlockTags.ICE), 0.0, 0.0, 0.0, 0.35, 0.0, 0.0),
		WOOD("wood", state -> state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS), 3.0, 0.0, 0.0, 0.0, 0.0, 0.0),
		SLIME("slime", state -> state.is(Blocks.SLIME_BLOCK), 0.0, 0.0, 0.0, 0.0, -0.5, -1.0),
		HONEY("honey", state -> state.is(Blocks.HONEY_BLOCK) || state.is(Blocks.HONEYCOMB_BLOCK), 2.0, 0.0, 0.0, -0.25, 0.0, 0.0),
		SPONGE("sponge", state -> state.is(Blocks.SPONGE) || state.is(Blocks.WET_SPONGE), 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
		OBSIDIAN("obsidian", state -> state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN), 12.0, 5.0, 0.9, -0.4, 0.0, 0.0),
		SAND("sand", state -> state.is(BlockTags.SAND), 0.0, 0.0, 0.0, 0.05, 0.0, 0.0),
		WOOL("wool", state -> state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS), 0.0, 0.0, 0.0, 0.0, 0.0, -0.5),
		BELL("bell", state -> state.is(Blocks.BELL), 1.0, 0.0, 0.0, 0.0, 0.0, 0.0);

		public final String id;
		final Predicate<BlockState> touch;
		final double armour, toughness, knockback, speed, attack, fall;

		Skin(String id, Predicate<BlockState> touch, double armour, double toughness, double knockback, double speed, double attack, double fall) {
			this.id = id;
			this.touch = touch;
			this.armour = armour;
			this.toughness = toughness;
			this.knockback = knockback;
			this.speed = speed;
			this.attack = attack;
			this.fall = fall;
		}

		/** The skin a block gives, or null. */
		public static @Nullable Skin of(BlockState state) {
			for (Skin skin : values()) {
				if (skin.touch.test(state)) {
					return skin;
				}
			}
			return null;
		}

		public static @Nullable Skin byId(String id) {
			for (Skin skin : values()) {
				if (skin.id.equals(id)) {
					return skin;
				}
			}
			return null;
		}
	}

	/** The skin someone wears: which, the colour it is drawn in (the touched block's map colour, RGB), and the game time it ends. */
	public record Worn(String skin, int rgb, long until) {
		public static final Codec<Worn> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("skin").forGetter(Worn::skin),
				Codec.INT.fieldOf("rgb").forGetter(Worn::rgb),
				Codec.LONG.fieldOf("until").forGetter(Worn::until))
			.apply(i, Worn::new));
		public static final StreamCodec<ByteBuf, Worn> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, Worn::skin,
			ByteBufCodecs.INT, Worn::rgb,
			ByteBufCodecs.VAR_LONG, Worn::until,
			Worn::new);
	}

	/** The skin worn, synced to everyone (they draw it). */
	public static final AttachmentType<Worn> WORN = AttachmentRegistry.create(JaymosPowers.id("skin_worn"),
		builder -> builder.persistent(Worn.CODEC).syncWith(Worn.STREAM_CODEC, AttachmentSyncPredicate.all()));
	/** Game time the drinker stays ready to touch something until. */
	public static final AttachmentType<Long> READY = AttachmentRegistry.create(JaymosPowers.id("skin_ready"),
		builder -> builder.persistent(Codec.LONG));

	public static Item SKIN_DRAUGHT;

	/** Each player's last landing push, so a bounce is not given twice in one landing. */
	private static final Map<UUID, Long> BOUNCED = new HashMap<>();

	private SkinDraughts() {
	}

	static void init() {
		SKIN_DRAUGHT = Alchemy.item("skin_draught", p -> new SkinDraughtItem(p.stacksTo(16).usingConvertsTo(Items.GLASS_BOTTLE)
			.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK)));
		Research.register(new Research.Entry("skins", Research.Category.CRUCIBLE, () -> SKIN_DRAUGHT,
			EssenceList.of(Essence.EARTH, 12, Essence.METAL, 8).plus(Essence.CRYSTAL, 8), List.of("principles"), 0, 3, 1));
		UseBlockCallback.EVENT.register(SkinDraughts::touch);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(SkinDraughts::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(SkinDraughts::afterDamage);
		ServerTickEvents.END_SERVER_TICK.register(SkinDraughts::tick);
	}

	/** The draught: drunk, it readies the drinker to take on the next material they touch. */
	public static class SkinDraughtItem extends Item {
		public SkinDraughtItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
			ItemStack left = super.finishUsingItem(stack, level, entity);
			if (level instanceof ServerLevel && entity instanceof ServerPlayer player) {
				player.setAttached(READY, level.getGameTime() + READY_TICKS);
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.skin.ready").withStyle(ChatFormatting.GRAY));
			}
			return left;
		}
	}

	public static @Nullable Skin skinOf(LivingEntity entity) {
		Worn worn = entity.getAttached(WORN);
		return worn == null ? null : Skin.byId(worn.skin());
	}

	// ---------------------------------------------------------------- taking a skin

	private static InteractionResult touch(net.minecraft.world.entity.player.Player who, Level level, InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
		if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(who instanceof ServerPlayer player) || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		Long ready = player.getAttached(READY);
		if (ready == null) {
			return InteractionResult.PASS;
		}
		if (level.getGameTime() > ready) {
			player.removeAttached(READY);
			return InteractionResult.PASS;
		}
		BlockState state = level.getBlockState(hit.getBlockPos());
		Skin skin = Skin.of(state);
		if (skin == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.skin.nothing").withStyle(ChatFormatting.GRAY));
			return InteractionResult.PASS;
		}
		player.removeAttached(READY);
		int rgb = state.getMapColor(level, hit.getBlockPos()).col;
		end(player);
		player.setAttached(WORN, new Worn(skin.id, rgb == 0 ? 0xA0A0A0 : rgb, level.getGameTime() + SKIN_TICKS));
		apply(player, skin);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.skin.taken", Component.translatable("skin.jaymospowers." + skin.id))
			.withStyle(ChatFormatting.GRAY));
		if (level instanceof ServerLevel server) {
			Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
			MagicFx.ring(server, player.position().add(0.0, 0.1, 0.0), 1.4F, rgb);
			MagicFx.burst(server, at.x, at.y, at.z, rgb, 0.9F, 14, 0.4, 0.6, 0.4);
			server.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, 0.8F, 0.7F);
		}
		return InteractionResult.SUCCESS;
	}

	// ---------------------------------------------------------------- the attributes

	private static final Identifier ARMOUR_ID = JaymosPowers.id("skin_armour"), TOUGHNESS_ID = JaymosPowers.id("skin_toughness"),
		KNOCKBACK_ID = JaymosPowers.id("skin_knockback"), SPEED_ID = JaymosPowers.id("skin_speed"), ATTACK_ID = JaymosPowers.id("skin_attack"),
		FALL_ID = JaymosPowers.id("skin_fall");

	/** Puts {@code skin}'s gains and costs on the player (safe to repeat). */
	private static void apply(ServerPlayer player, Skin skin) {
		modify(player, Attributes.ARMOR, ARMOUR_ID, skin.armour, AttributeModifier.Operation.ADD_VALUE);
		modify(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, skin.toughness, AttributeModifier.Operation.ADD_VALUE);
		modify(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, skin.knockback, AttributeModifier.Operation.ADD_VALUE);
		modify(player, Attributes.MOVEMENT_SPEED, SPEED_ID, skin.speed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		modify(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, skin.attack, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		modify(player, Attributes.FALL_DAMAGE_MULTIPLIER, FALL_ID, skin.fall, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		if (skin == Skin.WOOL) {
			player.setSilent(true);
		}
	}

	private static void modify(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (amount == 0.0) {
			instance.removeModifier(id);
		} else {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
		}
	}

	/** Takes the skin off (its attributes, its silence) without a word. */
	private static void end(ServerPlayer player) {
		for (Identifier id : new Identifier[] {ARMOUR_ID, TOUGHNESS_ID, KNOCKBACK_ID, SPEED_ID, ATTACK_ID, FALL_ID}) {
			for (Holder<Attribute> attribute : List.of(Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS, Attributes.KNOCKBACK_RESISTANCE,
				Attributes.MOVEMENT_SPEED, Attributes.ATTACK_DAMAGE, Attributes.FALL_DAMAGE_MULTIPLIER)) {
				AttributeInstance instance = player.getAttribute(attribute);
				if (instance != null) {
					instance.removeModifier(id);
				}
			}
		}
		// wool's silence is taken off with it (a saved silence outlasts a restart, so this does not rely on a record)
		if (skinOf(player) == Skin.WOOL) {
			player.setSilent(false);
		}
		player.removeAttached(WORN);
	}

	// ---------------------------------------------------------------- damage

	/** Whether a blow is a blunt one: a fall, a blast, a mace, or something with nothing in its hand. */
	static boolean blunt(DamageSource source) {
		if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_MACE_SMASH)) {
			return true;
		}
		return source.getDirectEntity() instanceof LivingEntity attacker && source.getEntity() == attacker
			&& (attacker.getMainHandItem().isEmpty() || attacker.getMainHandItem().is(Items.MACE));
	}

	/** What a hit on a skin does to its size: glass takes more from blunt blows, and the skins that burn take more from fire. */
	static float incoming(LivingEntity victim, DamageSource source, float amount) {
		Skin skin = skinOf(victim);
		if (skin == null) {
			return amount;
		}
		if (skin == Skin.GLASS && blunt(source)) {
			return amount * GLASS_BLUNT;
		}
		if ((skin == Skin.WOOD || skin == Skin.WOOL || skin == Skin.SPONGE) && source.is(DamageTypeTags.IS_FIRE)) {
			return amount * BURNS;
		}
		return amount;
	}

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player)) {
			return true;
		}
		Skin skin = skinOf(player);
		if (skin == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		switch (skin) {
			case SLIME -> {
				if (source.is(DamageTypeTags.IS_FALL)) {
					bounce(player, amount);
					return false;
				}
			}
			case SPONGE -> {
				if (source.is(DamageTypeTags.IS_DROWNING)) {
					return false;
				}
			}
			case OBSIDIAN -> {
				if (source.is(DamageTypeTags.IS_FIRE)) {
					player.clearFire();
					return false;
				}
			}
			case SAND -> {
				if (!wet(player) && player.getRandom().nextFloat() < SAND_DODGE) {
					if (player.level() instanceof ServerLevel level) {
						Worn worn = player.getAttached(WORN);
						int rgb = worn == null ? 0xE0D3A0 : worn.rgb();
						level.sendParticles(MagicFx.mote(rgb, 1.0F), player.getX(), player.getY() + 1.0, player.getZ(), 18, 0.35, 0.6, 0.35, 0.06);
						level.playSound(null, player.blockPosition(), SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 0.9F, 1.2F);
					}
					return false;
				}
			}
			default -> {
			}
		}
		return true;
	}

	private static void bounce(ServerPlayer player, float fall) {
		Long last = BOUNCED.get(player.getUUID());
		long now = player.level().getGameTime();
		if (last != null && now - last < 5) {
			return;
		}
		BOUNCED.put(player.getUUID(), now);
		double lift = Math.min(BOUNCE_MAX, BOUNCE * Math.sqrt(Math.max(1.0F, fall)));
		player.resetFallDistance();
		player.setDeltaMovement(player.getDeltaMovement().x, lift, player.getDeltaMovement().z);
		player.syncVelocity = true;
		if (player.level() instanceof ServerLevel level) {
			level.playSound(null, player.blockPosition(), SoundEvents.SLIME_BLOCK_FALL, SoundSource.PLAYERS, 1.0F, 1.1F);
		}
	}

	private static void afterDamage(LivingEntity entity, DamageSource source, float base, float taken, boolean blocked) {
		if (taken <= 0.0F || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		// someone wearing a skin is hit
		Skin worn = entity instanceof ServerPlayer ? skinOf(entity) : null;
		if (worn != null) {
			LivingEntity attacker = source.getEntity() instanceof LivingEntity living && source.getDirectEntity() == living ? living : null;
			switch (worn) {
				case GLASS -> {
					if (attacker != null && !source.is(DamageTypes.THORNS)) {
						attacker.hurtServer(level, level.damageSources().thorns(entity), taken * GLASS_REFLECT);
						level.sendParticles(MagicFx.spark(0xCFF3FF, 0.9F), attacker.getX(), attacker.getY() + 1.0, attacker.getZ(), 6, 0.3, 0.4, 0.3, 0.05);
					}
				}
				case HONEY -> {
					if (attacker != null) {
						attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STICK_TICKS, STICK_AMPLIFIER));
					}
				}
				case BELL -> {
					ring(level, entity);
					for (Mob mob : level.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(BELL_HEARD), m -> m instanceof Enemy && m.isAlive())) {
						mob.setTarget(entity);
					}
				}
				default -> {
				}
			}
		}
		// someone wearing a skin hits
		if (source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player && entity != player) {
			Skin skin = skinOf(player);
			if (skin == Skin.ICE) {
				entity.setTicksFrozen(entity.getTicksFrozen() + ICE_FREEZE);
				level.sendParticles(MagicFx.mote(0xCFEFFF, 0.9F), entity.getX(), entity.getY() + 1.0, entity.getZ(), 10, 0.3, 0.5, 0.3, 0.04);
			} else if (skin == Skin.BELL) {
				ring(level, player);
				DarktideStatus.stun(entity, BELL_STUN);
			}
		}
	}

	private static void ring(ServerLevel level, LivingEntity at) {
		level.playSound(null, at.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 2.5F, 0.9F + at.getRandom().nextFloat() * 0.2F);
		MagicFx.ring(level, at.position().add(0.0, at.getBbHeight() * 0.6, 0.0), 1.0F, 0xE8C25A);
	}

	// ---------------------------------------------------------------- the passing time

	private static boolean wet(ServerPlayer player) {
		return player.isInWaterOrRain();
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Worn worn = player.getAttached(WORN);
			if (worn == null) {
				continue;
			}
			Skin skin = Skin.byId(worn.skin());
			long now = player.level().getGameTime();
			if (skin == null || now >= worn.until() || !player.isAlive()) {
				end(player);
				if (skin != null && player.isAlive()) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.skin.gone").withStyle(ChatFormatting.GRAY));
				}
				continue;
			}
			if (player.tickCount % 20 == 0) {
				apply(player, skin);
			}
			step(player, skin, worn);
		}
	}

	private static void step(ServerPlayer player, Skin skin, Worn worn) {
		ServerLevel level = player.level();
		boolean inWater = player.isInWater();
		switch (skin) {
			case IRON -> {
				if (inWater && player.tickCount % 2 == 0) {
					player.push(0.0, -SINK, 0.0);
					player.syncVelocity = true;
				}
			}
			case WOOD -> {
				if (inWater && player.tickCount % 2 == 0) {
					player.push(0.0, FLOAT, 0.0);
					player.syncVelocity = true;
				}
			}
			case OBSIDIAN -> {
				if (player.isOnFire()) {
					player.clearFire();
				}
			}
			default -> {
			}
		}
		if (player.tickCount % LOOK_EVERY != 0) {
			return;
		}
		BlockPos feet = player.blockPosition();
		switch (skin) {
			case ICE -> {
				if (player.isOnFire() || near(level, feet, state -> state.is(BlockTags.FIRE) || state.is(Blocks.LAVA) || state.is(BlockTags.CAMPFIRES) || state.is(Blocks.MAGMA_BLOCK))) {
					player.setAttached(WORN, new Worn(worn.skin(), worn.rgb(), worn.until() - ICE_MELT));
					level.sendParticles(MagicFx.miasma(0xDDEEFF, 0.9F), player.getX(), player.getY() + 1.0, player.getZ(), 5, 0.3, 0.5, 0.3, 0.02);
					level.playSound(null, feet, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4F, 1.4F);
				}
			}
			case SAND -> {
				if (wet(player)) {
					player.hurtServer(level, level.damageSources().generic(), SAND_WET_DAMAGE * LOOK_EVERY / 20.0F);
				}
			}
			case SPONGE -> soak(level, feet);
			case GLASS -> {
				for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(24.0), m -> m.getTarget() == player)) {
					if (mob.distanceToSqr(player) > GLASS_SIGHT * GLASS_SIGHT) {
						mob.setTarget(null);
					}
				}
			}
			default -> {
			}
		}
	}

	private static boolean near(ServerLevel level, BlockPos at, Predicate<BlockState> test) {
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-2, -1, -2), at.offset(2, 2, 2))) {
			if (test.test(level.getBlockState(pos))) {
				return true;
			}
		}
		return false;
	}

	/** The sponge's work: plain water round the wearer is drunk up. */
	private static void soak(ServerLevel level, BlockPos at) {
		int reach = (int)Math.ceil(SOAK_RADIUS);
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-reach, -reach, -reach), at.offset(reach, reach + 1, reach))) {
			if (pos.distSqr(at) <= SOAK_RADIUS * SOAK_RADIUS + 1.0 && level.getBlockState(pos).is(Blocks.WATER)) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
			}
		}
	}

	/** The box a skin's tint is drawn over is the whole player; this is for clients' sake, a stable test for "is a skin on". */
	public static boolean wearing(LivingEntity entity) {
		return entity.hasAttached(WORN);
	}
}
