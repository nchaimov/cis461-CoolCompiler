import java.text.MessageFormat;
import java.util.Arrays;
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
				return type.substring(0, type.length() - 1);
			else
				throw new CodeGenerationException("Can't dereference a non-pointer.");
		}

		public String typeAndName() {
			return type + " " + name;
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

	public Register nextRegister(String type) {
		return new Register(nextID(), type);
	}

	public String generateCode() {
		output = new StringBuilder();
		id = 0;
		try {

			output.append("@str.format = private constant [3 x i8] c\"%s\\00\"\n");
			output.append("@str.format2 = private constant [3 x i8] c\"%d\\00\"\n");

			log("\n--> Generating class descriptors...");
			generateClassDescriptors();
			log("\n--> Generating functions...");
			generateFunctions();
			log("\n--> Generating main function...");
			writeMainFunction();

			output.append("\ndeclare i32 @printf(i8* noalias, ...)\n");
			output.append("declare noalias i8* @GC_malloc(i64)\n");
			output.append("declare void @GC_init()\n\n");
		} catch (Exception ex) {
			System.err.println("*** Code generation failed!");
			ex.printStackTrace();
			return "";
		}

		return output.toString();
	}

	protected void generateClassDescriptors() {
		output.append("@emptychar = global i8 0\n");
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

			for (Environment.CoolAttribute a : c.attrList) {
				b.append(", ");
				b.append(a.type.getInternalInstanceName());
				b.append("*");
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
				output.append("(").append(m.parent.getInternalInstanceName()).append(" * %this");
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
					Register r = new Register("%this", m.parent.getInternalInstanceName() + "*");
					generateFunctionBody(c, r, m);
				}
				output.append("\n}\n\n");
			}
		}

	}

	protected void generateFunctionBody(Environment.CoolClass cls, Register thiz,
			Environment.CoolMethod m) throws CodeGenerationException,
			Environment.EnvironmentException {
		if (m.node != null) {
			if (m.node.kind != Nodes.METHOD)
				throw new CodeGenerationException(MessageFormat.format(
						"Methods must start with a METHOD node, but found {0} instead.", Util
								.idToName(m.node.kind)));
			log(MessageFormat.format("Generating function body for {0} of {1}", m, cls));
			Register body = generate(cls, thiz, m.node.right);
			output.append("\tret ").append(body.typeAndName()).append("\n");
		}
	}

	protected Register generate(Environment.CoolClass cls, Register thiz, ASTnode n)
			throws CodeGenerationException, Environment.EnvironmentException {
		if (n != null) {
			switch (n.kind) {

			case sym.SEMI: {
				final Register leftVar = generate(cls, thiz, n.left);
				final Register rightVar = generate(cls, thiz, n.right);
				if (rightVar == null)
					return leftVar;
				else
					return rightVar;
			}

			case sym.DOT: {
				Register id = thiz;
				if (n.left != null) {
					id = generate(cls, thiz, n.left);
				}

				Environment.CoolClass curClass = cls;
				if (n.center != null) {
					curClass = env.getClass((String) n.center.value);
				}

				List<Register> mArgs = processMethodArgs(cls, thiz, n.right);
				List<Register> args = new LinkedList<Register>();

				Environment.CoolMethod method = env.lookupMethod(cls, (String) n.value);
				log("Will call method " + method + " at index " + method.index + " of "
						+ method.parent);

				int i = 0;
				for (Register r : mArgs) {
					final String desiredType = method.arguments.get(i).type
							.getInternalInstanceName()
							+ "*";
					final String actualType = r.type;
					if (!desiredType.equals(actualType)) {
						if (actualType.startsWith(desiredType)) {
							Register q = load(r);
							args.add(q);
						}
					} else {
						args.add(r);
					}

				}

				Register methodPtr = getElementPtr(new Register(method.parent
						.getInternalDescriptorName(), method.parent.getInternalClassName() + "*"),
						method.getInternalType() + "*", 0, method.index);
				Register methodInst = load(methodPtr);

				Register cast = bitcast(id, method.parent.getInternalInstanceName() + "*");

				output.append("\t; calling method ").append(method).append("\n");
				Register call = call(methodInst, cast, method.type.getInternalInstanceName() + "*",
						args);

				return call;
			}

			case sym.ID: {
				if (n.value.equals("self"))
					return thiz;
				Register local = env.registers.get((String) n.value);
				if (local != null)
					return local;
				Environment.CoolClass curClass = cls;
				Environment.CoolAttribute a = null;
				while (a == null && curClass != OBJECT) {
					a = curClass.attributes.get(n.value);
					curClass = curClass.parent;
				}
				int index = cls.attrList.indexOf(a) + 1;
				log("Attribute " + a + " is at index " + index + " of class " + a.parent);
				Register idPtr = getElementPtr(thiz, a.type.getInternalInstanceName() + "**", 0,
						index);
				Register idInst = load(idPtr);
				return idInst;
			}

			case sym.STRINGLIT: {
				Register str = instantiate(STRING);
				Register strP = load(str);
				setString(strP, (String) n.value);
				return str;
			}

			case sym.INTLIT: {
				Register i = instantiate(INT);
				Register iP = load(i);
				setInt(iP, Integer.parseInt((String) n.value));
				return i;
			}

			case sym.TRUE: {
				Register b = instantiate(BOOL);
				Register bP = load(b);
				setBool(bP, true);
				return b;
			}

			case sym.FALSE: {
				Register b = instantiate(BOOL);
				return b;
			}

			case sym.PLUS:
			case sym.MINUS:
			case sym.TIMES:
			case sym.DIV: {
				Register arg1 = generate(cls, thiz, n.left);
				Register arg2 = generate(cls, thiz, n.right);
				output.append("\t; *****************************\n");
				Register result = intOpt(n.kind, arg1, arg2);
				output.append("\t; *****************************\n");
				return result;
			}

			default:
				if (debug) {
					log("Unknown node type found in AST:" + Util.idToName(n.kind));
				} else
					throw new CodeGenerationException("Unknown node type found in AST: "
							+ Util.idToName(n.kind));
			}
		}
		return null;
	}

	private Register intOpt(int kind, Register r1, Register r2) throws CodeGenerationException,
			Environment.EnvironmentException {
		Register result = instantiate(INT);
		Register resInst = load(result);

		Register r1Inst = load(r1);
		Register r1IntPtr = getElementPtr(r1Inst, "i32 *", 0, 1);

		Register r2Inst = load(r2);
		Register r2IntPtr = getElementPtr(r2Inst, "i32 *", 0, 1);

		Register r1Int = load(r1IntPtr);
		Register r2Int = load(r2IntPtr);

		Register temp = nextRegister("i32");
		switch (kind) {
		case sym.PLUS:
			output.append("\t").append(temp.name).append(" = add ").append(r1Int.typeAndName())
					.append(", ").append(r2Int.name).append("\n");
			break;
		case sym.MINUS:
			output.append("\t").append(temp.name).append(" = sub ").append(r1Int.typeAndName())
					.append(", ").append(r2Int.name).append("\n");
			break;
		case sym.TIMES:
			output.append("\t").append(temp.name).append(" = mul ").append(r1Int.typeAndName())
					.append(", ").append(r2Int.name).append("\n");
			break;
		case sym.DIV:
			output.append("\t").append(temp.name).append(" = sdiv ").append(r1Int.typeAndName())
					.append(", ").append(r2Int.name).append("\n");
			break;
		}

		Register resultIntPtr = getElementPtr(resInst, "i32 *", 0, 1);
		store(temp, resultIntPtr);

		return result;
	}

	private List<Register> processMethodArgs(Environment.CoolClass cls, Register thiz, ASTnode n)
			throws CodeGenerationException, Environment.EnvironmentException {
		return processMethodArgs(cls, thiz, n, new LinkedList<Register>());
	}

	private List<Register> processMethodArgs(Environment.CoolClass cls, Register thiz, ASTnode n,
			List<Register> l) throws CodeGenerationException, Environment.EnvironmentException {
		if (n != null) {
			switch (n.kind) {
			case sym.COMMA: {
				processMethodArgs(cls, thiz, n.left, l);
				processMethodArgs(cls, thiz, n.right, l);
				break;
			}
			default: {
				l.add(generate(cls, thiz, n));
			}
			}
		}
		return l;
	}

	private void writeMainFunction() throws Environment.EnvironmentException,
			CodeGenerationException {
		output.append("define i32 @main() {\n\tcall void @GC_init()\n");
		final Environment.CoolClass mainClass = env.getClass("Main");
		final Environment.CoolMethod mainMethod = env.lookupMethod(mainClass, "main");
		Register main = instantiate(mainClass);
		Register mainInst = load(main);
		Register mainMethodPtr = getElementPtr(new Register(mainClass.getInternalDescriptorName(),
				mainClass.getInternalClassName() + "*"), mainMethod.getInternalType() + "*", 0,
				mainMethod.index);
		Register mainMethodInst = load(mainMethodPtr);
		// output.append("\t; ").append(mainMethodPtr.typeAndName()).append("\n");
		call(mainMethodInst, mainInst, mainMethod.type.getInternalInstanceName() + "*");
		output.append("\tret i32 0\n}\n\n");
	}

	private Register call(Register methodPtr, Register thiz, String retType, Register... args) {
		return call(methodPtr, thiz, retType, Arrays.asList(args));
	}

	private Register call(Register methodPtr, Register thiz, String retType, List<Register> args) {
		Register call = nextRegister(retType);
		output.append("\t").append(call.name).append(" = call ").append(retType).append(" ")
				.append(methodPtr.name).append("(").append(thiz.typeAndName());
		for (Register r : args) {
			output.append(", ").append(r.typeAndName());
		}
		output.append(")\n");
		return call;
	}

	private Register instantiate(Environment.CoolClass cls) throws CodeGenerationException,
			Environment.EnvironmentException {
		output.append("\t; START instantiating ").append(cls).append("\n");
		Register result = nextRegister(cls.getInternalInstanceName() + "**");
		alloca(result);
		malloc(result, result.derefType());
		Register instance = load(result);
		output.append("\t; setting class pointer\n");
		Register classPtr = getElementPtr(instance, cls.getInternalClassName() + "**", 0, 0);
		Register clazz = new Register(cls.getInternalDescriptorName(), cls.getInternalClassName()
				+ "*");
		store(clazz, classPtr);
		int i = 1;
		for (Environment.CoolAttribute a : cls.attrList) {
			output.append("\t; START attribute ").append(a).append(" of ").append(cls).append("\n");
			Register attrPtr = getElementPtr(instance, a.type.getInternalInstanceName() + "**", 0,
					i);
			Register attrClass;
			if (a.type == STRING || a.type == INT || a.type == BOOL) {
				attrClass = instantiate(a.type);
				Register attrInst = load(attrClass);
				store(attrInst, attrPtr);
			} else {
				store(new Register("null", a.type.getInternalClassName() + "*"), attrPtr);
			}
			i++;
			output.append("\t; END attribute ").append(a).append(" of ").append(cls).append("\n");
		}

		if (cls.builtin) {
			if (cls == STRING) {
				output.append("\t; Setting new String to default (empty)\n");
				setString(instance, "");
			} else if (cls == INT) {
				output.append("\t; Setting new Int to default (0)\n");
				setInt(instance, 0);
			} else if (cls == BOOL) {
				output.append("\t; Setting new Bool to default (false)\n");
				setBool(instance, false);
			}
		}

		int i2 = 1;
		for (Environment.CoolAttribute a : cls.attrList) {
			if (a.node.right != null) {
				output.append("\t; Initialize ").append(a).append(" to introduced value\n");
				Register attrPtr = getElementPtr(instance, a.type.getInternalInstanceName() + "**",
						0, i2);
				Register v = generate(cls, result, a.node.right);
				Register attrInst = load(v);
				store(attrInst, attrPtr);
			}
			i2++;
		}

		output.append("\t; END instantiating ").append(cls).append("\n");

		return result;
	}

	public void setBool(Register b, boolean val) {
		Register boolPtr = getElementPtr(b, "i1 *", 0, 1);
		store(new Register(val ? "1" : "0", "i1"), boolPtr);
	}

	public void setInt(Register i, int val) {
		Register intPtr = getElementPtr(i, "i32 *", 0, 1);
		store(new Register("" + val, "i32"), intPtr);
	}

	public void setString(Register str, String val) throws CodeGenerationException {
		final int len = val.length() + 1;
		val = val.replaceAll("[\"]", "\\\\22");
		Register lenPtr = getElementPtr(str, "i32 *", 0, 1);
		store(new Register("" + len, "i32"), lenPtr);
		Register charPtr = getElementPtr(str, "i8 **", 0, 2);
		Register charArrPtr = mallocCharArray(len);
		output.append("\tstore ").append(charArrPtr.derefType()).append(" c\"").append(val).append(
				"\\00\", ").append(charArrPtr.typeAndName()).append("\n");
		Register castCharArrPtr = bitcast(charArrPtr, "i8 *");
		store(castCharArrPtr, charPtr);
	}

	private Register bitcast(Register r, String type) {
		Register result = nextRegister(type);
		output.append("\t").append(result.name).append(" = bitcast ").append(r.typeAndName())
				.append(" to ").append(type).append("\n");
		return result;
	}

	private void store(Register value, Register dest) {
		output.append("\tstore ").append(value.typeAndName()).append(", ").append(
				dest.typeAndName()).append("\n");
	}

	private Register getElementPtr(Register r, String type, int... args) {
		Register result = nextRegister(type);
		output.append("\t").append(result.name).append(" = getelementptr ").append(r.typeAndName());
		for (int i : args) {
			output.append(", ");
			output.append("i32 ").append(i);
		}
		output.append("\n");
		return result;
	}

	private Register alloca(Register r) throws CodeGenerationException {
		output.append("\t").append(r.name).append(" = alloca ").append(r.derefType()).append("\n");
		return r;
	}

	private Register load(Register from) throws CodeGenerationException {
		Register result = nextRegister(from.derefType());
		output.append("\t").append(result.name).append(" = load ").append(from.typeAndName())
				.append("\n");
		return result;
	}

	private Register mallocCharArray(int len) {
		Register charArr = nextRegister("[" + len + " x i8]*");

		Register call = nextRegister("i8 *");
		output.append("\t").append(call.name).append(" = call noalias i8* @GC_malloc(i64 ").append(
				len).append(")\n");

		output.append("\t").append(charArr.name).append(" = bitcast ").append(call.typeAndName())
				.append(" to ").append(charArr.type).append("\n");

		return charArr;
	}

	private Register malloc(Register r, String type) {
		Register size = nextRegister(type);
		Register cast = nextRegister("i64");
		output.append("\t").append(size.name).append(" = getelementptr ").append(size.type).append(
				" null, i32 1\n");
		output.append("\t").append(cast.name).append(" = ptrtoint ").append(type).append(" ")
				.append(size.name).append(" to ").append(cast.type).append("\n");

		Register call = nextRegister("i8 *");
		output.append("\t").append(call.name).append(" = call noalias i8* @GC_malloc(i64 ").append(
				cast.name).append(")\n");

		Register cast2 = nextRegister(type);
		output.append("\t").append(cast2.name).append(" = bitcast ").append(call.typeAndName())
				.append(" to ").append(cast2.type).append("\n");

		output.append("\tstore ").append(cast2.typeAndName()).append(", ").append(r.type).append(
				" ").append(r.name).append("\n");
		return r;
	}

	private void log(String msg) {
		if (debug) {
			System.err.println(msg);
		}
	}

}
