package com.jaymoskingdoms.dragons;

import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.PowersCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.137) Boreas, the north wind: the second of the five wind dragons ({@link WindDragon}). A long, lean frost dragon
 * of the jagged peaks and the ice spikes, lighter and thinner-skinned than Notus (ice counts as solid for its perches,
 * and so does the snow on a peak). It barely flaps: it glides on long wings, and it dives in silence, with no roar before
 * the swoop and soft wingbeats, faster than Notus; ridden, it tires slowly aloft. Its breath is a cone of freezing mist
 * ({@link DragonFrost}: it freezes and slows what it meets, puts out fires, and turns still water to ice for a while); it
 * never freezes itself. Its favourite is raw salmon; the Frostborn and Cryokinesis users win it over twice as fast.
 */
public class BoreasDragon extends WindDragon {
	static final double HEALTH = 180.0, ARMOR = 8.0, TOUGHNESS = 3.0, SPEED = 0.24, BITE = 10.0;
	/** How fast it cruises and dives wild (Notus: 0.55 and 1.15). */
	static final double CRUISE = 0.7, SWOOP = 1.35;
	/** Ridden: stamina a second flapping (Notus 1.6) and gliding (Notus 0.25). */
	static final float FLAP_DRAIN = 1.2F, GLIDE_DRAIN = 0.1F;
	/** How loud its wingbeats are (Notus 2). */
	static final float FLAP_VOLUME = 0.4F;
	/** Its riders' seat height (blocks): the model's back top is 1.5, the saddle 0.1 over it. */
	static final double SEAT_HEIGHT = 1.6;
	/** Its mouth, on the ground and in the air: how far ahead of its middle, and how high above its feet. */
	static final double MOUTH_AHEAD_GROUND = 3.3, MOUTH_UP_GROUND = 2.2, MOUTH_AHEAD_AIR = 4.2, MOUTH_UP_AIR = 1.3;

	public BoreasDragon(EntityType<? extends BoreasDragon> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return dragonAttributes(HEALTH, ARMOR, TOUGHNESS, SPEED, BITE);
	}

	@Override
	public boolean favourite(ItemStack stack) {
		return stack.is(Items.SALMON);
	}

	@Override
	protected void breathe(ServerLevel level, Vec3 mouth, Vec3 aim, boolean griefing) {
		DragonFrost.breathe(level, this, mouth, aim, this.tickCount, griefing);
	}

	@Override
	protected float bondFactor(Player player) {
		return Heritage.is(player, Heritage.Kind.FROSTBORN) || "cryo".equals(PowersCompat.power(player)) ? BOND_FACTOR : 1.0F;
	}

	@Override
	protected double breathRange() {
		return DragonFrost.RANGE;
	}

	@Override
	protected double mouthAhead(boolean flying) {
		return flying ? MOUTH_AHEAD_AIR : MOUTH_AHEAD_GROUND;
	}

	@Override
	protected double mouthUp(boolean flying) {
		return flying ? MOUTH_UP_AIR : MOUTH_UP_GROUND;
	}

	@Override
	protected Vec3 seat(int index) {
		return new Vec3(0.0, SEAT_HEIGHT, index == 0 ? 0.25 : -0.55);
	}

	@Override
	protected double cruiseSpeed() {
		return CRUISE;
	}

	@Override
	protected double swoopSpeed() {
		return SWOOP;
	}

	@Override
	protected float flapDrain() {
		return FLAP_DRAIN;
	}

	@Override
	protected float glideDrain() {
		return GLIDE_DRAIN;
	}

	@Override
	protected float flapVolume() {
		return FLAP_VOLUME;
	}

	/** A silent diver: no roar before the swoop. */
	@Override
	protected boolean roarsBeforeSwoop() {
		return false;
	}

	@Override
	protected BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.WHITE;
	}

	/** A peak is often snow over rock (a layer of snow isn't solid): it perches on that too, with room over it. */
	@Override
	protected boolean perchable(ServerLevel level, BlockPos top) {
		return super.perchable(level, top)
			|| level.getBlockState(top).is(Blocks.SNOW) && level.getBlockState(top.below()).isSolid() && room(level, top);
	}

	/** The north wind never freezes itself, however long it hangs in powder snow. */
	@Override
	public boolean canFreeze() {
		return false;
	}
}
