package me.blvckbytes.item_predicate_parser.predicate;

import me.blvckbytes.item_predicate_parser.ComponentUtil;
import me.blvckbytes.item_predicate_parser.predicate.stringify.StringifyHandler;
import me.blvckbytes.item_predicate_parser.token.QuotedStringToken;
import me.blvckbytes.syllables_matcher.Syllables;
import me.blvckbytes.syllables_matcher.SyllablesMatcher;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Objects;

public class TextSearchPredicate implements ItemPredicate {

  public final QuotedStringToken token;
  private final Syllables tokenSyllables;

  public TextSearchPredicate(QuotedStringToken token) {
    this.token = token;
    this.tokenSyllables = Syllables.forString(token.value(), Syllables.DELIMITER_FREE_TEXT);
  }

  @Override
  public @Nullable ItemPredicate testForFailure(PredicateState state) {
    var meta = state.getMeta();

    if (meta == null)
      return this;

    int matchCount;

    var matcher = new SyllablesMatcher();

    matcher.setQuery(tokenSyllables);

    // ================================================================================
    // Display Name
    // ================================================================================

    if (meta.hasDisplayName()) {
      var displayNameSyllables = Syllables.forString(ComponentUtil.asTrimmedText(meta.displayName()), Syllables.DELIMITER_FREE_TEXT);

      matcher.setTarget(displayNameSyllables);
      matchCount = matcher.match();

      if (!matcher.hasUnmatchedQuerySyllables())
        return null;

      if (matchCount > 0 && state.isExactMode)
        return this;
    }

    // ================================================================================
    // Lore Lines
    // ================================================================================

    if (meta.hasLore()) {
      matchCount = 0;

      var loreLines = meta.lore();

      if (loreLines == null)
        loreLines = Collections.emptyList();

      for (var loreLine : loreLines) {
        var loreLineSyllables = Syllables.forString(ComponentUtil.asTrimmedText(loreLine), Syllables.DELIMITER_FREE_TEXT);

        matcher.setTarget(loreLineSyllables);
        matchCount += matcher.match();

        if (!matcher.hasUnmatchedQuerySyllables())
          return null;
      }

      if (matchCount > 0 && state.isExactMode)
        return this;
    }

    if (meta instanceof BookMeta bookMeta) {

      // ================================================================================
      // Book Author
      // ================================================================================

      if (bookMeta.hasAuthor()) {
        var authorSyllables = Syllables.forString(Objects.requireNonNull(bookMeta.getAuthor()), Syllables.DELIMITER_FREE_TEXT);

        matcher.setTarget(authorSyllables);
        matchCount = matcher.match();

        if (!matcher.hasUnmatchedQuerySyllables())
          return null;

        if (matchCount > 0 && state.isExactMode)
          return this;
      }

      // ================================================================================
      // Book Title
      // ================================================================================

      if (bookMeta.hasTitle()) {
        var titleSyllables = Syllables.forString(Objects.requireNonNull(bookMeta.getTitle()), Syllables.DELIMITER_FREE_TEXT);

        matcher.setTarget(titleSyllables);
        matchCount = matcher.match();

        if (!matcher.hasUnmatchedQuerySyllables())
          return null;

        if (matchCount > 0 && state.isExactMode)
          return this;
      }

      // ================================================================================
      // Book Pages
      // ================================================================================

      if (bookMeta.hasPages()) {
        matchCount = 0;

        for (var page : bookMeta.pages()) {
          var pageSyllables = Syllables.forString(ComponentUtil.asTrimmedText(page), Syllables.DELIMITER_FREE_TEXT);

          matcher.setTarget(pageSyllables);
          matchCount += matcher.match();

          if (!matcher.hasUnmatchedQuerySyllables())
            return null;
        }

        if (matchCount > 0 && state.isExactMode)
          return this;
      }
    }

    // ================================================================================
    // Skull Owner
    // ================================================================================

    if (meta instanceof SkullMeta skullMeta) {
      var playerProfile = skullMeta.getPlayerProfile();

      if (playerProfile != null) {
        var ownerName = playerProfile.getName();

        if (ownerName != null) {
          var ownerNameSyllables = Syllables.forString(ownerName, Syllables.DELIMITER_FREE_TEXT);

          matcher.setTarget(ownerNameSyllables);
          matchCount = matcher.match();

          if (!matcher.hasUnmatchedQuerySyllables())
            return null;

          if (matchCount > 0 && state.isExactMode)
            return this;
        }
      }
    }

    return this;
  }

  @Override
  public void stringify(StringifyHandler handler) {
    handler.stringify(this, output -> output.appendString(token.stringify()));
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof TextSearchPredicate otherPredicate))
      return false;

    return token.value().equals(otherPredicate.token.value());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(token.value());
  }
}
