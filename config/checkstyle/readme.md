### Add to idea
Settings -> Editor -> Code Style -> Java -> Scheme -> 
Import Scheme -> CheckStyle Configuration -> `config/checkstyle/checkstyle.xml`

### Suppressions Configuration
In this checkstyle.xml using [SuppressionXpathFilter](https://checkstyle.org/config_filters.html#SuppressionXpathFilter)  
Location: `config/checkstyle/suppressions.xml`  
The Scheme for XPath:
```markdown
CLASS_DEF -> CLASS_DEF [1:0]
|--MODIFIERS -> MODIFIERS [1:0]
|   `--LITERAL_PUBLIC -> public [1:0]
|--LITERAL_CLASS -> class [1:7]
|--IDENT -> InputTest [1:13]
`--OBJBLOCK -> OBJBLOCK [1:23]
|--LCURLY -> { [1:23]
|--METHOD_DEF -> METHOD_DEF [2:4]
|   |--MODIFIERS -> MODIFIERS [2:4]
|   |   |--ANNOTATION -> ANNOTATION [2:4]
|   |   |   |--AT -> @ [2:4]
|   |   |   |--IDENT -> Generated [2:5]
|   |   |   |--LPAREN -> ( [2:14]
|   |   |   |--EXPR -> EXPR [2:15]
|   |   |   |   `--STRING_LITERAL -> "first" [2:15]
|   |   |   `--RPAREN -> ) [2:22]
|   |   `--LITERAL_PUBLIC -> public [3:4]
|   |--TYPE -> TYPE [3:11]
|   |   `--LITERAL_VOID -> void [3:11]
|   |--IDENT -> test1 [3:16]
|   |--LPAREN -> ( [3:21]
|   |--PARAMETERS -> PARAMETERS [3:22]
|   |--RPAREN -> ) [3:22]
|   `--SLIST -> { [3:24]
|       `--RCURLY -> } [4:4]
|--METHOD_DEF -> METHOD_DEF [6:4]
|   |--MODIFIERS -> MODIFIERS [6:4]
|   |   |--ANNOTATION -> ANNOTATION [6:4]
|   |   |   |--AT -> @ [6:4]
|   |   |   |--IDENT -> Generated [6:5]
|   |   |   |--LPAREN -> ( [6:14]
|   |   |   |--EXPR -> EXPR [6:15]
|   |   |   |   `--STRING_LITERAL -> "second" [6:15]
|   |   |   `--RPAREN -> ) [6:23]
|   |   `--LITERAL_PUBLIC -> public [7:4]
|   |--TYPE -> TYPE [7:11]
|   |   `--LITERAL_VOID -> void [7:11]
|   |--IDENT -> test2 [7:16]
|   |--LPAREN -> ( [7:21]
|   |--PARAMETERS -> PARAMETERS [7:22]
|   |--RPAREN -> ) [7:22]
|   `--SLIST -> { [7:24]
|       `--RCURLY -> } [8:4]
`--RCURLY -> } [9:0]
```