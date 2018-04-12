import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class P2PHandler implements Runnable {
	peerProcess peerP;
	boolean run = true;

	public P2PHandler(peerProcess pP) {
		this.peerP = pP;
	}

	@Override
	public void run() {
		List<Socket> created = new ArrayList<>();
		ExecutorService fileHandler = Executors.newCachedThreadPool();// newSingleThreadExecutor();
		while (run) {

			if (Thread.interrupted()) {
				run = false;
				fileHandler.shutdownNow();
				System.out.println("Executor service killing P2PHandler");
				// kill the thread

				for (Socket peers : peerP.list.values()) {
					try {
						peers.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

				break;
			}
			synchronized (peerP) {
				for (Socket peers : peerP.list.values()) {
					if (created.size() == peerP.list.size()) {
						break;
					}
					if (!created.contains(peers)) {
						// create a new thread for file handling
						created.add(peers);
						BitTorrentSend sockThread = new BitTorrentSend(peerP, peers);
						fileHandler.execute(sockThread);
						ArrayList<Boolean> list = new ArrayList<Boolean>(peerP.totalPieces);
						for (int i = 0; i < peerP.totalPieces; i++) {
							list.add(false);
						}
						peerP.neighPieces.put(peers, list);
					}
				}
			}
		}
	}

}
