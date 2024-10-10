package me.blvckbytes.item_predicate_parser.parse;

import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

public class EnumMatcher<T extends Enum<?>> {

  private final NormalizedConstant<T>[] constants;

  @SuppressWarnings("unchecked")
  public EnumMatcher(T[] values) {
    this.constants = new NormalizedConstant[values.length];

    for (var i = 0; i < values.length; ++i)
      this.constants[i] = new NormalizedConstant<>(values[i]);

    // Sort just like the client would, so that the first match is equal to
    // the first entry in the suggestion-list displayed to the user
    Arrays.sort(this.constants, Comparator.comparing(a -> a.normalizedName));
  }

  public List<String> createCompletions(@Nullable String input) {
    return createCompletions(input, null);
  }

  public List<String> createCompletions(@Nullable String input, @Nullable EnumPredicate<T> filter) {
    var result = new ArrayList<String>();

    forEachMatch(input, filter, match -> result.add(match.normalizedName));

    return result;
  }

  public @Nullable NormalizedConstant<T> matchFirst(@Nullable String input) {
    return matchFirst(input, null);
  }

  public @Nullable NormalizedConstant<T> matchFirst(@Nullable String input, @Nullable EnumPredicate<T> filter) {
    return forEachMatch(input, filter, match -> false);
  }

  private @Nullable NormalizedConstant<T> forEachMatch(
    @Nullable String input,
    @Nullable EnumPredicate<T> filter,
    Function<NormalizedConstant<T>, Boolean> matchHandler
  ) {
    if (input == null) {
      for (var translationLanguage : constants) {
        if (filter != null && !filter.test(translationLanguage))
          continue;

        if (!matchHandler.apply(translationLanguage))
          return translationLanguage;
      }

      return null;
    }

    var inputSyllables = Syllables.forString(null, input, Syllables.DELIMITER_SEARCH_PATTERN);

    var matcher = new SyllablesMatcher();
    matcher.setQuery(inputSyllables);

    for (var constantIndex = 0; constantIndex < constants.length; ++constantIndex) {
      var constant = constants[constantIndex];

      if (filter != null && !filter.test(constant))
        continue;

      if (constantIndex != 0)
        matcher.resetQueryMatches();

      matcher.setTarget(constant.syllables);
      matcher.match();

      if (matcher.hasUnmatchedQuerySyllables())
        continue;

      if (!matchHandler.apply(constant))
        return constant;
    }

    return null;
  }
}
