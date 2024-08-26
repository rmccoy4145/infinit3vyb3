package com.mccoy;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;


public class AudioPlayer {

    private volatile boolean playing = true;
    private volatile boolean connected = false;
    private static final int BUFFER_SIZE = 4096;
    private final InetConnection inetConnection;
    public final String serverIpAddress = AppConfig.SERVER_IP_ADDRESS;
    public final int serverAudioUdpPort = AppConfig.SERVER_AUDIO_UDP_PORT;
    public final int serverTcpPort = AppConfig.SERVER_LISTEN_TCP_PORT;


    AudioPlayer(boolean tcpStream) throws SocketException {
        this.inetConnection = new InetConnection();
    }

    public void start() {
        while(!connected) {
            connect();
        }
        this.udpStream();
    }

    private void connect() {
        try (Socket socket = new Socket(this.serverIpAddress, this.serverTcpPort);
            InputStream inputStream = socket.getInputStream()) {

            BufferedInputStream bufferedIn = new BufferedInputStream(inputStream);
            System.out.println("Connected");
            this.connected = true;

            // Buffer to hold the audio data
            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalBytesRead = 0;


        } catch (IOException ex) {
            ex.printStackTrace();
            System.out.println("Connection lost. Attempting to reconnect...");
            // Optionally wait before trying to reconnect
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void udpStream() {
        try (DatagramSocket udpSocket = new DatagramSocket(this.serverAudioUdpPort)) {
            this.playing = true;

            byte[] buffer = new byte[BUFFER_SIZE];

            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            AudioFormat format = new AudioFormat(22050, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            SourceDataLine audioLine = (SourceDataLine) AudioSystem.getLine(info);
            audioLine.open(format);
            audioLine.start();

            System.out.println("Receiving UDP audio multicast...");

            while (this.playing) {
                udpSocket.receive(packet);
                // Output packet data as bytes
                System.out.println("Packet received with length: " + packet.getLength());

                audioLine.write(packet.getData(), 0, packet.getLength());
            }

        } catch (IOException | LineUnavailableException e) {
            e.printStackTrace();
        }
    }


    public void stop() {
        this.playing = false;
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

    private void printPlaybackInfo(AudioFormat format, long totalBytesRead) {
        long totalFramesRead = totalBytesRead / format.getFrameSize();
        double elapsedSeconds = totalFramesRead / format.getFrameRate();

        System.out.println(String.format("Playback Info: Frames Read: %d, Time Elapsed: %.2f seconds",
            totalFramesRead, elapsedSeconds));
    }

    public static void main(String[] args) throws SocketException {
        AudioPlayer audioPlayer = new AudioPlayer(false);
        audioPlayer.start();
    }
}
