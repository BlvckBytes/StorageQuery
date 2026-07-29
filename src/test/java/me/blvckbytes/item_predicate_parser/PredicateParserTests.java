package me.blvckbytes.item_predicate_parser;

import at.blvckbytes.cm_mapper.cm.ComponentMarkup;
import at.blvckbytes.component_markup.expression.interpreter.InterpretationEnvironment;
import me.blvckbytes.item_predicate_parser.parse.ItemPredicateParseException;
import me.blvckbytes.item_predicate_parser.parse.ParseConflict;
import me.blvckbytes.item_predicate_parser.parse.TokenParser;
import me.blvckbytes.item_predicate_parser.predicate.*;
import me.blvckbytes.item_predicate_parser.predicate.stringify.PlainStringifier;
import me.blvckbytes.item_predicate_parser.token.ComparisonMode;
import me.blvckbytes.item_predicate_parser.token.Token;
import me.blvckbytes.item_predicate_parser.translation.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class PredicateParserTests extends ParseTestBase {

  private static final ComponentMarkup malformedRender = new ComponentMarkup("<&4>{predicate}", new InterpretationEnvironment(), null);
  private static final ComponentMarkup remainingRender = new ComponentMarkup("<&c>{predicate}", new InterpretationEnvironment(), null);

  @Test
  public void shouldThrowOnMissingLeftHandSide() {
    assertEquals(
      parseLegacyColors("§4and"),
      makeExceptionCase(
        new String[] {
          "and"
        },
        0, ParseConflict.EXPECTED_LEFT_HAND_SIDE
      )
    );

    assertEquals(
      parseLegacyColors("§4or"),
      makeExceptionCase(
        new String[] {
          "or"
        },
        0, ParseConflict.EXPECTED_LEFT_HAND_SIDE
      )
    );

    assertEquals(
      parseLegacyColors("§4and §cand"),
      makeExceptionCase(
        new String[] {
          "and", "and"
        },
        0, ParseConflict.EXPECTED_LEFT_HAND_SIDE
      )
    );

    // Back-to-back binary-operators cause the corresponding parsing-stage to fail, so an exception
    // is thrown before the second occurrence is even reached; this is desired behavior.
    assertEquals(
      parseLegacyColors("§cdia and dia §4or §cand"),
      makeExceptionCase(
        new String[] {
          "dia", "and", "dia", "or", "and"
        },
        3, ParseConflict.EXPECTED_EXPRESSION_AFTER_OPERATOR
      )
    );
  }

  @Test
  public void shouldHighlightInput() {
    assertEquals(
      parseLegacyColors("§cback-to-back§4\"unterminated stringinput-data"),
      makeExceptionCase(
        new String[] {
          "back-to-back\"unterminated", "stringinput-data",
        },
        0, ParseConflict.MISSING_STRING_TERMINATION
      )
    );

    assertComponentsEqual(
      parseLegacyColors("§cback-to-back§4\"   \"§c51"),
      makeExceptionCase(
        new String[] {
          "back-to-back\"", "", "", "\"51"
        },
        0, ParseConflict.EMPTY_OR_BLANK_QUOTED_STRING
      )
    );

    assertEquals(
      parseLegacyColors("§cback-to-back§4\"\"§c51"),
      makeExceptionCase(
        new String[] {
          "back-to-back\"\"51"
        },
        0, ParseConflict.EMPTY_OR_BLANK_QUOTED_STRING
      )
    );

    assertEquals(
      parseLegacyColors("§csearch-term §4\"\" §c51"),
      makeExceptionCase(
        new String[] {
          "search-term", "\"\"", "51"
        },
        1, ParseConflict.EMPTY_OR_BLANK_QUOTED_STRING
      )
    );

    assertEquals(
      parseLegacyColors("§csearch-term §4\" \" §c51"),
      makeExceptionCase(
        new String[] {
          "search-term", "\"", "\"", "51"
        },
        1, ParseConflict.EMPTY_OR_BLANK_QUOTED_STRING
      )
    );

    assertEquals(
      parseLegacyColors("§4\" \" §c51"),
      makeExceptionCase(
        new String[] {
          "\"", "\"", "51"
        },
        0, ParseConflict.EMPTY_OR_BLANK_QUOTED_STRING
      )
    );

    assertEquals(
      parseLegacyColors("§451a §csearch-term"),
      makeExceptionCase(
        new String[] {
          "51a", "search-term"
        },
        0, ParseConflict.EXPECTED_CORRECT_INTEGER
      )
    );
  }

  @Test
  public void shouldNormalizeTranslatables() {
    assertEquals(
      "[My-custom]-item-':]'",
      TranslatedLangKeyed.normalize(
        "(My_custom) item \":)\""
      )
    );

    assertEquals(
      "This-is-a-test!",
      TranslatedLangKeyed.normalize(
        "This is - a - test!"
      )
    );
  }

  @Test
  public void shouldStringifyStringsWithEscapedDoubleQuotes() {
    makeStringificationCase(
      new String[] { "\"string", "with", "escaped", "\\\"", "quote", "\"" },
      "\"string with escaped \\\" quote \"",
      false, false
    );

    makeStringificationCase(
      new String[] { "\"string", "with", "escaped", "\\\"", "quote", "\"" },
      "\"string with escaped \\\" quote \"",
      false, true
    );
  }

  @Test
  public void shouldStringifyQuotedStringsWithEscapeSequences() {
    makeStringificationCase(
      new String[] { "\"my-\\\"-string\"" },
      "\"my-\\\"-string\"",
      false, false
    );

    makeStringificationCase(
      new String[] { "\"my", "\\\"", "string\"" },
      "\"my \\\" string\"",
      false, false
    );
  }

  @Test
  public void shouldStringifyAsParsed() {
    makeStringificationCase(
      new String[] { "dia-ches", "unbr", "2", "regen", "3", "sign-?" },
      "dia-ches unbr 2 regen 3 sign-?",
      false, true
    );

    makeStringificationCase(
      new String[] { "dia-ches", "and", "unbr", "2", "regen", "3", "sign-?" },
      "dia-ches and unbr 2 regen 3 sign-?",
      false, true
    );

    makeStringificationCase(
      new String[] { "dia-ches", "and", "unbr", "2", "or", "regen", "3", "sign-?" },
      "dia-ches and unbr 2 or regen 3 sign-?",
      false, true
    );

    makeStringificationCase(
      new String[] { "(dia-ches", "and", "unbr", "2)", "or", "(regen", "3", "sign-?)" },
      "(dia-ches and unbr 2) or (regen 3 sign-?)",
      false, true
    );

    // But we most definitely want parentheses to be closed
    makeStringificationCase(
      new String[] { "(dia-ches", "and", "(unbr", "2" },
      "(dia-ches and (unbr 2))",
      true, true
    );
  }

  @Test
  public void shouldStringifyExpandedAbbreviations() {
    makeStringificationCase(
      new String[] { "dia-ches", "unbr", "2", "regen", "3", "sign-?" },
      "Diamond-Chestplate Unbreaking 2 Regeneration 3 sign-?",
      false, false
    );

    makeStringificationCase(
      new String[] { "dia-ches", "and", "unbr", "2", "regen", "3", "sign-?" },
      "Diamond-Chestplate and Unbreaking 2 Regeneration 3 sign-?",
      false, false
    );

    makeStringificationCase(
      new String[] { "dia-ches", "and", "unbr", "2", "or", "regen", "3", "sign-?" },
      "Diamond-Chestplate and Unbreaking 2 or Regeneration 3 sign-?",
      false, false
    );

    makeStringificationCase(
      new String[] { "(dia-ches", "and", "unbr", "2)", "or", "(regen", "3", "sign-?)" },
      "(Diamond-Chestplate and Unbreaking 2) or (Regeneration 3 sign-?)",
      false, false
    );

    // But we most definitely want parentheses to be closed
    makeStringificationCase(
      new String[] { "(dia-ches", "and", "(unbr", "2" },
      "(Diamond-Chestplate and (Unbreaking 2))",
      true, false
    );
  }

  @Test
  public void shouldYieldNullOnEmptyInput() {
    makeCase(new String[] {}, null);
    makeCase(new String[] { "" }, null);
    makeCase(new String[] { "", "" }, null);
  }

  @Test
  public void shouldConjunctByDefault() {
    makeCase(
      new String[] { "dia-ches", "unbr", "4", "night-v", "deter" },
      andJoin(
        new Token[] {
          null, null, null
        },
        materialPredicate(Material.DIAMOND_CHESTPLATE, unquotedStringToken(0, 0, "dia-ches")),
        enchantmentPredicate(
          Enchantment.UNBREAKING,
          integerToken(2, 0, 0, 4),
          unquotedStringToken(1, 0, "unbr")
        ),
        potionEffectPredicate(
          PotionEffectType.NIGHT_VISION,
          null,
          null,
          unquotedStringToken(3, 0, "night-v")
        ),
        deteriorationPredicate(
          null,
          null,
          unquotedStringToken(4, 0, "deter")
        )
      )
    );
  }

  @Test
  public void shouldParseNegation() {
    makeCase(
      new String[] { "gold-boot", "not", "unbr" },
      andJoin(
        new Token[] {
          null
        },
        materialPredicate(Material.GOLDEN_BOOTS, unquotedStringToken(0, 0, "gold-boot")),
        negate(
          unquotedStringToken(1, 0, "not"),
          enchantmentPredicate(
            Enchantment.UNBREAKING,
            null,
            unquotedStringToken(2, 0, "unbr")
          )
        )
      )
    );

    makeCase(
      new String[] { "gold-boot", "not", "unbr", "not", "deter", "not", "\"test\"" },
      andJoin(
        new Token[] {
          null, null, null
        },
        materialPredicate(Material.GOLDEN_BOOTS, unquotedStringToken(0, 0, "gold-boot")),
        negate(
          unquotedStringToken(1, 0, "not"),
          enchantmentPredicate(
            Enchantment.UNBREAKING,
            null,
            unquotedStringToken(2, 0, "unbr")
          )
        ),
        negate(
          unquotedStringToken(3, 0, "not"),
          deteriorationPredicate(
            null,
            null,
            unquotedStringToken(4, 0, "deter")
          )
        ),
        negate(
          unquotedStringToken(5, 0, "not"),
          new TextSearchPredicate(
            quotedStringToken(6, 0, 6, 5, "test")
          )
        )
      )
    );
  }

  @Test
  public void shouldParseConjunction() {
    makeCase(
      new String[] { "mendi", "and", "unbr", "3", "and", "deter" },
      andJoin(
        new Token[] {
          unquotedStringToken(1, 0, "and"),
          unquotedStringToken(4, 0, "and")
        },
        enchantmentPredicate(
          Enchantment.MENDING,
          null,
          unquotedStringToken(0, 0, "mendi")
        ),
        enchantmentPredicate(
          Enchantment.UNBREAKING,
          integerToken(3, 0, 0, 3),
          unquotedStringToken(2, 0, "unbr")
        ),
        deteriorationPredicate(
          null,
          null,
          unquotedStringToken(5, 0, "deter")
        )
      )
    );
  }

  @Test
  public void shouldParseDisjunction() {
    makeCase(
      new String[] { "dia-shov", "or", "dia-pick", "or", "unbr", "2", "or", "gold-boot" },
      orJoin(
        new Token[] {
          unquotedStringToken(1, 0, "or"),
          unquotedStringToken(3, 0, "or"),
          unquotedStringToken(6, 0, "or"),
        },
        materialPredicate(Material.DIAMOND_SHOVEL, unquotedStringToken(0, 0, "dia-shov")),
        materialPredicate(Material.DIAMOND_PICKAXE, unquotedStringToken(2, 0, "dia-pick")),
        enchantmentPredicate(
          Enchantment.UNBREAKING,
          integerToken(5, 0, 0, 2),
          unquotedStringToken(4, 0, "unbr")
        ),
        materialPredicate(Material.GOLDEN_BOOTS, unquotedStringToken(7, 0, "gold-boot"))
      )
    );
  }

  @Test
  public void shouldParseParentheses() {
    makeCase(
      new String[] { "(", "dia-shov", ")" },
      new ParenthesesNode(
        materialPredicate(
          Material.DIAMOND_SHOVEL,
          unquotedStringToken(1, 0, "dia-shov")
        )
      )
    );

    makeCase(
      new String[] { "(dia-shov", ")" },
      new ParenthesesNode(
        materialPredicate(
          Material.DIAMOND_SHOVEL,
          unquotedStringToken(0, 1, "dia-shov")
        )
      )
    );

    makeCase(
      new String[] { "(", "dia-shov)" },
      new ParenthesesNode(
        materialPredicate(
          Material.DIAMOND_SHOVEL,
          unquotedStringToken(1, 0, "dia-shov")
        )
      )
    );

    makeCase(
      new String[] { "(dia-shov)" },
      new ParenthesesNode(
        materialPredicate(
          Material.DIAMOND_SHOVEL,
          unquotedStringToken(0, 1, "dia-shov")
        )
      )
    );

    makeCase(
      new String[] { "(unbr", "fire-prot", "3", "thorn)" },
      new ParenthesesNode(
        andJoin(
          new Token[] {
            null, null
          },
          enchantmentPredicate(
            Enchantment.UNBREAKING,
            null,
            unquotedStringToken(0, 1, "unbr")
          ),
          enchantmentPredicate(
            Enchantment.FIRE_PROTECTION,
            integerToken(2, 0, 0, 3),
            unquotedStringToken(1, 0, "fire-prot")
          ),
          enchantmentPredicate(
            Enchantment.THORNS,
            null,
            unquotedStringToken(3, 0, "thorn")
          )
        )
      )
    );

    makeCase(
      new String[] { "(unbr", "or", "fire-prot", "3", "or", "thorn)" },
      new ParenthesesNode(
        orJoin(
          new Token[] {
            unquotedStringToken(1, 0, "or"),
            unquotedStringToken(4, 0, "or"),
          },
          enchantmentPredicate(
            Enchantment.UNBREAKING,
            null,
            unquotedStringToken(0, 1, "unbr")
          ),
          enchantmentPredicate(
            Enchantment.FIRE_PROTECTION,
            integerToken(3, 0, 0, 3),
            unquotedStringToken(2, 0, "fire-prot")
          ),
          enchantmentPredicate(
            Enchantment.THORNS,
            null,
            unquotedStringToken(5, 0, "thorn")
          )
        )
      )
    );

    makeCase(
      new String[] { "(((unbr)))" },
      new ParenthesesNode(
        new ParenthesesNode(
          new ParenthesesNode(
            enchantmentPredicate(
              Enchantment.UNBREAKING,
              null,
              unquotedStringToken(0, 3, "unbr")
            )
          )
        )
      )
    );

    makeCase(
      new String[] { "(", "(", "(", "unbr", ")", ")", ")" },
      new ParenthesesNode(
        new ParenthesesNode(
          new ParenthesesNode(
            enchantmentPredicate(
              Enchantment.UNBREAKING,
              null,
              unquotedStringToken(3, 0, "unbr")
            )
          )
        )
      )
    );
  }

  @Test
  public void shouldImplicitlyAddConjunctionToParentheses() {
    makeCase(
      new String[] { "dia-ches", "(unbr", "2)" },
      andJoin(
        new Token[] {
          null, null
        },
        materialPredicate(Material.DIAMOND_CHESTPLATE, unquotedStringToken(0, 0, "dia-ches")),
        new ParenthesesNode(
          enchantmentPredicate(
            Enchantment.UNBREAKING,
            integerToken(2, 0, 0, 2),
            unquotedStringToken(1, 1, "unbr")
          )
        )
      )
    );

    makeCase(
      new String[] { "(dia-ches)", "(unbr", "2)" },
      andJoin(
        new Token[] {
          null
        },
        new ParenthesesNode(
          materialPredicate(Material.DIAMOND_CHESTPLATE, unquotedStringToken(0, 1, "dia-ches"))
        ),
        new ParenthesesNode(
          enchantmentPredicate(
            Enchantment.UNBREAKING,
            integerToken(2, 0, 0, 2),
            unquotedStringToken(1, 1, "unbr")
          )
        )
      )
    );

    makeCase(
      new String[] { "(dia-ches", "(unbr", "2))" },
        new ParenthesesNode(
          andJoin(
            new Token[] {
              null
            },
            materialPredicate(Material.DIAMOND_CHESTPLATE, unquotedStringToken(0, 1, "dia-ches")),
            new ParenthesesNode(
              enchantmentPredicate(
                Enchantment.UNBREAKING,
                integerToken(2, 0, 0, 2),
                unquotedStringToken(1, 1, "unbr")
              )
            )
         )
      )
    );

    makeExceptionCase(
      new String[] { "(dia-ches", "(unbr", "2)" },
      0,
      ParseConflict.EXPECTED_CLOSING_PARENTHESIS
    );
  }

  @Test
  public void shouldThrowOnMalformedParentheses() {
    makeExceptionCase(
      new String[] { ")" },
      0,
      ParseConflict.EXPECTED_OPENING_PARENTHESIS
    );

    makeExceptionCase(
      new String[] { "(" },
      0,
      ParseConflict.EXPECTED_SEARCH_PATTERN
    );

    makeExceptionCase(
      new String[] { "(", ")" },
      0,
      ParseConflict.EXPECTED_SEARCH_PATTERN
    );

    makeExceptionCase(
      new String[] { "(", "dia-ches" },
      0,
      ParseConflict.EXPECTED_CLOSING_PARENTHESIS
    );

    makeExceptionCase(
      new String[] { "(", "(dia-ches", ")" },
      0,
      ParseConflict.EXPECTED_CLOSING_PARENTHESIS
    );
  }

  @Test
  public void shouldAddMissingClosingParensToTheVeryEnd() {
    makeCase(
      new String[] {
        "(dia-shov", "(dia-pick", "(dia-hoe)", "dia-hel"
      },
      new ParenthesesNode(
        andJoin(
          new Token[] {
            null
          },
          materialPredicate(Material.DIAMOND_SHOVEL, unquotedStringToken(0, 1, "dia-shov")),
          new ParenthesesNode(
            andJoin(
              new Token[] {
                null, null
              },
              materialPredicate(Material.DIAMOND_PICKAXE, unquotedStringToken(1, 1, "dia-pick")),
              new ParenthesesNode(
                materialPredicate(Material.DIAMOND_HOE, unquotedStringToken(2, 1, "dia-hoe"))
              ),
              materialPredicate(Material.DIAMOND_HELMET, unquotedStringToken(3, 0, "dia-hel"))
            )
          )
        )
      ),
      true
    );
  }

  @Test
  public void shouldAdhereToPrecedences() {
    makeCase(
      new String[] { "dia", "and", "dia", "or", "dia" },
      orJoin(
        new Token[] {
          unquotedStringToken(3, 0, "or")
        },
        andJoin(
          new Token[] {
            unquotedStringToken(1, 0, "and")
          },
          materialPredicate(Material.DIAMOND, unquotedStringToken(0, 0, "dia")),
          materialPredicate(Material.DIAMOND, unquotedStringToken(2, 0, "dia"))
        ),
        materialPredicate(Material.DIAMOND, unquotedStringToken(4, 0, "dia"))
      )
    );

    makeCase(
      new String[] { "dia", "and", "(dia", "or", "dia)" },
      andJoin(
        new Token[] {
          unquotedStringToken(1, 0, "and")
        },
        materialPredicate(Material.DIAMOND, unquotedStringToken(0, 0, "dia")),
        new ParenthesesNode(
          orJoin(
            new Token[] {
              unquotedStringToken(3, 0, "or")
            },
            materialPredicate(Material.DIAMOND, unquotedStringToken(2, 1, "dia")),
            materialPredicate(Material.DIAMOND, unquotedStringToken(4, 0, "dia"))
          )
        )
      )
    );

    makeCase(
      new String[] { "dia", "and", "not", "dia", "or", "dia" },
      orJoin(
        new Token[] {
          unquotedStringToken(4, 0, "or")
        },
        andJoin(
          new Token[] {
            unquotedStringToken(1, 0, "and")
          },
          materialPredicate(Material.DIAMOND, unquotedStringToken(0, 0, "dia")),
          negate(
            unquotedStringToken(2, 0, "not"),
            materialPredicate(Material.DIAMOND, unquotedStringToken(3, 0, "dia"))
          )
        ),
        materialPredicate(Material.DIAMOND, unquotedStringToken(5, 0, "dia"))
      )
    );

    makeCase(
      new String[] { "dia", "and", "not", "(dia", "or", "dia)" },
      andJoin(
        new Token[] {
          unquotedStringToken(1, 0, "and")
        },
        materialPredicate(Material.DIAMOND, unquotedStringToken(0, 0, "dia")),
        negate(
          unquotedStringToken(2, 0, "not"),
          new ParenthesesNode(
            orJoin(
              new Token[] {
                unquotedStringToken(4, 0, "or")
              },
              materialPredicate(Material.DIAMOND, unquotedStringToken(3, 1, "dia")),
              materialPredicate(Material.DIAMOND, unquotedStringToken(5, 0, "dia"))
            )
          )
        )
      )
    );
  }

  @Test
  public void shouldParseImplicitAndWithFollowingFallingPrecedence() {
    makeCase(
      new String[] { "dia", "not", "iron-ingo", "or", "iron-door" },
      orJoin(
        new Token[] {
          unquotedStringToken(3, 0, "or")
        },
        andJoin(
          new Token[] {
            null
          },
          materialPredicate(Material.DIAMOND, unquotedStringToken(0, 0, "dia")),
          negate(
            unquotedStringToken(1, 0, "not"),
            materialPredicate(Material.IRON_INGOT, unquotedStringToken(2, 0, "iron-ingo"))
          )
        ),
        materialPredicate(Material.IRON_DOOR, unquotedStringToken(4, 0, "iron-door"))
      )
    );
  }

  @Test
  public void shouldHaveEquivalentPrecedencesForImplicitAndExplicitAnds() {
    makeCase(
      new String[] { "dia", "or", "iron-ingo", "not", "bone", "not", "gunpowder" },
      orJoin(
        new Token[] {
          unquotedStringToken(1, 0, "or")
        },
        materialPredicate(Material.DIAMOND, unquotedStringToken(0, 0, "dia")),
        andJoin(
          new Token[] {
            null, null
          },
          materialPredicate(Material.IRON_INGOT, unquotedStringToken(2, 0, "iron-ingo")),
          negate(
            unquotedStringToken(3, 0, "not"),
            materialPredicate(Material.BONE, unquotedStringToken(4, 0, "bone"))
          ),
          negate(
            unquotedStringToken(5, 0, "not"),
            materialPredicate(Material.GUNPOWDER, unquotedStringToken(6, 0, "gunpowder"))
          )
        )
      )
    );

    makeCase(
      new String[] { "dia", "or", "iron-ingo", "and", "not", "bone", "and", "not", "gunpowder" },
      orJoin(
        new Token[] {
          unquotedStringToken(1, 0, "or")
        },
        materialPredicate(Material.DIAMOND, unquotedStringToken(0, 0, "dia")),
        andJoin(
          new Token[] {
            unquotedStringToken(3, 0, "and"),
            unquotedStringToken(6, 0, "and")
          },
          materialPredicate(Material.IRON_INGOT, unquotedStringToken(2, 0, "iron-ingo")),
          negate(
            unquotedStringToken(4, 0, "not"),
            materialPredicate(Material.BONE, unquotedStringToken(5, 0, "bone"))
          ),
          negate(
            unquotedStringToken(7, 0, "not"),
            materialPredicate(Material.GUNPOWDER, unquotedStringToken(8, 0, "gunpowder"))
          )
        )
      )
    );
  }

  @Test
  public void shouldParseMultipleImplicitAndsWithFollowingFallingPrecedence() {
    makeCase(
      new String[] {
        "iron-ingo or dia not chest not gold-ingo or apple"
      },
      orJoin(
        new Token[] {
          unquotedStringToken(1, 0, "or"),
          unquotedStringToken(7, 0, "or")
        },
        materialPredicate(Material.IRON_INGOT, unquotedStringToken(0, 0, "iron-ingo")),
        andJoin(
          new Token[] {
            null, null
          },
          materialPredicate(Material.DIAMOND, unquotedStringToken(2, 0, "dia")),
          negate(
            unquotedStringToken(3, 0, "not"),
            materialPredicate(Material.CHEST, unquotedStringToken(4, 0, "chest"))
          ),
          negate(
            unquotedStringToken(5, 0, "not"),
            materialPredicate(Material.GOLD_INGOT, unquotedStringToken(6, 0, "gold-ingo"))
          )
        ),
        materialPredicate(Material.APPLE, unquotedStringToken(8, 0, "apple"))
      )
    );
  }

  @Test
  public void shouldParseSimpleSinglePredicates() {

    // ================================================================================
    // Material
    // ================================================================================

    makeCase(
      new String[] { "dia-ches" },
      materialPredicate(
        Material.DIAMOND_CHESTPLATE,
        unquotedStringToken(0, 0, "dia-ches")
      )
    );

    // ================================================================================
    // Potion Effect
    // ================================================================================

    makeCase(
      new String[] { "regen" },
      potionEffectPredicate(
        PotionEffectType.REGENERATION,
        null,
        null,
        unquotedStringToken(0, 0, "regen")
      )
    );

    makeCase(
      new String[] { "regen", "2" },
      potionEffectPredicate(
        PotionEffectType.REGENERATION,
        integerToken(1, 0, 0, 2),
        null,
        unquotedStringToken(0, 0, "regen")
      )
    );

    makeCase(
      new String[] { "regen", "*" },
      potionEffectPredicate(
        PotionEffectType.REGENERATION,
        integerToken(1, 0, 0, null),
        null,
        unquotedStringToken(0, 0, "regen")
      )
    );

    makeCase(
      new String[] { "regen", "2", "1600" },
      potionEffectPredicate(
        PotionEffectType.REGENERATION,
        integerToken(1, 0, 0, 2),
        integerToken(2, 0, 3, 1600),
        unquotedStringToken(0, 0, "regen")
      )
    );

    makeCase(
      new String[] { "regen", "*", "1600" },
      potionEffectPredicate(
        PotionEffectType.REGENERATION,
        integerToken(1, 0, 0, null),
        integerToken(2, 0, 3, 1600),
        unquotedStringToken(0, 0, "regen")
      )
    );

    makeCase(
      new String[] { "regen", "2", "*" },
      potionEffectPredicate(
        PotionEffectType.REGENERATION,
        integerToken(1, 0, 0, 2),
        integerToken(2, 0, 0, null),
        unquotedStringToken(0, 0, "regen")
      )
    );

    // ================================================================================
    // Enchantment
    // ================================================================================

    makeCase(
      new String[] { "thorn" },
      enchantmentPredicate(
        Enchantment.THORNS,
        null,
        unquotedStringToken(0, 0, "thorn")
      )
    );

    makeCase(
      new String[] { "thorn", "2" },
      enchantmentPredicate(
        Enchantment.THORNS,
        integerToken(1, 0, 0, 2),
        unquotedStringToken(0, 0, "thorn")
      )
    );

    makeCase(
      new String[] { "thorn", "*" },
      enchantmentPredicate(
        Enchantment.THORNS,
        integerToken(1, 0, 0, null),
        unquotedStringToken(0, 0, "thorn")
      )
    );

    // ================================================================================
    // Text Search
    // ================================================================================

    makeCase(
      new String[] { "\"a", "long", "search\"" },
      new TextSearchPredicate(
        quotedStringToken(0, 0, 2, 6, "a long search")
      )
    );

    makeCase(
      new String[] { "\"short\"" },
      new TextSearchPredicate(
        quotedStringToken(0, 0, 0, 6, "short")
      )
    );

    makeCase(
      new String[] { "\"", "short\"" },
      new TextSearchPredicate(
        quotedStringToken(0, 0, 1, 5, " short")
      )
    );

    makeCase(
      new String[] { "\"short", "\"" },
      new TextSearchPredicate(
        quotedStringToken(0, 0, 1, 0, "short ")
      )
    );

    makeCase(
      new String[] { "\"", "short", "\"" },
      new TextSearchPredicate(
        quotedStringToken(0, 0, 2, 0, " short ")
      )
    );

    // ================================================================================
    // Deterioration
    // ================================================================================

    makeCase(
      new String[] { "deter" },
      deteriorationPredicate(
        null,
        null,
        unquotedStringToken(0, 0, "deter")
      )
    );

    makeCase(
      new String[] { "deter", "5" },
      deteriorationPredicate(
        integerToken(1, 0, 0, 5),
        null,
        unquotedStringToken(0, 0, "deter")
      )
    );

    makeCase(
      new String[] { "deter", "*" },
      deteriorationPredicate(
        integerToken(1, 0, 0, null),
        null,
        unquotedStringToken(0, 0, "deter")
      )
    );

    makeCase(
      new String[] { "deter", "5", "85" },
      deteriorationPredicate(
        integerToken(1, 0, 0, 5),
        integerToken(2, 0, 1, 85),
        unquotedStringToken(0, 0, "deter")
      )
    );

    makeCase(
      new String[] { "deter", "*", "85" },
      deteriorationPredicate(
        integerToken(1, 0, 0, null),
        integerToken(2, 0, 1, 85),
        unquotedStringToken(0, 0, "deter")
      )
    );

    makeCase(
      new String[] { "deter", "5", "*" },
      deteriorationPredicate(
        integerToken(1, 0, 0, 5),
        integerToken(2, 0, 0, null),
        unquotedStringToken(0, 0, "deter")
      )
    );

    assertEquals(
      ParseConflict.DOES_NOT_ACCEPT_NON_EQUALS_COMPARISON,
      assertThrows(
        ItemPredicateParseException.class,
        () -> makeCase(
          new String[] { "deter", ">5" },
          amountPredicate(
            integerToken(1, 0, 1, 5, false, ComparisonMode.GREATER_THAN),
            unquotedStringToken(0, 0, "deter")
          )
        )
      ).getConflict()
    );

    assertEquals(
      ParseConflict.DOES_NOT_ACCEPT_NON_EQUALS_COMPARISON,
      assertThrows(
        ItemPredicateParseException.class,
        () -> makeCase(
          new String[] { "deter", "*", "<5" },
          amountPredicate(
            integerToken(2, 0, 1, 5, false, ComparisonMode.LESS_THAN),
            unquotedStringToken(0, 0, "deter")
          )
        )
      ).getConflict()
    );

    // ================================================================================
    // Amount
    // ================================================================================

    makeCase(
      new String[] { "amount", "32" },
      amountPredicate(
        integerToken(1, 0, 1, 32),
        unquotedStringToken(0, 0, "amount")
      )
    );

    assertEquals(
      ParseConflict.EXPECTED_FOLLOWING_INTEGER,
      assertThrows(
        ItemPredicateParseException.class,
        () -> makeCase(
          new String[] { "amount", "*" },
          amountPredicate(
            integerToken(1, 0, 0, 32),
            unquotedStringToken(0, 0, "amount")
          )
        )
      ).getConflict()
    );

    assertEquals(
      ParseConflict.EXPECTED_FOLLOWING_INTEGER,
      assertThrows(
        ItemPredicateParseException.class,
        () -> makeCase(
          new String[] { "amount" },
          amountPredicate(
            integerToken(1, 0, 1, 32),
            unquotedStringToken(0, 0, "amount")
          )
        )
      ).getConflict()
    );
  }

  @Test
  public void shouldParseComplexMultiplePredicates() {
    makeCase(
      new String[] {
        "deter", "3",
        "unbr", "*",
        "\"text", "a\"",
        "regen", "4", "*",
        "dia-pick",
        "\"multi", "arg", "text", "b", "\""
      },
      andJoin(
        new Token[] {
          null, null, null, null, null
        },
        deteriorationPredicate(
          integerToken(1, 0, 0, 3),
          null,
          unquotedStringToken(0, 0, "deter")
        ),
        enchantmentPredicate(
          Enchantment.UNBREAKING,
          integerToken(3, 0, 0, null),
          unquotedStringToken(2, 0, "unbr")
        ),
        new TextSearchPredicate(
          quotedStringToken(4, 0, 5, 1, "text a")
        ),
        potionEffectPredicate(
          PotionEffectType.REGENERATION,
          integerToken(7, 0, 0, 4),
          integerToken(8, 0, 0, null),
          unquotedStringToken(6, 0, "regen")
        ),
        materialPredicate(
          Material.DIAMOND_PICKAXE,
          unquotedStringToken(9, 0, "dia-pick")
        ),
        new TextSearchPredicate(
          quotedStringToken(10, 0, 14, 0, "multi arg text b ")
        )
      )
    );
  }

  @Test
  public void onlyEffectDurationShouldAcceptTimeNotation() {
    makeCase(
      new String[] { "regen", "2", "2:30" },
      potionEffectPredicate(
        PotionEffectType.REGENERATION,
        integerToken(1, 0, 0, 2),
        integerToken(2, 0, 3, 2 * 60 + 30, true, ComparisonMode.EQUALS),
        unquotedStringToken(0, 0, "regen")
      )
    );

    makeCase(
      new String[] { "regen", "*", "2:30" },
      potionEffectPredicate(
        PotionEffectType.REGENERATION,
        integerToken(1, 0, 0, null),
        integerToken(2, 0, 3, 2 * 60 + 30, true, ComparisonMode.EQUALS),
        unquotedStringToken(0, 0, "regen")
      )
    );

    makeExceptionCase(new String[] { "effi", "2:30" }, 1, ParseConflict.DOES_NOT_ACCEPT_TIME_NOTATION);
    makeExceptionCase(new String[] { "deterio", "2:30" }, 1, ParseConflict.DOES_NOT_ACCEPT_TIME_NOTATION);
    makeExceptionCase(new String[] { "deterio", "*", "2:30" }, 2, ParseConflict.DOES_NOT_ACCEPT_TIME_NOTATION);
    makeExceptionCase(new String[] { "deterio", "2:30", "*" }, 1, ParseConflict.DOES_NOT_ACCEPT_TIME_NOTATION);
    makeExceptionCase(new String[] { "deterio", "2:30", "2:30" }, 1, ParseConflict.DOES_NOT_ACCEPT_TIME_NOTATION);
    makeExceptionCase(new String[] { "regen", "2:30" }, 1, ParseConflict.DOES_NOT_ACCEPT_TIME_NOTATION);
    makeExceptionCase(new String[] { "regen", "2:30", "*" }, 1, ParseConflict.DOES_NOT_ACCEPT_TIME_NOTATION);
    makeExceptionCase(new String[] { "regen", "2:30", "2:30" }, 1, ParseConflict.DOES_NOT_ACCEPT_TIME_NOTATION);
    makeExceptionCase(new String[] { "amount", "2:30" }, 1, ParseConflict.DOES_NOT_ACCEPT_TIME_NOTATION);
  }


  @Test
  public void shouldThrowOnInvalidBeginning() {
    makeExceptionCase(new String[] { "5" }, 0, ParseConflict.EXPECTED_SEARCH_PATTERN);
    makeExceptionCase(new String[] { "2:30" }, 0, ParseConflict.EXPECTED_SEARCH_PATTERN);
  }

  @Test
  public void shouldThrowOnMultiplePatternWildcards() {
    makeExceptionCase(new String[] { "sign-?-?" }, 0, ParseConflict.MULTIPLE_SEARCH_PATTERN_WILDCARDS);
    makeExceptionCase(new String[] { "?-sign-?-oak" }, 0, ParseConflict.MULTIPLE_SEARCH_PATTERN_WILDCARDS);
  }

  @Test
  public void shouldThrowOnNoMatches() {
    makeExceptionCase(new String[] { "gibberish-gobbledygook" }, 0, ParseConflict.NO_SEARCH_MATCH);
  }

  @Test
  public void shouldHandleMaterialWildcards() {
    makeCase(
      new String[] { "pickax-?" },
      materialsPredicate(
        unquotedStringToken(0, 0, "pickax-?"),
        Tag.ITEMS_PICKAXES.getValues()
      )
    );

    makeCase(
      new String[] { "?-pickax" },
      materialsPredicate(
        unquotedStringToken(0, 0, "?-pickax"),
        Tag.ITEMS_PICKAXES.getValues()
      )
    );

    makeCase(
      new String[] { "hoe-?" },
      materialsPredicate(
        unquotedStringToken(0, 0, "hoe-?"),
        Tag.ITEMS_HOES.getValues()
      )
    );

    makeCase(
      new String[] { "boots-?" },
      materialsPredicate(
        unquotedStringToken(0, 0, "boots-?"),
        Tag.ITEMS_FOOT_ARMOR.getValues()
      )
    );

    makeCase(
      new String[] { "fen-gat-?" },
      materialsPredicate(
        unquotedStringToken(0, 0, "fen-gat-?"),
        Tag.FENCE_GATES.getValues()
      )
    );

    // Wildcards should not respond with direct matches
    makeCase(
      new String[] { "arrow-?" },
      materialsPredicate(
        unquotedStringToken(0, 0, "arrow-?"),
        List.of(Material.SPECTRAL_ARROW, Material.TIPPED_ARROW)
      )
    );

    makeCase(
      new String[] { "*-arrow" },
      materialsPredicate(
        unquotedStringToken(0, 0, "*-arrow"),
        List.of(Material.ARROW, Material.SPECTRAL_ARROW, Material.TIPPED_ARROW)
      )
    );
  }

  @Test
  public void shouldHandleExactKey() {
    makeCase(
      new String[] { "exact", "unbr", "2" },
      exact(
        unquotedStringToken(0, 0, "exact"),
        enchantmentPredicate(
          Enchantment.UNBREAKING,
          integerToken(2, 0, 0, 2),
          unquotedStringToken(1, 0, "unbr")
        )
      )
    );

    makeCase(
      new String[] { "dia-ches", "and", "exact(", "unbr", "fire-prot", "1)", "or", "not", "exact", "feather-fall" },
      orJoin(
        new Token[] {
          unquotedStringToken(6, 0, "or")
        },
        andJoin(
          new Token[] {
            unquotedStringToken(1, 0, "and")
          },
          materialPredicate(
            Material.DIAMOND_CHESTPLATE,
            unquotedStringToken(0, 0, "dia-ches")
          ),
          exact(
            unquotedStringToken(2, 0, "exact"),
            new ParenthesesNode(
              andJoin(
                new Token[] {
                  null
                },
                enchantmentPredicate(
                  Enchantment.UNBREAKING,
                  null,
                  unquotedStringToken(3, 0, "unbr")
                ),
                enchantmentPredicate(
                  Enchantment.FIRE_PROTECTION,
                  integerToken(5, 0, 0, 1),
                  unquotedStringToken(4, 0, "fire-prot")
                )
              )
            )
          )
        ),
        negate(
          unquotedStringToken(7, 0, "not"),
          exact(
            unquotedStringToken(8, 0, "exact"),
            enchantmentPredicate(
              Enchantment.FEATHER_FALLING,
              null,
              unquotedStringToken(9, 0, "feather-fall")
            )
          )
        )
      )
    );
  }

  @Test
  public void shouldUseShortestMatchByAlphabeticIndex() {
    makeCase(
      new String[] { "lu" }, // Luck, Lure
      potionEffectPredicate(
        PotionEffectType.LUCK,
        null,
        null,
        unquotedStringToken(0, 0, "lu")
      )
    );

    makeCase(
      new String[] { "bu" }, // Bush, Bucket, Bundle
      materialPredicate(
        Material.BUSH,
        unquotedStringToken(0, 0, "bu")
      )
    );

    makeCase(
      new String[] { "wh" }, // Wheat, White
      materialPredicate(
        Material.WHEAT,
        unquotedStringToken(0, 0, "wh")
      )
    );
  }

  private Component makeExceptionCase(String[] args, int expectedArgumentIndex, ParseConflict expectedConflict) {
    var exception = assertThrows(ItemPredicateParseException.class, () -> {
      var predicateParser = parserFactory.create(TokenParser.parseTokens(args, 0), false);
      predicateParser.parseAst();
    });

    assertEquals(expectedArgumentIndex, exception.getToken().beginCommandArgumentIndex());
    assertEquals(expectedConflict, exception.getConflict());

    return exception.highlightedInput(malformedRender, remainingRender);
  }

  private void makeStringificationCase(String[] args, String expected, boolean allowMissingClosingParentheses, boolean stringifyTokens) {
    var predicateParser = parserFactory.create(TokenParser.parseTokens(args, 0), allowMissingClosingParentheses);
    var ast = predicateParser.parseAst();

    assertNotNull(ast);
    assertEquals(expected, PlainStringifier.stringify(ast, stringifyTokens));
  }

  private void makeCase(String[] args, @Nullable ItemPredicate expected) {
    makeCase(args, expected, false);
  }

  private void makeCase(String[] args, @Nullable ItemPredicate expected, boolean allowMissingClosingParentheses) {
    var predicateParser = parserFactory.create(TokenParser.parseTokens(args, 0), allowMissingClosingParentheses);
    var actual = predicateParser.parseAst();
    equalityChecker.check(expected, actual);
  }

  private static Component parseLegacyColors(String input) {
    var result = Component.empty();

    NamedTextColor lastColor = null;
    int lastBeginning = -1;

    String part;

    for (var charIndex = 0; charIndex < input.length(); ++charIndex) {
      var currentChar = input.charAt(charIndex);
      var isLast = charIndex == input.length() - 1;
      var isSpace = currentChar == ' ';

      if (currentChar == '§') {
        if (lastBeginning > 0) {
          part = input.substring(lastBeginning, charIndex);
          result = result.append(Component.text(part).color(lastColor));
          lastBeginning = -1;
        }

        if (isLast)
          throw new IllegalStateException("Trailing color-sequence-initiator");

        lastColor = switch (input.charAt(++charIndex)) {
          case 'c' -> NamedTextColor.RED;
          case '4' -> NamedTextColor.DARK_RED;
          default -> throw new IllegalStateException("Unsupported color: " + input.charAt(charIndex));
        };

        continue;
      }

      if (!isSpace && lastBeginning < 0)
        lastBeginning = charIndex;

      if (isSpace || isLast) {
        if (lastBeginning > 0) {
          part = input.substring(lastBeginning, charIndex + (isSpace ? 0 : 1));
          result = result.append(Component.text(part).color(lastColor));
          lastBeginning = -1;
        }

        // Spaces are always unformatted
        if (isSpace)
          result = result.append(Component.space());
      }
    }

    return result;
  }

  private static String formattedStringification(Component component) {
    // This surely is a bit hackish, but it's more than enough to get a proper side-by-side diff-view
    return component.toString()
      .replace("{", "{\n")
      .replace(",", ",\n");
  }

  private static void assertComponentsEqual(Component expected, Component actual) {
    if (Objects.equals(expected, actual))
      return;

    throw new AssertionFailedError("Components differed", formattedStringification(expected), formattedStringification(actual));
  }
}
