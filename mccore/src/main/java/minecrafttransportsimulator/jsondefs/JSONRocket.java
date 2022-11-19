package minecrafttransportsimulator.jsondefs;

import minecrafttransportsimulator.packloading.JSONParser.JSONDescription;
import minecrafttransportsimulator.packloading.JSONParser.JSONRequired;

public class JSONRocket extends AJSONMultiModelProvider {
    @JSONRequired
    @JSONDescription("Rocket-specific properties.")
    public Rocket rocket;

    public static class Rocket {
        public boolean isIncendiary;
        public GuidanceSystem guidanceSystem;
        public boolean isHeat;
        public double radarCrossSection;
        public int quantity;
        public float diameter;
        public float length;
        public float damage;
        public float slowdownSpeed;
        public int burnTime;
        public int accelerationTime;
        public int accelerationDelay;
        public int maxVelocity;
        public float blastStrength;
        public float armorPenetration;
        public float proximityFuze;
        public int fuzeTime;
        public int impactDespawnTime;
        public float turnRate;

        public enum GuidanceSystem {
            DUMMY,
            GPS,
            HEAT,
            LASER,
            RADAR
        }
    }
}
