/**
 * Define the Edge Control FLow (ECF)
 */
grammar ECF;

@header {
package antlr; 
}

ecf: 'FLOW' '{' state+ '}';    

state  : 'edge' edge_name '{' 'cost' cost ';' 'in' in_state_ref? (',' in_state_ref)* ';' 'out' out_state_ref? (',' out_state_ref)* ';' '}' ';' ;

edge_name: STATE_ID;
in_state_ref: STATE_ID ;
out_state_ref: STATE_ID ; 
cost: NUMBER ;    
  
NUMBER: [0-9]+ ;  
STATE_ID : [A-Za-z0-9\\$_!#]+ ; 

ID: ~( '{'| '}'| ',' | ';' |' '|'\n'|'\r'|'\t');

WS : [ \t\r\n]+ -> skip ; // skip spaces, tabs, newlines     

