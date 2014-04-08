/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package robot;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author palmyman (Lubomir Cuhel / cuhellub)
 */
class Response {

    private String command, data;

    public Response(String rawResponse) {
        this.command = rawResponse.substring(0, 3);
        this.data = rawResponse.substring(5);
    }

    public String getCommand() {
        return command;
    }

    public String getData() {
        return data;
    }
}

class CThread extends Thread {

    private Socket socket;
    private BufferedWriter out;
    private BufferedInputStream in;

    public CThread(Socket socket) {
        super();
        this.socket = socket;
        try {
            this.out = new BufferedWriter(new OutputStreamWriter(this.socket.getOutputStream()));
            this.in = new BufferedInputStream(this.socket.getInputStream());
        } catch (IOException ex) {
            Logger.getLogger(CThread.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void run() {
        System.out.println("New clieant at thread: " + this.getName());

        if (!logIn()) {
            closeConn();
            return;
        }

        Response response;
        String rawResponse;
        while (((rawResponse = getRawResponse())) != null) {
            response = new Response(rawResponse);
            if ("INFO".equals(response.getCommand())) {
                logInfo();
                continue;
            } else {
                saveFoto();
                continue;
            }
        }
        closeConn();
    }

    private boolean logIn() {
        write("200 LOGIN\r\n");
        String login = getRawResponse();
        System.out.println("Entered login" + login.substring(0, login.length() - 2));
        Integer sum = 0;
        for (int i = 0; i < login.length() - 2; i++) {
            sum += (int) login.charAt(i);
        }
        System.out.println("Expecting pass:" + sum);
        
        write("201 PASSWORD\r\n");        
        String pass = getRawResponse();
        System.out.println("Entered pass:" + pass);        
        
        
        
        if (!pass.substring(0, pass.length() - 2).equals(sum.toString())) {
            write("500 LOGIN FAILED\r\n");
            return false;
        }
        write("202 OK\r\n");
        return true;
    }

    private void closeConn() {
        try {
            in.close();
            out.close();
            socket.close();
        } catch (IOException ex) {
            Logger.getLogger(CThread.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private String getRawResponse() {
        StringBuilder sBuilder = new StringBuilder();
        char current;
        int currentCode = 0;

        while (true) {
            try {
                currentCode = in.read();
            } catch (IOException ex) {
                Logger.getLogger(CThread.class.getName()).log(Level.SEVERE, null, ex);
            }

            if (currentCode < 0) {
                return null;
            }

            current = (char) currentCode;
            sBuilder.append(current);
            if (sBuilder.length() > 2) {
                if (sBuilder.charAt(sBuilder.length() - 1) == '\n'
                        && sBuilder.charAt(sBuilder.length() - 2) == '\r') {
                    break;
                }
            }
        }

        return sBuilder.toString();
    }

    private void write(String s) {
        try {
            out.write(s);
            out.flush();
        } catch (IOException ex) {
            Logger.getLogger(CThread.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void logInfo() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private void saveFoto() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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

        System.out.println("Server up.");

        while (true) {
            try {
                new CThread(ssocket.accept()).start();
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
            System.out.println("Usage: java robot <port>");
        }
    }
}