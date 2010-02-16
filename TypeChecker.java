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

	final Environment.CoolClass OBJECT;

	public static class TypeCheckException extends Exception {
		private static final long serialVersionUID = -7774893843820229667L;

		public TypeCheckException(String msg) {
			super(msg);
		}
	}

	public TypeChecker(ASTnode root, boolean debug) throws Environment.EnvironmentException {
		this.root = root;
		this.debug = debug;
		env = new Environment(debug);
		OBJECT = env.getClass("Object");
	}

	public boolean typecheck() {
		try {
			log("\n--> Pass 1: identifying classes...");
			identifyClasses(root);
			log("\n--> Pass 2: determining inheritance hierarchy...");
			identifyParents(root);
			log("\n--> Checking inheritance hierarchy for cycles...");
			checkHierarchyForCycles();

			log("\n--> Pass 3: identifying attributes and methods");
			getMethodsAndAttributes();

			log("\n--> Pass 4: typecheck attributes");
			checkAttributes();
		} catch (Exception ex) {
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
	public void identifyClasses(ASTnode node) throws Environment.EnvironmentException,
			TypeCheckException {
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
				throw new TypeCheckException(MessageFormat.format(
						"Malformed AST; while checking classes, expected CLASS or SEMI, found {0}",
						Util.idToName(node.kind)));
			}
		}
	}

	// Second pass: identify class hierarchy
	public void identifyParents(ASTnode node) throws Environment.EnvironmentException,
			TypeCheckException {
		if (node != null) {
			switch (node.kind) {
			case (sym.CLASS):
				if (node.left != null && node.left.kind == sym.INHERITS) {
					Environment.CoolClass thisClass = env.getClass((String) node.value);
					if (node.left.value.equals("Int") || node.left.value.equals("Bool")
							|| node.left.value.equals("String"))
						throw new TypeCheckException(MessageFormat.format(
								"Class {0} inherits from prohibited class {1}", thisClass,
								node.left.value));
					Environment.CoolClass parentClass = env.getClass((String) node.left.value);
					thisClass.parent = parentClass;
					log(MessageFormat.format("Class {0} inherits from {1}", thisClass, parentClass));
				} else {
					Environment.CoolClass thisClass = env.getClass((String) node.value);
					Environment.CoolClass parentClass = env.getClass("Object");
					thisClass.parent = parentClass;
					log(MessageFormat.format(
							"Class {0} has no listed parent, so assuming it inherits from {1}",
							thisClass, parentClass));
				}
				break;
			case (sym.SEMI):
				identifyParents(node.left);
				identifyParents(node.right);
				break;
			default:
				throw new TypeCheckException(MessageFormat.format(
						"Malformed AST; while checking classes, expected CLASS or SEMI, found {0}",
						Util.idToName(node.kind)));
			}
		}
	}

	protected void checkHierarchyForCycles() throws Environment.EnvironmentException,
			TypeCheckException {
		HashSet<Environment.CoolClass> red = new HashSet<Environment.CoolClass>();
		HashSet<Environment.CoolClass> green = new HashSet<Environment.CoolClass>();
		green.add(OBJECT);
		Iterator<Entry<String, Environment.CoolClass>> iter = env.classes.entrySet().iterator();
		boolean isTree = true;
		while (iter.hasNext() && isTree) {
			Entry<String, Environment.CoolClass> entry = iter.next();
			Environment.CoolClass currClass = entry.getValue();
			// log("while loop: currClass is " + currClass);
			while (!green.contains(currClass)) {
				if (red.contains(currClass))
					throw new TypeCheckException("Class hierarchy is not a tree.");
				else {
					red.add(currClass);
					currClass = currClass.parent;
					// log("else: currClass is " + currClass);
				}
			}
			Iterator<Environment.CoolClass> reds = red.iterator();
			Environment.CoolClass redClass;
			while (reds.hasNext()) {
				redClass = reds.next();
				reds.remove();
				green.add(redClass);
			}
			red.clear();
			// log("emptying red");
		}
		log("Class hierarchy contains no cycles.");
	}

	/*
	 * DETERMINE METHODS AND ATTRIBUTES
	 */

	protected void getMethodsAndAttributes() throws TypeCheckException,
			Environment.EnvironmentException {
		for (Entry<String, Environment.CoolClass> e : env.classes.entrySet()) {
			Environment.CoolClass curClass = e.getValue();
			if (curClass.node != null && curClass.node.right != null) {
				log(MessageFormat
						.format("Processing methods and attributes of class {0}", curClass));
				getMethodsAndAttributes(curClass, curClass.node.right);
			}
		}
	}

	protected void getMethodsAndAttributes(Environment.CoolClass curClass, ASTnode node)
			throws TypeCheckException, Environment.EnvironmentException {
		if (node != null) {
			switch (node.kind) {
			case Nodes.ATTRIBUTE: {
				String name = (String) node.left.left.value;
				Environment.CoolClass type = env.getClass((String) node.left.right.value);
				Environment.CoolAttribute attr = new Environment.CoolAttribute(name, type);
				attr.node = node;
				env.addAttribute(curClass, attr);
				node.type = type;
				break;
			}
			case Nodes.METHOD: {
				String name = (String) node.left.left.value;
				Environment.CoolClass returnType = env.getClass((String) node.left.right.value);
				Environment.CoolMethod method = new Environment.CoolMethod(name, returnType);
				method.node = node;
				if (node.center != null) {
					processMethodArguments(method, node.center);
				}
				env.addMethod(curClass, method);
				node.type = returnType;
				break;
			}
			case sym.SEMI: {
				getMethodsAndAttributes(curClass, node.left);
				getMethodsAndAttributes(curClass, node.right);
				break;
			}
			default:
				throw new TypeCheckException(
						"Malformed AST; METHOD, ATTRIBUTE, or SEMI expected, but found"
								+ Util.idToName(node.kind));
			}
		}
	}

	private void processMethodArguments(Environment.CoolMethod method, ASTnode node)
			throws Environment.EnvironmentException, TypeCheckException {
		if (node != null) {
			switch (node.kind) {
			case sym.COLON: {
				String name = (String) node.left.value;
				Environment.CoolClass type = env.getClass((String) node.right.value);
				method.arguments.add(new Environment.CoolAttribute(name, type));
				break;
			}
			case sym.COMMA: {
				processMethodArguments(method, node.left);
				processMethodArguments(method, node.right);
				break;
			}
			default:
				throw new TypeCheckException(MessageFormat.format(
						"Malformed AST; COLON or COMMA expected, but found {0}", Util
								.idToName(node.kind)));
			}
		}
	}

	/*
	 * TYPECHECKING METHODS
	 */

	public void checkAttributes() throws Environment.EnvironmentException, TypeCheckException {
		for (Entry<String, Environment.CoolClass> e : env.classes.entrySet()) {
			Environment.CoolClass curClass = e.getValue();
			if (curClass.builtin) {
				continue;
			}
			log(MessageFormat.format("Typechecking attributes of class {0}", curClass));
			for (Entry<String, Environment.CoolAttribute> e2 : curClass.attributes.entrySet()) {
				Environment.CoolAttribute attr = e2.getValue();
				if (attr.node.right != null) {
					log("Checking attribute " + attr);
					check(curClass, attr.node.right);
					log(MessageFormat.format("Expr type: {0}; Attr type: {1}",
							attr.node.right.type, attr.node.type));
					if (!moreGeneralOrEqualTo(attr.node.right.type, attr.node.type))
						throw new TypeCheckException(MessageFormat.format(
								"Attribute {0} has value of wrong type: {1}", attr,
								attr.node.right.type));
				}
			}
		}
	}

	public Environment.CoolClass check(Environment.CoolClass curClass, ASTnode node)
			throws Environment.EnvironmentException, TypeCheckException {
		if (node != null) {
			switch (node.kind) {

			// LITERALS
			case sym.TRUE:
			case sym.FALSE:
				return setType(env.getClass("Bool"), node);
			case sym.INTLIT:
				return setType(env.getClass("Int"), node);
			case sym.STRINGLIT:
				return setType(env.getClass("String"), node);

				// IDENTIFIER
			case sym.ID:
				return setType(lookupAttr(curClass, (String) node.value), node);

				// OPERATORS
			case sym.ASSIGN: {
				if (node.left.kind != sym.ID)
					throw new TypeCheckException(
							MessageFormat
									.format(
											"Left-hand side of an assignment must be an identifier, but {0} found instead",
											Util.idToName(node.left.kind)));
				Environment.CoolClass leftType = check(curClass, node.left);
				Environment.CoolClass rightType = check(curClass, node.right);
				log(MessageFormat.format(
						"Assignment: Left-side {0} has type {1}; right-side has type {2}",
						node.left.value, node.left.type, node.right.type));
				if (moreGeneralOrEqualTo(leftType, rightType)) {
					log(MessageFormat.format("Most specific parent in common is {0}",
							mostSpecificParent(leftType, rightType)));
					return setType(rightType, node);
				} else
					throw new TypeCheckException(MessageFormat.format(
							"Expression of type {0} not compatible with variable type {1}",
							node.right.type, node.left.type));
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
					if (node.center.kind != sym.TYPEID)
						throw new TypeCheckException(
								MessageFormat
										.format(
												"Malformed AST; center node of DOT, if it exists, should be TYPEID, but it was {0}",
												Util.idToName(node.center.kind)));
					Environment.CoolClass staticClass = env.getClass((String) node.center.value);
					if (!moreGeneralOrEqualTo(staticClass, containingClass))
						throw new TypeCheckException(MessageFormat.format(
								"Static class {0} not compatible with type ({1}) of {2}",
								staticClass, containingClass, node.left.value));
					log(MessageFormat.format(
							"Static dispatch; will use {0} as type for method call {1}",
							staticClass, node.value));
					containingClass = staticClass;
				}

				log(MessageFormat.format("Looking up method {0} in {1}", node.value,
						containingClass));
				Environment.CoolMethod method = lookupMethod(containingClass, (String) node.value);
				if (method == null)
					throw new TypeCheckException(MessageFormat.format(
							"Tried to call method {0} in {1}, but method not found.", node.value,
							containingClass));

				List<Environment.CoolClass> actuals = new LinkedList<Environment.CoolClass>();
				getArgumentTypes(node.right, actuals);
				List<Environment.CoolAttribute> formals = method.arguments;

				if (actuals.size() != formals.size())
					throw new TypeCheckException(
							MessageFormat
									.format(
											"Call to method {0} has wrong number of arguments (expected {1}, found {2})",
											method, formals.size(), actuals.size()));

				Iterator<Environment.CoolClass> actualIter = actuals.iterator();
				Iterator<Environment.CoolAttribute> formalIter = formals.iterator();

				while (actualIter.hasNext() && formalIter.hasNext()) {
					Environment.CoolClass expectedType = formalIter.next().type;
					Environment.CoolClass actualType = actualIter.next();

					if (!moreGeneralOrEqualTo(expectedType, actualType))
						throw new TypeCheckException(MessageFormat.format(
								"Expected argument of type {0}, but found {1}", expectedType,
								actualType));
				}

				return setType(method.type, node);
			}

			case sym.IF: {
				check(curClass, node.left);
				if (node.left.type != env.getClass("Bool"))
					throw new TypeCheckException(MessageFormat.format(
							"If condition must be of type Bool, but {0} found", node.left.type));
				check(curClass, node.center);
				check(curClass, node.right);
				Environment.CoolClass unionType = mostSpecificParent(node.center.type,
						node.right.type);
				log(MessageFormat.format("Then type: {0}; Else type: {1}; Union type: {2}",
						node.center.type, node.right.type, unionType));
				return setType(unionType, node);
			}

			default:
				throw new TypeCheckException("Unimplemented node type: " + Util.idToName(node.kind));
			}
		}
		return null;
	}

	private List<Environment.CoolClass> getArgumentTypes(ASTnode node,
			List<Environment.CoolClass> list) {
		if (node != null) {
			if (node.kind == sym.COMMA) {
				getArgumentTypes(node.left, list);
				getArgumentTypes(node.right, list);
			} else {
				log(MessageFormat.format("Argument type: {0}; value: {1}", node.type, node.value));
				list.add(node.type);
			}
		}
		return list;
	}

	private void typecheckMethodArguments(Environment.CoolClass curClass, ASTnode node)
			throws Environment.EnvironmentException, TypeCheckException {
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

	protected boolean moreGeneralOrEqualTo(Environment.CoolClass c1, Environment.CoolClass c2)
			throws Environment.EnvironmentException {
		while (c2 != c1 && c2 != OBJECT) {
			c2 = c2.parent;
		}
		return c2 == c1;
	}

	protected Environment.CoolClass mostSpecificParent(Environment.CoolClass c1,
			Environment.CoolClass c2) throws Environment.EnvironmentException {
		HashSet<Environment.CoolClass> alreadySeen = new HashSet<Environment.CoolClass>();

		while (true) {
			if (alreadySeen.contains(c1) && c1 != OBJECT)
				return c1;
			alreadySeen.add(c1);
			c1 = c1.parent;
			if (alreadySeen.contains(c2) && c2 != OBJECT)
				return c2;
			alreadySeen.add(c2);
			c2 = c2.parent;
			if (c1 == c2)
				return c1;
		}
	}

	protected Environment.CoolMethod lookupMethod(Environment.CoolClass cls, String id) {
		Environment.CoolMethod result = cls.methods.get(id);
		while (result == null && cls != OBJECT) {
			log(MessageFormat
					.format("Method {2} not found in {0}; trying {1}", cls, cls.parent, id));
			cls = cls.parent;
			result = cls.methods.get(id);
		}
		if (result == null) {
			log(MessageFormat.format("Method {0} not found", id));
		} else {
			log(MessageFormat.format("Method {0} found in {1}", id, cls));
		}
		return result;
	}

	protected Environment.CoolClass lookupAttr(Environment.CoolClass cls, String id) {
		log(MessageFormat.format("Looking up attribute {0} in local environment", id));
		Environment.CoolClass result = env.localEnv.get(id);
		if (result == null) {
			log(MessageFormat.format("Looking up attribute {0} in current class {1}", id, cls));
			result = cls.attributes.get(id).type;
		}
		while (result == null && cls != OBJECT) {
			cls = cls.parent;
			log(MessageFormat.format("Looking up attribute {0} in class {1}", id, cls));
			result = cls.attributes.get(id).type;
		}
		if (result == null) {
			log(MessageFormat.format("Attribute {0} not found", id));
		} else {
			log(MessageFormat.format("Attribute {0} found in class {1}", id, cls));
		}
		return result;
	}

	protected Environment.CoolClass setType(Environment.CoolClass cls, ASTnode node) {
		node.type = cls;
		return cls;
	}

	protected void addClass(String name, ASTnode node) throws Environment.EnvironmentException {
		Environment.CoolClass newClass = new Environment.CoolClass(name);
		newClass.node = node;
		env.addClass(newClass);
	}

	public void log(String msg) {
		if (debug) {
			System.err.println(msg);
		}
	}

}