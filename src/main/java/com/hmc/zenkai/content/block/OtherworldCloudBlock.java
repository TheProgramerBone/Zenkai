package com.hmc.zenkai.content.block;

import com.hmc.zenkai.feature.alignment.AlignmentTier;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * Nube del Otherworld/HFIL: sólida para jugadores GOOD/NEUTRAL (ver {@link AlignmentTier}),
 * pero SOLO mientras no vayan agachados — igual que hace vainilla con el andamio
 * ({@code ScaffoldingBlock.getCollisionShape}, {@code context.isDescending()}): agacharse
 * ("shift") hace que la oclusión desaparezca en ambos sentidos (bajar Y volver a subir), sin
 * necesidad de ningún teletransporte. Para jugadores EVIL la nube nunca tiene colisión, con o
 * sin shift — la atraviesan directamente. Cualquier otra entidad/contexto sin entidad (mobs,
 * proyectiles, pathfinding, heightmap) tampoco tiene colisión, igual que el bloque anónimo
 * original (.noCollission()). Este override REEMPLAZA por completo el comportamiento por
 * defecto de {@code hasCollision}/{@code .noCollission()} en las Properties, así que ese flag
 * del builder queda inerte aquí a propósito — no hace falta tocarlo en ModBlocks.
 */
public class OtherworldCloudBlock extends Block {

    public OtherworldCloudBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @NotNull VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                                      @NotNull BlockPos pos, @NotNull CollisionContext context) {
        // CollisionContext no expone la entidad directamente: solo su implementación
        // EntityCollisionContext la lleva. Sin entidad (pathfinding, heightmap,
        // CollisionContext.empty()) => sin colisión, igual que siempre.
        Player player = (context instanceof EntityCollisionContext ecc && ecc.getEntity() instanceof Player p)
                ? p : null;
        if (player == null) return Shapes.empty();

        int alignment = PlayerStatsAttachment.get(player).getAlignment();
        if (AlignmentTier.of(alignment) == AlignmentTier.EVIL) return Shapes.empty();

        // GOOD/NEUTRAL: sólida salvo mientras se agachan. isDescending() == isShiftKeyDown()
        // en vainilla (ver Entity.isDescending()), así que esto responde al shift incluso
        // volando, no solo caminando/cayendo.
        return context.isDescending() ? Shapes.empty() : Shapes.block();
    }
}
