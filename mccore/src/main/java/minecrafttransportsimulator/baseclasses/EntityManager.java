package minecrafttransportsimulator.baseclasses;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import minecrafttransportsimulator.entities.components.AEntityA_Base;
import minecrafttransportsimulator.entities.components.AEntityA_Base.EntityUpdateType;
import minecrafttransportsimulator.entities.components.AEntityC_Renderable;
import minecrafttransportsimulator.entities.components.AEntityD_Definable;
import minecrafttransportsimulator.entities.components.AEntityE_Interactable;
import minecrafttransportsimulator.entities.components.AEntityF_Multipart;
import minecrafttransportsimulator.entities.components.AEntityG_Towable;
import minecrafttransportsimulator.entities.instances.APart;
import minecrafttransportsimulator.entities.instances.EntityBullet;
import minecrafttransportsimulator.entities.instances.EntityPlacedPart;
import minecrafttransportsimulator.entities.instances.EntityPlayerGun;
import minecrafttransportsimulator.entities.instances.EntityVehicleF_Physics;
import minecrafttransportsimulator.entities.instances.PartGun;
import minecrafttransportsimulator.guis.instances.GUIPackMissing;
import minecrafttransportsimulator.items.components.AItemPack;
import minecrafttransportsimulator.items.components.AItemSubTyped;
import minecrafttransportsimulator.mcinterface.AWrapperWorld;
import minecrafttransportsimulator.mcinterface.IWrapperNBT;
import minecrafttransportsimulator.mcinterface.IWrapperPlayer;
import minecrafttransportsimulator.mcinterface.InterfaceManager;
import minecrafttransportsimulator.packets.instances.PacketPlayerJoin;
import minecrafttransportsimulator.packets.instances.PacketWorldEntityData;
import minecrafttransportsimulator.packloading.PackParser;
import minecrafttransportsimulator.systems.ConfigSystem;
import minecrafttransportsimulator.systems.ControlSystem;

/**
 * Class that manages entities in a world or other area.
 * This class has various lists and methods for querying the entities.
 *
 * @author don_bruce
 */
public abstract class EntityManager {
    protected final ConcurrentLinkedQueue<AEntityA_Base> allEntities = new ConcurrentLinkedQueue<>();
    protected final ConcurrentLinkedQueue<AEntityA_Base> allMainTickableEntities = new ConcurrentLinkedQueue<>();
    protected final ConcurrentLinkedQueue<AEntityA_Base> allLastTickableEntities = new ConcurrentLinkedQueue<>();
    public final ConcurrentLinkedQueue<AEntityC_Renderable> renderableEntities = new ConcurrentLinkedQueue<>();
    public final ConcurrentLinkedQueue<AEntityE_Interactable<?>> collidableEntities = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<Class<? extends AEntityA_Base>, ConcurrentLinkedQueue<? extends AEntityA_Base>> entitiesByClass = new ConcurrentHashMap<>();
    protected final ConcurrentHashMap<UUID, AEntityA_Base> trackedEntityMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PartGun> gunMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Map<Integer, EntityBullet>> bulletMap = new ConcurrentHashMap<>();
    protected final Map<UUID, EntityPlayerGun> playerServerGuns = new HashMap<>();
    private static int packWarningTicks;

    /**
     * Does all ticking operations for all entities, plus any supplemental logic for housekeeping.
     */
    public void runTick(AWrapperWorld world, boolean mainUpdate) {
        world.beginProfiling("MTS_EntityUpdates", true);
        for (AEntityA_Base entity : mainUpdate ? allMainTickableEntities : allLastTickableEntities) {
            if (!(entity instanceof AEntityG_Towable) || !(((AEntityG_Towable<?>) entity).blockMainUpdateCall())) {
                world.beginProfiling("MTSEntity_" + entity.uniqueUUID, true);
                entity.update();
                if (entity instanceof AEntityD_Definable) {
                    ((AEntityD_Definable<?>) entity).doPostUpdateLogic();
                }
                world.endProfiling();
            }
        }

        world.beginProfiling("MTS_GeneralFunctions", true);
        if (mainUpdate) {
            if (world.isClient()) {
                //Get player for future client calls and make sure they're valid.
                IWrapperPlayer player = InterfaceManager.clientInterface.getClientPlayer();
                if (player != null && !player.isSpectator()) {
                    //Call controls system to handle inputs.
                    ControlSystem.controlGlobal(player);

                    //Open pack missing GUI every 5 sec on clients without packs to force them to get some.
                    if (!PackParser.arePacksPresent() && ++packWarningTicks == 100) {
                        if (!InterfaceManager.clientInterface.isGUIOpen()) {
                            new GUIPackMissing();
                        }
                        packWarningTicks = 0;
                    }
                }
            } else {
                //Spawn guns for players that don't have them.
                //Guns may get killed if the player dies, TPs somewhere else, etc.
                for (IWrapperPlayer player : world.getAllPlayers()) {
                    if (!playerServerGuns.containsKey(player.getID())) {
                        IWrapperNBT newData = InterfaceManager.coreInterface.getNewNBTWrapper();
                        EntityPlayerGun gun = new EntityPlayerGun(world, player, newData);
                        gun.addPartsPostConstruction(player, newData);
                        addEntity(gun);
                        playerServerGuns.put(player.getID(), gun);
                    }
                }
            }
        }
        world.endProfiling();
    }

    /**
     * Adds the entity to the world.  This will make it get update ticks and be rendered
     * and do collision checks, as applicable.  Note that this should only be called at
     * FULL construction.  As such, it is recommended to NOT put the call in the entity
     * constructor itself unless the class is final, as it is possible that extending
     * constructors won't complete before the entity is accessed from this list.
     */
    public <EntityType extends AEntityA_Base> void addEntity(EntityType entity) {
        if (entity.shouldSync()) {
            AEntityA_Base otherEntity = trackedEntityMap.get(entity.uniqueUUID);
            if (otherEntity != null) {
                InterfaceManager.coreInterface.logError("Attempting to add already-created and tracked entity " + entity + " with UUID:" + entity.uniqueUUID + " old entity is being replaced!");
                removeEntity(otherEntity);
            }
            trackedEntityMap.put(entity.uniqueUUID, entity);
        }

        allEntities.add(entity);
        if (entity.getUpdateType() == EntityUpdateType.MAIN) {
            allMainTickableEntities.add(entity);
        } else if (entity.getUpdateType() == EntityUpdateType.LAST) {
            allLastTickableEntities.add(entity);
        }
        if (entity instanceof AEntityC_Renderable) {
            renderableEntities.add((AEntityC_Renderable) entity);
            if (entity instanceof AEntityD_Definable) {
                AEntityD_Definable<?> definable = (AEntityD_Definable<?>) entity;
                if (!entity.world.isClient() && definable.loadFromWorldData()) {
                    InterfaceManager.packetInterface.sendToAllClients(new PacketWorldEntityData(definable));
                }
                if (entity instanceof AEntityE_Interactable && ((AEntityE_Interactable<?>) entity).canCollide()) {
                    collidableEntities.add((AEntityE_Interactable<?>) entity);
                }
            }
        }
        if (entity instanceof PartGun) {
            gunMap.put(entity.uniqueUUID, (PartGun) entity);
            bulletMap.put(entity.uniqueUUID, new HashMap<>());
        }
        if (entity instanceof EntityBullet) {
            EntityBullet bullet = (EntityBullet) entity;
            bulletMap.get(bullet.gun.uniqueUUID).put(bullet.bulletNumber, bullet);
        }

        @SuppressWarnings("unchecked")
        ConcurrentLinkedQueue<EntityType> classList = (ConcurrentLinkedQueue<EntityType>) entitiesByClass.get(entity.getClass());
        if (classList == null) {
            classList = new ConcurrentLinkedQueue<>();
            entitiesByClass.put(entity.getClass(), classList);
        }
        classList.add(entity);
    }

    /**
     * Like {@link #addEntity(AEntityA_Base)}, except this creates the entity from the data rather than
     * adding an existing entity.
     */
    public void addEntityByData(AWrapperWorld world, IWrapperNBT data) {
        AItemPack<?> packItem = PackParser.getItem(data.getString("packID"), data.getString("systemName"), data.getString("subName"));
        if (packItem instanceof AItemSubTyped) {
            AEntityD_Definable<?> entity = ((AItemSubTyped<?>) packItem).createEntityFromData(world, data);
            if (entity != null) {
                if (entity instanceof AEntityF_Multipart) {
                    ((AEntityF_Multipart<?>) entity).addPartsPostConstruction(null, data);
                }
                addEntity(entity);
            }
        } else if (packItem == null) {
            if (data.getBoolean("isPlayerGun")) {
                EntityPlayerGun entity = new EntityPlayerGun(world, null, data);
                entity.addPartsPostConstruction(null, data);
                addEntity(entity);
            } else if (data.getBoolean("isPlacedPart")) {
                EntityPlacedPart entity = new EntityPlacedPart(world, null, data);
                entity.addPartsPostConstruction(null, data);
                addEntity(entity);
            } else {
                InterfaceManager.coreInterface.logError("Tried to create entity from NBT but couldn't find an item to create it from.  Did a pack change?");
            }
        } else {
            InterfaceManager.coreInterface.logError("Tried to create entity from NBT but found a pack item that wasn't a sub-typed item.  A pack could have changed, but this is probably a bug and should be reported.");
        }
    }

    /**
     * Removes this entity from the world.  Taking it off the update/functional lists.
     */
    public void removeEntity(AEntityA_Base entity) {
        allEntities.remove(entity);
        if (entity.getUpdateType() == EntityUpdateType.MAIN) {
            allMainTickableEntities.remove(entity);
        } else if (entity.getUpdateType() == EntityUpdateType.LAST) {
            allLastTickableEntities.remove(entity);
        }
        if (entity instanceof AEntityC_Renderable) {
            renderableEntities.remove(entity);
            if (entity instanceof AEntityE_Interactable && ((AEntityE_Interactable<?>) entity).canCollide()) {
                collidableEntities.remove(entity);
            }
            if (entity instanceof EntityBullet) {
                EntityBullet bullet = (EntityBullet) entity;
                bulletMap.get(bullet.gun.uniqueUUID).remove(bullet.bulletNumber);
            }
            if (entity instanceof EntityPlayerGun && !entity.world.isClient()) {
                playerServerGuns.remove(((EntityPlayerGun) entity).playerID);
            }
        }
        entitiesByClass.get(entity.getClass()).remove(entity);
        if (entity.shouldSync()) {
            trackedEntityMap.remove(entity.uniqueUUID);
        }
    }

    /**
     * Gets the entity with the requested UUID.
     */
    @SuppressWarnings("unchecked")
    public <EntityType extends AEntityA_Base> EntityType getEntity(UUID uniqueUUID) {
        return (EntityType) trackedEntityMap.get(uniqueUUID);
    }

    /**
     * Returns the gun associated with the gunID.  Guns are saved when they are seen in the world and
     * remain here for query even when removed.  This allows for referencing their properties for bullets
     * that were fired from a gun that was put away, moved out of render distance, etc.  If the gun is re-loaded
     * at some point, it simply replaces the reference returned by the function with the new instance.
     */
    public PartGun getBulletGun(UUID gunID) {
        return gunMap.get(gunID);
    }

    /**
     * Gets the bullet associated with the gun and bulletNumber.
     * This bullet MAY be null if we have had de-syncs across worlds that fouled the indexing.
     */
    public EntityBullet getBullet(UUID gunID, int bulletNumber) {
        return bulletMap.get(gunID).get(bulletNumber);
    }

    /**
     * Gets the list of all entities of the specified class.
     */
    @SuppressWarnings("unchecked")
    public <EntityType extends AEntityA_Base> ConcurrentLinkedQueue<EntityType> getEntitiesOfType(Class<EntityType> entityClass) {
        ConcurrentLinkedQueue<EntityType> classListing = (ConcurrentLinkedQueue<EntityType>) entitiesByClass.get(entityClass);
        if (classListing == null) {
            classListing = new ConcurrentLinkedQueue<>();
            entitiesByClass.put(entityClass, classListing);
        }
        return classListing;
    }

    /**
     * Returns a new, mutable list, with all entities that are an instanceof the passed-in class.
     * Different than {@link #getEntitiesOfType(Class)}, which must MATCH the passed-in class.
     * It is preferred to use the former since it doesn't require looping lookups and is therefore
     * more efficient.
     */
    @SuppressWarnings("unchecked")
    public <EntityType extends AEntityA_Base> List<EntityType> getEntitiesExtendingType(Class<EntityType> entityClass) {
        List<EntityType> list = new ArrayList<>();
        allEntities.forEach(entity -> {
            if (entityClass.isAssignableFrom(entity.getClass())) {
                list.add((EntityType) entity);
            }
        });
        return list;
    }

    /**
     * Gets the closest multipart intersected with, be it a vehicle, a part on that vehicle, or a placed part.
     * If nothing is intersected, null is returned.
     */
    public EntityInteractResult getMultipartEntityIntersect(Point3D startPoint, Point3D endPoint) {
        EntityInteractResult closestResult = null;
        BoundingBox vectorBounds = new BoundingBox(startPoint, endPoint);
        List<AEntityF_Multipart<?>> multiparts = new ArrayList<>();
        multiparts.addAll(getEntitiesOfType(EntityVehicleF_Physics.class));
        multiparts.addAll(getEntitiesOfType(EntityPlacedPart.class));

        for (AEntityF_Multipart<?> multipart : multiparts) {
            if (multipart.encompassingBox.intersects(vectorBounds)) {
                //Could have hit this multipart, check if and what we did via raytracing.
                for (BoundingBox box : multipart.allInteractionBoxes) {
                    if (box.intersects(vectorBounds)) {
                        Point3D intersectionPoint = box.getIntersectionPoint(startPoint, endPoint);
                        if (intersectionPoint != null) {
                            if (closestResult == null || startPoint.isFirstCloserThanSecond(intersectionPoint, closestResult.point)) {
                                APart part = multipart.getPartWithBox(box);
                                closestResult = new EntityInteractResult(part != null ? part : multipart, box, intersectionPoint);
                            }
                        }
                    }
                }
            }
        }
        return closestResult;
    }

    /**
     * Handles things that happen after the player joins.
     * Called on both server and client, though mostly just sends
     * over server entity data to them on their clients.
     * Note that this is called on the client ITSELF  on join,
     * but on the server this is called via a PACKET sent by the client.
     * This ensures the client is connected and ready to receive any data we give it.
     */
    public void onPlayerJoin(IWrapperPlayer player) {
        if (player.getWorld().isClient()) {
            //Send packet to the server to handle join logic.
            InterfaceManager.packetInterface.sendToServer(new PacketPlayerJoin(player));
        } else {
            //Send data to the client.
            for (AEntityA_Base entity : trackedEntityMap.values()) {
                if (entity instanceof AEntityD_Definable) {
                    AEntityD_Definable<?> definable = (AEntityD_Definable<?>) entity;
                    if (definable.loadFromWorldData()) {
                        player.sendPacket(new PacketWorldEntityData(definable));
                    }
                }
            }

            //If the player is new, add handbooks.
            UUID playerUUID = player.getID();
            if (ConfigSystem.settings.general.giveManualsOnJoin.value && !ConfigSystem.settings.general.joinedPlayers.value.contains(playerUUID)) {
                player.getInventory().addStack(PackParser.getItem("mts", "handbook_car").getNewStack(null));
                player.getInventory().addStack(PackParser.getItem("mts", "handbook_plane").getNewStack(null));
                ConfigSystem.settings.general.joinedPlayers.value.add(playerUUID);
                ConfigSystem.saveToDisk();
            }
        }
    }

    /**
     * Called to save all entities that are currently active in this manager.
     * Only called on servers, as clients don't save anything.
     */
    public void saveEntities(AWrapperWorld world) {
        IWrapperNBT entityData = InterfaceManager.coreInterface.getNewNBTWrapper();
        int entityCount = 0;
        for (AEntityA_Base entity : trackedEntityMap.values()) {
            if (entity instanceof AEntityD_Definable) {
                AEntityD_Definable<?> definable = (AEntityD_Definable<?>) entity;
                if (definable.loadFromWorldData()) {
                    entityData.setData("entity" + entityCount++, entity.save(InterfaceManager.coreInterface.getNewNBTWrapper()));
                }
            }
        }
        entityData.setInteger("entityCount", entityCount);
        world.setData("entities", entityData);
    }

    /**
     * Called to load all entities that were previously saved.
     * Only called on servers, as clients don't save anything.
     */
    public void loadEntities(AWrapperWorld world) {
        IWrapperNBT entityData = world.getData("entities");
        if (entityData != null) {
            int entityCount = entityData.getInteger("entityCount");
            System.out.println("Found X ents to load " + entityCount);
            for (int i = 0; i < entityCount; ++i) {
                System.out.println("Trying to load " + "entity" + i);
                addEntityByData(world, entityData.getData("entity" + i));
            }
        }
    }

    /**
     * Called to close out this manager.
     * This does a final save, and calls all entity remove methods so they get out of memory.
     */
    public void close(AWrapperWorld world) {
        saveEntities(world);
        for (AEntityA_Base entity : allEntities) {
            entity.remove();
        }
    }

    /**
     * Helper class for interact return data.
     */
    public static class EntityInteractResult {
        public final AEntityE_Interactable<?> entity;
        public final BoundingBox box;
        public final Point3D point;

        private EntityInteractResult(AEntityE_Interactable<?> entity, BoundingBox box, Point3D point) {
            this.entity = entity;
            this.box = box;
            this.point = point;
        }
    }
}