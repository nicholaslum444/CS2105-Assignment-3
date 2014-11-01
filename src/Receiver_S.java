import java.net.*;
import java.util.*;

public class Receiver_S {
	static int pkt_size = 10;

	public Receiver_S(int sk2_dst_port, int sk3_dst_port) {
		DatagramSocket sk2, sk3;
		System.out.println("sk2_dst_port=" + sk2_dst_port + ", "
				+ "sk3_dst_port=" + sk3_dst_port + ".");

		// create sockets
		try {
			sk2 = new DatagramSocket(sk2_dst_port);
			sk3 = new DatagramSocket();
			try {
				byte[] in_data = new byte[pkt_size];
				DatagramPacket in_pkt = new DatagramPacket(in_data,
						in_data.length);
				InetAddress dst_addr = InetAddress.getByName("127.0.0.1");

				while (true) {
					// receive packet
					sk2.receive(in_pkt);

					// print info
					System.out.print((new Date().getTime())
							+ ": receiver received " + in_pkt.getLength()
							+ "bytes from " + in_pkt.getAddress().toString()
							+ ":" + in_pkt.getPort() + ". data are ");
					for (int i = 0; i < pkt_size; ++i)
						System.out.print(in_data[i]);
					System.out.println();

					// send received packet
					DatagramPacket out_pkt = new DatagramPacket(in_data,
							in_data.length, dst_addr, sk3_dst_port);
					sk3.send(out_pkt);

					// print info
					System.out.print((new Date().getTime())
							+ ": receiver sent " + out_pkt.getLength()
							+ "bytes to " + out_pkt.getAddress().toString()
							+ ":" + out_pkt.getPort() + ". data are ");
					for (int i = 0; i < pkt_size; ++i)
						System.out.print(in_data[i]);
					System.out.println();
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(-1);
			} finally {
				sk2.close();
				sk3.close();
			}
		} catch (SocketException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

	public static void main(String[] args) {
		// parse parameters
		if (args.length != 2) {
			System.err
					.println("Usage: java TestReceiver sk2_dst_port, sk3_dst_port");
			System.exit(-1);
		} else
			new Receiver_S(Integer.parseInt(args[0]),
					Integer.parseInt(args[1]));
	}
}
