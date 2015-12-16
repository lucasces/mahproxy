package com.github.lucasces.mahproxy;

import java.net.*;
import java.io.*;

public class ProxyServer implements Runnable {
    private static final int port = 10000;

    private ServerSocket serverSocket = null;
    private boolean listening = true;

    public void run(){
        try {
            serverSocket = new ServerSocket(port);
            while (listening) {
                new ProxyThread(serverSocket.accept()).start();
            }
            serverSocket.close();

        } catch (IOException e) {
            System.err.println("Failed to start proxy");
        }
    }

    public void stop(){
        listening = false;
        try {
            serverSocket.close();
        } catch (IOException e) {
            System.err.println("Failed to stop proxy");
        }
    }
}
