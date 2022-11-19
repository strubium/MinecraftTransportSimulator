package minecrafttransportsimulator.radar;

import minecrafttransportsimulator.entities.components.AEntityA_Base;
import minecrafttransportsimulator.entities.components.AEntityB_Existing;
import minecrafttransportsimulator.entities.components.AEntityD_Definable;
import minecrafttransportsimulator.entities.instances.EntityBullet;
import minecrafttransportsimulator.mcinterface.AWrapperWorld;

import java.util.ArrayList;

public class Radar {
    private final AWrapperWorld world;
    public ArrayList<AEntityB_Existing> trackedEntities;

    public Radar(AWrapperWorld world) {
        this.world = world;
    }

    public void update() {
        for (AEntityB_Existing entity : this.trackedEntities) {
            if (entity instanceof EntityBullet && ((EntityBullet) entity).) {

            }
        }
        updateDisplays();
    }

    public void updateDisplays() {
        for (AEntityD_Definable<?> entity : this.world.getEntitiesOfType(AEntityD_Definable.class)) {

        }
    }
}
