package it.aboutbits.postgresql._support.testdata.base;

import net.datafaker.Faker;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@NullMarked
public abstract class TestDataCreator<T> {
    protected static final Faker FAKER = new Faker();

    protected final int numberOfItems;

    protected TestDataCreator(int numberOfItems) {
        this.numberOfItems = numberOfItems;
    }

    public void apply() {
        create();
    }

    public T returnFirst() {
        return create().getFirst();
    }

    public List<T> returnAll() {
        return create();
    }

    public Set<T> returnSet() {
        return new HashSet<>(create());
    }

    protected List<T> create() {
        var result = new ArrayList<T>();

        for (var index = 0; index < numberOfItems; index++) {
            result.add(
                    create(index)
            );
        }

        return result;
    }

    protected abstract T create(int index);

    public static String randomKubernetesNameSuffix(String name) {
        var maxLength = 63; // Kubernetes hard enforces RFC-1123

        if (name.length() > 60) {
            throw new IllegalArgumentException(
                    "The name is too long (must be <= 60 to allow '-' + at least 2 random chars, max %d total) [name=%s, length=%d]".formatted(
                            maxLength,
                            name,
                            name.length()
                    )
            );
        }

        var separator = "-";
        var suffixLength = maxLength - name.length() - separator.length();

        return name + separator + FAKER.regexify("[a-z0-9]{%d}".formatted(suffixLength));
    }
}
