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
            }
            target {
                packageName = "it.aboutbits.postgresql.core.infrastructure.persistence"
                directory = "src/main/java"
            }
        }
    }
}
