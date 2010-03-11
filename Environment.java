import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class Environment {

	public static class CoolAttribute {
		public String name;
		public CoolClass type;
		public ASTnode node;
		public CoolClass parent;

		public int index = -1;

		public CoolAttribute(final String name, final CoolClass type) {
			this.name = name;
			this.type = type;
		}

		@Override
		public String toString() {
			return MessageFormat.format("{0}:{1}", name, type);
		}
	}

	public static class CoolClass {
		public String name;
		public CoolClass parent;
		public HashMap<String, CoolMethod> methods = new HashMap<String, CoolMethod>();
		public HashMap<String, CoolAttribute> attributes = new HashMap<String, CoolAttribute>();
		public List<CoolAttribute> attrList = new LinkedList<CoolAttribute>();
		public ASTnode node;
		public boolean builtin = false;
		public boolean inheritDone = false;

		public CoolClass(final String name) {
			this(name, null);
		}

		public CoolClass(final String name, final CoolClass parent) {
			this.name = name;
			this.parent = parent;
		}

		@Override
		public String toString() {
			return name;
		}

		public String getInternalClassName() {
			return "%__class_" + name;
		}

		public String getInternalInstanceName() {
			return "%__instance_" + name;
		}

		public String getInternalDescriptorName() {
			return "@_" + name;
		}
	}

	public static class CoolMethod {
		public String name;
		public List<CoolAttribute> arguments = new LinkedList<CoolAttribute>();
		public CoolClass type;
		public ASTnode node;
		public CoolClass parent;
		public String builtinImplementation = null;

		public int index = -1;

		public CoolMethod(final String name, final CoolClass type) {
			this.name = name;
			this.type = type;
		}

		public String getInternalType() {
			final StringBuilder sb = new StringBuilder();
			sb.append(type.getInternalInstanceName());
			sb.append("* (").append(parent.getInternalInstanceName()).append(" *");
			for (CoolAttribute arg : arguments) {
				sb.append(", ");
				sb.append(arg.type.getInternalInstanceName());
				sb.append("* ");
			}
			sb.append(") *");
			return sb.toString();
		}

		public String getInternalName() {
			return "@__method_" + parent + "_" + name;
		}

		public String getName() {
			final StringBuilder sb = new StringBuilder();
			sb.append(name);
			for (final CoolAttribute c : arguments) {
				sb.append('*');
				sb.append(c.type.name);
			}
			return sb.toString();
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder();
			sb.append(name).append('(');
			boolean first = true;
			for (final CoolAttribute c : arguments) {
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

	public static class EnvironmentException extends Exception {
		private static final long serialVersionUID = -6780166175259658368L;

		public EnvironmentException(final String msg) {
			super(msg);
		}
	}

	public boolean debug;

	public HashMap<String, CoolClass> classes = new HashMap<String, CoolClass>();

	public HashStack<String, CoolClass> localTypes = new HashStack<String, CoolClass>();
	public HashStack<String, CodeGenerator.Register> registers = new HashStack<String, CodeGenerator.Register>();

	public Environment() throws EnvironmentException {
		this(false);
	}

	public Environment(final boolean debug) throws EnvironmentException {

		this.debug = debug;

		log("Setting up default environment...");
		// Set up default classes
		final CoolClass object = new CoolClass("Object");
		object.parent = object;
		final CoolClass ioClass = new CoolClass("IO", object);
		final CoolClass intClass = new CoolClass("Int", object);
		final CoolClass stringClass = new CoolClass("String", object);
		final CoolClass boolClass = new CoolClass("Bool", object);

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
		final CoolMethod abort = new CoolMethod("abort", object);
		final CoolMethod typeName = new CoolMethod("type_name", stringClass);
		// TODO Change this if we ever implement SELF_TYPE
		final CoolMethod copy = new CoolMethod("copy", object);

		addMethod(object, abort);
		addMethod(object, typeName);
		addMethod(object, copy);

		// Built-in methods of IO
		final CoolMethod outString = new CoolMethod("out_string", object);
		outString.arguments.add(new CoolAttribute("x", stringClass));
		final CoolMethod outInt = new CoolMethod("out_int", object);
		outInt.arguments.add(new CoolAttribute("x", intClass));
		final CoolMethod inString = new CoolMethod("in_string", stringClass);
		final CoolMethod inInt = new CoolMethod("in_int", intClass);

		addMethod(ioClass, outString);
		addMethod(ioClass, outInt);
		addMethod(ioClass, inString);
		addMethod(ioClass, inInt);

		// Int has no built-in methods

		// Built-in methods of String
		final CoolMethod length = new CoolMethod("length", intClass);
		final CoolMethod concat = new CoolMethod("concat", stringClass);
		concat.arguments.add(new CoolAttribute("s", stringClass));
		final CoolMethod substr = new CoolMethod("substr", stringClass);
		substr.arguments.add(new CoolAttribute("i", intClass));
		substr.arguments.add(new CoolAttribute("l", intClass));

		addMethod(stringClass, length);
		addMethod(stringClass, concat);
		addMethod(stringClass, substr);

		// final CoolAttribute test = new CoolAttribute("lols", object);
		// addAttribute(stringClass, test);

		// Bool has no built-in method

		outString.builtinImplementation = "\t%v1.addr = alloca %__instance_String *\n"
				+ "\tstore %__instance_String * %v1, %__instance_String ** %v1.addr\n"
				+ "\t%tmp = load %__instance_String** %v1.addr\n"
				+ "\t%tmp1 = getelementptr inbounds %__instance_String * %tmp, i32 0, i32 2\n"
				+ "\t%tmp2 = load i8** %tmp1\n"
				+ "\t%call = call i32 (i8*, ...)* @printf(i8* getelementptr inbounds ([3 x i8]* @str.format, i32 0, i32 0), i8* %tmp2)\n"
				+ "\t%retval = bitcast %__instance_IO * %this to %__instance_Object *\n"
				+ "\tret %__instance_Object * %retval";

		outInt.builtinImplementation = "\t%v1.addr = alloca %__instance_Int *\n"
				+ "\tstore %__instance_Int * %v1, %__instance_Int ** %v1.addr\n"
				+ "\t%tmp = load %__instance_Int** %v1.addr\n"
				+ "\t%tmp1 = getelementptr inbounds %__instance_Int * %tmp, i32 0, i32 1\n"
				+ "\t%tmp2 = load i32* %tmp1\n"
				+ "\t%call = call i32 (i8*, ...)* @printf(i8* getelementptr inbounds ([3 x i8]* @str.format2, i32 0, i32 0), i32 %tmp2)\n"
				+ "\t%retval = bitcast %__instance_IO * %this to %__instance_Object *\n"
				+ "\tret %__instance_Object * %retval";

		log("Done setting up default environment");

	}

	public void addAttribute(final CoolClass c, final CoolAttribute m) throws EnvironmentException {
		if (c.attributes.containsKey(m.name))
			throw new EnvironmentException(MessageFormat.format(
					"Attempting to define attribute already defined: {0} (in class {1})", m, c));
		CoolClass parent = c.parent;
		while (parent != getClass("Object")) {
			if (parent.attributes.containsKey(m.name))
				throw new EnvironmentException(
						MessageFormat
								.format(
										"Attempting to define attribute {0} in class {1}, but already defined in a superclass {2}",
										m.name, c, parent));
			parent = parent.parent;
		}
		log(MessageFormat.format("Adding attribute {0} to class {1}", m, c));
		m.parent = c;
		c.attributes.put(m.name, m);
	}

	public void addClass(final CoolClass c) throws EnvironmentException {
		if (classes.containsKey(c.name))
			throw new EnvironmentException(MessageFormat.format(
					"Attempting to define class already defined: {0}", c));
		log(MessageFormat.format("Adding class {0}", c));
		classes.put(c.name, c);
	}

	public void addMethod(final CoolClass c, final CoolMethod m) throws EnvironmentException {
		if (c.methods.containsKey(m.name))
			throw new EnvironmentException(MessageFormat.format(
					"Attempting to define method already defined: {0} (in class {1})", m, c));
		for (final CoolAttribute a : m.arguments) {
			if (a.name.equals("self"))
				throw new EnvironmentException(
						"The reserved name 'self' cannot be used as the name of a method parameter");
		}

		CoolClass parent = c.parent;
		while (parent != getClass("Object")) {
			if (parent.methods.containsKey(m.name)) {
				final CoolMethod m2 = parent.methods.get(m.name);
				if (m.arguments.size() != m2.arguments.size())
					throw new EnvironmentException(
							MessageFormat
									.format(
											"Attempting to create overriding method {0} with different number of arguments from overriden method {1}",
											m, m2));
				final Iterator<CoolAttribute> iter1 = m.arguments.iterator();
				final Iterator<CoolAttribute> iter2 = m2.arguments.iterator();
				while (iter1.hasNext() && iter2.hasNext()) {
					if ((iter1.next().type != iter2.next().type) || (m.type != m2.type))
						throw new EnvironmentException(
								MessageFormat
										.format(
												"Attempting to override method {0} with method of different signature {1}.",
												m2, m));
				}

			}
			parent = parent.parent;
		}
		log(MessageFormat.format("Adding method {0} to class {1} ({2})", m, c, m.getName()));
		m.parent = c;
		c.methods.put(m.name, m);
	}

	public CoolClass getClass(final String name) throws EnvironmentException {
		final CoolClass result = classes.get(name);
		if (result == null)
			throw new EnvironmentException(MessageFormat.format("Class {0} is not defined.", name));
		return result;
	}

	public Environment.CoolMethod lookupMethod(Environment.CoolClass cls, final String id)
			throws EnvironmentException {
		Environment.CoolMethod result = cls.methods.get(id);
		final CoolClass OBJECT = getClass("Object");
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

	public Environment.CoolClass lookupAttrType(Environment.CoolClass cls, final String id)
			throws EnvironmentException {
		final CoolClass OBJECT = getClass("Object");
		if (id.equals("self")) {
			log(MessageFormat.format("SELF is of type {0}", cls));
			return cls;
		}
		log(MessageFormat.format("Looking up attribute {0} in local environment", id));
		Environment.CoolClass result = localTypes.get(id);
		if (result == null) {
			log(MessageFormat.format("Looking up attribute {0} in current class {1}", id, cls));
			if (cls.attributes.get(id) != null) {
				result = cls.attributes.get(id).type;
			}
		} else {
			log(MessageFormat.format("Attribute {0} found in local environment: ", id, result));
			return result;
		}
		while (result == null && cls != OBJECT) {
			cls = cls.parent;
			log(MessageFormat.format("Looking up attribute {0} in class {1}", id, cls));
			if (cls.attributes.get(id) != null) {
				result = cls.attributes.get(id).type;
			}
		}
		if (result == null) {
			log(MessageFormat.format("Attribute {0} not found", id));
			throw new EnvironmentException(MessageFormat.format(
					"Attribute {0} referenced but not defined", id));
		} else {
			log(MessageFormat.format("Attribute {0} found in class {1}: {2}", id, cls, result));
		}

		return result;
	}

	protected void log(final String msg) {
		if (debug) {
			System.err.println(msg);
		}
	}

}
