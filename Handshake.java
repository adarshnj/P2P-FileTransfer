
public class Handshake {

	String Header = "P2PFILESHARINGPROJ";
	String zeroBits = "0000000000";
	String msg;

	public Handshake(int id) {
		this.msg = (Header + zeroBits + String.format("%04d", id));// .getBytes();
	}
}