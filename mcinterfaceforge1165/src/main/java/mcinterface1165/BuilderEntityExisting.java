package mcinterface1165;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.play.server.SSpawnObjectPacket;
import net.minecraft.world.World;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * THIS CLASS IS TEMPORARY AND ONLY MIGRATES ENTITES TO THE NEW SAVE LOAD SYSTEM
 *
 * @author don_bruce
 */
public class BuilderEntityExisting extends Entity {
    protected static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITIES, InterfaceLoader.MODID);

    public static RegistryObject<EntityType<BuilderEntityExisting>> E_TYPE2;

    private CompoundNBT lastLoadedNBT;

    public BuilderEntityExisting(EntityType<? extends BuilderEntityExisting> eType, World world) {
        super(eType, world);
    }

    @Override
    public void baseTick() {
        super.baseTick();
        //If we have NBT, and haven't loaded it, do so now.
        //We need to re-crate the entity we saved with into the world, and then we are done.
        if (lastLoadedNBT != null) {
            WrapperWorld worldWrapper = WrapperWorld.getWrapperFor(level);
            worldWrapper.addEntityByData(worldWrapper, new WrapperNBT(lastLoadedNBT));
            remove();
        }
    }

    @Override
    public void load(CompoundNBT tag) {
        super.load(tag);
        //Save the NBT for loading in the next update call.
        lastLoadedNBT = tag;
    }

    //Junk methods.
    @Override
    protected void addAdditionalSaveData(CompoundNBT pCompound) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundNBT pCompound) {
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public IPacket<?> getAddEntityPacket() {
        //Spawn object, we have a mixin override this code on the client though since it doesn't know to handle us.
        return new SSpawnObjectPacket(this);
    }
}
