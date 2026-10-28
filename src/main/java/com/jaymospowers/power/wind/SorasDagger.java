package com.jaymospowers.power.wind;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Powers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Sora's Dagger: Wind's weapon, a curved dagger that grows with you over four stages.
 * <ol>
 * <li><b>Iron</b>, forged (0.17.110): a Sora's Dagger Blade with a Tool Handle and a Tool Binding at the Tool Station
 * ({@link com.jaymospowers.tinkering.WeaponParts}): a quick plain dagger.</li>
 * <li><b>Gilded</b> (smithing: a feather, the dagger, a gold block): the wind follows its edge, more knockback and a
 * puff of air on every hit.</li>
 * <li><b>Diamond</b> (smithing: a breeze rod, the gilded dagger, a diamond block): harder knockback, and right click
 * unleashes a <b>Flurry</b>, a run of quick slashes at everything in front of you.</li>
 * <li><b>Tempest</b>: only a wind that has ascended (the Tempest) can forge it. Sneak and right click the diamond dagger
 * carrying the spoils of three bosses (a Nether Star, Dragon's Breath and a Heavy Core). Its Flurry is longer, and in a
 * wind user's hand it's carried by the winds: every slash a gust, a lunge on the wind, and a burst at the end.</li>
 * </ol>
 * Upgrading keeps what's on the dagger (enchantments, name, wear). (0.17.110) Anyone can find the blade and forge it, but
 * no stage stays in anyone's hand but a wind's. Only one dagger line is out at a time ({@link SorasLineage}).
 */
public final class SorasDagger extends Item {
	public static final int IRON = 0, GILDED = 1, DIAMOND = 2, TEMPEST = 3;

	public static SorasDagger[] STAGES = new SorasDagger[4];

	/** Per stage: attack damage over a fist's 1, attack speed (vanilla's 4 plus this), and extra knockback (a wind's hits only). */
	private static final float[] DAMAGE = {4.0F, 5.0F, 6.0F, 8.0F};
	private static final float[] SPEED = {-1.8F, -1.8F, -1.7F, -1.6F};
	private static final float[] KNOCKBACK = {0.0F, 0.5F, 1.0F, 1.5F};
	private static final ToolMaterial GILDED_MATERIAL = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, 500, 6.0F, 0.0F, 22,
		ItemTags.GOLD_TOOL_MATERIALS);
	private static final ToolMaterial TEMPEST_MATERIAL = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2600, 9.0F, 0.0F, 18,
		ItemTags.DIAMOND_TOOL_MATERIALS);

	/** The Flurry: slashes, ticks between them, reach (blocks), how wide (cosine of the half-angle), damage per slash (Ã— the dagger's), cooldown. */
	static final int FLURRY_SLASHES = 5, FLURRY_SLASHES_TEMPEST = 7, FLURRY_SLASHES_WIND = 10;
	static final int FLURRY_EVERY = 3;
	static final double FLURRY_REACH = 3.4, FLURRY_REACH_TEMPEST = 4.0, FLURRY_REACH_WIND = 5.5;
	static final double FLURRY_CONE = 0.35;
	static final float FLURRY_DAMAGE = 0.55F, FLURRY_DAMAGE_WIND = 0.65F;
	static final int FLURRY_COOLDOWN = 140, FLURRY_COOLDOWN_TEMPEST = 100;
	/** How far each slash carries you forward (blocks/tick), and on the wind. */
	static final double FLURRY_LUNGE = 0.16, FLURRY_LUNGE_WIND = 0.38;
	/** The winds' slashes throw what they hit (and the last one bursts out this far, this hard). */
	static final double WIND_SLASH_PUSH = 0.55, WIND_SLASH_LIFT = 0.22, WIND_BURST_RADIUS = 5.0, WIND_BURST_PUSH = 1.4;
	/** The Tempest mode (Z) and the ascension each make the winds' Flurry hit harder. */
	static final float TEMPEST_MODE_BONUS = 1.25F;

	private static final SwingAnimation SLASH = new SwingAnimation(SwingAnimationType.WHACK, FLURRY_EVERY);

	private record Flurry(ServerLevel level, UUID player, int stage, boolean wind, int slashes, int step) {
	}

	private static final Map<UUID, Flurry> FLURRIES = new HashMap<>();

	public final int stage;

	private SorasDagger(int stage, Item.Properties properties) {
		super(properties);
		this.stage = stage;
	}

	public static void register() {
		String[] ids = {"soras_dagger", "soras_dagger_gold", "soras_dagger_diamond", "soras_dagger_tempest"};
		ToolMaterial[] materials = {ToolMaterial.IRON, GILDED_MATERIAL, ToolMaterial.DIAMOND, TEMPEST_MATERIAL};
		Rarity[] rarities = {Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC};
		for (int stage = IRON; stage <= TEMPEST; stage++) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(ids[stage]));
			List<Component> lore = new ArrayList<>();
			lore.add(Component.translatable("item.jaymospowers.soras_dagger.stage" + stage).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			lore.add(Component.translatable("item.jaymospowers.soras_dagger.hint" + stage).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			Item.Properties properties = new Item.Properties().setId(key)
				.sword(materials[stage], 0.0F, SPEED[stage])
				.attributes(attributes(stage))
				.rarity(rarities[stage])
				.component(DataComponents.LORE, new ItemLore(lore));
			if (stage == TEMPEST) {
				properties = properties.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true).fireResistant();
			}
			STAGES[stage] = Registry.register(BuiltInRegistries.ITEM, key, new SorasDagger(stage, properties));
		}
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output ->
			output.insertAfter(Items.NETHERITE_SWORD, STAGES[IRON], STAGES[GILDED], STAGES[DIAMOND], STAGES[TEMPEST]));
		ServerTickEvents.END_SERVER_TICK.register(SorasDagger::tick);
	}

	private static ItemAttributeModifiers attributes(int stage) {
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder()
			.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, DAMAGE[stage], AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.MAINHAND)
			.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, SPEED[stage], AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.MAINHAND);
		return builder.build();
	}

	/** Its gifts only answer a wind. */
	static boolean wind(LivingEntity holder) {
		return holder instanceof Player player && Powers.has(player, WindPower.INSTANCE);
	}

	/**
	 * The wind in its edge (a wind's hand only): from the gilded stage on, a hit carries a puff of air and throws harder
	 * (and the later stages lift a little).
	 */
	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		super.postHurtEnemy(stack, target, attacker);
		if (this.stage == IRON || !wind(attacker) || !(target.level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 away = target.position().subtract(attacker.position()).horizontal();
		if (away.lengthSqr() > 1.0E-4) {
			target.setDeltaMovement(target.getDeltaMovement().add(away.normalize().scale(KNOCKBACK[this.stage] * 0.5)));
			target.syncVelocity = true;
		}
		Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
		level.sendParticles(this.stage == TEMPEST ? ParticleTypes.GUST_EMITTER_SMALL : ParticleTypes.SMALL_GUST, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.25F + 0.1F * this.stage, 1.6F);
		if (this.stage >= DIAMOND) {
			target.setDeltaMovement(target.getDeltaMovement().add(0.0, this.stage == TEMPEST ? 0.22 : 0.1, 0.0));
			target.syncVelocity = true;
		}
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (this.stage < DIAMOND || hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}
		if (this.stage == DIAMOND && player.isShiftKeyDown()) {
			forge(serverPlayer, serverLevel, stack);
			return InteractionResult.SUCCESS;
		}
		if (!wind(serverPlayer)) {
			// only a wind can call up the Flurry
			return InteractionResult.PASS;
		}
		if (player.getCooldowns().isOnCooldown(stack) || FLURRIES.containsKey(player.getUUID())) {
			return InteractionResult.FAIL;
		}
		boolean wind = this.stage == TEMPEST;
		int slashes = wind ? FLURRY_SLASHES_WIND : this.stage == TEMPEST ? FLURRY_SLASHES_TEMPEST : FLURRY_SLASHES;
		FLURRIES.put(player.getUUID(), new Flurry(serverLevel, player.getUUID(), this.stage, wind, slashes, 0));
		player.getCooldowns().addCooldown(stack, WindCold.cooldown(player, this.stage == TEMPEST ? FLURRY_COOLDOWN_TEMPEST : FLURRY_COOLDOWN));
		serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), wind ? SoundEvents.BREEZE_CHARGE : SoundEvents.PLAYER_ATTACK_SWEEP,
			SoundSource.PLAYERS, 0.9F, 1.3F);
		return InteractionResult.SUCCESS;
	}

	/**
	 * (0.17.110, was the Tempest dagger's alone) Andy: "anyone can take the blade, anyone can forge it, but only wind users
	 * can use it." No stage will be held by anyone but a wind: it slips from their hand into their pack (or to the ground).
	 */
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, net.minecraft.world.entity.Entity owner, EquipmentSlot slot) {
		if (!(owner instanceof ServerPlayer player) || wind(player)) {
			return;
		}
		InteractionHand hand = player.getMainHandItem() == stack ? InteractionHand.MAIN_HAND : player.getOffhandItem() == stack ? InteractionHand.OFF_HAND : null;
		if (hand == null) {
			return;
		}
		ItemStack held = stack.copy();
		player.setItemInHand(hand, ItemStack.EMPTY);
		boolean stowed = false;
		for (int i = 9; i < 36 && !stowed; i++) {
			if (player.getInventory().getItem(i).isEmpty()) {
				player.getInventory().setItem(i, held);
				stowed = true;
			}
		}
		if (!stowed) {
			player.drop(held, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.soras_dagger.refuses").withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_DEFLECT, SoundSource.PLAYERS, 0.8F, 1.2F);
		level.sendParticles(ParticleTypes.GUST, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
	}

	// ---------------------------------------------------------------- the Flurry

	private static void tick(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Flurry>> it = FLURRIES.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Flurry> entry = it.next();
			Flurry flurry = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(flurry.player());
			// it ends if you die, leave, or put the dagger away
			if (player == null || !player.isAlive() || player.level() != flurry.level()
				|| !(player.getMainHandItem().getItem() instanceof SorasDagger dagger) || dagger.stage < DIAMOND) {
				it.remove();
				continue;
			}
			if (flurry.step() % FLURRY_EVERY == 0) {
				int index = flurry.step() / FLURRY_EVERY;
				slash(player, flurry, index);
				if (index + 1 >= flurry.slashes()) {
					if (flurry.wind()) {
						burst(player);
					}
					it.remove();
					continue;
				}
			}
			entry.setValue(new Flurry(flurry.level(), flurry.player(), flurry.stage(), flurry.wind(), flurry.slashes(), flurry.step() + 1));
		}
	}

	private static void slash(ServerPlayer player, Flurry flurry, int index) {
		ServerLevel level = player.level();
		ItemStack stack = player.getMainHandItem();
		boolean tempest = flurry.stage() == TEMPEST;
		double reach = flurry.wind() ? FLURRY_REACH_WIND : tempest ? FLURRY_REACH_TEMPEST : FLURRY_REACH;
		float damage = (float)player.getAttributeValue(Attributes.ATTACK_DAMAGE) * (flurry.wind() ? FLURRY_DAMAGE_WIND : FLURRY_DAMAGE);
		if (flurry.wind() && (WindModes.tempest(player) || Fate.isAscended(player))) {
			damage *= TEMPEST_MODE_BONUS;
		}
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z).normalize();
		// alternating swings, a short lunge with each
		player.swing(index % 2 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, SLASH, true);
		double lunge = flurry.wind() ? FLURRY_LUNGE_WIND : FLURRY_LUNGE;
		player.setDeltaMovement(player.getDeltaMovement().add(flat.scale(lunge)));
		player.syncVelocity = true;
		Vec3 eye = player.getEyePosition();
		Vec3 sweep = eye.add(look.scale(1.6)).add(0.0, -0.3, 0.0);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, sweep.x, sweep.y, sweep.z, 1, 0.3, 0.15, 0.3, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7F,
			1.1F + 0.1F * (index % 4));
		if (flurry.wind()) {
			level.sendParticles(ParticleTypes.GUST, sweep.x + look.x, sweep.y + look.y, sweep.z + look.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ParticleTypes.CLOUD, sweep.x, sweep.y, sweep.z, 6, 0.6, 0.3, 0.6, 0.08);
		}
		boolean hit = false;
		AABB box = player.getBoundingBox().inflate(reach);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive() && !e.isSpectator()
			&& !player.isAlliedTo(e) && !(e instanceof TamableAnimal pet && pet.isOwnedBy(player)))) {
			Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
			double distance = to.length();
			if (distance > reach + target.getBbWidth() * 0.5 || distance > 1.0E-3 && to.scale(1.0 / distance).dot(look) < FLURRY_CONE) {
				continue;
			}
			// every slash lands, however close together (no hurt cooldown between them)
			target.setInvulnerableTime(0);
			Vec3 before = target.getDeltaMovement();
			if (target.hurtServer(level, player.damageSources().playerAttack(player), damage)) {
				hit = true;
				// the diamond Flurry holds them in place (no knockback); the winds' throws them
				target.setDeltaMovement(flurry.wind() ? target.getDeltaMovement().add(flat.scale(WIND_SLASH_PUSH)).add(0.0, WIND_SLASH_LIFT, 0.0) : before);
				target.syncVelocity = true;
				level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 4, 0.2, 0.2, 0.2, 0.2);
			}
		}
		if (hit && !player.isCreative()) {
			stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
		}
	}

	/** The winds' last word: everything near is thrown out and up. */
	private static void burst(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 center = player.position().add(0.0, 1.0, 0.0);
		level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.4F, 0.8F);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(WIND_BURST_RADIUS),
			e -> e != player && e.isAlive() && !player.isAlliedTo(e) && !(e instanceof TamableAnimal pet && pet.isOwnedBy(player)))) {
			Vec3 away = target.position().subtract(player.position());
			double distance = away.length();
			if (distance > WIND_BURST_RADIUS || distance < 1.0E-3) {
				continue;
			}
			double strength = WIND_BURST_PUSH * (1.0 - distance / (WIND_BURST_RADIUS + 1.0));
			target.setDeltaMovement(target.getDeltaMovement().add(new Vec3(away.x, 0.0, away.z).normalize().scale(strength)).add(0.0, strength * 0.6, 0.0));
			target.syncVelocity = true;
		}
	}

	// ---------------------------------------------------------------- the Tempest forging

	/** The three spoils the forging takes. */
	private static final Item[] SPOILS = {Items.NETHER_STAR, Items.DRAGON_BREATH, Items.HEAVY_CORE};

	private static void forge(ServerPlayer player, ServerLevel level, ItemStack stack) {
		if (!Powers.has(player, WindPower.INSTANCE) || !Fate.isAscended(player)) {
			// the winds don't answer: only the Tempest can wake it
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.soras_dagger.unanswered").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_IDLE_GROUND, SoundSource.PLAYERS, 0.7F, 0.5F);
			player.getCooldowns().addCooldown(stack, 20);
			return;
		}
		List<Component> missing = new ArrayList<>();
		for (Item spoil : SPOILS) {
			if (!player.getInventory().contains(new ItemStack(spoil)) && !player.isCreative()) {
				missing.add(new ItemStack(spoil).getHoverName());
			}
		}
		if (!missing.isEmpty()) {
			Component list = Component.empty();
			for (int i = 0; i < missing.size(); i++) {
				list = list.copy().append(i == 0 ? Component.empty() : Component.literal(", ")).append(missing.get(i));
			}
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.soras_dagger.missing", list).withStyle(ChatFormatting.AQUA));
			player.getCooldowns().addCooldown(stack, 20);
			return;
		}
		if (!player.isCreative()) {
			for (Item spoil : SPOILS) {
				int slot = player.getInventory().findSlotMatchingItem(new ItemStack(spoil));
				if (slot >= 0) {
					player.getInventory().removeItem(slot, 1);
				}
			}
		}
		ItemStack forged = stack.transmuteCopy(STAGES[TEMPEST]);
		forged.setDamageValue(0);
		player.setItemInHand(InteractionHand.MAIN_HAND, forged);
		Vec3 at = player.position();
		level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, at.x, at.y + 1.0, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.0, at.z, 60, 0.6, 1.0, 0.6, 0.12);
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 40, 1.2, 0.2, 1.2, 0.1);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.8F, 1.4F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.5F, 0.6F);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.soras_dagger.forged").withStyle(ChatFormatting.AQUA));
	}
}
