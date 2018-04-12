import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

public class Tracktime extends Thread implements Runnable {

	class MyComparator implements Comparator<Object> {

		Map<Socket, Long> map;

		public MyComparator(Map<Socket, Long> map) {
			this.map = map;
		}

		public int compare(Object o1, Object o2) {

			if (map.get(o2) == map.get(o1))
				return 1;
			else
				return ((Long) map.get(o1)).compareTo((Long) map.get(o2));

		}
	}

	peerProcess peerProc;
	int unchk;
	int optunchk;
	boolean times = true;
	long startTime;
	boolean first;
	long chkTime;
	ArrayList<Socket> tempSocket;
	ArrayList<Socket> prevPref;
	Socket prevOptUnck;

	public Tracktime(peerProcess pP) {
		this.startTime = System.currentTimeMillis();
		this.peerProc = pP;
		this.unchk = pP.unChkItv * 1000;
		this.optunchk = pP.optUnChkItv * 1000;
		this.tempSocket = new ArrayList<Socket>();
	}

	@Override
	public void run() {

		byte[] bitml = new byte[4];
		byte bittyp;

		while (times) {
			if (Thread.interrupted()) {
				times = false;
				System.out.println("Executor service killing time tracker");
				break;
			}

			chkTime = System.currentTimeMillis();
			BufferedOutputStream unbos = null;

			synchronized (peerProc) {
				prevOptUnck = peerProc.optUnChkPeer;
				prevPref = new ArrayList<>(peerProc.preferredneighbors);

				// Set Optimistically unchoked peer

				if (((chkTime - startTime) % optunchk) == 0) {
					if (!peerProc.interestedneighbors.isEmpty()) {
						int randKey = (int) Math.floor(Math.random() * peerProc.interestedneighbors.size());
						while (peerProc.interestedneighbors.size() > 1
								&& peerProc.interestedneighbors.get(randKey) != prevOptUnck) {
							randKey = (int) Math.floor(Math.random() * peerProc.interestedneighbors.size());
						}

						peerProc.optUnChkPeer = peerProc.interestedneighbors.get(randKey);

						if (prevOptUnck != peerProc.optUnChkPeer && !peerProc.preferredneighbors.contains(prevOptUnck)
								&& peerProc.interestedneighbors.size() > peerProc.NoofPNeig) {
							if (prevOptUnck != null && !peerProc.preferredneighbors.contains(prevOptUnck)) {
								unbos = peerProc.getoutstream.get(prevOptUnck);
								if (unbos != null) {
									try {
										bitml = ByteBuffer.allocate(4).putInt(1).array().clone();
										bittyp = (byte) 0;

										unbos.write(bitml);
										unbos.write(bittyp);
										unbos.flush();

									} catch (IOException e) {
										// TODO Auto-generated catch block
										e.printStackTrace();
									}

									peerProc.Unchokedneighbors.remove(prevOptUnck);
								}
							}
							unbos = peerProc.getoutstream.get(peerProc.optUnChkPeer);
							if (unbos != null) {
								try {
									bitml = ByteBuffer.allocate(4).putInt(1).array().clone();
									bittyp = (byte) 1;

									unbos.write(bitml);
									unbos.write(bittyp);
									unbos.flush();

								} catch (IOException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								peerProc.Unchokedneighbors.add(peerProc.optUnChkPeer);
							}
							// Log
							peerProc.writer.print("\n" + (new Date()) + ": Peer " + peerProc.peerID
									+ " has the optimistically unchoked neighbors ");
							for (int id : peerProc.list.keySet()) {
								if (peerProc.optUnChkPeer == peerProc.list.get(id)) {
									peerProc.writer.print(id);
								}
							}
							peerProc.writer.println();
							peerProc.writer.flush();
						}
						for (Socket s : tempSocket) {
							peerProc.Unchokedneighbors.add(s);
						}
					}
				}

				// Preferred Neighbors

				int preneighs = peerProc.NoofPNeig;
				int intneigsize = peerProc.interestedneighbors.size();

				if (((chkTime - startTime) % unchk) == 0) {

					if (peerProc.hasCompleteFile) {

						ArrayList<Integer> arrayList = new ArrayList<>();

						for (int i = 0; i < intneigsize; i++) {
							arrayList.add(i);
						}

						// shuffle list
						Collections.shuffle(arrayList);

						int isize = intneigsize;
						int added = 0;

						peerProc.preferredneighbors.clear();
						while (added <= preneighs && isize > 0) {
							peerProc.preferredneighbors.add(peerProc.interestedneighbors.get(arrayList.get(--isize)));
							added++;
						}
					} else {

						// get the download rates
						// sort by rates
						MyComparator comparator = new MyComparator(peerProc.downsl);

						Map<Socket, Long> newMap = new TreeMap<Socket, Long>(comparator);
						newMap.putAll(peerProc.downsl);

						peerProc.preferredneighbors.clear();
						int added = 0;
						for (Socket s : newMap.keySet()) {
							if (added <= preneighs) {
								peerProc.preferredneighbors.add(s);
								added++;
							}
						}
					}

					// Unchoke preferred neighbors if previously unchoked
					for (Socket s : peerProc.preferredneighbors) {
						if (peerProc.Unchokedneighbors.contains(s)) {
							// do nothing
						} else {

							unbos = peerProc.getoutstream.get(s);
							if (unbos != null) {
								try {
									bitml = ByteBuffer.allocate(4).putInt(1).array().clone();
									bittyp = (byte) 1;

									unbos.write(bitml);
									unbos.write(bittyp);
									unbos.flush();

								} catch (IOException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}

								peerProc.Unchokedneighbors.add(s);
							}
						}
					}

					// End of unchoking

					// Choke all neighbors who are neither preferred nor opt unchk
					// prev preferred neighbors are unchoked here if not preferred now

					for (Socket s : peerProc.Unchokedneighbors) {
						if (peerProc.preferredneighbors.contains(s) || s == peerProc.optUnChkPeer) {
							// Do nothing
						} else {
							unbos = peerProc.getoutstream.get(s);
							if (unbos != null) {
								try {
									bitml = ByteBuffer.allocate(4).putInt(1).array().clone();
									bittyp = (byte) 0;

									unbos.write(bitml);
									unbos.write(bittyp);
									unbos.flush();
								} catch (IOException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}

								tempSocket.add(s);
							}
						}
					}

					for (Socket s : tempSocket) {
						peerProc.Unchokedneighbors.remove(s);
					}
					tempSocket.clear();

					first = true;
					boolean changed = false;
					for (Socket s : peerProc.preferredneighbors) {
						if (!prevPref.contains(s)) {
							changed = true;
						}
					}
					if (changed) {
						for (int id : peerProc.list.keySet()) {
							Socket s = peerProc.list.get(id);
							if (peerProc.preferredneighbors.contains(s)) {
								if (first) {
									peerProc.writer.print("\n" + (new Date()) + ": Peer " + peerProc.peerID
											+ " has the preferred neighbors ");
									first = false;
								} else {
									peerProc.writer.print(",");
								}
								peerProc.writer.print(id);
							}
						}
					}

				}
				peerProc.notifyAll();
			}
		}
	}
}
