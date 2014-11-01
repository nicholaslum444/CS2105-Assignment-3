import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;


public class Sender {
	
	int sk1;
	int sk4;
	String inputPath;
	String outputName;
	
	BufferedInputStream br;

	public void run(String[] args) {
		setArgs(args);
		
		try {
			br = new BufferedInputStream(new FileInputStream(inputPath));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			debug("cannot read file");
			System.exit(-1);
		}
	}
















	private void setArgs(String[] args) {
		sk1 = Integer.parseInt(args[0]);
		sk4 = Integer.parseInt(args[1]);
		inputPath = args[2];
		outputName = args[3];
	}

	
	
	
	
	
	
	
	
	
	
	
	
	private void debug(Object s) {
		System.err.println(s);
	}
	
	public static void main(String[] args) {
		new Sender().run(args);
	}

}
