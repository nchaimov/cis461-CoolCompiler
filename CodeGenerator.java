import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class CodeGenerator {

	public static class CodeGenerationException extends Exception {
		private static final long serialVersionUID = 4478662362211669244L;

		public CodeGenerationException(final String msg) {
			super(msg);
		}
	}

	public static class Register {
		public String name;
		public String type;

		public Register(final String name, final String type) {
			this.name = name;
			this.type = type;
		}

		public String pointerType() {
			return type + "*";
		}

		public String derefType() throws CodeGenerationException {
			if (type.endsWith("*"))
				return type.substring(0, type.length() - 2);
			else
				throw new CodeGenerationException("Can't dereference a non-pointer.");
		}

		@Override
		public String toString() {
			return name;
		}
	}

	protected final Environment.CoolClass OBJECT;
	protected final Environment.CoolClass BOOL;
	protected final Environment.CoolClass INT;
	protected final Environment.CoolClass STRING;
	protected final Environment.CoolClass IO;

	protected int id;

	protected Environment env;

	protected boolean debug;

	protected StringBuilder output;

	public CodeGenerator(Environment env) throws Environment.EnvironmentException {
		this(env, false);
	}

	public CodeGenerator(Environment env, boolean debug) throws Environment.EnvironmentException {
		this.env = env;
		OBJECT = env.getClass("Object");
		BOOL = env.getClass("Bool");
		INT = env.getClass("Int");
		STRING = env.getClass("String");
		IO = env.getClass("IO");
		this.debug = debug;
	}

	public String nextID() {
		return "%i" + id++;
	}

	public String generateCode() {
		output = new StringBuilder();
		id = 0;
		try {

			output.append("@str.format = private constant [3 x i8] c\"%s\\00\"\n");

			log("\n--> Generating class descriptors...");
			generateClassDescriptors();
			log("\n--> Generating functions...");
			generateFunctions();

			output.append("\ndeclare i32 @printf(i8* noalias, ...)\n");
			output.append("\ndeclare noalias i8* @GC_malloc(i64)\n");
		} catch (Exception ex) {
			System.err.println("*** Code generation failed!");
			ex.printStackTrace();
			return "";
		}

		return output.toString();
	}

	protected void generateClassDescriptors() {
		output.append("@emptychar = global i8 0\n");
		output.append("%any = type opaque\n");
		for (Environment.CoolClass c : env.classes.values()) {
			StringBuilder b = new StringBuilder();
			b.append(c.getInternalClassName());
			b.append(" = type { ");
			b.append(c.parent.getInternalClassName());
			b.append("*");
			int index = 1;
			for (Environment.CoolMethod m : c.methods.values()) {
				if (m.parent.builtin && m.builtinImplementation == null) {
					continue;
				}
				m.index = index++;
				b.append(", ");
				b.append(m.getInternalType());
			}
			b.append(" }\n");

			b.append(c.getInternalInstanceName());
			b.append(" = type { ");
			b.append(c.getInternalClassName());
			b.append("*");

			if (c == INT) {
				b.append(", i32");
			} else if (c == STRING) {
				b.append(", i32, i8 *");
			} else if (c == BOOL) {
				b.append(", i1");
			}

			Environment.CoolClass p = c;
			while (p != OBJECT) {
				index = 1;
				for (Environment.CoolAttribute a : p.attributes.values()) {
					c.attrList.add(a);
					if (p == c) {
						a.index = index++;
					}
					b.append(", ");
					b.append(a.type.getInternalInstanceName());
					b.append("*");
				}
				p = p.parent;
			}
			b.append(" }\n");

			// @_Classname = global %__class_Classname { %__class_Parentclass
			// @Parentclass, <method pointers...> }
			b.append(c.getInternalDescriptorName());
			b.append(" = global ");
			b.append(c.getInternalClassName());
			b.append(" {");
			b.append(c.parent.getInternalClassName());
			b.append("* ");
			b.append(c.parent.getInternalDescriptorName());
			for (Environment.CoolMethod m : c.methods.values()) {
				if (m.parent.builtin && m.builtinImplementation == null) {
					continue;
				}
				b.append(", ");
				b.append(m.getInternalType());
				b.append(" ");
				b.append(m.getInternalName());
			}
			b.append(" }\n");

			output.append(b);
		}
		output.append("\n");
	}

	protected void generateFunctions() throws CodeGenerationException,
			Environment.EnvironmentException {

		for (Environment.CoolClass c : env.classes.values()) {
			for (Environment.CoolMethod m : c.methods.values()) {
				if (m.parent.builtin && m.builtinImplementation == null) {
					continue;
				}
				output.append("define ");
				output.append(m.type.getInternalInstanceName());
				output.append(" * ");
				output.append(m.getInternalName());
				output.append("(%any * %this");
				int index = 1;
				for (Environment.CoolAttribute a : m.arguments) {
					a.index = index++;
					output.append(", ");
					output.append(a.type.getInternalInstanceName());
					output.append(" * %v");
					output.append(a.index);
				}
				output.append(") {\n");
				if (m.builtinImplementation != null) {
					output.append(m.builtinImplementation);
				} else {
					generateFunctionBody(c, m);
				}
				output.append("\n}\n\n");
			}
		}

	}

	protected void generateFunctionBody(Environment.CoolClass cls, Environment.CoolMethod m)
			throws CodeGenerationException, Environment.EnvironmentException {
		if (m.node != null) {
			if (m.node.kind != Nodes.METHOD)
				throw new CodeGenerationException(MessageFormat.format(
						"Methods must start with a METHOD node, but found {0} instead.", Util
								.idToName(m.node.kind)));
			log(MessageFormat.format("Generating function body for {0} of {1}", m, cls));
			final Register returnValue = generate(cls, m.node.right);
			output.append("\tret ");
			output.append(m.type.getInternalInstanceName());
			output.append(" * ");
			output.append(returnValue);
		}
	}

	protected Register generate(Environment.CoolClass cls, ASTnode n)
			throws CodeGenerationException, Environment.EnvironmentException {
		if (n != null) {
			switch (n.kind) {

			case sym.SEMI: {
				final Register leftVar = generate(cls, n.left);
				final Register rightVar = generate(cls, n.right);
				if (rightVar == null)
					return leftVar;
				else
					return rightVar;
			}

			case sym.DOT: {
				Environment.CoolClass searchClass = cls;
				if (n.center != null) {
					searchClass = env.getClass((String) n.center.value);
				}
				Environment.CoolMethod m = env.lookupMethod(searchClass, (String) n.value);
				log(MessageFormat.format("Method {0} of class {1} is at index {2} of {3}", m,
						searchClass, m.index, m.parent));
				final Register resultLoc = stackAlloc(m.type, "return value");
				final List<Register> argAddrs = new ArrayList<Register>(m.arguments.size());
				for (Environment.CoolAttribute a : m.arguments) {
					argAddrs.add(stackAlloc(a.type, a.toString()));
				}
				final List<Register> argVals = processMethodArgs(cls, n.right);
				return resultLoc;
			}

			case sym.STRINGLIT: {
				final Register strId = heapAlloc(STRING, "string literal");
				instantiateObject(STRING, strId);
				break;
			}

			default:
				throw new CodeGenerationException("Unknown node type found in AST: "
						+ Util.idToName(n.kind));
			}
		}
		return null;
	}

	public Register getElementPtr(Register in, int... indexes) {
		return null;
	}

	protected Register heapAlloc(Environment.CoolClass cls, String comment) {
		final Register reg = stackAlloc(cls, comment != null ? comment + "(start)" : "start", 1);

		String size = nextID();
		String size2 = nextID();

		output.append("\t");
		output.append(size).append(" = getelementptr ").append(cls.getInternalInstanceName());
		output.append(" * null, i64 1\n");

		output.append("\t");
		output.append(size2).append(" = ptrtoint ").append(cls.getInternalInstanceName());
		output.append(" * ").append(size).append(" to i64\n");

		String call = nextID();

		output.append("\t").append(call);
		output.append(" = call noalias i8* @GC_malloc(i64 ").append(size2).append(")\n");

		String conv = nextID();

		output.append("\t");
		output.append(conv).append(" = bitcast i8 * ").append(call).append(" to ").append(
				cls.getInternalInstanceName());
		output.append(" *\n");

		output.append("\tstore ").append(cls.getInternalInstanceName()).append(" * ").append(conv);
		output.append(", ").append(cls.getInternalInstanceName()).append(" ** ").append(reg.name)
				.append("\n");

		return reg;
	}

	protected List<Register> processMethodArgs(Environment.CoolClass cls, ASTnode n)
			throws CodeGenerationException, Environment.EnvironmentException {
		return processMethodArgs(cls, n, new LinkedList<Register>());
	}

	protected List<Register> processMethodArgs(Environment.CoolClass cls, ASTnode n,
			List<Register> l) throws CodeGenerationException, Environment.EnvironmentException {
		if (n != null) {
			if (n.kind == sym.COMMA) {
				processMethodArgs(cls, n.left, l);
				processMethodArgs(cls, n.right, l);
			} else {
				l.add(generate(cls, n));
			}
		}
		return l;
	}

	protected Register stackAlloc(String type, String comment) {
		final String name = nextID();
		output.append("\t");
		output.append(name);
		output.append(" = alloca ");
		output.append(type);
		if (comment != null && !comment.isEmpty()) {
			output.append("\t\t; ");
			output.append(comment);
		}
		output.append(" \n");

		return new Register(name, type + "*");
	}

	protected Register stackAlloc(Environment.CoolClass cls, String comment) {
		return stackAlloc(cls, comment, 0);
	}

	protected Register stackAlloc(Environment.CoolClass cls, String comment, int ptrLevel) {
		String s = "";
		for (int i = 0; i < ptrLevel; ++i) {
			s += "*";
		}
		final String type = cls.getInternalInstanceName() + s;
		return stackAlloc(type, comment);
	}

	protected int getAttrIndex(Environment.CoolClass cls, Environment.CoolAttribute attr)
			throws CodeGenerationException {
		final int result = cls.attrList.indexOf(attr) + 1;
		if (result < 0)
			throw new CodeGenerationException("Attempted to get nonexistent attribute " + attr
					+ " of class " + cls);
		return result;
	}

	protected Register getAttrPtr(Register name, Environment.CoolClass cls,
			Environment.CoolAttribute attr) throws CodeGenerationException {
		final Register addr = stackAlloc(attr.type, MessageFormat.format("ptr to {0} of {1}", attr,
				cls));
		final int index = getAttrIndex(cls, attr);

		final String load = nextID();
		output.append("\t").append(load).append(" = load ").append(cls.getInternalInstanceName())
				.append(" ** ").append(name).append("\n");

		final String elptr = nextID();
		output.append("\t").append(elptr).append(" = getelementptr inbounds ");
		output.append(cls.getInternalInstanceName()).append(" * ").append(load).append(
				", i32 0, i32 ");
		output.append(index).append("\n");

		output.append("\tstore ").append(attr.type.getInternalInstanceName()).append(" * ").append(
				elptr).append(", ").append(attr.type.getInternalInstanceName()).append(" ** ")
				.append(addr).append("\n");

		return addr;
	}

	protected void instantiateString(Register name) {
		final String load = nextID();
		final String lenAddr = nextID();
		final String charAddr = nextID();

		output.append("\t").append(load).append(" = load ")
				.append(STRING.getInternalInstanceName()).append(" ** ").append(name).append("\n");

		output.append("\t").append(lenAddr).append(" = getelementptr inbounds ");
		output.append(STRING.getInternalInstanceName()).append(" * ").append(load).append(
				", i32 0, i32 1").append("\n");

		output.append("\t").append(charAddr).append(" = getelementptr inbounds ");
		output.append(STRING.getInternalInstanceName()).append(" * ").append(load).append(
				", i32 0, i32 2").append("\n");

		output.append("\tstore i32 0, i32 * ").append(lenAddr).append("\t\t; initial strlen\n");

		output.append("\tstore i8 * null, i8 ** ").append(charAddr)
				.append("\t\t; initial strlen\n");

	}

	protected void setStringValue(String name, String value) {

	}

	protected String setParent(Register name, Environment.CoolClass cls) {
		final String result = nextID();

		output.append("\t");
		output.append(result).append(" = alloca ").append(cls.parent.getInternalClassName())
				.append(" * \t\t; ptr to parent ptr\n");

		final String load = nextID();
		output.append("\t").append(load).append(" = load ").append(cls.getInternalInstanceName())
				.append(" ** ").append(name).append("\n");

		final String getAddr = nextID();

		output.append("\t");
		output.append(getAddr).append(" = getelementptr inbounds ").append(
				cls.getInternalInstanceName()).append(" * ");
		output.append(load).append(", i32 0, i32 0\n");

		output.append("\tstore ").append(cls.parent.getInternalClassName()).append(" * ").append(
				cls.parent.getInternalDescriptorName());
		output.append(", ").append(cls.parent.getInternalClassName()).append(" ** ")
				.append(getAddr);
		output.append("\n");
		return result;
	}

	protected void instantiateObject(Environment.CoolClass c, Register reg)
			throws CodeGenerationException {
		setParent(reg, c);
		if (c == STRING) {
			instantiateString(reg);
		} else {
			for (Environment.CoolAttribute a : c.attrList) {
				final Register ptr = getAttrPtr(reg, c, a);
				// TODO init objects
			}
		}
	}

	protected void writeMainFunction() {

	}

	private void log(String msg) {
		if (debug) {
			System.err.println(msg);
		}
	}

}
