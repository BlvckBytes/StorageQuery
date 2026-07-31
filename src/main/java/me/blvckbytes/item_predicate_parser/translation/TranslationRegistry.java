package me.blvckbytes.item_predicate_parser.translation;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.blvckbytes.item_predicate_parser.ComponentUtil;
import me.blvckbytes.item_predicate_parser.GeneratingDetail;
import me.blvckbytes.item_predicate_parser.SingletonTranslationRegistry;
import me.blvckbytes.item_predicate_parser.parse.ItemPredicateParseException;
import me.blvckbytes.item_predicate_parser.parse.ParseConflict;
import me.blvckbytes.item_predicate_parser.parse.PredicateParser;
import me.blvckbytes.item_predicate_parser.parse.TokenParser;
import me.blvckbytes.item_predicate_parser.predicate.ItemPredicate;
import me.blvckbytes.item_predicate_parser.predicate.PredicateState;
import me.blvckbytes.item_predicate_parser.predicate.stringify.PlainStringifier;
import me.blvckbytes.item_predicate_parser.token.UnquotedStringToken;
import me.blvckbytes.item_predicate_parser.translation.keyed.*;
import me.blvckbytes.item_predicate_parser.translation.resolver.TranslationResolver;
import me.blvckbytes.item_predicate_parser.translation.version.IVersionDependentCode;
import me.blvckbytes.syllables_matcher.Syllables;
import me.blvckbytes.syllables_matcher.SyllablesMatcher;
import me.blvckbytes.syllables_matcher.WildcardMode;
import org.bukkit.Material;
import org.bukkit.MusicInstrument;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.MusicInstrumentMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.logging.Logger;

public class TranslationRegistry implements SingletonTranslationRegistry {

  private record LangKeyedAndTranslation(LangKeyed<?> langKeyed, String translation) {}

  public final TranslationLanguage language;
  public final JsonObject languageFile;

  private final IVersionDependentCode versionDependentCode;
  private final @Nullable TranslationResolver translationResolver;
  private final Logger logger;

  private TranslatedLangKeyed<?>[] entries;
  private Map<Object, TranslatedLangKeyed<?>> entryByWrapped;

  public TranslationRegistry(
    TranslationLanguage language,
    JsonObject languageFile,
    IVersionDependentCode versionDependentCode,
    @Nullable TranslationResolver translationResolver,
    Logger logger
  ) {
    this.language = language;
    this.languageFile = languageFile;
    this.versionDependentCode = versionDependentCode;
    this.translationResolver = translationResolver;
    this.logger = logger;
  }

  public IVersionDependentCode getVersionDependentCode() {
    return versionDependentCode;
  }

  public void initialize(Iterable<LangKeyedSource> sources) {
    var unsortedEntries = new ArrayList<TranslatedLangKeyed<?>>();
    entryByWrapped = new HashMap<>();

    for (var source : sources)
      createEntries(source, unsortedEntries);

    this.entries = unsortedEntries
      .stream()
      .sorted(Comparator.comparing(it -> it.normalizedPrefixedTranslation))
      .toArray(TranslatedLangKeyed[]::new);

    this.entryByWrapped = new HashMap<>();

    for (var entry : entries)
      entryByWrapped.put(entry.langKeyed.getWrapped(), entry);

    var entryIndex = 0;

    for (; entryIndex < this.entries.length; ++entryIndex)
      this.entries[entryIndex].alphabeticalIndex = entryIndex;

    logger.info("Loaded " + entryIndex + " entries for language " + language.assetFileNameWithoutExtension);
  }

  @Override
  public @Nullable String getTranslationBySingleton(Object instance) {
    var entry = entryByWrapped.get(instance);

    if (entry == null)
      return null;

    return entry.translation;
  }

  @Override
  public @Nullable String getNormalizedPrefixedTranslationBySingleton(Object instance) {
    var entry = entryByWrapped.get(instance);

    if (entry == null)
      return null;

    return entry.normalizedPrefixedTranslation;
  }

  @Override
  public @Nullable ItemPredicate generateOrChainPredicateFor(List<ItemStack> items, EnumSet<GeneratingDetail> details) {
    var itemPredicates = new ArrayList<ItemPredicate>();

    itemLoop:
    for (var item : items) {
      if (item == null || item.getType().isAir())
        continue;

      var itemPredicate = generatePredicateFor(item, details);

      for (var existingPredicate : itemPredicates) {
        if (existingPredicate.equals(itemPredicate))
          continue itemLoop;
      }

      itemPredicates.add(itemPredicate);
    }

    if (itemPredicates.size() == 1)
      return itemPredicates.getFirst();

    var orTranslation = getNormalizedPrefixedTranslationBySingleton(DisjunctionKey.INSTANCE);

    if (orTranslation == null)
      throw new IllegalStateException("Could not locate translation for the OR operator in language " + language);

    var finalPredicate = new StringJoiner(" " + orTranslation + " ");

    for (var itemPredicate : itemPredicates)
      finalPredicate.add(PlainStringifier.stringify(itemPredicate, true));

    return tryParseItemPredicate(finalPredicate.toString());
  }

  @Override
  public @Nullable ItemPredicate generatePredicateFor(ItemStack item, EnumSet<GeneratingDetail> details) {
    var predicates = new ArrayList<String>();

    var material = item.getType();
    var materialTranslation = getNormalizedPrefixedTranslationBySingleton(material);

    if (materialTranslation == null)
      throw new IllegalStateException("Could not locate translation for " + material + " in language " + language);

    predicates.add(materialTranslation);

    if (details.contains(GeneratingDetail.AMOUNT)) {
      var amountTranslation = getNormalizedPrefixedTranslationBySingleton(AmountKey.INSTANCE);

      if (amountTranslation == null)
        throw new IllegalStateException("Could not locate translation for the Amount predicate in language " + language);

      predicates.add(amountTranslation + " " + item.getAmount());
    }

    var predicateState = new PredicateState(item);

    if (details.contains(GeneratingDetail.ENCHANTMENTS) && !predicateState.getEnchantments().isEmpty()) {
      var sortedEnchantments = new ArrayList<>(predicateState.getEnchantments());

      // Ensure constant order for each time of generating the predicate.
      sortedEnchantments.sort(Comparator.comparing(entry -> entry.getKey().getKey().getKey()));

      for (var enchantmentEntry : sortedEnchantments) {
        var enchantment = enchantmentEntry.getKey();

        var enchantmentTranslation = getNormalizedPrefixedTranslationBySingleton(enchantment);

        if (enchantmentTranslation == null)
          throw new IllegalStateException("Could not locate a translation for the enchantment " + enchantment.getKey() + " in language " + language);

        var omitLevel = enchantment.getMaxLevel() == 1 && enchantmentEntry.getValue() == 1;

        predicates.add(enchantmentTranslation + (omitLevel ? "" : " " + enchantmentEntry.getValue()));
      }
    }

    if ((isNaturallyEnchantable(item) || item.getType() == Material.ENCHANTED_BOOK) && details.contains(GeneratingDetail.ENCHANTMENT_COUNT)) {
      var enchantmentCountTranslation = getNormalizedPrefixedTranslationBySingleton(EnchantmentCountKey.INSTANCE);

      if (enchantmentCountTranslation == null)
        throw new IllegalStateException("Could not locate translation for the Enchantment-Count predicate in language " + language);

      predicates.add(enchantmentCountTranslation + " " + predicateState.getEnchantments().size());
    }

    if (details.contains(GeneratingDetail.POTION_EFFECTS) && !predicateState.getEffects().isEmpty()) {
      var sortedEffects = new ArrayList<>(predicateState.getEffects());

      // Ensure constant order for each time of generating the predicate.
      sortedEffects.sort(Comparator.comparing(entry -> entry.getType().getKey().getKey()));

      for (var effect : sortedEffects) {
        var potionEffectTypeTranslation = getNormalizedPrefixedTranslationBySingleton(effect.getType());

        if (potionEffectTypeTranslation == null)
          throw new IllegalStateException("Could not locate a translation for the potion-effect " + effect.getType().getKey() + " in language " + language);

        predicates.add(potionEffectTypeTranslation + " " + (effect.getAmplifier() + 1) + " " + effect.getDuration());
      }
    }

    if (!predicateState.getEffects().isEmpty() && details.contains(GeneratingDetail.EFFECT_COUNT)) {
      var effectCountTranslation = getNormalizedPrefixedTranslationBySingleton(EffectCountKey.INSTANCE);

      if (effectCountTranslation == null)
        throw new IllegalStateException("Could not locate translation for the Effect-Count predicate in language " + language);

      predicates.add(effectCountTranslation + " " + predicateState.getEffects().size());
    }

    if (details.contains(GeneratingDetail.POTION_TYPE)) {
      PotionType potionType;

      if (predicateState.getMeta() instanceof PotionMeta potionMeta && (potionType = potionMeta.getBasePotionType()) != null) {
        var potionTypeTranslation = getNormalizedPrefixedTranslationBySingleton(potionType);

        if (potionTypeTranslation == null)
          throw new IllegalStateException("Could not locate translation for the potion-type " + potionType.getKey() + " in language " + language);

        predicates.add(potionTypeTranslation);
      }
    }

    if (details.contains(GeneratingDetail.MUSIC_INSTRUMENT)) {
      MusicInstrument instrument;

      if (predicateState.getMeta() instanceof MusicInstrumentMeta instrumentMeta && (instrument = instrumentMeta.getInstrument()) != null) {
        var instrumentTranslation = getNormalizedPrefixedTranslationBySingleton(instrument);

        if (instrumentTranslation == null)
          throw new IllegalStateException("Could not locate translation for the music-instrument " + instrument.description() + " in language " + language);

        predicates.add(instrumentTranslation);
      }
    }

    if (details.contains(GeneratingDetail.DETERIORATION) && item.getType().getMaxDurability() > 0) {
      if (predicateState.getMeta() instanceof Damageable damageable) {
        var deteriorationTranslation = getNormalizedPrefixedTranslationBySingleton(DeteriorationKey.INSTANCE);

        if (deteriorationTranslation == null)
          throw new IllegalStateException("Could not locate translation for the Deterioration predicate in language " + language);

        predicates.add(deteriorationTranslation + " " + (damageable.hasDamage() ? "1" : "0 0"));
      }
    }

    if (predicateState.getMeta() != null && details.contains(GeneratingDetail.DISPLAY_NAME)) {
      var displayName = predicateState.getMeta().displayName();

      if (displayName != null) {
        var nameText = ComponentUtil.asTrimmedText(displayName);

        if (!nameText.isBlank())
          predicates.add("\"" + nameText.replace("\"", "\\\"") + "\"");
      }
    }

    if (predicateState.getMeta() != null && details.contains(GeneratingDetail.HAS_NAME)) {
      if (predicateState.getMeta().hasDisplayName()) {
        var hasNameTranslation = getNormalizedPrefixedTranslationBySingleton(HasNameKey.INSTANCE);

        if (hasNameTranslation == null)
          throw new IllegalStateException("Could not locate translation for the Has-Name predicate in language " + language);

        predicates.add(hasNameTranslation);
      }
    }

    if (details.contains(GeneratingDetail.REPAIR_COST) && (item.getType().getMaxDurability() > 0 || item.getType() == Material.ENCHANTED_BOOK)) {
      if (predicateState.getMeta() instanceof Repairable repairable) {
        var repairCostTranslation = getNormalizedPrefixedTranslationBySingleton(RepairCostKey.INSTANCE);

        if (repairCostTranslation == null)
          throw new IllegalStateException("Could not locate translation for the Repair-Cost predicate in language " + language);

        var hasCost = repairable.hasRepairCost() && repairable.getRepairCost() > 0;

        predicates.add(repairCostTranslation + " " + (hasCost ? ">0" : "0"));
      }
    }

    if (predicates.size() == 1)
      return tryParseItemPredicate(predicates.getFirst());

    return tryParseItemPredicate(String.join(" ", predicates));
  }

  @SuppressWarnings("unchecked")
  public <T extends LangKeyed<?>> List<TranslatedLangKeyed<T>> lookup(Class<T> type) {
    var result = new ArrayList<TranslatedLangKeyed<T>>();

    if (entries == null)
      return result;

    for (var entry : entries) {
      if (type.isInstance(entry.langKeyed))
        result.add((TranslatedLangKeyed<T>) entry);
    }

    return result;
  }

  public @Nullable TranslatedLangKeyed<?> lookup(LangKeyed<?> langKeyed) {
    if (entries == null)
      return null;

    for (var entry : entries) {
      if (entry.langKeyed.equals(langKeyed))
        return entry;
    }

    return null;
  }

  public SearchResult search(UnquotedStringToken query) {
    if (entries == null) {
      logger.warning("Tried to make use of search before initializing the registry");
      return new SearchResult(List.of(), WildcardMode.NONE);
    }

    var result = new ArrayList<TranslatedLangKeyed<?>>();

    var isVariableSearch = query.value().startsWith(Variable.ENCLOSING_MARKER);

    var queryValue = query.value();

    if (isVariableSearch)
      queryValue = Variable.ENCLOSING_MARKER + "-" + queryValue.substring(1);

    var querySyllablesResult = Syllables.forStringWithWildcardSupport(queryValue, Syllables.DELIMITER_SEARCH_PATTERN);

    if (querySyllablesResult.numberOfWildcardSyllables() > 1)
      throw new ItemPredicateParseException(query, ParseConflict.MULTIPLE_SEARCH_PATTERN_WILDCARDS);

    if (querySyllablesResult.numberOfWildcardSyllables() != 0 && querySyllablesResult.numberOfNonWildcardSyllables() == 0)
      throw new ItemPredicateParseException(query, ParseConflict.ONLY_SEARCH_PATTERN_WILDCARD);

    var querySyllables = querySyllablesResult.syllables();
    var wildcardMode = querySyllables.getWildcardMode();

    var matcher = new SyllablesMatcher();
    matcher.setQuery(querySyllables);

    for (var entryIndex = 0; entryIndex < entries.length; ++entryIndex) {
      var entry = entries[entryIndex];

      if (isVariableSearch && !entry.normalizedUnPrefixedTranslation.startsWith(Variable.ENCLOSING_MARKER))
        continue;

      if (!isVariableSearch && entry.normalizedUnPrefixedTranslation.startsWith(Variable.ENCLOSING_MARKER))
        continue;

      if (entryIndex != 0)
        matcher.resetQueryMatches();

      matcher.setTarget(entry.syllables);

      matcher.match();

      if (matcher.hasUnmatchedQuerySyllables())
        continue;

      if (wildcardMode != WildcardMode.NONE) {
        if (wildcardMode == WildcardMode.EXCLUDING_EXACT_MATCH && !matcher.hasUnmatchedTargetSyllables())
          continue;
      }

      result.add(entry);
    }

    return new SearchResult(result, wildcardMode);
  }

  private void createEntries(LangKeyedSource source, List<TranslatedLangKeyed<?>> translatedOutput) {
    var buckets = new HashMap<String, ArrayList<LangKeyedAndTranslation>>();

    for (var langKeyed : source.items()) {
      var translationValue = getTranslationOrNull(langKeyed);

      if (translationValue == null) {
        logger.warning("Could not locate translation-value for key " + langKeyed.getLanguageFileKey());
        continue;
      }

      var normalizedTranslationValue = TranslatedLangKeyed.normalize(translationValue);

      var bucket = buckets.computeIfAbsent(normalizedTranslationValue, _ -> new ArrayList<>());
      bucket.add(new LangKeyedAndTranslation(langKeyed, translationValue));
    }

    for (var bucketEntry : buckets.entrySet()) {
      var bucketNormalizedUnPrefixedTranslation = bucketEntry.getKey();
      var bucketItems = bucketEntry.getValue();
      var bucketSize = bucketItems.size();

      // Prefix all items of other sources that would collide with the item about to be added
      for (var itemIndex = 0; itemIndex < bucketSize; ++itemIndex) {
        var bucketItem = bucketItems.get(itemIndex);

        boolean hadCollision = false;

        for (var outputIndex = 0; outputIndex < translatedOutput.size(); ++outputIndex) {
          var existingEntry = translatedOutput.get(outputIndex);

          // Do not add cross-source collision prefixes on same-source items, as the incrementing
          // bucket index already takes care of these kinds of collision
          if (existingEntry.source == source)
            continue;

          if (!existingEntry.normalizedUnPrefixedTranslation.equalsIgnoreCase(bucketNormalizedUnPrefixedTranslation))
            continue;

          translatedOutput.set(outputIndex, new TranslatedLangKeyed<>(
            existingEntry.source,
            existingEntry.langKeyed,
            existingEntry.translation,
            existingEntry.normalizedUnPrefixedTranslation,
            existingEntry.source.collisionPrefix() + existingEntry.normalizedPrefixedTranslation
          ));

          hadCollision = true;
        }

        var newItemPrefixedTranslation = bucketNormalizedUnPrefixedTranslation;

        // Incrementing same-source prefixes should be nearest to the translation
        if (bucketSize > 1)
          newItemPrefixedTranslation = "[" + (itemIndex + 1) + "]-" + newItemPrefixedTranslation;

        if (hadCollision)
          newItemPrefixedTranslation = source.collisionPrefix() + newItemPrefixedTranslation;

        translatedOutput.add(new TranslatedLangKeyed<>(source, bucketItem.langKeyed, bucketItem.translation, bucketNormalizedUnPrefixedTranslation, newItemPrefixedTranslation));
      }
    }
  }

  private @Nullable String accessLanguageKey(String key, LangKeyed<?> langKeyed) {
    var translationValue = languageFile.get(key);

    if (translationValue == null) {
      if (langKeyed != null && translationResolver != null)
        return translationResolver.resolve(langKeyed);
      return null;
    }

    if (!(translationValue instanceof JsonPrimitive))
      return null;

    return translationValue.getAsString();
  }

  private String normalizeDescriptionTranslation(String descriptionTranslation) {
    return descriptionTranslation.replace(" - ", "-");
  }

  private @Nullable String tryGetSmithingTemplateDescriptionKey(LangKeyed<?> langKeyed) {
    /*
      The upgrade seems to be a completely different key:
      item.minecraft.>netherite_upgrade<_smithing_template => upgrade.minecraft.>netherite_upgrade<
     */

    if (langKeyed.getWrapped() instanceof Material material && material.name().equals("NETHERITE_UPGRADE_SMITHING_TEMPLATE"))
      return "upgrade.minecraft.netherite_upgrade";

    return null;
  }

  private @Nullable String getTranslationOrNull(LangKeyed<?> langKeyed) {
    var directTranslation = langKeyed.resolveTranslationDirectly(language);

    if (directTranslation != null)
      return directTranslation;

    var fileKey = langKeyed.getLanguageFileKey();

    if (langKeyed.getWrapped() instanceof Material) {
      var descriptionTranslationKey = tryGetSmithingTemplateDescriptionKey(langKeyed);

      if (descriptionTranslationKey == null)
        descriptionTranslationKey = fileKey + ".desc";

      var descriptionTranslation = accessLanguageKey(descriptionTranslationKey, null);

      if (descriptionTranslation != null)
        return accessLanguageKey(fileKey, langKeyed) + " " + normalizeDescriptionTranslation(descriptionTranslation);
    }

    return accessLanguageKey(fileKey, langKeyed);
  }

  private @Nullable ItemPredicate tryParseItemPredicate(String input) {
    var tokens = TokenParser.parseTokens(input);

    var conjunctionTranslation = lookup(ConjunctionKey.INSTANCE);

    if (conjunctionTranslation == null)
      throw new IllegalStateException("Could not locate translation for the AND operator in language " + language);

    return new PredicateParser(
      this,
      conjunctionTranslation,
      new ArrayList<>(tokens),
      false
    ).parseAst();
  }

  private static boolean isNaturallyEnchantable(ItemStack item) {
    var enchantmentRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);

    for (var enchantment : enchantmentRegistry) {
      if (enchantment.canEnchantItem(item))
        return true;
    }

    return false;
  }
}