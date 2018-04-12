import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

import javax.imageio.stream.FileImageInputStream;

public class BitTorrentSend extends Thread implements Runnable {
	peerProcess peerProcessHandle;
	Socket peer;
	String sendData;
	boolean p2psend;
	boolean firstRun;
	int peerid;
	long startRateTracker;
	long stopRateTracker;
	long rate;
	boolean bitset = true;

	RandomAccessFile raf = null;

	byte[] inStream;
	int reqPiece;

	ObjectInputStream in;
	ObjectOutputStream out;
	BufferedInputStream bis;
	BufferedOutputStream bos;

	int Type;
	int size;
	int pieces;
	ArrayList<Boolean> neighPiece = new ArrayList<>();
	ArrayList<Integer> missingPiece = new ArrayList<>();
	private int checkhave;

	public BitTorrentSend(peerProcess pP, Socket peer) {
		this.peerProcessHandle = pP;
		this.peer = peer;
		this.p2psend = true;
		this.firstRun = true;
		this.reqPiece = -1;
		this.rate = 0;

		for (int id : peerProcessHandle.list.keySet()) {
			if (peerProcessHandle.list.get(id) == peer) {
				this.peerid = id;
			}
		}
		peerProcessHandle.writer.flush();
	}

	@Override
	public void run() {
		try {
			out = new ObjectOutputStream(peer.getOutputStream());
			out.flush();
			in = new ObjectInputStream(peer.getInputStream());

			bos = new BufferedOutputStream(out, peerProcessHandle.pieceSize + 9);
			bos.flush();
			bis = new BufferedInputStream(in, peerProcessHandle.pieceSize + 9);

			peerProcessHandle.getoutstream.put(peer, bos);

			peerProcessHandle.missingPieces.put(peer, missingPiece);

			pieces = peerProcessHandle.pieces.size();
		} catch (IOException e3) {
			e3.printStackTrace();
		}
		while (p2psend) {
			Type = -1;
			if (Thread.interrupted()) {
				System.out.println("Executor service killing File Handler");
				peerProcessHandle.writer.close();
				p2psend = false;
				break;
			}
			synchronized (peerProcessHandle) {
				byte[] bitml = new byte[4];
				byte bittyp;
				byte[] bitpid = new byte[4];

				if (firstRun) {
					// First Message : Bit Field case 5
					if (peerProcessHandle.pieces.contains(true)) {
						int msglen = (int) ((peerProcessHandle.pieces.size() / 8));
						if (peerProcessHandle.pieces.size() % 8 > 0) {
							msglen += 1;
						}
						bitml = ByteBuffer.allocate(4).putInt((msglen + 1)).array().clone();
						bittyp = (byte) 5;
						// try {

						byte byt = 0;
						byte[] bytarr = new byte[msglen];
						int by = 0;
						int ind = 0;
						for (int p = 0; p < pieces; p++) {
							by = p % 8;
							if (peerProcessHandle.pieces.get(p) == false) {
								byt &= ~(1 << 7 - by);
								// set 0
							} else {
								byt |= (1 << 7 - by);
								// set 1
							}
							if (by == 7 || (p + 1 == pieces)) {
								try {
									bytarr[ind++] = (byte) (byt & 0xFF);
								} catch (Exception e) {
									e.printStackTrace();
								}
								byt = 0;
								if (p + 1 == pieces) {
									break;
								}
							}
						}
						try {
							bos.write(bitml);
							bos.write(bittyp);
							bos.write(bytarr);
							bos.flush();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					firstRun = false;
				} else {
					try {
						if (bis.available() >= 5) {
							byte[] Size = new byte[4];
							bis.read(Size);
							size = ByteBuffer.wrap(Size).getInt();
							inStream = new byte[size + 4];
							int i = 0;
							for (i = 0; i < 4; i++) {
								inStream[i] = Size[i];
							}
							i = 4;
							while (i < size + 4) {
								inStream[i++] = (byte) bis.read();
							}
							Type = inStream[4];
						}
					} catch (IOException ioe) {

					} catch (Exception e) {
						e.printStackTrace();
					}
				}

				switch (Type) {
				case 0: // choke
					if (size != 1)
						break;
					peerProcessHandle.preferredneighbors.remove(peer);
					try {
						peerProcessHandle.wait(1000);
					} catch (InterruptedException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					// clear reqpiece so as to request other neighbors when choked
					reqPiece = -1;
					// Log
					peerProcessHandle.writer.print(
							"\n" + (new Date()) + ": Peer " + peerProcessHandle.peerID + " is choked by " + peerid);
					peerProcessHandle.writer.flush();

					break;

				case 1: // unchoke
					if (size != 1)
						break;
					peerProcessHandle.preferredneighbors.add(peer);

					// check for interesting pieces and send request
					if (!missingPiece.isEmpty()) {
						if (reqPiece != -1) {
							try {
								bitml = ByteBuffer.allocate(4).putInt(5).array().clone();
								bittyp = (byte) 6;
								bitpid = ByteBuffer.allocate(4).putInt(reqPiece).array().clone();

								bos.write(bitml);
								bos.write(bittyp);
								bos.write(bitpid);
								bos.flush();
								startRateTracker = System.currentTimeMillis();
							} catch (IOException e) {
								e.printStackTrace();
							}

							// Do Nothing, waiting for previous piece
						} else {
							int pick = (int) Math.floor(Math.random() * missingPiece.size()); // random element from
							reqPiece = missingPiece.get(pick);
							while (peerProcessHandle.requestedPieces.contains(reqPiece) && missingPiece.size() > 1) {
								pick = (int) Math.floor(Math.random() * missingPiece.size()); // random element from
								reqPiece = missingPiece.get(pick);
							}
							try {
								bitml = ByteBuffer.allocate(4).putInt(5).array().clone();
								bittyp = (byte) 6;
								bitpid = ByteBuffer.allocate(4).putInt(reqPiece).array().clone();

								bos.write(bitml);
								bos.write(bittyp);
								bos.write(bitpid);
								bos.flush();

								startRateTracker = System.currentTimeMillis();
							} catch (IOException e) {
								e.printStackTrace();
							}
							// Log
							peerProcessHandle.writer.print("\n" + (new Date()) + ": Peer " + peerProcessHandle.peerID
									+ " is unchoked by " + peerid);
							peerProcessHandle.writer.flush();
						}
					}

					break;
				case 2: // interested

					if (size != 1)
						break;
					if (!peerProcessHandle.interestedneighbors.contains(peer))
					// add to interested neighbors list
					{
						peerProcessHandle.interestedneighbors.add(peer);

						peerProcessHandle.writer.print("\n" + (new Date()) + ": Peer " + peerProcessHandle.peerID
								+ " received the 'interested' message from " + peerid);
						peerProcessHandle.writer.flush();
					}
					break;
				case 3: // not interested
					if (size != 1)
						break;
					if (peerProcessHandle.interestedneighbors.contains(peer))
					// remove from interested neighbors list
					{
						peerProcessHandle.interestedneighbors.remove(peer);

						peerProcessHandle.writer.print("\n" + (new Date()) + ": Peer " + peerProcessHandle.peerID
								+ " received the 'not interested' message from " + peerid);
						peerProcessHandle.writer.flush();
					}
					break;
				case 4: // have

					if (size != 5)
						break;

					// send interested if not available
					// update local track of neighbor data
					// send not interested if no interesting piece available
					byte[] haveID = new byte[4];
					for (int i = 0; i < 4; i++) {
						haveID[i] = inStream[5 + i];
					}
					int hveid = ByteBuffer.wrap(haveID).getInt();

					if (checkhave == hveid) {
						stopRateTracker = System.currentTimeMillis();
						rate = stopRateTracker - startRateTracker;
						rate /= 2;

						peerProcessHandle.downsl.put(peer, rate);
					}

					// Log
					peerProcessHandle.writer.print("\n" + (new Date()) + ": Peer " + peerProcessHandle.peerID
							+ " received the 'have' message from " + peerid + " for the piece " + hveid);
					peerProcessHandle.writer.flush();

					ArrayList<Boolean> phandle = peerProcessHandle.neighPieces.get(peer);
					phandle.set(hveid, true);
					peerProcessHandle.neighPieces.put(peer, phandle);

					if (!phandle.contains(false)) {
						peerProcessHandle.peersHavingFile++;
					}

					if (!missingPiece.contains(hveid) && peerProcessHandle.pieces.get(hveid) == false) {

						missingPiece.add((Integer) hveid);
						peerProcessHandle.missingPieces.put(peer, missingPiece);
						// send interested
						try {
							bitml = ByteBuffer.allocate(4).putInt(1).array().clone();
							bittyp = (byte) 2;

							bos.write(bitml);
							bos.write(bittyp);
							bos.flush();
							reqPiece = hveid;
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					break;

				case 5: // bitfield
					if (bitset) {
						// set all pieces values
						char[] charArray;
						int i = 0;
						String pieceString;
						for (int j = 0; j < size - 1; j++) {
							pieceString = String.format("%08d",
									Integer.parseInt(Integer.toString(inStream[(5 + j)] & 0xFF, 2)));
							charArray = pieceString.toCharArray();
							for (int k = 0; k < 8; k++) {
								if (i < peerProcessHandle.totalPieces) {
									if (charArray[k] == '0') {
										neighPiece.add(false);
									} else {
										neighPiece.add(true);
										if (peerProcessHandle.pieces.get(i) == false) {
											missingPiece.add((Integer) i); // add piece to missing piece for this
																			// particular socket
										}
									}
									i++;
								}
							}
						}

						if (!neighPiece.contains(false)) {
							peerProcessHandle.peersHavingFile++;
						}

						peerProcessHandle.missingPieces.put(peer, missingPiece); // update globally

						peerProcessHandle.neighPieces.put(peer, neighPiece);
						if (missingPiece.size() > 0) {
							// send interested if the peer has interesting pieces
							try {
								bitml = ByteBuffer.allocate(4).putInt(1).array().clone();
								bittyp = (byte) 2;

								bos.write(bitml);
								bos.write(bittyp);
								bos.flush();
							} catch (IOException e) {
								e.printStackTrace();
							}

						} else {
							// send not interested
							try {
								bitml = ByteBuffer.allocate(4).putInt(1).array().clone();
								bittyp = (byte) 3;

								bos.write(bitml);
								bos.write(bittyp);
								bos.flush();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
						bitset = false; // set bitfield only once
					}
					break;

				// case 6 & 7 : File handling

				case 6: // request
					// send actual data
					byte[] pieceS = new byte[4];
					for (int i = 0; i < 4; i++) {
						pieceS[i] = (byte) inStream[5 + i];
					}
					int piece = ByteBuffer.wrap(pieceS).getInt();
					if (piece < 0 || piece > peerProcessHandle.totalPieces) {
						break;
					}

					// Check if preferred or opt unchoked neighbor before sending
					if (peerProcessHandle.preferredneighbors.contains(peer) || peerProcessHandle.optUnChkPeer == peer) {
						// Check the type of read to send the data

						byte[] actualPiece = null;
						// read from complete file
						String filepath = "peer_" + peerProcessHandle.peerID + "//tmp" + peerProcessHandle.fileName;
						if (peerProcessHandle.hasCompleteFile) {
							filepath = "peer_" + peerProcessHandle.peerID + "//" + peerProcessHandle.fileName;
						}

						try {
							raf = new RandomAccessFile(filepath, "r");
						} catch (FileNotFoundException e) {
							e.printStackTrace();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						long s;
						if (piece != (peerProcessHandle.lastPiece - 1)) {
							actualPiece = new byte[(int) peerProcessHandle.pieceSize];
							s = peerProcessHandle.pieceSize;
						} else {
							actualPiece = new byte[peerProcessHandle.lPsize];
							s = peerProcessHandle.lPsize;
						}

						try {
							raf.seek((int) (piece * peerProcessHandle.pieceSize));

							raf.read(actualPiece, 0, actualPiece.length);
							raf.close();
							// raf.readFully(actualPiece);
							bitml = ByteBuffer.allocate(4).putInt((int) (s + 5)).array().clone();
							bittyp = (byte) 7;
							bitpid = ByteBuffer.allocate(4).putInt(piece).array().clone();

							bos.flush();
							bos.write(bitml);
							bos.write(bittyp);
							bos.write(bitpid);
							bos.write(actualPiece);
							bos.flush();

							actualPiece = null;

							startRateTracker = System.currentTimeMillis();
							checkhave = piece;

						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					break;

				case 7: // piece
				{
					byte[] pieceID = new byte[4];
					for (int i = 0; i < 4; i++) {
						pieceID[i] = inStream[5 + i];
					}

					int pieceid = ByteBuffer.wrap(pieceID).getInt();
					if (!missingPiece.contains((Integer) pieceid)) {
						// break;
					}
					if (reqPiece == pieceid) {
						reqPiece = -1;

						stopRateTracker = System.currentTimeMillis();
						rate = stopRateTracker - startRateTracker;
						rate /= 2;

						peerProcessHandle.downsl.put(peer, rate);

						ArrayList<Integer> mp;
						for (Socket s : peerProcessHandle.missingPieces.keySet()) {
							mp = peerProcessHandle.missingPieces.get(s);
							if (mp.contains((Integer) pieceid)) {
								mp.remove((Integer) pieceid);
							}
							peerProcessHandle.missingPieces.put(s, mp); // update all missing pieces
						}

					} else {
						break;
					}

					byte[] partial = new byte[inStream.length - 9];// size - 5];// inStream.length - 9];
					int ind = 0;
					while (ind < inStream.length - 9) {
						partial[ind] = inStream[ind + 9];
						ind++;
					}

					String filepath = "peer_" + peerProcessHandle.peerID + "//tmp" + peerProcessHandle.fileName;

					try {
						raf = new RandomAccessFile(new File(filepath), "rw");
						try {
							raf.seek((int) (pieceid * peerProcessHandle.pieceSize));
							raf.write(partial);
							raf.close();
							partial = null;
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					}
					peerProcessHandle.writer.print(
							"\n" + (new Date()) + ": Peer " + peerProcessHandle.peerID + " has downloaded the piece "
									+ pieceid + " from " + peerid + ".\nNow the number of pieces it has is "
									+ Collections.frequency(peerProcessHandle.pieces, true));
					peerProcessHandle.writer.flush();

					peerProcessHandle.pieces.set(pieceid, true);

					peerProcessHandle.neighPieces.put(peer, peerProcessHandle.pieces);

					if (!peerProcessHandle.pieces.contains(false)) {
						File finalFile = new File(filepath.replace("tmp", ""));
						try {
							String ofile = "peer_" + peerProcessHandle.peerID + "//tmp" + peerProcessHandle.fileName;
							String nfile = "peer_" + peerProcessHandle.peerID + "//" + peerProcessHandle.fileName;

							File oldFile = new File(ofile);
							File newFile = new File(nfile);
							newFile.delete();

							if (oldFile.renameTo(newFile)) {
								System.out.println("rename success");
							} else {
								System.out.println("rename unsuccessful");
							}
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

						peerProcessHandle.hasCompleteFile = true;
						peerProcessHandle.writer.print("\n" + (new Date()) + ": Peer " + peerProcessHandle.peerID
								+ " has downloaded the complete file.");
						peerProcessHandle.writer.flush();

					} else {
						if (!missingPiece.isEmpty()) {
							// send the next request if still preferred neighbor
							if (peerProcessHandle.preferredneighbors.contains(peer)) {
								int pick;
								if (reqPiece != -1) {
									pick = reqPiece;
									// Do nothing
								} else {
									pick = (int) Math.floor(Math.random() * missingPiece.size()); // random element from
									reqPiece = missingPiece.get(pick);
								}
								try {

									bitml = ByteBuffer.allocate(4).putInt(5).array().clone();
									bittyp = (byte) 6;
									bitpid = ByteBuffer.allocate(4).putInt(reqPiece).array().clone();

									bos.write(bitml);
									bos.write(bittyp);
									bos.write(bitpid);
									bos.flush();
									startRateTracker = System.currentTimeMillis();
								} catch (IOException e) {
									e.printStackTrace();
								}
							}
						}
					}

					// Send have message - sending at the end so the socket doesn't immediately

					for (Socket s : peerProcessHandle.getoutstream.keySet()) {
						BufferedOutputStream cbos = peerProcessHandle.getoutstream.get(s);
						try {
							bitml = ByteBuffer.allocate(4).putInt(5).array().clone();
							bittyp = (byte) 4;
							bitpid = ByteBuffer.allocate(4).putInt(pieceid).array().clone();

							cbos.write(bitml);
							cbos.write(bittyp);
							cbos.write(bitpid);
							cbos.flush();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}

					if (!peerProcessHandle.pieces.contains(false)) {
						peerProcessHandle.peersHavingFile++;
					}

					// check all neighboring peer pieces and send not interested
					for (Socket check : peerProcessHandle.getoutstream.keySet()) {
						if (peerProcessHandle.missingPieces.get(check) != null
								&& peerProcessHandle.missingPieces.get(check).isEmpty()) {
							// send not interested
							BufferedOutputStream cbos = peerProcessHandle.getoutstream.get(check);
							try {
								bitml = ByteBuffer.allocate(4).putInt(1).array().clone();
								bittyp = (byte) 3;

								bos.write(bitml);
								bos.write(bittyp);
								bos.flush();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}

				}
					break;
				default:
					break;
				}

			}
		}
	}
}
