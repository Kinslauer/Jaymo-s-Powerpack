package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.102) The hidden blade: a leather bracer with a spring blade ({@code jaymoskingdoms:hidden_blade}), the Stealth
 * tree's own weapon. Anyone can craft it, but only a player at Stealth level {@link #BLADE_LEVEL} can work it: in lesser
 * hands the spring jams and the blade won't come out.
 * <ul>
 * <li><b>Worn</b> (0.7.113, like Jaymo's Powers' web shooters): use the bracer to strap it on the wrist of the hand
 * holding it (the other wrist if that one has one already), so one on either arm or both. It's kept in a synced player
 * attachment ({@link #WORN}), saved, and dropped where you die like armour unless keepInventory holds. Sneak + use with
 * that hand empty, aiming at nothing, takes a sheathed one off into the hand.</li>
 * <li><b>Spring</b>: use with an empty hand (nothing else to use there: no door, no villager) springs that wrist's blade
 * out, or sheathes it again (the main hand first; with both hands empty both blades go together). Anything taken into
 * that hand sheathes it. Out, with the main hand empty, the blows are the blade's ({@link #BLADE_DAMAGE},
 * {@link #BLADE_SPEED}), and it counts as a light blade (see {@link #weapon}).</li>
 * <li><b>Leap</b>: a skilled player with a blade on a wrist whose hand is empty who falls at least {@link #LEAP_MIN_FALL} blocks onto
 * a living thing takes no fall damage; what they would have taken is dealt to it as their own blow (so crimes,
 * witnesses and kill credit follow), and it is {@link #crippled Crippled}. A miss is a fall like any other.</li>
 * <li><b>Crippled</b>: a Kingdoms status on any living thing (an attachment holding the game time it ends, not a
 * {@code MobEffect}): it can barely walk or jump, a player can't sprint.</li>
 * <li><b>Stab</b>: every full-strength hidden-blade blow at a Crippled foe deals {@link #CRIPPLED_STAB} times the damage
 * (from {@code LivingEntityHurtMixin}, after the class perks and the Combat Arts).</li>
 * </ul>
 * Blades held in hand from before 0.7.113 are the same item: use one to strap it on.
 *
 * <p>How a leap is caught: each server tick, a falling player's box, swept from where their feet were last tick down to
 * {@link #LEAP_REACH} below where they are now, is checked for a living thing whose top was below their feet a tick
 * ago (they came down on it from above). Players fall through mobs rather than land on them, so if that misses (a mob
 * that moved under them on the very last tick), the landing itself is the second chance: when the fall damage comes, a
 * living thing standing where they land ({@link #LAND_REACH}) takes it instead.
 */
public final class HiddenBlade {
	// ---- tuning ----
	/** The Stealth level that works the blade. */
	static final int BLADE_LEVEL = 5;
	/** The blade out, main hand empty: added to the bare-hand attack (1), so 4 in all like an iron sword; its speed (4 + this: 2.2 swings a second). */
	static final double BLADE_DAMAGE = 3.0, BLADE_SPEED = -1.8;
	/** A leap needs a fall of at least this (blocks). */
	static final double LEAP_MIN_FALL = 4.0;
	/** How far below the feet a falling player's box reaches for a target (blocks), and how near a landing must be. */
	static final double LEAP_REACH = 0.6, LAND_REACH = 0.4;
	/** After a leap, a landing no further than this below the target's feet is the leap's own (no fall damage). */
	static final double LEAP_LAND_SLACK = 1.0;
	/** Crippled: this long at least (ticks), this much more for each {@link #CRIPPLE_STEP} blocks fallen past the least, at most this long. */
	static final int CRIPPLE_BASE_TICKS = 120, CRIPPLE_STEP_TICKS = 20, CRIPPLE_MAX_TICKS = 400;
	static final double CRIPPLE_STEP = 2.0;
	/** Crippled: the share of walking speed and of jump taken away. */
	static final double CRIPPLE_SLOW = 0.7, CRIPPLE_JUMP = 1.0;
	/** A full-strength stab at a Crippled foe deals this many times the blow. */
	static final float CRIPPLED_STAB = 10.0F;
	/** How often the jam message may repeat (ticks), and how often a Crippled one limps and bleeds a mark (ticks). */
	static final long JAM_GAP = 40L;
	static final int LIMP_TICKS = 20;

	/** The game time a living thing stops being Crippled (kept with it, so a chunk unload doesn't cure it). */
	static final AttachmentType<Long> CRIPPLED = AttachmentRegistry.create(JaymosKingdoms.id("crippled"), builder -> builder.persistent(Codec.LONG));
	private static final Identifier SLOW_ID = JaymosKingdoms.id("crippled_slow"), JUMP_ID = JaymosKingdoms.id("crippled_jump");
	private static final Identifier DAMAGE_ID = JaymosKingdoms.id("hidden_blade_damage"), SPEED_ID = JaymosKingdoms.id("hidden_blade_speed");

	/** The two wrists, as {@link #WORN}'s bits count them. */
	public static final int MAIN = 0, OFF = 1;
	/**
	 * On a player: the blades on their wrists, as bits: {@code 1 << hand} worn on that hand's wrist, {@code 4 << hand} its
	 * blade out. Saved; synced to everyone, for drawing them.
	 */
	public static final AttachmentType<Integer> WORN = AttachmentRegistry.create(JaymosKingdoms.id("hidden_blades"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/** Client → server: use with {@code hand} empty, wearing a blade on its wrist: spring it out or in, or ({@code takeOff}) take it off into the hand. */
	public record Spring(int hand, boolean takeOff) implements CustomPacketPayload {
		public static final Type<Spring> TYPE = new Type<>(JaymosKingdoms.id("hidden_blade_spring"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Spring> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.hand);
				buf.writeBoolean(p.takeOff);
			}, buf -> new Spring(buf.readVarInt(), buf.readBoolean()));

		@Override
		public Type<Spring> type() {
			return TYPE;
		}
	}

	public static Item ITEM;

	/** The Crippled ones loaded right now (each tick: limping, and cured on time). */
	private static final List<LivingEntity> TRACKED = new ArrayList<>();
	/** Player → their feet's height last tick (to sweep a fall's box); → when they were last told the blade jammed. */
	private static final Map<UUID, Double> LAST_Y = new HashMap<>();
	private static final Map<UUID, Long> JAMMED = new HashMap<>();
	/** Player → {target entity id, game time} of their last 10x stab (a re-hurt of the same blow, e.g. a backstab, isn't stabbed again). */
	private static final Map<UUID, long[]> STABBED = new HashMap<>();
	/**
	 * Players who leapt on something this fall, till they land → the height of the target's feet: landing there (the rest
	 * of the drop, down through it) hurts them no more; falling on past it, off a ledge or a flying thing, hurts as ever.
	 */
	private static final Map<UUID, Double> LEAPT = new HashMap<>();

	private HiddenBlade() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("hidden_blade"));
		ItemLore lore = new ItemLore(List.of(
			Component.translatable("item.jaymoskingdoms.hidden_blade.lore.1").withStyle(ChatFormatting.GRAY),
			Component.translatable("item.jaymoskingdoms.hidden_blade.lore.2", BLADE_LEVEL).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
			Component.translatable("item.jaymoskingdoms.hidden_blade.lore.3", (int) LEAP_MIN_FALL).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
			Component.translatable("item.jaymoskingdoms.hidden_blade.lore.4", (int) CRIPPLED_STAB).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
			Component.translatable("item.jaymoskingdoms.hidden_blade.lore.5").withStyle(ChatFormatting.DARK_GRAY)));
		// (0.7.113: no longer a sword in hand; the same id, so blades from older worlds load as this and strap on)
		ITEM = Registry.register(BuiltInRegistries.ITEM, key,
			new HiddenBladeItem(new Item.Properties().setId(key).stacksTo(1).component(DataComponents.LORE, lore)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.insertAfter(Items.IRON_SWORD, ITEM));
		PayloadTypeRegistry.serverboundPlay().register(Spring.TYPE, Spring.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Spring.TYPE, (payload, context) -> spring(context.player(), payload));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player && bits(player) != 0) {
				if (player.level().getGameRules().get(GameRules.KEEP_INVENTORY)) {
					// (kept, the attachment copied over, but sheathed)
					player.setAttached(WORN, bits(player) & 3);
				} else {
					// dropped like armour
					for (int hand = MAIN; hand <= OFF; hand++) {
						if (worn(player, hand)) {
							Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), new ItemStack(ITEM));
						}
					}
					player.removeAttached(WORN);
				}
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(HiddenBlade::allowDamage);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> loaded(entity));
		ServerTickEvents.END_SERVER_TICK.register(HiddenBlade::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			LAST_Y.remove(handler.player.getUUID());
			JAMMED.remove(handler.player.getUUID());
			STABBED.remove(handler.player.getUUID());
			LEAPT.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TRACKED.clear();
			LAST_Y.clear();
			JAMMED.clear();
			STABBED.clear();
			LEAPT.clear();
		});
	}

	// ---- who can work it -----------------------------------------------------------------------------------------------

	static boolean is(ItemStack stack) {
		return ITEM != null && stack.is(ITEM);
	}

	/** {@code player} is Stealthy enough to work the blade. */
	static boolean skilled(ServerPlayer player) {
		return Skills.of(player).level(Skills.Pillar.STEALTH) >= BLADE_LEVEL;
	}

	/** A skilled player with a blade on a wrist whose hand is empty (out or not: a leap springs it). */
	private static boolean armed(ServerPlayer player) {
		return (ready(player, MAIN) || ready(player, OFF)) && skilled(player);
	}

	private static void jammed(ServerPlayer player, long now) {
		Long last = JAMMED.get(player.getUUID());
		if (last == null || now - last >= JAM_GAP) {
			JAMMED.put(player.getUUID(), now);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.hidden_blade.jams", BLADE_LEVEL).withStyle(ChatFormatting.GRAY));
		}
	}

	// ---- wearing (both sides) ------------------------------------------------------------------------------------------

	/** {@link #WORN}'s bits on {@code player} (0: none worn). */
	public static int bits(Player player) {
		return player.getAttachedOrElse(WORN, 0);
	}

	/** A blade is strapped on {@code hand}'s wrist ({@link #MAIN} or {@link #OFF}). */
	public static boolean worn(Player player, int hand) {
		return (bits(player) & 1 << hand) != 0;
	}

	/** {@code hand}'s blade is out. */
	public static boolean out(Player player, int hand) {
		return (bits(player) & 4 << hand) != 0;
	}

	public static InteractionHand hand(int hand) {
		return hand == OFF ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
	}

	/** A blade on {@code hand}'s wrist, and that hand empty: free to spring. */
	public static boolean ready(Player player, int hand) {
		return worn(player, hand) && player.getItemInHand(hand(hand)).isEmpty();
	}

	/** The main hand empty, and a blade out: a blow now is the blade's (the off hand's too, struck backhand). */
	public static boolean bladeOut(Player player) {
		return player.getMainHandItem().isEmpty() && (out(player, MAIN) || out(player, OFF));
	}

	/**
	 * What {@code player} strikes with, for the class perks and Combat Arts that ask after a weapon: a hidden blade while one
	 * is out over an empty main hand, else what that hand holds (nothing, if that's a bracer not yet strapped on).
	 */
	public static ItemStack weapon(ServerPlayer player) {
		if (ITEM == null) {
			return player.getMainHandItem();
		}
		return bladeOut(player) ? new ItemStack(ITEM) : is(player.getMainHandItem()) ? ItemStack.EMPTY : player.getMainHandItem();
	}

	private static void setBits(ServerPlayer player, int bits) {
		if (bits == 0) {
			player.removeAttached(WORN);
		} else if (bits != bits(player)) {
			player.setAttached(WORN, bits);
		}
	}

	/** The bracer in {@code held} goes on that hand's wrist (else the other's). False if both wrists already wear one. */
	static boolean putOn(ServerPlayer player, InteractionHand held) {
		int first = held == InteractionHand.OFF_HAND ? OFF : MAIN;
		int wrist = !worn(player, first) ? first : !worn(player, 1 - first) ? 1 - first : -1;
		if (wrist < 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.hidden_blade.both").withStyle(ChatFormatting.GRAY));
			return false;
		}
		setBits(player, bits(player) | 1 << wrist);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0F, 1.1F);
		return true;
	}

	/** Use with an empty hand, from the client ({@link Spring}). */
	private static void spring(ServerPlayer player, Spring spring) {
		int hand = spring.hand();
		if (hand != MAIN && hand != OFF || !player.isAlive() || player.isSpectator() || !ready(player, hand)) {
			return;
		}
		if (spring.takeOff()) {
			if (!player.isShiftKeyDown() || out(player, hand)) {
				return;
			}
			setBits(player, bits(player) & ~(1 << hand));
			player.setItemInHand(hand(hand), new ItemStack(ITEM));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0F, 1.3F);
			return;
		}
		long now = player.level().getGameTime();
		if (!skilled(player)) {
			jammed(player, now);
			player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.4F, 1.6F);
			return;
		}
		boolean both = hand == MAIN && ready(player, OFF);
		// (both hands empty: out unless both already are)
		boolean draw = both ? !(out(player, MAIN) && out(player, OFF)) : !out(player, hand);
		int bits = draw ? bits(player) | 4 << hand : bits(player) & ~(4 << hand);
		if (both) {
			// (both hands empty: both blades together)
			bits = draw ? bits | 4 << OFF : bits & ~(4 << OFF);
		}
		setBits(player, bits);
		sound(player, draw);
	}

	/** The spring's sound: a sharp click and a ring of steel out, a duller click in. */
	private static void sound(ServerPlayer player, boolean draw) {
		player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.5F, draw ? 2.0F : 1.4F);
		if (draw) {
			player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ARMOR_EQUIP_CHAIN, SoundSource.PLAYERS, 0.7F, 1.9F);
		}
	}

	/**
	 * Each tick: a blade whose hand took something up (or whose wearer forgot the art) is sheathed, and the blade's attack
	 * and speed are on while one is out over an empty main hand (changed only when that changes, so nothing is resent; the
 * class perks then look again, see {@link #weapon}).
	 */
	private static void wear(ServerPlayer player) {
		int bits = bits(player);
		for (int hand = MAIN; hand <= OFF; hand++) {
			if ((bits & 4 << hand) != 0 && (!ready(player, hand) || player.isSpectator() || !skilled(player))) {
				bits &= ~(4 << hand);
			}
		}
		setBits(player, bits);
		boolean on = bladeOut(player), changed = false;
		AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE), speed = player.getAttribute(Attributes.ATTACK_SPEED);
		if (damage != null && damage.hasModifier(DAMAGE_ID) != on) {
			// (and a Thief's light-blade perks follow the blade in and out)
			changed = true;
			if (on) {
				damage.addOrUpdateTransientModifier(new AttributeModifier(DAMAGE_ID, BLADE_DAMAGE, AttributeModifier.Operation.ADD_VALUE));
			} else {
				damage.removeModifier(DAMAGE_ID);
			}
		}
		if (speed != null && speed.hasModifier(SPEED_ID) != on) {
			if (on) {
				speed.addOrUpdateTransientModifier(new AttributeModifier(SPEED_ID, BLADE_SPEED, AttributeModifier.Operation.ADD_VALUE));
			} else {
				speed.removeModifier(SPEED_ID);
			}
		}
		if (changed) {
			Classes.refresh(player);
		}
	}

	// ---- the stab ------------------------------------------------------------------------------------------------------

	/**
	 * What a blow deals, from {@code amount} (LivingEntity.hurtServer, after ClassPerks and CombatArts): a full-strength
	 * stab at a Crippled foe deals {@link #CRIPPLED_STAB} times (so is one at a monster that doesn't know it's there, with
	 * Assassinate: not stacking). Only a skilled player's own melee blow with a blade out over an empty main hand
	 * ({@link #bladeOut}), never one of the Combat Arts' own (a cleave, a bleed, a leap).
	 */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (ITEM == null || CombatArts.striking() || !(source.getEntity() instanceof ServerPlayer sp) || source.getDirectEntity() != sp
			|| !source.is(DamageTypes.PLAYER_ATTACK) || target == sp || sp.isSpectator() || !bladeOut(sp) || !skilled(sp)) {
			return amount;
		}
		long now = sp.level().getGameTime();
		boolean cripple = crippled(target);
		// (0.7.111) an unaware monster taken from behind (or while it searches) by a hand that has learned Assassinate: the same ten times
		boolean assassin = Senses.assassinable(target, sp);
		if (!(cripple || assassin) || !CombatArts.fullStrength(sp, now) || Senses.cooling(target, source)) {
			return amount;
		}
		long[] last = STABBED.get(sp.getUUID());
		if (last != null && last[0] == target.getId() && last[1] == now) {
			return amount;
		}
		STABBED.put(sp.getUUID(), new long[] {target.getId(), now});
		if (sp.level() instanceof ServerLevel level) {
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.0F, 0.6F);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.5F);
			level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 16, 0.25, 0.3, 0.25, 0.25);
		}
		if (assassin) {
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.hidden_blade.assassinate").withStyle(ChatFormatting.DARK_RED));
			Skills.assassination(sp);
		} else {
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.hidden_blade.stab").withStyle(ChatFormatting.DARK_RED));
		}
		// (the crippled and the assassin's bonus are one and the same: they don't stack)
		return amount * CRIPPLED_STAB;
	}

	// ---- the leap ------------------------------------------------------------------------------------------------------

	/**
	 * Fall damage on landing: after a leap that struck in the air, none (the rest of the drop, down through the target, is
	 * part of the leap); else, if a skilled leap came down on (or right beside) a living thing it missed in the air, it
	 * takes it instead.
	 */
	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (entity instanceof ServerPlayer leapt && source.is(DamageTypeTags.IS_FALL)) {
			Double feet = LEAPT.remove(leapt.getUUID());
			if (feet != null && leapt.getY() >= feet - LEAP_LAND_SLACK) {
				return false;
			}
		}
		if (!(entity instanceof ServerPlayer player) || !source.is(DamageTypeTags.IS_FALL) || player.fallDistance < LEAP_MIN_FALL
			|| !(player.level() instanceof ServerLevel level) || player.isSpectator() || !armed(player)) {
			return true;
		}
		AABB box = player.getBoundingBox().inflate(LAND_REACH);
		LivingEntity target = target(level, player, box, Double.MAX_VALUE);
		if (target == null) {
			return true;
		}
		leap(level, player, target, amount, player.fallDistance);
		return false;
	}

	/** The fall vanilla would hurt {@code player} for from here: (blocks fallen - safe fall) times the fall damage multiplier, rounded up. */
	static float fallDamage(ServerPlayer player) {
		double safe = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
		double mult = player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);
		return (float) Math.max(0.0, Math.ceil((player.fallDistance - safe) * mult));
	}

	/** The nearest living thing in {@code box} a leap could come down on: not {@code player}, their mount or rider; its top no higher than {@code topBelow}. */
	private static @Nullable LivingEntity target(ServerLevel level, ServerPlayer player, AABB box, double topBelow) {
		LivingEntity best = null;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive()
			&& !(e instanceof Player p && p.isSpectator()) && e != player.getVehicle() && e.getVehicle() != player)) {
			if (e.getBoundingBox().maxY > topBelow) {
				continue;
			}
			if (best == null || e.distanceToSqr(player) < best.distanceToSqr(player)) {
				best = e;
			}
		}
		return best;
	}

	/** {@code player} comes down on {@code target}: the fall is its, it's Crippled, and they take none of it. */
	private static void leap(ServerLevel level, ServerPlayer player, LivingEntity target, float damage, double fell) {
		player.resetFallDistance();
		// (the blades on empty hands spring out as they come down)
		int bits = bits(player);
		boolean drawn = false;
		for (int hand = MAIN; hand <= OFF; hand++) {
			if (ready(player, hand) && !out(player, hand)) {
				bits |= 4 << hand;
				drawn = true;
			}
		}
		if (drawn) {
			setBits(player, bits);
			sound(player, true);
		}
		CombatArts.strike(level, player, target, damage);
		if (target.isAlive()) {
			int extra = (int) Math.floor(Math.max(0.0, fell - LEAP_MIN_FALL) / CRIPPLE_STEP) * CRIPPLE_STEP_TICKS;
			cripple(target, Math.min(CRIPPLE_MAX_TICKS, CRIPPLE_BASE_TICKS + extra));
		}
		level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.8), target.getZ(), 24, 0.3, 0.4, 0.3, 0.3);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.6), target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.7F);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F, 0.8F);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 0.6F, 1.2F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.hidden_blade.leap").withStyle(ChatFormatting.DARK_RED));
		// (a leap that lands on its mark is Stealth's to learn from)
		Skills.grant(player, Skills.Pillar.STEALTH, Skills.XP_BLADE_LEAP);
	}

	/** A skilled, falling player: does this tick's fall (swept from last tick's feet to {@link #LEAP_REACH} below now) come down on something? */
	private static void falling(ServerPlayer player, double lastY) {
		if (player.onGround() || player.fallDistance < LEAP_MIN_FALL || player.isPassenger() || player.isFallFlying() || player.getAbilities().flying
			|| player.isInWater() || player.isSpectator() || !(player.level() instanceof ServerLevel level) || !armed(player)) {
			return;
		}
		AABB b = player.getBoundingBox();
		double top = Math.max(lastY, player.getY()) + 0.1;
		AABB box = new AABB(b.minX, player.getY() - LEAP_REACH, b.minZ, b.maxX, top, b.maxZ);
		// (its top below where their feet were: they came down on it, not fell alongside it)
		LivingEntity target = target(level, player, box, top);
		if (target != null) {
			leap(level, player, target, fallDamage(player), player.fallDistance);
			LEAPT.put(player.getUUID(), target.getY());
		}
	}

	// ---- Crippled ------------------------------------------------------------------------------------------------------

	/** {@code e} is Crippled now. */
	public static boolean crippled(LivingEntity e) {
		Long until = e.getAttached(CRIPPLED);
		return until != null && until > e.level().getGameTime();
	}

	/** Cripples {@code e} for {@code ticks} (or keeps the longer of this and what it has). */
	static void cripple(LivingEntity e, int ticks) {
		long until = e.level().getGameTime() + ticks;
		Long had = e.getAttached(CRIPPLED);
		e.setAttached(CRIPPLED, had == null ? until : Math.max(had, until));
		hobble(e, true);
		if (!TRACKED.contains(e)) {
			TRACKED.add(e);
		}
		if (e instanceof ServerPlayer p) {
			p.setSprinting(false);
			p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.crippled", (ticks + 19) / 20).withStyle(ChatFormatting.RED));
		}
	}

	/** Puts on (or takes off) the Crippled slow and lame jump. */
	private static void hobble(LivingEntity e, boolean on) {
		modifier(e, Attributes.MOVEMENT_SPEED, SLOW_ID, on ? -CRIPPLE_SLOW : 0.0);
		modifier(e, Attributes.JUMP_STRENGTH, JUMP_ID, on ? -CRIPPLE_JUMP : 0.0);
	}

	/** Like {@link Skills#modifier}, for any living thing. */
	private static void modifier(LivingEntity e, Holder<Attribute> attribute, Identifier id, double amount) {
		AttributeInstance instance = e.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (amount == 0.0) {
			instance.removeModifier(id);
		} else {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	/** A living thing comes into the world (a chunk loads, a player joins): still Crippled, it limps again; healed meanwhile, it's cured. */
	private static void loaded(Entity entity) {
		if (!(entity instanceof LivingEntity e) || e.getAttached(CRIPPLED) == null) {
			return;
		}
		if (crippled(e)) {
			hobble(e, true);
			if (!TRACKED.contains(e)) {
				TRACKED.add(e);
			}
		} else {
			cure(e);
		}
	}

	private static void cure(LivingEntity e) {
		e.removeAttached(CRIPPLED);
		hobble(e, false);
		if (e instanceof ServerPlayer p && p.isAlive()) {
			p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.crippled.over").withStyle(ChatFormatting.GRAY));
		}
	}

	// ---- the tick ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		boolean second = server.getTickCount() % 20 == 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			wear(player);
			Double lastY = LAST_Y.put(player.getUUID(), player.getY());
			if (player.onGround() || player.isInWater()) {
				// (landed: the leap's fall is over, its landing already let off)
				LEAPT.remove(player.getUUID());
			}
			falling(player, lastY == null ? player.getY() : lastY);
			if (second && !TRACKED.contains(player)) {
				// (a player back from a log-out or a respawn, in case nothing loaded them as an entity)
				loaded(player);
			}
		}
		for (Iterator<LivingEntity> it = TRACKED.iterator(); it.hasNext(); ) {
			LivingEntity e = it.next();
			if (e.isRemoved() || !e.isAlive()) {
				// (unloaded or gone: the attachment stays with it, and ENTITY_LOAD picks it up again)
				it.remove();
				continue;
			}
			if (!crippled(e)) {
				cure(e);
				it.remove();
				continue;
			}
			if (e instanceof ServerPlayer p && p.isSprinting()) {
				p.setSprinting(false);
			}
			if (e.tickCount % LIMP_TICKS == 0 && e.level() instanceof ServerLevel level) {
				level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, e.getX(), e.getY() + 0.3, e.getZ(), 2, 0.2, 0.1, 0.2, 0.05);
				if (e instanceof ServerPlayer p) {
					long left = e.getAttachedOrElse(CRIPPLED, 0L) - level.getGameTime();
					p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.crippled", (left + 19) / 20).withStyle(ChatFormatting.RED));
				}
			}
		}
	}
}
