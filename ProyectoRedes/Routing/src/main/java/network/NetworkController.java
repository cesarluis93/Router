package network;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import router.RouterController;
import main.Utils;

public final class NetworkController implements Runnable{

	protected ExecutorService threadPool = null;
	protected int serverPort;
	protected ServerSocket serverSocket = null;
	protected boolean isStopped = false;
	protected Thread runningThread = null;
	
	protected static String TAG = "NETWORK CONTROLLER";
	protected static Map<String, ServerRunnable> inputConnections;
	protected static Map<String, ClientSocket> outputConnections;
	
	public NetworkController(int port, int nThreads) {

		this.serverPort = port;
		this.threadPool = Executors.newFixedThreadPool(nThreads);
		
		inputConnections = new HashMap<String, ServerRunnable>();
		outputConnections = new HashMap<String, ClientSocket>();
	}
	
	public void run() {
		synchronized(this) {
			this.runningThread = Thread.currentThread();
		}
		openServerSocket();
		while(!isStopped()) {
			Socket clientSocket = null;
			try {
				clientSocket = this.serverSocket.accept();
			} catch (IOException e) {
				if (isStopped()) {
					Utils.printLog(3, "Server Stopped.", TAG);
					break;
				}
				throw new RuntimeException("Error accepting client connection", e);
			}
			
			Utils.printLog(3, "\nNew client connection arrived:", TAG);
			
			// Start server listener for new node
			this.threadPool.execute(new ServerRunnable(clientSocket));
			
		}
		this.threadPool.shutdown();
		Utils.printLog(3, "Server Stopped.", TAG);
		System.exit(0);
	}

	private synchronized boolean isStopped() {
		return this.isStopped;
	}
	
	private synchronized void stop() {
		try {
			this.serverSocket.close();
			this.isStopped = true;
		} catch (IOException e) {
			throw new RuntimeException("Error closing server.", e);
		}
	}
	
	private void openServerSocket() {
		try {
			this.serverSocket = new ServerSocket(this.serverPort);
		} catch (IOException e) {
			throw new RuntimeException("Cannot open port " + RouterController.PORT + ".", e);
		}
		Utils.printLog(3, "Server started. Listening...", TAG);
	}	
	
	public static synchronized void removeInputConnection(String host) {
		if (inputConnections.containsKey(host)) {
			inputConnections.remove(host);
			if (outputConnections.containsKey(host)) {
				outputConnections.get(host).closeConnection();
			}
			// Try to disconnect this node
			RouterController.disconectNode(host);
		} else {
			Utils.printLog(2, "Trying to remove nonexistent input connection: '" + host + "'", TAG);
		}
	}
	
	public static synchronized void removeOutputConnection(String host) {
		if (outputConnections.containsKey(host)) {
			outputConnections.remove(host);
			if (inputConnections.containsKey(host)) {
				inputConnections.get(host).closeConnection();	// Close its input connection.
			}
			// Try to disconnect this node
			RouterController.disconectNode(host);
		} else {
			Utils.printLog(2, "Trying to remove nonexistent output connection: '" + host + "'", TAG);
		}
	}
	
	/**
	 * Called for listener connections to pass data received from nodes.
	 * @param data
	 */
	public static synchronized void receivePacket(Packet packet) {
		Utils.printLog(3, "Receiving new packet:\n" + packet.toString(), TAG);
		RouterController.receivePacket(packet);
	}
	
	public static synchronized void sendPacket(String host, Packet packet) {
		if (outputConnections.containsKey(host)) {
			String data = "";
			// Send DV packet
			if (packet.type.equals(RouterController.DV)) {
				data = "From:" + packet.from + "\nType:" + packet.type + "\nLen:" + packet.len + "\n";
				for (String destiny: packet.costs.keySet()) {
					data += destiny + ":" + packet.costs.get(destiny) + "\n";
				}
			}
			// Send KEEP_ALIVE packet
			else {
				data = "From:" + packet.from + "\nType:" + packet.type + "\n";
			}

//			Utils.printLog(3, "Queing new packet to send:\n" + packet.toString(), TAG);
			outputConnections.get(host).addData(data);
		} else {
			Utils.printLog(2, "Trying to send data to a disconnected node: '" + host + "'", TAG);
		}
	}
	
	public static synchronized boolean existOutputConnection(String host) {
		return outputConnections.containsKey(host);
	}

	public static synchronized boolean existInputConnection(String host) {
		return inputConnections.containsKey(host);
	}
	
	public static void checkKeepAlive() {
		long currentTime;
		for (ServerRunnable listener: inputConnections.values()) {
			currentTime = new Date().getTime();
			if (currentTime - listener.getLastAlive() > RouterController.TIME_U * 1000) {
				Utils.printLog(2, "The '" + listener.getHost() + "' host has been dropped.", TAG);;
				Utils.printLog(3, "Last conection was " + (currentTime - listener.getLastAlive()) + "s ago.", TAG);
				// TODO: Remove this connection or set cost to INFINITY
			} else {
				Utils.printLog(3, "Host '" + listener.getHost() + "' keeps alive.", TAG);
			}
		}
	}
	
	public static synchronized void addOutputConnection(String hostname, ClientSocket clientSocket) {
		outputConnections.put(hostname, clientSocket);
		RouterController.addNeighborNode(hostname, clientSocket.getAddress());
	}
	
	public static synchronized void addInputConnection(String hostname, ServerRunnable serverRunnable) {
		inputConnections.put(hostname, serverRunnable);
		RouterController.addNeighborNode(hostname, serverRunnable.getAddress());
	}
}
