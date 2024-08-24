package me.blvckbytes.storage_query.translation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.bukkit.Registry;
import org.bukkit.Translatable;
import org.jetbrains.annotations.Nullable;

import java.io.InputStreamReader;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TranslationRegistry {

  private static final Gson GSON = new GsonBuilder().create();

  private final JsonObject languageFile;
  private final Logger logger;
  private TranslatedTranslatable[] entries;

  public TranslationRegistry(JsonObject languageFile, Logger logger) {
    this.languageFile = languageFile;
    this.logger = logger;
  }

  public void initialize(@Nullable List<TranslatedTranslatable> items) {
    var unsortedEntries = new ArrayList<TranslatedTranslatable>();

    if (items != null)
      unsortedEntries.addAll(items);

    else {
      createEntries(Registry.ENCHANTMENT, unsortedEntries);
      createEntries(Registry.MATERIAL, unsortedEntries);
      createEntries(Registry.EFFECT, unsortedEntries);
    }

    this.entries = unsortedEntries
      .stream()
      .sorted(Comparator.comparing(TranslatedTranslatable::translation))
      .toArray(TranslatedTranslatable[]::new);
  }

  public List<TranslatedTranslatable> search(String text) {
    if (entries == null) {
      logger.warning("Tried to make use of search before initializing the registry");
      return new ArrayList<>();
    }

    var result = new ArrayList<TranslatedTranslatable>();
    var textParts = SubstringIndices.forString(text, SubstringIndices.SEARCH_PATTERN_DELIMITERS);

    for (var entry : entries) {
      var pendingTextParts = new ArrayList<>(textParts);

      SubstringIndices.matchQuerySubstrings(text, pendingTextParts, entry.translation(), new ArrayList<>(entry.partIndices()));

      if (pendingTextParts.isEmpty())
        result.add(entry);
    }

    return result;
  }

  private void createEntries(Iterable<? extends Translatable> items, ArrayList<TranslatedTranslatable> output) {
    for (var translatable : items) {
      var translationKey = translatable.getTranslationKey();
      var translationValue = getTranslationOrNull(languageFile, translationKey);

      if (translationValue == null) {
        logger.warning("Could not locate translation-value for key " + translationKey);
        continue;
      }

      output.add(new TranslatedTranslatable(translatable, translationValue));
    }
  }

  private @Nullable String getTranslationOrNull(JsonObject languageFile, String translationKey) {
    var translationValue = languageFile.get(translationKey);

    if (translationValue == null)
      return null;

    if (!(translationValue instanceof JsonPrimitive))
      return null;

    return translationValue.getAsString();
  }

  public static @Nullable TranslationRegistry load(String absoluteLanguageFilePath, Logger logger) {
    try (var inputStream = TranslationRegistry.class.getResourceAsStream(absoluteLanguageFilePath)) {
      if (inputStream == null)
        throw new IllegalStateException("Resource stream was null");

      var languageJson = GSON.fromJson(new InputStreamReader(inputStream), JsonObject.class);

      var registry = new TranslationRegistry(languageJson, logger);
      registry.initialize(null);

      logger.info("Loaded registry for translation-file " + absoluteLanguageFilePath);
      return registry;
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Could not load translation-file " + absoluteLanguageFilePath, e);
      return null;
    }
  }
}