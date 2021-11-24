package sonar.fluxnetworks.common.connection.transfer;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import sonar.fluxnetworks.api.energy.IBlockEnergyBridge;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class SideTransfer {

    private final Direction mSide;

    @Nullable
    private BlockEntity mTarget;
    private IBlockEnergyBridge mBridge;
    private ItemStack mDisplayStack = ItemStack.EMPTY;

    public long mChange;

    public SideTransfer(@Nonnull Direction direction) {
        mSide = direction.getOpposite(); // the tile is on our north side, we charge it from its south side
    }

    public void set(@Nullable BlockEntity target, IBlockEnergyBridge bridge) {
        mTarget = target;
        mBridge = bridge;
        if (target != null) {
            mDisplayStack = new ItemStack(target.getBlockState().getBlock());
        } else {
            mDisplayStack = ItemStack.EMPTY;
        }
    }

    public long send(long amount, boolean simulate) {
        if (mTarget == null || mTarget.isRemoved()) {
            return 0;
        }
        if (mBridge.canAddEnergy(mTarget, mSide)) {
            long op = mBridge.addEnergy(amount, mTarget, mSide, simulate);
            if (!simulate) {
                mChange -= op;
            }
            return op;
        }
        return 0;
    }

    public void receive(long amount) {
        mChange += amount;
    }

    public void onCycleStart() {
        mChange = 0;
    }

    @Nullable
    public BlockEntity getTarget() {
        return mTarget;
    }

    @Nonnull
    public ItemStack getDisplayStack() {
        return mDisplayStack;
    }
}
