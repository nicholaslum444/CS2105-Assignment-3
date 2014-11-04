
public class ztest {

	public ztest() {
	}

	public static void main(String[] args) {
		byte b = (byte) 128;
		System.out.println(b);
		b = (byte) ~b;
		System.out.println(~b);
	}

}
