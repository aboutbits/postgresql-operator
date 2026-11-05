package it.aboutbits.gradle.format;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.LineMap;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/// Changes the layout of code that Google Java Format (AOSP) produced, in four places:
///
/// 1. A wrapped parameter list of a method, constructor or record header ends with `)` on its own line.
/// 2. A multi-line annotation ends with `)` on its own line.
/// 3. A declaration annotation of a field, in front of the modifiers, gets its own line.
/// 4. A ternary initializer that Google Java Format breaks after `=` keeps its condition on the `=` line,
///    and `?` and `:` start the next lines.
///
/// The rules change only whitespace. Google Java Format ignores that whitespace on its next run and produces the
/// same output again, so Google Java Format followed by this step always gives the same result.
public final class JavaLayout {
    /// The line length for Google Java Format and for these rules, the same as `max_line_length` in `.editorconfig`.
    /// The rules never make a line longer than this.
    static final int LINE_LENGTH = 120;

    private static final String CONTINUATION_INDENT = " ".repeat(8);

    private static final Set<String> MODIFIERS = Set.of(
            "public",
            "protected",
            "private",
            "static",
            "final",
            "transient",
            "volatile",
            "abstract",
            "synchronized",
            "native",
            "strictfp",
            "default",
            "sealed",
            "non-sealed"
    );

    private JavaLayout() {
    }

    public static String format(String source) {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("The Java layout step needs a JDK with javac, the Gradle daemon runs on a JRE");
        }

        var file = new SimpleJavaFileObject(URI.create("string:///Source.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var task = (JavacTask) compiler.getTask(null, null, diagnostics, List.of("-proc:none"), null, List.of(file));

        CompilationUnitTree unit;
        try {
            unit = task.parse().iterator().next();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        for (var diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                throw new IllegalStateException(
                        "The Java layout step cannot parse the source, the javac of the Gradle daemon may be too old: "
                                + diagnostic.getMessage(null)
                );
            }
        }

        var collector = new EditCollector(source, unit, Trees.instance(task).getSourcePositions());
        collector.scan(unit, null);
        return collector.apply();
    }

    private record Edit(int start, int end, String replacement) {
    }

    private static final class EditCollector extends TreeScanner<Void, Void> {
        private final String source;
        private final CompilationUnitTree unit;
        private final SourcePositions positions;
        private final LineMap lineMap;
        private final List<Edit> edits = new ArrayList<>();

        EditCollector(String source, CompilationUnitTree unit, SourcePositions positions) {
            this.source = source;
            this.unit = unit;
            this.positions = positions;
            this.lineMap = unit.getLineMap();
        }

        String apply() {
            var sorted = new ArrayList<>(edits);
            sorted.sort(Comparator.comparingInt(Edit::start).reversed());

            var result = new StringBuilder(source);
            var limit = source.length() + 1;
            for (var edit : sorted) {
                // Skip an edit that overlaps an edit already applied, the next run applies it
                if (edit.end() > limit) {
                    continue;
                }
                result.replace(edit.start(), edit.end(), edit.replacement());
                limit = edit.start();
            }
            return result.toString();
        }

        // Rule 1: parameter lists of methods and constructors

        @Override
        public Void visitMethod(MethodTree method, Void unused) {
            var parameters = method.getParameters();
            if (!parameters.isEmpty()) {
                var open = previousNonWhitespace(start(parameters.getFirst()));
                var close = nextNonWhitespace(end(parameters.getLast()));
                if (charAt(open) == '(' && charAt(close) == ')' && closingOnOwnLine(open, close, indentOf(open))) {
                    joinThrowsClause(close);
                }
            }
            return super.visitMethod(method, unused);
        }

        /// Google Java Format can put `throws` on the line after the parameters, keep it after the moved `)`.
        private void joinThrowsClause(int close) {
            var next = nextNonWhitespace(close + 1);
            if (source.startsWith("throws", next) && line(next) != line(close)) {
                edits.add(new Edit(close + 1, next, " "));
            }
        }

        // Rule 1: record headers, and rule 3: field annotations

        @Override
        public Void visitClass(ClassTree type, Void unused) {
            if (type.getKind() == Tree.Kind.RECORD) {
                recordHeader(type);
            }
            for (var member : type.getMembers()) {
                if (member instanceof VariableTree field) {
                    fieldAnnotations(field);
                }
            }
            return super.visitClass(type, unused);
        }

        private void recordHeader(ClassTree type) {
            var from = end(type.getModifiers()) > 0 ? end(type.getModifiers()) : start(type);
            var matcher = Pattern.compile("\\brecord\\s+" + Pattern.quote(type.getSimpleName().toString()) + "\\b")
                    .matcher(source);
            if (!matcher.find(from)) {
                return;
            }

            var open = nextNonWhitespace(matcher.end());
            if (charAt(open) == '<') {
                open = nextNonWhitespace(matching(open, '<', '>') + 1);
            }
            if (charAt(open) != '(') {
                return;
            }
            var close = matching(open, '(', ')');
            if (close > 0) {
                closingOnOwnLine(open, close, indentOf(matcher.start()));
            }
        }

        private void fieldAnnotations(VariableTree field) {
            var previousEnd = start(field);
            for (var annotation : field.getModifiers().getAnnotations()) {
                // A modifier in front of the annotation means a type-use position, such as `private @Nullable String`
                if (!source.substring(previousEnd, start(annotation)).isBlank()) {
                    return;
                }
                var annotationEnd = end(annotation);
                var next = nextNonWhitespace(annotationEnd);
                if (charAt(next) != '@' && !startsWithModifier(next)) {
                    return;
                }
                if (line(annotationEnd) == line(next)) {
                    edits.add(new Edit(annotationEnd, next, "\n" + indentOf(start(annotation))));
                }
                previousEnd = annotationEnd;
            }
        }

        // Rule 2: multi-line annotations

        @Override
        public Void visitAnnotation(AnnotationTree annotation, Void unused) {
            if (!annotation.getArguments().isEmpty()) {
                var open = nextNonWhitespace(end(annotation.getAnnotationType()));
                var close = end(annotation) - 1;
                var beforeClose = source.substring(lineStart(close), close).strip();
                // `})` that closes an array on its own line stays as it is
                if (charAt(open) == '(' && charAt(close) == ')' && !beforeClose.matches("[})\\]]+")) {
                    closingOnOwnLine(open, close, indentOf(start(annotation)));
                }
            }
            return super.visitAnnotation(annotation, unused);
        }

        // Rule 4: ternary initializers and assignments

        @Override
        public Void visitVariable(VariableTree variable, Void unused) {
            if (variable.getInitializer() instanceof ConditionalExpressionTree conditional) {
                ternary(conditional);
            }
            return super.visitVariable(variable, unused);
        }

        @Override
        public Void visitAssignment(AssignmentTree assignment, Void unused) {
            if (assignment.getExpression() instanceof ConditionalExpressionTree conditional) {
                ternary(conditional);
            }
            return super.visitAssignment(assignment, unused);
        }

        private void ternary(ConditionalExpressionTree conditional) {
            var condition = conditional.getCondition();
            var whenTrue = conditional.getTrueExpression();
            var whenFalse = conditional.getFalseExpression();

            var equals = previousNonWhitespace(start(conditional));
            var conditionStart = start(condition);
            var conditionEnd = end(condition);
            var question = previousNonWhitespace(start(whenTrue));
            var colon = previousNonWhitespace(start(whenFalse));
            var end = end(whenFalse);

            if (charAt(equals) != '=' || charAt(question) != '?' || charAt(colon) != ':') {
                return;
            }
            // Only where Google Java Format broke after `=`, and only for a condition on one line
            if (line(equals) == line(conditionStart) || line(conditionStart) != line(conditionEnd)) {
                return;
            }
            // Comments and text blocks between the parts stay untouched
            var segment = source.substring(equals, end);
            if (segment.contains("//") || segment.contains("/*") || segment.contains("\"\"\"")) {
                return;
            }

            var indent = indentOf(equals) + CONTINUATION_INDENT;
            var trueText = reindent(start(whenTrue), end(whenTrue), indent.length() + 2);
            var falseText = reindent(start(whenFalse), end, indent.length() + 2);
            if (trueText == null || falseText == null) {
                return;
            }

            var replacement = "= " + source.substring(conditionStart, conditionEnd)
                    + "\n" + indent + "? " + trueText
                    + "\n" + indent + ": " + falseText;

            var result = source.substring(lineStart(equals), equals) + replacement + source.substring(end, lineEnd(end));
            if (result.lines().anyMatch(resultLine -> resultLine.length() > LINE_LENGTH)) {
                return;
            }
            edits.add(new Edit(equals, end, replacement));
        }

        /// Returns the text from `start` to `end`, with its lines after the first moved so that the first character
        /// moves to `newColumn`, or `null` when a line has too little indent for that.
        private String reindent(int start, int end, int newColumn) {
            var shift = newColumn - (start - lineStart(start));
            var lines = source.substring(start, end).split("\n", -1);
            for (var i = 1; i < lines.length; i++) {
                var text = lines[i];
                if (text.isBlank()) {
                    continue;
                }
                if (shift >= 0) {
                    lines[i] = " ".repeat(shift) + text;
                } else {
                    var leading = text.length() - text.stripLeading().length();
                    if (leading < -shift) {
                        return null;
                    }
                    lines[i] = text.substring(-shift);
                }
            }
            return String.join("\n", lines);
        }

        // Shared helpers

        /// Puts `close` on its own line with `indent` when the parentheses span several lines.
        /// Returns `true` when it added an edit.
        private boolean closingOnOwnLine(int open, int close, String indent) {
            if (line(open) == line(close) || source.substring(lineStart(close), close).isBlank()) {
                return false;
            }
            edits.add(new Edit(previousNonWhitespace(close) + 1, close, "\n" + indent));
            return true;
        }

        /// Returns the position of the bracket that closes the one at `open`, or `-1`. Skips string and char
        /// literals, which is enough for the code that Google Java Format produces.
        private int matching(int open, char opening, char closing) {
            var depth = 0;
            for (var i = open; i < source.length(); i++) {
                var c = source.charAt(i);
                if (c == '"' || c == '\'') {
                    i = endOfLiteral(i, c);
                } else if (c == opening) {
                    depth++;
                } else if (c == closing && --depth == 0) {
                    return i;
                }
            }
            return -1;
        }

        private int endOfLiteral(int start, char quote) {
            for (var i = start + 1; i < source.length(); i++) {
                var c = source.charAt(i);
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    return i;
                }
            }
            return source.length();
        }

        private boolean startsWithModifier(int position) {
            var end = position;
            while (end < source.length()
                    && (Character.isJavaIdentifierPart(source.charAt(end)) || source.charAt(end) == '-')) {
                end++;
            }
            return MODIFIERS.contains(source.substring(position, end));
        }

        private int start(Tree tree) {
            return (int) positions.getStartPosition(unit, tree);
        }

        private int end(Tree tree) {
            return (int) positions.getEndPosition(unit, tree);
        }

        private long line(int position) {
            return lineMap.getLineNumber(position);
        }

        private char charAt(int position) {
            return position >= 0 && position < source.length() ? source.charAt(position) : '\0';
        }

        private int nextNonWhitespace(int position) {
            var i = position;
            while (i < source.length() && Character.isWhitespace(source.charAt(i))) {
                i++;
            }
            return i;
        }

        private int previousNonWhitespace(int position) {
            var i = position - 1;
            while (i >= 0 && Character.isWhitespace(source.charAt(i))) {
                i--;
            }
            return i;
        }

        private int lineStart(int position) {
            return source.lastIndexOf('\n', position - 1) + 1;
        }

        private int lineEnd(int position) {
            var end = source.indexOf('\n', position);
            return end < 0 ? source.length() : end;
        }

        private String indentOf(int position) {
            var start = lineStart(position);
            var i = start;
            while (i < source.length() && source.charAt(i) == ' ') {
                i++;
            }
            return source.substring(start, i);
        }
    }
}
