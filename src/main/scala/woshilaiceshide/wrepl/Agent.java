package woshilaiceshide.wrepl;

public class Agent {

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
	public static int DEFAULT_AGENT_LISTEN_PORT = 8181;

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

	public static void main(String args[]) {
		premain("");
	}

	public static void premain(String agentArgs) {

		final String address = getAgentListenAddress();
		final int port = getAgentListenPort();

		final Server server = Server.newServer(address, port);

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