package app.geo.stub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class AudioServer implements Runnable{

    private static final Logger LOG = LoggerFactory.getLogger(AudioServer.class);
    public final static int PORT = 65535;
    private final String audioFilePath = "resources/ImperialMarch60.wav";
    private ServerSocket serverSocket;
    private volatile boolean running = true;
    private final List<ClientHandler> clients = new ArrayList<>();
    private Thread playbackThread = null;


    public AudioServer() {
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
        File audioFile = new File(audioFilePath);
        if (!audioFile.exists()) {
            System.out.println("Audio file not found: " + audioFilePath);
            return;
        }

        while (running) {
            try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(audioFile)) {

                AudioFormat format = audioInputStream.getFormat();
                printAudioFormatInfo(format);


                byte[] buffer = new byte[4096];
                int bytesRead;

                // Time tracking to ensure real-time broadcasting
                long startTime = System.currentTimeMillis();

                while ((bytesRead = audioInputStream.read(buffer)) != -1 && running) {

                    // Broadcast the audio data only if there are clients connected
                    synchronized (clients) {
                        if (!clients.isEmpty()) {
                            broadcast(buffer, bytesRead);
                        }
                    }

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
            for (ClientHandler client : clients) {
                client.sendData(buffer, length);
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

        public void sendData(byte[] buffer, int length) {
            if (!connected) return;
            try {
                clientSocket.getOutputStream().write(buffer, 0, length);
            } catch (IOException e) {
                disconnect();
            }
        }

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
            System.out.println("Client disconnected: " + clientSocket.getInetAddress());
        }
    }

    public static void main(String[] args) {
        AudioServer audioSvr = new AudioServer();
        Thread audioSvrThread = new Thread(audioSvr);
        audioSvrThread.start();
    }
}
