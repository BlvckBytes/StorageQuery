package me.blvckbytes.item_predicate_parser;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import me.blvckbytes.item_predicate_parser.parse.ParserInput;
import me.blvckbytes.item_predicate_parser.predicate.*;
import me.blvckbytes.item_predicate_parser.token.*;
import me.blvckbytes.item_predicate_parser.translation.*;
import me.blvckbytes.item_predicate_parser.translation.keyed.*;
import me.blvckbytes.item_predicate_parser.translation.version.DetectedServerVersion;
import me.blvckbytes.item_predicate_parser.translation.version.VersionDependentCodeFactory;
import me.blvckbytes.syllables_matcher.Syllables;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.InputStreamReader;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class ParseTestBase {

  protected static final RecursiveInterceptedEqualityChecker equalityChecker = new RecursiveInterceptedEqualityChecker()
    .expectTypePrefix("me.blvckbytes")
    // Will always be null when creating test-tokens
    .interceptAndReturnTrue(ParserInput.class)
    // Bukkit types are outside the scope of testing
    .interceptAndUseAssertEquals(Enchantment.class)
    .interceptAndUseAssertEquals(PotionEffectType.class)
    .interceptAndUseAssertEquals(LangKeyed.class)
    .intercept(List.class, (_, pathParts, expected, actual) -> {
      var lastPathPart = pathParts.getLast();

      if (MaterialPredicate.class != lastPathPart.getDeclaringClass())
        return false;

      // Manual list content comparison
      if (expected == null && actual == null)
        return true;

      RecursiveInterceptedEqualityChecker.containsInAnyOrder(actual, expected);
      return true;
    })
    .intercept(Iterable.class, (_, pathParts, _, _) -> {
      var lastPathPart = pathParts.getLast();

      // There's no need to compare the items of a source, as the collision-prefix will always be unique
      return (
        lastPathPart.getDeclaringClass() == LangKeyedSource.class &&
        lastPathPart.getName().equals("items")
      );
    })
    .intercept(Syllables.class, (_, _, expected, actual) -> {
      if (expected == null || actual == null)
        return false;

      assertEquals(expected.size(), actual.size(), "Expected syllables to be of same size");

      for (var syllableIndex = 0; syllableIndex < expected.size(); ++syllableIndex)
        assertEquals(expected.getSyllable(syllableIndex), actual.getSyllable(syllableIndex), "Expected syllables at index=" + syllableIndex + " to equal");

      return true;
    });

  private static final Gson gson = new GsonBuilder().create();
  protected static final Logger logger = Logger.getAnonymousLogger();
  protected static Supplier<TranslationRegistry> translationRegistryFactory;
  protected static TranslationRegistry translationRegistry;
  protected static PredicateParserFactory parserFactory;
  protected static DetectedServerVersion serverVersion;
  protected static JsonObject languageJson;

  @BeforeAll
  public static void setup() throws Throwable {
    MockBukkit.mock();

    serverVersion = new DetectedServerVersion(26, 1, 2, "26.1.2");

    try (var inputStream = TranslationRegistry.class.getResourceAsStream("/en_us.json")) {
      if (inputStream == null)
        throw new IllegalStateException("Resource stream was null");

      languageJson = gson.fromJson(new InputStreamReader(inputStream), JsonObject.class);

      var versionDependentCode = new VersionDependentCodeFactory(serverVersion, logger).get();

      translationRegistryFactory = () -> new TranslationRegistry(TranslationLanguage.ENGLISH_US, languageJson, versionDependentCode, null, logger);

      translationRegistry = translationRegistryFactory.get();
      translationRegistry.initialize(makeSources());
    }

    parserFactory = new PredicateParserFactory(translationRegistry);
  }

  @AfterAll
  public static void tearDown() {
    MockBukkit.unmock();
  }

  protected IntegerToken integerToken(int commandArgumentIndex, int firstCharIndex, int lastCharIndex, @Nullable Integer value) {
    return integerToken(commandArgumentIndex, firstCharIndex, lastCharIndex, value, false, ComparisonMode.EQUALS);
  }

  protected IntegerToken integerToken(
    int commandArgumentIndex,
    int firstCharIndex,
    int lastCharIndex,
    @Nullable Integer value,
    boolean wasTimeNotation,
    ComparisonMode comparisonMode
  ) {
    return new IntegerToken(commandArgumentIndex, firstCharIndex, lastCharIndex, null, value, wasTimeNotation, comparisonMode);
  }

  protected ParenthesisToken parenthesisToken(int commandArgumentIndex, int firstCharIndex, boolean isOpening) {
    return new ParenthesisToken(commandArgumentIndex, firstCharIndex, null, isOpening);
  }

  protected QuotedStringToken quotedStringToken(
    int beginCommandArgumentIndex,
    int beginFirstCharIndex,
    int endCommandArgumentIndex,
    int endLastCharIndex,
    String value
  ) {
    return new QuotedStringToken(beginCommandArgumentIndex, beginFirstCharIndex, endCommandArgumentIndex, endLastCharIndex, null, value);
  }

  protected UnquotedStringToken unquotedStringToken(int commandArgumentIndex, int firstCharIndex, String value) {
    return new UnquotedStringToken(commandArgumentIndex, firstCharIndex, null, value);
  }

  protected ItemPredicate andJoin(Token[] keywordTokens, ItemPredicate... predicates) {
    if (predicates.length < 2)
      throw new IllegalStateException("Cannot join less than two predicates");

    var result = predicates[0];

    for (var i = 1; i < predicates.length; ++i) {
      var token = keywordTokens[i - 1];
      result = new ConjunctionNode(token, translationRegistry.lookup(ConjunctionKey.INSTANCE), result, predicates[i]);
    }

    return result;
  }

  protected ItemPredicate orJoin(Token[] keywordTokens, ItemPredicate... predicates) {
    if (predicates.length < 2)
      throw new IllegalStateException("Cannot join less than two predicates");

    var result = predicates[0];

    for (var i = 1; i < predicates.length; ++i)
      result = new DisjunctionNode(keywordTokens[i-1], translationRegistry.lookup(DisjunctionKey.INSTANCE), result, predicates[i]);

    return result;
  }

  protected ItemPredicate negate(UnquotedStringToken keywordToken, ItemPredicate predicate) {
    return new NegationNode(keywordToken, translationRegistry.lookup(NegationKey.INSTANCE), predicate);
  }

  protected ItemPredicate exact(UnquotedStringToken keywordToken, ItemPredicate predicate) {
    return new ExactNode(keywordToken, translationRegistry.lookup(ExactKey.INSTANCE), predicate);
  }

  @SuppressWarnings("unchecked")
  protected EnchantmentPredicate enchantmentPredicate(Enchantment enchantment, @Nullable IntegerToken level, UnquotedStringToken search) {
    return new EnchantmentPredicate(search, (TranslatedLangKeyed<LangKeyedEnchantment>) translationRegistry.lookup(new LangKeyedEnchantment(enchantment)), level);
  }

  @SuppressWarnings("unchecked")
  protected MaterialPredicate materialPredicate(Material material, UnquotedStringToken token) {
    return new MaterialPredicate(token, (TranslatedLangKeyed<LangKeyedItemMaterial>) translationRegistry.lookup(new LangKeyedItemMaterial(material, serverVersion, languageJson)), null);
  }

  protected MaterialPredicate materialsPredicate(UnquotedStringToken search, Collection<Material> materials) {
    return new MaterialPredicate(search, null, new ArrayList<>(materials));
  }

  @SuppressWarnings("unchecked")
  protected PotionEffectPredicate potionEffectPredicate(PotionEffectType type, @Nullable IntegerToken amplifier, @Nullable IntegerToken duration, UnquotedStringToken token) {
    return new PotionEffectPredicate(token, (TranslatedLangKeyed<LangKeyedPotionEffectType>) translationRegistry.lookup(new LangKeyedPotionEffectType(type)), amplifier, duration);
  }

  @SuppressWarnings("unchecked")
  protected DeteriorationPredicate deteriorationPredicate(@Nullable IntegerToken min, @Nullable IntegerToken max, UnquotedStringToken token) {
    return new DeteriorationPredicate(token, (TranslatedLangKeyed<DeteriorationKey>) translationRegistry.lookup(DeteriorationKey.INSTANCE), min, max);
  }

  @SuppressWarnings("unchecked")
  protected AmountPredicate amountPredicate(@Nullable IntegerToken amount, UnquotedStringToken token) {
    return new AmountPredicate(token, (TranslatedLangKeyed<AmountKey>) translationRegistry.lookup(AmountKey.INSTANCE), amount);
  }

  private static Iterable<LangKeyedSource> makeSources() {
    return Arrays.asList(
      new LangKeyedSource(
        Registry.ENCHANTMENT.stream().map(LangKeyedEnchantment::new).toList(),
        "[Enchantment] "
      ),
      new LangKeyedSource(
        Registry.EFFECT.stream().map(LangKeyedPotionEffectType::new).toList(),
        "[Effect] "
      ),
      new LangKeyedSource(
        Registry.MATERIAL.stream().filter(Material::isItem).map(it -> new LangKeyedItemMaterial(it, serverVersion, languageJson)).toList(),
        "[Material] "
      ),
      new LangKeyedSource(
        Registry.POTION.stream().map(LangKeyedPotionType::instantiateIfUsed).filter(Objects::nonNull).toList(),
        "[Potion] "
      ),
      new LangKeyedSource(
        Registry.INSTRUMENT.stream().map(LangKeyedMusicInstrument::new).toList(),
        "[Instrument] "
      ),
      new LangKeyedSource(List.of(
        DeteriorationKey.INSTANCE,
        NegationKey.INSTANCE,
        DisjunctionKey.INSTANCE,
        ConjunctionKey.INSTANCE,
        ExactKey.INSTANCE,
        AmountKey.INSTANCE,
        InnerAllKey.INSTANCE,
        InnerAllOrSelfKey.INSTANCE,
        InnerSomeKey.INSTANCE,
        InnerSomeOrSelfKey.INSTANCE,
        AnyKey.INSTANCE,
        HasNameKey.INSTANCE,
        RepairCostKey.INSTANCE,
        EffectCountKey.INSTANCE,
        EnchantmentCountKey.INSTANCE
      ), "")
    );
  }
}
