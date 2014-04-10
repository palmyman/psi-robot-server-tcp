/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package robot;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author palmyman (Lubomir Cuhel / cuhellub)
 */
class CThread extends Thread {

    private Socket socket;
    private BufferedWriter out;
    private BufferedInputStream in;
    private int globalCheckSum;
    private boolean status = true;

    public CThread(Socket socket) {
        super();
        this.socket = socket;
        this.globalCheckSum = 0;
        try {
            this.socket.setSoTimeout(45000);
        } catch (SocketException ex) {
            Logger.getLogger(CThread.class.getName()).log(Level.SEVERE, null, ex);
        }
        try {
            this.out = new BufferedWriter(new OutputStreamWriter(this.socket.getOutputStream()));
            this.in = new BufferedInputStream(this.socket.getInputStream());
        } catch (IOException ex) {
            Logger.getLogger(CThread.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void run() {
        debugLog("New robot");

        if (!logIn()) {
            sendMessage("500 LOGIN FAILED\r\n");
            debugLog("Robot failed to login");
            closeConn();
            return;
        }

        String command;
        while (true) {
            if(!status) break;
            sendMessage("202 OK\r\n");
            command = getMessage();
            if (command == null) {
                break;
            }
            switch (command) {
                case "INFO":
                    logInfo(getRawResponse());
                    continue;
                case "FOTO":
                    getFoto();
                    continue;
            }
        }
        sendMessage("501 SYNTAX ERROR\r\n");
        debugLog("Command syntax error, robot disconnected");
        closeConn();
    }

    private boolean logIn() {
        sendMessage("200 LOGIN\r\n");
        String login = getRawResponse();
        int loginCheckSum = globalCheckSum;
        debugLog("Entered login: " + login.substring(0, login.length() > 30 ? 30 : login.length()));
        debugLog("Expecting pass: " + loginCheckSum);

        sendMessage("201 PASSWORD\r\n");
        String pass = getRawResponse();

        if (pass == null || !login.startsWith("Robot")) {
            return false;
        }
        debugLog("Entered pass: " + pass);

        if (Integer.parseInt(pass) != loginCheckSum) {
            return false;
        }
        debugLog("Robot is logged in");
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
        globalCheckSum = -('\n' + '\r');

        while (true) {
            currentCode = getByte();
            globalCheckSum += currentCode;

            if (currentCode < 0) {
                status = false;
                return null;
            }

            current = (char) currentCode;
            sBuilder.append(current);

            if (sBuilder.length() >= 2) {
                if (sBuilder.charAt(sBuilder.length() - 1) == '\n'
                        && sBuilder.charAt(sBuilder.length() - 2) == '\r') {
                    break;
                }
            }
        }

        return sBuilder.toString().substring(0, sBuilder.length() - 2);
    }

    private String getMessage() {
        StringBuilder sBuilder = new StringBuilder();
        char current;
        int currentCode = 0;
        String info = "INFO ", foto = "FOTO ";

        for (int i = 0; i < 5; i++) {
            currentCode = getByte();

            if (currentCode < 0) {
                status = false;
                return null;
            }

            current = (char) currentCode;
            if (current != info.charAt(i) && current != foto.charAt(i)) {
                status = false;
                return null;
            }

            sBuilder.append(current);
        }

        return sBuilder.toString().substring(0, sBuilder.length() - 1);
    }

    private boolean getFoto() {
        StringBuilder fotoSizeString = new StringBuilder();
        char current;
        int currentCode = 0;
        int localCheckSum = 0;

        while ((currentCode = getByte()) != ' ') {
            if (currentCode < '0' || currentCode > '9') {
                debugLog("While loading foto size");
                status = false;
                return false;
            }
            fotoSizeString.append((char) currentCode);
        }

        int fotoSize = Integer.parseInt(fotoSizeString.toString());
        if (fotoSize < 1) {
            debugLog("While parsing foto size");
            status = false;
            return false;
        }

        debugLog("Loading foto of " + fotoSize + "B");
        int threadNumber = Integer.parseInt(getName().substring(7));
        String fileName = "foto0" + threadNumber + ".png";

        try {
            FileOutputStream fileOs = new FileOutputStream(fileName);
            ObjectOutputStream os = new ObjectOutputStream(fileOs);
            for (int i = 0; i < fotoSize; i++) {
                currentCode = getByte();
                localCheckSum += currentCode;
                if (currentCode < 0) {
                    debugLog("While reading foto data");
                    status = false;
                    return false;
                }
                os.writeByte(currentCode);
            }
            os.close();
        } catch (FileNotFoundException ex) {
            Logger.getLogger(CThread.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(CThread.class.getName()).log(Level.SEVERE, null, ex);
        }

        debugLog("Local checksum: " + localCheckSum);

        byte bytes[] = new byte[4];
        try {
            in.read(bytes, 0, 4);
        } catch (IOException ex) {
            Logger.getLogger(CThread.class.getName()).log(Level.SEVERE, null, ex);
        }

        int remoteCheckSum = ByteBuffer.wrap(bytes).getInt();
        debugLog("Remote checksum: " + remoteCheckSum);

        if (remoteCheckSum != localCheckSum) {
            debugLog("Bad checksum");
            sendMessage("300 BAD CHECKSUM\r\n");
            return true;
        }

        debugLog("Right checksum");

        return true;
    }

    private char getByte() {
        int currentCode = 0;
        try {
            currentCode = in.read();
        } catch (SocketTimeoutException ex) {
            sendMessage("502 TIMEOUT\r\n");
            debugLog("Timeout");
            closeConn();
            return '\0';
        } catch (IOException ex) {
            Logger.getLogger(CThread.class.getName()).log(Level.SEVERE, null, ex);
        }
        return (char) currentCode;
    }

    private void sendMessage(String s) {
        try {
            out.write(s);
            out.flush();
        } catch (IOException ex) {
            Logger.getLogger(CThread.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void debugLog(String message) {
        System.out.println(new Date() + " " + this.getName() + ": " + message);
    }

    private void logInfo(String data) {
        debugLog("INFO " + data.substring(0, data.length() > 30 ? 30 : data.length()));
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

        System.out.println("Server up at port " + port);

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