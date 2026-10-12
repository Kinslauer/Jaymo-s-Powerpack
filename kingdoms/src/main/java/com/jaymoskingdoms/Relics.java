package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.28) Relics: the special weapons and armour quests pay out, each with a passive. They're plain vanilla items (a
 * sword, a bow, a helm) with a name, enchantments, the passive written on them, and its key in their custom data, so
 * they need no registry of their own and survive without this mod (as ordinary enchanted gear).
 *
 * <p>Weapon passives answer the blows they land (by hand, or an arrow from a relic bow); armour passives answer the
 * blows taken by whoever wears them, or tick while worn.
 */
public final class Relics {
	/** The custom-data key holding a relic's passive. */
	static final String KEY = "jaymoskingdoms_relic";

	/** Bloodthirst: the share of the damage dealt healed. */
	static final float LIFESTEAL = 0.2F;
	/** Frostbite: slowness this long (ticks), this strong (amplifier). */
	static final int FROST_TICKS = 60, FROST_AMP = 1;
	/** Emberbrand: set alight for this long (seconds). */
	static final float EMBER_SECONDS = 4.0F;
	/** Stormcaller: chance per blow of a lightning strike, and the damage it does. */
	static final float STORM_CHANCE = 0.15F, STORM_DAMAGE = 5.0F;
	/** Executioner: a foe under this share of its health takes this share more. */
	static final float EXECUTE_BELOW = 0.35F, EXECUTE_BONUS = 0.4F;
	/** Venomfang: poison this long (ticks), this strong. */
	static final int VENOM_TICKS = 80, VENOM_AMP = 1;
	/** Second Wind: under this share of health, regeneration this long and strong, then a wait (ticks). */
	static final float SECOND_WIND_BELOW = 0.35F;
	static final int SECOND_WIND_TICKS = 100, SECOND_WIND_AMP = 1, SECOND_WIND_COOLDOWN = 20 * 60;
	/** Retribution: the share of a melee blow sent back. */
	static final float RETRIBUTION = 0.25F;
	/** Bulwark: chance per blow of absorption this long, this strong. */
	static final float BULWARK_CHANCE = 0.2F;
	static final int BULWARK_TICKS = 100, BULWARK_AMP = 0;
	/** How often worn passives (Fleetfoot, Flameward) are refreshed (ticks). */
	static final int WORN_TICKS = 20;

	/** The passives: weapons' first, then armour's. */
	public enum Passive {
		BLOODTHIRST(true), FROSTBITE(true), EMBERBRAND(true), STORMCALLER(true), EXECUTIONER(true), VENOMFANG(true),
		SECOND_WIND(false), RETRIBUTION(false), BULWARK(false), FLEETFOOT(false), FLAMEWARD(false);

		final boolean weapon;

		Passive(boolean weapon) {
			this.weapon = weapon;
		}

		String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		static @Nullable Passive of(String key) {
			for (Passive p : values()) {
				if (p.key().equals(key)) {
					return p;
				}
			}
			return null;
		}
	}

	/** Second Wind's wait, per player (game time it's ready again). */
	private static final Map<UUID, Long> WINDED = new HashMap<>();
	/** Set while a passive's own extra hit lands, so it doesn't set off passives again. */
	private static boolean striking;

	private Relics() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
			if (!striking && !blocked && damage > 0.0F && entity.level() instanceof ServerLevel level) {
				struck(level, entity, source, damage);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Relics::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> WINDED.clear());
	}

	// ---- making them ---------------------------------------------------------------------------------------------------

	/** A random relic spec ({@code passive@item}) for a weapon or a piece of armour; royal ones are of the best metals. */
	static String roll(RandomSource random, boolean weapon, boolean royal) {
		List<Passive> passives = new ArrayList<>();
		for (Passive p : Passive.values()) {
			if (p.weapon == weapon) {
				passives.add(p);
			}
		}
		Passive passive = passives.get(random.nextInt(passives.size()));
		Item[] bases = weapon
			? (royal ? new Item[] {Items.NETHERITE_SWORD, Items.NETHERITE_AXE, Items.BOW, Items.CROSSBOW, Items.DIAMOND_SWORD}
				: new Item[] {Items.IRON_SWORD, Items.IRON_AXE, Items.BOW, Items.CROSSBOW, Items.DIAMOND_SWORD})
			: (royal ? new Item[] {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS, Items.DIAMOND_CHESTPLATE}
				: new Item[] {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS, Items.DIAMOND_HELMET});
		Item base = bases[random.nextInt(bases.length)];
		// (Fleetfoot only makes sense on the legs or feet)
		if (passive == Passive.FLEETFOOT) {
			base = royal ? Items.NETHERITE_BOOTS : Items.IRON_BOOTS;
		}
		return passive.key() + "@" + BuiltInRegistries.ITEM.getKey(base) + (royal ? "@royal" : "");
	}

	/** The relic a spec names, made up: named, enchanted, its passive written on it; EMPTY if the spec's unknown. */
	static ItemStack make(ServerLevel level, String spec, String giverName) {
		String[] parts = spec.split("@");
		Passive passive = parts.length >= 2 ? Passive.of(parts[0]) : null;
		Identifier id = parts.length >= 2 ? Identifier.tryParse(parts[1]) : null;
		Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
		if (passive == null || item == null) {
			return ItemStack.EMPTY;
		}
		boolean royal = parts.length >= 3;
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable("relic.jaymoskingdoms.name",
				Component.translatable("relic.jaymoskingdoms." + passive.key()), Component.translatable(pieceKey(item)))
			.withStyle(style -> style.withItalic(false).withColor(royal ? ChatFormatting.GOLD : ChatFormatting.LIGHT_PURPLE)));
		stack.set(DataComponents.RARITY, royal ? Rarity.EPIC : Rarity.RARE);
		List<Component> lore = new ArrayList<>();
		lore.add(Component.translatable("relic.jaymoskingdoms.passive", Component.translatable("relic.jaymoskingdoms." + passive.key()))
			.withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GOLD)));
		lore.add(Component.translatable("relic.jaymoskingdoms." + passive.key() + ".desc").withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)));
		lore.add(Component.translatable(royal ? "relic.jaymoskingdoms.royal_gift" : "relic.jaymoskingdoms.gift", giverName)
			.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		stack.set(DataComponents.LORE, new ItemLore(lore));
		CompoundTag tag = new CompoundTag();
		tag.putString(KEY, passive.key());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		enchant(level, stack, item, royal);
		return stack;
	}

	private static String pieceKey(Item item) {
		String path = BuiltInRegistries.ITEM.getKey(item).getPath();
		for (String piece : new String[] {"sword", "axe", "crossbow", "bow", "helmet", "chestplate", "leggings", "boots"}) {
			if (path.endsWith(piece)) {
				return "relic.jaymoskingdoms.piece." + piece;
			}
		}
		return item.getDescriptionId();
	}

	private static void enchant(ServerLevel level, ItemStack stack, Item item, boolean royal) {
		String path = BuiltInRegistries.ITEM.getKey(item).getPath();
		int strong = royal ? 5 : 3, guard = royal ? 4 : 3;
		if (path.endsWith("sword") || path.endsWith("_axe")) {
			add(level, stack, Enchantments.SHARPNESS, strong);
		} else if (path.equals("bow")) {
			add(level, stack, Enchantments.POWER, strong);
		} else if (path.equals("crossbow")) {
			add(level, stack, Enchantments.QUICK_CHARGE, royal ? 3 : 2);
			add(level, stack, Enchantments.PIERCING, royal ? 4 : 2);
		} else {
			add(level, stack, Enchantments.PROTECTION, guard);
		}
		add(level, stack, Enchantments.UNBREAKING, 3);
		if (royal) {
			add(level, stack, Enchantments.MENDING, 1);
		}
	}

	private static void add(ServerLevel level, ItemStack stack, ResourceKey<Enchantment> key, int lvl) {
		enchantment(level, key).ifPresent(h -> stack.enchant(h, lvl));
	}

	static java.util.Optional<? extends Holder<Enchantment>> enchantment(ServerLevel level, ResourceKey<Enchantment> key) {
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key);
	}

	/** An enchanted book of {@code spec} ({@code enchantment id@level}); EMPTY if it's unknown. */
	static ItemStack book(ServerLevel level, String spec) {
		String[] parts = spec.split("@");
		Identifier id = Identifier.tryParse(parts[0]);
		if (id == null) {
			return ItemStack.EMPTY;
		}
		int lvl = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
		return enchantment(level, ResourceKey.create(Registries.ENCHANTMENT, id))
			.map(h -> EnchantmentHelper.createBook(new EnchantmentInstance(h, Math.min(lvl, h.value().getMaxLevel()))))
			.orElse(ItemStack.EMPTY);
	}

	/** The passive a stack carries, if it's a relic. */
	static @Nullable Passive passive(@Nullable ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? null : data.copyTag().getString(KEY).map(Passive::of).orElse(null);
	}

	// ---- their passives ------------------------------------------------------------------------------------------------

	private static void struck(ServerLevel level, LivingEntity victim, DamageSource source, float damage) {
		// the attacker's weapon: in hand for a blow, the bow for an arrow
		if (source.getEntity() instanceof LivingEntity attacker && attacker != victim) {
			ItemStack weapon = source.getDirectEntity() == attacker ? attacker.getMainHandItem()
				: source.getDirectEntity() instanceof AbstractArrow arrow ? arrow.getWeaponItem() : null;
			Passive p = passive(weapon);
			if (p != null && p.weapon) {
				weaponPassive(level, p, attacker, victim, damage);
			}
		}
		if (victim instanceof Player) {
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				Passive p = passive(victim.getItemBySlot(slot));
				if (p != null && !p.weapon) {
					armorPassive(level, p, victim, source, damage);
				}
			}
		}
	}

	private static void weaponPassive(ServerLevel level, Passive p, LivingEntity attacker, LivingEntity victim, float damage) {
		RandomSource random = level.getRandom();
		switch (p) {
			case BLOODTHIRST -> attacker.heal(damage * LIFESTEAL);
			case FROSTBITE -> {
				victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, FROST_TICKS, FROST_AMP), attacker);
				victim.setTicksFrozen(Math.max(victim.getTicksFrozen(), victim.getTicksRequiredToFreeze()));
			}
			case EMBERBRAND -> victim.igniteForSeconds(EMBER_SECONDS);
			case VENOMFANG -> victim.addEffect(new MobEffectInstance(MobEffects.POISON, VENOM_TICKS, VENOM_AMP), attacker);
			case STORMCALLER -> {
				if (random.nextFloat() < STORM_CHANCE) {
					LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
					if (bolt != null) {
						// (the light and thunder only: the bolt's own fire would burn the settlement down)
						bolt.setVisualOnly(true);
						bolt.snapTo(victim.getX(), victim.getY(), victim.getZ());
						level.addFreshEntity(bolt);
					}
					extraHit(level, victim, level.damageSources().indirectMagic(attacker, attacker), STORM_DAMAGE);
				}
			}
			case EXECUTIONER -> {
				if (victim.isAlive() && victim.getHealth() < victim.getMaxHealth() * EXECUTE_BELOW) {
					extraHit(level, victim, attacker instanceof Player player ? level.damageSources().playerAttack(player) : level.damageSources().mobAttack(attacker),
						damage * EXECUTE_BONUS);
				}
			}
			default -> {
			}
		}
	}

	private static void armorPassive(ServerLevel level, Passive p, LivingEntity wearer, DamageSource source, float damage) {
		switch (p) {
			case SECOND_WIND -> {
				long now = level.getGameTime();
				if (wearer.isAlive() && wearer.getHealth() < wearer.getMaxHealth() * SECOND_WIND_BELOW && WINDED.getOrDefault(wearer.getUUID(), 0L) <= now) {
					WINDED.put(wearer.getUUID(), now + SECOND_WIND_COOLDOWN);
					wearer.addEffect(new MobEffectInstance(MobEffects.REGENERATION, SECOND_WIND_TICKS, SECOND_WIND_AMP));
					level.playSound(null, wearer.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.4F, 1.6F);
				}
			}
			case RETRIBUTION -> {
				if (source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker && attacker != wearer) {
					extraHit(level, attacker, level.damageSources().thorns(wearer), damage * RETRIBUTION);
				}
			}
			case BULWARK -> {
				if (level.getRandom().nextFloat() < BULWARK_CHANCE) {
					wearer.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, BULWARK_TICKS, BULWARK_AMP));
				}
			}
			default -> {
			}
		}
	}

	/** A passive's own hit, through the target's moment of invulnerability, that sets off no passives itself. */
	private static void extraHit(ServerLevel level, LivingEntity target, DamageSource source, float amount) {
		if (amount <= 0.0F || !target.isAlive()) {
			return;
		}
		striking = true;
		try {
			target.setInvulnerableTime(0);
			target.hurtServer(level, source, amount);
		} finally {
			striking = false;
		}
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % WORN_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				Passive p = passive(player.getItemBySlot(slot));
				if (p == Passive.FLEETFOOT) {
					player.addEffect(new MobEffectInstance(MobEffects.SPEED, WORN_TICKS + 20, 0, true, false, true));
				} else if (p == Passive.FLAMEWARD && player.isOnFire()) {
					player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0, true, false, true));
				}
			}
		}
	}
}
