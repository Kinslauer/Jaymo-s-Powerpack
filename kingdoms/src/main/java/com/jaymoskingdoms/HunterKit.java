package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ChunkPos;

/**
 * The hunter's kit and the wards against a vampire's bite (Night's Blood patch 7). A vampire's bite meets four things:
 * <ul>
 * <li><b>The wreath</b> ({@link GarlicWreath}), worn in a baubles slot: the bite slips off, and the grab cannot take hold.</li>
 * <li><b>The hunter's coat</b> ({@link #COAT}, sold by tailors and by the Lantern Warden's priests): it turns a bite aside
 * {@link #COAT_TURN} of the time, wearing {@link #COAT_WEAR} points off itself each time, and no grab takes hold of whoever
 * wears it.</li>
 * <li><b>Sour blood</b> ({@link #SOUR_BLOOD}, brewed as the garlic draught from garlic on an awkward potion): the bite lands,
 * but the biter retches, lets go and takes nothing from it (no heal, no blood, no feeding).</li>
 * </ul>
 * Mob vampires ({@link Vampires}) and vampire players ({@link VampirePlayers}) both ask {@link #slips} and {@link #sour}.
 * <p>
 * The kit's third piece, the hunter's oil, is Jaymo's Powers' (a fifth blade oil on the cursed, {@code MonsterKinds}). The Lantern
 * Warden's priests sell the whole kit (the coat, a wreath and the oil) to anyone who has slain a vampire ({@link #SLAYER}):
 * "the Lantern Warden's priests sell the kit to anyone who has killed a vampire".
 */
public final class HunterKit {
	/** The share of bites the coat turns aside, and the wear each takes off it. */
	static final float COAT_TURN = 0.6F;
	static final int COAT_WEAR = 6;
	/** How long the garlic draught sours the blood (ticks): plain and long. */
	static final int SOUR_TICKS = 20 * 60 * 3, LONG_SOUR_TICKS = 20 * 60 * 8;
	/** How long a biter retches (ticks). */
	static final int RETCH_TICKS = 20 * 8;
	/** The priests' prices (emeralds), and the uses and experience each offer carries. */
	static final int COAT_PRICE = 12, WREATH_PRICE = 4, OIL_PRICE = 6, KIT_USES = 4, KIT_XP = 10;
	/** The coat's make: durability multiplier, chest defence, enchantability. */
	private static final int COAT_DURABILITY = 14, COAT_DEFENCE = 4, COAT_ENCHANT = 12;

	/** Whether this player has slain a vampire: it opens the priests' kit. Kept through death. */
	public static final AttachmentType<Boolean> SLAYER = AttachmentRegistry.create(JaymosKingdoms.id("vampire_slayer"),
		b -> b.persistent(Codec.BOOL).copyOnDeath());

	static final ArmorMaterial COAT_MATERIAL = new ArmorMaterial(COAT_DURABILITY, ArmorMaterials.makeDefense(1, 3, COAT_DEFENCE, 1, COAT_DEFENCE), COAT_ENCHANT,
		SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, 0.0F, ItemTags.REPAIRS_LEATHER_ARMOR,
		ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosKingdoms.id("hunters_coat")));

	public static Item COAT;
	public static Holder<MobEffect> SOUR_BLOOD;
	public static Holder<Potion> GARLIC_DRAUGHT, LONG_GARLIC_DRAUGHT;

	private HunterKit() {
	}

	/** Sour blood: it shows only in the wearer's effects; what it does is done when they are bitten ({@link #sour}). */
	private static final class Sour extends MobEffect {
		Sour() {
			super(MobEffectCategory.BENEFICIAL, 0xC8D0A0);
		}
	}

	static void register() {
		ResourceKey<Item> coat = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("hunters_coat"));
		COAT = Registry.register(BuiltInRegistries.ITEM, coat, new Item(new Item.Properties().setId(coat).humanoidArmor(COAT_MATERIAL, ArmorType.CHESTPLATE)));
		SOUR_BLOOD = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, JaymosKingdoms.id("sour_blood"), new Sour());
		GARLIC_DRAUGHT = Registry.registerForHolder(BuiltInRegistries.POTION, JaymosKingdoms.id("garlic_draught"),
			new Potion("garlic_draught", new MobEffectInstance(SOUR_BLOOD, SOUR_TICKS)));
		LONG_GARLIC_DRAUGHT = Registry.registerForHolder(BuiltInRegistries.POTION, JaymosKingdoms.id("long_garlic_draught"),
			new Potion("garlic_draught", new MobEffectInstance(SOUR_BLOOD, LONG_SOUR_TICKS)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.accept(COAT));
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp && world instanceof ServerLevel level && entity instanceof Villager v
				&& v.isAlive() && !v.isBaby() && v.getTradingPlayer() == null && v.getVillagerData().profession().is(VillagerProfession.CLERIC)) {
				offerKit(level, sp, v);
			}
			return InteractionResult.PASS;
		});
	}

	// ---- the wards ----------------------------------------------------------------------------------------------------

	/** Whether {@code who} wears a garlic wreath in a baubles slot (a player; the slots are theirs alone). */
	static boolean wreathed(LivingEntity who) {
		if (!(who instanceof Player player) || GarlicWreath.ITEM == null) {
			return false;
		}
		for (ItemStack worn : PowersCompat.baubles(player)) {
			if (worn.is(GarlicWreath.ITEM)) {
				return true;
			}
		}
		return false;
	}

	/** Whether {@code who} wears the hunter's coat. */
	static boolean coated(LivingEntity who) {
		return COAT != null && who.getItemBySlot(EquipmentSlot.CHEST).is(COAT);
	}

	/** Whether {@code who}'s blood is sour (the garlic draught). */
	static boolean sour(LivingEntity who) {
		return SOUR_BLOOD != null && who.hasEffect(SOUR_BLOOD);
	}

	/** Whether a grab can't take hold of {@code who}: a wreath or the coat. */
	static boolean refusesGrab(LivingEntity who) {
		return wreathed(who) || coated(who);
	}

	/**
	 * Whether {@code biter}'s bite slips off {@code prey}: always with a wreath; with the coat {@link #COAT_TURN} of the time, at
	 * {@link #COAT_WEAR} off the coat. A hiss and a puff of smoke when it does.
	 */
	static boolean slips(ServerLevel level, LivingEntity biter, LivingEntity prey) {
		boolean slips = wreathed(prey);
		if (!slips && coated(prey) && prey.getRandom().nextFloat() < COAT_TURN) {
			slips = true;
			ItemStack coat = prey.getItemBySlot(EquipmentSlot.CHEST);
			coat.hurtAndBreak(COAT_WEAR, prey, EquipmentSlot.CHEST);
		}
		if (slips) {
			level.playSound(null, prey.blockPosition(), SoundEvents.CAT_HISS_BABY.value(), SoundSource.HOSTILE, 0.9F, 0.7F);
			level.sendParticles(ParticleTypes.SMOKE, biter.getX(), biter.getEyeY(), biter.getZ(), 8, 0.2, 0.2, 0.2, 0.02);
		}
		return slips;
	}

	/** {@code biter} drank sour blood: it retches and lets go (no more of it), and gets nothing from the bite. */
	static void retch(ServerLevel level, LivingEntity biter) {
		biter.addEffect(new MobEffectInstance(MobEffects.NAUSEA, RETCH_TICKS, 0));
		biter.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, RETCH_TICKS / 2, 0, false, false, false));
		level.playSound(null, biter.blockPosition(), SoundEvents.PLAYER_BURP, SoundSource.HOSTILE, 0.9F, 0.5F);
		level.sendParticles(ParticleTypes.SPIT, biter.getX(), biter.getEyeY() - 0.2, biter.getZ(), 6, 0.15, 0.1, 0.15, 0.05);
		if (biter instanceof ServerPlayer player) {
			BloodGrab.release(player, false);
		}
	}

	// ---- the priests' kit ---------------------------------------------------------------------------------------------

	/** A vampire died: whoever struck the blow has slain one (a half-blood is not one). */
	static void slew(LivingEntity victim, DamageSource source) {
		if (source.getEntity() instanceof ServerPlayer killer && killer != victim && Vampires.isVampire(victim) && !Vampires.dhampir(victim)
			&& !killer.hasAttached(SLAYER)) {
			killer.setAttached(SLAYER, Boolean.TRUE);
		}
	}

	/** Whether {@code stack} is one of the kit's wares (what a priest's offer gives). */
	private static boolean kit(ItemStack stack) {
		return stack.is(COAT) || stack.is(GarlicWreath.ITEM) || BuiltInRegistries.ITEM.getOptional(oilId()).map(stack::is).orElse(false);
	}

	private static Identifier oilId() {
		return Identifier.fromNamespaceAndPath("jaymospowers", "oil_cursed");
	}

	/**
	 * Puts the kit among a priest's trades if {@code player} has slain a vampire and the priest keeps the Lantern Warden's
	 * faith (their realm's patron), and takes it out again when they have not (the offers are the villager's, shared by
	 * whoever trades with them, so each opening of the trade settles them for the one who opens it).
	 */
	private static void offerKit(ServerLevel level, ServerPlayer player, Villager priest) {
		boolean sells = player.hasAttached(SLAYER) && wardens(level, priest);
		boolean has = false;
		for (MerchantOffer offer : List.copyOf(priest.getOffers())) {
			if (kit(offer.getResult())) {
				has = true;
				if (!sells) {
					priest.getOffers().remove(offer);
				}
			}
		}
		if (!sells || has) {
			return;
		}
		priest.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, COAT_PRICE), new ItemStack(COAT), KIT_USES, KIT_XP, 0.05F));
		priest.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, WREATH_PRICE), new ItemStack(GarlicWreath.ITEM), KIT_USES, KIT_XP, 0.05F));
		BuiltInRegistries.ITEM.getOptional(oilId()).ifPresent(oil ->
			priest.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, OIL_PRICE), new ItemStack(oil), KIT_USES, KIT_XP, 0.05F)));
	}

	/** Whether {@code priest}'s realm keeps the Lantern Warden. */
	private static boolean wardens(ServerLevel level, Villager priest) {
		var server = level.getServer();
		Faction realm = Banners.owner(server, ChunkPos.containing(priest.blockPosition()));
		return realm != null && RealmFaiths.patron(server, realm) == Patron.LANTERN_WARDEN;
	}
}
