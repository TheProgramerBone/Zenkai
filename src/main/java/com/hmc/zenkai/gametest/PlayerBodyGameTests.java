package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.entity.misc.PlayerBodyEntity;
import com.hmc.zenkai.feature.player.death.PlayerBodyInteractEvent;
import com.hmc.zenkai.registry.ModDamageTypes;
import com.hmc.zenkai.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Cuerpos de jugador: la desintegración con ki (solo con {@code permadeath.body_griefing}) y el evento de interacción.
 * Se cambia la config del servidor de prueba y se restaura siempre, también si el test falla.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlayerBodyGameTests {
    private PlayerBodyGameTests() {}

    private static PlayerBodyEntity body(GameTestHelper helper, ServerPlayer owner) {
        PlayerBodyEntity b = ModEntities.PLAYER_BODY.get().create(helper.getLevel());
        b.configure(owner.getUUID(), "test-owner", "human", "", "", 1f, helper.getLevel().getGameTime() - 1000);
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        b.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        helper.getLevel().addFreshEntity(b);
        return b;
    }

    private static DamageSource ki(GameTestHelper helper) {
        return helper.getLevel().damageSources().source(ModDamageTypes.KI_BLAST);
    }

    private static void withGriefing(boolean on, Runnable body) {
        boolean prev = ServerConfig.PERMADEATH_BODY_GRIEFING.get();
        int prevGrace = ServerConfig.PERMADEATH_OWNER_GRACE_SECONDS.get();
        try {
            ServerConfig.PERMADEATH_BODY_GRIEFING.set(on);
            ServerConfig.PERMADEATH_OWNER_GRACE_SECONDS.set(0);
            body.run();
        } finally {
            ServerConfig.PERMADEATH_BODY_GRIEFING.set(prev);
            ServerConfig.PERMADEATH_OWNER_GRACE_SECONDS.set(prevGrace);
        }
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void kiDoesNotTouchBodyWithoutGriefing(GameTestHelper helper) {
        ServerPlayer owner = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        withGriefing(false, () -> {
            PlayerBodyEntity b = body(helper, owner);
            b.inventory().setItem(0, new ItemStack(Items.DIAMOND));
            helper.assertTrue(!b.hurt(ki(helper), 100f), "Sin body_griefing el ki no debe afectar al cuerpo");
            helper.assertTrue(!b.isRemoved() && b.hasItems(), "El cuerpo y su contenido siguen intactos");
        });
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void kiDisintegratesBodyWithGriefing(GameTestHelper helper) {
        ServerPlayer owner = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        withGriefing(true, () -> {
            PlayerBodyEntity b = body(helper, owner);
            b.inventory().setItem(0, new ItemStack(Items.DIAMOND));
            helper.assertTrue(b.hurt(ki(helper), 1f), "Con body_griefing un impacto de ki lo desintegra");
            helper.assertTrue(b.isRemoved(), "El cuerpo desaparece");
            var drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    b.getBoundingBox().inflate(3.0), i -> i.getItem().is(Items.DIAMOND));
            helper.assertTrue(!drops.isEmpty(), "Su contenido cae al suelo");
        });
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void nonKiDamageNeverHurtsBody(GameTestHelper helper) {
        ServerPlayer owner = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        withGriefing(true, () -> {
            PlayerBodyEntity b = body(helper, owner);
            DamageSource generic = helper.getLevel().damageSources().generic();
            helper.assertTrue(!b.hurt(generic, 1000f) && !b.isRemoved(), "Solo el ki destruye un cuerpo, ni con griefing");
            b.discard();
        });
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void interactEventKnowsOwnBody(GameTestHelper helper) {
        ServerPlayer owner = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        PlayerBodyEntity b = body(helper, owner);
        List<PlayerBodyInteractEvent> seen = new ArrayList<>();
        Consumer<PlayerBodyInteractEvent> listener = seen::add;
        NeoForge.EVENT_BUS.addListener(PlayerBodyInteractEvent.class, listener);
        try {
            PlayerBodyInteractEvent.fire(owner, b, PlayerBodyInteractEvent.Kind.OPEN);
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.assertTrue(seen.size() == 1, "Se publica una vez; se vio " + seen.size());
        helper.assertTrue(seen.get(0).own() && !seen.get(0).ally(), "Su propio cuerpo: own=true, ally=false");
        helper.assertTrue(seen.get(0).kind() == PlayerBodyInteractEvent.Kind.OPEN, "Conserva el tipo");
        b.discard();
        helper.succeed();
    }
}
