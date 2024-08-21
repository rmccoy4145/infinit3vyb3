package app.geo.stub;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

public class AudioPlayer {

    private static boolean playing = true;

    public static void main(String[] args) {
        String serverAddress = "localhost"; // Replace with your server's address
        int port = 65535; // Replace with your server's port

        while(AudioPlayer.playing) {
            try (Socket socket = new Socket(serverAddress, port);InputStream inputStream = socket.getInputStream()) {

                // BufferedInputStream to buffer the incoming audio stream
                BufferedInputStream bufferedIn = new BufferedInputStream(inputStream);

                // Get the audio format from the AudioInputStream
                AudioFormat format = new AudioFormat(22050, 16, 1, true, false);
                AudioInputStream audioStream = new AudioInputStream(bufferedIn, format, AudioSystem.NOT_SPECIFIED);

                printAudioFormatInfo(format);
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                SourceDataLine audioLine = (SourceDataLine) AudioSystem.getLine(info);
                audioLine.open(format);

                // Start the audio line to begin playback
                audioLine.start();
                System.out.println("Playback started...");

                // Buffer to hold the audio data
                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalBytesRead = 0;

                // Read from the audio stream and write to the audio line for playback
                while(AudioPlayer.playing) {
                    if ((bytesRead = audioStream.read(buffer)) > 0) {
                        audioLine.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;

                        } else if (bytesRead == -1) {
                            // If -1 is encountered, reset the audioStream to handle looping
                            audioStream = AudioSystem.getAudioInputStream(bufferedIn);
                        }

                    // Print playback information every second or every certain number of bytes
                    if (totalBytesRead % (format.getFrameSize() * format.getFrameRate()) < buffer.length) {
                        printPlaybackInfo(format, totalBytesRead);
                    }
                }

                // Cleanup: drain the audio line and close resources
                audioLine.drain();
                audioLine.close();
                audioStream.close();

                System.out.println("Playback completed.");

            } catch (UnsupportedAudioFileException | IOException | LineUnavailableException ex) {
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
    }

    public static void stop() {
        AudioPlayer.playing = false;
    }

    private static void printAudioFormatInfo(AudioFormat format) {
        System.out.println("Audio Format Information:");
        System.out.println("  Sample Rate: " + format.getSampleRate());
        System.out.println("  Sample Size in Bits: " + format.getSampleSizeInBits());
        System.out.println("  Channels: " + format.getChannels());
        System.out.println("  Frame Size: " + format.getFrameSize() + " bytes");
        System.out.println("  Frame Rate: " + format.getFrameRate());
        System.out.println("  Encoding: " + format.getEncoding());
        System.out.println("  Big Endian: " + format.isBigEndian());
    }

    private static void printPlaybackInfo(AudioFormat format, long totalBytesRead) {
        long totalFramesRead = totalBytesRead / format.getFrameSize();
        double elapsedSeconds = totalFramesRead / format.getFrameRate();

        System.out.println(String.format("Playback Info: Frames Read: %d, Time Elapsed: %.2f seconds",
            totalFramesRead, elapsedSeconds));
    }
}
