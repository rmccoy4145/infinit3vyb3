package com.mccoy;

import java.util.Enumeration;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.net.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AudioServer implements Runnable {

    private static final Logger LOG = Logger.getLogger(AudioServer.class.getName());
    public final static int PORT = AppConfig.SERVER_AUDIO_UDP_PORT;
    private final String audioFilePath = "audio/ImperialMarch60.wav";
    private ServerSocket serverSocket;
    private volatile boolean running = true;
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private Thread playbackThread = null;
    public static final String MULTICAST_GROUP_ADDRESS = AppConfig.SERVER_AUDIO_MULTICAST_GROUP_ADDRESS;
    File audioFile = null;
    private final InetConnection inetConnection;


    public AudioServer() throws IOException {
        this.inetConnection = new InetConnection();
        this.audioFile = new File(audioFilePath);
        if (!audioFile.exists()) {
            throw new IOException("Audio file not found: " + audioFilePath);
        }
    }

    @Override
    public void run() {
        this.playbackThread = new Thread(this::playAudio);
        playbackThread.start();

        try {

            serverSocket = new ServerSocket(PORT);
            LOG.info("Audio Server started on port " + PORT + "...");


            while (running) {
                Socket clientSocket = serverSocket.accept();

                ClientHandler clientHandler = new ClientHandler(clientSocket);
                synchronized (clients) {
                    clients.add(clientHandler);
                }
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void shutdown() {
        running = false;
        try {
            this.clients.forEach(ClientHandler::disconnect);
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close(); // Close the ServerSocket to release the port
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        LOG.info("Audio Server has been stopped.");
    }

    private void playAudio() {

        System.out.println("Starting multicast audio streaming on " + MULTICAST_GROUP_ADDRESS + ":" + PORT);
        while (running) {
                try (MulticastSocket multicastSocket = new MulticastSocket();
                     AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(audioFile)) {
                InetAddress multicastGroup = InetAddress.getByName(MULTICAST_GROUP_ADDRESS);
                multicastSocket.setNetworkInterface(this.inetConnection.getNetworkInterface());

                AudioFormat format = audioInputStream.getFormat();
                printAudioFormatInfo(format);


                byte[] buffer = new byte[4096];
                int bytesRead;

                // Time tracking to ensure real-time broadcasting
                long startTime = System.currentTimeMillis();

                while ((bytesRead = audioInputStream.read(buffer)) != -1 && running) {
                    DatagramPacket packet = new DatagramPacket(buffer, bytesRead, multicastGroup, PORT);
                    multicastSocket.send(packet);

                    // Broadcast the audio data only if there are clients connected
//                    synchronized (clients) {
//                        if (!clients.isEmpty()) {
//                            DatagramPacket packet = new DatagramPacket(buffer, bytesRead, multicastGroup, PORT);
//                            multicastSocket.send(packet);
//                            System.out.println("Packet sent with length: " + bytesRead);
//                        }
//                    }

                    // Sleep for the time it would have taken to play the audio
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    long expectedTime = (long) (1000.0 * bytesRead / format.getFrameRate() / format.getFrameSize());
                    if (elapsedTime < expectedTime) {
                        try {
                            Thread.sleep(expectedTime - elapsedTime);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    startTime = System.currentTimeMillis();
                }

                System.out.println("Looping audio file...");

            } catch (IOException | UnsupportedAudioFileException e) {
                e.printStackTrace();
                running = false;
            }
        }
    }

    private void broadcast(byte[] buffer, int length) {
        synchronized (clients) {
            for (ClientHandler clientHandler : clients) {
                if(!clientHandler.sendData(buffer, length)) {
                    clients.remove(clientHandler);
                }
            }
        }
    }

    private void printAudioFormatInfo(AudioFormat format) {
        System.out.println("Audio Format Information:");
        System.out.println("  Sample Rate: " + format.getSampleRate());
        System.out.println("  Sample Size in Bits: " + format.getSampleSizeInBits());
        System.out.println("  Channels: " + format.getChannels());
        System.out.println("  Frame Size: " + format.getFrameSize() + " bytes");
        System.out.println("  Frame Rate: " + format.getFrameRate());
        System.out.println("  Encoding: " + format.getEncoding());
        System.out.println("  Big Endian: " + format.isBigEndian());
    }

    private class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private volatile boolean connected = true;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            System.out.println("Client connected: " + clientSocket.getInetAddress());

            try {
                while (connected && !clientSocket.isClosed()) {
                    // Client is handled within the broadcast method
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                disconnect();
            }
        }

        public boolean sendData(byte[] buffer, int length) {
            if (!connected) return false;
            try {
                clientSocket.getOutputStream().write(buffer, 0, length);
                return true;
            } catch (IOException e) {
                disconnect();
                return false;
            }
        }

        public boolean isConnected() {return connected;}

        private void disconnect() {
            connected = false;
            synchronized (clients) {
                clients.remove(this);
            }
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            LOG.info("Client disconnected: " + clientSocket.getInetAddress());
        }
    }


    public static void main(String[] args) throws IOException {
        AudioServer audioSvr = new AudioServer();
        Thread audioSvrThread = new Thread(audioSvr);
        audioSvrThread.start();
    }
}
