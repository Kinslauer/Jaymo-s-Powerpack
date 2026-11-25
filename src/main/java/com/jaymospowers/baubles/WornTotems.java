package com.jaymospowers.baubles;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DeathProtection;
import org.jspecify.annotations.Nullable;

/**
 * Totems that save you without being held. A Totem of Undying (or anything with vanilla's death protection, like
 * Kingdoms' crude totem) worn in a bauble slot that takes it (the charm slot) turns death away just as one in the hand
 * does, after the hands have had their turn ({@code WornTotemMixin}). And now and then a villager or an illager (MCA's
 * villagers, Kingdoms' guards and rulers are villagers too) carries an old one: it saves them once, the same way, and
 * drops as loot if something kills them past it.
 */
public final class WornTotems {
	/** The chance a grown villager (any kind: MCA's, traders) carries a totem, rolled once when it first loads. */
	static final float VILLAGER_CHANCE = 1.0F / 60.0F;
	/** The chance a pillager, vindicator or other illager carries one. */
	static final float ILLAGER_CHANCE = 1.0F / 40.0F;
	/** The chance an evoker does: they study the old ones (Lore: they could make them once and cannot now). */
	static final float EVOKER_CHANCE = 1.0F / 6.0F;

	/** On a villager or illager: whether it carries a totem (true) or was rolled and doesn't (false). Absent: not rolled yet. */
	public static final AttachmentType<Boolean> CARRIED = AttachmentRegistry.create(JaymosPowers.id("carried_totem"),
		builder -> builder.persistent(Codec.BOOL));

	private WornTotems() {
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof LivingEntity living && !living.hasAttached(CARRIED) && !living.isBaby()) {
				float chance = chance(living);
				if (chance > 0.0F) {
					living.setAttached(CARRIED, level.getRandom().nextFloat() < chance);
				}
			}
		});
		// killed past it (the void, /kill, a beheading): the totem they carried is left with the body
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (Boolean.TRUE.equals(entity.getAttached(CARRIED)) && entity.level() instanceof ServerLevel level) {
				entity.setAttached(CARRIED, false);
				entity.spawnAtLocation(level, new ItemStack(Items.TOTEM_OF_UNDYING));
			}
		});
	}

	private static float chance(LivingEntity entity) {
		if (entity instanceof Evoker) {
			return EVOKER_CHANCE;
		}
		if (entity instanceof AbstractIllager) {
			return ILLAGER_CHANCE;
		}
		return entity instanceof AbstractVillager ? VILLAGER_CHANCE : 0.0F;
	}

	/**
	 * Whether a totem stands ready to turn this death away: one in either hand (vanilla's), one worn in a slot that takes
	 * it, or one carried. For the death hooks that leave a totem to do its work instead of downing or ending someone.
	 */
	public static boolean ready(LivingEntity entity) {
		for (InteractionHand hand : InteractionHand.values()) {
			if (entity.getItemInHand(hand).has(DataComponents.DEATH_PROTECTION)) {
				return true;
			}
		}
		return Boolean.TRUE.equals(entity.getAttached(CARRIED)) || entity instanceof Player player && worn(player) != null;
	}

	/** The worn totem (the live stack) that would save {@code player}, or null. */
	private static @Nullable ItemStack worn(Player player) {
		List<ItemStack> saved = player.getAttached(Baubles.WORN);
		if (saved != null) {
			for (int i = 0; i < saved.size() && i < Baubles.SIZE; i++) {
				ItemStack stack = saved.get(i);
				if (stack.has(DataComponents.DEATH_PROTECTION) && Baubles.fits(i, stack) && Bonds.works(player, stack)) {
					return stack;
				}
			}
		}
		return null;
	}

	/**
	 * (Server, called when vanilla found no totem in hand.) Spends a worn or carried totem to keep {@code entity} alive,
	 * as vanilla's {@code LivingEntity#checkTotemDeathProtection} does with a held one. True if one did.
	 */
	public static boolean save(LivingEntity entity, DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		ItemStack used;
		if (entity instanceof ServerPlayer player && worn(player) instanceof ItemStack stack) {
			used = stack.copy();
			stack.shrink(1);
			// saved again so its bonus, look and the bauble screen let it go
			Baubles.setWorn(player, Baubles.worn(player));
			player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(used.getItem()));
			net.minecraft.advancements.triggers.CriteriaTriggers.USED_TOTEM.trigger(player, used);
			used.causeUseVibration(player, net.minecraft.world.level.gameevent.GameEvent.ITEM_INTERACT_FINISH);
		} else if (Boolean.TRUE.equals(entity.getAttached(CARRIED))) {
			entity.setAttached(CARRIED, false);
			used = new ItemStack(Items.TOTEM_OF_UNDYING);
		} else {
			return false;
		}
		DeathProtection protection = used.get(DataComponents.DEATH_PROTECTION);
		entity.setHealth(1.0F);
		if (protection != null) {
			protection.applyEffects(used, entity);
		}
		entity.level().broadcastEntityEvent(entity, (byte)35);
		return true;
	}
}
