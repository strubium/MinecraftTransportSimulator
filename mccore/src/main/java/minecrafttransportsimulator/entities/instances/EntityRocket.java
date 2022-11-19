package minecrafttransportsimulator.entities.instances;

import minecrafttransportsimulator.baseclasses.BoundingBox;
import minecrafttransportsimulator.baseclasses.Damage;
import minecrafttransportsimulator.baseclasses.Point3D;
import minecrafttransportsimulator.baseclasses.RotationMatrix;
import minecrafttransportsimulator.entities.components.AEntityD_Definable;
import minecrafttransportsimulator.entities.components.AEntityE_Interactable;
import minecrafttransportsimulator.entities.instances.EntityBullet.HitType;
import minecrafttransportsimulator.jsondefs.JSONConfigLanguage;
import minecrafttransportsimulator.jsondefs.JSONRocket;
import minecrafttransportsimulator.mcinterface.AWrapperWorld;
import minecrafttransportsimulator.mcinterface.IWrapperEntity;
import minecrafttransportsimulator.mcinterface.IWrapperPlayer;
import minecrafttransportsimulator.mcinterface.InterfaceManager;
import minecrafttransportsimulator.packets.instances.PacketEntityBulletHitBlock;
import minecrafttransportsimulator.packets.instances.PacketPlayerChatMessage;
import minecrafttransportsimulator.radar.IDetectable;
import minecrafttransportsimulator.systems.ConfigSystem;

import java.util.List;
import java.util.TreeMap;

public class EntityRocket extends AEntityD_Definable<JSONRocket> implements IDetectable {
    public final PartRocketLauncher launcher;
    public final double initialVelocity;
    private final double velocityToAddEachTick;
    private final Point3D motionToAddEachTick;
    private int impactDesapawnTimer = -1;
    private Point3D targetPosition;
    public double targetDistance;
    private Point3D targetVector;
    private IWrapperEntity entityTarget;
    private double armorPenetrated;
    private HitType lastHit;

    /**
     * Generic and dummy constructor
     *
     * @param launcher    the part that this rocket/missile was fired from
     * @param position    the position that this rocket/missile was fire at
     * @param motion      the initial velocity of this rocket/missile
     * @param orientation the orientation of this rocket/missile
     * @param heatSeeking whether this missile is heat seeking or not
     */
    public EntityRocket(PartRocketLauncher launcher, Point3D position, Point3D motion, RotationMatrix orientation, boolean heatSeeking) {
        super(launcher.world, position, motion, ZERO_FOR_CONSTRUCTOR, launcher.loadedRocket);
        this.launcher = launcher;
        final double sizeCalc = definition.rocket.diameter / 1000D / 2D;
        this.boundingBox.widthRadius = sizeCalc;
        this.boundingBox.heightRadius = sizeCalc;
        this.boundingBox.depthRadius = sizeCalc;
        this.initialVelocity = motion.length();
        if (definition.rocket.accelerationTime > 0) {
            this.velocityToAddEachTick = (definition.rocket.maxVelocity / 20D / 10D - motion.length()) / definition.rocket.accelerationTime;
            this.motionToAddEachTick = new Point3D(0, 0, velocityToAddEachTick).rotate(launcher.orientation);
        } else {
            this.velocityToAddEachTick = 0;
            this.motionToAddEachTick = null;
        }
        this.orientation.set(orientation);
        this.prevOrientation.set(orientation);
        //TODO: make heatSeeking work
    }

    /**
     * GPS-guided missile constructor
     *
     * @param targetPosition the pre-set position for this missile to target
     */
    public EntityRocket(PartRocketLauncher launcher, Point3D position, Point3D motion, RotationMatrix orientation, Point3D targetPosition, boolean heatSeeking) {
        this(launcher, position, motion, orientation, heatSeeking);
        this.targetPosition = targetPosition;
    }

    /**
     * RADAR-guided missile constructor
     *
     * @param entityTarget the pre-set non-MTS entity for this missile to target
     */
    public EntityRocket(PartRocketLauncher launcher, Point3D position, Point3D motion, RotationMatrix orientation, IWrapperEntity entityTarget) {
        this(launcher, position, motion, orientation, entityTarget.getPosition(), false);
        this.entityTarget = entityTarget;
        final String entityName = entityTarget.getName();
        if (entityTarget instanceof EntityVehicleF_Physics) {
            displayDebugMessage("LOCKON VEHICLE " + entityName + " @ " + targetPosition);
        } else {
            displayDebugMessage("LOCKON ENTITY " + entityName + " @ " + targetPosition);
        }
    }

    @Override
    public void update() {
        // Check if the rocket made impact.  If so, don't process anything and just stay in place.
        if (impactDesapawnTimer >= 0) {
            if (impactDesapawnTimer-- == 0) {
                remove();
            }
            return;
        }

        // Add gravity and slowdown forces if there is no propulsion.
        if (ticksExisted > definition.rocket.burnTime) {
            if (definition.rocket.slowdownSpeed > 0) {
                motion.add(motion.copy().normalize().scale(-definition.rocket.slowdownSpeed));
            }
            motion.y -= launcher.definition.launcher.gravitationalVelocity;

            // Check to make sure we haven't gone too many ticks.
            if (ticksExisted > definition.rocket.burnTime + 600) {
                displayDebugMessage("TIEMOUT");
                remove();
                return;
            }
        }

        // Set motion to add each tick while accelerating
        boolean isBurning = definition.rocket.accelerationDelay == 0 || ticksExisted > definition.rocket.accelerationDelay;
        if (velocityToAddEachTick != 0 && isBurning && ticksExisted - definition.rocket.accelerationDelay < definition.rocket.accelerationTime) {
            motionToAddEachTick.set(0, 0, velocityToAddEachTick).rotate(orientation);
            motion.add(motionToAddEachTick);
        }

        // Update and go to target position if there is one and if the rocket has propulsion.
        if (targetPosition != null && isBurning) {
            if (entityTarget != null) {
                if (entityTarget.isValid()) {
                    targetPosition.set(entityTarget.getPosition().add(0, entityTarget.getBounds().heightRadius, 0));
                } else {
                    entityTarget = null;
                    targetPosition = null;
                }
            }

            if (targetPosition != null) {
                // Get the angular delta between us and our target in our local orientation coordinates.
                if (targetVector == null) {
                    targetVector = new Point3D();
                }
                targetVector.set(targetPosition).subtract(position).reOrigin(orientation).getAngles(true);

                // Clamp angular delta to match turn rate and apply.
                if (targetVector.y > definition.rocket.turnRate) {
                    targetVector.y = definition.rocket.turnRate;
                } else if (targetVector.y < -definition.rocket.turnRate) {
                    targetVector.y = -definition.rocket.turnRate;
                }
                orientation.rotateY(targetVector.y);

                if (targetVector.x > definition.rocket.turnRate) {
                    targetVector.x = definition.rocket.turnRate;
                } else if (targetVector.x < -definition.rocket.turnRate) {
                    targetVector.x = -definition.rocket.turnRate;
                }
                orientation.rotateX(targetVector.x);

                // Set motion to new orientation.
                targetVector.set(0, 0, motion.length()).rotate(orientation);
                motion.set(targetVector);

                // Update target distance.
                targetDistance = targetPosition.distanceTo(position);
            }
        }

        // Check for collisions
        Damage damage = new Damage((velocity / initialVelocity) * definition.rocket.damage * ConfigSystem.settings.damage.bulletDamageFactor.value, boundingBox, launcher, launcher.lastController, launcher.lastController != null ? JSONConfigLanguage.DEATH_BULLET_PLAYER : JSONConfigLanguage.DEATH_BULLET_NULL);
        damage.setRocket(getItem());

        // Check for collided external entities and attack them.
        List<IWrapperEntity> attackedEntities = world.attackEntities(damage, motion, true);
        for (IWrapperEntity entity : attackedEntities) {
            // Check to make sure the rocket doesn't hit its controller.
            if (!entity.equals(launcher.lastController)) {
                // Only attack the first entity.
                position.set(entity.getPosition());
                lastHit = HitType.ENTITY;
                if (!world.isClient()) {
                    entity.attack(damage);
                }
                displayDebugMessage("HIT ENTITY");
                explode();
                return;
            }
        }

        // Check for collided internal entities and attack them.
        // This is a bit more involved, as we need to check all possible types and check hitbox distance.
        Point3D endPoint = position.copy().add(motion);
        BoundingBox rocketMovementBounds = new BoundingBox(position, endPoint);
        for (EntityVehicleF_Physics hitVehicle : world.getEntitiesOfType(EntityVehicleF_Physics.class)) {
            // Don't attack the entity that has or had the launcher that fired the rocket.
            if (!hitVehicle.allParts.contains(launcher)) {
                // Make sure that the rocket could even possibly hit the vehicle before we try and attack it.
                if (hitVehicle.encompassingBox.intersects(rocketMovementBounds)) {
                    // Get all collision boxes on the vehicle and check if we hit any of them.
                    // Sort them by distance for later.
                    TreeMap<Double, BoundingBox> hitBoxes = new TreeMap<>();
                    for (BoundingBox box : hitVehicle.allInteractionBoxes) {
                        if (!hitVehicle.allPartSlotBoxes.containsKey(box)) {
                            Point3D delta = box.getIntersectionPoint(position, endPoint);
                            if (delta != null) {
                                hitBoxes.put(delta.distanceTo(position), box);
                            }
                        }
                    }
                    for (BoundingBox box : hitVehicle.allBulletCollisionBoxes) {
                        Point3D delta = box.getIntersectionPoint(position, endPoint);
                        if (delta != null) {
                            hitBoxes.put(delta.distanceTo(position), box);
                        }
                    }

                    // Check all boxes for armor and see if we penetrated them.
                    for (BoundingBox hitBox : hitBoxes.values()) {
                        APart hitPart = hitVehicle.getPartWithBox(hitBox);
                        AEntityE_Interactable<?> hitEntity = hitPart != null ? hitPart : hitVehicle;

                        // First check if we need to reduce health of the hitbox.
                        if (!world.isClient() && hitBox.groupDef != null && hitBox.groupDef.health != 0) {
                            hitEntity.damageCollisionBox(hitBox, damage.amount);
                            String variableName = "collision_" + (hitEntity.definition.collisionGroups.indexOf(hitBox.groupDef) + 1) + "_damage";
                            double currentDamage = hitEntity.getVariable(variableName);
                            displayDebugMessage("HIT HEALTH BOX.  ATTACKED FOR: " + damage.amount + ".  BOX CURRENT DAMAGE: " + currentDamage + " OF " + hitBox.groupDef.health);
                        }

                        double armorThickness = hitBox.definition != null ? (definition.rocket.isHeat && hitBox.definition.heatArmorThickness != 0 ? hitBox.definition.heatArmorThickness : hitBox.definition.armorThickness) : 0;
                        double penetrationPotential = definition.rocket.isHeat ? definition.rocket.armorPenetration : definition.rocket.armorPenetration * velocity / initialVelocity;
                        if (armorThickness > 0) {
                            armorPenetrated += armorThickness;
                            displayDebugMessage("HIT ARMOR OF: " + (int) armorThickness);
                            if (armorPenetrated > penetrationPotential) {
                                // Hit too much armor, explode now.
                                position.set(hitBox.globalCenter);
                                lastHit = HitType.ARMOR;
                                displayDebugMessage("HIT TOO MUCH ARMOR.  MAX PEN: " + (int) penetrationPotential);
                                explode();
                                return;
                            }
                        } else {
                            // Need to re-create damage object to reference this hitbox.
                            damage = new Damage(damage.amount, hitBox, launcher, null, null);

                            // Now check which damage we need to apply.
                            if (hitBox.groupDef != null) {
                                if (hitBox.groupDef.health == 0) {
                                    // This is a core hitbox, attack entity directly.
                                    // After this, we explode.
                                    position.set(hitBox.globalCenter);
                                    lastHit = HitType.ENTITY;
                                    if (!world.isClient()) {
                                        hitEntity.attack(damage);
                                    }
                                    displayDebugMessage("HIT ENTITY CORE BOX FOR DAMAGE: " + (int) damage.amount + " DAMAGE NOW AT " + (int) hitVehicle.damageAmount);
                                    explode();
                                    return;
                                }
                            } else if (hitPart != null) {
                                // Didn't have a group def, this must be a core part box.
                                // Damage part and keep going on, unless that part is flagged to forward damage, then we do so and die.
                                // Note that parts can get killed by too much damage and suddenly become null during iteration, hence the null check.

                                position.set(hitPart.position);
                                lastHit = HitType.PART;
                                if (!world.isClient()) {
                                    hitPart.attack(damage);
                                }
                                displayDebugMessage("HIT PART FOR DAMAGE: " + (int) damage.amount + " DAMAGE NOW AT " + (int) hitPart.damageAmount);
                                if (hitPart.definition.generic.forwardsDamage || hitPart instanceof PartEngine) {
                                    if (!world.isClient()) {
                                        hitVehicle.attack(damage);
                                    }
                                    displayDebugMessage("FORWARDING DAMAGE TO VEHICLE.  CURRENT DAMAGE IS: " + (int) hitVehicle.damageAmount);
                                    explode();
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }

        // Didn't hit an entity.  Check for blocks.
        AWrapperWorld.BlockHitResult hitResult = world.getBlockHit(position, motion);
        if (hitResult != null) {
            // Only change block state on the server.
            if (!world.isClient()) {
                float hardnessHit = world.getBlockHardness(hitResult.position);
                if (ConfigSystem.settings.general.blockBreakage.value && hardnessHit > 0 && hardnessHit <= (Math.random() * 0.3F + 0.3F * definition.rocket.diameter / 20F)) {
                    world.destroyBlock(hitResult.position, true);
                } else if (definition.rocket.isIncendiary) {
                    // Couldn't break block, but we might be able to set it on fire.
                    world.setToFire(hitResult);
                } else {
                    // Couldn't break the block or set it on fire.  Have clients do sounds.
                    InterfaceManager.packetInterface.sendToAllClients(new PacketEntityBulletHitBlock(hitResult.position));
                }
            }
            position.set(hitResult.position);
            lastHit = HitType.BLOCK;
            displayDebugMessage("HIT BLOCK");
            explode();
            return;
        }

        // Check proximity fuze against our target and blocks.
        if (definition.rocket.proximityFuze != 0) {
            Point3D targetToHit;
            if (targetPosition != null) {
                targetToHit = targetPosition;
            } else {
                hitResult = world.getBlockHit(position, motion.copy().normalize().scale(definition.rocket.proximityFuze + velocity));
                targetToHit = hitResult != null ? hitResult.position : null;
            }
            if (targetToHit != null) {
                double distanceToTarget = position.distanceTo(targetToHit);
                if (distanceToTarget < definition.rocket.proximityFuze + velocity) {
                    if (distanceToTarget > definition.rocket.proximityFuze) {
                        position.interpolate(targetToHit, (distanceToTarget - definition.rocket.proximityFuze) / definition.rocket.proximityFuze);
                    }
                    if (entityTarget != null) {
                        lastHit = HitType.ENTITY;
                        displayDebugMessage("PROX FUZE HIT ENTITY");
                    } else {
                        lastHit = HitType.BLOCK;
                        displayDebugMessage("PROX FUZE HIT BLOCK");
                    }
                    explode();
                    return;
                }
            }
        }

        // Didn't hit a block either. Check the fuze time, if it was used.
        if (definition.rocket.fuzeTime != 0) {
            if (ticksExisted > definition.rocket.fuzeTime) {
                lastHit = HitType.BURST;
                displayDebugMessage("BURST");
                explode();
                return;
            }
        }

        //Add our updated motion to the position.
        //Then set the angles to match the motion.
        //Doing this last lets us damage on the first update tick.
        position.add(motion);
        if (definition.rocket.accelerationDelay == 0 || ticksExisted > definition.rocket.accelerationDelay) {
            orientation.setToVector(motion, true);
        }
    }

    @Override
    public boolean requiresDeltaUpdates() {
        return true;
    }

    private void explode() {
        // Create an explosion
        if (!world.isClient() && lastHit != null) {
            float blastSize = definition.rocket.blastStrength == 0 ? definition.rocket.diameter / 10F : definition.rocket.blastStrength;
            world.spawnExplosion(position, blastSize, definition.rocket.isIncendiary);
        }
        // Despawn rocket
        impactDesapawnTimer = definition.rocket.impactDespawnTime;
    }

    private void displayDebugMessage(String message) {
        if (!world.isClient() && ConfigSystem.settings.general.devMode.value) {
            if (launcher.lastController instanceof IWrapperPlayer) {
                IWrapperPlayer player = (IWrapperPlayer) launcher.lastController;
                player.sendPacket(new PacketPlayerChatMessage(player, message));
            }
        }
    }

    @Override
    public double getRawVariableValue(String variable, float partialTicks) {
        switch (variable) {
            case ("rocket_hit"):
                return lastHit != null ? 1 : 0;
            case ("rocket_burntime"):
                return ticksExisted > definition.rocket.burnTime ? 0 : definition.rocket.burnTime - ticksExisted;
            case ("rocket_hit_block"):
                return HitType.BLOCK.equals(lastHit) ? 1 : 0;
            case ("rocket_hit_entity"):
                return HitType.ENTITY.equals(lastHit) ? 1 : 0;
            case ("rocket_hit_part"):
                return HitType.PART.equals(lastHit) ? 1 : 0;
            case ("rocket_hit_armor"):
                return HitType.ARMOR.equals(lastHit) ? 1 : 0;
            case ("rocket_hit_burst"):
                return HitType.BURST.equals(lastHit) ? 1 : 0;
        }

        return super.getRawVariableValue(variable, partialTicks);
    }

    @Override
    public boolean shouldSync() {
        return false;
    }

    @Override
    public boolean shouldSavePosition() {
        return false;
    }

    @Override
    public double radarCrossSection() {
        return definition.rocket.radarCrossSection;
    }
}
