plugins {
    `java-library`
    alias(libs.plugins.jooqPlugin)
}

dependencies {
    /**
     * jOOQ
     */
    api(libs.jooq)
    compileOnly(libs.jooqMeta)
    compileOnlyApi(libs.jspecify)
    // PostgreSQL JDBC Driver for jOOQ generation
    jooqCodegen(libs.postgresql)
}

jooq {
    configuration {
        jdbc {
            driver = "org.postgresql.Driver"
            url = "jdbc:postgresql://localhost:5432/postgres"
            user = "root"
            password = "password"
        }
        generator {
            database {
                name = "org.jooq.meta.postgres.PostgresDatabase"
                schemata {
                    schema {
                        inputSchema = "pg_catalog"
                    }
                }
                includes = """
                  aclexplode   
                | pg_auth_members
                | pg_authid
                | pg_class
                | pg_database
                | pg_db_role_setting
                | pg_default_acl
                | pg_get_userbyid
                | pg_namespace
                | shobj_description
                """.trimIndent()
                excludes = """
                """.trimIndent()
            }
            generate {
                deprecated = false
                fluentSetters = true
                generatedAnnotation = true
                pojos = false
                nonnullAnnotation = true
                nullableAnnotation = true
                // We use JSpecify annotations already even though jOOQ does not officially support JSpecify's TYPE_USE positioning yet.
                // See https://github.com/jOOQ/jOOQ/issues/10759
                // The positioning is correct for every scalar column, as our generated code is not using any generics,
                // collections, maps or forced types with inner classes.
                // The positioning is wrong for the six array columns. Java applies a TYPE_USE annotation written before
                // an array type to the element type, and NullAway drops array-dimension annotations in JSpecifyMode.
                // So "@Nullable String[] getNspacl()" reads as a non-null array of nullable strings, while the column is
                // a nullable array of non-null strings.
                // The six accessors are getNspacl, getDatacl, getRelacl, getReloptions, getSetconfig and getDefaclacl.
                // No operator code calls them today, as the operator uses the table field constants and Routines.aclexplode.
                // A declaration annotation for nullableAnnotationType would fix the positioning, as such an annotation
                // always applies to the method. That is a design change, not a comment fix.
                nonnullAnnotationType = "org.jspecify.annotations.NonNull"
                nullableAnnotationType = "org.jspecify.annotations.Nullable"
            }
            target {
                packageName = "it.aboutbits.postgresql.core.infrastructure.persistence"
                directory = "src/main/java"
            }
        }
    }
}
