package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Wands and staffs that hold charges. Each holds a few; a use spends one; at every sunrise (a new day) they are full again,
 * wherever they were kept. Spend the last one and the wand may crumble to dust ({@link #CRUMBLE}). They need no mana and
 * no attunement: anyone may point one. They are found in old chests and held by court wizards' vaults, never made.
 * <ul>
 * <li><b>Wand of embers:</b> a bolt of fire at the one you look at. <b>Wand of frost:</b> a ray that bites and slows.</li>
 * <li><b>Wand of mending:</b> heals who you look at, or you. <b>Wand of binding:</b> holds one fast for a few seconds.</li>
 * <li><b>Staff of gales:</b> a hard wind that throws back all before you. <b>Staff of warding:</b> a shield of light over you and your friends.</li>
 * </ul>
 */
public final class ChargedWand extends Item {
	/** The chance the wand crumbles when its last charge is spent. */
	static final float CRUMBLE = 0.25F;
	/** The wait between uses (ticks). */
	static final int COOLDOWN = 12;
	/** The bolts' reach (blocks), the damage of embers and of frost, how long fire burns (seconds), and how long frost and a hold last (ticks). */
	static final double REACH = 24.0;
	static final float EMBERS = 6.0F, FROST = 4.0F, MENDING = 8.0F;
	static final float BURN_SECONDS = 5.0F;
	static final int CHILL_TICKS = 100, BIND_TICKS = 20 * 6;
	/** The gale's reach, push and lift; the ward's reach, absorption (amplifier) and time (ticks). */
	static final double GALE_REACH = 9.0, GALE_PUSH = 1.5, GALE_LIFT = 0.35;
	static final double WARD_REACH = 6.0;
	static final int WARD_AMP = 1, WARD_TICKS = 20 * 60;
	/** Custom data: the charges left, and the day they were last filled. */
	static final String CHARGES = "jaymos_charges", DAY = "jaymos_dawn";
	static final long DAY_TICKS = 24000L;

	/** The six. */
	public enum Kind {
		EMBERS("wand_of_embers", 7, 0xFF8A2A, false), FROST("wand_of_frost", 7, 0x9FE0FF, false), MENDING("wand_of_mending", 6, 0x8AF0A0, false),
		BINDING("wand_of_binding", 5, 0xC8A0FF, false), GALES("staff_of_gales", 5, 0xDDEEFF, true), WARDING("staff_of_warding", 4, 0xFFE9A0, true);

		public final String id;
		public final int charges;
		public final int colour;
		public final boolean staff;

		Kind(String id, int charges, int colour, boolean staff) {
			this.id = id;
			this.charges = charges;
			this.colour = colour;
			this.staff = staff;
		}
	}

	private static final Map<Kind, Item> ITEMS = new EnumMap<>(Kind.class);

	private final Kind kind;

	private ChargedWand(Properties properties, Kind kind) {
		super(properties);
		this.kind = kind;
	}

	static void register() {
		for (Kind kind : Kind.values()) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(kind.id));
			ITEMS.put(kind, Registry.register(BuiltInRegistries.ITEM, key,
				new ChargedWand(new Item.Properties().setId(key).stacksTo(1).rarity(kind.staff ? Rarity.RARE : Rarity.UNCOMMON), kind)));
		}
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> ITEMS.values().forEach(output::accept));
	}

	/** The item for {@code kind}. */
	public static Item item(Kind kind) {
		return ITEMS.get(kind);
	}

	/** Every wand and staff, for the loot tables. */
	public static List<Item> all() {
		return new ArrayList<>(ITEMS.values());
	}

	// ---- the charges -----------------------------------------------------------------------------------------------------

	/** The charges left in {@code stack}: all of them if it has never been written. */
	public static int charges(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		int most = stack.getItem() instanceof ChargedWand w ? w.kind.charges : 0;
		return data == null ? most : Math.min(most, data.copyTag().getIntOr(CHARGES, most));
	}

	private static void setCharges(ItemStack stack, int charges, long day) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			tag.putInt(CHARGES, charges);
			tag.putLong(DAY, day);
		});
	}

	/** The day number a level is on (a new one begins at every sunrise). */
	static long today(ServerLevel level) {
		return Math.floorDiv(level.getOverworldClockTime(), DAY_TICKS);
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		long day = today(level);
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		long last = data == null ? day : data.copyTag().getLongOr(DAY, day);
		if (data == null) {
			setCharges(stack, this.kind.charges, day);
		} else if (last < day) {
			boolean was = charges(stack) < this.kind.charges;
			setCharges(stack, this.kind.charges, day);
			if (was && owner instanceof ServerPlayer player) {
				level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.5F);
				level.sendParticles(Fx.mote(this.kind.colour, 0.7F), player.getX(), player.getY(1.0), player.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
			}
		}
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return charges(stack) < this.kind.charges;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13.0F * charges(stack) / this.kind.charges);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return this.kind.colour;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.jaymoskingdoms.wand." + this.kind.id).withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("tooltip.jaymoskingdoms.wand.charges", charges(stack), this.kind.charges).withStyle(ChatFormatting.AQUA));
		builder.accept(Component.translatable("tooltip.jaymoskingdoms.wand.dawn").withStyle(ChatFormatting.DARK_GRAY));
	}

	// ---- using one -------------------------------------------------------------------------------------------------------

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		int left = charges(stack);
		if (left <= 0) {
			level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.8F, 1.2F);
			sp.getCooldowns().addCooldown(stack, COOLDOWN);
			return InteractionResult.FAIL;
		}
		if (!fire(server, sp)) {
			level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 1.6F);
			return InteractionResult.FAIL;
		}
		setCharges(stack, left - 1, today(server));
		sp.getCooldowns().addCooldown(stack, COOLDOWN);
		if (left - 1 <= 0 && sp.getRandom().nextFloat() < CRUMBLE) {
			crumble(server, sp, stack);
		}
		return InteractionResult.SUCCESS;
	}

	private static void crumble(ServerLevel level, ServerPlayer player, ItemStack stack) {
		stack.shrink(1);
		level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY(1.2), player.getZ(), 14, 0.3, 0.3, 0.3, 0.03);
		level.playSound(null, player.blockPosition(), SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 0.9F, 0.6F);
	}

	private boolean fire(ServerLevel level, ServerPlayer player) {
		Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(0.7)).subtract(0.0, 0.2, 0.0);
		LivingEntity target = Arcana.sighted(player);
		Vec3 to = target != null ? target.getBoundingBox().getCenter() : NewSpells.aim(level, player, REACH).add(0.0, 0.3, 0.0);
		switch (this.kind) {
			case EMBERS -> {
				Fx.beam(level, from, to, 0.18F, this.kind.colour, 8);
				if (target != null) {
					target.hurtServer(level, level.damageSources().indirectMagic(player, player), EMBERS);
					target.igniteForSeconds(BURN_SECONDS);
				}
				level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.9F, 1.1F);
			}
			case FROST -> {
				Fx.beam(level, from, to, 0.16F, this.kind.colour, 8);
				if (target != null) {
					target.hurtServer(level, level.damageSources().indirectMagic(player, player), FROST);
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CHILL_TICKS, 1), player);
					target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze()));
				}
				level.playSound(null, player.blockPosition(), SoundEvents.SNOW_GOLEM_SHOOT, SoundSource.PLAYERS, 0.9F, 0.8F);
			}
			case MENDING -> {
				LivingEntity who = target != null && (target instanceof Player || target instanceof TamableAnimal || target instanceof net.minecraft.world.entity.npc.villager.Villager) ? target : player;
				Fx.beam(level, from, who.getBoundingBox().getCenter(), 0.14F, this.kind.colour, 8);
				if (PowersCompat.healEveryPart(who, MENDING / 2.0F) < 0.0F) {
					who.heal(MENDING);
				}
				level.sendParticles(ParticleTypes.HEART, who.getX(), who.getY(1.2), who.getZ(), 4, 0.3, 0.3, 0.3, 0.0);
				level.playSound(null, who.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.9F, 1.3F);
			}
			case BINDING -> {
				if (target == null) {
					return false;
				}
				Fx.beam(level, from, to, 0.14F, this.kind.colour, 8);
				if (!Saves.resist(target, Saves.Kind.HOLD)) {
					HeldFast.hold(target, BIND_TICKS);
				}
				level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.8F, 1.2F);
			}
			case GALES -> {
				Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
				if (look.lengthSqr() < 1.0E-4) {
					return false;
				}
				look = look.normalize();
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(GALE_REACH), x -> x != player && x.isAlive())) {
					Vec3 away = e.position().subtract(player.position());
					if (away.horizontalDistance() > GALE_REACH || away.normalize().dot(look) < 0.4) {
						continue;
					}
					e.setDeltaMovement(e.getDeltaMovement().add(look.scale(GALE_PUSH).add(0.0, GALE_LIFT, 0.0)));
					e.syncVelocity = true;
				}
				Fx.ring(level, player.position().add(look.scale(2.0)), 4.0F, this.kind.colour);
				level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.0F, 0.9F);
			}
			case WARDING -> {
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(WARD_REACH),
					x -> x.isAlive() && (x == player || x instanceof Player || x instanceof TamableAnimal t && t.isOwnedBy(player)))) {
					e.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, WARD_TICKS, WARD_AMP));
				}
				Fx.ring(level, player.position().add(0.0, 0.1, 0.0), 3.0F, this.kind.colour);
				Fx.sigil(level, player.position().add(0.0, 0.05, 0.0), 2.0F, this.kind.colour, 40);
				level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7F, 1.6F);
			}
		}
		return true;
	}
}
