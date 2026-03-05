// Generated from ECF.g4 by ANTLR 4.7.2

package antlr; 

import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link ECFParser}.
 */
public interface ECFListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link ECFParser#ecf}.
	 * @param ctx the parse tree
	 */
	void enterEcf(ECFParser.EcfContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECFParser#ecf}.
	 * @param ctx the parse tree
	 */
	void exitEcf(ECFParser.EcfContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECFParser#state}.
	 * @param ctx the parse tree
	 */
	void enterState(ECFParser.StateContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECFParser#state}.
	 * @param ctx the parse tree
	 */
	void exitState(ECFParser.StateContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECFParser#edge_name}.
	 * @param ctx the parse tree
	 */
	void enterEdge_name(ECFParser.Edge_nameContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECFParser#edge_name}.
	 * @param ctx the parse tree
	 */
	void exitEdge_name(ECFParser.Edge_nameContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECFParser#in_state_ref}.
	 * @param ctx the parse tree
	 */
	void enterIn_state_ref(ECFParser.In_state_refContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECFParser#in_state_ref}.
	 * @param ctx the parse tree
	 */
	void exitIn_state_ref(ECFParser.In_state_refContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECFParser#out_state_ref}.
	 * @param ctx the parse tree
	 */
	void enterOut_state_ref(ECFParser.Out_state_refContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECFParser#out_state_ref}.
	 * @param ctx the parse tree
	 */
	void exitOut_state_ref(ECFParser.Out_state_refContext ctx);
	/**
	 * Enter a parse tree produced by {@link ECFParser#cost}.
	 * @param ctx the parse tree
	 */
	void enterCost(ECFParser.CostContext ctx);
	/**
	 * Exit a parse tree produced by {@link ECFParser#cost}.
	 * @param ctx the parse tree
	 */
	void exitCost(ECFParser.CostContext ctx);
}