package it.aboutbits.postgresql.crd.database;

import it.aboutbits.postgresql.core.infrastructure.persistence.Routines;
import jakarta.inject.Singleton;
import org.jooq.CloseableDSLContext;
import org.jooq.DSLContext;
import org.jspecify.annotations.NullMarked;

import static it.aboutbits.postgresql.core.infrastructure.persistence.Tables.PG_DATABASE;
import static org.jooq.impl.DSL.query;
import static org.jooq.impl.DSL.quotedName;
import static org.jooq.impl.DSL.role;
import static org.jooq.impl.DSL.selectOne;

@NullMarked
@Singleton
public class DatabaseService {
    public boolean databaseExists(
            DSLContext tx,
            DatabaseSpec spec
    ) {
        return tx.fetchExists(selectOne()
                .from(PG_DATABASE)
                .where(PG_DATABASE.DATNAME.eq(spec.getName()))
        );
    }

    public void createDatabase(
            DSLContext tx,
            DatabaseSpec spec
    ) {
        var name = quotedName(spec.getName());

        tx.createDatabase(name).execute();

        if (spec.getOwner() != null) {
            changeDatabaseOwner(tx, spec);
        }
    }

    public String fetchDatabaseOwner(
            DSLContext tx,
            DatabaseSpec spec
    ) {
        return tx
                .select(Routines.pgGetUserbyid(
                        PG_DATABASE.DATDBA
                ))
                .from(PG_DATABASE)
                .where(PG_DATABASE.DATNAME.eq(spec.getName()))
                .fetchSingleInto(String.class);
    }

    public void changeDatabaseOwner(
            DSLContext tx,
            DatabaseSpec spec
    ) {
        var name = quotedName(spec.getName());

        tx.execute(query(
                "alter database {0} owner to {1}",
                name,
                role(spec.getOwner())
        ));
    }

    public void dropDatabase(
            DSLContext dsl,
            DatabaseSpec spec
    ) {
        dsl.dropDatabaseIfExists(
                quotedName(spec.getName())
        ).execute();
    }
}
