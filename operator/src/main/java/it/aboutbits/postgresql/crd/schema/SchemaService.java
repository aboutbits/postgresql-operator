package it.aboutbits.postgresql.crd.schema;

import it.aboutbits.postgresql.core.infrastructure.persistence.Routines;
import jakarta.inject.Singleton;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

import static it.aboutbits.postgresql.core.infrastructure.persistence.Tables.PG_NAMESPACE;
import static org.jooq.impl.DSL.query;
import static org.jooq.impl.DSL.quotedName;
import static org.jooq.impl.DSL.role;
import static org.jooq.impl.DSL.selectOne;

@NullMarked
@Singleton
public class SchemaService {
    public boolean schemaExists(
            DSLContext tx,
            SchemaSpec spec
    ) {
        return tx.fetchExists(selectOne()
                .from(PG_NAMESPACE)
                .where(PG_NAMESPACE.NSPNAME.eq(spec.getName()))
        );
    }

    public void createSchema(
            DSLContext dsl,
            SchemaSpec spec
    ) {
        var name = quotedName(spec.getName());

        dsl.createSchema(name).execute();

        if (spec.getOwner() != null) {
            changeSchemaOwner(dsl, spec);
        }
    }

    public String fetchSchemaOwner(
            DSLContext tx,
            SchemaSpec spec
    ) {
        return tx
                .select(Routines.pgGetUserbyid(
                        PG_NAMESPACE.NSPOWNER
                ))
                .from(PG_NAMESPACE)
                .where(PG_NAMESPACE.NSPNAME.eq(spec.getName()))
                .fetchSingleInto(String.class);
    }

    public void changeSchemaOwner(
            DSLContext tx,
            SchemaSpec spec
    ) {
        var name = quotedName(spec.getName());

        tx.execute(query(
                "alter schema {0} owner to {1}",
                name,
                role(spec.getOwner())
        ));
    }

    public void dropSchema(
            DSLContext dsl,
            SchemaSpec spec
    ) {
        dsl.dropSchemaIfExists(
                quotedName(spec.getName())
        ).execute();
    }
}
