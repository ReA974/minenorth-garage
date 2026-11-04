package com.minenorth.vehicles.stand;

import com.minenorth.vehicles.VehiclesMod;
import com.minenorth.vehicles.depannage.ModItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Bornes de garage et de fourrière (blocs + objets, onglet créatif Garage). */
public final class StandBlocks {
    private StandBlocks() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, VehiclesMod.MODID);

    public static final RegistryObject<Block> GARAGE = BLOCKS.register("borne_garage", () -> new StandBlock(StandBlock.Kind.GARAGE));
    public static final RegistryObject<Block> FOURRIERE = BLOCKS.register("borne_fourriere", () -> new StandBlock(StandBlock.Kind.FOURRIERE));
    public static final RegistryObject<Item> GARAGE_ITEM = ModItems.ITEMS.register("borne_garage", () -> new BlockItem(GARAGE.get(), new Item.Properties()));
    public static final RegistryObject<Item> FOURRIERE_ITEM = ModItems.ITEMS.register("borne_fourriere", () -> new BlockItem(FOURRIERE.get(), new Item.Properties()));

    public static void register(IEventBus bus) { BLOCKS.register(bus); }
}
