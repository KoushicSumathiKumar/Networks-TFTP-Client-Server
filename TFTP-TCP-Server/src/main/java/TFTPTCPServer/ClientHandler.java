package TFTPTCPServer;

// imports used in this project
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;

class ClientHandler implements Runnable {
    private Socket clientSocket;
    private PrintWriter out;
    private int timeOut;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
        // 60 seconds timeout
        this.timeOut = 60000;
        try {
            this.out = new PrintWriter(clientSocket.getOutputStream(), true);
            clientSocket.setSoTimeout(timeOut);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        try {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
                String request;
                while ((request = in.readLine()) != null) {
                    // if the request received is a read request...
                    if (request.startsWith("Read Request")) {
                        // extracts the filename
                        // index 12 as you want after the "Read Request"
                        String filename = request.substring(12);
                        System.out.println(clientSocket.getInetAddress().getHostAddress() + ": Read Request on " + filename);
                        // a method to handle read request
                        readFile(filename);
                        // reset the timeout
                        clientSocket.setSoTimeout(timeOut);
                    }
                    // if the request received is a write request...
                    else if (request.startsWith("Write Request")) {
                        // extracts the filename
                        // index 13 as you want after the "Write Request"
                        String filename = request.substring(13);
                        System.out.println(clientSocket.getInetAddress().getHostAddress() + ": Write Request on " + filename);
                        // a method to handle write request
                        writeFile(filename, in);
                    } else {
                        // an output error message if the user sends any other requests
                        System.err.println(clientSocket.getInetAddress().getHostAddress() + ": Invalid Request");
                    }
                }
            } catch (SocketTimeoutException e) {
                // an output error message in case of a timeout
                System.err.println(clientSocket.getInetAddress().getHostAddress() + ": Timeout - No request received within 60 seconds.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // method to handle read request
    private void readFile(String filename) {
        try (BufferedReader fileReader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = fileReader.readLine()) != null) {
                out.println(line);
            }
            // a signal to indicate end of file transfer
            // I'm aware this might be an inefficient way to do it, but I wasn't sure if needed to split up the data into 512
            // This is mainly due to that there wasn't much description on how to build the client server for TCP
            // This could be sending extra data, but it still provides you with the same experience from the first task
            out.println("EOFT");
            // an output message if the file transfer was successful
            System.out.println(clientSocket.getInetAddress().getHostAddress() + ": " + filename + " content sent successfully");
        } catch (IOException e) {
            // error handling only for File Not Found (as the requirement says)
            out.println("ERROR: File not found");
            // an output error message if File Not Found
            System.err.println(clientSocket.getInetAddress().getHostAddress() + ":"  + filename + " File not found");
        }
    }

    // method to handle write request
    private void writeFile(String filename, BufferedReader in) {

        try (BufferedWriter fileWriter = new BufferedWriter(new FileWriter(filename))) {
            String line;
            while ((line = in.readLine()) != null && !line.equals("EOFT")) {
                fileWriter.write(line);
                fileWriter.newLine();
            }
            fileWriter.flush();
            // a message to let the client know the file transfer was successful
            out.println("File successfully written");
            // an output message if the file transfer was successful
            System.out.println(clientSocket.getInetAddress().getHostAddress() + ": " + filename + " content received successfully");
        } catch (IOException e) {
            e.printStackTrace();
            // an output error message if the file couldn't be written due to an error
            System.err.println(clientSocket.getInetAddress().getHostAddress() + ": Could not write to file");
        }
    }
}
