package me.blvckbytes.item_predicate_parser.translation.resolver;

import me.blvckbytes.item_predicate_parser.translation.keyed.LangKeyed;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.Stack;

public abstract class TranslationResolver {

  protected final Plugin loadedPlugin;

  public TranslationResolver(Plugin loadedPlugin) {
    this.loadedPlugin = loadedPlugin;
  }

  public abstract @Nullable String resolve(LangKeyed<?> langKeyed);

  public static String sanitize(String input) {
    var inputLength = input.length();
    var result = new StringBuilder(inputLength);

    /*
      - Simple Colors: (§|&)[0-9a-fk-or]
      - Hex Colors: (§|&)#([0-9a-fA-F]{3} | [0-9a-fA-F]{6})
      - XML-Tags (Mini-Message)
        - May contain other tags in string-parameters, marked by " or '
        - Example: <hover:show_text:"<red>test:TEST">
        - Escape-Sequences: \", \', \< \>
     */

    int possibleTagBeginning = -1;
    var quoteStack = new Stack<Character>();

    char previousChar = 0;

    for (var i = 0; i < inputLength; ++i) {
      var currentChar = input.charAt(i);
      var isEscaped = previousChar == '\\';

      previousChar = currentChar;

      if (possibleTagBeginning >= 0) {
        if (!isEscaped && (currentChar == '"' || currentChar == '\'')) {
          if (!quoteStack.empty() && quoteStack.peek() == currentChar)
            quoteStack.pop();
          else
            quoteStack.push(currentChar);
        }

        // No need to step through tags in strings; just anticipate non-stringed close char
        if (!quoteStack.empty())
          continue;

        if (!isEscaped && currentChar == '>') {
          possibleTagBeginning = -1;
          continue;
        }

        continue;
      }

      var isLastChar = i == inputLength - 1;

      if (!isLastChar && (currentChar == '§' || currentChar == '&')) {
        var nextChar = input.charAt(i + 1);

        if (
          isAlphaNumeric(nextChar) ||
          (nextChar >= 'k' && nextChar <= 'o') ||
          nextChar == 'r'
        ) {
          ++i;
          continue;
        }

        if (nextChar == '#') {
          var remainingChars = inputLength - 1 - i;
          var maxMatchingChars = Math.min(6, remainingChars);

          var matchedChars = 0;

          for (; matchedChars < maxMatchingChars; ++matchedChars) {
            if (!isAlphaNumeric(input.charAt(1 + (matchedChars + 1))))
              break;
          }

          // Skips & (current), # (next; +1) and the number of matched alphanumerics, up to 6
          // I think I've seen shorthands - so that's why 3 or 6; leave &# or malformed as is
          if (matchedChars == 3 || matchedChars == 6) {
            i += matchedChars + 1;
            continue;
          }
        }
      }

      if (!isEscaped && currentChar == '<') {
        possibleTagBeginning = i;
        continue;
      }

      result.append(currentChar);
    }

    // Tag was never closed, so let's leave it in
    if (possibleTagBeginning >= 0)
      result.append(input.substring(possibleTagBeginning));

    return result.toString().trim();
  }

  private static boolean isAlphaNumeric(char c) {
    return (
      (c >= '0' && c <= '9') ||
      (c >= 'a' && c <= 'f') ||
      (c >= 'A' && c <= 'F')
    );
  }
}
