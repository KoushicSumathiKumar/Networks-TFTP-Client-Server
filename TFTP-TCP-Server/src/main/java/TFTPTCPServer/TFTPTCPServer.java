package TFTPTCPServer;

// imports used in this project
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class TFTPTCPServer {
    // as the requirements says, any port above 1024
    public static int serverPort = 9000;

    public static void main(String[] args) {
        try {
            // create a server socket with to the specified port (9000)
            ServerSocket serverSocket = new ServerSocket(serverPort);
            System.out.println("Server is running...");

            while (true) {
                // accepts a new client connection
                Socket clientSocket = serverSocket.accept();
                // outputs the address of the connected client, so we know which client is requesting which data
                System.out.println("New client connected: " + clientSocket.getInetAddress().getHostAddress());
                // starts a new thread to handle the client connection
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            // any exceptions that occur during server operation...
            e.printStackTrace();
        }
    }
}
