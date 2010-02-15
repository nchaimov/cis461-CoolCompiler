import java.text.MessageFormat;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class Environment {

	public boolean debug;

	protected void log(String msg) {
		if (debug) {
			System.err.println(msg);
		}
	}

	public static class EnvironmentException extends Exception {
		private static final long serialVersionUID = -6780166175259658368L;

		public EnvironmentException(String msg) {
			super(msg);
		}
	}

	public static class CoolClass {
		public String name;
		public CoolClass parent;
		public HashMap<String, CoolMethod> methods = new HashMap<String, CoolMethod>();
		public HashMap<String, CoolAttribute> attributes = new HashMap<String, CoolAttribute>();
		public ASTnode node;
		public boolean builtin = false;

		public CoolClass(String name, CoolClass parent) {
			this.name = name;
			this.parent = parent;
		}

		public CoolClass(String name) {
			this(name, null);
		}

		@Override
		public String toString() {
			return name;
		}
	}

	public static class CoolAttribute {
		public String name;
		public CoolClass type;
		public ASTnode node;

		public CoolAttribute(String name, CoolClass type) {
			this.name = name;
			this.type = type;
		}

		@Override
		public String toString() {
			return MessageFormat.format("{0}:{1}", name, type);
		}
	}

	public static class CoolMethod {
		public String name;
		public List<CoolAttribute> arguments = new LinkedList<CoolAttribute>();
		public CoolClass type;
		public ASTnode node;

		public CoolMethod(String name, CoolClass type) {
			this.name = name;
			this.type = type;
		}

		public String getName() {
			StringBuilder sb = new StringBuilder();
			sb.append(name);
			for (CoolAttribute c : arguments) {
				sb.append('*');
				sb.append(c.type.name);
			}
			return sb.toString();
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append(name).append('(');
			boolean first = true;
			for (CoolAttribute c : arguments) {
				if (first) {
					first = false;
				} else {
					sb.append(", ");
				}
				sb.append(c.name);
				sb.append(":");
				sb.append(c.type.name);
			}
			sb.append("):").append(type);
			return sb.toString();
		}
	}

	public void addClass(CoolClass c) throws EnvironmentException {
		if (classes.containsKey(c.name))
			throw new EnvironmentException(MessageFormat.format(
					"Attempting to define class already defined: {0}", c));
		log(MessageFormat.format("Adding class {0}", c));
		classes.put(c.name, c);
	}

	public CoolClass getClass(String name) throws EnvironmentException {
		CoolClass result = classes.get(name);
		if (result == null)
			throw new EnvironmentException(MessageFormat.format("Class {0} is not defined.", name));
		return result;
	}

	public void addMethod(CoolClass c, CoolMethod m) throws EnvironmentException {
		if (c.methods.containsKey(m.getName()))
			throw new EnvironmentException(MessageFormat.format(
					"Attempting to define method already defined: {0} (in class {1})", m, c));
		log(MessageFormat.format("Adding method {0} to class {1} ({2})", m, c, m.getName()));
		c.methods.put(m.getName(), m);
	}

	public void addAttribute(CoolClass c, CoolAttribute m) throws EnvironmentException {
		if (c.attributes.containsKey(m.name))
			throw new EnvironmentException(MessageFormat.format(
					"Attempting to define attribute already defined: {0} (in class {1})", m, c));
		log(MessageFormat.format("Adding attribute {0} to class {1}", m, c));
		c.attributes.put(m.name, m);
	}

	public HashMap<String, CoolClass> classes = new HashMap<String, CoolClass>();
	public HashStack<String, CoolClass> localEnv = new HashStack<String, CoolClass>();

	public Environment() throws EnvironmentException {
		this(false);
	}

	public Environment(boolean debug) throws EnvironmentException {

		this.debug = debug;

		log("Setting up default environment...");
		// Set up default classes
		CoolClass object = new CoolClass("Object");
		object.parent = object;
		CoolClass ioClass = new CoolClass("IO", object);
		CoolClass intClass = new CoolClass("Int", object);
		CoolClass stringClass = new CoolClass("String", object);
		CoolClass boolClass = new CoolClass("Bool", object);

		object.builtin = true;
		ioClass.builtin = true;
		intClass.builtin = true;
		stringClass.builtin = true;
		boolClass.builtin = true;

		addClass(object);
		addClass(ioClass);
		addClass(intClass);
		addClass(stringClass);
		addClass(boolClass);

		// Built-in methods of Object
		CoolMethod abort = new CoolMethod("abort", object);
		CoolMethod typeName = new CoolMethod("type_name", stringClass);
		// TODO Change this if we ever implement SELF_TYPE
		CoolMethod copy = new CoolMethod("copy", object);

		addMethod(object, abort);
		addMethod(object, typeName);
		addMethod(object, copy);

		// Built-in methods of IO
		CoolMethod outString = new CoolMethod("out_string", object);
		outString.arguments.add(new CoolAttribute("x", stringClass));
		CoolMethod outInt = new CoolMethod("out_int", object);
		outInt.arguments.add(new CoolAttribute("x", intClass));
		CoolMethod inString = new CoolMethod("in_string", stringClass);
		CoolMethod inInt = new CoolMethod("in_int", intClass);

		addMethod(ioClass, outString);
		addMethod(ioClass, outInt);
		addMethod(ioClass, inString);
		addMethod(ioClass, inInt);

		// Int has no built-in methods

		// Built-in methods of String
		CoolMethod length = new CoolMethod("length", intClass);
		CoolMethod concat = new CoolMethod("concat", stringClass);
		concat.arguments.add(new CoolAttribute("s", stringClass));
		CoolMethod substr = new CoolMethod("substr", stringClass);
		substr.arguments.add(new CoolAttribute("i", intClass));
		substr.arguments.add(new CoolAttribute("l", intClass));

		addMethod(stringClass, length);
		addMethod(stringClass, concat);
		addMethod(stringClass, substr);

		// Bool has no built-in method

		log("Done setting up default environment");

	}

}
