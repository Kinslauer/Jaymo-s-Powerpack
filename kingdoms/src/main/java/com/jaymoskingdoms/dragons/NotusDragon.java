package com.jaymoskingdoms.dragons;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import com.jaymoskingdoms.Heritage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.137) Notus, the south wind: the first of the five wind dragons ({@link WindDragon}). A heavy fire dragon of the
 * badlands and the basalt deltas, with a clubbed tail and spikes down its back, that circles high and perches on the
 * tallest spires about. Its breath is a cone of fire ({@link DragonBreath}: creatures and blocks catch; a wild one minds
 * the mobGriefing rule). Its favourite is cooked mutton; Emberborn win it over twice as fast.
 */
public class NotusDragon extends WindDragon implements GeoEntity {
	static final double HEALTH = 220.0, ARMOR = 14.0, TOUGHNESS = 6.0, SPEED = 0.22, BITE = 12.0;

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	public NotusDragon(EntityType<? extends NotusDragon> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return dragonAttributes(HEALTH, ARMOR, TOUGHNESS, SPEED, BITE);
	}

	@Override
	public boolean favourite(ItemStack stack) {
		return stack.is(Items.COOKED_MUTTON);
	}

	@Override
	protected void breathe(ServerLevel level, Vec3 mouth, Vec3 aim, boolean griefing) {
		DragonBreath.breathe(level, this, mouth, aim, this.tickCount, griefing);
	}

	@Override
	protected float bondFactor(Player player) {
		return Heritage.is(player, Heritage.Kind.EMBERBORN) ? BOND_FACTOR : 1.0F;
	}

	/** Drawn and animated by GeckoLib ({@link FireDragonAnimations}). */
	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		FireDragonAnimations.register(controllers);
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	@Override
	protected void bitten(LivingEntity target) {
		this.triggerAnim("action", "bite");
	}

	@Override
	protected void swept() {
		this.triggerAnim("action", "sweep");
	}
}
