package me.blvckbytes.item_predicate_parser;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class ComponentUtil {

  public static String asTrimmedText(@Nullable Component component) {
    var result = new StringBuilder();
    forEachTextOfComponent(component, result::append);
    return result.toString().trim();
  }

  public static void forEachTextOfComponent(@Nullable Component component, Consumer<String> textHandler) {
    if (component == null)
      return;

    if (component instanceof TextComponent textComponent)
      textHandler.accept(textComponent.content());

    for (var child : component.children())
      forEachTextOfComponent(child, textHandler);
  }
}
