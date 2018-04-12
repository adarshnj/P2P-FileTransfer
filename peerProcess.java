import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class peerProcess {

	static int ind = 0;
	public int peerID;
	public int port;
	PrintWriter writer;
	File dir;

	ArrayList<Socket> preferredneighbors = new ArrayList<>();
	ArrayList<Socket> interestedneighbors = new ArrayList<>();
	ArrayList<Socket> Unchokedneighbors = new ArrayList<>();
	ArrayList<Integer> requestedPieces = new ArrayList<>();

	HashMap<Socket, BufferedOutputStream> getoutstream = new HashMap<>();

	Socket optUnChkPeer;
	int NoofPNeig;
	int unChkItv;
	int optUnChkItv;
	String fileName;
	long fileSize;
	int pieceSize;
	int totalPieces;
	int lastPiece;
	int lPsize;

	HashMap<Socket, Long> downsl = new HashMap<>();

	static boolean allPeersdHaveFile = true;
	int peersHavingFile = 0;

	ArrayList<Boolean> pieces = new ArrayList<>();
	HashMap<Socket, ArrayList<Integer>> missingPieces = new HashMap<>();
	HashMap<Socket, ArrayList<Boolean>> neighPieces = new HashMap<>();
	TreeMap<Integer, Socket> list = new TreeMap<>();
	TreeMap<Integer, peerList> peers = new TreeMap<>();

	public synchronized void putList(int id, Socket s) {
		list.put(id, s);
	}

	int socketNum = 0;
	String dirname;
	boolean hasCompleteFile = false;
	private ObjectInputStream in;
	private ObjectOutputStream out;

	class peerList {
		int id;
		String server;
		int port;
		boolean hasFile;

		public peerList(String[] set) {
			this.id = Integer.parseInt(set[0]);
			this.server = set[1];
			this.port = Integer.parseInt(set[2]);
			this.hasFile = (set[3].equals("1")) ? true : false;
		}
	}

	public static void main(String[] args) throws InterruptedException {
		peerProcess pp = new peerProcess();
		pp.peerID = Integer.parseInt(args[0]);

		pp.commoninfo(pp);
		pp.peerinfo(pp);

		// Create log file and directory to store file parts
		pp.create();

		ExecutorService allThreads = Executors.newCachedThreadPool();

		// ExecutorService listen = Executors.newSingleThreadExecutor();
		Listener runnable = new Listener(pp.port, pp);
		// listen.execute(runnable);

		// ExecutorService handler = Executors.newSingleThreadExecutor();
		P2PHandler handle = new P2PHandler(pp);
		// handler.execute(handle);

		// ExecutorService timeHandler = Executors.newCachedThreadPool();
		Tracktime timetrack = new Tracktime(pp);
		// timeHandler.execute(timetrack);

		allThreads.execute(runnable);
		allThreads.execute(handle);
		allThreads.execute(timetrack);

		synchronized (pp) {
			// Create tcp socket with all the active peers
			pp.tcpSocket(pp);

		}

		while (allPeersdHaveFile) { // loop until allpeershavefile
			synchronized (pp) {
				if (pp.peersHavingFile == pp.peers.size()) {
					allPeersdHaveFile = false;
				}
			}
		}
		allThreads.shutdownNow();
	}

	private void commoninfo(peerProcess pp) {
		try {
			BufferedReader in = new BufferedReader(new FileReader("Common.cfg"));
			String s;
			while ((s = in.readLine()) != null) {
				String[] var = s.split(" ");
				switch (var[0]) {
				case "NumberOfPreferredNeighbors":
					NoofPNeig = Integer.parseInt(var[1]);
					break;
				case "UnchokingInterval":
					unChkItv = Integer.parseInt(var[1]);
					break;
				case "OptimisticUnchokingInterval":
					optUnChkItv = Integer.parseInt(var[1]);
					break;
				case "FileName":
					fileName = var[1];
					break;
				case "FileSize":
					fileSize = Long.parseLong(var[1]);
					break;
				case "PieceSize":
					pieceSize = Integer.parseInt(var[1]);
					lPsize = (int) (fileSize % pieceSize);
					totalPieces = (int) ((lPsize > 0) ? ((fileSize / pieceSize) + 1) : (fileSize / pieceSize));
					lastPiece = totalPieces;
					for (int i = 0; i < totalPieces; i++)
						pieces.add(i, false);
					break;
				}
			}
			in.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void peerinfo(peerProcess pp) {
		try {
			BufferedReader in = new BufferedReader(new FileReader("PeerInfo.cfg"));
			String s;
			while ((s = in.readLine()) != null) {
				String[] var = s.split(" ");
				peerList p = pp.new peerList(var);
				pp.peers.put(p.id, p);

				if (p.id == pp.peerID && p.hasFile == true) {

					File checkFile = new File("peer_" + pp.peerID + "//" + fileName);
					// File created
					// Check if file physically exists

					if (checkFile.exists() && !checkFile.isDirectory()) {
						hasCompleteFile = true;
						for (int i = 0; i < totalPieces; i++) {
							pieces.set(i, true);
						}
						pp.peersHavingFile++;
					} else {
						hasCompleteFile = false;
						checkFile.delete();
						return;
					}
				}
			}

			try {
				in.close();
				pp.port = pp.peers.get(peerID).port;
			} catch (Exception e) {
				System.out.println("Peer id missing in peerinfo.cfg");
				// TODO: handle exception
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void create() {
		// TODO Auto-generated method stub
		try {
			writer = new PrintWriter("log_peer_" + peerID + ".log", "UTF-8");
			dirname = "peer_" + peerID;
			dir = new File(dirname);
			dir.mkdir();

			if (hasCompleteFile == false) {
				RandomAccessFile raf = null;
				String filepath = "peer_" + peerID + "//tmp" + fileName;
				try {
					raf = new RandomAccessFile(filepath, "rw");
					try {
						raf.setLength(fileSize);
						raf.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void tcpSocket(peerProcess pp) {
		try {
			for (int i : pp.peers.keySet()) {
				if (pp.peerID != i) {
					try {
						Socket socket = new Socket(pp.peers.get(i).server, pp.peers.get(i).port);
						out = new ObjectOutputStream(socket.getOutputStream());
						out.writeObject(new Handshake(pp.peerID).msg);
						out.flush();

						in = new ObjectInputStream(socket.getInputStream());
						if (((String) in.readObject()).equals(new Handshake(i).msg)) { // handshake successful
							pp.putList(i, socket);
							writer.print(
									"\n" + (new Date()) + ": Peer " + pp.peerID + " makes a connection to peer " + i);
							writer.flush();
						}
					} catch (Exception e) {
						continue;
					}
				} else {
					break;
				}
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
