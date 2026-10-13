package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.CourtWizards;
import com.jaymoskingdoms.Enlisted;
import com.jaymoskingdoms.Sorcery;
import com.jaymoskingdoms.Worker;
import com.jaymoskingdoms.Workers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.5.21) An MCA villager can be one of a settlement's {@link Worker}s: their job, their faction, their work and what
 * they're carrying are kept here (and saved). Their day is {@link Workers#step}'s ({@code VillagerWorkMixin}).
 */
@Mixin(VillagerEntityMCA.class)
abstract class McaWorkerMixin implements Enlisted {
	@Unique
	private int jaymoskingdoms$role = -1, jaymoskingdoms$faction = -1;
	@Unique
	private @Nullable BlockPos jaymoskingdoms$site;
	@Unique
	private String jaymoskingdoms$task = "";
	@Unique
	private boolean jaymoskingdoms$carrying;
	/** (0.5.53) When they came of age, and a court wizard's spell timers. */
	@Unique
	private long jaymoskingdoms$grownSince = CourtWizards.UNKNOWN;
	@Unique
	private final int[] jaymoskingdoms$spellTimers = new int[Sorcery.TIMERS];

	@Unique
	private VillagerEntityMCA jaymoskingdoms$self() {
		return (VillagerEntityMCA)(Object)this;
	}

	@Override
	public PathfinderMob mob() {
		return this.jaymoskingdoms$self();
	}

	@Override
	public int role() {
		return this.jaymoskingdoms$role;
	}

	@Override
	public int factionId() {
		return this.jaymoskingdoms$faction;
	}

	@Override
	public @Nullable BlockPos site() {
		return this.jaymoskingdoms$site;
	}

	@Override
	public void setSite(@Nullable BlockPos site) {
		this.jaymoskingdoms$site = site == null ? null : site.immutable();
	}

	@Override
	public String task() {
		return this.jaymoskingdoms$task;
	}

	@Override
	public void setTask(String task) {
		this.jaymoskingdoms$task = task == null ? "" : task;
	}

	@Override
	public ItemStack carried() {
		return this.jaymoskingdoms$carrying ? this.jaymoskingdoms$self().getMainHandItem() : ItemStack.EMPTY;
	}

	@Override
	public boolean isCarrying() {
		return this.jaymoskingdoms$carrying && !this.jaymoskingdoms$self().getMainHandItem().isEmpty();
	}

	@Override
	public void setCarried(ItemStack load) {
		this.jaymoskingdoms$carrying = !load.isEmpty();
		this.jaymoskingdoms$self().setItemSlot(EquipmentSlot.MAINHAND, this.jaymoskingdoms$carrying ? load : this.tool());
	}

	@Override
	public ItemStack tool() {
		return Worker.isWork(this.jaymoskingdoms$role) ? Worker.toolFor(this.jaymoskingdoms$role, false) : ItemStack.EMPTY;
	}

	@Override
	public long grownSince() {
		return this.jaymoskingdoms$grownSince;
	}

	@Override
	public void setGrownSince(long time) {
		this.jaymoskingdoms$grownSince = time;
	}

	@Override
	public int[] spellTimers() {
		return this.jaymoskingdoms$spellTimers;
	}

	@Override
	public void enlist(int role, int factionId) {
		this.jaymoskingdoms$role = role;
		this.jaymoskingdoms$faction = factionId;
		this.jaymoskingdoms$task = "";
	}

	@Override
	public void discharge() {
		VillagerEntityMCA self = this.jaymoskingdoms$self();
		if (this.isCarrying() && self.level() instanceof ServerLevel level) {
			self.spawnAtLocation(level, this.takeCarried());
		}
		ItemStack hand = self.getMainHandItem();
		if (!hand.isEmpty() && (hand.is(this.tool().getItem()) || this.jaymoskingdoms$role == Sorcery.WIZARD && CourtWizards.isStaff(hand))) {
			self.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
		this.jaymoskingdoms$role = -1;
		this.jaymoskingdoms$faction = -1;
		this.jaymoskingdoms$site = null;
		this.jaymoskingdoms$task = "";
		this.jaymoskingdoms$carrying = false;
		self.clearHome();
	}

	@Inject(method = "die", at = @At("HEAD"))
	private void jaymoskingdoms$died(DamageSource source, CallbackInfo ci) {
		if (this.jaymoskingdoms$role >= 0 && this.jaymoskingdoms$self().level() instanceof ServerLevel level) {
			Workers.died(level, this, source);
			this.jaymoskingdoms$role = -1;
		}
	}

	@Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
	private void jaymoskingdoms$save(ValueOutput output, CallbackInfo ci) {
		if (this.jaymoskingdoms$grownSince != CourtWizards.UNKNOWN) {
			output.putLong("jaymoskingdoms_grown_since", this.jaymoskingdoms$grownSince);
		}
		if (this.jaymoskingdoms$role < 0) {
			return;
		}
		output.putInt("jaymoskingdoms_role", this.jaymoskingdoms$role);
		output.putInt("jaymoskingdoms_faction", this.jaymoskingdoms$faction);
		if (this.jaymoskingdoms$site != null) {
			output.putLong("jaymoskingdoms_site", this.jaymoskingdoms$site.asLong());
		}
		output.putString("jaymoskingdoms_task", this.jaymoskingdoms$task);
		output.putBoolean("jaymoskingdoms_carrying", this.jaymoskingdoms$carrying);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
	private void jaymoskingdoms$load(ValueInput input, CallbackInfo ci) {
		this.jaymoskingdoms$role = input.getIntOr("jaymoskingdoms_role", -1);
		this.jaymoskingdoms$faction = input.getIntOr("jaymoskingdoms_faction", -1);
		this.jaymoskingdoms$site = input.getLong("jaymoskingdoms_site").map(BlockPos::of).orElse(null);
		this.jaymoskingdoms$task = input.getStringOr("jaymoskingdoms_task", "");
		this.jaymoskingdoms$carrying = input.getBooleanOr("jaymoskingdoms_carrying", false);
		this.jaymoskingdoms$grownSince = input.getLongOr("jaymoskingdoms_grown_since", CourtWizards.UNKNOWN);
	}
}
