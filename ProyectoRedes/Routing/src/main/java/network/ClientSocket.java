package network;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.Queue;

import router.RouterController;
import main.Utils;

public class ClientSocket implements Runnable{
	
	private String hostname = null;
	
	private Queue<String> dataQueue;
	private Socket clientSocket = null;
	protected String address = null;
    private DataOutputStream output = null;
    private DataInputStream input = null;
    private boolean isStopped = false;
    
    private String TAG = "CLIENT SOCKET";
    
    
    public ClientSocket(String address, int port, String hostname) {
    	this.address = address;
    	this.hostname = hostname;
    	
    	dataQueue = new LinkedList<String>();

    	// Instantiate connection socket and output/input stream
        try {
        	clientSocket = new Socket(this.address, port);
        	while (clientSocket == null) {}
            output = new DataOutputStream(clientSocket.getOutputStream());
            input = new DataInputStream(clientSocket.getInputStream());
        } catch (UnknownHostException e) {
        	Utils.printLog(1, "Don't know about host " + hostname, TAG);
        } catch (IOException e) {
        	Utils.printLog(1, "Couldn't get I/O for the connection to " + hostname, TAG);
        }
        
        // Add new connection
        System.out.println("Client socket openned for '" + this.hostname + "'");
        NetworkController.outputConnections.put(this.hostname, this);
    }
    
	private boolean login() {
		System.out.println("Client login proccess with '" + this.hostname + "'...");
    	String request = "From:" + RouterController.hostname + "\n" + "Type:HELLO\n";
    	String response1="", response2="";
    	
    	// Sending request message
    	try {
    		System.out.println("Sending HELLO to '" + this.hostname + "'...");
			output.writeBytes(request);
		} catch (IOException e) {
			e.printStackTrace();
			closeConnection();
		}

    	// Reading WELCOME message
    	try {
    		System.out.println("Trying to read WELCOME from '" + this.hostname + "'...");
			response1 = input.readLine();
			response2 = input.readLine();
		} catch (IOException e) {
			e.printStackTrace();
			Utils.printLog(1, e.getMessage(), TAG);
		}
    	if (response1.trim().equals("From:" + this.hostname) && response2.trim().equals("Type:HELLO")) {
    		System.out.println("Output connection stablished with '" + this.hostname + "'.");    		
    		
    		return true;
    	}
    	
    	return false;
    }

	public void run() {
        // Login process: if FALSE, brook connection
        if (!login()) {
        	System.out.println("Output connection with '" + this.hostname + "' failed.");
        	NetworkController.outputConnections.remove(this.hostname);
        	this.closeConnection();
        }

        // If logged successfully, start to sending data
		while (!isStopped) {
			// If the queue is empty, continue.
			if (dataQueue.isEmpty()) {
				continue;
			}
			
			// If the queue is not empty, send the packet at the head of queue.
			try {
				String data = dataQueue.poll();
				System.out.println("Sending to " + this.hostname + ": " + data );
				output.writeBytes(data);
			} catch (IOException e) {
				Utils.printLog(1, "Sending data in node " + this.hostname, TAG);
				e.printStackTrace();
			}
		}
	}
	
	public void addData(String data) {
		dataQueue.add(data);
	}
	
	public void closeConnection() {
		try {
			// Clean up
			output.close();
			input.close();
			clientSocket.close();
			
			// Remove from network controller
			NetworkController.removeClientConnection(this.hostname);
			
			// Set flag to stopped
			isStopped = true;
		} catch (IOException e) {
			Utils.printLog(1, "Clossing connection with '" + this.hostname + "'.", TAG);
			e.printStackTrace();
		}
	}
	
	public String getHost() {
		return hostname;
	}
}
