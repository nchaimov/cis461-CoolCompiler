public class CodeGenerator {

	protected Environment env;

	protected boolean debug;

	public CodeGenerator(Environment env) {
		this(env, false);
	}

	public CodeGenerator(Environment env, boolean debug) {
		this.env = env;
	}

	public boolean generateCode() {
		try {

		} catch (Exception ex) {
			return false;
		}

		return true;
	}

	private void log(String msg) {
		if (debug) {
			System.err.println(msg);
		}
	}

}
