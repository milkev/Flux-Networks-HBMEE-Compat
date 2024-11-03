package sonar.fluxnetworks.common.handler;

import com.google.common.collect.Lists;
import sonar.fluxnetworks.api.energy.ITileEnergyHandler;
import sonar.fluxnetworks.api.tiles.IFluxConnector;
import sonar.fluxnetworks.common.integration.energy.*;
import sonar.fluxnetworks.common.tileentity.TileFluxController;
import sonar.fluxnetworks.common.tileentity.TileFluxPlug;
import sonar.fluxnetworks.common.tileentity.TileFluxPoint;
import sonar.fluxnetworks.common.tileentity.TileFluxStorage;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.GameRegistry;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TileEntityHandler {

    public static List<ITileEnergyHandler> tileEnergyHandlers = Lists.newArrayList();
    public static Map<String, Integer> blockBlacklist = new HashMap<>();

    public static void registerTileEntity() {
        // SonarCore still use old method, this will be minecraft:FluxController etc.
        GameRegistry.registerTileEntity(TileFluxController.class, "FluxController");
        GameRegistry.registerTileEntity(TileFluxPoint.class, "FluxPoint");
        GameRegistry.registerTileEntity(TileFluxPlug.class, "FluxPlug");
        GameRegistry.registerTileEntity(TileFluxStorage.class, "FluxStorage");
        GameRegistry.registerTileEntity(TileFluxStorage.Herculean.class, "HerculeanFluxStorage");
        GameRegistry.registerTileEntity(TileFluxStorage.Gargantuan.class, "GargantuanFluxStorage");

        /*GameRegistry.registerTileEntity(TileFluxController.class, new ResourceLocation(FluxNetworks.MODID, "FluxController"));
        GameRegistry.registerTileEntity(TileFluxPoint.class, new ResourceLocation(FluxNetworks.MODID, "FluxPoint"));
        GameRegistry.registerTileEntity(TileFluxPlug.class, new ResourceLocation(FluxNetworks.MODID, "FluxPlug"));
        GameRegistry.registerTileEntity(TileFluxStorage.class, new ResourceLocation(FluxNetworks.MODID, "FluxStorage"));
        GameRegistry.registerTileEntity(TileFluxStorage.Herculean.class, new ResourceLocation(FluxNetworks.MODID, "HerculeanFluxStorage"));
        GameRegistry.registerTileEntity(TileFluxStorage.Gargantuan.class, new ResourceLocation(FluxNetworks.MODID, "GargantuanFluxStorage"));*/
    }

    public static void registerEnergyHandler() {
        tileEnergyHandlers.add(ForgeEnergyHandler.INSTANCE);
        ItemEnergyHandler.itemEnergyHandlers.add(ForgeEnergyHandler.INSTANCE);
        if(Loader.isModLoaded("gregtech")) {
            tileEnergyHandlers.add(GTEnergyHandler.INSTANCE);
            ItemEnergyHandler.itemEnergyHandlers.add(GTEnergyHandler.INSTANCE);
        }
        if(Loader.isModLoaded("redstoneflux")) {
            tileEnergyHandlers.add(RedstoneFluxHandler.INSTANCE);
            ItemEnergyHandler.itemEnergyHandlers.add(RedstoneFluxHandler.INSTANCE);
        }
        if(Loader.isModLoaded("ic2")) {
            tileEnergyHandlers.add(IC2EnergyHandler.INSTANCE);
            ItemEnergyHandler.itemEnergyHandlers.add(IC2EnergyHandler.INSTANCE);
        }
        if(Loader.isModLoaded("hbm")) {
            ItemEnergyHandler.itemEnergyHandlers.add(HBMEnergyHandler.INSTANCE);
        }
    }

    @Nullable
    public static ITileEnergyHandler getEnergyHandler(TileEntity tile, EnumFacing side) {
        if(tile instanceof IFluxConnector) {
            return null;
        }
        String s = tile.getBlockType().getRegistryName().toString();
        if(blockBlacklist.containsKey(s)) {
            int meta = blockBlacklist.get(s);
            if(meta == -1)
                return null;
            else if(meta == tile.getBlockMetadata())
                return null;
        }
        for(ITileEnergyHandler handler : tileEnergyHandlers) {
            if(handler.hasCapability(tile, side)) {
                return handler;
            }
        }
        return null;
    }

    public static boolean canRenderConnection(TileEntity tile, EnumFacing side) {
        if(tile == null) {
            return false;
        }
        if(tile instanceof IFluxConnector) {
            return false;
        }
        if(blockBlacklist.containsKey(tile.getBlockType().getRegistryName().toString())) {
            int meta = blockBlacklist.get(tile.getBlockType().getRegistryName().toString());
            if(meta == -1)
                return false;
            else if(meta == tile.getBlockMetadata())
                return false;
        }
        ITileEnergyHandler handler = null;
        for(ITileEnergyHandler handler1 : tileEnergyHandlers) {
            if(handler1.hasCapability(tile, side)) {
                handler = handler1;
            }
        }
        return handler != null;
    }
}
