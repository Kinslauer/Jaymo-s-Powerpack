package com.jaymoskingdoms;

import com.jaymoskingdoms.mixin.ChestedHorseInvoker;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.414) What the kit roads of the life story ({@link LifeStory.Start#HEIRLOOM}, {@link LifeStory.Start#TRADER},
 * {@link LifeStory.Start#PUP}, {@link LifeStory.Start#NOBODIES}) leave in the player's hands once they've woken.
 *
 * <ul>
 * <li><b>The Heirloom Blade</b>: a fine Tinkers sword (Jaymo's Powers', reached through {@link PowersCompat}; a named, enchanted
 * iron sword without it) named for the player's family and marked as theirs. Two to four days on, if it is still in their
 * pack, a contract is laid on them ({@link Assassins#laidOn}) and an assassin comes for it; if they've sold or dropped it and
 * lost it, nobody comes.</li>
 * <li><b>The Wandering Trader</b>: a pack mule (a horse cannot carry a chest in 26.3, a mule can) with the land's goods in its chest,
 * its owner the player, and two Merchant Orders already taken from the next capital along the road, to be sent on ({@link MerchantOrders#taken}).</li>
 * <li><b>A Pup at Heel</b>: a tamed wolf pup that takes {@link #PUP_GROW_DAYS} days to grow up and, for every day of its age, is
 * {@link #PUP_HEALTH_PER_DAY} health and {@link #PUP_DAMAGE_PER_DAY} damage stronger, up to {@link #PUP_MAX_DAYS} days.</li>
 * <li><b>The Nobodies</b>: three poor villagers hired to the player from the first minute, MCA's mercenaries, in the pack's poorest
 * outfits ({@link Uniforms#POOR}), thin and weakly armed, on a long contract. A hired villager counts as involved with the player
 * ({@link Strangers}), so they follow, carry and fight.</li>
 * </ul>
 */
public final class StoryKit {
	// ---- tuning --------------------------------------------------------------------------------------------------------

	/** The heirloom: how long until someone comes for it (ticks: two to four days), how often it is looked for, and the enchantment of the plain-iron fallback. */
	static final long HEIRLOOM_MIN = 2L * 24000L, HEIRLOOM_SPREAD = 2L * 24000L + 1L;
	static final int HEIRLOOM_CHECK = 100, FALLBACK_SHARPNESS = 2, FALLBACK_UNBREAKING = 2;
	/** The blade's metal and grip (Powers' material ids), and the custom-data key that marks it as the player's. */
	static final String BLADE_METAL = "iron", BLADE_GRIP = "wood", HEIRLOOM_KEY = "jaymoskingdoms_heirloom";
	/** The contract's motive on the assassin's seal. */
	static final String HEIRLOOM_MOTIVE = "heirloom";

	/** The trader's orders: how many, what each cost, and how long till each is ready to be sent on (ticks). */
	static final int ORDERS = 2, ORDER_PAID = 4;
	static final long ORDER_WAIT = 6000L;

	/** The pup: days to grow up, health and damage for each day of its age, the age they stop at (days), and how often it is looked at (ticks). */
	static final int PUP_GROW_DAYS = 3, PUP_MAX_DAYS = 10, PUP_CHECK = 200;
	static final double PUP_HEALTH_PER_DAY = 2.0, PUP_DAMAGE_PER_DAY = 0.5;
	static final long DAY_TICKS = 24000L;

	/** The nobodies: how many, their health and damage (a villager's is 20 and 1), and the contract (ticks: MCA's long one). */
	static final int NOBODIES = 3, NOBODY_CONTRACT = 168000;
	static final double NOBODY_HEALTH = 12.0, NOBODY_DAMAGE = 1.5;

	// ---- state ---------------------------------------------------------------------------------------------------------

	/** On a player with an heirloom to be asked after: the game time (the Overworld's) someone comes for it. Saved, kept through death. */
	static final AttachmentType<Long> HEIRLOOM_DUE = AttachmentRegistry.create(JaymosKingdoms.id("story_heirloom_due"),
		builder -> builder.persistent(Codec.LONG).copyOnDeath());

	/** A player's pup: its entity, and the game time it was born (set aboard, so its age needs no entity data). */
	record Pup(UUID wolf, long born) {
		static final Codec<Pup> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("wolf").forGetter(Pup::wolf),
			Codec.LONG.fieldOf("born").forGetter(Pup::born)).apply(i, Pup::new));
	}

	/** On a player with a pup at heel. Saved, kept through death. */
	static final AttachmentType<Pup> PUP = AttachmentRegistry.create(JaymosKingdoms.id("story_pup"),
		builder -> builder.persistent(Pup.CODEC).copyOnDeath());

	/** Player → the town (id) the road they woke on runs on to, till the trader's pack is made. */
	private static final Map<UUID, Integer> NEXT = new HashMap<>();

	private StoryKit() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(StoryKit::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> NEXT.clear());
	}

	/** {@code player}'s story is unmade: no heirloom to ask after, no pup to grow. */
	static void clear(ServerPlayer player) {
		player.removeAttached(HEIRLOOM_DUE);
		player.removeAttached(PUP);
		NEXT.remove(player.getUUID());
	}

	private static void tick(MinecraftServer server) {
		int ticks = server.getTickCount();
		boolean heirloom = ticks % HEIRLOOM_CHECK == 0, pup = ticks % PUP_CHECK == 0;
		if (!heirloom && !pup) {
			return;
		}
		ServerLevel overworld = Factions.level(server);
		long now = overworld.getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (heirloom) {
				Long due = player.getAttached(HEIRLOOM_DUE);
				if (due != null && now >= due) {
					player.removeAttached(HEIRLOOM_DUE);
					if (holdsHeirloom(player)) {
						Assassins.laidOn(player, HEIRLOOM_MOTIVE);
					}
				}
			}
			Pup mine = pup ? player.getAttached(PUP) : null;
			if (mine != null) {
				growUp(server, mine, now);
			}
		}
	}

	// ---- the heirloom blade ----------------------------------------------------------------------------------------------

	/** Whether {@code stack} is the family blade of the player whose UUID is {@code owner}. */
	private static boolean isHeirloom(ItemStack stack, String owner) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && owner.equals(data.copyTag().getStringOr(HEIRLOOM_KEY, ""));
	}

	/** Whether {@code player} still has their family blade about them (their pack, off hand or worn). */
	static boolean holdsHeirloom(ServerPlayer player) {
		String id = player.getStringUUID();
		return player.getInventory().contains(stack -> isHeirloom(stack, id));
	}

	/**
	 * Gives {@code player} a fine blade named for their family, marked as theirs, and sets the day someone comes for it.
	 * The sword is a Tinkers one of Powers' ({@link PowersCompat#tinkerSword}), else a named, enchanted iron sword.
	 */
	static void heirloom(ServerPlayer player) {
		ServerLevel level = (ServerLevel)player.level();
		RandomSource random = player.getRandom();
		ItemStack blade = PowersCompat.tinkerSword(BLADE_METAL, BLADE_GRIP);
		if (blade == null || blade.isEmpty()) {
			blade = new ItemStack(Items.IRON_SWORD);
			ItemStack sword = blade;
			Relics.enchantment(level, Enchantments.SHARPNESS).ifPresent(h -> sword.enchant(h, FALLBACK_SHARPNESS));
			Relics.enchantment(level, Enchantments.UNBREAKING).ifPresent(h -> sword.enchant(h, FALLBACK_UNBREAKING));
		}
		blade.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.heirloom", player.getName()).withStyle(style -> style.withItalic(false)
			.withColor(ChatFormatting.AQUA)));
		CustomData.update(DataComponents.CUSTOM_DATA, blade, tag -> tag.putString(HEIRLOOM_KEY, player.getStringUUID()));
		player.getInventory().placeItemBackInInventory(blade, Prediction.SERVER_ONLY);
		player.setAttached(HEIRLOOM_DUE, Factions.level(level.getServer()).getGameTime() + HEIRLOOM_MIN + (long)(random.nextDouble() * HEIRLOOM_SPREAD));
	}

	// ---- the wandering trader ----------------------------------------------------------------------------------------------

	/** (From the start, as it sets the player down) the road they woke on runs on to town {@code next}. */
	static void trade(ServerPlayer player, int next) {
		NEXT.put(player.getUUID(), next);
	}

	/**
	 * A pack mule beside {@code player}, theirs, with the land's goods in its chest, and {@link #ORDERS} Merchant Orders from the
	 * next town along their road already taken. (A saddle and a lead come with the kit.)
	 */
	static void pack(ServerPlayer player) {
		ServerLevel level = (ServerLevel)player.level();
		RandomSource random = level.getRandom();
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset(2, 0, 1));
		AbstractChestedHorse mule = EntityTypes.MULE.create(level, EntitySpawnReason.EVENT);
		if (mule != null) {
			mule.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot(), 0.0F);
			mule.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
			mule.setPersistenceRequired();
			mule.setChest(true);
			((ChestedHorseInvoker)mule).jaymoskingdoms$createInventory();
			List<ItemStack> goods = RoamingCaravans.cargo(random, Climate.at(level, at), RoamingCaravans.Kind.MERCHANT, false);
			for (int i = 0; i < goods.size() && i < mule.getInventorySize(); i++) {
				mule.getSlot(AbstractHorse.INVENTORY_SLOT_OFFSET + i).set(goods.get(i));
			}
			mule.setTamed(true);
			mule.setOwner(player);
			level.addFreshEntity(mule);
		}
		Integer next = NEXT.remove(player.getUUID());
		Faction town = next == null ? null : Factions.get(level.getServer(), next);
		if (town != null) {
			List<ItemStack> wares = RoamingCaravans.cargo(random, Climate.at(level, town.seat()), RoamingCaravans.Kind.MERCHANT, false);
			for (int i = 0; i < ORDERS && i < wares.size(); i++) {
				MerchantOrders.taken(level, player, town, wares.get(i), ORDER_PAID, ORDER_WAIT * (i + 1));
			}
		}
		player.getInventory().placeItemBackInInventory(new ItemStack(Items.SADDLE), Prediction.SERVER_ONLY);
		player.getInventory().placeItemBackInInventory(new ItemStack(Items.LEAD), Prediction.SERVER_ONLY);
	}

	// ---- a pup at heel ---------------------------------------------------------------------------------------------------------

	/** A tamed wolf pup at {@code player}'s side, theirs, that grows up in {@link #PUP_GROW_DAYS} days and toughens with every day. */
	static void pup(ServerPlayer player) {
		ServerLevel level = (ServerLevel)player.level();
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset(1, 0, 1));
		Wolf wolf = EntityTypes.WOLF.create(level, EntitySpawnReason.EVENT);
		if (wolf == null) {
			return;
		}
		wolf.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot(), 0.0F);
		wolf.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		wolf.setBaby(true);
		wolf.setAge((int)-(PUP_GROW_DAYS * DAY_TICKS));
		wolf.tame(player);
		wolf.setPersistenceRequired();
		if (level.addFreshEntity(wolf)) {
			player.setAttached(PUP, new Pup(wolf.getUUID(), Factions.level(level.getServer()).getGameTime()));
		}
	}

	/** The id of the pup's modifiers (one for its health, one for its damage), replaced as it ages. */
	private static final net.minecraft.resources.Identifier PUP_HEALTH = JaymosKingdoms.id("pup_age_health"), PUP_DAMAGE = JaymosKingdoms.id("pup_age_damage");

	/** How many of the pup's days of age count, from the game time it was born. */
	static double ageDays(long born, long now) {
		return Math.min(PUP_MAX_DAYS, Math.max(0L, now - born) / (double)DAY_TICKS);
	}

	/** {@code mine}'s wolf, if it's loaded, is as strong as its age: more health and damage for each day, up to the cap. */
	private static void growUp(MinecraftServer server, Pup mine, long now) {
		net.minecraft.world.entity.Entity found = null;
		for (ServerLevel level : server.getAllLevels()) {
			found = level.getEntity(mine.wolf());
			if (found != null) {
				break;
			}
		}
		if (!(found instanceof Wolf wolf) || !wolf.isAlive()) {
			return;
		}
		double days = ageDays(mine.born(), now);
		AttributeInstance health = wolf.getAttribute(Attributes.MAX_HEALTH), damage = wolf.getAttribute(Attributes.ATTACK_DAMAGE);
		if (health != null) {
			AttributeModifier before = health.getModifier(PUP_HEALTH);
			double extra = days * PUP_HEALTH_PER_DAY;
			health.addOrReplacePermanentModifier(new AttributeModifier(PUP_HEALTH, extra, AttributeModifier.Operation.ADD_VALUE));
			if (before != null && extra > before.amount()) {
				// (the health it grew into comes with it)
				wolf.heal((float)(extra - before.amount()));
			}
		}
		if (damage != null) {
			damage.addOrReplacePermanentModifier(new AttributeModifier(PUP_DAMAGE, days * PUP_DAMAGE_PER_DAY, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	// ---- the nobodies ------------------------------------------------------------------------------------------------------------

	/** Three poor villagers hired to {@code player}, standing round them: follow, carry and fight (MCA's hired mercenaries). */
	static void nobodies(ServerPlayer player) {
		McaCompat.hireNobodies((ServerLevel)player.level(), player, player.blockPosition(), NOBODIES, Uniforms.POOR, NOBODY_HEALTH, NOBODY_DAMAGE,
			NOBODY_CONTRACT);
	}
}
