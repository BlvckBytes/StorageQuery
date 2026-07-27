package me.blvckbytes.item_predicate_parser.display.overview;

import at.blvckbytes.cm_mapper.ConfigKeeper;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import me.blvckbytes.item_predicate_parser.config.MainSection;
import me.blvckbytes.item_predicate_parser.display.AsyncTaskQueue;
import me.blvckbytes.item_predicate_parser.display.Display;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

public class VariablesDisplay extends Display<VariablesDisplayData> {

  private final AsyncTaskQueue asyncQueue;

  private int numberOfPages;

  private InterpretationEnvironment pageEnvironment;

  private int currentPage = 1;

  public VariablesDisplay(
    ConfigKeeper<MainSection> config,
    Plugin plugin,
    Player player,
    VariablesDisplayData displayData
  ) {
    super(player, displayData, config, plugin);

    this.asyncQueue = new AsyncTaskQueue(plugin);

    // Within async context already, see corresponding command
    show();
  }

  private void setupEnvironment() {
    var numberOfDisplaySlots = config.rootSection.variablesDisplay.getPaginationSlots().size();
    this.numberOfPages = Math.max(1, (int) Math.ceil(displayData.variables().size() / (double) numberOfDisplaySlots));

    this.pageEnvironment = config.rootSection.variablesDisplay.inventoryEnvironment
      .copy()
      .withVariable("current_page", this.currentPage)
      .withVariable("number_of_pages", this.numberOfPages);
  }

  @Override
  public void onConfigReload() {
    show();
  }

  public void nextPage() {
    asyncQueue.enqueue(() -> {
      if (currentPage == numberOfPages)
        return;

      ++currentPage;
      show();
    });
  }

  public void previousPage() {
    asyncQueue.enqueue(() -> {
      if (currentPage == 1)
        return;

      --currentPage;
      show();
    });
  }

  public void firstPage() {
    asyncQueue.enqueue(() -> {
      if (currentPage == 1)
        return;

      currentPage = 1;
      show();
    });
  }

  public void lastPage() {
    asyncQueue.enqueue(() -> {
      if (currentPage == numberOfPages)
        return;

      currentPage = numberOfPages;
      show();
    });
  }

  @Override
  public void show() {
    setupEnvironment();
    super.show();
  }

  @Override
  protected void renderItems() {
    var displaySlots = config.rootSection.variablesDisplay.getPaginationSlots();
    var itemsIndex = (currentPage - 1) * displaySlots.size();
    var numberOfItems = displayData.variables().size();

    for (var slot : displaySlots) {
      var currentSlot = itemsIndex++;

      if (currentSlot >= numberOfItems) {
        inventory.setItem(slot, null);
        continue;
      }

      var variable = displayData.variables().get(currentSlot);

      var item = config.rootSection.variablesDisplay.items.variable.build(
        new InterpretationEnvironment()
          .withVariable("icon_type", variable.icon().name())
          .withVariable("name", variable.displayName())
          .withVariable("materials", variable.materialDisplayNames())
          .withVariable("inherited_materials", variable.inheritedMaterialDisplayNames())
          .withVariable("blocked_materials", variable.blockedMaterialDisplayNames())
          .withVariable("parents", variable.parentDisplayNames())
      );

      inventory.setItem(slot, item);
    }

    // Render filler first, such that it may be overridden by conditionally displayed items
    config.rootSection.variablesDisplay.items.filler.renderInto(inventory::setItem, pageEnvironment);

    config.rootSection.variablesDisplay.items.previousPage.renderInto(inventory::setItem, pageEnvironment);
    config.rootSection.variablesDisplay.items.nextPage.renderInto(inventory::setItem, pageEnvironment);
  }

  @Override
  protected Inventory makeInventory() {
    return config.rootSection.variablesDisplay.createInventory(pageEnvironment);
  }
}
