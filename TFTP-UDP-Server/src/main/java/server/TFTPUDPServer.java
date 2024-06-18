package server;

// imports that are using in this project
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

public class TFTPUDPServer {
    // as the requirements says, any port above 1024
    public static int serverPort = 9000;
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
            // datagramSocket to listen for incoming packets on port 9000
            DatagramSocket serverSocket = new DatagramSocket(serverPort);

            serverSocket.setSoTimeout(TIMEOUT);

            System.out.println("Server listening on port 9000...");

            while (true) {
                try {
                    // byte array to hold incoming data
                    // set buffer size to maximum packet size
                    byte[] receiveData = new byte[MAX_BYTES];

                    // datagramPacket to receive incoming packets
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

                    // receive a packet
                    serverSocket.receive(receivePacket);

                    // reset timeout after successful packet reception
                    serverSocket.setSoTimeout(TIMEOUT);

                    // extract client's address and port
                    InetAddress clientAddress = receivePacket.getAddress();
                    int clientPort = receivePacket.getPort();

                    // an output message when a connection is made with the received packet data
                    System.out.println("Received packet from " + clientAddress.getHostAddress() + ": " + clientPort);

                    // extract opcode from the received packet
                    short opcode = (short) (((receiveData[0] & 0xFF) << 8) | (receiveData[1] & 0xFF));

                    // check if it's a read request (RRQ)
                    if (opcode == RRQ) {
                        // method to handle read request
                        handleReadRequest(serverSocket, receiveData, receivePacket.getLength(), clientAddress, clientPort);
                    }
                    // check if it's a write request (WRQ)
                    else if (opcode == WRQ) {
                        // method to handle write request
                        handleWriteRequest(serverSocket, receiveData, receivePacket.getLength(), clientAddress, clientPort);
                    }
                    // ignore other types of requests (Errors are handled further down the code in methods)
                    else {
                        // an output error message if the server receives any invalid opcode (user enters any invalid options)
                        System.err.println("Ignoring unsupported opcode: " + opcode);
                    }
                } catch (SocketTimeoutException e) {
                    // an output error message if socket timeout occurs...
                    // the server will still be listening for any packets in-case any arrive
                    System.err.println("Socket timeout. No packet received within 60 seconds.");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // method to handle read requests...
    public static void handleReadRequest(DatagramSocket serverSocket, byte[] requestData, int requestLength, InetAddress clientAddress, int clientPort) throws IOException {
        // extract filename from the packet (skip opcode and null byte)
        String requestDataString = new String(requestData, 2, requestLength - 2);
        String[] requestDataParts = requestDataString.split("\0"); // Split by null byte
        String filename = requestDataParts[0]; // First part is the filename
        // an output message of the file that has been requested by the client
        System.out.println(clientAddress.getHostAddress() + ": Received read request for file - " + filename);

        // check if the file exists
        File file = new File(filename);
        if (!file.exists()) {
            // method to send an Error message to the server (Error code 1 - File not Found)
            // as the requirement says, only error handle for file not found
            sendErrorPacket(serverSocket, clientAddress, clientPort, (short) 1, "File not found ");
            System.err.println(clientAddress.getHostAddress() + ": ERROR FILE NOT FOUND - " + filename);
            return;
        }

        // buffer for file data
        // set buffer size to maximum packet size
        byte[] buffer = new byte[MAX_BYTES];
        int bytesRead;
        // initial block number
        short blockNumber = 1;

        // fileInputStream to read the content of the file
        FileInputStream fileInputStream = new FileInputStream(file);

        // read file data and send it in data packets
        while ((bytesRead = fileInputStream.read(buffer)) != -1) {
            // structure of the DATA Packet as mentioned in the RFC1350
            // 2 bytes     2 bytes      n bytes
            //  ----------------------------------
            // | Opcode |   Block #  |   Data     |
            //  ----------------------------------
            byte[] dataPacketData = new byte[bytesRead + 4];

            //opcode for Data Packet (03)
            dataPacketData[0] = 0;
            dataPacketData[1] = (byte) DATA;
            dataPacketData[2] = (byte) (blockNumber >> 8);
            dataPacketData[3] = (byte) blockNumber;
            // copy file data into data packet
            for (int i = 0; i < bytesRead; i++) {
                dataPacketData[i + 4] = buffer[i];
            }
            // send data packet to client
            DatagramPacket dataPacket = new DatagramPacket(dataPacketData, dataPacketData.length, clientAddress, clientPort);
            serverSocket.send(dataPacket);

            // an output message of the DATA packets and its corresponding block number sent to the client along with its size
            System.out.println(clientAddress.getHostAddress() + ": Sent for Packet: " + blockNumber + ", Data Packet Size: " + (dataPacket.getLength()-4));

            // acknowledgment packet size is 4 bytes
            byte[] ackData = new byte[4];
            DatagramPacket ackPacketFromClient = new DatagramPacket(ackData, ackData.length);

            // wait for acknowledgment packet from the client with a timeout
            try {
                serverSocket.receive(ackPacketFromClient);

                // extract opcode from acknowledgment packet
                short opcode = (short) ((ackData[0] << 8) | (ackData[1] & 0xFF));
                // check opcode for Acknowledgment packet (opcode 4)
                if (opcode != ACK) {
                    // an output error message if an unexpected error occurs...
                    System.err.println(clientAddress.getHostAddress() + ": Unexpected response received from client.");
                    return;
                } else {
                    // an output message of the ACK packet and its corresponding block number sent from the client
                    System.out.println(clientAddress.getHostAddress() + ": Acknowledgment Received For Packet " + blockNumber);
                }
            } catch (SocketTimeoutException e) {
                // handle timeout exception (no acknowledgment received within timeout duration)
                System.err.println("Socket timeout. No acknowledgment received within 60 seconds for packet " + blockNumber);
                // retransmit the data packet
                System.out.println("Retransmitting Data Packet " + blockNumber);
                // retransmit the same data packet
                serverSocket.send(dataPacket);
                continue;
            }
            // increasing the block number by one for the next packet
            blockNumber++;
        }
        System.out.println(clientAddress.getHostAddress() + ": End of file transfer.");
        // close FileInputStream
        fileInputStream.close();
        // an output message if the file transfer was successful
        System.out.println(clientAddress.getHostAddress() + ": File sent to client successfully");
    }

    //method to handle write request
    public static void handleWriteRequest(DatagramSocket serverSocket, byte[] requestData, int requestLength, InetAddress clientAddress, int clientPort) throws IOException {
        // extract filename from the packet (skip opcode and null byte)
        String requestDataString = new String(requestData, 2, requestLength - 2);
        String[] requestDataParts = requestDataString.split("\0"); // Split by null byte
        // extract filename
        String filename = requestDataParts[0];

        // an output message for receiving the request with the filename
        System.out.println(clientAddress.getHostAddress() + ": Received write request for file - " + filename);

        // initial block number
        int blockNumber = 0;

        // sends acknowledgment packet to the client indicating it's ready for file transfer
        // structure of the ACK Packet as mentioned in the RFC1350
        // 2 bytes     2 bytes
        //  ---------------------
        // | Opcode |   Block #  |
        //  ---------------------
        byte[] ackPacket = {0, (byte) ACK, 0, 0};
        DatagramPacket ackDatagram = new DatagramPacket(ackPacket, ackPacket.length, clientAddress, clientPort);
        serverSocket.send(ackDatagram);

        // an output message of the ACK packets and its corresponding block number sent to the client
        System.out.println(clientAddress.getHostAddress() + ": Acknowledgment sent for Packet: " + blockNumber);

        // fileOutputStream to write received file data to a local file (current directory as the requirement says)
        FileOutputStream fileOutputStream = new FileOutputStream(filename);

        while (true) {
            // byte array to receive response from the client
            // set buffer size to maximum packet size + 4 as we are receiving DatagramPackets size 516...
            byte[] receiveData = new byte[MAX_BYTES + 4];

            // datagramPacket to receive the file data from the client
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

            try {
                // receive a packet from the client
                serverSocket.receive(receivePacket);
            } catch (SocketTimeoutException e) {
                // handle timeout exception
                System.err.println(clientAddress.getHostAddress() + ": Timeout - Did not receive expected packet from client.");

                // retransmit the acknowledgment for the previous block
                byte[] ackData = {0, (byte) ACK, (byte) (blockNumber >> 8), (byte) (blockNumber & 0xFF)};
                DatagramPacket ackPacketRetransmit = new DatagramPacket(ackData, ackData.length, clientAddress, clientPort);
                serverSocket.send(ackPacketRetransmit);
                System.out.println(clientAddress.getHostAddress() + ": Retransmitted acknowledgment for Packet: " + blockNumber);
                continue;
            }

            // extract the opcode from the received packet
            short opcode = (short) (((receiveData[0] & 0xFF) << 8) | (receiveData[1] & 0xFF));

            // check opcode for Data packet (opcode 3)
            if (opcode == DATA) {
                // extract block number from the received packet
                int receivedBlockNumber = ((receiveData[2] & 0xFF) << 8) | (receiveData[3] & 0xFF);

                // if the received block number is the expected one, write data to file
                if (receivedBlockNumber == blockNumber + 1) {
                    // write received file data to local file
                    fileOutputStream.write(receiveData, 4, receivePacket.getLength() - 4);

                    // increasing the block number by one for the next packet
                    blockNumber++;
                    // an output message of the DATA packets and its corresponding block number sent from the client along with its size
                    System.out.println(clientAddress.getHostAddress() + ": Received Data Packet: " + blockNumber + ", Data Packet Size: " + (receivePacket.getLength() - 4));

                    // send acknowledgment packet to the client for the received block
                    // structure of the ACK Packet as mentioned in the RFC1350
                    // 2 bytes     2 bytes
                    //  ---------------------
                    // | Opcode |   Block #  |
                    //  ---------------------
                    byte[] ackData = {0, (byte) ACK, (byte) (receivedBlockNumber >> 8), (byte) (receivedBlockNumber & 0xFF)};
                    DatagramPacket ackPacketToSend = new DatagramPacket(ackData, ackData.length, clientAddress, clientPort);
                    serverSocket.send(ackPacketToSend);
                    // an output message of the ACK packets and its corresponding block number sent to the client
                    System.out.println(clientAddress.getHostAddress() + ": Acknowledgment sent for Packet: " + blockNumber);

                    // as it states in the RFC 1350, if the size of the file is less than 516, it signals the end of the transfer
                    if (receivePacket.getLength() < MAX_BYTES + 4) {
                        System.out.println(clientAddress.getHostAddress() + ": End of file transfer.");
                        // close the fileOutputStream
                        fileOutputStream.close();
                        // an output message if the file transfer was successful
                        System.out.println(clientAddress.getHostAddress() + ": File received from client successfully.");
                        break;
                    }
                } else {
                    // an output error message if an error occurs with potentially 2 things:
                    // * receiving packet with different block number to the expected block number
                    // * receiving duplicate packets
                    // both are ignored but still an output message is there
                    System.err.println(clientAddress.getHostAddress() + ": Received out-of-order or duplicate packet. Ignoring.");
                }
            }
            // check opcode for ERROR packet (opcode 5)
            else if (opcode == ERROR) {
                // close the fileOutputStream
                fileOutputStream.close();
                // method to handle Error sent from the client
                handleError(receivePacket, clientAddress);
                // deletes the file that was created above as an error has occurred
                File filepath = new File(filename);
                filepath.delete();
                break;
            }
        }
    }

    // method to create and send Error packets to the client
    // structure of the ERROR Packet as mentioned in the RFC1350
    // 2 bytes     2 bytes      string    1 byte
    //  -----------------------------------------
    // | Opcode |  ErrorCode |   ErrMsg   |   0  |
    //  -----------------------------------------
    public static void sendErrorPacket(DatagramSocket socket, InetAddress address, int port, int errorCode, String errorMessage) throws IOException {
        // opcode (2 bytes) + error code (2 bytes) + error message + null terminator
        int packetLength = 4 + errorMessage.length() + 1;

        // byte array to hold the error packet
        byte[] sendData = new byte[packetLength];

        // write the opcode for error packet (05)
        sendData[0] = 0;
        sendData[1] = (byte) ERROR;

        // write the error code
        sendData[2] = (byte) (errorCode >> 8);
        sendData[3] = (byte) errorCode;

        // write the error message
        byte[] errorMessageBytes = errorMessage.getBytes();
        for (int i = 0; i < errorMessageBytes.length; i++) {
            sendData[4 + i] = errorMessageBytes[i];
        }
        // write the null terminator
        sendData[packetLength-1] = 0;

        // datagramPacket and sends the packet
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, address, port);
        socket.send(sendPacket);
        // an output message for sending an ERROR packet to the client
        System.out.println("Error Packet sent to the client");
    }

    // method to handle Error sent from the client
    public static void handleError(DatagramPacket receivePacket, InetAddress clientAddress) {
        // extract error code from the received packet
        short errorCode = (short) ((receivePacket.getData()[2] << 8) | (receivePacket.getData()[3] & 0xFF));

        // extract error message from the received packet
        String errorMessage = new String(receivePacket.getData(), 4, receivePacket.getLength() - 6);

        // an output error message of the Error Code and the Error Message
        System.err.println(clientAddress.getHostAddress() + ": Error Code: " + errorCode);
        System.err.println(clientAddress.getHostAddress() + ": Error Message: " + errorMessage);
    }
}