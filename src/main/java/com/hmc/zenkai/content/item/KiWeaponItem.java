package com.hmc.zenkai.content.item;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.render_and_model_entities.item.KiWeaponRenderer;
import com.hmc.zenkai.feature.kiweapon.KiWeaponDef;
import com.hmc.zenkai.feature.kiweapon.KiWeaponRegistry;
import com.hmc.zenkai.feature.kiweapon.KiWeaponServer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * Arma de ki (espada o guadaña). Es un item REGISTRADO de verdad — no un layer visual —
 * porque así la primera persona, el inventario, los NPC y los modificadores de atributo
 * (alcance, velocidad) salen del render vanilla en vez de pelearse con los hooks de brazo.
 *
 * Pero es un item que NO DEBE PODER EXISTIR fuera de la mano de su dueño: no se craftea, no
 * se da, no se tira y no se guarda. En vez de tapar cada ruta de fuga por separado
 * (arrastrarlo en el inventario, meterlo en un cofre, morir, un mod que lo mueva, /give), el
 * guard está donde pasa cualquiera: inventoryTick se ejecuta sobre cada stack del inventario cada
 * tick, así que si el arma no está donde debe o su dueño ya no la quiere, se borra sola.
 *
 * OJO: inventoryTick solo corre para inventarios de jugador. Los mobs que la lleven equipada
 * no se autolimpian, que es justo lo que queremos para los NPC que la usen algún día.
 *
 * ASSETS: el nombre de asset va SEPARADO del id del interruptor a propósito, para que el
 * modelo pueda llamarse distinto que la habilidad que lo invoca sin renombrar nada más.
 */
public class KiWeaponItem extends Item implements GeoItem {

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    private final String variant;     // id del toggle: "ki_blade" / "ki_scythe"
    private final String assetName;   // base de geo/textura/animación: "ki_blade", ...
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public KiWeaponItem(Properties properties, String variant, String assetName) {
        super(properties.stacksTo(1).fireResistant());
        this.variant = variant;
        this.assetName = assetName;
    }

    /** Id del interruptor que invoca esta arma. */
    public String variant() { return variant; }

    /** Números de datapack. Nunca null: cae al FALLBACK si falta el JSON. */
    public KiWeaponDef def() { return KiWeaponRegistry.get(variant); }

    // ── Rutas de asset ───────────────────────────────────────────────────────

    public ResourceLocation geoPath() {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "geo/" + assetName + ".geo.json");
    }

    public ResourceLocation texturePath() {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/item/" + assetName + ".png");
    }

    public ResourceLocation animationPath() {
        return ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "animations/" + assetName + ".animation.json");
    }

    // ── Guard ────────────────────────────────────────────────────────────────

    /**
     * EL guard. Se borra si:
     *  - no está en la mano principal (la arrastraron, la metieron en un cofre, la duplicó
     *    algo), o
     *  - su portador ya no tiene el interruptor puesto o perdió las habilidades.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide()) return;
        if (!(entity instanceof Player p) || !isSelected || !KiWeaponServer.wants(p, variant)) {
            stack.setCount(0);
            return;
        }
        KiWeaponServer.refreshTint(p, stack);
    }

    /** Ni tirable con la tecla de soltar: el respaldo es el guard de arriba, esto solo evita
     *  el parpadeo de verla caer y desaparecer. */
    @Override
    public boolean canFitInsideContainerItems() { return false; }

    // ── GeckoLib ─────────────────────────────────────────────────────────────

    /**
     * Solo IDLE de momento. El swing va fuera hasta saber si Better Combat es compatible: si
     * lo es, sus movesets pisarían cualquier animación de ataque nuestra y sobraría el
     * trabajo. Añadirlo después es registrar un controlador más aquí y un triggerAnim desde
     * el servidor al golpear.
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private KiWeaponRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                if (this.renderer == null) this.renderer = new KiWeaponRenderer();
                return this.renderer;
            }
        });
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}