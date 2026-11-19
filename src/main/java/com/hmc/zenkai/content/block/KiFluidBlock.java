package com.hmc.zenkai.content.block;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.forms.FormRegistry;
import com.hmc.zenkai.feature.kichamber.KiEnergyType;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skilltree.SkillTree;
import com.hmc.zenkai.registry.ModDamageTypes;
import com.hmc.zenkai.feature.fluidreact.FluidReactions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * Fluido de Ki derramado en el mundo (0.7.0). Hasta esta versión el ki solo vivía en tanques; con
 * cubo y bloque, tocarlo hace algo, con la regla decidida con el usuario (2026-11-17):
 *
 * <b>Con afinidad</b> (lo DESBLOQUEADO, no la forma puesta, así no cambia al transformarse):
 *   Base       → cualquier jugador con raza (con ki). Recarga ki_fluid.recharge_base_percent.
 *   Súper      → alguna forma de tipo Súper comprada en el árbol. Recarga más (recharge_strong).
 *   Divino     → tiene God Ki. Recarga como Súper.
 *   Legendario → es Legendario. Recarga y además deja SOBRELLENAR el ki hasta
 *                legendary_overcharge_percent por encima del pool (PlayerResourcePools.holdOvercharge).
 * <b>Sin afinidad</b>: el fluido se TRAGA el ki (drain_percent por segundo), como la cámara de
 *   contención saca el ki a quien carga; con el ki ya a 0, hace daño.
 * <b>Sin ki</b> (mobs, jugadores sin raza): daño directo. Las entidades del propio mod (maestros,
 *   NPC de combate) tienen ki y no se dañan.
 *
 * Las interacciones con otros fluidos (agua, lava, otros ki, agua curativa, agua hirviendo) no viven
 * aquí: son FluidInteractionRegistry, en KiFluidInteractions. Las transformaciones de bloques
 * sólidos vecinos (adoquín a grava, barro a arcilla...) están en feature.fluidreact.FluidReactions.
 */
public class KiFluidBlock extends LiquidBlock {

    /** entityInside llega cada tick que la entidad solapa el fluido: se actúa dos veces por segundo. */
    private static final int TICK_INTERVAL = 10;

    private final KiEnergyType type;

    public KiFluidBlock(FlowingFluid fluid, KiEnergyType type, Properties properties) {
        super(fluid, properties);
        this.type = type;
    }

    public KiEnergyType kiType() { return type; }

    // ── Transformación de bloques sólidos vecinos (feature.fluidreact.FluidReactions) ──
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        FluidReactions.wake(level, pos, this);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        FluidReactions.wake(level, pos, this);
    }

    /** Un tic aleatorio basta para despertarlo si el bloque sólido se puso mucho antes (o tras recargar). */
    @Override
    protected boolean isRandomlyTicking(BlockState state) { return true; }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        FluidReactions.wake(level, pos, this);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        FluidReactions.tick(level, pos, this, FluidReactions.Family.ofKi(type));
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (level.isClientSide() || entity.tickCount % TICK_INTERVAL != 0) return;
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) return;
        double seconds = TICK_INTERVAL / 20.0;

        if (living instanceof Player player) {
            PlayerStatsAttachment att = PlayerStatsAttachment.get(player);
            if (!att.isRaceChosen()) { hurt(living, seconds); return; }
            int pool = Math.max(1, att.getEnergyMax());
            if (hasAffinity(player, type)) {
                if (type == KiEnergyType.LEGENDARY) {
                    att.pools().holdOvercharge((int) Math.round(pool * ServerConfig.kiFluidLegendaryOvercharge() / 100.0),
                            ServerConfig.kiFluidOverchargeFadeSeconds());
                }
                double pct = type == KiEnergyType.BASE ? ServerConfig.kiFluidRechargeBase() : ServerConfig.kiFluidRechargeStrong();
                att.addKi(Math.max(1.0, pool * pct / 100.0 * seconds));
            } else if (att.getKiCurrent() > 0) {
                att.addKi(-Math.max(1.0, pool * ServerConfig.kiFluidDrain() / 100.0 * seconds));
            } else {
                hurt(living, seconds);
            }
            PlayerLifeCycle.syncIfServer(player);
            return;
        }

        // Entidades del propio mod (maestros, Saibaman, Sombra...) tienen ki: no se dañan.
        if ("zenkai".equals(BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()).getNamespace())) return;
        hurt(living, seconds);
    }

    private static void hurt(LivingEntity e, double seconds) {
        float dmg = (float) (ServerConfig.kiFluidDamage() * seconds);
        if (dmg > 0f) e.hurt(e.damageSources().source(ModDamageTypes.KI_BLAST), dmg);
    }

    /**
     * ¿El jugador «tiene» este tipo de ki? Por lo desbloqueado, nunca por la forma puesta (decisión
     * del usuario): así bañarse en ki Divino no obliga a transformarse. Súper = algún nodo del árbol
     * que sea una forma cuyo tipo (KiEnergyType.of, el embudo de la cámara) es SUPER.
     */
    public static boolean hasAffinity(Player p, KiEnergyType type) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
        if (!att.isRaceChosen()) return false;
        return switch (type) {
            case BASE -> true;
            case SUPER -> att.skillTree().all().keySet().stream()
                    .anyMatch(id -> FormRegistry.get(id) != null && KiEnergyType.of(id) == KiEnergyType.SUPER);
            case DIVINE -> SkillTree.hasGodKi(p);
            case LEGENDARY -> att.isLegendary();
        };
    }
}
