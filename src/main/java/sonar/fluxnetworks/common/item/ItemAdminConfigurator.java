package sonar.fluxnetworks.common.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import sonar.fluxnetworks.api.device.IFluxProvider;
import sonar.fluxnetworks.client.ClientRepository;
import sonar.fluxnetworks.common.connection.FluxDeviceMenu;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ItemAdminConfigurator extends Item {

    public ItemAdminConfigurator(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level pLevel, Player pPlayer, InteractionHand pUsedHand) {
        if (!pLevel.isClientSide) {
            NetworkHooks.openGui((ServerPlayer) pPlayer,
                    new Provider(), buf -> buf.writeBoolean(false));
        }
        return InteractionResultHolder.success(pPlayer.getItemInHand(pUsedHand));
    }

    public static class Provider implements IFluxProvider {

        @Override
        public int getNetworkID() {
            return ClientRepository.adminViewingNetwork;
        }

        @Override
        public void onMenuOpened(@Nonnull FluxDeviceMenu menu, @Nonnull Player player) {
        }

        @Override
        public void onMenuClosed(@Nonnull FluxDeviceMenu menu, @Nonnull Player player) {
        }

        @Nullable
        @Override
        public FluxDeviceMenu createMenu(int containerId, @Nonnull Inventory inventory, @Nonnull Player player) {
            return new FluxDeviceMenu(containerId, inventory, this);
        }
    }
}
