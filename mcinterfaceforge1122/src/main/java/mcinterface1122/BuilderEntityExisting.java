package mcinterface1122;

import minecrafttransportsimulator.mcinterface.InterfaceManager;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;

/**
 * THIS CLASS IS TEMPORARY AND ONLY MIGRATES ENTITES TO THE NEW SAVE LOAD SYSTEM
 *
 * @author don_bruce
 */
@EventBusSubscriber
public class BuilderEntityExisting extends Entity {

    public NBTTagCompound lastLoadedNBT;

    public BuilderEntityExisting(World world) {
        super(world);
    }

    @Override
    public void onEntityUpdate() {
        super.onEntityUpdate();
        //If we have NBT, and haven't loaded it, do so now.
        //We need to re-crate the entity we saved with into the world, and then we are done.
        if (lastLoadedNBT != null) {
            WrapperWorld worldWrapper = WrapperWorld.getWrapperFor(world);
            worldWrapper.addEntityByData(worldWrapper, new WrapperNBT(lastLoadedNBT));
            setDead();
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        //Save the NBT for loading in the next update call.
        lastLoadedNBT = tag;
    }

    //Junk methods, forced to pull in.
    @Override
    protected void entityInit() {
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound p_70037_1_) {
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound p_70014_1_) {
    }

    /**
     * Registers all builder instances that build our own entities into the game.
     */
    @SubscribeEvent
    public static void registerEntities(RegistryEvent.Register<EntityEntry> event) {
        //Register our own classes.
        event.getRegistry().register(EntityEntryBuilder.create().entity(BuilderEntityExisting.class).id(new ResourceLocation(InterfaceManager.coreModID, "mts_entity"), 0).name("mts_entity").tracker(32 * 16, 5, false).build());
    }
}
