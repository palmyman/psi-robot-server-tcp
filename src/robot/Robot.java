/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package robot;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author palmyman (Lubomir Cuhel / cuhellub)
 */
class ClientThread extends Thread {    
    private Socket accept;
    public ClientThread(Socket accept) {
    }
}

class Server {

    private int port;

    public Server(int port) {
        this.port = port;
    }

    public void run() {
        ServerSocket ssocket = null;
        try {
            ssocket = new ServerSocket(port);
        } catch (IOException ex) {
            Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(-1);
        }

        System.out.println("Server spusten.");

        while (true) {
            try {
                new ClientThread(ssocket.accept()).start();
            } catch (IOException ex) {
                Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}

public class Robot {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if (args.length == 1) {
            Server server = new Server(Integer.parseInt(args[0]));
            server.run();
        } else {
            System.out.println("Zadejte cislo portu, jako argument programu.");
        }
    }
}