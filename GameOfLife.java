import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.util.Map;
import java.util.Random;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created 10.06.2016
 */

/**
 * @author Marcel Vogel</a>
 */
public class GameOfLife extends Application {
	
	public static void main(String[] args) {
		Application.launch(args);
	}
	
	private int size = 1000;
	
	private double popSize = 1;
	
	private boolean[][] lastGen = new boolean[size][size];
	private boolean[][] pop = new boolean[size][size];
	
	private Canvas canvas;
	private boolean isRunning = false;
	
	private Stack<Runnable> syncedRunnables = new Stack<>();
	
	private Map<Integer, Boolean[][]> threadChunks = new ConcurrentHashMap<>();
	
	private int cores;
	
	@Override
	public void start(Stage primaryStage) throws Exception {
		canvas = new Canvas(size * popSize, size * popSize);
		AnchorPane root = new AnchorPane(canvas);
		
		Scene scene = new Scene(root);
		
		primaryStage.setScene(scene);
		primaryStage.show();
		
		clearPop();
		
		AnimationTimer timer = new AnimationTimer() {
			long timeLastFrame = 0;
			
			@Override
			public void handle(long now) {
//				double deltaTime = TimeUnit.NANOSECONDS.toMillis(now - timeLastFrame);
//				timeLastFrame = now;
//				if (deltaTime != 0) {
//					System.out.println(Math.round((TimeUnit.SECONDS.toMillis(1) / deltaTime)));
//				}
				render();
			}
		};
		timer.start();
		
		Button button = new Button("Play");
		
		button.setOnAction((e) -> {
			isRunning = !isRunning;
			button.setText(isRunning ? "Stop" : "Play");
		});
		
		Button clear = new Button("Clear");
		
		clear.setOnAction((e) -> {
			clearPop();
		});
		
		Button random = new Button("Random");
		
		random.setOnAction((e) -> {
			fillPopRandom();
		});
		
		Button gun = new Button("Gun");
		
		gun.setOnAction((e) -> {
			int randomBase = size / 3;
			
			createGliderGun((int) Math.random() * randomBase, (int) Math.random() * randomBase);
		});
		
		HBox box = new HBox(button, clear, random, gun);
		root.getChildren().add(box);
		canvas.setOnMouseClicked((e) -> {
			int x = (int) (e.getX() / popSize);
			int y = (int) (e.getY() / popSize);
			if (e.getButton() == MouseButton.PRIMARY) {
				Random ran = new Random();
				int newCells = ran.nextInt(20) + 15;
				for (int i = 0; i < newCells; i++) {
					int sX = ran.nextInt(10) - 5;
					int sY = ran.nextInt(10) - 5;
					setState(x + sX, y + sY, true);
				}
				
			} else if (e.getButton() == MouseButton.SECONDARY) {
				createGliderGun(x, y);
			} else if (e.getButton() == MouseButton.MIDDLE) {
				createGlider(x, y);
			}
			
		});
		
		cores = /*Math.min(Runtime.getRuntime().availableProcessors(), 4)*/4;
		
		int sizePerThread = size / cores * 2;
		System.out.println(sizePerThread);
		int startX = 0;
		int startY = 0;
		
		for (int i = 0; i < cores; i++) {
			final int tStartX = startX;
			final int tStartY = startY;
			final int threadNumber = i;
			Thread t = new Thread() {
				@Override
				public void run() {
					while (true) {
						
						if (isRunning && !threadChunks.containsKey(threadNumber)) {
							Boolean[][] updateChunk = updateChunk(tStartX, tStartY, sizePerThread);
							threadChunks.put(threadNumber, updateChunk);
						} else {
							try {
								Thread.sleep(1);
							} catch (InterruptedException e) {}
						}
					}
				}
			};
			t.setDaemon(true);
			t.start();
			
			startX += sizePerThread;
			if (startX > size - sizePerThread) {
				startX = 0;
				startY += sizePerThread;
			}
		}
		
		Thread primaryThread = new Thread() {
			
			@Override
			public void run() {
				while (true) {
					try {
						Thread.sleep(2);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					if (allThreadsFinished()) {
						boolean[][] newPop = new boolean[size][size];
						int sX = 0;
						int sY = 0;
						int chunkSize = size / cores * 2;
						
						for (int j = 0; j < cores; j++) {
							Boolean[][] chunk = threadChunks.get(j);
							
							for (int x = 0; x < sizePerThread; x++) {
								for (int y = 0; y < sizePerThread; y++) {
									newPop[sX + x][sY + y] = Boolean.valueOf(chunk[x][y]);
								}
							}
							
							sX += chunkSize;
							if (sX > size - chunkSize) {
								sX = 0;
								sY += chunkSize;
							}
							
						}
						
						pop = newPop;
						while (!syncedRunnables.isEmpty()) {
							syncedRunnables.pop().run();
						}
						threadChunks.clear();
					}
					
				}
			}
			
		};
		primaryThread.setDaemon(true);
		primaryThread.start();
		
	}
	
	private boolean allThreadsFinished() {
		for (int i = 0; i < cores; i++) {
			if (!threadChunks.containsKey(i)) {
				return false;
			}
		}
		return true;
	}
	
	private void clearPop() {
		// clear pop
		
		syncedRunnables.push(() -> {
			for (int x = 0; x < size; x++) {
				for (int y = 0; y < size; y++) {
					pop[x][y] = false;
				}
			}
		});
		
	}
	
	private void fillPopRandom() {
		syncedRunnables.push(() -> {
			for (int x = 0; x < size; x++) {
				for (int y = 0; y < size; y++) {
					if (Math.random() * 100 > 85) {
						pop[x][y] = true;
					}
				}
			}
		});
	}
	
	private void createGliderGun(int offsetX, int offsetY) {
		syncedRunnables.push(() -> {
			int baseX = offsetX;
			int baseY = offsetY;
			
			// left base
			pop[baseX + 2][baseY + 6] = true;
			pop[baseX + 3][baseY + 6] = true;
			pop[baseX + 2][baseY + 7] = true;
			pop[baseX + 3][baseY + 7] = true;
			baseX++;
			// left struct
			pop[baseX + 13][baseY + 4] = true;
			pop[baseX + 14][baseY + 4] = true;
			pop[baseX + 12][baseY + 5] = true;
			pop[baseX + 11][baseY + 6] = true;
			pop[baseX + 11][baseY + 7] = true;
			pop[baseX + 11][baseY + 8] = true;
			pop[baseX + 12][baseY + 9] = true;
			pop[baseX + 13][baseY + 10] = true;
			pop[baseX + 14][baseY + 10] = true;
			
			pop[baseX + 16][baseY + 5] = true;
			pop[baseX + 17][baseY + 6] = true;
			pop[baseX + 17][baseY + 7] = true;
			pop[baseX + 17][baseY + 8] = true;
			pop[baseX + 18][baseY + 7] = true;
			pop[baseX + 16][baseY + 9] = true;
			pop[baseX + 15][baseY + 7] = true;
			
			// rigt struct
			baseX--;
			pop[baseX + 26][baseY + 2] = true;
			pop[baseX + 26][baseY + 3] = true;
			pop[baseX + 26][baseY + 7] = true;
			pop[baseX + 26][baseY + 8] = true;
			
			pop[baseX + 24][baseY + 3] = true;
			pop[baseX + 23][baseY + 4] = true;
			pop[baseX + 23][baseY + 5] = true;
			pop[baseX + 23][baseY + 6] = true;
			pop[baseX + 22][baseY + 4] = true;
			pop[baseX + 22][baseY + 5] = true;
			pop[baseX + 22][baseY + 6] = true;
			pop[baseX + 24][baseY + 7] = true;
			
			// right base
			pop[baseX + 36][baseY + 4] = true;
			pop[baseX + 37][baseY + 4] = true;
			pop[baseX + 37][baseY + 5] = true;
			pop[baseX + 36][baseY + 5] = true;
		});
	}
	
	private void createGlider(int offsetX, int offsetY) {
		syncedRunnables.push(() -> {
			int baseX = offsetX;
			int baseY = offsetY;
			pop[baseX + 1][baseY + 2] = true;
			pop[baseX + 3][baseY + 1] = true;
			pop[baseX + 3][baseY + 2] = true;
			pop[baseX + 3][baseY + 3] = true;
			pop[baseX + 2][baseY + 3] = true;
		});
	}
	
	private Boolean[][] updateChunk(int startX, int startY, int chunkSize) {
		
		Boolean[][] nextGen = new Boolean[chunkSize][chunkSize];
		
		for (int x = 0; x < chunkSize; x++) {
			
			for (int y = 0; y < chunkSize; y++) {
				
				boolean alive = pop[x + startX][y + startY];
				
				int aliveNeightbours = getAliveNeightbours(getNeightbours(x + startX, y + startY));
				nextGen[x][y] = Boolean.valueOf(alive);
				if (alive && (aliveNeightbours < 2 || aliveNeightbours > 3)) {
					nextGen[x][y] = false;
				}
				if (!alive && aliveNeightbours == 3) {
					nextGen[x][y] = true;
				}
			}
		}
		
		return nextGen;
	}
	
	private void update() {
		if (isRunning) {
			boolean[][] nextGen = new boolean[size][size];
			
			for (int x = 0; x < size; x++) {
				
				for (int y = 0; y < size; y++) {
					
					boolean alive = pop[x][y];
					
					int aliveNeightbours = getAliveNeightbours(getNeightbours(x, y));
					nextGen[x][y] = alive;
					if (alive && (aliveNeightbours < 2 || aliveNeightbours > 3)) {
						nextGen[x][y] = false;
					}
					if (!alive && aliveNeightbours == 3) {
						nextGen[x][y] = true;
					}
				}
			}
			
			pop = nextGen;
			
		}
		
	}
	
	private void render() {
		GraphicsContext context2d = canvas.getGraphicsContext2D();
		
		boolean[][] toDrawPop = pop.clone();
		

		
		int fills = 0;
		long fillTime = 0;
		
		int clears = 0;
		long clearTime = 0;
		
		for (int x = 0; x < size; x++) {
			for (int y = 0; y < size; y++) {
				boolean alive = toDrawPop[x][y];
				if (alive != lastGen[x][y]) {
					if (alive) {
						fills++;
						fillTime += System.nanoTime();
						context2d.fillRect(x * popSize, y * popSize, popSize, popSize);
					} else {
						clears++;
						clearTime += System.nanoTime();
						context2d.clearRect(x * popSize, y * popSize, popSize, popSize);
					}
				}
			}
		}
		

		
//		
//		long fillAvg = fills != 0 ? fillTime / fills : 0;
//		
//		long clearAvg = clears != 0 ? clearTime / clears : 0;
		
		System.out.println("Frame: Draw Calls " + (fills + clears));
		
		lastGen = toDrawPop;
	}
	
	private boolean[] getNeightbours(int x, int y) {
		boolean[] neightbours = new boolean[8];
		neightbours[0] = getState(x - 1, y - 1);
		neightbours[1] = getState(x, y - 1);
		neightbours[2] = getState(x + 1, y - 1);
		neightbours[3] = getState(x - 1, y);
		neightbours[4] = getState(x + 1, y);
		neightbours[5] = getState(x - 1, y + 1);
		neightbours[6] = getState(x, y + 1);
		neightbours[7] = getState(x + 1, y + 1);
		return neightbours;
	}
	
	private int getAliveNeightbours(boolean[] neightbours) {
		int count = 0;
		for (boolean b : neightbours) {
			if (b) {
				count++;
			}
		}
		return count;
	}
	
	private boolean getState(int x, int y) {
		if (x < 0 || y < 0 || x >= size || y >= size) {
			return false;
		}
		return pop[x][y];
	}
	
	private void setState(int x, int y, boolean state) {
		if (x < 0 || y < 0 || x >= size || y >= size) {
			return;
		}
		pop[x][y] = state;
	}
	
}
