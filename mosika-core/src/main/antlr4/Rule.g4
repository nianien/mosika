grammar Rule;

parse
 : expr EOF
 ;

expr
 : '(' expr ')' #PAREN
 | '!' expr #NOT
 | expr '&&' expr #AND
 | expr '||' expr #OR
 | expr '?'  expr (':' expr)? #IF
 | expr op=(SER_OP | PAR_OP) expr #SEQ
 | ANY LP arguments RP #ANY
 | ALL LP arguments RP #ALL
 | SOME LP bound ',' bound ',' arguments RP #SOME
 | (ID | NUMBER | ANY | ALL | SOME) ruleArguments? #ID
 ;

ruleArguments
 : LP RULE_ARGUMENT RP
 ;

arguments
 : expr (',' expr )*
 ;

bound
 : NUMBER
 | UNBOUNDED
 ;

LP:'(';
RP:')';
SER_OP: '->';
PAR_OP: '=>';
ANY: 'any';
ALL: 'all';
SOME: 'some';
RULE_ARGUMENT: '"""' .*? '"""';

UNBOUNDED: '_';
NUMBER: DIGIT+;

ID: '∅'|(ID_LETTER|DIGIT)+;
fragment ID_LETTER:'a'..'z'|'A'..'Z'|'_';
fragment DIGIT:'0'..'9';

WS: [ \t\r\n]+ -> skip;



