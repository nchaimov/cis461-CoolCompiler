class ASTnode {
	public int kind; /* Instead of using subclassing. Example binop */
	public ASTnode left;
	public ASTnode center;
	public ASTnode right;
	public Object value;

	protected static int nodeNum = 0;

	public static String idToName(int id) {
		switch (id) {
		case sym.INHERITS:
			return "INHERITS";
		case sym.STRINGLIT:
			return "STRINGLIT";
		case sym.CASE:
			return "CASE";
		case sym.LPAREN:
			return "LPAREN";
		case sym.SEMI:
			return "SEMI";
		case sym.MINUS:
			return "MINUS";
		case sym.RPAREN:
			return "RPAREN";
		case sym.NOT:
			return "NOT";
		case sym.TYPEID:
			return "TYPEID";
		case sym.INTLIT:
			return "INTLIT";
		case sym.LT:
			return "LT";
		case sym.IN:
			return "IN";
		case sym.COMMA:
			return "COMMA";
		case sym.CLASS:
			return "CLASS";
		case sym.FI:
			return "FI";
		case sym.DIV:
			return "DIV";
		case sym.PLUS:
			return "PLUS";
		case sym.ASSIGN:
			return "ASSIGN";
		case sym.IF:
			return "IF";
		case sym.ID:
			return "ID";
		case sym.DOT:
			return "DOT";
		case sym.OF:
			return "OF";
		case sym.EOF:
			return "EOF";
		case sym.OD:
			return "OD";
		case sym.TRUE:
			return "TRUE";
		case sym.NEW:
			return "NEW";
		case sym.error:
			return "error";
		case sym.ISVOID:
			return "ISVOID";
		case sym.EQ:
			return "EQ";
		case sym.TIMES:
			return "TIMES";
		case sym.COLON:
			return "COLON";
		case sym.NEG:
			return "NEG";
		case sym.LBRACE:
			return "LBRACE";
		case sym.ELSE:
			return "ELSE";
		case sym.WHILE:
			return "WHILE";
		case sym.ESAC:
			return "ESAC";
		case sym.LET:
			return "LET";
		case sym.THEN:
			return "THEN";
		case sym.RBRACE:
			return "RBRACE";
		case sym.LEQ:
			return "LEQ";
		case sym.RIGHTARROW:
			return "RIGHTARROW";
		case sym.AT:
			return "AT";
		case sym.FALSE:
			return "FALSE";
		case sym.DO:
			return "DO";
		case Nodes.METHOD:
			return "METHOD";
		case Nodes.ATTRIBUTE:
			return "ATTRIBUTE";
		default:
			throw new IllegalArgumentException("Unknown token");
		}
	}

	// Leaf node, with value
	public ASTnode(int _kind, Object _value) {
		kind = _kind;
		left = null;
		center = null;
		right = null;
		value = _value;
	}

	public ASTnode(int _kind, ASTnode _left, ASTnode _center, ASTnode _right, Object _value) {
		kind = _kind;
		left = _left;
		center = _center;
		right = _right;
		value = _value;
	}

	public void dump() {
		ASTnode.nodeNum = 0;
		System.out.println("digraph G {");
		System.out.println("\tnode [shape=record];");
		this.writeGraphviz(0);
		System.out.println("}");
	}

	protected void writeGraphviz(int id) {
		int leftid = -1;
		int centerid = -1;
		int rightid = -1;
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("\tnode");
		stringBuilder.append(id);
		stringBuilder.append(" [label=\"<f0> |<f1> ");
		stringBuilder.append(ASTnode.idToName(kind));
		stringBuilder.append("");
		stringBuilder.append((value != null ? (" (" + value + ")") : ""));
		stringBuilder.append("|<f2> \"];");
		System.out.println(stringBuilder.toString());
		if (left != null) {
			leftid = ++ASTnode.nodeNum;
		}
		if (center != null) {
			centerid = ++ASTnode.nodeNum;
		}
		if (right != null) {
			rightid = ++ASTnode.nodeNum;
		}

		if (left != null) {
			StringBuilder stringBuilder2 = new StringBuilder();
			stringBuilder2.append("\t\"node");
			stringBuilder2.append(id);
			stringBuilder2.append("\":f0 -> \"node");
			stringBuilder2.append(leftid);
			stringBuilder2.append("\":f1;");
			System.out.println(stringBuilder2.toString());
			left.writeGraphviz(leftid);
		}
		if (center != null) {
			StringBuilder stringBuilder2 = new StringBuilder();
			stringBuilder2.append("\t\"node");
			stringBuilder2.append(id);
			stringBuilder2.append("\":f1 -> \"node");
			stringBuilder2.append(centerid);
			stringBuilder2.append("\":f1;");
			System.out.println(stringBuilder2.toString());
			center.writeGraphviz(centerid);
		}
		if (right != null) {
			StringBuilder stringBuilder2 = new StringBuilder();
			stringBuilder2.append("\t\"node");
			stringBuilder2.append(id);
			stringBuilder2.append("\":f2 -> \"node");
			stringBuilder2.append(rightid);
			stringBuilder2.append("\":f1;");
			System.out.println(stringBuilder2.toString());
			right.writeGraphviz(rightid);
		}
	}

}
