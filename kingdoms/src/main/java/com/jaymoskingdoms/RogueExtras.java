package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Coinage;
import com.jaymoskingdoms.money.Money;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Class trees patch 8, the extras of the Thief and the Ranger (docs/ClassTrees-Plan.md).
 *
 * <p><b>Thief.</b> Nimble Fingers (a pocket or a lock a tenth quicker a rank), Case the Mark (sneak and watch someone three
 * seconds: two pips by the crosshair say whether they are wary and whether their pockets are worth it), Fence's Friend (what
 * is lifted from a pocket is stolen goods; a speakeasy's fence buys them, at half their worth from anyone and in full from a
 * friend), Second Storey (a landing from under six blocks and a climb aren't heard), the second crossroads Cutpurse King (a
 * fumbled pocket only makes the mark wary) or Shadow Blade (a light blade in the back of an unaware weaker foe puts it down),
 * and Ghost (the first blow out of Vanish is a sneak attack, and no blood is left while it lasts).
 *
 * <p><b>Ranger.</b> Keen Eye (arrows truer and faster a rank), Field Dressing (one more round of cuts from your own kill on
 * the hook), Fletcher (six arrows a set, and the arrow that kills is given back), the second crossroads Stalker (crouched
 * and still in grass or leaves, unseen past six blocks) or Warden (a favoured foe within 24 blocks is felt as a heartbeat),
 * and Heartseeker (a full-drawn shot at an unaware favoured foe can't miss or glance off the head it was called on).
 */
public final class RogueExtras {
	// ---- Thief ----
	/** Nimble Fingers: a pocket or a lock takes this much less time, each rank. */
	static final float FINGERS_PER_RANK = 0.1F;
	/** Case the Mark: how long to watch (ticks), how far, how squarely (the look's dot), and how often the marks are sent again. */
	static final int CASE_TICKS = 60, CASE_EVERY = 10;
	static final double CASE_RANGE = 16.0, CASE_DOT = 0.98;
	/** Fence's Friend: the share of its worth a fence gives anyone for stolen goods, and the least a piece is worth (milli). */
	static final float FENCE_PLAIN = 0.5F;
	static final long STOLEN_FLOOR = 40L;
	/** The smallest coin there is (milli): a fence pays in whole coins. */
	static final long SMALLEST_COIN = 100L;
	/** (what loot is worth is counted in the old emerald's 1.5: {@link Lockpicking#worth}) */
	static final float WORTH_EMERALD = 1.5F;
	/** Second Storey: a landing from this far (blocks) is heard, this far and so much further a block fallen, to this at most; under this a Second Storey Thief's isn't. A climb is heard this far. */
	static final double LAND_HEARD = 2.5, LAND_BASE = 5.0, LAND_PER_BLOCK = 1.5, LAND_MOST = 20.0, STOREY_FALL = 6.0, CLIMB_REACH = 6.0, CLIMB_MOVED = 0.3;
	/** Cutpurse King: a mark made wary keeps a hand on their purse this long (ticks), and a pocket of theirs takes this much longer. */
	static final int WARY_TICKS = 24000;
	static final float WARY_FACTOR = 1.5F;
	/** Shadow Blade: "weaker" is no more of its own health than this share of the Thief's. */
	static final float BLADE_WEAKER = 1.0F;
	/** Shadow Blade: the felling blow is this many times what the foe has left, so armour and warding (at most 96% between them) don't save them. */
	static final float BLADE_THROUGH = 30.0F;
	/** Ghost: how long after Vanish's cover is gone the first blow still counts (ticks). */
	static final int GHOST_TICKS = 120;
	// ---- Ranger ----
	/** Keen Eye: how far an arrow is pulled onto the line of the eye, and how much faster it flies, each rank. */
	static final float EYE_TRUER = 0.25F, EYE_SPEED = 0.05F;
	static final double EYE_OWN = 0.9;
	/** Field Dressing: more rounds of cuts from your own kill, and how near the carcass is looked for (blocks). */
	static final int DRESS_CUTS = 1;
	static final double DRESS_REACH = 6.0;
	/** Fletcher: arrows a set (vanilla's four), and the chance the arrow that kills is given back. */
	static final int FLETCH_SET = 6, PLAIN_SET = 4;
	/** A shot already looked at (saved with the arrow). Kitchen's tag for an arrow's chance to come out of a body whole, and its plain chance. */
	static final String SEEN_TAG = "jaymoskingdoms_shot_seen", SALVAGE_TAG = "jaymoskitchen.salvage.";
	static final float PLAIN_SALVAGE = 0.6F;
	/** Stalker: nothing sees them past this (blocks). */
	static final double STALK_RANGE = 6.0;
	/** Warden: how far a favoured foe is felt, how often it is looked for, and the heartbeat's slowest and fastest (ticks) and quietest. */
	static final double WARDEN_RANGE = 24.0;
	static final int WARDEN_EVERY = 4, WARDEN_SLOW = 44, WARDEN_FAST = 10;
	static final float WARDEN_QUIET = 0.2F;

	public static final String FINGERS = "thief_fingers", CASE = "thief_case", FENCE = "thief_fence", STOREY = "thief_storey", CUTPURSE = "thief_cutpurse",
		SHADOW_BLADE = "thief_shadow_blade", GHOST = "thief_ghost";
	public static final String EYE = "ranger_eye", DRESS = "ranger_dress", FLETCH = "ranger_fletch", STALKER = "ranger_stalker", WARDEN = "ranger_warden", HEART = "ranger_heart";
	static final String STOLEN_TAG = "jaymoskingdoms_stolen";

	/** Server → client: the mark {@code entity} has been cased; whether they are wary, and whether their pockets are worth it. */
	public record Cased(int entity, boolean wary, boolean worth) implements CustomPacketPayload {
		public static final Type<Cased> TYPE = new Type<>(JaymosKingdoms.id("thief_cased"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Cased> CODEC = CustomPacketPayload.codec((p, buf) -> {
			buf.writeVarInt(p.entity);
			buf.writeByte((p.wary ? 1 : 0) | (p.worth ? 2 : 0));
		}, buf -> {
			int entity = buf.readVarInt();
			int b = buf.readByte();
			return new Cased(entity, (b & 1) != 0, (b & 2) != 0);
		});

		@Override
		public Type<Cased> type() {
			return TYPE;
		}
	}

	private record Watch(int entity, int ticks) {
	}

	private static final Map<UUID, Watch> WATCHING = new HashMap<>();
	/** Until when (game time) a mark made wary (Cutpurse King) stays so. Saved with the mark, so a restart doesn't forget it. */
	static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<Long> WARY = net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
		.create(JaymosKingdoms.id("wary_until"), builder -> builder.persistent(com.mojang.serialization.Codec.LONG));
	/** The furthest each player has fallen this drop, and where they were on the last climb check. */
	private static final Map<UUID, Double> FALLING = new HashMap<>();
	private static final Map<UUID, Double> CLIMBED = new HashMap<>();
	/** Ghost: until when the first blow counts, whether it has been struck, and who had Vanish's cover on them a tick ago. */
	private static final Map<UUID, Long> GHOST_UNTIL = new HashMap<>();
	private static final Map<UUID, Boolean> GHOST_SPENT = new HashMap<>();
	private static final java.util.Set<UUID> HIDDEN = new java.util.HashSet<>();
	private static final Map<UUID, Long> BEAT = new HashMap<>();
	private static @Nullable Holder<MobEffect> stealth;
	private static boolean lookedForStealth;

	private RogueExtras() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Cased.TYPE, Cased.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(RogueExtras::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WATCHING.clear();
			FALLING.clear();
			CLIMBED.clear();
			GHOST_UNTIL.clear();
			GHOST_SPENT.clear();
			HIDDEN.clear();
			BEAT.clear();
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			// Keen Eye: the arrow leaves the string nearer the line of the eye, and faster
			if (entity instanceof AbstractArrow arrow && arrow.tickCount == 0 && !arrow.entityTags().contains(SEEN_TAG)
				&& arrow.getOwner() instanceof ServerPlayer archer && bowShot(arrow)) {
				// (the tag is saved: an arrow coming back with its chunk isn't a fresh shot)
				arrow.addTag(SEEN_TAG);
				float fletch = Math.min(1.0F, ClassTrees.power(archer, FLETCH));
				if (fletch > 0.0F && arrow.pickup == AbstractArrow.Pickup.ALLOWED) {
					// Fletcher, with Kitchen: every arrow of theirs comes out of a body whole (Kitchen rolls it as the arrow goes in)
					arrow.entityTags().removeIf(tag -> tag.startsWith(SALVAGE_TAG));
					arrow.addTag(SALVAGE_TAG + Math.round(100.0F * (PLAIN_SALVAGE + (1.0F - PLAIN_SALVAGE) * fletch)));
				}
				float eye = ClassTrees.power(archer, EYE);
				double speed = arrow.getDeltaMovement().length();
				if (eye > 0.0F && speed > 0.0 && arrow.getDeltaMovement().normalize().dot(archer.getLookAngle()) > EYE_OWN) {
					Vec3 line = arrow.getDeltaMovement().normalize().lerp(archer.getLookAngle(), Math.min(1.0F, EYE_TRUER * eye)).normalize();
					arrow.setDeltaMovement(line.scale(speed * (1.0F + EYE_SPEED * eye)));
					arrow.needsSync = true;
				}
			}
		});
		// Fletcher: the arrow that kills isn't broken in the kill
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getOwner() instanceof ServerPlayer archer
				// (with Kitchen the arrow stays in the body and is pulled out whole: no second one is dropped)
				&& !KitchenCompat.corpsesExist()
				&& arrow.pickup == AbstractArrow.Pickup.ALLOWED && entity.level() instanceof ServerLevel level && ClassTrees.has(archer, FLETCH) > 0
				&& level.getRandom().nextFloat() < Math.min(1.0F, ClassTrees.power(archer, FLETCH))) {
				ItemStack back = arrow.getPickupItemStackOrigin().copyWithCount(1);
				if (!back.isEmpty()) {
					level.addFreshEntity(new ItemEntity(level, entity.getX(), entity.getY(0.5), entity.getZ(), back));
				}
			}
		});
	}

	private static boolean bowShot(AbstractArrow arrow) {
		ItemStack weapon = arrow.getWeaponItem();
		return weapon != null && (weapon.getItem() instanceof BowItem || weapon.getItem() instanceof CrossbowItem);
	}

	// ---- Thief ----------------------------------------------------------------------------------------------------------------

	/** Nimble Fingers: what a pocket's hold, or a lock's waits, are multiplied by. Both sides (the lock's screen asks). */
	public static float fingers(Player player) {
		return Math.max(0.5F, 1.0F - FINGERS_PER_RANK * ClassTrees.power(player, FINGERS));
	}

	/** Cutpurse King: whether {@code mark} was made wary by a fumbled pocket and still is. */
	static boolean wary(LivingEntity mark, long now) {
		Long until = mark.getAttached(WARY);
		if (until == null) {
			return false;
		}
		if (until <= now) {
			mark.removeAttached(WARY);
			return false;
		}
		return true;
	}

	/**
	 * Cutpurse King: {@code player}'s hand was felt in {@code mark}'s pocket. True if it ends there: the mark turns and keeps a
	 * hand on their purse for a day, but nobody is told, nothing is remembered against the Thief and no crime is cried.
	 */
	static boolean fumbled(ServerPlayer player, ServerLevel level, LivingEntity mark) {
		if (ClassTrees.has(player, CUTPURSE) <= 0) {
			return false;
		}
		mark.setAttached(WARY, level.getGameTime() + WARY_TICKS);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.extras.wary", mark.getDisplayName()).withStyle(ChatFormatting.GOLD));
		level.playSound(null, mark.getX(), mark.getY(), mark.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.6F, 1.1F);
		return true;
	}

	/** Fence's Friend: what is lifted from a pocket is stolen goods (not coin, nor a thing that carries its own mark, a key). */
	static void steal(ItemStack loot) {
		if (loot.isEmpty() || Coinage.held(loot) > 0L || loot.has(DataComponents.CUSTOM_DATA)) {
			return;
		}
		CustomData.update(DataComponents.CUSTOM_DATA, loot, tag -> tag.putBoolean(STOLEN_TAG, true));
		loot.set(DataComponents.LORE, loot.getOrDefault(DataComponents.LORE, ItemLore.EMPTY)
			.withLineAdded(Component.translatable("tooltip.jaymoskingdoms.stolen").withStyle(ChatFormatting.DARK_RED)));
	}

	static boolean stolen(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(STOLEN_TAG, false);
	}

	/** What a stack of stolen goods is worth in full (milli). */
	static long stolenWorth(ItemStack stack) {
		long counted = (long) (Lockpicking.worth(List.of(stack)) / WORTH_EMERALD * Money.worth(1));
		return Math.max(counted, STOLEN_FLOOR * stack.getCount());
	}

	/** The fence buys the stolen goods in {@code player}'s hand: half their worth from anyone, all of it from a Fence's Friend. */
	static void fence(ServerLevel level, ServerPlayer player, Villager fence) {
		ItemStack goods = player.getMainHandItem();
		float friend = Math.min(1.0F, ClassTrees.power(player, FENCE));
		long pays = Math.max(1L, Math.round(stolenWorth(goods) * (FENCE_PLAIN + (1.0F - FENCE_PLAIN) * friend)));
		Faction law = Theft.lawAt(level, fence.blockPosition());
		// coins come whole: what doesn't make the smallest coin isn't bought (and the goods aren't taken)
		pays -= pays % SMALLEST_COIN;
		if (pays <= 0L) {
			Speech.tell(level, fence, player, Component.translatable("message.jaymoskingdoms.extras.fence_nothing"), Speech.GOLD);
			level.playSound(null, fence.getX(), fence.getY(), fence.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8F, 0.9F);
			return;
		}
		goods.setCount(0);
		Money.pay(player, law, pays);
		Speech.tell(level, fence, player, Component.translatable(friend > 0.0F ? "message.jaymoskingdoms.extras.fence_friend" : "message.jaymoskingdoms.extras.fence_plain",
			Money.text(law, pays)), Speech.GOLD);
		level.playSound(null, fence.getX(), fence.getY(), fence.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8F, 0.9F);
	}

	/** Ghost: whether {@code thief} is out of Vanish and their first blow still to come. */
	static boolean ghostBlow(ServerPlayer thief) {
		Long until = GHOST_UNTIL.get(thief.getUUID());
		return until != null && until >= thief.level().getGameTime() && !GHOST_SPENT.getOrDefault(thief.getUUID(), false);
	}

	/** Ghost: the blow was struck. */
	static void ghostStruck(ServerPlayer thief) {
		if (GHOST_UNTIL.containsKey(thief.getUUID())) {
			GHOST_SPENT.put(thief.getUUID(), true);
		}
	}

	/** Ghost: no blood is left behind while Vanish's cover lasts and the moments after. */
	static boolean bloodless(ServerPlayer thief) {
		Long until = GHOST_UNTIL.get(thief.getUUID());
		return until != null && until >= thief.level().getGameTime();
	}

	private static boolean vanished(ServerPlayer player) {
		if (!lookedForStealth) {
			lookedForStealth = true;
			stealth = BuiltInRegistries.MOB_EFFECT.get(Identifier.fromNamespaceAndPath("rogues", "stealth")).orElse(null);
		}
		return stealth != null && player.hasEffect(stealth);
	}

	// ---- Ranger ---------------------------------------------------------------------------------------------------------------

	/** Field Dressing: more rounds of cuts for {@code player} at a hung carcass, when the nearest one is their own kill. */
	static int dressing(Player player) {
		if (ClassTrees.has(player, DRESS) <= 0) {
			return 0;
		}
		Entity nearest = null;
		for (Entity e : player.level().getEntities(player, player.getBoundingBox().inflate(DRESS_REACH), e -> e.getClass().getName().equals("com.jaymoskitchen.corpse.CorpseEntity"))) {
			if (nearest == null || e.distanceToSqr(player) < nearest.distanceToSqr(player)) {
				nearest = e;
			}
		}
		if (nearest == null) {
			return 0;
		}
		try {
			return (boolean) nearest.getClass().getMethod("killedBy", Entity.class).invoke(nearest, player) ? DRESS_CUTS : 0;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 0;
		}
	}

	/** Fletcher: the share of a set's arrows {@code player} gets over and above it at Jaymo's Powers' Fletching Station (half again: six for four). */
	public static double fletchShare(Player player) {
		return (FLETCH_SET - PLAIN_SET) / (double) PLAIN_SET * Math.min(1.0F, ClassTrees.power(player, FLETCH));
	}

	/** Fletcher: {@code crafted} arrows came out of the grid; the Fletcher's sets are six, so the rest are handed over. */
	public static void fletched(Player player, ItemStack made, int crafted) {
		if (!(player instanceof ServerPlayer sp) || crafted <= 0 || !made.is(Items.ARROW) || ClassTrees.has(sp, FLETCH) <= 0) {
			return;
		}
		int more = Math.round(crafted * (FLETCH_SET - PLAIN_SET) / (float) PLAIN_SET * Math.min(1.0F, ClassTrees.power(sp, FLETCH)));
		if (more <= 0) {
			return;
		}
		ItemStack extra = new ItemStack(Items.ARROW, more);
		if (!sp.getInventory().add(extra) && !extra.isEmpty()) {
			sp.drop(extra, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
	}

	/** Stalker: what a mob's range for noticing {@code player} is multiplied by, from {@code f} ({@link Senses#factor}). */
	static double stalked(ServerPlayer player, Mob looker, boolean still, double f) {
		if (!still || !player.isCrouching() || Senses.boss(looker)) {
			return f;
		}
		float stalker = ClassTrees.power(player, STALKER);
		if (stalker <= 0.0F || !inCover(player)) {
			return f;
		}
		double range = looker.getAttributes().hasAttribute(Attributes.FOLLOW_RANGE) ? looker.getAttributeValue(Attributes.FOLLOW_RANGE) : 16.0;
		return range <= 0.0 ? f : Math.min(f, STALK_RANGE / Math.min(1.0F, stalker) / range);
	}

	/** In grass or leaves: grass, ferns or a bush at the feet or the eyes, or leaves against them. */
	private static boolean inCover(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos feet = player.blockPosition();
		if (grassy(level.getBlockState(feet)) || grassy(level.getBlockState(BlockPos.containing(player.getEyePosition())))) {
			return true;
		}
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-1, -1, -1), feet.offset(1, 1, 1))) {
			if (level.getBlockState(pos).is(BlockTags.LEAVES)) {
				return true;
			}
		}
		return false;
	}

	private static boolean grassy(BlockState state) {
		return state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN) || state.is(Blocks.BUSH)
			|| state.is(Blocks.SWEET_BERRY_BUSH) || state.is(BlockTags.LEAVES);
	}

	/**
	 * Heartseeker (Jaymo's Powers asks, {@link PowersCompat#shareExtras}): a full-drawn arrow from a Heartseeker Ranger at a
	 * favoured foe that never knew they were there can't miss or glance off the head it was called on.
	 */
	static boolean sure(LivingEntity victim, DamageSource source) {
		return source.getDirectEntity() instanceof AbstractArrow arrow && arrow.isCritArrow() && source.getEntity() instanceof ServerPlayer ranger
			&& ClassTrees.has(ranger, HEART) > 0 && Rangers.isFoe(Rangers.foe(ranger), victim) && Senses.unaware(victim, ranger);
	}

	// ---- blows ----------------------------------------------------------------------------------------------------------------

	/** Shadow Blade: a light blade in the back of an unaware weaker foe puts it down. */
	static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (!(source.getEntity() instanceof ServerPlayer thief) || source.getDirectEntity() != thief || target == thief || !(target instanceof Mob mob)) {
			return amount;
		}
		float blade = ClassTrees.power(thief, SHADOW_BLADE);
		if (blade <= 0.0F || !HiddenBlade.weapon(thief).is(ClassPerks.LIGHT_BLADES) || Senses.boss(mob) || Senses.cooling(target, source)
			|| mob.getMaxHealth() > thief.getMaxHealth() * BLADE_WEAKER * Math.min(1.0F, blade) || !Senses.unaware(target, thief) || !Senses.fromRear(target, thief)) {
			return amount;
		}
		if (target.level() instanceof ServerLevel level) {
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F, 0.6F);
		}
		// this is the blow before armour and warding take their share: enough that what is left still fells
		return Math.max(amount, (target.getHealth() + target.getAbsorptionAmount() + 1.0F) * BLADE_THROUGH);
	}

	// ---- every tick -----------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive() || player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
				continue;
			}
			long now = level.getGameTime();
			UUID id = player.getUUID();
			heard(level, player, id, now);
			if (ClassTrees.has(player, GHOST) > 0) {
				if (vanished(player)) {
					// (each Vanish has its one blow)
					if (HIDDEN.add(id)) {
						GHOST_SPENT.put(id, false);
					}
					GHOST_UNTIL.put(id, now + GHOST_TICKS);
				} else {
					HIDDEN.remove(id);
					if (GHOST_UNTIL.getOrDefault(id, Long.MAX_VALUE) < now) {
						GHOST_UNTIL.remove(id);
						GHOST_SPENT.remove(id);
					}
				}
			}
			if ((now + player.getId()) % CASE_EVERY == 0) {
				caseMark(level, player, id);
			}
			if ((now + player.getId()) % WARDEN_EVERY == 0) {
				warden(level, player, id, now);
			}
		}
	}

	/** Second Storey: a landing and a climb are heard, but not a Second Storey Thief's from under six blocks, or their climb. */
	private static void heard(ServerLevel level, ServerPlayer player, UUID id, long now) {
		if (player.isCreative() || player.isPassenger()) {
			FALLING.remove(id);
			return;
		}
		boolean storey = ClassTrees.has(player, STOREY) > 0;
		if (!player.onGround() && !player.isInWater() && !player.onClimbable() && !player.getAbilities().flying && !player.isFallFlying()) {
			if (player.fallDistance > 0.0) {
				FALLING.merge(id, player.fallDistance, Math::max);
			}
		} else {
			Double fell = FALLING.remove(id);
			if (fell != null && fell >= LAND_HEARD && player.onGround() && !(storey && fell < STOREY_FALL)) {
				Hearing.noise(level, player.position(), Math.min(LAND_MOST, LAND_BASE + fell * LAND_PER_BLOCK), id, Hearing.Kind.STEP);
			}
		}
		if ((now + player.getId()) % Footsteps.EVERY == 0) {
			if (player.onClimbable() && !player.onGround()) {
				Double last = CLIMBED.put(id, player.getY());
				if (last != null && Math.abs(player.getY() - last) >= CLIMB_MOVED && !storey && !player.isCrouching()) {
					Hearing.noise(level, player.position(), CLIMB_REACH, id, Hearing.Kind.STEP);
				}
			} else {
				CLIMBED.remove(id);
			}
		}
	}

	/** Case the Mark: sneaking and watching someone, the Thief reads them. */
	private static void caseMark(ServerLevel level, ServerPlayer player, UUID id) {
		if (!player.isCrouching() || ClassTrees.has(player, CASE) <= 0) {
			WATCHING.remove(id);
			return;
		}
		Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
		LivingEntity mark = null;
		double best = CASE_DOT;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(CASE_RANGE), e -> e != player && Pickpocket.markable(e))) {
			Vec3 to = e.getEyePosition().subtract(eye);
			double dot = to.lengthSqr() < 1.0E-4 ? 0.0 : to.normalize().dot(look);
			if (dot > best && player.hasLineOfSight(e)) {
				best = dot;
				mark = e;
			}
		}
		if (mark == null) {
			WATCHING.remove(id);
			return;
		}
		Watch was = WATCHING.get(id);
		int ticks = was != null && was.entity == mark.getId() ? was.ticks + CASE_EVERY : 0;
		WATCHING.put(id, new Watch(mark.getId(), ticks));
		if (ticks >= CASE_TICKS) {
			ServerPlayNetworking.send(player, new Cased(mark.getId(), waryOf(level, mark, player), worthIt(level, mark)));
		}
	}

	/** Whether {@code mark} has their guard up against {@code player}'s hand. */
	private static boolean waryOf(ServerLevel level, LivingEntity mark, ServerPlayer player) {
		if (wary(mark, level.getGameTime()) || Renown.knownRogue(player, Pickpocket.law(level, mark)) || mark instanceof Folk folk && folk.combatant()
			|| Downed.isGuard(mark)) {
			return true;
		}
		if (mark instanceof Villager v && (v.getBrain().hasMemoryValue(MemoryModuleType.IS_PANICKING) || McaCompat.panicking(v))) {
			return true;
		}
		return Recollections.of(mark, player).stream().anyMatch(r -> r.kind().equals(Recollections.Kind.ROBBED.key()));
	}

	/** Whether {@code mark}'s pockets are worth the reach: not picked clean today, and a rich one's, or a villager carrying goods. */
	private static boolean worthIt(ServerLevel level, LivingEntity mark) {
		if (Pickpocket.PICKED.getOrDefault(mark.getUUID(), 0L) > level.getGameTime()) {
			return false;
		}
		return Pickpocket.rich(mark) || mark instanceof Villager v && !v.getInventory().isEmpty();
	}

	/** Warden: a favoured foe near is a heartbeat in the Ranger's ears, quicker and louder the more squarely they face it. */
	private static void warden(ServerLevel level, ServerPlayer player, UUID id, long now) {
		if (ClassTrees.has(player, WARDEN) <= 0) {
			BEAT.remove(id);
			return;
		}
		if (BEAT.getOrDefault(id, 0L) > now) {
			return;
		}
		String kind = Rangers.foe(player);
		double range = WARDEN_RANGE * Math.min(1.0F, ClassTrees.power(player, WARDEN));
		LivingEntity near = null;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
			e -> e != player && e.isAlive() && !(e instanceof Player) && Rangers.isFoe(kind, e))) {
			if (e.distanceToSqr(player) <= range * range && (near == null || e.distanceToSqr(player) < near.distanceToSqr(player))) {
				near = e;
			}
		}
		if (near == null) {
			BEAT.put(id, now + WARDEN_EVERY * 5L);
			return;
		}
		Vec3 to = near.getEyePosition().subtract(player.getEyePosition());
		float facing = to.lengthSqr() < 1.0E-4 ? 1.0F : (float) Mth.clamp((to.normalize().dot(player.getLookAngle()) + 1.0) / 2.0, 0.0, 1.0);
		facing *= facing;
		BEAT.put(id, now + Math.round(Mth.lerp(facing, WARDEN_SLOW, WARDEN_FAST)));
		player.connection.send(new ClientboundSoundPacket(Holder.direct(SoundEvents.WARDEN_HEARTBEAT), SoundSource.PLAYERS, player.getX(), player.getEyeY(), player.getZ(),
			Mth.lerp(facing, WARDEN_QUIET, 1.0F), Mth.lerp(facing, 0.8F, 1.1F), player.getRandom().nextLong()));
	}
}
