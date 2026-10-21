package ic2.forge;

import ic2.core.block.comp.Energy;
import ic2.core.block.tileentity.Ic2TileEntity;
import ic2.core.energy.EnergyBridgeMath;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * Forge Energy view over an IC2 EU energy component.
 *
 * <p>The adapter operates on the existing component instead of storing a second energy value, so
 * energy sent by a third-party cable is immediately available to IC2 machines and vice versa.
 */
public final class Ic2EnergyStorage implements IEnergyStorage {
  private final Ic2TileEntity owner;
  private final Energy energy;
  private final Direction side;

  private Ic2EnergyStorage(Ic2TileEntity owner, Energy energy, @Nullable Direction side) {
    this.owner = owner;
    this.energy = energy;
    this.side = side;
  }

  @Nullable public static IEnergyStorage forBlockEntity(BlockEntity blockEntity, @Nullable Direction side) {
    if (!(blockEntity instanceof Ic2TileEntity tile)) {
      return null;
    }
    Energy component = tile.getComponent(Energy.class);
    return component == null ? null : new Ic2EnergyStorage(tile, component, side);
  }

  @Override
  public int receiveEnergy(int maxReceive, boolean simulate) {
    if (maxReceive <= 0 || !this.canReceive()) {
      return 0;
    }

    int accepted =
        Math.min(
            maxReceive,
            EnergyBridgeMath.clampToIntEnergy(
                EnergyBridgeMath.euToFeFloor(this.energy.getFreeEnergy())));
    if (accepted <= 0 || simulate) {
      return accepted;
    }

    double acceptedEu = EnergyBridgeMath.feToEu(accepted);
    double actuallyAcceptedEu = this.energy.addEnergy(acceptedEu);
    int actuallyAccepted =
        EnergyBridgeMath.clampToIntEnergy(EnergyBridgeMath.euToFeFloor(actuallyAcceptedEu));
    if (actuallyAccepted > 0) {
      this.owner.setChanged();
    }
    return actuallyAccepted;
  }

  @Override
  public int extractEnergy(int maxExtract, boolean simulate) {
    if (maxExtract <= 0 || !this.canExtract()) {
      return 0;
    }

    int available =
        EnergyBridgeMath.clampToIntEnergy(EnergyBridgeMath.euToFeFloor(this.energy.getEnergy()));
    int extracted = Math.min(maxExtract, available);
    if (extracted <= 0 || simulate) {
      return extracted;
    }

    this.energy.useEnergy(EnergyBridgeMath.feToEu(extracted));
    this.owner.setChanged();
    return extracted;
  }

  @Override
  public int getEnergyStored() {
    return EnergyBridgeMath.clampToIntEnergy(EnergyBridgeMath.euToFeFloor(this.energy.getEnergy()));
  }

  @Override
  public int getMaxEnergyStored() {
    return EnergyBridgeMath.clampToIntEnergy(
        EnergyBridgeMath.euToFeFloor(this.energy.getCapacity()));
  }

  @Override
  public boolean canExtract() {
    // Capability discovery must not depend on the current charge level. External cables need to
    // stay connected while a generator is temporarily empty or a buffer is currently full.
    return this.energy.canExtractTo(this.side);
  }

  @Override
  public boolean canReceive() {
    return this.energy.canReceiveFrom(this.side);
  }
}
