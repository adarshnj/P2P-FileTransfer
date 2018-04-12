import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

public class filegen {
	public static void main(String[] args) throws IOException {
		String text = "Hello world";
		BufferedWriter output = null;
		File file = new File("example.txt");
		FileOutputStream is = new FileOutputStream(file);
		OutputStreamWriter osw = new OutputStreamWriter(is);
		Writer w = new BufferedWriter(osw);
		for (int i = 0; i < 10000; i++)
			w.write(text + " " + i + "\n");
		w.close();

	}
}
