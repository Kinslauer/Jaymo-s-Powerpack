package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.core.Holder;
import org.jspecify.annotations.Nullable;

/**
 * They use their own pack well (docs/Pawn-Plan.md row F4; after the pawns of Dragon's Dogma, a commercial game, who
 * heal at sensible points with the cheapest cure first: ideas only).
 * <ul>
 * <li><b>Friend.</b> A companion out of a fight and badly hurt eats or bandages from its own pack (a villager's
 * inventory), the cheapest first, and spoiled food only when there's nothing else. It hands you food when you're
 * starving and binds you when you're badly hurt, from the same pack. After a fight it picks up the arrows lying about
 * and hands them to you if you shoot. It wears the better thing you hand it (use a weapon or a piece of armour on it),
 * gives you back what it had, and says so; a worse one it gives back.</li>
 * <li><b>Foe.</b> A band member (a grown fighter, never a child or a townsman with nothing to fight with) carries one real healing draught in its pack (a villager's or pillager's inventory, a
 * folk's belt pouch), put there the first time it's seen and again once a day while it's out of a fight if it has none
 * ({@link #DRAUGHT}). {@link BreakOff} drinks it when the member falls back badly hurt; once drunk it's gone until the
 * next day's restock, and it can be looted like anything else it carries.</li>
 * </ul>
 */
public final class PackUse {
	/** Looked at this often (ticks); badly hurt below this share of health; starving below this food. */
	static final int EVERY = 20;
	static final float HURT = 0.5F;
	static final int STARVING = 6;
	/** A companion tends itself or you at most this often (ticks); arrows are picked up this near (blocks). */
	static final long TEND_GAP = 600L;
	static final double ARROWS_NEAR = 8.0;
	/** Band members this near a player are given their draught (blocks). */
	static final double DRAUGHT_NEAR = 32.0;
	/** Bandages, cheapest first, and what each binds (health). */
	static final String[] BANDAGES = {"jaymospowers:grass_bandage", "jaymospowers:rag", "jaymospowers:linen_bandage"};
	static final float[] BANDAGE_HEAL = {3.0F, 4.0F, 6.0F};

	/** On a band member: the day its draught was last put in its pack. Saved. */
	static final AttachmentType<Long> DRAUGHT = AttachmentRegistry.create(JaymosKingdoms.id("band_draught"),
		builder -> builder.persistent(Codec.LONG));

	private static final Map<UUID, Long> TENDED = new HashMap<>();

	private PackUse() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(PackUse::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TENDED.clear();
		});
		UseEntityCallback.EVENT.register(PackUse::hand);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			for (Mob m : Bonds.companions(player, 16.0)) {
				if (m instanceof Villager v && Comrades.target(v) == null) {
					tend(player, v, now);
				}
			}
			foes(player, now);
		}
	}

	/** A companion out of a fight sees to itself, then to you, then picks up arrows. */
	private static void tend(ServerPlayer player, Villager v, long now) {
		SimpleContainer pack = v.getInventory();
		if (now - TENDED.getOrDefault(v.getUUID(), -TEND_GAP) >= TEND_GAP) {
			if (v.getHealth() < v.getMaxHealth() * HURT) {
				int slot = cheapest(pack, true);
				if (slot >= 0) {
					use(v, pack, slot, v);
					TENDED.put(v.getUUID(), now);
					return;
				}
			}
			if (player.getHealth() < player.getMaxHealth() * HURT && v.distanceTo(player) < 4.0) {
				int slot = bandage(pack);
				if (slot >= 0) {
					use(v, pack, slot, player);
					Callouts.say(v, "pack.bind", player, true, player.getDisplayName());
					TENDED.put(v.getUUID(), now);
					return;
				}
			}
			if (player.getFoodData().getFoodLevel() < STARVING && v.distanceTo(player) < 4.0) {
				int slot = cheapest(pack, false);
				if (slot >= 0) {
					ItemStack bite = pack.removeItem(slot, 1);
					give(player, bite);
					Callouts.say(v, "pack.feed", player, true, bite.getHoverName());
					TENDED.put(v.getUUID(), now);
					return;
				}
			}
		}
		// after the fight, the arrows lying about
		ServerLevel level = player.level();
		int picked = 0;
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, v.getBoundingBox().inflate(ARROWS_NEAR), i -> i.getItem().is(Items.ARROW))) {
			picked += item.getItem().getCount();
			pack.addItem(item.getItem().copy());
			item.discard();
		}
		for (AbstractArrow arrow : level.getEntitiesOfClass(AbstractArrow.class, v.getBoundingBox().inflate(ARROWS_NEAR),
			a -> a.pickup == AbstractArrow.Pickup.ALLOWED && a.tickCount > 20 && a.getDeltaMovement().lengthSqr() < 1.0E-4)) {
			pack.addItem(new ItemStack(Items.ARROW));
			arrow.discard();
			picked++;
		}
		if (picked > 0 && shoots(player)) {
			int given = 0;
			for (int i = 0; i < pack.getContainerSize(); i++) {
				if (pack.getItem(i).is(Items.ARROW)) {
					given += pack.getItem(i).getCount();
					give(player, pack.removeItemNoUpdate(i));
				}
			}
			Callouts.say(v, "pack.arrows", player, false, given);
		}
	}

	private static boolean shoots(Player p) {
		for (ItemStack s : p.getInventory().getNonEquipmentItems()) {
			if (s.getItem() instanceof net.minecraft.world.item.BowItem || s.getItem() instanceof net.minecraft.world.item.CrossbowItem) {
				return true;
			}
		}
		return false;
	}

	/** The cheapest thing in {@code pack} that mends ({@code self}: a bandage or food; else food only); spoiled food last. -1 if none. */
	private static int cheapest(SimpleContainer pack, boolean self) {
		if (self) {
			int b = bandage(pack);
			if (b >= 0) {
				return b;
			}
		}
		int best = -1, bestNutrition = Integer.MAX_VALUE, spoiled = -1;
		for (int i = 0; i < pack.getContainerSize(); i++) {
			ItemStack s = pack.getItem(i);
			if (s.isEmpty() || !s.has(DataComponents.FOOD)) {
				continue;
			}
			if (s.is(Items.ROTTEN_FLESH) || s.is(Items.SPIDER_EYE) || s.is(Items.POISONOUS_POTATO)) {
				spoiled = i;
				continue;
			}
			int n = s.get(DataComponents.FOOD).nutrition();
			if (n < bestNutrition) {
				bestNutrition = n;
				best = i;
			}
		}
		return best >= 0 ? best : spoiled;
	}

	/** The cheapest bandage in {@code pack}, or -1. */
	private static int bandage(SimpleContainer pack) {
		for (String id : BANDAGES) {
			for (int i = 0; i < pack.getContainerSize(); i++) {
				ItemStack s = pack.getItem(i);
				if (!s.isEmpty() && BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals(id)) {
					return i;
				}
			}
		}
		return -1;
	}

	/** {@code v} uses the thing in {@code slot} on {@code who}: a bandage binds, food mends. */
	private static void use(Villager v, SimpleContainer pack, int slot, LivingEntity who) {
		ItemStack s = pack.removeItem(slot, 1);
		String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
		float heal = 2.0F;
		for (int i = 0; i < BANDAGES.length; i++) {
			if (BANDAGES[i].equals(id)) {
				heal = BANDAGE_HEAL[i];
			}
		}
		var food = s.get(DataComponents.FOOD);
		if (food != null) {
			heal = Math.max(1.0F, food.nutrition());
			v.playSound(SoundEvents.GENERIC_EAT.value(), 0.8F, 1.0F);
		}
		who.heal(heal);
		v.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		if (who == v) {
			Callouts.say(v, food != null ? "pack.eat" : "pack.bandage", null, false, s.getHoverName());
		}
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
	}

	/**
	 * Band members near {@code player} who carry no healing draught are given one, into their own pack: the first time they're
	 * seen, then once a day while out of a fight. A member with no pack (or a full one) gets none.
	 */
	private static void foes(ServerPlayer player, long now) {
		long day = player.level().getOverworldClockTime() / 24000L;
		for (Mob m : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(DRAUGHT_NEAR), m -> m.isAlive()
			&& !Downed.isDowned(m) && Temperaments.humanoid(m) && NpcEyes.fighter(m) && FoeLore.keeps(Comrades.side(m))
			&& (!m.hasAttached(DRAUGHT) || m.getAttached(DRAUGHT) != day && Comrades.target(m) == null))) {
			SimpleContainer pack = m instanceof net.minecraft.world.entity.npc.InventoryCarrier carrier ? carrier.getInventory() : BeltPouch.open(m);
			if (pack == null) {
				continue;
			}
			if (!carriesDraught(pack)) {
				ItemStack draught = net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, net.minecraft.world.item.alchemy.Potions.HEALING);
				if (!pack.canAddItem(draught)) {
					continue;
				}
				pack.addItem(draught);
			}
			m.setAttached(DRAUGHT, day);
		}
	}

	private static boolean carriesDraught(SimpleContainer pack) {
		for (int i = 0; i < pack.getContainerSize(); i++) {
			ItemStack s = pack.getItem(i);
			net.minecraft.world.item.alchemy.PotionContents contents = s.get(DataComponents.POTION_CONTENTS);
			if (s.is(Items.POTION) && contents != null && contents.is(net.minecraft.world.item.alchemy.Potions.HEALING)) {
				return true;
			}
		}
		return false;
	}

	// ---- handing them gear ---------------------------------------------------------------------------------------------

	/** A weapon or a piece of armour used on your own companion: they wear it if it's better, and give you theirs. */
	private static InteractionResult hand(Player player, Level level, InteractionHand hand, Entity entity, net.minecraft.world.phys.@Nullable EntityHitResult hit) {
		if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND || sp.isSpectator()
			|| sp.isShiftKeyDown() || !(entity instanceof Villager v) || !sp.getUUID().equals(Bonds.lordOf(v))) {
			return InteractionResult.PASS;
		}
		ItemStack held = sp.getMainHandItem();
		EquipmentSlot slot = slotOf(held);
		if (slot == null) {
			return InteractionResult.PASS;
		}
		ItemStack worn = v.getItemBySlot(slot);
		if (score(held, slot) <= score(worn, slot)) {
			Callouts.say(v, "gear.worse", sp, true, held.getHoverName());
			return InteractionResult.SUCCESS;
		}
		v.setItemSlot(slot, held.split(1));
		if (!worn.isEmpty()) {
			give(sp, worn);
		}
		Callouts.say(v, "gear.better", sp, true, v.getItemBySlot(slot).getHoverName());
		return InteractionResult.SUCCESS;
	}

	/** Where {@code s} is worn: a piece of armour's slot, or the hand for a weapon; null for anything else. */
	private static @Nullable EquipmentSlot slotOf(ItemStack s) {
		if (s.isEmpty()) {
			return null;
		}
		var eq = s.get(DataComponents.EQUIPPABLE);
		if (eq != null && eq.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
			return eq.slot();
		}
		return s.is(net.minecraft.tags.ItemTags.SWORDS) || s.is(net.minecraft.tags.ItemTags.AXES) ? EquipmentSlot.MAINHAND : null;
	}

	/** How good {@code s} is in {@code slot}: its armour, or its damage. */
	private static double score(ItemStack s, EquipmentSlot slot) {
		if (s.isEmpty()) {
			return 0.0;
		}
		Holder<Attribute> what = slot == EquipmentSlot.MAINHAND ? Attributes.ATTACK_DAMAGE : Attributes.ARMOR;
		ItemAttributeModifiers mods = s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		double sum = 0.0;
		for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
			if (e.attribute().equals(what)) {
				sum += e.modifier().amount();
			}
		}
		if (slot != EquipmentSlot.MAINHAND) {
			for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
				if (e.attribute().equals(Attributes.ARMOR_TOUGHNESS)) {
					sum += e.modifier().amount() * 0.5;
				}
			}
		}
		return sum;
	}
}
