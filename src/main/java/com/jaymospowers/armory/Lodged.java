package com.jaymospowers.armory;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.finisher.Finishers;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;

/**
 * (0.18.118, Motion patch 8) Stuck weapons. Andy: "You can throw daggers at them or sometimes the run through
 * animation will leave your sword stuck in them because you are less experienced, axes get stuck with higher frequencies
 * but both low."
 * <ul>
 * <li>Some zombies rise with a weapon stuck in them ({@link #ZOMBIE_CHANCE}): on first sight of a player they wrench it
 * out and fight with it, and it drops when they die.</li>
 * <li>A thrown dagger that strikes a zombie and doesn't kill it stays in.</li>
 * <li>Whatever is stuck in a living body ({@link #LODGED}, shown to all) is pulled free by an empty hand's use, which
 * hurts them; it drops where they die.</li>
 * <li>A finisher's killing stroke can leave your weapon stuck in the body: Run Through {@link #RUN_THROUGH}, Stabbed
 * Through {@link #STAB_THROUGH}, Executioner {@link #EXECUTIONER}, each shrinking with the bladed kills you've made
 * ({@link #KILLS}). It stays standing in the body until you go and take it.</li>
 * </ul>
 */
public final class Lodged {
	/** Most weapons stuck in one body. */
	static final int MOST = 3;
	/** A natural zombie's chance to rise with a weapon in it. */
	static final float ZOMBIE_CHANCE = 0.07F;
	/** What's stuck in them: worn down to between these (of its durability). */
	static final float WORN_MIN = 0.3F, WORN_MAX = 0.8F;
	/** How often zombies with a weapon in them look about (ticks). */
	static final int EVERY = 10;
	/** Pulling one free hurts them this much. */
	static final float PULL_HURT = 2.0F;
	/** Chances a killing stroke leaves the weapon in them, by finisher, before experience. */
	static final float RUN_THROUGH = 0.05F, STAB_THROUGH = 0.08F, EXECUTIONER = 0.12F;
	/** The chance halves after this many bladed finisher kills, thirds after twice as many, and so on. */
	static final int EXPERIENCE = 12;
	/** What zombies rise with in them. */
	static final String[] ZOMBIE_WEAPONS = {"minecraft:iron_sword", "minecraft:stone_sword", "minecraft:iron_axe", "minecraft:stone_axe",
		"jaymospowers:iron_dagger", "jaymospowers:flint_hatchet", "minecraft:golden_sword"};

	/** What's stuck in a body (shown to everyone). */
	public static final AttachmentType<List<ItemStack>> LODGED = AttachmentRegistry.create(JaymosPowers.id("lodged"),
		builder -> builder.persistent(ItemStack.CODEC.listOf()).syncWith(ItemStack.OPTIONAL_LIST_STREAM_CODEC, AttachmentSyncPredicate.all()));
	/** A zombie that will wrench out what's in it when it sees a player. */
	public static final AttachmentType<Boolean> DRAWS = AttachmentRegistry.create(JaymosPowers.id("lodged_draws"),
		builder -> builder.persistent(Codec.BOOL));
	/** A player's bladed finisher kills (experience against getting stuck). */
	public static final AttachmentType<Integer> KILLS = AttachmentRegistry.create(JaymosPowers.id("blade_kills"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());

	private static final Set<Mob> DRAWING = Collections.newSetFromMap(new WeakHashMap<>());

	private Lodged() {
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && mob.hasAttached(DRAWS)) {
				DRAWING.add(mob);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> DRAWING.remove(entity));
		ServerTickEvents.END_SERVER_TICK.register(Lodged::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((dead, source) -> {
			List<ItemStack> stuck = dead.getAttached(LODGED);
			if (stuck != null && dead.level() instanceof ServerLevel level) {
				dead.removeAttached(LODGED);
				stuck.forEach(stack -> dead.spawnAtLocation(level, stack.copy()));
			}
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty() || player.isSpectator()
				|| !(entity instanceof LivingEntity body) || !body.isAlive() || stuck(body).isEmpty()) {
				return InteractionResult.PASS;
			}
			if (world instanceof ServerLevel level) {
				pull(level, player, body);
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** What's stuck in {@code body}. */
	public static List<ItemStack> stuck(LivingEntity body) {
		List<ItemStack> stuck = body.getAttached(LODGED);
		return stuck == null ? List.of() : stuck;
	}

	/** Sticks {@code stack} in {@code body}, if there's room. */
	public static boolean lodge(LivingEntity body, ItemStack stack) {
		List<ItemStack> stuck = new ArrayList<>(stuck(body));
		if (stuck.size() >= MOST || stack.isEmpty()) {
			return false;
		}
		stuck.add(stack.copyWithCount(1));
		body.setAttached(LODGED, List.copyOf(stuck));
		return true;
	}

	private static ItemStack takeLast(LivingEntity body) {
		List<ItemStack> stuck = new ArrayList<>(stuck(body));
		if (stuck.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack out = stuck.removeLast();
		if (stuck.isEmpty()) {
			body.removeAttached(LODGED);
		} else {
			body.setAttached(LODGED, List.copyOf(stuck));
		}
		return out;
	}

	/** {@code player} pulls the last weapon stuck in {@code body} free. */
	private static void pull(ServerLevel level, Player player, LivingEntity body) {
		ItemStack out = takeLast(body);
		if (out.isEmpty()) {
			return;
		}
		player.setItemInHand(InteractionHand.MAIN_HAND, out);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		body.hurtServer(level, level.damageSources().playerAttack(player), PULL_HURT);
		level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.8F, 0.7F);
		level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, body.getX(), body.getY(0.6), body.getZ(), 3, 0.2, 0.2, 0.2, 0.1);
		if (body instanceof Mob mob) {
			mob.removeAttached(DRAWS);
		}
	}

	/** A thrown weapon struck {@code body} and left it alive: a dagger in a zombie stays in. */
	static boolean thrownIn(LivingEntity body, ItemStack weapon) {
		return body instanceof Zombie && body.isAlive() && dagger(weapon) && lodge(body, weapon);
	}

	static boolean dagger(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("dagger");
	}

	/** Called as a mob finishes spawning: a natural zombie may rise with a weapon in it. */
	public static void spawned(Mob mob, EntitySpawnReason reason, RandomSource random) {
		if (!(mob instanceof Zombie) || reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION
			|| !mob.getMainHandItem().isEmpty() || random.nextFloat() >= ZOMBIE_CHANCE) {
			return;
		}
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(ZOMBIE_WEAPONS[random.nextInt(ZOMBIE_WEAPONS.length)]));
		if (item == Items.AIR) {
			item = Items.IRON_SWORD;
		}
		ItemStack stack = new ItemStack(item);
		if (stack.isDamageableItem()) {
			stack.setDamageValue((int)(stack.getMaxDamage() * (WORN_MIN + random.nextFloat() * (WORN_MAX - WORN_MIN))));
		}
		if (lodge(mob, stack)) {
			mob.setAttached(DRAWS, true);
			DRAWING.add(mob);
		}
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0 || DRAWING.isEmpty()) {
			return;
		}
		for (Mob mob : List.copyOf(DRAWING)) {
			if (!mob.isAlive() || !mob.hasAttached(DRAWS) || !(mob.level() instanceof ServerLevel level)) {
				DRAWING.remove(mob);
				continue;
			}
			if (mob.getTarget() instanceof Player target && mob.hasLineOfSight(target) && mob.getMainHandItem().isEmpty()) {
				draw(level, mob);
			}
		}
	}

	/** It wrenches the weapon out of itself and takes it up. */
	private static void draw(ServerLevel level, Mob mob) {
		mob.removeAttached(DRAWS);
		DRAWING.remove(mob);
		ItemStack out = takeLast(mob);
		if (out.isEmpty()) {
			return;
		}
		mob.setItemSlot(EquipmentSlot.MAINHAND, out);
		// (it drops when they die, for you to take up)
		mob.setDropChance(EquipmentSlot.MAINHAND, 2.0F);
		mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.TRIDENT_RETURN, SoundSource.HOSTILE, 0.9F, 0.6F);
		level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, mob.getX(), mob.getY(0.6), mob.getZ(), 4, 0.2, 0.2, 0.2, 0.1);
	}

	/**
	 * A finisher's killing stroke landed: the blade may stay in the body, left standing in it where it falls, until
	 * {@code player} takes it.
	 */
	public static void killingStroke(ServerLevel level, ServerPlayer player, LivingEntity body, Finishers.Kind kind) {
		float chance = switch (kind) {
			case SWORD -> RUN_THROUGH;
			case IMPALE -> STAB_THROUGH;
			case AXE -> EXECUTIONER;
			default -> 0.0F;
		};
		ItemStack weapon = player.getMainHandItem();
		if (chance <= 0.0F || weapon.isEmpty() || player.isCreative()) {
			return;
		}
		int kills = player.getAttachedOrElse(KILLS, 0);
		player.setAttached(KILLS, kills + 1);
		if (level.getRandom().nextFloat() >= chance / (1.0F + kills / (float) EXPERIENCE)) {
			return;
		}
		ThrownWeapon stuck = new ThrownWeapon(level, player, weapon.copy());
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		stuck.pickup = AbstractArrow.Pickup.ALLOWED;
		stuck.spent();
		stuck.setPos(body.getX(), body.getY() + body.getBbHeight() * 0.5 + 0.6, body.getZ());
		stuck.setDeltaMovement(0.0, -1.0, 0.0);
		level.addFreshEntity(stuck);
		level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.9F, 0.6F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.lodged.stuck", weapon.getHoverName()).withStyle(ChatFormatting.GRAY));
	}
}
