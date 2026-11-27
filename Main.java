import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simula lectores, escritores y un monitor sobre un {@link GestorAcceso}
 * y mide la espera máxima de los escritores para comprobar la equidad.
 *
 * Uso: java Main [justo|injusto] [segundos]
 *
 * Thread-safety: los contadores compartidos son AtomicLong y el acceso
 * al recurso lo controla el GestorAcceso, así que no hay estado mutable
 * compartido sin proteger.
 */
public class Main {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final AtomicLong maxEsperaEscritorMs = new AtomicLong(0);
    private static final AtomicLong escriturasHechas = new AtomicLong(0);
    private static final AtomicLong lecturasHechas = new AtomicLong(0);

    /**
     * Imprime una línea con la hora actual y una etiqueta, con el formato del enunciado.
     *
     * @param etiqueta tipo de mensaje (LECTURA, SOLICITUD, BLOQUEO, ESCRITURA)
     * @param msg      texto del mensaje
     */
    private static void log(String etiqueta, String msg) {
        System.out.println("[" + LocalTime.now().format(FMT) + "] [" + etiqueta + "] " + msg);
    }

    /**
     * Duerme el hilo actual un tiempo aleatorio dentro del rango indicado.
     *
     * @param minMs mínimo en milisegundos (incluido)
     * @param maxMs máximo en milisegundos (incluido)
     * @throws InterruptedException si el hilo es interrumpido mientras duerme
     */
    private static void dormir(int minMs, int maxMs) throws InterruptedException {
        Thread.sleep(ThreadLocalRandom.current().nextInt(minMs, maxMs + 1));
    }

    /**
     * Lanza la simulación: 6 lectores, 3 escritores y un monitor.
     *
     * @param args args[0] = "justo" (por defecto) o "injusto"; args[1] = duración en segundos (30 por defecto)
     * @throws InterruptedException si el hilo principal es interrumpido
     */
    public static void main(String[] args) throws InterruptedException {
        boolean justo = args.length == 0 || !args[0].equalsIgnoreCase("injusto");
        int segundos = args.length > 1 ? Integer.parseInt(args[1]) : 30;

        GestorAcceso gestor = new GestorAcceso(justo);
        System.out.println("=== Modo " + (justo ? "JUSTO" : "INJUSTO") + " durante " + segundos + " s ===");

        List<Thread> hilos = new ArrayList<>();

        // Hilos lectores (L1 a L6)
        for (int i = 1; i <= 6; i++) {
            final int id = i;
            hilos.add(new Thread(() -> lector(gestor, id), "L" + id));
        }
        // Hilos escritores (E1 a E3)
        for (int i = 1; i <= 3; i++) {
            final int id = i;
            hilos.add(new Thread(() -> escritor(gestor, id), "E" + id));
        }

        // Monitor en segundo plano: informe del estado cada 2 segundos
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

    /**
     * Ciclo de vida de un hilo lector: pide acceso de lectura, lee un rato,
     * lo libera y descansa un momento antes de repetir, hasta que lo interrumpen.
     *
     * Thread-safety: no comparte estado mutable propio. El acceso al recurso
     * lo controla el GestorAcceso y el contador de lecturas es un AtomicLong.
     *
     * @param gestor gestor de acceso compartido por todos los hilos
     * @param id     número del lector, solo se usa para los mensajes de consola
     */
    private static void lector(GestorAcceso gestor, int id) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                if (gestor.getEscritoresEsperando() > 0) {
                    log("BLOQUEO", "Lector #" + id + " espera porque hay un Escritor con prioridad en cola.");
                }
                gestor.entrarLectura();
                try {
                    log("LECTURA", "Lector #" + id + " entra. Lectores activos: " + gestor.getLectoresActivos());
                    dormir(500, 1500);               // duración de la lectura
                    lecturasHechas.incrementAndGet();
                } finally {
                    gestor.salirLectura();           // siempre se libera
                }
                dormir(10, 100);                     // pausa mínima: flujo casi continuo de lectores
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Ciclo de vida de un hilo escritor: espera un rato, solicita acceso exclusivo,
     * escribe y lo libera, hasta que lo interrumpen. Registra su tiempo de espera
     * para calcular la espera máxima de los escritores.
     *
     * Thread-safety: el tiempo máximo se actualiza con accumulateAndGet sobre un
     * AtomicLong y el acceso exclusivo lo garantiza el GestorAcceso.
     *
     * @param gestor gestor de acceso compartido por todos los hilos
     * @param id     número del escritor, solo se usa para los mensajes de consola
     */
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