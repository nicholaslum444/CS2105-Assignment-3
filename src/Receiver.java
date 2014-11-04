import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;


public class Receiver {
	
	private final static int ACK = 1;
	private final static int NAK = 0;
	
	private final static String DESTINATION_IP = "127.0.0.1";
	
	private final static int SEQ = Sender.SEQ; // sequence number index
	private final static int CHK = Sender.CHK; // checksum index
	public final static int START = Sender.START; // signals first packet, filename sending
	public final static int STOP = Sender.STOP; // signals last packet, dont write the data here.
	public final static int LEN = Sender.LEN; // length of data
	private final static int DATA = Sender.DATA; // data start index
	
	private final static int PACKET_SIZE = Sender.PACKET_SIZE;
	private final static int READ_SIZE = PACKET_SIZE - DATA;
	
	private String outputPath;
	private BufferedOutputStream fileWriter;
	
	private String outputName;

	private InetAddress destination;

	private int outPort;
	private int inPort;
	private DatagramSocket outSocket;
	private DatagramSocket inSocket;
	
	private int currentSeq = -1;


	public void run(String[] args) {

		setArgs(args);

		try { // get destIP ip
			destination = InetAddress.getByName(DESTINATION_IP);
		} catch (UnknownHostException e1) {
			e1.printStackTrace();
			debug("cannot make ip");
			return;
		}
		
		try { // make socket
			outSocket = new DatagramSocket();
			inSocket = new DatagramSocket(inPort);
			//outSocket.setSoTimeout(1000);
			//inSocket.setSoTimeout(1000);
		} catch (SocketException e) {
			e.printStackTrace();
			debug("make socket fail");
			return;
		}
		
		// main loop
		while (true) {
			byte[] buffer = new byte[PACKET_SIZE];
			DatagramPacket pkt = new DatagramPacket(buffer,
					buffer.length);
			
			try {
				inSocket.receive(pkt);
				
				if (Sender.checksumOK(buffer)) {
					
					if (buffer[START] == 1) {
						// read the filename
						outputName = makeString(buffer);
						debug("filename: " + outputName);
						
						currentSeq = buffer[SEQ];

						try {
							// open file writer
							File f = new File(outputPath);
							f.mkdirs();
							fileWriter = new BufferedOutputStream(
									new FileOutputStream(outputPath + outputName));
							
							// send ack
							int seq = buffer[SEQ];
							DatagramPacket ack = makeAck(seq);
							
							try {
								outSocket.send(ack);
							} catch (IOException e) {
								e.printStackTrace();
								debug("send ack fail");
								break;
							}
							
							debug("sending " + java.util.Arrays.toString(ack.getData()));
							
						} catch (FileNotFoundException e) {
							e.printStackTrace();
							debug("cannot read file");
							return;
						}
						
					} else {
						int bufferSeq = buffer[SEQ];
						
						if (bufferSeq == (currentSeq + 1)) {
							debug("in order");
							
							if (buffer[STOP] == 1) {
								// closing packet, ack and exit.
								// send ack
								int seq = buffer[SEQ];
								DatagramPacket ack = makeAck(seq);
								
								try {
									for (int i = 1; i <= 10; i++) {
										debug("sending count: " + i);
										outSocket.send(ack);
										debug("sending " + java.util.Arrays.toString(ack.getData()));
									}
								} catch (IOException e) {
									e.printStackTrace();
									debug("send ack fail");
									break;
								}
								
								debug("exiting");
								exit();
							} else {
								
								currentSeq = bufferSeq;
								// write to file
								fileWriter.write(buffer, DATA, buffer[LEN]);
								fileWriter.flush();
								
								String dataString = makeString(buffer);
								debug("received string: " + dataString);
								
								// send ack
								int seq = buffer[SEQ];
								DatagramPacket ack = makeAck(seq);
								
								try {
									outSocket.send(ack);
								} catch (IOException e) {
									e.printStackTrace();
									debug("send ack fail");
									break;
								}
								
								debug("sending " + java.util.Arrays.toString(ack.getData()));
							}
							
						} else {
							debug("out of order");
							debug("currentSeq = " + currentSeq);
							debug("bufferSeq = " + bufferSeq);
							if (currentSeq >= bufferSeq) {
								// send ack
								int seq = buffer[SEQ];
								DatagramPacket ack = makeAck(seq);
								
								try {
									outSocket.send(ack);
								} catch (IOException e) {
									e.printStackTrace();
									debug("send ack fail");
									break;
								}
							}
						}
					}
				}

			} catch (IOException e) {
				e.printStackTrace();
				debug("receive fail");
				break;
			}
		}
		
		System.exit(0);
	}
	
	private String makeString(byte[] buffer) {
		String s = "";
		int lenToRead = buffer[LEN] + DATA;
		for (int i = DATA; i < lenToRead; i++) {
			s += ((char) buffer[i]);
		}
		return s;
	}

	private DatagramPacket makeAck(int seq) {
		byte[] ackBuffer = new byte[PACKET_SIZE];
		// ack means all 1
		for (int i = DATA, j = 1; i < ackBuffer.length; i++) {
			ackBuffer[i] = 1;
			ackBuffer[LEN] = (byte) j;
		}
		
		// make header
		ackBuffer[SEQ] = (byte) seq;
		ackBuffer[CHK] = Sender.makeChecksum(ackBuffer);
		
		DatagramPacket ack = new DatagramPacket(
				ackBuffer, 
				PACKET_SIZE, 
				destination, 
				outPort);
		
		return ack;
	}
	
	private DatagramPacket makeNak(int seq) {
		byte[] nakBuffer = new byte[PACKET_SIZE];
		nakBuffer[SEQ] = (byte) seq;
		// ack means all 1
		for (int i = DATA, j = 1; i < nakBuffer.length; i++) {
			nakBuffer[i] = -1;
			nakBuffer[LEN] = (byte) j;
		}
		
		DatagramPacket nak = new DatagramPacket(
				nakBuffer, 
				PACKET_SIZE, 
				destination, 
				outPort);
		
		return nak;
	}

	private void setArgs(String[] args) {
		inPort = Integer.parseInt(args[0]);
		outPort = Integer.parseInt(args[1]);
		outputPath = args[2];
	}
	

	
	
	
	public void debug(Object s) {
		System.err.println("Receiver: " + s.toString());
	}
	
	public void exit() {
		outSocket.close();
		inSocket.close();
		try {
			fileWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.exit(0);
	}

	public static void main(String[] args) {
		new Receiver().run(args);
	}

}
