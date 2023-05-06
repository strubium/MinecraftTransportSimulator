package mcinterface1165.mixin.common;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import mcinterface1165.BuilderEntityExisting;
import mcinterface1165.WrapperEntity;
import minecrafttransportsimulator.entities.components.AEntityD_Definable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Pose;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;

@Mixin(Entity.class)
public abstract class EntityMixin {
    /**
     * Need this to force eye position while in vehicles.
     * Otherwise, MC uses standard position, which will be wrong.
     */
    @Inject(method = "getEyePosition(F)Lnet/minecraft/util/math/vector/Vector3d;", at = @At(value = "HEAD"), cancellable = true)
    private void inject_getEyePosition(float pPartialTicks, CallbackInfoReturnable<Vector3d> ci) {
        AEntityD_Definable<?> riding = WrapperEntity.getWrapperFor((Entity) ((Object) this)).getEntityRiding();
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
            Entity entity = (Entity) ((Object) this);
            AxisAlignedBB box = entity.getBoundingBox();
            boolean collidedWithVehicle = false;
            for (BuilderEntityExisting builder : entity.level.getEntitiesOfClass(BuilderEntityExisting.class, box.expandTowards(movement))) {
                if (builder.collisionBoxes != null) {
                    movement = builder.collisionBoxes.getCollision(movement, box);
                    collidedWithVehicle = true;
                }
            }
            if (collidedWithVehicle) {
                ci.setReturnValue(movement);
            }
        }
    }


    /**
     * Need this to force pose while in vehicles.
     * For some reason, MC collides with them and gets confused.
     */
    @Inject(method = "canEnterPose(Lnet/minecraft/entity/Pose;)Z", at = @At(value = "HEAD"), cancellable = true)
    private void inject_canEnterPose(Pose pPose, CallbackInfoReturnable<Boolean> ci) {
        //TODO this probably won't be needed when we do collisions without builders.
        AEntityD_Definable<?> riding = WrapperEntity.getWrapperFor((Entity) ((Object) this)).getEntityRiding();
        if (riding != null && pPose != Pose.STANDING) {
            ci.setReturnValue(false);
        }
    }
}
