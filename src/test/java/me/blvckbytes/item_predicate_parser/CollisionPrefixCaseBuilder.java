package me.blvckbytes.item_predicate_parser;

import me.blvckbytes.item_predicate_parser.token.UnquotedStringToken;
import me.blvckbytes.item_predicate_parser.translation.LangKeyedSource;
import me.blvckbytes.item_predicate_parser.translation.TranslationRegistry;
import me.blvckbytes.item_predicate_parser.translation.keyed.LangKeyed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class CollisionPrefixCaseBuilder {

  private final TranslationRegistry translationRegistry;
  private final List<LangKeyedSource> sources;
  private final List<TranslatedLangKeyedPredicate> predicates;

  public CollisionPrefixCaseBuilder(Supplier<TranslationRegistry> translationRegistryFactory) {
    this.translationRegistry = translationRegistryFactory.get();
    this.sources = new ArrayList<>();
    this.predicates = new ArrayList<>();
  }

  public CollisionPrefixCaseBuilder withSource(LangKeyedSource source) {
    this.sources.add(source);
    return this;
  }

  public CollisionPrefixCaseBuilder withSingleSource(LangKeyed<?> langKeyed, String collisionPrefix) {
    this.sources.add(new LangKeyedSource(Collections.singletonList(langKeyed), collisionPrefix));
    return this;
  }

  public CollisionPrefixCaseBuilder expectResult(LangKeyed<?> langKeyed, String prefix) {
    this.predicates.add(new TranslatedLangKeyedPredicate(translationRegistry, langKeyed, prefix));
    return this;
  }

  public void execute(String search) {
    translationRegistry.initialize(sources);

    var searchToken = new UnquotedStringToken(0, 0, null, search);
    var searchResult = translationRegistry.search(searchToken);

    var remainingPredicates = new ArrayList<>(predicates);
    var remainingResults = new ArrayList<>(searchResult.result());

    predicateLoop: while (!remainingPredicates.isEmpty()) {
      var currentPredicate = remainingPredicates.remove(0);

      for (var resultIterator = remainingResults.iterator(); resultIterator.hasNext();) {
        if (!currentPredicate.test(resultIterator.next()))
          continue;

        resultIterator.remove();
        continue predicateLoop;
      }

      throw new IllegalStateException("No item matched the current predicate: " + currentPredicate + "; items: " + remainingResults);
    }

    if (!remainingResults.isEmpty())
      throw new IllegalStateException("Not all results have been matched by a predicate: " + remainingResults);
  }
}
