import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simula lectores, escritores y un monitor sobre un GestorAcceso
 * y mide la espera máxima de los escritores para comprobar la equidad.
 *
 * Uso: java Main [justo|injusto] [segundos]
 */
public class Main {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final AtomicLong maxEsperaEscritorMs = new AtomicLong(0);
    private static final AtomicLong escriturasHechas = new AtomicLong(0);
    private static final AtomicLong lecturasHechas = new AtomicLong(0);

    private static void log(String etiqueta, String msg) {
        System.out.println("[" + LocalTime.now().format(FMT) + "] [" + etiqueta + "] " + msg);
    }

    private static void dormir(int minMs, int maxMs) throws InterruptedException {
        Thread.sleep(ThreadLocalRandom.current().nextInt(minMs, maxMs + 1));
    }

    public static void main(String[] args) throws InterruptedException {
        boolean justo = args.length == 0 || !args[0].equalsIgnoreCase("injusto");
        int segundos = args.length > 1 ? Integer.parseInt(args[1]) : 30;

        GestorAcceso gestor = new GestorAcceso(justo);
        System.out.println("=== Modo " + (justo ? "JUSTO" : "INJUSTO") + " durante " + segundos + " s ===");

        List<Thread> hilos = new ArrayList<>();

        for (int i = 1; i <= 6; i++) {
            final int id = i;
            hilos.add(new Thread(() -> lector(gestor, id), "L" + id));
        }
        for (int i = 1; i <= 3; i++) {
            final int id = i;
            hilos.add(new Thread(() -> escritor(gestor, id), "E" + id));
        }

        Thread monitor = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(2000);
                    System.out.println("[MONITOR] --- ESTADO: Lectores=" + gestor.getLectoresActivos()
                            + " | EscritoresEsperando=" + gestor.getEscritoresEsperando()
                            + " | EscritorActivo=" + gestor.isEscritorActivo() + " ---");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Monitor");
        monitor.setDaemon(true);

        hilos.forEach(Thread::start);
        monitor.start();

        Thread.sleep(segundos * 1000L);
        hilos.forEach(Thread::interrupt);
        for (Thread t : hilos) {
            t.join();
        }

        System.out.println("\n=== RESULTADO (" + (justo ? "JUSTO" : "INJUSTO") + ") ===");
        System.out.println("Lecturas completadas : " + lecturasHechas.get());
        System.out.println("Escrituras completadas: " + escriturasHechas.get());
        System.out.println("Espera MÁXIMA de un escritor: " + maxEsperaEscritorMs.get() + " ms");
    }

    private static void lector(GestorAcceso gestor, int id) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                if (gestor.getEscritoresEsperando() > 0) {
                    log("BLOQUEO", "Lector #" + id + " espera porque hay un Escritor con prioridad en cola.");
                }
                gestor.entrarLectura();
                try {
                    log("LECTURA", "Lector #" + id + " entra. Lectores activos: " + gestor.getLectoresActivos());
                    dormir(500, 1500);
                    lecturasHechas.incrementAndGet();
                } finally {
                    gestor.salirLectura();
                }
                dormir(10, 100); // pausa mínima: flujo casi continuo de lectores
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void escritor(GestorAcceso gestor, int id) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                dormir(500, 1500);
                log("SOLICITUD", "Escritor #" + id + " solicita acceso. Escritores en cola: "
                        + (gestor.getEscritoresEsperando() + 1));
                long inicio = System.nanoTime();
                gestor.entrarEscritura();
                try {
                    long esperaMs = (System.nanoTime() - inicio) / 1_000_000;
                    maxEsperaEscritorMs.accumulateAndGet(esperaMs, Math::max);
                    log("ESCRITURA", "Escritor #" + id + " entra de forma EXCLUSIVA. (esperó " + esperaMs + " ms)");
                    dormir(1000, 2000);
                    escriturasHechas.incrementAndGet();
                } finally {
                    gestor.salirEscritura();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
