package mcinterface1122;

import java.util.List;

import minecrafttransportsimulator.mcinterface.InterfaceManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;

/**
 * Builder for an entity to sit in so they can ride another entity.  We use this to make MC
 * stop being stupid about entity movement.
 *
 * @author don_bruce
 */
@EventBusSubscriber
public class BuilderEntityLinkedSeat extends ABuilderEntityBase {

    private final WrapperEntity rider;

    public BuilderEntityLinkedSeat(World world) {
        super(world);
        this.rider = null;
    }

    public BuilderEntityLinkedSeat(WrapperEntity rider) {
        super(rider.entity.world);
        setSize(0.05F, 0.05F);
        this.rider = rider;
    }

    @Override
    public void onEntityUpdate() {
        super.onEntityUpdate();
        if (rider == null) {
            //Don't restore this entity from servers.
            if (!world.isRemote) {
                setDead();
            }
        } else {
            List<Entity> passengers = getPassengers();
            if (!passengers.isEmpty()) {
                Entity passenger = passengers.get(0);
                setPosition(passenger.posX, passenger.posY, passenger.posZ);
            } else if (!world.isRemote) {
                setDead();
            }
        }
    }

    @Override
    public void updatePassenger(Entity passenger) {
        //Don't update passenger, we do this in the main tick loop.
    }

    /**
     * Registers our own class for use.
     */
    @SubscribeEvent
    public static void registerEntities(RegistryEvent.Register<EntityEntry> event) {
        event.getRegistry().register(EntityEntryBuilder.create().entity(BuilderEntityLinkedSeat.class).id(new ResourceLocation(InterfaceManager.coreModID, "mts_entity_seat"), 1).tracker(32 * 16, 5, false).name("mts_entity_seat").build());
    }
}