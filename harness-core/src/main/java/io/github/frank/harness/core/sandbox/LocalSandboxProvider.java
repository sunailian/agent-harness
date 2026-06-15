package io.github.frank.harness.core.sandbox;

/**
 * Provider that creates {@link LocalSandbox} instances.
 *
 * <p>Always available — no daemon or network required.
 */
public class LocalSandboxProvider implements SandboxProvider {

    @Override
    public String name() {
        return "local";
    }

    @Override
    public Sandbox create(SandboxConfig config) {
        return new LocalSandbox(
                config.workDir(),
                config.env(),
                config.defaultTimeout());
    }

    @Override
    public boolean supports(SandboxConfig config) {
        return !config.isDocker();
    }
}
