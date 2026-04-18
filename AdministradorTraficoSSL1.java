import java.net.*;
import java.io.*;
import javax.net.ssl.*;
import java.security.*;

public class AdministradorTraficoSSL {

    static class Worker extends Thread {
        InputStream is;
        OutputStream os;

        Worker(InputStream is, OutputStream os) {
            this.is = is;
            this.os = os;
        }

        public void run() {
            try {
                byte[] buffer = new byte[4096];
                int n;
                while ((n = is.read(buffer)) > 0) {
                    os.write(buffer, 0, n);
                    os.flush();
                }
            } catch (Exception e) {
            }
        }
    }

    static class ReplicadorWorker extends Thread {
        InputStream is;

        ReplicadorWorker(InputStream is) {
            this.is = is;
        }

        public void run() {
            try {
                byte[] buffer = new byte[4096];
                while (is.read(buffer) > 0);
            } catch (Exception e) {
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            System.err.println("Uso: java AdministradorTraficoSSL <host_p> <puerto_p> <host_r> <puerto_r> <puerto_local>");
            System.exit(1);
        }

        String host_p = args[0];
        int puerto_p = Integer.parseInt(args[1]);
        String host_r = args[2];
        int puerto_r = Integer.parseInt(args[3]);
        int puerto_local = Integer.parseInt(args[4]);

        System.setProperty("javax.net.ssl.keyStore", "/home/jhernandez/tomcat.p12");
        System.setProperty("javax.net.ssl.keyStorePassword", "1234");
        System.setProperty("javax.net.ssl.keyStoreType", "PKCS12");

        SSLServerSocketFactory ssf = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
        SSLServerSocket ss = (SSLServerSocket) ssf.createServerSocket(puerto_local);

        while (true) {
            try {
                Socket cliente = ss.accept();

                Socket s_principal = new Socket(host_p, puerto_p);
                Socket s_replica = new Socket(host_r, puerto_r);

                InputStream is_cliente = cliente.getInputStream();
                OutputStream os_p = s_principal.getOutputStream();
                OutputStream os_r = s_replica.getOutputStream();

                new Thread(() -> {
                    try {
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = is_cliente.read(buf)) > 0) {
                            os_p.write(buf, 0, n);
                            os_p.flush();
                            os_r.write(buf, 0, n);
                            os_r.flush();
                        }
                    } catch (Exception e) {}
                }).start();

                new Worker(s_principal.getInputStream(), cliente.getOutputStream()).start();
                new ReplicadorWorker(s_replica.getInputStream()).start();

            } catch (Exception e) {
            }
        }
    }
}
