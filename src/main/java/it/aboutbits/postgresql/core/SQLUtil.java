package it.aboutbits.postgresql.core;

import org.jooq.QueryPart;
import org.jspecify.annotations.NullMarked;

import java.util.List;

import static org.jooq.impl.DSL.sql;

@NullMarked
public final class SQLUtil {
    public static QueryPart concatenateQueryPartsWithSpaces(List<? extends QueryPart> parts) {
        return concatenateQueryParts(parts, " ");
    }

    public static QueryPart concatenateQueryPartsWithComma(List<? extends QueryPart> parts) {
        return concatenateQueryParts(parts, ", ");
    }

    /**
     * Concatenate QueryParts with the requested separator
     */
    private static QueryPart concatenateQueryParts(
            List<? extends QueryPart> items,
            String separator
    ) {
        int size = items.size();

        if (items.isEmpty()) {
            return sql("");
        } else if (size == 1) {
            return items.getFirst();
        }

        var template = new StringBuilder();

        // Add the first item without a separator
        template.append('{').append(0).append('}');

        // Add the rest of the items with the leading separator
        for (int i = 1; i < size; i++) {
            template.append(separator).append('{').append(i).append('}');
        }

        return sql(
                template.toString(),
                items.toArray(QueryPart[]::new)
        );
    }

    private SQLUtil() {
    }
}
