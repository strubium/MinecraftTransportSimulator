package minecrafttransportsimulator.entities.instances;

import minecrafttransportsimulator.baseclasses.*;
import minecrafttransportsimulator.entities.components.AEntityF_Multipart;
import minecrafttransportsimulator.jsondefs.JSONMuzzle;
import minecrafttransportsimulator.jsondefs.JSONPart.InteractableComponentType;
import minecrafttransportsimulator.jsondefs.JSONPart.JSONPartProjectileLauncher;
import minecrafttransportsimulator.jsondefs.JSONPartDefinition;
import minecrafttransportsimulator.jsondefs.JSONText;
import minecrafttransportsimulator.mcinterface.IWrapperEntity;
import minecrafttransportsimulator.mcinterface.IWrapperNBT;
import minecrafttransportsimulator.mcinterface.IWrapperPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Basic gun class.  This class is responsible for representing a gun in the world.  This gun
 * can be placed on anything and modeled by anything as the code is only for controlling the firing
 * of the gun.  This means this class only stores the internal state of the gun, such as the number
 * of bullets, cooldown time remaining, who is controlling it, etc.  It does NOT set these states, as
 * these are done externally.
 * <br><br>
 * However, since this gun object is responsible for firing bullets, it does need to have spatial awareness.
 * Because of this, the gun contains a position and orientation offset that may be set to "move" the gun in
 * the world.  This should not be confused with the gun's internal orientation, which is set based on commands
 * given to the gun and may change.
 *
 * @author don_bruce
 */
public abstract class PartProjectileLauncher extends APart {
    private final JSONPartProjectileLauncher projectileLauncher;
    //Variables based on the specific gun's properties.
    protected final double minYaw;
    protected final double maxYaw;
    protected final double defaultYaw;
    protected final double yawSpeed;
    protected final double minPitch;
    protected final double maxPitch;
    protected final double defaultPitch;
    protected final double pitchSpeed;

    protected final List<PartInteractable> connectedCrates = new ArrayList<>();

    //Stored variables used to determine bullet firing behavior.
    //private int bulletsLeft;
    protected int currentMuzzleGroupIndex;
    protected final RotationMatrix internalOrientation;
    protected final RotationMatrix prevInternalOrientation;
    //protected ItemBullet loadedBullet;
    //private ItemBullet reloadingBullet;
    //public ItemBullet clientNextBullet;

    //These variables are used during firing and will be reset on loading.
    public LauncherState state;
    public boolean firedThisRequest;
    public boolean firedThisCheck;
    public boolean playerHoldingTrigger;
    public boolean isHandHeldGunAimed;
    protected boolean isRunningInCoaxialMode;
    protected int camOffset;
    protected int cooldownTimeRemaining;
    protected int reloadTimeRemaining;
    protected int windupTimeCurrent;
    protected int windupRotation;
    protected long lastMillisecondFired;
    public IWrapperEntity lastController;
    protected PartSeat lastControllerSeat;
    protected final Point3D controllerRelativeLookVector = new Point3D();
    protected IWrapperEntity entityTarget;
    protected PartEngine engineTarget;
    protected final Point3D projectilePosition = new Point3D();
    protected final Point3D projectileVelocity = new Point3D();
    protected final RotationMatrix projectileOrientation = new RotationMatrix();
    protected final Point3D projectilePositionRender = new Point3D();
    protected final Point3D projectileVelocityRender = new Point3D();
    protected final RotationMatrix bulletOrientationRender = new RotationMatrix();
    protected final List<PartSeat> seatsControllingGun = new ArrayList<>();

    //Temp helper variables for calculations
    protected final Point3D targetVector = new Point3D();
    protected final Point3D targetAngles = new Point3D();
    protected final RotationMatrix firingSpreadRotation = new RotationMatrix();
    protected final RotationMatrix pitchMuzzleRotation = new RotationMatrix();
    protected final RotationMatrix yawMuzzleRotation = new RotationMatrix();

    //Global data.
    protected static final int RAYTRACE_DISTANCE = 750;

    public PartProjectileLauncher(AEntityF_Multipart<?> entityOn, IWrapperPlayer placingPlayer, JSONPartDefinition placementDefinition, IWrapperNBT data, JSONPartProjectileLauncher projectileLauncher) {
        super(entityOn, placingPlayer, placementDefinition, data);
        
        this.projectileLauncher = projectileLauncher;
        //Set min/max yaw/pitch angles based on our definition and the entity definition.
        //If the entity definition min/max yaw is -180 to 180, set it to that.  Otherwise, get the max bounds.
        //Yaw/Pitch set to 0 is ignored as it's assumed to be un-defined.
        if (placementDefinition.minYaw == -180 && placementDefinition.maxYaw == 180) {
            this.minYaw = -180;
            this.maxYaw = 180;
        } else {
            if (projectileLauncher.minYaw != 0) {
                this.minYaw = placementDefinition.minYaw != 0 ? Math.max(projectileLauncher.minYaw, placementDefinition.minYaw) : projectileLauncher.minYaw;
            } else {
                this.minYaw = placementDefinition.minYaw;
            }
            if (projectileLauncher.maxYaw != 0) {
                this.maxYaw = placementDefinition.maxYaw != 0 ? Math.min(projectileLauncher.maxYaw, placementDefinition.maxYaw) : projectileLauncher.maxYaw;
            } else {
                this.maxYaw = placementDefinition.maxYaw;
            }
        }
        if (placementDefinition.defaultYaw != 0 && placementDefinition.defaultYaw >= minYaw && placementDefinition.defaultYaw <= maxYaw) {
            this.defaultYaw = placementDefinition.defaultYaw;
        } else {
            this.defaultYaw = projectileLauncher.defaultYaw;
        }
        if (projectileLauncher.yawSpeed != 0 && placementDefinition.yawSpeed != 0) {
            this.yawSpeed = projectileLauncher.yawSpeed < placementDefinition.yawSpeed ? projectileLauncher.yawSpeed : placementDefinition.yawSpeed;
        } else if (projectileLauncher.yawSpeed != 0) {
            this.yawSpeed = projectileLauncher.yawSpeed;
        } else {
            this.yawSpeed = placementDefinition.yawSpeed;
        }

        //Swap min and max pitch.  In JSON, negative values are down and positive up.
        //But for us, positive is down and negative is up.
        if (projectileLauncher.minPitch != 0) {
            this.minPitch = placementDefinition.maxPitch != 0 ? -Math.max(projectileLauncher.maxPitch, placementDefinition.maxPitch) : -projectileLauncher.maxPitch;
        } else {
            this.minPitch = -placementDefinition.maxPitch;
        }
        if (projectileLauncher.minPitch != 0) {
            this.maxPitch = placementDefinition.minPitch != 0 ? -Math.min(projectileLauncher.minPitch, placementDefinition.minPitch) : -projectileLauncher.minPitch;
        } else {
            this.maxPitch = -placementDefinition.minPitch;
        }
        if (placementDefinition.defaultPitch != 0 && -placementDefinition.defaultPitch >= minPitch && -placementDefinition.defaultPitch <= maxPitch) {
            this.defaultPitch = -placementDefinition.defaultPitch;
        } else {
            this.defaultPitch = -projectileLauncher.defaultPitch;
        }
        if (projectileLauncher.pitchSpeed != 0 && placementDefinition.pitchSpeed != 0) {
            this.pitchSpeed = projectileLauncher.pitchSpeed < placementDefinition.pitchSpeed ? projectileLauncher.pitchSpeed : placementDefinition.pitchSpeed;
        } else if (projectileLauncher.pitchSpeed != 0) {
            this.pitchSpeed = projectileLauncher.pitchSpeed;
        } else {
            this.pitchSpeed = placementDefinition.pitchSpeed;
        }

        this.internalOrientation = new RotationMatrix().setToAngles(data.getPoint3d("internalAngles"));
        this.prevInternalOrientation = new RotationMatrix().set(this.internalOrientation);
        this.state = LauncherState.values()[data.getInteger("state")];
    }

    @Override
    public abstract boolean interact(IWrapperPlayer player);

    @Override
    public void update() {
        //Set launcher state and do updates.
        firedThisCheck = false;
        isRunningInCoaxialMode = false;
        prevInternalOrientation.set(internalOrientation);
    }

    @Override
    public void updatePartList() {
        super.updatePartList();

        seatsControllingGun.clear();
        addLinkedPartsToList(seatsControllingGun, PartSeat.class);
        for (APart part : parts) {
            if (part instanceof PartSeat) {
                seatsControllingGun.add((PartSeat) part);
            }
        }

        connectedCrates.clear();
        for (APart part : parts) {
            if (part instanceof PartInteractable) {
                connectedCrates.add((PartInteractable) part);
            }
        }
        addLinkedPartsToList(connectedCrates, PartInteractable.class);
        connectedCrates.removeIf(crate -> crate.definition.interactable.interactionType != InteractableComponentType.CRATE || !crate.definition.interactable.feedsVehicles);
    }

    /**
     * Helper method to calculate yaw/pitch movement.  Takes controller
     * look vector into account, as well as gun position.  Does not take
     * gun clamping into account as that's done in {@link #handleMovement(double, double)}
     */
    protected abstract void handleControl(IWrapperEntity controller);

    /**
     * Helper method to validate a entityTarget as possible for this gun.
     * Checks entity position relative to the gun, and if the entity
     * is behind any blocks.  Returns true if the entityTarget is valid.
     * Also sets {@link #targetVector} and {@link #targetAngles}
     */
    private boolean validateTarget(IWrapperEntity target) {
        if (target.isValid()) {
            //Get vector from eyes of controller to entityTarget.
            //Target we aim for the middle, as it's more accurate.
            //We also take into account tracking for bullet speed.
            targetVector.set(target.getPosition());
            targetVector.y += target.getEyeHeight() / 2D;
            double ticksToTarget = target.getPosition().distanceTo(position) / projectileLauncher.exitVelocity / 20D / 10D;
            targetVector.add(target.getVelocity().scale(ticksToTarget)).subtract(position);

            //Transform vector to gun's coordinate system.
            //Get the angles the gun has to rotate to match the entityTarget.
            //If the are outside the gun's clamps, this isn't a valid entityTarget.
            targetAngles.set(targetVector).reOrigin(zeroReferenceOrientation).getAngles(true);

            //Check yaw, if we need to.
            if (minYaw != -180 || maxYaw != 180) {
                if (targetAngles.y < minYaw || targetAngles.y > maxYaw) {
                    return false;
                }
            }

            //Check pitch.
            if (targetAngles.x < minPitch || targetAngles.x > maxPitch) {
                return false;
            }

            //Check block raytracing.
            return world.getBlockHit(position, targetVector) == null;
        }
        return false;
    }

    /**
     * Helper method to do yaw/pitch movement.
     * Returns true if the movement was impeded by a clamp.
     * Only call this ONCE per update loop as it sets prev values.
     */
    protected void handleMovement(double deltaYaw, double deltaPitch) {
        if (deltaYaw != 0 || deltaPitch != 0) {
            if (deltaYaw != 0) {
                //Adjust yaw.  We need to normalize the delta here as yaw can go past -180 to 180.
                if (deltaYaw < -180)
                    deltaYaw += 360;
                if (deltaYaw > 180)
                    deltaYaw -= 360;
                if (deltaYaw < 0) {
                    if (deltaYaw < -yawSpeed) {
                        deltaYaw = -yawSpeed;
                    }
                    internalOrientation.angles.y += deltaYaw;
                } else if (deltaYaw > 0) {
                    if (deltaYaw > yawSpeed) {
                        deltaYaw = yawSpeed;
                    }
                    internalOrientation.angles.y += deltaYaw;
                }

                //Apply yaw clamps.
                //If yaw is from -180 to 180, we are a gun that can spin around on its mount.
                //We need to do special logic for this trackingType of gun.
                if (minYaw == -180 && maxYaw == 180) {
                    if (internalOrientation.angles.y > 180) {
                        internalOrientation.angles.y -= 360;
                        prevInternalOrientation.angles.y -= 360;
                    } else if (internalOrientation.angles.y < -180) {
                        internalOrientation.angles.y += 360;
                        prevInternalOrientation.angles.y += 360;
                    }
                } else {
                    if (internalOrientation.angles.y > maxYaw) {
                        internalOrientation.angles.y = maxYaw;
                    }
                    if (internalOrientation.angles.y < minYaw) {
                        internalOrientation.angles.y = minYaw;
                    }
                }
            }

            if (deltaPitch != 0) {
                //Adjust pitch.
                if (deltaPitch < 0) {
                    if (deltaPitch < -pitchSpeed) {
                        deltaPitch = -pitchSpeed;
                    }
                    internalOrientation.angles.x += deltaPitch;
                } else if (deltaPitch > 0) {
                    if (deltaPitch > pitchSpeed) {
                        deltaPitch = pitchSpeed;
                    }
                    internalOrientation.angles.x += deltaPitch;
                }

                //Apply pitch clamps.
                if (internalOrientation.angles.x > maxPitch) {
                    internalOrientation.angles.x = maxPitch;
                }
                if (internalOrientation.angles.x < minPitch) {
                    internalOrientation.angles.x = minPitch;
                }
            }
        }
    }

    /**
     * Returns the controller for the gun.
     * The returned value may be a player riding the entity that this gun is on,
     * or perhaps a player in a seat that's on this gun.  May also be the player
     * hodling this gun if the gun is hand-held.
     */
    public IWrapperEntity getGunController() {
        //If the master entity we are on is destroyed, don't allow anything to control us.
        if (masterEntity.damageAmount == masterEntity.definition.general.health) {
            return null;
        }

        //Check if the entity we are on is a player-holding entity.
        if (entityOn instanceof EntityPlayerGun) {
            return ((EntityPlayerGun) entityOn).player;
        }

        //Check if our parent entity is a seat and has a rider.
        if (entityOn instanceof PartSeat && entityOn.rider != null) {
            return entityOn.rider;
        }

        //Check any child seats.  These take priority over global seats.
        for (APart part : parts) {
            if (part instanceof PartSeat && part.rider != null) {
                return part.rider;
            }
        }

        //Check any linked seats.
        //This also includes seats on us, and the seat we are on (if we are on one).
        for (PartSeat seat : seatsControllingGun) {
            if (seat.rider != null) {
                return seat.rider;
            }
        }
        return null;
    }

    /**
     * Helper method to set the position and velocity of a bullet's spawn.
     * This is based on the passed-in muzzle, and the parameters of that muzzle.
     * Used in both spawning the bullet, and in rendering where the muzzle position is.
     */
    public void setProjectileSpawn(Point3D projectilePosition, Point3D projectileVelocity, RotationMatrix projectileOrientation, JSONMuzzle muzzle) {
        //Add launcher velocity to projectile to ensure we spawn with the offset.
        if (vehicleOn != null) {
            projectileVelocity.addScaled(motion, vehicleOn.speedFactor);
        } else {
            projectileVelocity.add(motion);
        }

        //Set position.
        projectilePosition.set(muzzle.pos);
        if (muzzle.center != null) {
            pitchMuzzleRotation.setToZero().rotateX(internalOrientation.angles.x);
            yawMuzzleRotation.setToZero().rotateY(internalOrientation.angles.y);
            projectilePosition.subtract(muzzle.center).rotate(pitchMuzzleRotation).add(muzzle.center).rotate(yawMuzzleRotation);
        } else {
            projectilePosition.rotate(internalOrientation);
        }
        projectilePosition.rotate(zeroReferenceOrientation).add(position);

        //Set orientation.
        projectileOrientation.set(zeroReferenceOrientation).multiply(internalOrientation);
        if (muzzle.rot != null && !projectileLauncher.disableMuzzleOrientation) {
            projectileOrientation.multiply(muzzle.rot);
        }
    }

    @Override
    public double getRawVariableValue(String variable, float partialTicks) {
        return super.getRawVariableValue(variable, partialTicks);
    }

    @Override
    public String getRawTextVariableValue(JSONText textDef, float partialTicks) {
        return super.getRawTextVariableValue(textDef, partialTicks);
    }

    @Override
    public void renderBoundingBoxes(TransformationMatrix transform) {
        if (entityOn.isVariableListTrue(placementDefinition.interactableVariables)) {
            super.renderBoundingBoxes(transform);
            //Draw the gun muzzle bounding boxes.
            for (JSONMuzzle muzzle : projectileLauncher.muzzleGroups.get(currentMuzzleGroupIndex).muzzles) {
                setProjectileSpawn(projectilePositionRender, projectileVelocityRender, bulletOrientationRender, muzzle);
                new BoundingBox(projectilePositionRender, 0.25, 0.25, 0.25).renderWireframe(this, transform, null, ColorRGB.BLUE);
            }
        }
    }

    @Override
    public IWrapperNBT save(IWrapperNBT data) {
        super.save(data);
        data.setInteger("state", (byte) state.ordinal());
        return data;
    }

    enum LauncherState {
        INACTIVE,
        ACTIVE,
        CONTROLLED,
        FIRING_REQUESTED,
        FIRING_CURRENTLY;

        public LauncherState promote(LauncherState newState) {
            return newState.ordinal() > this.ordinal() ? newState : this;
        }

        public LauncherState demote(LauncherState newState) {
            return newState.ordinal() < this.ordinal() ? newState : this;
        }

        public boolean isAtLeast(LauncherState testState) {
            return this.ordinal() >= testState.ordinal();
        }
    }
}
