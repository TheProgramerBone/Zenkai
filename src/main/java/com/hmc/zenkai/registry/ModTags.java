package com.hmc.zenkai.registry;

import com.hmc.zenkai.Zenkai;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.Structure;

public class ModTags {
    public static class Blocks {
        public static final TagKey<Block> DRAGON_BALLS_BLOCK = createTag("dragon_balls_block");
        public static final TagKey<Block> NAMEKIAN_ORE_REPLACEABLES = createTag("namekian_ore_replaceables");
        public static final TagKey<Block> KI_INDESTRUCTIBLE = createTag("ki_indestructible");

        private static TagKey<Block> createTag(String name) {
            return BlockTags.create(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,name));
        }

    }

    public static class Items{

        public static final TagKey<Item> DRAGON_BALLS_ITEM = createTag("dragon_balls_item");
        /** Objetos que el deseo "Quiero más de..." NUNCA duplica, diga lo que diga la config: las esferas
         *  (#dragon_balls_item), los bloques de las siete fundidas y las piedras de esfera (sueltan esferas
         *  al romperse, así que duplicarlas sería una forma de farmearlas). Es un tag, o sea data-driven:
         *  un datapack puede ampliarlo. */
        public static final TagKey<Item> WISH_BANNED = createTag("wish_banned");
        public static final TagKey<Item> KEEPS_HAIR = createTag("keeps_hair");
        /** Objetos que ocupan la OREJA IZQUIERDA del portador (el scouter se apoya en ella): hacen desaparecer esa oreja del cuerpo
         *  de la raza que la tenga (Namekiano), declarada por el ítem de raza con {@code hideOnEarGear}. Distinto de keeps_hair
         *  (que dice «no cubro el cráneo»): el halo no toca ninguna oreja. Se lee en la ranura de casco y en la de Curios. */
        public static final TagKey<Item> COVERS_EAR = createTag("covers_ear");
        /** Objetos que «take_all» NUNCA retira del jugador al intercambiar armadura: el scouter, el halo y las pesas son
         *  armaduras de verdad que ocupan ranuras de armadura. Data-driven: un datapack o mod puede ampliarlo. */
        public static final TagKey<Item> NO_ARMOR_SWAP = createTag("no_armor_swap");
        public static final TagKey<Item> AJISA_LOGS = createTag("ajisa_logs");
        public static final TagKey<Item> WORLDGEN_DEBRIS = createTag("worldgen_debris");
        /** Combustibles EXTRA del generador, además de los quemables de vanilla. El cristal
         *  de energía entra por aquí: no es leña, pero arde mucho más. */

        private static TagKey<Item> createTag(String name) {
            return ItemTags.create(ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID,name));
        }

    }

    public static class Structures {
        public static final TagKey<Structure> DRAGON_BALLS =
                TagKey.create(Registries.STRUCTURE,
                        ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "dragon_balls"));
    }

    public static class EntityTypes {
        /** Mobs que merecen escala de jefe. El resto se reparte por MobCategory. */
        public static final TagKey<EntityType<?>> BOSSES = createTag("bosses");
        /** No se puede agarrar con la técnica Agarre (jefes y similares). */
        public static final TagKey<EntityType<?>> UNGRABBABLE = createTag("ungrabbable");
        /** Seres con ki divino (Ángeles, Janemba…): el Ki Sense de quien no tiene God Ki los
         *  percibe como una firma desconocida y tenue (skill tree F5). */
        public static final TagKey<EntityType<?>> DIVINE = createTag("divine");
        /** No les afecta la gravedad de la cámara de gravedad (maestros y NPC). Ampliable por datapack. */
        public static final TagKey<EntityType<?>> GRAVITY_IMMUNE = createTag("gravity_immune");

        private static TagKey<EntityType<?>> createTag(String name) {
            return TagKey.create(Registries.ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, name));
        }
    }
}
