package de.dailab.jiacvi.aot.gridworld;

import java.io.InputStream;
import java.util.*;



/**
 * Helper class for some utility functions.
 */
public class Util {

	public static final Random random = new Random();

	/**
	 * Create a random Gridworld game using the given parameters
	 */
	public static GridworldGame createRandomGame(int size, int turns, int numOrders, int numWorkers, String... brokerIds) {
		GridworldGame game = new GridworldGame(new Position(size, size), 0, turns,
				new HashMap<>(), new HashMap<>(), new HashSet<>(), "logs/game-"+random.nextInt(Integer.MAX_VALUE)+".log");
		
		// initialize Broker(s)
		for (int b = 0; b < brokerIds.length; b++) {

			Broker broker = new Broker(brokerIds[b], new ArrayList<>(),
					new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>());

			// initialize Workers for this Broker
			for (int w = 0; w < numWorkers; w++) {

				Worker worker = new Worker(String.format("w%d%d", b, w), broker.getId(),
						Position.Companion.randomPosition(game.getSize()), 0, 0, new ArrayList<>());
				broker.getWorkers().add(worker);
			}
			game.getBrokers().put(broker.getId(), broker);
		}
		
		// initialize random Orders
		for (int o = 0; o < numOrders; o++) {

			var created = Util.random.nextInt(game.getMaxTurns() - size);
			Order order = new Order(String.format("o%d", o), Position.Companion.randomPosition(game.getSize()),
					10 + Util.random.nextInt(20), created, created + Util.random.nextInt(size + size),
					1, null);
			game.getOrders().put(order.getId(), order);
		}
		return game;
	}
	
	/**
	 * Load gridworld game from the given file, to be found on the class path,
	 * more specifically in the resources/grids directory; the filename must
	 * be absolute (relative to the class-path root) and should include the
	 * root "/", e.g. "/grid/somegridfile.grid".
	 */
	public static GridworldGame loadGameFromFile(String filename, String... brokerIds) {
		InputStream is = Util.class.getResourceAsStream(filename);
		if (is == null) {
			throw new IllegalArgumentException("Invalid grid file: " + filename);
		}
		try (Scanner scanner = new Scanner(is)) {

			// first line: general game parameters
			int width = scanner.nextInt();
			int height = scanner.nextInt();
			int turns = scanner.nextInt();
			int numOrders = scanner.nextInt();
			int numBrokers = scanner.nextInt();
			int numWorkers = scanner.nextInt();
			int numAssigned = scanner.nextInt();

			final GridworldGame game = new GridworldGame(
					new Position(width, height), 0, turns,
					new HashMap<>(), new HashMap<>(), new HashSet<>(), "logs/game-"+random.nextInt(Integer.MAX_VALUE)+".log"
			);

			// next height lines: grid and obstacles
			for (int y = 0; y < height; y++) {
				String line = scanner.next(String.format(".{%d}", width));
				for (int x = 0; x < width; x++) {
					if (line.charAt(x) == '#') {
						game.getObstacles().add(new Position(x, y));
					}
				}
			}

			// next numOrders lines: the orders
			for (int o = 0; o < numOrders; o++) {
				String id = scanner.next("\\w+");
				int x = scanner.nextInt();
				int y = scanner.nextInt();
				int created  = scanner.nextInt();
				int deadline = scanner.nextInt();
				int value    = scanner.nextInt();
				int penalty  = scanner.nextInt();

				Order order = new Order(id, new Position(x, y), value, created, penalty, deadline, null);
				game.getOrders().put(order.getId(), order);
			}

			// initialize Broker(s)
			for (int b = 0; b < numBrokers; b++) {

				Broker broker = new Broker(brokerIds[b], new ArrayList<>(),
						new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>());

				// initialize Workers for this Broker
				for (int w = 0; w < numWorkers; w++) {
					String id = scanner.next("\\w+");
					int x = scanner.nextInt();
					int y = scanner.nextInt();

					Worker worker = new Worker(id, broker.getId(), new Position(x, y), 0, 0, new ArrayList<>());
					broker.getWorkers().add(worker);
				}
				game.getBrokers().put(broker.getId(), broker);
			}

			// add initially assigned orders to workers, if any
			for (int a = 0; a < numAssigned; a++) {
				String workerId = scanner.next("\\w+");
				String orderId  = scanner.next("\\w+");
				// creating a Map<String, Worker> would be faster, but we won't ever have that many workers...
				game.getBrokers().values().stream()
						.flatMap(b -> b.getWorkers().stream())
						.filter(w -> w.getId().equals(workerId)).findFirst().get()
						.getAssignedOrders().add(orderId);
			}
			return game;
		}
	}

}
