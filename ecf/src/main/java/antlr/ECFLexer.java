// Generated from ECF.g4 by ANTLR 4.7.2

package antlr; 

import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class ECFLexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.7.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9, 
		NUMBER=10, STATE_ID=11, ID=12, WS=13;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	private static String[] makeRuleNames() {
		return new String[] {
			"T__0", "T__1", "T__2", "T__3", "T__4", "T__5", "T__6", "T__7", "T__8", 
			"NUMBER", "STATE_ID", "ID", "WS"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'FLOW'", "'{'", "'}'", "'edge'", "'cost'", "';'", "'in'", "','", 
			"'out'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, null, null, null, null, null, "NUMBER", 
			"STATE_ID", "ID", "WS"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}


	public ECFLexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "ECF.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public String[] getChannelNames() { return channelNames; }

	@Override
	public String[] getModeNames() { return modeNames; }

	@Override
	public ATN getATN() { return _ATN; }

	public static final String _serializedATN =
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\2\17N\b\1\4\2\t\2\4"+
		"\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13\t"+
		"\13\4\f\t\f\4\r\t\r\4\16\t\16\3\2\3\2\3\2\3\2\3\2\3\3\3\3\3\4\3\4\3\5"+
		"\3\5\3\5\3\5\3\5\3\6\3\6\3\6\3\6\3\6\3\7\3\7\3\b\3\b\3\b\3\t\3\t\3\n\3"+
		"\n\3\n\3\n\3\13\6\13=\n\13\r\13\16\13>\3\f\6\fB\n\f\r\f\16\fC\3\r\3\r"+
		"\3\16\6\16I\n\16\r\16\16\16J\3\16\3\16\2\2\17\3\3\5\4\7\5\t\6\13\7\r\b"+
		"\17\t\21\n\23\13\25\f\27\r\31\16\33\17\3\2\6\3\2\62;\t\2##%&\62;C\\^^"+
		"aac|\t\2\13\f\17\17\"\"..==}}\177\177\5\2\13\f\17\17\"\"\2P\2\3\3\2\2"+
		"\2\2\5\3\2\2\2\2\7\3\2\2\2\2\t\3\2\2\2\2\13\3\2\2\2\2\r\3\2\2\2\2\17\3"+
		"\2\2\2\2\21\3\2\2\2\2\23\3\2\2\2\2\25\3\2\2\2\2\27\3\2\2\2\2\31\3\2\2"+
		"\2\2\33\3\2\2\2\3\35\3\2\2\2\5\"\3\2\2\2\7$\3\2\2\2\t&\3\2\2\2\13+\3\2"+
		"\2\2\r\60\3\2\2\2\17\62\3\2\2\2\21\65\3\2\2\2\23\67\3\2\2\2\25<\3\2\2"+
		"\2\27A\3\2\2\2\31E\3\2\2\2\33H\3\2\2\2\35\36\7H\2\2\36\37\7N\2\2\37 \7"+
		"Q\2\2 !\7Y\2\2!\4\3\2\2\2\"#\7}\2\2#\6\3\2\2\2$%\7\177\2\2%\b\3\2\2\2"+
		"&\'\7g\2\2\'(\7f\2\2()\7i\2\2)*\7g\2\2*\n\3\2\2\2+,\7e\2\2,-\7q\2\2-."+
		"\7u\2\2./\7v\2\2/\f\3\2\2\2\60\61\7=\2\2\61\16\3\2\2\2\62\63\7k\2\2\63"+
		"\64\7p\2\2\64\20\3\2\2\2\65\66\7.\2\2\66\22\3\2\2\2\678\7q\2\289\7w\2"+
		"\29:\7v\2\2:\24\3\2\2\2;=\t\2\2\2<;\3\2\2\2=>\3\2\2\2><\3\2\2\2>?\3\2"+
		"\2\2?\26\3\2\2\2@B\t\3\2\2A@\3\2\2\2BC\3\2\2\2CA\3\2\2\2CD\3\2\2\2D\30"+
		"\3\2\2\2EF\n\4\2\2F\32\3\2\2\2GI\t\5\2\2HG\3\2\2\2IJ\3\2\2\2JH\3\2\2\2"+
		"JK\3\2\2\2KL\3\2\2\2LM\b\16\2\2M\34\3\2\2\2\6\2>CJ\3\b\2\2";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}