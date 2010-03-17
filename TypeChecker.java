import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

public class TypeChecker {
	
	protected ASTnode root;
	protected Environment env;
	
	protected boolean debug;
	
	protected final Environment.CoolClass OBJECT;
	protected final Environment.CoolClass BOOL;
	protected final Environment.CoolClass INT;
	protected final Environment.CoolClass STRING;
	protected final Environment.CoolClass IO;
	
	public static class TypeCheckException extends Exception {
		private static final long serialVersionUID = -7774893843820229667L;
		
		public TypeCheckException(final String msg) {
			super(msg);
		}
	}
	
	public TypeChecker(final ASTnode root, final boolean debug)
			throws Environment.EnvironmentException {
		this.root = root;
		this.debug = debug;
		env = new Environment(debug);
		OBJECT = env.getClass("Object");
		BOOL = env.getClass("Bool");
		INT = env.getClass("Int");
		STRING = env.getClass("String");
		IO = env.getClass("IO");
	}
	
	public Environment getEnvironment() {
		return env;
	}
	
	public boolean typecheck() {
		try {
			log("\n--> Pass 1: identifying classes...");
			identifyClasses(root);
			log("\n--> Pass 2: determining inheritance hierarchy...");
			identifyParents(root);
			log("\n--> Checking inheritance hierarchy for cycles...");
			checkHierarchyForCycles();
			
			log("\n--> Pass 3a: identifying attributes and methods");
			this.getMethodsAndAttributes();
			
			log("\n--> Pass 3b: dealing with attribute inheritance");
			inheritAttributes();
			
			log("\n--> Pass 3c: dealing with method inheritance");
			inheritMethods();
			
			log("\n--> Pass 4: typecheck attributes");
			checkAttributes();
			
			log("\n--> Pass 5: typecheck methods");
			checkMethods();
			
			if (!env.classes.containsKey("Main")) {
				System.err.println("\nWARNING: Main class not present");
			} else {
				final Environment.CoolMethod mainMethod = env.classes
						.get("Main").methods.get("main");
				if (mainMethod == null) {
					System.err
							.println("\nWARNING: Main class does not contain main() method");
				} else if (mainMethod.arguments.size() != 0) {
					System.err
							.println("\nWARNING: Main class's main() method should not have arguments.");
				}
			}
			
			log("\n--> Typechecking completed!");
		} catch (final Exception ex) {
			System.err.println("*** Typechecking Failed! ***");
			ex.printStackTrace();
			return false;
		}
		return true;
	}
	
	/*
	 * DETERMINE CLASS HIERARCHY
	 */

	// First pass: identify class names
	public void identifyClasses(final ASTnode node)
			throws Environment.EnvironmentException, TypeCheckException {
		if (node != null) {
			switch (node.kind) {
			case (sym.CLASS):
				addClass((String) node.value, node);
				break;
			case (sym.SEMI):
				identifyClasses(node.left);
				identifyClasses(node.right);
				break;
			default:
				throw new TypeCheckException(
						MessageFormat
								.format(
										"Malformed AST; while checking classes, expected CLASS or SEMI, found {0}",
										Util.idToName(node.kind)));
			}
		}
	}
	
	// Second pass: identify class hierarchy
	public void identifyParents(final ASTnode node)
			throws Environment.EnvironmentException, TypeCheckException {
		if (node != null) {
			switch (node.kind) {
			case (sym.CLASS):
				if (node.left != null && node.left.kind == sym.INHERITS) {
					final Environment.CoolClass thisClass = env
							.getClass((String) node.value);
					if (node.left.value.equals("Int")
							|| node.left.value.equals("Bool")
							|| node.left.value.equals("String")) {
						throw new TypeCheckException(MessageFormat.format(
								"Class {0} inherits from prohibited class {1}",
								thisClass, node.left.value));
					}
					final Environment.CoolClass parentClass = env
							.getClass((String) node.left.value);
					thisClass.parent = parentClass;
					log(MessageFormat.format("Class {0} inherits from {1}",
							thisClass, parentClass));
				} else {
					final Environment.CoolClass thisClass = env
							.getClass((String) node.value);
					final Environment.CoolClass parentClass = OBJECT;
					thisClass.parent = parentClass;
					log(MessageFormat
							.format(
									"Class {0} has no listed parent, so assuming it inherits from {1}",
									thisClass, parentClass));
				}
				break;
			case (sym.SEMI):
				identifyParents(node.left);
				identifyParents(node.right);
				break;
			default:
				throw new TypeCheckException(
						MessageFormat
								.format(
										"Malformed AST; while checking classes, expected CLASS or SEMI, found {0}",
										Util.idToName(node.kind)));
			}
		}
	}
	
	// Make sure class hierarchy is a tree
	protected void checkHierarchyForCycles()
			throws Environment.EnvironmentException, TypeCheckException {
		final HashSet<Environment.CoolClass> red = new HashSet<Environment.CoolClass>();
		final HashSet<Environment.CoolClass> green = new HashSet<Environment.CoolClass>();
		green.add(OBJECT);
		final Iterator<Entry<String, Environment.CoolClass>> iter = env.classes
				.entrySet().iterator();
		while (iter.hasNext()) {
			final Entry<String, Environment.CoolClass> entry = iter.next();
			Environment.CoolClass currClass = entry.getValue();
			while (!green.contains(currClass)) {
				if (red.contains(currClass)) {
					throw new TypeCheckException(
							"Class hierarchy is not a tree.");
				} else {
					red.add(currClass);
					currClass = currClass.parent;
				}
			}
			final Iterator<Environment.CoolClass> reds = red.iterator();
			Environment.CoolClass redClass;
			while (reds.hasNext()) {
				redClass = reds.next();
				reds.remove();
				green.add(redClass);
			}
			red.clear();
		}
		log("Class hierarchy contains no cycles.");
	}
	
	/*
	 * DETERMINE METHODS AND ATTRIBUTES
	 */

	protected void getMethodsAndAttributes() throws TypeCheckException,
			Environment.EnvironmentException {
		for (final Entry<String, Environment.CoolClass> e : env.classes
				.entrySet()) {
			final Environment.CoolClass curClass = e.getValue();
			if (curClass.node != null && curClass.node.right != null) {
				log(MessageFormat.format(
						"Processing methods and attributes of class {0}",
						curClass));
				this.getMethodsAndAttributes(curClass, curClass.node.right);
			}
		}
	}
	
	protected void getMethodsAndAttributes(
			final Environment.CoolClass curClass, final ASTnode node)
			throws TypeCheckException, Environment.EnvironmentException {
		if (node != null) {
			switch (node.kind) {
			case Nodes.ATTRIBUTE: {
				final String name = (String) node.left.left.value;
				final Environment.CoolClass type = env
						.getClass((String) node.left.right.value);
				final Environment.CoolAttribute attr = new Environment.CoolAttribute(
						name, type);
				attr.node = node;
				env.addAttribute(curClass, attr);
				node.type = type;
				break;
			}
			case Nodes.METHOD: {
				final String name = (String) node.left.left.value;
				final Environment.CoolClass returnType = env
						.getClass((String) node.left.right.value);
				final Environment.CoolMethod method = new Environment.CoolMethod(
						name, returnType);
				method.node = node;
				if (node.center != null) {
					processMethodArguments(method, node.center);
				}
				env.addMethod(curClass, method);
				node.type = returnType;
				break;
			}
			case sym.SEMI: {
				this.getMethodsAndAttributes(curClass, node.left);
				this.getMethodsAndAttributes(curClass, node.right);
				break;
			}
			default:
				throw new TypeCheckException(
						"Malformed AST; METHOD, ATTRIBUTE, or SEMI expected, but found"
								+ Util.idToName(node.kind));
			}
		}
	}
	
	private void inheritAttributes() {
		for (final Environment.CoolClass c : env.classes.values()) {
			inheritAttributes(c);
		}
	}
	
	private void inheritAttributes(final Environment.CoolClass c) {
		if (!c.attrInheritDone && c != OBJECT) {
			log("Checking " + c);
			inheritAttributes(c.parent);
			final LinkedList<Environment.CoolClass> q = new LinkedList<Environment.CoolClass>();
			q.push(c);
			Environment.CoolClass p = c.parent;
			while (p != OBJECT) {
				q.push(p);
				p = p.parent;
			}
			while (!q.isEmpty()) {
				final Environment.CoolClass curClass = q.pop();
				for (final Environment.CoolAttribute a : curClass.attributes
						.values()) {
					log("Found attribute " + a + " of " + curClass + " for "
							+ c);
					c.attrList.add(a);
				}
			}
			c.attrInheritDone = true;
		}
		log("Class: " + c);
		if (debug) {
			for (final Environment.CoolAttribute a : c.attrList) {
				System.err.println(MessageFormat.format("In {0} is {1}", c, a));
			}
		}
	}
	
	private void inheritMethods() {
		for (final Environment.CoolClass c : env.classes.values()) {
			inheritMethods(c);
		}
		if (debug) {
			for (final Environment.CoolClass c : env.classes.values()) {
				log(MessageFormat.format("\nClass {0} methodlist:", c));
				for (final Environment.CoolMethod m : c.methodList) {
					log(MessageFormat.format("{0} (parent = {1})", m, m.parent));
				}
			}
		}
	}
	
	private void inheritMethods(final Environment.CoolClass c) {
		if (!c.methodInheritDone && c != OBJECT) {
			log("Checking " + c);
			inheritMethods(c.parent);
			final LinkedList<Environment.CoolClass> q = new LinkedList<Environment.CoolClass>();
			q.push(c);
			Environment.CoolClass p = c.parent;
			while (p != OBJECT) {
				q.push(p);
				p = p.parent;
			}
			while (!q.isEmpty()) {
				final Environment.CoolClass curClass = q.pop();
				for (final Environment.CoolMethod a : curClass.methods.values()) {
					log("Found method " + a + " of " + curClass + " for " + c);
					final Environment.CoolMethod overriddenMethod = c.methods
							.get(a.name);
					log(overriddenMethod != null ? "" + overriddenMethod.parent
							: "not overridden");
					if (overriddenMethod != null) {
						if (!c.methodList.contains(overriddenMethod)) {
							c.methodList.add(overriddenMethod);
						}
					} else {
						if (!c.methodList.contains(a)) {
							c.methodList.add(a);
						}
					}
				}
			}
			c.methodInheritDone = true;
		}
		log("Class: " + c);
		if (debug) {
			for (final Environment.CoolAttribute a : c.attrList) {
				System.err.println(MessageFormat.format("In {0} is {1}", c, a));
			}
		}
	}
	
	private void processMethodArguments(final Environment.CoolMethod method,
			final ASTnode node) throws Environment.EnvironmentException,
			TypeCheckException {
		if (node != null) {
			switch (node.kind) {
			case sym.COLON: {
				final String name = (String) node.left.value;
				final Environment.CoolClass type = env
						.getClass((String) node.right.value);
				method.arguments.add(new Environment.CoolAttribute(name, type));
				break;
			}
			case sym.COMMA: {
				processMethodArguments(method, node.left);
				processMethodArguments(method, node.right);
				break;
			}
			default:
				throw new TypeCheckException(
						MessageFormat
								.format(
										"Malformed AST; COLON or COMMA expected, but found {0}",
										Util.idToName(node.kind)));
			}
		}
	}
	
	/*
	 * TYPECHECKING METHODS
	 */

	public void checkAttributes() throws Environment.EnvironmentException,
			TypeCheckException {
		for (final Entry<String, Environment.CoolClass> e : env.classes
				.entrySet()) {
			final Environment.CoolClass curClass = e.getValue();
			if (curClass.builtin) {
				continue;
			}
			log(MessageFormat.format("Typechecking attributes of class {0}",
					curClass));
			for (final Entry<String, Environment.CoolAttribute> e2 : curClass.attributes
					.entrySet()) {
				final Environment.CoolAttribute attr = e2.getValue();
				if (attr.node.right != null) {
					log("Checking attribute " + attr);
					check(curClass, attr.node.right);
					log(MessageFormat.format("Expr type: {0}; Attr type: {1}",
							attr.node.right.type, attr.node.type));
					if (!moreGeneralOrEqualTo(attr.node.type,
							attr.node.right.type)) {
						throw new TypeCheckException(MessageFormat.format(
								"Attribute {0} has value of wrong type: {1}",
								attr, attr.node.right.type));
					}
				}
			}
		}
	}
	
	public void checkMethods() throws Environment.EnvironmentException,
			TypeCheckException {
		for (final Entry<String, Environment.CoolClass> e : env.classes
				.entrySet()) {
			final Environment.CoolClass curClass = e.getValue();
			if (curClass.builtin) {
				continue;
			}
			log(MessageFormat.format("Typechecking methods of class {0}",
					curClass));
			for (final Entry<String, Environment.CoolMethod> e2 : curClass.methods
					.entrySet()) {
				final Environment.CoolMethod method = e2.getValue();
				if (method.node.right != null) {
					log("Checking method " + method);
					for (final Environment.CoolAttribute a : method.arguments) {
						log(MessageFormat
								.format(
										"Pushing method argument {0} onto local environment",
										a));
						env.localTypes.push(a.name, a.type);
					}
					log(MessageFormat.format("Local environment is {0}",
							env.localTypes));
					check(curClass, method.node.right);
					for (@SuppressWarnings("unused")
					final Environment.CoolAttribute a : method.arguments) {
						log("Popping local environment");
						env.localTypes.pop();
					}
					log(MessageFormat.format("Local environment is {0}",
							env.localTypes));
					log(MessageFormat.format(
							"Declared method type: {0}; Method body type: {1}",
							method.node.right.type, method.node.type));
					if (!moreGeneralOrEqualTo(method.node.type,
							method.node.right.type)) {
						throw new TypeCheckException(MessageFormat.format(
								"Method {0} has body of wrong type: {1}",
								method, method.node.right.type));
					}
				}
			}
		}
	}
	
	public Environment.CoolClass check(final Environment.CoolClass curClass,
			final ASTnode node) throws Environment.EnvironmentException,
			TypeCheckException {
		if (node != null) {
			switch (node.kind) {
			
			// LITERALS
			case sym.TRUE:
			case sym.FALSE:
				return setType(BOOL, node);
			case sym.INTLIT:
				return setType(INT, node);
			case sym.STRINGLIT:
				return setType(STRING, node);
				
				// IDENTIFIER
			case sym.ID:
				return setType(env
						.lookupAttrType(curClass, (String) node.value), node);
				
				// OPERATORS
			case sym.ASSIGN: {
				if (node.left.kind != sym.ID) {
					throw new TypeCheckException(
							MessageFormat
									.format(
											"Left-hand side of an assignment must be an identifier, but {0} found instead",
											Util.idToName(node.left.kind)));
				}
				if (node.left.value.equals("self")) {
					throw new TypeCheckException(
							"The special variable 'self' cannot be assigned to.");
				}
				final Environment.CoolClass leftType = check(curClass,
						node.left);
				final Environment.CoolClass rightType = check(curClass,
						node.right);
				log(MessageFormat
						.format(
								"Assignment: Left-side {0} has type {1}; right-side has type {2}",
								node.left.value, node.left.type,
								node.right.type));
				if (moreGeneralOrEqualTo(leftType, rightType)) {
					log(MessageFormat.format(
							"Most specific parent in common is {0}",
							mostSpecificParent(leftType, rightType)));
					return setType(rightType, node);
				} else {
					throw new TypeCheckException(
							MessageFormat
									.format(
											"Expression of type {0} not compatible with variable type {1}",
											node.right.type, node.left.type));
				}
			}
				
			case sym.NEW: {
				return setType(env.getClass((String) node.value), node);
			}
				
			case sym.DOT: {
				typecheckMethodArguments(curClass, node.right);
				Environment.CoolClass containingClass;
				if (node.left != null) {
					check(curClass, node.left);
					containingClass = node.left.type;
				} else {
					containingClass = curClass;
				}
				
				if (node.center != null) {
					if (node.center.kind != sym.TYPEID) {
						throw new TypeCheckException(
								MessageFormat
										.format(
												"Malformed AST; center node of DOT, if it exists, should be TYPEID, but it was {0}",
												Util.idToName(node.center.kind)));
					}
					final Environment.CoolClass staticClass = env
							.getClass((String) node.center.value);
					if (!moreGeneralOrEqualTo(staticClass, containingClass)) {
						throw new TypeCheckException(
								MessageFormat
										.format(
												"Static class {0} not compatible with type ({1}) of {2}",
												staticClass, containingClass,
												node.left.value));
					}
					log(MessageFormat
							.format(
									"Static dispatch; will use {0} as type for method call {1}",
									staticClass, node.value));
					containingClass = staticClass;
				}
				
				log(MessageFormat.format("Looking up method {0} in {1}",
						node.value, containingClass));
				final Environment.CoolMethod method = env.lookupMethod(
						containingClass, (String) node.value);
				if (method == null) {
					throw new TypeCheckException(
							MessageFormat
									.format(
											"Tried to call method {0} in {1}, but method not found.",
											node.value, containingClass));
				}
				
				final List<Environment.CoolClass> actuals = new LinkedList<Environment.CoolClass>();
				getArgumentTypes(node.right, actuals);
				final List<Environment.CoolAttribute> formals = method.arguments;
				
				if (actuals.size() != formals.size()) {
					throw new TypeCheckException(
							MessageFormat
									.format(
											"Call to method {0} has wrong number of arguments (expected {1}, found {2})",
											method, formals.size(), actuals
													.size()));
				}
				
				final Iterator<Environment.CoolClass> actualIter = actuals
						.iterator();
				final Iterator<Environment.CoolAttribute> formalIter = formals
						.iterator();
				
				while (actualIter.hasNext() && formalIter.hasNext()) {
					final Environment.CoolClass expectedType = formalIter
							.next().type;
					final Environment.CoolClass actualType = actualIter.next();
					
					if (!moreGeneralOrEqualTo(expectedType, actualType)) {
						throw new TypeCheckException(MessageFormat.format(
								"Expected argument of type {0}, but found {1}",
								expectedType, actualType));
					}
				}
				
				return setType(method.type, node);
			}
				
			case sym.IF: {
				check(curClass, node.left);
				if (node.left.type != BOOL) {
					throw new TypeCheckException(MessageFormat.format(
							"If condition must be of type Bool, but {0} found",
							node.left.type));
				}
				check(curClass, node.center);
				check(curClass, node.right);
				final Environment.CoolClass unionType = mostSpecificParent(
						node.center.type, node.right.type);
				log(MessageFormat.format(
						"Then type: {0}; Else type: {1}; Union type: {2}",
						node.center.type, node.right.type, unionType));
				return setType(unionType, node);
			}
				
			case sym.SEMI: {
				// Check the mandatory first expression
				check(curClass, node.left);
				Environment.CoolClass lastType = node.left.type;
				
				// Then check the optional remaining expressions,
				// if they are present.
				if (node.right != null) {
					lastType = checkSequence(curClass, node.right);
				}
				return setType(lastType, node);
			}
				
			case sym.LET: {
				final int numVars = addLetIntroductions(curClass, node.left, 0);
				log(MessageFormat
						.format(
								"Let expression resulted in {0} variables added to local environment, which is now: {1}",
								numVars, env.localTypes));
				check(curClass, node.right);
				for (int i = 0; i < numVars; ++i) {
					log("Popping mapping off local environment");
					env.localTypes.pop();
				}
				log(MessageFormat.format(
						"After let evaluated, local environment is {0}",
						env.localTypes));
				return setType(node.right.type, node);
			}
				
			case sym.CASE: {
				check(curClass, node.left);
				List<Environment.CoolClass> list = new LinkedList<Environment.CoolClass>();
				list = getCaseTypes(curClass, node.right, list);
				final Iterator<Environment.CoolClass> iter = list.iterator();
				Environment.CoolClass caseClass = iter.next();
				while (iter.hasNext()) {
					final Environment.CoolClass nextClass = iter.next();
					log(MessageFormat.format("Comparing {0} and {1}",
							caseClass, nextClass));
					caseClass = mostSpecificParent(caseClass, nextClass);
				}
				log(MessageFormat.format("Union type of case statement is {0}",
						caseClass));
				return setType(caseClass, node);
			}
				
			case sym.WHILE: {
				check(curClass, node.left);
				if (node.left.type != BOOL) {
					throw new TypeCheckException(
							MessageFormat
									.format(
											"Loop condition of a WHILE loop must be a Bool, but found {0}",
											node.left.type));
				}
				check(curClass, node.right);
				return setType(OBJECT, node);
			}
				
			case sym.ISVOID: {
				check(curClass, node.left);
				return setType(BOOL, node);
			}
				
			case sym.NOT: {
				check(curClass, node.left);
				if (node.left.type != BOOL) {
					throw new TypeCheckException(MessageFormat.format(
							"Argument to NOT must be Bool, but found {0}",
							node.left.type));
				}
				return setType(BOOL, node);
			}
				
			case sym.LT:
			case sym.LEQ: {
				check(curClass, node.left);
				check(curClass, node.right);
				if (node.left.type != INT) {
					throw new TypeCheckException(
							"Left argument of comparison must be Int, but found"
									+ node.left.type);
				}
				if (node.right.type != INT) {
					throw new TypeCheckException(
							"Right argument of comparison must be Int, but found"
									+ node.left.type);
				}
				return setType(BOOL, node);
			}
				
			case sym.MINUS:
			case sym.DIV:
			case sym.TIMES:
			case sym.PLUS: {
				check(curClass, node.left);
				check(curClass, node.right);
				if (node.left.type != INT || node.right.type != INT) {
					throw new TypeCheckException("The operator "
							+ Util.idToName(node.kind)
							+ " takes two arguments of type Int");
				}
				return setType(INT, node);
			}
				
			case sym.EQ: {
				check(curClass, node.left);
				check(curClass, node.right);
				
				if ((node.left.type == INT && node.right.type != INT)
						|| (node.left.type == BOOL && node.right.type != BOOL)
						|| (node.left.type == STRING && node.right.type != STRING)
						|| (node.right.type == INT && node.left.type != INT)
						|| (node.right.type == BOOL && node.left.type != BOOL)
						|| (node.right.type == STRING && node.left.type != STRING)) {
					throw new TypeCheckException(
							MessageFormat
									.format(
											"Ints, Bools and Strings can only be compared to each other, but tried to compare a {0} to a {1}",
											node.left.type, node.right.type));
				}
				
				return setType(BOOL, node);
			}
				
			case sym.NEG: {
				check(curClass, node.left);
				if (node.left.type != INT) {
					throw new TypeCheckException(
							"The ~ operator only takes objects of type Int, but found "
									+ node.left.type);
				}
				return setType(INT, node);
			}
				
			default:
				throw new TypeCheckException("Unimplemented node type: "
						+ Util.idToName(node.kind));
			}
		}
		return null;
	}
	
	private List<Environment.CoolClass> getCaseTypes(
			final Environment.CoolClass curClass, final ASTnode node,
			final List<Environment.CoolClass> list)
			throws Environment.EnvironmentException, TypeCheckException {
		if (node != null) {
			if (node.kind == sym.SEMI) {
				getCaseTypes(curClass, node.left, list);
				getCaseTypes(curClass, node.right, list);
			} else if (node.kind == sym.RIGHTARROW) {
				final String name = (String) node.left.left.value;
				if (name.equals("self")) {
					throw new TypeCheckException(
							"The special variable 'self' cannot be bound in a case statement.");
				}
				final Environment.CoolClass type = env
						.getClass((String) node.left.right.value);
				env.localTypes.push(name, type);
				log(MessageFormat
						.format(
								"Pushing {0}:{1} onto local environment for CASE branch; localEnv is {2}",
								name, type, env.localTypes));
				check(curClass, node.right);
				env.localTypes.pop();
				log(MessageFormat
						.format(
								"Popping local environment after CASE branch; localEnv is {0}",
								env.localTypes));
				list.add(node.right.type);
			}
		}
		return list;
	}
	
	private int addLetIntroductions(final Environment.CoolClass curClass,
			final ASTnode node, int numVars)
			throws Environment.EnvironmentException, TypeCheckException {
		
		if (node != null) {
			switch (node.kind) {
			case sym.COMMA:
				numVars += addLetIntroductions(curClass, node.left, 0);
				numVars += addLetIntroductions(curClass, node.right, 0);
				break;
			case sym.ASSIGN: {
				numVars += 1;
				final Environment.CoolClass type = env
						.getClass((String) node.left.right.value);
				final String name = (String) node.left.left.value;
				if (name.equals("self")) {
					throw new TypeCheckException(
							"The special variable 'self' cannot be bound in a let expression.");
				}
				Environment.CoolClass actualType = type;
				if (node.right != null) {
					check(curClass, node.right);
					if (!moreGeneralOrEqualTo(type, node.right.type)) {
						throw new TypeCheckException(
								MessageFormat
										.format(
												"Assignment in LET introduction to variable of incompatible type (expected {0}; found {1})",
												type, node.right.type));
					}
					actualType = node.right.type;
				}
				log(MessageFormat.format(
						"Pushing {0}:{1} onto local environment", name,
						actualType));
				env.localTypes.push(name, actualType);
				break;
			}
			default:
				throw new TypeCheckException(
						MessageFormat
								.format(
										"Malformed AST; expected COMMA or ASSIGN to left of LET but found {0}",
										Util.idToName(node.kind)));
				
			}
		}
		
		return numVars;
	}
	
	private Environment.CoolClass checkSequence(
			final Environment.CoolClass curClass, final ASTnode node)
			throws Environment.EnvironmentException, TypeCheckException {
		if (node != null) {
			if (node.kind == sym.SEMI) {
				final Environment.CoolClass leftClass = checkSequence(curClass,
						node.left);
				final Environment.CoolClass rightClass = checkSequence(
						curClass, node.right);
				if (rightClass != null) {
					return rightClass;
				} else {
					return leftClass;
				}
			} else {
				check(curClass, node);
				return node.type;
			}
		}
		return null;
	}
	
	private List<Environment.CoolClass> getArgumentTypes(final ASTnode node,
			final List<Environment.CoolClass> list) {
		if (node != null) {
			if (node.kind == sym.COMMA) {
				getArgumentTypes(node.left, list);
				getArgumentTypes(node.right, list);
			} else {
				log(MessageFormat.format("Argument type: {0}; value: {1}",
						node.type, node.value));
				list.add(node.type);
			}
		}
		return list;
	}
	
	private void typecheckMethodArguments(final Environment.CoolClass curClass,
			final ASTnode node) throws Environment.EnvironmentException,
			TypeCheckException {
		if (node != null) {
			if (node.kind == sym.COMMA) {
				typecheckMethodArguments(curClass, node.left);
				typecheckMethodArguments(curClass, node.right);
			} else {
				check(curClass, node);
			}
		}
	}
	
	/*
	 * UTILITY METHODS
	 */

	protected boolean moreGeneralOrEqualTo(final Environment.CoolClass c1,
			Environment.CoolClass c2) throws Environment.EnvironmentException {
		while (c2 != c1 && c2 != OBJECT) {
			c2 = c2.parent;
		}
		return c2 == c1;
	}
	
	protected Environment.CoolClass mostSpecificParent(
			Environment.CoolClass c1, Environment.CoolClass c2)
			throws Environment.EnvironmentException {
		final HashSet<Environment.CoolClass> alreadySeen = new HashSet<Environment.CoolClass>();
		
		while (true) {
			if (alreadySeen.contains(c1) && c1 != OBJECT) {
				return c1;
			}
			alreadySeen.add(c1);
			c1 = c1.parent;
			if (alreadySeen.contains(c2) && c2 != OBJECT) {
				return c2;
			}
			alreadySeen.add(c2);
			c2 = c2.parent;
			if (c1 == c2) {
				return c1;
			}
		}
	}
	
	protected Environment.CoolClass setType(final Environment.CoolClass cls,
			final ASTnode node) {
		node.type = cls;
		return cls;
	}
	
	protected void addClass(final String name, final ASTnode node)
			throws Environment.EnvironmentException {
		final Environment.CoolClass newClass = new Environment.CoolClass(name);
		newClass.node = node;
		env.addClass(newClass);
	}
	
	public void log(final String msg) {
		if (debug) {
			System.err.println(msg);
		}
	}
	
}
