package com.jaymoskingdoms.inhabitants.fts;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The weapons of From the Shadows Reborn 3.0 (Sonmok14 and L_Ender; AGPL v3; credit and file list in
 * licenses/from-the-shadows/CREDITS-gear.md; the code is our own): the Devil Splitter, Thirst for Blood, the crystallized
 * dagger and the Horn of Apocalypse. The Devil Splitter and Thirst for Blood are drawn in the hand as the original's 3D
 * models ({@code GeoWeaponModel}, the client's). The thrown dagger is the Plague Bearer's ({@link PlagueKnife}); the
 * original has no dagger a player throws, nor the Horn of Purification or the Apocrypha (their pictures are in the jar
 * with no item behind them), so none of those are here.
 * <p>Families and tiers: the Devil Splitter, Thirst for Blood and the dagger are blades (tag {@code form_blades}: the blade
 * family's forms); the Horn is a horn (like the Breaking Horn). All of it is of the dread tier.
 */
public final class FtsWeapons {
	public static Item DEVIL_SPLITTER, THIRST_FOR_BLOOD, CRYSTALLIZED_DAGGER, HORN_OF_APOCALYPSE;
	public static DataComponentType<Integer> BLOOD_CHARGES;

	/** Blood mends all three (a Bottle of Blood). */
	static final TagKey<Item> BLOOD_REPAIR = TagKey.create(Registries.ITEM, JaymosKingdoms.id("repairs_with_blood"));
	/**
	 * The original's blades: iron's mining level, 350 uses, 20 enchantability (Thirst for Blood 12 uses, unbreakable, and 18
	 * as an item). The damage is added to the player's 1: the Devil Splitter 8 a blow, Thirst for Blood 9, the dagger 7,
	 * all at the sword's speed (1.6).
	 */
	static final ToolMaterial BLOOD_STEEL = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, 350, 5.0F, 0.0F, 20, BLOOD_REPAIR);
	static final float SPLITTER_DAMAGE = 7.0F, THIRST_DAMAGE = 8.0F, DAGGER_DAMAGE = 6.0F;
	static final float ATTACK_SPEED = -2.4F;
	/** Reach beyond the hand's: the Devil Splitter is long, Thirst for Blood a little, the dagger short. */
	static final float SPLITTER_REACH = 1.0F, THIRST_REACH = 0.5F, DAGGER_REACH = -1.0F;
	static final int SPLITTER_ENCHANTABILITY = 18;

	/** The Devil Splitter: this many blows on one foe within this many ticks (20 s), the last one doubled; against a demon every blow lands twice. */
	static final int SPLITTER_HITS = 6, SPLITTER_WINDOW = 400;
	/** What a demon's death (the Nehemoth's) mends of the Devil Splitter, and the share of a doubled blow that heals the wielder. */
	static final int SPLITTER_MEND = 20;
	static final float SPLITTER_HEAL = 1.0F / 6.0F;
	static final TagKey<EntityType<?>> DEMON = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("demon"));

	/** Thirst for Blood: charges it holds (it begins full), the kill that fills one (a foe of at least this much health), the laser. */
	static final int MAX_CHARGES = 12;
	static final float WORTHWHILE_KILL_HEALTH = 10.0F;
	static final int CHARGE_BAR_COLOUR = 0x9E1B16;
	/** Its laser: damage (half hearts) to each foe along it, seconds of fire it leaves, how far it reaches and how wide it is, the wait after it (ticks). */
	static final float LASER_DAMAGE = 9.0F, LASER_FIRE_SECONDS = 5.0F;
	static final double LASER_RANGE = 24.0, LASER_WIDTH = 0.5;
	static final int LASER_COOLDOWN = 40;
	static final int LASER_COLOUR = 0xC81E1E;

	/** The crystallized dagger: below this health (half hearts) it swings faster, by up to this much more speed at none; the chance of a fragment from a kill, and more per Looting level. */
	static final double DAGGER_FAST_BELOW = 10.0, DAGGER_FASTEST_BONUS = 0.5;
	static final double FRAGMENT_CHANCE = 0.25, FRAGMENT_LOOTING = 0.15;
	static final TagKey<EntityType<?>> NO_FRAGMENTS = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("not_drop_crystallized_fragments"));
	static final Identifier DAGGER_SPEED_ID = JaymosKingdoms.id("low_health_attack_speed_bonus");

	/** The Horn of Apocalypse: everything alive this near is cut and its healing blocked for this long (ticks); the wait after it, how long it is blown. */
	static final double HORN_RANGE = 10.0;
	static final int HORN_TICKS = 200, HORN_COOLDOWN = 400, HORN_USE_TICKS = 120;
	static final float HORN_CUT = 1.0F;

	/** Hits on a foe, per wielder: how many, and since when (game time). */
	private static final Map<UUID, Map<UUID, int[]>> SPLITTER_HITS_ON = new HashMap<>();

	private FtsWeapons() {
	}

	public static void register() {
		BLOOD_CHARGES = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKingdoms.id("blood_charges"),
			DataComponentType.<Integer>builder().persistent(Codec.intRange(0, MAX_CHARGES)).networkSynchronized(ByteBufCodecs.VAR_INT).build());
		DEVIL_SPLITTER = item("devil_splitter", p -> new DevilSplitter(blade(p, SPLITTER_DAMAGE, SPLITTER_REACH).rarity(Rarity.UNCOMMON).fireResistant()
			.enchantable(SPLITTER_ENCHANTABILITY)));
		THIRST_FOR_BLOOD = item("thirst_for_blood", p -> new ThirstForBlood(blade(p, THIRST_DAMAGE, THIRST_REACH).rarity(Rarity.UNCOMMON).fireResistant()
			.enchantable(SPLITTER_ENCHANTABILITY).component(DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE)
			.component(BLOOD_CHARGES, MAX_CHARGES)));
		CRYSTALLIZED_DAGGER = item("crystallized_dagger", p -> new Dagger(blade(p, DAGGER_DAMAGE, DAGGER_REACH).rarity(Rarity.UNCOMMON).fireResistant()));
		HORN_OF_APOCALYPSE = item("horn_of_apocalypse", p -> new Horn(p.stacksTo(1).rarity(Rarity.RARE).fireResistant()));

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.insertAfter(Items.NETHERITE_SWORD, DEVIL_SPLITTER);
			output.insertAfter(DEVIL_SPLITTER, THIRST_FOR_BLOOD);
			output.insertAfter(THIRST_FOR_BLOOD, CRYSTALLIZED_DAGGER);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(HORN_OF_APOCALYPSE));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				quicken(player);
			}
		});
		// a kill with the dagger may leave a fragment
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer player && player.getMainHandItem().is(CRYSTALLIZED_DAGGER) && entity.level() instanceof ServerLevel level
				&& !entity.getType().builtInRegistryHolder().is(NO_FRAGMENTS) && !(entity instanceof Animal) && !(entity instanceof AbstractFish) && !(entity instanceof AbstractVillager)
				&& !(entity instanceof AbstractGolem)) {
				int looting = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.LOOTING)
					.map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, player.getMainHandItem())).orElse(0);
				if (level.getRandom().nextDouble() < FRAGMENT_CHANCE + FRAGMENT_LOOTING * looting) {
					entity.spawnAtLocation(level, new ItemStack(FtsGear.CRYSTALLIZED_FRAGMENT));
				}
			}
		});
	}

	/** The blade's properties: iron-level sword of {@link #BLOOD_STEEL}, this much damage and reach, the sword's speed. */
	private static Item.Properties blade(Item.Properties p, float damage, float reach) {
		return p.sword(BLOOD_STEEL, damage, ATTACK_SPEED).attributes(ItemAttributeModifiers.builder()
			.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, damage, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
			.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, ATTACK_SPEED, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
			.add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(JaymosKingdoms.id("blood_blade_reach"), reach, AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.MAINHAND).build());
	}

	private static Item item(String name, Function<Item.Properties, Item> make) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, make.apply(new Item.Properties().setId(key)));
	}

	/** The dagger in the hand swings faster the nearer to death its wielder is. */
	private static void quicken(ServerPlayer player) {
		AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
		if (speed == null) {
			return;
		}
		speed.removeModifier(DAGGER_SPEED_ID);
		if (player.getMainHandItem().is(CRYSTALLIZED_DAGGER) && player.getHealth() < DAGGER_FAST_BELOW) {
			double bonus = (DAGGER_FAST_BELOW - player.getHealth()) / DAGGER_FAST_BELOW * DAGGER_FASTEST_BONUS;
			speed.addTransientModifier(new AttributeModifier(DAGGER_SPEED_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	/** The Devil Splitter: the sixth blow on a foe within twenty seconds is doubled; against a demon, every blow is. */
	static final class DevilSplitter extends Item {
		DevilSplitter(Item.Properties properties) {
			super(properties);
		}

		@Override
		public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
			if (!(attacker.level() instanceof ServerLevel level)) {
				return;
			}
			LivingEntity body = target;
			float strength = attacker instanceof Player player ? player.getAttackStrengthScale(0.5F) : 1.0F;
			float blow = (float)attacker.getAttributeValue(Attributes.ATTACK_DAMAGE) * (0.2F + strength * strength * 0.8F);
			if (attacker instanceof Player player && sixth(attacker, body, level.getGameTime())) {
				twice(level, player, body, blow);
				Fx.burst(level, body.getX(), body.getY() + body.getBbHeight() / 2.0, body.getZ(), 0xC81E1E, 1.0F, 12, 0.0, 0.0, 0.0);
				level.playSound(null, attacker.blockPosition(), SoundEvents.ENDER_DRAGON_HURT, SoundSource.PLAYERS, 1.0F, 1.0F);
			}
			if (body.getType().builtInRegistryHolder().is(DEMON)) {
				if (twice(level, attacker, body, blow) && !body.isAlive()) {
					stack.setDamageValue(Math.max(0, stack.getDamageValue() - SPLITTER_MEND));
				}
			}
		}

		/** The extra blow of a doubling: another of the same size, gone through the foe's guard of invulnerability. */
		private static boolean twice(ServerLevel level, LivingEntity attacker, LivingEntity body, float blow) {
			body.setInvulnerableTime(0);
			boolean hurt = body.hurtServer(level, attacker instanceof Player player ? level.damageSources().playerAttack(player) : level.damageSources().mobAttack(attacker),
				blow);
			if (hurt && body.getType().builtInRegistryHolder().is(DEMON)) {
				attacker.heal(blow * SPLITTER_HEAL);
			}
			return hurt;
		}

		/** Whether this blow is the sixth on {@code body} within the window (then the count starts again). */
		private static boolean sixth(LivingEntity attacker, LivingEntity body, long now) {
			Map<UUID, int[]> mine = SPLITTER_HITS_ON.computeIfAbsent(attacker.getUUID(), k -> new HashMap<>());
			if (mine.size() > 64) {
				mine.values().removeIf(h -> now - h[1] > SPLITTER_WINDOW);
			}
			int[] hit = mine.get(body.getUUID());
			if (hit == null || now - hit[1] > SPLITTER_WINDOW) {
				hit = new int[] {0, (int)now};
				mine.put(body.getUUID(), hit);
			}
			hit[0]++;
			if (hit[0] >= SPLITTER_HITS) {
				mine.remove(body.getUUID());
				return true;
			}
			return false;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			for (int i = 1; i <= 4; i++) {
				builder.accept(Component.translatable("tooltip.jaymoskingdoms.devil_splitter." + i).withStyle(ChatFormatting.BLUE));
			}
		}
	}

	/** Thirst for Blood: a kill of a foe of some size loads a charge; using it spends one on a laser that burns what it crosses. */
	static final class ThirstForBlood extends Item {
		ThirstForBlood(Item.Properties properties) {
			super(properties);
		}

		static int charges(ItemStack stack) {
			return stack.getOrDefault(BLOOD_CHARGES, MAX_CHARGES);
		}

		static void setCharges(ItemStack stack, int charges) {
			stack.set(BLOOD_CHARGES, Mth.clamp(charges, 0, MAX_CHARGES));
		}

		@Override
		public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
			if (!target.isAlive() && target.getMaxHealth() >= WORTHWHILE_KILL_HEALTH) {
				setCharges(stack, charges(stack) + 1);
			}
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			if (charges(stack) <= 0) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server) {
				laser(server, player);
				if (!player.getAbilities().instabuild) {
					setCharges(stack, charges(stack) - 1);
				}
			}
			player.getCooldowns().addCooldown(stack, LASER_COOLDOWN);
			return InteractionResult.SUCCESS;
		}

		/** A line of red from the wielder's eye along their gaze, stopped by the first block, hurting and burning what it crosses. */
		private static void laser(ServerLevel level, Player player) {
			Vec3 from = player.getEyePosition().add(0.0, -0.3, 0.0);
			Vec3 to = from.add(player.getViewVector(1.0F).scale(LASER_RANGE));
			HitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
			level.playSound(null, player.blockPosition(), NetherBeasts.NEHEMOTH_BEAM, SoundSource.PLAYERS, 3.0F, 0.8F + player.getRandom().nextFloat() * 0.1F);
			Fx.beam(level, from, end, 0.2F, LASER_COLOUR, 4);
			level.sendParticles(ParticleTypes.FLAME, end.x, end.y, end.z, 6, 0.3, 0.3, 0.3, 0.05);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(LASER_WIDTH + 1.0),
				t -> t != player && t.isAlive() && !t.isAlliedTo(player))) {
				if (e.getBoundingBox().inflate(LASER_WIDTH).clip(from, end).isPresent() && e.hurtServer(level, level.damageSources().indirectMagic(player, player), LASER_DAMAGE)) {
					e.igniteForSeconds(LASER_FIRE_SECONDS);
				}
			}
		}

		@Override
		public ItemUseAnimation getUseAnimation(ItemStack stack) {
			return ItemUseAnimation.BOW;
		}

		@Override
		public boolean isBarVisible(ItemStack stack) {
			return charges(stack) < MAX_CHARGES;
		}

		@Override
		public int getBarWidth(ItemStack stack) {
			return Math.round(13.0F * charges(stack) / MAX_CHARGES);
		}

		@Override
		public int getBarColor(ItemStack stack) {
			return CHARGE_BAR_COLOUR;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("tooltip.jaymoskingdoms.thirst_for_blood.charges", charges(stack), MAX_CHARGES).withStyle(ChatFormatting.DARK_RED));
			builder.accept(Component.translatable("tooltip.jaymoskingdoms.thirst_for_blood.1").withStyle(ChatFormatting.BLUE));
			builder.accept(Component.translatable("tooltip.jaymoskingdoms.thirst_for_blood.2").withStyle(ChatFormatting.BLUE));
		}
	}

	/** The crystallized dagger: quick and short; see {@link #quicken} and the fragments of its kills. */
	static final class Dagger extends Item {
		Dagger(Item.Properties properties) {
			super(properties);
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("tooltip.jaymoskingdoms.crystallized_dagger.1").withStyle(ChatFormatting.BLUE));
			builder.accept(Component.translatable("tooltip.jaymoskingdoms.crystallized_dagger.2").withStyle(ChatFormatting.BLUE));
		}
	}

	/**
	 * The Horn of Apocalypse: blown, it cuts everything alive near (Powers' wounds open a cut for a tearing blow, as the beasts'
	 * do) and blocks its healing (Heal Block, if the effect is there: it is registered by the effects' port).
	 */
	static final class Horn extends Item {
		Horn(Item.Properties properties) {
			super(properties);
		}

		@Override
		public ItemUseAnimation getUseAnimation(ItemStack stack) {
			return ItemUseAnimation.TOOT_HORN;
		}

		@Override
		public int getUseDuration(ItemStack stack, LivingEntity user) {
			return HORN_USE_TICKS;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			player.startUsingItem(hand);
			ItemStack stack = player.getItemInHand(hand);
			if (level instanceof ServerLevel server) {
				server.playSound(null, player.blockPosition(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(5).value(), SoundSource.PLAYERS, 4.0F, 0.2F);
				Fx.ring(server, player.position().add(0.0, 0.2, 0.0), (float)HORN_RANGE, 0x701018);
				for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(HORN_RANGE),
					t -> t != player && NetherBeasts.fair(t) && !t.isAlliedTo(player) && t.getTeam() == null)) {
					BuiltInRegistries.MOB_EFFECT.get(JaymosKingdoms.id("heal_block")).ifPresent(effect -> e.addEffect(new MobEffectInstance(effect, HORN_TICKS, 0)));
					NetherBeasts.tear(server, player, e, HORN_CUT);
				}
			}
			player.getCooldowns().addCooldown(stack, HORN_COOLDOWN);
			return InteractionResult.CONSUME;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("tooltip.jaymoskingdoms.horn_of_apocalypse").withStyle(ChatFormatting.BLUE));
		}
	}
}
