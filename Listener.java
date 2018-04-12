import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;

public class Listener implements Runnable {
	int port;
	ServerSocket ssocket;
	Socket peer;
	peerProcess pP;
	boolean isTerminated = false;
	private ObjectInputStream in;
	private ObjectOutputStream out;

	Listener(int port, peerProcess pP) {
		this.port = port;
		this.pP = pP;
	}

	@Override
	public void run() {
		int peerId = pP.peerID;

		try {
			ssocket = new ServerSocket(port);
			while (!isTerminated) {
				if (Thread.interrupted()) {
					System.out.println("Executor service killing listener");
					// kill the thread
					isTerminated = true;
					break;
				}
				ssocket.setSoTimeout(5000);
				try {
					peer = ssocket.accept();
					String input = null;
					if (peer != null) {
						out = new ObjectOutputStream(peer.getOutputStream());
						out.writeObject(new Handshake(peerId).msg);// send handshake message
						out.flush();
						try {
							in = new ObjectInputStream(peer.getInputStream());
							input = (String) in.readObject();
						} catch (ClassNotFoundException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						int connectedPID = Integer.parseInt(input.substring(28));// Integer.parseInt(input);
						{
							pP.putList(connectedPID, peer);
							pP.writer.print(
									"\n" + new Date() + ": Peer " + peerId + " is connected to peer " + connectedPID);
							pP.writer.flush();
						}
					}
				} catch (IOException e) {
					// after accept timeout's continue listening
				}

			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
