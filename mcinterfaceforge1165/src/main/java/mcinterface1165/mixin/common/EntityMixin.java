package mcinterface1165.mixin.common;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import mcinterface1165.WrapperEntity;
import mcinterface1165.WrapperWorld;
import minecrafttransportsimulator.baseclasses.BoundingBox;
import minecrafttransportsimulator.baseclasses.Point3D;
import minecrafttransportsimulator.entities.components.AEntityE_Interactable;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;

@Mixin(Entity.class)
public abstract class EntityMixin {
    private final BoundingBox mutableCollisionBounds = new BoundingBox(new Point3D(), 0);
    private final List<BoundingBox> collidingBoxes = new ArrayList<>();

    /**
     * Need this to force eye position while in vehicles.
     * Otherwise, MC uses standard position, which will be wrong.
     */
    @Inject(method = "getEyePosition(F)Lnet/minecraft/util/math/vector/Vector3d;", at = @At(value = "HEAD"), cancellable = true)
    private void inject_getEyePosition(float pPartialTicks, CallbackInfoReturnable<Vector3d> ci) {
        AEntityE_Interactable<?> riding = WrapperEntity.getWrapperFor((Entity) ((Object) this)).getEntityRiding();
        if (riding != null) {
            ci.setReturnValue(new Vector3d(riding.riderHeadPosition.x, riding.riderHeadPosition.y, riding.riderHeadPosition.z));
        }
    }

    /**
     * Need this to force collision with vehicles.
     */
    @Inject(method = "collide(Lnet/minecraft/util/math/vector/Vector3d;)Lnet/minecraft/util/math/vector/Vector3d;", at = @At(value = "HEAD"), cancellable = true)
    private void inject_collide(Vector3d movement, CallbackInfoReturnable<Vector3d> ci) {
        if (WrapperEntity.getWrapperFor((Entity) ((Object) this)).getEntityRiding() == null) {
            Entity mcEntity = (Entity) ((Object) this);
            WrapperWorld world = WrapperWorld.getWrapperFor(mcEntity.level);
            AxisAlignedBB box = mcEntity.getBoundingBox().expandTowards(movement);

            mutableCollisionBounds.widthRadius = (box.maxX - box.minX) / 2D;
            mutableCollisionBounds.heightRadius = (box.maxY - box.minY) / 2D;
            mutableCollisionBounds.depthRadius = (box.maxZ - box.minZ) / 2D;
            mutableCollisionBounds.globalCenter.x = box.minX + mutableCollisionBounds.widthRadius;
            mutableCollisionBounds.globalCenter.y = box.minY + mutableCollisionBounds.heightRadius;
            mutableCollisionBounds.globalCenter.z = box.minZ + mutableCollisionBounds.depthRadius;

            collidingBoxes.clear();
            for (AEntityE_Interactable<?> entity : world.collidableEntities) {
                if (entity.encompassingBox.intersects(mutableCollisionBounds)) {
                    for (BoundingBox testBox : entity.getCollisionBoxes()) {
                        if (testBox.intersects(mutableCollisionBounds)) {
                            collidingBoxes.add(testBox);
                        }
                    }
                }
            }

            if (!collidingBoxes.isEmpty()) {
                getCollision(movement, box);
                ci.setReturnValue(movement);
            }
        }
    }

    /**
     * Helper method that's akin to MC's older collision methods in 1.12.2, just here rather than
     * in a VoxelShape.
     */
    public Vector3d getCollision(Vector3d movement, AxisAlignedBB testBox) {
        double x = movement.x != 0 ? calculateXOffset(testBox, movement.x) : 0;
        double y = movement.y != 0 ? calculateYOffset(testBox, movement.y) : 0;
        double z = movement.z != 0 ? calculateZOffset(testBox, movement.z) : 0;
        return new Vector3d(x, y, z);
    }

    private double calculateXOffset(AxisAlignedBB box, double offset) {
        for (BoundingBox testBox : collidingBoxes) {
            if (box.maxY > testBox.globalCenter.y - testBox.heightRadius && box.minY < testBox.globalCenter.y + testBox.heightRadius && box.maxZ > testBox.globalCenter.z - testBox.depthRadius && box.minZ < testBox.globalCenter.z + testBox.depthRadius) {
                if (offset > 0.0D) {
                    //Positive offset, box.maxX <= this.minX.
                    double collisionDepth = testBox.globalCenter.x - testBox.widthRadius - box.maxX;
                    if (collisionDepth >= 0 && collisionDepth < offset) {
                        offset = collisionDepth;
                    }
                } else if (offset < 0.0D) {
                    //Negative offset, box.minX >= this.maxX.
                    double collisionDepth = testBox.globalCenter.x + testBox.widthRadius - box.minX;
                    if (collisionDepth <= 0 && collisionDepth > offset) {
                        offset = collisionDepth;
                    }
                }
            }
        }
        return offset;
    }

    private double calculateYOffset(AxisAlignedBB box, double offset) {
        for (BoundingBox testBox : collidingBoxes) {
            if (box.maxX > testBox.globalCenter.x - testBox.widthRadius && box.minX < testBox.globalCenter.x + testBox.widthRadius && box.maxZ > testBox.globalCenter.z - testBox.depthRadius && box.minZ < testBox.globalCenter.z + testBox.depthRadius) {
                if (offset > 0.0D) {
                    //Positive offset, box.maxX <= this.minX.
                    double collisionDepth = testBox.globalCenter.y - testBox.heightRadius - box.maxY;
                    if (collisionDepth >= 0 && collisionDepth < offset) {
                        offset = collisionDepth;
                    }
                } else if (offset < 0.0D) {
                    //Negative offset, box.minX >= this.maxX.
                    double collisionDepth = testBox.globalCenter.y + testBox.heightRadius - box.minY;
                    if (collisionDepth <= 0 && collisionDepth > offset) {
                        offset = collisionDepth;
                    }
                }
            }
        }
        return offset;
    }

    private double calculateZOffset(AxisAlignedBB box, double offset) {
        for (BoundingBox testBox : collidingBoxes) {
            if (box.maxX > testBox.globalCenter.x - testBox.widthRadius && box.minX < testBox.globalCenter.x + testBox.widthRadius && box.maxY > testBox.globalCenter.y - testBox.heightRadius && box.minY < testBox.globalCenter.y + testBox.heightRadius) {
                if (offset > 0.0D) {
                    //Positive offset, box.maxX <= this.minX.
                    double collisionDepth = testBox.globalCenter.z - testBox.depthRadius - box.maxZ;
                    if (collisionDepth >= 0 && collisionDepth < offset) {
                        offset = collisionDepth;
                    }
                } else if (offset < 0.0D) {
                    //Negative offset, box.minX >= this.maxX.
                    double collisionDepth = testBox.globalCenter.z + testBox.depthRadius - box.minZ;
                    if (collisionDepth <= 0 && collisionDepth > offset) {
                        offset = collisionDepth;
                    }
                }
            }
        }
        return offset;
    }
}
