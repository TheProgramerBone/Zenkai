package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.entity.misc.PlayerBodyEntity;
import com.hmc.zenkai.feature.player.death.PlayerBodyManager;
import com.hmc.zenkai.feature.player.death.PlayerBodyRegistry;
import com.hmc.zenkai.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Límites de la muerte permanente: tope de cuerpos por jugador y del mundo, caducidad, y pérdida de objetos y durabilidad al
 * morir. Cada test cambia la config del servidor de prueba y la restaura siempre. Los cuerpos se crean con fechas
 * ({@code createdAt}) explícitas para que el orden de «el más antiguo» no dependa del tick en que se crean.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BodyLimitsGameTests {
    private BodyLimitsGameTests() {}

    /** Cuerpo registrado en el índice, con un diamante dentro y la fecha dada. */
    private static PlayerBodyEntity body(GameTestHelper helper, UUID owner, long createdAt) {
        return body(helper, owner, createdAt, true);
    }

    /** Sin {@code register} el cuerpo no entra en el índice: un test de tope global que corra a la vez no lo retira. */
    private static PlayerBodyEntity body(GameTestHelper helper, UUID owner, long createdAt, boolean register) {
        PlayerBodyEntity b = ModEntities.PLAYER_BODY.get().create(helper.getLevel());
        b.configure(owner, "test-owner", "human", "", "", 1f, createdAt);
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        b.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        b.inventory().setItem(0, new ItemStack(Items.DIAMOND));
        helper.getLevel().addFreshEntity(b);
        if (register) PlayerBodyRegistry.get(helper.getLevel().getServer()).put(
                new PlayerBodyRegistry.Entry(b.getUUID(), owner, helper.getLevel().dimension(), b.blockPosition(), createdAt));
        return b;
    }

    private static int diamondsOnGround(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        int n = 0;
        for (ItemEntity i : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(at).inflate(4.0), e -> e.getItem().is(Items.DIAMOND))) n += i.getItem().getCount();
        return n;
    }

    private static void cleanup(GameTestHelper helper, List<PlayerBodyEntity> bodies) {
        PlayerBodyRegistry reg = PlayerBodyRegistry.get(helper.getLevel().getServer());
        for (PlayerBodyEntity b : bodies) {
            reg.remove(b.getUUID());
            if (!b.isRemoved()) b.discard();
        }
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void perPlayerCapRetiresOldest(GameTestHelper helper) {
        ServerPlayer owner = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        int prevCap = ServerConfig.PERMADEATH_BODY_CAP.get();
        int prevWorld = ServerConfig.PERMADEATH_WORLD_BODY_CAP.get();
        List<PlayerBodyEntity> all = new ArrayList<>();
        try {
            ServerConfig.PERMADEATH_BODY_CAP.set(2);
            ServerConfig.PERMADEATH_WORLD_BODY_CAP.set(0);   // el tope global no interviene
            PlayerBodyEntity oldest = body(helper, owner.getUUID(), -3000);
            PlayerBodyEntity middle = body(helper, owner.getUUID(), -2000);
            PlayerBodyEntity newest = body(helper, owner.getUUID(), -1000);
            all.addAll(List.of(oldest, middle, newest));
            PlayerBodyManager.registerCreated(owner, newest);
            helper.assertTrue(oldest.isRemoved(), "Con tope 2 y 3 cuerpos, desaparece el más antiguo");
            helper.assertTrue(!middle.isRemoved() && !newest.isRemoved(), "Los dos más recientes se quedan");
            helper.assertTrue(diamondsOnGround(helper) == 1, "El contenido del retirado cae al suelo; hay " + diamondsOnGround(helper));
            helper.assertTrue(PlayerBodyRegistry.get(helper.getLevel().getServer()).of(owner.getUUID()).size() == 2,
                    "El índice queda con 2 cuerpos");
        } finally {
            ServerConfig.PERMADEATH_BODY_CAP.set(prevCap);
            ServerConfig.PERMADEATH_WORLD_BODY_CAP.set(prevWorld);
            cleanup(helper, all);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void worldCapRetiresOldestOfAnyOwner(GameTestHelper helper) {
        ServerPlayer a = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        UUID other = UUID.randomUUID();
        int prevCap = ServerConfig.PERMADEATH_BODY_CAP.get();
        int prevWorld = ServerConfig.PERMADEATH_WORLD_BODY_CAP.get();
        List<PlayerBodyEntity> all = new ArrayList<>();
        try {
            ServerConfig.PERMADEATH_BODY_CAP.set(32);
            // Fechas muy anteriores a cualquier cuerpo que otro test haya dejado en el índice: los míos son los más antiguos.
            PlayerBodyEntity oldest = body(helper, other, -9_000_000);
            PlayerBodyEntity middle = body(helper, other, -8_000_000);
            PlayerBodyEntity newest = body(helper, a.getUUID(), -7_000_000);
            all.addAll(List.of(oldest, middle, newest));
            ServerConfig.PERMADEATH_WORLD_BODY_CAP.set(PlayerBodyRegistry.get(helper.getLevel().getServer()).all().size() - 1);
            PlayerBodyManager.registerCreated(a, newest);
            helper.assertTrue(oldest.isRemoved(), "Pasado el tope global, desaparece el más antiguo del mundo (aunque sea de otro dueño)");
            helper.assertTrue(!middle.isRemoved() && !newest.isRemoved(), "Solo sobra uno: los demás se quedan");
            helper.assertTrue(diamondsOnGround(helper) == 1, "Su contenido cae al suelo");
        } finally {
            ServerConfig.PERMADEATH_BODY_CAP.set(prevCap);
            ServerConfig.PERMADEATH_WORLD_BODY_CAP.set(prevWorld);
            cleanup(helper, all);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void worldCapKeepsTheNewBodyEvenIfOverCap(GameTestHelper helper) {
        ServerPlayer a = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        int prevCap = ServerConfig.PERMADEATH_BODY_CAP.get();
        int prevWorld = ServerConfig.PERMADEATH_WORLD_BODY_CAP.get();
        List<PlayerBodyEntity> all = new ArrayList<>();
        try {
            ServerConfig.PERMADEATH_BODY_CAP.set(32);
            // El cuerpo recién creado es el MÁS antiguo del índice, pero nunca se retira a sí mismo.
            PlayerBodyEntity only = body(helper, a.getUUID(), -9_000_000);
            all.add(only);
            ServerConfig.PERMADEATH_WORLD_BODY_CAP.set(1);
            PlayerBodyManager.registerCreated(a, only);
            helper.assertTrue(!only.isRemoved(), "El cuerpo que acaba de crearse no se retira a sí mismo");
        } finally {
            ServerConfig.PERMADEATH_BODY_CAP.set(prevCap);
            ServerConfig.PERMADEATH_WORLD_BODY_CAP.set(prevWorld);
            cleanup(helper, all);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void expiredBodyEmptiesOnGround(GameTestHelper helper) {
        ServerPlayer owner = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        int prevDays = ServerConfig.PERMADEATH_BODY_LIFETIME_DAYS.get();
        ServerConfig.PERMADEATH_BODY_LIFETIME_DAYS.set(1);
        long now = helper.getLevel().getGameTime();
        PlayerBodyEntity expired = body(helper, owner.getUUID(), now - 2 * 24000L, false);
        PlayerBodyEntity fresh = body(helper, owner.getUUID(), now, false);
        // El cuerpo comprueba su edad una vez por segundo (cada 20 ticks): se espera más que eso.
        helper.runAfterDelay(45, () -> {
            try {
                helper.assertTrue(expired.isRemoved(), "Con 1 día de vida, el cuerpo de hace 2 días desaparece");
                helper.assertTrue(!fresh.isRemoved(), "El cuerpo de ahora sigue en pie (razon=" + fresh.getRemovalReason() + ", t=" + helper.getLevel().getGameTime() + ", now=" + now + ")");
                helper.assertTrue(diamondsOnGround(helper) == 1, "El caducado suelta su contenido; hay " + diamondsOnGround(helper));
                ServerConfig.PERMADEATH_BODY_LIFETIME_DAYS.set(0);
                PlayerBodyEntity ancient = body(helper, owner.getUUID(), now - 5000 * 24000L, false);
                helper.runAfterDelay(45, () -> {
                    try {
                        helper.assertTrue(!ancient.isRemoved(), "Con 0 días (nunca caduca) ni uno antiguo desaparece");
                    } finally {
                        ServerConfig.PERMADEATH_BODY_LIFETIME_DAYS.set(prevDays);
                        cleanup(helper, List.of(expired, fresh, ancient));
                    }
                    helper.succeed();
                });
            } catch (RuntimeException e) {
                ServerConfig.PERMADEATH_BODY_LIFETIME_DAYS.set(prevDays);
                cleanup(helper, List.of(expired, fresh));
                throw e;
            }
        });
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void deathLossRemovesStacksAndWearsDurability(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        int prevLoss = ServerConfig.PERMADEATH_ITEM_LOSS_PERCENT.get();
        int prevDur = ServerConfig.PERMADEATH_DURABILITY_LOSS_PERCENT.get();
        try {
            // Sin riesgo: nada cambia.
            ServerConfig.PERMADEATH_ITEM_LOSS_PERCENT.set(0);
            ServerConfig.PERMADEATH_DURABILITY_LOSS_PERCENT.set(0);
            List<ItemStack> items = new ArrayList<>(List.of(new ItemStack(Items.DIAMOND, 5), new ItemStack(Items.IRON_PICKAXE)));
            PlayerBodyManager.applyDeathLoss(items, sp);
            helper.assertTrue(items.size() == 2 && items.get(1).getDamageValue() == 0, "Con 0 % no se pierde ni se desgasta nada");

            // Durabilidad al 50 %: la mitad de la vida máxima, sin tocar lo que no se daña.
            ServerConfig.PERMADEATH_DURABILITY_LOSS_PERCENT.set(50);
            items = new ArrayList<>(List.of(new ItemStack(Items.DIAMOND, 5), new ItemStack(Items.IRON_PICKAXE)));
            PlayerBodyManager.applyDeathLoss(items, sp);
            int max = Items.IRON_PICKAXE.getDefaultInstance().getMaxDamage();
            helper.assertTrue(items.size() == 2, "Con solo desgaste no se pierde ninguna pila");
            helper.assertTrue(items.get(1).getDamageValue() == Math.round(max * 50 / 100f),
                    "Con 50 % pierde la mitad de la durabilidad máxima; daño=" + items.get(1).getDamageValue() + " de " + max);
            helper.assertTrue(items.get(0).getCount() == 5, "Un objeto sin durabilidad no cambia");

            // Al 100 % nunca se rompe: se queda con 1 de vida.
            ServerConfig.PERMADEATH_DURABILITY_LOSS_PERCENT.set(100);
            items = new ArrayList<>(List.of(new ItemStack(Items.IRON_PICKAXE)));
            PlayerBodyManager.applyDeathLoss(items, sp);
            helper.assertTrue(items.get(0).getDamageValue() == max - 1, "Al 100 % queda con 1 de durabilidad, no se rompe");

            // Pérdida de pilas: 100 % las pierde todas.
            ServerConfig.PERMADEATH_DURABILITY_LOSS_PERCENT.set(0);
            ServerConfig.PERMADEATH_ITEM_LOSS_PERCENT.set(100);
            items = new ArrayList<>(List.of(new ItemStack(Items.DIAMOND, 5), new ItemStack(Items.IRON_PICKAXE)));
            PlayerBodyManager.applyDeathLoss(items, sp);
            helper.assertTrue(items.isEmpty(), "Con 100 % de pérdida no queda ninguna pila");

            // 50 % sobre muchas pilas: ni todas ni ninguna.
            ServerConfig.PERMADEATH_ITEM_LOSS_PERCENT.set(50);
            items = new ArrayList<>();
            for (int i = 0; i < 200; i++) items.add(new ItemStack(Items.DIAMOND));
            PlayerBodyManager.applyDeathLoss(items, sp);
            helper.assertTrue(items.size() > 40 && items.size() < 160, "Con 50 % se pierde más o menos la mitad; quedan " + items.size());
        } finally {
            ServerConfig.PERMADEATH_ITEM_LOSS_PERCENT.set(prevLoss);
            ServerConfig.PERMADEATH_DURABILITY_LOSS_PERCENT.set(prevDur);
        }
        helper.succeed();
    }
}
