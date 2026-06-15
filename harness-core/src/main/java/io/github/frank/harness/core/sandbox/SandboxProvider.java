package io.github.frank.harness.core.sandbox;

/**
 * Factory that creates {@link Sandbox} instances from a {@link SandboxConfig}.
 *
 * <p>Implementations are registered via Java {@link java.util.ServiceLoader} so
 * that the product layer can discover available sandbox back-ends at runtime.
 */
public interface SandboxProvider {

    /** Human-readable name of this provider (e.g. {@code "local"}, {@code "docker"}). */
    String name();

    /** Create a sandbox from the given configuration. */
    Sandbox create(SandboxConfig config);

    /**
     * Whether this provider can handle the supplied configuration.
     * <p>
     * The default implementation returns {@code true}. Providers that need
     * a Docker daemon, for example, should override this to check availability.
     */
    default boolean supports(SandboxConfig config) {
        return true;
    }
}
