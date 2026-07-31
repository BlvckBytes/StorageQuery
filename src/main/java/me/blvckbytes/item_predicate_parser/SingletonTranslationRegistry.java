package me.blvckbytes.item_predicate_parser;

import me.blvckbytes.item_predicate_parser.predicate.ItemPredicate;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

public interface SingletonTranslationRegistry {

  @Nullable String getTranslationBySingleton(Object instance);

  @Nullable String getNormalizedPrefixedTranslationBySingleton(Object instance);

  @Nullable ItemPredicate generatePredicateFor(ItemStack item, EnumSet<GeneratingDetail> details);

  @Nullable ItemPredicate generateOrChainPredicateFor(List<ItemStack> items, EnumSet<GeneratingDetail> details);

}
