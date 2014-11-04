import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;


public class Sender {
	
	public static final int TIMEOUT = 1000;
	public final static int ACK = 1;
	public final static int NAK = 0;
	
	private final static String DESTINATION_IP = "127.0.0.1";

	public final static int CHK = 0; // checksum index
	public final static int SEQ = 1; // sequence number index
	public final static int START = 2; // signals first packet, filename sending
	public final static int STOP = 3; // signals last packet, dont write the data here.
	public final static int LEN = 4; // length of data
	public final static int DATA = 5; // data start index
	
	public final static int PACKET_SIZE = 100;
	private final static int READ_SIZE = PACKET_SIZE - DATA;
	
	private String inputPath;
	private BufferedInputStream fileReader;
	
	private String outputName;

	private InetAddress destination;

	private int outPort;
	private int inPort;
	private DatagramSocket outSocket;
	private DatagramSocket inSocket;
	
	private volatile boolean isRunning = true;
	private volatile boolean closingSent = false;

	private final static int WINDOW_SIZE = 10;
	private volatile boolean canSend = true;
	private volatile int currentSeq = 0;
	private volatile int baseSeq = 0;
	private volatile int lastSeq = 99999999;
	
	private ArrayList<DatagramPacket> history = new ArrayList<DatagramPacket>();
	
	private class SendPkt extends Thread {
		
		private boolean startBroadcast;
		
		public SendPkt(boolean b) {
			startBroadcast = b;
		}
		
		public void run() {
			
			// if sending the filename, then send the filename
			if (startBroadcast) {
				byte[] fileBytes = outputName.getBytes();
				byte[] nameBuffer = new byte[PACKET_SIZE];
				for (int i = 0, j = DATA; i < fileBytes.length; i++, j++) {
					nameBuffer[j] = fileBytes[i];
				}
				makeHeader(nameBuffer, fileBytes.length, true, false);
				
				DatagramPacket pkt = new DatagramPacket(
						nameBuffer, 
						PACKET_SIZE, 
						destination, 
						outPort);

				debug("sending " + java.util.Arrays.toString(pkt.getData()));

				try {
					sendPkt(pkt);
				} catch (IOException e) {
					e.printStackTrace();
					isRunning = false;
					return;
				}

				currentSeq++;
				canSend = false;
				return;
			}

			// make pkt from file if not sending the filename.
			byte[] dataBuffer = new byte[PACKET_SIZE];
			int bytesRead = 0;
			boolean resending = false;
			
			if (currentSeq < history.size()) {
				DatagramPacket oldPacket = history.get(currentSeq);
				dataBuffer = oldPacket.getData();
				resending = true;
			} else {	
				synchronized (fileReader) {
					try {
						bytesRead = fileReader.read(dataBuffer, DATA, READ_SIZE);
					} catch (IOException e) {
						e.printStackTrace();
						isRunning = false;
						return;
					}
					
				}
			}
			
			if (resending) {
				DatagramPacket pkt = new DatagramPacket(
						dataBuffer, 
						PACKET_SIZE, 
						destination, 
						outPort);

				debug("sending " + java.util.Arrays.toString(pkt.getData()));

				try {
					sendPkt(pkt);
				} catch (IOException e) {
					e.printStackTrace();
					isRunning = false;
					return;
				}
				
				// update cansend
				currentSeq++;
				updateCanSend();
				return;
			}

			// send pkt if bytes > 0
			if (bytesRead > 0 ) {
				makeHeader(dataBuffer, bytesRead);
				DatagramPacket pkt = new DatagramPacket(
						dataBuffer, 
						PACKET_SIZE, 
						destination, 
						outPort);
				
				debug("sending " + java.util.Arrays.toString(pkt.getData()));
				
				try {
					sendPkt(pkt);
				} catch (IOException e) {
					e.printStackTrace();
					isRunning = false;
					return;
				}
				
				// update cansend
				currentSeq++;
				updateCanSend();

			} else {
				debug("send closing packet");
				if (closingSent) { 
					canSend = false;
					return;
				}
				// should send the closing packet
				byte[] stopBuffer = new byte[PACKET_SIZE];
				makeHeader(stopBuffer, 0, false, true);
				
				DatagramPacket pkt = new DatagramPacket(
						stopBuffer, 
						PACKET_SIZE, 
						destination, 
						outPort);

				debug("sending " + java.util.Arrays.toString(pkt.getData()));

				try {
					sendPkt(pkt);
				} catch (IOException e) {
					e.printStackTrace();
					isRunning = false;
					return;
				}
				closingSent = true;
				lastSeq = currentSeq;
				canSend = false;
				return;
			}
		}

		private void sendPkt(DatagramPacket pkt) throws IOException {
			if (currentSeq == history.size()) {
				history.add(pkt);
			}
			debug("history: " + history.toString());
			synchronized (outSocket) {
				outSocket.send(pkt);
			}
		}

		private void updateCanSend() {
			canSend = (currentSeq < (baseSeq + WINDOW_SIZE));
			debug("can send? " + canSend);
		}

	}

	private class ListenAck extends Thread {
		
		public void run() {
			byte[] buffer = new byte[PACKET_SIZE];
			DatagramPacket pkt = new DatagramPacket(buffer, PACKET_SIZE);
			try {
				inSocket.receive(pkt);
				debug("received " + java.util.Arrays.toString(buffer));
				
				if (buffer[START] == 1) { // first packet, no data
					if (checksumOK(buffer)) {
						pushBase(buffer);
					}
				} else if (buffer[STOP] == 1) {
					if (checksumOK(buffer)) {
						canSend = false;
						isRunning = false;
					}
				} else {
					if (checksumOK(buffer)) {
						if (isAck(buffer)) {
							debug("received ack");
							debug("seq num == " + buffer[SEQ]);
							if (buffer[SEQ] == lastSeq) {
								debug("final seq");
								canSend = false;
								isRunning = false;
							} else {
								pushBase(buffer);
							}
						} else if (isNak(buffer)) {
							debug("received nak");
							debug("seq num == " + buffer[SEQ]);
							throw new SocketTimeoutException();
						}
					}
				}
				
			} catch (SocketTimeoutException e) {
				e.printStackTrace();
				debug("timeout");
				// set the current back to the base
				currentSeq = baseSeq;
				
			} catch (IOException e) {
				e.printStackTrace();
				isRunning = false; 
			}
			
			updateCanSend();
		}

		private boolean isAck(byte[] buffer) {
			for (int i = DATA; i < buffer.length; i++) {
				if (buffer[i] != 1) {
					return false;
				}
			}
			return true;
		}
		
		private boolean isNak(byte[] buffer) {
			for (int i = DATA; i < buffer.length; i++) {
				if (buffer[i] != -1) {
					return false;
				}
			}
			return true;
		}

		private void pushBase(byte[] buffer) {
			debug("received ok, pushing base");
			if (buffer[SEQ] >= baseSeq) {
				baseSeq = buffer[SEQ] + 1;
			}
		}

		private void updateCanSend() {
			canSend = (currentSeq < (baseSeq + WINDOW_SIZE));
		}
	}
	
	//-----------------------------------------------------

	// use go back n
	public void run(String[] args) {
		
		setArgs(args);
		
		try { // get destIP ip
			destination = InetAddress.getByName(DESTINATION_IP);
		} catch (UnknownHostException e1) {
			e1.printStackTrace();
			debug("cannot make ip");
			return;
		}

		try { // get file
			fileReader = new BufferedInputStream(new FileInputStream(inputPath));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			debug("cannot read file");
			return;
		}
		
		try { // make socket
			outSocket = new DatagramSocket();
			inSocket = new DatagramSocket(inPort);
			inSocket.setSoTimeout(TIMEOUT);
		} catch (SocketException e) {
			e.printStackTrace();
			debug("make socket fail");
			return;
		}
		
		// send the filename out first.
		new SendPkt(true).run();
		
		// wait for the ack for filename.
		ListenAck la = new ListenAck();
		la.run();
		while (!canSend) {
		}
		
		while (isRunning) {
			while (canSend) {
				new SendPkt(false).run();
			}
			new ListenAck().run();
		}
		
		System.exit(0);
	}
	
	
	
	
	
	public static byte makeChecksum(byte[] buffer) {
		byte chk = 0;
		for (int i = SEQ; i < buffer.length; i++) {
			chk += buffer[i];
		}
		return chk;
	}
	
	public static boolean checksumOK(byte[] buffer) {
		byte localChecksum = makeChecksum(buffer);
		byte bufferChecksum = buffer[CHK];
		boolean ok = localChecksum == bufferChecksum;
		debug("checksum ok? " + ok);
		return ok;
	}
	
	public void makeHeader(byte[] b, int len) {
		makeHeader(b, len, false, false);
	}
	
	public void makeHeader(byte[] b, int len, boolean start, boolean stop) {
		b[SEQ] = (byte) currentSeq;
		b[START] = (byte) (start ? 1 : 0);
		b[STOP] = (byte) (stop ? 1 : 0);
		b[LEN] = (byte) len;
		b[CHK] = makeChecksum(b); // checksum
	}

	private void setArgs(String[] args) {
		outPort = Integer.parseInt(args[0]);
		inPort = Integer.parseInt(args[1]);
		inputPath = args[2];
		outputName = args[3];
	}
	
	
	
	public static void debug(Object s) {
		System.err.println("sender: " + s);
	}
	
	public void exit() {
		System.exit(-1);
	}

	public static void main(String[] args) {
		new Sender().run(args);
	}

}
