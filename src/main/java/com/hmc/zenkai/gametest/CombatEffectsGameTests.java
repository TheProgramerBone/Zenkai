package com.hmc.zenkai.gametest;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.combat.CombatModeServerState;
import com.hmc.zenkai.feature.combat.PotionDamageEffects;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Debilidad, Fuerza y daños de efecto (Veneno, Wither, Hambre) en el pipeline de Zenkai (2026-11-25, a raíz de «con debilidad el
 * jugador hace daño nulo»). Ver {@link PotionDamageEffects}. Los tests miden los EVENTOS de daño (no la vida del objetivo, que
 * se espeja a un pool propio) y además imprimen una tabla {@code [CombatFx]} para leer las cifras.
 */
@GameTestHolder(Zenkai.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombatEffectsGameTests {
    private CombatEffectsGameTests() {}

    /** Lo que vio el pipeline en un golpe: si hubo daño, su valor vanilla entrante y el daño resuelto por Zenkai. */
    private record Hit(boolean arrived, float incoming, float resolved) {}

    /** Hace pasar a la entidad por su tick previo (donde se reconcilia la compensación de la poción), como en el juego. */
    private static void tickPre(LivingEntity e) {
        NeoForge.EVENT_BUS.post(new EntityTickEvent.Pre(e));
    }

    private static void setEffect(LivingEntity e, Holder<MobEffect> effect, int amp) {
        e.removeAllEffects();
        if (effect != null) e.addEffect(new MobEffectInstance(effect, 2000, amp, false, false));
        tickPre(e);
    }

    /** Golpe completo del jugador (modo combate) al zombi con el efecto y el arma (+6 de ataque si hay arma) dados. */
    private static Hit playerHit(GameTestHelper helper, ServerPlayer sp, Holder<MobEffect> effect, int amp, boolean sword) {
        // Un zombi nuevo por golpe: los golpes seguidos agotan su pool propio y falseaban las proporciones.
        Zombie target = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 2, 4));
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100_000);
        setEffect(sp, effect, amp);
        var atk = sp.getAttribute(Attributes.ATTACK_DAMAGE);
        var swordId = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "test_sword");
        atk.removeModifier(swordId);
        // El equipo no se procesa sin tick: la espada de hierro se simula con su modificador de atributo.
        if (sword) atk.addTransientModifier(new AttributeModifier(swordId, 6.0, AttributeModifier.Operation.ADD_VALUE));
        ZenkaiGameTestHelpers.invokeHandler(() -> CombatModeServerState.update(sp, true));
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        att.addStamina(att.getStaminaMax());
        att.addEnergy(att.getEnergyMax());   // Ki Infuse gasta ki: sin esto los golpes con arma pierden su bonus a mitad de serie
        target.setHealth(target.getMaxHealth());
        target.invulnerableTime = 0;
        target.hurtTime = 0;
        fullCharge(sp);
        com.hmc.zenkai.feature.combat.ComboTracker.reset(sp.getUUID());   // el combo multiplica golpes seguidos
        Hit h = measure(target, () -> sp.attack(target));
        atk.removeModifier(swordId);
        return h;
    }

    /** Ejecuta la acción y devuelve lo que los eventos de daño vieron en la víctima dada. */
    private static Hit measure(LivingEntity victim, Runnable action) {
        final boolean[] arrived = {false};
        final float[] in = {0F}, out = {0F};
        Consumer<LivingIncomingDamageEvent> onIn = ev -> {
            if (ev.getEntity() == victim) { arrived[0] = true; in[0] = ev.getAmount(); }
        };
        Consumer<LivingDamageEvent.Pre> onPre = ev -> {
            if (ev.getEntity() == victim) out[0] = ev.getNewDamage();
        };
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(EventPriority.LOWEST, true, LivingIncomingDamageEvent.class, onIn);
        bus.addListener(EventPriority.LOWEST, true, LivingDamageEvent.Pre.class, onPre);
        try {
            action.run();
        } finally {
            bus.unregister(onIn);
            bus.unregister(onPre);
        }
        return new Hit(arrived[0], in[0], out[0]);
    }

    /** Pone el ticker de ataque al máximo (golpe al 100 % de carga) con reflexión: el campo es protected. */
    private static void fullCharge(ServerPlayer sp) {
        try {
            var f = LivingEntity.class.getDeclaredField("attackStrengthTicker");
            f.setAccessible(true);
            f.setInt(sp, 1000);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Quita la invulnerabilidad de aparición (60 ticks, privada) del jugador de prueba. */
    private static void clearSpawnProtection(ServerPlayer sp) {
        try {
            var f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            f.setAccessible(true);
            f.setInt(sp, 0);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String row(String name, Hit h) {
        return String.format(Locale.ROOT, "  %-26s llega=%-5s entrante_vanilla=%.2f resuelto_zenkai=%.3f", name, h.arrived(), h.incoming(), h.resolved());
    }

    private static void near(GameTestHelper helper, String what, double got, double expected) {
        helper.assertTrue(Math.abs(got - expected) <= Math.max(0.02 * expected, 0.01),
                what + ": se esperaba ~" + String.format(Locale.ROOT, "%.3f", expected) + " y salió " + String.format(Locale.ROOT, "%.3f", got));
    }

    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void weaknessAndStrengthScalePlayerHits(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 3.5));
        sp.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        Hit fist = playerHit(helper, sp, null, 0, false);
        Hit fistW1 = playerHit(helper, sp, MobEffects.WEAKNESS, 0, false);
        Hit fistW2 = playerHit(helper, sp, MobEffects.WEAKNESS, 1, false);
        Hit fistS1 = playerHit(helper, sp, MobEffects.DAMAGE_BOOST, 0, false);
        Hit sword = playerHit(helper, sp, null, 0, true);
        Hit swordW1 = playerHit(helper, sp, MobEffects.WEAKNESS, 0, true);
        Hit swordW2 = playerHit(helper, sp, MobEffects.WEAKNESS, 1, true);
        Hit swordS1 = playerHit(helper, sp, MobEffects.DAMAGE_BOOST, 0, true);
        setEffect(sp, null, 0);

        System.out.println("[CombatFx] golpe completo del jugador en modo combate\n"
                + row("puño", fist) + "\n" + row("puño + Debilidad I", fistW1) + "\n" + row("puño + Debilidad II", fistW2) + "\n"
                + row("puño + Fuerza I", fistS1) + "\n" + row("espada", sword) + "\n" + row("espada + Debilidad I", swordW1) + "\n"
                + row("espada + Debilidad II", swordW2) + "\n" + row("espada + Fuerza I", swordS1));

        helper.assertTrue(fistW1.arrived() && fistW2.arrived() && swordW2.arrived(), "Con Debilidad el golpe siempre llega (antes era 0 con el puño)");
        near(helper, "puño + Debilidad I", fistW1.resolved(), fist.resolved() * 0.75);
        near(helper, "puño + Debilidad II", fistW2.resolved(), fist.resolved() * 0.50);
        near(helper, "puño + Fuerza I", fistS1.resolved(), fist.resolved() * 1.25);
        // Con arma la mitigación posterior (defensa/armadura del zombi) no es lineal: se comprueba el orden y no la proporción.
        // El pipeline en sí SÍ es lineal (medido con una traza: total ×0,75 / ×0,5 / ×1,25 al céntimo).
        helper.assertTrue(swordW2.resolved() < swordW1.resolved() && swordW1.resolved() < sword.resolved() && sword.resolved() < swordS1.resolved(),
                "Con arma: Debilidad II < Debilidad I < sin efecto < Fuerza I");
        helper.assertTrue(PotionDamageEffects.meleeFactor(null) == 1.0, "Sin entidad, factor 1");
        helper.succeed();
    }

    /** Un zombi (con stats) con y sin Debilidad/Fuerza golpea a un jugador con raza: la Debilidad lo escala en vez de anularlo. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void weaknessAndStrengthScaleMobHits(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 3.5));
        sp.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        clearSpawnProtection(sp);
        Zombie z = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 2, 4));
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);

        Map<String, Hit> res = new LinkedHashMap<>();
        for (Object[] c : new Object[][]{{"sin efecto", null, 0}, {"Debilidad I", MobEffects.WEAKNESS, 0}, {"Fuerza I", MobEffects.DAMAGE_BOOST, 0}}) {
            @SuppressWarnings("unchecked") Holder<MobEffect> eff = (Holder<MobEffect>) c[1];
            setEffect(z, eff, (int) c[2]);
            att.setBody(att.getBodyMax());
            sp.invulnerableTime = 0;
            int before = att.getBody();
            Hit raw = measure(sp, () -> z.doHurtTarget(sp));
            res.put((String) c[0], new Hit(raw.arrived(), raw.incoming(), before - att.getBody()));   // resolved = body perdido
        }
        setEffect(z, null, 0);
        StringBuilder sb = new StringBuilder("[CombatFx] zombi golpea a un jugador con raza\n");
        res.forEach((k, v) -> sb.append(row(k, v)).append('\n'));
        System.out.println(sb);

        Hit base = res.get("sin efecto");
        helper.assertTrue(base.arrived(), "El golpe base del zombi llega");
        helper.assertTrue(res.get("Debilidad I").arrived(), "Con Debilidad el golpe del zombi llega (antes no)");
        near(helper, "zombi + Debilidad I", res.get("Debilidad I").resolved(), base.resolved() * 0.75);
        near(helper, "zombi + Fuerza I", res.get("Fuerza I").resolved(), base.resolved() * 1.25);
        helper.succeed();
    }

    /** Veneno, Wither y Hambre pasan a ser daño ambiental: escalan como % del body en vez de 1 de body fijo. */
    @GameTest(templateNamespace = Zenkai.MOD_ID, template = "empty")
    public static void damageOverTimeScalesWithBody(GameTestHelper helper) {
        ServerPlayer sp = ZenkaiGameTestHelpers.raceChosenPlayer(helper);
        clearSpawnProtection(sp);
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        Map<String, DamageSource> sources = new LinkedHashMap<>();
        sources.put("veneno (magic)", sp.damageSources().magic());
        sources.put("wither", sp.damageSources().wither());
        sources.put("hambre (starve)", sp.damageSources().starve());
        StringBuilder sb = new StringBuilder("[CombatFx] 3 golpes de 1 de daño vanilla, bodyMax=" + att.getBodyMax() + "\n");
        double perHitFraction = 1.0 / sp.getMaxHealth();   // % de vida vanilla que vale 1 de daño
        for (var e : sources.entrySet()) {
            att.setBody(att.getBodyMax());
            int before = att.getBody();
            for (int i = 0; i < 3; i++) { sp.invulnerableTime = 0; sp.hurt(e.getValue(), 1.0F); }
            int lost = before - att.getBody();
            sb.append(String.format(Locale.ROOT, "  %-16s body %d -> %d (-%d); esperado ~%.0f%n", e.getKey(), before, att.getBody(), lost, 3 * perHitFraction * before));
            near(helper, e.getKey() + ": body perdido", lost, 3 * perHitFraction * before);
        }
        System.out.println(sb);
        helper.succeed();
    }
}
