package minecrafttransportsimulator.items.instances;

import minecrafttransportsimulator.blocks.components.ABlockBase;
import minecrafttransportsimulator.blocks.components.ABlockBaseTileEntity;
import minecrafttransportsimulator.blocks.instances.BlockBeacon;
import minecrafttransportsimulator.blocks.instances.BlockCharger;
import minecrafttransportsimulator.blocks.instances.BlockChest;
import minecrafttransportsimulator.blocks.instances.BlockDecor;
import minecrafttransportsimulator.blocks.instances.BlockFluidLoader;
import minecrafttransportsimulator.blocks.instances.BlockFuelPump;
import minecrafttransportsimulator.blocks.instances.BlockItemLoader;
import minecrafttransportsimulator.blocks.instances.BlockRadio;
import minecrafttransportsimulator.blocks.instances.BlockSignalController;
import minecrafttransportsimulator.blocks.tileentities.instances.TileEntityChest;
import minecrafttransportsimulator.blocks.tileentities.instances.TileEntityDecor;
import minecrafttransportsimulator.items.components.AItemSubTyped;
import minecrafttransportsimulator.items.components.IItemBlock;
import minecrafttransportsimulator.jsondefs.JSONDecor;
import minecrafttransportsimulator.jsondefs.JSONSubDefinition;
import minecrafttransportsimulator.mcinterface.AWrapperWorld;
import minecrafttransportsimulator.mcinterface.IWrapperNBT;

/**
 * Decor item.  Note that while this item can (and does) spawn decor blocks,
 * it can also spawn traffic signal controllers and fuel pumps depending on
 * the definition.  This item, therefore, is essentially a catch-all for all
 * pack, block-based things that aren't poles.
 *
 * @author don_bruce
 */
public class ItemDecor extends AItemSubTyped<JSONDecor> implements IItemBlock {

    public ItemDecor(JSONDecor definition, JSONSubDefinition subDefinition, String sourcePackID) {
        super(definition, subDefinition, sourcePackID);
    }

    @Override
    public Class<? extends ABlockBase> getBlockClass() {
        switch (definition.decor.type) {
            case CHEST:
                return BlockChest.class;
            case BEACON:
                return BlockBeacon.class;
            case SIGNAL_CONTROLLER:
                return BlockSignalController.class;
            case FUEL_PUMP:
                return BlockFuelPump.class;
            case CHARGER:
                return BlockCharger.class;
            case ITEM_LOADER:
            case ITEM_UNLOADER:
                return BlockItemLoader.class;
            case FLUID_LOADER:
            case FLUID_UNLOADER:
                return BlockFluidLoader.class;
            case RADIO:
                return BlockRadio.class;
            case SEAT:
            case GENERIC:
                return BlockDecor.class;
        }

        //We won't ever get here, but this makes the compiler happy.
        return null;
    }

    @Override
    public TileEntityDecor createEntityFromData(AWrapperWorld world, IWrapperNBT data) {
        return (TileEntityDecor) ((ABlockBaseTileEntity) getBlock()).createTileEntity(world, data.getPoint3d("position"), null, data);
    }
}
