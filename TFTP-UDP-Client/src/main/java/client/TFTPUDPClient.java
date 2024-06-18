package client;

// imports that are using in this project
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Scanner;
import java.net.SocketTimeoutException;

public class TFTPUDPClient {
    // as the requirements says, any port above 1024, change to 69 if testing with Third-Party
    public static int serverPort = 9000;

    // as the requirements says, Transfer mode is always set to octet
    public static String mode = "octet";
    // as the requirements says, packet size will be 512 (excluding headers)
    public static int MAX_BYTES = 512;
    public static int RRQ = 1;
    public static int WRQ = 2;
    public static int DATA = 3;
    public static int ACK = 4;
    public static int  ERROR = 5;

    // timeout set to 60 seconds
    public static int TIMEOUT = 60000;

    public static void main(String[] args) {
        try {
            // datagramSocket to send packets
            DatagramSocket clientSocket = new DatagramSocket();

            // server address is local host
            InetAddress serverAddress = InetAddress.getByName("localhost");

            clientSocket.setSoTimeout(TIMEOUT);

            // as the requirement says, options for the user to read or write a file
            System.out.println("Select an option:");
            System.out.println("1. Retrieve a file");
            System.out.println("2. Send a file");

            // user Inputs their option
            Scanner scanner = new Scanner(System.in);
            String option = scanner.next();

            switch (option) {
                // retrieve a file
                case "1":
                    scanner.nextLine();
                    // allows the user to enter the filename they want to read
                    System.out.println("Enter the filename to read: ");
                    String readFilename = scanner.nextLine();

                    // method to create read request packet
                    byte[] readRequestData = createReadRequest(readFilename, mode);

                    // datagramPacket to send the read request to the server
                    DatagramPacket readSendPacket = new DatagramPacket(readRequestData, readRequestData.length, serverAddress, serverPort);

                    // send the read request packet to the server
                    clientSocket.send(readSendPacket);

                    // method to handle Read (Downloads the file content)
                    receiveFile(clientSocket, readFilename);

                    break;
                // write file option
                case "2":
                    scanner.nextLine();
                    // allows the user to enter the filename they want to write
                    System.out.println("Enter the filename to write: ");
                    String writeFilename = scanner.nextLine();

                    // method to create write request packet
                    byte[] writeRequestData = createWriteRequest(writeFilename, mode);

                    // datagramPacket to send the write request to the server
                    DatagramPacket writeSendPacket = new DatagramPacket(writeRequestData, writeRequestData.length, serverAddress, serverPort);

                    // sends the write request packet to the server
                    clientSocket.send(writeSendPacket);

                    // wait for acknowledgment packet from the server
                    // acknowledgment packets are 4 bytes
                    byte[] ackData = new byte[4];
                    DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length);

                    try {
                        clientSocket.receive(ackPacket);
                    } catch (SocketTimeoutException e) {
                        // if the client doesn't receive any acknowledgment packet from the server...
                        System.err.println("Timeout: Did not receive acknowledgment from server.");
                        return;
                    }

                    // extract the opcode from the acknowledgment packet
                    short opcode = (short) (((ackData[0] & 0xFF) << 8) | (ackData[1] & 0xFF));

                    // check if it's an acknowledgment packet
                    if (opcode == ACK) {
                        System.out.println("Acknowledgment received from server. Attempting to send file data...");
                        // method to handle Write (sends the file to the server)
                        // extract the server address and port from the acknowledgment packet
                        InetAddress serverAddressAck = ackPacket.getAddress();
                        int serverPortAck = ackPacket.getPort();

                        // method to handle Write (sends the file to the server)
                        sendFile(clientSocket, writeFilename, serverAddressAck, serverPortAck);
                    } else {
                        // an output error message if an unexpected error occurs...
                        System.err.println("Unexpected response received from server.");
                    }
                    break;
                // an output error message if the user enters any invalid options
                default:
                    System.err.println("Invalid option.");
            }

            // close the socket
            clientSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // method to handle Reading (Downloads the file content)
    public static void receiveFile(DatagramSocket clientSocket, String filename) throws IOException {
        // opens the FileOutputStream
        FileOutputStream fileOutputStream = new FileOutputStream(filename);
        // variables to use later on
        int blockNumber = 0;
        InetAddress senderAddress = null;
        int senderPort = 0;

        while (true) {
            // byte array to receive response from the server
            // set buffer size to maximum packet size + 4 as we are receiving DatagramPackets size 516...
            byte[] receiveData = new byte[MAX_BYTES + 4];

            // datagramPacket to receive the file data from the server
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

            try {
                // receive a packet from the server
                clientSocket.receive(receivePacket);
            } catch (SocketTimeoutException e) {
                System.err.println("Timeout: Did not receive expected packet from server.");
                // handle timeout exception
                // retransmit the previous acknowledgment packet
                // structure of the ACK Packet as mentioned in the RFC1350
                // 2 bytes     2 bytes
                //  ---------------------
                // | Opcode |   Block #  |
                //  ---------------------
                if (senderAddress != null && senderPort != 0) {
                    byte[] ackPacketData = {0, 4, (byte) ((blockNumber) >> 8), (byte) ((blockNumber) & 0xFF)};
                    DatagramPacket ackPacketRetransmit = new DatagramPacket(ackPacketData, ackPacketData.length, senderAddress, senderPort);
                    clientSocket.send(ackPacketRetransmit);
                    System.out.println("Retransmitted Acknowledgment for Data Packet " + blockNumber);
                }
                continue;
            }

            // if it's the first packet, extract sender's address and port
            if (senderAddress == null) {
                senderAddress = receivePacket.getAddress();
                senderPort = receivePacket.getPort();
            }
            // check if the received packet is an error packet
            if (receiveData[1] == ERROR) {
                // close FileOutputStream
                fileOutputStream.close();
                // method to handle error packet
                handleError(clientSocket, receivePacket.getData(), receivePacket.getLength(), filename);
            }
            // an output message of the DATA packet and its corresponding block number sent from the server
            System.out.println("Received Data Packet " + (blockNumber + 1));

            // write received file data to local file
            fileOutputStream.write(receiveData, 4, receivePacket.getLength() - 4);

            // send acknowledgment packet to the server
            // structure of the ACK Packet as mentioned in the RFC1350
            // 2 bytes     2 bytes
            //  ---------------------
            // | Opcode |   Block #  |
            //  ---------------------

            byte[] ackPacketData = {0, 4, (byte) ((blockNumber + 1) >> 8), (byte) ((blockNumber + 1) & 0xFF)};
            DatagramPacket ackPacket = new DatagramPacket(ackPacketData, ackPacketData.length, senderAddress, senderPort);
            clientSocket.send(ackPacket);

            // an output message of the ACK packet and its corresponding block number sent to the server
            System.out.println("Sent Acknowledgment Packet " + (blockNumber + 1));
            // increasing the block number by one for the next packet
            blockNumber++;

            // as it states in the RFC 1350, if the size of the file is less than 516, it signals the end of the transfer
            if (receivePacket.getLength() < MAX_BYTES + 4) {
                break;
            }
        }
        // close FileOutputStream
        fileOutputStream.close();
        // an output message if the file transfer was successful
        System.out.println("File downloaded successfully.");
    }

    // method to handle Writing (sends the file to the server)
    public static void sendFile(DatagramSocket clientSocket, String filename, InetAddress address, int port) throws IOException {
        // read the content of the file to be written
        File file = new File(filename);
        // checks if the file exists on the clients side (needs to be in the current directory)
        if (!file.exists()) {
            // method to send an Error message to the server (Error code 1 - File not Found)
            // as the requirement says, only error handle for file not found
            sendErrorMessage(clientSocket, address, port, (short) 1, "File not found ");
            System.out.println("Error: FILE NOT FOUND");
            return;
        }
        // if it does exist then, open an FileInputStream
        FileInputStream fileInputStream = new FileInputStream(file);

        // byte array to hold file data
        // set buffer size to maximum packet size
        byte[] fileData = new byte[MAX_BYTES];

        // start with block number 1
        short blockNumber = 1; //
        int bytesRead;
        while ((bytesRead = fileInputStream.read(fileData)) != -1) {
            // structure of the DATA Packet as mentioned in the RFC1350
            // 2 bytes     2 bytes      n bytes
            //  ----------------------------------
            // | Opcode |   Block #  |   Data     |
            //  ----------------------------------
            byte[] sendData = new byte[bytesRead + 4];
            // opcode for Data packet 03
            sendData[0] = 0;
            sendData[1] = (byte) DATA;
            sendData[2] = (byte) ((blockNumber >> 8) & 0xFF);
            sendData[3] = (byte) (blockNumber & 0xFF);
            // copy file data to sendData
            for (int i = 0; i < bytesRead; i++) {
                sendData[i + 4] = fileData[i];
            }
            // sends the data packet to the server with its block number
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, address, port);

            boolean sent = false;
            // implement retransmission with maximum of 3 attempts
            int attempts = 0;
            while (!sent && attempts < 3) {
                // send the packet
                clientSocket.send(sendPacket);
                // an output message of the DATA packets and its corresponding block number sent to the server
                System.out.println("Sent Packet " + blockNumber);

                // wait for acknowledgment packet from the server with a timeout
                try {
                    // acknowledgment packet size is 4 bytes
                    byte[] ackData = new byte[4]; // Acknowledgment packets are 4 bytes
                    DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length);
                    clientSocket.receive(ackPacket);

                    // extract the opcode from the acknowledgment packet
                    short opcode = (short) (((ackData[0] & 0xFF) << 8) | (ackData[1] & 0xFF));

                    // check if it's an acknowledgment packet
                    if (opcode == ACK) {
                        // an output message of the ACK packet and its corresponding block number sent from the server
                        System.out.println("Acknowledgment received from server for packet: " + blockNumber);
                        // increasing the block number by one for the next packet
                        blockNumber++;
                        sent = true;
                    } else {
                        // an output error message if an unexpected error occurs...
                        System.err.println("Unexpected response received from server.");
                    }
                } catch (SocketTimeoutException e) {
                    // timeout exception
                    System.err.println("Socket timeout. No acknowledgment received within 60 seconds for packet " + blockNumber);
                    // increment attempts counter by one
                    attempts++;
                }
            }

            // if not sent after 3 attempts, outputs an error message
            if (!sent) {
                System.err.println("Failed to send packet after maximum attempts for block number: " + blockNumber);
                // close FileInputStream
                fileInputStream.close();
                // close the socket
                clientSocket.close();
                // terminate the program
                System.exit(1);
            }
        }

        // close FileInputStream
        fileInputStream.close();
        // an output message if the file transfer was successful
        System.out.println("File sent to server.");
    }

    // method to send a Read Request to the server
    // structure of the RRQ Packet as mentioned in the RFC1350
    // 2 bytes     string    1 byte     string   1 byte
    //  ------------------------------------------------
    // | Opcode |  Filename  |   0  |    Mode    |   0  |
    //  ------------------------------------------------
    public static byte[] createReadRequest(String filename, String mode) {
        // opcode for read request (RRQ)
        // represents opcode 1 as two bytes
        byte[] opcodeBytes = {0, (byte) RRQ};

        // as the requirements say, file transfer should be done in raw sequence of bytes
        // convert filename and mode to bytes
        byte[] filenameBytes = filename.getBytes();
        byte[] modeBytes = mode.getBytes();

        // calculate the length of the requestData
        int requestDataLength = opcodeBytes.length + filenameBytes.length + 1 + modeBytes.length + 1;

        // byte array to hold the request
        byte[] requestData = new byte[requestDataLength];

        // copy the opcode (01) bytes to requestData
        requestData[0] = opcodeBytes[0];
        requestData[1] = opcodeBytes[1];

        // copy the filename bytes to requestData
        for (int i = 0; i < filenameBytes.length; i++) {
            requestData[2 + i] = filenameBytes[i];
        }

        // set the null byte after filename
        requestData[2 + filenameBytes.length] = 0;

        // copy the mode bytes to requestData
        for (int i = 0; i < modeBytes.length; i++) {
            requestData[2 + filenameBytes.length + 1 + i] = modeBytes[i];
        }

        // set the null byte after mode
        requestData[2 + filenameBytes.length + 1 + modeBytes.length] = 0;

        return requestData;
    }

    // method to send a Write Request to the server
    // structure of the WRQ Packet as mentioned in the RFC1350
    // 2 bytes     string    1 byte     string   1 byte
    //  ------------------------------------------------
    // | Opcode |  Filename  |   0  |    Mode    |   0  |
    //  ------------------------------------------------
    public static byte[] createWriteRequest(String filename, String mode) {
        // opcode for write request (WRQ)
        // represents opcode 2 as two bytes
        byte[] opcodeBytes = {0, (byte) WRQ};

        // as the requirements say, file transfer should be done in raw sequence of bytes
        // convert filename and mode to bytes
        byte[] filenameBytes = filename.getBytes();
        byte[] modeBytes = mode.getBytes();

        // calculate the length of the request data
        int requestDataLength = opcodeBytes.length + filenameBytes.length + 1 + modeBytes.length + 1;

        // byte array to hold the request
        byte[] requestData = new byte[requestDataLength];

        // copy the opcode (02) bytes to requestData
        requestData[0] = opcodeBytes[0];
        requestData[1] = opcodeBytes[1];

        // copy the filename bytes to requestData
        for (int i = 0; i < filenameBytes.length; i++) {
            requestData[2 + i] = filenameBytes[i];
        }

        // set the null byte after filename
        requestData[2 + filenameBytes.length] = 0;

        // copy the mode bytes to requestData
        for (int i = 0; i < modeBytes.length; i++) {
            requestData[2 + filenameBytes.length + 1 + i] = modeBytes[i];
        }

        // set the null byte after mode
        requestData[2 + filenameBytes.length + 1 + modeBytes.length] = 0;

        return requestData;
    }


    // method to handle Errors
    public static void handleError(DatagramSocket clientSocket, byte[] errorData, int packetLength, String filepath) {
        // extract the error code from the error packet
        short errorCode = (short) ((errorData[2] << 8) | (errorData[3] & 0xFF));
        System.out.println(packetLength);
        // extract the error message from the error packet
        String errorMessage = new String(errorData, 4, packetLength-6);
        System.out.println(errorMessage.length());

        // an error message to output the error code and the error message
        System.err.println("Error code: " + errorCode);
        System.err.println("Error message: " + errorMessage);

        // deletes the created file
        File file = new File(filepath);
        file.delete();

        // close the client socket
        clientSocket.close();

        // terminate the program
        System.exit(1);
    }

    // method to send the Error Message to the server
    // structure of the ERROR Packet as mentioned in the RFC1350
    // 2 bytes     2 bytes      string    1 byte
    //  -----------------------------------------
    // | Opcode |  ErrorCode |   ErrMsg   |   0  |
    //  -----------------------------------------
    public static void sendErrorMessage(DatagramSocket clientSocket, InetAddress serverAddress, int serverPort, short errorCode, String errorMessage) throws IOException {
        // as the requirements say, file transfer should be done in raw sequence of bytes
        // convert the error message to bytes
        byte[] errorMessageBytes = errorMessage.getBytes();

        // byte array to hold error message packet data
        // opcode (2 bytes) + Error Code (2 bytes) + Error Message + Null terminator
        byte[] errorPacketData = new byte[4 + errorMessageBytes.length + 1];

        // write opcode (05) to errorPacketData
        errorPacketData[0] = 0;
        errorPacketData[1] = (byte) ERROR;

        // write error code to errorPacketData
        errorPacketData[2] = (byte) (errorCode >> 8);
        errorPacketData[3] = (byte) errorCode;

        // write error message bytes to errorPacketData
        for (int i = 0; i < errorMessageBytes.length; i++) {
            errorPacketData[4 + i] = errorMessageBytes[i];
        }
        // set the null terminator
        errorPacketData[errorPacketData.length - 1] = 0;

        // datagramPacket to send the error message packet to the server
        DatagramPacket errorPacket = new DatagramPacket(errorPacketData, errorPacketData.length, serverAddress, serverPort);

        // send the error message packet to the server
        clientSocket.send(errorPacket);
        // an output message for sending an ERROR packet to the server
        System.out.println("Error Packet sent to the server");
    }

}

