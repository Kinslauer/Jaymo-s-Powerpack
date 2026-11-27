package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.dino.Dino;
import com.jaymospowers.dino.entity.Dinosaur;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The barbaric Warrior-born of the Primordial Caves (Andy, 2026-11-24: "ancient warrior born still wrestle the dinos,
 * like they have forever", and "there should also be barbaric warrior born found training by fighting the dinos";
 * Lore.md, "The old fighters under the ferns"). Now and then, while a player is down in Alex's Primordial Caves
 * ({@link Dino#PRIMORDIAL_CAVES}), a band of two to four comes out beside one of the beasts there (ours, {@link Dinosaur},
 * or Alex's) and fights it bare-handed: the Z Fighter rogue's strings ({@link ZFighterRogue}), in odd pieces of
 * caveman gear and hide ({@link #dress}), with a Warrior-born's tail.
 * <p>
 * They train, they don't hunt: a beast beaten down to {@link #SPARE} of its health is let go, so a band never empties
 * the caves of the herds it trains on. Nobody's tamed beast is touched. They leave players alone unless one walks
 * right up to them ({@link #CLOSE}) or strikes one. A band is only kept while it's seen: the mark that makes one of
 * them a cave fighter ({@link #BRUTE}) isn't saved, so after a reload the rogue's own rule clears them away, and a
 * fresh band comes the next time someone is down there.
 */
public final class ZFighterCaveBand {
	// ---- tuning ----
	/** How often (ticks) a player in the caves may draw a band, and the chance each time. */
	static final int COME_EVERY = 200;
	static final float COME_CHANCE = 0.2F;
	static final int BAND_MIN = 2, BAND_MAX = 4;
	/** Blocks: a band near the player (no second one comes while one is), and where a band's beast may be. */
	static final double NEAR = 64.0, BEAST_RANGE = 40.0, BEAST_MIN = 14.0;
	/** Blocks: how far a fighter looks for his next beast, and how close a player may come before he turns on them. */
	static final double TRAIN_RANGE = 24.0, CLOSE = 4.0;
	/** A beast at or under this share of its health is let go. */
	static final float SPARE = 0.3F;
	/** Health and blows against a raider's (a champion's base: 80 health, 2 damage). */
	static final double HEALTH = 120.0, DAMAGE = 2.6;
	static final String TAG = "jaymospowers.cave_brute";
	/** Head, chest, legs, feet: the chance of the caveman piece, then of a plain leather one; otherwise bare. */
	static final EquipmentSlot[] WORN = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	static final float[][] WEAR = {{0.3F, 0.1F}, {0.4F, 0.2F}, {0.6F, 0.4F}, {0.4F, 0.2F}};
	static final String[] CAVEMAN = {"caveman_helmet", "caveman_chestplate", "caveman_leggings", "caveman_boots"};
	static final net.minecraft.world.item.Item[] LEATHER = {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS};
	/** Alex's Caves' primordial beasts, by id (nothing of Alex's is needed to load). */
	static final Set<String> CAVE_BEASTS = Set.of("alexscaves:grottoceratops", "alexscaves:vallumraptor", "alexscaves:tremorsaurus",
		"alexscaves:relicheirus", "alexscaves:atlatitan", "alexscaves:subterranodon");

	/** A cave fighter (shown to clients for his tail; not saved, see the class note). */
	public static final AttachmentType<Boolean> BRUTE = AttachmentRegistry.create(JaymosPowers.id("cave_brute"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	private ZFighterCaveBand() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterCaveBand::tick);
	}

	public static boolean brute(Entity entity) {
		return Boolean.TRUE.equals(entity.getAttached(BRUTE));
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		ServerLevel level = server.getLevel(Level.OVERWORLD);
		if (level == null) {
			return;
		}
		boolean draw = server.getTickCount() % COME_EVERY == 0;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !level.getBiome(player.blockPosition()).is(Dino.PRIMORDIAL_CAVES)) {
				continue;
			}
			List<ZFighterRogue> band = level.getEntitiesOfClass(ZFighterRogue.class, player.getBoundingBox().inflate(NEAR), ZFighterCaveBand::brute);
			for (ZFighterRogue brute : band) {
				train(level, brute);
			}
			if (band.isEmpty() && draw && level.getRandom().nextFloat() < COME_CHANCE) {
				come(level, player);
			}
		}
	}

	/** A beast a band may train on: one of the caves' (ours or Alex's), alive, nobody's pet. */
	static boolean beast(Entity e) {
		if (!(e instanceof LivingEntity living) || !living.isAlive() || e instanceof TamableAnimal pet && pet.isTame()) {
			return false;
		}
		return e instanceof Dinosaur || CAVE_BEASTS.contains(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
	}

	/** Still worth a fight: not yet beaten down to {@link #SPARE}. */
	private static boolean fresh(LivingEntity beast) {
		return beast.getHealth() > beast.getMaxHealth() * SPARE;
	}

	/** A band comes out beside a beast near the player, and goes for it. */
	private static void come(ServerLevel level, ServerPlayer player) {
		RandomSource random = level.getRandom();
		List<LivingEntity> beasts = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(BEAST_RANGE),
			e -> beast(e) && fresh(e) && e.distanceTo(player) > BEAST_MIN);
		if (beasts.isEmpty()) {
			return;
		}
		LivingEntity beast = beasts.get(random.nextInt(beasts.size()));
		int count = BAND_MIN + random.nextInt(BAND_MAX - BAND_MIN + 1);
		for (int i = 0; i < count; i++) {
			BlockPos at = ground(level, beast.blockPosition().offset(random.nextInt(11) - 5, 0, random.nextInt(11) - 5));
			if (at == null) {
				continue;
			}
			ZFighterRogue brute = ZFighterRaiders.raider(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, false, 0, TAG, ZFighterCaveBand::dress);
			if (brute != null) {
				brute.setTarget(beast);
			}
		}
	}

	/** Standing room near {@code near}: a sturdy floor with two blocks of air above, a few blocks up or down. */
	private static @Nullable BlockPos ground(ServerLevel level, BlockPos near) {
		for (int dy = 3; dy >= -5; dy--) {
			BlockPos at = near.above(dy);
			BlockPos floor = at.below();
			if (level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP) && level.getBlockState(at).getCollisionShape(level, at).isEmpty()
				&& level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty() && level.getFluidState(at).isEmpty()) {
				return at;
			}
		}
		return null;
	}

	/**
	 * Primitive hide, never metal (Andy: "not all shirtless but all in primitive hide", "they can use the caveman gear as
	 * well as hide pieces"): each piece is the Armory's caveman gear, plain leather or nothing, rolled by
	 * {@link #WEAR}, so no two look the same; legs are always covered. Then a cave fighter's health and blows, his name, his mark.
	 */
	private static void dress(ZFighterRogue brute) {
		brute.setAttached(BRUTE, true);
		RandomSource random = brute.getRandom();
		for (int i = 0; i < WORN.length; i++) {
			float roll = random.nextFloat();
			ItemStack piece = roll < WEAR[i][0] ? caveman(CAVEMAN[i]) : roll < WEAR[i][0] + WEAR[i][1] ? new ItemStack(LEATHER[i]) : ItemStack.EMPTY;
			if (piece.isEmpty() && WORN[i] == EquipmentSlot.LEGS) {
				piece = new ItemStack(LEATHER[i]);
			}
			brute.setItemSlot(WORN[i], piece);
			brute.setDropChance(WORN[i], 0.0F);
		}
		brute.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(HEALTH);
		brute.setHealth((float)HEALTH);
		brute.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(DAMAGE);
		brute.setCustomName(Component.translatable("entity.jaymospowers.cave_brute"));
	}

	private static ItemStack caveman(String id) {
		return BuiltInRegistries.ITEM.getOptional(JaymosPowers.id(id)).map(ItemStack::new).orElse(ItemStack.EMPTY);
	}

	/** Every second: a player who comes right up is fought; a beaten beast is let go; a free fighter finds the next beast. */
	private static void train(ServerLevel level, ZFighterRogue brute) {
		LivingEntity target = brute.getTarget();
		if (target instanceof Player && target.isAlive()) {
			return;
		}
		Player close = level.getNearestPlayer(brute.getX(), brute.getY(), brute.getZ(), CLOSE,
			e -> e instanceof Player p && !p.isCreative() && !p.isSpectator());
		if (close != null) {
			brute.setTarget(close);
			return;
		}
		if (target != null && target.isAlive() && beast(target) && fresh(target)) {
			return;
		}
		LivingEntity next = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, brute.getBoundingBox().inflate(TRAIN_RANGE), e -> beast(e) && fresh(e))) {
			double d = brute.distanceToSqr(e);
			if (d < best) {
				best = d;
				next = e;
			}
		}
		brute.setTarget(next);
	}
}
