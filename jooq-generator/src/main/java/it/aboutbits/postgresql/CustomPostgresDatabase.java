package it.aboutbits.postgresql;

import org.jooq.meta.RoutineDefinition;
import org.jooq.meta.postgres.PostgresDatabase;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class CustomPostgresDatabase extends PostgresDatabase {
    @Override
    protected List<RoutineDefinition> getRoutines0() throws SQLException {
        var routines = super.getRoutines0()
                .stream()
                .filter(routineDefinition -> getSchemata().contains(routineDefinition.getSchema()))
                .toList();

        var result = new ArrayList<RoutineDefinition>();

        for (var routine : routines) {
            // Only generate the has_*_privileges routine overloads with parameter types <name>, <text>, <text>[, <text>]
            // See https://www.postgresql.org/docs/current/functions-info.html#FUNCTIONS-INFO-ACCESS
            if (routine.getName().startsWith("has_") && routine.getName().endsWith("_privilege")) {
                var firstParameterTypeIsName = routine.getInParameters()
                        .getFirst()
                        .getType()
                        .getType()
                        .equals("name");

                var otherParameterTypesAreText = routine.getInParameters()
                        .stream()
                        .skip(1)
                        .allMatch(parameterDefinition -> parameterDefinition.getType()
                                .getType()
                                .equals("text")
                        );

                if (firstParameterTypeIsName && otherParameterTypesAreText) {
                    result.add(routine);
                }
            } else {
                // Generate all other functions normally
                result.add(routine);
            }
        }

        return result;
    }
}
