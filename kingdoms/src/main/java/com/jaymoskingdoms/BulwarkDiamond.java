package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.477) Andy (2026-11-04): "i like the diamond golem's functionality, but id rather see it be an imbued item rather
 * than a golem". Special Golems' diamond golem (ideas only) sits on its owner's word and takes the blows meant for its
 * allies. Ours is the <b>Bulwark Diamond</b>, a diamond imbued on the infusion altar (Golemancy):
 * <ul>
 * <li>Use it to raise its ward (use again to lower it). While it's up and the diamond is in hand, you are rooted like a
 * sitting golem (barely able to walk), and every blow aimed at an ally within {@link #RANGE} blocks (your party, your pets
 * and golems, the folk of any town) is caught by the diamond instead: it drains the diamond's ward, shown as its bar
 * ({@link #POOL} points, one per point of harm). You yourself are not covered: you are the wall.</li>
 * <li>Spent dry, the ward breaks and drops. It fills again while the ward is down: {@link #REFILL} a second on its own,
 * {@link #MANA_REFILL} more drawing on its bearer's mana if they're attuned.</li>
 * </ul>
 * Only blows from something (a mob, a player, an arrow, a blast) are caught, not falls, drowning or hunger.
 */
public final class BulwarkDiamond extends Item {
	public static Item ITEM;

	/** The ward's depth (points of harm), its reach (blocks), and the bearer's slowing while it's up. */
	static final int POOL = 500;
	static final double RANGE = 8.0;
	static final double ROOTED = -0.85;
	/** Points refilled a second while the ward is down; more with mana, and what that costs. */
	static final int REFILL = 1, MANA_REFILL = 4;
	static final float MANA_COST = 0.5F;

	static final net.minecraft.resources.Identifier ROOT_ID = JaymosKingdoms.id("bulwark_rooted");
	/** Who holds a ward up, and in which hand. */
	private static final Map<UUID, InteractionHand> WARDING = new HashMap<>();

	BulwarkDiamond(Item.Properties properties) {
		super(properties);
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("bulwark_diamond"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, key, new BulwarkDiamond(new Item.Properties().setId(key).stacksTo(1)
			.durability(POOL).rarity(Rarity.RARE)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.accept(ITEM));
		ServerTickEvents.END_SERVER_TICK.register(BulwarkDiamond::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(BulwarkDiamond::catchBlow);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (WARDING.containsKey(sp.getUUID())) {
			lower(sp, false);
		} else if (left(stack) <= 0) {
			sp.sendSystemMessage(Component.translatable("item.jaymoskingdoms.bulwark_diamond.dim"), true);
			return InteractionResult.FAIL;
		} else {
			WARDING.put(sp.getUUID(), hand);
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			AttributeInstance speed = sp.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null && !speed.hasModifier(ROOT_ID)) {
				speed.addTransientModifier(new AttributeModifier(ROOT_ID, ROOTED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
			level.playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.6F);
			sp.sendSystemMessage(Component.translatable("item.jaymoskingdoms.bulwark_diamond.up"), true);
		}
		return InteractionResult.SUCCESS;
	}

	/** What's left of its ward. */
	static int left(ItemStack stack) {
		return stack.getMaxDamage() - stack.getDamageValue();
	}

	/** Lowers {@code player}'s ward ({@code broke}: spent dry). */
	static void lower(ServerPlayer player, boolean broke) {
		InteractionHand hand = WARDING.remove(player.getUUID());
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(ROOT_ID);
		}
		for (InteractionHand h : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(h);
			if (stack.is(ITEM)) {
				stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
			}
		}
		if (hand != null) {
			player.level().playSound(null, player.blockPosition(), broke ? SoundEvents.AMETHYST_CLUSTER_BREAK : SoundEvents.AMETHYST_BLOCK_CHIME,
				SoundSource.PLAYERS, 1.0F, broke ? 0.7F : 1.0F);
			if (broke) {
				player.sendSystemMessage(Component.translatable("item.jaymoskingdoms.bulwark_diamond.broke"), true);
			}
		}
	}

	private static void tick(MinecraftServer server) {
		java.util.List<ServerPlayer> done = new java.util.ArrayList<>();
		for (Iterator<Map.Entry<UUID, InteractionHand>> it = WARDING.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, InteractionHand> e = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null) {
				it.remove();
			} else if (!p.isAlive() || !p.getItemInHand(e.getValue()).is(ITEM)) {
				done.add(p);
			} else if (p.tickCount % 10 == 0) {
				ring((ServerLevel)p.level(), p);
			}
		}
		for (ServerPlayer p : done) {
			lower(p, false);
		}
	}

	/** A faint ring of diamond motes round a warder, showing the ward's reach. */
	private static void ring(ServerLevel level, ServerPlayer p) {
		for (int i = 0; i < 16; i++) {
			double a = (p.tickCount / 10 + i) * (Math.PI * 2 / 16);
			level.sendParticles(new DustParticleOptions(0x7FEFE0, 1.0F), p.getX() + Math.cos(a) * RANGE, p.getY() + 0.2,
				p.getZ() + Math.sin(a) * RANGE, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity holder, @Nullable EquipmentSlot slot) {
		if (!(holder instanceof ServerPlayer p) || WARDING.containsKey(p.getUUID()) || stack.getDamageValue() <= 0 || p.tickCount % 20 != 0) {
			return;
		}
		int fill = REFILL;
		if (Mana.attuned(p) && Mana.canPay(p, MANA_COST)) {
			Mana.drain(p, MANA_COST);
			fill += MANA_REFILL;
		}
		stack.setDamageValue(Math.max(0, stack.getDamageValue() - fill));
	}

	/** A blow aimed at {@code victim}: a warder near them catches it in their diamond. */
	private static boolean catchBlow(LivingEntity victim, DamageSource source, float amount) {
		if (WARDING.isEmpty() || amount <= 0.0F || !(victim.level() instanceof ServerLevel level) || !aimed(source)) {
			return true;
		}
		for (Map.Entry<UUID, InteractionHand> e : WARDING.entrySet()) {
			ServerPlayer warder = level.getServer().getPlayerList().getPlayer(e.getKey());
			if (warder == null || warder == victim || warder.level() != level || warder.distanceToSqr(victim) > RANGE * RANGE
				|| source.getEntity() == warder || !ally(warder, victim)) {
				continue;
			}
			ItemStack stack = warder.getItemInHand(e.getValue());
			if (!stack.is(ITEM) || left(stack) <= 0) {
				continue;
			}
			int cost = Math.max(1, Math.round(amount));
			stack.setDamageValue(Math.min(stack.getMaxDamage(), stack.getDamageValue() + cost));
			line(level, victim, warder);
			level.playSound(null, victim.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.0F, 0.6F + level.getRandom().nextFloat() * 0.3F);
			if (left(stack) <= 0) {
				// (the loop ends here, so the map can change)
				lower(warder, true);
			}
			return false;
		}
		return true;
	}

	/** Only blows from something are caught: a mob's, a player's, a shot, a blast. */
	private static boolean aimed(DamageSource source) {
		return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
			&& (source.getEntity() != null || source.getDirectEntity() != null || source.is(DamageTypeTags.IS_EXPLOSION));
	}

	/** {@code victim} is one {@code warder} shields: their party, their pets and golems, a town's folk. */
	static boolean ally(ServerPlayer warder, LivingEntity victim) {
		if (victim instanceof ServerPlayer other) {
			return Parties.allied(warder, other);
		}
		if (victim instanceof OwnableEntity pet && pet.getOwnerReference() != null && warder.getUUID().equals(pet.getOwnerReference().getUUID())) {
			return true;
		}
		if (victim instanceof TownGolem golem) {
			return warder.getUUID().equals(golem.owner()) || golem.faction() >= 0;
		}
		return Deeds.friendly(victim);
	}

	private static void line(ServerLevel level, LivingEntity from, ServerPlayer to) {
		Vec3 a = from.position().add(0, from.getBbHeight() * 0.5, 0), b = to.position().add(0, 1.2, 0);
		Vec3 step = b.subtract(a);
		int n = Math.max(4, (int)(step.length() * 3));
		for (int i = 0; i <= n; i++) {
			Vec3 p = a.add(step.scale(i / (double)n));
			level.sendParticles(new DustParticleOptions(0x7FEFE0, 1.0F), p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, a.x, a.y, a.z, 6, 0.2, 0.2, 0.2, 0.05);
	}
}
