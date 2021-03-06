rmdir /S /q src\java\map_regexps
rmdir /S /q target\classes
rm lib\parser.jar
java -jar lib\sablecc.jar src\java\lang.grammar
call lein javac
cp src/java/map_regexps/parser/parser.dat target/classes/map_regexps/parser
cp src/java/map_regexps/lexer/lexer.dat target/classes/map_regexps/lexer
cd target\classes
jar -cvf parser.jar map_regexps
mv parser.jar ../../lib/
cd ..\..
