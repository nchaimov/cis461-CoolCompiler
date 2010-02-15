import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;

public class TypeChecker {

	protected ASTnode root;
	protected Environment env;

	protected boolean debug;

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
		final Environment.CoolClass OBJECT = env.getClass("Object");
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
				log("Checking attribute " + attr);
				check(curClass, attr.node.right);
				if (attr.type != attr.node.right.type)
					throw new TypeCheckException(MessageFormat.format(
							"Attribute {0} has value of wrong type: {1}", attr,
							attr.node.right.type));
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

			case sym.ID:
				return setType(lookup(curClass, (String) node.value), node);

			default:
				throw new TypeCheckException("Unimplemented node type: " + Util.idToName(node.kind));
			}
		}
		return null;
	}

	/*
	 * UTILITY METHODS
	 */

	protected Environment.CoolClass lookup(Environment.CoolClass cls, String id) {
		Environment.CoolClass result = env.localEnv.get(id);
		if (result == null) {
			result = cls.attributes.get(id).type;
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