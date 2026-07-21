grammar Rule;

parse
 : expr EOF
 ;

expr
 : '(' expr ')' #PAREN
 | <assoc=right> '!' expr #NOT
 | expr '&&' expr #AND
 | expr '||' expr #OR
 | expr '?'  expr (':' expr)? #IF
 | expr '->' expr #SER
 | expr '=>' expr #PAR
 | HITS LP bound ',' bound ',' arguments RP #HITS
 | (ID | NUMBER) #ID
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
HITS: 'hits';
STRING:'"'(ESC|.)*?':';
fragment ESC:'\\'[btnr"\\];

UNBOUNDED: '_';
NUMBER: DIGIT+;

ID: '∅'|(ID_LETTER|DIGIT)+;
fragment ID_LETTER:'a'..'z'|'A'..'Z'|'_';
fragment DIGIT:'0'..'9';

WS: [ \t\r\n]+ -> skip;






