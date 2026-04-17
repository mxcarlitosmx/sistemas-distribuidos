import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.ServerSocket;

class AdministradorTraficoSSL {
    static String host_principal;
    static int puerto_principal;
    static String host_replica;
    static int puerto_replica;
    static int puerto_local;

    // Worker_1: Lee del Cliente y escribe a AMBOS (Principal y Réplica)
    static class Worker_1 extends Thread {
        Socket cliente, principal, replica;

        Worker_1(Socket cliente) {
            this.cliente = cliente;
        }

        public void run() {
            try {
                // Se conecta a ambos servidores
                principal = new Socket(host_principal, puerto_principal);
                replica = new Socket(host_replica, puerto_replica);

                // Hilo para la respuesta del Principal (regresa al cliente)
                new Worker_Principal_A_Cliente(cliente, principal, replica).start();
                
                // Hilo para "tirar a la basura" la respuesta de la Réplica
                new Worker_Replica_Descarte(replica).start();

                InputStream entrada_cliente = cliente.getInputStream();
                OutputStream salida_principal = principal.getOutputStream();
                OutputStream salida_replica = replica.getOutputStream();

                byte[] buffer = new byte[1024];
                int n;
                while ((n = entrada_cliente.read(buffer)) != -1) {
                    // REPLICACIÓN: Se envía a los dos
                    salida_principal.write(buffer, 0, n);
                    salida_principal.flush();

                    salida_replica.write(buffer, 0, n);
                    salida_replica.flush();
                }
            } catch (IOException e) {
                // Error silencioso para no saturar la consola
            } finally {
                cerrarTodo();
            }
        }

        private void cerrarTodo() {
            try {
                if (cliente != null) cliente.close();
                if (principal != null) principal.close();
                if (replica != null) replica.close();
            } catch (IOException e) {}
        }
    }

    // Worker para mandar la respuesta del Principal al Cliente
    static class Worker_Principal_A_Cliente extends Thread {
        Socket cliente, principal, replica;

        Worker_Principal_A_Cliente(Socket cliente, Socket principal, Socket replica) {
            this.cliente = cliente;
            this.principal = principal;
            this.replica = replica;
        }

        public void run() {
            try {
                InputStream entrada_principal = principal.getInputStream();
                OutputStream salida_cliente = cliente.getOutputStream();
                byte[] buffer = new byte[4096];
                int n;
                while ((n = entrada_principal.read(buffer)) != -1) {
                    salida_cliente.write(buffer, 0, n);
                    salida_cliente.flush();
                }
            } catch (IOException e) {
            }
        }
    }

    // Worker para leer la Réplica y descartar (para que no se llene el buffer)
    static class Worker_Replica_Descarte extends Thread {
        Socket replica;

        Worker_Replica_Descarte(Socket replica) {
            this.replica = replica;
        }

        public void run() {
            try {
                InputStream entrada_replica = replica.getInputStream();
                byte[] buffer = new byte[4096];
                // Simplemente leemos y no hacemos nada con el dato
                while (entrada_replica.read(buffer) != -1);
            } catch (IOException e) {
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            System.err.println("Uso:\njava AdministradorTrafico <host-p> <puerto-p> <host-r> <puerto-r> <puerto-local>");
            System.exit(1);
        }

        host_principal = args[0];
        puerto_principal = Integer.parseInt(args[1]);
        host_replica = args[2];
        puerto_replica = Integer.parseInt(args[3]);
        puerto_local = Integer.parseInt(args[4]);

        System.out.println("Administrador de Tráfico Iniciado...");
        System.out.println("Principal: " + host_principal + ":" + puerto_principal);
        System.out.println("Réplica: " + host_replica + ":" + puerto_replica);
        System.out.println("Escuchando en puerto: " + puerto_local);

        ServerSocket ss = new ServerSocket(puerto_local);
        for (;;) {
            Socket cliente = ss.accept();
            new Worker_1(cliente).start();
        }
    }
}
