import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Gestiona el acceso concurrente a una base de datos compartida.
 *
 * Permite que varios lectores accedan simultáneamente y garantiza
 * que los escritores tengan acceso exclusivo.
 *
 * Utiliza un bloqueo justo (cola FIFO) para evitar la inanición de
 * escritores: si hay un escritor esperando, los lectores que llegan
 * después deben esperar detrás de él.
 *
 * Thread-safety: todos los métodos pueden invocarse desde múltiples hilos.
 * El estado interno se protege con un ReentrantReadWriteLock y un AtomicInteger.
 */
public class GestorAcceso {

    /** Bloqueo de lectura y escritura. */
    private final ReentrantReadWriteLock lock;

    /** Contador de escritores que están esperando. */
    private final AtomicInteger escritoresEsperando = new AtomicInteger(0);

    /**
     * Crea el gestor en modo justo (recomendado).
     */
    public GestorAcceso() {
        this(true);
    }

    /**
     * Crea el gestor indicando si el bloqueo es justo.
     *
     * @param justo true = cola FIFO (evita inanición); false = modo injusto,
     *              solo para pruebas comparativas
     */
    public GestorAcceso(boolean justo) {
        this.lock = new ReentrantReadWriteLock(justo);
    }

    /**
     * Solicita acceso de lectura al recurso compartido.
     * Varios lectores pueden acceder simultáneamente. Bloquea si hay un
     * escritor activo o (en modo justo) un escritor esperando por delante.
     */
    public void entrarLectura() {
        lock.readLock().lock();
    }

    /**
     * Libera el acceso de lectura. Debe llamarse en un bloque finally.
     */
    public void salirLectura() {
        lock.readLock().unlock();
    }

    /**
     * Solicita acceso exclusivo de escritura.
     * Los lectores y otros escritores deberán esperar.
     */
    public void entrarEscritura() {
        escritoresEsperando.incrementAndGet();
        try {
            lock.writeLock().lock();
        } finally {
            escritoresEsperando.decrementAndGet();
        }
    }

    /**
     * Libera el acceso exclusivo de escritura. Debe llamarse en un bloque finally.
     */
    public void salirEscritura() {
        lock.writeLock().unlock();
    }

    /**
     * Devuelve el número de lectores activos.
     *
     * @return número de lectores que están leyendo
     */
    public int getLectoresActivos() {
        return lock.getReadLockCount();
    }

    /**
     * Devuelve el número de escritores esperando.
     *
     * @return número de escritores en espera
     */
    public int getEscritoresEsperando() {
        return escritoresEsperando.get();
    }

    /**
     * Comprueba si existe un escritor activo.
     *
     * @return true si hay un escritor activo
     */
    public boolean isEscritorActivo() {
        return lock.isWriteLocked();
    }
}