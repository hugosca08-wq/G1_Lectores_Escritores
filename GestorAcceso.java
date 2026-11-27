
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Gestiona el acceso concurrente a una base de datos compartida.
 *
 * Permite que varios lectores accedan simultáneamente y garantiza
 * que los escritores tengan acceso exclusivo.
 *
 * Utiliza un bloqueo justo para evitar la inanición de escritores.
 */
public class GestorAcceso {

    // Bloqueo de lectura y escritura en modo justo
    private final ReentrantReadWriteLock lock =
            new ReentrantReadWriteLock(true);

    // Contador de escritores que están esperando
    private final AtomicInteger escritoresEsperando =
            new AtomicInteger(0);

    /**
     * Solicita acceso de lectura al recurso compartido.
     * Varios lectores pueden acceder simultáneamente.
     */
    public void entrarLectura() {
        lock.readLock().lock();
    }

    /**
     * Libera el acceso de lectura.
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
     * Libera el acceso exclusivo de escritura.
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
