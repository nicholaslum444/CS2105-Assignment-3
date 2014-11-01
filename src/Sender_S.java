import java.net.*;
import java.util.*;

public class Sender_S {
	final static int PACKET_SIZE = 10;
	final static int SEND_INTERVAL = 500;

	public class OutThread extends Thread {
		private DatagramSocket sk_out;
		private int dst_port;
		private int recv_port;

		public OutThread(DatagramSocket sk_out, int dst_port, int recv_port) {
			this.sk_out = sk_out;
			this.dst_port = dst_port;
			this.recv_port = recv_port;
		}

		public void run() {
			try {
				int count = 0;
				byte[] out_data = new byte[PACKET_SIZE];
				InetAddress dst_addr = InetAddress.getByName("127.0.0.1");

				// To register the recv_port at the UnreliNet first
				/*DatagramPacket out_pkt = new DatagramPacket(
						("REG:" + recv_port).getBytes(),
						("REG:" + recv_port).getBytes().length,
						dst_addr,
						dst_port);
				sk_out.send(out_pkt);*/

				try {
					while (true) {
						// construct the packet
						for (int i = 0; i < PACKET_SIZE; ++i)
							out_data[i] = (byte) (count % 10);

						// send the packet
						DatagramPacket out_pkt = new DatagramPacket(out_data, out_data.length,
								dst_addr, dst_port);
						sk_out.send(out_pkt);

						// print info
						System.out.print((new Date().getTime())
								+ ": sender sent " + out_pkt.getLength()
								+ "bytes to " + out_pkt.getAddress().toString()
								+ ":" + out_pkt.getPort() + ". data are ");
						for (int i = 0; i < PACKET_SIZE; ++i)
							System.out.print(out_data[i]);
						System.out.println();

						// wait for a while
						sleep(SEND_INTERVAL);

						// increase counter
						count++;
					}
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					sk_out.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}
	}

	public class InThread extends Thread {
		private DatagramSocket sk_in;

		public InThread(DatagramSocket sk_in) {
			this.sk_in = sk_in;
		}

		public void run() {
			try {
				byte[] in_data = new byte[PACKET_SIZE];
				DatagramPacket in_pkt = new DatagramPacket(in_data,
						in_data.length);
				try {
					while (true) {
						sk_in.receive(in_pkt);
						System.out.print((new Date().getTime())
								+ ": sender received " + in_pkt.getLength()
								+ "bytes from "
								+ in_pkt.getAddress().toString() + ":"
								+ in_pkt.getPort() + ". data are ");
						for (int i = 0; i < PACKET_SIZE; ++i)
							System.out.print(in_data[i]);
						System.out.println();
					}
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					sk_in.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}
	}

	// constructor
	public Sender_S(int sk1_dst_port, int sk4_dst_port) {
		DatagramSocket sk1, sk4;
		System.out.println("sk1_dst_port=" + sk1_dst_port + ", "
				+ "sk4_dst_port=" + sk4_dst_port + ".");

		try {
			// create sockets
			sk1 = new DatagramSocket();
			sk4 = new DatagramSocket(sk4_dst_port);

			// create threads to process data
			InThread th_in = new InThread(sk4);
			OutThread th_out = new OutThread(sk1, sk1_dst_port, sk4_dst_port);
			th_in.start();
			th_out.start();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public static void main(String[] args) {
		// parse parameters
		if (args.length != 2) {
			System.err.println("Usage: java TestSender sk1_dst_port, sk4_dst_port");
			System.exit(-1);
		} else
			new Sender_S(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
	}
}
