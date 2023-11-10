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
    private static final Point3D ZERO = new Point3D();

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
            WrapperEntity entity = WrapperEntity.getWrapperFor(mcEntity);
            if (entity.getEntityRiding() == null) {
                WrapperWorld world = WrapperWorld.getWrapperFor(mcEntity.level);
                AxisAlignedBB box = mcEntity.getBoundingBox().expandTowards(movement);

                BoundingBox collisionBounds = new BoundingBox(ZERO, 0);
                collisionBounds.widthRadius = (box.maxX - box.minX) / 2D;
                collisionBounds.heightRadius = (box.maxY - box.minY) / 2D;
                collisionBounds.depthRadius = (box.maxZ - box.minZ) / 2D;
                collisionBounds.globalCenter.x = box.minX + collisionBounds.widthRadius;
                collisionBounds.globalCenter.y = box.minY + collisionBounds.heightRadius;
                collisionBounds.globalCenter.z = box.minZ + collisionBounds.depthRadius;

                List<BoundingBox> collidingBoxes = new ArrayList<>();
                for (AEntityE_Interactable<?> testEntity : world.collidableEntities) {
                    if (testEntity.encompassingBox.intersects(collisionBounds)) {
                        for (BoundingBox testBox : testEntity.getCollisionBoxes()) {
                            if (testBox.intersects(collisionBounds)) {
                                collidingBoxes.add(testBox);
                            }
                        }
                    }
                }

                if (!collidingBoxes.isEmpty()) {
                    ci.setReturnValue(getCollision(movement, mcEntity.getBoundingBox(), collidingBoxes));
                }
            }
        }
    }

    /**
     * Helper method that's akin to MC's older collision methods in 1.12.2, just here rather than
     * in a VoxelShape.
     */
    private static Vector3d getCollision(Vector3d movement, AxisAlignedBB testBox, List<BoundingBox> collidingBoxes) {
        double x = movement.x != 0 ? calculateXOffset(testBox, movement.x, collidingBoxes) : 0;
        double y = movement.y != 0 ? calculateYOffset(testBox, movement.y, collidingBoxes) : 0;
        double z = movement.z != 0 ? calculateZOffset(testBox, movement.z, collidingBoxes) : 0;
        return new Vector3d(x, y, z);
    }

    private static double calculateXOffset(AxisAlignedBB box, double offset, List<BoundingBox> collidingBoxes) {
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

    private static double calculateYOffset(AxisAlignedBB box, double offset, List<BoundingBox> collidingBoxes) {
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

    private static double calculateZOffset(AxisAlignedBB box, double offset, List<BoundingBox> collidingBoxes) {
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
