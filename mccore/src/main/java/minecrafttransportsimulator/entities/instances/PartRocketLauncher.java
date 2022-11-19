package minecrafttransportsimulator.entities.instances;

import minecrafttransportsimulator.baseclasses.Point3D;
import minecrafttransportsimulator.baseclasses.RotationMatrix;
import minecrafttransportsimulator.entities.components.AEntityF_Multipart;
import minecrafttransportsimulator.items.components.AItemBase;
import minecrafttransportsimulator.items.instances.ItemPartProjectileLauncher;
import minecrafttransportsimulator.items.instances.ItemRocket;
import minecrafttransportsimulator.jsondefs.*;
import minecrafttransportsimulator.jsondefs.JSONPart.JSONPartRocketLauncher;
import minecrafttransportsimulator.mcinterface.*;
import minecrafttransportsimulator.packloading.PackParser;
import minecrafttransportsimulator.systems.ConfigSystem;

import java.util.List;

public class PartRocketLauncher extends PartProjectileLauncher {
    private int rocketsLeft;
    protected ItemRocket loadedRocket;
    private ItemRocket reloadingRocket;
    public ItemRocket clientNextRocket;
    public IWrapperEntity lastController;


    public PartRocketLauncher(AEntityF_Multipart<?> entityOn, IWrapperPlayer placingPlayer, JSONPartDefinition placementDefinition, IWrapperNBT data, JSONPartRocketLauncher rocketLauncher) {
        super(entityOn, placingPlayer, placementDefinition, data, rocketLauncher);

        //Load saved data.
        this.rocketsLeft = data.getInteger("rocketsLeft");
        this.currentMuzzleGroupIndex = data.getInteger("currentMuzzleGroupIndex");
        String loadedRocketPack = data.getString("loadedRocket");
        if (!loadedRocketPack.isEmpty()) {
            String loadedRocketName = data.getString("loadedRocketName");
            this.loadedRocket = PackParser.getItem(loadedRocketPack, loadedRocketName);
        }
        String reloadingRocket = data.getString("reloadingRocket");
        if (!reloadingRocket.isEmpty()) {
            String reloadingRocketName = data.getString("reloadingRocketName");
            this.reloadingRocket = PackParser.getItem(reloadingRocket, reloadingRocketName);
        }
        //If we didn't load the rocket due to pack changes, set the current rocket count to 0.
        //This prevents pack changes from locking launchers.
        if (loadedRocket == null) {
            rocketsLeft = 0;
        }
    }

    @Override
    public boolean interact(IWrapperPlayer player) {
        //Check to see if we have any rockets in our hands.
        //If so, try to re-load this launcher with them.
        AItemBase heldItem = player.getHeldItem();
        if (heldItem instanceof ItemRocket) {
            if (tryToReload((ItemRocket) heldItem) && !player.isCreative()) {
                player.getInventory().removeFromSlot(player.getHotbarIndex(), 1);
            }
        }
        return true;
    }

    @Override
    public void update() {
        //Set launcher state and do updates.
        firedThisCheck = false;
        isRunningInCoaxialMode = false;
        prevInternalOrientation.set(internalOrientation);
        if (isActive && !isSpare) {
            //Check if we have a controller.
            //We aren't making sentry turrets here.... yet.
            IWrapperEntity controller = getGunController();
            if (controller != null) {
                lastController = controller;
                if (entityOn instanceof EntityPlayerGun) {
                    state = state.promote(LauncherState.CONTROLLED);
                } else {
                    //If this launcher trackingType can only have one selected at a time, check that this has the selected index.
                    lastControllerSeat = (PartSeat) lastController.getEntityRiding();
                    if (getItem() == lastControllerSeat.activeLauncherItem && (!definition.launcher.fireSolo || lastControllerSeat.launcherGroups.get(getItem()).get(lastControllerSeat.launcherIndex) == this)) {
                        state = state.promote(LauncherState.CONTROLLED);
                    } else {
                        state = state.demote(LauncherState.ACTIVE);
                        controller = null;
                        entityTarget = null;
                        engineTarget = null;
                    }
                }
            }
            if (controller == null) {
                //If we aren't being controlled, check if we have any coaxial launchers.
                //If we do, and they have a controller, then we use that as our controller.
                //This allows them to control this launcher without being the actual controller for firing.
                if (!parts.isEmpty()) {
                    for (APart part : parts) {
                        if (part instanceof PartGun && part.placementDefinition.isCoAxial) {
                            controller = ((PartGun) part).getGunController();
                            if (controller != null) {
                                //Check if the coaxial is controlled or not.
                                lastController = controller;
                                lastControllerSeat = (PartSeat) lastController.getEntityRiding();
                                if (part.getItem() == lastControllerSeat.activeLauncherItem && (!definition.launcher.fireSolo || lastControllerSeat.launcherGroups.get(part.getItem()).get(lastControllerSeat.launcherIndex) == part)) {
                                    state = state.promote(LauncherState.CONTROLLED);
                                    isRunningInCoaxialMode = true;
                                }
                                break;
                            }
                        }
                    }
                }
                if (controller == null) {
                    state = state.demote(LauncherState.ACTIVE);
                    //If we are hand-held, we need to die since we aren't a valid launcher.
                    if (entityOn instanceof EntityPlayerGun) {
                        isValid = false;
                        return;
                    }
                }
            }

            //Adjust yaw and pitch to the direction of the controller.
            if (state.isAtLeast(LauncherState.CONTROLLED)) {
                handleControl(controller);
                if (isRunningInCoaxialMode) {
                    state = state.demote(LauncherState.ACTIVE);
                    controller = null;
                    entityTarget = null;
                    engineTarget = null;
                }
            }

            //Decrement cooldown, if we have it.
            if (cooldownTimeRemaining > 0) {
                --cooldownTimeRemaining;
            }

            //Set final launcher active state and variables, and fire if those line up with conditions.
            //Note that this code runs concurrently on the client and server.  This prevents the need for packets for rocket
            //spawning and ensures that they spawn every tick on quick-firing launchers.  Hits are registered on both sides, but
            //hit processing is only done on the server; clients just de-spawn the rocket and wait for packets.
            //Because of this, there is no linking between client and server rockets, and therefore they do not handle NBT or UUIDs.
            boolean ableToFire = windupTimeCurrent == definition.launcher.windupTime && (!definition.launcher.isSemiAuto || !firedThisRequest);
            if (ableToFire && state.isAtLeast(LauncherState.FIRING_REQUESTED)) {
                //Set firing to true if we aren't firing, and we've waited long enough since the last firing command.
                //If we don't wait, we can bypass the cooldown by toggling the trigger.
                if (cooldownTimeRemaining == 0) {
                    //Get current group and use it to determine firing offset.
                    //Don't calculate this if we already did on a prior firing command.
                    if (camOffset <= 0) {
                        if (!definition.launcher.fireSolo && lastControllerSeat != null) {
                            List<PartProjectileLauncher> launcherGroup = lastControllerSeat.launcherGroups.get((ItemPartProjectileLauncher) getItem());
                            int thisGunIndex = launcherGroup.indexOf(this);
                            if (lastControllerSeat.launcherGroupIndex == thisGunIndex) {
                                camOffset = ((int) definition.launcher.fireDelay) / launcherGroup.size();
                            } else {
                                //Wait for our turn.
                                camOffset = -1;
                            }
                        }
                    } else {
                        --camOffset;
                    }

                    //If we have rockets, try and fire them.
                    boolean cycledGun = false;
                    if (rocketsLeft > 0) {
                        state = state.promote(LauncherState.FIRING_CURRENTLY);

                        //If we are in our cam, fire the rockets.
                        if (camOffset == 0) {
                            for (JSONMuzzle muzzle : definition.launcher.muzzleGroups.get(currentMuzzleGroupIndex).muzzles) {
                                //Get the rocket's state.
                                setProjectileSpawn(projectilePosition, projectileVelocity, projectileOrientation, muzzle);

                                //Add the rocket to the world.
                                //If the rocket is a missile, give it a target.
                                EntityRocket newRocket;
                                if (loadedRocket.definition.rocket.turnRate > 0) {
                                    if (entityTarget != null) {
                                        newRocket = new EntityRocket(this, projectilePosition, projectileVelocity, projectileOrientation, entityTarget);
                                        //} else if (engineTarget != null) {
                                        //    newRocket = new EntityRocket(projectilePosition, projectileVelocity, projectileOrientation, this, engineTarget);
                                    } else {
                                        //No entity found, just fire missile off in direction facing.
                                        newRocket = new EntityRocket(this, projectilePosition, projectileVelocity, projectileOrientation, false);
                                    }
                                } else {
                                    newRocket = new EntityRocket(this, projectilePosition, projectileVelocity, projectileOrientation, false);
                                }

                                world.addEntity(newRocket);

                                //Decrement rockets, but check to make sure we still have some.
                                //We might have a partial volley with only some muzzles firing in this group.
                                if (--rocketsLeft == 0) {
                                    //Only set the rocket to null on the server. This lets the server choose a different rocket to load.
                                    //If we did this on the client, we might set the rocket to null after we got a packet for a reload.
                                    //That would cause us to finish the reload with a null rocket, and crash later.
                                    if (!world.isClient()) {
                                        loadedRocket = null;
                                    }
                                    break;
                                }
                            }

                            //Update states.
                            cooldownTimeRemaining = (int) definition.launcher.fireDelay;
                            firedThisRequest = true;
                            firedThisCheck = true;
                            cycledGun = true;
                            lastMillisecondFired = System.currentTimeMillis();
                            if (definition.launcher.muzzleGroups.size() == ++currentMuzzleGroupIndex) {
                                currentMuzzleGroupIndex = 0;
                            }
                        }
                    } else if (camOffset == 0) {
                        //Got to end of cam with no rockets, cycle launcher.
                        cycledGun = true;
                    }
                    if (cycledGun) {
                        if (lastControllerSeat != null) {
                            List<PartProjectileLauncher> launcherGroup = lastControllerSeat.launcherGroups.get((ItemPartProjectileLauncher) getItem());
                            int currentIndex = launcherGroup.indexOf(this);
                            if (currentIndex + 1 < launcherGroup.size()) {
                                lastControllerSeat.launcherGroupIndex = currentIndex + 1;
                            } else {
                                lastControllerSeat.launcherGroupIndex = 0;
                            }
                        }
                    }
                }
            } else if (!ableToFire) {
                state = state.demote(LauncherState.FIRING_REQUESTED);
                if (!state.isAtLeast(LauncherState.FIRING_REQUESTED)) {
                    firedThisRequest = false;
                }
            }

            //If we can accept rockets, and aren't currently loading any, re-load ourselves from any inventories.
            //While the reload method checks for reload time, we check here to save on code processing.
            //No sense in looking for rockets if we can't load them anyways.
            if (!world.isClient() && rocketsLeft < definition.launcher.capacity && reloadingRocket == null) {
                if (entityOn instanceof EntityPlayerGun) {
                    if (definition.launcher.autoReload || rocketsLeft == 0) {
                        //Check the player's inventory for rockets.
                        IWrapperInventory inventory = ((IWrapperPlayer) lastController).getInventory();
                        for (int i = 0; i < inventory.getSize(); ++i) {
                            IWrapperItemStack stack = inventory.getStack(i);
                            AItemBase item = stack.getItem();
                            if (item instanceof ItemRocket) {
                                if (tryToReload((ItemRocket) item)) {
                                    //Rocket is right trackingType, and we can fit it.  Remove from player's inventory and add to the launcher.
                                    if (!ConfigSystem.settings.general.devMode.value)
                                        inventory.removeFromSlot(i, 1);
                                    break;
                                }
                            }
                        }
                    }
                } else {
                    if (definition.launcher.autoReload) {
                        //Iterate through all the inventory slots in crates to try to find matching ammo.
                        for (PartInteractable crate : connectedCrates) {
                            if (crate.isActive) {
                                EntityInventoryContainer inventory = crate.inventory;
                                for (int i = 0; i < inventory.getSize(); ++i) {
                                    IWrapperItemStack stack = inventory.getStack(i);
                                    AItemBase item = stack.getItem();
                                    if (item instanceof ItemRocket) {
                                        if (tryToReload((ItemRocket) item)) {
                                            //Rocket is right trackingType, and we can fit it.  Remove from crate and add to the launcher.
                                            //Return here to ensure we don't set the loadedRocket to blank since we found rockets.
                                            if (!ConfigSystem.settings.general.devMode.value)
                                                inventory.removeFromSlot(i, 1);
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            //If we are a client, this is where we get our rockets.
            if (clientNextRocket != null) {
                reloadingRocket = clientNextRocket;
                reloadTimeRemaining = definition.launcher.reloadTime;
                clientNextRocket = null;
            }

            //If we are reloading, decrement the reloading timer.
            //If we are done reloading, add the new rockets.
            //This comes after the reloading block as we need a 0/1 state-change for the various animations,
            //so at some point the reload time needs to hit 0.
            if (reloadTimeRemaining > 0) {
                --reloadTimeRemaining;
            } else if (reloadingRocket != null) {
                loadedRocket = reloadingRocket;
                rocketsLeft += reloadingRocket.definition.rocket.quantity;
                reloadingRocket = null;
            }
        } else {
            //Inactive launcher, set as such and set to default position if we have one.
            state = LauncherState.INACTIVE;
            entityTarget = null;
            engineTarget = null;
            if (definition.launcher.resetPosition) {
                handleMovement(defaultYaw - internalOrientation.angles.y, defaultPitch - internalOrientation.angles.x);
            }
        }

        //Increment or decrement windup.
        //This is done outside the main active area as windup can wind-down on deactivated launchers.
        if (state.isAtLeast(LauncherState.FIRING_REQUESTED)) {
            if (windupTimeCurrent < definition.launcher.windupTime) {
                ++windupTimeCurrent;
            }
        } else if (windupTimeCurrent > 0) {
            --windupTimeCurrent;
        }
        windupRotation += windupTimeCurrent;

        //Reset fire command bit if we aren't firing.
        if (!state.isAtLeast(LauncherState.FIRING_REQUESTED)) {
            firedThisRequest = false;
        }

        //Now run super.  This needed to wait for the launcher states to ensure proper states.
        super.update();

        //If we have a controller seat on us, adjust the player's facing to account for our movement.
        //If we don't, we'll just rotate forever.
        if (lastControllerSeat != null && parts.contains(lastControllerSeat)) {
            orientation.convertToAngles();
            lastControllerSeat.riderRelativeOrientation.angles.y -= (orientation.angles.y - prevOrientation.angles.y);
            lastControllerSeat.riderRelativeOrientation.angles.x -= (orientation.angles.x - prevOrientation.angles.x);
        }
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
        connectedCrates.removeIf(crate -> crate.definition.interactable.interactionType != JSONPart.InteractableComponentType.CRATE || !crate.definition.interactable.feedsVehicles);
    }

    /**
     * Helper method to calculate yaw/pitch movement.  Takes controller
     * look vector into account, as well as launcher position.  Does not take
     * launcher clamping into account as that's done in {@link #handleMovement(double, double)}
     */
    protected void handleControl(IWrapperEntity controller) {
        //If the controller isn't a player, but is a NPC, make them look at the nearest hostile mob.
        //We also get a flag to see if the launcher is currently pointed to the hostile mob.
        //If not, then we don't fire the launcher, as that'd waste ammo.
        //Need to aim for the middle of the mob, not their base (feet).
        //Also make the launcherner account for rocket delay and movement of the hostile.
        //This makes them track better when the target is moving.
        //We only do this
        if (!(controller instanceof IWrapperPlayer)) {
            //Get new target if we don't have one, or if we've gone 1 second and we have a closer target by 5 blocks.
            boolean checkForCloser = entityTarget != null && ticksExisted % 20 == 0;
            if (entityTarget == null || checkForCloser) {
                for (IWrapperEntity entity : world.getEntitiesHostile(controller, 48)) {
                    if (validateTarget(entity)) {
                        if (entityTarget != null) {
                            double distanceToBeat = position.distanceTo(entityTarget.getPosition());
                            if (checkForCloser) {
                                distanceToBeat += 5;
                            }
                            if (position.distanceTo(entity.getPosition()) > distanceToBeat) {
                                continue;
                            }
                        }
                        entityTarget = entity;
                    }
                }
            }

            //If we have a target, validate it and try to hit it.
            if (entityTarget != null) {
                if (validateTarget(entityTarget)) {
                    controller.setYaw(targetAngles.y);
                    controller.setPitch(targetAngles.x);
                    //Only fire if we're within 1 movement increment of the target.
                    if (Math.abs(targetAngles.y - internalOrientation.angles.y) < yawSpeed && Math.abs(targetAngles.x - internalOrientation.angles.x) < pitchSpeed) {
                        state = state.promote(LauncherState.FIRING_REQUESTED);
                    } else {
                        state = state.demote(LauncherState.CONTROLLED);
                    }
                } else {
                    entityTarget = null;
                    state = state.demote(LauncherState.CONTROLLED);
                }
            } else {
                state = state.demote(LauncherState.CONTROLLED);
            }
        } else {
            //Player-controlled launcher.
            //Check for a target for this launcher if we have a lock-on missile.
            //Only do this once every 1/2 second.
            if (loadedRocket != null && loadedRocket.definition.rocket.turnRate > 0) {
                //Try to find the entity the controller is looking at.
                entityTarget = world.getEntityLookingAt(controller, RAYTRACE_DISTANCE, true);
                if (entityTarget == null) {
                    engineTarget = null;
                    EntityVehicleF_Physics vehicleTargeted = world.getRaytraced(EntityVehicleF_Physics.class, controller.getPosition(), controller.getPosition().copy().add(controller.getLineOfSight(RAYTRACE_DISTANCE)), true, vehicleOn);
                    if (vehicleTargeted != null) {
                        for (APart part : vehicleTargeted.parts) {
                            if (part instanceof PartEngine) {
                                engineTarget = (PartEngine) part;
                                break;
                            }
                        }
                    }
                }
            }

            //If we are holding the trigger, request to fire.
            if (playerHoldingTrigger) {
                state = state.promote(LauncherState.FIRING_REQUESTED);
            } else {
                state = state.demote(LauncherState.CONTROLLED);
            }
        }

        //Get the delta between our orientation and the player's orientation.
        if (lastControllerSeat != null) {
            controllerRelativeLookVector.computeVectorAngles(controller.getOrientation(), zeroReferenceOrientation);
            handleMovement(controllerRelativeLookVector.y - internalOrientation.angles.y, controllerRelativeLookVector.x - internalOrientation.angles.x);
            //If the seat is a part on us, or the seat has animations linked to us, adjust player rotations.
            //This is required to ensure this launcher doesn't rotate forever.
            if (!lastControllerSeat.externalAnglesRotated.isZero() && lastControllerSeat.placementDefinition.animations != null) {
                boolean updateYaw = false;
                boolean updatePitch = false;
                for (JSONAnimationDefinition def : lastControllerSeat.placementDefinition.animations) {
                    if (def.variable.contains("launcher_yaw")) {
                        updateYaw = true;
                    } else if (def.variable.contains("launcher_pitch")) {
                        updatePitch = true;
                    }
                }
                if (updateYaw) {
                    lastControllerSeat.riderRelativeOrientation.angles.y -= (internalOrientation.angles.y - prevInternalOrientation.angles.y);

                }
                if (updatePitch) {
                    lastControllerSeat.riderRelativeOrientation.angles.x -= (internalOrientation.angles.x - prevInternalOrientation.angles.x);
                }
            }
        }
    }

    /**
     * Helper method to validate a target as possible for this launcher.
     * Checks entity position relative to the launcher, and if the entity
     * is behind any blocks.  Returns true if the target is valid.
     * Also sets {@link #targetVector} and {@link #targetAngles}
     */
    private boolean validateTarget(IWrapperEntity target) {
        if (target.isValid()) {
            //Get vector from eyes of controller to target.
            //Target we aim for the middle, as it's more accurate.
            //We also take into account tracking for rocket speed.
            targetVector.set(target.getPosition());
            targetVector.y += target.getEyeHeight() / 2D;
            double ticksToTarget = target.getPosition().distanceTo(position) / definition.launcher.exitVelocity / 20D / 10D;
            targetVector.add(target.getVelocity().scale(ticksToTarget)).subtract(position);

            //Transform vector to launcher's coordinate system.
            //Get the angles the launcher has to rotate to match the target.
            //If the are outside the launcher's clamps, this isn't a valid target.
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
     * Attempts to reload the launcher with the passed-in item.  Returns true if the item is a rocket
     * and was loaded, false if not.  Provider methods are then called for packet callbacks.
     */
    public boolean tryToReload(ItemRocket item) {
        //Only fill rockets if we match the rocket already in the launcher, or if our diameter matches, or if we got a signal on the client.
        //Also don't fill rockets if we are currently reloading rockets.
        if (item.definition.rocket != null) {
            boolean isNewRocketValid = item.definition.rocket.diameter == definition.launcher.diameter && item.definition.rocket.length >= definition.launcher.minLength && item.definition.rocket.length <= definition.launcher.maxLength;
            if ((reloadingRocket == null && (loadedRocket == null ? isNewRocketValid : loadedRocket.equals(item)))
                    && item.definition.rocket.quantity + rocketsLeft <= definition.launcher.capacity) {
                reloadingRocket = item;
                reloadTimeRemaining = definition.launcher.reloadTime;
                InterfaceManager.packetInterface.sendToAllClients(new PacketPartLauncher(this, reloadingRocket));
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the controller for the launcher.
     * The returned value may be a player riding the entity that this launcher is on,
     * or perhaps a player in a seat that's on this launcher.  May also be the player
     * hodling this launcher if the launcher is hand-held.
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
     * Helper method to set the position and velocity of a rocket's spawn.
     * This is based on the passed-in muzzle, and the parameters of that muzzle.
     * Used in both spawning the rocket, and in rendering where the muzzle position is.
     */
    @Override
    public void setProjectileSpawn(Point3D projectilePosition, Point3D projectileVelocity, RotationMatrix projectileOrientation, JSONMuzzle muzzle) {
        //Set velocity.
        if (definition.launcher.exitVelocity != 0) {
            projectileVelocity.set(0, 0, definition.launcher.exitVelocity / 20D / 10D);

            //Now that velocity is set, rotate it to match the gun's orientation.
            //For this, we get the reference orientation, and our internal orientation.
            if (muzzle.rot != null) {
                projectileVelocity.rotate(muzzle.rot);
            }
            projectileVelocity.rotate(internalOrientation).rotate(zeroReferenceOrientation);
        } else {
            projectileVelocity.set(0, 0, 0);
        }

        // Have the super method finish setting all the bullet properties
        super.setProjectileSpawn(projectilePosition, projectileVelocity, projectileOrientation, muzzle);
    }

    @Override
    public double getRawVariableValue(String variable, float partialTicks) {
        switch (variable) {
            case ("launcher_inhand"):
                return entityOn instanceof EntityPlayerGun ? 1 : 0;
            case ("launcher_controller_firstperson"):
                return InterfaceManager.clientInterface.getClientPlayer().equals(lastController) && InterfaceManager.clientInterface.inFirstPerson() ? 1 : 0;
            case ("launcher_inhand_sneaking"):
                return entityOn instanceof EntityPlayerGun && ((EntityPlayerGun) entityOn).player != null && ((EntityPlayerGun) entityOn).player.isSneaking() ? 1 : 0;
            case ("launcher_active"):
                return state.isAtLeast(LauncherState.CONTROLLED) ? 1 : 0;
            case ("launcher_firing"):
                return state.isAtLeast(LauncherState.FIRING_REQUESTED) ? 1 : 0;
            case ("launcher_fired"):
                return firedThisCheck ? 1 : 0;
            case ("launcher_muzzleflash"):
                return firedThisCheck && lastMillisecondFired + 25 < System.currentTimeMillis() ? 1 : 0;
            case ("launcher_lockedon"):
                return entityTarget != null || engineTarget != null ? 1 : 0;
            case ("launcher_lockedon_x"):
                return entityTarget != null ? entityTarget.getPosition().x : (engineTarget != null ? engineTarget.position.x : 0);
            case ("launcher_lockedon_y"):
                return entityTarget != null ? entityTarget.getPosition().y : (engineTarget != null ? engineTarget.position.y : 0);
            case ("launcher_lockedon_z"):
                return entityTarget != null ? entityTarget.getPosition().z : (engineTarget != null ? engineTarget.position.z : 0);
            case ("launcher_pitch"):
                return partialTicks != 0 ? prevInternalOrientation.angles.x + (internalOrientation.angles.x - prevInternalOrientation.angles.x) * partialTicks : internalOrientation.angles.x;
            case ("launcher_yaw"):
                return partialTicks != 0 ? prevInternalOrientation.angles.y + (internalOrientation.angles.y - prevInternalOrientation.angles.y) * partialTicks : internalOrientation.angles.y;
            case ("launcher_pitching"):
                return prevInternalOrientation.angles.x != internalOrientation.angles.x ? 1 : 0;
            case ("launcher_yawing"):
                return prevInternalOrientation.angles.y != internalOrientation.angles.y ? 1 : 0;
            case ("launcher_cooldown"):
                return cooldownTimeRemaining > 0 ? 1 : 0;
            case ("launcher_windup_time"):
                return windupTimeCurrent;
            case ("launcher_windup_rotation"):
                return windupRotation;
            case ("launcher_windup_complete"):
                return windupTimeCurrent == definition.launcher.windupTime ? 1 : 0;
            case ("launcher_reload"):
                return reloadTimeRemaining > 0 ? 1 : 0;
            case ("launcher_ammo_count"):
                return rocketsLeft;
            case ("launcher_ammo_percent"):
                return (float) (rocketsLeft / definition.launcher.capacity);
            case ("launcher_active_muzzlegroup"):
                return currentMuzzleGroupIndex + 1;
        }
        return super.getRawVariableValue(variable, partialTicks);
    }

    @Override
    public String getRawTextVariableValue(JSONText textDef, float partialTicks) {
        if (textDef.variableName.equals("launcher_lockedon_name")) {
            return entityTarget != null ? entityTarget.getName() : (engineTarget != null ? engineTarget.masterEntity.getItem().getItemName() : "");
        }

        return super.getRawTextVariableValue(textDef, partialTicks);
    }

    @Override
    public IWrapperNBT save(IWrapperNBT data) {
        super.save(data);
        data.setInteger("rocketsLeft", rocketsLeft);
        data.setInteger("currentMuzzleGroupIndex", currentMuzzleGroupIndex);
        data.setPoint3d("internalAngles", internalOrientation.angles);
        if (loadedRocket != null) {
            data.setString("loadedRocketPack", loadedRocket.definition.packID);
            data.setString("loadedRocketName", loadedRocket.definition.systemName);
        }
        if (reloadingRocket != null) {
            data.setString("reloadingRocketPack", reloadingRocket.definition.packID);
            data.setString("reloadingRocketName", reloadingRocket.definition.systemName);
        }
        return data;
    }
}
