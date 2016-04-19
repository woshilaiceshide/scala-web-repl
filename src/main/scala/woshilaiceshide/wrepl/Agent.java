package woshilaiceshide.wrepl;

public class Agent {

	public static class AgentConfigException extends RuntimeException {

		private static final long serialVersionUID = 207893945872804006L;

		public AgentConfigException(String configName) {
			super("no proper value for " + configName);
		}

	}

	private Agent() {
	}

	public static String AGENT_LISTEN_ADDRESS = "wrepl.listen.address";
	public static String DEFAULT_AGENT_LISTEN_ADDRESS = "0.0.0.0";

	public static String getAgentListenAddress() {
		String raw = System.getProperty(AGENT_LISTEN_ADDRESS);
		if (null != raw) {
			return raw;
		} else {
			return DEFAULT_AGENT_LISTEN_ADDRESS;
		}
	}

	public static String AGENT_LISTEN_PORT = "wrepl.listen.port";
	public static int DEFAULT_AGENT_LISTEN_PORT = 8484;

	public static int getAgentListenPort() {
		String raw = System.getProperty(AGENT_LISTEN_PORT);
		int port = DEFAULT_AGENT_LISTEN_PORT;
		if (null != raw) {
			try {
				port = Integer.parseInt(raw);
			} catch (NumberFormatException nfe) {

			}
		}
		return port;
	}

	public static String AGENT_AUTHENTICATE_USER = "wrepl.authenticate.user";

	public static String getAgentAuthenticateUser() {
		String raw = System.getProperty(AGENT_AUTHENTICATE_USER);
		if (null == raw) {
			throw new AgentConfigException(AGENT_AUTHENTICATE_USER);
		} else {
			return raw;
		}
	}

	public static String AGENT_AUTHENTICATE_PASSWORD = "wrepl.authenticate.password";

	public static String getAgentAuthenticatePassword() {
		String raw = System.getProperty(AGENT_AUTHENTICATE_PASSWORD);
		if (null == raw) {
			throw new AgentConfigException(AGENT_AUTHENTICATE_PASSWORD);
		} else {
			return raw;
		}
	}

	public static void main(String args[]) {
		premain("");
	}

	public static void premain(String agentArgs) {

		final String address = getAgentListenAddress();
		final int port = getAgentListenPort();
		final String user = getAgentAuthenticateUser();
		final String password = getAgentAuthenticatePassword();

		final Server server = Server.newSimpleServer(address, port, user, password);

		System.out.println("starting Scala-Web-REPL...");
		server.start(true);
		System.out.println(String.format("Scala-Web-REPL started at %s:%s", address, port));

		Runtime.getRuntime().addShutdownHook(new Thread() {

			@Override
			public void run() {
				server.stop(3000);
			}
		});
	}
}