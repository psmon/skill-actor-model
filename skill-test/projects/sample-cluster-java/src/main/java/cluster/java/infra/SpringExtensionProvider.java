package cluster.java.infra;

public final class SpringExtensionProvider {
    private static final SpringExtension INSTANCE = new SpringExtension();

    private SpringExtensionProvider() {
    }

    public static SpringExtension getInstance() {
        return INSTANCE;
    }
}
