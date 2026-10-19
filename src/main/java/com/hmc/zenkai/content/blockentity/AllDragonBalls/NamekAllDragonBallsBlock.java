package com.hmc.zenkai.content.blockentity.AllDragonBalls;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.wishes.WishBroadcast;
import com.hmc.zenkai.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

/**
 * Las siete esferas de Namek fundidas (las forma DragonBallStructureGenerator con la H de esferas
 * namekianas). Mismo bloque, modelo, textura y entidad que el de la Tierra; cambia solo quién
 * responde y cuánto concede: PORUNGA TODAVÍA NO TIENE MODELO, así que invoca a Shenlong con los
 * deseos de {@link ServerConfig#namekDragonWishCount()} (3 por defecto) y avisa de ello. Cuando
 * Porunga tenga modelo, esta clase es el único sitio que cambia: summonShenron() pasará a su entidad
 * y el aviso desaparece (petición del usuario, 2026-10-18).
 */
public class NamekAllDragonBallsBlock extends AllDragonBallsBlock {
    public static final MapCodec<NamekAllDragonBallsBlock> NAMEK_CODEC = simpleCodec(NamekAllDragonBallsBlock::new);

    public NamekAllDragonBallsBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return NAMEK_CODEC;
    }

    @Override
    protected int wishCount() {
        return ServerConfig.namekDragonWishCount();
    }

    @Override
    protected void announceSummon(Player player) {
        WishBroadcast.nearby(player, Component.translatable("messages.zenkai.porunga_placeholder", wishCount()), false);
        super.announceSummon(player);
    }

    @Override
    protected Block[] returnedBalls() {
        return new Block[]{ModBlocks.NAMEK_DRAGON_BALL_1.get(), ModBlocks.NAMEK_DRAGON_BALL_2.get(),
                ModBlocks.NAMEK_DRAGON_BALL_3.get(), ModBlocks.NAMEK_DRAGON_BALL_4.get(),
                ModBlocks.NAMEK_DRAGON_BALL_5.get(), ModBlocks.NAMEK_DRAGON_BALL_6.get(),
                ModBlocks.NAMEK_DRAGON_BALL_7.get()};
    }
}
