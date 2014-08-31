/**    / \____  _    ______   _____ / \____   ____  _____
 *    /  \__  \/ \  / \__  \ /  __//  \__  \ /    \/ __  \   Javaslang
 *  _/  // _\  \  \/  / _\  \\_  \/  // _\  \  /\  \__/  /   Copyright 2014 Daniel Dietrich
 * /___/ \_____/\____/\_____/____/\___\_____/_/  \_/____/    Licensed under the Apache License, Version 2.0
 */
package javaslang.parser;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javaslang.Requirements;
import javaslang.Strings;
import javaslang.collection.Node;
import javaslang.collection.Tree;
import javaslang.monad.Either;
import javaslang.monad.Failure;
import javaslang.monad.Success;
import javaslang.monad.Try;

// TODO: Distinguish between tokenizing (Lexer) and parsing (Parser)
//       - https://github.com/antlr/grammars-v4/blob/master/antlr4/ANTLRv4Lexer.g4
//       - https://github.com/antlr/grammars-v4/blob/master/antlr4/ANTLRv4Parser.g4
// TODO: Make regular expressions first class members of grammar definitions, e.g. fragment DIGITS: '1'..'9' '0'..'9'*;
//		- '|' <space> * ? + .. \n \r \t etc. (escape reserved symbols, e.g. quote ')
// TODO: Make grammars compatible to Antlr4 grammars (i.e. parse (and stringify) them - https://github.com/antlr/grammars-v4/blob/master/java8/Java8.g4
// TODO: Add fragments - http://stackoverflow.com/questions/6487593/what-does-fragment-means-in-antlr
// TODO: CST to AST transformation (as external DSL within the grammar)
// TODO: add Regex Parser: "regex" (literal has single quotes 'lll')
// TODO: unescape literals
// TODO: remove Branch, Sequence and Multiplicity nodes if they have no name/id
/**
 * <pre>
 * <code>class JSONGrammar extends Grammar {
 * 
 *     // define start rule
 *     JSONGrammar() {
 *         super(JSONGrammar::json);
 *     }
 *     
 *     // json : jsonObject | jsonArray | jsonString | jsonNumber | 'true' | 'false' | 'null' ;
 *     static Rule json() {
 *         return rule("json",
 *                 JSONGrammar::jsonObject,
 *                 JSONGrammar::jsonArray,
 *                 JSONGrammar::jsonString,
 *                 JSONGrammar::jsonNumber,
 *                 str("true"),
 *                 str("false"),
 *                 str("null"));
 *     }
 *     
 *     // jsonObject : '{' ( pair ( ',' pair )* )? '}' ;
 *     static Parser jsonObject() {
 *         return rule("jsonObject", seq(str("{"), list(JSONGrammar::pair, ","), str("}"));
 *     }
 *     
 *     // pair : jsonString ':' json ;
 *     static Parser pair() {
 *         return seq(JSONGrammar::jsonString, str(":"), JSONGrammar::json);
 *     }
 *     
 *     // etc.
 *     
 * }</code>
 * </pre>
 * 
 * @see <a
 *      href="http://stackoverflow.com/questions/1888854/what-is-the-difference-between-an-abstract-syntax-tree-and-a-concrete-syntax-tre">Abstract
 *      vs. concrete syntax tree</a>
 */
// DEV-NOTE: Extra class needed because interface cannot override Object#toString()
public class Grammar {

	public static final Parsers.Any ANY = Parsers.Any.INSTANCE;
	public static final Parsers.EOF EOF = Parsers.EOF.INSTANCE;

	// TODO: should all parsers initially get the supplied referenced parsers?
	private final Parsers.Rule startRule;

	protected Grammar(Supplier<Parsers.Rule> startRule) {
		Requirements.requireNonNull(startRule, "startRule is null");
		this.startRule = startRule.get();
	}

	/**
	 * TODO: javadoc
	 * 
	 * @param text A text input to be parsed.
	 * @return A concrete syntax tree of the text on parse success or a failure if a parse error occured.
	 */
	public Try<Tree<Token>> parse(String text) {
		final Either<Integer, Node<Token>> parseResult = startRule.parse(text, 0, false);
		if (parseResult.isRight()) {
			final Tree<Token> concreteSyntaxTree = parseResult.right().get().asTree();
			return new Success<>(concreteSyntaxTree);
		} else {
			final int index = parseResult.left().get();
			return new Failure<>(new IllegalArgumentException("cannot parse input at "
					+ Strings.lineAndColumn(text, index)));
		}
	}

	@Override
	public String toString() {
		// preserving insertion order
		final LinkedHashSet<Parsers.Rule> rules = new LinkedHashSet<>();
		findRules(rules, startRule);
		return rules.stream().map(Object::toString).collect(Collectors.joining("\n\n"));
	}

	private void findRules(Set<Parsers.Rule> rules, Parsers.Rule rule) {
		if (!rules.contains(rule)) {
			rules.add(rule);
			Stream.of(rule.alternatives)
					.filter(parser -> parser.get() instanceof Parsers.Rule)
					.forEach(ruleRef -> findRules(rules, (Parsers.Rule) ruleRef.get()));
		}
	}

	// -- atomic shortcuts used in grammar definitions

	/**
	 * Shortcut for {@code new Parsers.Rule(name, alternatives)}.
	 * 
	 * @param name Rule name.
	 * @param alternatives Rule alternatives.
	 * @return A new {@link Parsers.Rule}.
	 */
	@SafeVarargs
	public static Parsers.Rule rule(String name, Supplier<Parser>... alternatives) {
		return new Parsers.Rule(name, alternatives);
	}

	/**
	 * Shortcut for {@code new Parsers.SubRule(alternatives)}.
	 * 
	 * @param alternatives SubRule alternatives.
	 * @return A new {@link Parsers.SubRule}.
	 */
	@SafeVarargs
	public static Parsers.SubRule subRule(Supplier<Parser>... alternatives) {
		return new Parsers.SubRule(alternatives);
	}

	/**
	 * Shortcut for {@code new Parsers.Sequence(parsers))}.
	 * 
	 * @param parsers Sequenced parsers.
	 * @return A new {@link Parsers.Sequence}.
	 */
	@SafeVarargs
	public static Parsers.Sequence seq(Supplier<Parser>... parsers) {
		return new Parsers.Sequence(parsers);
	}

	/**
	 * Shortcut for {@code new Parsers.CharSet(charset)}.
	 * 
	 * @param charset A charset String.
	 * @return A new {@link Parsers.CharSet}.
	 */
	public static Parsers.CharSet charSet(String charset) {
		return new Parsers.CharSet(charset);
	}

	/**
	 * Shortcut for {@code new Parsers.CharRange(from, to)}.
	 * 
	 * @param from Left bound of the range, inclusive.
	 * @param to Right bound of the range, inclusive.
	 * @return A new {@link Parsers.CharRange}.
	 */
	public static Parsers.CharRange range(char from, char to) {
		return new Parsers.CharRange(from, to);
	}

	/**
	 * Shortcut for {@code new Parsers.Literal(s)}.
	 * 
	 * @param s A string.
	 * @return A new {@link Parsers.Literal}.
	 */
	public static Parsers.Literal str(String s) {
		return new Parsers.Literal(s);
	}

	/**
	 * Shortcut for {@code new Parsers.Quantifier(new Parsers.Sequence(parsers), Parsers.Quantifier.Bounds.ZERO_TO_ONE)}
	 * .
	 * 
	 * @param parsers Quantified parsers.
	 * @return A new {@link Parsers.Quantifier}.
	 */
	@SafeVarargs
	public static Parsers.Quantifier _0_1(Supplier<Parser>... parsers) {
		return quantifier(parsers, Parsers.Quantifier.Bounds.ZERO_TO_ONE);
	}

	/**
	 * Shortcut for {@code new Parsers.Quantifier(new Parsers.Sequence(parsers), Parsers.Quantifier.Bounds.ZERO_TO_N)}.
	 * 
	 * @param parsers Quantified parsers.
	 * @return A new {@link Parsers.Quantifier}.
	 */
	@SafeVarargs
	public static Parsers.Quantifier _0_n(Supplier<Parser>... parsers) {
		return quantifier(parsers, Parsers.Quantifier.Bounds.ZERO_TO_N);
	}

	/**
	 * Shortcut for {@code new Parsers.Quantifier(new Parsers.Sequence(parsers), Parsers.Quantifier.Bounds.ONE_TO_N)}.
	 * 
	 * @param parsers Quantified parsers.
	 * @return A new {@link Parsers.Quantifier}.
	 */
	@SafeVarargs
	public static Parsers.Quantifier _1_n(Supplier<Parser>... parsers) {
		return quantifier(parsers, Parsers.Quantifier.Bounds.ONE_TO_N);
	}

	private static Parsers.Quantifier quantifier(Supplier<Parser>[] parsers, Parsers.Quantifier.Bounds bounds) {
		Requirements.requireNotNullOrEmpty(parsers, "parsers is null or empty");
		final Supplier<Parser> parser = (parsers.length == 1) ? parsers[0] : new Parsers.Sequence(parsers);
		return new Parsers.Quantifier(parser, bounds);
	}

	// -- composite shortcuts used in grammar definitions

	/**
	 * A separated list, equivalent to {@code ( P ( ',' P )* )?}.
	 * <p>
	 * {@code list(parser, separator)}
	 * <p>
	 * is a shortcut for
	 * <p>
	 * {@code _0_1(parser, _0_N(str(separator), parser))}.
	 * <p>
	 * which expands to
	 * <p>
	 * {@code new Quantifier(new Sequence(parser, new Quantifier(new Sequence(new Literal(separator), parser), ZERO_TO_N)), ZERO_TO_ONE)}.
	 * 
	 * @param parser A Parser.
	 * @param delimiter A delimiter.
	 * @return A Parser which recognizes {@code ( P ( ',' P )* )?}.
	 */
	public static Parser list(Supplier<Parser> parser, String delimiter) {
		return _0_1(parser, _0_n(str(delimiter), parser));
	}

	public static Parser list(Supplier<Parser> parser, String delimiter, String prefix, String suffix) {
		return seq(str(prefix), _0_1(parser, _0_n(str(delimiter), parser)), str(suffix));
	}
}