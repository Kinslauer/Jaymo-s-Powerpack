package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.58, Andy's NOEA picks) Fighter weapons. Fists first ({@link ZFighterBattle}) makes a Z Fighter's weapon blows weak
 * (a share of what they would be) unless his Kingdoms class is trained with it; these three are his own, so they count as
 * fists ({@link #fistLike}) and hit at full strength:
 * <ul>
 * <li><b>Telescoping Staff</b>: hold right click and the staff runs out, its reach growing {@link #STAFF_PER_TICK} a tick up to
 * {@link #STAFF_MAX} extra blocks (a light ring of motes marks its length); let go and it holds its length for
 * {@link #STAFF_HOLD} ticks (long enough to swing) and then draws back. Iron tough, a quick light swing.</li>
 * <li><b>Titanforged Greatsword</b>: the legendary one. Rare loot (end cities, ancient cities, bastion treasure: about
 * {@link #LEGENDARY_CHANCE} a chest at most). Hits for 12 and swings slowly; carrying it in your main hand weighs you down
 * ({@link #LEGENDARY_SLOW} speed).</li>
 * <li><b>Hero's Blade</b>: craftable. A diamond blade that keeps its edge in the dark: under {@link #COURAGE} of your health,
 * held in the main hand, it fills you with Strength.</li>
 * </ul>
 */
public final class ZFighterWeapons {
	/** The staff: extra reach a tick of hold and at most (blocks), how long it stays out after release and how fast it draws back (ticks, blocks a tick). */
	static final float STAFF_PER_TICK = 0.12F, STAFF_MAX = 5.0F;
	static final int STAFF_HOLD = 60;
	static final float STAFF_BACK = 0.25F;
	/** The greatsword's weight: a speed modifier while it is in the main hand; the chance a rich chest holds one. */
	static final double LEGENDARY_SLOW = -0.12;
	static final float LEGENDARY_CHANCE = 0.05F;
	/** The Hero's Blade gives Strength below this share of health, refreshed this long (ticks). */
	static final float COURAGE = 0.4F;
	static final int COURAGE_TICKS = 40;

	private static final Identifier REACH_ID = JaymosPowers.id("telescoping_staff_reach");
	private static final Identifier WEIGHT_ID = JaymosPowers.id("greatsword_weight");

	public static Item STAFF, LEGENDARY_SWORD, HERO_BLADE;

	/** How far out each player's staff is, and when it was last held out (game time). */
	private record Out(float reach, long since) {
	}

	private static final Map<UUID, Out> OUT = new HashMap<>();

	private ZFighterWeapons() {
	}

	static void init() {
		Consumable hold = Consumable.builder().consumeSeconds(3600.0F).animation(ItemUseAnimation.BLOCK).hasConsumeParticles(false)
			.sound(Holder.direct(SoundEvents.AMETHYST_BLOCK_STEP)).build();
		ResourceKey<Item> staffKey = key("telescoping_staff");
		STAFF = Registry.register(BuiltInRegistries.ITEM, staffKey, new Item(new Item.Properties().setId(staffKey)
			.sword(ToolMaterial.IRON, 2.0F, -2.0F).rarity(Rarity.UNCOMMON).component(DataComponents.CONSUMABLE, hold)));
		ToolMaterial titan = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 4000, 8.0F, 0.0F, 20, ItemTags.NETHERITE_TOOL_MATERIALS);
		ResourceKey<Item> swordKey = key("titanforged_greatsword");
		LEGENDARY_SWORD = Registry.register(BuiltInRegistries.ITEM, swordKey, new Item(new Item.Properties().setId(swordKey)
			.sword(titan, 11.0F, -3.3F).rarity(Rarity.EPIC).fireResistant()));
		ResourceKey<Item> bladeKey = key("heros_blade");
		HERO_BLADE = Registry.register(BuiltInRegistries.ITEM, bladeKey, new Item(new Item.Properties().setId(bladeKey)
			.sword(ToolMaterial.DIAMOND, 4.0F, -2.0F).rarity(Rarity.RARE)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.accept(STAFF);
			output.accept(HERO_BLADE);
			output.accept(LEGENDARY_SWORD);
		});
		LootTableEvents.MODIFY.register((table, builder, source, registries) -> {
			if (source.isBuiltin() && (table.equals(BuiltInLootTables.END_CITY_TREASURE) || table.equals(BuiltInLootTables.ANCIENT_CITY)
				|| table.equals(BuiltInLootTables.BASTION_TREASURE))) {
				builder.withPool(LootPool.lootPool().when(LootItemRandomChanceCondition.randomChance(LEGENDARY_CHANCE)).add(LootItem.lootTableItem(LEGENDARY_SWORD)));
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(ZFighterWeapons::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> OUT.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> OUT.clear());
	}

	private static ResourceKey<Item> key(String name) {
		return ResourceKey.create(Registries.ITEM, JaymosPowers.id(name));
	}

	/** Fists first: these weapons are part of the fighter's own way and hit in full (see {@link ZFighterBattle#isWeapon}). */
	static boolean fistLike(ItemStack stack) {
		return !stack.isEmpty() && (stack.is(STAFF) || stack.is(LEGENDARY_SWORD) || stack.is(HERO_BLADE));
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive()) {
				continue;
			}
			ItemStack main = player.getMainHandItem();
			// the greatsword weighs on the arm that carries it
			weigh(player, main.is(LEGENDARY_SWORD) ? LEGENDARY_SLOW : 0.0);
			if (main.is(HERO_BLADE) && player.getHealth() <= player.getMaxHealth() * COURAGE && now % 10 == 0) {
				player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, COURAGE_TICKS, 0, true, false));
				player.level().sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.2, player.getZ(), 4, 0.3, 0.5, 0.3, 0.1);
			}
			staff(player, now);
		}
	}

	private static void weigh(ServerPlayer player, double amount) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		AttributeModifier now = speed.getModifier(WEIGHT_ID);
		if (amount == 0.0) {
			if (now != null) {
				speed.removeModifier(WEIGHT_ID);
			}
		} else if (now == null || now.amount() != amount) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(WEIGHT_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	/** The staff's reach: grows while he holds it out, stays a while after, then draws back. */
	private static void staff(ServerPlayer player, long now) {
		boolean held = player.getMainHandItem().is(STAFF) || player.getOffhandItem().is(STAFF);
		Out out = OUT.get(player.getUUID());
		float reach = out == null ? 0.0F : out.reach();
		long since = out == null ? now : out.since();
		if (held && player.isUsingItem() && player.getUseItem().is(STAFF)) {
			reach = Math.min(STAFF_MAX, reach + STAFF_PER_TICK);
			since = now;
		} else if (!held) {
			reach = 0.0F;
		} else if (now - since > STAFF_HOLD) {
			reach = Math.max(0.0F, reach - STAFF_BACK);
		}
		if (reach <= 0.0F && out == null) {
			return;
		}
		if (reach <= 0.0F) {
			OUT.remove(player.getUUID());
		} else {
			OUT.put(player.getUUID(), new Out(reach, since));
		}
		AttributeInstance range = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
		if (range != null) {
			if (reach <= 0.0F) {
				range.removeModifier(REACH_ID);
			} else {
				range.addOrUpdateTransientModifier(new AttributeModifier(REACH_ID, reach, AttributeModifier.Operation.ADD_VALUE));
			}
		}
		if (reach > 0.5F && now % 4 == 0 && player.level() instanceof ServerLevel level) {
			Vec3 look = player.getLookAngle();
			Vec3 from = player.getEyePosition().add(look.scale(0.8)).add(0.0, -0.3, 0.0);
			double length = 2.5 + reach;
			for (double d = 0.0; d <= length; d += 1.0) {
				Vec3 at = from.add(look.scale(d));
				level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
	}
}
