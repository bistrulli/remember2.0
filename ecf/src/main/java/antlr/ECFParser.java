// Generated from ECF.g4 by ANTLR 4.7.2

package antlr; 

import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class ECFParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.7.2", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9, 
		NUMBER=10, STATE_ID=11, ID=12, WS=13;
	public static final int
		RULE_ecf = 0, RULE_state = 1, RULE_edge_name = 2, RULE_in_state_ref = 3, 
		RULE_out_state_ref = 4, RULE_cost = 5;
	private static String[] makeRuleNames() {
		return new String[] {
			"ecf", "state", "edge_name", "in_state_ref", "out_state_ref", "cost"
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

	@Override
	public String getGrammarFileName() { return "ECF.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public ECFParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	public static class EcfContext extends ParserRuleContext {
		public List<StateContext> state() {
			return getRuleContexts(StateContext.class);
		}
		public StateContext state(int i) {
			return getRuleContext(StateContext.class,i);
		}
		public EcfContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_ecf; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ECFListener ) ((ECFListener)listener).enterEcf(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ECFListener ) ((ECFListener)listener).exitEcf(this);
		}
	}

	public final EcfContext ecf() throws RecognitionException {
		EcfContext _localctx = new EcfContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_ecf);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(12);
			match(T__0);
			setState(13);
			match(T__1);
			setState(15); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(14);
				state();
				}
				}
				setState(17); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( _la==T__3 );
			setState(19);
			match(T__2);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class StateContext extends ParserRuleContext {
		public Edge_nameContext edge_name() {
			return getRuleContext(Edge_nameContext.class,0);
		}
		public CostContext cost() {
			return getRuleContext(CostContext.class,0);
		}
		public List<In_state_refContext> in_state_ref() {
			return getRuleContexts(In_state_refContext.class);
		}
		public In_state_refContext in_state_ref(int i) {
			return getRuleContext(In_state_refContext.class,i);
		}
		public List<Out_state_refContext> out_state_ref() {
			return getRuleContexts(Out_state_refContext.class);
		}
		public Out_state_refContext out_state_ref(int i) {
			return getRuleContext(Out_state_refContext.class,i);
		}
		public StateContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_state; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ECFListener ) ((ECFListener)listener).enterState(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ECFListener ) ((ECFListener)listener).exitState(this);
		}
	}

	public final StateContext state() throws RecognitionException {
		StateContext _localctx = new StateContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_state);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(21);
			match(T__3);
			setState(22);
			edge_name();
			setState(23);
			match(T__1);
			setState(24);
			match(T__4);
			setState(25);
			cost();
			setState(26);
			match(T__5);
			setState(27);
			match(T__6);
			setState(29);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==STATE_ID) {
				{
				setState(28);
				in_state_ref();
				}
			}

			setState(35);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__7) {
				{
				{
				setState(31);
				match(T__7);
				setState(32);
				in_state_ref();
				}
				}
				setState(37);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(38);
			match(T__5);
			setState(39);
			match(T__8);
			setState(41);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==STATE_ID) {
				{
				setState(40);
				out_state_ref();
				}
			}

			setState(47);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__7) {
				{
				{
				setState(43);
				match(T__7);
				setState(44);
				out_state_ref();
				}
				}
				setState(49);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(50);
			match(T__5);
			setState(51);
			match(T__2);
			setState(52);
			match(T__5);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Edge_nameContext extends ParserRuleContext {
		public TerminalNode STATE_ID() { return getToken(ECFParser.STATE_ID, 0); }
		public Edge_nameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_edge_name; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ECFListener ) ((ECFListener)listener).enterEdge_name(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ECFListener ) ((ECFListener)listener).exitEdge_name(this);
		}
	}

	public final Edge_nameContext edge_name() throws RecognitionException {
		Edge_nameContext _localctx = new Edge_nameContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_edge_name);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(54);
			match(STATE_ID);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class In_state_refContext extends ParserRuleContext {
		public TerminalNode STATE_ID() { return getToken(ECFParser.STATE_ID, 0); }
		public In_state_refContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_in_state_ref; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ECFListener ) ((ECFListener)listener).enterIn_state_ref(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ECFListener ) ((ECFListener)listener).exitIn_state_ref(this);
		}
	}

	public final In_state_refContext in_state_ref() throws RecognitionException {
		In_state_refContext _localctx = new In_state_refContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_in_state_ref);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(56);
			match(STATE_ID);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Out_state_refContext extends ParserRuleContext {
		public TerminalNode STATE_ID() { return getToken(ECFParser.STATE_ID, 0); }
		public Out_state_refContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_out_state_ref; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ECFListener ) ((ECFListener)listener).enterOut_state_ref(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ECFListener ) ((ECFListener)listener).exitOut_state_ref(this);
		}
	}

	public final Out_state_refContext out_state_ref() throws RecognitionException {
		Out_state_refContext _localctx = new Out_state_refContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_out_state_ref);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(58);
			match(STATE_ID);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class CostContext extends ParserRuleContext {
		public TerminalNode NUMBER() { return getToken(ECFParser.NUMBER, 0); }
		public CostContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_cost; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof ECFListener ) ((ECFListener)listener).enterCost(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof ECFListener ) ((ECFListener)listener).exitCost(this);
		}
	}

	public final CostContext cost() throws RecognitionException {
		CostContext _localctx = new CostContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_cost);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(60);
			match(NUMBER);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static final String _serializedATN =
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\3\17A\4\2\t\2\4\3\t"+
		"\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\3\2\3\2\3\2\6\2\22\n\2\r\2\16\2\23"+
		"\3\2\3\2\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\3\5\3 \n\3\3\3\3\3\7\3$\n\3\f\3"+
		"\16\3\'\13\3\3\3\3\3\3\3\5\3,\n\3\3\3\3\3\7\3\60\n\3\f\3\16\3\63\13\3"+
		"\3\3\3\3\3\3\3\3\3\4\3\4\3\5\3\5\3\6\3\6\3\7\3\7\3\7\2\2\b\2\4\6\b\n\f"+
		"\2\2\2?\2\16\3\2\2\2\4\27\3\2\2\2\68\3\2\2\2\b:\3\2\2\2\n<\3\2\2\2\f>"+
		"\3\2\2\2\16\17\7\3\2\2\17\21\7\4\2\2\20\22\5\4\3\2\21\20\3\2\2\2\22\23"+
		"\3\2\2\2\23\21\3\2\2\2\23\24\3\2\2\2\24\25\3\2\2\2\25\26\7\5\2\2\26\3"+
		"\3\2\2\2\27\30\7\6\2\2\30\31\5\6\4\2\31\32\7\4\2\2\32\33\7\7\2\2\33\34"+
		"\5\f\7\2\34\35\7\b\2\2\35\37\7\t\2\2\36 \5\b\5\2\37\36\3\2\2\2\37 \3\2"+
		"\2\2 %\3\2\2\2!\"\7\n\2\2\"$\5\b\5\2#!\3\2\2\2$\'\3\2\2\2%#\3\2\2\2%&"+
		"\3\2\2\2&(\3\2\2\2\'%\3\2\2\2()\7\b\2\2)+\7\13\2\2*,\5\n\6\2+*\3\2\2\2"+
		"+,\3\2\2\2,\61\3\2\2\2-.\7\n\2\2.\60\5\n\6\2/-\3\2\2\2\60\63\3\2\2\2\61"+
		"/\3\2\2\2\61\62\3\2\2\2\62\64\3\2\2\2\63\61\3\2\2\2\64\65\7\b\2\2\65\66"+
		"\7\5\2\2\66\67\7\b\2\2\67\5\3\2\2\289\7\r\2\29\7\3\2\2\2:;\7\r\2\2;\t"+
		"\3\2\2\2<=\7\r\2\2=\13\3\2\2\2>?\7\f\2\2?\r\3\2\2\2\7\23\37%+\61";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}