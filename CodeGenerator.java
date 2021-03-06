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
			if (type.endsWith("*")) {
				return type.substring(0, type.length() - 1);
			} else {
				throw new CodeGenerationException(
						"Can't dereference a non-pointer.");
			}
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
	protected int label;
	
	protected final Environment env;
	
	protected final boolean debug;
	
	protected StringBuilder output;
	
	public CodeGenerator(final Environment env)
			throws Environment.EnvironmentException {
		this(env, false);
	}
	
	public CodeGenerator(final Environment env, final boolean debug)
			throws Environment.EnvironmentException {
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
	
	public Register nextRegister(final String type) {
		return new Register(nextID(), type);
	}
	
	public String nextLabel() {
		return "Label" + label++;
	}
	
	public String generateCode() {
		output = new StringBuilder();
		id = 0;
		label = 0;
		try {
			
			output
					.append("@str.format = private constant [3 x i8] c\"%s\\00\"\n");
			output
					.append("@str.format2 = private constant [3 x i8] c\"%d\\00\"\n");
			
			log("\n--> Generating class descriptors...");
			generateClassDescriptors();
			log("\n--> Generating functions...");
			generateFunctions();
			log("\n--> Generating main function...");
			writeMainFunction();
			
			output.append("\ndeclare i32 @printf(i8* noalias, ...)\n");
			output.append("declare noalias i8* @GC_malloc(i64)\n");
			output.append("declare void @GC_init()\n");
			output.append("declare i32 @strcmp(i8*, i8*)\n\n");
		} catch (final Exception ex) {
			System.err.println("*** Code generation failed!");
			ex.printStackTrace();
			return "";
		}
		
		return output.toString();
	}
	
	protected void generateClassDescriptors() {
		output.append("@emptychar = global i8 0\n");
		for (final Environment.CoolClass c : env.classes.values()) {
			final StringBuilder b = new StringBuilder();
			b.append(c.getInternalClassName());
			b.append(" = type { ");
			b.append(c.parent.getInternalClassName());
			b.append("*");
			int index = 1;
			for (final Environment.CoolMethod m : c.methods.values()) {
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
			
			for (final Environment.CoolAttribute a : c.attrList) {
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
			for (final Environment.CoolMethod m : c.methods.values()) {
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
		
		for (final Environment.CoolClass c : env.classes.values()) {
			for (final Environment.CoolMethod m : c.methods.values()) {
				if (m.parent.builtin && m.builtinImplementation == null) {
					continue;
				}
				output.append("define ");
				output.append(m.type.getInternalInstanceName());
				output.append(" * ");
				output.append(m.getInternalName());
				output.append("(").append(m.parent.getInternalInstanceName())
						.append(" * %this");
				int index = 1;
				for (final Environment.CoolAttribute a : m.arguments) {
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
					final Register r = new Register("%this", m.parent
							.getInternalInstanceName()
							+ "*");
					generateFunctionBody(c, r, m);
				}
				output.append("\n}\n\n");
			}
		}
		
	}
	
	protected void generateFunctionBody(final Environment.CoolClass cls,
			final Register thiz, final Environment.CoolMethod m)
			throws CodeGenerationException, Environment.EnvironmentException {
		if (m.node != null) {
			if (m.node.kind != Nodes.METHOD) {
				throw new CodeGenerationException(
						MessageFormat
								.format(
										"Methods must start with a METHOD node, but found {0} instead.",
										Util.idToName(m.node.kind)));
			}
			log(MessageFormat.format("Generating function body for {0} of {1}",
					m, cls));
			Register body = generate(cls, thiz, m.node.right);
			if (!body.type.equals(m.type.getInternalInstanceName() + "*")) {
				body = bitcast(body, m.type.getInternalInstanceName() + "*");
			}
			output.append("\tret ").append(body.typeAndName()).append("\n");
		}
	}
	
	protected void comment(final String comment) {
		output.append("\t; ").append(comment).append("\n");
	}
	
	protected Register generate(final Environment.CoolClass cls, Register thiz,
			final ASTnode n) throws CodeGenerationException,
			Environment.EnvironmentException {
		thiz = makeSinglePtr(thiz);
		if (n != null) {
			switch (n.kind) {
			
			case sym.TRUE: {
				comment("START True literal");
				final Register b = instantiate(BOOL);
				final Register bP = load(b);
				setBool(bP, true);
				comment("END True literal");
				return b;
			}
				
			case sym.FALSE: {
				comment("START False literal");
				final Register b = instantiate(BOOL);
				comment("END False literal");
				return b;
			}
				
			case sym.INTLIT: {
				comment(MessageFormat
						.format("START Int literal ({0})", n.value));
				final Register i = instantiate(INT);
				final Register iP = load(i);
				setInt(iP, Integer.parseInt((String) n.value));
				comment(MessageFormat.format("END Int literal ({0})", n.value));
				return i;
			}
				
			case sym.STRINGLIT: {
				final String v = ((String) n.value).replaceAll("[^A-Za-z0-9]",
						"");
				comment(MessageFormat.format("START String literal ({0})", v));
				final Register str = instantiate(STRING);
				final Register strP = load(str);
				setString(strP, (String) n.value);
				comment(MessageFormat.format("END String literal ({0})", v));
				return str;
			}
				
			case sym.ID: {
				if (n.value.equals("self")) {
					return thiz;
				}
				comment(MessageFormat.format("START ID load ({0})", n.value));
				final Register local = env.registers.get((String) n.value);
				if (local != null) {
					return local;
				}
				Environment.CoolClass curClass = cls;
				Environment.CoolAttribute a = null;
				while (a == null && curClass != OBJECT) {
					a = curClass.attributes.get(n.value);
					curClass = curClass.parent;
				}
				final int index = cls.attrList.indexOf(a) + 1;
				log("Attribute " + a + " is at index " + index + " of class "
						+ a.parent);
				final Register idPtr = getElementPtr(thiz, a.type
						.getInternalInstanceName()
						+ "**", 0, index);
				final Register idInst = load(idPtr);
				comment(MessageFormat.format("END ID load ({0})", n.value));
				return idInst;
			}
				
			case sym.ASSIGN: {
				comment("Start ASSIGN");
				
				// Get attribute location
				final String id = (String) n.left.value;
				final Register local = env.registers.get(id);
				if (local != null) {
					return local;
				}
				Environment.CoolClass curClass = cls;
				Environment.CoolAttribute a = null;
				while (a == null && curClass != OBJECT) {
					a = curClass.attributes.get(id);
					curClass = curClass.parent;
				}
				final int index = cls.attrList.indexOf(a) + 1;
				log("Attribute " + a + " is at index " + index + " of class "
						+ a.parent);
				final Register thizInst = makeSinglePtr(thiz);
				final Register idPtr = getElementPtr(thizInst, a.type
						.getInternalInstanceName()
						+ "**", 0, index);
				
				final Register rightSide = generate(cls, thiz, n.right);
				final Register rightInst = makeSinglePtr(rightSide);
				
				store(rightInst, idPtr);
				
				comment("End ASSIGN");
				return rightSide;
			}
				
			case sym.NEW: {
				final Register newObj = instantiate(env
						.getClass((String) n.value));
				return newObj;
			}
				
			case sym.DOT: {
				comment(MessageFormat
						.format("START Method call ({0})", n.value));
				Register id = thiz;
				Environment.CoolClass curClass = cls;
				if (n.left != null) {
					id = generate(cls, thiz, n.left);
					curClass = n.left.type;
					log(MessageFormat.format(
							"Target of method invocation is {0} of type {1}",
							id.typeAndName(), cls));
				}
				
				if (n.center != null) {
					curClass = env.getClass((String) n.center.value);
					log(MessageFormat.format(
							"Will statically use type {0} for method call.",
							curClass));
				}
				
				final List<Register> mArgs = processMethodArgs(cls, thiz,
						n.right);
				final List<Register> args = new LinkedList<Register>();
				
				log("Looking up method " + n.value + " in " + curClass);
				final Environment.CoolMethod method = env.lookupMethod(
						curClass, (String) n.value);
				log("Will call method " + method + " at index " + method.index
						+ " of " + method.parent);
				
				final int i = 0;
				for (final Register r : mArgs) {
					final String desiredType = method.arguments.get(i).type
							.getInternalInstanceName()
							+ "*";
					final String actualType = r.type;
					if (!desiredType.equals(actualType)) {
						if (actualType.startsWith(desiredType)) {
							final Register q = load(r);
							args.add(q);
						}
					} else {
						args.add(r);
					}
					
				}
				
				comment("Get pointer to class of object");
				final Register castId = bitcast(id, curClass
						.getInternalInstanceName()
						+ "*");
				final Register idClassPtr = getElementPtr(castId, curClass
						.getInternalClassName()
						+ "**", 0, 0);
				// Register clsCast = bitcast(idClassPtr,
				// method.parent.getInternalClassName() + "**");
				final Register idClass = makeSinglePtr(idClassPtr);
				comment("getting method " + method + " of " + method.parent);
				final Register methodPtr = getElementPtr(idClass, method
						.getInternalType()
						+ "*", 0, method.index);
				final Register methodInst = load(methodPtr);
				
				final Register cast = bitcast(id, method.parent
						.getInternalInstanceName()
						+ "*");
				
				output.append("\t; calling method ").append(method)
						.append("\n");
				final Register call = call(methodInst, cast, method.type
						.getInternalInstanceName()
						+ "*", args);
				
				comment(MessageFormat.format("END Method call ({0})", n.value));
				return call;
			}
				
			case sym.IF: {
				comment("START If statement");
				final Register cond = generate(cls, thiz, n.left);
				final Register condLoad = makeSinglePtr(cond);
				final Register condPtr = getElementPtr(condLoad, "i1 *", 0, 1);
				final Register condVal = load(condPtr);
				final String trueBranch = nextLabel();
				final String falseBranch = nextLabel();
				final String doneBranch = nextLabel();
				branch(condVal, trueBranch, falseBranch);
				writeLabel(trueBranch);
				Register trueResult = generate(cls, thiz, n.center);
				trueResult = makeSinglePtr(trueResult);
				if (!trueResult.type.equals(n.type.getInternalInstanceName()
						+ "*")) {
					trueResult = bitcast(trueResult, n.type
							.getInternalInstanceName()
							+ "*");
				}
				branch(doneBranch);
				writeLabel(falseBranch);
				Register falseResult = generate(cls, thiz, n.right);
				falseResult = makeSinglePtr(falseResult);
				if (!falseResult.type.equals(n.type.getInternalInstanceName()
						+ "*")) {
					falseResult = bitcast(falseResult, n.type
							.getInternalInstanceName()
							+ "*");
				}
				branch(doneBranch);
				writeLabel(doneBranch);
				final Register ifResult = nextRegister(n.type
						.getInternalInstanceName()
						+ "*");
				output.append("\t").append(ifResult.name).append(" = phi ")
						.append(ifResult.type).append(" [ ").append(
								trueResult.name).append(", %").append(
								trueBranch).append(" ], [ ").append(
								falseResult.name).append(", %").append(
								falseBranch).append(" ]\n");
				comment("END If statement");
				return ifResult;
			}
				
			case sym.SEMI: {
				final Register leftVar = generate(cls, thiz, n.left);
				final Register rightVar = generate(cls, thiz, n.right);
				if (rightVar == null) {
					return leftVar;
				} else {
					return rightVar;
				}
			}
				
			case sym.LET: {
				final int numInts = processLetIntroductions(cls, thiz, n.left);
				final Register result = generate(cls, thiz, n.right);
				for (int i = 0; i < numInts; ++i) {
					env.registers.pop();
				}
				return result;
			}
				// TODO CASE
				
			case sym.WHILE: {
				comment("START While loop");
				final String loopHead = nextLabel();
				final String loopTest = nextLabel();
				final String afterLoop = nextLabel();
				branch(loopTest);
				writeLabel(loopHead);
				@SuppressWarnings("unused")
				final Register loop = generate(cls, thiz, n.right);
				branch(loopTest);
				writeLabel(loopTest);
				final Register cond = generate(cls, thiz, n.left);
				final Register condLoad = load(cond);
				final Register condPtr = getElementPtr(condLoad, "i1 *", 0, 1);
				final Register condVal = load(condPtr);
				branch(condVal, loopHead, afterLoop);
				writeLabel(afterLoop);
				final Register resultPtr = nextRegister(OBJECT
						.getInternalInstanceName()
						+ "**");
				alloca(resultPtr);
				store(new Register("null", OBJECT.getInternalInstanceName()
						+ "*"), resultPtr);
				final Register result = load(resultPtr);
				comment("END While loop");
				return result;
			}
				
			case sym.ISVOID: {
				comment("START isvoid");
				final Register value = generate(cls, thiz, n.left);
				final Register resVal = nextRegister("i1");
				output.append("\t").append(resVal).append(" = icmp eq ")
						.append(value.typeAndName()).append(", null").append(
								"\n");
				comment("END isvoid");
				final Register resultPtr = instantiate(BOOL);
				final Register result = load(resultPtr);
				final Register boolPtr = getElementPtr(result, "i1 *", 0, 1);
				store(resVal, boolPtr);
				
				return resultPtr;
			}
				
			case sym.NOT: {
				comment("START not");
				final Register cond = generate(cls, thiz, n.left);
				final Register condLoad = makeSinglePtr(cond);
				final Register condPtr = getElementPtr(condLoad, "i1 *", 0, 1);
				final Register condVal = load(condPtr);
				
				final Register resVal = nextRegister("i1");
				output.append("\t").append(resVal).append(" = icmp ne ")
						.append(condVal.typeAndName()).append(", 0\n");
				
				final Register resultPtr = instantiate(BOOL);
				final Register result = load(resultPtr);
				final Register boolPtr = getElementPtr(result, "i1 *", 0, 1);
				store(resVal, boolPtr);
				
				comment("END not");
				return resultPtr;
			}
				
			case sym.LEQ:
			case sym.LT: {
				comment("START less-than comparison");
				final Register int1 = generate(cls, thiz, n.left);
				final Register int1load = makeSinglePtr(int1);
				final Register int1Ptr = getElementPtr(int1load, "i32 *", 0, 1);
				final Register int1Val = load(int1Ptr);
				
				final Register int2 = generate(cls, thiz, n.right);
				final Register int2load = makeSinglePtr(int2);
				final Register int2Ptr = getElementPtr(int2load, "i32 *", 0, 1);
				final Register int2Val = load(int2Ptr);
				
				String op;
				if (n.kind == sym.LEQ) {
					op = "sle";
				} else {
					op = "slt";
				}
				
				final Register resVal = nextRegister("i1");
				output.append("\t").append(resVal).append(" = icmp ")
						.append(op).append(" ").append(int1Val.typeAndName())
						.append(", ").append(int2Val.name).append("\n");
				
				final Register resultPtr = instantiate(BOOL);
				final Register result = load(resultPtr);
				final Register boolPtr = getElementPtr(result, "i1 *", 0, 1);
				store(resVal, boolPtr);
				
				comment("END less-than comparison");
				return resultPtr;
			}
				
			case sym.PLUS:
			case sym.MINUS:
			case sym.TIMES:
			case sym.DIV: {
				comment(MessageFormat.format(
						"START Arithmetic operation ({0})", Util
								.idToName(n.kind)));
				final Register arg1 = generate(cls, thiz, n.left);
				final Register arg2 = generate(cls, thiz, n.right);
				final Register result = intOpt(n.kind, arg1, arg2);
				comment(MessageFormat.format("END Arithmetic operation ({0})",
						Util.idToName(n.kind)));
				return result;
			}
				
			case sym.EQ: {
				if (n.left.type == INT) {
					comment("START integer equality comparison");
					final Register int1 = generate(cls, thiz, n.left);
					final Register int1load = makeSinglePtr(int1);
					final Register int1Ptr = getElementPtr(int1load, "i32 *",
							0, 1);
					final Register int1Val = load(int1Ptr);
					
					final Register int2 = generate(cls, thiz, n.right);
					final Register int2load = makeSinglePtr(int2);
					final Register int2Ptr = getElementPtr(int2load, "i32 *",
							0, 1);
					final Register int2Val = load(int2Ptr);
					
					final Register resVal = nextRegister("i1");
					output.append("\t").append(resVal).append(" = icmp eq ")
							.append(" ").append(int1Val.typeAndName()).append(
									", ").append(int2Val.name).append("\n");
					final Register resultPtr = instantiate(BOOL);
					final Register result = load(resultPtr);
					final Register boolPtr = getElementPtr(result, "i1 *", 0, 1);
					store(resVal, boolPtr);
					
					comment("END integer equality comparison");
					return resultPtr;
				} else if (n.left.type == BOOL) {
					comment("START bool equality comparison");
					final Register int1 = generate(cls, thiz, n.left);
					final Register int1load = makeSinglePtr(int1);
					final Register int1Ptr = getElementPtr(int1load, "i1 *", 0,
							1);
					final Register int1Val = load(int1Ptr);
					
					final Register int2 = generate(cls, thiz, n.right);
					final Register int2load = makeSinglePtr(int2);
					final Register int2Ptr = getElementPtr(int2load, "i1 *", 0,
							1);
					final Register int2Val = load(int2Ptr);
					
					final Register resVal = nextRegister("i1");
					output.append("\t").append(resVal).append(" = icmp eq ")
							.append(" ").append(int1Val.typeAndName()).append(
									", ").append(int2Val.name).append("\n");
					final Register resultPtr = instantiate(BOOL);
					final Register result = load(resultPtr);
					final Register boolPtr = getElementPtr(result, "i1 *", 0, 1);
					store(resVal, boolPtr);
					
					comment("END bool equality comparison");
					return resultPtr;
				} else if (n.left.type == STRING) {
					comment("START string equality comparison");
					final Register int1 = generate(cls, thiz, n.left);
					final Register int1load = makeSinglePtr(int1);
					final Register int1Ptr = getElementPtr(int1load, "i8 **",
							0, 2);
					final Register int1Val = load(int1Ptr);
					
					final Register int2 = generate(cls, thiz, n.right);
					final Register int2load = makeSinglePtr(int2);
					final Register int2Ptr = getElementPtr(int2load, "i8 **",
							0, 2);
					final Register int2Val = load(int2Ptr);
					
					final Register call = nextRegister("i32");
					
					output.append("\t").append(call.name).append(
							" = call i32 @strcmp(").append(
							int1Val.typeAndName()).append(", ").append(
							int2Val.typeAndName()).append(")\n");
					
					final Register resVal = nextRegister("i1");
					output.append("\t").append(resVal).append(" = icmp eq ")
							.append(" ").append(call.typeAndName()).append(
									", 0\n");
					
					final Register resultPtr = instantiate(BOOL);
					final Register result = load(resultPtr);
					final Register boolPtr = getElementPtr(result, "i1 *", 0, 1);
					store(resVal, boolPtr);
					
					comment("END string equality comparison");
					return resultPtr;
				} else {
					comment("START object equality comparison");
					final Register arg1 = generate(cls, thiz, n.left);
					final Register arg2 = generate(cls, thiz, n.right);
					
					final Register resVal = nextRegister("i1");
					output.append("\t").append(resVal).append(" = icmp eq ")
							.append(" ").append(arg1.typeAndName())
							.append(", ").append(arg2.name).append("\n");
					
					final Register resultPtr = instantiate(BOOL);
					final Register result = load(resultPtr);
					final Register boolPtr = getElementPtr(result, "i1 *", 0, 1);
					store(resVal, boolPtr);
					comment("END object equality comparison");
					return resultPtr;
				}
			}
				
			case sym.NEG: {
				comment("START negation");
				final Register arg1 = generate(cls, thiz, n.left);
				final Register zeroPtr = instantiate(INT);
				final Register zero = load(zeroPtr);
				setInt(zero, 0);
				final Register result = intOpt(sym.MINUS, zero, arg1);
				comment("END negation");
				return result;
			}
				
			default:
				if (debug) {
					log("Unknown node type found in AST:"
							+ Util.idToName(n.kind));
				} else {
					throw new CodeGenerationException(
							"Unknown node type found in AST: "
									+ Util.idToName(n.kind));
				}
			}
		}
		return null;
	}
	
	private int processLetIntroductions(final Environment.CoolClass cls,
			final Register thiz, final ASTnode node)
			throws CodeGenerationException, Environment.EnvironmentException {
		return processLetIntroductions(cls, thiz, node, 0);
	}
	
	private int processLetIntroductions(final Environment.CoolClass cls,
			final Register thiz, final ASTnode node, int numVars)
			throws CodeGenerationException, Environment.EnvironmentException {
		
		if (node != null) {
			switch (node.kind) {
			case sym.COMMA: {
				numVars += processLetIntroductions(cls, thiz, node.left, 0);
				numVars += processLetIntroductions(cls, thiz, node.right, 0);
				break;
			}
			case sym.ASSIGN: {
				numVars += 1;
				final String name = (String) node.left.left.value;
				final Environment.CoolClass type = env
						.getClass((String) node.left.right.value);
				final Register letVar = instantiate(type);
				if (node.right != null) {
					final Register letValuePtr = generate(cls, thiz, node.right);
					final Register letValue = load(letValuePtr);
					store(letValue, letVar);
				}
				log(MessageFormat.format("Pushing {0} for {1}", letVar
						.typeAndName(), name));
				env.registers.push(name, letVar);
				break;
			}
			default:
				throw new CodeGenerationException(
						"Invalid node type in Let introductions: "
								+ Util.idToName(node.kind));
			}
		}
		
		return numVars;
	}
	
	protected Register makeSinglePtr(final Register ptr)
			throws CodeGenerationException {
		Register result = ptr;
		while (result.type.endsWith("**")) {
			result = load(result);
		}
		return result;
	}
	
	protected void branch(final String label) {
		output.append("\tbr label %").append(label).append("\n");
	}
	
	protected void branch(final Register cond, final String trueBranch,
			final String falseBranch) {
		output.append("\tbr ").append(cond.typeAndName()).append(", label %")
				.append(trueBranch).append(", label %").append(falseBranch)
				.append("\n");
	}
	
	protected void writeLabel(final String label) {
		output.append(label).append(":\n");
	}
	
	private Register intOpt(final int kind, final Register r1, final Register r2)
			throws CodeGenerationException, Environment.EnvironmentException {
		final Register result = instantiate(INT);
		final Register resInst = load(result);
		
		comment("Getting first parameter to binop");
		final Register r1Inst = makeSinglePtr(r1);
		final Register r1IntPtr = getElementPtr(r1Inst, "i32 *", 0, 1);
		
		comment("Getting second parameter to binop");
		final Register r2Inst = makeSinglePtr(r2);
		final Register r2IntPtr = getElementPtr(r2Inst, "i32 *", 0, 1);
		
		final Register r1Int = load(r1IntPtr);
		final Register r2Int = load(r2IntPtr);
		
		final Register temp = nextRegister("i32");
		switch (kind) {
		case sym.PLUS:
			output.append("\t").append(temp.name).append(" = add ").append(
					r1Int.typeAndName()).append(", ").append(r2Int.name)
					.append("\n");
			break;
		case sym.MINUS:
			output.append("\t").append(temp.name).append(" = sub ").append(
					r1Int.typeAndName()).append(", ").append(r2Int.name)
					.append("\n");
			break;
		case sym.TIMES:
			output.append("\t").append(temp.name).append(" = mul ").append(
					r1Int.typeAndName()).append(", ").append(r2Int.name)
					.append("\n");
			break;
		case sym.DIV:
			output.append("\t").append(temp.name).append(" = sdiv ").append(
					r1Int.typeAndName()).append(", ").append(r2Int.name)
					.append("\n");
			break;
		}
		
		final Register resultIntPtr = getElementPtr(resInst, "i32 *", 0, 1);
		store(temp, resultIntPtr);
		
		return result;
	}
	
	private List<Register> processMethodArgs(final Environment.CoolClass cls,
			final Register thiz, final ASTnode n)
			throws CodeGenerationException, Environment.EnvironmentException {
		return processMethodArgs(cls, thiz, n, new LinkedList<Register>());
	}
	
	private List<Register> processMethodArgs(final Environment.CoolClass cls,
			final Register thiz, final ASTnode n, final List<Register> l)
			throws CodeGenerationException, Environment.EnvironmentException {
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
		final Environment.CoolMethod mainMethod = env.lookupMethod(mainClass,
				"main");
		final Register main = instantiate(mainClass);
		final Register mainInst = load(main);
		final Register mainMethodPtr = getElementPtr(new Register(mainClass
				.getInternalDescriptorName(), mainClass.getInternalClassName()
				+ "*"), mainMethod.getInternalType() + "*", 0, mainMethod.index);
		final Register mainMethodInst = load(mainMethodPtr);
		// output.append("\t; ").append(mainMethodPtr.typeAndName()).append("\n");
		call(mainMethodInst, mainInst, mainMethod.type
				.getInternalInstanceName()
				+ "*");
		output.append("\tret i32 0\n}\n\n");
	}
	
	private Register call(final Register methodPtr, final Register thiz,
			final String retType, final Register... args) {
		return call(methodPtr, thiz, retType, Arrays.asList(args));
	}
	
	private Register call(final Register methodPtr, final Register thiz,
			final String retType, final List<Register> args) {
		final Register call = nextRegister(retType);
		output.append("\t").append(call.name).append(" = call ")
				.append(retType).append(" ").append(methodPtr.name).append("(")
				.append(thiz.typeAndName());
		for (final Register r : args) {
			output.append(", ").append(r.typeAndName());
		}
		output.append(")\n");
		return call;
	}
	
	private Register instantiate(final Environment.CoolClass cls)
			throws CodeGenerationException, Environment.EnvironmentException {
		output.append("\t; START instantiating ").append(cls).append("\n");
		final Register result = nextRegister(cls.getInternalInstanceName()
				+ "**");
		alloca(result);
		malloc(result, result.derefType());
		final Register instance = load(result);
		output.append("\t; setting class pointer\n");
		final Register classPtr = getElementPtr(instance, cls
				.getInternalClassName()
				+ "**", 0, 0);
		final Register clazz = new Register(cls.getInternalDescriptorName(),
				cls.getInternalClassName() + "*");
		store(clazz, classPtr);
		int i = 1;
		for (final Environment.CoolAttribute a : cls.attrList) {
			output.append("\t; START attribute ").append(a).append(" of ")
					.append(cls).append("\n");
			final Register attrPtr = getElementPtr(instance, a.type
					.getInternalInstanceName()
					+ "**", 0, i);
			Register attrClass;
			if (a.type == STRING || a.type == INT || a.type == BOOL) {
				attrClass = instantiate(a.type);
				final Register attrInst = load(attrClass);
				store(attrInst, attrPtr);
			} else {
				store(new Register("null", a.type.getInternalInstanceName()
						+ "*"), attrPtr);
			}
			i++;
			output.append("\t; END attribute ").append(a).append(" of ")
					.append(cls).append("\n");
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
		for (final Environment.CoolAttribute a : cls.attrList) {
			if (a.node.right != null) {
				output.append("\t; Initialize ").append(a).append(
						" to introduced value\n");
				final Register attrPtr = getElementPtr(instance, a.type
						.getInternalInstanceName()
						+ "**", 0, i2);
				final Register v = generate(cls, result, a.node.right);
				Register attrInst = makeSinglePtr(v);
				if (!(attrInst.type + "*").equals(attrPtr.type)) {
					attrInst = bitcast(attrInst, attrPtr.derefType());
				}
				store(attrInst, attrPtr);
			}
			i2++;
		}
		
		output.append("\t; END instantiating ").append(cls).append("\n");
		
		return result;
	}
	
	public void setBool(final Register b, final boolean val) {
		final Register boolPtr = getElementPtr(b, "i1 *", 0, 1);
		store(new Register(val ? "1" : "0", "i1"), boolPtr);
	}
	
	public void setInt(final Register i, final int val) {
		final Register intPtr = getElementPtr(i, "i32 *", 0, 1);
		store(new Register("" + val, "i32"), intPtr);
	}
	
	public void setString(final Register str, String val)
			throws CodeGenerationException {
		final int len = val.length() + 1;
		val = val.replaceAll("[\"]", "\\\\22");
		final Register lenPtr = getElementPtr(str, "i32 *", 0, 1);
		store(new Register("" + len, "i32"), lenPtr);
		final Register charPtr = getElementPtr(str, "i8 **", 0, 2);
		final Register charArrPtr = mallocCharArray(len);
		output.append("\tstore ").append(charArrPtr.derefType()).append(" c\"")
				.append(val).append("\\00\", ")
				.append(charArrPtr.typeAndName()).append("\n");
		final Register castCharArrPtr = bitcast(charArrPtr, "i8 *");
		store(castCharArrPtr, charPtr);
	}
	
	private Register bitcast(final Register r, final String type) {
		final Register result = nextRegister(type);
		output.append("\t").append(result.name).append(" = bitcast ").append(
				r.typeAndName()).append(" to ").append(type).append("\n");
		return result;
	}
	
	private void store(final Register value, final Register dest) {
		output.append("\tstore ").append(value.typeAndName()).append(", ")
				.append(dest.typeAndName()).append("\n");
	}
	
	private Register getElementPtr(final Register r, final String type,
			final int... args) {
		final Register result = nextRegister(type);
		output.append("\t").append(result.name).append(" = getelementptr ")
				.append(r.typeAndName());
		for (final int i : args) {
			output.append(", ");
			output.append("i32 ").append(i);
		}
		output.append("\n");
		return result;
	}
	
	private Register alloca(final Register r) throws CodeGenerationException {
		output.append("\t").append(r.name).append(" = alloca ").append(
				r.derefType()).append("\n");
		return r;
	}
	
	private Register load(final Register from) throws CodeGenerationException {
		final Register result = nextRegister(from.derefType());
		output.append("\t").append(result.name).append(" = load ").append(
				from.typeAndName()).append("\n");
		return result;
	}
	
	private Register mallocCharArray(final int len) {
		final Register charArr = nextRegister("[" + len + " x i8]*");
		
		final Register call = nextRegister("i8 *");
		output.append("\t").append(call.name).append(
				" = call noalias i8* @GC_malloc(i64 ").append(len)
				.append(")\n");
		
		output.append("\t").append(charArr.name).append(" = bitcast ").append(
				call.typeAndName()).append(" to ").append(charArr.type).append(
				"\n");
		
		return charArr;
	}
	
	private Register malloc(final Register r, final String type) {
		final Register size = nextRegister(type);
		final Register cast = nextRegister("i64");
		output.append("\t").append(size.name).append(" = getelementptr ")
				.append(size.type).append(" null, i32 1\n");
		output.append("\t").append(cast.name).append(" = ptrtoint ").append(
				type).append(" ").append(size.name).append(" to ").append(
				cast.type).append("\n");
		
		final Register call = nextRegister("i8 *");
		output.append("\t").append(call.name).append(
				" = call noalias i8* @GC_malloc(i64 ").append(cast.name)
				.append(")\n");
		
		final Register cast2 = nextRegister(type);
		output.append("\t").append(cast2.name).append(" = bitcast ").append(
				call.typeAndName()).append(" to ").append(cast2.type).append(
				"\n");
		
		output.append("\tstore ").append(cast2.typeAndName()).append(", ")
				.append(r.type).append(" ").append(r.name).append("\n");
		return r;
	}
	
	private void log(final String msg) {
		if (debug) {
			System.err.println(msg);
		}
	}
	
}
