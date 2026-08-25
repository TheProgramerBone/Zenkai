package com.hmc.zenkai.content.entity;

import com.hmc.zenkai.feature.master.MasterManager;
import com.hmc.zenkai.network.OpenMasterPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

/**
 * Base de los maestros. Extiende ZenkaiDefaultNPC (inmortal, quieto, animación idle) y añade
 * lo único que los distingue: al hacer clic derecho comprueban si te aceptan y, si sí, te
 * abren su tienda.
 *
 * CADA MAESTRO ES SU PROPIO EntityType. Es a propósito y no es un capricho de diseño:
 * NpcMarkerBlockEntity spawnea por ResourceLocation de EntityType y no tiene ranura para
 * pasar un id. Con una sola entidad parametrizada, cada maestro colocado con el
 * marcador saldrían siendo el mismo. Así una subclase son ocho líneas y el marcador funciona
 * sin tocarlo.
 *
 * Los datos (PL requerido, alineamiento) NO viven aquí: viven en el datapack y los resuelve
 * MasterManager por {@link #masterId()}.
 */
public abstract class ZenkaiMasterEntity extends ZenkaiDefaultNPC {

    /** Rango en el que mira al jugador. Independiente del rango de interacción. */
    protected static final float LOOK_RANGE = 8.0f;

    protected ZenkaiMasterEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    /** Id del maestro. Debe coincidir con el campo "master" de sus SkillDef y con el nombre
     *  del archivo en data/&lt;ns&gt;/zenkai_masters/. */
    public abstract String masterId();

    /** Hacia dónde mira en reposo. Sobrescribible por maestro. */
    protected float restYaw() { return 180.0f; }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, LOOK_RANGE));
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    protected @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        if (!this.level().isClientSide && player instanceof ServerPlayer sp) {
            MasterManager.Result r = MasterManager.check(sp, masterId(), this);
            if (!r.ok()) {
                MasterManager.tell(sp, masterId(), r);
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
            // Agachado = el favor del maestro (Korin y sus semillas); de pie = su tienda.
            // Separados porque son dos cosas distintas y mezclarlas en un clic hace que el
            // jugador nunca sepa cuál va a pasar.
            if (player.isShiftKeyDown() && offerFavor(sp)) {
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
            PacketDistributor.sendToPlayer(sp, new OpenMasterPayload(masterId(), this.getId()));
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    /** Lo que este maestro da además de enseñar. Por defecto nada: devolver false hace que
     *  el shift-clic caiga en la tienda, así que un maestro sin favor no se queda mudo. */
    protected boolean offerFavor(ServerPlayer sp) { return false; }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0f)
                .add(Attributes.MOVEMENT_SPEED, 0.0f);
    }

    /** Sin jugador cerca vuelve a su orientación de reposo. Mismo truco que Yemma: sin esto
     *  se quedan mirando hacia donde estuvo el último jugador, para siempre. */
    @Override
    public void aiStep() {
        super.aiStep();
        if (this.level().isClientSide) return;

        if (this.level().getNearestPlayer(this, LOOK_RANGE) == null) {
            float yaw = restYaw();
            this.setYRot(yaw);
            this.yRotO = yaw;
            this.setYBodyRot(yaw);
            this.setYHeadRot(yaw);
            this.yHeadRotO = yaw;
            this.getNavigation().stop();
        }
    }
}