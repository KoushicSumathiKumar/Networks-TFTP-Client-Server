package TFTPTCPClient;

// imports used in this project
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.File;
import java.net.Socket;

public class TFTPTCPClient {
    // as the requirements says, any port above 1024
    public static int serverPort = 9000;
    // address is localhost
    public static String address = "localhost";

    public static void main(String[] args) {
        try (Socket socket = new Socket(address, serverPort)) {
            // an output message for clients connecting to server
            System.out.println("Connected to server...");
            // initialising input and output streams for communication with server
            BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // as the requirement says, user options for read or write
            System.out.println("Select an option: ");
            System.out.println("1. Retrieve a file");
            System.out.println("2. Send a file");
            String choice = userInput.readLine();

            // if option 1 was selected...
            if (choice.equals("1")) {
                // read Request
                // user inputs the file they want to GET from the server
                System.out.println("Enter file name to read: ");
                String filename = userInput.readLine();
                // sends the read request to server
                out.println("Read Request" + filename);
                // receives the file contents from server
                // method receiveFile is called to handle Read Request
                receiveFile(filename, in);

            // if option 2 was selected...
            } else if (choice.equals("2")) {
                // write Request
                // user inputs the file they want to PUT from the server
                System.out.print("Enter file name to write: ");
                String filename = userInput.readLine();
                // method writeFile is called to handle Write Request
                writeFile(filename, out);
            } else {
                System.err.println("Invalid choice.");
            }
        } catch (IOException e) {
            // if any exceptions that occur during client operation...
            e.printStackTrace();
        }
    }

    // as the requirement says, implement a protocol that operates like TFTP (i.e. supports only read and write operations)
    // method to read the data received from the server
    public static void receiveFile(String filename, BufferedReader in) {
        try (BufferedWriter fileWriter = new BufferedWriter(new FileWriter(filename))) {
            String line;
            while ((line = in.readLine()) != null && !line.equals("EOFT")) {
                if (line.startsWith("ERROR")) {
                    // output the error message
                    System.err.println("Error from server: " + line.substring(6)); // Extract the error message
                    fileWriter.close();
                    File file = new File(filename);
                    // delete the file due to the error
                    file.delete();
                    return;
                }
                fileWriter.write(line);
                fileWriter.newLine();
            }
            fileWriter.flush();
            // output confirmation message
            System.out.println("File successfully received");
        } catch (IOException e) {
            // if an exception occurs during file being received
            e.printStackTrace();
            System.err.println("Error receiving file.");
        }
    }

    // as the requirement says, implement a protocol that operates like TFTP (i.e. supports only read and write operations)
    // method to write the data from the client to the server
    public static void writeFile(String filename, PrintWriter out) {
        try {
            File file = new File(filename);
            // checks if file exists
            if (!file.exists()) {
                // an output message if the file doesn't exist
                System.err.println("File not found.");
                return;
            }
            // sends write request to server
            out.println("Write Request" + filename);
            //bufferedWriter to read the content of the file and send it to the server
            try (BufferedReader fileReader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = fileReader.readLine()) != null) {
                    // send line to server
                    out.println(line);
                }
                // a signal end of file transfer to server
                // I'm aware this might be an inefficient way to do it, but I wasn't sure if needed to split up the data into 512
                // This is mainly due to that there wasn't much description on how to build the client server for TCP
                // This could be sending extra data, but it still provides you with the same experience from the first task
                out.println("EOFT");
            } catch (IOException e) {
                // if any exceptions that occur during file reading
                e.printStackTrace();
            }
            // an output file sent successfully message
            System.out.println("File sent successfully.");
        } catch (Exception e) {
            // if any exceptions that occur during client operation
            e.printStackTrace();
        }
    }
}
