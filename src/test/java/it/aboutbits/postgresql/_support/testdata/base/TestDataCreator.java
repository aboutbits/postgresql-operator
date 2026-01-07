package it.aboutbits.postgresql._support.testdata.base;

import net.datafaker.Faker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class TestDataCreator<ITEM> {
    protected static final Faker FAKER = new Faker();

    protected final int numberOfItems;

    protected TestDataCreator(int numberOfItems) {
        this.numberOfItems = numberOfItems;
    }

    public void apply() {
        create();
    }

    public ITEM returnFirst() {
        return create().getFirst();
    }

    public List<ITEM> returnAll() {
        return create();
    }

    public Set<ITEM> returnSet() {
        return new HashSet<>(create());
    }

    protected List<ITEM> create() {
        var result = new ArrayList<ITEM>();

        for (var index = 0; index < numberOfItems; index++) {
            result.add(
                    create(index)
            );
        }

        return result;
    }

    protected abstract ITEM create(int index);

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
