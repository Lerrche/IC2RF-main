package ic2.core.recipe;

import ic2.api.recipe.Recipes;
import java.util.Comparator;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

/** Adds safe macerator fallbacks for common-tagged ores/raw materials from other mods. */
public final class MaceratorCompatibility {
  private static final TagKey<Item> ORES = itemTag("c:ores");
  private static final TagKey<Item> RAW_MATERIALS = itemTag("c:raw_materials");
  private static final TagKey<Item> INGOTS = itemTag("c:ingots");

  private MaceratorCompatibility() {}

  public static void addAutomaticRecipes(
      RecipeManager recipeManager, BasicMachineRecipeManager macerator) {
    for (var holder : recipeManager.getAllRecipesFor(RecipeType.SMELTING)) {
      addFromCookingRecipe(holder.value(), macerator);
    }
    for (var holder : recipeManager.getAllRecipesFor(RecipeType.BLASTING)) {
      addFromCookingRecipe(holder.value(), macerator);
    }
  }

  private static void addFromCookingRecipe(
      AbstractCookingRecipe cookingRecipe, BasicMachineRecipeManager macerator) {
    ItemStack ingot = cookingRecipe.getResultItem((RegistryAccess) null);
    if (ingot.isEmpty() || !isIngot(ingot) || cookingRecipe.getIngredients().size() != 1) {
      return;
    }

    Item dust = findDustForIngot(ingot);
    if (dust == null) {
      // Never generate a recipe which outputs an unrelated dust or a non-existent item.
      return;
    }

    Ingredient ingredient = cookingRecipe.getIngredients().getFirst();
    for (ItemStack input : ingredient.getItems()) {
      if (input.isEmpty() || !isOreOrRawMaterial(input)) {
        continue;
      }
      int dustCount = isOre(input) ? 2 : 1;
      macerator.addRecipe(
          Recipes.inputFactory.forItem(input.getItem()),
          null,
          false,
          new ItemStack(dust, dustCount));
    }
  }

  private static boolean isIngot(ItemStack stack) {
    if (stack.is(INGOTS)) {
      return true;
    }
    String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    return path.endsWith("_ingot") || path.startsWith("ingot_") || path.startsWith("ingots/");
  }

  private static boolean isOreOrRawMaterial(ItemStack stack) {
    if (stack.is(ORES) || stack.is(RAW_MATERIALS)) {
      return true;
    }
    String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    return isOrePath(path)
        || path.startsWith("raw_")
        || path.startsWith("crushed_raw_")
        || path.startsWith("raw/");
  }

  private static boolean isOre(ItemStack stack) {
    if (stack.is(ORES)) {
      return true;
    }
    return isOrePath(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
  }

  private static boolean isOrePath(String path) {
    return path.endsWith("_ore") || path.startsWith("deepslate_") && path.endsWith("_ore");
  }

  private static Item findDustForIngot(ItemStack ingot) {
    String material = materialName(ingot);
    if (material == null || material.isBlank()) {
      return null;
    }

    // Prefer the common dust tag so third-party dusts work without hard dependencies.
    TagKey<Item> dustTag = itemTag("c:dusts/" + material);
    ItemStack[] taggedDusts = Ingredient.of(dustTag).getItems();
    if (taggedDusts.length > 0) {
      return java.util.Arrays.stream(taggedDusts)
          .map(ItemStack::getItem)
          .distinct()
          .sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()))
          .findFirst()
          .orElse(null);
    }

    // Some packs add a dust item but have not added it to the common tag yet.
    ResourceLocation ingotId = BuiltInRegistries.ITEM.getKey(ingot.getItem());
    ResourceLocation localDustId =
        ResourceLocation.fromNamespaceAndPath(ingotId.getNamespace(), material + "_dust");
    Item localDust = BuiltInRegistries.ITEM.getOptional(localDustId).orElse(null);
    if (localDust != null) {
      return localDust;
    }

    // IC2's own same-material dust is the final fallback where IC2 registers one.
    ResourceLocation ic2DustId = ResourceLocation.fromNamespaceAndPath("ic2", material + "_dust");
    return BuiltInRegistries.ITEM.getOptional(ic2DustId).orElse(null);
  }

  private static String materialName(ItemStack ingot) {
    String path = BuiltInRegistries.ITEM.getKey(ingot.getItem()).getPath();
    if (path.contains("/")) {
      path = path.substring(path.lastIndexOf('/') + 1);
    }
    if (path.startsWith("ingot_")) {
      return path.substring("ingot_".length());
    }
    if (path.endsWith("_ingot")) {
      return path.substring(0, path.length() - "_ingot".length());
    }
    if (path.startsWith("raw_")) {
      return path.substring("raw_".length());
    }
    return null;
  }

  private static TagKey<Item> itemTag(String id) {
    return TagKey.create(Registries.ITEM, ResourceLocation.parse(id));
  }
}
