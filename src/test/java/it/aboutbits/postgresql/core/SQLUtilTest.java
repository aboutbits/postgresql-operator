package it.aboutbits.postgresql.core;

import org.jooq.QueryPart;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.sql;

@NullMarked
class SQLUtilTest {
    @Nested
    class ConcatenateQueryPartsWithSpaces {
        @Test
        @DisplayName("when empty, should return empty string")
        void whenEmpty_shouldReturnEmptyString() {
            // given / when
            var result = SQLUtil.concatenateQueryPartsWithSpaces(List.of());

            // then
            assertThat(render(result)).isEmpty();
        }

        @Test
        @DisplayName("when single item, should return item")
        void whenSingleItem_shouldReturnItem() {
            // given
            var part = sql("item1");

            // when
            var result = SQLUtil.concatenateQueryPartsWithSpaces(List.of(part));

            // then
            assertThat(render(result)).isEqualTo("item1");
        }

        @Test
        @DisplayName("when multiple items, should join with spaces")
        void whenMultipleItems_shouldJoinWithSpaces() {
            // given
            var parts = List.of(sql("item1"), sql("item2"), sql("item3"));

            // when
            var result = SQLUtil.concatenateQueryPartsWithSpaces(parts);

            // then
            assertThat(render(result)).isEqualTo("item1 item2 item3");
        }
    }

    @Nested
    class ConcatenateQueryPartsWithComma {
        @Test
        @DisplayName("when empty, should return empty string")
        void whenEmpty_shouldReturnEmptyString() {
            // given / when
            var result = SQLUtil.concatenateQueryPartsWithComma(List.of());

            // then
            assertThat(render(result)).isEmpty();
        }

        @Test
        @DisplayName("when single item, should return item")
        void whenSingleItem_shouldReturnItem() {
            // given
            var part = sql("item1");

            // when
            var result = SQLUtil.concatenateQueryPartsWithComma(List.of(part));

            // then
            assertThat(render(result)).isEqualTo("item1");
        }

        @Test
        @DisplayName("when multiple items, should join with comma")
        void whenMultipleItems_shouldJoinWithComma() {
            // given
            var parts = List.of(sql("item1"), sql("item2"), sql("item3"));

            // when
            var result = SQLUtil.concatenateQueryPartsWithComma(parts);

            // then
            assertThat(render(result)).isEqualTo("item1, item2, item3");
        }
    }

    private String render(QueryPart queryPart) {
        return DSL.using(SQLDialect.POSTGRES).render(queryPart);
    }
}
