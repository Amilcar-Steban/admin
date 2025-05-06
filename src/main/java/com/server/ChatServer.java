package com.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ChatServer {
    private static final int PORT = 8081;
    private static final Set<ClientHandler> clients = Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) {
        System.out.println("🚀 Servidor iniciado en puerto " + PORT);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n🛑 Apagando servidor...");
                synchronized (clients) {
                    clients.forEach(ClientHandler::disconnect);
                }
            }));

            while (true) {
                System.out.println("🕒 Esperando conexiones..."); // <-- Añade esta línea
                Socket clientSocket = serverSocket.accept();
                System.out.println("🔗 Conexión aceptada de: " + clientSocket.getInetAddress());
                ClientHandler clientThread = new ClientHandler(clientSocket, clients);
                synchronized (clients) {
                    clients.add(clientThread);
                }
                clientThread.start();
            }
            
        } catch (IOException e) {
            System.err.println("❌ Error crítico: " + e.getMessage());
        }
    }
}

class ClientHandler extends Thread {
    private final Socket socket;
    private PrintWriter out;
    private final Set<ClientHandler> clients;
    private String username;

    public ClientHandler(Socket socket, Set<ClientHandler> clients) {
        this.socket = socket;
        this.clients = clients;
    }

    @Override
    public void run() {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out = new PrintWriter(socket.getOutputStream(), true);
            username = in.readLine();

            String message;
            while ((message = in.readLine()) != null) {
                if ("/exit".equalsIgnoreCase(message)) break;
                broadcastUserMessage(message);
            }
        } catch (IOException e) {
            System.out.println("⚠️ Cliente " + username + " desconectado abruptamente");
        } finally {
            disconnect();
        }
    }

    private void broadcastUserMessage(String message) {
        String formattedMsg = message; // username + ": " + 
        System.out.println("💬 " + formattedMsg);
        synchronized (clients) {
            clients.stream()
                .filter(client -> client != this)
                .forEach(client -> client.out.println(formattedMsg));
        }
    }

    private void broadcastSystemMessage(String message) {
        System.out.println("📢 " + message);
        synchronized (clients) {
            clients.forEach(client -> client.out.println(message));
        }
    }

    public void disconnect() {
        try {
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Error al cerrar recursos: " + e.getMessage());
        } finally {
            synchronized (clients) {
                clients.remove(this);
            }
            broadcastSystemMessage("👋 " + username + " se ha desconectado");
        }
    }
}