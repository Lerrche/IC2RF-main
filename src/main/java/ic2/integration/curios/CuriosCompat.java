package ic2.integration.curios;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/** Soft Curios bridge. Reflection keeps Curios an optional runtime dependency. */
public final class CuriosCompat {
  private CuriosCompat() {}

  public static void forEachEquippedStack(LivingEntity entity, Consumer<ItemStack> consumer) {
    if (entity == null || consumer == null || !ModList.get().isLoaded("curios")) {
      return;
    }

    try {
      Class<?> curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
      Class<?> inventoryApi =
          Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
      Object optionalValue =
          curiosApi.getMethod("getCuriosInventory", LivingEntity.class).invoke(null, entity);
      if (!(optionalValue instanceof Optional<?> inventoryOptional)
          || inventoryOptional.isEmpty()) {
        return;
      }

      Object inventory = inventoryOptional.get();
      Object equipped = inventoryApi.getMethod("getEquippedCurios").invoke(inventory);
      Class<?> itemHandler = Class.forName("net.neoforged.neoforge.items.IItemHandler");
      Method getSlots = itemHandler.getMethod("getSlots");
      Method getStackInSlot = itemHandler.getMethod("getStackInSlot", int.class);
      int slots = (int) getSlots.invoke(equipped);
      for (int slot = 0; slot < slots; slot++) {
        Object stack = getStackInSlot.invoke(equipped, slot);
        if (stack instanceof ItemStack itemStack && !itemStack.isEmpty()) {
          consumer.accept(itemStack);
        }
      }
    } catch (ReflectiveOperationException | LinkageError ignored) {
      // Curios is optional. A mismatched or unavailable Curios API must not break IC2 gameplay.
    }
  }
}
