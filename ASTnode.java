public class ASTnode {
	public final int kind; /* Instead of using subclassing. Example binop */
	public final ASTnode left;
	public final ASTnode center;
	public final ASTnode right;
	public final Object value;
	public Environment.CoolClass type;
	
	protected static int nodeNum = 0;
	
	// Leaf node, with value
	public ASTnode(final int _kind, final Object _value) {
		kind = _kind;
		left = null;
		center = null;
		right = null;
		value = _value;
	}
	
	public ASTnode(final int _kind, final ASTnode _left, final ASTnode _center,
			final ASTnode _right, final Object _value) {
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
		writeGraphviz(0);
		System.out.println("}");
	}
	
	protected void writeGraphviz(final int id) {
		int leftid = -1;
		int centerid = -1;
		int rightid = -1;
		final StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("\tnode");
		stringBuilder.append(id);
		stringBuilder.append(" [label=\"<f0> |{ <f1> ");
		stringBuilder.append(Util.idToName(kind));
		stringBuilder.append("");
		stringBuilder.append((value != null ? (" (" + value + ")") : ""));
		stringBuilder.append("| <ft> ");
		stringBuilder.append(type != null ? type : "");
		stringBuilder.append("}| <f2> \"];");
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
			final StringBuilder stringBuilder2 = new StringBuilder();
			stringBuilder2.append("\t\"node");
			stringBuilder2.append(id);
			stringBuilder2.append("\":f0 -> \"node");
			stringBuilder2.append(leftid);
			stringBuilder2.append("\":f1;");
			System.out.println(stringBuilder2.toString());
			left.writeGraphviz(leftid);
		}
		if (center != null) {
			final StringBuilder stringBuilder2 = new StringBuilder();
			stringBuilder2.append("\t\"node");
			stringBuilder2.append(id);
			stringBuilder2.append("\":ft -> \"node");
			stringBuilder2.append(centerid);
			stringBuilder2.append("\":f1;");
			System.out.println(stringBuilder2.toString());
			center.writeGraphviz(centerid);
		}
		if (right != null) {
			final StringBuilder stringBuilder2 = new StringBuilder();
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
